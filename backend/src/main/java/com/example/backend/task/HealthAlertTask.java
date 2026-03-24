package com.example.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.entity.ComponentConfig;
import com.example.backend.entity.Information;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.ComponentConfigMapper;
import com.example.backend.mapper.InformationMapper;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.service.AlertMailService;
import com.example.backend.service.AlertRecipientService;
import com.example.backend.service.DiagnosisService;
import com.example.backend.service.MonitorService;
import com.example.backend.utils.InfoNormalizationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HealthAlertTask {

    private static final String HIGH_RISK_LEVEL = "高";
    private static final String SYSTEM_MONITOR_COMPONENT = "系统监控";
    private static final String SYSTEM_MONITOR_CONFIG_KEY = "system_monitor";
    private static final String MONITOR_RECHECK_ACTIONS = "检查 SSH 网络连通性；确认服务器在线；检查 SSH 端口、账号密码和防火墙配置。";
    private static final String DIAGNOSIS_RECHECK_ACTIONS = "重新检查日志路径、SSH 连接、服务运行状态以及最近错误日志；必要时人工登录服务器进一步确认。";

    private final Map<Long, LocalDateTime> processedAlertMap = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> sentFingerprintMap = new ConcurrentHashMap<>();

    @Autowired
    private InformationMapper informationMapper;

    @Autowired
    private ComponentConfigMapper componentConfigMapper;

    @Autowired
    private UserLoginMapper userLoginMapper;

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private DiagnosisService diagnosisService;

    @Autowired
    private AlertRecipientService alertRecipientService;

    @Autowired
    private AlertMailService alertMailService;

    @Value("${alert.mail.cooldown-minutes:30}")
    private long cooldownMinutes;

    @Value("${alert.recheck.lookback-minutes:20}")
    private long lookbackMinutes;

    @Scheduled(fixedRateString = "${alert.schedule.fixed-rate:120000}")
    public void checkAndSendAlerts() {
        LocalDateTime now = LocalDateTime.now();
        cleanupState(now);

        List<Information> recentAlerts = informationMapper.selectList(
                new LambdaQueryWrapper<Information>()
                        .ge(Information::getCreatedAt, now.minusMinutes(Math.max(lookbackMinutes, 1)))
                        .orderByAsc(Information::getCreatedAt)
        );

        if (recentAlerts == null || recentAlerts.isEmpty()) {
            return;
        }

        for (Information alert : recentAlerts) {
            if (!shouldProcess(alert)) {
                continue;
            }

            try {
                processAlert(alert);
            } catch (Exception ex) {
                log.error("高风险告警二次复检失败, alertId={}, userId={}, serverIp={}, component={}",
                        alert.getId(), alert.getUserId(), alert.getServerIp(), alert.getComponent(), ex);
            }
        }
    }

    private boolean shouldProcess(Information alert) {
        return alert != null
                && alert.getId() != null
                && !processedAlertMap.containsKey(alert.getId())
                && HIGH_RISK_LEVEL.equals(InfoNormalizationUtils.normalizeRiskLevel(alert.getRiskLevel()));
    }

    private void processAlert(Information alert) {
        RecheckResult recheckResult = recheckAlert(alert);

        if (recheckResult.falsePositive) {
            informationMapper.deleteById(alert.getId());
            processedAlertMap.put(alert.getId(), LocalDateTime.now());
            log.info("高风险告警复检后判定为误报，已删除告警, alertId={}, serverIp={}, component={}",
                    alert.getId(), alert.getServerIp(), alert.getComponent());
            return;
        }

        String recipient = alertRecipientService.getRecipientByUserId(alert.getUserId());
        if (!StringUtils.hasText(recipient)) {
            processedAlertMap.put(alert.getId(), LocalDateTime.now());
            log.debug("高风险告警已复检确认，但用户 {} 尚未配置邮箱, alertId={}, serverIp={}",
                    alert.getUserId(), alert.getId(), alert.getServerIp());
            return;
        }

        String fingerprint = buildFingerprint(alert, recheckResult.confirmedInfo);
        LocalDateTime lastSentAt = sentFingerprintMap.get(fingerprint);
        if (lastSentAt != null && lastSentAt.plusMinutes(cooldownMinutes).isAfter(LocalDateTime.now())) {
            processedAlertMap.put(alert.getId(), LocalDateTime.now());
            log.debug("高风险告警邮件仍处于冷却期，跳过发送, alertId={}, fingerprint={}", alert.getId(), fingerprint);
            return;
        }

        String username = resolveUsername(alert.getUserId());
        alertMailService.sendConfirmedHighRiskAlert(
                recipient,
                username,
                alert,
                recheckResult.confirmedInfo,
                recheckResult.note
        );

        LocalDateTime sentAt = LocalDateTime.now();
        sentFingerprintMap.put(fingerprint, sentAt);
        processedAlertMap.put(alert.getId(), sentAt);
        log.info("高风险告警已复检确认并发送邮件, alertId={}, userId={}, serverIp={}, recipient={}",
                alert.getId(), alert.getUserId(), alert.getServerIp(), recipient);
    }

    private RecheckResult recheckAlert(Information alert) {
        if (SYSTEM_MONITOR_COMPONENT.equals(InfoNormalizationUtils.normalizeComponent(alert.getComponent()))) {
            return recheckSystemMonitorAlert(alert);
        }
        return recheckDiagnosisAlert(alert);
    }

    private RecheckResult recheckSystemMonitorAlert(Information alert) {
        ComponentConfig config = componentConfigMapper.selectOne(
                new LambdaQueryWrapper<ComponentConfig>()
                        .eq(ComponentConfig::getUserId, alert.getUserId())
                        .eq(ComponentConfig::getServerIp, safeText(alert.getServerIp()))
                        .eq(ComponentConfig::getConfigKey, SYSTEM_MONITOR_CONFIG_KEY)
                        .orderByDesc(ComponentConfig::getUpdatedAt)
                        .last("LIMIT 1")
        );

        if (config == null) {
            return RecheckResult.falsePositive("系统监控配置已不存在，原高风险告警不再继续通知。");
        }
        if (Integer.valueOf(0).equals(config.getIsEnabled())) {
            return RecheckResult.falsePositive("系统监控已暂停检测，原高风险告警不再继续通知。");
        }
        if (!StringUtils.hasText(config.getUsername()) || !StringUtils.hasText(config.getPassword())) {
            String note = "系统监控告警二次复检失败：未找到可用的 SSH 监控配置，已按原高风险告警处理。";
            return RecheckResult.confirmed(buildFallbackHighRiskInfo(alert, note, MONITOR_RECHECK_ACTIONS), note);
        }

        Map<String, Object> sample = monitorService.sampleMetricsOnce(
                alert.getServerIp(),
                config.getUsername(),
                config.getPassword(),
                config.getConfigValue()
        );

        if (Boolean.TRUE.equals(sample.get("success"))) {
            return RecheckResult.falsePositive("系统监控二次复检成功，CPU/内存采集已经恢复正常。");
        }

        String error = safeText(String.valueOf(sample.getOrDefault("error", "CPU/内存采集仍然失败")));
        String note = "系统监控二次复检仍失败：" + error;
        return RecheckResult.confirmed(buildFallbackHighRiskInfo(alert, note, MONITOR_RECHECK_ACTIONS), note);
    }

    private RecheckResult recheckDiagnosisAlert(Information alert) {
        ComponentConfig config = componentConfigMapper.selectOne(
                new LambdaQueryWrapper<ComponentConfig>()
                        .eq(ComponentConfig::getUserId, alert.getUserId())
                        .eq(ComponentConfig::getServerIp, safeText(alert.getServerIp()))
                        .eq(ComponentConfig::getComponent, safeText(alert.getComponent()))
                        .orderByDesc(ComponentConfig::getIsVerified)
                        .orderByDesc(ComponentConfig::getUpdatedAt)
                        .last("LIMIT 1")
        );

        if (config == null) {
            return RecheckResult.falsePositive("对应日志监控配置已不存在，原高风险告警不再继续通知。");
        }
        if (Integer.valueOf(0).equals(config.getIsEnabled())) {
            return RecheckResult.falsePositive("对应日志监控已暂停检测，原高风险告警不再继续通知。");
        }

        try {
            Information recheckedInfo = diagnosisService.buildDiagnosisInfo(config);
            String recheckedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel(recheckedInfo.getRiskLevel());
            if (!HIGH_RISK_LEVEL.equals(recheckedRiskLevel)) {
                String note = "日志告警二次复检结果已降为 " + recheckedRiskLevel + "，原高风险告警判定为误报。";
                return RecheckResult.falsePositive(note);
            }
            return RecheckResult.confirmed(recheckedInfo, "日志告警二次复检后仍为高风险。");
        } catch (Exception ex) {
            String note = "日志告警二次复检执行失败：" + safeText(ex.getMessage());
            return RecheckResult.confirmed(buildFallbackHighRiskInfo(alert, note, DIAGNOSIS_RECHECK_ACTIONS), note);
        }
    }

    private Information buildFallbackHighRiskInfo(Information originalAlert, String note, String suggestedActions) {
        Information info = new Information();
        info.setUserId(originalAlert.getUserId());
        info.setServerIp(originalAlert.getServerIp());
        info.setComponent(InfoNormalizationUtils.normalizeComponent(originalAlert.getComponent()));
        info.setErrorSummary(InfoNormalizationUtils.normalizeText(originalAlert.getErrorSummary(), "高风险告警仍然存在"));
        info.setAnalysisResult(InfoNormalizationUtils.normalizeText(note, "高风险告警二次复检后仍然异常"));
        info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions(suggestedActions, HIGH_RISK_LEVEL));
        info.setRawLog(InfoNormalizationUtils.normalizeText(note, "无"));
        info.setRiskLevel(HIGH_RISK_LEVEL);
        info.setCreatedAt(LocalDateTime.now());
        return info;
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "";
        }
        UserLogin user = userLoginMapper.selectById(userId);
        return user == null ? "" : user.getUsername();
    }

    private String buildFingerprint(Information originalAlert, Information confirmedInfo) {
        String component = confirmedInfo != null && StringUtils.hasText(confirmedInfo.getComponent())
                ? confirmedInfo.getComponent()
                : originalAlert.getComponent();
        String summary = confirmedInfo != null && StringUtils.hasText(confirmedInfo.getErrorSummary())
                ? confirmedInfo.getErrorSummary()
                : originalAlert.getErrorSummary();
        return (originalAlert.getUserId() == null ? 0L : originalAlert.getUserId())
                + "|"
                + safeText(originalAlert.getServerIp())
                + "|"
                + safeText(component)
                + "|"
                + safeText(summary);
    }

    private void cleanupState(LocalDateTime now) {
        LocalDateTime processedExpireAt = now.minusMinutes(Math.max(lookbackMinutes, cooldownMinutes) + 10);
        processedAlertMap.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(processedExpireAt));
        sentFingerprintMap.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().plusMinutes(cooldownMinutes).isBefore(now));
    }

    private String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private static class RecheckResult {
        private final boolean falsePositive;
        private final Information confirmedInfo;
        private final String note;

        private RecheckResult(boolean falsePositive, Information confirmedInfo, String note) {
            this.falsePositive = falsePositive;
            this.confirmedInfo = confirmedInfo;
            this.note = note;
        }

        private static RecheckResult falsePositive(String note) {
            return new RecheckResult(true, null, note);
        }

        private static RecheckResult confirmed(Information confirmedInfo, String note) {
            return new RecheckResult(false, confirmedInfo, note);
        }
    }
}

package com.example.backend.service;

import com.example.backend.entity.Information;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class AlertMailService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JavaMailSender mailSender;

    @Value("${alert.mail.from}")
    private String from;

    public AlertMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendHealthAlert(String to,
                                String username,
                                String serverIp,
                                String fingerprint,
                                Map<String, Object> healthState) {
        String subject = "[Mimir-Ops] 服务器红色告警 - " + (StringUtils.hasText(serverIp) ? serverIp : "未知服务器");
        String content = buildHealthAlertContent(username, serverIp, fingerprint, healthState);
        sendPlainText(to, subject, content);
    }

    public void sendConfirmedHighRiskAlert(String to,
                                           String username,
                                           Information originalAlert,
                                           Information recheckedAlert,
                                           String recheckNote) {
        String serverIp = originalAlert == null ? "" : originalAlert.getServerIp();
        String subject = "[Mimir-Ops] 高风险告警确认 - " + (StringUtils.hasText(serverIp) ? serverIp : "未知服务器");
        String content = buildConfirmedHighRiskAlertContent(username, originalAlert, recheckedAlert, recheckNote);
        sendPlainText(to, subject, content);
    }

    public void sendTestMail(String to, String username) {
        String subject = "[Mimir-Ops] 邮件通道测试";
        StringBuilder content = new StringBuilder();
        content.append("这是一封来自 Mimir-Ops 的测试邮件。\n\n");
        if (StringUtils.hasText(username)) {
            content.append("当前用户：").append(username).append('\n');
        }
        content.append("发送时间：").append(LocalDateTime.now().format(TIME_FORMATTER)).append('\n');
        content.append("如果你收到这封邮件，说明 QQ SMTP 配置已经可用。");
        sendPlainText(to, subject, content.toString());
    }

    public void sendPlainText(String to, String subject, String content) {
        if (!StringUtils.hasText(to)) {
            throw new RuntimeException("收件邮箱不能为空");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.trim());
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    private String buildHealthAlertContent(String username,
                                           String serverIp,
                                           String fingerprint,
                                           Map<String, Object> healthState) {
        StringBuilder content = new StringBuilder();
        content.append("Minir-Ops 检测到服务器连续两次处于不健康状态。\n\n");
        content.append("告警时间：").append(LocalDateTime.now().format(TIME_FORMATTER)).append('\n');
        if (StringUtils.hasText(username)) {
            content.append("所属用户：").append(username).append('\n');
        }
        content.append("服务器 IP：").append(StringUtils.hasText(serverIp) ? serverIp : "未知").append('\n');
        content.append("问题标识：").append(StringUtils.hasText(fingerprint) ? fingerprint : "未识别").append('\n');
        content.append("健康分数：").append(String.valueOf(healthState.getOrDefault("score", 0))).append('\n');
        content.append("健康等级：").append(String.valueOf(healthState.getOrDefault("label", "高风险"))).append('\n');
        content.append("CPU：").append(String.valueOf(healthState.getOrDefault("cpuUsage", 0))).append("%\n");
        content.append("内存：").append(String.valueOf(healthState.getOrDefault("memUsage", 0))).append("%\n");
        content.append("说明：").append(String.valueOf(healthState.getOrDefault("description", "服务器存在异常，请尽快排查"))).append("\n\n");
        content.append("判定依据：\n");

        Object reasonsValue = healthState.get("reasons");
        if (reasonsValue instanceof List<?> reasons && !reasons.isEmpty()) {
            int index = 1;
            for (Object reason : reasons) {
                content.append(index++).append(". ").append(String.valueOf(reason)).append('\n');
            }
        } else {
            content.append("1. 服务器健康度已进入红色区间\n");
        }

        content.append("\n请尽快登录系统排查对应服务器。");
        return content.toString();
    }

    private String buildConfirmedHighRiskAlertContent(String username,
                                                      Information originalAlert,
                                                      Information recheckedAlert,
                                                      String recheckNote) {
        StringBuilder content = new StringBuilder();
        content.append("Mimir-Ops 检测到一条高风险告警，并完成了二次复检，结果仍然异常。\n\n");
        content.append("确认时间：").append(LocalDateTime.now().format(TIME_FORMATTER)).append('\n');
        if (StringUtils.hasText(username)) {
            content.append("所属用户：").append(username).append('\n');
        }
        content.append("服务器 IP：").append(getInfoValue(originalAlert, Information::getServerIp, "未知")).append('\n');
        content.append("组件：").append(getInfoValue(originalAlert, Information::getComponent, "未知")).append('\n');
        content.append("首次告警时间：").append(formatTime(originalAlert == null ? null : originalAlert.getCreatedAt())).append('\n');
        content.append("首次告警摘要：").append(getInfoValue(originalAlert, Information::getErrorSummary, "无")).append('\n');
        content.append("首次风险等级：").append(getInfoValue(originalAlert, Information::getRiskLevel, "高")).append("\n\n");

        content.append("复检结论：").append(getInfoValue(recheckedAlert, Information::getErrorSummary, "复检后仍为高风险异常")).append('\n');
        content.append("复检风险等级：").append(getInfoValue(recheckedAlert, Information::getRiskLevel, "高")).append('\n');
        content.append("复检分析：").append(getInfoValue(recheckedAlert, Information::getAnalysisResult, "服务器异常仍然存在，请尽快排查")).append('\n');

        String actions = formatSuggestedActions(recheckedAlert == null ? null : recheckedAlert.getSuggestedActions());
        content.append("建议处理：").append(actions).append('\n');

        if (StringUtils.hasText(recheckNote)) {
            content.append("复检备注：").append(recheckNote.trim()).append('\n');
        }

        String rawLog = getInfoValue(recheckedAlert, Information::getRawLog, "");
        if (StringUtils.hasText(rawLog) && !"无".equals(rawLog)) {
            content.append("复检原始信息：").append(limitText(rawLog, 240)).append('\n');
        }

        content.append("\n请尽快登录系统查看详情并处理对应服务器。");
        return content.toString();
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return "未知";
        }
        return time.format(TIME_FORMATTER);
    }

    private String formatSuggestedActions(String rawValue) {
        if (!StringUtils.hasText(rawValue) || "[]".equals(rawValue.trim())) {
            return "无";
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawValue);
            if (root.isArray() && !root.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                int index = 1;
                for (JsonNode node : root) {
                    String value = node == null ? "" : node.asText("");
                    if (!StringUtils.hasText(value)) {
                        continue;
                    }
                    if (builder.length() > 0) {
                        builder.append("；");
                    }
                    builder.append(index++).append(". ").append(value.trim());
                }
                if (builder.length() > 0) {
                    return builder.toString();
                }
            }
        } catch (Exception ignored) {
        }

        return rawValue.trim();
    }

    private String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim() + "...";
    }

    private String getInfoValue(Information info,
                                java.util.function.Function<Information, String> extractor,
                                String defaultValue) {
        if (info == null) {
            return defaultValue;
        }
        String value = extractor.apply(info);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}

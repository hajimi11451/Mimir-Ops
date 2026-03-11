package com.example.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.dto.MetricDTO;
import com.example.backend.entity.Information;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.InformationMapper;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.utils.InfoNormalizationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthService {

    private static final int ALERT_WINDOW_MINUTES = 10;
    private static final int HIGH_ALERT_PENALTY = 60;
    private static final int MEDIUM_ALERT_PENALTY = 40;
    private static final int LOW_ALERT_PENALTY = 20;
    private static final double RESOURCE_PENALTY_RATE = 0.15;
    private static final int DOUBLE_HIGH_BURST_PENALTY = 8;
    private static final int SINGLE_CRITICAL_BURST_PENALTY = 15;
    private static final int DOUBLE_CRITICAL_BURST_PENALTY = 30;
    private static final int WARNING_SCORE_THRESHOLD = 75;
    private static final int ERROR_SCORE_THRESHOLD = 45;

    @Autowired
    private InformationMapper informationMapper;

    @Autowired
    private UserLoginMapper userLoginMapper;

    public Map<String, Object> buildHealthState(String username,
                                                String serverIp,
                                                Map<String, Object> currentInfo,
                                                List<MetricDTO> history) {
        List<Information> recentInfos = loadRecentInfos(username, serverIp);
        return buildHealthState(currentInfo, history, recentInfos);
    }

    public Map<String, Object> buildHealthState(Long userId,
                                                String serverIp,
                                                Map<String, Object> currentInfo,
                                                List<MetricDTO> history) {
        List<Information> recentInfos = loadRecentInfos(userId, serverIp);
        return buildHealthState(currentInfo, history, recentInfos);
    }

    public List<Information> loadRecentInfos(String username, String serverIp) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(serverIp)) {
            return List.of();
        }

        UserLogin user = userLoginMapper.selectOne(
                new LambdaQueryWrapper<UserLogin>().eq(UserLogin::getUsername, username.trim())
        );
        return loadRecentInfos(user == null ? null : user.getId(), serverIp);
    }

    public List<Information> loadRecentInfos(Long userId, String serverIp) {
        if (userId == null || !StringUtils.hasText(serverIp)) {
            return List.of();
        }

        LocalDateTime since = LocalDateTime.now().minusMinutes(ALERT_WINDOW_MINUTES);
        return informationMapper.selectList(
                new LambdaQueryWrapper<Information>()
                        .eq(Information::getUserId, userId)
                        .eq(Information::getServerIp, serverIp.trim())
                        .ge(Information::getCreatedAt, since)
                        .orderByDesc(Information::getCreatedAt)
        );
    }

    private Map<String, Object> buildHealthState(Map<String, Object> currentInfo,
                                                 List<MetricDTO> history,
                                                 List<Information> recentInfos) {
        double cpuUsage = resolveUsage(currentInfo, history, true);
        double memUsage = resolveUsage(currentInfo, history, false);
        boolean hasMetricData = hasMetricData(currentInfo, history);

        Map<String, Integer> riskSummary = summarizeRiskLevels(recentInfos);
        int highRiskCount = riskSummary.get("高");
        int mediumRiskCount = riskSummary.get("中");
        int lowRiskCount = riskSummary.get("低");
        int normalCount = riskSummary.get("无");
        int activeAlertCount = highRiskCount + mediumRiskCount + lowRiskCount;

        if (!hasMetricData && activeAlertCount == 0) {
            return buildPendingState(normalCount);
        }

        int alertPenalty = resolveAlertPenalty(highRiskCount, mediumRiskCount, lowRiskCount);
        int cpuPenalty = roundToInt(cpuUsage * RESOURCE_PENALTY_RATE);
        int memPenalty = roundToInt(memUsage * RESOURCE_PENALTY_RATE);
        int burstPenalty = resolveBurstPenalty(cpuUsage, memUsage);

        int score = clamp(100 - alertPenalty - cpuPenalty - memPenalty - burstPenalty, 0, 100);
        String level = resolveLevel(score);
        String label = resolveLabel(level);
        String description = resolveDescription(level, highRiskCount, mediumRiskCount, lowRiskCount, cpuUsage, memUsage);
        List<String> reasons = buildReasons(highRiskCount, mediumRiskCount, lowRiskCount, cpuUsage, memUsage,
                alertPenalty, cpuPenalty, memPenalty, burstPenalty, hasMetricData);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("score", score);
        state.put("level", level);
        state.put("label", label);
        state.put("description", description);
        state.put("reasons", reasons);
        state.put("cpuUsage", round1(cpuUsage));
        state.put("memUsage", round1(memUsage));
        state.put("riskSummary", riskSummary);
        state.put("activeAlertCount", activeAlertCount);
        state.put("highRiskCount", highRiskCount);
        state.put("mediumRiskCount", mediumRiskCount);
        state.put("lowRiskCount", lowRiskCount);
        state.put("normalCount", normalCount);
        state.put("totalLogsCount", recentInfos == null ? 0 : recentInfos.size());
        state.put("recentWindowMinutes", ALERT_WINDOW_MINUTES);
        return state;
    }

    private Map<String, Object> buildPendingState(int normalCount) {
        Map<String, Integer> riskSummary = new LinkedHashMap<>();
        riskSummary.put("高", 0);
        riskSummary.put("中", 0);
        riskSummary.put("低", 0);
        riskSummary.put("无", normalCount);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("score", 0);
        state.put("level", "warning");
        state.put("label", "待采样");
        state.put("description", "暂未获取到 CPU 与内存监控数据，请稍候等待首次采样。");
        state.put("reasons", List.of("当前服务器还没有最新监控数据，系统将继续采集 CPU 与内存状态。"));
        state.put("cpuUsage", 0.0);
        state.put("memUsage", 0.0);
        state.put("riskSummary", riskSummary);
        state.put("activeAlertCount", 0);
        state.put("highRiskCount", 0);
        state.put("mediumRiskCount", 0);
        state.put("lowRiskCount", 0);
        state.put("normalCount", normalCount);
        state.put("totalLogsCount", normalCount);
        state.put("recentWindowMinutes", ALERT_WINDOW_MINUTES);
        return state;
    }

    private Map<String, Integer> summarizeRiskLevels(List<Information> infos) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("高", 0);
        summary.put("中", 0);
        summary.put("低", 0);
        summary.put("无", 0);

        if (infos == null) {
            return summary;
        }

        for (Information info : infos) {
            String level = InfoNormalizationUtils.normalizeRiskLevel(info == null ? null : info.getRiskLevel());
            summary.put(level, summary.get(level) + 1);
        }
        return summary;
    }

    private int resolveAlertPenalty(int highRiskCount, int mediumRiskCount, int lowRiskCount) {
        if (highRiskCount > 0) {
            return HIGH_ALERT_PENALTY;
        }
        if (mediumRiskCount > 0) {
            return MEDIUM_ALERT_PENALTY;
        }
        if (lowRiskCount > 0) {
            return LOW_ALERT_PENALTY;
        }
        return 0;
    }

    private int resolveBurstPenalty(double cpuUsage, double memUsage) {
        if (cpuUsage >= 95 && memUsage >= 95) {
            return DOUBLE_CRITICAL_BURST_PENALTY;
        }
        if (cpuUsage >= 95 || memUsage >= 95) {
            return SINGLE_CRITICAL_BURST_PENALTY;
        }
        if (cpuUsage >= 85 && memUsage >= 85) {
            return DOUBLE_HIGH_BURST_PENALTY;
        }
        return 0;
    }

    private String resolveLevel(int score) {
        if (score < ERROR_SCORE_THRESHOLD) {
            return "error";
        }
        if (score < WARNING_SCORE_THRESHOLD) {
            return "warning";
        }
        return "success";
    }

    private String resolveLabel(String level) {
        return switch (level) {
            case "error" -> "高风险";
            case "warning" -> "需关注";
            default -> "稳定";
        };
    }

    private String resolveDescription(String level,
                                      int highRiskCount,
                                      int mediumRiskCount,
                                      int lowRiskCount,
                                      double cpuUsage,
                                      double memUsage) {
        if (highRiskCount > 0) {
            return "最近一段时间内存在高风险告警，系统健康度已进入红色区间。";
        }
        if ("error".equals(level)) {
            return "CPU 或内存占用已处于高压区，建议立即排查热点进程与系统负载。";
        }
        if (mediumRiskCount > 0) {
            return "检测到中风险告警，建议尽快排查对应服务和异常事件。";
        }
        if (lowRiskCount > 0) {
            return "检测到低风险告警，建议结合资源占用情况持续观察。";
        }
        if (cpuUsage >= 70 || memUsage >= 70) {
            return "当前资源占用偏高，建议关注热点进程、容量与服务响应。";
        }
        return "暂无高风险信号，CPU 与内存占用保持在可控区间。";
    }

    private List<String> buildReasons(int highRiskCount,
                                      int mediumRiskCount,
                                      int lowRiskCount,
                                      double cpuUsage,
                                      double memUsage,
                                      int alertPenalty,
                                      int cpuPenalty,
                                      int memPenalty,
                                      int burstPenalty,
                                      boolean hasMetricData) {
        List<String> reasons = new ArrayList<>();

        if (highRiskCount > 0) {
            reasons.add("最近 " + ALERT_WINDOW_MINUTES + " 分钟内存在 " + highRiskCount + " 条高风险告警，直接扣 "
                    + HIGH_ALERT_PENALTY + " 分");
        } else if (mediumRiskCount > 0) {
            reasons.add("最近 " + ALERT_WINDOW_MINUTES + " 分钟内存在 " + mediumRiskCount + " 条中风险告警，扣 "
                    + MEDIUM_ALERT_PENALTY + " 分");
        } else if (lowRiskCount > 0) {
            reasons.add("最近 " + ALERT_WINDOW_MINUTES + " 分钟内存在 " + lowRiskCount + " 条低风险告警，扣 "
                    + LOW_ALERT_PENALTY + " 分");
        } else if (alertPenalty == 0) {
            reasons.add("最近 " + ALERT_WINDOW_MINUTES + " 分钟内没有新增风险告警");
        }

        if (hasMetricData) {
            reasons.add("CPU 当前占用 " + round1(cpuUsage) + "%，扣 " + cpuPenalty + " 分");
            reasons.add("内存当前占用 " + round1(memUsage) + "%，扣 " + memPenalty + " 分");
            if (burstPenalty > 0) {
                reasons.add("资源占用进入高压区，额外扣 " + burstPenalty + " 分");
            }
        } else {
            reasons.add("当前还没有最新 CPU / 内存采样数据，资源项暂按 0 分计算");
        }

        return reasons;
    }

    private boolean hasMetricData(Map<String, Object> currentInfo, List<MetricDTO> history) {
        if (extractUsage(currentInfo == null ? null : currentInfo.get("cpuUsage")) != null) {
            return true;
        }
        if (extractUsage(currentInfo == null ? null : currentInfo.get("memUsage")) != null) {
            return true;
        }
        return history != null && !history.isEmpty();
    }

    private double resolveUsage(Map<String, Object> currentInfo, List<MetricDTO> history, boolean cpu) {
        String key = cpu ? "cpuUsage" : "memUsage";
        Double current = extractUsage(currentInfo == null ? null : currentInfo.get(key));
        if (current != null) {
            return current;
        }

        if (history != null && !history.isEmpty()) {
            MetricDTO latest = history.get(history.size() - 1);
            Double fromHistory = extractUsage(cpu ? latest.getCpuUsage() : latest.getMemUsage());
            if (fromHistory != null) {
                return fromHistory;
            }
        }

        return 0.0;
    }

    private Double extractUsage(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(String.valueOf(rawValue));
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return round1(clamp(value, 0, 100));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    private int roundToInt(double value) {
        return (int) Math.round(value);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

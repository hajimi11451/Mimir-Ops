package com.example.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.dto.MetricDTO;
import com.example.backend.entity.ComponentConfig;
import com.example.backend.entity.Information;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.ComponentConfigMapper;
import com.example.backend.mapper.InformationMapper;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.utils.InfoNormalizationUtils;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MonitorService {

    private static final String MONITOR_FAILURE_COMPONENT = "系统监控";
    private static final String MONITOR_FAILURE_SUMMARY = "SSH服务器网络异常";
    private static final String MONITOR_FAILURE_ANALYSIS = "采集不到 CPU 和内存数据，请检查 SSH 连接、服务器网络状态以及目标主机是否可达。";
    private static final String SYSTEM_MONITOR_CONFIG_KEY = "system_monitor";
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    @Autowired
    private ComponentConfigMapper componentConfigMapper;

    @Autowired
    private UserLoginMapper userLoginMapper;

    @Autowired
    private InformationMapper informationMapper;

    @Value("${monitor.schedule.fixed-rate:60000}")
    private long monitorFixedRateMs;

    // IP -> History List
    private final Map<String, List<MetricDTO>> historyMap = new ConcurrentHashMap<>();
    
    // IP -> Current Info Map
    private final Map<String, Map<String, Object>> currentInfoMap = new ConcurrentHashMap<>();

    /**
     * 定时任务：每隔一段时间执行一次 (由 monitor.schedule.fixed-rate 配置，默认 60000ms)
     * 遍历数据库中的服务器配置，远程采集 CPU 和内存使用率
     */
    @Scheduled(
            initialDelayString = "${monitor.schedule.fixed-rate:60000}",
            fixedRateString = "${monitor.schedule.fixed-rate:60000}"
    )
    public void collectMetrics() {
        // 1. 获取所有配置的服务器 (去重，取第一个配置)
        List<ComponentConfig> configs = componentConfigMapper.selectList(new QueryWrapper<ComponentConfig>()
                .select("DISTINCT server_ip, username, password")
                .eq("config_key", SYSTEM_MONITOR_CONFIG_KEY)
                .eq("is_enabled", 1));
        
        // 简单的去重逻辑 (按IP)
        Map<String, ComponentConfig> uniqueServers = new HashMap<>();
        for (ComponentConfig config : configs) {
            if (config.getServerIp() != null && !uniqueServers.containsKey(config.getServerIp())) {
                uniqueServers.put(config.getServerIp(), config);
            }
        }

        if (uniqueServers.isEmpty()) {
            System.out.println("没有配置需要监控的服务器");
            return;
        }

        // 2. 遍历采集
        for (ComponentConfig server : uniqueServers.values()) {
            collectServerMetrics(server);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void collectMetricsOnStartup() {
        System.out.println("应用启动完成，立即执行一次 CPU/内存采集");
        collectMetrics();
    }

    private void collectServerMetrics(ComponentConfig server) {
        String ip = server.getServerIp();
        String user = server.getUsername();
        String password = server.getPassword();
        
        // 如果没有用户名密码，无法监控
        if (user == null || password == null) {
            System.err.println("跳过服务器 " + ip + ": 缺少用户名或密码");
            return;
        }

        try {
            // SSH 连接并执行命令
            // 获取 CPU 使用率 (使用 vmstat 获取空闲率，然后用 100 减去)
            // vmstat 1 2 取第二行数据
            String cpuCmd = "vmstat 1 2 | tail -1 | awk '{print 100 - $15}'";
            String memCmd = "awk '/MemTotal/ {total=$2} /MemAvailable/ {avail=$2} END {if (total>0 && avail>=0) print (total-avail)/total*100}' /proc/meminfo";
            String uptimeCmd = "uptime -p";
            String osCmd = "cat /etc/os-release | grep PRETTY_NAME | cut -d= -f2 | tr -d '\"'";
            String totalMemCmd = "free -h | grep Mem | awk '{print $2}'";

            double cpuUsage = parseMetricValue(sshExec(ip, user, password, cpuCmd), "CPU");
            double memUsage = parseMetricValue(sshExec(ip, user, password, memCmd), "内存");
            String upTime = sshExec(ip, user, password, uptimeCmd).trim();
            String os = sshExec(ip, user, password, osCmd).trim();
            String totalMem = sshExec(ip, user, password, totalMemCmd).trim();

            // 更新历史数据
            String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            MetricDTO metric = new MetricDTO(
                    currentTime,
                    Math.round(cpuUsage * 10.0) / 10.0,
                    Math.round(memUsage * 10.0) / 10.0
            );

            historyMap.computeIfAbsent(ip, k -> new CopyOnWriteArrayList<>()).add(metric);
            List<MetricDTO> history = historyMap.get(ip);
            if (history.size() > 60) {
                history.remove(0);
            }

            // 更新实时信息
            Map<String, Object> info = new HashMap<>();
            info.put("os", os.isEmpty() ? "Linux" : os);
            info.put("upTime", upTime);
            info.put("cpuUsage", metric.getCpuUsage());
            info.put("memUsage", metric.getMemUsage());
            info.put("totalMemory", totalMem);
            info.put("processor", "Remote Server"); // 简化，暂不获取详细CPU型号
            
            currentInfoMap.put(ip, info);

            System.out.println("已采集服务器 " + ip + " 数据: " + metric);

        } catch (Exception e) {
            System.err.println("采集服务器 " + ip + " 失败: " + e.getMessage());
            createMonitorFailureInfo(ip, e.getMessage());
        }
    }

    private String sshExec(String host, String user, String password, String command) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(10000); // 10秒超时
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    private double parseMetricValue(String str, String metricName) {
        String raw = str == null ? "" : str.trim();
        if (isNetworkFailure(raw)) {
            throw new RuntimeException(MONITOR_FAILURE_SUMMARY);
        }

        try {
            return Double.parseDouble(raw);
        } catch (Exception e) {
            Matcher matcher = NUMERIC_PATTERN.matcher(raw.replace(",", "."));
            if (matcher.find()) {
                return Double.parseDouble(matcher.group());
            }
            throw new RuntimeException(metricName + "数据采集失败");
        }
    }

    private boolean isNetworkFailure(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        return raw.startsWith("SSH Error:")
                || raw.contains("Connection timed out")
                || raw.contains("Connection refused")
                || raw.contains("No route to host")
                || raw.contains("Read timed out")
                || raw.contains("Auth fail")
                || raw.contains("timeout")
                || raw.contains("refused");
    }

    private void createMonitorFailureInfo(String serverIp, String detailMessage) {
        List<ComponentConfig> configs = componentConfigMapper.selectList(
                new QueryWrapper<ComponentConfig>()
                        .select("DISTINCT user_id, server_ip")
                        .eq("config_key", SYSTEM_MONITOR_CONFIG_KEY)
                        .eq("is_enabled", 1)
                        .eq("server_ip", serverIp)
        );

        if (configs == null || configs.isEmpty()) {
            return;
        }

        Set<Long> userIds = new LinkedHashSet<>();
        for (ComponentConfig config : configs) {
            if (config != null && config.getUserId() != null) {
                userIds.add(config.getUserId());
            }
        }

        String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel("高");
        String normalizedRawLog = InfoNormalizationUtils.normalizeText(detailMessage, MONITOR_FAILURE_SUMMARY);

        for (Long userId : userIds) {
            Information info = new Information();
            info.setUserId(userId);
            info.setServerIp(serverIp);
            info.setComponent(MONITOR_FAILURE_COMPONENT);
            info.setErrorSummary(MONITOR_FAILURE_SUMMARY);
            info.setAnalysisResult(MONITOR_FAILURE_ANALYSIS);
            info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions(
                    "检查 SSH 网络连通性；确认服务器在线；检查 SSH 端口、账号密码和防火墙配置。",
                    normalizedRiskLevel
            ));
            info.setRawLog(normalizedRawLog);
            info.setRiskLevel(normalizedRiskLevel);
            info.setCreatedAt(LocalDateTime.now());
            informationMapper.insert(info);
        }
    }

    /**
     * 获取指定IP的历史趋势数据
     */
    public List<MetricDTO> getHistory(String ip) {
        return historyMap.getOrDefault(ip, Collections.emptyList());
    }

    /**
     * 获取指定IP的当前详细硬件信息
     */
    public Map<String, Object> getRealtime(String ip) {
        return currentInfoMap.getOrDefault(ip, Collections.emptyMap());
    }
    
    /**
     * 获取所有已监控的IP列表
     */
    public List<String> getMonitoredIps() {
        return new ArrayList<>(currentInfoMap.keySet());
    }

    public List<String> getMonitoredIpsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }
        UserLogin user = userLoginMapper.selectOne(
                new QueryWrapper<UserLogin>().eq("username", username)
        );
        if (user == null) {
            return Collections.emptyList();
        }

        List<ComponentConfig> ownedConfigs = componentConfigMapper.selectList(
                new QueryWrapper<ComponentConfig>()
                        .select("DISTINCT server_ip")
                        .eq("config_key", SYSTEM_MONITOR_CONFIG_KEY)
                        .eq("user_id", user.getId())
        );
        Set<String> ownedIps = new LinkedHashSet<>();
        for (ComponentConfig cfg : ownedConfigs) {
            if (cfg.getServerIp() != null && !cfg.getServerIp().isBlank()) {
                ownedIps.add(cfg.getServerIp());
            }
        }
        return new ArrayList<>(ownedIps);
    }

    public boolean isServerMonitorEnabledByUsername(String username, String serverIp) {
        if (username == null || username.isBlank() || serverIp == null || serverIp.isBlank()) {
            return false;
        }

        UserLogin user = userLoginMapper.selectOne(
                new QueryWrapper<UserLogin>().eq("username", username)
        );
        if (user == null || user.getId() == null) {
            return false;
        }

        ComponentConfig config = componentConfigMapper.selectOne(
                new QueryWrapper<ComponentConfig>()
                        .eq("user_id", user.getId())
                        .eq("server_ip", serverIp.trim())
                        .eq("config_key", SYSTEM_MONITOR_CONFIG_KEY)
                        .orderByDesc("updated_at")
                        .last("LIMIT 1")
        );
        return config != null && !Integer.valueOf(0).equals(config.getIsEnabled());
    }

    /**
     * 提供给 Agent 的监控数据读取入口：
     * - serverIp: 目标服务器 IP
     * - timeRange: 例如 30m / 1h / 2h
     * 返回值是可直接序列化为 JSON 的结构。
     */
    public Map<String, Object> getMetrics(String serverIp, String timeRange) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverIp", serverIp);
        result.put("timeRange", timeRange);
        result.put("sampleIntervalMs", monitorFixedRateMs);
        result.put("current", getRealtime(serverIp));

        List<MetricDTO> fullHistory = historyMap.getOrDefault(serverIp, Collections.emptyList());
        int points = calcPointsByRange(timeRange, monitorFixedRateMs);
        int startIdx = Math.max(0, fullHistory.size() - points);
        List<MetricDTO> sliced = new ArrayList<>(fullHistory.subList(startIdx, fullHistory.size()));

        result.put("history", sliced);
        result.put("historyPoints", sliced.size());
        result.put("summary", buildSummary(sliced));
        return result;
    }

    /**
     * 当历史为空时，允许 Agent 触发一次即时采样。
     * 采样成功会写入 historyMap / currentInfoMap，便于后续 getMetrics 直接拿到数据。
     */
    public Map<String, Object> sampleMetricsOnce(String serverIp, String username, String password) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("serverIp", serverIp);

        if (serverIp == null || serverIp.isBlank() || username == null || username.isBlank() || password == null || password.isBlank()) {
            sample.put("success", false);
            sample.put("error", "serverIp/username/password 不能为空");
            return sample;
        }

        try {
            String cpuCmd = "vmstat 1 2 | tail -1 | awk '{print 100 - $15}'";
            String memCmd = "awk '/MemTotal/ {total=$2} /MemAvailable/ {avail=$2} END {if (total>0 && avail>=0) print (total-avail)/total*100}' /proc/meminfo";
            String uptimeCmd = "uptime -p";
            String osCmd = "cat /etc/os-release | grep PRETTY_NAME | cut -d= -f2 | tr -d '\"'";
            String totalMemCmd = "free -h | grep Mem | awk '{print $2}'";

            double cpuUsage = parseMetricValue(sshExec(serverIp, username, password, cpuCmd), "CPU");
            double memUsage = parseMetricValue(sshExec(serverIp, username, password, memCmd), "内存");
            String upTime = sshExec(serverIp, username, password, uptimeCmd).trim();
            String os = sshExec(serverIp, username, password, osCmd).trim();
            String totalMem = sshExec(serverIp, username, password, totalMemCmd).trim();

            String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            MetricDTO metric = new MetricDTO(
                    currentTime,
                    Math.round(cpuUsage * 10.0) / 10.0,
                    Math.round(memUsage * 10.0) / 10.0
            );

            historyMap.computeIfAbsent(serverIp, k -> new CopyOnWriteArrayList<>()).add(metric);
            List<MetricDTO> history = historyMap.get(serverIp);
            if (history.size() > 60) {
                history.remove(0);
            }

            Map<String, Object> info = new HashMap<>();
            info.put("os", os.isEmpty() ? "Linux" : os);
            info.put("upTime", upTime);
            info.put("cpuUsage", metric.getCpuUsage());
            info.put("memUsage", metric.getMemUsage());
            info.put("totalMemory", totalMem);
            info.put("processor", "Remote Server");
            currentInfoMap.put(serverIp, info);

            sample.put("success", true);
            sample.put("metric", metric);
            sample.put("current", info);
            return sample;
        } catch (Exception e) {
            sample.put("success", false);
            sample.put("error", e.getMessage());
            return sample;
        }
    }

    public void clearServerCache(String serverIp) {
        if (serverIp == null || serverIp.isBlank()) {
            return;
        }
        historyMap.remove(serverIp.trim());
        currentInfoMap.remove(serverIp.trim());
    }

    private int calcPointsByRange(String timeRange, long intervalMs) {
        if (intervalMs <= 0) {
            intervalMs = 60000;
        }
        if (timeRange == null || timeRange.isBlank()) {
            return 30;
        }

        String s = timeRange.trim().toLowerCase(Locale.ROOT);
        try {
            long minutes;
            if (s.endsWith("m")) {
                minutes = Long.parseLong(s.substring(0, s.length() - 1));
            } else if (s.endsWith("h")) {
                minutes = Long.parseLong(s.substring(0, s.length() - 1)) * 60;
            } else {
                minutes = 30;
            }
            long totalMs = minutes * 60_000L;
            return (int) Math.max(1, Math.ceil((double) totalMs / intervalMs));
        } catch (Exception ignored) {
            return 30;
        }
    }

    private Map<String, Object> buildSummary(List<MetricDTO> metrics) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (metrics == null || metrics.isEmpty()) {
            summary.put("avgCpu", 0.0);
            summary.put("maxCpu", 0.0);
            summary.put("avgMem", 0.0);
            summary.put("maxMem", 0.0);
            return summary;
        }

        double sumCpu = 0.0;
        double sumMem = 0.0;
        double maxCpu = 0.0;
        double maxMem = 0.0;
        for (MetricDTO m : metrics) {
            double cpu = m.getCpuUsage() == null ? 0.0 : m.getCpuUsage();
            double mem = m.getMemUsage() == null ? 0.0 : m.getMemUsage();
            sumCpu += cpu;
            sumMem += mem;
            maxCpu = Math.max(maxCpu, cpu);
            maxMem = Math.max(maxMem, mem);
        }
        summary.put("avgCpu", Math.round((sumCpu / metrics.size()) * 10.0) / 10.0);
        summary.put("maxCpu", Math.round(maxCpu * 10.0) / 10.0);
        summary.put("avgMem", Math.round((sumMem / metrics.size()) * 10.0) / 10.0);
        summary.put("maxMem", Math.round(maxMem * 10.0) / 10.0);
        return summary;
    }
}

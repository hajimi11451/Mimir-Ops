package com.example.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.dto.MetricDTO;
import com.example.backend.entity.ComponentConfig;
import com.example.backend.mapper.ComponentConfigMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class MonitorService {

    @Autowired
    private ComponentConfigMapper componentConfigMapper;

    // IP -> History List
    private final Map<String, List<MetricDTO>> historyMap = new ConcurrentHashMap<>();
    
    // IP -> Current Info Map
    private final Map<String, Map<String, Object>> currentInfoMap = new ConcurrentHashMap<>();

    /**
     * 定时任务：每隔一段时间执行一次 (由 monitor.schedule.fixed-rate 配置，默认 60000ms)
     * 遍历数据库中的服务器配置，远程采集 CPU 和内存使用率
     */
    @Scheduled(fixedRateString = "${monitor.schedule.fixed-rate:60000}")
    public void collectMetrics() {
        // 1. 获取所有配置的服务器 (去重，取第一个配置)
        List<ComponentConfig> configs = componentConfigMapper.selectList(new QueryWrapper<ComponentConfig>()
                .select("DISTINCT server_ip, username, password"));
        
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
            String memCmd = "free -m | grep Mem | awk '{print $3/$2 * 100}'";
            String uptimeCmd = "uptime -p";
            String osCmd = "cat /etc/os-release | grep PRETTY_NAME | cut -d= -f2 | tr -d '\"'";
            String totalMemCmd = "free -h | grep Mem | awk '{print $2}'";

            double cpuUsage = parseDouble(sshExec(ip, user, password, cpuCmd));
            double memUsage = parseDouble(sshExec(ip, user, password, memCmd));
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
            // 可以记录一个空的或错误状态，这里简单跳过
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

    private double parseDouble(String str) {
        try {
            return Double.parseDouble(str.trim());
        } catch (Exception e) {
            return 0.0;
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
}

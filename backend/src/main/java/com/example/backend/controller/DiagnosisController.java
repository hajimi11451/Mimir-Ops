package com.example.backend.controller;

import com.example.backend.service.DiagnosisService;
import com.example.backend.service.ServerService;
import com.example.backend.utils.AiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/diagnosis")
public class DiagnosisController {

    @Autowired
    private DiagnosisService diagnosisService;

    @Autowired
    private AiUtils aiUtils;

    @Autowired
    private ServerService serverService;

    
    @GetMapping("/logPath")
    public ResponseEntity<?> getLogPath(@RequestParam String serverIp, 
                                      @RequestParam String component,
                                      @RequestParam(required = false) String username,
                                      @RequestParam(required = false) String password,
                                      @RequestParam(required = false, defaultValue = "false") boolean useSudo) {
        try {
            String path = diagnosisService.getLogPath(serverIp, component, username, password, useSudo);

            Map<String, Object> data = new HashMap<>();
            data.put("path", path);
            data.put("isVerified", true);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 400);
            response.put("msg", e.getMessage());
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("自动发现日志路径时发生未知错误", e);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    
    @PostMapping("/config/add")
    public ResponseEntity<?> addConfig(@RequestBody com.example.backend.entity.ComponentConfig config) {
        try {
            diagnosisService.addConfig(config);
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("msg", "配置添加成功");
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 400);
            response.put("msg", e.getMessage());
            response.put("data", null);
            
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("添加配置时发生未知错误", e);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "服务器内部错误: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/server-monitor/add")
    public ResponseEntity<?> addServerMonitor(@RequestBody com.example.backend.entity.ComponentConfig config) {
        try {
            Map<String, Object> data = diagnosisService.addServerMonitor(config);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("msg", "服务器监控添加成功");
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 400);
            response.put("msg", e.getMessage());
            response.put("data", null);

            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("添加服务器监控时发生未知错误", e);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/server-monitor/stop")
    public ResponseEntity<?> stopServerMonitor(@RequestBody Map<String, String> request) {
        try {
            String serverIp = request.get("serverIp");
            String username = request.get("appUsername");
            if (username == null || username.isBlank()) {
                username = request.get("username");
            }

            diagnosisService.setServerMonitorEnabled(username, serverIp, false);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("msg", "服务器监控已暂停");
            response.put("data", Map.of("serverIp", serverIp));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 400);
            response.put("msg", e.getMessage());
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("停止服务器监控时发生未知错误", e);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/server-monitor/resume")
    public ResponseEntity<?> resumeServerMonitor(@RequestBody Map<String, String> request) {
        try {
            String serverIp = request.get("serverIp");
            String username = request.get("appUsername");
            if (username == null || username.isBlank()) {
                username = request.get("username");
            }

            diagnosisService.setServerMonitorEnabled(username, serverIp, true);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("msg", "服务器监控已恢复");
            response.put("data", Map.of("serverIp", serverIp));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 400);
            response.put("msg", e.getMessage());
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("恢复服务器监控时发生未知错误", e);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/config/list")
    public ResponseEntity<?> listConfigs(@RequestParam(required = false) String username) {
        List<com.example.backend.entity.ComponentConfig> list = diagnosisService.listConfigs(username);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", list);
        
        return ResponseEntity.ok(response);
    }


    @PostMapping("/config/delete")
    public ResponseEntity<?> deleteConfig(@RequestBody Map<String, Object> request) {
        Long id = request.get("id") == null ? null : Long.valueOf(String.valueOf(request.get("id")));
        String username = request.get("username") == null ? null : String.valueOf(request.get("username"));
        if (id != null) {
            diagnosisService.deleteConfig(id, username);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "删除成功");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/config/status")
    public ResponseEntity<?> updateConfigStatus(@RequestBody Map<String, Object> request) {
        try {
            Long id = request.get("id") == null ? null : Long.valueOf(String.valueOf(request.get("id")));
            String username = request.get("appUsername") == null ? null : String.valueOf(request.get("appUsername"));
            if (username == null || username.isBlank()) {
                username = request.get("username") == null ? null : String.valueOf(request.get("username"));
            }
            Integer isEnabled = request.get("isEnabled") == null ? null : Integer.valueOf(String.valueOf(request.get("isEnabled")));

            diagnosisService.updateConfigStatus(id, username, isEnabled);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("msg", Integer.valueOf(0).equals(isEnabled) ? "监控已暂停" : "监控已恢复");
            response.put("data", Map.of("id", id, "isEnabled", isEnabled));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 400);
            response.put("msg", e.getMessage());
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("更新监控状态时发生未知错误", e);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }


    @PostMapping("/execute")
    public ResponseEntity<?> executeDiagnosis(@RequestBody Map<String, Object> request) {
        try {
            String serverIp = request.get("serverIp") == null ? null : String.valueOf(request.get("serverIp"));
            String component = request.get("component") == null ? null : String.valueOf(request.get("component"));
            String logPath = request.get("logPath") == null ? null : String.valueOf(request.get("logPath"));
            String username = request.get("username") == null ? null : String.valueOf(request.get("username"));
            String password = request.get("password") == null ? null : String.valueOf(request.get("password"));
            boolean useSudo = Boolean.parseBoolean(String.valueOf(request.getOrDefault("useSudo", false)));

            Map<String, Object> diagnosisResult = diagnosisService.executeDiagnosis(serverIp, component, logPath, username, password, useSudo);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("data", diagnosisResult);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 400);
            response.put("msg", e.getMessage());
            response.put("data", null);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("执行诊断时发生未知错误", e);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }


    @PostMapping("/ai/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        String answer = aiUtils.chatWithOpsAssistant(query);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", answer);

        return ResponseEntity.ok(response);
    }


    @PostMapping("/ai/analyze")
    public ResponseEntity<?> analyzeLogOnly(@RequestBody Map<String, String> request) {
        String logContent = request.get("logContent");
        if (logContent == null) {
            logContent = "";
        }
        String result = aiUtils.analyzeLog(logContent);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", result);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/server/connect")
    public ResponseEntity<?> connectServer(@RequestBody Map<String, String> request) {
        String ip = request.get("ip");
        String user = request.get("user");
        String password = request.get("password");

        List<String> components = serverService.connectAndDetect(ip, user, password);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "连接成功");
        response.put("data", components);

        return ResponseEntity.ok(response);
    }
}

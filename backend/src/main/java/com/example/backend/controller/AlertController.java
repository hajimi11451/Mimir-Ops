package com.example.backend.controller;

import com.example.backend.service.AlertMailService;
import com.example.backend.service.AlertRecipientService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/alert")
public class AlertController {

    private final AlertRecipientService alertRecipientService;
    private final AlertMailService alertMailService;

    public AlertController(AlertRecipientService alertRecipientService, AlertMailService alertMailService) {
        this.alertRecipientService = alertRecipientService;
        this.alertMailService = alertMailService;
    }

    @GetMapping("/contact")
    public ResponseEntity<?> getContact(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", Map.of("email", alertRecipientService.getRecipientByUsername(username)));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/contact")
    public ResponseEntity<?> saveContact(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String email = request.get("email");

        String saved = alertRecipientService.updateRecipient(username, email);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", StringUtils.hasText(saved) ? "紧急联系人邮箱已保存" : "紧急联系人邮箱已清空");
        response.put("data", Map.of("email", saved));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test")
    public ResponseEntity<?> sendTestMail(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String email = alertRecipientService.resolveEffectiveRecipient(username, request.get("email"));
        if (!StringUtils.hasText(email)) {
            throw new RuntimeException("请先保存紧急联系人邮箱，或在请求中传入 email");
        }

        alertMailService.sendTestMail(email, username);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "测试邮件已发送");
        response.put("data", Map.of("email", email));
        return ResponseEntity.ok(response);
    }
}

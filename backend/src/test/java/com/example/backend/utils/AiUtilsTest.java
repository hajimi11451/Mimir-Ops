package com.example.backend.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AiUtilsTest {

    @Autowired
    private AiUtils aiUtils;

    @Test
    public void testAnalyzeLogWithError() {
        // 模拟 50 行包含错误的日志
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            if (i == 25) {
                sb.append(String.format("%d: 2026-01-30 10:%02d:00 [ERROR] com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure\n", i, i % 60));
            } else if (i == 40) {
                sb.append(String.format("%d: 2026-01-30 10:%02d:00 [ERROR] java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"str\" is null\n", i, i % 60));
            } else {
                sb.append(String.format("%d: 2026-01-30 10:%02d:00 [INFO] Processing request id: %d\n", i, i % 60, i));
            }
        }
        String errorLog = sb.toString();
        
        System.out.println("--- 测试 50 行混合日志 (包含错误) ---");
        long startTime = System.currentTimeMillis();
        String result = aiUtils.analyzeLog(errorLog);
        long endTime = System.currentTimeMillis();
        
        System.out.println("分析结果:\n" + result);
        System.out.println("处理耗时: " + (endTime - startTime) + " ms");
    }

    @Test
    public void testAnalyzeLogWithNoIssue() {
        // 模拟 50 行正常的日志
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            sb.append(String.format("%d: 2026-01-30 11:%02d:00 [INFO] Task-%d completed successfully in %dms\n", i, i % 60, i, (int)(Math.random() * 100)));
        }
        String normalLog = sb.toString();
        
        System.out.println("--- 测试 50 行正常日志 ---");
        long startTime = System.currentTimeMillis();
        String result = aiUtils.analyzeLog(normalLog);
        long endTime = System.currentTimeMillis();
        
        System.out.println("分析结果: " + result);
        System.out.println("处理耗时: " + (endTime - startTime) + " ms");
    }
}

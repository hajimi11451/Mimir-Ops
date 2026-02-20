package com.example.backend.dto;

import lombok.Data;

@Data
public class MetricDTO {
    // 字符串 (HH:mm)
    private String time;
    // 双精度浮点 (例如 12.5)
    private Double cpuUsage;
    // 双精度浮点 (例如 45.8)
    private Double memUsage;

    public MetricDTO(String time, Double cpuUsage, Double memUsage) {
        this.time = time;
        this.cpuUsage = cpuUsage;
        this.memUsage = memUsage;
    }
}

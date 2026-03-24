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
    // 网卡接收速率（Byte/s）
    private Double netRxBytesPerSec;
    // 网卡发送速率（Byte/s）
    private Double netTxBytesPerSec;
    // 磁盘读取速率（Byte/s）
    private Double diskReadBytesPerSec;
    // 磁盘写入速率（Byte/s）
    private Double diskWriteBytesPerSec;

    public MetricDTO(
            String time,
            Double cpuUsage,
            Double memUsage,
            Double netRxBytesPerSec,
            Double netTxBytesPerSec,
            Double diskReadBytesPerSec,
            Double diskWriteBytesPerSec
    ) {
        this.time = time;
        this.cpuUsage = cpuUsage;
        this.memUsage = memUsage;
        this.netRxBytesPerSec = netRxBytesPerSec;
        this.netTxBytesPerSec = netTxBytesPerSec;
        this.diskReadBytesPerSec = diskReadBytesPerSec;
        this.diskWriteBytesPerSec = diskWriteBytesPerSec;
    }
}

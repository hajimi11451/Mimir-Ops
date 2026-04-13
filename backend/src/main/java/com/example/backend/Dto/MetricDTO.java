package com.example.backend.dto;

import lombok.Data;

@Data
public class MetricDTO {
    private String time;
    private Double cpuUsage;
    private Double memUsage;
    //网卡接收速率
    private Double netRxBytesPerSec;
    //网卡发送速率
    private Double netTxBytesPerSec;
    //磁盘读取速率
    private Double diskReadBytesPerSec;
    //磁盘写入速率
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

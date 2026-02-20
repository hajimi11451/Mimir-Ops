package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("information")
public class Information {
    @TableId(type = IdType.AUTO)
    //主键id
    private Long id;
    //关联用户ID
    private Long userId;
    //服务器IP
    private String serverIp;
    //组件名称
    private String component;
    //问题摘要
    private String errorSummary;
    // AI 分析结果（遇到的问题描述）
    private String analysisResult;
    // 建议处理方式（与问题分开展示）
    private String suggestedActions;
    // 日志原始内容
    private String rawLog;
    //风险等级
    private String riskLevel;
    //发生时间
    private LocalDateTime createdAt;
}

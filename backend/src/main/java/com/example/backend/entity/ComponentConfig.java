package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("componentconfig")
public class ComponentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String serverIp;
    //服务器用户名
    private String username;
    //服务器密码
    private String password;
    //sudo权限
    private Boolean useSudo;
    //前端登录用户名（不入库）
    @TableField(exist = false)
    private String appUsername;

    @TableField(exist = false)
    private Boolean cpuEnabled;

    @TableField(exist = false)
    private Boolean memEnabled;

    @TableField(exist = false)
    private Boolean netRxEnabled;

    @TableField(exist = false)
    private Boolean netTxEnabled;

    @TableField(exist = false)
    private Boolean diskReadEnabled;

    @TableField(exist = false)
    private Boolean diskWriteEnabled;

    private String component;

    private String configKey;

    private String configValue;

    private Integer isVerified;

    private Integer isEnabled;

    private LocalDateTime updatedAt;
}

package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("componentConfig")
public class ComponentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;

    private String serverIp;

    /**
     * 服务器登录用户名
     */
    private String username;

    /**
     * 服务器登录密码
     */
    private String password;
    
    private String component;
    
    /**
     * 配置项 (固定为 error_log_path)
     */
    private String configKey;
    
    /**
     * 路径值
     */
    private String configValue;
    
    /**
     * 是否验证通过 (0=未验证, 1=已验证)
     */
    private Integer isVerified;
    
    private LocalDateTime updatedAt;
}

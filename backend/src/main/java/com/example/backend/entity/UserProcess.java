package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("userprocess")
public class UserProcess {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String serverIp;

    private String component;

    private String problemLog;

    private String processMethod;

    private LocalDateTime processTime;
}

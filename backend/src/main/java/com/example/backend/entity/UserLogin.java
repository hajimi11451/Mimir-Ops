package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("userLogin")
public class UserLogin {
    @TableId(type = IdType.AUTO)
    //主键ID
    private Long id;
    //用户名称
    private String username;
    //密码
    private String password;
    //账户创建时间
    private LocalDateTime createdAt;
}

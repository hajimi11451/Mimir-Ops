package com.example.backend.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("userProcess")
public class UserProcess {
    @TableId(type = IdType.AUTO)
    //主键
    private Long id;
    //用户ID
    private Long userId;
    //服务器IP
    private String serverIp;
    //组件名称
    private String component;
    //选择AI处理方式
    private String processMethod;
    //处理时间
    private LocalDateTime processTime;

}

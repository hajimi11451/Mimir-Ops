package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.User;
import com.example.backend.mapper.UserMapper;
import com.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    //登录
    @Override
    public String login(Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        queryWrapper.eq("password", password);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            return "登录失败";
        }
        return "登录成功";
    }
    //注册
    @Override
    public String register(Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = userMapper.selectOne(queryWrapper);
        if (user != null) {
            return "注册失败";
        }
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(password);
        userMapper.insert(newUser);
        return "注册成功";
    }

}

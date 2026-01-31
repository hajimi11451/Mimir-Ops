package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserLoginMapper userLoginMapper;

    //登录
    @Override
    public String login(Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        QueryWrapper<UserLogin> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        queryWrapper.eq("password", password);
        UserLogin user = userLoginMapper.selectOne(queryWrapper);
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
        QueryWrapper<UserLogin> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        UserLogin user = userLoginMapper.selectOne(queryWrapper);
        if (user != null) {
            return "注册失败";
        }
        UserLogin newUser = new UserLogin();
        newUser.setUsername(username);
        newUser.setPassword(password);
        userLoginMapper.insert(newUser);
        return "注册成功";
    }

}

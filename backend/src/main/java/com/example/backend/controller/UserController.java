package com.example.backend.controller;

import com.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;


    //注册
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {

        String res =userService.register(request);
        if (res.equals("注册成功")){
            //返回注册 成功的response
            return ResponseEntity.ok(res);
        }else {
            //返回登录失败的response
            return ResponseEntity.status(401).body(res);
        }

    }


    //登录
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> request) {
        String res=userService.login(request);
        if (res.equals("登录成功")){
            //返回登录成功的response
            return ResponseEntity.ok(res);
        }else {
            //返回登录失败的response
            return ResponseEntity.status(401).body(res);
        }
    }




}

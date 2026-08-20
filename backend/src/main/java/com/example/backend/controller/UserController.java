package com.example.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.security.JwtUtil;
import com.example.backend.security.SecurityUtils;
import com.example.backend.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private UserLoginMapper userLoginMapper;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Value("${jwt.cookie-name}")
    private String cookieName;


    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String res = userService.register(request);
        Map<String, Object> response = new HashMap<>();
        if (res.equals("注册成功")){
            response.put("code", 200);
            response.put("msg", res);
            return ResponseEntity.ok(response);
        } else if (res.equals("用户名已存在")) {
            response.put("code", 409);
            response.put("msg", res);
            return ResponseEntity.status(409).body(response);
        } else {
            response.put("code", 400);
            response.put("msg", res);
            return ResponseEntity.badRequest().body(response);
        }
    }


    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> request, 
                                   HttpServletResponse response) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            
            // 查询用户
            QueryWrapper<UserLogin> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            UserLogin user = userLoginMapper.selectOne(queryWrapper);
            
            if (user == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("code", 401);
                resp.put("msg", "用户名或密码错误");
                return ResponseEntity.status(401).body(resp);
            }
            
            // 验证密码
            if (!passwordEncoder.matches(password, user.getPassword())) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("code", 401);
                resp.put("msg", "用户名或密码错误");
                return ResponseEntity.status(401).body(resp);
            }
            
            // 生成Token
            String token = jwtUtil.generateToken(user.getUsername(), user.getId().intValue());
            
            // 设置HttpOnly Cookie
            Cookie cookie = new Cookie(cookieName, token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(24 * 60 * 60); // 24小时
            // 生产环境必须启用：
            // cookie.setSecure(true); // 仅HTTPS
            response.addCookie(cookie);
            
            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 200);
            resp.put("msg", "登录成功");
            resp.put("username", user.getUsername());
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 500);
            resp.put("msg", "系统错误：" + e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }
    
    
    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        // 清除Cookie
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("msg", "登出成功");
        return ResponseEntity.ok(resp);
    }
    
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser() {
        try {
            Integer userId = SecurityUtils.getCurrentUserId();
            if (userId == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("code", 401);
                resp.put("msg", "未登录");
                return ResponseEntity.status(401).body(resp);
            }
            
            UserLogin user = userLoginMapper.selectById(userId);
            if (user != null) {
                // 不返回密码
                user.setPassword(null);
                Map<String, Object> resp = new HashMap<>();
                resp.put("code", 200);
                resp.put("user", user);
                return ResponseEntity.ok(resp);
            } else {
                Map<String, Object> resp = new HashMap<>();
                resp.put("code", 404);
                resp.put("msg", "用户不存在");
                return ResponseEntity.status(404).body(resp);
            }
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 500);
            resp.put("msg", "系统错误");
            return ResponseEntity.status(500).body(resp);
        }
    }


}

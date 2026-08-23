# API防护实施指南

## 📋 概述

本文档提供API层面的安全防护方案，包括登录防暴力破解、API速率限制、CORS策略、统一异常处理等。

**优先级**：P1-P2（建议实施）  
**预计工作量**：2-3天  
**前置依赖**：认证授权机制（01文档）  
**影响范围**：所有API端点、过滤器、异常处理器

## 🎯 实施目标

- ✅ 登录失败次数限制，防止暴力破解
- ✅ API请求速率限制，防止DDoS攻击
- ✅ CORS策略收紧，防止跨域攻击
- ✅ 统一异常处理，避免信息泄露
- ✅ 请求日志记录，便于审计
- ✅ 敏感操作二次验证

## 🚨 当前问题

1. **无登录防护**：可以无限次尝试登录
2. **无速率限制**：单个IP可以疯狂调用API
3. **CORS过宽**：`WebSocketConfig`允许所有源
4. **错误暴露**：异常信息直接返回给前端
5. **无操作日志**：无法追溯谁做了什么

## 🏗️ 架构设计

### 防护层级

```
客户端请求
    ↓
[1] CORS过滤器 → 检查来源域名
    ↓
[2] 速率限制过滤器 → 检查请求频率
    ↓
[3] JWT认证过滤器 → 验证身份
    ↓
[4] 登录防护 → 检查失败次数
    ↓
[5] 业务逻辑
    ↓
[6] 统一异常处理 → 隐藏敏感信息
    ↓
返回响应
```

## 🔧 实施步骤

### 第一步：添加依赖

编辑 `backend/pom.xml`：

```xml
<dependencies>
    <!-- 现有依赖... -->
    
    <!-- Redis (用于速率限制和登录失败计数) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- Bucket4j (速率限制) -->
    <dependency>
        <groupId>com.github.vladimir-bukhtoyarov</groupId>
        <artifactId>bucket4j-core</artifactId>
        <version>8.7.0</version>
    </dependency>
    
    <!-- Caffeine (本地缓存，如果不用Redis可选) -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
    </dependency>
</dependencies>
```

### 第二步：配置Redis

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  # Redis配置
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 5000
      lettuce:
        pool:
          max-active: 8
          max-wait: -1ms
          max-idle: 8
          min-idle: 0

# 速率限制配置
rate-limit:
  enabled: true
  # 全局限制：每IP每分钟最多100次请求
  global:
    capacity: 100
    refill-tokens: 100
    refill-duration: 60
  # API限制：每用户每分钟最多30次
  api:
    capacity: 30
    refill-tokens: 30
    refill-duration: 60
  # 登录限制：每IP每小时最多10次失败
  login:
    max-attempts: 10
    lockout-duration: 3600

# CORS配置
cors:
  allowed-origins: ${CORS_ORIGINS:http://localhost:5173,http://localhost:3000}
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allowed-headers: "*"
  allow-credentials: true
  max-age: 3600
```

### 第三步：创建速率限制服务

创建 `backend/src/main/java/com/example/backend/service/RateLimitService.java`：

```java
package com.example.backend.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    // 本地缓存作为后备方案
    private final ConcurrentHashMap<String, Bucket> localCache = new ConcurrentHashMap<>();
    
    @Value("${rate-limit.enabled:true}")
    private boolean enabled;
    
    @Value("${rate-limit.global.capacity:100}")
    private int globalCapacity;
    
    @Value("${rate-limit.global.refill-duration:60}")
    private int globalRefillDuration;
    
    /**
     * 检查是否超过速率限制
     * @param key 限制键（如IP地址、用户ID）
     * @param capacity 容量
     * @param refillDuration 恢复时间（秒）
     * @return true=允许，false=拒绝
     */
    public boolean isAllowed(String key, int capacity, int refillDuration) {
        if (!enabled) {
            return true;
        }
        
        Bucket bucket = getBucket(key, capacity, refillDuration);
        return bucket.tryConsume(1);
    }
    
    /**
     * 检查全局速率限制（按IP）
     */
    public boolean isAllowedGlobal(String ip) {
        return isAllowed("global:" + ip, globalCapacity, globalRefillDuration);
    }
    
    /**
     * 检查API速率限制（按用户）
     */
    public boolean isAllowedForUser(Integer userId, int capacity, int refillDuration) {
        return isAllowed("user:" + userId, capacity, refillDuration);
    }
    
    /**
     * 获取或创建Bucket
     */
    private Bucket getBucket(String key, int capacity, int refillDuration) {
        return localCache.computeIfAbsent(key, k -> {
            Bandwidth bandwidth = Bandwidth.classic(
                capacity, 
                Refill.intervally(capacity, Duration.ofSeconds(refillDuration))
            );
            return Bucket.builder()
                .addLimit(bandwidth)
                .build();
        });
    }
    
    /**
     * 重置限制（管理员功能）
     */
    public void reset(String key) {
        localCache.remove(key);
    }
}
```

### 第四步：创建登录防护服务

创建 `backend/src/main/java/com/example/backend/service/LoginProtectionService.java`：

```java
package com.example.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class LoginProtectionService {
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    // 本地缓存作为后备方案
    private final ConcurrentHashMap<String, LoginAttempt> localCache = new ConcurrentHashMap<>();
    
    @Value("${rate-limit.login.max-attempts:10}")
    private int maxAttempts;
    
    @Value("${rate-limit.login.lockout-duration:3600}")
    private int lockoutDuration;
    
    /**
     * 记录登录失败
     * @param identifier IP地址或用户名
     * @return 失败次数
     */
    public int recordFailure(String identifier) {
        String key = "login:fail:" + identifier;
        
        if (redisTemplate != null) {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, lockoutDuration, TimeUnit.SECONDS);
            return count.intValue();
        } else {
            // 使用本地缓存
            LoginAttempt attempt = localCache.computeIfAbsent(key, k -> new LoginAttempt());
            attempt.increment();
            return attempt.getCount();
        }
    }
    
    /**
     * 检查是否被锁定
     */
    public boolean isLocked(String identifier) {
        String key = "login:fail:" + identifier;
        
        if (redisTemplate != null) {
            Integer count = (Integer) redisTemplate.opsForValue().get(key);
            return count != null && count >= maxAttempts;
        } else {
            LoginAttempt attempt = localCache.get(key);
            return attempt != null && attempt.getCount() >= maxAttempts;
        }
    }
    
    /**
     * 获取剩余失败次数
     */
    public int getRemainingAttempts(String identifier) {
        String key = "login:fail:" + identifier;
        
        if (redisTemplate != null) {
            Integer count = (Integer) redisTemplate.opsForValue().get(key);
            return count == null ? maxAttempts : Math.max(0, maxAttempts - count);
        } else {
            LoginAttempt attempt = localCache.get(key);
            int count = attempt == null ? 0 : attempt.getCount();
            return Math.max(0, maxAttempts - count);
        }
    }
    
    /**
     * 清除失败记录（登录成功后）
     */
    public void clearFailures(String identifier) {
        String key = "login:fail:" + identifier;
        
        if (redisTemplate != null) {
            redisTemplate.delete(key);
        } else {
            localCache.remove(key);
        }
    }
    
    /**
     * 本地缓存的登录尝试记录
     */
    private static class LoginAttempt {
        private int count = 0;
        private long firstAttempt = System.currentTimeMillis();
        
        public void increment() {
            count++;
        }
        
        public int getCount() {
            return count;
        }
    }
}
```

### 第五步：创建速率限制过滤器

创建 `backend/src/main/java/com/example/backend/filter/RateLimitFilter.java`：

```java
package com.example.backend.filter;

import com.example.backend.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    
    @Autowired
    private RateLimitService rateLimitService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) 
            throws ServletException, IOException {
        
        // 跳过静态资源
        String path = request.getRequestURI();
        if (path.startsWith("/static/") || path.endsWith(".js") || path.endsWith(".css")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 获取客户端IP
        String ip = getClientIp(request);
        
        // 检查全局速率限制
        if (!rateLimitService.isAllowedGlobal(ip)) {
            sendRateLimitError(response, "请求过于频繁，请稍后再试");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果有多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    /**
     * 返回速率限制错误
     */
    private void sendRateLimitError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429); // Too Many Requests
        response.setContentType("application/json;charset=UTF-8");
        
        Map<String, Object> error = Map.of(
            "success", false,
            "message", message,
            "code", "RATE_LIMIT_EXCEEDED"
        );
        
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
```

### 第六步：改造UserController添加登录防护

编辑 `backend/src/main/java/com/example/backend/controller/UserController.java`：

```java
@Autowired
private LoginProtectionService loginProtectionService;

@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody UserLogin loginRequest, 
                                HttpServletRequest request,
                                HttpServletResponse response) {
    try {
        String ip = getClientIp(request);
        
        // 检查是否被锁定
        if (loginProtectionService.isLocked(ip)) {
            return ResponseEntity.status(429).body(Map.of(
                "success", false,
                "message", "登录失败次数过多，账户已被临时锁定，请1小时后重试"
            ));
        }
        
        // 查询用户
        UserLogin user = userService.selectUserByUsername(loginRequest.getUsername());
        if (user == null) {
            loginProtectionService.recordFailure(ip);
            int remaining = loginProtectionService.getRemainingAttempts(ip);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "用户名或密码错误",
                "remainingAttempts", remaining
            ));
        }
        
        // 验证密码
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            loginProtectionService.recordFailure(ip);
            int remaining = loginProtectionService.getRemainingAttempts(ip);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "用户名或密码错误",
                "remainingAttempts", remaining
            ));
        }
        
        // 登录成功，清除失败记录
        loginProtectionService.clearFailures(ip);
        
        // 生成Token
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        
        // 设置Cookie
        Cookie cookie = new Cookie(cookieName, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);
        response.addCookie(cookie);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "登录成功",
            "username", user.getUsername()
        ));
    } catch (Exception e) {
        logger.error("登录失败", e);
        return ResponseEntity.status(500).body(Map.of(
            "success", false,
            "message", "系统错误"
        ));
    }
}

private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
        ip = request.getHeader("X-Real-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
        ip = request.getRemoteAddr();
    }
    if (ip != null && ip.contains(",")) {
        ip = ip.split(",")[0].trim();
    }
    return ip;
}
```

### 第七步：收紧CORS配置

编辑 `backend/src/main/java/com/example/backend/config/WebSocketConfig.java`：

```java
package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    private ServerWebSocketHandler serverWebSocketHandler;
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(serverWebSocketHandler, "/ws")
                .setAllowedOrigins(allowedOrigins.split(","))  // 限制允许的域名
                .withSockJS();
    }
}
```

创建全局CORS配置：

```java
package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;
    
    @Value("${cors.max-age:3600}")
    private Long maxAge;
    
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的域名
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        
        // 允许的HTTP方法
        config.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        
        // 允许的请求头
        config.addAllowedHeader("*");
        
        // 允许携带凭证（Cookie）
        config.setAllowCredentials(true);
        
        // 预检请求缓存时间
        config.setMaxAge(maxAge);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
```

### 第八步：统一异常处理

创建 `backend/src/main/java/com/example/backend/exception/GlobalExceptionHandler.java`：

```java
package com.example.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    
    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        logger.error("系统异常", e);
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", "系统错误，请稍后重试");
        
        // 开发环境返回详细错误
        if ("dev".equals(activeProfile)) {
            error.put("detail", e.getMessage());
            error.put("type", e.getClass().getSimpleName());
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("参数错误：{}", e.getMessage());
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", e.getMessage());
        
        return ResponseEntity.badRequest().body(error);
    }
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常：{}", e.getMessage());
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", e.getMessage());
        error.put("code", e.getCode());
        
        return ResponseEntity.status(e.getHttpStatus()).body(error);
    }
}
```

创建业务异常类：

```java
package com.example.backend.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus httpStatus;
    
    public BusinessException(String message) {
        this(message, "BUSINESS_ERROR", HttpStatus.BAD_REQUEST);
    }
    
    public BusinessException(String message, String code, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
    
    public String getCode() {
        return code;
    }
    
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
```

### 第九步：注册过滤器

编辑 `backend/src/main/java/com/example/backend/security/SecurityConfig.java`：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Autowired
    private RateLimitFilter rateLimitFilter;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/user/register", "/user/login").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated()
            )
            // 添加速率限制过滤器（最先执行）
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

## ✅ 测试验证

### 1. 速率限制测试

```bash
# 快速发送100次请求
for i in {1..100}; do
  curl -s http://localhost:8080/api/system/dashboard -b cookies.txt
done

# 第101次应该返回429错误
curl -i http://localhost:8080/api/system/dashboard -b cookies.txt
# HTTP/1.1 429 Too Many Requests
```

### 2. 登录防护测试

```bash
# 连续10次错误登录
for i in {1..10}; do
  curl -X POST http://localhost:8080/user/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"wrong"}'
  echo ""
done

# 第11次应该返回锁定错误
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"wrong"}'
```

### 3. CORS测试

```bash
# 正确的域名
curl -i http://localhost:8080/api/system/dashboard \
  -H "Origin: http://localhost:5173" \
  -b cookies.txt
# 应该返回 Access-Control-Allow-Origin: http://localhost:5173

# 错误的域名
curl -i http://localhost:8080/api/system/dashboard \
  -H "Origin: http://evil.com" \
  -b cookies.txt
# 应该没有 Access-Control-Allow-Origin 头
```

### 4. 异常处理测试

```bash
# 触发异常
curl http://localhost:8080/api/nonexistent -b cookies.txt
# 生产环境应该返回通用错误信息，不暴露堆栈
```

## 📊 性能影响评估

| 操作 | 额外开销 | 说明 |
|------|---------|------|
| 速率检查 | +1-2ms | 内存操作很快 |
| 登录防护 | +2-5ms | Redis查询 |
| CORS检查 | <1ms | 请求头比对 |
| 异常处理 | 0ms | 仅异常时触发 |

## 🎉 完成检查清单

- [ ] 添加Redis和速率限制依赖
- [ ] 配置Redis连接
- [ ] 创建RateLimitService
- [ ] 创建LoginProtectionService
- [ ] 创建RateLimitFilter
- [ ] 改造UserController添加登录防护
- [ ] 收紧WebSocket CORS配置
- [ ] 创建全局CORS配置
- [ ] 创建统一异常处理器
- [ ] 注册过滤器到SecurityConfig
- [ ] 测试速率限制
- [ ] 测试登录防护
- [ ] 测试CORS策略
- [ ] 测试异常处理
- [ ] 更新环境变量配置模板

## 📝 下一步

完成本文档后，继续实施：
- [06-命令执行安全加固.md](./06-命令执行安全加固.md) - 增强命令校验

---

**重要提醒**：生产环境务必配置正确的CORS域名！

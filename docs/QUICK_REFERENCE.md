# 安全加固快速参考

## 🚀 三步快速开始

### 第一步：认证授权（3-5天）
```bash
# 1. 添加依赖到 pom.xml
Spring Security + JWT

# 2. 实现核心类
JwtUtil.java - JWT工具类
JwtAuthenticationFilter.java - 认证过滤器
SecurityConfig.java - 安全配置

# 3. 改造Controller
@PreAuthorize("isAuthenticated()")
获取当前用户：SecurityUtils.getCurrentUserId()

# 4. 前端改造
Token存储、axios拦截器、路由守卫
```

### 第二步：密码加密（2-3天）
```bash
# 用户密码 - BCrypt
注册：BCryptPasswordEncoder.encode(password)
登录：passwordEncoder.matches(raw, encoded)

# SSH密码 - AES
保存：encryptionService.encrypt(password)
使用：encryptionService.decrypt(ciphertext)
```

### 第三步：配置外部化（0.5天）
```yaml
# application.yml
password: ${DB_PASSWORD:defaultPassword}

# 启动时传入
java -jar app.jar --DB_PASSWORD=xxx
# 或使用.env文件
```

## 📊 风险优先级

| 风险 | 优先级 | 修复时间 |
|------|--------|---------|
| 无认证机制 | 🔴 P0 | 3-5天 |
| 密码明文 | 🔴 P0 | 2-3天 |
| 配置明文 | 🔴 P0 | 0.5天 |
| SSH不验证 | 🟠 P1 | 1-2天 |
| 无登录防护 | 🟠 P1 | 1天 |
| 命令可绕过 | 🟡 P2 | 2-3天 |

## 🎯 最小可用配置（1周）

只实施这三个文档即可部署到内网测试：
- ✅ 01-认证授权实施指南.md
- ✅ 02-密码加密实施指南.md（仅用户密码部分）
- ✅ 03-配置安全实施指南.md

## 📦 推荐配置（2-3周）

生产环境建议加上：
- ✅ 02-密码加密实施指南.md（SSH密码部分）
- ✅ 04-SSH安全加固指南.md
- ✅ 05-API防护实施指南.md（登录防护+CORS）

## 🔧 关键代码片段

### JWT生成
```java
public String generateToken(String username, Integer userId) {
    return Jwts.builder()
        .setSubject(username)
        .claim("userId", userId)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
}
```

### 密码加密
```java
// 用户密码
String hashed = new BCryptPasswordEncoder().encode(rawPassword);

// SSH密码
Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
byte[] encrypted = cipher.doFinal(plainText.getBytes());
return Base64.getEncoder().encodeToString(encrypted);
```

### 认证过滤器
```java
String token = extractToken(request);
if (token != null && jwtUtil.validateToken(token)) {
    String username = jwtUtil.getUsernameFromToken(token);
    UsernamePasswordAuthenticationToken auth = 
        new UsernamePasswordAuthenticationToken(username, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

### 获取当前用户
```java
public static Integer getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserDetails) {
        return ((CustomUserDetails) auth.getPrincipal()).getId();
    }
    throw new UnauthorizedException("未登录");
}
```

### 速率限制
```java
public boolean isAllowed(String key, int capacity, int duration) {
    Bucket bucket = buckets.computeIfAbsent(key, k -> 
        Bucket.builder()
            .addLimit(Bandwidth.classic(capacity, 
                Refill.intervally(capacity, Duration.ofSeconds(duration))))
            .build()
    );
    return bucket.tryConsume(1);
}
```

### 命令检测
```java
public SafetyCheckResult checkCommand(String command) {
    // 1. 黑名单正则
    for (Pattern pattern : DANGEROUS_PATTERNS) {
        if (pattern.matcher(command).find()) {
            return blocked("危险命令");
        }
    }
    
    // 2. 命令解析
    List<ParsedCommand> commands = CommandParser.parse(command);
    
    // 3. 检查危险参数组合
    for (ParsedCommand cmd : commands) {
        if (isDangerousCombination(cmd)) {
            return blocked("危险参数组合");
        }
    }
    
    return safe();
}
```

## 🧪 快速测试

### 测试认证
```bash
# 未登录访问API（应返回401）
curl http://localhost:8080/api/dashboard

# 登录获取Token
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' \
  -c cookies.txt

# 使用Token访问API
curl http://localhost:8080/api/dashboard -b cookies.txt
```

### 测试登录防护
```bash
# 连续10次错误登录
for i in {1..10}; do
  curl -X POST http://localhost:8080/user/login \
    -d '{"username":"test","password":"wrong"}'
done

# 第11次应该被锁定
```

### 测试命令检测
```bash
# 危险命令应被阻止
curl -X POST http://localhost:8080/api/command/check \
  -b cookies.txt \
  -d '{"command":"rm -rf /"}'
```

## 📚 完整文档

| 文档 | 页数 | 说明 |
|------|------|------|
| [00-安全加固总览](./00-安全加固总览.md) | 310行 | 整体方案、风险评估 |
| [01-认证授权实施指南](./01-认证授权实施指南.md) | 966行 | Spring Security + JWT |
| [02-密码加密实施指南](./02-密码加密实施指南.md) | 870行 | BCrypt + AES加密 |
| [03-配置安全实施指南](./03-配置安全实施指南.md) | 751行 | 环境变量管理 |
| [04-SSH安全加固指南](./04-SSH安全加固指南.md) | 908行 | 主机密钥验证 |
| [05-API防护实施指南](./05-API防护实施指南.md) | 828行 | 速率限制、CORS |
| [06-命令执行安全加固](./06-命令执行安全加固.md) | 727行 | 命令注入防护 |

**总计：5,644行详细实施指南**

## 💡 常见错误

### 错误1：忘记更新调用方
```java
// ❌ 错误
public void method() {
    Integer userId = 1; // 硬编码
}

// ✅ 正确
public void method() {
    Integer userId = SecurityUtils.getCurrentUserId();
}
```

### 错误2：密码未加密
```java
// ❌ 错误
user.setPassword(request.getPassword());

// ✅ 正确
user.setPassword(passwordEncoder.encode(request.getPassword()));
```

### 错误3：配置仍然硬编码
```yaml
# ❌ 错误
password: MyPassword123

# ✅ 正确
password: ${DB_PASSWORD}
```

### 错误4：前端未携带Token
```javascript
// ❌ 错误
axios.get('/api/dashboard')

// ✅ 正确
axios.get('/api/dashboard', { withCredentials: true })
```

## 🎯 验收检查

实施完成后，确保：
- [ ] 未登录访问API返回401
- [ ] 数据库中密码全部加密
- [ ] Git仓库无明文密码
- [ ] 登录失败N次后锁定
- [ ] 危险命令被阻止
- [ ] CORS仅允许指定域名
- [ ] 用户只能看到自己的数据

## 📞 需要帮助？

参考完整文档：[README.md](./README.md)

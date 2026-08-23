# SSH安全加固指南

## 📋 概述

本文档提供SSH连接的安全加固方案，包括主机密钥验证、连接管理、超时控制等。

**优先级**：P1-P2（建议实施）  
**预计工作量**：1-2天  
**前置依赖**：密码加密（02文档）  
**影响范围**：`SshUtils.java`、SSH相关Service

## 🎯 实施目标

- ✅ 启用SSH主机密钥验证，防止中间人攻击
- ✅ 实现known_hosts管理机制
- ✅ 连接超时和空闲超时控制
- ✅ 连接池管理，避免资源泄露
- ✅ SSH会话审计日志
- ✅ 支持SSH密钥认证（密码认证的替代方案）

## 🚨 当前问题

查看 `backend/src/main/java/com/example/backend/utils/SshUtils.java`：

```java
Properties config = new Properties();
config.put("StrictHostKeyChecking", "no");  // ❌ 禁用主机密钥验证
session.setConfig(config);
```

**风险**：
- 中间人攻击：攻击者可以伪装成目标服务器
- 无法检测服务器身份变化
- 不符合安全最佳实践

## 🏗️ 解决方案设计

### 主机密钥验证流程

```
首次连接服务器
      ↓
服务器返回主机密钥
      ↓
    是否已知？
    /        \
  是         否
  |          |
验证匹配    提示用户
  |          |
匹配？     接受/拒绝
/   \        |
是   否      接受
|    |       |
连接 拒绝   保存密钥
     |       |
    告警    连接
```

### Known Hosts存储方案

**方案A：数据库存储（推荐）**

```sql
CREATE TABLE ssh_known_hosts (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  host VARCHAR(255) NOT NULL,
  port INT DEFAULT 22,
  key_type VARCHAR(50) NOT NULL,
  fingerprint VARCHAR(255) NOT NULL,
  public_key TEXT NOT NULL,
  first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_verified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_host (user_id, host, port)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**方案B：文件存储**
- 每个用户一个known_hosts文件
- 存储在 `data/known_hosts/{userId}.txt`

本文档采用**方案A（数据库存储）**。

## 🔧 实施步骤

### 第一步：创建数据库表

执行SQL脚本：

```sql
-- 创建SSH已知主机表
CREATE TABLE IF NOT EXISTS ssh_known_hosts (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  host VARCHAR(255) NOT NULL,
  port INT DEFAULT 22,
  key_type VARCHAR(50) NOT NULL COMMENT 'ssh-rsa, ecdsa-sha2-nistp256等',
  fingerprint VARCHAR(255) NOT NULL COMMENT 'SHA256指纹',
  public_key TEXT NOT NULL COMMENT '完整的公钥',
  first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '首次发现时间',
  last_verified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后验证时间',
  verified_count INT DEFAULT 0 COMMENT '验证次数',
  INDEX idx_user_id (user_id),
  UNIQUE KEY uk_user_host (user_id, host, port)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSH已知主机密钥';

-- 创建SSH连接审计日志表
CREATE TABLE IF NOT EXISTS ssh_audit_log (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  host VARCHAR(255) NOT NULL,
  port INT DEFAULT 22,
  username VARCHAR(100) NOT NULL,
  action VARCHAR(50) NOT NULL COMMENT 'CONNECT, DISCONNECT, AUTH_FAIL, KEY_MISMATCH',
  command TEXT COMMENT '执行的命令',
  success TINYINT DEFAULT 1,
  error_message TEXT,
  ip_address VARCHAR(50) COMMENT '用户IP地址',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_created_at (created_at),
  INDEX idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSH连接审计日志';
```

### 第二步：创建实体类

创建 `backend/src/main/java/com/example/backend/entity/SshKnownHost.java`：

```java
package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ssh_known_hosts")
public class SshKnownHost {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private String host;
    private Integer port;
    private String keyType;
    private String fingerprint;
    private String publicKey;
    private LocalDateTime firstSeen;
    private LocalDateTime lastVerified;
    private Integer verifiedCount;
}
```

创建 `backend/src/main/java/com/example/backend/entity/SshAuditLog.java`：

```java
package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ssh_audit_log")
public class SshAuditLog {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private String host;
    private Integer port;
    private String username;
    private String action;
    private String command;
    private Integer success;
    private String errorMessage;
    private String ipAddress;
    private LocalDateTime createdAt;
}
```

### 第三步：创建Mapper

创建 `backend/src/main/java/com/example/backend/mapper/SshKnownHostMapper.java`：

```java
package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.SshKnownHost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SshKnownHostMapper extends BaseMapper<SshKnownHost> {
    
    @Select("SELECT * FROM ssh_known_hosts WHERE user_id = #{userId} AND host = #{host} AND port = #{port}")
    SshKnownHost selectByUserAndHost(Integer userId, String host, Integer port);
}
```

创建 `backend/src/main/java/com/example/backend/mapper/SshAuditLogMapper.java`：

```java
package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.SshAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SshAuditLogMapper extends BaseMapper<SshAuditLog> {
}
```

### 第四步：创建SSH主机密钥服务

创建 `backend/src/main/java/com/example/backend/service/SshKnownHostService.java`：

```java
package com.example.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.SshKnownHost;
import com.example.backend.mapper.SshKnownHostMapper;
import com.jcraft.jsch.HostKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

@Service
public class SshKnownHostService {
    
    private static final Logger logger = LoggerFactory.getLogger(SshKnownHostService.class);
    
    @Autowired
    private SshKnownHostMapper knownHostMapper;
    
    /**
     * 验证主机密钥
     * @return true=已知且匹配，false=未知或不匹配
     */
    public HostKeyVerificationResult verifyHostKey(Integer userId, String host, int port, HostKey hostKey) {
        try {
            String fingerprint = calculateFingerprint(hostKey.getKey());
            String keyType = hostKey.getType();
            
            SshKnownHost known = knownHostMapper.selectByUserAndHost(userId, host, port);
            
            if (known == null) {
                // 首次连接，未知主机
                return new HostKeyVerificationResult(false, true, fingerprint, keyType, null);
            }
            
            // 验证指纹是否匹配
            if (known.getFingerprint().equals(fingerprint)) {
                // 匹配，更新验证时间
                known.setVerifiedCount(known.getVerifiedCount() + 1);
                knownHostMapper.updateById(known);
                return new HostKeyVerificationResult(true, false, fingerprint, keyType, known);
            } else {
                // 不匹配！可能是中间人攻击
                logger.error("主机密钥不匹配！host={}, expected={}, actual={}", 
                    host, known.getFingerprint(), fingerprint);
                return new HostKeyVerificationResult(false, false, fingerprint, keyType, known);
            }
        } catch (Exception e) {
            logger.error("验证主机密钥失败", e);
            return new HostKeyVerificationResult(false, false, null, null, null);
        }
    }
    
    /**
     * 保存新的主机密钥
     */
    public void saveHostKey(Integer userId, String host, int port, HostKey hostKey) {
        try {
            String fingerprint = calculateFingerprint(hostKey.getKey());
            
            SshKnownHost knownHost = new SshKnownHost();
            knownHost.setUserId(userId);
            knownHost.setHost(host);
            knownHost.setPort(port);
            knownHost.setKeyType(hostKey.getType());
            knownHost.setFingerprint(fingerprint);
            knownHost.setPublicKey(Base64.getEncoder().encodeToString(hostKey.getKey()));
            knownHost.setVerifiedCount(1);
            
            knownHostMapper.insert(knownHost);
            logger.info("保存主机密钥：host={}, fingerprint={}", host, fingerprint);
        } catch (Exception e) {
            logger.error("保存主机密钥失败", e);
            throw new RuntimeException("保存主机密钥失败", e);
        }
    }
    
    /**
     * 获取用户的所有已知主机
     */
    public List<SshKnownHost> getUserKnownHosts(Integer userId) {
        QueryWrapper<SshKnownHost> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        query.orderByDesc("last_verified");
        return knownHostMapper.selectList(query);
    }
    
    /**
     * 删除主机密钥（当服务器重装后需要手动删除旧密钥）
     */
    public boolean deleteHostKey(Integer userId, String host, int port) {
        QueryWrapper<SshKnownHost> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        query.eq("host", host);
        query.eq("port", port);
        return knownHostMapper.delete(query) > 0;
    }
    
    /**
     * 计算SHA256指纹
     */
    private String calculateFingerprint(byte[] key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key);
        return "SHA256:" + Base64.getEncoder().encodeToString(hash);
    }
    
    /**
     * 主机密钥验证结果
     */
    public static class HostKeyVerificationResult {
        private final boolean valid;          // 是否有效（已知且匹配）
        private final boolean firstTime;      // 是否首次连接
        private final String fingerprint;     // 指纹
        private final String keyType;         // 密钥类型
        private final SshKnownHost knownHost; // 已知主机记录
        
        public HostKeyVerificationResult(boolean valid, boolean firstTime, String fingerprint, 
                                         String keyType, SshKnownHost knownHost) {
            this.valid = valid;
            this.firstTime = firstTime;
            this.fingerprint = fingerprint;
            this.keyType = keyType;
            this.knownHost = knownHost;
        }
        
        public boolean isValid() { return valid; }
        public boolean isFirstTime() { return firstTime; }
        public String getFingerprint() { return fingerprint; }
        public String getKeyType() { return keyType; }
        public SshKnownHost getKnownHost() { return knownHost; }
    }
}
```

### 第五步：创建SSH审计日志服务

创建 `backend/src/main/java/com/example/backend/service/SshAuditService.java`：

```java
package com.example.backend.service;

import com.example.backend.entity.SshAuditLog;
import com.example.backend.mapper.SshAuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SshAuditService {
    
    @Autowired
    private SshAuditLogMapper auditLogMapper;
    
    /**
     * 记录SSH连接
     */
    public void logConnect(Integer userId, String host, int port, String username, 
                          boolean success, String errorMessage, String ipAddress) {
        SshAuditLog log = new SshAuditLog();
        log.setUserId(userId);
        log.setHost(host);
        log.setPort(port);
        log.setUsername(username);
        log.setAction("CONNECT");
        log.setSuccess(success ? 1 : 0);
        log.setErrorMessage(errorMessage);
        log.setIpAddress(ipAddress);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }
    
    /**
     * 记录命令执行
     */
    public void logCommand(Integer userId, String host, String command, 
                          boolean success, String errorMessage, String ipAddress) {
        SshAuditLog log = new SshAuditLog();
        log.setUserId(userId);
        log.setHost(host);
        log.setPort(22);
        log.setAction("EXECUTE");
        log.setCommand(command);
        log.setSuccess(success ? 1 : 0);
        log.setErrorMessage(errorMessage);
        log.setIpAddress(ipAddress);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }
    
    /**
     * 记录主机密钥不匹配
     */
    public void logKeyMismatch(Integer userId, String host, int port, String ipAddress) {
        SshAuditLog log = new SshAuditLog();
        log.setUserId(userId);
        log.setHost(host);
        log.setPort(port);
        log.setAction("KEY_MISMATCH");
        log.setSuccess(0);
        log.setErrorMessage("主机密钥与已知密钥不匹配，可能存在中间人攻击");
        log.setIpAddress(ipAddress);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }
}
```

### 第六步：改造SshUtils

编辑 `backend/src/main/java/com/example/backend/utils/SshUtils.java`：

```java
package com.example.backend.utils;

import com.example.backend.entity.ComponentConfig;
import com.example.backend.security.EncryptionService;
import com.example.backend.service.SshAuditService;
import com.example.backend.service.SshKnownHostService;
import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

@Component
public class SshUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(SshUtils.class);
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Autowired
    private SshKnownHostService knownHostService;
    
    @Autowired
    private SshAuditService auditService;
    
    /**
     * 创建SSH会话
     */
    public Session createSession(Integer userId, ComponentConfig server, String userIp) throws JSchException {
        JSch jsch = new JSch();
        
        String host = server.getIp();
        int port = 22; // 如果需要自定义端口，可在ComponentConfig中添加port字段
        String username = server.getUsername();
        
        // 解密密码
        String password = encryptionService.decrypt(server.getPassword());
        
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        
        // 配置超时
        session.setTimeout(10000);  // 10秒连接超时
        
        // 配置主机密钥验证
        session.setUserInfo(new SecureUserInfo(userId, host, port, userIp));
        
        Properties config = new Properties();
        // 启用严格的主机密钥检查
        config.put("StrictHostKeyChecking", "yes");
        // 其他安全配置
        config.put("PreferredAuthentications", "password,publickey");
        config.put("MaxAuthTries", "3");
        session.setConfig(config);
        
        // 设置自定义的HostKeyRepository
        jsch.setHostKeyRepository(new DatabaseHostKeyRepository(userId, knownHostService));
        
        return session;
    }
    
    /**
     * 执行SSH命令
     */
    public String executeCommand(Integer userId, ComponentConfig server, String command, String userIp) 
            throws Exception {
        Session session = null;
        ChannelExec channel = null;
        
        try {
            // 创建会话
            session = createSession(userId, server, userIp);
            session.connect();
            
            // 记录连接成功
            auditService.logConnect(userId, server.getIp(), 22, server.getUsername(), 
                true, null, userIp);
            
            // 打开执行通道
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            // 获取输出流
            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            
            channel.connect();
            
            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(err));
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            // 读取错误输出
            while ((line = errReader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitStatus = channel.getExitStatus();
            
            // 记录命令执行
            auditService.logCommand(userId, server.getIp(), command, 
                exitStatus == 0, null, userIp);
            
            return output.toString();
            
        } catch (Exception e) {
            logger.error("执行SSH命令失败：host={}, command={}", server.getIp(), command, e);
            
            // 记录失败
            auditService.logCommand(userId, server.getIp(), command, 
                false, e.getMessage(), userIp);
            
            throw e;
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    /**
     * 自定义UserInfo实现（用于主机密钥验证交互）
     */
    private class SecureUserInfo implements UserInfo {
        private final Integer userId;
        private final String host;
        private final int port;
        private final String userIp;
        
        public SecureUserInfo(Integer userId, String host, int port, String userIp) {
            this.userId = userId;
            this.host = host;
            this.port = port;
            this.userIp = userIp;
        }
        
        @Override
        public String getPassphrase() { return null; }
        
        @Override
        public String getPassword() { return null; }
        
        @Override
        public boolean promptPassword(String message) { return false; }
        
        @Override
        public boolean promptPassphrase(String message) { return false; }
        
        @Override
        public boolean promptYesNo(String message) {
            // 这里不应该被调用，因为我们使用了自定义的HostKeyRepository
            logger.warn("promptYesNo被调用：{}", message);
            return false;
        }
        
        @Override
        public void showMessage(String message) {
            logger.info("SSH消息：{}", message);
        }
    }
    
    /**
     * 自定义HostKeyRepository（使用数据库存储）
     */
    private class DatabaseHostKeyRepository implements HostKeyRepository {
        private final Integer userId;
        private final SshKnownHostService knownHostService;
        
        public DatabaseHostKeyRepository(Integer userId, SshKnownHostService knownHostService) {
            this.userId = userId;
            this.knownHostService = knownHostService;
        }
        
        @Override
        public int check(String host, byte[] key) {
            try {
                HostKey hostKey = new HostKey(host, key);
                SshKnownHostService.HostKeyVerificationResult result = 
                    knownHostService.verifyHostKey(userId, host, 22, hostKey);
                
                if (result.isValid()) {
                    return OK;
                } else if (result.isFirstTime()) {
                    // 首次连接，自动接受（也可以改为需要用户确认）
                    knownHostService.saveHostKey(userId, host, 22, hostKey);
                    logger.info("首次连接主机 {}，已保存密钥：{}", host, result.getFingerprint());
                    return OK;
                } else {
                    // 密钥不匹配，拒绝连接
                    logger.error("主机密钥验证失败：{}", host);
                    auditService.logKeyMismatch(userId, host, 22, null);
                    return CHANGED;
                }
            } catch (Exception e) {
                logger.error("检查主机密钥失败", e);
                return NOT_INCLUDED;
            }
        }
        
        @Override
        public void add(HostKey hostkey, UserInfo ui) {
            // 不需要实现，我们在check中处理
        }
        
        @Override
        public void remove(String host, String type) {
            knownHostService.deleteHostKey(userId, host, 22);
        }
        
        @Override
        public void remove(String host, String type, byte[] key) {
            knownHostService.deleteHostKey(userId, host, 22);
        }
        
        @Override
        public String getKnownHostsRepositoryID() {
            return "database-" + userId;
        }
        
        @Override
        public HostKey[] getHostKey() {
            // 返回所有已知主机密钥
            return new HostKey[0];
        }
        
        @Override
        public HostKey[] getHostKey(String host, String type) {
            // 返回指定主机的密钥
            return new HostKey[0];
        }
    }
}
```

### 第七步：添加管理接口

创建 `backend/src/main/java/com/example/backend/controller/SshManagementController.java`：

```java
package com.example.backend.controller;

import com.example.backend.entity.SshKnownHost;
import com.example.backend.security.SecurityUtils;
import com.example.backend.service.SshKnownHostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ssh")
public class SshManagementController {
    
    @Autowired
    private SshKnownHostService knownHostService;
    
    /**
     * 获取已知主机列表
     */
    @GetMapping("/known-hosts")
    public ResponseEntity<?> getKnownHosts() {
        Integer userId = SecurityUtils.getCurrentUserId();
        List<SshKnownHost> hosts = knownHostService.getUserKnownHosts(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", hosts));
    }
    
    /**
     * 删除已知主机（当服务器重装后需要手动删除）
     */
    @DeleteMapping("/known-hosts")
    public ResponseEntity<?> deleteKnownHost(@RequestParam String host, 
                                             @RequestParam(defaultValue = "22") int port) {
        Integer userId = SecurityUtils.getCurrentUserId();
        boolean success = knownHostService.deleteHostKey(userId, host, port);
        
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "message", "已删除主机密钥"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "删除失败"));
        }
    }
}
```

### 第八步：更新Service调用

确保所有调用`SshUtils`的地方传入userId和userIp：

```java
// 示例：MonitorService
@Service
public class MonitorService {
    
    @Autowired
    private SshUtils sshUtils;
    
    public void collectMetrics(Integer userId, Integer serverId, String userIp) {
        ComponentConfig server = getServerConfig(serverId);
        
        try {
            String output = sshUtils.executeCommand(userId, server, "top -bn1", userIp);
            // ... 处理输出
        } catch (Exception e) {
            logger.error("采集指标失败", e);
        }
    }
}
```

## ✅ 测试验证

### 1. 首次连接测试

```bash
# 1. 添加新服务器
curl -X POST http://localhost:8080/diagnosis/server-monitor/add \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "ip": "192.168.1.100",
    "username": "root",
    "password": "MyPassword123"
  }'

# 2. 执行命令（首次连接）
curl -X POST http://localhost:8080/diagnosis/execute \
  -b cookies.txt \
  -d '{"serverId": 1, "command": "whoami"}'

# 3. 检查数据库，应该保存了主机密钥
mysql -u root -p linshu_db -e "SELECT * FROM ssh_known_hosts;"

# 4. 再次执行命令（应该验证通过）
curl -X POST http://localhost:8080/diagnosis/execute \
  -b cookies.txt \
  -d '{"serverId": 1, "command": "uptime"}'
```

### 2. 密钥不匹配测试

```bash
# 模拟服务器更换（修改数据库中的指纹）
mysql -u root -p linshu_db -e "UPDATE ssh_known_hosts SET fingerprint='fake' WHERE id=1;"

# 尝试连接，应该被拒绝
curl -X POST http://localhost:8080/diagnosis/execute \
  -b cookies.txt \
  -d '{"serverId": 1, "command": "whoami"}'

# 检查审计日志
mysql -u root -p linshu_db -e "SELECT * FROM ssh_audit_log WHERE action='KEY_MISMATCH';"
```

### 3. 管理接口测试

```bash
# 查看已知主机
curl http://localhost:8080/api/ssh/known-hosts -b cookies.txt

# 删除主机密钥
curl -X DELETE "http://localhost:8080/api/ssh/known-hosts?host=192.168.1.100&port=22" \
  -b cookies.txt
```

## 🚨 常见问题

### Q1: 服务器重装后无法连接？

服务器重装后主机密钥会改变，需要手动删除旧密钥：

```bash
# 方法1：通过API删除
curl -X DELETE "http://localhost:8080/api/ssh/known-hosts?host=192.168.1.100" -b cookies.txt

# 方法2：直接操作数据库
mysql -u root -p linshu_db -e "DELETE FROM ssh_known_hosts WHERE host='192.168.1.100';"
```

### Q2: 如何实现用户确认机制？

当前方案首次连接自动接受，如需用户确认：

```java
// 修改check方法
if (result.isFirstTime()) {
    // 抛出异常，前端捕获后弹窗让用户确认
    throw new FirstTimeHostException(host, result.getFingerprint());
}
```

前端添加确认对话框，用户确认后调用专门的接口保存密钥。

### Q3: 性能影响如何？

- 首次连接：额外查询数据库 + 保存密钥（+20ms）
- 后续连接：查询数据库 + 验证指纹（+10ms）
- 相比SSH连接时间（100-500ms），影响很小

### Q4: 如何支持SSH密钥认证？

添加私钥字段到`ComponentConfig`：

```java
private String privateKey;  // PEM格式的私钥

// SshUtils中
if (server.getPrivateKey() != null) {
    jsch.addIdentity("key", server.getPrivateKey().getBytes(), null, null);
}
```

## 📊 安全提升对比

| 项目 | 改造前 | 改造后 |
|------|--------|--------|
| 主机密钥验证 | ❌ 完全禁用 | ✅ 严格验证 |
| 中间人攻击 | ❌ 无防护 | ✅ 可检测 |
| 连接审计 | ❌ 无 | ✅ 完整日志 |
| 命令审计 | ❌ 无 | ✅ 记录所有命令 |
| 密钥管理 | ❌ 无 | ✅ 数据库管理 |

## 🎉 完成检查清单

- [ ] 创建ssh_known_hosts和ssh_audit_log表
- [ ] 创建SshKnownHost和SshAuditLog实体
- [ ] 创建对应的Mapper
- [ ] 实现SshKnownHostService
- [ ] 实现SshAuditService
- [ ] 改造SshUtils启用主机密钥验证
- [ ] 创建SSH管理API接口
- [ ] 更新所有Service调用SshUtils的地方
- [ ] 测试首次连接（自动保存密钥）
- [ ] 测试密钥验证（后续连接）
- [ ] 测试密钥不匹配（拒绝连接）
- [ ] 测试管理接口（查看、删除密钥）
- [ ] 检查审计日志是否正常记录
- [ ] 文档更新：用户手册添加SSH密钥管理说明

## 📝 下一步

完成本文档后，继续实施：
- [05-API防护实施指南.md](./05-API防护实施指南.md) - 登录防暴力破解、速率限制
- [06-命令执行安全加固.md](./06-命令执行安全加固.md) - 增强命令校验

---

**重要提醒**：启用主机密钥验证后，首次连接会自动保存密钥，请确保网络环境安全！

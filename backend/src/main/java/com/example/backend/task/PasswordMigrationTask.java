package com.example.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.UserLoginMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 密码迁移任务：将现有明文密码加密为BCrypt
 * 执行一次后可以删除或禁用此类
 */
@Slf4j
@Component
public class PasswordMigrationTask implements CommandLineRunner {

    @Autowired
    private UserLoginMapper userLoginMapper;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始检查需要迁移的密码...");
        
        // 查询所有用户
        QueryWrapper<UserLogin> queryWrapper = new QueryWrapper<>();
        List<UserLogin> users = userLoginMapper.selectList(queryWrapper);
        
        int migratedCount = 0;
        int skippedCount = 0;
        
        for (UserLogin user : users) {
            String password = user.getPassword();
            
            // 检查密码是否已经是BCrypt格式（以$2a$或$2b$开头）
            if (password != null && (password.startsWith("$2a$") || password.startsWith("$2b$"))) {
                log.debug("用户 {} 的密码已加密，跳过", user.getUsername());
                skippedCount++;
                continue;
            }
            
            // 如果密码为空或明文，进行加密
            if (password == null || password.isEmpty()) {
                log.warn("用户 {} 的密码为空，设置默认密码", user.getUsername());
                password = "ChangeMe123"; // 默认密码，用户需要修改
            }
            
            // 加密密码
            String encryptedPassword = passwordEncoder.encode(password);
            user.setPassword(encryptedPassword);
            userLoginMapper.updateById(user);
            
            log.info("已迁移用户 {} 的密码", user.getUsername());
            migratedCount++;
        }
        
        log.info("密码迁移完成！共迁移 {} 个用户，跳过 {} 个已加密用户", migratedCount, skippedCount);
        
        if (migratedCount > 0) {
            log.warn("=================================================================");
            log.warn("重要提示：已迁移的用户需要使用原密码登录");
            log.warn("如果原密码为空，默认密码为: ChangeMe123");
            log.warn("建议通知用户修改密码");
            log.warn("=================================================================");
        }
    }
}

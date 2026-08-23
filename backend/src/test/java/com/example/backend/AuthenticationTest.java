package com.example.backend;

import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT认证功能测试
 */
@SpringBootTest
public class AuthenticationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserLoginMapper userLoginMapper;

    /**
     * 测试密码加密
     */
    @Test
    public void testPasswordEncryption() {
        String rawPassword = "test123456";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        
        // 验证密码不是明文
        assertNotEquals(rawPassword, encodedPassword);
        
        // 验证密码可以正确匹配
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
        
        // 验证错误密码不匹配
        assertFalse(passwordEncoder.matches("wrongpassword", encodedPassword));
        
        System.out.println("✓ 密码加密测试通过");
    }

    /**
     * 测试JWT生成和验证
     */
    @Test
    public void testJwtGenerationAndValidation() {
        String username = "testuser";
        Long userId = 123L;
        
        // 生成token
        String token = jwtUtil.generateToken(username, userId);
        assertNotNull(token);
        assertTrue(token.length() > 0);
        
        // 验证token
        assertTrue(jwtUtil.validateToken(token, username));
        
        // 从token中提取用户名
        String extractedUsername = jwtUtil.getUsernameFromToken(token);
        assertEquals(username, extractedUsername);
        
        // 从token中提取用户ID
        Long extractedUserId = jwtUtil.getUserIdFromToken(token);
        assertEquals(userId, extractedUserId);
        
        // 验证错误的用户名
        assertFalse(jwtUtil.validateToken(token, "wronguser"));
        
        System.out.println("✓ JWT生成和验证测试通过");
    }

    /**
     * 测试JWT过期
     */
    @Test
    public void testJwtExpiration() throws InterruptedException {
        String username = "testuser";
        Long userId = 123L;
        
        // 生成一个很短有效期的token（1秒）
        String token = jwtUtil.generateToken(username, userId, 1000L);
        
        // 立即验证应该通过
        assertTrue(jwtUtil.validateToken(token, username));
        
        // 等待2秒后验证应该失败
        Thread.sleep(2000);
        assertFalse(jwtUtil.validateToken(token, username));
        
        System.out.println("✓ JWT过期测试通过");
    }

    /**
     * 测试数据库中的密码已加密
     */
    @Test
    public void testDatabasePasswordsEncrypted() {
        // 查询一个用户
        UserLogin user = userLoginMapper.selectById(1);
        
        if (user != null) {
            String password = user.getPassword();
            
            // BCrypt加密后的密码应该以$2a$或$2b$开头，长度为60
            assertTrue(password.startsWith("$2a$") || password.startsWith("$2b$"), 
                "数据库中的密码应该是BCrypt加密格式");
            assertEquals(60, password.length(), 
                "BCrypt加密后的密码长度应该是60");
            
            System.out.println("✓ 数据库密码加密测试通过");
        } else {
            System.out.println("⚠ 数据库中没有用户数据，跳过测试");
        }
    }
}

package com.example.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.UserLoginMapper;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AlertRecipientService {

    @Autowired
    private UserLoginMapper userLoginMapper;

    public String getRecipientByUsername(String username) {
        UserLogin user = resolveUser(username);
        return user == null ? "" : normalizeEmail(user.getEmail());
    }

    public String getRecipientByUserId(Long userId) {
        if (userId == null) {
            return "";
        }
        UserLogin user = userLoginMapper.selectById(userId);
        return user == null ? "" : normalizeEmail(user.getEmail());
    }

    public String updateRecipient(String username, String email) {
        UserLogin user = resolveUser(username);
        if (user == null || user.getId() == null) {
            throw new RuntimeException("未找到对应用户，无法保存紧急联系人邮箱");
        }

        String normalized = normalizeEmail(email);
        if (StringUtils.hasText(normalized)) {
            validateEmail(normalized);
        }

        user.setEmail(normalized);
        userLoginMapper.updateById(user);
        return normalizeEmail(user.getEmail());
    }

    public String resolveEffectiveRecipient(String username, String email) {
        String normalized = normalizeEmail(email);
        if (StringUtils.hasText(normalized)) {
            validateEmail(normalized);
            return normalized;
        }
        return getRecipientByUsername(username);
    }

    private UserLogin resolveUser(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userLoginMapper.selectOne(
                new LambdaQueryWrapper<UserLogin>().eq(UserLogin::getUsername, username.trim())
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim();
    }

    private void validateEmail(String email) {
        try {
            InternetAddress address = new InternetAddress(email);
            address.validate();
        } catch (Exception ex) {
            throw new RuntimeException("邮箱格式不正确");
        }
    }
}

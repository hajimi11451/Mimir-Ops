package com.example.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CommandSafetyService {

    private static final List<Pattern> HIGH_RISK_PATTERNS = Arrays.asList(
            Pattern.compile("(^|\\s)rm\\s+-rf\\s+/(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)mkfs(\\.|\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)dd\\s+if=.*of=/dev/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)shutdown(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)reboot(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)userdel\\s+-r\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)chmod\\s+-R\\s+777\\s+/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)>\\s*/etc/", Pattern.CASE_INSENSITIVE)
    );

    public boolean isHighRiskCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return false;
        }
        return HIGH_RISK_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(command).find());
    }

    public String injectSudoPassword(String command, String password) {
        if (!StringUtils.hasText(command) || !command.contains("sudo")) {
            return command;
        }

        String escapedPwd = password == null ? "" : password.replace("\"", "\\\"");
        String withoutSudo = command.replaceAll("\\bsudo\\s+", "").trim();
        return "echo \"" + escapedPwd + "\" | sudo -S " + withoutSudo;
    }
}

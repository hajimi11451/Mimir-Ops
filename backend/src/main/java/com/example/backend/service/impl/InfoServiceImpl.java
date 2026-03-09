package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.Information;
import com.example.backend.entity.UserLogin;
import com.example.backend.entity.UserProcess;
import com.example.backend.mapper.InformationMapper;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.mapper.UserProcessMapper;
import com.example.backend.service.InfoService;
import com.example.backend.utils.InfoNormalizationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class InfoServiceImpl implements InfoService {

    @Autowired
    private InformationMapper informationMapper;

    @Autowired
    private UserProcessMapper userProcessMapper;

    @Autowired
    private UserLoginMapper userLoginMapper;

    private Long resolveUserId(Map<String, String> request) {
        String username = request.get("username");
        if (username != null && !username.isBlank()) {
            QueryWrapper<UserLogin> wrapper = new QueryWrapper<>();
            wrapper.eq("username", username);
            UserLogin user = userLoginMapper.selectOne(wrapper);
            return user == null ? null : user.getId();
        }

        String userId = request.get("userId");
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public List<Information> selectAllInfo(Map<String, String> request) {
        Long userId = resolveUserId(request);
        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId == null ? -1L : userId);
        queryWrapper.orderByDesc("created_at");

        List<Information> response = informationMapper.selectList(queryWrapper);
        normalizeInformationList(response);
        return response.isEmpty() ? null : response;
    }

    @Override
    public List<Information> selectInfo(Map<String, String> request) {
        Long userId = resolveUserId(request);
        String id = request.get("id");
        String serverIp = request.get("serverIp");
        String component = request.get("component");
        String errorSummary = request.get("errorSummary");
        String riskLevel = request.get("riskLevel");
        if (riskLevel != null && !riskLevel.isBlank()) {
            riskLevel = InfoNormalizationUtils.normalizeRiskLevel(riskLevel);
        }

        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        if (request.get("startTime") != null && request.get("endTime") != null) {
            try {
                startTime = LocalDateTime.parse(request.get("startTime"));
                endTime = LocalDateTime.parse(request.get("endTime"));
            } catch (Exception ignored) {
            }
        }

        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId == null ? -1L : userId);
        if (id != null && !id.isBlank()) {
            queryWrapper.eq("id", id);
        }
        if (serverIp != null && !serverIp.isBlank()) {
            queryWrapper.eq("server_ip", serverIp);
        }
        if (component != null && !component.isBlank()) {
            queryWrapper.eq("component", component);
        }
        if (errorSummary != null && !errorSummary.isBlank()) {
            queryWrapper.eq("error_summary", errorSummary);
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            queryWrapper.eq("risk_level", riskLevel);
        }
        if (startTime != null && endTime != null) {
            queryWrapper.between("created_at", startTime, endTime);
        }

        List<Information> response = informationMapper.selectList(queryWrapper);
        normalizeInformationList(response);
        return response.isEmpty() ? null : response;
    }

    @Override
    public String insertProcess(Map<String, String> request) {
        Long userId = resolveUserId(request);
        String serverIp = request.get("serverIp");
        String component = request.get("component");
        String problemLog = resolveProblemLog(request);
        String processMethod = request.get("processMethod");

        LocalDateTime processTime = null;
        String processTimeText = request.get("processTime");
        if (processTimeText == null || processTimeText.isBlank()) {
            processTimeText = request.get("endTime");
        }
        if (processTimeText != null && !processTimeText.isBlank()) {
            try {
                processTime = LocalDateTime.parse(processTimeText);
            } catch (Exception ignored) {
            }
        }
        if (processTime == null) {
            processTime = LocalDateTime.now();
        }

        if (userId == null
                || serverIp == null || serverIp.isBlank()
                || component == null || component.isBlank()
                || processMethod == null || processMethod.isBlank()) {
            return "输入参数不能为空";
        }

        UserProcess userProcess = new UserProcess();
        userProcess.setUserId(userId);
        userProcess.setServerIp(serverIp);
        userProcess.setComponent(component);
        userProcess.setProblemLog(problemLog);
        userProcess.setProcessMethod(processMethod);
        userProcess.setProcessTime(processTime);

        int result = userProcessMapper.insert(userProcess);
        return result == 1 ? "录入成功" : "录入失败";
    }

    @Override
    public List<UserProcess> selectAllProcess(Map<String, String> request) {
        Long userId = resolveUserId(request);
        QueryWrapper<UserProcess> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId == null ? -1L : userId);
        queryWrapper.orderByDesc("process_time");

        List<UserProcess> response = userProcessMapper.selectList(queryWrapper);
        return response.isEmpty() ? null : response;
    }

    @Override
    public List<UserProcess> selectProcess(Map<String, String> request) {
        Long userId = resolveUserId(request);
        String serverIp = request.get("serverIp");
        String component = request.get("component");

        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        if (request.get("startTime") != null && request.get("endTime") != null) {
            try {
                startTime = LocalDateTime.parse(request.get("startTime"));
                endTime = LocalDateTime.parse(request.get("endTime"));
            } catch (Exception ignored) {
            }
        }

        QueryWrapper<UserProcess> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId == null ? -1L : userId);
        if (serverIp != null && !serverIp.isBlank()) {
            queryWrapper.eq("server_ip", serverIp);
        }
        if (component != null && !component.isBlank()) {
            queryWrapper.eq("component", component);
        }
        if (startTime != null && endTime != null) {
            queryWrapper.between("process_time", startTime, endTime);
        }
        queryWrapper.orderByDesc("process_time");

        List<UserProcess> response = userProcessMapper.selectList(queryWrapper);
        return response.isEmpty() ? null : response;
    }

    @Override
    public int deleteAllInfo(Map<String, String> request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return 0;
        }

        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        return informationMapper.delete(queryWrapper);
    }

    private void normalizeInformationList(List<Information> response) {
        if (response == null || response.isEmpty()) {
            return;
        }

        for (Information info : response) {
            if (info == null) {
                continue;
            }

            String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel(info.getRiskLevel());
            info.setComponent(InfoNormalizationUtils.normalizeComponent(info.getComponent()));
            info.setErrorSummary(InfoNormalizationUtils.normalizeText(info.getErrorSummary(), "无"));
            info.setAnalysisResult(InfoNormalizationUtils.normalizeText(info.getAnalysisResult(), "无"));
            info.setRiskLevel(normalizedRiskLevel);
            info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions(info.getSuggestedActions(), normalizedRiskLevel));
        }
    }

    private String resolveProblemLog(Map<String, String> request) {
        String[] candidates = {"problemLog", "problem", "issue", "rawLog", "logContent", "logInfo"};
        for (String key : candidates) {
            String value = request.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

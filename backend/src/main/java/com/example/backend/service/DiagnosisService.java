package com.example.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.entity.ComponentConfig;
import com.example.backend.entity.Information;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.ComponentConfigMapper;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.utils.AiUtils;
import com.example.backend.utils.InfoNormalizationUtils;
import com.example.backend.utils.SshUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DiagnosisService {

    /** 默认用户ID，用于无登录态的配置关联（如未传 userId 时） */
    private static final long DEFAULT_USER_ID = 1L;

    /** 组件名称 -> 常见 Linux 日志路径，AI 失败或限流时作为回退 */
    private static final Map<String, List<String>> COMMON_LOG_PATHS = Map.of(
            "nginx", List.of("/var/log/nginx/error.log", "/var/log/syslog"),
            "mysql", List.of("/var/log/mysql/error.log", "/var/log/mysqld.log", "/var/log/syslog"),
            "tomcat", List.of("/var/log/tomcat/catalina.out", "/opt/tomcat/logs/catalina.out"),
            "redis", List.of("/var/log/redis/redis-server.log", "/var/log/syslog"),
            "java", List.of("/var/log/syslog"),
            "docker", List.of("/var/log/docker.log", "/var/log/syslog")
    );

    private static final String DEFAULT_OS_HINT = "Ubuntu Linux";

    /** 校验路径是否为合法 Linux 路径（以 / 开头且不含明显异常字符） */
    private static final Pattern VALID_PATH = Pattern.compile("^/[/a-zA-Z0-9_.-]+$");

    /** AI 判断异常时写入数据库的固定文案 */
    private static final String AI_EXCEPTION_STORED_MESSAGE = "上报管理员，ai判断运行异常";

    /** SSH / 日志读取失败时写入数据库的固定摘要 */
    private static final String SSH_FETCH_FAILURE_SUMMARY = "日志获取失败";

    private static final String SYSTEM_MONITOR_COMPONENT = "系统监控";

    private static final String SYSTEM_MONITOR_CONFIG_KEY = "system_monitor";

    private static final String SYSTEM_MONITOR_CONFIG_VALUE = "默认采集全部系统指标";

    private static final int ENABLED = 1;

    private static final int DISABLED = 0;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ComponentConfigMapper componentConfigMapper;

    @Autowired
    private UserLoginMapper userLoginMapper;

    @Autowired
    private com.example.backend.mapper.InformationMapper informationMapper;

    @Autowired
    private AiUtils aiUtils;

    @Autowired
    private SshUtils sshUtils;

    @Autowired
    private MonitorService monitorService;

    private Long resolveUserIdFromAppUsername(String appUsername) {
        if (!StringUtils.hasText(appUsername)) {
            return null;
        }
        UserLogin user = userLoginMapper.selectOne(
                new LambdaQueryWrapper<UserLogin>().eq(UserLogin::getUsername, appUsername)
        );
        return user == null ? null : user.getId();
    }

    /**
     * 获取日志路径（Controller 入口）
     */
    public String getLogPath(String serverIp, String component, String username, String password, Boolean useSudo) {
        boolean sudoEnabled = isUseSudoEnabled(useSudo);
        if (sudoEnabled && (!StringUtils.hasText(username) || !StringUtils.hasText(password))) {
            throw new RuntimeException("启用 sudo 读取日志时必须填写 SSH 用户名和密码");
        }
        return discoverLogPath(serverIp, component, DEFAULT_OS_HINT, username, password, sudoEnabled);
    }

    /**
     * 智能路径发现：先查库 -> 再试 AI -> AI 失败则用常见路径回退
     */
    public String discoverLogPath(String serverIp, String component, String osType, String username, String password, boolean useSudo) {
        log.info("开始发现日志路径: serverIp={}, component={}", serverIp, component);
        if (useSudo && (!StringUtils.hasText(username) || !StringUtils.hasText(password))) {
            throw new RuntimeException("启用 sudo 读取日志时必须填写 SSH 用户名和密码");
        }
        String configKey = "error_log_path";

        // 1. 查库：优先使用已验证的路径
        ComponentConfig config = componentConfigMapper.selectOne(
                new LambdaQueryWrapper<ComponentConfig>()
                        .eq(ComponentConfig::getServerIp, serverIp)
                        .eq(ComponentConfig::getComponent, component)
                        .eq(ComponentConfig::getConfigKey, configKey)
                        .eq(ComponentConfig::getIsVerified, 1)
        );

        if (config != null && StringUtils.hasText(config.getConfigValue())) {
            log.info("数据库命中已验证路径: {}", config.getConfigValue());
            return config.getConfigValue();
        }

        // 2. 调用 AI 猜测路径（可能因限流/异常返回错误文案）
        String guessedPath = null;
        try {
            String command = aiUtils.generateLogCommand(component, osType);
            guessedPath = extractPathFromCommand(command);
            if (StringUtils.hasText(guessedPath) && VALID_PATH.matcher(guessedPath).matches()) {
                log.info("AI 推荐路径: {}", guessedPath);
            } else {
                guessedPath = null;
            }
        } catch (Exception e) {
            log.warn("AI 生成路径失败，将使用常见路径回退: {}", e.getMessage());
        }

        List<String> candidates = buildLogPathCandidates(component, osType, guessedPath);
        log.info("日志路径候选列表: {}", candidates);

        String resolvedPath = resolveReadableLogPath(serverIp, username, password, candidates, useSudo);

        // 5. 验证成功，持久化到数据库
        log.info("路径验证成功，更新数据库记录");
        // addConfig 会按 userId 持久化，这里只返回路径，避免写入错误用户数据
        return resolvedPath;
    }

    /**
     * 执行智能诊断
     * @param serverIp 服务器IP
     * @param component 组件名称
     * @param logPath 日志路径
     * @param username 用户名
     * @param password 密码
     * @return 诊断结果映射
     */
    public Map<String, Object> executeDiagnosis(String serverIp, String component, String logPath, String username, String password, boolean useSudo) {
        log.info("开始执行诊断: ip={}, component={}, path={}, useSudo={}", serverIp, component, logPath, useSudo);
        if (useSudo && (!StringUtils.hasText(username) || !StringUtils.hasText(password))) {
            throw new RuntimeException("启用 sudo 读取日志时必须填写 SSH 用户名和密码");
        }
        
        // 1. 调用 SSH 获取原始日志
        String rawLog = runLogCommand(serverIp, username, password, buildTailCommand(logPath, 50, useSudo, password));

        String normalizedRawLog = InfoNormalizationUtils.normalizeText(rawLog, "");
        if (isSshOrLogFetchFailure(normalizedRawLog)) {
            Map<String, Object> fetchFailureResult = new HashMap<>();
            fetchFailureResult.put("rawLog", InfoNormalizationUtils.normalizeText(normalizedRawLog, "无"));
            fetchFailureResult.put("analysis", "");
            fetchFailureResult.put("summary", SSH_FETCH_FAILURE_SUMMARY);
            fetchFailureResult.put("riskLevel", "高");
            fetchFailureResult.put("noLogData", false);
            fetchFailureResult.put("fetchFailure", true);
            return fetchFailureResult;
        }
        if (!StringUtils.hasText(normalizedRawLog)) {
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("rawLog", "无");
            emptyResult.put("analysis", "");
            emptyResult.put("summary", "无");
            emptyResult.put("riskLevel", "无");
            emptyResult.put("noLogData", true);
            emptyResult.put("fetchFailure", false);
            return emptyResult;
        }
        
        // 2. 调用 AI 分析日志
        String analysisJson = aiUtils.analyzeLog(normalizedRawLog);
        
        // 3. 组装结果
        Map<String, Object> result = new HashMap<>();
        result.put("rawLog", normalizedRawLog);
        result.put("analysis", analysisJson);
        result.put("summary", "发现 " + component + " 运行异常");
        result.put("riskLevel", "高");
        result.put("noLogData", false);
        result.put("fetchFailure", false);
        
        return result;
    }

    private boolean isSshOrLogFetchFailure(String rawLog) {
        if (!StringUtils.hasText(rawLog)) {
            return false;
        }

        String value = rawLog.trim();
        return value.startsWith("SSH Error:")
                || value.startsWith("Command failed with exit code")
                || value.contains("No such file")
                || value.contains("cannot open")
                || value.contains("Permission denied")
                || value.contains("is not in the sudoers file")
                || value.contains("a password is required")
                || value.contains("incorrect password attempt")
                || value.startsWith("sudo:")
                || value.contains("Read timed out")
                || value.contains("Connection timed out")
                || value.contains("Connection refused")
                || value.contains("Auth fail");
    }

    private String extractPathFromCommand(String command) {
        if (command.contains(" ")) {
            String[] parts = command.split(" ");
            return parts[parts.length - 1];
        }
        return command;
    }

    private List<String> buildLogPathCandidates(String component, String osType, String aiPath) {
        String key = normalizeComponentKey(component);
        boolean ubuntuLike = isUbuntuLike(osType);
        Set<String> candidates = new LinkedHashSet<>();

        if (StringUtils.hasText(aiPath) && VALID_PATH.matcher(aiPath.trim()).matches()) {
            candidates.add(aiPath.trim());
        }

        if (isSshAuthComponent(key)) {
            if (ubuntuLike) {
                candidates.add("/var/log/auth.log");
                candidates.add("/var/log/syslog");
            } else {
                candidates.add("/var/log/secure");
                candidates.add("/var/log/messages");
                candidates.add("/var/log/syslog");
            }
        }

        List<String> commonPaths = COMMON_LOG_PATHS.get(key);
        if (commonPaths != null) {
            candidates.addAll(commonPaths);
        }

        if (ubuntuLike) {
            candidates.add("/var/log/syslog");
        } else {
            candidates.add("/var/log/messages");
            candidates.add("/var/log/syslog");
        }

        return new ArrayList<>(candidates);
    }

    private String resolveReadableLogPath(String serverIp, String username, String password, List<String> candidates, boolean useSudo) {
        RuntimeException permissionFailure = null;
        RuntimeException lastFailure = null;

        for (String candidate : candidates) {
            String verifyResult;
            try {
                verifyResult = runLogCommand(serverIp, username, password, buildTailCommand(candidate, 1, useSudo, password));
            } catch (Exception e) {
                log.warn("候选日志路径验证异常: {} -> {}", candidate, e.getMessage());
                lastFailure = new RuntimeException("日志路径验证失败: " + e.getMessage());
                continue;
            }

            String validationMessage = getLogPathValidationMessage(candidate, verifyResult);
            if (!StringUtils.hasText(validationMessage)) {
                log.info("日志路径验证成功: {}", candidate);
                return candidate;
            }

            log.warn("候选日志路径不可用: {} -> {}", candidate, verifyResult);
            RuntimeException failure = new RuntimeException(validationMessage);
            if (validationMessage.contains("无权限")) {
                if (permissionFailure == null) {
                    permissionFailure = failure;
                }
            } else {
                lastFailure = failure;
            }
        }

        if (permissionFailure != null) {
            throw permissionFailure;
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new RuntimeException("未找到可读取的日志路径");
    }

    private String normalizeComponentKey(String component) {
        return component == null ? "" : component.toLowerCase().replaceAll("\\s+", "");
    }

    private boolean isUbuntuLike(String osType) {
        String normalized = osType == null ? "" : osType.toLowerCase();
        return normalized.contains("ubuntu") || normalized.contains("debian");
    }

    private boolean isSshAuthComponent(String componentKey) {
        return componentKey.contains("ssh")
                || componentKey.contains("sshd")
                || componentKey.contains("auth")
                || componentKey.contains("login")
                || componentKey.contains("sudo")
                || componentKey.contains("secure")
                || componentKey.contains("认证")
                || componentKey.contains("登录")
                || componentKey.contains("授权")
                || componentKey.contains("安全");
    }

    // --- 监控配置管理 ---

    /**
     * 添加/更新监控配置。
     * 首先验证SSH连接，若账号密码错误则创建Information记录并抛出异常。
     * 若 userId 未传则使用默认用户 ID，避免表字段 user_id 无默认值导致插入失败。
     * 
     * @param config 监控配置
     * @throws RuntimeException 当SSH连接失败时抛出，包含错误信息
     */
    public void addConfig(ComponentConfig config) {
        Long resolvedUserId = resolveUserIdFromAppUsername(config.getAppUsername());
        if (resolvedUserId != null) {
            config.setUserId(resolvedUserId);
        } else if (config.getUserId() == null) {
            throw new RuntimeException("未识别登录用户，无法保存监控配置");
        }

        String serverIp = config.getServerIp();
        String username = config.getUsername();
        String password = config.getPassword();
        String configValue = config.getConfigValue();
        String component = config.getComponent();
        boolean useSudo = isUseSudoEnabled(config.getUseSudo());

        if (useSudo && (!StringUtils.hasText(username) || !StringUtils.hasText(password))) {
            throw new RuntimeException("启用 sudo 读取日志时必须填写 SSH 用户名和密码");
        }

        log.info("保存监控配置：开始执行 SSH 连接验证与日志路径验证 - {}@{}", serverIp, component);

        // 1. SSH 连接验证（点击「保存并开始自动监控」时执行第一次）
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            boolean connected = sshUtils.testConnection(serverIp, username, password);
            if (!connected) {
                log.warn("SSH连接失败，创建Information记录: {}@{}", username, serverIp);
                createSshAuthFailureInfo(config);
                throw new RuntimeException("SSH连接失败：账号或密码错误，请检查后重试");
            }
            log.info("SSH连接验证成功: {}@{}", username, serverIp);
        } else {
            log.info("未提供SSH账号密码，跳过SSH连接验证（路径验证将使用默认凭据）");
        }

        // 2. 日志存放位置验证（点击「保存并开始自动监控」时执行第一次）
        if (StringUtils.hasText(configValue)) {
            String verifyResult;
            try {
                verifyResult = runLogCommand(serverIp, username, password, buildTailCommand(configValue, 1, useSudo, password));
            } catch (Exception e) {
                log.error("日志路径验证时SSH执行失败: {}", e.getMessage());
                throw new RuntimeException("日志路径验证失败: " + e.getMessage());
            }
            validateLogPathReadable(configValue, verifyResult);
            log.info("日志路径验证成功: {}", configValue);
            config.setIsVerified(1);
        } else {
            log.info("未填写日志路径，开始自动发现并验证日志路径");
            String discoveredPath = getLogPath(serverIp, component, username, password, useSudo);
            config.setConfigValue(discoveredPath);
            config.setIsVerified(1);
            log.info("自动发现并验证的日志路径: {}", discoveredPath);
        }

        config.setUseSudo(useSudo);

        // 3. 验证通过，保存/更新配置
        ComponentConfig existing = componentConfigMapper.selectOne(
                new LambdaQueryWrapper<ComponentConfig>()
                        .eq(ComponentConfig::getServerIp, config.getServerIp())
                        .eq(ComponentConfig::getComponent, config.getComponent())
        );

        if (existing != null) {
            existing.setConfigValue(config.getConfigValue());
            if (StringUtils.hasText(config.getUsername())) existing.setUsername(config.getUsername());
            if (StringUtils.hasText(config.getPassword())) existing.setPassword(config.getPassword());
            existing.setUseSudo(useSudo);
            if (!StringUtils.hasText(config.getConfigValue())) {
                existing.setIsVerified(0);
            } else {
                existing.setIsVerified(1);
            }
            existing.setIsEnabled(resolveEnabledValue(config.getIsEnabled()));
            componentConfigMapper.updateById(existing);
        } else {
            if (config.getConfigKey() == null) config.setConfigKey("error_log_path");
            if (!StringUtils.hasText(config.getConfigValue())) {
                config.setIsVerified(0);
            } else {
                config.setIsVerified(1);
            }
            config.setIsEnabled(resolveEnabledValue(config.getIsEnabled()));
            config.setUpdatedAt(java.time.LocalDateTime.now());
            componentConfigMapper.insert(config);
        }
    }

    /**
     * 当SSH认证失败时，创建Information记录供前端展示
     */
    private void createSshAuthFailureInfo(ComponentConfig config) {
        try {
            com.example.backend.entity.Information info = new com.example.backend.entity.Information();
            info.setUserId(config.getUserId() != null ? config.getUserId() : DEFAULT_USER_ID);
            info.setServerIp(config.getServerIp());
            String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel("高");
            info.setComponent(InfoNormalizationUtils.normalizeComponent(config.getComponent()));
            info.setErrorSummary(InfoNormalizationUtils.normalizeText("SSH连接失败：账号或密码错误", "无"));
            info.setAnalysisResult(InfoNormalizationUtils.normalizeText("请检查服务器IP、SSH用户名和密码是否正确，确认服务器是否允许SSH连接。", "无"));
            info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions("检查服务器IP、SSH用户名和密码是否正确；确认服务器是否允许SSH连接。", normalizedRiskLevel));
            info.setRawLog("SSH认证失败 - 服务器: " + config.getServerIp() + ", 用户名: " + config.getUsername());
            info.setRiskLevel(normalizedRiskLevel);
            info.setCreatedAt(java.time.LocalDateTime.now());
            informationMapper.insert(info);
            log.info("已创建SSH认证失败的Information记录: {} - {}", config.getServerIp(), config.getComponent());
        } catch (Exception e) {
            log.error("创建SSH认证失败记录时出错", e);
        }
    }

    public List<ComponentConfig> listConfigs(String appUsername) {
        Long userId = resolveUserIdFromAppUsername(appUsername);
        if (userId == null) {
            return java.util.Collections.emptyList();
        }
        return componentConfigMapper.selectList(new LambdaQueryWrapper<ComponentConfig>()
                .eq(ComponentConfig::getUserId, userId));
    }

    public List<ComponentConfig> listAllConfigsForAutoTask() {
        List<ComponentConfig> list = componentConfigMapper.selectList(
                new LambdaQueryWrapper<ComponentConfig>()
                        .eq(ComponentConfig::getIsEnabled, ENABLED)
        );
        list.removeIf(config -> SYSTEM_MONITOR_CONFIG_KEY.equalsIgnoreCase(String.valueOf(config.getConfigKey())));
        return list;
    }

    public void deleteConfig(Long id, String appUsername) {
        Long userId = resolveUserIdFromAppUsername(appUsername);
        if (userId == null) {
            return;
        }
        ComponentConfig cfg = componentConfigMapper.selectById(id);
        if (cfg == null || cfg.getUserId() == null || !cfg.getUserId().equals(userId)) {
            return;
        }
        componentConfigMapper.deleteById(id);
    }

    public void setServerMonitorEnabled(String appUsername, String serverIp, boolean enabled) {
        Long userId = resolveUserIdFromAppUsername(appUsername);
        if (userId == null) {
            throw new RuntimeException("未识别登录用户，无法更新服务器监控状态");
        }
        if (!StringUtils.hasText(serverIp)) {
            throw new RuntimeException("服务器 IP 不能为空");
        }

        String normalizedServerIp = serverIp.trim();
        List<ComponentConfig> monitorConfigs = componentConfigMapper.selectList(
                new LambdaQueryWrapper<ComponentConfig>()
                        .eq(ComponentConfig::getUserId, userId)
                        .eq(ComponentConfig::getServerIp, normalizedServerIp)
                        .eq(ComponentConfig::getConfigKey, SYSTEM_MONITOR_CONFIG_KEY)
        );

        if (monitorConfigs == null || monitorConfigs.isEmpty()) {
            throw new RuntimeException("未找到该服务器的运行中监控");
        }

        for (ComponentConfig config : monitorConfigs) {
            if (config != null && config.getId() != null) {
                config.setIsEnabled(enabled ? ENABLED : DISABLED);
                config.setUpdatedAt(java.time.LocalDateTime.now());
                componentConfigMapper.updateById(config);
            }
        }
    }

    public void updateConfigStatus(Long id, String appUsername, Integer isEnabled) {
        Long userId = resolveUserIdFromAppUsername(appUsername);
        if (userId == null) {
            throw new RuntimeException("未识别登录用户，无法更新监控状态");
        }
        if (id == null) {
            throw new RuntimeException("监控配置 ID 不能为空");
        }

        ComponentConfig config = componentConfigMapper.selectById(id);
        if (config == null || config.getUserId() == null || !config.getUserId().equals(userId)) {
            throw new RuntimeException("未找到对应监控配置");
        }

        config.setIsEnabled(resolveEnabledValue(isEnabled));
        config.setUpdatedAt(java.time.LocalDateTime.now());
        componentConfigMapper.updateById(config);
    }

    /**
     * 判断 AI 返回内容是否为“调用异常”类文案（而非正常 JSON 分析结果）。
     * 若为异常文案，入库时应改为固定提示“上报管理员，ai判断运行异常”。
     */
    private boolean isAiExceptionResponse(String analysis) {
        if (!StringUtils.hasText(analysis)) {
            return true;
        }
        String s = sanitizeAiJson(analysis);
        if (isExplicitAiFailureText(s)) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(s);
            if (root == null || !root.isObject()) {
                return false;
            }

            boolean hasMeaningfulFields = root.has("errorSummary") || root.has("analysisResult") || root.has("riskLevel");
            if (!hasMeaningfulFields) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean resolveMetricEnabled(Boolean value) {
        return value == null || Boolean.TRUE.equals(value);
    }

    private String buildSystemMonitorConfigValue(ComponentConfig config) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("cpuEnabled", resolveMetricEnabled(config.getCpuEnabled()));
        settings.put("memEnabled", resolveMetricEnabled(config.getMemEnabled()));
        settings.put("netRxEnabled", resolveMetricEnabled(config.getNetRxEnabled()));
        settings.put("netTxEnabled", resolveMetricEnabled(config.getNetTxEnabled()));
        settings.put("diskReadEnabled", resolveMetricEnabled(config.getDiskReadEnabled()));
        settings.put("diskWriteEnabled", resolveMetricEnabled(config.getDiskWriteEnabled()));

        try {
            return objectMapper.writeValueAsString(settings);
        } catch (Exception ignored) {
            return SYSTEM_MONITOR_CONFIG_VALUE;
        }
    }

    public Map<String, Object> addServerMonitor(ComponentConfig config) {
        Long resolvedUserId = resolveUserIdFromAppUsername(config.getAppUsername());
        if (resolvedUserId != null) {
            config.setUserId(resolvedUserId);
        } else if (config.getUserId() == null) {
            throw new RuntimeException("未识别登录用户，无法保存服务器监控");
        }

        String serverIp = config.getServerIp();
        String username = config.getUsername();
        String password = config.getPassword();

        if (!StringUtils.hasText(serverIp)) {
            throw new RuntimeException("服务器 IP 不能为空");
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("添加服务器监控时必须填写 SSH 用户名和密码");
        }

        config.setComponent(SYSTEM_MONITOR_COMPONENT);
        config.setConfigKey(SYSTEM_MONITOR_CONFIG_KEY);
        String monitorConfigValue = buildSystemMonitorConfigValue(config);
        config.setConfigValue(monitorConfigValue);
        config.setUseSudo(false);

        log.info("保存服务器监控配置：开始执行 SSH 连接验证 - {}@{}", username, serverIp);

        boolean connected = sshUtils.testConnection(serverIp, username, password);
        if (!connected) {
            log.warn("服务器监控 SSH 连接失败，创建Information记录: {}@{}", username, serverIp);
            createSshAuthFailureInfo(config);
            throw new RuntimeException("SSH连接失败：账号或密码错误，请检查后重试");
        }

        ComponentConfig existing = componentConfigMapper.selectOne(
                new LambdaQueryWrapper<ComponentConfig>()
                        .eq(ComponentConfig::getUserId, config.getUserId())
                        .eq(ComponentConfig::getServerIp, serverIp)
                        .eq(ComponentConfig::getConfigKey, SYSTEM_MONITOR_CONFIG_KEY)
        );

        if (existing != null) {
            existing.setUsername(username);
            existing.setPassword(password);
            existing.setUseSudo(false);
            existing.setComponent(SYSTEM_MONITOR_COMPONENT);
            existing.setConfigValue(monitorConfigValue);
            existing.setIsVerified(1);
            existing.setIsEnabled(ENABLED);
            existing.setUpdatedAt(java.time.LocalDateTime.now());
            componentConfigMapper.updateById(existing);
        } else {
            config.setIsVerified(1);
            config.setIsEnabled(ENABLED);
            config.setUpdatedAt(java.time.LocalDateTime.now());
            componentConfigMapper.insert(config);
        }

        Map<String, Object> sampleResult = monitorService.sampleMetricsOnce(serverIp, username, password, monitorConfigValue);

        Map<String, Object> result = new HashMap<>();
        result.put("serverIp", serverIp);
        result.put("component", SYSTEM_MONITOR_COMPONENT);
        result.put("sample", sampleResult);
        return result;
    }

    private boolean isExplicitAiFailureText(String analysis) {
        if (!StringUtils.hasText(analysis)) {
            return true;
        }

        return analysis.contains("调用 AI 服务时发生异常")
                || analysis.contains("未能获取有效回答")
                || analysis.contains("AI 服务暂时不可用")
                || analysis.contains("AI 服务连接中断或超时");
    }

    private void validateLogPathReadable(String logPath, String verifyResult) {
        String validationMessage = getLogPathValidationMessage(logPath, verifyResult);
        if (!StringUtils.hasText(validationMessage)) {
            return;
        }

        log.error("日志路径验证失败: {} -> {}", logPath, verifyResult == null ? "" : verifyResult.trim());
        throw new RuntimeException(validationMessage);
    }

    private boolean isLogPathValidationFailure(String verifyResult) {
        return verifyResult.contains("No such file")
                || verifyResult.contains("cannot open")
                || verifyResult.contains("Permission denied")
                || verifyResult.contains("is not in the sudoers file")
                || verifyResult.contains("a password is required")
                || verifyResult.contains("incorrect password attempt")
                || verifyResult.startsWith("sudo:")
                || verifyResult.contains("SSH Error");
    }

    private String buildLogPathValidationMessage(String logPath, String verifyResult) {
        if (verifyResult.contains("is not in the sudoers file")) {
            return "日志提权失败：当前 SSH 用户没有 sudo 权限";
        }
        if (verifyResult.contains("a password is required") || verifyResult.contains("incorrect password attempt")) {
            return "日志提权失败：sudo 密码校验失败，请确认 sudo 密码是否与 SSH 密码一致";
        }
        if (verifyResult.startsWith("sudo:")) {
            return "日志提权失败: " + verifyResult;
        }
        if (verifyResult.contains("Permission denied")) {
            return "日志路径不可读：当前 SSH 用户无权限读取 " + logPath;
        }
        if (verifyResult.contains("No such file")) {
            return "日志路径不存在: " + logPath;
        }
        if (verifyResult.contains("SSH Error")) {
            return "日志路径验证失败: " + verifyResult;
        }
        return "日志路径无效或不可读: " + logPath;
    }

    private String getLogPathValidationMessage(String logPath, String verifyResult) {
        if (!StringUtils.hasText(verifyResult)) {
            return null;
        }

        String normalized = verifyResult.trim();
        if (!isLogPathValidationFailure(normalized)) {
            return null;
        }
        return buildLogPathValidationMessage(logPath, normalized);
    }

    private String sanitizeAiJson(String analysis) {
        if (!StringUtils.hasText(analysis)) {
            return "";
        }
        String sanitized = analysis
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
        int start = sanitized.indexOf('{');
        int end = sanitized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return sanitized.substring(start, end + 1);
        }
        return sanitized;
    }

    /**
     * 解析 AI 返回的 JSON，将「遇到的问题」与「建议处理方式」分别写入 info。
     */
    private void parseAndSetAiResult(com.example.backend.entity.Information info,
                                     String analysisJson,
                                     Map<String, Object> result,
                                     String configuredComponent) {
        try {
            JsonNode root = objectMapper.readTree(sanitizeAiJson(analysisJson));
            if (root != null && root.isObject()) {
                String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel(
                        root.has("riskLevel") ? root.get("riskLevel").asText("") : String.valueOf(result.get("riskLevel"))
                );
                info.setComponent(InfoNormalizationUtils.normalizeComponent(configuredComponent));
                info.setErrorSummary(InfoNormalizationUtils.normalizeText(
                        root.has("errorSummary") ? root.get("errorSummary").asText("") : (String) result.get("summary"),
                        "无"
                ));
                info.setAnalysisResult(InfoNormalizationUtils.normalizeText(
                        root.has("analysisResult") ? root.get("analysisResult").asText("") : analysisJson,
                        "无"
                ));
                info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions(root.get("suggestedActions"), normalizedRiskLevel));
                info.setRiskLevel(normalizedRiskLevel);
                return;
            }
        } catch (Exception e) {
            log.warn("解析 AI 返回 JSON 失败，使用原始内容: {}", e.getMessage());
        }
        String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel(String.valueOf(result.get("riskLevel")));
        info.setComponent(InfoNormalizationUtils.normalizeComponent(configuredComponent));
        info.setErrorSummary(InfoNormalizationUtils.normalizeText((String) result.get("summary"), "无"));
        info.setAnalysisResult(InfoNormalizationUtils.normalizeText(analysisJson, "无"));
        info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions("", normalizedRiskLevel));
        info.setRiskLevel(normalizedRiskLevel);
    }

    // --- 自动诊断 ---

    public void diagnoseAndSave(ComponentConfig config) {
        try {
            Information info = buildDiagnosisInfo(config);
            informationMapper.insert(info);
            log.info("自动诊断完成并保存: {} - {}", config.getServerIp(), config.getComponent());
        } catch (Exception e) {
            log.error("自动诊断失败: {} - {}", config.getServerIp(), config.getComponent(), e);
        }
    }

    public Information buildDiagnosisInfo(ComponentConfig config) {
        if (config == null) {
            throw new RuntimeException("诊断配置不能为空");
        }

        String logPath = config.getConfigValue();
        if (!StringUtils.hasText(logPath)) {
            logPath = getLogPath(config.getServerIp(), config.getComponent(), config.getUsername(), config.getPassword(), config.getUseSudo());
        }

        Map<String, Object> result = executeDiagnosis(
                config.getServerIp(),
                config.getComponent(),
                logPath,
                config.getUsername(),
                config.getPassword(),
                isUseSudoEnabled(config.getUseSudo())
        );

        String analysis = (String) result.get("analysis");
        boolean noLogData = Boolean.TRUE.equals(result.get("noLogData"));
        boolean fetchFailure = Boolean.TRUE.equals(result.get("fetchFailure"));
        boolean aiException = isAiExceptionResponse(analysis);

        Information info = new Information();
        info.setUserId(config.getUserId() != null ? config.getUserId() : DEFAULT_USER_ID);
        info.setServerIp(config.getServerIp());
        info.setComponent(InfoNormalizationUtils.normalizeComponent(config.getComponent()));

        if (fetchFailure) {
            String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel(String.valueOf(result.get("riskLevel")));
            info.setErrorSummary(InfoNormalizationUtils.normalizeText((String) result.get("summary"), SSH_FETCH_FAILURE_SUMMARY));
            info.setAnalysisResult(InfoNormalizationUtils.normalizeText((String) result.get("rawLog"), SSH_FETCH_FAILURE_SUMMARY));
            info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions(
                    "检查目标服务器 SSH 连通性、账号密码、端口、日志路径权限以及 sudo 权限配置；确认日志文件存在且可读。",
                    normalizedRiskLevel
            ));
            info.setRiskLevel(normalizedRiskLevel);
        } else if (noLogData) {
            String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel("无");
            info.setErrorSummary(InfoNormalizationUtils.normalizeText("无", "无"));
            info.setAnalysisResult(InfoNormalizationUtils.normalizeText("无", "无"));
            info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions("", normalizedRiskLevel));
            info.setRiskLevel(normalizedRiskLevel);
        } else if (aiException) {
            String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel("高");
            info.setErrorSummary(InfoNormalizationUtils.normalizeText(AI_EXCEPTION_STORED_MESSAGE, "无"));
            info.setAnalysisResult(InfoNormalizationUtils.normalizeText(AI_EXCEPTION_STORED_MESSAGE, "无"));
            info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions(AI_EXCEPTION_STORED_MESSAGE, normalizedRiskLevel));
            info.setRiskLevel(normalizedRiskLevel);
        } else {
            parseAndSetAiResult(info, analysis, result, config.getComponent());
        }

        info.setRawLog(InfoNormalizationUtils.normalizeText((String) result.get("rawLog"), "无"));
        info.setCreatedAt(java.time.LocalDateTime.now());
        return info;
    }

    /**
     * 将验证通过的日志路径保存或更新到 componentconfig 表。
     * 新建时需设置 userId，避免表字段无默认值导致插入失败。
     */
    private void saveOrUpdateConfig(String serverIp, String component, String configKey, String value, String username, String password) {
        ComponentConfig existing = componentConfigMapper.selectOne(
                new LambdaQueryWrapper<ComponentConfig>()
                        .eq(ComponentConfig::getServerIp, serverIp)
                        .eq(ComponentConfig::getComponent, component)
                        .eq(ComponentConfig::getConfigKey, configKey)
        );

        if (existing != null) {
            existing.setConfigValue(value);
            if (StringUtils.hasText(username)) existing.setUsername(username);
            if (StringUtils.hasText(password)) existing.setPassword(password);
            existing.setUseSudo(false);
            existing.setIsVerified(1);
            existing.setIsEnabled(ENABLED);
            componentConfigMapper.updateById(existing);
        } else {
            ComponentConfig newConfig = new ComponentConfig();
            newConfig.setUserId(DEFAULT_USER_ID);
            newConfig.setServerIp(serverIp);
            newConfig.setComponent(component);
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(value);
            newConfig.setUsername(username);
            newConfig.setPassword(password);
            newConfig.setUseSudo(false);
            newConfig.setIsVerified(1);
            newConfig.setIsEnabled(ENABLED);
            componentConfigMapper.insert(newConfig);
        }
    }

    private int resolveEnabledValue(Integer isEnabled) {
        return isEnabled != null && isEnabled == DISABLED ? DISABLED : ENABLED;
    }

    private boolean isUseSudoEnabled(Boolean useSudo) {
        return Boolean.TRUE.equals(useSudo);
    }

    private String runLogCommand(String serverIp, String username, String password, String command) {
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            return sshUtils.exec(serverIp, username, password, command);
        }
        return sshUtils.exec(serverIp, command);
    }

    private String buildTailCommand(String logPath, int lines, boolean useSudo, String password) {
        String command = "tail -n " + lines + " " + logPath;
        return useSudo ? injectSudoPassword(command, password) : command;
    }

    private String injectSudoPassword(String command, String password) {
        if (!StringUtils.hasText(command)) {
            return command;
        }
        String escapedPwd = password == null ? "" : password.replace("\"", "\\\"");
        return "echo \"" + escapedPwd + "\" | sudo -S -p '' " + command;
    }
}

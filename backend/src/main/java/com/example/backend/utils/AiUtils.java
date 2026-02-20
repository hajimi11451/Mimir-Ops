package com.example.backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工具类
 * 用于与千帆 ModelBuilder (OpenAI 兼容接口) 交互，实现 RAG (Retrieval-Augmented Generation) + API 的功能。
 */
@Slf4j
@Component
public class AiUtils {

    @Value("${qianfan.v2.base-url}")
    private String baseUrl;

    @Value("${qianfan.v2.token}")
    private String token;

    @Value("${qianfan.v2.model-name}")
    private String modelName;

    // RAG 使用的知识库文件路径 (硬编码)
    private static final String RAG_FILE_PATH = "d:\\WorkSpace\\JavaWorkSpace\\aiOps\\backend\\src\\main\\resources\\info.md";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分析日志内容
     *
     * @param logContent 日志内容
     * @return 分析结果 (JSON 字符串或 "无问题")
     */
    public String analyzeLog(String logContent) {
        // 1. 读取 RAG 文件内容
        String ragContext = readRagFile();

        // 2. 构建 System Prompt
        String systemPrompt = "你是一名智能运维专家。请根据提供的日志信息进行分析。\n";
        
        if (StringUtils.hasText(ragContext)) {
            systemPrompt += "参考资料如下：\n" + ragContext + "\n\n";
        }

        systemPrompt += "请严格遵守以下输出规则：\n" +
                "1. 如果发现异常或错误，请返回一个标准的 JSON 对象，包含以下字段（除名称外均用中文）：\n" +
                "   - component: 当前组件名称\n" +
                "   - errorSummary: 问题摘要（一句话概括）\n" +
                "   - analysisResult: 遇到的问题（仅描述现象与原因，不要写建议；可多条分点）\n" +
                "   - suggestedActions: 建议处理方式（仅写解决步骤或建议，与 analysisResult 分开）\n" +
                "   - riskLevel: 风险等级 (如：高、中、低、无)\n" +
                "2. 如果日志无异常，上述字段中 errorSummary、analysisResult、suggestedActions 填\"无\"，riskLevel 填\"无\"。\n" +
                "3. 直接返回 JSON，不要包含 Markdown 标记 (如 ```json)。";

        // 3. 调用 API
        return callQianfanApi(systemPrompt, logContent);
    }

    /**
     * 获取 RAG 增强后的回答
     *
     * @param userQuery 用户的问题
     * @return AI 的回答
     */
    public String chatWithRag(String userQuery) {
        // 1. 读取 RAG 文件内容
        String ragContext = readRagFile();
        
        // 2. 构建 System Prompt (系统指令)
        // 在这里修改 System 内容，定义 AI 的角色和行为
        String systemPrompt = "你是一个专业的 AI 助手。";
        if (StringUtils.hasText(ragContext)) {
            systemPrompt += "\n以下是参考资料，请结合这些资料回答用户的问题，如果资料中没有相关信息，请根据你自己来思考有什么问题：\n" + ragContext;
        }

        // 3. 调用千帆/OpenAI 兼容 API
        return callQianfanApi(systemPrompt, userQuery);
    }

    /**
     * 让 AI 生成查找日志的 Linux 命令
     * @param component 组件名称
     * @param osType 操作系统类型
     * @return Linux 命令字符串
     */
    public String generateLogCommand(String component, String osType) {
        String systemPrompt = "你是一个 Linux 运维专家。根据组件名和操作系统，生成一条获取最近 50 行错误日志的命令。只返回命令字符串，不要Markdown，不要解释。优先使用标准路径。";
        String userPrompt = String.format("OS: %s, Component: %s", osType, component);
        // 调用现有的 callQianfanApi 方法
        String cmd = callQianfanApi(systemPrompt, userPrompt);
        // 简单清洗，防止 AI 返回 ```bash
        return cmd.replace("```bash", "").replace("```", "").trim();
    }

    /**
     * 读取 RAG 文件内容
     *
     * @return 文件内容字符串
     */
    private String readRagFile() {
        try {
            Path path = Paths.get(RAG_FILE_PATH);
            if (Files.exists(path)) {
                return Files.readString(path);
            } else {
                log.warn("RAG 文件不存在: {}", RAG_FILE_PATH);
                return ""; // 或者抛出异常，视业务需求而定
            }
        } catch (IOException e) {
            log.error("读取 RAG 文件失败", e);
            return "";
        }
    }

    /**
     * 自动分析历史数据并提取知识到 RAG 文件
     * @param dataList 格式化后的运维记录列表
     */
    public void analyzeAndExtractKnowledge(List<String> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            log.info("没有可分析的历史运维数据。");
            return;
        }

        // 1. 拼接数据
        StringBuilder sb = new StringBuilder("以下是最近7天的用户运维操作记录：\n");
        for (String record : dataList) {
            sb.append("- ").append(record).append("\n");
        }

        // 2. 定义 Prompt，强制要求输出格式
        String systemPrompt = "你是一个运维知识库构建专家。请分析数据，找出特定组件最常用的处理方式。\n" +
                "输出规则：\n" +
                "1. 格式严格为：针对组件：[组件名]；常规维护建议：用户高频选择执行 [处理方式] 操作。\n" +
                "2. 过滤掉无意义的偶发操作。\n" +
                "3. 如果无规律，直接回答“无”。\n" +
                "4. 请直接返回结果，不要包含 Markdown 格式标记 (如 ```json ... ```)。";

        // 3. 调用 AI
        log.info("开始调用 AI 提取高频运维知识...");
        String aiResponse = callQianfanApi(systemPrompt, sb.toString());

        // 4. 存入文件
        if (StringUtils.hasText(aiResponse) && !"无".equals(aiResponse.trim())) {
            log.info("发现有价值的知识，正在追加到 RAG 文件...");
            appendKnowledgeToRagFile(aiResponse);
        } else {
            log.info("AI 未发现明显的高频运维规律。");
        }
    }

    /**
     * 将知识以追加模式写入 RAG 文件 (info.md)
     * @param content 提取的知识内容
     */
    private void appendKnowledgeToRagFile(String content) {
        try {
            Path path = Paths.get(RAG_FILE_PATH);
            String finalContent = "\n\n### 自动归档知识 (" + LocalDate.now() + ")\n" + content;
            
            // 确保目录存在
            Files.createDirectories(path.getParent());
            
            // 如果文件不存在则创建，存在则追加
            Files.writeString(path, finalContent, 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            
            log.info("成功追加知识到文件: {}", RAG_FILE_PATH);
        } catch (IOException e) {
            log.error("追加知识到 RAG 文件失败", e);
        }
    }

    /**
     * 调用千帆 (OpenAI 兼容) API
     *
     * @param systemPrompt 系统提示词 (System Role)
     * @param userPrompt 用户提示词 (User Role)
     * @return AI 回答内容
     */
    private String callQianfanApi(String systemPrompt, String userPrompt) {
        try {
            // 构建完整的 API URL
            String url = baseUrl;
            if (!url.endsWith("/")) {
                url += "/";
            }
            url += "chat/completions";

            // 构建请求体 (OpenAI 格式)
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            
            List<Map<String, String>> messages = new ArrayList<>();
            
            // 添加 System 消息 (设置 AI 行为)
            if (StringUtils.hasText(systemPrompt)) {
                Map<String, String> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
                messages.add(systemMsg);
            }

            // 添加 User 消息 (用户问题)
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);
            
            requestBody.put("messages", messages);
            
            // 可以添加其他参数，如 temperature, top_p 等
            // requestBody.put("temperature", 0.7);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                // OpenAI 格式响应解析
                if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                    JsonNode choice = root.get("choices").get(0);
                    if (choice.has("message") && choice.get("message").has("content")) {
                         return choice.get("message").get("content").asText();
                    }
                } else if (root.has("error")) {
                     log.error("API 调用返回错误: {}", root.get("error").toString());
                     return "AI 服务暂时不可用: " + root.get("error").toString();
                }
            }
        } catch (Exception e) {
            log.error("调用 AI API 异常", e);
            return "调用 AI 服务时发生异常。";
        }
        return "未能获取有效回答。";
    }
}

package com.example.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.entity.UserProcess;
import com.example.backend.mapper.UserProcessMapper;
import com.example.backend.utils.AiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 知识库定时更新任务
 */
@Slf4j
@Component
public class RagUpdateTask {

    @Autowired
    private UserProcessMapper userProcessMapper;

    @Autowired
    private AiUtils aiUtils;

    /**
     * 每周日凌晨 2 点执行知识提取任务
     * 提取最近 7 天的运维操作记录，由 AI 总结高频规律并更新到 RAG 文件中
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void autoHarvestKnowledge() {
        log.info("定时任务启动：开始从最近 7 天的运维记录中提取知识...");
        
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        
        // 1. 查询最近 7 天且有意义的数据
        List<UserProcess> list = userProcessMapper.selectList(
            new LambdaQueryWrapper<UserProcess>()
                .ge(UserProcess::getProcessTime, sevenDaysAgo)
                .isNotNull(UserProcess::getComponent)
                .isNotNull(UserProcess::getProcessMethod)
                .ne(UserProcess::getComponent, "")
                .ne(UserProcess::getProcessMethod, "")
        );

        if (list.isEmpty()) {
            log.info("最近 7 天没有发现有效的运维记录，跳过知识提取。");
            return;
        }

        // 2. 格式化数据，只提取 AI 需要的关键字段 (减少 Token 消耗)
        List<String> formattedData = list.stream()
                .map(p -> "组件：" + p.getComponent() + " | 操作：" + p.getProcessMethod())
                .distinct() // 去重，相同组件的相同操作只传一次，节省 token 且更利于 AI 统计频率
                .collect(Collectors.toList());

        log.info("成功获取 {} 条格式化后的运维记录，正在提交 AI 分析...", formattedData.size());

        // 3. 调用 AI 分析并提取知识
        aiUtils.analyzeAndExtractKnowledge(formattedData);
    }
}

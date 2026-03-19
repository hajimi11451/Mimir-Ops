package com.example.backend.agent;

import com.example.backend.model.TaskState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.util.function.BooleanSupplier;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionContext {

    private String serverIp;

    private String username;

    private String password;

    private String approvedRiskCommand;

    private WebSocketSession session;

    private TaskState taskState;

    @Builder.Default
    private int maxRounds = 15;

    @Builder.Default
    private int usedRounds = 0;

    @Builder.Default
    private long startedAt = System.currentTimeMillis();

    @Builder.Default
    private boolean metricsRequested = false;

    private BooleanSupplier stopSignal;

    public boolean shouldStop() {
        return Thread.currentThread().isInterrupted() || (stopSignal != null && stopSignal.getAsBoolean());
    }

    public void consumeRound() {
        if (usedRounds >= maxRounds) {
            throw new AgentRoundLimitException("达到最大循环轮次(" + maxRounds + ")，任务暂停。你可以发送“继续”来恢复执行。");
        }
        usedRounds++;
    }
}

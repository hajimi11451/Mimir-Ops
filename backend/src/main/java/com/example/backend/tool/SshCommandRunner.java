package com.example.backend.tool;

import com.example.backend.utils.SshUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SshCommandRunner {

    private final SshUtils sshUtils;

    public SshUtils.SshResult execute(String serverIp,
                                      String username,
                                      String password,
                                      String command,
                                      long timeoutSeconds) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<SshUtils.SshResult> future = executor.submit(
                () -> sshUtils.execWithResult(serverIp, username, password, command)
        );
        try {
            return future.get(Math.max(timeoutSeconds, 1), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return new SshUtils.SshResult(-1, "SSH Error: 命令执行被中断。");
        } catch (TimeoutException e) {
            future.cancel(true);
            return new SshUtils.SshResult(-1, "SSH Error: 命令执行超时(" + timeoutSeconds + "s)，已中断。");
        } catch (Exception e) {
            return new SshUtils.SshResult(-1, "SSH Error: " + e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    public String trimOutputLines(String output, int maxLines) {
        if (output == null || output.isEmpty()) {
            return "";
        }

        String[] lines = output.split("\\R");
        if (lines.length <= maxLines) {
            return output;
        }

        return Arrays.stream(lines)
                .limit(maxLines)
                .collect(Collectors.joining(System.lineSeparator()))
                + System.lineSeparator()
                + "...(输出已截断，仅保留前 " + maxLines + " 行)";
    }
}

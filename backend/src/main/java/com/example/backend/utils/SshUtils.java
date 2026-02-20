package com.example.backend.utils;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * SSH 工具类 (真实实现)
 */
@Slf4j
@Component
public class SshUtils {

    /**
     * 执行 SSH 命令
     * @param ip 服务器IP
     * @param command 命令
     * @return 命令执行结果
     */
    public String exec(String ip, String command) {
        // 注意：实际项目中用户名和密码应从数据库或配置文件中安全获取
        // 这里为了演示“真实代码”逻辑，假设存在默认凭据或后续通过参数传入
        return exec(ip, "root", "123456", command);
    }

    /**
     * 测试 SSH 连接是否成功（仅连接，不执行命令）
     * @param ip 服务器IP（支持 IP:Port 格式）
     * @param user SSH用户名
     * @param password SSH密码
     * @return true=连接成功，false=连接失败
     */
    public boolean testConnection(String ip, String user, String password) {
        Session session = null;
        try {
            JSch jsch = new JSch();
            
            // 解析 IP 和端口 (支持 192.168.1.1:2222 格式)
            String host = ip;
            int port = 22;
            
            if (ip.contains(":")) {
                String[] parts = ip.split(":");
                if (parts.length == 2) {
                    host = parts[0];
                    try {
                        port = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        log.warn("解析端口失败，使用默认端口 22: {}", ip);
                    }
                }
            }
            
            session = jsch.getSession(user, host, port);
            session.setPassword(password);

            // 忽略主机密钥检查
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(5000); // 5秒超时

            session.connect();
            log.info("SSH 连接测试成功: {}@{}:{}", user, host, port);
            return true;
        } catch (Exception e) {
            log.error("SSH 连接测试失败: {}@{} - {}", user, ip, e.getMessage());
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    public String exec(String ip, String user, String password, String command) {
        log.info("正在服务器 {} 上执行命令: {}", ip, command);
        
        StringBuilder output = new StringBuilder();
        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            
            // 解析 IP 和端口 (支持 192.168.1.1:2222 格式)
            String host = ip;
            int port = 22;
            
            if (ip.contains(":")) {
                String[] parts = ip.split(":");
                if (parts.length == 2) {
                    host = parts[0];
                    try {
                        port = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        log.warn("解析端口失败，使用默认端口 22: {}", ip);
                    }
                }
            }
            
            session = jsch.getSession(user, host, port);
            session.setPassword(password);

            // 忽略主机密钥检查
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(5000); // 5秒超时

            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            channel.connect();

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            log.info("命令执行成功，返回长度: {}", output.length());
        } catch (Exception e) {
            log.error("SSH 执行异常: {}", e.getMessage());
            return "SSH Error: " + e.getMessage();
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }

        return output.toString();
    }
}

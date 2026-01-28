-- 创建数据库
CREATE DATABASE IF NOT EXISTS ai_ops_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_ops_db;

-- 1. 用户表 (User Table)
-- 用于存储登录/注册信息
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '用户表';

-- 2. 巡检记录表 (Inspection Record Table)
-- 存储出现的问题、关联账号、发生时间以及原始日志
CREATE TABLE IF NOT EXISTS information (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '关联的用户ID',
    server_ip VARCHAR(50) COMMENT '服务器IP',
    component VARCHAR(50) COMMENT '组件名称 (Nginx/Tomcat等)',
    error_summary VARCHAR(255) COMMENT '问题摘要',
    analysis_result TEXT COMMENT 'AI详细分析结果',
    raw_log TEXT COMMENT '原始日志内容 (源信息)',
    risk_level VARCHAR(20) COMMENT '风险等级',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    FOREIGN KEY (user_id) REFERENCES user(id)
) COMMENT '巡检记录表';

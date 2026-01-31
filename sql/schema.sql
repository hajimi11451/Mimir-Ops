-- 创建数据库
CREATE DATABASE IF NOT EXISTS ai_ops_db 
    DEFAULT CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE ai_ops_db;

-- 1. 用户表 (UserLogin Table)
CREATE TABLE IF NOT EXISTS userLogin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '用户表';

-- 2. 巡检记录表 (Inspection Record Table)
CREATE TABLE IF NOT EXISTS information (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '关联的用户ID',
    server_ip VARCHAR(50) COMMENT '服务器IP',
    component VARCHAR(50) COMMENT '组件名称 (Nginx/Tomcat等)',
    error_summary VARCHAR(255) COMMENT '问题摘要',
    analysis_result TEXT COMMENT 'AI详细分析结果',
    raw_log TEXT COMMENT '原始日志内容',
    risk_level VARCHAR(20) COMMENT '风险等级',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间'
) COMMENT '巡检记录表';

-- 3. 用户处理记录表
CREATE TABLE IF NOT EXISTS userProcess (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '关联的用户ID',
    server_ip VARCHAR(50) COMMENT '服务器IP',
    component VARCHAR(50) COMMENT '组件名称 (Nginx/Tomcat等)',
    process_method TEXT COMMENT 'AI处理方式',
    process_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间'
) COMMENT '用户处理记录表';
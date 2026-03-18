CREATE DATABASE IF NOT EXISTS `ai_ops_db`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `ai_ops_db`;

CREATE TABLE `userlogin` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '告警接收邮箱',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表'

CREATE TABLE `information` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '关联的用户ID',
  `server_ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务器IP',
  `component` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件名称 (Nginx/Tomcat等)',
  `error_summary` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问题摘要',
  `analysis_result` text COLLATE utf8mb4_unicode_ci COMMENT 'AI详细分析结果',
  `suggested_actions` text COLLATE utf8mb4_unicode_ci COMMENT '建议处理方式',
  `raw_log` text COLLATE utf8mb4_unicode_ci COMMENT '原始日志内容',
  `risk_level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险等级',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1093 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检记录表'

CREATE TABLE `userprocess` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '关联的用户ID',
  `server_ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务器IP',
  `component` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件名称 (Nginx/Tomcat等)',
  `problem_log` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '遇到的问题（日志信息）',
  `process_method` text COLLATE utf8mb4_unicode_ci COMMENT 'AI处理方式',
  `process_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户处理记录表'

CREATE TABLE `componentconfig` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '关联的用户ID',
  `server_ip` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务器IP',
  `username` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务器登录用户名',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务器登录密码',
  `use_sudo` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否使用sudo读取日志',
  `component` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组件名称',
  `config_key` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置项 (固定为 error_log_path)',
  `config_value` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '路径值',
  `is_verified` tinyint(1) DEFAULT '0' COMMENT '是否验证通过',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用检测',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组件配置表'

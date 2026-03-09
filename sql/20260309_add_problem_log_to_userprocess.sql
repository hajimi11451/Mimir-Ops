ALTER TABLE `userprocess`
  ADD COLUMN `problem_log` text COLLATE utf8mb4_unicode_ci COMMENT '遇到的问题（日志信息）' AFTER `component`;

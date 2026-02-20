# AI Ops Platform

AI Ops Platform 是一个集成了服务器监控、AI 辅助运维功能的现代化运维管理平台。

## 项目简介

本项目采用前后端分离架构，旨在提供便捷的服务器性能监控与智能化运维体验。
核心功能包括：
- **服务器监控**：通过 SSH 远程连接 Linux 服务器，实时采集 CPU、内存等关键指标。
- **数据可视化**：提供直观的监控仪表盘，展示实时数据与历史趋势图表。
- **多服务器管理**：支持动态添加和切换不同的服务器进行监控。
- **AI 赋能** (开发中)：集成百度千帆大模型 (Wenxin Yiyan)，提供智能运维建议。

## 技术栈

### 后端 (Backend)
- **核心框架**: Spring Boot 3.4.2
- **数据库 ORM**: MyBatis Plus 3.5.7
- **数据库**: MySQL 8.0+
- **远程连接**: JSch (SSH 协议支持)
- **AI SDK**: Qianfan SDK (Baidu Cloud)
- **构建工具**: Maven

### 前端 (Frontend)
- **核心框架**: Vue 3 (Composition API)
- **构建工具**: Vite
- **UI 组件库**: Element Plus
- **样式框架**: Tailwind CSS
- **图表库**: Chart.js
- **HTTP 客户端**: Axios

## 目录结构

```
aiOps/
├── backend/            # 后端 Spring Boot 项目源码
│   ├── src/main/java   # Java 源代码
│   ├── src/main/resources
│   │   ├── application.yml  # 后端配置文件
│   │   └── info.md          # RAG 知识库文件 (AI 功能用)
│   └── pom.xml         # Maven 依赖配置
├── front/              # 前端 Vue 项目源码
│   ├── src/            # Vue 源代码
│   │   ├── api/        # 接口请求定义
│   │   ├── views/      # 页面视图 (Dashboard.vue 等)
│   │   └── ...
│   ├── package.json    # npm 依赖配置
│   └── vite.config.js  # Vite 配置 (包含代理设置)
├── sql/                # 数据库脚本
│   └── schema.sql      # 数据库初始化 SQL
└── README.md           # 项目说明文档
```

## 快速开始

### 1. 环境准备
- JDK 17+
- Node.js 16+
- MySQL 8.0+
- Maven 3.6+

### 2. 数据库配置
1. 创建数据库 `ai_ops_db`。
2. 执行 `sql/schema.sql` 脚本初始化表结构。
3. 在 `componentconfig` 表中添加需要监控的服务器信息：
   ```sql
   INSERT INTO componentconfig (server_ip, username, password) VALUES ('192.168.1.100', 'root', 'your_password');
   ```

### 3. 后端启动
1. 修改配置文件 `backend/src/main/resources/application.yml`：
   - 配置数据库连接信息 (`spring.datasource`).
   - 配置百度千帆 API Key (如需使用 AI 功能).
   - 可选配置监控频率 (`monitor.schedule.fixed-rate`).
2. 进入 `backend` 目录并运行：
   ```bash
   mvn clean package -DskipTests
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```
   后端服务将启动在 `http://localhost:8080`。

### 4. 前端启动
1. 进入 `front` 目录：
   ```bash
   npm install
   npm run dev
   ```
2. 访问前端页面 (通常为 `http://localhost:5173`)。

## 功能配置

### 监控频率调整
在 `application.yml` 中修改 `monitor.schedule.fixed-rate` (单位：毫秒)，默认 60000 (1分钟)。

### 添加监控服务器
直接在数据库 `componentconfig` 表中插入新的服务器记录即可，无需重启服务。系统会自动识别并开始采集新服务器的数据。

## 注意事项
- 确保后端服务器能够通过 SSH (默认端口 22) 连接到目标监控服务器。
- 目标服务器需支持 `vmstat`, `free`, `uptime` 等基础 Linux 命令。

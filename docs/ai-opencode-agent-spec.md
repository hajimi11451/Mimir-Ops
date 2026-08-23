# LinshuOps OpenCode Agent 改造规格

## 1. 目标

将现有运维执行型 Agent 的“规划、上下文管理、工具选择、循环决策”逐步迁移到 OpenCode；Spring Boot 后端继续是唯一的业务控制面与执行权威。

本规格中的 OpenCode 指 `anomalyco/opencode` 稳定版 1.x。实现必须固定一个已验证版本，不能依赖 V2 beta 或在线文档中的未固定行为。

本次改造必须同时满足：

1. 不破坏现有 `ops_chat`、`risk_execute`、`ops_force_stop` 与图表相关 WebSocket 协议。
2. OpenCode 不获得 SSH 密码、私钥、数据库凭据或任意生产主机 shell 权限。
3. 可以通过配置立即回退到现有 Agent。
4. 首个可用版本仅开放只读运维能力；写操作必须在后续阶段接入一次性审批。

## 2. 范围与非目标

### 范围

- 执行型链路：计划、任务运行、工具调用、停止、恢复、最终总结。
- 将 OpenCode Session 与 LinshuOps 任务关联。
- 通过 Spring 受控工具网关访问监控、日志和 SSH 能力。
- OpenCode 事件转换为既有前端事件。
- 运行时切换：`legacy`、`shadow`、`opencode`。

### 非目标

- 不在第一阶段重写普通问答、日志分析、RAG 与意图识别；它们继续使用 `AiUtils`。
- 不在第一阶段把 OpenCode 暴露给浏览器。
- 不在第一阶段开放任意远程命令、自动修复或自动回滚。
- 不在第一阶段删除 `AgentOrchestrator`、`PlannerService`、`LlmClient` 与既有工具实现。

## 3. 目标架构

```text
Vue OpsAssistantView
        | existing WebSocket messages/events
        v
Spring WebSocket Handler / OpsExecutionService
        | runtime selection
        +-- LegacyOpsRuntime ------> existing OpsAgentService
        |
        +-- OpenCodeOpsRuntime ----> OpenCode REST + SSE
                                      |
                                      v
                              OpenCode Agent Runtime
                                      |
                         custom tool / remote MCP call
                                      |
                                      v
                           Spring Ops Tool Gateway
                         /          |             \
                  metrics       logs       restricted SSH
```

职责边界：

| 组件 | 责任 |
| --- | --- |
| OpenCode | 推理、计划、工具选择、会话上下文、任务循环、最终结构化输出 |
| Spring 控制层 | 用户与任务归属、目标服务器绑定、凭据解析、策略、审批、审计、限流、WebSocket 适配 |
| Ops Tool Gateway | 参数验证、目标机绑定、日志路径限制、命令白名单、SSH/监控真实调用 |
| 前端 | 展示计划、进度、审批与结果；不保存或回传可执行审批结论之外的敏感凭据 |

## 4. 运行时契约

新增 `OpsExecutionRuntime` 抽象，提供：

```java
AgentRunResult run(OpsExecutionRequest request);
void stop(String clientSessionId);
RuntimeMode mode();
```

`LegacyOpsRuntime` 仅适配现有 `OpsAgentService`。

`OpenCodeOpsRuntime` 负责：

1. 确保 OpenCode 服务健康；
2. 创建或恢复 OpenCode Session；
3. 发送带专用系统提示词的任务；
4. 订阅 SSE，并将进度、工具、权限、结束和错误事件转换为领域事件；
5. 在停止时调用 OpenCode abort；
6. 保存 `runId -> OpenCode sessionId -> 目标服务器引用` 映射。

运行时选择：

| 模式 | 行为 |
| --- | --- |
| `legacy` | 仅运行现有自研 Agent，默认值 |
| `shadow` | 现有 Agent 执行；OpenCode 只生成只读建议并记录评估，不影响真实操作 |
| `opencode` | OpenCode 驱动受控工具；第一阶段仅允许只读工具 |

## 5. 数据和会话模型

业务任务使用稳定 `runId`，不得再以 WebSocket ID 作为唯一任务主键。

```text
AgentRun
  runId
  clientSessionId
  runtimeMode
  status
  openCodeSessionId
  targetRef
  credentialRef
  pendingApprovalId
  lastEventAt
  createdAt / updatedAt
```

第一阶段允许使用线程安全内存仓储以兼容当前单实例应用，但接口必须独立，后续可切换到 MySQL/Redis。WebSocket 断线不应自动取消 AgentRun；只有显式停止或过期策略才可停止。

## 6. OpenCode 配置与隔离

OpenCode 必须作为独立 sidecar/worker 运行，优先绑定 `127.0.0.1` 或私有容器网络，并设置服务认证。Spring 是唯一调用方。

OpenCode 工作目录必须是专用、最小权限目录，不能直接把生产主机目录、用户 home、SSH 配置或项目源码以可写形式挂入。

专用 `linshu-ops` Agent 的策略：

- 禁止内置 `bash`、`edit`、`write`、`external_directory`、`webfetch` 与 `websearch`。
- 仅暴露 `linshu_*` 受控工具。
- 工具调用数量、总运行时间、输出大小和模型 token 使用量必须受限。
- 禁止在生产运行中启用自动批准所有权限的 CLI 参数。

现有千帆 OpenAI-compatible 接口可先作为自定义 Provider 使用；上线前必须验证模型的 tool calling、JSON 输出、超时和中文运维提示词表现。

## 7. 受控工具协议

初始工具集合：

| 工具 | 风险 | 第一阶段策略 |
| --- | --- | --- |
| `linshu_report_plan` | 无 | 允许；输出当前前端的 `plan` 事件 |
| `linshu_get_metrics` | 低 | 允许；服务器由 run 绑定，模型不得任意指定 IP |
| `linshu_read_log` | 中 | 允许受限路径与输出大小 |
| `linshu_run_readonly` | 中 | 仅白名单查询命令 |
| `linshu_finish_task` | 无 | 允许；输出总结与图表建议 |
| `linshu_execute_change` | 高 | 第一阶段拒绝；后续始终要求一次性审批 |

工具接口必须接收 `openCodeSessionId`，由 Spring 查找其绑定的 `AgentRun`。模型传入的服务器 IP、用户名、凭据或命令上下文均不视为可信输入。

写操作阶段的审批记录：

```text
Approval
  approvalId
  runId
  actorId
  toolName
  normalizedArgsHash
  targetRef
  expiresAt
  status: PENDING | APPROVED_ONCE | REJECTED | EXPIRED
```

前端只回传 `approvalId`；不得回传或拼接原始命令。审批只能使用一次。

## 8. WebSocket 兼容映射

| OpenCode/领域事件 | 保持的前端事件 |
| --- | --- |
| 计划已生成或 `linshu_report_plan` | `plan` |
| Agent 开始思考 | `ops_progress(agent_think)` |
| 工具开始/结束 | `step_start`、`tool_call`、`step_done` |
| 等待权限 | `ops_chat_result.needRiskConfirm=true` |
| Session 完成 | `task_done` 和最终 `ops_chat_result` |
| Session 失败 | `task_failed` |
| 用户停止 | `ops_force_stop_result` |

为了避免依赖 OpenCode 内部事件名称变化，计划、最终总结和审批等业务关键事件优先经 `linshu_*` 工具或 Spring 领域事件输出，SSE 只负责传递状态与增量内容。

## 9. 安全要求

1. 入口日志必须脱敏，不得记录 WebSocket 的密码字段、Token、完整 Prompt、完整工具参数或工具原始输出。
2. 不得通过 `echo password | sudo -S` 拼接 sudo 密码；后续应使用 SSH key、短期凭据和受限 sudoers。
3. 禁止将目标服务器密码、私钥或数据库密码发送给 OpenCode、模型 Provider 或 custom tool 进程。
4. 远程 SSH 应启用受信任主机指纹校验。
5. 命令安全不能只依赖关键字正则；默认使用结构化操作和命令模板，任意 shell 是受控例外。
6. 所有工具调用、审批和状态变更应具有可审计的 runId。
7. 每个会话最多一个运行任务；每台目标服务器最多一个写任务。

## 10. 分期实施

### P0：安全基线

- WebSocket 入站日志脱敏。
- 引入运行时配置和专用 OpenCode 工作目录配置。
- 不向 OpenCode 传递敏感凭据。

### P1：运行时骨架与只读 PoC（本次实现范围）

- 新增 `OpsExecutionRuntime`、旧 Agent 适配器、运行时路由。
- 新增 OpenCode HTTP 健康检查、Session 映射、停止能力和可测试 API 客户端。
- 添加 `opencode` 配置、专用 Agent/工具配置模板与 Feature Flag。
- 保持 `legacy` 为默认；`opencode` 未健康时安全回退或明确失败，绝不回退后重复执行。
- 修复 WebSocket 密码日志泄漏。

### P2：OpenCode 事件桥和只读工具

- 实现 SSE 事件桥。
- 实现监控、日志、白名单查询工具网关。
- 将计划和最终结构化输出映射为现有前端事件。

### P3：审批与变更工具

- 持久化 AgentRun/Approval。
- 接入一次性审批与目标服务器写锁。
- 开放受控变更工具和审计。

### P4：评估和清理

- 比较成功率、人工审批率、耗时、token 成本和错误恢复率。
- 仅在 OpenCode 路径稳定后移除旧执行循环。

## 11. 验收标准

P1 完成后必须满足：

1. 默认配置仍走 Legacy，现有前端流程可用。
2. `opencode` 配置可被解析，能健康检查和创建会话（使用 Mock 或本机服务）。
3. OpenCode 服务不可达时不会执行任何远程 SSH 操作。
4. 停止请求同时通知当前运行时。
5. WebSocket 日志不再输出 `password`、`token`、`authorization` 等敏感字段。
6. 单元测试覆盖运行时选择、OpenCode 健康失败、请求脱敏和 Legacy 委派。
7. Maven 编译与测试通过。


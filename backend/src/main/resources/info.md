# 运维日志分析知识库

## 风险分级补充规则
SSH检查：
    中风险：
        SSH/sshd 日志中仅出现 Failed password、Invalid user、authentication failure、Connection closed 或断连重试等暴力破解 迹象，但无任何登录成功、提权、持久化、后门落地或敏感篡改证据时，最高判定为“中”风险。
    高风险：
        仅当明确出现以下“已攻破”证据之一时，判定为“高”风险：
            登录成功（如 Accepted password、Accepted publickey、session opened），且成功前 5 分钟内同 IP 失败次数 ≥ 3次；
            提权成功、持久化植入、后门落地；
            敏感配置文件或数据被篡改。
    例外排除：同一 IP 在监测周期内仅出现多次成功登录且无任何失败记录的，视为正常运维行为，忽略其告警。

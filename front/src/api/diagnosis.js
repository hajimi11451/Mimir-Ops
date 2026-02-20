import request from '../utils/request'

/**
 * 1. 获取日志路径 (用于路径发现)
 * @param {string} serverIp 
 * @param {string} component 
 * @param {string} username (可选)
 * @param {string} password (可选)
 */
export function getLogPath(serverIp, component, username, password) {
  return request({
    url: '/diagnosis/logPath',
    method: 'get',
    params: { serverIp, component, username, password }
  })
}

/**
 * 2. 执行诊断 (核心功能)
 * @param {Object} data { serverIp, component, logPath }
 */
export function executeDiagnosis(data) {
  return request({
    url: '/diagnosis/execute',
    method: 'post',
    data
  })
}

/**
 * 3. AI 智能问答 (RAG Chat)
 * @param {string} query 
 */
export function chatWithAi(query) {
  return request({
    url: '/diagnosis/ai/chat',
    method: 'post',
    data: { query }
  })
}

/**
 * 4. 连接服务器 (资源管理)
 * @param {Object} serverInfo { ip, user, password, port }
 */
export function connectServer(serverInfo) {
  return request({
    url: '/diagnosis/server/connect',
    method: 'post',
    data: serverInfo
  })
}

// --- 监控配置 ---

export function addConfig(data) {
  return request({
    url: '/diagnosis/config/add',
    method: 'post',
    data
  })
}

export function listConfigs() {
  return request({
    url: '/diagnosis/config/list',
    method: 'get'
  })
}

export function deleteConfig(id) {
  return request({
    url: '/diagnosis/config/delete',
    method: 'post',
    data: { id }
  })
}

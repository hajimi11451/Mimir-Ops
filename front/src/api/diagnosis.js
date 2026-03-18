import request from '../utils/request'

const SSH_REQUEST_TIMEOUT = 30000

function getCurrentUsername() {
  try {
    const raw = localStorage.getItem('user')
    if (!raw) return ''
    const user = JSON.parse(raw)
    return user?.username || ''
  } catch (e) {
    return ''
  }
}

export function getLogPath(serverIp, component, username, password, useSudo = false) {
  return request({
    url: '/diagnosis/logPath',
    method: 'get',
    timeout: SSH_REQUEST_TIMEOUT,
    params: { serverIp, component, username, password, useSudo }
  })
}

export function executeDiagnosis(data) {
  return request({
    url: '/diagnosis/execute',
    method: 'post',
    data
  })
}

export function chatWithAi(query) {
  return request({
    url: '/diagnosis/ai/chat',
    method: 'post',
    data: { query }
  })
}

export function connectServer(serverInfo) {
  return request({
    url: '/diagnosis/server/connect',
    method: 'post',
    timeout: SSH_REQUEST_TIMEOUT,
    data: serverInfo
  })
}

export function addConfig(data) {
  return request({
    url: '/diagnosis/config/add',
    method: 'post',
    timeout: SSH_REQUEST_TIMEOUT,
    data: {
      ...(data || {}),
      appUsername: (data && data.appUsername) || getCurrentUsername()
    }
  })
}

export function addServerMonitor(data) {
  return request({
    url: '/diagnosis/server-monitor/add',
    method: 'post',
    timeout: SSH_REQUEST_TIMEOUT,
    data: {
      ...(data || {}),
      appUsername: (data && data.appUsername) || getCurrentUsername()
    }
  })
}

export function stopServerMonitor(serverIp, appUsername) {
  return request({
    url: '/diagnosis/server-monitor/stop',
    method: 'post',
    data: {
      serverIp,
      appUsername: appUsername || getCurrentUsername()
    }
  })
}

export function resumeServerMonitor(serverIp, appUsername) {
  return request({
    url: '/diagnosis/server-monitor/resume',
    method: 'post',
    data: {
      serverIp,
      appUsername: appUsername || getCurrentUsername()
    }
  })
}

export function updateConfigStatus(id, isEnabled, appUsername) {
  return request({
    url: '/diagnosis/config/status',
    method: 'post',
    data: {
      id,
      isEnabled,
      appUsername: appUsername || getCurrentUsername()
    }
  })
}

export function listConfigs(username) {
  return request({
    url: '/diagnosis/config/list',
    method: 'get',
    params: {
      username: username || getCurrentUsername()
    }
  })
}

export function deleteConfig(id, username) {
  return request({
    url: '/diagnosis/config/delete',
    method: 'post',
    data: {
      id,
      username: username || getCurrentUsername()
    }
  })
}

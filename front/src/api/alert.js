import request from '../utils/request'

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

export function getAlertContact(username) {
  return request({
    url: '/api/alert/contact',
    method: 'get',
    params: {
      username: username || getCurrentUsername(),
    },
  })
}

export function saveAlertContact(data) {
  return request({
    url: '/api/alert/contact',
    method: 'post',
    data: {
      ...(data || {}),
      username: (data && data.username) || getCurrentUsername(),
    },
  })
}

export function sendAlertTestMail(data) {
  return request({
    url: '/api/alert/test',
    method: 'post',
    timeout: 30000,
    data: {
      ...(data || {}),
      username: (data && data.username) || getCurrentUsername(),
    },
  })
}

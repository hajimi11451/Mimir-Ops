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

export function getSystemDashboard(ip, username) {
  return request({
    url: '/api/system/dashboard',
    method: 'get',
    params: { ip, username: username || getCurrentUsername() }
  })
}

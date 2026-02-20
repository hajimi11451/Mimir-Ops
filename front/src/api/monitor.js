import request from '../utils/request'

export function getSystemDashboard(ip) {
  return request({
    url: '/api/system/dashboard',
    method: 'get',
    params: { ip }
  })
}

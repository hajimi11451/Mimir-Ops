import request from '../utils/request'

/**
 * 用户登录
 * @param {Object} data { username, password }
 */
export function login(data) {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

/**
 * 用户注册
 * @param {Object} data { username, password, email, ... }
 */
export function register(data) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

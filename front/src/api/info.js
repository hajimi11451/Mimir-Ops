import request from '../utils/request'

/**
 * 查询所有信息
 */
export function selectAllInfo() {
  return request({
    url: '/info/selectAllInfo',
    method: 'post',
    data: {}
  })
}

/**
 * 按条件查询信息
 * @param {Object} params { serverIp, component, riskLevel, time }
 */
export function selectInfo(params) {
  return request({
    url: '/info/selectInfo',
    method: 'post',
    data: params
  })
}

/**
 * 存储用户处理记录
 * @param {Object} data { ... }
 */
export function insertProcess(data) {
  return request({
    url: '/info/insertProcess',
    method: 'post',
    data
  })
}

/**
 * 查询所有处理记录
 */
export function selectAllProcess() {
  return request({
    url: '/info/selectAllProcess',
    method: 'post',
    data: {}
  })
}

/**
 * 按条件查询处理记录
 * @param {Object} params
 */
export function selectProcess(params) {
  return request({
    url: '/info/selectProcess',
    method: 'post',
    data: params
  })
}

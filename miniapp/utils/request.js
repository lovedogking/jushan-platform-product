/**
 * 微信小程序请求封装
 * 基于 wx.request 的 Promise 包装，自动注入 Token 和统一错误处理
 */

const app = getApp()

/**
 * 基础请求
 */
function request(options) {
  return new Promise((resolve, reject) => {
    const token = app.globalData.token
    const header = {
      'Content-Type': 'application/json',
      ...(options.header || {}),
    }
    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    wx.request({
      url: `${app.globalData.apiBaseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header,
      success(res) {
        if (res.statusCode === 200 && res.data.code === 200) {
          resolve(res.data.data)
        } else if (res.statusCode === 401 || res.data.code === 401) {
          // Token 过期，清除登录态
          app.logout()
          wx.showToast({ title: '登录已过期', icon: 'none' })
          reject(new Error('未授权'))
        } else {
          wx.showToast({ title: res.data?.msg || '请求失败', icon: 'none' })
          reject(new Error(res.data?.msg || '请求失败'))
        }
      },
      fail(err) {
        wx.showToast({ title: '网络连接失败', icon: 'none' })
        reject(err)
      },
    })
  })
}

/**
 * GET 请求
 */
function get(url, params = {}) {
  const queryString = Object.keys(params)
    .filter(key => params[key] !== undefined && params[key] !== null)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  return request({
    url: queryString ? `${url}?${queryString}` : url,
    method: 'GET',
  })
}

/**
 * POST 请求
 */
function post(url, data = {}) {
  return request({ url, method: 'POST', data })
}

/**
 * PUT 请求
 */
function put(url, data = {}) {
  return request({ url, method: 'PUT', data })
}

/**
 * DELETE 请求
 */
function del(url, params = {}) {
  const queryString = Object.keys(params)
    .filter(key => params[key] !== undefined && params[key] !== null)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  return request({
    url: queryString ? `${url}?${queryString}` : url,
    method: 'DELETE',
  })
}

module.exports = { request, get, post, put, del }

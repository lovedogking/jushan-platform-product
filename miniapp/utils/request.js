/**
 * 微信小程序请求封装
 * 基于 wx.request 的 Promise 包装，自动注入 Token 和统一错误处理
 * 成功条件：后端 R.ok() 返回 code=0，历史契约同时支持 code=200；message 字段、traceId 保留、jushan_access_token
 */

const app = getApp()

/**
 * 安全解析响应体。
 * 处理 JSON 对象、空响应、字符串响应等情况。
 */
function safeParseBody(data) {
  if (data === null || data === undefined) {
    return null
  }
  if (typeof data === 'object') {
    return data
  }
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return null
    }
  }
  return null
}

/**
 * 构造统一错误对象，保留 message/code/status/traceId。
 */
function createApiError(message, code, status, traceId) {
  const error = new Error(message)
  error.code = code
  error.status = status
  error.traceId = traceId
  return error
}

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
        const statusCode = res.statusCode
        const body = safeParseBody(res.data)

        // 2xx 成功响应
        if (statusCode >= 200 && statusCode < 300) {
          // 有合法 JSON body 且 code===0 或 code===200
          if (body && (body.code === 0 || body.code === 200)) {
            resolve(body.data)
            return
          }
          // 有合法 JSON body 但业务失败
          if (body && body.code !== undefined) {
            handleErrorResponse(body, statusCode, reject)
            return
          }
          // 无 body 或 body 无 code 字段：视为成功但无数据
          resolve(null)
          return
        }

        // 非 2xx HTTP 响应
        if (statusCode === 401) {
          // 清除登录态
          app.logout()
          const err = createApiError(
            (body && body.message) || '未登录或登录已过期',
            body && body.code,
            statusCode,
            body && body.traceId,
          )
          reject(err)
          return
        }

        if (statusCode === 403) {
          // 403 不清除 Token
          const err = createApiError(
            (body && body.message) || '无权限访问',
            body && body.code,
            statusCode,
            body && body.traceId,
          )
          reject(err)
          return
        }

        // 其他非 2xx：尝试解析 body
        if (body) {
          handleErrorResponse(body, statusCode, reject)
        } else {
          reject(createApiError('请求失败', undefined, statusCode))
        }
      },
      fail(err) {
        // 网络失败 / 超时
        reject(createApiError(
          err.errMsg || '网络连接失败',
          undefined,
          0,
        ))
      },
    })
  })
}

/**
 * 处理错误响应 body（JSON 格式）。
 */
function handleErrorResponse(body, statusCode, reject) {
  const msg = body.message || '请求失败'
  const traceId = body.traceId
  const code = body.code
  reject(createApiError(msg, code, statusCode, traceId))
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

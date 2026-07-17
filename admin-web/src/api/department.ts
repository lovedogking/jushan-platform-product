import request from '@/utils/request'

export function getDepartmentTree() {
  return request({
    url: '/v1/departments/tree',
    method: 'get'
  })
}

export function getDepartmentPage(params: any) {
  return request({
    url: '/v1/departments',
    method: 'get',
    params
  })
}

export function getDepartmentDetail(id: number) {
  return request({
    url: `/v1/departments/${id}`,
    method: 'get'
  })
}

export function createDepartment(data: any) {
  return request({
    url: '/v1/departments',
    method: 'post',
    data
  })
}

export function updateDepartment(id: number, data: any) {
  return request({
    url: `/v1/departments/${id}`,
    method: 'put',
    data
  })
}

export function deleteDepartment(id: number) {
  return request({
    url: `/v1/departments/${id}`,
    method: 'delete'
  })
}

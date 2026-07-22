import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import UsersView from './UsersView.vue'

const adminApi = vi.hoisted(() => ({
  users: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  uploadAvatar: vi.fn(),
  resetPassword: vi.fn(),
}))

vi.mock('@/api', () => ({adminApi}))

describe('用户列表本地化回归测试', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  // Regression: ISSUE-004 — 中文用户管理页面直接显示英文角色和状态枚举
  // Found by /qa on 2026-07-23
  // Report: /tmp/rag-demo-qa-reports/qa-report-localhost-2026-07-23.md
  it('使用中文展示角色和账号状态', async () => {
    adminApi.users.mockResolvedValue([
      {id: 1, username: 'admin', nickname: '管理员', roles: ['ADMIN', 'USER'], status: 'ENABLED'},
      {id: 2, username: 'disabled', nickname: '停用用户', roles: ['USER'], status: 'DISABLED'},
    ])
    const wrapper = mount(UsersView, {attachTo: document.body, global: {plugins: [ElementPlus]}})
    await flushPromises()

    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('普通用户')
    expect(wrapper.text()).toContain('已启用')
    expect(wrapper.text()).toContain('已停用')
    expect(wrapper.text()).not.toContain('ENABLED')
    expect(wrapper.text()).not.toContain('DISABLED')
    wrapper.unmount()
  })
})

import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import ElementPlus, {ElMessageBox} from 'element-plus'
import UsersView from './UsersView.vue'

const adminApi = vi.hoisted(() => ({
  users: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  uploadAvatar: vi.fn(),
  resetPassword: vi.fn(),
}))

vi.mock('@/api', () => ({adminApi}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({user: {id: 99}, reloadUser: vi.fn()}),
}))

function clickButton(label: string, index = 0) {
  const buttons = [...document.querySelectorAll('button')]
    .filter(item => item.textContent?.trim() === label)
  expect(buttons.length).toBeGreaterThan(index)
  buttons[index]!.click()
}

describe('用户密码管理回归测试', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  // Regression: ISSUE-002 — 新增和重置用户时使用可预测的默认密码 123456
  // Found by /qa on 2026-07-23
  // Report: /tmp/rag-demo-qa-reports/qa-report-localhost-2026-07-23.md
  it('新增用户时要求管理员明确输入初始密码', async () => {
    adminApi.users.mockResolvedValue([])
    const wrapper = mount(UsersView, {attachTo: document.body, global: {plugins: [ElementPlus]}})
    await flushPromises()

    clickButton('新增用户')
    await flushPromises()

    const password = document.querySelector<HTMLInputElement>('input[type="password"]')
    expect(password).toBeTruthy()
    expect(password!.value).toBe('')
    wrapper.unmount()
  })

  it('重置密码时提交管理员输入的新密码', async () => {
    adminApi.users.mockResolvedValue([{
      id: 1,
      username: 'user',
      nickname: '演示用户',
      avatarUrl: '',
      roles: ['USER'],
      status: 'ENABLED',
    }])
    vi.spyOn(ElMessageBox, 'prompt').mockResolvedValue({value: 'NewPass123!'} as never)
    const wrapper = mount(UsersView, {attachTo: document.body, global: {plugins: [ElementPlus]}})
    await flushPromises()

    clickButton('重置密码')
    await flushPromises()

    expect(adminApi.resetPassword).toHaveBeenCalledWith(1, 'NewPass123!')
    wrapper.unmount()
  })
})

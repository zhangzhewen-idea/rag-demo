import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import KnowledgeAdminView from './KnowledgeAdminView.vue'
import UsersView from './UsersView.vue'

const adminApi = vi.hoisted(() => ({
  knowledge: vi.fn(),
  createKnowledge: vi.fn(),
  updateKnowledge: vi.fn(),
  users: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetPassword: vi.fn(),
}))

vi.mock('@/api', () => ({ adminApi }))

class ResizeObserverStub {
  observe() { }
  unobserve() { }
  disconnect() { }
}

function clickButton(label: string) {
  const button = [...document.querySelectorAll('button')].find(item => item.textContent?.trim() === label)
  expect(button, `未找到按钮：${label}`).toBeTruthy()
  button!.click()
}

describe('管理员表单校验', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub)
    adminApi.knowledge.mockResolvedValue([])
    adminApi.users.mockResolvedValue([])
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  // Regression: ISSUE-001 — 空知识库表单请求后端但页面没有反馈
  // Found by /qa on 2026-07-20
  // Report: .gstack/qa-reports/qa-report-localhost-2026-07-20.md
  it('知识库名称为空时显示提示且不发送请求', async () => {
    const wrapper = mount(KnowledgeAdminView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus], mocks: { $router: { push: vi.fn() } } },
    })
    await flushPromises()

    clickButton('新增知识库')
    await nextTick()
    await flushPromises()
    clickButton('保存')
    await flushPromises()

    expect(document.body.textContent).toContain('请填写知识库名称')
    expect(adminApi.createKnowledge).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  // Regression: ISSUE-002 — 空用户表单请求后端但页面没有反馈
  // Found by /qa on 2026-07-20
  // Report: .gstack/qa-reports/qa-report-localhost-2026-07-20.md
  it('账号和昵称为空时显示提示且不发送请求', async () => {
    const wrapper = mount(UsersView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    clickButton('新增用户')
    await nextTick()
    await flushPromises()
    clickButton('保存')
    await flushPromises()

    expect(document.body.textContent).toContain('请完善必填项')
    expect(adminApi.createUser).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

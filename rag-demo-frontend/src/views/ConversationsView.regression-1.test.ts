import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import ElementPlus, {ElMessageBox} from 'element-plus'
import ConversationsView from './ConversationsView.vue'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  rename: vi.fn(),
  remove: vi.fn(),
  push: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({push: mocks.push}),
}))

vi.mock('@/api', () => ({
  conversationApi: {
    list: mocks.list,
    rename: mocks.rename,
    remove: mocks.remove,
  },
}))

describe('历史会话回归测试', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  // Regression: ISSUE-001 — 中文界面的重命名弹窗显示英文操作按钮
  // Found by /qa on 2026-07-23
  // Report: /tmp/rag-demo-qa-reports/qa-report-localhost-2026-07-23.md
  it('重命名弹窗使用中文确认和取消按钮', async () => {
    mocks.list.mockResolvedValue([{id: 12, title: '技术开发规范问答', status: 'ACTIVE'}])
    const prompt = vi.spyOn(ElMessageBox, 'prompt').mockRejectedValue('cancel')
    const wrapper = mount(ConversationsView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    const rename = wrapper.findAll('button').find(button => button.text().trim() === '重命名')
    expect(rename).toBeTruthy()
    await rename!.trigger('click')
    await flushPromises()

    expect(prompt).toHaveBeenCalledWith('输入新标题', '重命名', expect.objectContaining({
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    }))
    wrapper.unmount()
  })
})

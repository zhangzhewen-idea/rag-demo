import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {nextTick} from 'vue'
import ElementPlus from 'element-plus'
import ChatView from './ChatView.vue'

const mocks = vi.hoisted(() => ({
  detail: vi.fn(),
  streamChat: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({params: {conversationId: '10'}}),
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({user: {nickname: '管理员'}}),
}))
vi.mock('@/api', () => ({
  conversationApi: {detail: mocks.detail},
}))
vi.mock('@/api/sse', () => ({streamChat: mocks.streamChat}))

describe('会话页面', () => {
  const scrollTo = vi.fn()

  beforeEach(() => {
    Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
      configurable: true,
      get: () => 800,
    })
    Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
      configurable: true,
      get: () => 300,
    })
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
      configurable: true,
      value: scrollTo,
    })
    mocks.detail.mockResolvedValue({
      conversation: {id: 10, userId: 1, knowledgeBaseId: 3, title: '技术开发规范', status: 'ACTIVE'},
      messages: [{
        id: 1,
        role: 'ASSISTANT',
        content: '历史回答',
        status: 'COMPLETED',
        createdAt: '2026-07-22T00:00:00',
      }],
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('进入会话后滚动到底部，并可通过悬浮按钮返回底部', async () => {
    const wrapper = mount(ChatView, {attachTo: document.body, global: {plugins: [ElementPlus]}})
    await flushPromises()

    expect(scrollTo).toHaveBeenCalledWith({top: 800, behavior: 'auto'})

    const messages = wrapper.get('section.messages')
    Object.defineProperty(messages.element, 'scrollTop', {configurable: true, value: 0, writable: true})
    await messages.trigger('scroll')

    const button = wrapper.get('button[aria-label="回到底部"]')
    expect(button.attributes('style')).not.toContain('display: none')
    await button.trigger('click')
    expect(scrollTo).toHaveBeenLastCalledWith({top: 800, behavior: 'smooth'})
    wrapper.unmount()
  })

  it('等待回答时展示证据检索描述和动态等待状态', async () => {
    vi.useFakeTimers()
    mocks.streamChat.mockReturnValue(new Promise(() => undefined))
    const wrapper = mount(ChatView, {attachTo: document.body, global: {plugins: [ElementPlus]}})
    await flushPromises()

    await wrapper.get('textarea').setValue('Java 规范是什么？')
    const send = wrapper.findAll('button').find(button => button.text().trim() === '发送')
    expect(send).toBeTruthy()
    await send!.trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('正在检索并整理知识库证据')
    expect(wrapper.text()).toContain('回答会严格依据当前知识库中的可用资料')
    expect(wrapper.findAll('.thinking-dots i')).toHaveLength(3)

    vi.advanceTimersByTime(2000)
    await nextTick()
    expect(wrapper.text()).toContain('已等待 2 秒')
    wrapper.unmount()
    vi.useRealTimers()
  })
})

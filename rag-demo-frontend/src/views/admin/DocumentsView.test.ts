import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {nextTick} from 'vue'
import ElementPlus from 'element-plus'
import DocumentsView from './DocumentsView.vue'

const adminApi = vi.hoisted(() => ({
  knowledge: vi.fn(),
  documents: vi.fn(),
  previewChunks: vi.fn(),
  upload: vi.fn(),
  retry: vi.fn(),
  removeDocument: vi.fn(),
}))

vi.mock('@/api', () => ({adminApi}))
vi.mock('vue-router', () => ({useRoute: () => ({query: {kb: '1'}})}))

class ResizeObserverStub {
  observe() {
  }

  unobserve() {
  }

  disconnect() {
  }
}

function clickButton(label: string) {
  const button = [...document.querySelectorAll('button')]
    .find(item => item.textContent?.trim() === label)
  expect(button, `未找到按钮：${label}`).toBeTruthy()
  button!.click()
}

describe('文档切片预览', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub)
    adminApi.knowledge.mockResolvedValue([
      {id: 1, name: '知识库', description: '', coverUrl: '', status: 'ENABLED'},
    ])
    adminApi.documents.mockResolvedValue([])
    adminApi.previewChunks.mockResolvedValue({
      configFingerprint: 'fingerprint',
      totalChunks: 1,
      previewedChunks: 1,
      truncated: false,
      statistics: {
        minCharacters: 4,
        maxCharacters: 4,
        averageCharacters: 4,
        shortChunkCount: 1,
      },
      chunks: [{
        index: 0,
        content: '预览内容',
        characterCount: 4,
        overlapCharacters: 0,
      }],
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  it('为知识库选择框提供名称且上传入口只有一个按钮语义', async () => {
    const wrapper = mount(DocumentsView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    const knowledgeSelect = document.querySelector('.head-actions [role="combobox"]')
    expect(knowledgeSelect?.getAttribute('aria-label')).toBe('选择知识库')

    const upload = document.querySelector('.head-actions .el-upload')
    expect(upload?.getAttribute('role')).toBe('button')
    expect(upload?.querySelector('.sr-only')?.textContent).toBe('上传文档')

    expect(upload?.querySelectorAll('button')).toHaveLength(0)
    const visualButton = upload?.querySelector('.el-button')
    expect(visualButton?.getAttribute('aria-hidden')).toBe('true')
    wrapper.unmount()
  })

  it('默认使用两个换行切分，参数变化后必须重新预览，重置恢复默认值', async () => {
    const wrapper = mount(DocumentsView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    const file = new File(['文档内容'], 'sample.txt', {type: 'text/plain'})
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', {value: [file]})
    await input.trigger('change')
    await flushPromises()

    expect(document.body.textContent).toContain('段落分隔（两个换行 \\n\\n）')
    expect(document.body.textContent).toContain('替换掉连续的空格、换行符和制表符')

    clickButton('生成预览')
    await flushPromises()

    expect(adminApi.previewChunks).toHaveBeenCalledWith(1, file, {
      strategy: 'CUSTOM',
      separator: '\n\n',
      maxChunkLength: 800,
      overlapLength: 100,
      normalizeWhitespace: true,
    })
    expect(document.body.textContent).toContain('预览内容')

    const maxLengthInput = document.querySelector<HTMLInputElement>('.el-input-number input')!
    maxLengthInput.value = '900'
    maxLengthInput.dispatchEvent(new Event('input', {bubbles: true}))
    maxLengthInput.dispatchEvent(new Event('change', {bubbles: true}))
    await nextTick()

    const confirm = [...document.querySelectorAll<HTMLButtonElement>('button')]
      .find(button => button.textContent?.includes('确认并开始向量入库'))!
    expect(confirm.disabled).toBe(true)

    clickButton('重置')
    await nextTick()
    expect(document.querySelector<HTMLInputElement>('.el-input-number input')!.value).toBe('800')
    wrapper.unmount()
  })

  it('将自定义分隔符中的转义写法转换为真实控制字符', async () => {
    const wrapper = mount(DocumentsView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    const file = new File(['文档内容'], 'sample.txt', {type: 'text/plain'})
    const fileInput = wrapper.find('input[type="file"]')
    Object.defineProperty(fileInput.element, 'files', {value: [file]})
    await fileInput.trigger('change')
    await flushPromises()

    document.querySelector<HTMLElement>('.chunk-form .el-select__wrapper')!.click()
    await nextTick()
    const customOption = [...document.querySelectorAll<HTMLElement>('[role="option"]')]
      .find(option => option.textContent?.trim() === '自定义')!
    customOption.click()
    await nextTick()

    const separatorInput = document.querySelector<HTMLInputElement>(
      '.chunk-form input[placeholder*="支持"]')!
    separatorInput.value = '\\r\\n\\t\\\\'
    separatorInput.dispatchEvent(new Event('input', {bubbles: true}))
    await nextTick()

    clickButton('生成预览')
    await flushPromises()

    expect(adminApi.previewChunks).toHaveBeenCalledWith(1, file, expect.objectContaining({
      separator: '\r\n\t\\',
    }))
    wrapper.unmount()
  })
})

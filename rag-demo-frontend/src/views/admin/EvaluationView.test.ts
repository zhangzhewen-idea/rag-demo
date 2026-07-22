import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import EvaluationView from './EvaluationView.vue'

const {adminApi, evaluationApi} = vi.hoisted(() => ({
  adminApi: {knowledge: vi.fn(), documents: vi.fn()},
  evaluationApi: {
    thresholds: vi.fn(),
    datasets: vi.fn(),
    runs: vi.fn(),
    run: vi.fn(),
    createDataset: vi.fn(),
    startRun: vi.fn(),
    review: vi.fn(),
  },
}))

vi.mock('@/api', () => ({adminApi, evaluationApi}))

class ResizeObserverStub {
  observe() {
  }

  unobserve() {
  }

  disconnect() {
  }
}

describe('RAG 评估页面', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub)
    evaluationApi.thresholds.mockResolvedValue({
      candidateHitRate: .9,
      candidateMrr: .7,
      contextRecall: .8,
      contextPrecision: .6,
      faithfulness: .8,
      answerRelevancy: .8,
      evidenceSupportAccuracy: .8,
      noAnswerAccuracy: .9,
      maxRegression: .03,
    })
    adminApi.knowledge.mockResolvedValue([{id: 3, name: '技术规范', status: 'ENABLED'}])
    adminApi.documents.mockResolvedValue([{
      id: 21,
      knowledgeBaseId: 3,
      originalName: 'Java开发规范.md',
      extension: 'md',
      fileSize: 1024,
      status: 'READY',
      chunkCount: 3,
      retryCount: 0,
    }])
    evaluationApi.datasets.mockResolvedValue([{
      id: 8,
      knowledgeBaseId: 3,
      name: '回归集',
      version: 'v1',
      caseCount: 1,
      createdBy: 1,
      createdAt: '2026-07-22T10:00:00',
      cases: [],
    }])
    const failedRun = {
      id: 12,
      datasetId: 8,
      status: 'FAILED',
      configSnapshot: '{}',
      scores: {
        candidateHitRate: 0,
        candidateMrr: 0,
        contextRecall: 0,
        contextPrecision: 0,
        faithfulness: 1,
        answerRelevancy: 1,
        evidenceSupportAccuracy: 1,
      },
      passed: false,
      totalCases: 1,
      completedCases: 1,
      failedCases: 0,
      promptTokens: 10,
      completionTokens: 10,
      latencyMs: 100,
      p95LatencyMs: 100,
      triggeredBy: 1,
      completedAt: '2026-07-22T10:01:00',
      results: [],
    }
    evaluationApi.runs.mockResolvedValue([failedRun])
    evaluationApi.run.mockResolvedValue(failedRun)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  // Regression: ISSUE-003 — 后端 FAILED 终态被当作非终态，门禁结论显示为短横线
  // Found by /qa on 2026-07-22
  it('显示未通过指标、门槛状态颜色和指标说明', async () => {
    const wrapper = mount(EvaluationView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    expect(document.body.textContent).toContain('质量门禁未通过')
    expect(document.body.textContent).toContain('未通过指标：候选命中率')
    expect(wrapper.get('[data-metric="candidateHitRate"]').classes()).toContain('metric-failed')
    expect(wrapper.get('[data-metric="faithfulness"]').classes()).toContain('metric-passed')
    expect(wrapper.get('[data-metric="noAnswerAccuracy"]').classes()).toContain('metric-not-applicable')

    await wrapper.get('button[aria-label="查看候选命中率说明"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('重排前的候选列表中，至少出现一条黄金证据')
    wrapper.unmount()
  })

  it('创建评估集时从当前知识库加载黄金证据文档', async () => {
    const wrapper = mount(EvaluationView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    const createButton = wrapper.findAll('button').find(button => button.text().includes('新建评估集'))
    expect(createButton).toBeDefined()
    await createButton!.trigger('click')
    await flushPromises()

    expect(adminApi.documents).toHaveBeenCalledWith(3)
    expect(document.body.querySelector('[data-testid="source-document-select"]')).not.toBeNull()
    wrapper.unmount()
  })
})

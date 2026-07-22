import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import EvaluationView from './EvaluationView.vue'

const {adminApi, evaluationApi} = vi.hoisted(() => ({
  adminApi: {knowledge: vi.fn()},
  evaluationApi: {
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
    adminApi.knowledge.mockResolvedValue([{id: 3, name: '技术规范', status: 'ENABLED'}])
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
  it('正确显示未通过的运行终态和质量门禁结论', async () => {
    const wrapper = mount(EvaluationView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    expect(document.body.textContent).toContain('质量门禁未通过')
    expect(document.body.textContent).toContain('未通过')
    wrapper.unmount()
  })
})

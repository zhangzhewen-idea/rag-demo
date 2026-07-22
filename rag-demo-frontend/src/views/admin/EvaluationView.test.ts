import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import ElementPlus, {ElMessageBox} from 'element-plus'
import EvaluationView from './EvaluationView.vue'

const {adminApi, evaluationApi} = vi.hoisted(() => ({
  adminApi: {knowledge: vi.fn(), documents: vi.fn()},
  evaluationApi: {
    thresholds: vi.fn(),
    datasets: vi.fn(),
    dataset: vi.fn(),
    runs: vi.fn(),
    run: vi.fn(),
    createDataset: vi.fn(),
    updateDataset: vi.fn(),
    removeDataset: vi.fn(),
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
    evaluationApi.dataset.mockResolvedValue({
      id: 8,
      knowledgeBaseId: 3,
      name: '回归集',
      version: 'v1',
      caseCount: 1,
      createdBy: 1,
      createdAt: '2026-07-22T10:00:00',
      cases: [{
        id: 81,
        question: '年假几天？',
        goldenAnswer: '十天',
        answerType: 'FACTUAL',
        critical: true,
        expectedContexts: [{sourceName: 'Java开发规范.md', evidenceContains: '年假十天'}],
      }],
    })
    evaluationApi.updateDataset.mockResolvedValue(undefined)
    evaluationApi.removeDataset.mockResolvedValue(undefined)
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
      results: [{
        id: 31,
        runId: 12,
        evaluationCase: {
          id: 41,
          question: '如何设计 API？',
          goldenAnswer: '遵循统一规范',
          answerType: 'FACTUAL',
          critical: false,
          expectedContexts: [],
        },
        execution: {
          caseId: 41,
          answer: '遵循统一规范',
          expandedQueries: [],
          candidates: [],
          finalEvidence: [],
          scores: {
            candidateHitRate: 1,
            candidateMrr: .5,
            contextRecall: .8,
            contextPrecision: .6,
            faithfulness: .9,
            answerRelevancy: .75,
            evidenceSupportAccuracy: .85,
            noAnswerAccuracy: 0,
          },
          promptTokens: 123,
          completionTokens: 45,
          latencyMs: 18718,
        },
        passed: false,
      }],
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

  it('逐题结果展示全部评估指标、Token 和延迟', async () => {
    const wrapper = mount(EvaluationView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    await wrapper.get('.case-results .el-collapse-item__header').trigger('click')
    await flushPromises()

    const scoreRow = wrapper.get('.case-score-row').text()
    expect(scoreRow).toContain('Hit 100.0%')
    expect(scoreRow).toContain('MRR 50.0%')
    expect(scoreRow).toContain('Recall 80.0%')
    expect(scoreRow).toContain('Precision 60.0%')
    expect(scoreRow).toContain('忠实度 90.0%')
    expect(scoreRow).toContain('答案相关性 75.0%')
    expect(scoreRow).toContain('证据准确率 85.0%')
    expect(scoreRow).toContain('拒答准确率 0.0%')
    expect(scoreRow).toContain('Prompt Token 123')
    expect(scoreRow).toContain('Completion Token 45')
    expect(scoreRow).toContain('延迟 18718 ms')
    wrapper.unmount()
  })

  it('在每个评估集记录右侧提供修改和删除操作', async () => {
    const wrapper = mount(EvaluationView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    const actions = [...document.querySelectorAll<HTMLButtonElement>('.dataset-actions button')]
    expect(actions.map(button => button.textContent?.trim())).toEqual(['修改', '删除'])

    actions.find(button => button.textContent?.trim() === '修改')!.click()
    await flushPromises()
    expect(evaluationApi.dataset).toHaveBeenCalledWith(8)
    expect(document.body.textContent).toContain('修改评估集')

    const save = [...document.querySelectorAll<HTMLButtonElement>('button')]
      .find(button => button.textContent?.trim() === '保存修改')!
    save.click()
    await flushPromises()
    expect(evaluationApi.updateDataset).toHaveBeenCalledWith(8, expect.objectContaining({
      name: '回归集',
      version: 'v1',
    }))
    wrapper.unmount()
  })

  it('确认后删除尚未运行的评估集', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue({action: 'confirm'} as never)
    const wrapper = mount(EvaluationView, {
      attachTo: document.body,
      global: {plugins: [ElementPlus]},
    })
    await flushPromises()

    const remove = [...document.querySelectorAll<HTMLButtonElement>('.dataset-actions button')]
      .find(button => button.textContent?.trim() === '删除')!
    remove.click()
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(evaluationApi.removeDataset).toHaveBeenCalledWith(8)
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

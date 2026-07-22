<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import {ElMessage, type FormInstance, type FormRules} from 'element-plus'
import {adminApi, evaluationApi} from '@/api'
import type {
  CreateEvaluationDataset,
  EvaluationAnswerType,
  EvaluationCaseResult,
  EvaluationDataset,
  EvaluationReviewVerdict,
  EvaluationRun,
  KnowledgeBase
} from '@/types'

const POLL_INTERVAL = 2_000
const ACTIVE_RUN_STATUSES = new Set(['QUEUED', 'RUNNING'])
const ANSWER_TYPES: Array<{ label: string; value: EvaluationAnswerType }> = [
  {label: '事实问答', value: 'FACTUAL'},
  {label: '步骤说明', value: 'PROCEDURE'},
  {label: '比较分析', value: 'COMPARISON'},
  {label: '拒答', value: 'REFUSAL'},
  {label: '摘要', value: 'SUMMARY'},
]
const STATUS_TEXT: Record<string, string> = {
  QUEUED: '排队中', RUNNING: '评估中', PASSED: '已通过', FAILED: '未通过', ERROR: '异常'
}

const knowledge = ref<KnowledgeBase[]>([])
const selectedKnowledgeBaseId = ref<number>()
const datasets = ref<EvaluationDataset[]>([])
const selectedDataset = ref<EvaluationDataset>()
const runs = ref<EvaluationRun[]>([])
const selectedRun = ref<EvaluationRun>()
const loading = ref(false)
const runLoading = ref(false)
const starting = ref(false)
const createVisible = ref(false)
const reviewVisible = ref(false)
const createFormRef = ref<FormInstance>()
const reviewingResult = ref<EvaluationCaseResult>()
let pollTimer: number | undefined

const form = reactive<CreateEvaluationDataset>({
  knowledgeBaseId: 0,
  name: '',
  version: 'v1',
  cases: [newCase()]
})
const reviewForm = reactive<{ verdict: EvaluationReviewVerdict; comment: string }>({
  verdict: 'ACCURATE', comment: ''
})
const rules: FormRules<CreateEvaluationDataset> = {
  knowledgeBaseId: [{required: true, message: '请选择知识库', trigger: 'change'}],
  name: [{required: true, whitespace: true, message: '请输入评估集名称', trigger: 'blur'}],
  version: [{required: true, whitespace: true, message: '请输入版本', trigger: 'blur'}],
}

const activeRun = computed(() => runs.value.find(run => ACTIVE_RUN_STATUSES.has(run.status)))
const completedPercent = computed(() => {
  const run = selectedRun.value
  if (!run?.totalCases) return 0
  return Math.round(run.completedCases / run.totalCases * 100)
})
const metrics = computed(() => {
  const scores = selectedRun.value?.scores
  if (!scores) return []
  return [
    ['候选命中率', scores.candidateHitRate],
    ['候选 MRR', scores.candidateMrr],
    ['上下文召回率', scores.contextRecall],
    ['上下文精确率', scores.contextPrecision],
    ['忠实度', scores.faithfulness],
    ['答案相关性', scores.answerRelevancy],
    ['证据支持准确率', scores.evidenceSupportAccuracy],
    ['拒答准确率', scores.noAnswerAccuracy],
  ]
})

onMounted(async () => {
  try {
    knowledge.value = await adminApi.knowledge()
    selectedKnowledgeBaseId.value = knowledge.value[0]?.id
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
})
onBeforeUnmount(stopPolling)

watch(selectedKnowledgeBaseId, async id => {
  selectedDataset.value = undefined
  selectedRun.value = undefined
  runs.value = []
  if (id) await loadDatasets(id)
})

watch(selectedDataset, async dataset => {
  stopPolling()
  selectedRun.value = undefined
  runs.value = []
  if (dataset) await loadRuns(dataset.id)
})

function newCase() {
  return {
    question: '', goldenAnswer: '', answerType: 'FACTUAL' as EvaluationAnswerType,
    critical: false, expectedContexts: [{sourceName: '', evidenceContains: ''}]
  }
}

function stopPolling() {
  if (pollTimer !== undefined) window.clearTimeout(pollTimer)
  pollTimer = undefined
}

function schedulePolling() {
  stopPolling()
  if (activeRun.value && selectedDataset.value) {
    pollTimer = window.setTimeout(() => void loadRuns(selectedDataset.value!.id, false), POLL_INTERVAL)
  }
}

async function loadDatasets(knowledgeBaseId: number) {
  loading.value = true
  try {
    datasets.value = await evaluationApi.datasets(knowledgeBaseId)
    selectedDataset.value = datasets.value[0]
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadRuns(datasetId: number, showLoading = true) {
  if (showLoading) runLoading.value = true
  try {
    runs.value = await evaluationApi.runs(datasetId)
    if (!selectedRun.value || ACTIVE_RUN_STATUSES.has(selectedRun.value.status)) {
      selectedRun.value = runs.value[0]
    }
    if (selectedRun.value) selectedRun.value = await evaluationApi.run(selectedRun.value.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    if (showLoading) runLoading.value = false
    schedulePolling()
  }
}

function openCreate() {
  Object.assign(form, {
    knowledgeBaseId: selectedKnowledgeBaseId.value ?? knowledge.value[0]?.id ?? 0,
    name: '', version: 'v1', cases: [newCase()]
  })
  createVisible.value = true
}

function changeAnswerType(index: number) {
  const item = form.cases[index]
  if (!item) return
  item.expectedContexts = item.answerType === 'REFUSAL'
    ? []
    : (item.expectedContexts.length ? item.expectedContexts : [{sourceName: '', evidenceContains: ''}])
}

function validateCases() {
  for (let index = 0; index < form.cases.length; index++) {
    const item = form.cases[index]!
    if (!item.question.trim() || !item.goldenAnswer.trim()) {
      ElMessage.warning(`请完整填写第 ${index + 1} 条样本的问题和黄金答案`)
      return false
    }
    if (item.answerType !== 'REFUSAL' && (!item.expectedContexts.length || item.expectedContexts.some(
      evidence => !evidence.sourceName.trim() || !evidence.evidenceContains.trim()
    ))) {
      ElMessage.warning(`请完整填写第 ${index + 1} 条样本的黄金证据`)
      return false
    }
  }
  return true
}

async function createDataset() {
  if (!await createFormRef.value?.validate().catch(() => false) || !validateCases()) return
  try {
    const created = await evaluationApi.createDataset(form)
    createVisible.value = false
    ElMessage.success('评估集已创建')
    await loadDatasets(form.knowledgeBaseId)
    selectedDataset.value = datasets.value.find(item => item.id === created.id) ?? datasets.value[0]
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function startRun() {
  if (!selectedDataset.value || starting.value) return
  starting.value = true
  try {
    const created = await evaluationApi.startRun(selectedDataset.value.id)
    ElMessage.success('评估任务已启动')
    await loadRuns(selectedDataset.value.id, false)
    selectedRun.value = await evaluationApi.run(created.id)
    schedulePolling()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    starting.value = false
  }
}

async function selectRun(run: EvaluationRun) {
  runLoading.value = true
  try {
    selectedRun.value = await evaluationApi.run(run.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    runLoading.value = false
  }
}

function openReview(result: EvaluationCaseResult) {
  reviewingResult.value = result
  reviewForm.verdict = result.reviewVerdict ?? 'ACCURATE'
  reviewForm.comment = result.reviewComment ?? ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewingResult.value || !selectedRun.value) return
  try {
    await evaluationApi.review(reviewingResult.value.id, reviewForm.verdict, reviewForm.comment)
    reviewVisible.value = false
    selectedRun.value = await evaluationApi.run(selectedRun.value.id)
    ElMessage.success('人工复核已保存')
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function percent(value?: number) {
  return value == null ? '-' : `${(value * 100).toFixed(1)}%`
}

function dateTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', {hour12: false}) : '-'
}

function statusType(status: string) {
  return ({PASSED: 'success', FAILED: 'danger', ERROR: 'danger', RUNNING: 'warning', QUEUED: 'info'} as const)[status] ?? 'info'
}
</script>

<template>
  <div class="page evaluation-page">
    <header class="page-head">
      <div>
        <span class="eyebrow">RAG QUALITY GATE</span>
        <h1>RAG 评估</h1>
        <p>用固定评估集验证检索、生成与拒答质量，持续阻断指标退化。</p>
      </div>
      <div class="head-actions">
        <el-select v-model="selectedKnowledgeBaseId" placeholder="选择知识库" style="width: 220px">
          <el-option v-for="kb in knowledge" :key="kb.id" :label="kb.name" :value="kb.id"/>
        </el-select>
        <el-button type="primary" :disabled="!knowledge.length" @click="openCreate">新建评估集</el-button>
      </div>
    </header>

    <div class="evaluation-layout">
      <aside class="panel dataset-panel" v-loading="loading">
        <div class="panel-head"><h2>评估集</h2><span>{{ datasets.length }} 个版本</span></div>
        <button v-for="dataset in datasets" :key="dataset.id"
                :class="['dataset-item', {active: selectedDataset?.id === dataset.id}]"
                @click="selectedDataset = dataset">
          <strong>{{ dataset.name }}</strong>
          <span><el-tag size="small">{{ dataset.version }}</el-tag>{{ dataset.caseCount }} 条样本</span>
          <small>{{ dateTime(dataset.createdAt) }}</small>
        </button>
        <el-empty v-if="!loading && !datasets.length" description="暂无评估集" :image-size="80"/>
      </aside>

      <section class="panel runs-panel" v-loading="runLoading">
        <div class="panel-head">
          <div><h2>运行记录</h2><span v-if="selectedDataset">{{ selectedDataset.name }} · {{ selectedDataset.version }}</span></div>
          <el-button type="primary" :loading="starting" :disabled="!selectedDataset || !!activeRun" @click="startRun">
            {{ activeRun ? '评估进行中' : '开始评估' }}
          </el-button>
        </div>
        <el-table v-if="runs.length" :data="runs" size="small" @row-click="selectRun">
          <el-table-column prop="id" label="运行" width="82"><template #default="{row}">#{{ row.id }}</template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template></el-table-column>
          <el-table-column label="结论" width="90"><template #default="{row}"><span v-if="row.status === 'PASSED' || row.status === 'FAILED'" :class="row.passed ? 'pass-text' : 'fail-text'">{{ row.passed ? '通过' : '未通过' }}</span><span v-else>-</span></template></el-table-column>
          <el-table-column label="进度" min-width="100"><template #default="{row}">{{ row.completedCases }}/{{ row.totalCases }}</template></el-table-column>
          <el-table-column label="完成时间" min-width="160"><template #default="{row}">{{ dateTime(row.completedAt) }}</template></el-table-column>
        </el-table>
        <el-empty v-else description="选择评估集后开始第一次运行" :image-size="90"/>
      </section>
    </div>

    <section v-if="selectedRun" class="run-detail">
      <div class="detail-head">
        <div>
          <span class="eyebrow">RUN #{{ selectedRun.id }}</span>
          <h2>评估报告</h2>
        </div>
        <div class="run-summary">
          <el-tag :type="statusType(selectedRun.status)">{{ STATUS_TEXT[selectedRun.status] ?? selectedRun.status }}</el-tag>
          <strong v-if="selectedRun.status === 'PASSED' || selectedRun.status === 'FAILED'" :class="selectedRun.passed ? 'pass-text' : 'fail-text'">{{ selectedRun.passed ? '质量门禁通过' : '质量门禁未通过' }}</strong>
        </div>
      </div>

      <el-progress v-if="ACTIVE_RUN_STATUSES.has(selectedRun.status)" :percentage="completedPercent" :stroke-width="12"/>
      <el-alert v-if="selectedRun.errorMessage" type="error" :title="selectedRun.errorMessage" show-icon :closable="false"/>

      <div v-if="metrics.length" class="evaluation-metrics">
        <article v-for="metric in metrics" :key="metric[0]">
          <span>{{ metric[0] }}</span><b>{{ percent(metric[1] as number | undefined) }}</b>
        </article>
      </div>
      <div class="run-meta">
        <span>样本 {{ selectedRun.completedCases }}/{{ selectedRun.totalCases }}</span>
        <span>失败 {{ selectedRun.failedCases }}</span>
        <span>P95 延迟 {{ selectedRun.p95LatencyMs }} ms</span>
        <span>Token {{ selectedRun.promptTokens + selectedRun.completionTokens }}</span>
        <span v-if="selectedRun.baselineRunId">基线 #{{ selectedRun.baselineRunId }}</span>
      </div>

      <el-collapse v-if="selectedRun.results.length" class="case-results">
        <el-collapse-item v-for="(result, index) in selectedRun.results" :key="result.id" :name="result.id">
          <template #title>
            <div class="case-title">
              <el-tag size="small" :type="result.passed ? 'success' : 'danger'">{{ result.passed ? '通过' : '未通过' }}</el-tag>
              <b>{{ index + 1 }}. {{ result.evaluationCase.question }}</b>
              <el-tag v-if="result.evaluationCase.critical" size="small" type="danger" effect="plain">关键样本</el-tag>
              <span v-if="result.reviewVerdict" class="reviewed">已复核</span>
            </div>
          </template>
          <div class="case-body">
            <div class="answer-grid">
              <article><h4>黄金答案</h4><p>{{ result.evaluationCase.goldenAnswer }}</p></article>
              <article><h4>模型回答</h4><p>{{ result.execution.answer || result.execution.errorMessage || '-' }}</p></article>
            </div>
            <div v-if="result.execution.scores" class="case-score-row">
              <span>Hit {{ percent(result.execution.scores.candidateHitRate) }}</span>
              <span>Recall {{ percent(result.execution.scores.contextRecall) }}</span>
              <span>Precision {{ percent(result.execution.scores.contextPrecision) }}</span>
              <span>忠实度 {{ percent(result.execution.scores.faithfulness) }}</span>
              <span>证据准确率 {{ percent(result.execution.scores.evidenceSupportAccuracy) }}</span>
              <span>{{ result.execution.latencyMs }} ms</span>
            </div>
            <el-collapse v-if="result.execution.finalEvidence.length">
              <el-collapse-item title="查看最终证据" name="evidence">
                <article v-for="(evidence, evidenceIndex) in result.execution.finalEvidence" :key="`${evidence.documentId}-${evidence.chunkIndex}`" class="evidence-card">
                  <b>{{ evidenceIndex + 1 }}. {{ evidence.sourceName }}</b>
                  <small>切片 {{ evidence.chunkIndex }} · 相似度 {{ percent(evidence.similarityScore) }}</small>
                  <p>{{ evidence.excerpt }}</p>
                </article>
              </el-collapse-item>
            </el-collapse>
            <p v-if="result.execution.judgeRationale" class="judge"><b>裁判说明：</b>{{ result.execution.judgeRationale }}</p>
            <div class="review-row">
              <span v-if="result.reviewVerdict">人工结论：<b>{{ result.reviewVerdict === 'ACCURATE' ? '准确' : '不准确' }}</b><template v-if="result.reviewComment"> · {{ result.reviewComment }}</template></span>
              <span v-else>尚未人工复核</span>
              <el-button type="primary" plain size="small" @click="openReview(result)">人工复核</el-button>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </section>

    <el-dialog v-model="createVisible" title="新建评估集" width="880px" top="4vh">
      <el-form ref="createFormRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="知识库" prop="knowledgeBaseId"><el-select v-model="form.knowledgeBaseId" class="wide"><el-option v-for="kb in knowledge" :key="kb.id" :label="kb.name" :value="kb.id"/></el-select></el-form-item>
          <el-form-item label="评估集名称" prop="name"><el-input v-model="form.name" maxlength="128"/></el-form-item>
          <el-form-item label="版本" prop="version"><el-input v-model="form.version" maxlength="64"/></el-form-item>
        </div>
        <div class="samples-head"><h3>评估样本</h3><span>最多 100 条</span></div>
        <section v-for="(item, index) in form.cases" :key="index" class="sample-card">
          <div class="sample-title"><b>样本 {{ index + 1 }}</b><el-button v-if="form.cases.length > 1" text type="danger" @click="form.cases.splice(index, 1)">移除</el-button></div>
          <el-form-item label="问题"><el-input v-model="item.question" type="textarea" :rows="2" maxlength="4000" show-word-limit/></el-form-item>
          <el-form-item label="黄金答案"><el-input v-model="item.goldenAnswer" type="textarea" :rows="2"/></el-form-item>
          <div class="sample-options">
            <el-form-item label="答案类型"><el-select v-model="item.answerType" @change="changeAnswerType(index)"><el-option v-for="type in ANSWER_TYPES" :key="type.value" :label="type.label" :value="type.value"/></el-select></el-form-item>
            <el-form-item label="关键样本"><el-switch v-model="item.critical" active-text="失败时阻断门禁"/></el-form-item>
          </div>
          <template v-if="item.answerType !== 'REFUSAL'">
            <div class="evidence-title"><b>黄金证据</b><el-button text type="primary" @click="item.expectedContexts.push({sourceName: '', evidenceContains: ''})">添加证据</el-button></div>
            <div v-for="(evidence, evidenceIndex) in item.expectedContexts" :key="evidenceIndex" class="evidence-form">
              <el-input v-model="evidence.sourceName" placeholder="来源文件名" maxlength="255"/>
              <el-input v-model="evidence.evidenceContains" placeholder="证据中必须包含的文本" maxlength="1000"/>
              <el-button :disabled="item.expectedContexts.length === 1" text type="danger" @click="item.expectedContexts.splice(evidenceIndex, 1)">移除</el-button>
            </div>
          </template>
          <el-alert v-else title="拒答样本不填写黄金证据，用于验证系统能否正确拒绝无依据问题。" type="info" :closable="false"/>
        </section>
        <el-button class="wide" :disabled="form.cases.length >= 100" @click="form.cases.push(newCase())">添加样本</el-button>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" @click="createDataset">创建评估集</el-button></template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="人工复核" width="500px">
      <el-form label-position="top">
        <el-form-item label="复核结论"><el-radio-group v-model="reviewForm.verdict"><el-radio-button value="ACCURATE">准确</el-radio-button><el-radio-button value="INACCURATE">不准确</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="复核备注"><el-input v-model="reviewForm.comment" type="textarea" :rows="4" maxlength="1000" show-word-limit/></el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewVisible = false">取消</el-button><el-button type="primary" @click="submitReview">保存复核</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.evaluation-layout{display:grid;grid-template-columns:320px 1fr;gap:18px}.panel,.run-detail{background:rgba(255,255,255,.88);border:1px solid #fff;border-radius:20px;box-shadow:0 10px 35px rgba(65,45,112,.07)}.panel{padding:20px;min-height:330px}.panel-head,.detail-head,.run-summary,.sample-title,.samples-head,.evidence-title,.review-row{display:flex;align-items:center;justify-content:space-between}.panel-head{margin-bottom:14px}.panel-head h2,.detail-head h2{margin:0}.panel-head span{color:#746d7d;font-size:13px}.dataset-panel{display:flex;flex-direction:column;gap:8px}.dataset-item{display:flex;flex-direction:column;align-items:flex-start;gap:8px;padding:14px;border:1px solid #ece7f7;border-radius:13px;background:#fff;color:inherit;text-align:left;cursor:pointer}.dataset-item:hover,.dataset-item.active{border-color:#8a69ef;background:#f4f0ff}.dataset-item>span{display:flex;align-items:center;gap:8px;color:#676071}.dataset-item small{color:#8a8491}.runs-panel .el-table{cursor:pointer}.run-detail{padding:26px;margin-top:20px}.run-summary{gap:12px}.evaluation-metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:20px}.evaluation-metrics article{padding:17px;border:1px solid #eee9f7;border-radius:14px;background:#faf9fe}.evaluation-metrics span{color:#6f6879;font-size:13px}.evaluation-metrics b{display:block;margin-top:8px;font-size:24px}.run-meta,.case-score-row{display:flex;flex-wrap:wrap;gap:18px;margin:18px 0;color:#6d6677}.case-results{border-top:1px solid #ece7f5}.case-title{display:flex;align-items:center;gap:10px;min-width:0}.case-title b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.case-body{padding:4px 18px 18px}.answer-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.answer-grid article,.judge,.evidence-card{padding:14px;border-radius:12px;background:#f8f6fc}.answer-grid h4{margin:0 0 8px}.answer-grid p,.judge,.evidence-card p{white-space:pre-wrap;line-height:1.65;margin:0}.case-score-row span{padding:6px 10px;border-radius:999px;background:#f0ecfa;font-size:12px}.evidence-card{margin-bottom:8px}.evidence-card small{display:block;color:#787180;margin:5px 0}.review-row{margin-top:15px}.reviewed,.pass-text{color:#219168}.fail-text{color:#d04a5d}.form-grid{display:grid;grid-template-columns:1fr 1.5fr 1fr;gap:14px}.samples-head{margin:10px 0}.samples-head h3{margin:0}.samples-head span{color:#837c8b;font-size:13px}.sample-card{padding:18px;margin-bottom:14px;border:1px solid #e9e3f5;border-radius:15px;background:#fbfaff}.sample-title{margin-bottom:10px}.sample-options{display:grid;grid-template-columns:1fr 1fr;gap:16px}.evidence-title{margin-bottom:9px}.evidence-form{display:grid;grid-template-columns:210px 1fr auto;gap:8px;margin-bottom:8px}@media(max-width:1180px){.evaluation-layout{grid-template-columns:280px 1fr}.evaluation-metrics{grid-template-columns:repeat(2,1fr)}}
</style>

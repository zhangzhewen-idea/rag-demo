<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {ElMessage, ElMessageBox, type UploadFile} from 'element-plus'
import {adminApi} from '@/api'
import type {ChunkingConfig, ChunkPreview, DocumentTask, KnowledgeBase} from '@/types'

const POLL_INTERVAL = 2_000
const ACTIVE_STATUSES = new Set(['PENDING', 'PROCESSING', 'DELETING'])
const STATUS_TEXT: Record<string, string> = {
  PENDING: '等待处理',
  PROCESSING: '处理中',
  READY: '已入库',
  FAILED: '失败',
  DELETING: '删除中',
}

const route = useRoute()
const knowledge = ref<KnowledgeBase[]>([])
const selected = ref<number>()
const list = ref<DocumentTask[]>([])
const loading = ref(false)
const uploading = ref(false)
const previewing = ref(false)
const uploadDialog = ref(false)
const selectedFile = ref<File>()
const preview = ref<ChunkPreview>()
const accept = '.txt,.md,.pdf,.doc,.docx'
const CUSTOM_SEPARATOR = '__custom__'
const DEFAULT_CONFIG = {
  strategy: 'CUSTOM' as const,
  separatorPreset: '\n\n',
  customSeparator: '',
  maxChunkLength: 800,
  overlapLength: 100,
  normalizeWhitespace: true,
}
const chunking = reactive({...DEFAULT_CONFIG})
let pollTimer: number | undefined
let requestId = 0

const ready = computed(() => list.value.filter(item => item.status === 'READY').length)
const hasActiveTasks = computed(() => list.value.some(item => ACTIVE_STATUSES.has(item.status)))
const resolvedSeparator = computed(() => chunking.separatorPreset === CUSTOM_SEPARATOR
  ? chunking.customSeparator : chunking.separatorPreset)
const previewValid = computed(() => !!preview.value && preview.value.totalChunks > 0)

onMounted(async () => {
  knowledge.value = await adminApi.knowledge()
  selected.value = Number(route.query.kb) || knowledge.value[0]?.id
})

onBeforeUnmount(stopPolling)
watch(selected, () => {
  requestId++
  stopPolling()
  void load(true)
})
watch(chunking, () => {
  preview.value = undefined
}, {deep: true})

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
}

function schedulePolling() {
  stopPolling()
  if (hasActiveTasks.value) {
    pollTimer = window.setTimeout(() => void load(false), POLL_INTERVAL)
  }
}

async function load(showLoading = true) {
  if (!selected.value) return
  stopPolling()
  const currentRequest = ++requestId
  if (showLoading) loading.value = true
  try {
    const documents = await adminApi.documents(selected.value)
    if (currentRequest !== requestId) return
    list.value = documents
  } catch (error) {
    if (showLoading) ElMessage.error((error as Error).message)
  } finally {
    if (currentRequest === requestId) {
      if (showLoading) loading.value = false
      schedulePolling()
    }
  }
}

function selectFile(uploadFile: UploadFile) {
  if (!uploadFile.raw) return
  selectedFile.value = uploadFile.raw
  resetConfig()
  uploadDialog.value = true
}

function requestConfig(): ChunkingConfig {
  return {
    strategy: chunking.strategy,
    separator: chunking.strategy === 'CUSTOM' ? resolvedSeparator.value : null,
    maxChunkLength: chunking.maxChunkLength,
    overlapLength: chunking.overlapLength,
    normalizeWhitespace: chunking.normalizeWhitespace,
  }
}

function validateChunking() {
  if (chunking.strategy === 'CUSTOM' && !resolvedSeparator.value) {
    ElMessage.warning('请选择或输入分隔符')
    return false
  }
  if (Array.from(resolvedSeparator.value).length > 20) {
    ElMessage.warning('分隔符不能超过 20 个字符')
    return false
  }
  if (chunking.maxChunkLength < 100 || chunking.maxChunkLength > 4000) {
    ElMessage.warning('最大长度必须在 100 到 4000 之间')
    return false
  }
  if (chunking.overlapLength < 0 || chunking.overlapLength > 500
    || chunking.overlapLength >= chunking.maxChunkLength) {
    ElMessage.warning('重叠长度必须在 0 到 500 之间且小于最大长度')
    return false
  }
  return true
}

async function generatePreview() {
  if (!selected.value || !selectedFile.value || !validateChunking()) return
  previewing.value = true
  try {
    preview.value = await adminApi.previewChunks(selected.value, selectedFile.value, requestConfig())
    if (!preview.value.totalChunks) ElMessage.warning('文档没有生成有效切片')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    previewing.value = false
  }
}

async function upload() {
  if (!selected.value || !selectedFile.value || !preview.value || !validateChunking()) return
  uploading.value = true
  try {
    await adminApi.upload(selected.value, selectedFile.value, requestConfig(),
      preview.value.configFingerprint)
    ElMessage.success('文件已上传，正在异步处理')
    uploadDialog.value = false
    await load(false)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    uploading.value = false
  }
}

function resetConfig() {
  Object.assign(chunking, DEFAULT_CONFIG)
  preview.value = undefined
}

function closeUploadDialog() {
  selectedFile.value = undefined
  preview.value = undefined
}

async function retry(id: number) {
  try {
    await adminApi.retry(id)
    ElMessage.success('已提交重试')
    await load(false)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function remove(id: number) {
  await ElMessageBox.confirm('将同时删除向量与物理文件，确认继续？', '删除文档', {type: 'warning'})
  await adminApi.removeDocument(id)
  await load(false)
}

function tag(status: string) {
  return ({
    READY: 'success',
    FAILED: 'danger',
    PROCESSING: 'warning',
    PENDING: 'info',
    DELETING: 'warning'
  } as const)[status as keyof typeof STATUS_TEXT] ?? 'info'
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <span class="eyebrow">DOCUMENT INGESTION</span>
        <h1>文档管理</h1>
        <p>上传后自动完成解析、切片和向量入库；“已入库”表示可以直接检索与问答。</p>
      </div>
      <div class="head-actions">
        <el-select v-model="selected" placeholder="选择知识库" style="width: 220px">
          <el-option v-for="kb in knowledge" :key="kb.id" :label="kb.name" :value="kb.id"/>
        </el-select>
        <el-upload :show-file-list="false" :accept="accept" :auto-upload="false"
                   :on-change="selectFile">
          <el-button type="primary" :loading="uploading" :disabled="!selected">上传文档</el-button>
        </el-upload>
      </div>
    </header>

    <div class="inline-stats">
      <span>文档总数 <b>{{ list.length }}</b></span>
      <span>已入库 <b>{{ ready }}</b></span>
      <span v-if="hasActiveTasks" class="muted">任务状态每 2 秒自动刷新</span>
    </div>

    <el-table v-loading="loading" :data="list" class="glass-table">
      <el-table-column prop="originalName" label="文件名" min-width="250"/>
      <el-table-column prop="extension" label="类型" width="90"/>
      <el-table-column label="大小" width="120">
        <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
      </el-table-column>
      <el-table-column label="入库状态" width="120">
        <template #default="{ row }">
          <el-tag :type="tag(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="切片" width="90"/>
      <el-table-column label="失败原因" min-width="220">
        <template #default="{ row }"><span class="error-text">{{ row.failureReason || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button v-if="row.status === 'FAILED'" text type="primary" @click="retry(row.id)">
            重试
          </el-button>
          <el-button text type="danger" :disabled="ACTIVE_STATUSES.has(row.status)"
                     @click="remove(row.id)">删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="selected && !loading && !list.length"
              description="暂无文档，上传一个开始构建知识库"/>

    <el-dialog v-model="uploadDialog" title="上传并预览切片" width="900px"
               destroy-on-close @closed="closeUploadDialog">
      <div class="upload-file-row">
        <strong>{{ selectedFile?.name }}</strong>
        <span>{{ selectedFile ? (selectedFile.size / 1024).toFixed(1) : 0 }} KB</span>
      </div>

      <el-form label-position="top" class="chunk-form">
        <el-form-item label="切片模式">
          <el-radio-group v-model="chunking.strategy">
            <el-radio-button value="AUTO">自动切片</el-radio-button>
            <el-radio-button value="CUSTOM">自定义切片</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="chunking.strategy === 'CUSTOM'">
          <el-form-item label="分隔符">
            <el-select v-model="chunking.separatorPreset">
              <el-option label="段落分隔（两个换行 \n\n）" :value="'\n\n'"/>
              <el-option label="单个换行（\n）" :value="'\n'"/>
              <el-option label="中文句号（。）" value="。"/>
              <el-option label="英文句号（.）" value="."/>
              <el-option label="中文分号（；）" value="；"/>
              <el-option label="自定义" :value="CUSTOM_SEPARATOR"/>
            </el-select>
            <el-input v-if="chunking.separatorPreset === CUSTOM_SEPARATOR"
                      v-model="chunking.customSeparator" maxlength="20" show-word-limit
                      placeholder="输入普通文本分隔符，不支持正则表达式"/>
          </el-form-item>
          <div class="chunk-number-row">
            <el-form-item label="最大长度（字符）">
              <el-input-number v-model="chunking.maxChunkLength" :min="100" :max="4000"/>
            </el-form-item>
            <el-form-item label="重叠长度（字符）">
              <el-input-number v-model="chunking.overlapLength" :min="0" :max="500"/>
            </el-form-item>
          </div>
          <el-form-item label="文本预处理规则">
            <el-checkbox v-model="chunking.normalizeWhitespace">
              替换掉连续的空格、换行符和制表符
            </el-checkbox>
          </el-form-item>
        </template>
      </el-form>

      <div class="preview-actions">
        <el-button @click="resetConfig">重置</el-button>
        <el-button type="primary" :loading="previewing" @click="generatePreview">
          {{ preview ? '重新生成预览' : '生成预览' }}
        </el-button>
      </div>

      <template v-if="preview">
        <div class="preview-stats">
          <span>共 <b>{{ preview.totalChunks }}</b> 个 chunks</span>
          <span>平均 <b>{{ preview.statistics.averageCharacters.toFixed(0) }}</b> 字符</span>
          <span>最长 <b>{{ preview.statistics.maxCharacters }}</b></span>
          <span>短块 <b>{{ preview.statistics.shortChunkCount }}</b></span>
        </div>
        <p v-if="preview.truncated" class="muted">
          仅展示前 {{ preview.previewedChunks }} / {{ preview.totalChunks }} 个 chunks
        </p>
        <div class="chunk-preview-list">
          <article v-for="chunk in preview.chunks" :key="chunk.index" class="chunk-preview-item">
            <header>
              <strong>Chunk #{{ chunk.index + 1 }}</strong>
              <span>
                <template v-if="chunk.overlapCharacters">重叠 {{ chunk.overlapCharacters }} · </template>
                {{ chunk.characterCount }} 字符
              </span>
            </header>
            <p>{{ chunk.content }}</p>
          </article>
        </div>
      </template>

      <template #footer>
        <el-button @click="uploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!previewValid"
                   @click="upload">确认并开始向量入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.upload-file-row,
.preview-stats,
.chunk-preview-item header,
.preview-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.upload-file-row {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.chunk-form {
  margin-top: 18px;
}

.chunk-number-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.preview-actions {
  justify-content: flex-end;
  margin-bottom: 16px;
}

.preview-stats {
  justify-content: flex-start;
  flex-wrap: wrap;
  padding: 12px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.chunk-preview-list {
  display: grid;
  gap: 12px;
  max-height: 420px;
  margin-top: 12px;
  overflow-y: auto;
}

.chunk-preview-item {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fbfaff;
}

.chunk-preview-item header span,
.upload-file-row span {
  color: #827c89;
}

.chunk-preview-item p {
  margin: 10px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .chunk-number-row {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {ElMessage, ElMessageBox} from 'element-plus'
import {adminApi} from '@/api'
import type {DocumentTask, KnowledgeBase} from '@/types'

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
const accept = '.txt,.md,.pdf,.doc,.docx'
let pollTimer: number | undefined
let requestId = 0

const ready = computed(() => list.value.filter(item => item.status === 'READY').length)
const hasActiveTasks = computed(() => list.value.some(item => ACTIVE_STATUSES.has(item.status)))

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

async function upload(options: { file: File }) {
  if (!selected.value) return
  uploading.value = true
  try {
    await adminApi.upload(selected.value, options.file)
    ElMessage.success('文件已上传，正在异步处理')
    await load(false)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    uploading.value = false
  }
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
        <el-upload :show-file-list="false" :accept="accept" :http-request="upload">
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
  </div>
</template>

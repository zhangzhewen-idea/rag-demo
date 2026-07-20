<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { conversationApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Conversation } from '@/types'

const list = ref<Conversation[]>([])
const router = useRouter()
const loading = ref(true)

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await conversationApi.list()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function rename(item: Conversation) {
  try {
    const { value } = await ElMessageBox.prompt('输入新标题', '重命名', {
      inputValue: item.title,
      inputValidator: value => Boolean(value.trim()) || '请输入会话标题',
    })
    await conversationApi.rename(item.id, value.trim())
    ElMessage.success('已重命名')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error((error as Error).message)
  }
}

async function remove(id: number) {
  try {
    await ElMessageBox.confirm('删除后将不再显示该会话，确认继续？', '删除会话', { type: 'warning' })
    await conversationApi.remove(id)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error((error as Error).message)
  }
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div><span class="eyebrow">CONVERSATIONS</span><h1>历史会话</h1><p>仅显示属于你的会话记录。</p></div>
      <el-button type="primary" @click="router.push('/knowledge-bases')">新建会话</el-button>
    </header>
    <el-table v-loading="loading" :data="list" class="glass-table">
      <el-table-column prop="title" label="会话标题" min-width="280" />
      <el-table-column prop="status" label="状态" width="120"><template #default="{ row }"><el-tag type="success">{{ row.status === 'ACTIVE' ? '进行中' : row.status }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="250"><template #default="{ row }"><el-button text type="primary" @click="router.push(`/chat/${row.id}`)">继续问答</el-button><el-button text @click="rename(row)">重命名</el-button><el-button text type="danger" @click="remove(row.id)">删除</el-button></template></el-table-column>
    </el-table>
  </div>
</template>

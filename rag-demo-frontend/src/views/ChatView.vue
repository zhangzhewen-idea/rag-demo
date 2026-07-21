<script setup lang="ts">
import {nextTick, onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {ElMessage, type InputInstance} from 'element-plus'
import {useAuthStore} from '@/stores/auth'
import {conversationApi} from '@/api'
import {streamChat} from '@/api/sse'
import type {Conversation, Message, Reference} from '@/types'

const route = useRoute()
const auth = useAuthStore()
const id = Number(route.params.conversationId)
const conversation = ref<Conversation>()
const messages = ref<Message[]>([])
const question = ref('')
const generating = ref(false)
const references = ref<Reference[]>([])
const drawer = ref(false)
const controller = ref<AbortController>()
const questionInput = ref<InputInstance>()

onMounted(load)

async function load() {
  const data = await conversationApi.detail(id)
  conversation.value = data.conversation
  messages.value = data.messages
  await nextTick()
  questionInput.value?.focus()
}

async function send() {
  const text = question.value.trim()
  if (!text || generating.value) return
  question.value = ''
  references.value = []
  messages.value.push({
    id: Date.now(),
    role: 'USER',
    content: text,
    status: 'COMPLETED',
    createdAt: new Date().toISOString()
  })
  const answer: Message = {
    id: Date.now() + 1,
    role: 'ASSISTANT',
    content: '',
    status: 'COMPLETED',
    createdAt: new Date().toISOString()
  }
  messages.value.push(answer)
  generating.value = true
  controller.value = new AbortController()
  try {
    await streamChat(id, text, {
      delta: value => {
        answer.content += value
        nextTick(() => document.querySelector('.messages')?.scrollTo({
          top: 999999,
          behavior: 'smooth'
        }))
      },
      references: value => {
        references.value = value
      },
      done: () => {
        answer.status = 'COMPLETED'
      },
      error: value => {
        answer.status = 'FAILED'
        if (!answer.content) answer.content = value
        ElMessage.error(value)
      },
    }, controller.value.signal)
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      answer.status = 'FAILED'
      if (!answer.content) answer.content = (error as Error).message || '回答生成失败，请稍后重试'
      ElMessage.error(answer.content)
    } else {
      answer.status = 'CANCELLED'
    }
  } finally {
    generating.value = false
  }
}

function stop() {
  controller.value?.abort()
}

function reuseQuestion(index: number) {
  for (let i = index - 1; i >= 0; i--) {
    if (messages.value[i]?.role === 'USER') {
      question.value = messages.value[i].content
      break
    }
  }
}

async function copy(text: string) {
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}
</script>

<template>
  <div class="chat-page">
    <header class="chat-head">
      <div><span class="status-dot"/><b>{{ conversation?.title }}</b><small>知识库
        #{{ conversation?.knowledgeBaseId }}</small></div>
      <el-button v-if="references.length" @click="drawer = true">查看来源 {{
          references.length
        }}
      </el-button>
    </header>
    <section class="messages">
      <div v-if="!messages.length" class="chat-empty">
        <div class="spark">✦</div>
        <h2>想从知识库中了解什么？</h2>
        <p>我只会基于可靠证据回答，并标注来源。</p>
      </div>
      <article v-for="(message, index) in messages" :key="message.id"
               :class="['message', message.role.toLowerCase()]">
        <el-avatar v-if="message.role === 'USER'" class="avatar" :src="auth.user?.avatarUrl">
          {{ auth.user?.nickname?.slice(0, 1) || '你' }}
        </el-avatar>
        <div v-else class="avatar">AI</div>
        <div class="bubble">
          <p><span v-if="generating && message === messages.at(-1) && !message.content"
                   class="thinking">正在思考，请稍作等待…</span>
            <template v-else>{{ message.content }}</template>
            <span v-if="generating && message === messages.at(-1) && message.content"
                  class="cursor"/></p>
          <div v-if="message.role === 'ASSISTANT' && message.content" class="message-actions">
            <el-button text size="small" @click="copy(message.content)">复制</el-button>
            <el-button v-if="message.status === 'FAILED'" text size="small" type="primary"
                       @click="reuseQuestion(index)">重新提问
            </el-button>
            <el-button v-if="references.length && message === messages.at(-1)" text size="small"
                       @click="drawer = true">{{ references.length }} 条来源
            </el-button>
          </div>
        </div>
      </article>
    </section>
    <footer class="composer">
      <div class="input-wrap">
        <el-input ref="questionInput" v-model="question" type="textarea" :rows="2" resize="none"
                  placeholder="输入问题，Enter 发送，Shift+Enter 换行"
                  @keydown.enter.exact.prevent="send"/>
        <el-button v-if="generating" class="send" @click="stop">停止</el-button>
        <el-button v-else type="primary" class="send" :disabled="!question.trim()" @click="send">
          发送
        </el-button>
      </div>
      <small>AI 仅依据知识库证据回答，重要信息请核对原文。</small>
    </footer>
    <el-drawer v-model="drawer" title="回答来源" size="420px">
      <article v-for="(item, index) in references" :key="`${item.documentId}-${item.chunkIndex}`"
               class="reference-card">
        <b>{{ index + 1 }}. {{ item.sourceName }}</b>
        <small>相似度 {{ (item.similarityScore * 100).toFixed(1) }}% · 切片 {{ item.chunkIndex }}
          <template v-if="item.pageNumber"> · 第 {{ item.pageNumber }} 页</template>
        </small>
        <p>{{ item.excerpt }}</p>
      </article>
    </el-drawer>
  </div>
</template>

<style scoped>
.thinking {
  color: #756e7d;
}
</style>

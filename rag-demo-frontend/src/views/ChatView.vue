<script setup lang="ts">
import {nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
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
const messagesContainer = ref<HTMLElement>()
const showScrollButton = ref(false)
const followsLatestMessage = ref(true)
const waitingSeconds = ref(0)
let waitingTimer: number | undefined

onMounted(load)

async function load() {
  const data = await conversationApi.detail(id)
  conversation.value = data.conversation
  messages.value = data.messages
  await nextTick()
  scrollToBottom('auto')
  questionInput.value?.focus()
}

function updateScrollState() {
  const container = messagesContainer.value
  if (!container) return
  const distance = container.scrollHeight - container.scrollTop - container.clientHeight
  followsLatestMessage.value = distance < 100
  showScrollButton.value = distance >= 100
}

function scrollToBottom(behavior: ScrollBehavior = 'smooth') {
  const container = messagesContainer.value
  if (!container) return
  container.scrollTo({top: container.scrollHeight, behavior})
  followsLatestMessage.value = true
  showScrollButton.value = false
}

function startWaiting() {
  waitingSeconds.value = 0
  if (waitingTimer !== undefined) window.clearInterval(waitingTimer)
  waitingTimer = window.setInterval(() => waitingSeconds.value++, 1000)
}

function stopWaiting() {
  if (waitingTimer !== undefined) window.clearInterval(waitingTimer)
  waitingTimer = undefined
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
  startWaiting()
  controller.value = new AbortController()
  await nextTick()
  scrollToBottom()
  try {
    await streamChat(id, text, {
      delta: value => {
        const shouldFollow = followsLatestMessage.value
        answer.content += value
        if (shouldFollow) nextTick(() => scrollToBottom())
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
    stopWaiting()
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

onBeforeUnmount(() => {
  stopWaiting()
  controller.value?.abort()
})
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
    <section ref="messagesContainer" class="messages" @scroll="updateScrollState">
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
                   class="thinking">
            <span class="thinking-title">正在检索并整理知识库证据<span class="thinking-dots"
                                                                  aria-hidden="true"><i/><i/><i/></span></span>
            <small>回答会严格依据当前知识库中的可用资料<span v-if="waitingSeconds"> · 已等待 {{
                waitingSeconds
              }} 秒</span></small>
          </span>
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
    <el-button v-show="showScrollButton" class="scroll-bottom" circle aria-label="回到底部"
               title="回到底部" @click="scrollToBottom()">↓
    </el-button>
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
        <small>切片 {{ item.chunkIndex }}
          <template v-if="item.rerankScore != null"> · 重排 {{ item.rerankScore.toFixed(4) }}</template>
          <template v-if="item.fusionScore != null"> · RRF {{ item.fusionScore.toFixed(4) }}</template>
          <template v-if="item.vectorScore != null"> · 向量 {{ item.vectorScore.toFixed(4) }}</template>
          <template v-if="item.bm25Score != null"> · BM25 {{ item.bm25Score.toFixed(4) }}</template>
          <template v-if="item.pageNumber"> · 第 {{ item.pageNumber }} 页</template>
        </small>
        <p>{{ item.excerpt }}</p>
      </article>
    </el-drawer>
  </div>
</template>

<style scoped>
.chat-page {
  position: relative;
}

.thinking {
  display: flex;
  min-width: 280px;
  flex-direction: column;
  gap: 3px;
  padding: 8px 2px;
  color: #514960;
}

.thinking-title {
  font-weight: 650;
}

.thinking small {
  color: #8a8393;
  font-size: 12px;
}

.thinking-dots {
  display: inline-flex;
  gap: 4px;
  margin-left: 8px;
  vertical-align: middle;
}

.thinking-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #7652ee;
  animation: thinking-bounce 1.2s ease-in-out infinite;
}

.thinking-dots i:nth-child(2) {
  animation-delay: .15s;
}

.thinking-dots i:nth-child(3) {
  animation-delay: .3s;
}

.scroll-bottom {
  position: absolute;
  right: max(6vw, 55px);
  bottom: 132px;
  z-index: 3;
  width: 40px;
  height: 40px;
  border-color: #dcd4f1;
  color: #6542df;
  box-shadow: 0 8px 24px #50318f2b;
}

@keyframes thinking-bounce {
  0%, 60%, 100% {
    opacity: .35;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-4px);
  }
}
</style>

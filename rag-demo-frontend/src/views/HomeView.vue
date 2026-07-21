<script setup lang="ts">import {onMounted, ref} from 'vue';
import {useRouter} from 'vue-router';
import {conversationApi, knowledgeApi} from '@/api';
import {useAuthStore} from '@/stores/auth';
import type {Conversation, KnowledgeBase} from '@/types';

const auth = useAuthStore(), router = useRouter(), knowledge = ref<KnowledgeBase[]>([]),
    recent = ref<Conversation[]>([]), loading = ref(true);
onMounted(async () => {
  try {
    [knowledge.value, recent.value] = await Promise.all([knowledgeApi.list(), conversationApi.list()])
  } finally {
    loading.value = false
  }
});

async function start(id: number, name: string) {
  const result = await conversationApi.create(id, `${name}问答`);
  router.push(`/chat/${result.id}`)
}</script>
<template>
  <div class="page">
    <section class="welcome">
      <div><span class="eyebrow">KNOWLEDGE WORKSPACE</span>
        <h1>下午好，{{ auth.user?.nickname }}</h1>
        <p>从可信的企业资料中找到答案，每一条结论都有来源。</p></div>
      <el-button type="primary" size="large" @click="$router.push('/knowledge-bases')">开始新问答
      </el-button>
    </section>
    <el-skeleton :loading="loading" animated :rows="5">
      <section>
        <div class="section-head"><h2>可用知识库</h2>
          <el-link @click="$router.push('/knowledge-bases')">查看全部</el-link>
        </div>
        <div class="card-grid">
          <article v-for="kb in knowledge.slice(0,3)" :key="kb.id" class="kb-card">
            <div class="kb-icon"><img v-if="kb.coverUrl" :src="kb.coverUrl" :alt="kb.name"
                                      style="width:100%;height:100%;object-fit:cover;border-radius:inherit"/>
              <template v-else>✦</template>
            </div>
            <h3>{{ kb.name }}</h3>
            <p>{{ kb.description || '暂无描述' }}</p>
            <el-button text type="primary" @click="start(kb.id,kb.name)">进入问答 →</el-button>
          </article>
        </div>
      </section>
      <section>
        <div class="section-head"><h2>最近会话</h2></div>
        <el-empty v-if="!recent.length" description="还没有会话"/>
        <div v-else class="recent-list">
          <button v-for="item in recent.slice(0,5)" :key="item.id"
                  @click="router.push(`/chat/${item.id}`)"><span
              class="chat-dot">◆</span><b>{{ item.title }}</b><small>继续对话 →</small></button>
        </div>
      </section>
    </el-skeleton>
  </div>
</template>

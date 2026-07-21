<script setup lang="ts">
import {reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage, type FormInstance, type FormRules} from 'element-plus'
import {useAuthStore} from '@/stores/auth'

interface LoginForm {
  username: string
  password: string
}

const form = reactive<LoginForm>({username: 'admin', password: '123456'})
const formRef = ref<FormInstance>()
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const rules: FormRules<LoginForm> = {
  username: [{required: true, whitespace: true, message: '请输入账号', trigger: 'blur'}],
  password: [{required: true, whitespace: true, message: '请输入密码', trigger: 'blur'}],
}

async function submit() {
  if (loading.value) return
  if (!form.username.trim() || !form.password.trim()) {
    await formRef.value?.validate().catch(() => false)
    ElMessage.warning('请填写账号和密码')
    return
  }
  if (!await formRef.value?.validate().catch(() => false)) return
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    await router.push(String(route.query.redirect ?? '/home'))
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="orb one"/>
      <div class="orb two"/>
      <span class="eyebrow">ENTERPRISE KNOWLEDGE INTELLIGENCE</span>
      <h1>张喆闻<br><em>RAG</em> 企业知识库<br>问答系统</h1>
      <p>让企业文档成为可追溯、可引用、可信赖的智能答案。</p>
      <div class="hero-tags"><span>语义检索</span><span>流式问答</span><span>来源引用</span></div>
    </section>
    <el-card class="login-card">
      <h2>欢迎回来</h2>
      <p>登录后进入智能知识工作台</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top"
               @submit.prevent="submit">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" size="large" autocomplete="username"/>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password size="large"
                    autocomplete="current-password"/>
        </el-form-item>
        <el-button native-type="submit" type="primary" size="large" :loading="loading" class="wide">
          登录系统
        </el-button>
      </el-form>
      <div class="demo-tip"><b>演示账号</b><span>admin / 123456</span><span>user / 123456</span>
      </div>
      <p class="risk-tip">教学系统按需求使用 MD5，禁止复用真实密码。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { adminApi } from '@/api'
import type { User } from '@/types'

interface UserForm {
  username: string
  nickname: string
  password: string
  status: string
  roles: string[]
}

const list = ref<User[]>([])
const dialog = ref(false)
const editing = ref<number>()
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<UserForm>({ username: '', nickname: '', password: '123456', status: 'ENABLED', roles: ['USER'] })
const rules: FormRules<UserForm> = {
  username: [
    { required: true, whitespace: true, message: '请输入账号', trigger: 'blur' },
    { max: 64, message: '账号不能超过 64 个字符', trigger: 'blur' },
  ],
  nickname: [
    { required: true, whitespace: true, message: '请输入昵称', trigger: 'blur' },
    { max: 64, message: '昵称不能超过 64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, whitespace: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 100, message: '初始密码长度应为 6 至 100 个字符', trigger: 'blur' },
  ],
  roles: [{ type: 'array', min: 1, required: true, message: '请至少选择一个角色', trigger: 'change' }],
}

onMounted(load)

async function load() {
  try {
    list.value = await adminApi.users()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function open(item?: User) {
  editing.value = item?.id
  Object.assign(form, item ? { ...item, password: '', roles: [...item.roles] } : { username: '', nickname: '', password: '123456', status: 'ENABLED', roles: ['USER'] })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  if (saving.value) return
  const missingRequired = !form.username.trim() || !form.nickname.trim() || !form.roles.length || (!editing.value && !form.password.trim())
  if (missingRequired) {
    await formRef.value?.validate().catch(() => false)
    ElMessage.warning('请完善必填项')
    return
  }
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    if (editing.value) await adminApi.updateUser(editing.value, { nickname: form.nickname, status: form.status, roles: form.roles })
    else await adminApi.createUser(form)
    dialog.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function reset(id: number) {
  try {
    await ElMessageBox.confirm('密码将重置为 123456，确认继续？', '重置密码', { type: 'warning' })
    await adminApi.resetPassword(id)
    ElMessage.success('密码已重置')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error((error as Error).message)
  }
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div><span class="eyebrow">USER ADMIN</span><h1>用户管理</h1><p>维护账号、角色和启停状态。</p></div>
      <el-button type="primary" @click="open()">新增用户</el-button>
    </header>
    <el-table :data="list" class="glass-table">
      <el-table-column prop="username" label="账号" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column label="角色"><template #default="{ row }"><el-tag v-for="role in row.roles" :key="role" class="role-tag">{{ role }}</el-tag></template></el-table-column>
      <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="220"><template #default="{ row }"><el-button text type="primary" @click="open(row)">编辑</el-button><el-button text @click="reset(row.id)">重置密码</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="editing ? '编辑用户' : '新增用户'" width="500">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item label="账号" prop="username"><el-input v-model="form.username" :disabled="!!editing" maxlength="64" /></el-form-item>
        <el-form-item label="昵称" prop="nickname"><el-input v-model="form.nickname" maxlength="64" /></el-form-item>
        <el-form-item v-if="!editing" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="100" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio-button value="ENABLED">启用</el-radio-button><el-radio-button value="DISABLED">停用</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="角色" prop="roles"><el-checkbox-group v-model="form.roles"><el-checkbox value="USER">普通用户</el-checkbox><el-checkbox value="ADMIN">管理员</el-checkbox></el-checkbox-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

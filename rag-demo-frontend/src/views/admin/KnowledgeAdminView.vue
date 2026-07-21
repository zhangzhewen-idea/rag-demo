<script setup lang="ts">
import {nextTick, onMounted, reactive, ref} from 'vue'
import {ElMessage, type FormInstance, type FormRules, type UploadRequestOptions} from 'element-plus'
import {adminApi} from '@/api'
import type {KnowledgeBase} from '@/types'

interface KnowledgeForm {
  name: string
  description: string
  coverUrl: string
  status: string
}

const list = ref<KnowledgeBase[]>([])
const dialog = ref(false)
const editing = ref<number>()
const saving = ref(false)
const uploadingCover = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<KnowledgeForm>({name: '', description: '', coverUrl: '', status: 'ENABLED'})
const rules: FormRules<KnowledgeForm> = {
  name: [
    {required: true, whitespace: true, message: '请输入知识库名称', trigger: 'blur'},
    {max: 128, message: '知识库名称不能超过 128 个字符', trigger: 'blur'},
  ],
  description: [{max: 1000, message: '描述不能超过 1000 个字符', trigger: 'blur'}],
}

onMounted(load)

async function load() {
  try {
    list.value = await adminApi.knowledge()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function open(item?: KnowledgeBase) {
  editing.value = item?.id
  Object.assign(form, item ?? {name: '', description: '', coverUrl: '', status: 'ENABLED'})
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  if (saving.value) return
  if (!form.name.trim()) {
    await formRef.value?.validateField('name').catch(() => false)
    ElMessage.warning('请填写知识库名称')
    return
  }
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    if (editing.value) await adminApi.updateKnowledge(editing.value, form)
    else await adminApi.createKnowledge(form)
    dialog.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function toggle(item: KnowledgeBase) {
  try {
    await adminApi.updateKnowledge(item.id, {
      ...item,
      status: item.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    })
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function uploadCover(options: UploadRequestOptions) {
  uploadingCover.value = true
  try {
    const result = await adminApi.uploadCover(options.file)
    form.coverUrl = result.url
    options.onSuccess(result)
    ElMessage.success('封面上传成功')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    uploadingCover.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div><span class="eyebrow">KNOWLEDGE ADMIN</span>
        <h1>知识库管理</h1>
        <p>维护知识范围、启停状态和文档入口。</p></div>
      <el-button type="primary" @click="open()">新增知识库</el-button>
    </header>
    <el-table :data="list" class="glass-table">
      <el-table-column prop="name" label="名称" min-width="200"/>
      <el-table-column prop="description" label="描述" min-width="280" show-overflow-tooltip/>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
            {{ row.status === 'ENABLED' ? '已启用' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button text type="primary" @click="open(row)">编辑</el-button>
          <el-button text @click="toggle(row)">{{
              row.status === 'ENABLED' ? '停用' : '启用'
            }}
          </el-button>
          <el-button text
                     @click="$router.push({ path: '/admin/documents', query: { kb: row.id } })">文档管理
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="editing ? '编辑知识库' : '新增知识库'" width="520">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top"
               @submit.prevent="save">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="128" show-word-limit/>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000"
                    show-word-limit/>
        </el-form-item>
        <el-form-item label="封面图片" prop="coverUrl">
          <el-upload class="cover-uploader" :show-file-list="false"
                     accept="image/png,image/jpeg,image/webp" :http-request="uploadCover">
            <img v-if="form.coverUrl" :src="form.coverUrl" class="cover-preview" alt="知识库封面"/>
            <el-button v-else :loading="uploadingCover">选择图片</el-button>
          </el-upload>
          <small class="cover-help">支持 PNG、JPEG、WebP，最大 5 MB</small>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cover-uploader {
  display: block;
}

.cover-preview {
  display: block;
  width: 160px;
  height: 100px;
  object-fit: cover;
  border: 1px solid var(--line);
  border-radius: 10px;
}

.cover-help {
  display: block;
  margin-top: 8px;
  color: #827c89;
}
</style>

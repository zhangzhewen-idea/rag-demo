import { defineStore } from 'pinia'
import { computed,ref } from 'vue'
import { authApi } from '@/api'
import { setAccessToken } from '@/api/http'
import type { User } from '@/types'

/** 认证状态只在内存保存 Access Token，刷新页面通过 HttpOnly Cookie 恢复。 */
export const useAuthStore=defineStore('auth',()=>{const token=ref('');const user=ref<User>();const restored=ref(false);const isAdmin=computed(()=>user.value?.roles.includes('ADMIN')??false);const authenticated=computed(()=>Boolean(token.value&&user.value));function apply(data:{accessToken:string;user:User}){token.value=data.accessToken;user.value=data.user;setAccessToken(data.accessToken)}async function login(username:string,password:string){apply(await authApi.login(username,password))}async function restore(){if(restored.value)return;try{apply(await authApi.refresh())}catch{clear()}finally{restored.value=true}}async function logout(){try{await authApi.logout()}finally{clear()}}function clear(){token.value='';user.value=undefined;setAccessToken('')}return{token,user,restored,isAdmin,authenticated,login,restore,logout,clear}})

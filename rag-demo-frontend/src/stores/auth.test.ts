import { beforeEach,describe,expect,it,vi } from 'vitest'
import { createPinia,setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
vi.mock('@/api',()=>({authApi:{login:vi.fn().mockResolvedValue({accessToken:'token',user:{id:1,username:'admin',nickname:'管理员',roles:['ADMIN'],status:'ENABLED'}}),refresh:vi.fn().mockRejectedValue(new Error('none')),logout:vi.fn()}}))
describe('auth store',()=>{beforeEach(()=>setActivePinia(createPinia()));it('登录后识别管理员角色',async()=>{const store=useAuthStore();await store.login('admin','123456');expect(store.authenticated).toBe(true);expect(store.isAdmin).toBe(true)});it('刷新失败时保持未登录',async()=>{const store=useAuthStore();await store.restore();expect(store.authenticated).toBe(false);expect(store.restored).toBe(true)})})

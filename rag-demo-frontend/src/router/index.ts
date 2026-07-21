import {createRouter, createWebHistory} from 'vue-router'
import {useAuthStore} from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(), routes: [
    {path: '/login', component: () => import('@/views/LoginView.vue'), meta: {public: true}},
    {
      path: '/', component: () => import('@/layouts/AppLayout.vue'), children: [
        {path: '', redirect: '/home'}, {
          path: 'home',
          component: () => import('@/views/HomeView.vue')
        }, {
          path: 'knowledge-bases',
          component: () => import('@/views/KnowledgeBasesView.vue')
        }, {
          path: 'chat/:conversationId',
          component: () => import('@/views/ChatView.vue')
        }, {
          path: 'conversations',
          component: () => import('@/views/ConversationsView.vue')
        }, {path: 'profile', component: () => import('@/views/ProfileView.vue')},
        {
          path: 'admin/dashboard',
          component: () => import('@/views/admin/DashboardView.vue'),
          meta: {admin: true}
        }, {
          path: 'admin/knowledge-bases',
          component: () => import('@/views/admin/KnowledgeAdminView.vue'),
          meta: {admin: true}
        }, {
          path: 'admin/documents',
          component: () => import('@/views/admin/DocumentsView.vue'),
          meta: {admin: true}
        }, {
          path: 'admin/users',
          component: () => import('@/views/admin/UsersView.vue'),
          meta: {admin: true}
        },
      ]
    }, {path: '/:pathMatch(.*)*', redirect: '/home'}]
})
/** 路由守卫先恢复登录态，再执行公开页和管理员权限隔离。 */
router.beforeEach(async to => {
  const auth = useAuthStore();
  if (!auth.restored) await auth.restore();
  if (to.meta.public) return auth.authenticated ? '/home' : true;
  if (!auth.authenticated) return {path: '/login', query: {redirect: to.fullPath}};
  if (to.meta.admin && !auth.isAdmin) return '/home';
  return true
})
export default router

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useSpaceStore } from '@/stores/spaces'
import { useUiStore } from '@/stores/ui'
import Icon from '@/components/Icon.vue'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const spaces = useSpaceStore()
const ui = useUiStore()
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const error = ref('')
const form = reactive({ userAccount: '', userPassword: '', checkPassword: '' })
const title = computed(() => mode.value === 'login' ? '回到夜之馆' : '建立你的档案馆')
const sub = computed(() => mode.value === 'login' ? '登录后继续整理你的私人影像收藏。' : '只需要一个账号，所有照片会安全地留在你的图库里。')
function switchMode(m: 'login' | 'register') { mode.value = m; error.value = '' }
async function submit() {
  error.value = ''
  if (!form.userAccount.trim()) { error.value = '请输入账号。'; return }
  if (!form.userPassword) { error.value = '请输入密码。'; return }
  if (mode.value === 'register' && form.userPassword !== form.checkPassword) { error.value = '两次输入的密码不一致。'; return }
  loading.value = true
  try {
    if (mode.value === 'login') await user.login({ userAccount: form.userAccount.trim(), userPassword: form.userPassword })
    else await user.register({ userAccount: form.userAccount.trim(), userPassword: form.userPassword, checkPassword: form.checkPassword })
    await spaces.ensureLoaded(true)
    ui.toast(mode.value === 'login' ? '欢迎回来，' + user.displayName : '账号已建立，欢迎来到夜之馆', 'success')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) { error.value = (e as Error).message } finally { loading.value = false }
}
</script>
<template>
  <div class="auth">
    <section class="auth-art">
      <div class="auth-mosaic"><figure><img src="/art/a.jpg" alt=""></figure><figure><img src="/art/b.jpg" alt=""></figure><figure><img src="/art/c.jpg" alt=""></figure><figure><img src="/art/d.jpg" alt=""></figure></div>
      <div class="auth-mark">NOCTURNE</div>
      <div class="auth-copy"><div class="eyebrow">Private archive · Est. 2026</div><h1>把光线，<br>留在夜里。</h1><p>Every photograph is a room you can return to.</p></div>
    </section>
    <section class="auth-panel">
      <RouterLink class="auth-back" to="/">先随便看看 →</RouterLink>
      <div class="auth-box">
        <div class="eyebrow">Nocturne / 夜之馆</div>
        <h2>{{ title }}</h2>
        <p class="sub">{{ sub }}</p>
        <div class="auth-tabs"><button :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button><button :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button></div>
        <form class="auth-form" @submit.prevent="submit">
          <div class="field"><label for="account">Account / 账号</label><input id="account" v-model="form.userAccount" class="input" autocomplete="username" placeholder="your account" maxlength="32"></div>
          <div class="field"><label for="password">Password / 密码</label><input id="password" v-model="form.userPassword" class="input" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="至少 8 位" maxlength="64"></div>
          <div v-if="mode === 'register'" class="field"><label for="check">Confirm / 确认密码</label><input id="check" v-model="form.checkPassword" class="input" type="password" autocomplete="new-password" placeholder="再输入一次" maxlength="64"></div>
          <p v-if="error" class="form-error">{{ error }}</p>
          <button class="btn gold lg" type="submit" :disabled="loading">{{ loading ? '正在进入…' : mode === 'login' ? '进入夜之馆' : '创建账号' }}<Icon name="arrow-right" :size="13" /></button>
        </form>
        <div class="auth-foot">Session protected · 登录状态由 Sa-Token 与 Redis 保管</div>
      </div>
    </section>
  </div>
</template>

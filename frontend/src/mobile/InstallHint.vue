<script setup lang="ts">
import { computed } from 'vue'
import { isIOS, isStandalone } from '@/composables/useIsMobile'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [v: boolean] }>()
const visible = computed(() => props.open && isIOS() && !isStandalone())
</script>

<template>
  <Teleport to="body">
    <Transition name="ih">
      <div v-if="visible" class="ih-wrap" role="dialog" aria-modal="true" aria-label="添加到主屏幕">
        <div class="ih-scrim" @click="emit('update:open', false)"></div>
        <div class="ih-card">
          <div class="ih-mark">N</div>
          <b>把夜之馆装到主屏</b>
          <p>在 Safari 里点底部的「分享」按钮，选择「添加到主屏幕」，就能像 App 一样全屏打开。</p>
          <ol>
            <li><span>1</span>点击底部工具栏的分享图标</li>
            <li><span>2</span>向下滑动，选择「添加到主屏幕」</li>
            <li><span>3</span>点右上角「添加」，回到主屏打开</li>
          </ol>
          <button class="ih-ok" type="button" @click="emit('update:open', false)">知道了</button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.ih-wrap{position:fixed;inset:0;z-index:1200;display:flex;align-items:flex-end}
.ih-scrim{position:absolute;inset:0;background:rgba(0,0,0,.5);opacity:1;transition:opacity var(--dur-fade) var(--spring-fade)}
.ih-card{position:relative;width:100%;padding:22px 22px calc(18px + env(safe-area-inset-bottom));background:#1c1c1e;border-radius:18px 18px 0 0;color:#fff;font-family:-apple-system,'PingFang SC',system-ui,sans-serif;will-change:transform}
.ih-mark{width:44px;height:44px;border-radius:11px;background:linear-gradient(160deg,#141416,#000);border:.5px solid rgba(201,169,106,.5);display:grid;place-items:center;font-family:Georgia,serif;font-size:24px;color:#c9a96a;margin-bottom:14px}
.ih-card b{display:block;font-size:19px;font-weight:600;letter-spacing:.01em}
.ih-card p{margin:8px 0 14px;font-size:13px;line-height:1.5;color:rgba(235,235,245,.6)}
.ih-card ol{margin:0;padding:0;list-style:none;display:flex;flex-direction:column;gap:10px}
.ih-card li{display:flex;align-items:center;gap:10px;font-size:13px;color:rgba(235,235,245,.85)}
.ih-card li span{width:20px;height:20px;border-radius:50%;background:rgba(10,132,255,.18);color:#0a84ff;display:grid;place-items:center;font-size:11px;flex:none}
.ih-ok{margin-top:18px;width:100%;height:46px;border-radius:12px;background:rgba(118,118,128,.24);font-size:16px;font-weight:600;color:#fff}
.ih-enter-active,.ih-leave-active{transition:opacity var(--dur-fade) var(--spring-fade)}
.ih-enter-active .ih-card,.ih-leave-active .ih-card{transition:transform var(--dur-sheet) var(--spring-sheet)}
.ih-enter-from,.ih-leave-to{opacity:0}
.ih-enter-from .ih-card,.ih-leave-to .ih-card{transform:translate3d(0,100%,0)}
.ih-ok{transition:transform var(--dur-press) var(--spring-press),background var(--dur-press) ease}
.ih-ok:active{transform:scale(.97);background:rgba(118,118,128,.36)}
</style>

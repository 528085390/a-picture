<script setup lang="ts">
import AppShell from '@/components/AppShell.vue'
</script>
<template><AppShell><main class="library"><div class="notfound"><div class="nf-n">404</div><div class="eyebrow">Lost frame / 失焦的一帧</div><h1>这一页没有留下底片。</h1><p>你走进了一间不存在的展厅。回到图库，光线还在那里。</p><RouterLink class="btn gold" to="/gallery">返回图库</RouterLink></div></main></AppShell></template>
<style scoped>
.notfound{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding-bottom:80px}.nf-n{font-family:var(--serif);font-size:130px;line-height:.8;color:rgba(201,169,106,.16);letter-spacing:.1em;margin-bottom:22px}.notfound h1{font-family:var(--cn);font-size:30px;font-weight:300;letter-spacing:.08em;color:var(--ivory);margin:12px 0 10px}.notfound p{font-size:9px;color:var(--dim);line-height:1.8;max-width:360px;margin-bottom:24px}
</style>

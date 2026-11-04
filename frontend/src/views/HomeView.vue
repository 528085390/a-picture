<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useGalleryStore } from '@/stores/gallery'
import { useUserStore } from '@/stores/user'
import { mediaUrl, thumbUrl } from '@/utils/media'
import type { PictureVO } from '@/api/types'
import Icon from '@/components/Icon.vue'

const router = useRouter()
const gallery = useGalleryStore()
const user = useUserStore()
const root = ref<HTMLElement | null>(null)
const hero = ref<HTMLElement | null>(null)
const progress = ref(0)
const scrolled = ref(false)
const preview = ref<{ photo: PictureVO; x: number; y: number } | null>(null)
let revealObserver: IntersectionObserver | null = null

const heroPhoto = computed(() => gallery.photos[0] || null)
const heroSrc = computed(() => mediaUrl(heroPhoto.value?.url || ''))
const featured = computed(() => gallery.photos.slice(0, 4))
const collection = computed(() => gallery.photos.slice(4, 9))
const totalLabel = computed(() => gallery.total ? String(gallery.total) : '—')

function dateOf(p: PictureVO) {
  const d = p.createTime || ''
  const m = d.match(/(\d{4})-(\d{2})-(\d{2})/)
  return m ? m[1] + '.' + m[2] + '.' + m[3] : (d.slice(0, 10) || '2026')
}
function yearOf(p: PictureVO) { return dateOf(p).slice(0, 4) }
function categoryOf(p: PictureVO) { return (p.category || '私人影像').toUpperCase() }
function showPhoto(p: PictureVO) { router.push({ name: 'gallery', query: { photo: String(p.id) } }) }
function scrollToId(id: string) {
  const el = root.value?.querySelector('#' + id) as HTMLElement | null
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function onHeroMove(e: MouseEvent) {
  const el = hero.value; if (!el) return
  const r = el.getBoundingClientRect()
  el.style.setProperty('--sx', (e.clientX - r.left) + 'px')
  el.style.setProperty('--sy', (e.clientY - r.top) + 'px')
}
function onScroll() {
  const el = root.value; if (!el) return
  const max = el.scrollHeight - el.clientHeight
  progress.value = max > 0 ? Math.min(100, el.scrollTop / max * 100) : 0
  scrolled.value = el.scrollTop > 40
}
function enterPreview(e: MouseEvent, p: PictureVO) { preview.value = { photo: p, x: e.clientX, y: e.clientY } }
function movePreview(e: MouseEvent) {
  if (!preview.value) return
  preview.value = { ...preview.value, x: e.clientX, y: Math.min(window.innerHeight - 230, Math.max(230, e.clientY)) }
}
function leavePreview() { preview.value = null }
function setupReveal() {
  revealObserver?.disconnect()
  if (document.documentElement.classList.contains('static') || matchMedia('(prefers-reduced-motion: reduce)').matches) {
    root.value?.querySelectorAll('.hn-reveal').forEach(el => el.classList.add('on'))
    return
  }
  revealObserver = new IntersectionObserver(entries => {
    entries.forEach(entry => { if (entry.isIntersecting) { entry.target.classList.add('on'); revealObserver?.unobserve(entry.target) } })
  }, { root: root.value, threshold: 0.1, rootMargin: '0px 0px -6% 0px' })
  root.value?.querySelectorAll('.hn-reveal:not(.on)').forEach(el => revealObserver?.observe(el))
}
onMounted(async () => {
  await gallery.ensureLoaded()
  await nextTick()
  setupReveal()
  onScroll()
})
watch(() => featured.value.length, async () => { await nextTick(); setupReveal() })
onBeforeUnmount(() => { revealObserver?.disconnect(); revealObserver = null })
</script>

<template>
  <div ref="root" class="hn-home" @scroll.passive="onScroll">
    <div class="noise"></div>
    <div class="hn-progress"><i :style="{ width: progress + '%' }"></i></div>

    <header class="hn-header" :class="{ scrolled }">
      <button class="hn-brand" type="button" aria-label="回到首页" @click="scrollToId('top')"><b>NOCTURNE</b><span>夜之馆</span></button>
      <nav class="hn-nav" aria-label="首页导航">
        <button type="button" @click="scrollToId('view')">展览</button>
        <button type="button" @click="scrollToId('collection')">馆藏</button>
        <RouterLink to="/live">放映</RouterLink>
        <RouterLink to="/spaces">空间</RouterLink>
        <button type="button" @click="scrollToId('about')">关于</button>
      </nav>
      <RouterLink class="hn-ticket" :to="user.isLoggedIn ? '/gallery' : '/login'">
        {{ user.isLoggedIn ? '进入图库' : '预约参观' }}<Icon name="arrow-right" :size="11" />
      </RouterLink>
    </header>

    <section id="top" ref="hero" class="hn-hero" @mousemove="onHeroMove">
      <div class="hn-hero-bg"><img v-if="heroSrc" :src="heroSrc" alt="夜之馆当前展览" draggable="false"></div>
      <div class="hn-spotlight"></div>
      <div class="hn-hero-inner">
        <div class="hn-eyebrow">A private house of light · Shanghai</div>
        <h1>NOCTURNE</h1>
        <div class="hn-hero-cn">夜 之 馆</div>
        <p class="hn-hero-sub">收藏光，也收藏光落下之后的寂静。</p>
        <RouterLink class="hn-hero-cta" to="/gallery">进入图库 <Icon name="arrow-right" :size="12" /></RouterLink>
      </div>
      <div class="hn-hero-bottom">
        <div class="hn-now">当前展览<b>{{ heroPhoto?.name || '光的遗迹 — 亚洲影像收藏展' }}</b><span>{{ dateOf(heroPhoto || ({} as PictureVO)) }} / {{ totalLabel }} 件馆藏</span></div>
        <button class="hn-scroll" type="button" @click="scrollToId('prologue')">Scroll to enter<i></i></button>
      </div>
    </section>

    <main class="hn-main">
      <section id="prologue" class="hn-prologue">
        <div class="hn-big-num">01</div>
        <div class="hn-pro-copy hn-reveal">
          <div class="hn-kicker">Prologue / 序</div>
          <h2>照片不是被拍下的，<br>而是被光<em>留下</em>的。</h2>
          <p>夜之馆是一间不对外开放的私人影像收藏空间。我们收集那些安静、克制、几乎要消失在暗处的画面；它们不喧哗，却在多年之后仍然发亮。</p>
          <p>这里没有热门排序，也没有算法推荐。只有策展人一次次翻阅、比对、放弃，最后留下来的少数。</p>
          <div class="hn-line"></div>
        </div>
        <div class="hn-pro-side">“The light<br>stays longer<br>than the eye.”</div>
      </section>

      <section id="view" class="hn-onview">
        <div class="hn-sec-head hn-reveal">
          <div class="hn-sec-left"><span class="hn-no">02</span><h2>正在展出<small>On view / 2026—2027</small></h2></div>
          <RouterLink to="/gallery">全部展览 →</RouterLink>
        </div>
        <div class="hn-shows">
          <article v-for="(p, i) in featured" :key="p.id" class="hn-show hn-reveal" @click="showPhoto(p)">
            <div class="hn-show-img">
              <span class="hn-tag">ROOM 0{{ i + 1 }}</span>
              <img :src="thumbUrl(p.url, 900)" :alt="p.name || '馆藏影像'" draggable="false">
              <span class="hn-go"><Icon name="arrow-right" :size="10" /></span>
            </div>
            <div class="hn-show-meta">
              <span>{{ categoryOf(p) }}</span>
              <h3>{{ p.name || '未命名影像' }}</h3>
              <p>{{ dateOf(p) }} · {{ p.user?.userName || 'NOCTURNE' }}</p>
            </div>
          </article>
          <div v-if="!featured.length" class="hn-empty">正在布置展厅…</div>
        </div>
      </section>

      <section id="collection" class="hn-collection">
        <div class="hn-sec-head hn-reveal">
          <div class="hn-sec-left"><span class="hn-no">03</span><h2>馆藏精选<small>Selected collection / 2026</small></h2></div>
          <RouterLink to="/gallery">浏览全部馆藏 →</RouterLink>
        </div>
        <div class="hn-coll-list hn-reveal">
          <div v-for="(p, i) in collection" :key="p.id" class="hn-coll-row" @click="showPhoto(p)" @mouseenter="enterPreview($event, p)" @mousemove="movePreview" @mouseleave="leavePreview">
            <span class="hn-idx">No.0{{ i + 1 }}</span>
            <h3>{{ p.name || '未命名影像' }}</h3>
            <span class="hn-cat">{{ categoryOf(p) }}</span>
            <span class="hn-year">{{ yearOf(p) }}</span>
            <span class="hn-go"><Icon name="arrow-right" :size="11" /></span>
          </div>
          <div v-if="!collection.length" class="hn-empty">馆藏正在入库…</div>
        </div>
      </section>

      <section id="join" class="hn-membership">
        <div class="hn-mem-inner">
          <div class="hn-mem-copy hn-reveal">
            <div class="hn-kicker">Private access / 私人入口</div>
            <h2>成为光的<br><em>收藏者</em>。</h2>
            <p>这里保存着只属于你的影像、团队空间与 Live 照片。登录后，继续整理那些不愿被算法打扰的画面。</p>
          </div>
          <div class="hn-mem-form hn-reveal">
            <label>Archive access</label>
            <p class="hn-mem-note">{{ user.isLoggedIn ? '你的档案馆已经点亮。' : '一个账号，一间只属于你的暗房。' }}</p>
            <RouterLink class="hn-mem-btn" :to="user.isLoggedIn ? '/gallery' : '/login'">{{ user.isLoggedIn ? '进入我的图库' : '登录 / 注册' }} <Icon name="arrow-right" :size="13" /></RouterLink>
            <p class="hn-fine">Session protected · Sa-Token 与 Redis 守护登录状态</p>
          </div>
        </div>
      </section>
    </main>

    <footer id="about" class="hn-footer">
      <div class="hn-foot-top">
        <div class="hn-foot-brand"><b>NOCTURNE</b><span>Night gallery · Shanghai</span><p>一间关于光、时间与寂静的私人影像馆。收藏日常里那些几乎要消失在暗处的画面。</p></div>
        <div class="hn-foot-col"><h4>Visit</h4><RouterLink to="/gallery">全部照片</RouterLink><RouterLink to="/live">Live 照片</RouterLink><RouterLink to="/spaces">团队空间</RouterLink><RouterLink to="/favorites">我的收藏</RouterLink></div>
        <div class="hn-foot-col"><h4>About</h4><button type="button" @click="scrollToId('prologue')">关于夜之馆</button><button type="button" @click="scrollToId('collection')">馆藏精选</button><RouterLink to="/settings">账号设置</RouterLink><RouterLink to="/login">登录 / 注册</RouterLink></div>
        <div class="hn-foot-col"><h4>Follow</h4><span>Instagram</span><span>小红书</span><span>邮件订阅</span></div>
      </div>
      <div class="hn-foot-bottom"><span>© 2026 Nocturne Gallery</span><span>上海市 · 徐汇区</span><span>By appointment only</span></div>
      <div class="hn-foot-logo">NOCTURNE</div>
    </footer>

    <div v-if="preview" class="hn-hover-preview" :style="{ left: preview.x + 'px', top: preview.y + 'px' }"><img :src="thumbUrl(preview.photo.url, 700)" :alt="preview.photo.name || '馆藏预览'"></div>
  </div>
</template>

<style scoped>
.hn-home{height:100dvh;overflow-y:auto;overflow-x:hidden;background:var(--bg);color:var(--ivory);scroll-behavior:smooth;scrollbar-width:thin;scrollbar-color:rgba(201,169,106,.26) transparent}
.hn-home::-webkit-scrollbar{width:4px}.hn-home::-webkit-scrollbar-track{background:transparent}.hn-home::-webkit-scrollbar-thumb{background:rgba(201,169,106,.24)}
.hn-progress{position:fixed;top:0;left:0;right:0;height:1px;background:rgba(255,255,255,.06);z-index:210}.hn-progress i{display:block;height:100%;background:var(--gold);box-shadow:0 0 14px var(--gold);transition:width .12s linear}
.hn-home [id]{scroll-margin-top:108px}
.hn-header{position:fixed;top:0;left:0;right:0;z-index:100;display:flex;align-items:center;justify-content:space-between;padding:26px 46px;mix-blend-mode:difference;transition:background .45s var(--ease-soft),backdrop-filter .45s var(--ease-soft),border-color .45s var(--ease-soft)}.hn-header.scrolled{mix-blend-mode:normal;background:rgba(7,7,8,.94);border-bottom:1px solid rgba(201,169,106,.12)}
.hn-brand{display:flex;align-items:baseline;gap:13px;text-align:left}.hn-brand b{font-family:var(--serif);font-size:19px;font-weight:500;letter-spacing:.32em;color:#fff}.hn-brand span{font-family:var(--cn);font-size:10px;letter-spacing:.28em;color:var(--gold);opacity:.85}
.hn-nav{display:flex;gap:38px}.hn-nav a,.hn-nav button{font-size:10px;letter-spacing:.24em;text-transform:uppercase;color:rgba(255,255,255,.62);position:relative;padding:3px 0}.hn-nav a:after,.hn-nav button:after{content:"";position:absolute;left:0;right:100%;bottom:-3px;height:1px;background:var(--gold);transition:right .4s}.hn-nav a:hover,.hn-nav button:hover{color:#fff}.hn-nav a:hover:after,.hn-nav button:hover:after{right:0}
.hn-ticket{display:flex;align-items:center;gap:10px;border:1px solid rgba(255,255,255,.25);padding:11px 17px;font-size:9px;letter-spacing:.2em;text-transform:uppercase;color:#fff;transition:.4s}.hn-ticket:hover{background:var(--gold);border-color:var(--gold);color:#08080a}
.hn-hero{height:100vh;min-height:680px;position:relative;overflow:hidden;display:flex;align-items:center;justify-content:center;background:#000}
.hn-hero-bg{position:absolute;inset:0;opacity:.62}.hn-hero-bg img{width:100%;height:100%;object-fit:cover;animation:hnKen 28s ease-out both;filter:grayscale(.65) contrast(1.1) brightness(.58)}
@keyframes hnKen{from{transform:scale(1.12)}to{transform:scale(1)}}
.hn-hero:before{content:"";position:absolute;inset:0;background:radial-gradient(ellipse at 50% 45%,rgba(8,8,10,.1) 0%,rgba(8,8,10,.6) 58%,#08080a 100%);z-index:1}.hn-hero:after{content:"";position:absolute;inset:0;background:linear-gradient(180deg,rgba(8,8,10,.65) 0%,transparent 25%,transparent 70%,#08080a 100%);z-index:1}
.hn-spotlight{position:absolute;inset:0;z-index:2;pointer-events:none;background:radial-gradient(circle 420px at var(--sx,50%) var(--sy,40%),rgba(201,169,106,.16),transparent 70%);transition:background .15s linear}
.hn-hero-inner{position:relative;z-index:4;text-align:center;padding:0 30px;transform:translateY(-10px)}.hn-eyebrow{font-size:9px;letter-spacing:.46em;text-transform:uppercase;color:var(--gold);margin-bottom:34px}.hn-hero h1{font-family:var(--serif);font-weight:300;font-size:clamp(58px,10.5vw,168px);line-height:1;letter-spacing:.16em;text-indent:.16em;color:#f3ecdd;text-shadow:0 0 60px rgba(201,169,106,.16);margin:0}.hn-hero-cn{font-family:var(--cn);font-size:clamp(11px,1.1vw,15px);letter-spacing:1.2em;text-indent:1.2em;color:var(--gold);margin-top:22px;font-weight:300}.hn-hero-sub{margin-top:30px;font-family:var(--cn);font-size:13px;letter-spacing:.24em;color:rgba(236,229,215,.6);font-weight:300}.hn-hero-cta{display:inline-flex;align-items:center;gap:13px;margin-top:34px;padding:13px 24px;border:1px solid rgba(201,169,106,.52);font-size:9px;letter-spacing:.26em;text-transform:uppercase;color:var(--gold2);transition:.4s}.hn-hero-cta:hover{background:var(--gold);border-color:var(--gold);color:#08080a}
.hn-hero-bottom{position:absolute;left:46px;right:46px;bottom:34px;z-index:4;display:flex;justify-content:space-between;align-items:flex-end;font-size:9px;letter-spacing:.18em;text-transform:uppercase;color:var(--muted)}.hn-now b{display:block;font-family:var(--cn);font-size:14px;letter-spacing:.12em;color:var(--ivory);font-weight:400;margin:8px 0 5px}.hn-now span{color:var(--gold-soft)}.hn-scroll{display:flex;align-items:center;gap:12px;writing-mode:vertical-rl;text-orientation:mixed;font-size:8px;letter-spacing:.3em;color:var(--muted)}.hn-scroll i{width:1px;height:58px;background:linear-gradient(var(--gold),transparent);display:block;margin-left:4px}
.hn-main{position:relative;z-index:3}
.hn-prologue{padding:130px 46px 120px;max-width:1440px;margin:0 auto;display:grid;grid-template-columns:repeat(12,1fr);gap:30px;border-bottom:1px solid rgba(255,255,255,.06)}.hn-big-num{grid-column:1/4;font-family:var(--serif);font-size:clamp(100px,13vw,210px);line-height:.8;color:transparent;-webkit-text-stroke:1px rgba(201,169,106,.28);font-weight:300}.hn-pro-copy{grid-column:5/11;padding-top:8px}.hn-kicker{font-size:9px;letter-spacing:.34em;text-transform:uppercase;color:var(--gold);margin-bottom:28px}.hn-pro-copy h2{font-family:var(--cn);font-weight:300;font-size:clamp(28px,3vw,44px);line-height:1.65;letter-spacing:.06em;color:#e8e0d1;margin:0}.hn-pro-copy h2 em{font-family:var(--serif);font-style:italic;color:var(--gold);font-size:1.16em}.hn-pro-copy p{font-family:var(--cn);font-size:12px;line-height:2.3;color:var(--muted);font-weight:300;margin-top:30px;max-width:480px}.hn-pro-copy .hn-line{margin-top:36px;width:70px;height:1px;background:var(--gold);opacity:.6}.hn-pro-side{grid-column:11/13;align-self:end;text-align:right;font-family:var(--serif);font-style:italic;font-size:18px;color:var(--gold-soft);line-height:1.5}
.hn-onview{padding:100px 46px 110px;border-bottom:1px solid rgba(255,255,255,.06)}.hn-sec-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:48px}.hn-sec-left{display:flex;align-items:baseline;gap:28px}.hn-no{font-family:var(--serif);font-size:12px;letter-spacing:.2em;color:var(--gold)}.hn-sec-head h2{font-family:var(--cn);font-size:clamp(28px,3.2vw,46px);font-weight:300;letter-spacing:.12em;margin:0}.hn-sec-head h2 small{display:block;font-family:var(--sans);font-size:8px;font-weight:400;letter-spacing:.34em;color:var(--muted);margin-top:10px}.hn-sec-head>a{font-size:9px;letter-spacing:.2em;color:var(--muted);border-bottom:1px solid rgba(201,169,106,.35);padding-bottom:4px;transition:.3s}.hn-sec-head>a:hover{color:var(--gold)}
.hn-shows{display:grid;grid-template-columns:repeat(4,1fr);gap:22px}.hn-show{position:relative;border:1px solid rgba(255,255,255,.07);background:var(--panel);transition:border .5s,transform .6s;cursor:pointer}.hn-show:hover{border-color:var(--line);transform:translateY(-5px)}.hn-show-img{aspect-ratio:3/4;overflow:hidden;position:relative}.hn-show-img:after{content:"";position:absolute;inset:0;background:linear-gradient(180deg,transparent 55%,rgba(5,5,7,.9));opacity:.9;pointer-events:none}.hn-show-img img{width:100%;height:100%;object-fit:cover;filter:grayscale(.55) brightness(.82);transition:transform 1.2s var(--ease-out),filter 1s}.hn-show:hover img{transform:scale(1.05);filter:grayscale(.1) brightness(.92)}.hn-tag{position:absolute;left:16px;top:16px;z-index:2;font-size:8px;letter-spacing:.24em;color:var(--gold);border:1px solid rgba(201,169,106,.4);padding:5px 8px;background:rgba(8,8,10,.35);backdrop-filter:blur(6px)}.hn-show-meta{position:absolute;left:18px;right:18px;bottom:18px;z-index:2}.hn-show-meta span{font-size:8px;letter-spacing:.22em;color:var(--gold-soft)}.hn-show-meta h3{font-family:var(--cn);font-weight:400;font-size:21px;letter-spacing:.08em;margin:7px 0 5px}.hn-show-meta p{font-size:9px;letter-spacing:.12em;color:var(--muted)}.hn-go{position:absolute;right:16px;top:16px;z-index:2;width:30px;height:30px;border:1px solid rgba(255,255,255,.2);border-radius:50%;display:grid;place-items:center;opacity:0;transform:translate(-4px,4px);transition:.4s}.hn-show:hover .hn-go{opacity:1;transform:none}
.hn-collection{padding:110px 46px 120px;border-bottom:1px solid rgba(255,255,255,.06)}.hn-coll-list{border-top:1px solid rgba(255,255,255,.12)}.hn-coll-row{display:grid;grid-template-columns:80px 1fr 180px 150px 40px;align-items:center;gap:20px;padding:30px 8px;border-bottom:1px solid rgba(255,255,255,.08);cursor:pointer;transition:padding .4s,background .4s}.hn-coll-row:hover{background:linear-gradient(90deg,rgba(201,169,106,.07),transparent);padding-left:22px;padding-right:16px}.hn-idx{font-family:var(--serif);font-style:italic;font-size:16px;color:var(--gold-soft)}.hn-coll-row h3{font-family:var(--cn);font-weight:300;font-size:clamp(22px,2.5vw,36px);letter-spacing:.12em;transition:color .3s;margin:0}.hn-coll-row:hover h3{color:var(--gold)}.hn-cat,.hn-year{font-size:9px;letter-spacing:.2em;color:var(--muted);text-transform:uppercase}.hn-coll-row .hn-go{position:static;justify-self:end;opacity:1;transform:none;border-color:rgba(255,255,255,.15)}.hn-coll-row:hover .hn-go{background:var(--gold);border-color:var(--gold);color:#08080a}
.hn-hover-preview{position:fixed;width:300px;height:380px;z-index:90;pointer-events:none;transform:translate(-50%,-50%) scale(.96);transition:opacity .35s,transform .35s;border:1px solid rgba(201,169,106,.35);padding:8px;background:rgba(8,8,10,.65);backdrop-filter:blur(6px);opacity:1}.hn-hover-preview img{width:100%;height:100%;object-fit:cover;filter:saturate(.8) contrast(1.05)}
.hn-membership{margin:0 46px 110px;border:1px solid var(--line);position:relative;overflow:hidden;background:radial-gradient(circle at 80% 20%,rgba(201,169,106,.1),transparent 55%),var(--panel)}.hn-membership:before{content:"";position:absolute;width:520px;height:520px;border:1px solid rgba(201,169,106,.18);border-radius:50%;right:-160px;top:-220px}.hn-membership:after{content:"";position:absolute;width:340px;height:340px;border:1px solid rgba(201,169,106,.1);border-radius:50%;right:-60px;top:-130px}.hn-mem-inner{position:relative;z-index:2;display:grid;grid-template-columns:1.1fr .9fr;gap:60px;padding:78px 64px;align-items:center}.hn-mem-copy h2{font-family:var(--cn);font-size:clamp(30px,3.2vw,48px);font-weight:300;letter-spacing:.1em;line-height:1.4;margin:0}.hn-mem-copy h2 em{font-family:var(--serif);font-style:italic;color:var(--gold);font-weight:400}.hn-mem-copy p{font-family:var(--cn);font-size:12px;line-height:2.1;color:var(--muted);margin-top:20px;max-width:420px}.hn-mem-form{max-width:390px;justify-self:end;width:100%;border:1px solid rgba(201,169,106,.2);background:rgba(201,169,106,.035);padding:30px 28px 26px}.hn-mem-form label{display:block;font-size:8px;letter-spacing:.26em;text-transform:uppercase;color:var(--gold);margin-bottom:16px}.hn-mem-note{font-family:var(--cn);font-size:13px;color:var(--ivory);letter-spacing:.04em;margin-bottom:22px}.hn-mem-btn{display:flex;align-items:center;justify-content:space-between;background:var(--gold);color:#08080a;padding:13px 15px;font-size:9px;letter-spacing:.18em;text-transform:uppercase;transition:.3s}.hn-mem-btn:hover{background:var(--gold2)}.hn-fine{font-size:9px;line-height:1.8;color:rgba(139,132,120,.65);margin-top:18px;letter-spacing:.04em}
.hn-footer{padding:0 46px 30px}.hn-foot-top{display:grid;grid-template-columns:2fr 1fr 1fr 1fr;gap:36px;border-top:1px solid rgba(255,255,255,.08);padding:58px 0 46px}.hn-foot-brand b{font-family:var(--serif);font-size:25px;letter-spacing:.24em;font-weight:400;display:block}.hn-foot-brand span{display:block;font-size:8px;letter-spacing:.3em;color:var(--gold);margin-top:10px;text-transform:uppercase}.hn-foot-brand p{font-family:var(--cn);font-size:11px;line-height:2;color:var(--muted);margin-top:22px;max-width:280px}.hn-foot-col h4{font-size:8px;letter-spacing:.28em;text-transform:uppercase;color:var(--gold-soft);margin-bottom:18px}.hn-foot-col a,.hn-foot-col button,.hn-foot-col span{display:block;font-family:var(--cn);font-size:11px;color:var(--muted);padding:6px 0;letter-spacing:.06em;transition:.3s;text-align:left}.hn-foot-col a:hover,.hn-foot-col button:hover{color:var(--ivory)}.hn-foot-bottom{display:flex;justify-content:space-between;align-items:center;border-top:1px solid rgba(255,255,255,.06);padding-top:22px;font-size:8px;letter-spacing:.2em;color:rgba(139,132,120,.7);text-transform:uppercase}.hn-foot-logo{font-family:var(--serif);font-size:clamp(70px,15vw,230px);line-height:.75;letter-spacing:.14em;text-align:center;color:transparent;-webkit-text-stroke:1px rgba(201,169,106,.18);padding:46px 0 6px;white-space:nowrap}
.hn-reveal{opacity:0;transform:translateY(26px);transition:opacity 1.1s var(--ease-out),transform 1.1s var(--ease-out)}.hn-reveal.on{opacity:1;transform:none}.hn-empty{grid-column:1/-1;padding:40px 0;color:var(--dim);font-size:10px;letter-spacing:.16em}
@media(max-width:1100px){.hn-shows{grid-template-columns:repeat(2,1fr)}.hn-pro-copy{grid-column:5/13}.hn-pro-side{display:none}.hn-mem-inner{padding:56px 40px;grid-template-columns:1fr;gap:34px}.hn-mem-form{justify-self:start}}
@media(max-width:760px){.hn-header{padding:18px 20px}.hn-nav{display:none}.hn-ticket{padding:9px 12px}.hn-hero h1{font-size:clamp(38px,10.2vw,64px);letter-spacing:.1em;text-indent:.1em}.hn-hero-inner{padding-left:16px;padding-right:16px}.hn-hero-bottom{left:20px;right:20px}.hn-prologue,.hn-onview,.hn-collection,.hn-footer{padding-left:20px;padding-right:20px}.hn-prologue{grid-template-columns:1fr;gap:24px}.hn-big-num,.hn-pro-copy{grid-column:1}.hn-shows{grid-template-columns:1fr}.hn-sec-head{gap:20px}.hn-coll-row{grid-template-columns:44px 1fr 30px;gap:10px}.hn-cat,.hn-year{display:none}.hn-membership{margin:0 20px 70px}.hn-mem-inner{padding:40px 24px}.hn-foot-top{grid-template-columns:1fr 1fr}.hn-foot-brand{grid-column:1/3}.hn-foot-bottom{flex-direction:column;gap:10px;align-items:flex-start}.hn-hover-preview{display:none}.hn-hero{min-height:620px}.hn-hero-cn{letter-spacing:.7em;text-indent:.7em}.hn-hero-sub{font-size:11px;letter-spacing:.14em}.hn-hero-bottom .hn-now{max-width:220px}.hn-now b{font-size:12px}.hn-scroll{display:none}}
@media(prefers-reduced-motion:reduce){.hn-hero-bg img{animation:none}.hn-reveal{opacity:1;transform:none;transition:none}.hn-home{scroll-behavior:auto}}
</style>

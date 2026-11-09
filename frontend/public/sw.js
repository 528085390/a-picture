/* NOCTURNE 夜之馆 Service Worker —— 只在 HTTPS 下注册（见 src/pwa/bootstrap.ts） */
const VERSION = 'nocturne-v1'
const SHELL = ['/', '/index.html', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png']
const MAX_THUMBS = 600
const MAX_IMAGES = 120

self.addEventListener('install', event => {
  event.waitUntil(caches.open(VERSION).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(key => key !== VERSION).map(key => caches.delete(key))))
      .then(() => self.clients.claim())
  )
})

async function trim(cacheName, max) {
  const cache = await caches.open(cacheName)
  const keys = await cache.keys()
  if (keys.length <= max) return
  await Promise.all(keys.slice(0, keys.length - max).map(key => cache.delete(key)))
}

async function cacheFirst(request, cacheName, max) {
  const cache = await caches.open(cacheName)
  const hit = await cache.match(request)
  if (hit) return hit
  try {
    const response = await fetch(request)
    if (response.ok || response.type === 'opaque') {
      await cache.put(request, response.clone())
      trim(cacheName, max)
    }
    return response
  } catch (error) {
    return hit || Response.error()
  }
}

self.addEventListener('fetch', event => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return

  if (url.pathname.startsWith('/api/picture/thumb') || url.pathname.startsWith('/api/picture/file')) {
    event.respondWith(cacheFirst(request, VERSION + '-thumbs', MAX_THUMBS))
    return
  }
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(fetch(request).catch(() => caches.match(request)))
    return
  }
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.match('/index.html').then(hit => hit || caches.match('/'))))
    return
  }
  event.respondWith(
    caches.open(VERSION + '-assets').then(async cache => {
      const hit = await cache.match(request)
      const network = fetch(request).then(response => {
        if (response.ok) cache.put(request, response.clone())
        return response
      }).catch(() => hit)
      return hit || network
    })
  )
})

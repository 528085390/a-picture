import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import type { Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 把路由懒加载的分包全部提前 modulepreload：
 * 现在每个 chunk 都要等上一个 chunk 执行完才被发现，串行 RTT 把首屏拖到 2.3s。
 * 预加载后这些几百字节的小包并行下载，首屏少掉 ~1s。
 */
function modulePreloadRoutes(): Plugin {
  return {
    name: 'modulepreload-routes',
    enforce: 'post',
    transformIndexHtml: {
      order: 'post',
      handler(html, ctx) {
        const bundle = ctx.bundle as Record<string, any> | undefined
        if (!bundle) return html
        const entry = Object.values(bundle).find((f: any) => f.type === 'chunk' && f.isEntry) as any
        if (!entry) return html
        const seen = new Set<string>()
        const links: string[] = []
        const walk = (fileName: string) => {
          const chunk = bundle[fileName] as any
          if (!chunk || chunk.type !== 'chunk') return
          for (const dep of (chunk.dynamicImports || []) as string[]) {
            if (seen.has(dep)) continue
            seen.add(dep)
            links.push('<link rel="modulepreload" crossorigin href="/' + dep + '">')
            walk(dep)
          }
        }
        walk(entry.fileName)
        if (!links.length) return html
        return html.replace('</head>', '    ' + links.join('\n    ') + '\n  </head>')
      },
    },
  }
}

const apiTarget = process.env.VITE_API_TARGET || 'http://127.0.0.1:8000'

export default defineConfig({
  plugins: [vue(), modulePreloadRoutes()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  preview: {
    port: 5174,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true, ws: true },
    },
  },
  server: {
    watch: { ignored: ['**/scratch/**', '**/shots/**', '**/design-samples/**', '**/dist/**'] },
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true, ws: true },
    },
  },
})
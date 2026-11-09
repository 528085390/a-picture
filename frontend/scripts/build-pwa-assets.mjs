#!/usr/bin/env node
/**
 * build-pwa-assets.mjs — 生成 NOCTURNE 夜之馆 的 iOS PWA 图标与启动图
 *
 * 启动一个临时 Chrome（--headless=new + 随机远程调试端口 + 临时用户目录），
 * 通过 CDP Page.captureScreenshot 按精确像素出图；最后读取每个 PNG 的 IHDR
 * 校验真实宽高与位深/颜色类型。脚本可重复运行，输出文件会被覆盖。
 *
 * 用法：
 *   node scripts/build-pwa-assets.mjs
 *   node scripts/build-pwa-assets.mjs --chrome "D:/Chrome/chrome.exe"
 *   node scripts/build-pwa-assets.mjs --out public
 *
 * 依赖：Node.js >= 22（内置 WebSocket / fetch），本机安装 Google Chrome。
 */

import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import { existsSync } from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, '..');
const DEFAULT_CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const GOLD = '#C9A96A';
const CREAM = '#EDE6D6';

/* 资源清单 */

const ICONS = [
  { file: 'public/icons/icon-180.png', width: 180, height: 180, mode: 'icon' },
  { file: 'public/icons/icon-192.png', width: 192, height: 192, mode: 'icon' },
  { file: 'public/icons/icon-512.png', width: 512, height: 512, mode: 'icon' },
  { file: 'public/icons/maskable-512.png', width: 512, height: 512, mode: 'maskable' },
  { file: 'public/icons/apple-touch-icon.png', width: 180, height: 180, mode: 'icon' },
];

const SPLASHES = [
  [1290, 2796], [1179, 2556], [1284, 2778], [1170, 2532], [1125, 2436],
  [1242, 2688], [828, 1792], [750, 1334], [2796, 1290], [2556, 1179],
].map(([width, height]) => ({
  file: `public/splash/splash-${width}x${height}.png`,
  width,
  height,
  mode: 'splash',
}));

/* HTML 画布
 * 几何（相对 lockup 边长 S）：
 *  - 圆环外径 52% S，线宽 1.5% S，金色 55% 透明度
 *  - N 字号 34% S，衬线体，居中于圆环
 *  - NOCTURNE 字号 4.4% S，字距 .42em，圆环下方 12% S
 *  - 图标：S = 画布宽；maskable：S = 画布宽 * 60%（整体缩小居中）
 *  - 启动图：整体宽度约短边 42%。竖屏即画布宽 42%；横屏用短边，
 *    保持与竖屏相同的物理尺寸，避免宽画布把标志撑破高度。
 */
function buildHtml({ width, height, mode }) {
  const shortEdge = Math.min(width, height);
  let lockup;
  let background;

  if (mode === 'splash') {
    lockup = (shortEdge * 0.42) / 0.52; // 圆环是最宽元素，外径 = 短边 42%
    background = '#000000';
  } else {
    lockup = mode === 'maskable' ? width * 0.6 : width;
    background = `radial-gradient(circle at 50% 50%, #111114 0%, #000000 72%)`;
  }

  const ringStroke = lockup * 0.015;

  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>NOCTURNE</title>
<style>
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
  body { display: grid; place-items: center; }
  .stage {
    position: fixed; inset: 0;
    display: grid; place-items: center;
    background: ${background};
    background-color: #000;
  }
  .lockup {
    width: ${lockup}px;
    height: ${lockup}px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }
  .ring {
    position: relative;
    flex: 0 0 auto;
    width: 52%;
    height: 52%;
    border: ${ringStroke}px solid rgba(201, 169, 106, 0.55);
    border-radius: 50%;
    display: grid;
    place-items: center;
  }
  .monogram {
    font-family: Georgia, 'Times New Roman', Times, serif;
    font-weight: 400;
    font-size: ${lockup * 0.34}px;
    line-height: 1;
    color: ${GOLD};
    transform: translateY(-0.01em); /* 视觉微调：让 N 的字面中心对准圆环中心 */
    transform-origin: 50% 50%;
  }
  .wordmark {
    margin-top: 12%;
    margin-right: -0.42em; /* 抵消末字符后多出的字距，保证视觉居中 */
    font-family: Georgia, 'Times New Roman', Times, serif;
    font-weight: 400;
    font-size: ${lockup * 0.044}px;
    line-height: 1;
    letter-spacing: 0.42em;
    text-transform: uppercase;
    white-space: nowrap;
    color: ${CREAM};
    opacity: 0.78;
  }
</style>
</head>
<body>
  <div class="stage">
    <div class="lockup">
      <div class="ring"><span class="monogram">N</span></div>
      <div class="wordmark">NOCTURNE</div>
    </div>
  </div>
</body>
</html>`;
}

/* 小工具 */

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function parseArgs(argv) {
  const opts = { chrome: process.env.CHROME_PATH || DEFAULT_CHROME, out: null };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--chrome') opts.chrome = argv[++i];
    else if (argv[i] === '--out') opts.out = argv[++i];
    else if (argv[i] === '--help' || argv[i] === '-h') {
      console.log('用法: node scripts/build-pwa-assets.mjs [--chrome <chrome.exe>] [--out <public 目录>]');
      process.exit(0);
    }
  }
  return opts;
}

async function getFreePort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  const { port } = server.address();
  await new Promise((resolve) => server.close(resolve));
  return port;
}

async function waitForJson(url, timeoutMs, isDead) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    if (isDead && isDead()) throw new Error('Chrome 进程提前退出');
    try {
      const response = await fetch(url);
      if (response.ok) return await response.json();
      lastError = new Error('HTTP ' + response.status);
    } catch (error) {
      lastError = error;
    }
    await delay(150);
  }
  throw new Error('等待 ' + url + ' 超时: ' + (lastError ? lastError.message : '未知错误'));
}

/* 极简 CDP 客户端 */

class CDP {
  constructor(ws) {
    this.ws = ws;
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Map();
    this.closed = false;

    ws.addEventListener('message', (event) => {
      let message;
      try {
        message = JSON.parse(typeof event.data === 'string' ? event.data : Buffer.from(event.data).toString('utf8'));
      } catch {
        return;
      }
      if (message.id && this.pending.has(message.id)) {
        const { resolve, reject, method } = this.pending.get(message.id);
        this.pending.delete(message.id);
        if (message.error) reject(new Error('CDP ' + method + ' 失败: ' + message.error.message));
        else resolve(message.result);
        return;
      }
      if (message.method) {
        const handlers = this.listeners.get(message.method);
        if (handlers) for (const handler of [...handlers]) handler(message.params);
      }
    });

    ws.addEventListener('close', () => {
      this.closed = true;
      for (const { reject, method } of this.pending.values()) {
        reject(new Error('CDP 连接已关闭（等待 ' + method + '）'));
      }
      this.pending.clear();
    });
  }

  static async connect(url) {
    const ws = new WebSocket(url);
    await new Promise((resolve, reject) => {
      ws.addEventListener('open', resolve, { once: true });
      ws.addEventListener('error', () => reject(new Error('无法建立 CDP WebSocket 连接')), { once: true });
    });
    return new CDP(ws);
  }

  send(method, params = {}) {
    if (this.closed) return Promise.reject(new Error('CDP 连接已关闭'));
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject, method });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  on(method, handler) {
    if (!this.listeners.has(method)) this.listeners.set(method, new Set());
    this.listeners.get(method).add(handler);
    return () => this.listeners.get(method)?.delete(handler);
  }

  once(method, timeoutMs = 15000) {
    return new Promise((resolve, reject) => {
      const off = this.on(method, (params) => {
        clearTimeout(timer);
        off();
        resolve(params);
      });
      const timer = setTimeout(() => {
        off();
        reject(new Error('等待 CDP 事件 ' + method + ' 超时'));
      }, timeoutMs);
    });
  }

  close() {
    try { this.ws.close(); } catch { /* ignore */ }
  }
}

/* Chrome 生命周期 */

async function launchChrome(chromePath) {
  if (!existsSync(chromePath)) {
    throw new Error('找不到 Chrome：' + chromePath + '\n请安装 Google Chrome，或用 --chrome 指定 chrome.exe 路径。');
  }

  const port = await getFreePort();
  const profileDir = await fs.mkdtemp(path.join(os.tmpdir(), 'nocturne-pwa-'));
  const args = [
    '--headless=new',
    '--remote-debugging-port=' + port,
    '--remote-debugging-address=127.0.0.1',
    '--user-data-dir=' + profileDir,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-extensions',
    '--disable-default-apps',
    '--disable-background-networking',
    '--disable-component-update',
    '--disable-sync',
    '--mute-audio',
    '--hide-scrollbars',
    '--force-device-scale-factor=1',
    '--window-size=800,600',
    'about:blank',
  ];

  const child = spawn(chromePath, args, { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
  let stderr = '';
  child.stderr.on('data', (chunk) => { stderr = (stderr + chunk.toString()).slice(-4000); });
  child.stdout.resume();

  let exited = false;
  child.once('exit', () => { exited = true; });

  const spawnError = new Promise((_, reject) => {
    child.once('error', (error) => reject(new Error('无法启动 Chrome：' + error.message)));
  });

  try {
    await Promise.race([
      waitForJson('http://127.0.0.1:' + port + '/json/version', 30000, () => exited),
      spawnError,
    ]);
  } catch (error) {
    child.kill();
    await fs.rm(profileDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => {});
    throw new Error(error.message + (stderr ? '\nChrome stderr:\n' + stderr : ''));
  }

  return { child, port, profileDir };
}

async function stopChrome({ child, profileDir }) {
  if (child && child.exitCode === null) {
    if (process.platform === 'win32' && child.pid) {
      await new Promise((resolve) => {
        const killer = spawn('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true });
        killer.once('exit', resolve);
        killer.once('error', resolve);
      });
    } else {
      child.kill('SIGTERM');
    }
    await Promise.race([
      new Promise((resolve) => child.once('exit', resolve)),
      delay(4000),
    ]);
    if (child.exitCode === null) child.kill('SIGKILL');
  }
  if (profileDir) {
    await fs.rm(profileDir, { recursive: true, force: true, maxRetries: 10, retryDelay: 300 }).catch(() => {});
  }
}

/* 截图 */

async function findPageTarget(port) {
  const deadline = Date.now() + 20000;
  while (Date.now() < deadline) {
    const targets = await waitForJson('http://127.0.0.1:' + port + '/json/list', 5000);
    const page = targets.find((target) => target.type === 'page' && target.webSocketDebuggerUrl);
    if (page) return page;
    await delay(150);
  }
  throw new Error('Chrome 没有可用的 page target');
}

async function capturePng(cdp, asset) {
  const { width, height } = asset;
  await cdp.send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: false,
    screenWidth: width,
    screenHeight: height,
  });

  const loaded = cdp.once('Page.loadEventFired', 15000);
  const dataUrl = 'data:text/html;charset=utf-8,' + encodeURIComponent(buildHtml(asset));
  await cdp.send('Page.navigate', { url: dataUrl });
  await loaded;

  const evaluated = await cdp.send('Runtime.evaluate', {
    expression: `(async () => {
      await document.fonts.ready;
      await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      const rect = (selector) => document.querySelector(selector).getBoundingClientRect().toJSON();
      return {
        innerWidth: window.innerWidth,
        innerHeight: window.innerHeight,
        dpr: window.devicePixelRatio,
        fonts: document.fonts.status,
        ring: rect('.ring'),
        monogram: rect('.monogram'),
        wordmark: rect('.wordmark'),
      };
    })()`,
    awaitPromise: true,
    returnByValue: true,
  });

  const metrics = evaluated.result?.value;
  if (!metrics || metrics.innerWidth !== width || metrics.innerHeight !== height || metrics.dpr !== 1) {
    throw new Error('页面视口不符合预期：' + JSON.stringify(metrics));
  }

  const shot = await cdp.send('Page.captureScreenshot', {
    format: 'png',
    fromSurface: true,
    captureBeyondViewport: false,
    clip: { x: 0, y: 0, width, height, scale: 1 },
  });

  return { buffer: Buffer.from(shot.data, 'base64'), metrics };
}

/* PNG IHDR 校验 */

function readIhdr(buffer, file) {
  if (buffer.length < 33) throw new Error(file + ' 不是有效 PNG：文件太短');
  const signature = buffer.subarray(0, 8).toString('hex');
  if (signature !== '89504e470d0a1a0a') throw new Error(file + ' 不是有效 PNG：签名错误 (' + signature + ')');
  if (buffer.toString('ascii', 12, 16) !== 'IHDR') throw new Error(file + ' 不是有效 PNG：缺少 IHDR');
  return {
    width: buffer.readUInt32BE(16),
    height: buffer.readUInt32BE(20),
    bitDepth: buffer[24],
    colorType: buffer[25],
    interlace: buffer[28],
  };
}

const COLOR_TYPES = { 0: 'gray', 2: 'RGB', 3: 'palette', 4: 'gray+alpha', 6: 'RGBA' };
const formatBytes = (bytes) => bytes.toLocaleString('en-US');

function printVerification(rows) {
  const headers = ['file', 'expected', 'actual', 'PNG', 'bytes', 'status'];
  const widths = [46, 11, 11, 10, 10, 6];
  const line = (left, mid, right) => left + widths.map((w) => '─'.repeat(w + 2)).join(mid) + right;
  const row = (cells) => '│ ' + cells.map((cell, i) => String(cell).padEnd(widths[i])).join(' │ ') + ' │';

  console.log('');
  console.log('PNG IHDR 校验');
  console.log(line('┌', '┬', '┐'));
  console.log(row(headers));
  console.log(line('├', '┼', '┤'));
  for (const item of rows) {
    console.log(row([
      item.file,
      item.expectedWidth + '×' + item.expectedHeight,
      item.width + '×' + item.height,
      item.bitDepth + '/' + (COLOR_TYPES[item.colorType] || item.colorType),
      formatBytes(item.bytes),
      item.ok ? 'OK' : 'FAIL',
    ]));
  }
  console.log(line('└', '┴', '┘'));
}

/* 主流程 */

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  const publicDir = opts.out ? path.resolve(opts.out) : path.join(PROJECT_ROOT, 'public');

  const assets = [...ICONS, ...SPLASHES].map((asset) => ({
    ...asset,
    file: asset.file.replace(/^public\//, ''),
    absolutePath: path.join(publicDir, asset.file.replace(/^public\//, '')),
  }));

  // 先确认 Chrome 存在，避免删掉旧资源后才发现无法出图
  if (!existsSync(opts.chrome)) {
    throw new Error('找不到 Chrome：' + opts.chrome + '\n请安装 Google Chrome，或用 --chrome 指定 chrome.exe 路径。');
  }

  console.log('NOCTURNE PWA assets');
  console.log('Chrome : ' + opts.chrome);
  console.log('输出   : ' + publicDir);
  console.log('资源   : ' + assets.length + ' 个（' + ICONS.length + ' 图标 + ' + SPLASHES.length + ' 启动图）');

  // 先删旧图，确保任何中途失败都不会留下“看起来存在”的旧文件
  for (const asset of assets) await fs.rm(asset.absolutePath, { force: true });
  await fs.mkdir(path.join(publicDir, 'icons'), { recursive: true });
  await fs.mkdir(path.join(publicDir, 'splash'), { recursive: true });

  let chrome;
  let cdp;
  const startedAt = Date.now();

  try {
    chrome = await launchChrome(opts.chrome);
    console.log('CDP 端口: ' + chrome.port + '（临时实例，用户目录 ' + chrome.profileDir + '）');

    const target = await findPageTarget(chrome.port);
    cdp = await CDP.connect(target.webSocketDebuggerUrl);
    await cdp.send('Page.enable');
    await cdp.send('Runtime.enable');

    for (const asset of assets) {
      const { buffer, metrics } = await capturePng(cdp, asset);
      const info = readIhdr(buffer, asset.file);
      if (info.width !== asset.width || info.height !== asset.height) {
        throw new Error(asset.file + ' 输出尺寸错误：期望 ' + asset.width + '×' + asset.height + '，实际 ' + info.width + '×' + info.height);
      }
      await fs.writeFile(asset.absolutePath, buffer);
      console.log(
        '✓ ' + asset.file.padEnd(44) + ' ' + String(info.width + '×' + info.height).padEnd(10) +
        formatBytes(buffer.length).padStart(9) + ' B  ring@' + metrics.ring.x.toFixed(0) + ',' + metrics.ring.y.toFixed(0) +
        ' N@' + metrics.monogram.x.toFixed(0) + ',' + metrics.monogram.y.toFixed(0) +
        ' word@' + metrics.wordmark.x.toFixed(0) + ',' + metrics.wordmark.y.toFixed(0)
      );
    }

    // 用真实文件做 IHDR 复核（不是复用内存里的 buffer）
    const rows = [];
    let allOk = true;
    for (const asset of assets) {
      const buffer = await fs.readFile(asset.absolutePath);
      const info = readIhdr(buffer, asset.file);
      const ok = info.width === asset.width && info.height === asset.height && info.bitDepth === 8;
      if (!ok) allOk = false;
      rows.push({ file: asset.file, expectedWidth: asset.width, expectedHeight: asset.height, bytes: buffer.length, ok, ...info });
    }
    printVerification(rows);

    const totalBytes = rows.reduce((sum, row) => sum + row.bytes, 0);
    const seconds = ((Date.now() - startedAt) / 1000).toFixed(1);
    console.log('共 ' + rows.length + ' 个 PNG，合计 ' + formatBytes(totalBytes) + ' B，用时 ' + seconds + 's。');
    if (!allOk) throw new Error('存在尺寸校验失败的 PNG');
    console.log('全部通过：PNG 签名、IHDR 宽高、8-bit 位深。');
  } finally {
    if (cdp) cdp.close();
    if (chrome) await stopChrome(chrome);
  }
}

main().catch((error) => {
  console.error('');
  console.error('生成失败：' + error.message);
  process.exit(1);
});

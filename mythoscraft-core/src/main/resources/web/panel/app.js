'use strict';
/* MythosCraft Panel – talks to the plugin's own web server (/api/...). */

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = (n) => Number(n || 0).toLocaleString('de-DE');
const head = (uuid) => `https://mc-heads.net/avatar/${encodeURIComponent(uuid)}/64`;

let me = null;
let actions = [];
let live = null;
let tpsHistory = [];
let lastSnap = null;

async function api(path, body) {
  const opt = body === undefined ? {} : {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Panel': '1' },
    body: JSON.stringify(body),
  };
  const res = await fetch(path, { credentials: 'same-origin', ...opt });
  const data = await res.json().catch(() => ({}));
  if (res.status === 401) {
    showLogin();
    throw new Error('login');
  }
  if (!res.ok) throw new Error(data.message || data.error || res.statusText);
  return data;
}

function toast(text, err = false) {
  const t = document.createElement('div');
  t.className = 'toast' + (err ? ' err' : '');
  t.textContent = text;
  $('#toasts').append(t);
  setTimeout(() => t.remove(), 4200);
}

/* ------------------------------------------------------------------ background particles */
(function particles() {
  const c = $('#bg');
  const ctx = c.getContext('2d');
  let w, h, pts;
  const resize = () => {
    w = c.width = innerWidth;
    h = c.height = innerHeight;
    pts = Array.from({ length: Math.min(90, Math.floor(w / 16)) }, () => ({
      x: Math.random() * w, y: Math.random() * h, vx: (Math.random() - 0.5) * 0.3, vy: (Math.random() - 0.5) * 0.3,
    }));
  };
  resize();
  addEventListener('resize', resize);
  (function frame() {
    ctx.clearRect(0, 0, w, h);
    for (const p of pts) {
      p.x = (p.x + p.vx + w) % w;
      p.y = (p.y + p.vy + h) % h;
      ctx.fillStyle = 'rgba(160,140,255,.5)';
      ctx.fillRect(p.x, p.y, 1.6, 1.6);
    }
    for (let i = 0; i < pts.length; i++) {
      for (let j = i + 1; j < pts.length; j++) {
        const dx = pts[i].x - pts[j].x;
        const dy = pts[i].y - pts[j].y;
        const d = dx * dx + dy * dy;
        if (d < 12000) {
          ctx.strokeStyle = `rgba(34,211,238,${0.12 * (1 - d / 12000)})`;
          ctx.beginPath();
          ctx.moveTo(pts[i].x, pts[i].y);
          ctx.lineTo(pts[j].x, pts[j].y);
          ctx.stroke();
        }
      }
    }
    requestAnimationFrame(frame);
  })();
})();

/* ------------------------------------------------------------------ login */
let pollTimer = null;

function showLogin() {
  if (live) live.close();
  clearInterval(polling);
  polling = null;
  $('#app').classList.add('hidden');
  $('#login').classList.remove('hidden');
  $('#login-start').classList.remove('hidden');
  $('#login-code').classList.add('hidden');
}

$('#btn-login').addEventListener('click', async () => {
  $('#login-error').textContent = '';
  try {
    const r = await api('/api/login/start', {});
    $('#code').textContent = r.code;
    $('#login-start').classList.add('hidden');
    $('#login-code').classList.remove('hidden');
    let left = r.expires;
    clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
      left -= 2;
      $('#countdown').textContent = `(${Math.max(0, left)}s)`;
      const p = await fetch('/api/login/poll?id=' + encodeURIComponent(r.id), { credentials: 'same-origin' }).then((x) => x.json());
      if (p.state === 'ok') {
        clearInterval(pollTimer);
        start();
      } else if (p.state === 'denied') {
        clearInterval(pollTimer);
        showLogin();
        $('#login-error').textContent = 'No access to the team panel. Players: use the wiki for daily rewards.';
      } else if (p.state === 'expired' || left <= 0) {
        clearInterval(pollTimer);
        showLogin();
        $('#login-error').textContent = 'Code abgelaufen – bitte neu anmelden.';
      }
    }, 2000);
  } catch (e) {
    $('#login-error').textContent = e.message === 'rate' ? 'Zu viele Versuche – kurz warten.' : 'Server nicht erreichbar.';
  }
});

$('#btn-copy').addEventListener('click', () => {
  navigator.clipboard?.writeText('/website login ' + $('#code').textContent);
  toast('Befehl kopiert – jetzt ingame einfügen');
});

$('#btn-logout').addEventListener('click', async () => {
  await api('/api/logout', {}).catch(() => {});
  showLogin();
});

/* ------------------------------------------------------------------ app start */
async function start() {
  try {
    me = await api('/api/me');
  } catch {
    return;
  }
  $('#login').classList.add('hidden');
  $('#app').classList.remove('hidden');
  $('#me-name').textContent = me.name;
  $('#me-role').textContent = me.role;
  $('#me-head').src = head(me.uuid);
  const rank = { view: 0, edit: 1, admin: 2, owner: 3 }[me.role];
  $$('.sidebar nav a').forEach((a) => {
    const need = a.dataset.role;
    if (need === 'admin' && rank < 2) a.classList.add('hidden');
    if (need === 'owner' && rank < 3) a.classList.add('hidden');
  });
  if (me.owner) hologram(me.uuid);
  connectLive();
  actions = await api('/api/actions').catch(() => []);
  renderActions();
}

/* ------------------------------------------------------------------ navigation */
$$('.sidebar nav a[data-page]').forEach((a) => a.addEventListener('click', () => go(a.dataset.page)));

function go(page) {
  $$('.sidebar nav a').forEach((x) => x.classList.toggle('active', x.dataset.page === page));
  $$('.page').forEach((p) => p.classList.add('hidden'));
  const el = $('#page-' + page);
  el.classList.remove('hidden');
  el.style.animation = 'none';
  void el.offsetWidth;
  el.style.animation = '';
  if (page === 'files') loadFile($('#file-select').value || 'config.yml');
  if (page === 'access') loadAccess();
  if (page === 'log') loadLog();
  if (page === 'players') renderPlayerList();
}

/* ------------------------------------------------------------------ live dashboard */
/* Live data: a stream when possible – behind proxies that cut long connections (e.g. Netlify) it falls back to
   asking every 2 seconds, which looks just as live. */
let streamFails = 0;
let polling = null;
function connectLive() {
  if (live) live.close();
  if (streamFails >= 2) {
    startPolling();
    return;
  }
  const opened = Date.now();
  live = new EventSource('/api/live');
  live.onmessage = (e) => render(JSON.parse(e.data));
  live.onerror = () => {
    live.close();
    if (Date.now() - opened < 60000) streamFails++;
    setTimeout(() => api('/api/me').then(connectLive).catch(() => {}), 1500);
  };
}
function startPolling() {
  if (polling) return;
  const tick = () => api('/api/stats').then(render).catch(() => {});
  tick();
  polling = setInterval(tick, 2000);
}

function tween(el, to, suffix = '') {
  const from = Number(el.dataset.v || 0);
  el.dataset.v = to;
  const t0 = performance.now();
  const step = (t) => {
    const k = Math.min(1, (t - t0) / 700);
    const e = 1 - Math.pow(1 - k, 3);
    el.textContent = fmt(Math.round(from + (to - from) * e)) + suffix;
    if (k < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

function duration(s) {
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  return (d ? d + 'd ' : '') + h + 'h ' + m + 'm';
}

function render(s) {
  lastSnap = s;
  $('#tps').textContent = s.tps.toFixed(1);
  const ring = $('#tps-ring');
  ring.style.strokeDashoffset = 327 * (1 - Math.min(20, s.tps) / 20);
  ring.style.stroke = s.tps > 18 ? '#4ade80' : s.tps > 14 ? '#facc15' : '#f87171';
  $('#mspt').textContent = s.mspt + ' ms/Tick';
  tween($('#online'), s.online);
  $('#max').textContent = 'von ' + s.max;
  tween($('#ram'), s.ramUsed, ' MB');
  $('#ram-bar').style.width = Math.round((s.ramUsed / s.ramMax) * 100) + '%';
  $('#uptime').textContent = duration(s.uptime);
  $('#world-info').textContent = `${s.worlds} Welten · ${fmt(s.chunks)} Chunks · ${fmt(s.entities)} Entities`;
  tween($('#coins'), s.coinsOnline);
  tween($('#gems'), s.gemsOnline);
  const ev = s.event;
  const bg = s.background;
  $('#event-box').innerHTML = (ev ? `⚡ ${esc(ev.name)} <span class="timer">${Math.floor(ev.seconds / 60)}:${String(ev.seconds % 60).padStart(2, '0')}</span>`
    : `<span class="sub">Kein Event – nächstes in ${Math.ceil(s.nextEvent / 60)} Min.</span>`)
    + (bg ? `<br><small class="sub">Ganztägig: ${esc(bg.name)}</small>` : '')
    + (s.giveaway ? '<br><small>🎁 Verlosung läuft</small>' : '');
  $('#season').textContent = s.season || 'keine';
  $('#clanwar').innerHTML = s.clanWar.length ? s.clanWar.map((c) => `<li><span>[${esc(c.tag)}] ${esc(c.name)}</span><b>${fmt(c.points)}</b></li>`).join('')
    : '<li class="sub">noch keine Punkte</li>';
  $('#player-cards').innerHTML = s.players.map((p, i) => `
    <div class="pcard" style="animation-delay:${i * 40}ms" data-name="${esc(p.name)}">
      <img src="${head(p.uuid)}" alt=""><div><b>${esc(p.name)}</b><small>${esc(p.rank)} · ${p.ping} ms</small><small>💰 ${fmt(p.coins)} · 💎 ${fmt(p.gems)}</small></div>
    </div>`).join('') || '<p class="sub">Niemand online</p>';
  $$('#player-cards .pcard').forEach((c) => c.addEventListener('click', () => { go('players'); showPlayer(c.dataset.name); }));
  tpsHistory.push(s.tps);
  if (tpsHistory.length > 90) tpsHistory.shift();
  chart();
  if (!$('#page-players').classList.contains('hidden')) renderPlayerList();
}

function chart() {
  const c = $('#tps-chart');
  const w = (c.width = c.clientWidth * devicePixelRatio);
  const h = (c.height = 120 * devicePixelRatio);
  const ctx = c.getContext('2d');
  ctx.clearRect(0, 0, w, h);
  if (tpsHistory.length < 2) return;
  const x = (i) => (i / Math.max(1, tpsHistory.length - 1)) * w;
  const y = (v) => h - (Math.min(20, v) / 20) * (h - 10) - 5;
  const g = ctx.createLinearGradient(0, 0, w, 0);
  g.addColorStop(0, '#22d3ee');
  g.addColorStop(1, '#a855f7');
  ctx.beginPath();
  tpsHistory.forEach((v, i) => (i ? ctx.lineTo(x(i), y(v)) : ctx.moveTo(x(i), y(v))));
  ctx.strokeStyle = g;
  ctx.lineWidth = 2.5 * devicePixelRatio;
  ctx.shadowColor = '#22d3ee';
  ctx.shadowBlur = 12;
  ctx.stroke();
  ctx.lineTo(x(tpsHistory.length - 1), h);
  ctx.lineTo(0, h);
  const f = ctx.createLinearGradient(0, 0, 0, h);
  f.addColorStop(0, 'rgba(34,211,238,.25)');
  f.addColorStop(1, 'rgba(34,211,238,0)');
  ctx.fillStyle = f;
  ctx.shadowBlur = 0;
  ctx.fill();
}

/* ------------------------------------------------------------------ owner hologram */
function hologram(uuid) {
  $('#hologram').classList.remove('hidden');
  const canvas = $('#holo-canvas');
  try {
    if (!window.skinview3d) throw new Error('no skinview');
    const viewer = new skinview3d.SkinViewer({ canvas, width: 220, height: 300, skin: `https://mc-heads.net/skin/${uuid}` });
    viewer.background = null;
    viewer.zoom = 0.85;
    viewer.autoRotate = true;
    viewer.autoRotateSpeed = 0.6;
    viewer.animation = new skinview3d.IdleAnimation();
    viewer.controls.enableZoom = false;
  } catch {
    canvas.classList.add('hidden');
    const img = $('#holo-fallback');
    img.src = `https://mc-heads.net/body/${uuid}/220`;
    img.classList.remove('hidden');
  }
}

/* ------------------------------------------------------------------ players */
function renderPlayerList() {
  const q = $('#player-search').value.toLowerCase();
  const list = (lastSnap?.players || []).filter((p) => p.name.toLowerCase().includes(q));
  $('#player-list').innerHTML = list.map((p) => `
    <div class="item" data-name="${esc(p.name)}"><img src="${head(p.uuid)}" alt=""><span class="grow">${esc(p.name)}</span><small class="sub">${esc(p.rank)}</small></div>`).join('')
    || '<p class="sub">Niemand online</p>';
  $$('#player-list .item').forEach((i) => i.addEventListener('click', () => showPlayer(i.dataset.name)));
}
$('#player-search').addEventListener('input', renderPlayerList);

async function showPlayer(name) {
  const p = await api('/api/player?name=' + encodeURIComponent(name)).catch((e) => ({ error: e.message }));
  const box = $('#player-detail');
  if (p.error) {
    box.innerHTML = `<p class="sub">${esc(name)} ist offline.</p>`;
    return;
  }
  box.innerHTML = `
    <div class="detail-head"><img src="https://mc-heads.net/body/${p.uuid}/120" alt=""><div><h2>${esc(p.name)}</h2><span class="pill">${esc(p.rank)}</span></div></div>
    <div class="kv">
      <div><small>Coins</small><b>${fmt(p.coins)}</b></div><div><small>Gems</small><b>${fmt(p.gems)}</b></div>
      <div><small>Leben</small><b>${p.health} ❤</b></div><div><small>Hunger</small><b>${p.food}</b></div>
      <div><small>Level</small><b>${p.level}</b></div><div><small>Homes</small><b>${p.homes}</b></div>
      <div><small>Welt</small><b>${esc(p.world)}</b></div><div><small>Position</small><b>${esc(p.location)}</b></div>
    </div>
    <h3>Jobs</h3><div class="chips">${Object.entries(p.jobs).map(([k, v]) => `<span class="chip">${esc(k)} Lv ${v}</span>`).join('')}</div>
    <div class="quick">
      ${['eco', 'rank', 'item', 'key', 'magic', 'kick'].map((id) => {
        const a = actions.find((x) => x.id === id);
        return a ? `<button class="btn ghost small" data-act="${id}">${a.icon} ${esc(a.label)}</button>` : '';
      }).join('')}
    </div>
    <h3>Inventar</h3><div class="chips">${p.inventory.map((i) => `<span class="chip">${esc(i)}</span>`).join('') || '<span class="sub">leer</span>'}</div>`;
  $$('#player-detail [data-act]').forEach((b) => b.addEventListener('click', () => openAction(b.dataset.act, { player: p.name })));
  $$('#player-list .item').forEach((i) => i.classList.toggle('sel', i.dataset.name === p.name));
}

/* ------------------------------------------------------------------ actions */
function renderActions() {
  const groups = {};
  actions.forEach((a) => (groups[a.category] ||= []).push(a));
  const canEdit = me.role !== 'view';
  const isAdmin = me.role === 'admin' || me.role === 'owner';
  $('#action-groups').innerHTML = Object.entries(groups).map(([g, list]) => `
    <div class="group"><h2>${esc(g)}</h2><div class="action-grid">
      ${list.filter((a) => !a.admin || isAdmin).map((a, i) => `
        <div class="action glass" data-id="${a.id}" style="animation-delay:${i * 35}ms">
          <div class="ico">${a.icon}</div><div><b>${esc(a.label)}</b><small>${a.params.length ? a.params.length + ' Optionen' : 'sofort'}</small></div>
        </div>`).join('')}
    </div></div>`).join('');
  $$('.action').forEach((el) => el.addEventListener('click', () => {
    if (!canEdit) return toast('Du darfst nur ansehen.', true);
    openAction(el.dataset.id);
  }));
}

let current = null;
function openAction(id, preset = {}) {
  current = actions.find((a) => a.id === id);
  if (!current) return;
  $('#modal-title').textContent = current.icon + ' ' + current.label;
  $('#modal-out').classList.add('hidden');
  $('#modal-form').innerHTML = current.params.map((p) => {
    const val = preset[p.key] ?? p.def;
    if (p.type === 'select' || p.type === 'player') {
      const opts = p.options.length ? p.options : [''];
      return `<label>${esc(p.label)}<select class="input" name="${p.key}">${opts.map((o) => `<option ${o === val ? 'selected' : ''}>${esc(o)}</option>`).join('')}</select>${p.type === 'player' ? '<input class="input" name="' + p.key + '__free" placeholder="…oder Name eingeben">' : ''}</label>`;
    }
    return `<label>${esc(p.label)}<input class="input" name="${p.key}" type="${p.type === 'number' ? 'number' : 'text'}" value="${esc(val)}"></label>`;
  }).join('') || '<p class="sub">Wird sofort ausgeführt.</p>';
  $('#modal').classList.remove('hidden');
}

$('#modal-cancel').addEventListener('click', () => $('#modal').classList.add('hidden'));
$('#modal').addEventListener('click', (e) => { if (e.target.id === 'modal') $('#modal').classList.add('hidden'); });
$('#modal-run').addEventListener('click', async () => {
  const params = {};
  current.params.forEach((p) => {
    const free = $(`[name="${p.key}__free"]`)?.value.trim();
    params[p.key] = free || $(`[name="${p.key}"]`).value;
  });
  const btn = $('#modal-run');
  btn.disabled = true;
  try {
    const r = await api('/api/action', { id: current.id, params });
    $('#modal-out').classList.remove('hidden');
    $('#modal-out').textContent = '/' + r.command + '\n' + r.output.join('\n');
    toast('✓ ' + current.label);
  } catch (e) {
    toast(e.message, true);
  } finally {
    btn.disabled = false;
  }
});

/* ------------------------------------------------------------------ console */
const cmdHistory = [];
let hIndex = 0;
$('#term-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const cmd = $('#term-in').value.trim();
  if (!cmd) return;
  cmdHistory.push(cmd);
  hIndex = cmdHistory.length;
  $('#term-in').value = '';
  const out = $('#term-out');
  out.insertAdjacentHTML('beforeend', `<div class="cmd">› ${esc(cmd)}</div>`);
  try {
    const r = await api('/api/command', { command: cmd });
    out.insertAdjacentHTML('beforeend', `<div>${esc(r.output.join('\n'))}</div>`);
  } catch (err) {
    out.insertAdjacentHTML('beforeend', `<div style="color:var(--red)">${esc(err.message)}</div>`);
  }
  out.scrollTop = out.scrollHeight;
});
$('#term-in').addEventListener('keydown', (e) => {
  if (e.key === 'ArrowUp' && hIndex > 0) $("#term-in").value = cmdHistory[--hIndex];
  if (e.key === 'ArrowDown' && hIndex < cmdHistory.length) $("#term-in").value = cmdHistory[++hIndex] || '';
});

/* ------------------------------------------------------------------ files */
async function loadFile(name) {
  const r = await api('/api/config?file=' + encodeURIComponent(name)).catch((e) => { toast(e.message, true); return null; });
  if (!r) return;
  $('#file-select').innerHTML = r.files.map((f) => `<option ${f === r.file ? 'selected' : ''}>${f}</option>`).join('');
  $('#editor').value = r.text;
  $('#file-status').textContent = r.file + ' geladen';
}
$('#file-select').addEventListener('change', (e) => loadFile(e.target.value));
$('#btn-save').addEventListener('click', async () => {
  try {
    const r = await api('/api/config', { file: $('#file-select').value, text: $('#editor').value });
    $('#file-status').textContent = 'Gespeichert (Backup angelegt) · ' + r.output.join(' ');
    toast('✓ Gespeichert & neu geladen');
  } catch (e) {
    $('#file-status').textContent = 'Fehler: ' + e.message;
    toast('YAML-Fehler – nicht gespeichert', true);
  }
});
$('#editor').addEventListener('keydown', (e) => {
  if (e.key === 'Tab') {
    e.preventDefault();
    const t = e.target;
    const s = t.selectionStart;
    t.value = t.value.slice(0, s) + '  ' + t.value.slice(t.selectionEnd);
    t.selectionStart = t.selectionEnd = s + 2;
  }
});

/* ------------------------------------------------------------------ access */
async function loadAccess() {
  const r = await api('/api/access').catch(() => ({ users: [] }));
  $('#access-list').innerHTML = r.users.map((u) => `
    <div class="item"><img src="${head(u.uuid)}" alt=""><span class="grow">${esc(u.name)}</span><span class="pill">${esc(u.role)}</span>
    <button class="btn danger small" data-name="${esc(u.name)}">Entfernen</button></div>`).join('') || '<p class="sub">Nur du hast Zugriff.</p>';
  $$('#access-list [data-name]').forEach((b) => b.addEventListener('click', async () => {
    await api('/api/access', { name: b.dataset.name, role: 'remove' });
    toast('Zugriff entfernt');
    loadAccess();
  }));
}
$('#access-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  await api('/api/access', { name: $('#access-name').value.trim(), role: $('#access-role').value });
  $('#access-name').value = '';
  toast('Zugriff vergeben');
  loadAccess();
});

/* ------------------------------------------------------------------ log */
async function loadLog() {
  const r = await api('/api/log').catch(() => ({ log: [] }));
  $('#log').textContent = r.log.slice().reverse().join('\n') || 'Noch nichts passiert.';
}

/* boot: already signed in? */
api('/api/me').then(start).catch(() => showLogin());

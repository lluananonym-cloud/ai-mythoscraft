'use strict';
const $ = s => document.querySelector(s);
let players = [], current = null, viewing = null, timer = null;

async function api(path, opts = {}) {
  const r = await fetch(path, Object.assign({credentials: 'same-origin', headers: {'X-Panel': '1', 'Content-Type': 'application/json'}}, opts));
  return {status: r.status, body: await r.json().catch(() => ({}))};
}
const esc = s => String(s).replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const when = t => t ? new Date(t).toLocaleString() : '–';
const nice = t => t.replace(/_/g, ' ');

async function start() {
  const me = await api('/api/me');
  if (me.status === 401) { $('#login').classList.remove('hidden'); return; }
  if (!['admin', 'owner'].includes(me.body.role)) { $('#denied').classList.remove('hidden'); return; }
  $('#who').textContent = me.body.name;
  $('#app').classList.remove('hidden');
  await loadList();
  $('#search').addEventListener('input', renderList);
  setInterval(loadList, 15000);
}

async function loadList() {
  const r = await api('/api/inventory/list');
  players = r.body.players || [];
  renderList();
}

function renderList() {
  const q = $('#search').value.toLowerCase();
  $('#players').innerHTML = players.filter(p => p.name.toLowerCase().includes(q)).map(p =>
    `<li data-id="${esc(p.uuid)}" class="${current === p.uuid ? 'on' : ''}"><span>${esc(p.name)}</span><span class="tag">${esc(p.where)}</span></li>`).join('')
    || '<p class="muted">Nobody yet.</p>';
  document.querySelectorAll('#players li').forEach(li => li.onclick = () => open(li.dataset.id));
}

function slots(items, count, from = 0) {
  const by = {};
  (items || []).forEach(i => by[i.slot] = i);
  let html = '';
  for (let s = from; s < from + count; s++) {
    const i = by[s];
    html += i ? `<div class="slot${i.custom ? ' custom' : ''}" title="${esc((i.name || nice(i.type)) + (i.custom ? ' [' + i.custom + ']' : ''))}">${esc(i.name || nice(i.type))}<b>${i.amount > 1 ? i.amount : ''}</b></div>`
      : '<div class="slot empty"></div>';
  }
  return html;
}

function inv(v) {
  if (!v || !v.inventory) return '<p class="muted">No stored inventory.</p>';
  return `<div class="stats"><span>Level ${v.level}</span><span>Health ${Math.round(v.health)}</span><span>Food ${v.food}</span><span class="muted">${when(v.time)}</span></div>
    <h3>Armour · off hand</h3><div class="grid">${slots(v.inventory, 4, 36)}${slots(v.inventory, 1, 40)}</div>
    <h3>Inventory</h3><div class="grid">${slots(v.inventory, 27, 9)}</div>
    <h3>Hotbar</h3><div class="grid">${slots(v.inventory, 9, 0)}</div>
    <h3>Ender chest</h3><div class="grid">${slots(v.ender, 27, 0)}</div>`;
}

async function open(id) {
  current = id; viewing = null;
  renderList();
  await refresh();
  clearInterval(timer);
  timer = setInterval(() => { if (!viewing) refresh(); }, 5000);   // live
}

async function refresh() {
  const r = await api('/api/inventory/get?uuid=' + encodeURIComponent(current));
  const d = r.body;
  $('#detail').innerHTML = `<h2>${esc(d.name)} <span class="tag">${esc(d.where)}</span></h2>
    <p class="muted">${esc(d.source)} · updates every 5 s</p>${inv(d.current)}
    <h3>Backups</h3><div id="msg"></div>
    <table>${(d.backups || []).map(b => `<tr><td>${when(b.time)}</td><td>${esc(b.reason)}</td>
      <td><button data-view="${esc(b.id)}">View</button> <button data-restore="${esc(b.id)}">Restore</button></td></tr>`).join('') || '<tr><td class="muted">No backups yet.</td></tr>'}</table>`;
  document.querySelectorAll('[data-view]').forEach(b => b.onclick = () => view(b.dataset.view));
  document.querySelectorAll('[data-restore]').forEach(b => b.onclick = () => restore(b.dataset.restore));
}

async function view(id) {
  viewing = id;
  const r = await api('/api/inventory/backup?uuid=' + encodeURIComponent(current) + '&id=' + encodeURIComponent(id));
  $('#detail').innerHTML = `<h2>Backup</h2><p class="muted">${esc(id)}</p>
    <button id="back">← Back to live view</button> <button id="rs">Restore this backup</button>${inv(r.body)}`;
  $('#back').onclick = () => { viewing = null; refresh(); };
  $('#rs').onclick = () => restore(id);
}

async function restore(id) {
  if (!confirm('Replace the inventory of this player with this backup? (The current one is backed up first.)')) return;
  const r = await api('/api/inventory/restore', {method: 'POST', body: JSON.stringify({uuid: current, id})});
  alert(r.body.ok ? 'Restored – ' + r.body.applied : 'Not restored: ' + (r.body.error || r.status));
  viewing = null;
  refresh();
}

start();

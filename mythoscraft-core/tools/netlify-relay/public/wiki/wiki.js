// MythosCraft Wiki – items as real 3D Minecraft models (three.js), collections, season calendar, tournaments and
// live leaderboards (from /api/public, pushed by the server through the panel relay).
import * as THREE from 'https://unpkg.com/three@0.160.0/build/three.module.js';

// ------------------------------------------------------------------ i18n
const T = {
  de: {
    'nav.items': 'Items', 'nav.collections': 'Kollektionen', 'nav.calendar': 'Kalender', 'nav.tournaments': 'Turniere', 'nav.leaderboards': 'Bestenlisten',
    'hero.eyebrow': 'Das offizielle Wiki', 'hero.lead': 'Jedes Item in 3D zum Drehen, alle Kollektionen, wann welche Saison startet, die nächsten Turniere und die besten Spieler – live vom Server.',
    'hero.online': 'online', 'hero.items': 'Items', 'hero.collections': 'Kollektionen', 'hero.seasons': 'Saisons',
    'items.title': 'Alle Items', 'items.sub': 'Klick auf ein Item, um es in 3D zu drehen und zu sehen, wo du es bekommst.', 'items.search': 'Suchen …',
    'coll.title': 'Kollektionen', 'coll.sub': 'Jede Kollektion hat ihre eigene Kiste – manche gibt es nur in ihrer Saison.',
    'cal.title': 'Saison-Kalender', 'cal.sub': 'Wann was reinkommt – die Saisons im Jahr und was als Nächstes startet.',
    'tour.title': 'Turniere', 'tour.sub': 'PvP-Turniere mit Pokalen – Anmeldung ingame mit /turnier.',
    'lb.title': 'Bestenlisten', 'lb.sub': 'Live vom Server – wird jede Minute aktualisiert.',
    'foot.panel': 'Team-Panel', 'foot.note': 'Nicht offiziell mit Mojang verbunden.', 'modal.hint': 'Ziehen zum Drehen · Mausrad zum Zoomen',
    all: 'Alle', kinds: { tool: 'Werkzeuge', weapon: 'Waffen', armor: 'Rüstung', consumable: 'Verbrauch', charm: 'Talismane', trophy: 'Pokale', visual: 'Deko' },
    rarity: { common: 'Gewöhnlich', uncommon: 'Ungewöhnlich', rare: 'Selten', epic: 'Episch' },
    items: 'Items', live: 'Jetzt aktiv', always: 'Immer erhältlich', in: 'in', days: 'Tagen', today: 'Heute', until: 'bis',
    where: 'Wo bekomme ich es?', crate: 'Kiste', shop: 'Shop', collection: 'Kollektion', none: 'Keine Treffer.',
    starts: 'startet', ends: 'endet', eventNow: 'Läuft gerade', nextEvent: 'Nächstes Event', events: 'Alle Events',
    offline: 'Server gerade nicht verbunden – Live-Daten folgen.', noEvent: 'Gerade kein Event',
    tiers: { daily: 'Tagesturnier', weekly: 'Wochenturnier', monthly: 'Monatsturnier' }, every: { daily: 'Jeden Tag', weekly: 'Jeden', monthly: 'Am' },
    boards: { coins: 'Coins', kills: 'Kills', playtime: 'Spielzeit', eventswon: 'Event-Siege', eventpoints: 'Event-Punkte', bossdamage: 'Boss-Schaden', mobkills: 'Mob-Kills', crates: 'Kisten', streak: 'Killstreak' },
    months: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
    weekdays: { MONDAY: 'Montag', TUESDAY: 'Dienstag', WEDNESDAY: 'Mittwoch', THURSDAY: 'Donnerstag', FRIDAY: 'Freitag', SATURDAY: 'Samstag', SUNDAY: 'Sonntag' },
    hours: 'Std.', noData: 'Noch keine Einträge.', ofMonth: '. des Monats',
  },
  en: {
    'nav.items': 'Items', 'nav.collections': 'Collections', 'nav.calendar': 'Calendar', 'nav.tournaments': 'Tournaments', 'nav.leaderboards': 'Leaderboards',
    'hero.eyebrow': 'The official wiki', 'hero.lead': 'Every item in 3D to spin around, all collections, when each season starts, the next tournaments and the best players – live from the server.',
    'hero.online': 'online', 'hero.items': 'items', 'hero.collections': 'collections', 'hero.seasons': 'seasons',
    'items.title': 'All items', 'items.sub': 'Click an item to spin it in 3D and see where to get it.', 'items.search': 'Search …',
    'coll.title': 'Collections', 'coll.sub': 'Every collection has its own crate – some only during their season.',
    'cal.title': 'Season calendar', 'cal.sub': 'What comes when – the seasons of the year and what starts next.',
    'tour.title': 'Tournaments', 'tour.sub': 'PvP tournaments with trophies – join in game with /tournament.',
    'lb.title': 'Leaderboards', 'lb.sub': 'Live from the server – updated every minute.',
    'foot.panel': 'Team panel', 'foot.note': 'Not affiliated with Mojang.', 'modal.hint': 'Drag to rotate · scroll to zoom',
    all: 'All', kinds: { tool: 'Tools', weapon: 'Weapons', armor: 'Armour', consumable: 'Consumables', charm: 'Charms', trophy: 'Trophies', visual: 'Deco' },
    rarity: { common: 'Common', uncommon: 'Uncommon', rare: 'Rare', epic: 'Epic' },
    items: 'items', live: 'Active now', always: 'Always available', in: 'in', days: 'days', today: 'Today', until: 'until',
    where: 'Where to get it?', crate: 'Crate', shop: 'Shop', collection: 'Collection', none: 'Nothing found.',
    starts: 'starts', ends: 'ends', eventNow: 'Running now', nextEvent: 'Next event', events: 'All events',
    offline: 'Server not connected right now – live data will follow.', noEvent: 'No event right now',
    tiers: { daily: 'Daily tournament', weekly: 'Weekly tournament', monthly: 'Monthly tournament' }, every: { daily: 'Every day', weekly: 'Every', monthly: 'On the' },
    boards: { coins: 'Coins', kills: 'Kills', playtime: 'Playtime', eventswon: 'Event wins', eventpoints: 'Event points', bossdamage: 'Boss damage', mobkills: 'Mob kills', crates: 'Crates', streak: 'Kill streak' },
    months: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
    weekdays: { MONDAY: 'Monday', TUESDAY: 'Tuesday', WEDNESDAY: 'Wednesday', THURSDAY: 'Thursday', FRIDAY: 'Friday', SATURDAY: 'Saturday', SUNDAY: 'Sunday' },
    hours: 'h', noData: 'No entries yet.', ofMonth: ' of the month',
  },
};
let lang = (localStorage.getItem('wiki.lang') || (navigator.language || 'de')).startsWith('de') ? 'de' : 'en';
const t = (k) => T[lang][k] ?? k;
const $ = (s, r = document) => r.querySelector(s);
const el = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };

// ------------------------------------------------------------------ Minecraft model → three.js
const texCache = new Map();
function loadTex(spec) {
  if (!texCache.has(spec.url)) {
    texCache.set(spec.url, new Promise((res) => new THREE.TextureLoader().load(spec.url, (tx) => {
      tx.magFilter = THREE.NearestFilter; tx.minFilter = THREE.NearestFilter; tx.colorSpace = THREE.SRGBColorSpace; tx.generateMipmaps = false;
      res(tx);
    }, undefined, () => res(null))));
  }
  return texCache.get(spec.url);
}
const SHADE = { up: 1.0, down: 0.55, north: 0.85, south: 0.85, east: 0.7, west: 0.7 };
function corners(f, t0, face) {
  const [x0, y0, z0] = f, [x1, y1, z1] = t0;
  return {
    north: [[x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]],
    south: [[x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]],
    east: [[x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]],
    west: [[x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0]],
    up: [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]],
    down: [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]],
  }[face];
}
function rotatePoint(p, rot) {
  if (!rot || !rot.angle) return p;
  const o = rot.origin, a = THREE.MathUtils.degToRad(rot.angle);
  let [x, y, z] = [p[0] - o[0], p[1] - o[1], p[2] - o[2]];
  const c = Math.cos(a), s = Math.sin(a);
  if (rot.axis === 'x') [y, z] = [y * c - z * s, y * s + z * c];
  else if (rot.axis === 'y') [x, z] = [x * c + z * s, -x * s + z * c];
  else [x, y] = [x * c - y * s, x * s + y * c];
  return [x + o[0], y + o[1], z + o[2]];
}
async function buildElements(model) {
  const group = new THREE.Group();
  const byTex = new Map();
  for (const e of model.elements) {
    for (const [face, fd] of Object.entries(e.faces || {})) {
      const key = (fd.texture || '#0').replace('#', '');
      const spec = model.textures[key] || model.textures['0'] || Object.values(model.textures)[0];
      if (!spec) continue;
      if (!byTex.has(spec.url)) byTex.set(spec.url, { spec, pos: [], uv: [], col: [] });
      const b = byTex.get(spec.url);
      const q = corners(e.from, e.to, face).map((p) => rotatePoint(p, e.rotation));
      const [u0, v0, u1, v1] = fd.uv || [0, 0, 16, 16];
      const fr = spec.frames || 1;
      const U = (u) => u / 16, V = (v) => 1 - v / 16 / fr;
      const uvs = [[U(u0), V(v0)], [U(u1), V(v0)], [U(u1), V(v1)], [U(u0), V(v1)]];
      const lum = e.light_emission ? 1.0 : SHADE[face];
      for (const i of [0, 3, 2, 0, 2, 1]) {
        b.pos.push(...q[i]); b.uv.push(...uvs[i]); b.col.push(lum, lum, lum);
      }
    }
  }
  for (const b of byTex.values()) {
    const tx = await loadTex(b.spec);
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(b.pos, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(b.uv, 2));
    g.setAttribute('color', new THREE.Float32BufferAttribute(b.col, 3));
    const m = new THREE.MeshBasicMaterial({ map: tx, vertexColors: true, alphaTest: 0.1, side: THREE.DoubleSide, transparent: false });
    group.add(new THREE.Mesh(g, m));
  }
  return group;
}
async function buildSprite(model) {
  // vanilla "item/generated": every opaque pixel becomes a 1/16 thick voxel slab (front/back + edge walls)
  const group = new THREE.Group();
  for (const [li, spec] of model.layers.entries()) {
    const img = await new Promise((res) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => res(null); i.src = spec.url; });
    if (!img) continue;
    const w = img.width, h = Math.floor(img.height / (spec.frames || 1));
    const c = document.createElement('canvas'); c.width = w; c.height = h;
    const ctx = c.getContext('2d'); ctx.drawImage(img, 0, 0);
    const px = ctx.getImageData(0, 0, w, h).data;
    const a = (x, y) => (x < 0 || y < 0 || x >= w || y >= h) ? 0 : px[(y * w + x) * 4 + 3];
    const pos = [], col = [];
    const s = 16 / w, z0 = 7.5 - li * 0.01, z1 = 8.5 + li * 0.01;
    const quad = (p, rgb, f) => { for (const i of [0, 1, 2, 0, 2, 3]) { pos.push(...p[i]); col.push(rgb[0] * f, rgb[1] * f, rgb[2] * f); } };
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      if (a(x, y) < 26) continue;
      const i = (y * w + x) * 4;
      const rgb = [px[i] / 255, px[i + 1] / 255, px[i + 2] / 255].map((v) => Math.pow(v, 2.2));
      const X0 = x * s, X1 = (x + 1) * s, Y1 = 16 - y * s, Y0 = 16 - (y + 1) * s;
      quad([[X0, Y0, z1], [X1, Y0, z1], [X1, Y1, z1], [X0, Y1, z1]], rgb, 0.85);
      quad([[X1, Y0, z0], [X0, Y0, z0], [X0, Y1, z0], [X1, Y1, z0]], rgb, 0.85);
      if (a(x, y - 1) < 26) quad([[X0, Y1, z1], [X1, Y1, z1], [X1, Y1, z0], [X0, Y1, z0]], rgb, 1.0);
      if (a(x, y + 1) < 26) quad([[X0, Y0, z0], [X1, Y0, z0], [X1, Y0, z1], [X0, Y0, z1]], rgb, 0.55);
      if (a(x - 1, y) < 26) quad([[X0, Y0, z0], [X0, Y0, z1], [X0, Y1, z1], [X0, Y1, z0]], rgb, 0.7);
      if (a(x + 1, y) < 26) quad([[X1, Y0, z1], [X1, Y0, z0], [X1, Y1, z0], [X1, Y1, z1]], rgb, 0.7);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
    group.add(new THREE.Mesh(g, new THREE.MeshBasicMaterial({ vertexColors: true, side: THREE.DoubleSide })));
  }
  return group;
}
const modelCache = new Map();
async function buildItem(item) {
  if (!item.model) return null;
  if (!modelCache.has(item.id)) modelCache.set(item.id, fetch(item.model).then((r) => r.json()));
  const model = await modelCache.get(item.id);
  const inner = model.type === 'elements' ? await buildElements(model) : await buildSprite(model);
  inner.position.set(-8, -8, -8);
  const pose = new THREE.Group();          // inventory pose (like the game shows it)
  pose.add(inner);
  const gui = model.display && model.display.gui;
  if (gui && gui.rotation) {
    pose.rotation.set(THREE.MathUtils.degToRad(gui.rotation[0]), THREE.MathUtils.degToRad(gui.rotation[1]), THREE.MathUtils.degToRad(gui.rotation[2]), 'XYZ');
  }
  const box = new THREE.Box3().setFromObject(pose);
  const size = box.getSize(new THREE.Vector3()).length() || 16;
  const center = box.getCenter(new THREE.Vector3());
  pose.position.sub(center);
  const root = new THREE.Group();
  root.add(pose);
  root.scale.setScalar(20 / size);
  return root;
}

// ------------------------------------------------------------------ thumbnails (one shared offscreen renderer)
const thumbR = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
thumbR.setSize(256, 256, false);
thumbR.outputColorSpace = THREE.SRGBColorSpace;
const thumbCam = new THREE.PerspectiveCamera(28, 1, 1, 200);
thumbCam.position.set(0, 0, 44);
const thumbQueue = [];
let thumbBusy = false;
function thumb(item, img) { thumbQueue.push([item, img]); pumpThumbs(); }
async function pumpThumbs() {
  if (thumbBusy) return;
  thumbBusy = true;
  while (thumbQueue.length) {
    const [item, img] = thumbQueue.shift();
    try {
      const obj = await buildItem(item);
      if (obj) {
        const scene = new THREE.Scene();
        obj.rotation.y = item.model && item.hat ? 0 : 0.35;
        scene.add(obj);
        thumbR.render(scene, thumbCam);
        img.src = thumbR.domElement.toDataURL('image/png');
        item._thumb = img.src;
      }
    } catch (e) { /* broken model – keep placeholder */ }
    await new Promise((r) => setTimeout(r, 0));
  }
  thumbBusy = false;
}

// ------------------------------------------------------------------ interactive viewers
function viewer(canvas, { auto = 0.006, zoom = true } = {}) {
  const r = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  r.outputColorSpace = THREE.SRGBColorSpace;
  const cam = new THREE.PerspectiveCamera(30, 1, 1, 400);
  cam.position.set(0, 0, 46);
  const scene = new THREE.Scene();
  const spin = new THREE.Group();
  scene.add(spin);
  let obj = null, drag = null, vx = auto, vy = 0, rx = 0.15, ry = 0, dist = 46, alive = true;
  const fit = () => {
    const w = canvas.clientWidth, h = canvas.clientHeight || w;
    if (canvas.width !== Math.floor(w * devicePixelRatio) || canvas.height !== Math.floor(h * devicePixelRatio)) {
      r.setPixelRatio(devicePixelRatio); r.setSize(w, h, false); cam.aspect = w / h; cam.updateProjectionMatrix();
    }
  };
  canvas.addEventListener('pointerdown', (e) => { drag = [e.clientX, e.clientY]; canvas.setPointerCapture(e.pointerId); });
  canvas.addEventListener('pointermove', (e) => {
    if (!drag) return;
    vx = (e.clientX - drag[0]) * 0.01; vy = (e.clientY - drag[1]) * 0.01;
    ry += vx; rx = Math.max(-1.3, Math.min(1.3, rx + vy)); drag = [e.clientX, e.clientY];
  });
  canvas.addEventListener('pointerup', () => { drag = null; });
  if (zoom) canvas.addEventListener('wheel', (e) => { e.preventDefault(); dist = Math.max(22, Math.min(90, dist + e.deltaY * 0.04)); }, { passive: false });
  const loop = () => {
    if (!alive) return;
    fit();
    if (!drag) { vx += (auto - vx) * 0.03; ry += vx; vy *= 0.9; }
    spin.rotation.set(rx, ry, 0);
    cam.position.z += (dist - cam.position.z) * 0.15;
    r.render(scene, cam);
    requestAnimationFrame(loop);
  };
  loop();
  return {
    async show(item) {
      const o = await buildItem(item);
      if (obj) spin.remove(obj);
      obj = o; if (o) { spin.add(o); o.scale.multiplyScalar(0.01); }
      const target = o ? o.scale.x * 100 : 1;
      const t0 = performance.now();
      const grow = (now) => { if (!o || obj !== o) return; const k = Math.min(1, (now - t0) / 450); o.scale.setScalar(target * (1 - Math.pow(1 - k, 3)) + 0.0001); if (k < 1) requestAnimationFrame(grow); };
      requestAnimationFrame(grow);
    },
    reset() { rx = 0.15; ry = 0; dist = 46; },
    stop() { alive = false; },
  };
}

// ------------------------------------------------------------------ data
const data = await fetch('data.json').then((r) => r.json());
const items = data.items.filter((i) => i.model);
const byId = Object.fromEntries(items.map((i) => [i.id, i]));
let live = null;

function applyI18n() {
  document.documentElement.lang = lang;
  document.querySelectorAll('[data-i18n]').forEach((e) => { e.textContent = t(e.dataset.i18n); });
  document.querySelectorAll('[data-i18n-ph]').forEach((e) => { e.placeholder = t(e.dataset.i18nPh); });
  $('#lang').textContent = lang === 'de' ? 'EN' : 'DE';
}

// counters
function countUp(elm, to) {
  const t0 = performance.now();
  const step = (now) => { const k = Math.min(1, (now - t0) / 1200); elm.textContent = Math.round(to * (1 - Math.pow(1 - k, 3))); if (k < 1) requestAnimationFrame(step); };
  requestAnimationFrame(step);
}

// ------------------------------------------------------------------ hero
const hero = viewer($('#hero3d'), { auto: 0.008, zoom: false });
const featured = ['dragon_blade', 'crystal_pickaxe', 'ice_wand', 'blossom_wand', 'inferno_axe', 'pharaoh_helmet', 'star_saber', 'trophy_monthly', 'companion_ghost', 'tide_staff']
  .map((id) => byId[id]).filter(Boolean);
let heroIdx = 0;
async function nextHero() {
  const it = featured[heroIdx++ % featured.length];
  if (!it) return;
  const cap = $('#hero-caption');
  cap.style.opacity = 0;
  await hero.show(it);
  cap.innerHTML = it.name[lang];
  cap.style.opacity = 1;
}
nextHero();
setInterval(nextHero, 4500);
$('#ip').addEventListener('click', async (e) => {
  try { await navigator.clipboard.writeText('mythoscraftsmp.minekeep.gg'); } catch { /* ignore */ }
  e.currentTarget.classList.add('copied'); setTimeout(() => $('#ip').classList.remove('copied'), 1600);
});
// hero parallax
addEventListener('scroll', () => { const y = scrollY; $('.hero-stage').style.transform = `translateY(${y * 0.18}px)`; $('.hero-text').style.transform = `translateY(${y * 0.08}px)`; }, { passive: true });

// ------------------------------------------------------------------ item grid
const state = { q: '', kind: null, rarity: null, coll: null };
function chips(host, values, labels, key) {
  host.innerHTML = '';
  const mk = (v, label) => {
    const b = el('button', 'chip' + (state[key] === v ? ' on' : ''), label);
    b.onclick = () => { state[key] = state[key] === v ? null : v; renderFilters(); renderGrid(); };
    host.append(b);
  };
  mk(null, t('all'));
  for (const v of values) mk(v, labels(v));
}
function renderFilters() {
  chips($('#kind-chips'), [...new Set(items.map((i) => i.kind))], (k) => T[lang].kinds[k] || k, 'kind');
  chips($('#rarity-chips'), ['common', 'uncommon', 'rare', 'epic'].filter((r) => items.some((i) => i.rarity === r)), (r) => T[lang].rarity[r], 'rarity');
  chips($('#coll-chips'), data.collections.map((c) => c.id), (c) => (data.collections.find((x) => x.id === c) || {}).name || c, 'coll');
}
const cardObs = new IntersectionObserver((es) => es.forEach((e) => {
  if (!e.isIntersecting) return;
  const card = e.target;
  card.classList.add('in');
  const img = card.querySelector('img');
  if (!img.dataset.done) { img.dataset.done = 1; const it = byId[card.dataset.id]; if (it._thumb) img.src = it._thumb; else thumb(it, img); }
  cardObs.unobserve(card);
}), { rootMargin: '200px' });
function renderGrid() {
  const g = $('#grid');
  g.innerHTML = '';
  const q = state.q.toLowerCase();
  const list = items.filter((i) => (!state.kind || i.kind === state.kind) && (!state.rarity || i.rarity === state.rarity)
    && (!state.coll || i.collection === state.coll) && (!q || i.id.includes(q) || i.name[lang].replace(/<[^>]+>/g, '').toLowerCase().includes(q)));
  if (!list.length) { g.append(el('p', 'empty', t('none'))); return; }
  list.forEach((it, n) => {
    const c = el('div', 'card');
    c.dataset.id = it.id;
    c.style.transitionDelay = (n % 12) * 35 + 'ms';
    c.innerHTML = `<span class="badge r-${it.rarity}">${T[lang].rarity[it.rarity] || it.rarity}</span><div class="thumb"><img alt=""></div><div class="name">${it.name[lang]}</div>`;
    c.onclick = () => openItem(it);
    g.append(c);
    cardObs.observe(c);
  });
}
$('#search').addEventListener('input', (e) => { state.q = e.target.value; renderGrid(); });

// ------------------------------------------------------------------ modal
let modalViewer = null;
function openItem(it) {
  const m = $('#modal');
  m.hidden = false;
  document.body.style.overflow = 'hidden';
  if (!modalViewer) modalViewer = viewer($('#view3d'), { auto: 0.006 });
  modalViewer.reset();
  modalViewer.show(it);
  const coll = data.collections.find((c) => c.id === it.collection);
  const where = [];
  for (const cr of it.crates) where.push(`<span class="pill">🎁 ${t('crate')}: ${data.crates[cr] || cr}</span>`);
  if (it.shop) where.push(`<span class="pill">🛒 ${t('shop')}: ${it.shop.price} ${it.shop.currency === 'coins' ? 'Coins' : 'Gems'}</span>`);
  $('#info').innerHTML = `<h3>${it.name[lang]}</h3>
    <div class="row"><span class="pill r-${it.rarity}">${T[lang].rarity[it.rarity] || it.rarity}</span><span class="pill">${T[lang].kinds[it.kind] || it.kind}</span>
    <span class="pill">${t('collection')}: ${coll ? coll.name : it.collection}</span></div>
    <div class="lore">${it.lore[lang].join('<br>')}</div>
    ${where.length ? `<h4>${t('where')}</h4><div class="row">${where.join('')}</div>` : ''}`;
}
function closeModal() { $('#modal').hidden = true; document.body.style.overflow = ''; }
$('#close').onclick = closeModal;
$('#modal').addEventListener('click', (e) => { if (e.target.id === 'modal') closeModal(); });
addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModal(); });

// ------------------------------------------------------------------ seasons helpers
const md = (s) => { const [m, d] = String(s).split('-').map(Number); return { m, d }; };
function nextRange(season, now = new Date()) {
  const a = md(season.start), b = md(season.end);
  for (const y of [now.getFullYear() - 1, now.getFullYear(), now.getFullYear() + 1]) {
    const st = new Date(y, a.m - 1, a.d), en = new Date(b.m < a.m || (b.m === a.m && b.d < a.d) ? y + 1 : y, b.m - 1, b.d, 23, 59);
    if (en >= now) return { st, en, active: st <= now };
  }
  return null;
}
const fmtDate = (d) => d.toLocaleDateString(lang === 'de' ? 'de-DE' : 'en-GB', { day: 'numeric', month: 'long' });
const daysTo = (d) => Math.ceil((d - new Date()) / 864e5);

function renderCollections() {
  const rail = $('#coll-rail');
  rail.innerHTML = '';
  for (const c of data.collections) {
    const its = items.filter((i) => i.collection === c.id);
    const card = el('div', 'coll reveal');
    let tag = `<span class="tag always">${t('always')}</span>`;
    if (c.season) {
      const r = nextRange(c.season);
      if (r && r.active) tag = `<span class="tag live">● ${t('live')} · ${t('until')} ${fmtDate(r.en)}</span>`;
      else if (r) tag = `<span class="tag soon">${t('starts')} ${fmtDate(r.st)} · ${t('in')} ${daysTo(r.st)} ${t('days')}</span>`;
    }
    card.innerHTML = `<div class="icons"></div><h3>${c.name}</h3><div class="meta">${its.length} ${t('items')}</div>${tag}`;
    const icons = card.querySelector('.icons');
    its.slice(0, 3).forEach((it) => { const img = el('img'); icons.append(img); if (it._thumb) img.src = it._thumb; else thumb(it, img); });
    card.onclick = () => { state.coll = c.id; renderFilters(); renderGrid(); $('#items').scrollIntoView({ behavior: 'smooth' }); };
    rail.append(card);
    revealObs.observe(card);
  }
}

function renderCalendar() {
  const year = $('#year');
  year.innerHTML = `<div class="months">${T[lang].months.map((m) => `<span>${m}</span>`).join('')}</div><div class="lanes"></div>`;
  const lanes = year.querySelector('.lanes');
  const pos = (m, d) => ((m - 1) + (d - 1) / 31) / 12 * 100;
  const now = new Date();
  for (const s of data.seasons) {
    if (!s.start || !s.end) continue;
    const a = md(s.start), b = md(s.end);
    const lane = el('div', 'lane');
    const r = nextRange(s);
    const segs = (b.m < a.m || (b.m === a.m && b.d < a.d)) ? [[pos(a.m, a.d), 100], [0, pos(b.m, b.d) + 2.7]] : [[pos(a.m, a.d), pos(b.m, b.d) + 2.7]];
    segs.forEach(([x0, x1], i) => {
      const bar = el('div', 'bar' + (r && r.active ? ' live' : ''), i === 0 ? s.name : '');
      bar.style.left = x0 + '%'; bar.style.width = Math.max(2, x1 - x0) + '%';
      lane.append(bar);
    });
    lanes.append(lane);
  }
  const today = el('div', 'today');
  today.dataset.label = t('today');
  today.style.left = pos(now.getMonth() + 1, now.getDate()) + '%';
  lanes.append(today);
  // upcoming / active seasons
  const up = $('#upcoming');
  up.innerHTML = '';
  data.seasons.map((s) => ({ s, r: nextRange(s) })).filter((x) => x.r).sort((x, y) => (x.r.active ? 0 : x.r.st) - (y.r.active ? 0 : y.r.st)).slice(0, 4)
    .forEach(({ s, r }) => {
      const c = el('div', 'up reveal', `<b>${s.name}</b><span>${r.active ? `● ${t('live')} · ${t('ends')} ${fmtDate(r.en)}` : `${t('starts')} ${fmtDate(r.st)} · ${t('in')} ${daysTo(r.st)} ${t('days')}`}</span>`);
      up.append(c); revealObs.observe(c);
    });
}

function renderEvent() {
  const box = $('#event-live');
  if (!live) { box.innerHTML = `<p>${t('offline')}</p>`; return; }
  const ev = live.event;
  const nextAt = live.nextEventAt;
  box.innerHTML = `<h3>${ev ? `⚡ ${t('eventNow')}: ${ev.name}` : t('noEvent')}</h3>
    ${nextAt ? `<p>${t('nextEvent')}: <b class="count" data-at="${nextAt}"></b></p>` : ''}
    <div class="evlist">${(live.events || []).map((e) => `<span>${e.name}</span>`).join('')}</div>`;
}

// ------------------------------------------------------------------ tournaments
function nextFromConfig(tier, cfg) {
  if (!cfg || !cfg.time) return null;
  const [h, mi] = cfg.time.split(':').map(Number);
  const now = new Date();
  for (let i = 0; i < 400; i++) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + i, h, mi);
    if (d <= now) continue;
    if (tier === 'weekly' && cfg.day && Object.keys(T.en.weekdays)[(d.getDay() + 6) % 7] !== cfg.day) continue;
    if (tier === 'monthly' && d.getDate() !== (cfg.date || 1)) continue;
    return d.getTime();
  }
  return null;
}
function renderTours() {
  const host = $('#tours');
  host.innerHTML = '';
  const img = { daily: 'trophy_daily', weekly: 'trophy_weekly', monthly: 'trophy_monthly' };
  for (const tier of ['daily', 'weekly', 'monthly']) {
    const cfg = data.tournaments[tier];
    const fromLive = live && (live.tournaments || []).find((x) => x.tier === tier);
    const at = (fromLive && fromLive.next) || nextFromConfig(tier, cfg);
    let when = '';
    if (cfg) {
      when = tier === 'daily' ? `${T[lang].every.daily}, ${cfg.time}` : tier === 'weekly'
        ? `${T[lang].every.weekly} ${T[lang].weekdays[cfg.day] || cfg.day}, ${cfg.time}` : `${T[lang].every.monthly} ${cfg.date || 1}${T[lang].ofMonth}, ${cfg.time}`;
    }
    const card = el('div', 'tour reveal', `<img alt=""><h3>${T[lang].tiers[tier]}</h3><div class="when">${when}</div>${at ? `<div class="count" data-at="${at}"></div>` : ''}`);
    const it = byId[img[tier]];
    if (it) { const i = card.querySelector('img'); if (it._thumb) i.src = it._thumb; else thumb(it, i); }
    host.append(card); revealObs.observe(card);
  }
}
function tickCountdowns() {
  document.querySelectorAll('.count[data-at]').forEach((c) => {
    let s = Math.max(0, Math.floor((Number(c.dataset.at) - Date.now()) / 1000));
    const d = Math.floor(s / 86400); s %= 86400;
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    c.textContent = (d ? d + 'd ' : '') + [h, m, sec].map((v) => String(v).padStart(2, '0')).join(':');
  });
}
setInterval(tickCountdowns, 1000);

// ------------------------------------------------------------------ leaderboards
let board = 'coins';
function fmtVal(stat, v) {
  if (stat === 'playtime') return Math.floor(v / 3600) + ' ' + t('hours');
  return Number(v).toLocaleString(lang === 'de' ? 'de-DE' : 'en-GB');
}
function renderBoards() {
  const tabs = $('#lb-tabs');
  tabs.innerHTML = '';
  for (const k of Object.keys(T[lang].boards)) {
    const b = el('button', 'chip' + (board === k ? ' on' : ''), T[lang].boards[k]);
    b.onclick = () => { board = k; renderBoards(); };
    tabs.append(b);
  }
  const ol = $('#board');
  ol.innerHTML = '';
  const rows = live && live.leaderboards && live.leaderboards[board];
  if (!rows || !rows.length) { ol.append(el('li', '', `<span class="who">${live ? t('noData') : t('offline')}</span>`)); return; }
  rows.forEach((r, i) => {
    const li = el('li', '', `<span class="pos">#${i + 1}</span><img alt="" src="https://mc-heads.net/avatar/${encodeURIComponent(r.uuid)}/32"><span class="who"></span><span class="val">${fmtVal(board, r.value)}</span>`);
    li.querySelector('.who').textContent = r.name;
    li.style.animationDelay = i * 60 + 'ms';
    ol.append(li);
  });
}

// ------------------------------------------------------------------ live data
async function loadLive() {
  try {
    const r = await fetch('/api/public', { cache: 'no-store' });
    live = r.ok ? await r.json() : null;
  } catch { live = null; }
  const on = $('#online');
  if (live && !live.offline) { on.classList.remove('off'); on.querySelector('b').textContent = live.online + ' / ' + live.max; }
  else { on.classList.add('off'); on.querySelector('b').textContent = '–'; }
  renderEvent(); renderTours(); renderBoards(); tickCountdowns();
}

// ------------------------------------------------------------------ scroll reveal & init
const revealObs = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) { e.target.classList.add('in'); revealObs.unobserve(e.target); } }), { threshold: 0.12 });
function renderAll() {
  applyI18n(); renderFilters(); renderGrid(); renderCollections(); renderCalendar(); renderEvent(); renderTours(); renderBoards(); tickCountdowns();
  document.querySelectorAll('.reveal').forEach((e) => revealObs.observe(e));
}
$('#lang').onclick = () => { lang = lang === 'de' ? 'en' : 'de'; localStorage.setItem('wiki.lang', lang); renderAll(); };
renderAll();
countUp($('#st-items'), items.length);
countUp($('#st-colls'), data.collections.length);
countUp($('#st-seasons'), data.seasons.length);
loadLive();
setInterval(loadLive, 30000);

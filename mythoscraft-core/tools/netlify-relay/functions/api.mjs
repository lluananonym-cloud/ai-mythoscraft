// MythosCraft Panel – relay between the website and the Minecraft server.
// The server cannot be reached from outside (no extra ports at the host), so the plugin connects OUT to this
// function every second: it uploads live data and fetches waiting jobs (buttons, console, files …).
// The browser talks to this function exactly like it would talk to the plugin directly.
import { getStore } from '@netlify/blobs';
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

export const config = { path: '/api/*' };

const COOKIE = 'mcpanel';
const PCOOKIE = 'mcplayer';
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const ROLES = ['view', 'edit', 'admin', 'owner'];

const store = () => getStore({ name: 'mythoscraft-panel', consistency: 'strong' });
const json = (body, status = 200, headers = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers } });
const rand = (n) => randomBytes(n).toString('base64url');
const sha = (s) => createHash('sha256').update(s).digest('hex');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const atLeast = (role, need) => ROLES.indexOf(role) >= ROLES.indexOf(need);

function secretOk(req) {
  const want = Netlify.env.get('PANEL_SECRET') || '';
  const got = req.headers.get('x-relay-secret') || '';
  if (want.length < 24 || got.length !== want.length) return false;
  return timingSafeEqual(Buffer.from(got), Buffer.from(want));
}

function cookie(req, name = COOKIE) {
  const raw = req.headers.get('cookie') || '';
  for (const part of raw.split(';')) {
    const [k, v] = part.trim().split('=');
    if (k === name && v) return v;
  }
  return null;
}

// ---- players: sign in with the Minecraft account, daily login rewards ------------------------------------------
const berlinDay = (d = new Date()) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Berlin' }).format(d); // YYYY-MM-DD
function msToBerlinMidnight(now = new Date()) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Berlin', hour12: false,
    hour: '2-digit', minute: '2-digit', second: '2-digit' }).formatToParts(now).map((x) => [x.type, x.value]));
  const passed = ((+parts.hour % 24) * 3600 + +parts.minute * 60 + +parts.second) * 1000;
  return 864e5 - passed;
}
function yesterday(day) {
  const d = new Date(day + 'T12:00:00Z');
  d.setUTCDate(d.getUTCDate() - 1);
  return d.toISOString().slice(0, 10);
}
async function playerSession(req, s) {
  const token = cookie(req, PCOOKIE);
  if (!token) return null;
  const sess = await s.get('psessions/' + sha(token), { type: 'json' });
  if (!sess || sess.expires < Date.now()) return null;
  return sess;
}
async function dailyState(s, uuid) {
  const d = (await s.get('daily/' + uuid, { type: 'json' })) || { last: null, streak: 0, total: 0 };
  const today = berlinDay();
  const canClaim = d.last !== today;
  // streak shown on the page: the one the next claim would give
  const next = !canClaim ? d.streak : (d.last === yesterday(today) ? d.streak + 1 : 1);
  return { ...d, today, canClaim, nextStreak: next, resetIn: msToBerlinMidnight() };
}
async function playerApi(req, s, path, post) {
  if (path === '/api/player/logout') {
    const token = cookie(req, PCOOKIE);
    if (token) await s.delete('psessions/' + sha(token));
    return json({ ok: true }, 200, { 'Set-Cookie': `${PCOOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax` });
  }
  const me = await playerSession(req, s);
  if (!me) return json({ error: 'login' }, 401);
  if (path === '/api/player/me') {
    return json({ name: me.name, uuid: me.uuid, daily: await dailyState(s, me.uuid) });
  }
  if (path === '/api/player/claim') {
    if (!post) return json({ error: 'method' }, 405);
    const st = await dailyState(s, me.uuid);
    if (!st.canClaim) return json({ error: 'already', daily: st }, 409);
    const streak = st.nextStreak;
    await s.setJSON('daily/' + me.uuid, { last: st.today, streak, total: (st.total || 0) + 1 });
    const id = Date.now().toString(36) + rand(6);
    await s.setJSON('rewards/' + id, { id, uuid: me.uuid, name: me.name, streak, at: Date.now() });
    return json({ ok: true, streak, daily: await dailyState(s, me.uuid) });
  }
  return json({ error: 'unknown' }, 404);
}

/** The current role of a session – always taken from the latest access list the server uploaded. */
async function session(req, s) {
  const token = cookie(req);
  if (!token) return null;
  const sess = await s.get('sessions/' + sha(token), { type: 'json' });
  if (!sess || sess.expires < Date.now()) return null;
  const access = (await s.get('access', { type: 'json' })) || {};
  const role = access[sess.uuid];
  if (!role) return null;
  return { ...sess, role };
}

/** Puts a job for the server into the queue and waits (max. 8 s) for its answer. */
async function job(s, type, payload, by) {
  const id = Date.now().toString(36) + rand(6);
  await s.setJSON('jobs/' + id, { id, type, payload, by, at: Date.now() });
  for (let i = 0; i < 32; i++) {
    await sleep(250);
    const res = await s.get('results/' + id, { type: 'json' });
    if (res) {
      await s.delete('results/' + id);
      return res;
    }
  }
  await s.delete('jobs/' + id);
  return { error: 'Der Server antwortet gerade nicht (offline oder Plugin nicht verbunden).' };
}

async function bridge(req, s) {
  if (!secretOk(req)) return json({ error: 'secret' }, 403);
  const body = await req.json().catch(() => ({}));
  if (body.snapshot) await s.setJSON('snapshot', { ...body.snapshot, relayAt: Date.now() });
  if (body.actions) await s.setJSON('actions', body.actions);
  if (body.access) await s.setJSON('access', body.access);
  if (body.public) await s.setJSON('public', { ...body.public, relayAt: Date.now() });
  if (body.log) await s.setJSON('log', body.log);
  for (const r of body.results || []) await s.setJSON('results/' + r.id, r);
  // daily rewards claimed on the website → handed to the server (kept until it fetches them)
  const rewards = [];
  const { blobs: rw } = await s.list({ prefix: 'rewards/' });
  for (const b of rw.slice(0, 50)) {
    const r = await s.get(b.key, { type: 'json' });
    await s.delete(b.key);
    if (r) rewards.push(r);
  }
  // logins confirmed in game (/website login <code>)
  for (const c of body.confirmed || []) {
    const ch = await s.get('codes/' + c.code, { type: 'json' });
    if (ch && ch.expires > Date.now()) await s.setJSON('codes/' + c.code, { ...ch, uuid: c.uuid, name: c.name });
  }
  const jobs = [];
  const { blobs } = await s.list({ prefix: 'jobs/' });
  for (const b of blobs.slice(0, 20)) {
    const j = await s.get(b.key, { type: 'json' });
    await s.delete(b.key);
    if (j && Date.now() - j.at < 15000) jobs.push(j);
  }
  return json({ jobs, rewards });
}

export default async (req) => {
  const s = store();
  const url = new URL(req.url);
  const path = url.pathname;
  const post = req.method === 'POST';

  if (path === '/api/bridge') return bridge(req, s);
  if (path === '/api/public') {
    // wiki website: public data (leaderboards, events, tournaments, seasons) – no login, short cache
    const pub = await s.get('public', { type: 'json' });
    if (!pub) return json({ error: 'offline' }, 503, { 'Access-Control-Allow-Origin': '*' });
    pub.offline = Date.now() - pub.relayAt > 120000;
    return new Response(JSON.stringify(pub), { status: 200, headers: { 'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'public, max-age=15', 'Access-Control-Allow-Origin': '*' } });
  }
  if (post && req.headers.get('x-panel') !== '1') return json({ error: 'csrf' }, 403);
  if (path.startsWith('/api/player/')) return playerApi(req, s, path, post);

  if (path === '/api/login/start') {
    let code = '';
    for (let i = 0; i < 6; i++) code += CODE_CHARS[randomBytes(1)[0] % CODE_CHARS.length];
    const id = rand(24);
    const kind = url.searchParams.get('kind') === 'player' ? 'player' : 'panel';
    await s.setJSON('codes/' + code, { id, kind, expires: Date.now() + 180000 });
    await s.setJSON('challenge/' + id, { code, expires: Date.now() + 180000 });
    return json({ id, code, expires: 180 });
  }
  if (path === '/api/login/poll') {
    const id = url.searchParams.get('id') || '';
    const ch = await s.get('challenge/' + id, { type: 'json' });
    if (!ch || ch.expires < Date.now()) return json({ state: 'expired' });
    const c = await s.get('codes/' + ch.code, { type: 'json' });
    if (!c || !c.uuid) return json({ state: 'waiting' });
    await s.delete('codes/' + ch.code);
    await s.delete('challenge/' + id);
    const token = rand(32);
    if (c.kind === 'player') {
      // wiki / daily rewards: every player may sign in, own cookie, 30 days
      await s.setJSON('psessions/' + sha(token), { uuid: c.uuid, name: c.name, expires: Date.now() + 30 * 864e5 });
      return json({ state: 'ok', name: c.name, uuid: c.uuid }, 200,
        { 'Set-Cookie': `${PCOOKIE}=${token}; Path=/; Max-Age=2592000; HttpOnly; Secure; SameSite=Lax` });
    }
    const access = (await s.get('access', { type: 'json' })) || {};
    if (!access[c.uuid]) return json({ state: 'denied' });
    await s.setJSON('sessions/' + sha(token), { uuid: c.uuid, name: c.name, expires: Date.now() + 7 * 864e5 });
    return json({ state: 'ok', name: c.name }, 200,
      { 'Set-Cookie': `${COOKIE}=${token}; Path=/; Max-Age=604800; HttpOnly; Secure; SameSite=Strict` });
  }
  if (path === '/api/logout') {
    const token = cookie(req);
    if (token) await s.delete('sessions/' + sha(token));
    return json({ ok: true }, 200, { 'Set-Cookie': `${COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict` });
  }

  const sess = await session(req, s);
  if (!sess) return json({ error: 'login' }, 401);
  const by = sess.name;

  switch (path) {
    case '/api/me':
      return json({ name: sess.name, uuid: sess.uuid, role: sess.role, owner: sess.role === 'owner', url: url.origin });
    case '/api/live':
      return json({ error: 'stream' }, 501); // the page then asks every 2 s
    case '/api/stats': {
      const snap = await s.get('snapshot', { type: 'json' });
      if (!snap) return json({ error: 'Der Server hat sich noch nicht verbunden.' }, 503);
      snap.offline = Date.now() - snap.relayAt > 10000;
      return json(snap);
    }
    case '/api/actions':
      return json((await s.get('actions', { type: 'json' })) || []);
    case '/api/log':
      return json({ log: (await s.get('log', { type: 'json' })) || [] });
    case '/api/player':
      return json(await job(s, 'player', { name: url.searchParams.get('name') || '' }, by));
    case '/api/action': {
      if (!post || !atLeast(sess.role, 'edit')) return json({ error: 'role' }, 403);
      const body = await req.json();
      const r = await job(s, 'action', { id: String(body.id || ''), params: body.params || {}, role: sess.role }, by);
      return json(r, r.error ? 400 : 200);
    }
    case '/api/command': {
      if (!post || !atLeast(sess.role, 'admin')) return json({ error: 'role' }, 403);
      const body = await req.json();
      const r = await job(s, 'command', { command: String(body.command || '').slice(0, 500) }, by);
      return json(r, r.error ? 400 : 200);
    }
    case '/api/config': {
      if (!atLeast(sess.role, 'admin')) return json({ error: 'role' }, 403);
      const r = post ? await job(s, 'config_save', await req.json(), by)
        : await job(s, 'config_get', { file: url.searchParams.get('file') || 'config.yml' }, by);
      return json(r, r.error ? 400 : 200);
    }
    case '/api/access': {
      if (sess.role !== 'owner') return json({ error: 'owner' }, 403);
      const r = post ? await job(s, 'access_set', await req.json(), by) : await job(s, 'access_list', {}, by);
      return json(r, r.error ? 400 : 200);
    }
    default:
      return json({ error: 'unknown' }, 404);
  }
};

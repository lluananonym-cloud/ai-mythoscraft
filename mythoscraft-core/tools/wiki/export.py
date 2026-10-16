"""Exports everything the wiki website needs into tools/netlify-relay/public/wiki/:
items (names/lore DE+EN, collection, rarity, where to get them), the 3D models + textures straight from the generated
resource pack, collections with their seasons. Live data (leaderboards, events, tournaments) comes from /api/public.

    python tools/wiki/export.py          (after: java tools/PackGenerator.java resourcepack)
"""
import html
import json
import os
import re
import shutil

import yaml

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
PACK = os.path.join(ROOT, 'resourcepack', 'assets')
OUT = os.path.join(ROOT, 'tools', 'netlify-relay', 'public', 'wiki')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'online', 'mythoscraft', 'core', 'item', 'CustomItem.java')

NAMED = {'black': '#000000', 'dark_blue': '#0000aa', 'dark_green': '#00aa00', 'dark_aqua': '#00aaaa', 'dark_red': '#aa0000',
         'dark_purple': '#aa00aa', 'gold': '#ffaa00', 'gray': '#aaaaaa', 'dark_gray': '#555555', 'blue': '#5555ff',
         'green': '#55ff55', 'aqua': '#55ffff', 'red': '#ff5555', 'light_purple': '#ff55ff', 'yellow': '#ffff55',
         'white': '#ffffff'}


def _hex(c):
    c = c.strip()
    h = NAMED.get(c, c if c.startswith('#') else '#ffffff')
    # the website is dark: very dark text colours are lifted so they stay readable
    try:
        r, g, b = (int(h[i:i + 2], 16) for i in (1, 3, 5))
    except ValueError:
        return h
    lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
    if lum < 95:
        f = (95 - lum) / 95 * 0.75
        r, g, b = (round(v + (235 - v) * f) for v in (r, g, b))
        h = '#%02x%02x%02x' % (r, g, b)
    return h


def mm_html(text):
    """Small MiniMessage → HTML converter (colours, gradients, bold/italic); unknown tags are dropped."""
    if text is None:
        return ''
    out, stack = [], []
    pos = 0
    tokens = re.split(r'(<[^<>]+>)', str(text))
    grad = None
    for tok in tokens:
        if not tok:
            continue
        if tok.startswith('<') and tok.endswith('>'):
            tag = tok[1:-1]
            low = tag.lower()
            if low.startswith('gradient:'):
                cols = [_hex(c) for c in tag.split(':')[1:]]
                grad = cols
                continue
            if low == '/gradient':
                grad = None
                continue
            if low in ('bold', 'b'):
                out.append('<b>'); stack.append('</b>'); continue
            if low in ('italic', 'i', 'em'):
                out.append('<i>'); stack.append('</i>'); continue
            if low in ('/bold', '/b', '/italic', '/i', '/em'):
                close = '</b>' if 'b' in low else '</i>'
                if close in stack:
                    stack.remove(close)
                    out.append(close)
                continue
            if low in NAMED or re.fullmatch(r'#[0-9a-fA-F]{6}', tag) or low.startswith('color:'):
                col = _hex(tag.split(':', 1)[1] if low.startswith('color:') else tag)
                out.append(f'<span style="color:{col}">'); stack.append('</span>'); continue
            if low.startswith('/'):
                if stack and stack[-1] == '</span>':
                    out.append(stack.pop())
                continue
            continue
        txt = html.escape(tok)
        if grad and len(grad) >= 2 and len(tok) > 0:
            n = len(tok)
            for i, ch in enumerate(tok):
                t = i / max(1, n - 1) * (len(grad) - 1)
                k = min(int(t), len(grad) - 2)
                f = t - k
                a, b = grad[k], grad[k + 1]
                rgb = [round(int(a[j:j + 2], 16) * (1 - f) + int(b[j:j + 2], 16) * f) for j in (1, 3, 5)]
                out.append('<span style="color:#%02x%02x%02x">%s</span>' % (*rgb, html.escape(ch)))
        else:
            out.append(txt)
    out.extend(reversed(stack))
    return ''.join(out)


def load_yaml(name):
    with open(os.path.join(RES, name), encoding='utf-8') as f:
        return yaml.safe_load(f)


def items_from_java():
    src = open(JAVA, encoding='utf-8').read()
    out = []
    for m in re.finditer(r'spec\("([a-z0-9_]+)",\s*Material\.([A-Z0-9_]+),\s*(\w+),\s*(\w+),\s*(\w+)\)([^\n]*)', src):
        iid, base, rarity, kind, coll, rest = m.groups()
        out.append({'id': iid, 'base': base.lower(), 'rarity': rarity.lower(), 'kind': kind.lower(),
                    'collection': coll.lower(), 'hat': '.hat()' in rest})
    # armour pieces: armor("set", EquipmentSlot.HEAD, COLLECTION, RARITY, …) → set_helmet …
    piece = {'HEAD': 'helmet', 'CHEST': 'chestplate', 'LEGS': 'leggings', 'FEET': 'boots'}
    for m in re.finditer(r'armor\("([a-z0-9_]+)",\s*EquipmentSlot\.([A-Z]+),\s*(\w+),\s*(\w+)', src):
        aset, slot, coll, rarity = m.groups()
        out.append({'id': f'{aset}_{piece.get(slot, "boots")}', 'base': 'netherite_' + piece.get(slot, 'boots'),
                    'rarity': rarity.lower(), 'kind': 'armor', 'collection': coll.lower(), 'hat': False})
    return out


def english_map():
    """German config texts → English (src/main/resources/lang/config_en.yml)"""
    f = os.path.join(RES, 'lang', 'config_en.yml')
    if not os.path.exists(f):
        return {}
    y = yaml.safe_load(open(f, encoding='utf-8')) or {}
    return {str(t['de']): str(t['en']) for t in y.get('texts', []) if 'de' in t and 'en' in t}


def nice(material):
    return ' '.join(w.capitalize() for w in str(material).lower().split('_'))


def reward_info(rew, en_items, crate_keys):
    """English text (HTML) + optional item id for a crate reward line"""
    parts = str(rew).split()
    kind = parts[0] if parts else ''
    num = lambda i, d=1: int(float(parts[i])) if len(parts) > i and re.fullmatch(r'[0-9.]+', parts[i]) else d
    if kind == 'coins':
        return f'<span style="color:#facc15">{num(1):,} Coins</span>', None, 'coins'
    if kind == 'gems':
        return f'<span style="color:#e879f9">{num(1)} Gems</span>', None, 'gems'
    if kind == 'xp':
        return f'<span style="color:#a3e635">{num(1):,} XP</span>', None, 'xp'
    if kind == 'tool' and len(parts) > 1:
        n = num(2)
        name = en_items.get(parts[1], parts[1])
        return (f'{n}× ' if n > 1 else '') + name, parts[1], 'item'
    if kind == 'key' and len(parts) > 1:
        n = num(2)
        return (f'{n}× ' if n > 1 else '') + crate_keys.get(parts[1], nice(parts[1]) + ' Key'), None, 'key'
    if kind == 'item' and len(parts) > 1:
        n = num(2)
        return (f'{n}× ' if n > 1 else '') + nice(parts[1]), None, 'vanilla'
    if kind == 'book' and len(parts) > 1:
        lvl = ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII'][min(7, num(2))]
        return f'<span style="color:#c4b5fd">Book: {nice(parts[1])} {lvl}</span>', None, 'book'
    if kind == 'voucher' and len(parts) > 1:
        dur = parts[2] if len(parts) > 2 else ''
        return f'<span style="color:#fbbf24">Rank voucher: {parts[1]} {dur}</span>', None, 'voucher'
    if kind == 'spawner' and len(parts) > 1:
        return f'{nice(parts[1])} Spawner', None, 'spawner'
    return html.escape(str(rew)), None, 'other'


def tex_url(ref, copied):
    """mythoscraft:item/x → tex/mythoscraft/item/x.png (copied from the pack); returns url, frames"""
    if ':' not in ref:
        ref = 'minecraft:' + ref
    ns, path = ref.split(':', 1)
    src = os.path.join(PACK, ns, 'textures', path + '.png')
    if not os.path.exists(src):
        return None, 1
    rel = f'tex/{ns}/{path}.png'
    if rel not in copied:
        dst = os.path.join(OUT, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        copied.add(rel)
    frames = 1
    if os.path.exists(src + '.mcmeta'):
        from PIL import Image
        w, h = Image.open(src).size
        frames = max(1, h // max(1, w))
    return rel, frames


def resolve_model(ref, seen=None):
    """loads a model and merges its parents (elements/textures/display)"""
    seen = seen or set()
    if ':' not in ref:
        ref = 'minecraft:' + ref
    if ref in seen:
        return {}
    seen.add(ref)
    ns, path = ref.split(':', 1)
    f = os.path.join(PACK, ns, 'models', path + '.json')
    if not os.path.exists(f):
        return {'parent_vanilla': ref}
    data = json.load(open(f, encoding='utf-8'))
    if 'parent' in data:
        base = resolve_model(data['parent'], seen)
        merged = dict(base)
        merged['textures'] = {**base.get('textures', {}), **data.get('textures', {})}
        if 'elements' in data:
            merged['elements'] = data['elements']
        merged['display'] = {**base.get('display', {}), **data.get('display', {})}
        if 'parent_vanilla' in base and 'elements' not in merged:
            merged['parent_vanilla'] = base['parent_vanilla']
        return merged
    return data


def export_model(iid, copied):
    m = resolve_model('mythoscraft:item/' + iid)
    if not m or (not m.get('elements') and not m.get('textures')):
        return None
    tex = {}
    for k, v in m.get('textures', {}).items():
        while isinstance(v, str) and v.startswith('#'):
            v = m['textures'].get(v[1:], '')
        if v:
            url, frames = tex_url(v, copied)
            if url:
                tex[k] = {'url': url, 'frames': frames}
    if m.get('elements'):
        out = {'type': 'elements', 'elements': m['elements'], 'textures': tex, 'display': m.get('display', {})}
    elif 'layer0' in tex:
        out = {'type': 'sprite', 'layers': [tex[k] for k in sorted(tex) if k.startswith('layer')]}
    else:
        return None
    rel = f'models/{iid}.json'
    os.makedirs(os.path.join(OUT, 'models'), exist_ok=True)
    with open(os.path.join(OUT, rel), 'w', encoding='utf-8') as f:
        json.dump(out, f, separators=(',', ':'))
    return rel


def main():
    shutil.rmtree(os.path.join(OUT, 'tex'), ignore_errors=True)
    shutil.rmtree(os.path.join(OUT, 'models'), ignore_errors=True)
    os.makedirs(OUT, exist_ok=True)
    de, en = load_yaml('messages.yml'), load_yaml('messages_en.yml')
    cfg, crates = load_yaml('config.yml'), load_yaml('crates.yml')
    # where items come from: crates and shop
    sources = {}
    crate_defs = crates.get('crates', crates)
    crate_names = {}
    coll_names = {}
    tr = english_map()
    for cid, c in (crate_defs or {}).items():
        if not isinstance(c, dict):
            continue
        name_en = tr.get(str(c.get('name', cid)), str(c.get('name', cid)))
        crate_names[cid] = mm_html(name_en)
        coll_names[cid] = mm_html(re.sub(r'[- ]?(Kiste|Crate)', '', name_en))
        for r in c.get('rewards', []) or []:
            rew = r.get('reward', '') if isinstance(r, dict) else str(r)
            mm = re.match(r'tool ([a-z0-9_]+)', str(rew))
            if mm:
                sources.setdefault(mm.group(1), set()).add('crate:' + cid)
    shop = {}
    for e in (cfg.get('shop', {}) or {}).get('entries', []) or []:
        mm = re.match(r'tool ([a-z0-9_]+)', str(e.get('reward', '')))
        if mm:
            shop[mm.group(1)] = {'price': e.get('price'), 'currency': e.get('currency', 'gems')}
    copied = set()
    items = []
    for it in items_from_java():
        iid = it['id']
        d = (de.get('items') or {}).get(iid, {}) or {}
        e = (en.get('items') or {}).get(iid, {}) or {}
        model = export_model(iid, copied)
        items.append({**it,
                      'name': {'de': mm_html(d.get('name', iid)), 'en': mm_html(e.get('name', d.get('name', iid)))},
                      'lore': {'de': [mm_html(x) for x in d.get('lore', []) or []],
                               'en': [mm_html(x) for x in e.get('lore', d.get('lore', [])) or []]},
                      'crates': sorted(s.split(':', 1)[1] for s in sources.get(iid, ())),
                      'shop': shop.get(iid),
                      'model': model})
    # collections: name from their crate, season dates from config
    seasons = cfg.get('seasons', {}) or {}
    season_of = {}
    for sid, s in seasons.items():
        if isinstance(s, dict) and s.get('crate'):
            season_of[s['crate']] = {'id': sid, 'name': mm_html(s.get('name', sid)), 'start': s.get('start'), 'end': s.get('end')}
    colls = []
    for cid in dict.fromkeys(i['collection'] for i in items):
        colls.append({'id': cid, 'name': coll_names.get(cid, cid.capitalize()), 'season': season_of.get(cid),
                      'count': sum(1 for i in items if i['collection'] == cid)})
    # crate simulator: every crate with its reward table (same weights as in game)
    en_items = {i['id']: i['name']['en'] for i in items}
    crate_keys = {cid: mm_html(tr.get(str(c.get('key-name', '')), str(c.get('key-name', cid)))) for cid, c in crate_defs.items()
                  if isinstance(c, dict)}
    sim = []
    for cid, c in crate_defs.items():
        if not isinstance(c, dict) or not c.get('rewards'):
            continue
        rows = []
        total = sum(float(r.get('weight', 1)) for r in c['rewards'] if isinstance(r, dict))
        for r in c['rewards']:
            if not isinstance(r, dict):
                continue
            text, item, kind = reward_info(r.get('reward', ''), en_items, crate_keys)
            w = float(r.get('weight', 1))
            rows.append({'text': text, 'item': item, 'kind': kind, 'weight': w, 'chance': round(w / total * 100, 3),
                         'rarity': str(r.get('rarity', 'COMMON')).lower()})
        sim.append({'id': cid, 'name': crate_names[cid], 'color': str(c.get('color', '#a855f7')),
                    'lore': [mm_html(tr.get(str(l), str(l))) for l in c.get('key-lore', []) or []], 'rewards': rows})
    data = {'items': items, 'collections': colls, 'crates': crate_names, 'crateSim': sim,
            'seasons': [{'id': k, 'name': mm_html(v.get('name', k)), 'start': v.get('start'), 'end': v.get('end'),
                         'crate': v.get('crate')} for k, v in seasons.items() if isinstance(v, dict)],
            'tournaments': {k: {'time': v.get('time'), 'day': v.get('day'), 'date': v.get('day-of-month')}
                            for k, v in (cfg.get('tournaments', {}) or {}).items() if isinstance(v, dict) and 'time' in v}}
    with open(os.path.join(OUT, 'data.json'), 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
    with_model = sum(1 for i in items if i['model'])
    print(f'{len(items)} items ({with_model} with 3D/sprite model), {len(colls)} collections, {len(copied)} textures')


if __name__ == '__main__':
    main()

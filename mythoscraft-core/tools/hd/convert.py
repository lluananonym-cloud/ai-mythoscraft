"""Turns the generated element models (shared 'materials' atlas) into HD models: every face gets its own painted
area with bevels and highlights. Faces using other textures (animations) keep them."""
import json
import os

from hd import Model, Box, metal, crystal, cracks, wood, fabric, grad, glow, stripes, leaves, scales, solid

MATERIALS = ["wood", "gold", "obsidian", "crystal", "netherite", "steel", "sculk", "echo",
             "leather", "lava_rock", "lava_glow", "emerald", "diamond", "bone", "brass", "amethyst",
             "pumpkin", "candy", "ice", "snow", "ghost", "bat", "silver", "straw", "leaf", "ruby", "night"]

PAINT = {
    'wood': wood('#6b4423', '#3a230f'),
    'gold': metal('#e8b02e', '#fff1a8'),
    'obsidian': crystal('#2e1065', '#0c0a1a'),
    'crystal': crystal('#d8b4fe', '#7e22ce'),
    'netherite': metal('#3f3a40', '#8a8290'),
    'steel': metal('#a1a9b5', '#f1f5f9'),
    'sculk': cracks('#0f2a2e', '#22d3ee'),
    'echo': crystal('#5eead4', '#0f766e'),
    'leather': fabric('#7c4a21', '#6b3e1c'),
    'lava_rock': cracks('#292524', '#f97316'),
    'lava_glow': glow('#f97316', '#fde047'),
    'emerald': crystal('#4ade80', '#065f46'),
    'diamond': crystal('#67e8f9', '#0e7490'),
    'bone': grad('#f5f5f4', '#d6d3d1', noise=5),
    'brass': metal('#b8860b', '#f5d77a'),
    'amethyst': crystal('#c084fc', '#581c87'),
    'pumpkin': stripes('#f97316', '#c2410c', every=3, horizontal=False, w2=1),
    'candy': stripes('#ef4444', '#ffffff', every=2, w2=2),
    'ice': crystal('#e0f2fe', '#38bdf8'),
    'snow': grad('#ffffff', '#e2e8f0', noise=4),
    'ghost': grad('#f0fdfa', '#a5f3fc', noise=5),
    'bat': fabric('#1f1b2e', '#2e2a40'),
    'silver': metal('#cbd5e1', '#ffffff'),
    'straw': stripes('#fde68a', '#d4a017', every=2, w2=1, horizontal=False),
    'leaf': leaves('#22c55e', '#166534', '#86efac'),
    'ruby': crystal('#f87171', '#991b1b'),
    'night': crystal('#1e1b4b', '#0b0a1f'),
}


def material_of(uv):
    u, v = uv[0], uv[1]
    idx = int(u // 2) + 8 * int(v // 2)
    return MATERIALS[idx] if 0 <= idx < len(MATERIALS) else 'steel'


def convert(src_path, out_root, name):
    data = json.load(open(src_path, encoding='utf-8'))
    tex = data.get('textures', {})
    if tex.get('m') != 'mythoscraft:item/materials' or 'elements' not in data:
        return False
    m = Model(name)
    keep = []  # (element index, face, face json) using other textures
    for i, e in enumerate(data['elements']):
        faces = e.get('faces', {})
        mats = {f: material_of(fd['uv']) for f, fd in faces.items() if fd.get('texture') == '#m' and 'uv' in fd}
        if not mats:
            mats_default = 'steel'
        else:
            mats_default = max(set(mats.values()), key=list(mats.values()).count)
        b = Box(e['from'], e['to'], PAINT[mats_default], faces={f: PAINT[mt] for f, mt in mats.items() if mt != mats_default},
                rot=e.get('rotation'), glow=bool(e.get('light_emission')), name=e.get('name', ''))
        b.hide = {f for f in ('north', 'south', 'east', 'west', 'up', 'down') if f not in mats}
        b.extra = {k: v for k, v in e.items() if k not in ('from', 'to', 'faces', 'rotation', 'name')}
        for f, fd in faces.items():
            if f not in mats:
                keep.append((i, f, fd))
        m.boxes.append(b)
    m.display = 'handheld'
    m.build(seed=len(name))
    j = m.json('mythoscraft:item/hd/' + name)
    # original display, light and extra element keys
    if 'display' in data:
        j['display'] = data['display']
    if 'gui_light' in data:
        j['gui_light'] = data['gui_light']
    else:
        j.pop('gui_light', None)
    for k, v in tex.items():
        if k not in ('m', 'particle'):
            j['textures'][k] = v
    for i, b in enumerate(m.boxes):
        j['elements'][i].update(b.extra)
    for i, f, fd in keep:
        j['elements'][i]['faces'][f] = fd
    tex_dir = os.path.join(out_root, 'assets', 'mythoscraft', 'textures', 'item', 'hd')
    mod_dir = os.path.join(out_root, 'assets', 'mythoscraft', 'models', 'item')
    os.makedirs(tex_dir, exist_ok=True)
    os.makedirs(mod_dir, exist_ok=True)
    img = m.tex
    # palette PNG keeps the pack small
    q = img.convert('RGBA').quantize(colors=256, method=2, dither=0).convert('RGBA')
    alpha = img.getchannel('A')
    q.putalpha(alpha)
    q.save(os.path.join(tex_dir, name + '.png'), optimize=True)
    with open(os.path.join(mod_dir, name + '.json'), 'w', encoding='utf-8') as fh:
        json.dump(j, fh, separators=(',', ':'))
    return m

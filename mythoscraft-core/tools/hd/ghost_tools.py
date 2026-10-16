"""Shadow-ghost collection: the player's own 128×128 drawings (data/ghost/*.png) as real 3D tools.

The picture stays 1:1 – it is the texture of a solid plate (front and back), the outline gets side walls coloured with
the edge pixels (like vanilla's extruded items, just thicker), and the little ghost on every tool is a cube that sticks
out on both sides with the ghost of the drawing on its faces. The drawings point the handle to the bottom right, vanilla
tools to the bottom left – so they are mirrored once and then held exactly like vanilla tools (handheld display)."""
import json
import math
import os

from PIL import Image, ImageDraw

import sprite3d

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'data', 'ghost')
TOOLS = ['sword', 'pickaxe', 'axe', 'shovel', 'hoe']

THICK = 1.6          # plate thickness (vanilla extruded items: 1)
GHOST_THICK = 3.4    # the ghost cube sticks out on both sides
SMALL = 6            # specks smaller than this (pixels) get no side walls – they stay flat sparkles


def _load(tool):
    img = Image.open(os.path.join(SRC, f'ghost_{tool}.png')).convert('RGBA')
    img = img.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    # alpha in clean steps (the drawing uses 0/64/127/191/255) – very faint pixels go
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            if px[x, y][3] < 40:
                px[x, y] = (0, 0, 0, 0)
    return img


def _components(solid):
    comp = {}
    for p in solid:
        if p in comp:
            continue
        stack, members = [p], []
        comp[p] = -1
        while stack:
            q = stack.pop()
            members.append(q)
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                r = (q[0] + d[0], q[1] + d[1])
                if r in solid and r not in comp:
                    comp[r] = -1
                    stack.append(r)
        for q in members:
            comp[q] = len(members)
    return comp


def _ghost_box(img):
    """bounding box (pixels) of the ghost incl. its white ring"""
    px = img.load()
    pts = {(x, y) for y in range(img.height) for x in range(img.width)
           if px[x, y][3] > 200 and px[x, y][2] - px[x, y][0] >= 8 and max(px[x, y][:3]) < 90}
    best, seen = [], set()
    for p in pts:
        if p in seen:
            continue
        stack, members = [p], []
        seen.add(p)
        while stack:
            q = stack.pop()
            members.append(q)
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    r = (q[0] + dx, q[1] + dy)
                    if r in pts and r not in seen:
                        seen.add(r)
                        stack.append(r)
        if len(members) > len(best):
            best = members
    xs = [p[0] for p in best]
    ys = [p[1] for p in best]
    return min(xs) - 3, min(ys) - 3, max(xs) + 4, max(ys) + 4


def model(tool):
    img = _load(tool)
    w = img.width
    s = 16 / w
    px = img.load()
    solid = {(x, y) for y in range(w) for x in range(w) if px[x, y][3] > 0}
    size = _components(solid)
    walled = {p for p in solid if size[p] >= SMALL}
    z0, z1 = 8 - THICK / 2, 8 + THICK / 2
    T = '#0'
    els = [{'from': [0, 0, z0], 'to': [16, 16, z1], 'faces': {
        'south': {'uv': [0, 0, 16, 16], 'texture': T},
        'north': {'uv': [16, 0, 0, 16], 'texture': T}}}]

    def r(v):
        return round(v, 4)

    # horizontal edges: runs of pixels with nothing above / below
    for y in range(w):
        for side, dy in (('up', -1), ('down', 1)):
            x = 0
            while x < w:
                if (x, y) in walled and (x, y + dy) not in solid:
                    x0 = x
                    while x < w and (x, y) in walled and (x, y + dy) not in solid:
                        x += 1
                    yy = 16 - (y if side == 'up' else y + 1) * s
                    els.append({'from': [r(x0 * s), r(yy), z0], 'to': [r(x * s), r(yy), z1],
                                'faces': {side: {'uv': [r(x0 * s), r(y * s), r(x * s), r((y + 1) * s)], 'texture': T}}})
                else:
                    x += 1
    # vertical edges
    for x in range(w):
        for side, dx in (('west', -1), ('east', 1)):
            y = 0
            while y < w:
                if (x, y) in walled and (x + dx, y) not in solid:
                    y0 = y
                    while y < w and (x, y) in walled and (x + dx, y) not in solid:
                        y += 1
                    xx = (x if side == 'west' else x + 1) * s
                    els.append({'from': [r(xx), r(16 - y * s), z0], 'to': [r(xx), r(16 - y0 * s), z1],
                                'faces': {side: {'uv': [r(x * s), r(y0 * s), r((x + 1) * s), r(y * s)], 'texture': T}}})
                else:
                    y += 1
    # the ghost: a cube sticking out on both sides
    gx0, gy0, gx1, gy1 = _ghost_box(img)
    g0, g1 = 8 - GHOST_THICK / 2, 8 + GHOST_THICK / 2
    cx, cy = (gx0 + gx1) // 2, (gy0 + gy1) // 2
    dark = [r(cx * s), r(cy * s), r((cx + 1) * s), r((cy + 1) * s)]            # a ghost-body pixel for the sides
    ring = [r(gx0 * s), r((gy0 + 1) * s), r((gx0 + 1) * s), r((gy0 + 2) * s)]
    face = [r(gx0 * s), r(gy0 * s), r(gx1 * s), r(gy1 * s)]
    els.append({'from': [r(gx0 * s), r(16 - gy1 * s), g0], 'to': [r(gx1 * s), r(16 - gy0 * s), g1], 'faces': {
        'south': {'uv': face, 'texture': T},
        'north': {'uv': [face[2], face[1], face[0], face[3]], 'texture': T},
        'up': {'uv': dark, 'texture': T}, 'down': {'uv': dark, 'texture': T},
        'east': {'uv': dark, 'texture': T}, 'west': {'uv': dark, 'texture': T}}})
    # white glow rim around the ghost cube (thin frame just outside it)
    m = 0.18
    for frm, to in (([gx0 * s - m, 16 - gy1 * s - m, g0 + 0.3], [gx1 * s + m, 16 - gy1 * s, g1 - 0.3]),
                    ([gx0 * s - m, 16 - gy0 * s, g0 + 0.3], [gx1 * s + m, 16 - gy0 * s + m, g1 - 0.3]),
                    ([gx0 * s - m, 16 - gy1 * s, g0 + 0.3], [gx0 * s, 16 - gy0 * s, g1 - 0.3]),
                    ([gx1 * s, 16 - gy1 * s, g0 + 0.3], [gx1 * s + m, 16 - gy0 * s, g1 - 0.3])):
        els.append({'from': [r(v) for v in frm], 'to': [r(v) for v in to], 'light_emission': 15,
                    'faces': {f: {'uv': ring, 'texture': T} for f in ('north', 'south', 'east', 'west', 'up', 'down')}})
    for e in els:
        assert all(-16 <= v <= 32 for v in e['from'] + e['to']), e
    name = f'ghost_{tool}'
    j = {'textures': {'0': f'mythoscraft:item/hd/{name}', 'particle': f'mythoscraft:item/hd/{name}'},
         'elements': els, 'display': dict(sprite3d.DISPLAY['minecraft:item/handheld'])}
    j['display']['gui'] = {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}
    return name, img, j


def render(img, j, path, size=300, yaw=-28, pitch=18):
    """small textured preview (painter's algorithm per texel)"""
    tex = img.load()
    tw = img.width
    ya, pa = math.radians(yaw), math.radians(pitch)

    def proj(v):
        x, y, z = v[0] - 8, v[1] - 8, v[2] - 8
        x, z = x * math.cos(ya) - z * math.sin(ya), x * math.sin(ya) + z * math.cos(ya)
        y, z = y * math.cos(pa) - z * math.sin(pa), y * math.sin(pa) + z * math.cos(pa)
        k = size / 20
        return (size / 2 + x * k, size / 2 - y * k, z)

    shade = {'south': 1, 'north': 0.75, 'up': 0.95, 'down': 0.55, 'east': 0.8, 'west': 0.7}
    quads = []
    for e in j['elements']:
        (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
        corners = {
            'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
            'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        }
        for f, spec in e['faces'].items():
            u0, v0, u1, v1 = [c * tw / 16 for c in spec['uv']]
            nu = max(1, round(abs(u1 - u0)))
            nv = max(1, round(abs(v1 - v0)))
            a, b, c, d = corners[f]
            for i in range(nu):
                for k in range(nv):
                    tu = int(min(tw - 1, min(u0, u1) + (i if u1 >= u0 else nu - 1 - i)))
                    tv = int(min(tw - 1, min(v0, v1) + k))
                    col = tex[tu, tv]
                    if col[3] == 0:
                        continue

                    def lerp(p, q, t):
                        return tuple(p[n] + (q[n] - p[n]) * t for n in range(3))

                    def pt(fu, fv):
                        top = lerp(a, b, fu)
                        bot = lerp(d, c, fu)
                        return proj(lerp(top, bot, fv))
                    poly = [pt(i / nu, k / nv), pt((i + 1) / nu, k / nv), pt((i + 1) / nu, (k + 1) / nv), pt(i / nu, (k + 1) / nv)]
                    depth = sum(p[2] for p in poly) / 4
                    l = shade[f]
                    quads.append((depth, [(p[0], p[1]) for p in poly], (int(col[0] * l), int(col[1] * l), int(col[2] * l), col[3])))
    quads.sort(key=lambda q: q[0])
    out = Image.new('RGBA', (size, size), (60, 110, 70, 255))
    layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    dr = ImageDraw.Draw(layer)
    for _, poly, col in quads:
        dr.polygon(poly, fill=col)
    out.alpha_composite(layer)
    out.save(path)


def build(out, prev=None, render_fn=None):
    done = set()
    tex_dir = os.path.join(out, 'assets', 'mythoscraft', 'textures', 'item', 'hd')
    mod_dir = os.path.join(out, 'assets', 'mythoscraft', 'models', 'item')
    os.makedirs(tex_dir, exist_ok=True)
    os.makedirs(mod_dir, exist_ok=True)
    for tool in TOOLS:
        name, img, j = model(tool)
        img.save(os.path.join(tex_dir, name + '.png'))
        with open(os.path.join(mod_dir, name + '.json'), 'w', encoding='utf-8') as f:
            json.dump(j, f, separators=(',', ':'))
        done.add(name)
        if prev:
            render(img, j, os.path.join(prev, 'item_' + name + '.png'))
    return done


if __name__ == '__main__':
    o = os.path.join(HERE, 'out')
    p = os.path.join(HERE, 'preview')
    os.makedirs(p, exist_ok=True)
    names = build(o, p)
    for n in sorted(names):
        print(n, len(json.load(open(os.path.join(o, 'assets/mythoscraft/models/item', n + '.json')))['elements']), 'elements')

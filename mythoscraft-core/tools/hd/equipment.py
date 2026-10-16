"""HD armour layers (chestplate, leggings, boots) at 128x64 – twice the vanilla resolution."""
import math
import os
import hd
import random

from PIL import Image, ImageDraw, ImageFilter

from hd import metal, scales, fabric, crystal, cracks, stripes, grad, wood, leaves, fur, rgb, shade, mix

S = 2  # 64x32 layout scaled by 2

# region rectangles in the 64x32 skin layout: (x, y, w, h)
BODY = {'front': (20, 20, 8, 12), 'back': (32, 20, 8, 12), 'right': (16, 20, 4, 12), 'left': (28, 20, 4, 12),
        'top': (20, 16, 8, 4), 'bottom': (28, 16, 8, 4)}
ARM = {'front': (44, 20, 4, 12), 'back': (52, 20, 4, 12), 'right': (40, 20, 4, 12), 'left': (48, 20, 4, 12),
       'top': (44, 16, 4, 4), 'bottom': (48, 16, 4, 4)}
LEG = {'front': (4, 20, 4, 12), 'back': (12, 20, 4, 12), 'right': (0, 20, 4, 12), 'left': (8, 20, 4, 12),
       'top': (4, 16, 4, 4), 'bottom': (8, 16, 4, 4)}

# set: (material painter, trim colour, accent colour, glow colour or None, emblem)
SETS = {
    'angel': (metal('#f1f5f9', '#ffffff'), '#d4a017', '#38bdf8', None, 'wings'),
    'astronaut': (metal('#e2e8f0', '#ffffff', brushed=False), '#f97316', '#2563eb', '#22d3ee', 'panel'),
    'crystal': (crystal('#c084fc', '#581c87'), '#4c1d95', '#f0abfc', '#e879f9', 'gem'),
    'dragon': (scales('#7f1d1d', '#2a0a0a'), '#e7e5e4', '#f97316', '#fb923c', 'eye'),
    'druid': (leaves('#16a34a', '#14532d', '#86efac'), '#7c4a21', '#a3e635', '#4ade80', 'leaf'),
    'frost': (metal('#7dd3fc', '#f0f9ff'), '#0369a1', '#e0f2fe', '#bae6fd', 'flake'),
    'inferno': (cracks('#292524', '#f97316'), '#7c2d12', '#fbbf24', '#f97316', 'flame'),
    'knight': (metal('#9ca3af', '#f3f4f6'), '#d4a017', '#b91c1c', None, 'cross'),
    'miner': (fabric('#1e3a8a', '#1e40af'), '#78350f', '#facc15', '#fef08a', 'stripes'),
    'mythos': (grad('#7c3aed', '#3b0764', noise=5), '#e8b02e', '#f472b6', '#c084fc', 'gem'),
    'ninja': (fabric('#18181b', '#27272a'), '#b91c1c', '#71717a', None, 'sash'),
    'ocean': (scales('#14b8a6', '#134e4a'), '#e8b02e', '#fde68a', '#5eead4', 'shell'),
    'pharaoh': (stripes('#e8b02e', '#1e40af', every=5, w2=3), '#e8b02e', '#dc2626', '#38bdf8', 'collar'),
    'pumpkin': (stripes('#ea580c', '#c2410c', every=3, horizontal=False, w2=1), '#365314', '#fde047', '#fbbf24', 'face'),
    'sculk': (cracks('#0f2a2e', '#22d3ee'), '#052e2b', '#67e8f9', '#22d3ee', 'eye'),
    'sky': (grad('#f8fafc', '#bae6fd', noise=4), '#38bdf8', '#ffffff', '#7dd3fc', 'wings'),
    'storm': (metal('#1e3a8a', '#60a5fa'), '#facc15', '#fde047', '#fde047', 'bolt'),
    'viking': (fabric('#78350f', '#92400e'), '#a8a29e', '#d6a76c', None, 'plates'),
}


class _Box:
    pass


def paint_region(img, rect, painter, face, rnd):
    x, y, w, h = (v * S for v in rect)
    tile = Image.new('RGBA', (w, h))
    painter(tile, face, _Box(), rnd)
    img.paste(tile, (x, y))
    return x, y, w, h


def trim(d, box, color, top=True, bottom=True, sides=False, width=2):
    x, y, w, h = box
    c = rgb(color)
    if top:
        d.rectangle([x, y, x + w - 1, y + width - 1], fill=c)
        d.line([x, y, x + w - 1, y], fill=shade(c, 1.3))
    if bottom:
        d.rectangle([x, y + h - width, x + w - 1, y + h - 1], fill=shade(c, 0.85))
    if sides:
        d.rectangle([x, y, x + width - 1, y + h - 1], fill=c)
        d.rectangle([x + w - width, y, x + w - 1, y + h - 1], fill=shade(c, 0.85))


def rivets(d, box, color, every=6):
    x, y, w, h = box
    c = rgb(color)
    for yy in range(y + 3, y + h - 2, every):
        for xx in (x + 2, x + w - 3):
            d.point((xx, yy), fill=shade(c, 1.4))
            d.point((xx + 1, yy + 1), fill=shade(c, 0.6))


def emblem(d, box, kind, accent, glow_c):
    x, y, w, h = box
    cx, cy = x + w // 2, y + h // 3
    a = rgb(accent)
    g = rgb(glow_c) if glow_c else a
    if kind in ('gem', 'eye'):
        d.polygon([(cx, cy - 4), (cx + 4, cy), (cx, cy + 4), (cx - 4, cy)], fill=g)
        d.polygon([(cx, cy - 4), (cx + 2, cy), (cx, cy + 1), (cx - 2, cy)], fill=shade(g, 1.35))
        if kind == 'eye':
            d.rectangle([cx - 1, cy - 2, cx, cy + 2], fill=(15, 15, 15))
    elif kind == 'wings':
        for s in (-1, 1):
            d.polygon([(cx, cy), (cx + s * 7, cy - 4), (cx + s * 6, cy + 1), (cx + s * 4, cy + 3)], fill=a)
        d.rectangle([cx - 1, cy - 2, cx, cy + 3], fill=shade(a, 0.8))
    elif kind == 'cross':
        d.rectangle([cx - 1, cy - 5, cx, cy + 6], fill=a)
        d.rectangle([cx - 4, cy - 2, cx + 3, cy - 1], fill=a)
    elif kind == 'bolt':
        d.polygon([(cx + 1, cy - 6), (cx - 3, cy + 1), (cx, cy + 1), (cx - 2, cy + 7), (cx + 3, cy - 1), (cx, cy - 1)], fill=g)
    elif kind == 'flame':
        d.polygon([(cx, cy - 6), (cx + 4, cy + 2), (cx, cy + 5), (cx - 4, cy + 2)], fill=g)
        d.polygon([(cx, cy - 2), (cx + 2, cy + 2), (cx, cy + 4), (cx - 2, cy + 2)], fill=(254, 240, 138))
    elif kind == 'flake':
        for dx, dy in ((0, 5), (5, 0), (4, 4), (4, -4)):
            d.line([cx - dx, cy - dy, cx + dx, cy + dy], fill=g, width=1)
    elif kind == 'leaf':
        d.ellipse([cx - 3, cy - 5, cx + 3, cy + 5], fill=g)
        d.line([cx, cy - 5, cx, cy + 5], fill=shade(g, 0.6))
    elif kind == 'shell':
        for i in range(5):
            d.line([cx, cy + 4, cx - 4 + i * 2, cy - 3], fill=a)
        d.arc([cx - 5, cy - 4, cx + 5, cy + 4], 180, 360, fill=a)
    elif kind == 'face':
        d.polygon([(cx - 4, cy - 1), (cx - 2, cy - 4), (cx - 1, cy - 1)], fill=g)
        d.polygon([(cx + 1, cy - 1), (cx + 2, cy - 4), (cx + 4, cy - 1)], fill=g)
        d.polygon([(cx - 4, cy + 2), (cx, cy + 5), (cx + 4, cy + 2)], fill=g)
    elif kind == 'panel':
        d.rectangle([cx - 5, cy - 3, cx + 4, cy + 4], fill=(30, 41, 59))
        for i, c in enumerate(('#ef4444', '#22c55e', '#facc15')):
            d.rectangle([cx - 4 + i * 3, cy - 2, cx - 3 + i * 3, cy - 1], fill=rgb(c))
        d.rectangle([cx - 4, cy + 1, cx + 3, cy + 2], fill=g)
    elif kind == 'collar':
        for r, c in ((7, '#e8b02e'), (5, '#1e40af'), (3, '#dc2626')):
            d.arc([cx - r - 3, y - r, cx + r + 3, y + r + 2], 0, 180, fill=rgb(c), width=2)
    elif kind == 'stripes':
        for yy in (y + h // 2 - 3, y + h // 2 + 2):
            d.rectangle([x, yy, x + w - 1, yy + 1], fill=rgb('#facc15'))
            d.rectangle([x, yy + 2, x + w - 1, yy + 2], fill=rgb('#e5e7eb'))
    elif kind == 'sash':
        for i in range(h):
            xx = x + int(i * w / h)
            d.rectangle([xx - 1, y + i, xx + 1, y + i], fill=a if i % 6 else shade(a, 0.7))
    elif kind == 'plates':
        for yy in range(y + 4, y + h - 2, 6):
            d.rectangle([x + 2, yy, x + w - 3, yy + 3], fill=rgb('#a8a29e'))
            d.line([x + 2, yy, x + w - 3, yy], fill=rgb('#e7e5e4'))


def build_set(name, spec, seed):
    painter, trim_c, accent, glow_c, kind = spec
    rnd = random.Random(seed)
    chest = Image.new('RGBA', (64 * S, 32 * S), (0, 0, 0, 0))
    legs = Image.new('RGBA', (64 * S, 32 * S), (0, 0, 0, 0))
    dc = ImageDraw.Draw(chest)
    dl = ImageDraw.Draw(legs)
    # ---- chestplate: body + arms (humanoid layer)
    for face, rect in BODY.items():
        box = paint_region(chest, rect, painter, face, rnd)
        if face in ('front', 'back', 'left', 'right'):
            trim(dc, box, trim_c, top=True, bottom=True)
            rivets(dc, box, trim_c, every=7)
        if face == 'front':
            emblem(dc, box, kind, accent, glow_c)
            # pectoral plate lines
            x, y, w, h = box
            dc.line([x + 2, y + h // 2 + 2, x + w - 3, y + h // 2 + 2], fill=shade(rgb(trim_c), 0.7))
    for face, rect in ARM.items():
        box = paint_region(chest, rect, painter, face, rnd)
        x, y, w, h = box
        if face not in ('top', 'bottom'):
            # shoulder pauldron + cuff
            dc.rectangle([x, y, x + w - 1, y + 5], fill=rgb(trim_c))
            dc.line([x, y, x + w - 1, y], fill=shade(rgb(trim_c), 1.35))
            dc.rectangle([x, y + h - 4, x + w - 1, y + h - 1], fill=shade(rgb(trim_c), 0.85))
            if glow_c and face == 'front':
                dc.rectangle([x + w // 2 - 1, y + 8, x + w // 2, y + 14], fill=rgb(glow_c))
        else:
            dc.rectangle([x, y, x + w - 1, y + h - 1], fill=rgb(trim_c))
    # ---- boots: lower part of the legs on the humanoid layer
    for face, rect in LEG.items():
        x, y, w, h = (v * S for v in rect)
        if face in ('top',):
            continue
        tile = Image.new('RGBA', (w, h))
        painter(tile, face, _Box(), rnd)
        if face != 'bottom':
            tile = tile.crop((0, h // 2, w, h))
            chest.paste(tile, (x, y + h // 2))
            dc.rectangle([x, y + h // 2, x + w - 1, y + h // 2 + 2], fill=rgb(trim_c))
            dc.rectangle([x, y + h - 2, x + w - 1, y + h - 1], fill=shade(rgb(trim_c), 0.7))
        else:
            chest.paste(tile, (x, y))
            dc.rectangle([x, y, x + w - 1, y + h - 1], fill=shade(rgb(trim_c), 0.6))
    # ---- leggings layer: belt on the body + legs with knee guards
    for face, rect in BODY.items():
        if face in ('top', 'bottom'):
            continue
        x, y, w, h = (v * S for v in rect)
        tile = Image.new('RGBA', (w, h))
        painter(tile, face, _Box(), rnd)
        legs.paste(tile.crop((0, h - 8, w, h)), (x, y + h - 8))
        dl.rectangle([x, y + h - 8, x + w - 1, y + h - 5], fill=rgb(trim_c))
        dl.line([x, y + h - 8, x + w - 1, y + h - 8], fill=shade(rgb(trim_c), 1.3))
        if face == 'front':
            dl.rectangle([x + w // 2 - 3, y + h - 8, x + w // 2 + 2, y + h - 4], fill=rgb(accent))
    for face, rect in LEG.items():
        box = paint_region(legs, rect, painter, face, rnd)
        x, y, w, h = box
        if face not in ('top', 'bottom'):
            dl.rectangle([x, y + 8, x + w - 1, y + 13], fill=rgb(trim_c))
            dl.line([x, y + 8, x + w - 1, y + 8], fill=shade(rgb(trim_c), 1.35))
            if glow_c and face == 'front':
                dl.rectangle([x + w // 2 - 1, y + 10, x + w // 2, y + 11], fill=rgb(glow_c))
    return chest, legs


def mannequin(chest, legs):
    """Front view: body, arms and legs from both layers (for the preview sheet)."""
    k = 6
    out = Image.new('RGBA', (16 * S * k, 32 * S * k), (26, 22, 38, 255))

    def put(img, rect, dx, dy):
        x, y, w, h = (v * S for v in rect)
        part = img.crop((x, y, x + w, y + h)).resize((w * k, h * k), Image.NEAREST)
        out.alpha_composite(part, (dx * k, dy * k))
    put(chest, BODY['front'], 4 * S, 8 * S)
    put(chest, ARM['front'], 0, 8 * S)
    put(chest, ARM['front'], 12 * S, 8 * S)
    put(legs, BODY['front'], 4 * S, 8 * S)
    put(legs, LEG['front'], 4 * S, 20 * S)
    put(legs, LEG['front'], 8 * S, 20 * S)
    put(chest, LEG['front'], 4 * S, 20 * S)
    put(chest, LEG['front'], 8 * S, 20 * S)
    return out


# cube faces of the 64x32 humanoid layout: (x, y, w, h, face)
_FACES = []
for ox, oy, w, d, h in ((0, 0, 8, 8, 8), (32, 0, 8, 8, 8),          # head, hat layer
                        (16, 16, 8, 4, 12), (40, 16, 4, 4, 12), (0, 16, 4, 4, 12)):  # body, arm, leg
    _FACES += [(ox + d, oy, w, d, 'up'), (ox + d + w, oy, w, d, 'down'),
               (ox, oy + d, d, h, 'east'), (ox + d, oy + d, w, h, 'north'),
               (ox + d + w, oy + d, d, h, 'west'), (ox + 2 * d + w, oy + d, w, h, 'south')]


def hand_faces(img):
    """hand-painted shading per cube face of the armour layer (same principle as the 3D models)"""
    for x, y, w, h, face in _FACES:
        box = (x * S, y * S, (x + w) * S, (y + h) * S)
        tile = img.crop(box)
        if tile.getbbox() is None:
            continue
        img.paste(hd.hand_shade(tile, face), box)
    return img


def build(out_root, prev_dir):
    base = os.path.join(out_root, 'assets', 'mythoscraft', 'textures', 'entity', 'equipment')
    os.makedirs(os.path.join(base, 'humanoid'), exist_ok=True)
    os.makedirs(os.path.join(base, 'humanoid_leggings'), exist_ok=True)
    for i, (name, spec) in enumerate(SETS.items()):
        chest, legs = build_set(name, spec, 100 + i)
        chest, legs = (hand_faces(hd.clean(img.filter(ImageFilter.ModeFilter(3)), colors=14)) for img in (chest, legs))
        chest.save(os.path.join(base, 'humanoid', name + '.png'))
        legs.save(os.path.join(base, 'humanoid_leggings', name + '.png'))
        mannequin(chest, legs).save(os.path.join(prev_dir, 'armor_' + name + '.png'))


# ====================================================================== clean plate armour (premium-pack style)
SC = 4  # 64x32 layout scaled by 4 (256x128): 1px rims stay fine lines


def _median_colour(painter):
    import random as _r
    t = Image.new('RGBA', (16, 16))
    painter(t, 'north', _Box(), _r.Random(1))
    px = sorted((p[:3] for p in t.getdata() if p[3]), key=lambda c: sum(c))
    return px[len(px) // 2]


def _plate(d, box, col, rim=True, light=True):
    """flat plate: base colour, faint rim, light band under the top edge"""
    x0, y0, x1, y1 = box
    if x1 <= x0 or y1 <= y0:
        return
    c = tuple(col)
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=c)
    if light and y1 - y0 > 6:
        d.rectangle([x0 + 1, y0 + 2, x1 - 2, y0 + 3], fill=mix(c, (255, 248, 225), 0.18))
    if rim:
        d.rectangle([x0, y0, x1 - 1, y1 - 1], outline=mix(c, (16, 10, 36), 0.28))


def _rect(rect, part=(0, 0, 1, 1)):
    """skin rect (x, y, w, h) → pixel box; part = fractions (fx0, fy0, fx1, fy1) of the face"""
    x, y, w, h = rect
    fx0, fy0, fx1, fy1 = part
    return (round((x + w * fx0) * SC), round((y + h * fy0) * SC), round((x + w * fx1) * SC), round((y + h * fy1) * SC))


def _emblem_clean(img, rect, kind, accent, glow_c):
    """reuse the emblem shapes (drawn for scale 2) on a transparent tile, scaled up crisp"""
    x, y, w, h = rect
    tile = Image.new('RGBA', (w * 2, h * 2), (0, 0, 0, 0))
    emblem(ImageDraw.Draw(tile), (0, 0, w * 2, h * 2), kind, accent, glow_c)
    tile = tile.resize((w * SC, h * SC), Image.NEAREST)
    img.alpha_composite(tile, (x * SC, y * SC))


def build_set_clean(name, spec):
    painter, trim_c, accent, glow_c, kind = spec
    base = _median_colour(painter)
    trim = rgb(trim_c)
    acc = rgb(accent)
    dark = mix(base, (16, 10, 36), 0.25)
    chest = Image.new('RGBA', (64 * SC, 32 * SC), (0, 0, 0, 0))
    legs = Image.new('RGBA', (64 * SC, 32 * SC), (0, 0, 0, 0))
    dc, dl = ImageDraw.Draw(chest), ImageDraw.Draw(legs)
    # ---- chestplate: body
    for face, rect in BODY.items():
        if face in ('top', 'bottom'):
            _plate(dc, _rect(rect), trim, light=False)
            continue
        _plate(dc, _rect(rect, (0, 0, 1, 0.12)), trim)                          # collar
        _plate(dc, _rect(rect, (0, 0.12, 1, 0.58)), base)                       # chest plate
        _plate(dc, _rect(rect, (0, 0.58, 1, 0.79)), dark)                       # abdomen plates
        _plate(dc, _rect(rect, (0, 0.79, 1, 1.0)), dark)
        if face in ('front', 'back'):
            _plate(dc, _rect(rect, (0.42, 0.58, 0.58, 1.0)), base, light=False)  # centre seam plate
    _emblem_clean(chest, BODY['front'], kind, accent, glow_c)
    # ---- arms: pauldron, sleeve plate, cuff
    for face, rect in ARM.items():
        if face == 'top':
            _plate(dc, _rect(rect), trim, light=False)
            continue
        if face == 'bottom':
            _plate(dc, _rect(rect), dark, light=False)
            continue
        _plate(dc, _rect(rect, (0, 0, 1, 0.36)), trim)                          # pauldron
        _plate(dc, _rect(rect, (0, 0.36, 1, 0.82)), base)
        _plate(dc, _rect(rect, (0, 0.82, 1, 1.0)), trim)                        # cuff
        if glow_c and face == 'front':
            _plate(dc, _rect(rect, (0.38, 0.5, 0.62, 0.7)), rgb(glow_c), rim=False, light=False)
    # ---- boots (lower half of the legs on the chest layer)
    for face, rect in LEG.items():
        if face == 'top':
            continue
        if face == 'bottom':
            _plate(dc, _rect(rect), mix(dark, (0, 0, 0), 0.3), light=False)
            continue
        _plate(dc, _rect(rect, (0, 0.55, 1, 0.68)), trim)                       # boot cuff
        _plate(dc, _rect(rect, (0, 0.68, 1, 0.9)), base)
        _plate(dc, _rect(rect, (0, 0.9, 1, 1.0)), mix(dark, (0, 0, 0), 0.3), light=False)   # sole
    # ---- leggings: belt + legs with knee guards
    for face, rect in BODY.items():
        if face in ('top', 'bottom'):
            continue
        _plate(dl, _rect(rect, (0, 0.72, 1, 1.0)), trim)                        # belt
        if face == 'front':
            _plate(dl, _rect(rect, (0.36, 0.74, 0.64, 0.98)), acc)              # buckle
    for face, rect in LEG.items():
        if face == 'top':
            _plate(dl, _rect(rect), trim, light=False)
            continue
        if face == 'bottom':
            _plate(dl, _rect(rect), dark, light=False)
            continue
        _plate(dl, _rect(rect, (0, 0, 1, 0.3)), base)
        _plate(dl, _rect(rect, (0, 0.3, 1, 0.5)), trim)                         # knee guard
        if face == 'front':
            _plate(dl, _rect(rect, (0.3, 0.33, 0.7, 0.47)), acc, light=False)
        _plate(dl, _rect(rect, (0, 0.5, 1, 1.0)), dark)
    return chest, legs


def mannequin_clean(chest, legs):
    k = 3
    out = Image.new('RGBA', (16 * SC * k, 32 * SC * k), (26, 22, 38, 255))

    def put(img, rect, dx, dy):
        x, y, w, h = (v * SC for v in rect)
        part = img.crop((x, y, x + w, y + h)).resize((w * k, h * k), Image.NEAREST)
        out.alpha_composite(part, (dx * SC * k, dy * SC * k))
    put(chest, ARM['front'], 0, 8)
    put(chest, ARM['front'], 12, 8)
    put(chest, BODY['front'], 4, 8)
    put(legs, BODY['front'], 4, 8)
    put(legs, LEG['front'], 4, 20)
    put(legs, LEG['front'], 8, 20)
    put(chest, LEG['front'], 4, 20)
    put(chest, LEG['front'], 8, 20)
    return out


def build(out_root, prev_dir):  # noqa: F811 – replaces the painted version above
    base = os.path.join(out_root, 'assets', 'mythoscraft', 'textures', 'entity', 'equipment')
    os.makedirs(os.path.join(base, 'humanoid'), exist_ok=True)
    os.makedirs(os.path.join(base, 'humanoid_leggings'), exist_ok=True)
    for name, spec in SETS.items():
        chest, legs = build_set_clean(name, spec)
        chest.save(os.path.join(base, 'humanoid', name + '.png'))
        legs.save(os.path.join(base, 'humanoid_leggings', name + '.png'))
        mannequin_clean(chest, legs).save(os.path.join(prev_dir, 'armor_' + name + '.png'))

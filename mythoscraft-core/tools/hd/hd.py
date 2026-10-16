"""HD model toolkit for MythosCraft: box models with their own painted texture (several pixels per model unit),
written as Minecraft Java item models (Blockbench-compatible) plus a quick preview renderer.

A model is a list of boxes. Every face of every box gets its own region in the model's texture and is painted by a
painter function (material) – optionally replaced per face by a decal painter (faces, visors, emblems …)."""
import json
import math
import os
import random

from PIL import Image, ImageDraw

PX = 4  # texture pixels per model unit
FACES = ['north', 'south', 'east', 'west', 'up', 'down']


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + tuple(c[3:])


class Box:
    def __init__(self, frm, to, mat, faces=None, rot=None, glow=False, name='', hide=()):
        self.frm = list(frm)
        self.to = list(to)
        self.mat = mat              # default painter
        self.faces = faces or {}    # face -> painter (decals)
        self.rot = rot              # {"origin": [..], "axis": "y", "angle": 22.5}
        self.glow = glow
        self.name = name
        self.hide = set(hide)

    def size(self, face):
        dx, dy, dz = (self.to[i] - self.frm[i] for i in range(3))
        return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[face]


class Model:
    def __init__(self, name, display='hat', px=PX):
        self.name = name
        self.boxes = []
        self.display = display
        self.px = px

    def add(self, *a, **k):
        b = Box(*a, **k)
        self.boxes.append(b)
        return b

    # ------------------------------------------------------------------ texture packing & painting
    def build(self, seed=1):
        rnd = random.Random(seed)
        clean_mode = getattr(self, 'clean', True) and getattr(self, 'realism', True)
        UPSCALE = 1 if clean_mode else getattr(self, 'upscale', UPSCALE_DEFAULT)
        rects = []
        for bi, b in enumerate(self.boxes):
            for f in FACES:
                if f in b.hide:
                    continue
                w, h = b.size(f)
                pw, ph = max(1, int(math.ceil(w * self.px - 1e-6))), max(1, int(math.ceil(h * self.px - 1e-6)))
                rects.append((bi, f, pw * UPSCALE, ph * UPSCALE))
        # shelf packing, tallest first
        order = sorted(rects, key=lambda r: -r[3])
        size = 64
        while True:
            pos = {}
            x = y = shelf = 0
            ok = True
            for bi, f, w, h in order:
                if w > size:
                    ok = False
                    break
                if x + w > size:
                    x = 0
                    y += shelf
                    shelf = 0
                if y + h > size:
                    ok = False
                    break
                pos[(bi, f)] = (x, y, w, h)
                x += w
                shelf = max(shelf, h)
            if ok:
                break
            size *= 2
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        for (bi, f), (x, y, w, h) in pos.items():
            b = self.boxes[bi]
            painter = b.faces.get(f, b.mat)
            tile = Image.new('RGBA', (w // UPSCALE, h // UPSCALE), (0, 0, 0, 0))
            painter(tile, f, b, rnd)
            tile = tile.resize((w, h), Image.NEAREST)
            if clean_mode:
                tile = hand_shade(clean(tile, 3), f)
            elif not b.glow and getattr(self, 'realism', True):
                realism(tile, f, rnd)
                pixelart(tile, f)
            img.paste(tile, (x, y))
        self.tex = img
        self.pos = pos
        return img

    def json(self, texture_id, extra_display=None):
        s = self.tex.size[0]
        els = []
        for bi, b in enumerate(self.boxes):
            faces = {}
            for f in FACES:
                if (bi, f) not in self.pos:
                    continue
                x, y, w, h = self.pos[(bi, f)]
                uv = [round(x / s * 16, 4), round(y / s * 16, 4), round((x + w) / s * 16, 4), round((y + h) / s * 16, 4)]
                faces[f] = {'uv': uv, 'texture': '#0'}
            e = {'from': [round(v, 4) for v in b.frm], 'to': [round(v, 4) for v in b.to], 'faces': faces}
            if b.name:
                e['name'] = b.name
            if b.rot:
                e['rotation'] = b.rot
            if b.glow:
                e['light_emission'] = 15
            els.append(e)
        out = {'textures': {'0': texture_id, 'particle': texture_id}, 'elements': els, 'display': DISPLAYS[self.display]}
        if extra_display:
            out['display'].update(extra_display)
        if self.display in ('hat', 'companion'):
            out['gui_light'] = 'side'
        return out

    def save(self, out_root, folder='hd'):
        self.build()
        tex_dir = os.path.join(out_root, 'assets', 'mythoscraft', 'textures', 'item', folder)
        mod_dir = os.path.join(out_root, 'assets', 'mythoscraft', 'models', 'item')
        os.makedirs(tex_dir, exist_ok=True)
        os.makedirs(mod_dir, exist_ok=True)
        self.tex.save(os.path.join(tex_dir, self.name + '.png'))
        with open(os.path.join(mod_dir, self.name + '.json'), 'w', encoding='utf-8') as f:
            json.dump(self.json(f'mythoscraft:item/{folder}/{self.name}'), f, indent=1)


DISPLAYS = {
    # worn on the head: the shell from -0.5..16.5 hugs the player's head (scale 0.82)
    'hat': {
        'head': {'translation': [0, 0, 0], 'scale': [0.82, 0.82, 0.82]},
        'gui': {'rotation': [25, -145, 0], 'translation': [0, -1.5, 0], 'scale': [0.62, 0.62, 0.62]},
        'ground': {'translation': [0, 1, 0], 'scale': [0.4, 0.4, 0.4]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, -1, 0], 'scale': [0.7, 0.7, 0.7]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2, 0], 'scale': [0.35, 0.35, 0.35]},
        'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2, 0], 'scale': [0.35, 0.35, 0.35]},
        'firstperson_righthand': {'rotation': [0, -135, 0], 'translation': [0, 2, 0], 'scale': [0.35, 0.35, 0.35]},
        'firstperson_lefthand': {'rotation': [0, -135, 0], 'translation': [0, 2, 0], 'scale': [0.35, 0.35, 0.35]},
    },
    # trophies: held upright in the hand like a cup, front plate towards the viewer in the inventory
    'trophy': {
        'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
        'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
        'firstperson_righthand': {'rotation': [0, -90, 0], 'translation': [1.13, 3.2, 1.13], 'scale': [0.6, 0.6, 0.6]},
        'firstperson_lefthand': {'rotation': [0, 90, 0], 'translation': [1.13, 3.2, 1.13], 'scale': [0.6, 0.6, 0.6]},
        'gui': {'rotation': [20, 200, 0], 'scale': [0.85, 0.85, 0.85]},
        'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [0.9, 0.9, 0.9]},
    },
    # shoulder companions (rendered by an item display without display transform; the inventory icon uses gui)
    'companion': {
        'gui': {'rotation': [20, -150, 0], 'translation': [0, -1, 0], 'scale': [0.9, 0.9, 0.9]},
        'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [0.8, 0.8, 0.8]},
        'thirdperson_righthand': {'rotation': [0, 180, 0], 'translation': [0, 2, 2], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_lefthand': {'rotation': [0, 180, 0], 'translation': [0, 2, 2], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [0, 160, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_lefthand': {'rotation': [0, 160, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    },
    # voxel sprites drawn like vanilla tools (handle bottom-left, tip top-right, front = south)
    'voxel': {
        'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [0, 180, 0]},
    },
    'handheld': {
        'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'gui': {'rotation': [0, 90, 0], 'scale': [0.8, 0.8, 0.8]},
        'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [0.9, 0.9, 0.9]},
    },
}


# ====================================================================== realism pass
UPSCALE_DEFAULT = 2  # every painted texel becomes 2x2, the realism pass then adds detail below the design


def _value_noise(w, h, cell, rnd):
    gw, gh = w // cell + 2, h // cell + 2
    g = [[rnd.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        fy, iy = (y / cell) % 1, y // cell
        sy = fy * fy * (3 - 2 * fy)
        for x in range(w):
            fx, ix = (x / cell) % 1, x // cell
            sx = fx * fx * (3 - 2 * fx)
            a = g[iy][ix] + (g[iy][ix + 1] - g[iy][ix]) * sx
            b = g[iy + 1][ix] + (g[iy + 1][ix + 1] - g[iy + 1][ix]) * sx
            out[y][x] = a + (b - a) * sy
    return out


def realism(tile, face, rnd):
    """PrimeBlocks-like finish on top of the painted design: soft top light, ambient occlusion toward the lower and
    outer edges, a crisp highlight rim, dark outline, low-frequency wear and fine grain, slightly richer colours."""
    w, h = tile.size
    if w < 2 or h < 2:
        return
    p = tile.load()
    wear = _value_noise(w, h, max(3, min(w, h) // 3), rnd)
    base_light = {'up': 1.10, 'down': 0.72, 'north': 1.0, 'south': 1.0, 'east': 0.94, 'west': 0.94}[face]
    for y in range(h):
        for x in range(w):
            c = p[x, y]
            if c[3] == 0:
                continue
            r, g, b = c[0], c[1], c[2]
            # contrast + saturation boost
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            r, g, b = (lum + (v - lum) * 1.18 for v in (r, g, b))
            r, g, b = ((v - 128) * 1.08 + 128 for v in (r, g, b))
            # vertical light falloff (sides lit from above) and occlusion near the edges
            v = y / (h - 1)
            f = base_light * ((1.12 - 0.26 * v) if face not in ('up', 'down') else 1.0)
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge < 3 and min(w, h) > 6:
                f *= 0.86 + 0.045 * edge
            f *= 0.9 + 0.2 * wear[y][x]
            n = rnd.randint(-5, 5)
            r, g, b = (v * f + n for v in (r, g, b))
            # cool shadows, warm lights
            if f < 0.95:
                b += 6
                r -= 3
            elif f > 1.08:
                r += 4
                g += 2
            out = tuple(max(0, min(255, int(round(v)))) for v in (r, g, b))
            if min(w, h) > 4:
                if y == 0 or x == 0:
                    out = tuple(min(255, int(v * 1.22 + 10)) for v in out)
                elif y == h - 1 or x == w - 1:
                    out = tuple(int(v * 0.55) for v in out)
            p[x, y] = out + (c[3],)


# ====================================================================== clean pass
def clean(tile, colors=4):
    """The premium-pack principle: every face is a few flat colours, no noise, no gradients – the game's own face
    lighting does the shading. Keeps stripes, gems and decals, removes the grainy look."""
    alpha = tile.getchannel('A')
    if min(tile.size) >= 8:  # smooth away grain/patterns from the old painters, keep shapes like visors and gems
        from PIL import ImageFilter
        tile = tile.filter(ImageFilter.ModeFilter(5))
    q = tile.convert('RGB').quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert('RGBA')
    q.putalpha(alpha)
    return q


def hand_shade(tile, face):
    """Hand-painted cube face (the premium-pack principle, worked out in Blockbench): bright rim row on top
    (cream/light tint of the colour), soft light falloff from the upper left to a hue-shifted lower right, darker
    last row, side faces a little darker. Pixel based, no random noise."""
    w, h = tile.size
    p = tile.load()
    for y in range(h):
        for x in range(w):
            c = p[x, y]
            if c[3] == 0:
                continue
            base = c[:3]
            hi = mix(base, (255, 244, 205), 0.38)
            rim = mix(base, (255, 250, 230), 0.7)
            lo = mix(base, (16, 10, 36), 0.45)
            t = (x / max(1, w - 1)) * 0.6 + (y / max(1, h - 1)) * 0.4
            col = mix(hi, base, min(1.0, t * 1.6)) if face != 'down' else mix(base, lo, 0.5)
            if t > 0.75:
                col = mix(col, lo, (t - 0.75) * 1.2)
            if face == 'up':
                col = mix(col, hi, 0.25)
            if h > 2 and y == 0 and face != 'down':
                col = rim
            elif h > 2 and y == h - 1 and face not in ('up', 'down'):
                col = mix(col, lo, 0.6)
            if face in ('east', 'west'):
                col = mix(col, lo, 0.12)
            p[x, y] = tuple(col) + (c[3],)
    return tile


def flat_shade(tile, face):
    """The premium-pack texture principle (learned from a reference mask): every cube face stays its flat base
    colour; the top texel row is a bright cream/light rim, the second row half-light, the last row darker and
    hue-shifted, side faces slightly darker. No gradients, no noise."""
    w, h = tile.size
    p = tile.load()
    for y in range(h):
        for x in range(w):
            c = p[x, y]
            if c[3] == 0:
                continue
            base = c[:3]
            light = mix(base, (255, 246, 214), 0.5)
            dark = mix(base, (20, 12, 40), 0.35)
            col = base
            if face == 'up':
                col = mix(base, light, 0.22)
            elif face == 'down':
                col = mix(base, dark, 0.6)
            elif h > 2 and y == 0:
                col = light
            elif h > 4 and y == 1:
                col = mix(base, light, 0.4)
            elif h > 2 and y == h - 1:
                col = dark
            if face in ('east', 'west') and y > 0:
                col = mix(col, dark, 0.2)
            p[x, y] = tuple(col) + (c[3],)
    return tile


def paint_shade(tile, face):
    """Applies the hand-painted cube shading (see painted()) on top of the face's own colours: light top with a bright
    rim, hue-shifted darker bottom, side faces a bit darker, soft specular spot. Smooth, no noise."""
    w, h = tile.size
    p = tile.load()
    for y in range(h):
        t = y / max(1, h - 1)
        for x in range(w):
            c = p[x, y]
            if c[3] == 0:
                continue
            base = c[:3]
            light = mix(base, (255, 246, 214), 0.45)
            shadow = mix(base, (20, 12, 40), 0.55)
            if face == 'up':
                col = mix(base, light, 0.35 - 0.2 * x / max(1, w - 1))
            elif face == 'down':
                col = mix(base, shadow, 0.6)
            else:
                col = mix(light, base, t * 2) if t < 0.5 else mix(base, shadow, (t - 0.5) * 1.7)
                if face in ('east', 'west'):
                    col = mix(col, shadow, 0.15)
                if w > 3 and h > 2:
                    d = ((x - w * 0.28) / (w * 0.22)) ** 2 + ((y - h * 0.3) / max(1.0, h * 0.35)) ** 2
                    if d < 1:
                        col = mix(col, light, 0.4 * (1 - d))
            if h > 2 and y == 0 and face != 'down':
                col = mix(light, (255, 255, 255), 0.3)
            elif h > 2 and y == h - 1:
                col = mix(col, shadow, 0.5)
            p[x, y] = tuple(col) + (c[3],)
    return tile


def chunky(tile, face):
    """Premium-pack cube texture: coarse texels, light top rim, dark bottom rim, lighter left column."""
    w, h = tile.size
    p = tile.load()
    for y in range(h):
        for x in range(w):
            c = p[x, y]
            if c[3] == 0:
                continue
            f = 1.0
            if h > 2 and y == 0:
                f = 1.22
            elif h > 2 and y == h - 1:
                f = 0.74
            elif w > 2 and x == 0:
                f = 1.08
            if face == 'up':
                f *= 1.06
            p[x, y] = tuple(max(0, min(255, int(v * f + (12 if f > 1.15 else 0)))) for v in c[:3]) + (c[3],)
    return tile


# ====================================================================== pixel-art pass (same look as the voxel items)
_SH, _LI = (27, 16, 48), (255, 244, 214)


def pixelart(tile, face=None, levels=5, outline=True):
    """Posterised, hue-shifted shading (cool purple shadows, warm cream lights) + dark rim + bright top-left rim."""
    w, h = tile.size
    p = tile.load()
    for y in range(h):
        for x in range(w):
            c = p[x, y]
            if c[3] == 0:
                continue
            lum = (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255
            q = round(lum * (levels - 1)) / (levels - 1)
            base = c[:3]
            if q < lum:
                col = mix(base, _SH, (lum - q) * 1.6 + (0.18 if q < 0.3 else 0))
            else:
                col = mix(base, _LI, (q - lum) * 1.4 + (0.12 if q > 0.8 else 0))
            if outline and w > 4 and h > 4:
                if x == w - 1 or y == h - 1:
                    col = mix(col, _SH, 0.55)
                elif x == 0 or y == 0:
                    col = mix(col, _LI, 0.3)
            p[x, y] = tuple(col) + (c[3],)


# ====================================================================== painters (materials)
def finish(tile, light=1.12, dark=0.7, border=True):
    """Bevel: lighter top/left edge, darker bottom/right edge, gives the polished look."""
    w, h = tile.size
    p = tile.load()
    if w < 3 or h < 3 or not border:
        return
    for x in range(w):
        for y in range(h):
            c = p[x, y]
            if c[3] == 0:
                continue
            if x == 0 or y == 0:
                p[x, y] = shade(c, light)
            elif x == w - 1 or y == h - 1:
                p[x, y] = shade(c, dark)


def grad(top, bottom, noise=6, vertical=True):
    top, bottom = rgb(top), rgb(bottom)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                t = (y / max(1, h - 1)) if vertical else (x / max(1, w - 1))
                c = mix(top, bottom, t)
                n = rnd.randint(-noise, noise)
                p[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (255,)
        if face == 'up':
            for x in range(w):
                for y in range(h):
                    p[x, y] = shade(p[x, y], 1.08)
        if face == 'down':
            for x in range(w):
                for y in range(h):
                    p[x, y] = shade(p[x, y], 0.8)
        finish(tile)
    return paint


def metal(base, spec=None, brushed=True):
    base = rgb(base)
    spec = rgb(spec) if spec else shade(base, 1.45)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for y in range(h):
            row = rnd.randint(-8, 8) if brushed else 0
            for x in range(w):
                t = 0.5 + 0.5 * math.sin((x + y * 0.6) / max(3, w) * math.pi * 1.4)
                c = mix(shade(base, 0.82), spec, t * 0.55)
                n = row + rnd.randint(-4, 4)
                p[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (255,)
        # specular streak
        for i in range(-h, w, max(6, w // 2)):
            for k in range(min(w, h)):
                x, y = i + k, k
                if 0 <= x < w and 0 <= y < h:
                    p[x, y] = shade(p[x, y], 1.18)
        if face == 'down':
            for x in range(w):
                for y in range(h):
                    p[x, y] = shade(p[x, y], 0.78)
        finish(tile, 1.2, 0.62)
    return paint


def stripes(c1, c2, every=3, horizontal=True, metallic=True, w2=None):
    c1, c2 = rgb(c1), rgb(c2)
    w2 = w2 or every

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                v = (y if horizontal or face in ('up', 'down') else x) % (every + w2)
                c = c1 if v < every else c2
                if metallic:
                    c = shade(c, 0.9 + 0.2 * math.sin(x / max(2, w) * math.pi))
                n = rnd.randint(-5, 5)
                p[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (255,)
                if v == 0 or v == every:
                    p[x, y] = shade(p[x, y], 1.15)
                elif v == every - 1 or v == every + w2 - 1:
                    p[x, y] = shade(p[x, y], 0.82)
        finish(tile)
    return paint


def glow(c, core=None):
    c = rgb(c)
    core = rgb(core) if core else (255, 255, 255)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                d = math.hypot((x + 0.5) / w - 0.5, (y + 0.5) / h - 0.5) * 2
                p[x, y] = mix(core, c, min(1, d * 1.2)) + (255,)
    return paint


def crystal(c, dark):
    c, dark = rgb(c), rgb(dark)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                t = ((x * 0.7 + y) % max(4, w // 2 + 2)) / max(4, w // 2 + 2)
                col = mix(c, dark, t * 0.8)
                p[x, y] = shade(col, 1 + rnd.random() * 0.08) + (255,)
        for _ in range(max(1, w * h // 40)):
            p[rnd.randrange(w), rnd.randrange(h)] = (255, 255, 255, 255)
        finish(tile, 1.3, 0.65)
    return paint


def fabric(c, weave=None):
    c = rgb(c)
    weave = rgb(weave) if weave else shade(c, 0.85)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                col = c if (x + y) % 2 else weave
                p[x, y] = shade(col, 0.95 + rnd.random() * 0.1) + (255,)
        finish(tile, 1.08, 0.8)
    return paint


def fur(c, tip):
    c, tip = rgb(c), rgb(tip)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                t = rnd.random()
                p[x, y] = mix(c, tip, t * 0.7) + (255,)
        for _ in range(w * h // 6):
            x, y = rnd.randrange(w), rnd.randrange(h)
            p[x, y] = shade(p[x, y], 0.75)
        finish(tile, 1.05, 0.85)
    return paint


def scales(c, edge):
    c, edge = rgb(c), rgb(edge)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                row = y // 4
                ox = 2 if row % 2 else 0
                cx = ((x + ox) % 4) - 1.5
                cy = (y % 4) - 0.5
                d = math.hypot(cx, cy * 1.2)
                col = mix(shade(c, 1.2), edge, min(1, d / 2.2))
                p[x, y] = col + (255,)
        finish(tile, 1.1, 0.7)
    return paint


def solid(c, noise=4):
    c = rgb(c)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                n = rnd.randint(-noise, noise)
                p[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (255,)
        finish(tile)
    return paint


def decal(base, draw):
    """Base painter + a drawing callback draw(d, w, h, face) with an ImageDraw (for faces, emblems, visors)."""
    def paint(tile, face, box, rnd):
        base(tile, face, box, rnd)
        d = ImageDraw.Draw(tile)
        draw(d, tile.size[0], tile.size[1], face, tile)
    return paint


# ====================================================================== preview renderer
def render(model, path, size=320, yaw=145, pitch=24, background=(26, 22, 38), zoom=1.0):
    """Quick textured orthographic render (painter's algorithm per texel) to look at a model."""
    if not hasattr(model, 'tex'):
        model.build()
    tex = model.tex.load()
    ya, pa = math.radians(yaw), math.radians(pitch)

    def rot_el(v, b):
        if not b.rot:
            return v
        o = b.rot['origin']
        a = math.radians(b.rot['angle'])
        x, y, z = v[0] - o[0], v[1] - o[1], v[2] - o[2]
        ax = b.rot['axis']
        if ax == 'y':
            x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
        elif ax == 'x':
            y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
        else:
            x, y = x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)
        return (x + o[0], y + o[1], z + o[2])

    def proj(v):
        x, y, z = v[0] - 8, v[1] - 8, v[2] - 8
        x, z = x * math.cos(ya) - z * math.sin(ya), x * math.sin(ya) + z * math.cos(ya)
        y, z = y * math.cos(pa) - z * math.sin(pa), y * math.sin(pa) + z * math.cos(pa)
        return x, y, z

    quads = []
    light = {'north': 0.92, 'south': 0.92, 'east': 0.8, 'west': 0.8, 'up': 1.0, 'down': 0.6}  # item GUI lighting
    for bi, b in enumerate(model.boxes):
        f0, t0 = b.frm, b.to
        corners = {
            'north': [(t0[0], t0[1], f0[2]), (f0[0], t0[1], f0[2]), (f0[0], f0[1], f0[2]), (t0[0], f0[1], f0[2])],
            'south': [(f0[0], t0[1], t0[2]), (t0[0], t0[1], t0[2]), (t0[0], f0[1], t0[2]), (f0[0], f0[1], t0[2])],
            'east': [(t0[0], t0[1], t0[2]), (t0[0], t0[1], f0[2]), (t0[0], f0[1], f0[2]), (t0[0], f0[1], t0[2])],
            'west': [(f0[0], t0[1], f0[2]), (f0[0], t0[1], t0[2]), (f0[0], f0[1], t0[2]), (f0[0], f0[1], f0[2])],
            'up': [(f0[0], t0[1], f0[2]), (t0[0], t0[1], f0[2]), (t0[0], t0[1], t0[2]), (f0[0], t0[1], t0[2])],
            'down': [(f0[0], f0[1], t0[2]), (t0[0], f0[1], t0[2]), (t0[0], f0[1], f0[2]), (f0[0], f0[1], f0[2])],
        }
        for f, cs in corners.items():
            if (bi, f) not in model.pos:
                continue
            x0, y0, w, h = model.pos[(bi, f)]
            P = [proj(rot_el(c, b)) for c in cs]  # tl, tr, br, bl in texture orientation
            st = max(1, min(w, h) // 16)          # preview only: big textures are drawn in blocks (fast)
            for ty in range(0, h, st):
                for tx in range(0, w, st):
                    col = tex[x0 + tx, y0 + ty]
                    if col[3] < 128:
                        continue
                    u0, u1, v0, v1 = tx / w, min(1, (tx + st) / w), ty / h, min(1, (ty + st) / h)

                    def at(u, v):
                        top = [P[0][i] + (P[1][i] - P[0][i]) * u for i in range(3)]
                        bot = [P[3][i] + (P[2][i] - P[3][i]) * u for i in range(3)]
                        return [top[i] + (bot[i] - top[i]) * v for i in range(3)]
                    pts = [at(u0, v0), at(u1, v0), at(u1, v1), at(u0, v1)]
                    depth = sum(p[2] for p in pts) / 4
                    lf = 1.0 if b.glow else light[f]
                    quads.append((depth, pts, shade(col[:3], lf)))
    quads.sort(key=lambda q: q[0])
    img = Image.new('RGB', (size, size), background)
    d = ImageDraw.Draw(img)
    sc = size / 36 * zoom
    for depth, pts, col in quads:
        d.polygon([(size / 2 + p[0] * sc, size / 2 - p[1] * sc) for p in pts], fill=col)
    img.save(path)
    return img


def cracks(base, crack, glowing=True):
    base_p = metal(base, brushed=False) if isinstance(base, str) else base
    crack = rgb(crack)

    def paint(tile, face, box, rnd):
        base_p(tile, face, box, rnd)
        w, h = tile.size
        p = tile.load()
        for _ in range(max(1, (w * h) // 90)):
            x, y = rnd.randrange(w), rnd.randrange(h)
            for _ in range(rnd.randint(3, 9)):
                if 0 <= x < w and 0 <= y < h:
                    p[x, y] = crack + (255,)
                    if glowing and 0 <= x + 1 < w:
                        p[x + 1, y] = mix(p[x + 1, y][:3], crack, 0.5) + (255,)
                x += rnd.choice((-1, 0, 1))
                y += rnd.choice((0, 1, 1))
    return paint


def wood(c, dark):
    c, dark = rgb(c), rgb(dark)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        off = rnd.random() * 6
        for x in range(w):
            for y in range(h):
                t = 0.5 + 0.5 * math.sin((x + math.sin(y * 0.4 + off) * 1.6) * 1.3)
                p[x, y] = mix(c, dark, t * 0.6 + rnd.random() * 0.1) + (255,)
        finish(tile, 1.08, 0.75)
    return paint


def leaves(c, dark, light=None):
    c, dark = rgb(c), rgb(dark)
    light = rgb(light) if light else shade(c, 1.3)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                r = rnd.random()
                p[x, y] = (dark if r < 0.3 else light if r > 0.85 else c) + (255,)
        finish(tile, 1.05, 0.8)
    return paint


def glass(c, alpha=150):
    c = rgb(c)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for x in range(w):
            for y in range(h):
                t = (x + y) / max(1, w + h)
                col = mix(shade(c, 1.25), shade(c, 0.75), t)
                a = alpha
                if abs((x - y) - w * 0.15) < 2 or abs((x - y) - w * 0.4) < 1:
                    col, a = (240, 250, 255), 220
                p[x, y] = col + (a,)
        for x in range(w):
            p[x, 0] = shade(c, 1.4) + (230,)
            p[x, h - 1] = shade(c, 0.6) + (230,)
    return paint


def painted(base, light, shadow, rim=None, spec=True):
    """Hand-painted cube face like the premium packs: smooth top→bottom gradient (light → base → hue-shifted
    shadow), bright rim along the top edge, darker bottom edge, soft specular spot – no noise at all."""
    base, light, shadow = rgb(base), rgb(light), rgb(shadow)
    rim = rgb(rim) if rim else mix(light, (255, 255, 255), 0.35)

    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        for y in range(h):
            t = y / max(1, h - 1)
            for x in range(w):
                if face == 'up':
                    c = mix(base, light, 0.45 - 0.25 * (x / max(1, w - 1)))
                elif face == 'down':
                    c = mix(base, shadow, 0.7)
                else:
                    c = mix(light, base, t * 2) if t < 0.5 else mix(base, shadow, (t - 0.5) * 2 * 0.85)
                    if face in ('east', 'west'):
                        c = mix(c, shadow, 0.18)
                if spec and face not in ('down',) and w > 3 and h > 2:
                    sx, sy = w * 0.28, h * 0.3
                    d = ((x - sx) / (w * 0.22)) ** 2 + ((y - sy) / max(1.0, h * 0.35)) ** 2
                    if d < 1:
                        c = mix(c, light, 0.45 * (1 - d))
                if y == 0 and face != 'down':
                    c = rim
                elif y == h - 1 and h > 2:
                    c = mix(c, shadow, 0.55)
                elif (x == 0 or x == w - 1) and w > 3 and face != 'up':
                    c = mix(c, shadow if x else light, 0.25)
                p[x, y] = tuple(c) + (255,)
    return paint

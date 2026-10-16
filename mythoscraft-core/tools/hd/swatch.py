"""Swatch models – the premium-pack texture principle.

Every cube face shows ONE whole 8x8 material swatch, stretched over the face (no matter how big the cube is), so you
never see single texture pixels. A swatch is painted like a hand-made material tile:
  matte : darker 1px rim, lighter row under the top rim, light inner corners
  metal : darker rim with light corners, a diagonal sheen line from lower left to upper right
  glow  : bright core (with light_emission on the element)
The texture is a tiny atlas of swatches (one per colour+style)."""
import json
import math
import os

from PIL import Image

from hd import DISPLAYS, rgb, mix

SW = 64  # swatch size (high resolution): 1px rim is ~1.5 % of a face – fine edges, room for painted detail


def _hex(c):
    return '%02x%02x%02x' % tuple(c[:3])


def _hash(x, y, k=0):
    """deterministic pseudo random 0..1 (same swatch every build)"""
    n = (x * 374761393 + y * 668265263 + k * 2147483647) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535


def paint_swatch(base, style):
    """Hand-painted material tile at 64x64: soft edge occlusion, light from the upper left, fine material detail."""
    b = rgb(base)
    im = Image.new('RGBA', (SW, SW))
    p = im.load()
    last = SW - 1
    for y in range(SW):
        for x in range(SW):
            u, v = x / last, y / last
            if style == 'glow':
                d = max(abs(u - 0.5), abs(v - 0.5)) * 2
                c = mix(mix(b, (255, 255, 255), 0.5), b, min(1, d * 1.3))
                if d > 0.94:
                    c = mix(b, (255, 255, 255), 0.18)
                p[x, y] = c + (255,)
                continue
            if style == 'flat':
                # voxel cubes of converted sprites: clean colour, gentle light, no rim (no visible pixel grid)
                f = 1.05 - 0.1 * v
                p[x, y] = tuple(max(0, min(255, int(ch * f))) for ch in b) + (255,)
                continue
            # light from the upper left, soft occlusion towards the edges
            f = 1.08 - 0.16 * (0.65 * v + 0.35 * u)
            edge = min(x, y, last - x, last - y)
            if edge < 4:
                f *= 0.9 + 0.025 * edge
            c = tuple(max(0, min(255, int(ch * f))) for ch in b)
            if style == 'metal':
                # brushed metal: faint horizontal streaks + a wide soft diagonal sheen + a crisp highlight line
                streak = (_hash(0, y // 2, 7) - 0.5) * 0.08
                c = tuple(max(0, min(255, int(ch * (1 + streak)))) for ch in c)
                dd = abs((u + v) - 0.85)
                if dd < 0.22:
                    c = mix(c, (255, 252, 235), 0.28 * (1 - dd / 0.22))
                if abs((u + v) - 0.85) < 0.025:
                    c = mix(c, (255, 255, 245), 0.55)
                if (x, y) in ((1, 1), (2, 1), (1, 2)):
                    c = mix(c, (255, 255, 255), 0.6)       # glint on the corner
            else:
                # matte: very soft mottling (cloth/paint/stone feel) + light band under the top edge
                m = (_hash(x // 4, y // 4, 3) - 0.5) * 0.05
                c = tuple(max(0, min(255, int(ch * (1 + m)))) for ch in c)
                if 2 <= y <= 4 and 2 <= x <= last - 2:
                    c = mix(c, (255, 255, 255), 0.1)
            if edge == 0:
                c = mix(c, (10, 8, 30), 0.14) if style != 'metal' else mix(c, (30, 18, 4), 0.2)
            elif edge == 1 and (x == 1 or y == 1):
                c = mix(c, (255, 255, 240), 0.12)            # bright inner bevel top/left
            p[x, y] = c + (255,)
    return im


class Cube:
    def __init__(self, frm, to, color, style='matte', rot=None, glow=False, name='', faces=None):
        self.frm, self.to = list(frm), list(to)
        self.color, self.style = color, ('glow' if glow and style == 'matte' else style)
        self.rot, self.glow, self.name = rot, glow, name
        self.faces = faces or {}            # face -> (color, style) override


class SwatchModel:
    def __init__(self, name, display='hat'):
        self.name, self.display = name, display
        self.cubes = []

    def add(self, frm, to, color, style='matte', **k):
        c = Cube(frm, to, color, style, **k)
        self.cubes.append(c)
        return c

    # -------------------------------------------------------------- atlas
    def _keys(self):
        keys = []
        for c in self.cubes:
            for k in [(c.color, c.style)] + [v for v in c.faces.values() if isinstance(v, tuple)]:
                if k not in keys:
                    keys.append(k)
        return keys

    def build(self):
        keys = self._keys()
        size = SW
        images = [v for c in self.cubes for v in c.faces.values() if not isinstance(v, tuple)]
        n = len(keys) + len(images)
        while (size // SW) ** 2 < n:
            size *= 2
        tex = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        self.slot = {}
        per = size // SW
        for i, k in enumerate(keys):
            x, y = (i % per) * SW, (i // per) * SW
            tex.paste(paint_swatch(*k), (x, y))
            self.slot[k] = (x, y, SW, SW)
        for j, img in enumerate(images):
            i = len(keys) + j
            x, y = (i % per) * SW, (i // per) * SW
            tex.paste(img.resize((SW, SW), Image.NEAREST), (x, y))
            self.slot[id(img)] = (x, y, SW, SW)
        self.tex = tex
        # hd.render compatibility: boxes + pos
        self.boxes = self.cubes
        self.pos = {}
        for bi, c in enumerate(self.cubes):
            for f in ('north', 'south', 'east', 'west', 'up', 'down'):
                v = c.faces.get(f, (c.color, c.style))
                self.pos[(bi, f)] = self.slot[v if isinstance(v, tuple) else id(v)]
        return tex

    def json(self, texture_id):
        s = self.tex.size[0]
        els = []
        for bi, c in enumerate(self.cubes):
            faces = {}
            for f in ('north', 'south', 'east', 'west', 'up', 'down'):
                x, y, w, h = self.pos[(bi, f)]
                faces[f] = {'uv': [round(x / s * 16, 4), round(y / s * 16, 4), round((x + w) / s * 16, 4), round((y + h) / s * 16, 4)],
                            'texture': '#0'}
            e = {'from': [round(v, 4) for v in c.frm], 'to': [round(v, 4) for v in c.to], 'faces': faces}
            if c.name:
                e['name'] = c.name
            if c.rot:
                e['rotation'] = c.rot
            if c.glow:
                e['light_emission'] = 15
            els.append(e)
        out = {'textures': {'0': texture_id, 'particle': texture_id}, 'elements': els,
               'display': getattr(self, 'extra_display', None) or DISPLAYS[self.display]}
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


# ------------------------------------------------------------------ conversion of the older painted box models
def _style_of(painter):
    q = getattr(painter, '__qualname__', '')
    if q.startswith(('metal', 'crystal', 'glass')):
        return 'metal'
    if q.startswith('glow'):
        return 'glow'
    return 'matte'


def _colour_of(painter, box, face='north'):
    import random
    tile = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    painter(tile, face, box, random.Random(3))
    px = [p[:3] for p in tile.getdata() if p[3] > 0]
    if not px:
        return '#808080'
    px.sort(key=lambda c: c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11)
    return '#' + _hex(px[len(px) // 2])


def from_model(m, keep_pictures=False):
    """hd.Model → SwatchModel: every box becomes one material swatch (its median colour). Face pictures (carved
    pumpkin face, skull …) are kept only when keep_pictures is set – masks have no face of their own."""
    import random
    out = SwatchModel(m.name, m.display)
    for b in m.boxes:
        c = out.add(b.frm, b.to, _colour_of(b.mat, b), _style_of(b.mat), rot=b.rot, glow=b.glow, name=b.name)
        for f, painter in b.faces.items():
            w, h = b.size(f)
            if keep_pictures and w >= 6 and h >= 6:
                tile = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
                painter(tile, f, b, random.Random(3))
                c.faces[f] = tile
            else:
                c.faces[f] = (_colour_of(painter, b, f), _style_of(painter))
    return out

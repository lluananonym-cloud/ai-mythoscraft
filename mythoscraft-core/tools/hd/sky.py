"""Season sky:
1. one small extra resource pack per season theme: tinted clouds, own sun and moon (sent next to the main pack),
2. sky dome pictures (huge glowing planes shown above the spawn by the plugin) as item models in the overlay."""
import io
import json
import math
import os
import random
import zipfile

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
SEASON_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'resourcepack', 'seasons')
VANILLA_CLOUDS = os.path.join(HERE, 'vanilla_clouds.png')

THEMES = ['halloween', 'winter', 'spring', 'summer', 'autumn', 'newyear', 'valentine', 'stars']
MOON_PHASES = ['full_moon', 'waning_gibbous', 'third_quarter', 'waning_crescent', 'new_moon', 'waxing_crescent',
               'first_quarter', 'waxing_gibbous']
CLOUD_TINT = {'halloween': (255, 150, 90), 'winter': (220, 235, 255), 'spring': (255, 205, 230), 'summer': (255, 250, 235),
              'autumn': (255, 190, 130), 'newyear': (210, 190, 255), 'valentine': (255, 170, 200), 'stars': (180, 170, 255)}


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ------------------------------------------------------------------ sun & moon (64x64)
def disc(img, cx, cy, r, inner, outer, glow=0):
    d = ImageDraw.Draw(img)
    for i in range(int(r + glow), 0, -1):
        t = min(1, i / r)
        c = tuple(int(inner[k] + (outer[k] - inner[k]) * t) for k in range(3))
        a = 255 if i <= r else int(255 * (1 - (i - r) / max(1, glow)) * 0.6)
        d.ellipse([cx - i, cy - i, cx + i, cy + i], fill=c + (a,))


def sun(theme):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if theme == 'valentine':
        d.polygon([(32, 54), (8, 28), (14, 12), (32, 22), (50, 12), (56, 28)], fill=(255, 120, 170, 255))
        d.ellipse([10, 10, 32, 32], fill=(255, 120, 170, 255))
        d.ellipse([32, 10, 54, 32], fill=(255, 120, 170, 255))
        d.ellipse([18, 16, 26, 22], fill=(255, 220, 235, 255))
        return img
    if theme == 'winter':
        disc(img, 32, 32, 18, (255, 255, 255), (186, 230, 253), glow=10)
        for a in range(0, 360, 60):
            r = math.radians(a)
            d.line([32, 32, 32 + math.cos(r) * 26, 32 + math.sin(r) * 26], fill=(255, 255, 255, 255), width=2)
        return img
    if theme == 'halloween':
        disc(img, 32, 32, 20, (255, 214, 120), (234, 88, 12), glow=8)
        d.polygon([(22, 26), (28, 20), (30, 28)], fill=(60, 20, 0, 255))
        d.polygon([(34, 28), (36, 20), (42, 26)], fill=(60, 20, 0, 255))
        d.polygon([(20, 36), (26, 42), (32, 38), (38, 42), (44, 36), (40, 46), (24, 46)], fill=(60, 20, 0, 255))
        return img
    colors = {'spring': ((255, 245, 200), (255, 170, 210)), 'summer': ((255, 255, 220), (255, 170, 30)),
              'autumn': ((255, 230, 170), (230, 110, 30)), 'newyear': ((255, 250, 220), (250, 200, 60)),
              'stars': ((240, 240, 255), (160, 140, 255))}[theme]
    disc(img, 32, 32, 16, colors[0], colors[1], glow=12)
    for a in range(0, 360, 30):
        r = math.radians(a)
        d.line([32 + math.cos(r) * 20, 32 + math.sin(r) * 20, 32 + math.cos(r) * 30, 32 + math.sin(r) * 30],
               fill=colors[1] + (220,), width=2)
    return img


def moon(theme, phase_index):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    base = {'halloween': ((255, 160, 60), (190, 70, 20)), 'winter': ((235, 245, 255), (150, 190, 230)),
            'spring': ((255, 230, 240), (230, 160, 200)), 'summer': ((255, 245, 220), (220, 200, 160)),
            'autumn': ((255, 210, 150), (200, 120, 60)), 'newyear': ((240, 230, 255), (170, 150, 230)),
            'valentine': ((255, 200, 220), (230, 110, 160)), 'stars': ((220, 225, 255), (120, 120, 200))}[theme]
    disc(img, 32, 32, 22, base[0], base[1], glow=6)
    rnd = random.Random(7)
    for _ in range(6):
        x, y, r = rnd.randint(18, 46), rnd.randint(18, 46), rnd.randint(2, 5)
        d.ellipse([x - r, y - r, x + r, y + r], fill=tuple(int(c * 0.85) for c in base[1]) + (255,))
    if theme == 'halloween':
        for bx, by in ((14, 20), (44, 14), (40, 46)):
            d.polygon([(bx, by), (bx - 6, by - 3), (bx - 3, by + 1), (bx, by + 3), (bx + 3, by + 1), (bx + 6, by - 3)], fill=(20, 10, 20, 255))
    if theme == 'valentine':
        d.polygon([(32, 44), (22, 32), (25, 26), (32, 30), (39, 26), (42, 32)], fill=(255, 80, 140, 255))
    # phase shadow (vanilla order: full, waning gibbous … waxing gibbous)
    lit = [1.0, 0.75, 0.5, 0.25, 0.0, 0.25, 0.5, 0.75][phase_index]
    waning = phase_index in (1, 2, 3)
    if lit < 1:
        shadow = Image.new('L', (64, 64), 0)
        sd = ImageDraw.Draw(shadow)
        off = int(44 * lit)
        if waning:
            sd.ellipse([10 - off, 10, 54 - off, 54], fill=200)
        else:
            sd.ellipse([10 + off, 10, 54 + off, 54], fill=200)
        dark = Image.new('RGBA', (64, 64), (10, 10, 30, 255))
        mask = Image.composite(shadow, Image.new('L', (64, 64), 0), img.getchannel('A'))
        img = Image.composite(dark, img, mask)
    return img


def clouds(theme):
    if not os.path.isfile(VANILLA_CLOUDS):
        return None
    src = Image.open(VANILLA_CLOUDS).convert('RGBA')
    t = CLOUD_TINT[theme]
    px = src.load()
    for x in range(src.width):
        for y in range(src.height):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r * t[0] // 255, g * t[1] // 255, b * t[2] // 255, a)
    return src


def season_packs(pack_format_meta):
    os.makedirs(SEASON_DIR, exist_ok=True)
    for theme in THEMES:
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as z:
            meta = json.loads(pack_format_meta)
            meta['pack']['description'] = 'MythosCraft – season sky: ' + theme
            z.writestr('pack.mcmeta', json.dumps(meta))

            def png(path, img):
                b = io.BytesIO()
                img.save(b, 'PNG')
                z.writestr(path, b.getvalue())
            png('assets/minecraft/textures/environment/celestial/sun.png', sun(theme))
            for i, ph in enumerate(MOON_PHASES):
                png(f'assets/minecraft/textures/environment/celestial/moon/{ph}.png', moon(theme, i))
            c = clouds(theme)
            if c:
                png('assets/minecraft/textures/environment/clouds.png', c)
        with open(os.path.join(SEASON_DIR, theme + '.zip'), 'wb') as f:
            f.write(buf.getvalue())


# ------------------------------------------------------------------ sky dome pictures (item models)
def soft(img, radius=2):
    return img.filter(ImageFilter.GaussianBlur(radius))


def aurora():
    img = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(3)
    for x in range(128):
        top = 6 + int(math.sin(x * 0.09) * 6 + math.sin(x * 0.23) * 3)
        for y in range(top, 60):
            t = (y - top) / max(1, 60 - top)
            c = (int(80 + 120 * (1 - t)), int(255 * (1 - t * 0.4)), int(170 + 80 * t))
            a = int(200 * (1 - t) * (0.6 + 0.4 * math.sin(x * 0.5 + rnd.random())))
            d.point((x, y), fill=c + (max(0, a),))
    return soft(img, 1)


def big_moon():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    disc(img, 32, 32, 22, (255, 190, 90), (210, 80, 20), glow=9)
    d = ImageDraw.Draw(img)
    for bx, by in ((12, 18), (50, 12), (46, 50), (18, 52)):
        d.polygon([(bx, by), (bx - 7, by - 4), (bx - 3, by + 1), (bx, by + 4), (bx + 3, by + 1), (bx + 7, by - 4)], fill=(15, 8, 18, 255))
    return img


def cloud_puff(color, extra=None):
    img = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(hash(color) & 1023)
    for _ in range(14):
        x, y, r = rnd.randint(16, 112), rnd.randint(24, 44), rnd.randint(10, 20)
        d.ellipse([x - r, y - r * 0.7, x + r, y + r * 0.7], fill=color + (200,))
    img = soft(img, 2)
    if extra:
        extra(ImageDraw.Draw(img), rnd)
    return img


def petals(d, rnd):
    for _ in range(40):
        x, y = rnd.randint(4, 124), rnd.randint(4, 60)
        d.ellipse([x, y, x + 3, y + 2], fill=(255, 150, 200, 255))


def hearts(d, rnd):
    for _ in range(10):
        x, y = rnd.randint(10, 118), rnd.randint(10, 54)
        d.polygon([(x, y + 4), (x - 4, y), (x - 2, y - 3), (x, y - 1), (x + 2, y - 3), (x + 4, y)], fill=(255, 80, 140, 255))


def leaves_fx(d, rnd):
    for _ in range(36):
        x, y = rnd.randint(4, 124), rnd.randint(4, 60)
        c = rnd.choice([(234, 88, 12), (202, 138, 4), (185, 28, 28)])
        d.polygon([(x, y), (x + 3, y + 1), (x + 1, y + 4)], fill=c + (255,))


def fireworks():
    img = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(11)
    for cx, cy, c in ((30, 26, (250, 204, 21)), (70, 20, (236, 72, 153)), (104, 34, (56, 189, 248)), (52, 44, (168, 85, 247))):
        for a in range(0, 360, 15):
            r = math.radians(a)
            ln = rnd.randint(9, 15)
            d.line([cx, cy, cx + math.cos(r) * ln, cy + math.sin(r) * ln], fill=c + (230,), width=1)
            d.point((cx + math.cos(r) * (ln + 2), cy + math.sin(r) * (ln + 2)), fill=(255, 255, 255, 255))
    return soft(img, 0.6)


def galaxy():
    img = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for x in range(128):
        for y in range(64):
            band = math.exp(-((y - 32 - math.sin(x * 0.05) * 8) / 11) ** 2)
            if band > 0.05:
                d.point((x, y), fill=(int(120 + 80 * band), int(80 + 60 * band), 255, int(150 * band)))
    rnd = random.Random(5)
    for _ in range(90):
        d.point((rnd.randint(0, 127), rnd.randint(0, 63)), fill=(255, 255, 255, 255))
    return soft(img, 0.8)


def sunrays():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    disc(img, 32, 32, 16, (255, 255, 230), (255, 190, 40), glow=14)
    d = ImageDraw.Draw(img)
    for a in range(0, 360, 20):
        r = math.radians(a)
        d.line([32 + math.cos(r) * 22, 32 + math.sin(r) * 22, 32 + math.cos(r) * 31, 32 + math.sin(r) * 31], fill=(255, 210, 80, 200), width=2)
    return img


DOME = {
    'winter': [('aurora', aurora)],
    'halloween': [('moon', big_moon), ('mist', lambda: cloud_puff((120, 60, 160)))],
    'spring': [('blossom', lambda: cloud_puff((255, 210, 230), petals))],
    'summer': [('sun', sunrays), ('cloud', lambda: cloud_puff((255, 255, 255)))],
    'autumn': [('leaves', lambda: cloud_puff((255, 200, 140), leaves_fx))],
    'newyear': [('fireworks', fireworks)],
    'valentine': [('hearts', lambda: cloud_puff((255, 190, 215), hearts))],
    'stars': [('galaxy', galaxy)],
}


def plane_model(tex, w, h):
    # a flat, double-sided, fully lit picture (16 units wide, aspect kept)
    hh = 16 * h / w
    y0 = 8 - hh / 2
    face = {'uv': [0, 0, 16, 16], 'texture': '#0'}
    return {'textures': {'0': tex, 'particle': tex},
            'elements': [{'from': [0, y0, 7.95], 'to': [16, y0 + hh, 8.05], 'light_emission': 15, 'shade': False,
                          'faces': {'north': face, 'south': face}}],
            'display': {'fixed': {'scale': [1, 1, 1]}}}


def dome(out_root):
    tex_dir = os.path.join(out_root, 'assets', 'mythoscraft', 'textures', 'item', 'sky')
    mod_dir = os.path.join(out_root, 'assets', 'mythoscraft', 'models', 'item', 'sky')
    item_dir = os.path.join(out_root, 'assets', 'mythoscraft', 'items')
    for d in (tex_dir, mod_dir, item_dir):
        os.makedirs(d, exist_ok=True)
    names = []
    for theme, parts in DOME.items():
        for part, make in parts:
            name = f'sky_{theme}_{part}'
            img = make()
            img.save(os.path.join(tex_dir, name + '.png'))
            with open(os.path.join(mod_dir, name + '.json'), 'w') as f:
                json.dump(plane_model(f'mythoscraft:item/sky/{name}', *img.size), f)
            with open(os.path.join(item_dir, name + '.json'), 'w') as f:
                json.dump({'model': {'type': 'minecraft:model', 'model': f'mythoscraft:item/sky/{name}'}}, f)
            names.append(name)
    return names


def build(out_root, prev_dir):
    meta = open(os.path.join(ROOT, 'resourcepack', 'pack.mcmeta'), encoding='utf-8').read()
    season_packs(meta)
    names = dome(out_root)
    # preview: suns, moons and dome pictures
    sheet = Image.new('RGBA', (len(THEMES) * 140, 280 + 80), (20, 18, 36, 255))
    for i, t in enumerate(THEMES):
        sheet.alpha_composite(sun(t).resize((128, 128), Image.NEAREST), (i * 140, 0))
        sheet.alpha_composite(moon(t, 0).resize((128, 128), Image.NEAREST), (i * 140, 140))
        parts = DOME.get(t, [])
        if parts:
            p = parts[0][1]().resize((128, 64))
            sheet.alpha_composite(p, (i * 140, 290))
    sheet.save(os.path.join(prev_dir, 'sky_sheet.png'))
    return names

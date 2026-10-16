"""Turns the remaining flat custom items (vanilla "item/generated" sprites, bows, rods) into chunky 3D voxel models:
every pixel becomes a cube, thicker towards the middle (round look), colours reduced to a few clean tones, merged into
runs. Displays stay those of the original parent, so the items are held exactly like before."""
import json
import os
from collections import deque

from PIL import Image

from swatch import SwatchModel

GENERATED = {
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7]},
    'fixed': {'rotation': [0, 180, 0]},
}
DISPLAY = {
    'minecraft:item/generated': GENERATED,
    'minecraft:item/handheld': {
        'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]}, 'fixed': {'rotation': [0, 180, 0]}},
    'minecraft:item/handheld_rod': {
        'thirdperson_righthand': {'rotation': [0, 90, 55], 'translation': [0, 4, 2.5], 'scale': [0.85, 0.85, 0.85]},
        'thirdperson_lefthand': {'rotation': [0, -90, -55], 'translation': [0, 4, 2.5], 'scale': [0.85, 0.85, 0.85]},
        'firstperson_righthand': {'rotation': [0, 90, 25], 'translation': [0, 1.6, 0.8], 'scale': [0.68, 0.68, 0.68]},
        'firstperson_lefthand': {'rotation': [0, -90, -25], 'translation': [0, 1.6, 0.8], 'scale': [0.68, 0.68, 0.68]},
        'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]}, 'fixed': {'rotation': [0, 180, 0]}},
    'minecraft:item/bow': {
        'thirdperson_righthand': {'rotation': [-80, 260, -40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
        'thirdperson_lefthand': {'rotation': [-80, -280, 40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]}, 'fixed': {'rotation': [0, 180, 0]}},
}


def _parent_display(models_dir, parent, seen=0):
    """display of the vanilla parent – also through our own intermediate parents"""
    if parent in DISPLAY or seen > 4:
        return DISPLAY.get(parent, GENERATED)
    if parent and parent.startswith('mythoscraft:'):
        f = os.path.join(models_dir, parent.split(':', 1)[1].split('/', 1)[1] + '.json')
        if os.path.exists(f):
            j = json.load(open(f, encoding='utf-8'))
            return j.get('display') or _parent_display(models_dir, j.get('parent'), seen + 1)
    return GENERATED


def convert(name, model_json, models_dir, textures_root):
    tex = model_json.get('textures', {}).get('layer0')
    if not tex or not tex.startswith('mythoscraft:'):
        return None
    png = os.path.join(textures_root, tex.split(':', 1)[1] + '.png')
    if not os.path.exists(png):
        return None
    img = Image.open(png).convert('RGBA')
    w = img.width
    h = min(img.height, w)                              # animated strips: first frame
    img = img.crop((0, 0, w, h))
    # few clean tones
    alpha = img.getchannel('A')
    q = img.convert('RGB').quantize(colors=12, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert('RGB')
    px = q.load()
    al = alpha.load()
    solid = {(x, y) for y in range(h) for x in range(w) if al[x, y] > 40}
    if not solid:
        return None
    # distance to the outline → thickness (round, thicker middle)
    dist = {}
    dq = deque()
    for (x, y) in solid:
        if any((x + dx, y + dy) not in solid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            dist[(x, y)] = 0
            dq.append((x, y))
    while dq:
        x, y = dq.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            k = (x + dx, y + dy)
            if k in solid and k not in dist:
                dist[k] = dist[(x, y)] + 1
                dq.append(k)
    s = 16 / w
    cap = max(1, 3 * w // 16)                            # thickness steps (same look for 16px and 32px sprites)
    m = SwatchModel(name, display='voxel')
    disp = _parent_display(models_dir, model_json.get('parent'))
    for y in range(h):
        x = 0
        while x < w:
            if (x, y) not in solid:
                x += 1
                continue
            key = (px[x, y], min(dist.get((x, y), 0), cap))
            x1 = x
            while (x1 + 1, y) in solid and (px[x1 + 1, y], min(dist.get((x1 + 1, y), 0), cap)) == key:
                x1 += 1
            col, d = key
            thick = 1.0 + d * s * 0.75                   # 1 unit at the outline … ~3.25 in the middle
            y0 = 16 - (y + 1) * s
            m.add([x * s, y0, 8 - thick / 2], [(x1 + 1) * s, y0 + s, 8 + thick / 2], '#%02x%02x%02x' % col, 'flat')
            x = x1 + 1
    m.extra_display = disp
    return m

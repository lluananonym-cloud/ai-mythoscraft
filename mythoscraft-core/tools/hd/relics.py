"""Relic crate items V1–V4, the spawner booster and the shadow companion – in the same HD style as the other items
(swatch cubes, see boxitem.py / swatch.py). Also: animated textures (chainsaw chain, soul blade, drill bit), the
MinePro641 totem built from his real skin, and the zap tools converted from the drawn sprites in data/sprites.

    python tools/hd/relics.py      writes only these models into tools/hd/out (fast)
    python tools/hd/build.py       includes them in the full build
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from boxitem import BoxItem, tone, handle, pommel, gem, guard, blade, teeth, pick_head, socket, orb  # noqa: E402
from swatch import SwatchModel  # noqa: E402
import sprite3d  # noqa: E402

DATA = os.path.join(HERE, 'data')
ANIM = 'mythoscraft:item/hd/'
G, B, BD = 1.9, 4.2, 1.5


# ====================================================================== animated textures
def anim_frames(name, size, frames, painter, frametime=2):
    strip = Image.new('RGBA', (size, size * frames), (0, 0, 0, 0))
    for f in range(frames):
        tile = Image.new('RGBA', (size, size), (0, 0, 0, 255))
        painter(ImageDraw.Draw(tile), tile, f, frames)
        strip.paste(tile, (0, f * size))
    return name, strip, frametime


def _saw(d, im, f, n):
    """chain links running along the bar: dark links with bright teeth"""
    s = im.width
    d.rectangle([0, 0, s, s], fill=(52, 54, 62, 255))
    step = s // 8
    off = int(f / n * step * 2) % (step * 2)
    for k in range(-2, 10):
        x = k * step + off
        d.rectangle([x, s * 0.28, x + step * 0.55, s * 0.72], fill=(150, 156, 170, 255))
        d.polygon([(x, s * 0.28), (x + step * 0.55, s * 0.28), (x + step * 0.3, s * 0.05)], fill=(226, 232, 240, 255))
        d.polygon([(x, s * 0.72), (x + step * 0.55, s * 0.72), (x + step * 0.3, s * 0.95)], fill=(226, 232, 240, 255))
        d.rectangle([x + step * 0.55, s * 0.44, x + step, s * 0.56], fill=(92, 96, 108, 255))


def _soul(d, im, f, n):
    """dark blade core with cyan soul flames wandering up"""
    s = im.width
    for y in range(s):
        t = y / s
        d.line([(0, y), (s, y)], fill=(int(18 + 20 * t), int(30 + 40 * t), int(52 + 50 * t), 255))
    for k in range(6):
        cy = (k * s / 6 - f / n * s) % s
        cx = s * (0.3 + 0.4 * ((k * 37) % 10) / 10)
        r = s * 0.12
        d.ellipse([cx - r, cy - r * 1.6, cx + r, cy + r], fill=(94, 234, 212, 255))
        d.ellipse([cx - r * 0.5, cy - r * 0.8, cx + r * 0.5, cy + r * 0.4], fill=(224, 255, 250, 255))


def _drill(d, im, f, n):
    """spiral flutes turning on the drill bit"""
    s = im.width
    d.rectangle([0, 0, s, s], fill=(120, 126, 140, 255))
    off = f / n * s / 2
    for k in range(-2, 6):
        y0 = k * s / 4 + off
        d.polygon([(0, y0), (s, y0 + s / 4), (s, y0 + s / 4 + s / 10), (0, y0 + s / 10)], fill=(214, 222, 236, 255))
        d.polygon([(0, y0 + s / 10), (s, y0 + s / 4 + s / 10), (s, y0 + s / 4 + s / 6), (0, y0 + s / 6)], fill=(70, 74, 86, 255))


ANIMS = [anim_frames('anim_saw', 32, 8, _saw, 1), anim_frames('anim_soulblade', 32, 8, _soul, 2),
         anim_frames('anim_drillbit', 32, 8, _drill, 1)]


def save_anims(out):
    d = os.path.join(out, 'assets', 'mythoscraft', 'textures', 'item', 'hd')
    os.makedirs(d, exist_ok=True)
    for name, strip, ft in ANIMS:
        strip.save(os.path.join(d, name + '.png'))
        with open(os.path.join(d, name + '.png.mcmeta'), 'w', encoding='utf-8') as f:
            json.dump({'animation': {'frametime': ft, 'interpolate': True}}, f)


def animated(it_or_model, cube, name):
    cube.anim = ANIM + name
    return cube


# ====================================================================== V1
def sort_wand():
    it = BoxItem('sort_wand')
    pommel(it, 0.9, 2.0, '#e0a628')
    handle(it, 1.6, 13.5, 1.9, '#5a3a20', '#e0a628', every=1.6, band=0.5)
    socket(it, 14.0, 1.4, 2.8, '#e0a628')
    # three stacked, neatly sorted little crates
    for i, col in enumerate(('#2f9a3c', '#2f62d8', '#d8313a')):
        u = 15.6 + i * 2.2
        it.block(u, 0, 2.2, col, 'matte', d=2.4, name='sorted box')
        it.bar((u - 1.15, 0), (u - 0.95, 0), 2.35, 2.55, tone(col, -0.35), name='box rim')
    gem(it, 22.2, 0, 1.3, '#f6d23c')
    return it


def infinite_bucket():
    m = SwatchModel('infinite_bucket', 'trophy')
    iron, dark, water = '#a7b0bd', '#5c6574', '#2f62d8'
    m.add((3, 0, 3), (13, 1.2, 13), dark, 'metal', name='bottom')
    m.add((2.6, 1.2, 2.6), (13.4, 10.5, 13.4), iron, 'metal', name='bucket')
    m.add((2.2, 9.6, 2.2), (13.8, 11, 13.8), tone(iron, 0.25), 'metal', name='rim')
    m.add((3.4, 10.4, 3.4), (12.6, 10.9, 12.6), water, 'glow', glow=True, name='water')
    m.add((5, 10.9, 5), (11, 11.2, 11), '#9fe6ff', 'glow', glow=True, name='water shine')
    for y in (3.2, 7.0):
        m.add((2.4, y, 2.4), (13.6, y + 0.8, 13.6), dark, 'metal', name='band')
    # infinity rune on the front
    m.add((5.2, 4.6, 2.2), (7.8, 6.2, 2.6), '#46d4e8', 'glow', glow=True, name='rune')
    m.add((8.2, 4.6, 2.2), (10.8, 6.2, 2.6), '#46d4e8', 'glow', glow=True, name='rune')
    m.add((7.4, 5.1, 2.1), (8.6, 5.7, 2.5), '#e0ffff', 'glow', glow=True, name='rune knot')
    for x0, x1 in ((1.4, 2.6), (13.4, 14.6)):
        m.add((x0, 8, 7.4), (x1, 9.2, 8.6), dark, 'metal', name='ear')
    m.add((1.8, 13.8, 7.6), (14.2, 14.4, 8.4), dark, 'metal', name='handle')
    m.add((1.8, 9.2, 7.6), (2.4, 14.4, 8.4), dark, 'metal')
    m.add((13.6, 9.2, 7.6), (14.2, 14.4, 8.4), dark, 'metal')
    return m


# ====================================================================== V2
def vein_pickaxe():
    it = BoxItem('vein_pickaxe')
    pommel(it, 0.9, 2.0, '#c8743c')
    handle(it, 1.6, 17.5, G, '#5a3a20', '#c8743c', every=1.5, band=0.5)
    socket(it, 18.2, 2.0, 3.0, '#3a2d52')
    pick_head(it, 18.4, 9.0, 2.4, '#4a4458', '#ff9a2a', d=1.8, droop=2.4)
    # glowing ore veins running through the head
    for (a, b) in (((18.4, -6.5), (18.0, -2.5)), ((18.0, -2.5), (18.6, 1.0)), ((18.6, 1.0), (18.0, 5.6))):
        it.bar(a, b, 0.5, 2.0, '#ff9a2a', 'glow', glow=True, name='vein')
    gem(it, 18.4, 0, 1.6, '#2ed27a')
    return it


def repair_charm():
    m = SwatchModel('repair_charm', 'trophy')
    iron, dark = '#8d99aa', '#3a3f4c'
    m.add((5, 0, 5), (11, 1.5, 11), dark, 'metal', name='foot')
    m.add((6.5, 1.5, 6.5), (9.5, 4.5, 9.5), iron, 'metal', name='waist')
    m.add((3, 4.5, 5.5), (13, 7.5, 10.5), iron, 'metal', name='anvil top')
    m.add((0.8, 5.4, 6.6), (3, 7.2, 9.4), iron, 'metal', name='horn')
    m.add((13, 5.8, 6.8), (14.4, 7.5, 9.2), iron, 'metal', name='heel')
    # green repair cross floating above, glowing
    m.add((7.2, 9, 7.6), (8.8, 14, 8.4), '#2ed27a', 'glow', glow=True, name='cross')
    m.add((5.5, 10.7, 7.6), (10.5, 12.3, 8.4), '#2ed27a', 'glow', glow=True, name='cross')
    m.add((7.6, 11.1, 7.4), (8.4, 11.9, 8.6), '#d6ffe8', 'glow', glow=True, name='cross core')
    m.add((7.6, 7.5, 7.8), (8.4, 9, 8.2), '#c8d0dc', 'metal', name='chain')
    return m


# ====================================================================== V3
def paint_brush():
    it = BoxItem('paint_brush')
    it.bar((0.4, 0), (1.6, 0), 1.6, 1.6, '#d8313a', name='handle end')
    handle(it, 1.4, 12.5, 1.7, '#e6dcc0', '#d8313a', every=2.0, band=0.6)
    it.bar((12.5, 0), (15.0, 0), 2.8, 2.0, '#c8d0dc', 'metal', name='ferrule')
    it.bar((14.6, 0), (15.2, 0), 3.0, 2.2, '#8d99aa', 'metal', name='ferrule rim')
    # bristles in stripes of colour, wider towards the tip
    cols = ['#d8313a', '#f07a1c', '#f6d23c', '#2f9a3c', '#2f62d8', '#7c3ad0']
    for i, c in enumerate(cols):
        v = -2.0 + i * 0.8
        it.bar((15.2, v * 0.75), (20.4, v), 0.85, 1.8, c, name='bristles')
    it.bar((20.0, -0.6), (21.6, -0.2), 1.4, 1.6, '#7c3ad0', 'glow', glow=True, name='paint tip')
    it.block(19.0, 3.2, 0.9, '#f6d23c', 'glow', glow=True, name='drop')
    return it


def concrete_mixer():
    m = SwatchModel('concrete_mixer', 'trophy')
    frame, drum, stripe, wheel = '#3a3f4c', '#a7b0bd', '#f07a1c', '#22232b'
    m.add((2, 0, 4), (14, 1.2, 12), frame, 'metal', name='base')
    for x in (2.6, 12.4):
        m.add((x, 0, 2.6), (x + 1.0, 2.4, 4.6), wheel, name='wheel')
        m.add((x, 0, 11.4), (x + 1.0, 2.4, 13.4), wheel, name='wheel')
    m.add((7, 1.2, 7), (9, 6, 9), frame, 'metal', name='stand')
    # the drum, tilted towards the viewer
    rot = {'origin': [8, 9.5, 8], 'axis': 'x', 'angle': -22.5}
    m.add((4, 6, 4), (12, 13, 12), drum, 'metal', rot=rot, name='drum')
    m.add((5, 13, 5), (11, 14.5, 11), tone(drum, -0.2), 'metal', rot=rot, name='drum neck')
    m.add((6, 14.5, 6), (10, 15.2, 10), '#5c6574', 'metal', rot=rot, name='opening')
    for y in (7.6, 10.2):
        m.add((3.8, y, 3.8), (12.2, y + 0.9, 12.2), stripe, rot=rot, name='stripe')
    m.add((6.6, 14.6, 6.6), (9.4, 15.4, 9.4), '#9aa0aa', 'matte', rot=rot, name='concrete')
    m.add((12, 8.5, 7.4), (14.8, 9.3, 8.6), frame, 'metal', name='crank')
    m.add((14.2, 6.8, 7.4), (14.8, 9.3, 8.6), frame, 'metal')
    m.add((14.0, 6.2, 7.2), (15.0, 7.0, 8.8), stripe, name='crank grip')
    return m


def soul_sword():
    it = BoxItem('soul_sword')
    pommel(it, 0.9, 2.2, '#1c1626')
    it.block(0.9, 0, 1.2, '#5eead4', 'glow', glow=True, name='pommel soul')
    handle(it, 1.6, 6.4, G, '#1c1626', '#2dd4bf', every=1.3, band=0.55)
    guard(it, 7.0, 4.8, 2.0, '#2a2236', d=2.6, curl=1.6)
    # skull in the middle of the guard
    it.block(7.0, 0, 2.6, '#e6dcc0', d=2.9, name='skull')
    it.block(7.3, -0.6, 0.7, '#120c1c', d=3.0, z=0.05, name='eye')
    it.block(7.3, 0.6, 0.7, '#120c1c', d=3.0, z=0.05, name='eye')
    blade(it, 8.0, 22.0, 4.4, '#1f2d3a', edge='#5eead4', ridge='#0f1a24', d=BD, tip=3.6, glow_edge=True)
    core = it.bar((9.0, 0), (17.6, 0), 1.6, BD + 0.3, '#2dd4bf', 'glow', glow=True, name='soul core')
    animated(it, core, 'anim_soulblade')
    teeth(it, 9.4, 17.0, 1, 2.0, '#2a3a4a', every=2.4, size=1.3, d=1.0)
    return it


def dwarf_hat():
    m = SwatchModel('dwarf_hat', 'hat')
    red, dark, white, gold = '#c0282f', '#7e1720', '#f4f1ea', '#e0a628'
    m.add((-0.8, 15.6, -0.8), (16.8, 17.6, 16.8), white, name='fur brim')
    y = 17.6
    rings = ((0.6, 15.4, 2.6), (2.2, 13.8, 2.6), (3.6, 12.4, 2.6), (4.8, 11.2, 2.6), (5.8, 10.2, 2.4), (6.6, 9.4, 2.2))
    for i, (a, b, h) in enumerate(rings):
        # every ring smaller and leaning back – a soft, floppy gnome cone
        m.add((a, y, a + 0.7 * i), (b, y + h, b + 0.7 * i), red if i % 2 == 0 else tone(red, 0.1), name='cone')
        y += h
    # the floppy tip hangs to the back
    m.add((7.0, 32.0, 10.4), (9.0, 33.6, 14.4), dark, name='tip')
    m.add((7.2, 30.4, 14.2), (8.8, 32.6, 15.8), dark, name='tip end')
    m.add((7.0, 28.8, 15.0), (9.0, 30.6, 17.0), gold, 'metal', name='bell')
    m.add((7.6, 28.2, 15.6), (8.4, 29.0, 16.4), tone(gold, -0.3), 'metal', name='bell clapper')
    return m


def growth_totem():
    m = SwatchModel('growth_totem', 'trophy')
    gold, green, wood = '#e0a628', '#2ed27a', '#7a4a26'
    m.add((4.5, 0, 4.5), (11.5, 1.5, 11.5), wood, name='base')
    m.add((5.5, 1.5, 5.5), (10.5, 9.5, 10.5), gold, 'metal', name='body')
    m.add((4.6, 9.5, 4.6), (11.4, 14.5, 11.4), gold, 'metal', name='head')
    m.add((5.6, 11.4, 4.3), (7.2, 12.8, 4.7), green, 'glow', glow=True, name='eye')
    m.add((8.8, 11.4, 4.3), (10.4, 12.8, 4.7), green, 'glow', glow=True, name='eye')
    m.add((6.6, 10.0, 4.3), (9.4, 10.6, 4.7), '#5a3a20', name='mouth')
    for x0, x1 in ((2.4, 5.5), (10.5, 13.6)):
        m.add((x0, 5.5, 7), (x1, 7, 9), gold, 'metal', name='arm')
    # an arrow growing upward out of the head
    m.add((7.4, 14.5, 7.4), (8.6, 18, 8.6), green, 'glow', glow=True, name='growth')
    m.add((6.0, 17, 7.4), (10.0, 18.2, 8.6), green, 'glow', glow=True, name='arrow')
    m.add((6.8, 18.2, 7.4), (9.2, 19.2, 8.6), green, 'glow', glow=True, name='arrow')
    m.add((7.5, 19.2, 7.5), (8.5, 20, 8.5), '#d6ffe8', 'glow', glow=True, name='arrow tip')
    for x, z in ((4.8, 4.8), (10.2, 4.8), (4.8, 10.2), (10.2, 10.2)):
        m.add((x, 1.5, z), (x + 1, 3, z + 1), '#58b83a', name='leaf')
    return m


# ====================================================================== V4
def power_drill():
    it = BoxItem('power_drill')
    body, dark, batt = '#f07a1c', '#22232b', '#3a3f4c'
    # pistol grip along the item axis, battery at the bottom
    it.bar((0.0, 0), (2.6, 0), 4.4, 4.0, batt, 'metal', name='battery')
    it.bar((0.4, 0), (2.2, 0), 4.6, 4.2, dark, name='battery band')
    it.block(1.3, 2.4, 0.7, '#2ed27a', 'glow', glow=True, name='charge led')
    it.bar((2.6, 0), (9.0, 0), 2.6, 2.6, dark, name='grip')
    it.bar((3.2, 0), (8.4, 0), 2.8, 2.2, body, name='grip shell')
    it.bar((7.2, 1.3), (8.2, 2.0), 1.0, 1.4, '#d8313a', name='trigger')
    # motor housing across the grip
    it.bar((9.6, -3.6), (9.6, 4.6), 4.6, 3.8, body, name='housing')
    it.bar((9.6, -3.9), (9.6, -2.6), 4.8, 4.0, dark, name='housing back')
    for k in range(3):
        it.bar((8.0 + k * 1.1, -3.0), (8.6 + k * 1.1, -3.0), 0.4, 4.1, '#3a3f4c', name='vents')
    it.bar((9.6, 4.6), (9.6, 6.4), 3.2, 3.2, '#5c6574', 'metal', name='chuck')
    it.bar((9.6, 6.4), (9.6, 7.2), 2.4, 2.4, '#8d99aa', 'metal', name='chuck tip')
    bit = it.bar((9.6, 7.2), (9.6, 13.6), 1.1, 1.1, '#c8d0dc', 'metal', name='drill bit')
    animated(it, bit, 'anim_drillbit')
    it.bar((9.6, 13.4), (9.6, 14.4), 0.6, 0.6, '#e2e8f0', 'metal', name='bit tip')
    return it


def mega_chainsaw():
    it = BoxItem('mega_chainsaw')
    body, dark, bar = '#f6d23c', '#22232b', '#5c6574'
    # rear handle loop
    it.bar((0.4, -2.2), (0.4, 2.2), 1.2, 2.0, dark, name='rear handle')
    it.bar((0.4, -2.2), (3.6, -2.2), 1.2, 2.0, dark, name='rear handle')
    it.bar((0.4, 2.2), (3.6, 2.2), 1.2, 2.0, dark, name='rear handle')
    # engine block
    it.bar((3.4, 0), (10.4, 0), 6.4, 5.0, body, name='engine')
    it.bar((3.4, -3.4), (10.4, -3.4), 0.8, 5.2, dark, name='engine edge')
    it.bar((4.4, 3.4), (9.6, 3.4), 1.2, 4.6, '#d8313a', name='fuel cap side')
    for k in range(4):
        it.bar((5.0 + k * 1.3, 0), (5.5 + k * 1.3, 0), 5.0, 5.2, tone(body, -0.25), name='cooling fins')
    it.block(7.0, -1.0, 1.4, '#d8313a', d=5.4, name='start button')
    # front handle bar arching over the engine
    it.bar((9.2, -4.0), (9.2, 4.0), 1.1, 1.4, dark, z=2.8, name='front handle')
    it.bar((10.4, 0), (11.4, 0), 3.2, 3.6, dark, 'metal', name='clutch cover')
    # long guide bar with the running chain
    it.bar((11.4, 0), (22.6, 0), 3.4, 0.9, bar, 'metal', name='guide bar')
    for s in (-1, 1):
        ch = it.bar((11.6, s * 1.85), (22.0, s * 1.85), 0.5, 1.2, '#c8d0dc', 'metal', name='chain')
        animated(it, ch, 'anim_saw')
    nose = it.bar((22.0, -1.85), (22.0, 1.85), 0.7, 1.2, '#c8d0dc', 'metal', name='chain nose')
    animated(it, nose, 'anim_saw')
    it.bar((14.0, 0), (20.0, 0), 1.2, 1.0, tone(bar, 0.3), 'metal', name='bar logo')
    return it


def blossom_crown():
    m = SwatchModel('blossom_crown', 'hat')
    vine, leaf, leaf2, petal, petal2, heart = '#5a3a20', '#58b83a', '#2f9a3c', '#f9a8d4', '#fbcfe8', '#fde047'
    for a, b in (((-0.8, 15, -0.8), (16.8, 16.8, 0.6)), ((-0.8, 15, 15.4), (16.8, 16.8, 16.8)),
                 ((-0.8, 15, 0.6), (0.6, 16.8, 15.4)), ((15.4, 15, 0.6), (16.8, 16.8, 15.4))):
        m.add(a, b, vine, name='wreath')
        m.add((a[0] + 0.2, 16.6, a[2] + 0.2), (b[0] - 0.2, 17.3, b[2] - 0.2), leaf2, name='wreath leaves')
    spots = [(x, -1.6) for x in (0.0, 4.4, 8.8, 13.2)] + [(x, 16.0) for x in (0.0, 4.4, 8.8, 13.2)] + \
            [(-1.6, z) for z in (4.4, 8.8)] + [(16.0, z) for z in (4.4, 8.8)]
    for i, (x, z) in enumerate(spots):
        size = 3.0 if i % 2 == 0 else 2.4
        y = 16.6 + (i % 3) * 0.5
        col = petal if i % 2 == 0 else petal2
        m.add((x, y, z), (x + size, y + size * 0.6, z + 1.6), col, name='blossom')
        m.add((x + size * 0.35, y + size * 0.15, z - 0.15), (x + size * 0.65, y + size * 0.45, z + 1.75), heart, 'glow', glow=True,
              name='blossom heart')
        m.add((x + 0.4, y - 0.6, z + 0.2), (x + size - 0.4, y, z + 1.4), leaf, name='leaf')
    return m


def bark_hand():
    """big wooden glove-fist with bark plates and a mossy cuff – held in the off hand"""
    m = SwatchModel('bark_hand', 'trophy')
    bark, dark, light, moss = '#7a4a26', '#4a2c16', '#a8743c', '#58b83a'
    m.add((4, 0, 5), (12, 4, 11), moss, name='cuff')
    m.add((3.6, 3.2, 4.6), (12.4, 4.4, 11.4), dark, name='cuff rim')
    m.add((3.2, 4, 4.2), (12.8, 11.5, 11.8), bark, name='palm')
    for i in range(4):
        x = 3.4 + i * 2.35
        m.add((x, 11.5, 4.0), (x + 2.2, 15.5, 8.6), bark if i % 2 == 0 else tone(bark, 0.08), name='finger')
        m.add((x, 14.0, 3.6), (x + 2.2, 15.5, 8.8), light, name='knuckle')
    m.add((12.6, 6.5, 4.2), (15.4, 10.5, 8.4), bark, name='thumb')
    m.add((13.4, 10.5, 4.6), (15.6, 12.4, 8.0), light, name='thumb tip')
    for y in (5.5, 8.0, 10.2):
        m.add((3.0, y, 4.0), (13.0, y + 0.7, 4.4), dark, name='bark ridge')
    m.add((5.5, 6.0, 3.8), (8.0, 7.6, 4.2), moss, name='moss')
    return m


SKIN = None


def _skin():
    global SKIN
    if SKIN is None:
        SKIN = Image.open(os.path.join(DATA, 'minepro641_skin.png')).convert('RGBA')
    return SKIN


def _part(base, overlay, w, h, rot=0):
    """skin face (base + its overlay layer composited)"""
    s = _skin()
    img = s.crop((base[0], base[1], base[0] + w, base[1] + h))
    if overlay:
        ov = s.crop((overlay[0], overlay[1], overlay[0] + w, overlay[1] + h))
        img = Image.alpha_composite(img, ov)
    if rot:
        img = img.rotate(rot, expand=True)
    return img


def minepro_totem():
    """MinePro641 sitting on a golden totem base – built from his real skin (head, body, arms, bent legs)."""
    m = SwatchModel('minepro_totem', 'companion')
    gold, purple = '#e0a628', '#7c3ad0'
    m.add((2.5, 0, 2.5), (13.5, 1.6, 13.5), gold, 'metal', name='base')
    m.add((3, 1.6, 3), (13, 2.4, 13), purple, 'glow', glow=True, name='base glow')
    m.add((3.4, 2.4, 3.4), (12.6, 4.4, 12.6), gold, 'metal', name='seat')

    def cube(frm, to, faces, name):
        c = m.add(frm, to, '#222222', 'matte', name=name)
        for f, img in faces.items():
            c.faces[f] = img
        return c

    # head 4×4×4 (front = north)
    hx, hy, hz = 6, 13.4, 6.4
    cube((hx, hy, hz), (hx + 4, hy + 4, hz + 4), {
        'north': _part((8, 8), (40, 8), 8, 8), 'south': _part((24, 8), (56, 8), 8, 8),
        'east': _part((0, 8), (32, 8), 8, 8), 'west': _part((16, 8), (48, 8), 8, 8),
        'up': _part((8, 0), (40, 0), 8, 8), 'down': _part((16, 0), (48, 0), 8, 8)}, 'head')
    # body 4×6×2
    cube((6, 7.4, 7.4), (10, 13.4, 9.4), {
        'north': _part((20, 20), (20, 36), 8, 12), 'south': _part((32, 20), (32, 36), 8, 12),
        'east': _part((16, 20), (16, 36), 4, 12), 'west': _part((28, 20), (28, 36), 4, 12),
        'up': _part((20, 16), (20, 32), 8, 4), 'down': _part((28, 16), (28, 32), 8, 4)}, 'body')
    # arms 2×6×2 resting on the knees (tilted forward)
    for x0, base, ov, name in ((10, (44, 20), (44, 36), 'right arm'), (4, (36, 52), (52, 52), 'left arm')):
        c = cube((x0, 7.6, 7.2), (x0 + 2, 13.4, 9.2), {
            'north': _part(base, ov, 4, 12), 'south': _part((base[0] + 8, base[1]), (ov[0] + 8, ov[1]), 4, 12),
            'east': _part((base[0] - 4, base[1]), (ov[0] - 4, ov[1]), 4, 12),
            'west': _part((base[0] + 4, base[1]), (ov[0] + 4, ov[1]), 4, 12),
            'up': _part((base[0], base[1] - 4), (ov[0], ov[1] - 4), 4, 4),
            'down': _part((base[0] + 4, base[1] - 4), (ov[0] + 4, ov[1] - 4), 4, 4)}, name)
        c.rot = {'origin': [x0 + 1, 13.4, 8.2], 'axis': 'x', 'angle': 22.5}
    # thighs lying forward on the seat, shins hanging down in front
    for x0, base, ov, name in ((8, (4, 20), (4, 36), 'right leg'), (6, (20, 52), (4, 52), 'left leg')):
        cube((x0, 5.4, 3.4), (x0 + 2, 7.4, 9.4), {
            'up': _part(base, ov, 4, 12, rot=90), 'down': _part((base[0] + 8, base[1]), (ov[0] + 8, ov[1]), 4, 12, rot=90),
            'east': _part((base[0] - 4, base[1]), (ov[0] - 4, ov[1]), 4, 12, rot=90),
            'west': _part((base[0] + 4, base[1]), (ov[0] + 4, ov[1]), 4, 12, rot=90),
            'north': _part((base[0], base[1] - 4), (ov[0], ov[1] - 4), 4, 4),
            'south': _part((base[0], base[1] - 4), (ov[0], ov[1] - 4), 4, 4)}, name + ' thigh')
        cube((x0, 2.4, 3.4), (x0 + 2, 5.4, 5.4), {
            'north': _part((base[0], base[1] + 6), (ov[0], ov[1] + 6), 4, 6),
            'south': _part((base[0] + 8, base[1] + 6), (ov[0] + 8, ov[1] + 6), 4, 6),
            'east': _part((base[0] - 4, base[1] + 6), (ov[0] - 4, ov[1] + 6), 4, 6),
            'west': _part((base[0] + 4, base[1] + 6), (ov[0] + 4, ov[1] + 6), 4, 6),
            'down': _part((base[0] + 4, base[1] - 4), (ov[0] + 4, ov[1] - 4), 4, 4),
            'up': _part((base[0] + 4, base[1] - 4), (ov[0] + 4, ov[1] - 4), 4, 4)}, name + ' shin')
    # little crown – he is the owner
    m.add((5.8, 17.4, 6.2), (10.2, 18.2, 10.6), gold, 'metal', name='crown band')
    for x, z in ((5.8, 6.2), (9.4, 6.2), (5.8, 9.8), (9.4, 9.8), (7.6, 6.0)):
        m.add((x, 18.2, z), (x + 0.8, 19.4, z + 0.8), gold, 'metal', name='crown point')
    m.add((7.6, 17.6, 6.0), (8.4, 18.4, 6.2), '#d8313a', 'glow', glow=True, name='crown gem')
    return m


# ====================================================================== spawner booster & companion
def spawner_booster():
    m = SwatchModel('spawner_booster', 'trophy')
    cage, core, cap = '#2a2f3a', '#a3e635', '#7c3aed'
    m.add((5, 0, 5), (11, 1.4, 11), cap, 'metal', name='cap')
    m.add((5, 13.6, 5), (11, 15, 11), cap, 'metal', name='cap')
    for x, z in ((5, 5), (10, 5), (5, 10), (10, 10)):
        m.add((x, 1.4, z), (x + 1, 13.6, z + 1), cage, 'metal', name='cage bar')
    m.add((6.2, 1.4, 6.2), (9.8, 13.6, 9.8), core, 'glow', glow=True, name='core')
    m.add((7.2, 2.4, 7.2), (8.8, 12.6, 8.8), '#ecfccb', 'glow', glow=True, name='core light')
    # two speed chevrons on the front
    for y in (5.0, 8.4):
        m.add((6.4, y, 5.6), (9.6, y + 0.9, 6.0), '#fde047', 'glow', glow=True, name='chevron')
        m.add((7.2, y + 0.9, 5.6), (8.8, y + 1.8, 6.0), '#fde047', 'glow', glow=True, name='chevron tip')
    m.add((7.4, 15, 7.4), (8.6, 16.4, 8.6), '#fde047', 'glow', glow=True, name='spark')
    return m


def companion_shadow():
    """the shadow cube (drawn by the server owner): dark cube body, two white eyes, little feet, smoke on top"""
    m = SwatchModel('companion_shadow', 'companion')
    body, edge, eye, foot, smoke, smoke2 = '#1d1428', '#3a2a52', '#f4f1ff', '#140e1c', '#d6dbe4', '#f2f0f8'
    m.add((3, 3, 3), (13, 13, 13), body, name='body')
    for (a, b) in (((3, 12.6, 3), (13, 13.2, 3.4)), ((3, 12.6, 3), (3.4, 13.2, 13)), ((12.6, 12.6, 3), (13, 13.2, 13))):
        m.add(a, b, edge, name='edge highlight')
    m.add((4.6, 6.6, 2.6), (6.4, 9, 3.0), eye, 'glow', glow=True, name='eye')
    m.add((8.0, 6.6, 2.6), (9.8, 9, 3.0), eye, 'glow', glow=True, name='eye')
    for x, z in ((3.6, 3.6), (9.6, 3.6), (3.6, 9.6), (9.6, 9.6)):
        m.add((x, 0, z), (x + 2.8, 3, z + 2.8), foot, name='foot')
    m.add((7, 13, 7), (9, 14.6, 9), '#120c1c', name='tuft')
    m.add((7.6, 14.6, 7.6), (8.4, 16, 8.4), '#2a2236', name='tuft tip')
    for (x, y, z, s, c) in ((5, 15.2, 6, 1.6, smoke), (9.6, 15.8, 8.4, 1.4, smoke2), (6.8, 17.2, 9.2, 1.2, smoke),
                            (10.4, 17.6, 5.4, 1.0, smoke2), (4.2, 17.0, 9.8, 1.0, smoke2), (8.2, 18.8, 6.8, 0.9, smoke)):
        m.add((x, y, z), (x + s, y + s, z + s), c, name='smoke')
    return m


BOX = {f.__name__: f for f in (sort_wand, infinite_bucket, vein_pickaxe, repair_charm, paint_brush, concrete_mixer, soul_sword,
                                dwarf_hat, growth_totem, power_drill, mega_chainsaw, blossom_crown, bark_hand, minepro_totem,
                                spawner_booster, companion_shadow)}
SPRITES = {'zap_pickaxe': 'minecraft:item/handheld', 'zap_sword': 'minecraft:item/handheld',
           'zap_rod': 'minecraft:item/handheld_rod', 'zap_rod_cast': 'minecraft:item/handheld_rod'}


def reduce_keep_lines(img):
    """128 → 64 px: every 2×2 block becomes its darkest pixel if that one is an outline (dark), otherwise the average
    of its solid pixels – thin black lightning lines survive, the white areas stay clean"""
    w, h = img.width // 2, img.height // 2
    src = img.load()
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    dst = out.load()
    for y in range(h):
        for x in range(w):
            px = [src[x * 2 + dx, y * 2 + dy] for dx in (0, 1) for dy in (0, 1)]
            solid = [p for p in px if p[3] > 60]
            if len(solid) < 2 and not any(p[3] > 60 and (p[0] + p[1] + p[2]) < 240 for p in px):
                continue
            dark = min(solid, key=lambda p: p[0] + p[1] + p[2])
            if dark[0] + dark[1] + dark[2] < 240:
                dst[x, y] = dark[:3] + (255,)
            else:
                n = len(solid)
                dst[x, y] = (sum(p[0] for p in solid) // n, sum(p[1] for p in solid) // n, sum(p[2] for p in solid) // n, 255)
    return out


def sprite_models():
    """the drawn zap tools: 128 px sprites → reduced to 64 px (alpha-aware) → chunky 3D voxels like every sprite item"""
    out = {}
    tmp_root = os.path.join(HERE, 'out_sprites_tmp')
    tex_dir = os.path.join(tmp_root, 'item')
    os.makedirs(tex_dir, exist_ok=True)
    for name, parent in SPRITES.items():
        src = os.path.join(DATA, 'sprites', name.replace('_cast', '') + '.png')
        img = Image.open(src).convert('RGBA')
        small = reduce_keep_lines(img)
        small.save(os.path.join(tex_dir, name + '.png'))
        m = sprite3d.convert(name, {'parent': parent, 'textures': {'layer0': 'mythoscraft:item/' + name}}, tmp_root, tmp_root)
        if name == 'zap_rod_cast' and m:
            m.name = name
        out[name] = m
    import shutil
    shutil.rmtree(tmp_root, ignore_errors=True)
    return out


def build(out, prev=None, render=None):
    save_anims(out)
    done = set()
    for name, fn in BOX.items():
        r = fn()
        m = r.model() if isinstance(r, BoxItem) else r
        m.save(out, 'hd')
        done.add(name)
        if render and prev:
            disp = getattr(m, 'display', 'voxel')
            if disp == 'voxel':
                render(m, os.path.join(prev, 'item_' + name + '.png'), size=260, yaw=20, pitch=14, zoom=1.45)
            else:
                render(m, os.path.join(prev, name + '.png'), size=300)
    for name, m in sprite_models().items():
        if m:
            m.save(out, 'hd')
            done.add(name)
            if render and prev:
                render(m, os.path.join(prev, 'item_' + name + '.png'), size=220, yaw=20, pitch=14, zoom=1.45)
    return done


if __name__ == '__main__':
    import hd
    o = os.path.join(HERE, 'out')
    p = os.path.join(HERE, 'preview')
    os.makedirs(p, exist_ok=True)
    names = build(o, p, hd.render)
    print('relic items', len(names), sorted(names))

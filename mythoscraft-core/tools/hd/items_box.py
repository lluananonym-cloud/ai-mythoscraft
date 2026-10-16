"""Every held custom item as a hand-made style box model (see boxitem.py). Each item has its own silhouette."""
import math

from boxitem import (BoxItem, tone, handle, pommel, gem, guard, blade, teeth, pick_head, axe_head, socket, orb, shard,
                     flame, sparkles)
from swatch import SwatchModel

G, B, BD = 1.9, 4.2, 1.5       # grip thickness, blade width, blade thickness
ITEMS = {}


def item(fn):
    ITEMS[fn.__name__] = fn
    return fn


# ====================================================================== extra parts
def arc(it, cu, cv, r, a0, a1, w0, w1, d, col, style='metal', n=7, glow=False, name='arc'):
    pts = [(cu + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cv + r * math.sin(math.radians(a0 + (a1 - a0) * i / n)))
           for i in range(n + 1)]
    for i in range(n):
        w = w0 + (w1 - w0) * i / max(1, n - 1)
        it.bar(pts[i], pts[i + 1], w, d, col, style, glow=glow, name=name)


def star(it, u, v, s, col, style='glow', glow=True, z=0.0, d=None):
    d = d or s * 0.6
    it.block(u, v, s * 0.7, col, style, d=d, z=z, glow=glow, name='star')
    for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        it.bar((u, v), (u + du * s, v + dv * s), s * 0.38, d * 0.8, col, style, z=z, glow=glow, name='star ray')
    for du, dv in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        it.bar((u, v), (u + du * s * 0.55, v + dv * s * 0.55), s * 0.28, d * 0.7, col, style, z=z, glow=glow, name='star ray')


def bow(it, u, col, s=1.0):
    knot = tone(col, -0.2)
    for sv in (-1, 1):
        it.bar((u, sv * 0.5 * s), (u + 1.5 * s, sv * 2.4 * s), 1.1 * s, 1.0 * s, col, name='bow loop')
        it.bar((u + 1.5 * s, sv * 2.4 * s), (u + 0.2 * s, sv * 3.1 * s), 1.0 * s, 1.0 * s, col, name='bow loop')
        it.bar((u - 0.2 * s, sv * 0.6 * s), (u - 2.4 * s, sv * 1.8 * s), 0.9 * s, 0.7 * s, col, name='bow tail')
    it.block(u, 0, 1.4 * s, knot, d=1.6 * s, name='bow knot')


def stripes_handle(it, u0, u1, w, a, b, every=1.0):
    u, k = u0, 0
    while u < u1 - 0.01:
        e = min(u1, u + every)
        it.bar((u, 0), (e, 0), w, w, a if k % 2 == 0 else b, name='candy')
        u, k = e, k + 1


def leaf(it, u, v, du, dv, col, w=1.1):
    it.bar((u, v), (u + du, v + dv), w, 0.5, col, name='leaf')
    it.bar((u + du * 0.3, v + dv * 0.3), (u + du * 0.75, v + dv * 0.75), w * 1.5, 0.45, tone(col, 0.15), name='leaf')


def std_sword(it, metal, grip, wrap, gem_col, u_guard=7.0, half=4.6, pom=2.4):
    pommel(it, 0.9, pom, metal)
    handle(it, 1.6, u_guard - 0.6, G, grip, wrap, every=1.4, band=0.55)
    guard(it, u_guard, half, 1.8, metal, d=2.4, curl=1.3)
    if gem_col:
        gem(it, u_guard, 0, 1.5, gem_col)


# ====================================================================== swords
@item
def dragon_blade():
    it = BoxItem('dragon_blade')
    pommel(it, 0.9, 2.2, '#e9b52c')
    handle(it, 1.6, 6.4, G, '#efe6cf', '#1fa59a', every=1.3, band=0.55)
    guard(it, 7.0, 5.2, 2.0, '#e9b52c', d=2.6, curl=1.8)
    for s in (-1, 1):                                   # dragon horns on the guard
        it.bar((7.0, s * 3.0), (5.2, s * 4.2), 0.9, 1.6, '#f4ead0', name='horn')
    blade(it, 7.8, 21.8, 4.4, '#1f4a44', edge='#2ed27a', ridge='#16332f', d=BD, tip=3.6, glow_edge=True)
    teeth(it, 9.0, 18.8, -1, 2.0, '#2a6a5e', every=1.9, size=1.6, d=1.0)
    gem(it, 7.0, 0, 1.7, '#2ed27a')
    sparkles(it, [(19, 3.5, 0.5), (15, -4.5, 0), (21.5, -2, 0.3)], '#7dffb5')
    return it


@item
def bat_blade():
    it = BoxItem('bat_blade')
    pommel(it, 0.9, 2.0, '#9e1530')
    handle(it, 1.6, 6.4, G, '#1c1626', '#9e1530', every=1.3)
    it.bar((7.0, -1.6), (7.0, 1.6), 1.8, 2.4, '#2a2236', name='guard')
    for s in (-1, 1):                                   # bat wings
        it.bar((7.0, s * 1.4), (9.6, s * 4.4), 1.4, 0.7, '#1c1626', name='wing bone')
        it.bar((9.6, s * 4.4), (7.4, s * 6.4), 1.1, 0.7, '#1c1626', name='wing bone')
        it.bar((7.0, s * 2.4), (8.6, s * 4.2), 2.2, 0.5, '#3a2d52', name='wing skin')
        it.bar((6.6, s * 4.0), (7.6, s * 5.8), 1.6, 0.5, '#3a2d52', name='wing skin')
    blade(it, 7.8, 21.6, 3.8, '#2a1f38', edge='#ff3b4e', ridge='#120c1c', d=BD, tip=4.0, glow_edge=True)
    gem(it, 7.0, 0, 1.4, '#ff3b4e')
    sparkles(it, [(18, 3.4, 0.4), (13, -3.6, 0)], '#ff6b7a')
    return it


@item
def frost_blade():
    it = BoxItem('frost_blade')
    std_sword(it, '#cfe2f0', '#22305e', '#cfe2f0', None, half=4.0)
    blade(it, 7.8, 21.8, 4.0, '#9fe6ff', edge='#ffffff', ridge='#5fb8e8', d=BD, tip=3.8)
    teeth(it, 8.6, 19.0, 1, 1.9, '#e6f8ff', every=2.6, size=1.4, d=0.8)
    teeth(it, 9.8, 19.0, -1, 1.9, '#e6f8ff', every=2.6, size=1.4, d=0.8)
    star(it, 7.0, 0, 1.4, '#bff4ff', z=0.9)
    sparkles(it, [(20, 3, 0), (16, -3.6, 0.3), (12, 3.6, 0)], '#e6fbff')
    return it


@item
def nutcracker_sword():
    it = BoxItem('nutcracker_sword')
    it.bar((0.0, 0), (2.2, 0), 2.4, 2.4, '#1c1c24', name='hat')            # nutcracker hat pommel
    it.bar((1.6, 0), (2.2, 0), 2.7, 2.7, '#e9b52c', 'metal', name='hat band')
    handle(it, 2.2, 6.4, G, '#c2202e', '#e9b52c', every=1.2, band=0.5)
    guard(it, 7.0, 4.4, 1.8, '#e9b52c', d=2.4, curl=0)
    it.bar((7.0, -3.2), (7.0, 3.2), 0.9, 2.8, '#c2202e', name='guard red')
    blade(it, 7.8, 21.8, 3.2, '#d6dde8', edge='#ffffff', ridge='#9aa6b8', d=1.2, tip=3.0)
    gem(it, 7.0, 0, 1.3, '#2fae5c')
    return it


@item
def star_saber():
    it = BoxItem('star_saber')
    star(it, 0.9, 0, 1.4, '#ffd84a')
    it.bar((1.4, 0), (6.8, 0), 2.2, 2.2, '#c8d0dc', 'metal', name='hilt')
    for u in (2.4, 3.8, 5.2):
        it.bar((u, 0), (u + 0.5, 0), 2.5, 2.5, '#22305e', name='hilt ring')
    it.bar((6.8, 0), (7.8, 0), 3.0, 3.0, '#22305e', 'metal', name='emitter')
    it.bar((7.8, 0), (21.6, 0), 2.6, 2.1, '#b25cf0', 'glow', glow=True, name='beam')
    it.bar((7.8, 0), (21.9, 0), 1.0, 2.3, '#f6e8ff', 'glow', glow=True, name='beam core')
    sparkles(it, [(18, 2.6, 0), (12, -2.8, 0.4), (21, -1.6, 0)], '#f0c8ff')
    return it


@item
def coral_blade():
    it = BoxItem('coral_blade')
    std_sword(it, '#1fa59a', '#22305e', '#38c8c0', '#38c8c0', half=4.2)
    blade(it, 7.8, 21.4, 4.0, '#ff7a6a', edge='#ffd0c4', ridge='#d8504a', d=BD, tip=3.4, style='matte')
    for (u, s) in ((10.5, 1), (14.0, -1), (17.0, 1)):                   # coral branches
        it.bar((u, s * 1.8), (u + 1.6, s * 3.6), 0.9, 0.9, '#ff9a8a', name='coral')
        it.bar((u + 1.6, s * 3.6), (u + 2.6, s * 3.4), 0.7, 0.7, '#ff9a8a', name='coral')
    for (u, v) in ((12.2, -2.6), (18.6, 2.4)):
        it.block(u, v, 0.9, '#f4f0ff', 'metal', name='pearl')
    return it


@item
def carrot_blade():
    it = BoxItem('carrot_blade')
    pommel(it, 0.9, 2.0, '#7a4a26', 'matte')
    handle(it, 1.6, 6.4, G, '#8a5a2c', '#5a3a20', every=1.4)
    for (du, dv) in ((-2.6, 3.2), (-3.0, -2.8), (-1.6, 4.4), (-1.8, -4.2)):  # leafy collar
        leaf(it, 7.2, 0, du, dv, '#58b83a', w=1.2)
    for i, (u0, u1, w) in enumerate(((7.2, 11, 4.6), (11, 14.6, 3.8), (14.6, 17.8, 2.8), (17.8, 20.2, 1.8), (20.2, 21.8, 0.9))):
        it.bar((u0, 0), (u1, 0), w, w * 0.8, '#f08a1c', name='carrot')
        it.bar((u1 - 0.35, 0), (u1, 0), w + 0.15, w * 0.8 + 0.15, '#c86a10', name='carrot ring')
    return it


@item
def wind_blade():
    it = BoxItem('wind_blade')
    std_sword(it, '#c8d0dc', '#22305e', '#c8d0dc', '#46d4e8', half=4.0)
    pts = [(7.6, 0), (10.6, 1.6), (13.0, -1.0), (16.0, 1.4), (18.4, -0.8), (21.8, 0.6)]
    for a, b in zip(pts, pts[1:]):                                       # lightning zig-zag
        it.bar(a, b, 2.6, 1.3, '#eef6ff', 'metal', name='bolt')
        it.bar(a, b, 1.0, 1.5, '#8cc8ff', 'glow', glow=True, name='bolt core')
    sparkles(it, [(19.5, 3, 0), (12, -3.4, 0.4)], '#dff4ff')
    return it


@item
def cloud_sword():
    it = BoxItem('cloud_sword')
    std_sword(it, '#8cc8ff', '#f2f6ff', '#8cc8ff', '#8cc8ff', half=4.2)
    blade(it, 7.8, 21.6, 4.0, '#f2f6ff', edge='#ffffff', ridge='#bcd8f4', d=1.6, tip=3.2, style='matte')
    for (u, v, s) in ((9.2, 2.4, 2.0), (11.6, -2.6, 2.2), (14.6, 2.6, 1.8), (17.2, -2.3, 1.6), (8.4, -2.0, 1.6)):
        it.block(u, v, s, '#ffffff', d=s + 0.4, name='cloud puff')
    return it


@item
def khopesh():
    it = BoxItem('khopesh')
    pommel(it, 0.9, 2.0, '#2c86d6')
    handle(it, 1.6, 6.6, G, '#1d4f86', '#efb52c', every=1.2)
    guard(it, 7.0, 2.8, 1.6, '#efb52c', d=2.2, curl=0)
    it.bar((7.6, 0), (13.4, 0), 2.8, 1.3, '#efb52c', 'metal', name='blade')
    arc(it, 13.4, -3.4, 3.4, 90, -70, 2.8, 1.2, 1.3, '#efb52c', n=7, name='sickle')
    arc(it, 13.4, -3.4, 4.6, 80, -50, 0.5, 0.4, 1.0, '#fff1a8', n=6, name='sickle edge')
    gem(it, 7.0, 0, 1.2, '#2c86d6')
    return it


@item
def warden_blade():
    it = BoxItem('warden_blade')
    std_sword(it, '#1c4a5a', '#0e1418', '#1fa5b4', '#2fe0e8', half=4.4)
    blade(it, 7.8, 21.8, 4.6, '#0f2a33', edge='#1fd2e0', ridge='#081a20', d=BD, tip=3.6, glow_edge=True)
    for (a, b) in (((9, -0.6), (12, 0.8)), ((12, 0.8), (15, -0.4)), ((15, -0.4), (18.4, 0.6))):
        it.bar(a, b, 0.5, BD + 0.3, '#2fe0e8', 'glow', glow=True, name='sculk vein')
    teeth(it, 9.4, 18.6, 1, 2.2, '#123a44', every=2.4, size=1.2, d=1.0)
    return it


@item
def candy_cane():
    it = BoxItem('candy_cane')
    stripes_handle(it, 0.0, 12.0, 2.2, '#e8343c', '#ffffff')
    bow(it, 12.2, '#e02a2a', 1.0)
    for (du, dv) in ((1.6, 2.8), (1.4, -2.8)):
        leaf(it, 12.6, 0, du, dv, '#1f7a34')
    it.block(13.4, 0.6, 0.9, '#e02a2a', 'metal', name='berry')
    it.block(13.2, -0.7, 0.9, '#e02a2a', 'metal', name='berry')
    for (u, v, s) in ((16.0, -2.4, 1.9), (18.6, 2.0, 1.7), (20.4, -1.2, 1.5), (15.4, 2.6, 1.3)):
        star(it, u, v, s, '#c9772f', 'matte', glow=False, d=1.1)              # gingerbread stars
        it.block(u, v, s * 0.45, '#fff4e6', d=1.25, name='icing')
    sparkles(it, [(21.6, 2.4, 0), (17.4, -4, 0.3)], '#ffffff')
    return it


@item
def newyear_sword():
    it = BoxItem('newyear_sword')
    std_sword(it, '#e9e9f0', '#1c1c24', '#efb52c', '#ff6ad5', half=4.6)
    blade(it, 7.8, 21.8, 4.2, '#efb52c', edge='#fff1a8', ridge='#c98414', d=BD, tip=3.6, glow_edge=True)
    sparkles(it, [(20, 3.4, 0), (16, -4, 0.3), (12.5, 3.8, 0), (21.6, -2, 0)], '#ff6ad5')
    sparkles(it, [(18, 4.2, 0.4), (14, -3.2, 0)], '#46d4e8')
    return it


# ====================================================================== pickaxes, axes, hammers
def shaft(it, top, grip='#6b4426', wrap='#3e2614', w=1.8, every=2.2):
    handle(it, 0.4, top, w, grip, wrap, every=max(every, 1.3), band=0.6)


@item
def ore_pickaxe():
    it = BoxItem('ore_pickaxe')
    shaft(it, 17.0, '#efe6cf', '#1fa59a', every=1.8)
    pick_head(it, 17.4, 7.6, 2.8, '#1f4a44', '#2fd8c8', d=2.2, droop=2.2)
    for s in (-1, 1):
        for k in range(3):
            it.bar((17.9 + 0.2 * k, s * (2.6 + k * 1.5)), (19.6 - 0.2 * k, s * (3.2 + k * 1.5)), 0.7, 1.2, '#2a6a5e',
                   'metal', name='spike')
    socket(it, 17.4, 3.4, 3.0, '#efb52c')
    gem(it, 17.4, 0, 1.5, '#46d4e8', z=0.9)
    return it


@item
def crystal_pickaxe():
    it = BoxItem('crystal_pickaxe')
    shaft(it, 16.6, '#1c1c24', '#d8313a', every=1.5)
    socket(it, 16.8, 2.4, 2.6, '#3a2d52')
    shard(it, (16.8, 0), (21.6, 0.2), 2.6, '#a45cf0')
    for s in (-1, 1):
        shard(it, (17.0, s * 1.2), (16.0, s * 7.4), 2.4, '#a45cf0')
        shard(it, (17.6, s * 1.6), (19.6, s * 5.0), 1.7, '#c88cff')
        shard(it, (16.2, s * 2.0), (14.6, s * 4.4), 1.3, '#7c3ad0')
    sparkles(it, [(20, 4, 0), (15, -6, 0.4), (21, -3, 0)], '#e2c8ff')
    return it


@item
def smelter_pickaxe():
    it = BoxItem('smelter_pickaxe')
    shaft(it, 17.0, '#2a2a33', '#ff7a1c', every=2.0)
    pick_head(it, 17.4, 7.4, 2.9, '#231a33', '#ff5a1c', d=2.2, droop=2.0)
    for s in (-1, 1):
        it.bar((17.2, s * 2.0), (16.6, s * 5.0), 0.6, 2.4, '#ff6a1c', 'glow', glow=True, name='lava vein')
    it.bar((16.0, 0), (18.8, 0), 3.4, 3.4, '#5a5a62', name='furnace')
    it.bar((16.4, 0), (18.2, 0), 1.6, 3.6, '#ff8a2a', 'glow', glow=True, name='furnace glow')
    return it


@item
def spawner_pickaxe():
    it = BoxItem('spawner_pickaxe')
    shaft(it, 17.0, '#231a33', '#d8313a', every=1.8)
    pick_head(it, 17.4, 7.2, 2.6, '#2a2a33', '#ff3b4e', d=2.0, droop=1.8)
    for k in (-1, 0, 1):                                                  # spawner cage socket
        it.bar((15.8, k * 1.2), (19.0, k * 1.2), 0.45, 3.4, '#121216', 'metal', name='cage')
        it.bar((15.8 + 1.6 + k * 1.5, -1.8), (15.8 + 1.6 + k * 1.5, 1.8), 0.45, 3.4, '#121216', 'metal', name='cage')
    it.block(17.4, 0, 1.6, '#ff3b4e', 'glow', d=2.6, glow=True, name='flame core')
    return it


@item
def newyear_pickaxe():
    it = BoxItem('newyear_pickaxe')
    shaft(it, 17.0, '#1c1c24', '#efb52c', every=1.6)
    pick_head(it, 17.4, 7.6, 2.8, '#efb52c', '#fff1a8', d=2.2, droop=2.2)
    socket(it, 17.4, 3.2, 2.9, '#e9e9f0')
    gem(it, 17.4, 0, 1.4, '#ff6ad5', z=0.9)
    sparkles(it, [(20.5, 3, 0), (19.5, -5, 0.4), (14, 5, 0)], '#ff6ad5')
    sparkles(it, [(21, -2, 0), (15, -6, 0)], '#46d4e8')
    return it


@item
def mythos_hammer():
    it = BoxItem('mythos_hammer')
    shaft(it, 16.6, '#231a33', '#efb52c', every=1.8, w=2.0)
    it.bar((17.2, -4.6), (17.2, 4.6), 4.2, 4.0, '#7c3ad0', 'metal', name='head')
    for s in (-1, 1):
        it.bar((17.2, s * 4.4), (17.2, s * 5.6), 3.4, 3.2, '#b25cf0', 'metal', name='head end')
        it.bar((17.2, s * 3.0), (17.2, s * 3.6), 4.6, 4.4, '#efb52c', 'metal', name='head band')
    gem(it, 17.2, 0, 2.2, '#e0a0ff', z=1.2)
    sparkles(it, [(21, 2, 0), (20, -4, 0.4), (14, 5, 0)], '#e0a0ff')
    return it


@item
def thunder_hammer():
    it = BoxItem('thunder_hammer')
    shaft(it, 16.8, '#22305e', '#efb52c', every=1.6, w=2.0)
    it.bar((17.4, -4.2), (17.4, 4.2), 4.6, 4.4, '#8d99aa', 'metal', name='head')
    for s in (-1, 1):
        it.bar((17.4, s * 4.0), (17.4, s * 4.8), 5.2, 5.0, '#efb52c', 'metal', name='cap')
    for a, b in (((19.0, -2.0), (17.6, -0.4)), ((17.6, -0.4), (17.2, 0.6)), ((17.2, 0.6), (15.8, 2.2))):
        it.bar(a, b, 0.7, 4.8, '#ffe066', 'glow', glow=True, name='bolt')
    sparkles(it, [(21, 3, 0), (20.5, -4, 0.3)], '#ffe066')
    return it


@item
def timber_axe():
    it = BoxItem('timber_axe')
    shaft(it, 19.0, '#8a5a2c', '#6b3a22', every=2.6)
    socket(it, 17.0, 3.2, 2.4, '#5a6270')
    axe_head(it, 17.0, 1, 5.4, 3.0, 7.2, '#a7b0bd', '#e8eef6', d=1.2)
    it.bar((17.0, -1.2), (16.2, -3.2), 1.4, 1.2, '#5a6270', 'metal', name='back spike')
    return it


@item
def inferno_axe():
    it = BoxItem('inferno_axe')
    shaft(it, 19.4, '#2a2a33', '#ff7a1c', every=2.0)
    for s in (-1, 1):
        axe_head(it, 16.8, s, 5.0, 3.2, 7.4, '#231a33', '#ff5a1c', d=1.3, glow_edge=True)
        it.bar((16.8, s * 2.0), (19.4, s * 3.6), 0.8, 1.4, '#3a2d52', 'metal', name='horn')
    socket(it, 16.8, 3.6, 3.0, '#efb52c')
    gem(it, 16.8, 0, 1.5, '#ff5a1c', z=0.9)
    flame(it, 19.6, 0, 0.6)
    return it


@item
def newyear_axe():
    it = BoxItem('newyear_axe')
    shaft(it, 19.4, '#1c1c24', '#efb52c', every=1.6)
    for s in (-1, 1):
        axe_head(it, 16.8, s, 4.8, 3.0, 6.8, '#efb52c', '#fff1a8', d=1.2, glow_edge=True)
    socket(it, 16.8, 3.4, 2.9, '#e9e9f0')
    gem(it, 16.8, 0, 1.4, '#ff6ad5', z=0.9)
    sparkles(it, [(21, 2, 0), (20, -5, 0.3), (13, 5, 0)], '#ff6ad5')
    sparkles(it, [(21.5, -1.5, 0), (14, -6, 0)], '#46d4e8')
    return it


@item
def chainsaw():
    it = BoxItem('chainsaw')
    it.bar((0.4, 0), (3.6, 0), 2.4, 2.2, '#1c1c24', name='rear grip')
    it.bar((3.4, 0), (9.6, 0), 4.6, 3.6, '#f07a1c', name='engine')
    it.bar((4.0, 0), (9.0, 0), 4.8, 1.2, '#1c1c24', name='engine stripe')
    it.bar((5.0, 2.4), (8.0, 3.6), 1.0, 1.0, '#1c1c24', name='top handle')
    it.bar((9.4, 0), (21.4, 0), 2.8, 0.8, '#a7b0bd', 'metal', name='bar')
    u = 9.8
    while u < 21:
        for s in (-1, 1):
            it.bar((u, s * 1.4), (u + 0.6, s * 1.9), 0.5, 1.0, '#2a2a33', 'metal', name='chain')
        u += 1.0
    gem(it, 6.5, 0, 1.2, '#ffe066', glow=False, z=1.6)
    return it


@item
def excavator():
    it = BoxItem('excavator')
    stripes_handle(it, 0.2, 12.8, 1.9, '#1c1c24', '#f6d23c', every=1.6)
    socket(it, 13.4, 1.6, 2.6, '#5a6270')
    it.bar((14.0, 0), (19.4, 0), 6.0, 1.0, '#8d99aa', 'metal', name='spade')
    it.bar((19.4, 0), (20.8, 0), 4.2, 0.9, '#8d99aa', 'metal', name='spade tip')
    it.bar((20.8, 0), (21.6, 0), 2.0, 0.8, '#e8eef6', 'metal', name='spade point')
    for s in (-1, 1):
        it.bar((14.2, s * 2.9), (19.4, s * 2.9), 0.4, 1.1, '#e8eef6', 'metal', name='spade edge')
        it.block(15.2, s * 1.8, 0.6, '#f6d23c', 'metal', d=1.3, name='rivet')
    return it


@item
def pumpkin_scythe():
    it = BoxItem('pumpkin_scythe')
    shaft(it, 18.4, '#3a2a1c', '#7c3ad0', every=2.2, w=1.7)
    arc(it, 15.0, 0.2, 5.6, 5, -125, 2.8, 0.6, 1.0, '#231a33', n=8, name='scythe')
    arc(it, 15.0, 0.2, 4.4, 0, -115, 0.6, 0.4, 1.1, '#ff8a1c', 'glow', n=7, glow=True, name='scythe edge')
    it.block(19.8, 0.6, 3.2, '#f08a1c', d=3.0, name='pumpkin')
    for v in (-0.6, 0.6, 1.8):
        it.bar((18.2, v), (21.4, v), 0.35, 3.2, '#c86a10', name='pumpkin rib')
    it.bar((21.4, 0.6), (22.2, 0.9), 0.6, 0.6, '#3d8a2a', name='stem')
    it.block(19.8, 1.2, 0.6, '#ffe066', 'glow', d=3.4, glow=True, name='eye')
    sparkles(it, [(21, -4, 0), (14, -6.5, 0.3)], '#ff9a2a')
    return it


@item
def harvest_sickle():
    it = BoxItem('harvest_sickle')
    handle(it, 0.4, 9.6, 2.0, '#8a5a2c', '#6b3a22', every=1.8)
    socket(it, 9.8, 1.2, 2.4, '#5a6270')
    arc(it, 14.4, -3.0, 5.0, 145, -40, 2.2, 0.6, 0.9, '#a7b0bd', n=8, name='sickle')
    arc(it, 14.4, -3.0, 4.0, 140, -35, 0.45, 0.35, 1.0, '#e8eef6', n=7, name='sickle edge')
    return it


@item
def newyear_hoe():
    it = BoxItem('newyear_hoe')
    shaft(it, 19.8, '#1c1c24', '#efb52c', every=1.6)
    socket(it, 18.6, 2.6, 2.6, '#e9e9f0')
    it.bar((18.6, -1.2), (18.6, -6.2), 3.0, 1.0, '#efb52c', 'metal', name='hoe blade')
    it.bar((17.4, -5.6), (17.2, -6.4), 0.6, 1.0, '#fff1a8', 'metal', name='hoe edge')
    gem(it, 18.6, 0, 1.2, '#ff6ad5', z=0.9)
    sparkles(it, [(21, 2, 0), (20, -7, 0.3)], '#ff6ad5')
    return it


# ====================================================================== wands & staffs
@item
def ice_wand():                                          # christmas tree wand
    it = BoxItem('ice_wand')
    stripes_handle(it, 0.0, 8.0, 2.0, '#e8343c', '#ffffff')
    bow(it, 8.2, '#e02a2a', 0.9)
    for i in range(4):
        u0 = 8.6 + i * 3.0
        w = 9.0 - i * 2.0
        it.bar((u0, 0), (u0 + 3.4, 0), w, 3.2 - i * 0.45, '#2a8a3a' if i % 2 == 0 else '#34a046', name='tier')
        it.bar((u0, 0), (u0 + 0.5, 0), w + 0.4, 3.3 - i * 0.45, '#1d6e2c', name='tier rim')
    for (u, v, c) in ((10.2, -3.0, '#e8343c'), (10.6, 2.6, '#ffd84a'), (13.4, -1.4, '#46d4e8'), (13.6, 2.2, '#e8343c'),
                      (16.4, 0.6, '#ffd84a'), (11.6, 0.2, '#ff6ad5'), (18.8, -0.6, '#46d4e8')):
        it.block(u, v, 0.9, c, 'glow', z=1.4, glow=True, name='ornament')
    star(it, 21.2, 0, 1.3, '#ffd84a')
    sparkles(it, [(21, 3.4, 0), (15, -5, 0.3)], '#ffffff')
    return it


@item
def blossom_wand():                                      # rose on a thorny stem
    it = BoxItem('blossom_wand')
    it.bar((0.2, 0), (15.6, 0), 1.2, 1.2, '#1f7a34', name='stem')
    it.bar((2.6, 0), (3.6, 0), 1.7, 1.7, '#efb52c', 'metal', name='gold ring')
    for k, u in enumerate((5.0, 7.2, 9.4, 11.6, 13.4)):
        s = 1 if k % 2 else -1
        it.bar((u, s * 0.5), (u + 0.6, s * 1.3), 0.4, 0.5, '#2f9a3c', name='thorn')
    leaf(it, 8.0, 0, 2.2, 3.0, '#3fae4a', w=1.3)
    leaf(it, 11.0, 0, 2.0, -3.0, '#3fae4a', w=1.2)
    for (du, dv) in ((0.8, 1.6), (0.8, -1.6), (1.2, 0)):
        it.bar((15.4, 0), (15.4 + du, dv), 0.8, 0.8, '#2f9a3c', name='sepal')
    it.block(18.2, 0, 4.2, '#c81e3c', d=4.2, name='bloom')
    for (du, dv, z) in ((0, 1.9, 0), (0, -1.9, 0), (-1.2, 0, 1.8), (-1.2, 0, -1.8)):
        it.block(17.6 + du, dv, 3.0, '#a01430', d=3.0, z=z, name='petal')
    it.block(19.8, 0, 2.6, '#e0405e', d=2.6, name='bloom top')
    it.block(20.6, 0.3, 1.2, '#7c0c22', d=1.4, name='bloom heart')
    sparkles(it, [(21.4, 3, 0), (16, -4, 0.3)], '#ffb0c8')
    return it


@item
def crystal_wand():
    it = BoxItem('crystal_wand')
    handle(it, 0.3, 11.2, 1.8, '#c8d0dc', '#7c3ad0', every=1.6)
    socket(it, 11.4, 1.6, 2.6, '#efb52c')
    shard(it, (11.6, 0), (21.8, 0), 3.0, '#a45cf0')
    for s in (-1, 1):
        shard(it, (12.0, s * 1.2), (16.6, s * 4.0), 1.8, '#c88cff')
        shard(it, (12.4, s * 0.6), (14.4, s * 2.6), 1.2, '#7c3ad0')
    sparkles(it, [(20, 3, 0), (17, -4, 0.3), (22, -1, 0)], '#e2c8ff')
    return it


@item
def ember_wand():
    it = BoxItem('ember_wand')
    handle(it, 0.3, 12.4, 1.8, '#3a2a1c', '#efb52c', every=2.0)
    it.bar((12.2, 0), (13.6, 0), 3.0, 3.0, '#efb52c', 'metal', name='cup')
    for s in (-1, 1):
        it.bar((13.4, s * 1.2), (14.6, s * 2.2), 0.8, 1.6, '#efb52c', 'metal', name='cup prong')
    flame(it, 15.2, 0, 1.25)
    sparkles(it, [(21, 2, 0), (19, -3, 0.4), (17, 3.4, 0)], '#ffb04a')
    return it


@item
def void_scepter():
    it = BoxItem('void_scepter')
    handle(it, 0.3, 13.0, 1.8, '#231a33', '#efb52c', every=2.2)
    it.bar((12.8, 0), (14.0, 0), 3.2, 3.2, '#efb52c', 'metal', name='collar')
    for v in (-2.0, 0, 2.0):
        it.bar((13.8, v * 0.6), (15.6, v), 0.7, 1.2, '#efb52c', 'metal', name='prong')
    orb(it, 17.6, 0, 2.4, '#7c3ad0', ring='#efb52c')
    it.block(17.6, 0, 1.4, '#f0c8ff', 'glow', d=3.6, glow=True, name='orb core')
    star(it, 21.2, 0, 0.9, '#f0c8ff')
    return it


@item
def meteor_staff():
    it = BoxItem('meteor_staff')
    handle(it, 0.3, 13.4, 1.9, '#5a5a62', '#ff7a1c', every=2.4)
    for (du, dv, s) in ((0, 0, 4.0), (1.2, 1.4, 2.8), (-0.8, -1.6, 2.6), (1.6, -1.2, 2.4)):
        it.block(16.4 + du, dv, s, '#2a2230', d=s, name='rock')
    for (a, b) in (((15.0, -1.2), (17.2, 0.4)), ((17.2, 0.4), (18.0, 1.8))):
        it.bar(a, b, 0.5, 4.4, '#ff6a1c', 'glow', glow=True, name='lava')
    flame(it, 18.8, 0.2, 0.9)
    return it


@item
def dragon_staff():
    it = BoxItem('dragon_staff')
    handle(it, 0.3, 14.0, 1.9, '#3a2a1c', '#efb52c', every=2.4)
    orb(it, 17.4, 0, 1.9, '#e8343c', 'glow')
    for s in (-1, 1):                                                       # golden claws
        it.bar((14.0, s * 0.8), (15.6, s * 2.6), 0.9, 1.1, '#efb52c', 'metal', name='claw')
        it.bar((15.6, s * 2.6), (18.6, s * 2.8), 0.8, 1.0, '#efb52c', 'metal', name='claw')
        it.bar((18.6, s * 2.8), (19.6, s * 1.4), 0.7, 0.9, '#efb52c', 'metal', name='claw tip')
        it.bar((15.0, s * 1.6), (13.2, s * 4.0), 0.9, 1.2, '#2fae5c', 'metal', name='horn')
    it.bar((14.0, 0), (15.0, 0), 2.6, 2.6, '#efb52c', 'metal', name='collar')
    return it


@item
def tide_staff():                                        # trident
    it = BoxItem('tide_staff')
    handle(it, 0.3, 15.2, 1.7, '#1fa59a', '#efb52c', every=2.6)
    it.bar((15.0, -2.8), (15.0, 2.8), 1.4, 1.6, '#efb52c', 'metal', name='crossbar')
    it.bar((15.6, 0), (20.4, 0), 1.3, 1.3, '#efb52c', 'metal', name='prong')
    it.bar((20.4, 0), (22.0, 0), 0.7, 0.9, '#5ff0e8', 'glow', glow=True, name='prong tip')
    for s in (-1, 1):
        it.bar((15.4, s * 2.6), (19.2, s * 2.6), 1.1, 1.1, '#efb52c', 'metal', name='prong')
        it.bar((19.2, s * 2.6), (20.6, s * 2.6), 0.6, 0.8, '#5ff0e8', 'glow', glow=True, name='prong tip')
        it.bar((18.4, s * 2.6), (17.6, s * 3.6), 0.5, 0.8, '#efb52c', 'metal', name='barb')
    it.block(15.0, 0, 1.2, '#38c8c0', 'glow', d=2.0, glow=True, name='pearl')
    return it


@item
def druid_staff():
    it = BoxItem('druid_staff')
    u, k = 0.2, 0
    while u < 15.4:                                                      # twisted wood
        it.bar((u, 0.25 * (1 if k % 2 else -1)), (u + 1.6, 0.25 * (1 if k % 2 else -1)), 1.9, 1.9, '#6b4426', name='wood')
        u += 1.6
        k += 1
    arc(it, 17.2, 0, 2.6, 180, -100, 1.7, 1.2, 1.7, '#6b4426', 'matte', n=6, name='knot')
    it.block(17.2, 0, 1.7, '#2ed27a', 'glow', d=2.0, glow=True, name='emerald')
    for (du, dv) in ((2.6, 3.0), (3.2, -1.2), (1.2, 3.8), (2.4, -3.4), (4.0, 1.2)):
        leaf(it, 18.0, 0, du, dv, '#58b83a', w=1.2)
    sparkles(it, [(21.5, 2, 0), (15, -4, 0.3)], '#b8ff9a')
    return it


@item
def pharaoh_staff():                                     # ankh staff
    it = BoxItem('pharaoh_staff')
    stripes_handle(it, 0.2, 14.6, 1.8, '#1d4f86', '#efb52c', every=1.4)
    it.bar((15.4, -3.2), (15.4, 3.2), 1.5, 1.6, '#efb52c', 'metal', name='ankh bar')
    it.bar((14.6, 0), (16.2, 0), 1.6, 1.6, '#efb52c', 'metal', name='ankh stem')
    arc(it, 18.4, 0, 2.2, 180, -180, 1.2, 1.2, 1.5, '#efb52c', n=8, name='ankh loop')
    it.block(18.4, 0, 1.4, '#2c86d6', 'glow', d=1.2, glow=True, name='lapis')
    sparkles(it, [(21.4, 2.6, 0), (16, -4.6, 0.3)], '#fff1a8')
    return it


@item
def builder_wand():
    it = BoxItem('builder_wand')
    handle(it, 0.3, 13.6, 1.8, '#8a5a2c', '#efb52c', every=2.2)
    it.block(16.4, 0, 4.4, '#c8743c', 'metal', d=4.4, name='block head')
    for (du, dv) in ((-2.2, -2.2), (-2.2, 2.2), (2.2, -2.2), (2.2, 2.2)):
        it.block(16.4 + du, dv, 1.2, '#efb52c', 'metal', d=4.8, name='corner')
    gem(it, 16.4, 0, 1.4, '#2ed27a', z=2.0)
    sparkles(it, [(20.6, 3, 0), (19.6, -3.6, 0.3)], '#fff1a8')
    return it


@item
def region_wand():
    it = BoxItem('region_wand')
    handle(it, 0.3, 13.8, 1.8, '#c8d0dc', '#46d4e8', every=2.2)
    it.block(16.6, 0, 3.8, '#22305e', d=3.8, name='cube')
    for (du, dv) in ((-1.9, 0), (1.9, 0)):
        it.bar((16.6 + du, -1.9), (16.6 + du, 1.9), 0.45, 4.0, '#46d4e8', 'glow', glow=True, name='edge glow')
    for dv in (-1.9, 1.9):
        it.bar((14.7, dv), (18.5, dv), 0.45, 4.0, '#46d4e8', 'glow', glow=True, name='edge glow')
    sparkles(it, [(20.6, 2.6, 0), (19.8, -3.2, 0.4), (21.6, 0, 0)], '#9ff0ff')
    return it


@item
def laser_gun():
    it = BoxItem('laser_gun')
    it.bar((3.0, 2.6), (7.4, 1.2), 2.4, 2.4, '#22305e', name='grip')
    it.bar((5.4, 0), (14.6, 0), 3.6, 3.0, '#c8d0dc', 'metal', name='body')
    it.bar((6.0, 0), (13.8, 0), 3.8, 1.0, '#22305e', name='body stripe')
    it.bar((14.4, 0), (20.6, 0), 1.7, 1.7, '#8d99aa', 'metal', name='barrel')
    for u in (15.6, 17.4, 19.2):
        it.bar((u, 0), (u + 0.5, 0), 2.1, 2.1, '#46d4e8', 'glow', glow=True, name='coil')
    it.bar((20.6, 0), (21.6, 0), 2.0, 2.0, '#9ff0ff', 'glow', glow=True, name='muzzle')
    it.bar((9.0, -1.8), (12.4, -1.8), 1.0, 1.4, '#46d4e8', 'glow', glow=True, name='sight')
    return it


def broom(name, spark):
    it = BoxItem(name)
    handle(it, 0.2, 15.6, 1.6, '#4a3020', '#7c3ad0', every=3.2)
    it.bar((15.2, 0), (16.4, 0), 2.6, 2.6, '#7c3ad0', name='band')
    for (dv, z) in ((-2.2, 0), (-1.0, 0.8), (0, -0.6), (1.0, 0.8), (2.2, 0), (0, 1.0)):
        it.bar((16.0, dv * 0.35), (21.8, dv), 1.2, 1.2, '#d8b878', z=z, name='bristle')
    it.bar((17.2, 0), (17.8, 0), 3.4, 2.8, '#9a6a30', name='tie')
    if spark:
        sparkles(it, [(21.6, 3.2, 0), (19, -4, 0.3), (14, 3, 0)], '#e0a0ff')
    return it


@item
def witch_broom():
    return broom('witch_broom', False)


@item
def witch_broom_ride():
    return broom('witch_broom_ride', True)


@item
def ancient_relic():
    it = BoxItem('ancient_relic')
    for (p0, p1, w) in (((4, 0), (20, 0), 4.0), ((6, 1.5), (15, 6.0), 2.6), ((6, -1.5), (14, -6.0), 2.6),
                        ((8, 0.8), (12, 3.6), 1.6), ((9, -0.6), (12.6, -3.0), 1.4)):
        shard(it, p0, p1, w, '#1fa59a')
    it.block(10.0, 0, 2.4, '#e8fff8', 'glow', d=4.4, glow=True, name='core')
    sparkles(it, [(19, 4, 0), (14, -6.6, 0.4), (21.6, -1.4, 0), (5, 3, 0)], '#9ff0e8')
    return it


@item
def meteor():
    it = BoxItem('meteor')
    for (du, dv, s, c) in ((0, 0, 7.0, '#2a2230'), (2.2, 2.0, 4.4, '#3a3040'), (-2.0, -2.4, 4.0, '#3a3040'),
                           (2.4, -2.6, 3.6, '#231a28'), (-2.6, 2.2, 3.6, '#231a28')):
        it.block(10.0 + du, dv, s, c, d=s, name='rock')
    for (a, b) in (((7.6, -1.4), (10.4, 0.6)), ((10.4, 0.6), (12.6, -0.4)), ((9.0, 1.8), (11.0, 2.8))):
        it.bar(a, b, 0.6, 7.4, '#ff6a1c', 'glow', glow=True, name='lava crack')
    flame(it, 14.0, 0, 1.3)
    return it


@item
def meteor_core():
    it = BoxItem('meteor_core')
    it.block(11.0, 0, 6.0, '#ff6a1c', 'glow', d=6.0, glow=True, name='core')
    it.block(11.0, 0, 3.6, '#ffe066', 'glow', d=6.4, glow=True, name='core heart')
    for (du, dv, s) in ((-3.0, 0, 3.0), (3.0, 0.6, 2.8), (0.4, 3.2, 2.6), (-0.6, -3.2, 2.6)):
        it.block(11.0 + du, dv, s, '#2a2230', d=s, name='shell')
    sparkles(it, [(17, 3, 0), (5, -3, 0.3), (15, -4.4, 0)], '#ffb04a')
    return it


# ====================================================================== trophies (upright, world coordinates)
def trophy(name, cup, base, trim):
    from boxitem import tone as _t
    m = SwatchModel(name, 'trophy')
    m.add((3, 0, 3), (13, 2.4, 13), base, name='base')
    m.add((3.6, 2.4, 3.6), (12.4, 3.4, 12.4), trim, 'metal', name='base trim')
    m.add((6.4, 3.4, 6.4), (9.6, 4.6, 9.6), cup, 'metal', name='foot')
    m.add((7.2, 4.6, 7.2), (8.8, 8.0, 8.8), cup, 'metal', name='stem')
    m.add((5.0, 8.0, 5.0), (11.0, 9.4, 11.0), cup, 'metal', name='bowl low')
    m.add((3.6, 9.4, 3.6), (12.4, 14.4, 12.4), cup, 'metal', name='bowl')
    m.add((3.2, 14.0, 3.2), (12.8, 15.0, 12.8), _t(cup, 0.3), 'metal', name='rim')
    for x0, x1 in ((1.0, 3.6), (12.4, 15.0)):
        m.add((x0, 13.0, 7.2), (x1, 13.8, 8.8), cup, 'metal', name='handle top')
        m.add((x0 if x0 < 8 else x1 - 0.8, 10.0, 7.2), (x0 + 0.8 if x0 < 8 else x1, 13.8, 8.8), cup, 'metal', name='handle side')
        m.add((x0, 9.6, 7.2), (x1, 10.4, 8.8), cup, 'metal', name='handle low')
    m.add((6.8, 10.4, 3.2), (9.2, 12.8, 3.6), '#ffd84a', 'glow', glow=True, name='star plate')
    return m


@item
def trophy_daily():
    return trophy('trophy_daily', '#c8743c', '#3a2a1c', '#c8743c')


@item
def trophy_weekly():
    return trophy('trophy_weekly', '#c8d0dc', '#22305e', '#c8d0dc')


@item
def trophy_monthly():
    return trophy('trophy_monthly', '#efb52c', '#231a33', '#efb52c')


def build_all():
    out = {}
    for name, fn in ITEMS.items():
        r = fn()
        out[name] = r.model() if isinstance(r, BoxItem) else r
    return out

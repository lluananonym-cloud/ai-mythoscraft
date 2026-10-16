"""Little shoulder companions (shown by an item display next to the player's head). Flat colours per cube.
Model space: feet at y=0, centred on x/z = 8, face toward north (-z)."""
import os

from hd import Model, solid


def S(c):
    return solid(c, noise=0)


def snowman():
    m = Model('companion_snowman', display='companion')
    snow, coal, carrot, red, dred, black, stick = S('#f4f7fb'), S('#1c1f26'), S('#f08a1c'), S('#d8313a'), S('#9e1530'), S('#22232b'), S('#6b4426')
    m.add((3, 0, 3), (13, 7, 13), snow, name='body')
    m.add((4, 7, 4), (12, 8.5, 12), snow)
    m.add((4.5, 8.5, 4.5), (11.5, 14.5, 11.5), snow, name='head')
    m.add((6, 10.5, 4.1), (7, 11.7, 4.5), coal)
    m.add((9, 10.5, 4.1), (10, 11.7, 4.5), coal)
    m.add((7.5, 9.6, 2), (8.5, 10.6, 4.5), carrot, name='nose')
    for y in (2.2, 4.4):
        m.add((7.4, y, 2.6), (8.6, y + 1.2, 3), coal)
    m.add((4, 7.4, 4), (12, 8.8, 12), red, name='scarf')
    m.add((9.5, 3.6, 2.4), (11, 7.4, 3.6), dred, name='scarf end')
    m.add((4, 14.5, 4), (12, 15.2, 12), black, name='brim')
    m.add((5.2, 15.2, 5.2), (10.8, 20, 10.8), black, name='hat')
    m.add((5.1, 15.4, 5.1), (10.9, 16.6, 10.9), red, name='band')
    m.add((7.4, 15.6, 4.9), (8.6, 16.4, 5.2), S('#efb52c'))
    m.add((13, 4, 7.5), (16, 5, 8.5), stick, name='arm')
    m.add((15, 5, 7.5), (16, 7, 8.5), stick)
    m.add((0, 4, 7.5), (3, 5, 8.5), stick)
    return m


def penguin():
    m = Model('companion_penguin', display='companion')
    black, white, orange = S('#23263a'), S('#f4f4f0'), S('#f39a1e')
    m.add((4, 0, 5), (12, 11, 12), black, name='body')
    m.add((5, 1, 4.4), (11, 9.5, 5), white, name='belly')
    m.add((4.5, 11, 5.5), (11.5, 16, 11.5), black, name='head')
    m.add((5.5, 11.5, 5), (10.5, 14.5, 5.5), white, name='face')
    m.add((6.3, 13.2, 4.8), (7.3, 14.2, 5.1), black)
    m.add((8.7, 13.2, 4.8), (9.7, 14.2, 5.1), black)
    m.add((7.2, 11.8, 3.5), (8.8, 12.8, 5), orange, name='beak')
    m.add((3, 3, 6.5), (4, 9, 10.5), black)
    m.add((12, 3, 6.5), (13, 9, 10.5), black)
    m.add((5, -0.5, 4), (7.5, 0.5, 7), orange)
    m.add((8.5, -0.5, 4), (11, 0.5, 7), orange)
    m.add((4.4, 15.5, 5.4), (11.6, 16.6, 11.6), S('#d8313a'), name='beanie')
    m.add((7.2, 16.6, 7.6), (8.8, 18, 9.2), S('#f4f4f0'))
    return m


def ghost():
    """the ghost built together in Blockbench style: pale cube body, small dark eyes, feet, an arm, thin dark
    smoke/lightning streaks and white wisps around it (geometry in data/ghost_companion.json)"""
    import json
    from swatch import SwatchModel
    colours = {'body': '#eef8e8', 'dark': '#121214', 'wisp': '#f2f2ee'}
    m = SwatchModel('companion_ghost', display='companion')
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data', 'ghost_companion.json'), encoding='utf-8') as f:
        for p in json.load(f):
            m.add(p['from'], p['to'], colours[p['kind']], name=p['name'])
    return m


def dragon():
    m = Model('companion_dragon', display='companion')
    g, dg, belly, horn, eye = S('#2fae5c'), S('#1d6e3c'), S('#f2d27a'), S('#f4ead0'), S('#f6d23c')
    m.add((5, 2, 5), (11, 9, 12), g, name='body')
    m.add((6, 3, 4.5), (10, 8, 5), belly)
    m.add((5.5, 9, 3.5), (10.5, 14, 9), g, name='head')
    m.add((6.5, 9, 1.5), (9.5, 11.5, 3.5), g, name='snout')
    m.add((6.2, 12, 3.3), (7.2, 13, 3.6), eye)
    m.add((8.8, 12, 3.3), (9.8, 13, 3.6), eye)
    m.add((6, 14, 6), (7, 16.5, 7), horn)
    m.add((9, 14, 6), (10, 16.5, 7), horn)
    m.add((1, 7, 7), (5, 12, 8), dg, name='left wing')
    m.add((11, 7, 7), (15, 12, 8), dg, name='right wing')
    m.add((7, 2, 12), (9, 4, 15), g, name='tail')
    m.add((7.3, 2.3, 15), (8.7, 3.7, 16), dg)
    for x in (5.5, 9):
        m.add((x, 0, 5.5), (x + 1.5, 2, 7.5), dg)
    for z in (6, 8, 10):
        m.add((7.4, 9, z), (8.6, 10, z + 1), dg)
    return m


def bee():
    m = Model('companion_bee', display='companion')
    y, b, wing, eye = S('#f6c52c'), S('#2a2420'), S('#d8f0ff'), S('#1c1f26')
    m.add((4, 4, 4), (12, 11, 13), y, name='body')
    for z in (7, 10):
        m.add((3.9, 3.9, z), (12.1, 11.1, z + 1.5), b)
    m.add((5, 7.5, 3.6), (6.5, 9.5, 4), eye)
    m.add((9.5, 7.5, 3.6), (11, 9.5, 4), eye)
    m.add((5, 11, 6), (7.5, 11.6, 10), wing)
    m.add((8.5, 11, 6), (11, 11.6, 10), wing)
    m.add((5.5, 11, 4.4), (6.3, 13, 5.2), b)
    m.add((9.7, 11, 4.4), (10.5, 13, 5.2), b)
    m.add((7.5, 6.5, 13), (8.5, 7.5, 14.5), b, name='sting')
    return m


COMPANIONS = {'companion_snowman': snowman, 'companion_penguin': penguin, 'companion_ghost': ghost,
              'companion_dragon': dragon, 'companion_bee': bee}

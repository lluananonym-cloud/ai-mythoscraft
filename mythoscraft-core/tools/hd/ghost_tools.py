"""Shadow-ghost collection, drawn by the server owner (data/ghost/*.png are the drawings, kept as reference).

Built like the other HD tools (clean swatch cubes, same proportions as the crystal pickaxe & co.) instead of converted
pixels: black heads with a frosty white crust, a grey striped grip, a black handle with a little hook at the end and
the shadow ghost – an upright dark cube with white eyes in a glowing white ring – where head and handle meet."""
import os

from boxitem import BoxItem, handle, socket, tone

BLACK, BLACK2, STREAK = '#121117', '#1d1b25', '#22322a'
FROST, FROST2 = '#f6f8fc', '#d9e1ea'
GRIP, GRIP_DARK, HOOK = '#8f939b', '#4e5159', '#2a2a31'
GHOST, EYE, RING = '#1a0b2a', '#ffffff', '#f2f4ff'


def _handle(it, top, grip_to=8.6):
    """hook at the end, grey striped grip, black upper shaft with a flared collar"""
    it.bar((0.9, 0), (top, 0), 1.6, 1.6, BLACK, 'matte', name='shaft')
    handle(it, 2.4, grip_to, 1.9, GRIP, GRIP_DARK, every=1.1, band=0.45)
    it.bar((0.4, 0), (2.4, 0), 2.0, 2.0, BLACK2, 'metal', name='end cap')
    it.bar((0.9, 0.6), (-0.2, 2.0), 0.9, 1.4, HOOK, 'metal', name='hook')
    it.bar((-0.2, 2.0), (0.6, 3.0), 0.8, 1.3, HOOK, 'metal', name='hook tip')
    it.bar((grip_to, 0), (grip_to + 1.2, 0), 2.3, 2.3, BLACK2, 'metal', name='collar')


def _ghost(it, u, s=3.2):
    """upright ghost cube (not turned with the tool) with eyes on both sides and a glowing white ring"""
    from boxitem import world
    x, y = world(u, 0)
    m = it.m
    r = s / 2
    m.add((x - r - 0.45, y - r - 0.45, 7.0), (x + r + 0.45, y + r + 0.45, 9.0), RING, 'glow', glow=True, name='ghost ring')
    m.add((x - r, y - r, 8 - r), (x + r, y + r, 8 + r), GHOST, 'matte', name='ghost')
    m.add((x - r + 0.2, y + r - 0.35, 8 - r + 0.2), (x + r - 0.2, y + r, 8 + r - 0.2), tone(GHOST, 0.18), name='ghost top')
    for z0, z1 in ((8 + r, 8 + r + 0.15), (8 - r - 0.15, 8 - r)):
        for dx in (-0.75, 0.45):
            m.add((x + dx, y - 0.1, z0), (x + dx + 0.42, y + 0.6, z1), EYE, 'glow', glow=True, name='ghost eye')


def _frost(it, pts, d=1.6, style='matte'):
    """white snowy crust along an edge: a connected band through the points (u, v, size) with soft bumps on it"""
    for k, (a, b) in enumerate(zip(pts, pts[1:])):
        w = min(a[2], b[2]) * 0.62
        it.bar((a[0], a[1]), (b[0], b[1]), w, d - 0.1, FROST2 if k % 2 else FROST, style, name='frost band')
    for k, (u, v, s) in enumerate(pts):
        it.block(u, v, s * 0.78, FROST, style, d=d + 0.15, name='frost bump')


def ghost_sword():
    it = BoxItem('ghost_sword')
    _handle(it, 9.0, grip_to=7.2)
    # broad black cleaver blade, straight edge on one side, frosty jagged crust on the other
    it.bar((9.6, -0.4), (20.4, -0.4), 4.6, 1.3, BLACK, 'metal', name='blade')
    it.bar((20.4, -0.9), (22.0, -1.3), 2.6, 1.1, BLACK, 'metal', name='blade tip')
    it.bar((10.0, -2.55), (20.6, -2.55), 0.45, 1.0, '#3a3f4a', 'metal', name='edge')
    it.bar((10.6, 0.2), (19.6, 0.2), 0.3, 1.45, STREAK, 'metal', name='streak')
    _frost(it, [(10.0, 2.2, 1.2), (11.3, 2.6, 1.5), (12.7, 2.1, 1.1), (13.9, 2.7, 1.6), (15.3, 2.3, 1.2),
                (16.6, 2.0, 1.3), (17.8, 2.5, 1.5), (19.1, 1.9, 1.1), (20.3, 1.2, 1.2), (21.3, 0.3, 1.0)])
    _ghost(it, 8.6, 3.2)
    return it


def ghost_pickaxe():
    it = BoxItem('ghost_pickaxe')
    _handle(it, 16.4)
    socket(it, 16.6, 2.4, 2.4, BLACK2)
    for s in (-1, 1):
        it.bar((16.8, s * 1.4), (15.6, s * 5.0), 2.6, 1.6, BLACK, 'metal', name='arm')
        it.bar((15.6, s * 5.0), (14.0, s * 7.8), 1.7, 1.4, BLACK, 'metal', name='arm tip')
        it.bar((14.2, s * 7.6), (13.4, s * 8.7), 0.8, 1.1, '#3a3f4a', 'metal', name='point')
        it.bar((17.0, s * 1.6), (15.9, s * 4.6), 0.3, 1.7, STREAK, 'metal', name='streak')
        # frost on the upper side of each arm
        _frost(it, [(18.0, s * 1.4, 1.2), (17.5, s * 3.0, 1.4), (16.8, s * 4.6, 1.2), (15.9, s * 6.1, 1.1), (15.0, s * 7.4, 0.9)])
    _frost(it, [(18.2, 0, 1.4)])
    _ghost(it, 15.2, 3.0)
    return it


def ghost_axe():
    it = BoxItem('ghost_axe')
    _handle(it, 18.6)
    socket(it, 17.0, 3.0, 2.4, BLACK2)
    # big stepped black axe blade on one side
    for i, (h, v) in enumerate(((4.0, -2.0), (5.4, -3.4), (6.6, -4.8), (7.4, -6.1))):
        it.bar((17.0 - h / 2, v), (17.0 + h / 2, v), 1.45, 1.4, BLACK, 'metal', name='axe')
    it.bar((13.0, -6.95), (21.0, -6.95), 0.5, 1.1, '#3a3f4a', 'metal', name='axe edge')
    it.bar((14.8, -3.0), (19.2, -5.6), 0.3, 1.5, STREAK, 'metal', name='streak')
    it.bar((17.0, 1.2), (16.2, 2.8), 1.2, 1.2, BLACK, 'metal', name='back spike')
    _frost(it, [(19.6, -1.6, 1.2), (20.2, -3.0, 1.4), (20.8, -4.5, 1.2), (21.2, -6.0, 1.3), (19.0, 0.4, 1.0), (18.4, 1.8, 1.1)])
    _ghost(it, 14.6, 3.0)
    return it


def ghost_shovel():
    it = BoxItem('ghost_shovel')
    _handle(it, 14.2)
    socket(it, 14.4, 1.6, 2.4, BLACK2)
    it.bar((15.0, 0), (20.4, 0), 5.8, 1.2, BLACK, 'metal', name='spade')
    it.bar((20.4, 0), (21.6, 0), 4.0, 1.1, BLACK, 'metal', name='spade tip')
    it.bar((15.4, 0.8), (20.0, 0.8), 0.3, 1.35, STREAK, 'metal', name='streak')
    for s in (-1, 1):
        it.bar((15.2, s * 2.75), (20.4, s * 2.75), 0.35, 1.3, '#3a3f4a', 'metal', name='spade edge')
    _frost(it, [(21.6, -1.6, 1.3), (22.0, 0.0, 1.2), (21.6, 1.6, 1.3), (20.4, 3.0, 1.2), (18.8, 3.4, 1.1), (17.2, 3.2, 1.2),
                (15.6, 3.0, 1.0)])
    _ghost(it, 13.0, 3.0)
    return it


def ghost_hoe():
    it = BoxItem('ghost_hoe')
    _handle(it, 20.4, grip_to=7.6)
    socket(it, 19.6, 2.0, 2.2, BLACK2)
    # long thin black blade sticking out to one side, frosty crust on top
    it.bar((19.8, -1.0), (19.2, -6.4), 1.9, 1.2, BLACK, 'metal', name='hoe blade')
    it.bar((19.2, -6.4), (17.8, -7.4), 1.1, 1.0, BLACK, 'metal', name='hoe tip')
    it.bar((18.6, -1.4), (17.9, -6.2), 0.35, 1.1, '#3a3f4a', 'metal', name='hoe edge')
    _frost(it, [(21.0, -0.8, 1.1), (20.8, -2.4, 1.3), (20.5, -4.0, 1.1), (20.1, -5.5, 1.2), (19.3, -7.0, 0.9), (21.4, 0.6, 0.9)])
    _frost(it, [(15.6, 1.4, 0.8), (13.4, 1.3, 0.7), (11.6, 1.4, 0.8)], d=1.2)
    _ghost(it, 10.2, 2.8)
    return it


TOOLS = (ghost_sword, ghost_pickaxe, ghost_axe, ghost_shovel, ghost_hoe)


def build(out, prev=None, render=None):
    done = set()
    for fn in TOOLS:
        m = fn().model()
        m.save(out, 'hd')
        for e in m.json('x')['elements']:
            assert all(-16 <= v <= 32 for v in e['from'] + e['to']), (m.name, e)
        done.add(m.name)
        if render and prev:
            render(m, os.path.join(prev, 'item_' + m.name + '.png'), size=260, yaw=20, pitch=14, zoom=1.45)
    return done


if __name__ == '__main__':
    import hd
    here = os.path.dirname(os.path.abspath(__file__))
    names = build(os.path.join(here, 'out'), os.path.join(here, 'preview'), hd.render)
    print(sorted(names))

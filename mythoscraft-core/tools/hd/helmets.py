"""3D helmets for every armour set (worn on the head like the masks)."""
from hd import Model, grad, metal, stripes, glow, crystal, fabric, fur, scales, solid, decal, rgb, shade, mix

GOLD = metal('#e8b02e', '#fff1a8')
DARKGOLD = metal('#a8721a', '#f0c860')
LAPIS = stripes('#e8b02e', '#1e40af', every=5, w2=3)
LAPIS_V = stripes('#e8b02e', '#1e40af', every=5, w2=3, horizontal=False)


def pharaoh_face(d, w, h, face, tile):
    # golden face: kohl eyes, blue brows, nose and mouth shading
    k = w / 64
    def R(x0, y0, x1, y1, c):
        d.rectangle([int(x0 * k), int(y0 * k), int(x1 * k) - 1, int(y1 * k) - 1], fill=c)
    blue, black, white, shadow = rgb('#1e3a8a'), rgb('#111827'), rgb('#f8fafc'), rgb('#9a6a12')
    # brows
    R(8, 8, 28, 12, blue); R(36, 8, 56, 12, blue)
    # eyes with kohl wing
    R(10, 16, 28, 22, black); R(36, 16, 54, 22, black)
    R(13, 17, 25, 21, white); R(39, 17, 51, 21, white)
    R(17, 17, 21, 21, black); R(43, 17, 47, 21, black)
    R(4, 20, 10, 22, black); R(54, 20, 60, 22, black)
    # nose
    R(29, 18, 35, 34, shadow); R(27, 32, 37, 36, shadow)
    # mouth
    R(24, 39, 40, 42, rgb('#7c2d12')); R(26, 42, 38, 44, shadow)
    # cheeks shading
    R(2, 26, 8, 44, shadow); R(56, 26, 62, 44, shadow)


def cobra(d, w, h, face, tile):
    d.rectangle([0, 0, w - 1, h // 3], fill=rgb('#dc2626'))
    d.rectangle([w // 3, h // 3, w - w // 3 - 1, h - 1], fill=rgb('#1d4ed8'))


def pharaoh():
    m = Model('pharaoh_helmet')
    # golden face mask
    m.add((0.5, 0, -0.9), (15.5, 12, -0.3), GOLD, faces={'north': decal(GOLD, pharaoh_face)}, name='face')
    # nemes headdress: crown, sides and back in lapis/gold stripes
    m.add((-0.8, 11.5, -0.8), (16.8, 17, 16.8), stripes('#e8b02e', '#1e40af', every=5, w2=3, horizontal=False), name='crown')
    m.add((-1.2, 13, -1.2), (17.2, 15, 0), GOLD, name='band')
    # side cloth: hugs the temples, then flares out over the shoulders
    m.add((-2.2, 4, 0.4), (-0.8, 12, 13), LAPIS, name='left side')
    m.add((16.8, 4, 0.4), (18.2, 12, 13), LAPIS, name='right side')
    m.add((-3.8, -4, 0), (-0.8, 4, 12), LAPIS, name='left flare')
    m.add((16.8, -4, 0), (19.8, 4, 12), LAPIS, name='right flare')
    m.add((-5.2, -9, -0.6), (-1.6, -4, 10), LAPIS, name='left shoulder')
    m.add((17.6, -9, -0.6), (21.2, -4, 10), LAPIS, name='right shoulder')
    # lappets hanging over the chest
    m.add((-4.2, -14, -1.6), (-0.2, -9, 1.4), LAPIS, name='left lappet')
    m.add((16.2, -14, -1.6), (20.2, -9, 1.4), LAPIS, name='right lappet')
    m.add((-4.4, -15, -1.8), (0, -14, 1.6), GOLD)
    m.add((16, -15, -1.8), (20.4, -14, 1.6), GOLD)
    # back and braid
    m.add((-0.8, 0, 13), (16.8, 11.5, 16.8), LAPIS, name='back')
    m.add((-0.8, -6, 12), (16.8, 0, 16.4), LAPIS, name='back drape')
    m.add((5, -12, 15.4), (11, -6, 17.4), LAPIS_V, name='braid')
    m.add((4.6, -13, 15.2), (11.4, -12, 17.6), GOLD)
    # uraeus (cobra) and false beard
    m.add((7, 15, -2.2), (9, 19, -1.2), GOLD, faces={'north': decal(GOLD, cobra)}, name='uraeus')
    m.add((6.5, -5, -1.2), (9.5, 0, 1), stripes('#e8b02e', '#1e40af', every=3, w2=2), name='beard')
    m.add((6, -6, -1.4), (10, -5, 1.2), GOLD)
    return m


HELMETS = {'pharaoh_helmet': pharaoh}


def anubis_pharaoh():
    """Pharaoh mask (nemes headdress with jackal ears), hugging the head like a real helmet: thin lapis/gold banded
    cloth around the sides and back, short lappets over the shoulders, gold brow with a lapis jewel and cobra,
    slim jackal ears. No face – the player's own face shows. Built from swatch cubes (clean faces, fine edges)."""
    from swatch import SwatchModel
    navy, gold, lapis, ink, tip = '#1d4f86', '#f2b52a', '#2c86d6', '#1b1622', '#9a96b6'
    m = SwatchModel('pharaoh_helmet')
    # cloth shell: sides and back 1.2 thick, top 1 thick
    m.add((-1.2, 16, -1.2), (17.2, 17, 17.2), navy, name='top')
    m.add((-1.2, 3, -0.6), (0, 16, 17.2), navy, name='left side')
    m.add((16, 3, -0.6), (17.2, 16, 17.2), navy, name='right side')
    m.add((0, 3, 16), (16, 16, 17.2), navy, name='back')
    # thin gold stripes laid over the cloth (every 2.6, like the reference)
    for y in (13.4, 10.8, 8.2, 5.6, 3.0):
        m.add((-1.5, y, -0.9), (-1.2, y + 1.0, 17.5), gold, 'metal', name='stripe l')
        m.add((17.2, y, -0.9), (17.5, y + 1.0, 17.5), gold, 'metal', name='stripe r')
        m.add((-1.2, y, 17.2), (17.2, y + 1.0, 17.5), gold, 'metal', name='stripe back')
    # brow band, jewel and cobra
    m.add((-1.4, 12.6, -1.6), (17.4, 16, 0), gold, 'metal', name='brow')
    m.add((6.6, 13.2, -2.0), (9.4, 15.4, -1.6), lapis, 'metal', name='jewel')
    m.add((7.4, 15.4, -2.0), (8.6, 18.6, -1.0), gold, 'metal', name='cobra')
    m.add((7.1, 17.8, -2.4), (8.9, 19.0, -1.0), lapis, 'metal', name='cobra head')
    # lappets over the shoulders (short, slightly narrowing) with stripes
    for side, x0, x1 in (('l', -2.4, 2.6), ('r', 13.4, 18.4)):
        m.add((x0, -5, -1.4), (x1, 3, 2.4), navy, name='lappet ' + side)
        for y in (0.4, -2.2, -4.6):
            m.add((x0 - 0.3, y, -1.7), (x1 + 0.3, y + 1.0, 2.7), gold, 'metal', name='lappet stripe')
    # slim jackal ears (gold rim at the base, grey tips)
    for x in (1.5, 11.5):
        m.add((x - 0.3, 17, 3.7), (x + 3.3, 17.8, 6.3), gold, 'metal', name='ear rim')
        m.add((x, 17.8, 4), (x + 3, 21.5, 6), ink, name='ear')
        m.add((x + 0.5, 21.5, 4.2), (x + 2.5, 24.5, 5.8), ink, name='ear top')
        m.add((x + 0.9, 24.5, 4.4), (x + 2.1, 26.2, 5.6), tip, name='ear tip')
    return m


HELMETS['pharaoh_helmet'] = anubis_pharaoh

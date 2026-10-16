"""HD masks and hats (cosmetics worn on the head)."""
from hd import Model, grad, metal, stripes, glow, crystal, fabric, fur, scales, solid, decal, rgb, shade, wood, leaves, glass
from helmets2 import carved_face, gem

GOLD = metal('#e8b02e', '#fff1a8')


def skull_face(d, w, h, face, tile):
    k = w / 64
    dark = (24, 20, 28)
    d.ellipse([10 * k, 18 * k, 28 * k, 34 * k], fill=dark)
    d.ellipse([36 * k, 18 * k, 54 * k, 34 * k], fill=dark)
    d.polygon([(32 * k, 36 * k), (28 * k, 44 * k), (36 * k, 44 * k)], fill=dark)
    for i in range(7):
        x = (14 + i * 5.4) * k
        d.rectangle([x, 50 * k, x + 3.6 * k, 58 * k], fill=(240, 236, 224))
    d.rectangle([12 * k, 48 * k, 52 * k, 49.5 * k], fill=dark)
    d.ellipse([16 * k, 23 * k, 20 * k, 27 * k], fill=(239, 68, 68))
    d.ellipse([44 * k, 23 * k, 48 * k, 27 * k], fill=(239, 68, 68))


def snowman_face(d, w, h, face, tile):
    k = w / 64
    coal = (30, 30, 34)
    d.rectangle([16 * k, 18 * k, 24 * k, 26 * k], fill=coal)
    d.rectangle([40 * k, 18 * k, 48 * k, 26 * k], fill=coal)
    for i, x in enumerate((14, 21, 28, 35, 42, 49)):
        y = 44 + (2 if i in (0, 5) else 0) + (2 if i in (1, 4) else 4)
        d.rectangle([x * k, y * k, (x + 4) * k, (y + 4) * k], fill=coal)


def hat(name):
    return Model(name)


def party_hat():
    m = hat('party_hat')
    stripe = stripes('#f472b6', '#38bdf8', every=3, w2=3)
    for i, (a, b) in enumerate(((2, 14), (3.5, 12.5), (5, 11), (6.3, 9.7), (7.2, 8.8))):
        m.add((a, 16 + i * 2.6, a), (b, 18.6 + i * 2.6, b), stripe, name='cone')
    m.add((6.5, 29, 6.5), (9.5, 32, 9.5), fur('#fde047', '#ffffff'), name='pompom')
    m.add((1.4, 16, 1.4), (14.6, 17, 14.6), fur('#facc15', '#fef08a'), name='rim')
    return m


def golden_crown():
    m = hat('golden_crown')
    m.add((-0.6, 16, -0.6), (16.6, 19.5, 16.6), GOLD, name='band')
    for x, z in ((0, -0.6), (5.5, -0.6), (11, -0.6), (0, 15.6), (5.5, 15.6), (11, 15.6)):
        m.add((x + 0.5, 19.5, z), (x + 4.5, 23, z + 1), GOLD, name='point')
    for x, z in ((-0.6, 3), (-0.6, 9.5), (15.6, 3), (15.6, 9.5)):
        m.add((x, 19.5, z), (x + 1, 22.5, z + 3.5), GOLD)
    m.add((6, 16.5, -1.4), (10, 19, -0.6), GOLD, faces={'north': decal(GOLD, gem('#dc2626'))}, name='ruby')
    m.add((-1.4, 16.5, 6), (-0.6, 19, 10), GOLD, faces={'west': decal(GOLD, gem('#2563eb'))})
    m.add((16.6, 16.5, 6), (17.4, 19, 10), GOLD, faces={'east': decal(GOLD, gem('#16a34a'))})
    m.add((1, 16.4, 1), (15, 17.6, 15), fabric('#7f1d1d', '#991b1b'), name='velvet')
    return m


def pumpkin_mask():
    m = hat('pumpkin_mask')
    rind = stripes('#f97316', '#c2410c', every=3, horizontal=False, w2=1)
    m.add((-0.6, -0.4, -0.6), (16.6, 16.8, 16.6), rind, faces={'north': decal(rind, carved_face('#fde047'))}, name='pumpkin')
    m.add((7, 16.8, 7), (9, 20, 9), wood('#4d7c0f', '#365314'), name='stem')
    m.add((5, 17, 8), (7.5, 17.8, 12), leaves('#65a30d', '#3f6212'), name='leaf')
    m.add((9, 19, 8), (12, 19.6, 9), wood('#4d7c0f', '#365314'), name='curl')
    return m


def skull_mask():
    m = hat('skull_mask')
    bone = grad('#f5f5f4', '#d6d3d1', noise=6)
    m.add((-0.6, 0, -0.6), (16.6, 16.8, 16.6), bone, faces={'north': decal(bone, skull_face)}, name='skull')
    m.add((-1, 12, 2), (17, 14, 12), grad('#e7e5e4', '#a8a29e'), name='crack band')
    m.add((3, -2, -1.2), (13, 1, 0), bone, name='jaw')
    return m


def witch_hat():
    m = hat('witch_hat')
    felt = fabric('#3b0764', '#4c1d95')
    m.add((-4, 16, -4), (20, 17, 20), felt, name='brim')
    m.add((0, 17, 0), (16, 21, 16), felt, name='crown')
    m.add((0, 19, 0), (16, 20.4, 16), metal('#a3e635', '#ecfccb'), name='band')
    m.add((6.5, 19, -0.8), (9.5, 21.4, 0), GOLD, name='buckle')
    m.add((2.5, 21, 3), (13.5, 25, 14), felt)
    m.add((4.5, 25, 6), (11.5, 29, 13), felt, rot={'origin': [8, 25, 9], 'axis': 'x', 'angle': 22.5})
    m.add((6.5, 27, 10), (9.5, 30.5, 14), felt, rot={'origin': [8, 27, 12], 'axis': 'x', 'angle': 45})
    m.add((7, 29.5, 14), (9, 31.5, 17), glow('#a3e635'), glow=True, name='star')
    return m


def santa_hat():
    m = hat('santa_hat')
    red = fabric('#dc2626', '#b91c1c')
    trim = fur('#ffffff', '#e2e8f0')
    m.add((-1, 15, -1), (17, 18.5, 17), trim, name='trim')
    m.add((0, 18.5, 0), (16, 22, 16), red)
    m.add((2, 22, 2), (14, 25, 13), red)
    m.add((4, 25, 5), (12, 27.5, 12), red, rot={'origin': [8, 25, 8], 'axis': 'x', 'angle': 22.5})
    m.add((6, 26, 11), (10, 29, 16), red, rot={'origin': [8, 26, 13], 'axis': 'x', 'angle': 45})
    m.add((6, 22, 17), (10, 26, 21), trim, name='pompom')
    return m


def reindeer_antlers():
    m = hat('reindeer_antlers')
    band = fabric('#7c2d12', '#9a3412')
    antler = wood('#a16207', '#713f12')
    m.add((-0.8, 15, 6), (16.8, 17, 9), band, name='headband')
    for sx, d in ((1, -1), (12, 1)):
        m.add((sx, 17, 7), (sx + 3, 21, 9), antler, name='antler')
        m.add((sx + d * 2, 20, 7), (sx + 3 + d * 2, 22, 9), antler)
        m.add((sx + d * 4, 21, 7), (sx + 2 + d * 4, 26, 9), antler)
        m.add((sx + d * 1, 23, 7), (sx + 2 + d * 1, 27, 9), antler)
        m.add((sx + d * 6, 24, 7), (sx + 2 + d * 6, 27, 9), antler)
    m.add((6.5, 4, -2.4), (9.5, 7, -0.6), glow('#ef4444', '#fecaca'), glow=True, name='red nose')
    m.add((-1.6, 13, 7), (-0.8, 16, 10), fur('#a16207', '#fbbf24'), name='ear')
    m.add((16.8, 13, 7), (17.6, 16, 10), fur('#a16207', '#fbbf24'))
    return m


def snowman_head():
    m = hat('snowman_head')
    snow = grad('#ffffff', '#dbeafe', noise=5)
    m.add((-0.8, -0.4, -0.8), (16.8, 16.8, 16.8), snow, faces={'north': decal(snow, snowman_face)}, name='snowball')
    m.add((7, 7, -4.6), (9, 9, -0.8), grad('#fb923c', '#c2410c'), name='carrot')
    m.add((7.4, 7.4, -6), (8.6, 8.6, -4.6), solid('#f97316'))
    hat_c = fabric('#18181b', '#27272a')
    m.add((-1.6, 16.8, -1.6), (17.6, 17.8, 17.6), hat_c, name='hat brim')
    m.add((1.5, 17.8, 1.5), (14.5, 25, 14.5), hat_c, name='top hat')
    m.add((1.4, 18, 1.4), (14.6, 19.5, 14.6), fabric('#dc2626', '#b91c1c'), name='ribbon')
    m.add((-0.8, -1.6, -0.8), (16.8, 1, 16.8), stripes('#dc2626', '#ffffff', every=2, horizontal=False), name='scarf')
    return m


def bunny_ears():
    m = hat('bunny_ears')
    fluff = fur('#ffffff', '#f1f5f9')
    pink = fabric('#f9a8d4', '#f472b6')
    m.add((-0.8, 15, 6), (16.8, 17, 9), fabric('#f472b6', '#ec4899'), name='headband')
    for x, ang in ((2, 22.5), (10, -22.5)):
        m.add((x, 17, 7), (x + 4, 30, 9), fluff, rot={'origin': [x + 2, 17, 8], 'axis': 'z', 'angle': ang}, name='ear')
        m.add((x + 1, 18, 6.6), (x + 3, 28, 7), pink, rot={'origin': [x + 2, 17, 8], 'axis': 'z', 'angle': ang})
    m.add((6, 17, 5.6), (10, 19, 6.4), pink, name='bow')
    return m


def heart_glasses():
    m = hat('heart_glasses')
    frame = metal('#be185d', '#fbcfe8')
    lens = glass('#f472b6', 190)
    m.add((0, 7, -1.4), (16, 8, -0.6), frame, name='bridge')
    m.add((1, 6, -1.6), (7, 11, -0.8), lens, name='left lens')
    m.add((9, 6, -1.6), (15, 11, -0.8), lens, name='right lens')
    m.add((1.5, 11, -1.6), (3.5, 12, -0.8), lens)
    m.add((4.5, 11, -1.6), (6.5, 12, -0.8), lens)
    m.add((9.5, 11, -1.6), (11.5, 12, -0.8), lens)
    m.add((12.5, 11, -1.6), (14.5, 12, -0.8), lens)
    m.add((-0.8, 9, -0.6), (0, 10, 12), frame, name='temple')
    m.add((16, 9, -0.6), (16.8, 10, 12), frame)
    return m


def sun_hat():
    m = hat('sun_hat')
    straw = stripes('#fde68a', '#eab308', every=2, w2=1, horizontal=False)
    m.add((-6, 16, -6), (22, 17, 22), straw, name='brim')
    m.add((0, 17, 0), (16, 22, 16), straw, name='crown')
    m.add((-0.2, 17.5, -0.2), (16.2, 19, 16.2), fabric('#38bdf8', '#0ea5e9'), name='ribbon')
    m.add((12, 18, -1), (16, 22, 1), solid('#f472b6'), name='flower')
    m.add((13.2, 19.2, -1.4), (14.8, 20.8, -1), solid('#fde047'))
    return m


def leaf_crown():
    m = hat('leaf_crown')
    leaf = leaves('#22c55e', '#166534', '#86efac')
    m.add((-0.8, 15, -0.8), (16.8, 17.4, 16.8), wood('#7c4a21', '#3f2410'), name='vine')
    for i in range(8):
        x = -1 + (i % 4) * 4.6
        z = -1.4 if i < 4 else 16.4
        m.add((x, 17, z), (x + 3, 21, z + 1), leaf, name='leaf')
    for z in (2, 7, 12):
        m.add((-1.4, 17, z), (-0.4, 20.5, z + 3), leaf)
        m.add((16.4, 17, z), (17.4, 20.5, z + 3), leaf)
    m.add((7, 17, -1.8), (9, 19, -1.2), solid('#f472b6'), name='flower')
    return m


def new_year_glasses():
    m = hat('new_year_glasses')
    frame = metal('#d4a017', '#fff3b0')
    lens = glass('#1e1b4b', 210)
    m.add((-1, 6, -1.6), (17, 11, -0.8), frame, name='frame')
    m.add((1, 6.6, -1.8), (7, 10.4, -1.4), lens)
    m.add((9, 6.6, -1.8), (15, 10.4, -1.4), lens)
    # big "2027" style digits as glowing bars on top
    for x in (1, 5, 9, 13):
        m.add((x, 11, -1.6), (x + 2.6, 15, -0.8), glow('#fde047', '#ffffff'), glow=True, name='digit')
    m.add((-0.8, 9, -0.8), (0, 10, 12), frame, name='temple')
    m.add((16, 9, -0.8), (16.8, 10, 12), frame)
    return m


def star_halo():
    m = hat('star_halo')
    star = glow('#fde047', '#ffffff')
    ring = glow('#c4b5fd', '#ffffff')
    m.add((1, 20, 1), (15, 20.8, 2), ring, glow=True, name='ring')
    m.add((1, 20, 14), (15, 20.8, 15), ring, glow=True)
    m.add((1, 20, 2), (2, 20.8, 14), ring, glow=True)
    m.add((14, 20, 2), (15, 20.8, 14), ring, glow=True)
    for x, z in ((7, 0), (7, 14), (0, 7), (14, 7)):
        m.add((x, 21, z), (x + 2, 23, z + 2), star, glow=True, name='star')
    return m


def top_hat():
    m = hat('top_hat')
    silk = fabric('#111827', '#1f2937')
    m.add((-3, 16, -3), (19, 17, 19), silk, name='brim')
    m.add((1, 17, 1), (15, 28, 15), silk, name='crown')
    m.add((0.8, 17.5, 0.8), (15.2, 20, 15.2), fabric('#7c3aed', '#6d28d9'), name='band')
    m.add((12, 18, -0.4), (14, 22, 0.8), solid('#f8fafc'), name='card')
    return m


def cat_ears():
    m = hat('cat_ears')
    furry = fur('#1f2937', '#4b5563')
    pink = fabric('#f9a8d4', '#f472b6')
    m.add((-0.8, 15, 5), (16.8, 17, 8), fabric('#111827', '#1f2937'), name='headband')
    for x in (1, 11):
        m.add((x, 17, 5), (x + 4, 19, 8), furry, name='ear')
        m.add((x + 0.5, 19, 5.5), (x + 3.5, 21, 7.5), furry)
        m.add((x + 1.2, 21, 6), (x + 2.8, 22.5, 7), furry)
        m.add((x + 1, 17.5, 4.6), (x + 3, 20, 5), pink)
    return m


HATS = {f.__name__: f for f in (party_hat, golden_crown, pumpkin_mask, skull_mask, witch_hat, santa_hat, reindeer_antlers,
                                snowman_head, bunny_ears, heart_glasses, sun_hat, leaf_crown, new_year_glasses, star_halo,
                                top_hat, cat_ears)}

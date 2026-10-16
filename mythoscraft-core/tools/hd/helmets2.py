"""The other 17 armour helmets (3D, worn on the head)."""
from hd import Model, grad, metal, stripes, glow, crystal, fabric, fur, scales, solid, decal, rgb, shade, cracks, wood, leaves, glass
from helmets import GOLD, HELMETS


def eyes(color, size=3, gap=3):
    c = rgb(color)

    def draw(d, w, h, face, tile):
        k = w / 64
        ey = int(h * 0.38)
        s = int(size * 4 * k * 0.9)
        cx = w // 2
        g = int(gap * 4 * k)
        d.rectangle([cx - g - s, ey, cx - g - 1, ey + s // 2], fill=c)
        d.rectangle([cx + g, ey, cx + g + s - 1, ey + s // 2], fill=c)
    return draw


def shell(m, mat, front=None, top=None, open_face=False, inset=0.6):
    """A shell around the head (-inset .. 16+inset). open_face: the front only gets a brow band."""
    a, b = -inset, 16 + inset
    m.add((a, 16, a), (b, b, b), top or mat, name='top')
    m.add((a, 0, 16), (b, 16, b), mat, name='back')
    m.add((a, 0, a), (0, 16, 16), mat, name='left')
    m.add((16, 0, a), (b, 16, 16), mat, name='right')
    if open_face:
        m.add((0, 12, a), (16, 16, 0), mat, name='brow')
    else:
        m.add((0, 0, a), (16, 16, 0), mat, faces={'north': front} if front else None, name='front')


def visor_slits(d, w, h, face, tile):
    k = w / 64
    for row in (22, 30):
        d.rectangle([int(6 * k), int(row * k), int(58 * k), int((row + 3) * k)], fill=(14, 14, 20))
    for i in range(6):
        x = int((14 + i * 7) * k)
        d.rectangle([x, int(42 * k), x + int(3 * k), int(52 * k)], fill=(14, 14, 20))
    d.rectangle([int(30 * k), int(4 * k), int(34 * k), int(60 * k)], fill=(205, 210, 220))


def carved_face(glow_c):
    g = rgb(glow_c)

    def draw(d, w, h, face, tile):
        k = w / 64
        d.polygon([(10 * k, 30 * k), (22 * k, 16 * k), (26 * k, 30 * k)], fill=g)
        d.polygon([(38 * k, 30 * k), (42 * k, 16 * k), (54 * k, 30 * k)], fill=g)
        d.polygon([(29 * k, 36 * k), (32 * k, 30 * k), (35 * k, 36 * k)], fill=g)
        pts = [(8, 42), (16, 50), (22, 44), (28, 52), (36, 44), (42, 52), (48, 44), (56, 42), (50, 56), (14, 56)]
        d.polygon([(x * k, y * k) for x, y in pts], fill=g)
    return draw


def ninja_wrap(d, w, h, face, tile):
    k = w / 64
    d.rectangle([0, int(22 * k), w - 1, int(32 * k)], fill=(232, 220, 196))
    d.rectangle([int(12 * k), int(25 * k), int(26 * k), int(29 * k)], fill=(20, 20, 24))
    d.rectangle([int(38 * k), int(25 * k), int(52 * k), int(29 * k)], fill=(20, 20, 24))


def gem(color):
    c = rgb(color)

    def draw(d, w, h, face, tile):
        cx, cy = w / 2, h / 2
        r = min(w, h) * 0.38
        d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=c)
        d.polygon([(cx, cy - r), (cx + r * 0.4, cy), (cx, cy + r * 0.2), (cx - r * 0.4, cy)], fill=shade(c, 1.35))
    return draw


def bolt(d, w, h, face, tile):
    kx, ky = w / 16, h / 16
    pts = [(9, 1), (4, 9), (8, 9), (6, 15), (12, 6), (8, 6), (10, 1)]
    d.polygon([(x * kx, y * ky) for x, y in pts], fill=(253, 224, 71))


def angel():
    m = Model('angel_helmet')
    white = metal('#f1f5f9', '#ffffff')
    shell(m, white, open_face=True)
    m.add((-0.9, 11.5, -0.9), (16.9, 13, 16.9), GOLD, name='circlet')
    m.add((7, 13, -1.6), (9, 15, -0.9), GOLD, faces={'north': decal(GOLD, gem('#38bdf8'))})
    halo = glow('#fde68a', '#ffffff')
    m.add((2, 20, 2), (14, 20.8, 3.2), halo, glow=True, name='halo')
    m.add((2, 20, 12.8), (14, 20.8, 14), halo, glow=True)
    m.add((2, 20, 3.2), (3.2, 20.8, 12.8), halo, glow=True)
    m.add((12.8, 20, 3.2), (14, 20.8, 12.8), halo, glow=True)
    feather = grad('#ffffff', '#cbd5e1', noise=5)
    m.add((-4.5, 8, 8), (-0.6, 15, 9.2), feather, name='wing')
    m.add((-4.5, 13, 9.2), (-1.5, 18, 10.4), feather)
    m.add((16.6, 8, 8), (20.5, 15, 9.2), feather, name='wing')
    m.add((17.5, 13, 9.2), (20.5, 18, 10.4), feather)
    return m


def astronaut():
    m = Model('astronaut_helmet')
    white = metal('#e2e8f0', '#ffffff')
    a, b = -1.2, 17.2
    m.add((a, 16, a), (b, 18.4, b), white, name='top')
    m.add((a, -1, 14), (b, 16, b), white, name='back')
    m.add((a, -1, a), (1, 16, 14), white, name='left')
    m.add((15, -1, a), (b, 16, 14), white, name='right')
    m.add((1, -1, a), (15, 3, 1), white, name='chin')
    m.add((1, 13, a), (15, 16, 1), white, name='forehead')
    m.add((1, 3, -1.4), (15, 13, -0.6), glass('#f59e0b', 170), name='visor')
    m.add((-2.2, 5, 4), (-1.2, 10, 9), metal('#94a3b8'), name='ear')
    m.add((17.2, 5, 4), (18.2, 10, 9), metal('#94a3b8'), name='ear')
    m.add((14, 18.4, 12), (15, 24, 13), metal('#cbd5e1'), name='antenna')
    m.add((13.6, 24, 11.6), (15.4, 25.8, 13.4), glow('#ef4444'), glow=True)
    m.add((-1.4, 10, 3), (-1.2, 12, 10), glow('#22d3ee'), glow=True, name='stripe')
    m.add((17.2, 10, 3), (17.4, 12, 10), glow('#22d3ee'), glow=True)
    return m


def crystal_h():
    m = Model('crystal_helmet')
    body = crystal('#c084fc', '#581c87')
    shell(m, metal('#4c1d95', '#a78bfa'), open_face=True)
    for i, (x, hgt) in enumerate(((1, 5), (4.5, 8), (8, 11), (11.5, 8), (14, 5))):
        m.add((x, 16, -0.8), (x + 2.2, 16 + hgt, 1.6), body, name='spike')
    m.add((6.5, 12.5, -1.6), (9.5, 15.5, -0.7), body, faces={'north': decal(body, gem('#f0abfc'))}, name='gem')
    m.add((-2, 10, 10), (-0.6, 17, 13), body)
    m.add((16.6, 10, 10), (18, 17, 13), body)
    return m


def dragon():
    m = Model('dragon_helmet')
    sc = scales('#7f1d1d', '#2a0a0a')
    shell(m, sc, open_face=True, top=scales('#991b1b', '#3f0d0d'))
    horn = metal('#e7e5e4', '#ffffff')
    for sx in (1, 12):
        m.add((sx, 15, 6), (sx + 3, 19, 9), horn, name='horn base')
        m.add((sx + 0.5, 18, 9), (sx + 2.5, 21, 13), horn)
        m.add((sx + 0.8, 20, 13), (sx + 2.2, 22, 17), horn)
    for i in range(4):
        m.add((7, 16.6, 1 + i * 4), (9, 18.5 + (1 if i % 2 else 0), 3 + i * 4), sc, name='spine')
    m.add((3, -1, -2.4), (13, 3, -0.6), sc, name='jaw')
    m.add((4, 9, -1.4), (12, 10.2, -0.6), glow('#f97316'), glow=True, name='brow glow')
    return m


def druid():
    m = Model('druid_helmet')
    bark = wood('#7c4a21', '#3f2410')
    leaf = leaves('#16a34a', '#14532d', '#86efac')
    m.add((-0.8, 11, -0.8), (16.8, 13.4, 16.8), bark, name='wreath')
    m.add((-1.6, 12.6, -1.6), (17.6, 15, 17.6), leaf, name='leaves')
    m.add((-0.6, 15, 3), (16.6, 16.6, 13), leaf)
    for sx, d in ((2, -1), (12, 1)):
        m.add((sx, 15, 7), (sx + 2, 20, 9), bark, name='antler')
        m.add((sx + d * 2, 19, 7), (sx + 2 + d * 2, 21, 9), bark)
        m.add((sx + d * 4, 20, 7), (sx + 2 + d * 4, 24, 9), bark)
        m.add((sx - d, 21, 7), (sx + 2 - d, 23.5, 9), bark)
    m.add((7, 13, -2.2), (9, 15, -1.6), glow('#4ade80'), glow=True, name='seed')
    return m


def frost():
    m = Model('frost_helmet')
    ice = crystal('#bae6fd', '#0369a1')
    steel = metal('#7dd3fc', '#f0f9ff')
    shell(m, steel, open_face=True)
    for x, hgt in ((0, 4), (3, 7), (6.5, 10), (10, 7), (13, 4)):
        m.add((x, 16, 0), (x + 3, 16 + hgt, 3), ice, name='ice spike')
    for x in (1, 4, 7, 10, 13):
        m.add((x, 9, -1.4), (x + 1.4, 12, -0.6), ice, name='icicle')
    m.add((-1.6, 2, 2), (-0.6, 14, 14), ice)
    m.add((16.6, 2, 2), (17.6, 14, 14), ice)
    return m


def inferno():
    m = Model('inferno_helmet')
    rock = cracks('#292524', '#f97316')
    shell(m, rock, open_face=True)
    fire = glow('#f97316', '#fde047')
    m.add((-1, 15, 4), (3, 19, 8), rock, name='horn')
    m.add((-1, 19, 6), (2, 23, 9), rock)
    m.add((-0.5, 23, 7), (1.5, 26, 9), fire, glow=True)
    m.add((13, 15, 4), (17, 19, 8), rock, name='horn')
    m.add((14, 19, 6), (17, 23, 9), rock)
    m.add((14.5, 23, 7), (16.5, 26, 9), fire, glow=True)
    m.add((5, 16.6, 5), (11, 18, 11), fire, glow=True, name='crown fire')
    m.add((6.5, 18, 6.5), (9.5, 21, 9.5), fire, glow=True)
    return m


def knight():
    m = Model('knight_helmet')
    steel = metal('#9ca3af', '#f3f4f6')
    shell(m, steel, front=decal(steel, visor_slits))
    m.add((-1, 15.5, -1), (17, 17.4, 17), metal('#6b7280'), name='rim')
    m.add((7.2, 16.6, 0), (8.8, 19, 16), steel, name='ridge')
    plume = fur('#dc2626', '#fca5a5')
    m.add((7, 19, 2), (9, 23, 8), plume, name='plume')
    m.add((7, 18, 8), (9, 22, 14), plume)
    m.add((7, 14, 14), (9, 20, 19), plume)
    m.add((-1, 6, 4), (-0.6, 10, 8), GOLD, name='rivet')
    m.add((16.6, 6, 4), (17, 10, 8), GOLD)
    return m


def miner():
    m = Model('miner_helmet')
    yellow = grad('#facc15', '#ca8a04', noise=4)
    m.add((-1, 12, -1), (17, 17.6, 17), yellow, name='hard hat')
    m.add((-2.5, 11.4, -3), (18.5, 12.4, 18.5), yellow, name='brim')
    m.add((7.2, 17.6, -1), (8.8, 18.6, 17), yellow, name='ridge')
    m.add((5, 13, -2.2), (11, 17, -1), metal('#475569'), name='lamp')
    m.add((6, 14, -2.6), (10, 16, -2.2), glow('#fef3c7', '#ffffff'), glow=True, name='light')
    m.add((-0.8, 7, 2), (0, 12, 4), fabric('#78350f'), name='strap')
    m.add((16, 7, 2), (16.8, 12, 4), fabric('#78350f'))
    return m


def mythos():
    m = Model('mythos_helmet')
    body = grad('#7c3aed', '#3b0764', noise=5)
    shell(m, body, open_face=True)
    m.add((-1, 11, -1), (17, 13.4, 17), GOLD, name='crown band')
    for x in (0, 4, 8, 12):
        m.add((x + 0.5, 13.4, -1), (x + 3, 17 + (3 if x in (4, 8) else 1.5), 1), GOLD, name='crown point')
    m.add((6.5, 13.5, -1.8), (9.5, 16.5, -1), GOLD, faces={'north': decal(GOLD, gem('#f472b6'))}, name='gem')
    m.add((5, 16.6, 5), (11, 17.4, 11), glow('#c084fc', '#ffffff'), glow=True, name='aura')
    m.add((-2, 7, 6), (-1, 14, 10), GOLD, name='wing')
    m.add((17, 7, 6), (18, 14, 10), GOLD)
    return m


def ninja():
    m = Model('ninja_helmet')
    cloth = fabric('#18181b', '#27272a')
    shell(m, cloth, front=decal(cloth, ninja_wrap), inset=0.5)
    band = fabric('#b91c1c', '#7f1d1d')
    m.add((-0.9, 11, -0.9), (16.9, 13, 16.9), band, name='headband')
    m.add((6.5, 11.2, -1.3), (9.5, 12.8, -0.9), metal('#cbd5e1'), name='plate')
    m.add((10, 6, 16.9), (12, 11, 18), band, rot={'origin': [11, 11, 17], 'axis': 'x', 'angle': 22.5}, name='tail')
    m.add((12.5, 5, 16.9), (14.5, 11, 18), band, rot={'origin': [13.5, 11, 17], 'axis': 'x', 'angle': 45}, name='tail')
    return m


def ocean():
    m = Model('ocean_helmet')
    prism = scales('#14b8a6', '#134e4a')
    shell(m, prism, open_face=True)
    fin = grad('#5eead4', '#0f766e')
    m.add((7.2, 16.6, 1), (8.8, 22, 12), fin, name='crest fin')
    m.add((-3.5, 7, 6), (-0.6, 15, 7.5), fin, name='side fin')
    m.add((16.6, 7, 6), (19.5, 15, 7.5), fin)
    pearl = metal('#fde68a')
    m.add((6, 12.6, -1.6), (10, 15.4, -0.6), pearl, faces={'north': decal(pearl, gem('#38bdf8'))}, name='pearl')
    m.add((2, 12, -1.2), (4, 14, -0.6), solid('#f9a8d4'), name='shell')
    m.add((12, 12, -1.2), (14, 14, -0.6), solid('#f9a8d4'))
    return m


def pumpkin():
    m = Model('pumpkin_helmet')
    rind = stripes('#ea580c', '#c2410c', every=3, horizontal=False, w2=1)
    m.add((-0.8, -0.4, -0.8), (16.8, 16.8, 16.8), rind, faces={'north': decal(rind, carved_face('#fde047'))}, name='pumpkin')
    m.add((7, 16.8, 7), (9, 19.5, 9), wood('#4d7c0f', '#365314'), name='stem')
    m.add((9, 17, 6), (13, 17.8, 10), leaves('#65a30d', '#3f6212'), name='leaf')
    return m


def sculk():
    m = Model('sculk_helmet')
    body = cracks('#0f2a2e', '#22d3ee')
    shell(m, body, open_face=True)
    tendril = glow('#22d3ee', '#cffafe')
    for x, z, hgt in ((2, 3, 5), (6, 9, 7), (11, 4, 6), (13, 12, 4), (4, 13, 4)):
        m.add((x, 16.6, z), (x + 1.2, 16.6 + hgt, z + 1.2), tendril, glow=True, name='sensor')
    m.add((-1.4, 6, 3), (-0.6, 14, 4.2), tendril, glow=True)
    m.add((16.6, 6, 3), (17.4, 14, 4.2), tendril, glow=True)
    return m


def sky():
    m = Model('sky_helmet')
    body = grad('#f8fafc', '#bae6fd', noise=4)
    shell(m, body, open_face=True)
    feather = grad('#ffffff', '#7dd3fc', noise=6)
    m.add((-5, 9, 6), (-0.6, 13, 7.4), feather, name='wing')
    m.add((-5, 12, 7.4), (-1.6, 17, 8.8), feather)
    m.add((-4.5, 16, 8.8), (-2.5, 20, 10.2), feather)
    m.add((16.6, 9, 6), (21, 13, 7.4), feather, name='wing')
    m.add((17.6, 12, 7.4), (21, 17, 8.8), feather)
    m.add((18.5, 16, 8.8), (20.5, 20, 10.2), feather)
    m.add((6, 12.5, -1.6), (10, 15, -0.6), glow('#7dd3fc'), glow=True, name='sky gem')
    return m


def storm():
    m = Model('storm_helmet')
    body = metal('#1e3a8a', '#60a5fa')
    shell(m, body, open_face=True)
    crest = glow('#fde047', '#ffffff')
    m.add((7, 16.6, -1), (9, 19, 3), crest, glow=True, name='bolt')
    m.add((7, 18, 3), (9, 21, 7), crest, glow=True)
    m.add((7, 20, 7), (9, 24, 11), crest, glow=True)
    m.add((-1, 3, 4), (-0.6, 14, 12), body, faces={'west': decal(body, bolt)}, name='side bolt')
    m.add((16.6, 3, 4), (17, 14, 12), body, faces={'east': decal(body, bolt)})
    return m


def viking():
    m = Model('viking_helmet')
    steel = metal('#a8a29e', '#f5f5f4')
    m.add((-0.8, 11, -0.8), (16.8, 17, 16.8), steel, name='cap')
    m.add((7.2, 17, -0.8), (8.8, 17.8, 16.8), metal('#78716c'), name='ridge')
    trim = fur('#78350f', '#d6a76c')
    m.add((-1.2, 10, -1.2), (17.2, 11.6, 17.2), trim, name='fur trim')
    m.add((7, 3, -1.6), (9, 11, -0.8), steel, name='nose guard')
    horn = grad('#fef3c7', '#a16207')
    m.add((-4, 12, 6), (-0.8, 15, 9), horn, name='left horn')
    m.add((-6, 14, 6.5), (-3.5, 18, 8.5), horn)
    m.add((-6.5, 18, 7), (-4.5, 21, 8), horn)
    m.add((16.8, 12, 6), (20, 15, 9), horn, name='right horn')
    m.add((19.5, 14, 6.5), (22, 18, 8.5), horn)
    m.add((20.5, 18, 7), (22.5, 21, 8), horn)
    m.add((-0.8, 4, 12), (16.8, 11, 16.8), trim, name='neck fur')
    return m


HELMETS.update({
    'angel_helmet': angel, 'astronaut_helmet': astronaut, 'crystal_helmet': crystal_h, 'dragon_helmet': dragon,
    'druid_helmet': druid, 'frost_helmet': frost, 'inferno_helmet': inferno, 'knight_helmet': knight,
    'miner_helmet': miner, 'mythos_helmet': mythos, 'ninja_helmet': ninja, 'ocean_helmet': ocean,
    'pumpkin_helmet': pumpkin, 'sculk_helmet': sculk, 'sky_helmet': sky, 'storm_helmet': storm, 'viking_helmet': viking,
})

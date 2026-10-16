"""All held items as 3D voxel models (see voxel.py). u: 0 handle end … ~54 tip, v: across (+ = upper left)."""
from voxel import (Item, R, ramp, jagged, tri, bow, rose, thorns, vine, ornament_hub, rod, disc, gem, box, blade, guard, crescent, axe, arc, star, spiral, hook, blob, shard)


# ------------------------------------------------------------------ building blocks
def hilt(grip, wrap, metal, gemm=None, at=14, half=6.5, curl=0.25, pommel=None):
    p = [rod(2, at, 2.6, R[grip], R[wrap], 3), disc(2.4, 0, 2.2, R[pommel or metal]), guard(at, half, 2.6, R[metal], curl=curl)]
    if gemm:
        p.append(gem(at, 0, 1.9, R[gemm]))
    return p


def sword(name, bl, edge=None, grip='leather', wrap='gold', metal='gold', gemm='red', curve=0.0, w=(5, 3.4), glow=False,
          extra=(), spark=None, serr=0, tip=0.28, half=6.5):
    parts = hilt(grip, wrap, metal, gemm, half=half) + [blade(15, 53, w[0], w[1], R[bl], edge=R[edge] if edge else None,
                                                              curve=curve, glow_edge=glow, serrate=serr, tip=tip)] + list(extra)
    return Item(name, parts, sparkles=[(R[spark], 8, (26, 54), (-6, 6))] if spark else None)


def shaft(mat='wood', wrap='darkwood', top=46, w=2.4, every=5):
    return [rod(2, top, w, R[mat], R[wrap], every), disc(2.2, 0, 1.8, R[wrap])]


def pickaxe(name, head, edge, sh=('wood', 'darkwood'), gemm='gold', spark=None, extra=()):
    parts = shaft(*sh) + [crescent(44, 18, 6.5, R[head], bend=0.022, edge=R[edge]), gem(44, 0, 2, R[gemm])] + list(extra)
    return Item(name, parts, sparkles=[(R[spark], 9, (36, 54), (-16, 16))] if spark else None)


def axe_item(name, head, edge, sh=('wood', 'darkwood'), double=False, glow=False, spark=None, extra=()):
    parts = shaft(*sh, top=48) + [axe(42, 13, 5, 15, R[head], 1, edge=R[edge], glow_edge=glow)]
    if double:
        parts.append(axe(42, 13, 5, 15, R[head], -1, edge=R[edge], glow_edge=glow))
    parts += [box(40, 45, -1.8, 1.8, R['steel'])] + list(extra)
    return Item(name, parts, sparkles=[(R[spark], 8, (32, 54), (-14, 14))] if spark else None)


def hammer(name, head, trim, sh=('wood', 'darkwood'), spark=None, extra=()):
    parts = shaft(*sh, top=44) + [box(39, 51, -9, 9, R[head], depth=6), box(38.5, 51.5, -9.5, -7, R[trim]),
                                  box(38.5, 51.5, 7, 9.5, R[trim]), box(43, 47, -3, 3, R[trim])] + list(extra)
    return Item(name, parts, sparkles=[(R[spark], 10, (34, 56), (-14, 14))] if spark else None)


def staff(name, sh, wrap, top, spark=None, length=40):
    parts = [rod(2, length, 2.6, R[sh], R[wrap], 4), disc(2.2, 0, 2, R[wrap])] + list(top)
    return Item(name, parts, sparkles=[(R[spark], 12, (length - 4, 56), (-12, 12))] if spark else None)


def wand(name, sh, wrap, tip, spark=None, length=34):
    return staff(name, sh, wrap, tip, spark, length)


def scythe(name, blade_mat, edge, sh=('darkwood', 'wood'), glow=False, spark=None, extra=()):
    parts = shaft(*sh, top=52) + [arc(36, -3, 14, 6, 175, 40, R[blade_mat], depth=3, edge=R[edge], glow_edge=glow),
                                  box(48, 53, -2.2, 2.2, R['steel'])] + list(extra)
    return Item(name, parts, sparkles=[(R[spark], 8, (30, 56), (-16, 14))] if spark else None)


def hoe(name, head, edge, sh=('wood', 'darkwood'), spark=None, extra=()):
    parts = shaft(*sh, top=50) + [box(45, 51, 0, 12, R[head], depth=4), box(45, 51, 10, 13, R[edge])] + list(extra)
    return Item(name, parts, sparkles=[(R[spark], 8, (36, 56), (-6, 16))] if spark else None)


def shovel(name, head, edge, sh=('wood', 'darkwood'), spark=None, extra=()):
    parts = shaft(*sh, top=36) + [blob([(44, 0, 6.5), (48, 0, 6)], R[head], depth=3), blade(38, 55, 11, 9, R[head], R[edge], tip=0.25)] + list(extra)
    return Item(name, parts, sparkles=[(R[spark], 8, (36, 56), (-10, 10))] if spark else None)


def flames(u, v, s, mat='ember', core='yellow'):
    return [blob([(u, v, 3 * s), (u + 3 * s, v + 1.5 * s, 2.2 * s), (u + 5 * s, v - 1 * s, 1.6 * s), (u + 6.5 * s, v + 0.6 * s, 1 * s)], R[mat], glow=True),
            blob([(u, v, 1.6 * s), (u + 2.2 * s, v + 0.8 * s, 1.1 * s)], R[core], glow=True)]


# ------------------------------------------------------------------ the collections
ITEMS = {}


def add(item):
    ITEMS[item.name] = item


# Halloween
add(sword('bat_blade', 'obsidian', 'crimson', grip='black', wrap='crimson', metal='dark', gemm='red', curve=-0.03, glow=True,
          extra=[shard(16, 2, 22, 10, 3, R['obsidian'], glow=False), shard(16, -2, 22, -10, 3, R['obsidian'], glow=False)], spark='crimson'))
add(scythe('pumpkin_scythe', 'obsidian', 'pumpkin', glow=True, spark='pumpkin',
           extra=[blob([(50, 0, 4.4), (50, 3, 3.6), (50, -3, 3.6)], R['pumpkin'], depth=4), box(54, 56, -0.8, 0.8, R['green']),
                  gem(50, 1.5, 0.9, R['yellow']), gem(50, -1.5, 0.9, R['yellow'])]))
add(staff('witch_broom', 'darkwood', 'purple', [blob([(42, 0, 4), (46, 0, 5), (50, 0, 5.5), (53, 0, 4.5)], R['sand'], depth=3),
                                                 box(39, 41.5, -4, 4, R['purple'])], length=42))
add(staff('witch_broom_ride', 'darkwood', 'purple', [blob([(42, 0, 4), (46, 0, 5), (50, 0, 5.5), (53, 0, 4.5)], R['sand'], depth=3),
                                                      box(39, 41.5, -4, 4, R['purple'])], spark='violet', length=42))

# Frostfest
add(sword('frost_blade', 'ice', 'white', grip='navy', wrap='silver', metal='silver', gemm='cyan', glow=True, spark='ice',
          extra=[shard(30, 0, 34, 6, 2, R['ice']), shard(38, 0, 42, -6, 2, R['ice'])]))
add(Item('candy_cane', [spiral(2, 42, 3.4, R['red'], R['white'], 3), hook(42, -5, 5, 3.4, R['red'], R['white'], -10, 180),
                        blob([(16, 2.5, 2), (17, -2.5, 2)], R['green']), gem(16.5, 0, 1.6, R['red'])], sparkles=[(R['white'], 8, (30, 54), (-12, 6))]))
add(sword('nutcracker_sword', 'silver', 'white', grip='crimson', wrap='gold', metal='gold', gemm='green', spark='white',
          extra=[box(15, 18, -3, 3, R['red'])]))
add(wand('ice_wand', 'silver', 'cyan', [shard(34, 0, 50, 0, 5, R['ice']), shard(36, 1, 44, 7, 2.6, R['ice']), shard(36, -1, 44, -7, 2.6, R['ice'])], spark='ice'))

# Celestial / space
add(sword('star_saber', 'violet', 'white', grip='navy', wrap='silver', metal='silver', gemm='yellow', glow=True, w=(3.6, 3.2), tip=0.1,
          extra=[star(18, 0, 3.4, R['yellow'])], spark='yellow'))
add(wand('void_scepter', 'obsidian', 'violet', [disc(42, 0, 6, R['void'], ring=R['gold']), gem(42, 0, 3.2, R['violet']),
                                                star(50, 0, 2.4, R['violet'])], spark='violet'))
add(staff('meteor_staff', 'stone', 'ember', [disc(46, 0, 6.5, R['obsidian'], depth=5)] + flames(46, 0, 1.2) +
          [blob([(44, 2, 1.5), (48, -2, 1.3)], R['lava'], glow=True)], spark='ember'))
add(Item('meteor', [blob([(26, 0, 11), (30, 4, 7), (22, -4, 7)], R['obsidian'], depth=8), blob([(24, 3, 2.4), (30, -2, 2), (20, -5, 1.6)], R['lava'], glow=True)]
         + flames(36, 0, 1.5), sparkles=[(R['ember'], 10, (14, 50), (-14, 14))]))
add(Item('meteor_core', [blob([(28, 0, 11)], R['lava'], depth=8, glow=True), blob([(28, 0, 6)], R['yellow'], depth=10, glow=True),
                         shard(28, 8, 30, 14, 3, R['obsidian'], glow=False), shard(28, -8, 26, -14, 3, R['obsidian'], glow=False)],
         sparkles=[(R['ember'], 12, (12, 46), (-16, 16))]))
add(Item('laser_gun', [box(16, 38, -4, 3, R['silver'], depth=6), box(36, 52, -1.5, 1.5, R['steel'], depth=4), box(51, 54, -2, 2, R['cyan']),
                       box(12, 19, -8, -3, R['navy'], depth=5), box(24, 30, -6.5, -4, R['steel']), gem(24, 1, 1.8, R['cyan'])],
         sparkles=[(R['cyan'], 6, (50, 56), (-4, 4))]))

# Inferno
add(axe_item('inferno_axe', 'obsidian', 'lava', ('black', 'crimson'), double=True, glow=True, spark='ember'))
add(wand('ember_wand', 'darkwood', 'gold', flames(38, 0, 1.6), spark='ember'))
add(pickaxe('smelter_pickaxe', 'obsidian', 'lava', ('black', 'ember'), gemm='lava', spark='ember'))

# Dragon
add(sword('dragon_blade', 'emerald', 'gold', grip='darkwood', wrap='gold', metal='gold', gemm='red', curve=0.02, serr=4, spark='emerald',
          extra=[blob([(15, 7, 2.4), (15, -7, 2.4)], R['emerald'])]))
add(staff('dragon_staff', 'darkwood', 'gold', [hook(44, 0, 6, 2.6, R['gold'], None, -170, 170), disc(44, 0, 3.4, R['red'], depth=6, glow=True),
                                               shard(50, 4, 56, 8, 2.4, R['emerald'], glow=False), shard(50, -4, 56, -8, 2.4, R['emerald'], glow=False)],
          spark='red'))

# Ocean
add(sword('coral_blade', 'coral', 'aqua', grip='navy', wrap='aqua', metal='teal', gemm='aqua', serr=5, spark='aqua',
          extra=[blob([(32, 5, 2), (40, -5, 2)], R['pink'])]))
add(staff('tide_staff', 'teal', 'gold', [rod(40, 54, 2.2, R['gold']), rod(40, 50, 2, R['gold']),
                                          blade(44, 56, 2.6, 2, R['aqua'], tip=0.5), guard(42, 7, 2.2, R['gold'], curl=-0.4),
                                          blade(42, 52, 2.4, 2, R['aqua'], off=6, tip=0.5), blade(42, 52, 2.4, 2, R['aqua'], off=-6, tip=0.5),
                                          gem(40, 0, 2, R['aqua'])], spark='aqua'))

# Forest / spring / farm
add(staff('druid_staff', 'darkwood', 'leaf', [hook(44, 0, 6, 2.8, R['wood'], None, -160, 160), blob([(48, 6, 3), (52, 2, 3), (50, -5, 3)], R['leaf']),
                                              gem(44, 0, 3, R['emerald'])], spark='leaf'))
add(wand('blossom_wand', 'wood', 'pink', [blob([(38, 0, 3.2), (41, 3, 2.8), (41, -3, 2.8), (44, 0, 3), (40, 0, 2)], R['pink'], depth=3),
                                          gem(40.5, 0, 1.6, R['yellow']), blob([(35, 3, 1.6), (35, -3, 1.6)], R['leaf'])], spark='pink'))
add(axe_item('timber_axe', 'steel', 'iron', ('wood', 'leather'), spark=None))
add(Item('harvest_sickle', shaft('wood', 'leather', top=20) + [arc(32, -2, 12, 4.5, 180, -10, R['iron'], depth=3, edge=R['silver'])]))
add(sword('carrot_blade', 'orange', 'pumpkin', grip='wood', wrap='leaf', metal='leaf', gemm='lime', w=(6, 3), tip=0.6,
          extra=[blob([(13, 4, 2.6), (12, -4, 2.6), (11, 0, 2.4)], R['leaf'])]))

# Storm / sky
add(hammer('thunder_hammer', 'steel', 'gold', ('navy', 'gold'), spark='yellow',
           extra=[shard(45, 0, 45, 0.1, 0.1, R['yellow']), gem(45, 0, 2, R['cyan'])]))
add(sword('wind_blade', 'sky', 'cloud', grip='navy', wrap='silver', metal='silver', gemm='cyan', curve=0.035, spark='cloud',
          extra=[blob([(15, 7, 2.6), (14, 9, 1.8), (15, -7, 2.6), (14, -9, 1.8)], R['cloud'])]))
add(sword('cloud_sword', 'cloud', 'sky', grip='sky', wrap='white', metal='white', gemm='sky', w=(6, 5), spark='cloud',
          extra=[blob([(16, 0, 4), (18, 4, 3), (18, -4, 3)], R['cloud'])]))

# Crystal / ancient / mythos
add(pickaxe('crystal_pickaxe', 'amethyst', 'violet', ('darkwood', 'violet'), gemm='violet', spark='violet',
            extra=[shard(44, 10, 50, 18, 3, R['amethyst']), shard(44, -10, 48, -18, 3, R['amethyst'])]))
add(wand('crystal_wand', 'silver', 'violet', [shard(32, 0, 52, 0, 6, R['amethyst']), shard(34, 2, 44, 9, 3, R['violet']),
                                               shard(34, -2, 44, -9, 3, R['violet'])], spark='violet'))
add(Item('ancient_relic', [shard(10, 0, 50, 0, 14, R['teal']), shard(14, 4, 40, 14, 6, R['cyan']), shard(14, -4, 38, -14, 6, R['cyan']),
                           gem(26, 0, 3, R['white'])], sparkles=[(R['cyan'], 10, (10, 54), (-16, 16))]))
add(sword('khopesh', 'gold', 'yellow', grip='navy', wrap='gold', metal='gold', gemm='cyan', w=(3.6, 5), tip=0.15,
          extra=[arc(42, -6, 9, 5, 10, 170, R['gold'], edge=R['yellow'])]))
add(staff('pharaoh_staff', 'navy', 'gold', [disc(44, 0, 5, R['gold'], ring=R['navy']), gem(44, 0, 2.4, R['cyan']), crescent(52, 7, 2.6, R['gold'], bend=0.08)],
          spark='yellow'))
add(hammer('mythos_hammer', 'violet', 'gold', ('obsidian', 'gold'), spark='violet', extra=[gem(45, 0, 2.6, R['violet'])]))
add(sword('warden_blade', 'obsidian', 'teal', grip='black', wrap='teal', metal='navy', gemm='teal', glow=True, serr=6, spark='teal'))

# Mining / tools
add(pickaxe('ore_pickaxe', 'cyan', 'ice', gemm='emerald', extra=[gem(44, 12, 1.4, R['gold']), gem(44, -12, 1.4, R['red'])]))
add(pickaxe('spawner_pickaxe', 'black', 'red', ('obsidian', 'red'), gemm='red', spark='red'))
add(shovel('excavator', 'steel', 'yellow', ('black', 'yellow')))
add(Item('chainsaw', [box(4, 16, -2.5, 2.5, R['black'], depth=4), box(12, 24, -6, 6, R['orange'], depth=7), box(14, 20, 6, 9, R['black']),
                      blade(23, 54, 7, 6, R['steel'], edge=R['iron'], tip=0.12, serrate=2), gem(18, 0, 2, R['yellow'])]))
add(wand('builder_wand', 'wood', 'gold', [box(36, 46, -5, 5, R['copper'], depth=6), box(40, 42, -5.5, 5.5, R['gold']), gem(41, 0, 2, R['emerald'])],
         spark='yellow'))
add(wand('region_wand', 'silver', 'cyan', [disc(40, 0, 5, R['navy'], ring=R['cyan']), star(40, 0, 3, R['cyan'])], spark='cyan'))

# Silvester (new year) – gold with firework sparks
for kind in ('sword', 'pickaxe', 'axe', 'hoe'):
    n = 'newyear_' + kind
    sp = 'rainbow'
    if kind == 'sword':
        add(sword(n, 'gold', 'yellow', grip='black', wrap='gold', metal='silver', gemm='pink', glow=True, spark=sp))
    elif kind == 'pickaxe':
        add(pickaxe(n, 'gold', 'yellow', ('black', 'gold'), gemm='pink', spark=sp))
    elif kind == 'axe':
        add(axe_item(n, 'gold', 'yellow', ('black', 'gold'), glow=True, spark=sp))
    else:
        add(hoe(n, 'gold', 'yellow', ('black', 'gold'), spark=sp))

# Tournament trophies (upright)
for n, cup, base in (('trophy_daily', 'copper', 'darkwood'), ('trophy_weekly', 'silver', 'navy'), ('trophy_monthly', 'gold', 'obsidian')):
    ITEMS[n] = Item(n, [box(1, 6, -9, 9, R[base], depth=8), box(6, 8, -6, 6, R[cup], depth=6), rod(8, 16, 3, R[cup], depth=4),
                        blob([(24, 0, 10)], R[cup], depth=9), box(26, 34, -10, 10, R[cup], depth=9),
                        hook(26, 10.5, 4, 2, R[cup], None, -100, 100), hook(26, -10.5, 4, 2, R[cup], None, 80, 180), hook(26, -10.5, 4, 2, R[cup], None, -180, -80),
                        star(26, 0, 4, R['yellow'])],
                    sparkles=[(R['yellow'], 8, (20, 38), (-14, 14))], upright=True)


# ------------------------------------------------------------------ premium look (proportions after the reference pictures)
R.update({k: ramp(v) for k, v in {'darkteal': '1f4a44', 'bone2': 'efe6cf', 'holly': '1f7a34', 'ginger': 'c9772f',
                                   'tree': '2a8a3a', 'redbow': 'e02a2a'}.items()})


def wrapped(u0, u1, w=2.4, mat='bone2', band='teal', every=3):
    return [rod(u0, u1, w, R[mat], R[band], every), disc(u0 + 0.4, 0, 1.7, R[band])]


# jagged emerald-obsidian blade, ornate gold guard, pale wrapped grip (~30 % grip)
add(Item('dragon_blade', wrapped(2, 16, 2.6, 'bone2', 'teal') + [
    guard(16.5, 7.5, 2.4, R['gold'], curl=0.35), box(15, 18.5, -3, 3, R['gold'], depth=5), gem(16.8, 0, 1.8, R['emerald']),
    jagged(18, 54, 4.6, R['darkteal'], R['emerald'], period=5, spike=3.2, side=1),
    jagged(18, 50, 2, R['emerald'], None, period=7, spike=1.4, side=-1, glow=True)],
    sparkles=[(R['emerald'], 7, (24, 54), (-6, 8))]))

# dark spiked pickaxe with a gold socket (~55 % shaft, head spans nearly the full width)
add(Item('ore_pickaxe', wrapped(2, 42, 2.4, 'bone2', 'teal', 4) + [
    crescent(41, 18, 6.5, R['darkteal'], bend=0.02, edge=R['teal']),
    shard(43, 6, 48, 10, 2.6, R['darkteal'], glow=False), shard(43, -6, 48, -10, 2.6, R['darkteal'], glow=False),
    shard(40, 12, 43, 16, 2.2, R['darkteal'], glow=False), shard(40, -12, 43, -16, 2.2, R['darkteal'], glow=False),
    ornament_hub(41, 4.6, R['gold'], R['cyan'])], sparkles=[(R['teal'], 6, (36, 54), (-18, 18))]))

# double axe, dark spiked heads, gold hub
add(Item('inferno_axe', wrapped(2, 46, 2.4, 'bone2', 'ember', 4) + [
    axe(37, 12, 6, 16, R['obsidian'], 1, edge=R['lava'], glow_edge=True), axe(37, 12, 6, 16, R['obsidian'], -1, edge=R['lava'], glow_edge=True),
    shard(41, 2, 48, 3, 2.4, R['obsidian'], glow=False),
    ornament_hub(37, 4.4, R['gold'], R['lava'])], sparkles=[(R['ember'], 8, (32, 54), (-16, 16))]))
add(Item('newyear_axe', wrapped(2, 46, 2.4, 'black', 'gold', 4) + [
    axe(37, 12, 6, 16, R['gold'], 1, edge=R['yellow'], glow_edge=True), axe(37, 12, 6, 16, R['gold'], -1, edge=R['yellow'], glow_edge=True),
    ornament_hub(37, 4.4, R['silver'], R['pink'])], sparkles=[(R['rainbow'], 10, (32, 54), (-16, 16))]))

# amethyst cluster pickaxe, black grip with red bands
add(Item('crystal_pickaxe', wrapped(2, 42, 2.6, 'black', 'red', 3) + [
    crescent(43, 17, 5, R['purple'], bend=0.03),
    shard(43, 0, 49, 0, 6, R['amethyst']), shard(42, 4, 46, 13, 5, R['amethyst']), shard(42, -4, 46, -13, 5, R['amethyst']),
    shard(41, 10, 40, 20, 4, R['violet']), shard(41, -10, 40, -20, 4, R['violet']), shard(45, 7, 51, 12, 3, R['violet']),
    shard(45, -7, 51, -12, 3, R['violet'])], sparkles=[(R['violet'], 10, (34, 56), (-20, 20))]))

# christmas tree wand on a candy-cane grip with a red bow
add(Item('ice_wand', [spiral(2, 20, 3, R['red'], R['white'], 2)] + [
    tri(20, 12, 11, R['tree'], 5), tri(28, 11, 8.5, R['tree'], 5), tri(35, 10, 6.5, R['tree'], 5), tri(41, 9, 4.5, R['tree'], 5),
    gem(26, 4, 1.2, R['red']), gem(27, -5, 1.2, R['yellow']), gem(33, 2, 1.2, R['cyan']), gem(32, -4, 1.2, R['red']),
    gem(39, -1.5, 1.2, R['yellow']), gem(44, 1.5, 1.1, R['red']), gem(30, 7, 1.1, R['cyan']),
    star(51, 0, 3.4, R['yellow']), bow(19, 4, R['redbow'])], sparkles=[(R['white'], 10, (20, 56), (-14, 14))]))

# gingerbread stars on a candy grip with bow and holly
add(Item('candy_cane', [spiral(2, 36, 3.2, R['red'], R['white'], 2), bow(35, 4.2, R['redbow']),
                        blob([(37, 4, 2), (38, -4, 2)], R['holly']), gem(37, 0, 1.4, R['red'])] + [
    star(44, 5, 4, R['ginger'], glow=False), star(48, -4, 3.6, R['ginger'], glow=False), star(52, 3, 3.4, R['ginger'], glow=False),
    star(42, -7, 3, R['ginger'], glow=False), star(49, 10, 3, R['ginger'], glow=False)],
    sparkles=[(R['white'], 8, (36, 56), (-14, 14))]))

# rose on a thorny stem with curling vines and a gold ring
add(Item('blossom_wand', [rod(2, 44, 2, R['holly']), thorns(6, 40, 5, R['green']), vine(10, 30, 4, 10, 1.2, R['leaf']),
                          box(8, 9.5, -2, 2, R['gold']), blob([(38, 4, 2.4), (36, -4, 2.2)], R['leaf']),
                          rose(47, 0, 6.5, R['rose'])], sparkles=[(R['pink'], 6, (40, 56), (-10, 10))]))

# zig-zag lightning blade
add(Item('wind_blade', wrapped(2, 14, 2.2, 'silver', 'black', 2) + [guard(14.5, 5, 2, R['black']),
    jagged(15, 54, 2.2, R['cloud'], R['sky'], period=4, spike=3, side=0)], sparkles=[(R['cloud'], 8, (20, 54), (-8, 8))]))

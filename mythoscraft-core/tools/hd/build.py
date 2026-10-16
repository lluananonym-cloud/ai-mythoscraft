"""Builds all HD content into tools/hd/out (PackGenerator copies it over the generated pack) + previews.

    source env, then:  python tools/hd/build.py   and   java tools/PackGenerator.java resourcepack

1. helmets (3D, worn on the head), 2. masks & hats, 3. armour layers 128x64,
4. every element-based held item converted to its own painted HD texture."""
import glob
import json
import re
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import hd  # noqa: E402
import helmets  # noqa: E402
import helmets2  # noqa: E402,F401
import hats  # noqa: E402
import equipment  # noqa: E402
import convert  # noqa: E402
import sky  # noqa: E402
import items_box  # noqa: E402
import companions  # noqa: E402
import swatch  # noqa: E402
import sprite3d  # noqa: E402
import relics  # noqa: E402
import ghost_tools  # noqa: E402

# masks that really are a face keep their picture; every other mask has no face of its own
FACE_PICTURES = {'pumpkin_mask', 'pumpkin_helmet', 'skull_mask', 'snowman_head'}

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(HERE, 'out')
PREV = os.path.join(HERE, 'preview')


CUSTOM_IDS = set(re.findall(r'spec\("([a-z0-9_]+)"', open(os.path.join(ROOT, 'src', 'main', 'java', 'online', 'mythoscraft',
                                                                     'core', 'item', 'CustomItem.java'), encoding='utf-8').read()))


def original_models():
    """Runs the generator into a temp folder (without the overlay) to get the original element models."""
    tmp = tempfile.mkdtemp(prefix='mcpack')
    hold = OUT + '.hold'
    if os.path.isdir(OUT):
        shutil.move(OUT, hold)
    try:
        subprocess.run(['java', 'tools/PackGenerator.java', tmp], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    finally:
        if os.path.isdir(hold):
            shutil.rmtree(hold)
    return tmp


def main():
    src = original_models()
    shutil.rmtree(OUT, ignore_errors=True)
    shutil.rmtree(PREV, ignore_errors=True)
    os.makedirs(PREV, exist_ok=True)
    done = set()
    for table in (helmets.HELMETS, hats.HATS, companions.COMPANIONS):
        for name, make in table.items():
            m = make()
            if not isinstance(m, swatch.SwatchModel):
                m = swatch.from_model(m, keep_pictures=name in FACE_PICTURES)
            m.save(OUT, 'hd')
            hd.render(m, os.path.join(PREV, name + '.png'), size=300)
            done.add(name)
    equipment.build(OUT, PREV)
    sky.build(OUT, PREV)
    box_items = items_box.build_all()
    for name, m in box_items.items():
        m.save(OUT, 'hd')
        hd.render(m, os.path.join(PREV, 'item_' + name + '.png'), size=260, yaw=20, pitch=14, zoom=1.45)
        done.add(name)
    # relic crate items, spawner booster, shadow companion, zap tools (own module, also runnable on its own)
    done |= relics.build(OUT, PREV, hd.render)
    done |= ghost_tools.build(OUT, PREV, hd.render)
    converted = 0
    for path in sorted(glob.glob(os.path.join(src, 'assets', 'mythoscraft', 'models', 'item', '*.json'))):
        name = os.path.splitext(os.path.basename(path))[0]
        if name in done:
            continue
        raw = json.load(open(path, encoding='utf-8'))
        if 'elements' not in raw and any(name == i or name.startswith(i + '_') for i in CUSTOM_IDS):
            sm = sprite3d.convert(name, raw, os.path.join(src, 'assets', 'mythoscraft', 'models', 'item'),
                                  os.path.join(src, 'assets', 'mythoscraft', 'textures'))
            if sm:
                sm.save(OUT, 'hd')
                converted += 1
                hd.render(sm, os.path.join(PREV, 'item_' + name + '.png'), size=220, yaw=20, pitch=14, zoom=1.45)
            continue
        m = convert.convert(path, OUT, name)
        if m:
            converted += 1
            hd.render(m, os.path.join(PREV, 'item_' + name + '.png'), size=220, yaw=120, pitch=20)
    shutil.rmtree(src, ignore_errors=True)
    print('helmets', len(helmets.HELMETS), 'hats', len(hats.HATS), 'armour sets', len(equipment.SETS), '3d items', len(box_items), 'converted', converted)


if __name__ == '__main__':
    main()

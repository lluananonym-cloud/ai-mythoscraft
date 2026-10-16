"""Contact sheet of all 3D items: python tools/hd/sheet.py out.png"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hd
from items3d import ITEMS
from PIL import Image, ImageDraw
names = sys.argv[2:] or list(ITEMS)
S = 220
cols = 8
im = Image.new('RGB', (S * cols, S * ((len(names) + cols - 1) // cols)), (26, 22, 38))
d = ImageDraw.Draw(im)
for i, n in enumerate(names):
    m = ITEMS[n].model()
    m.build()
    tile = hd.render(m, sys.argv[1] + '.tmp.png', size=S + 120, yaw=35, pitch=25).crop((60, 60, S + 60, S + 60))
    im.paste(tile, ((i % cols) * S, (i // cols) * S))
    d.text(((i % cols) * S + 4, (i // cols) * S + 4), f'{n} ({len(m.boxes)})', fill=(200, 200, 200))
os.remove(sys.argv[1] + '.tmp.png')
im.save(sys.argv[1])

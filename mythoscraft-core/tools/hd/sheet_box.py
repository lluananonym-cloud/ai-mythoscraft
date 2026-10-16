"""Contact sheet of the box items: python tools/hd/sheet_box.py out.png [names...]"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hd
import items_box
from PIL import Image, ImageDraw
models = items_box.build_all()
names = sys.argv[2:] or list(models)
S, cols = 230, 7
im = Image.new('RGB', (S * cols, S * ((len(names) + cols - 1) // cols)), (40, 40, 48))
d = ImageDraw.Draw(im)
bad = []
for i, n in enumerate(names):
    m = models[n]
    m.build()
    bad += [n for c in m.cubes if any(v < -16 or v > 32 for v in c.frm + c.to)]
    t = hd.render(m, sys.argv[1] + '.t.png', size=S, yaw=20, pitch=14, background=(40, 40, 48), zoom=1.45)
    im.paste(t, ((i % cols) * S, (i // cols) * S))
    d.text(((i % cols) * S + 4, (i // cols) * S + 4), f'{n} ({len(m.cubes)})', fill=(220, 220, 220))
os.remove(sys.argv[1] + '.t.png')
im.save(sys.argv[1])
print('out of range:', sorted(set(bad)))

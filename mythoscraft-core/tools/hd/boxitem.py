"""Held items built like hand-made Blockbench models: clean swatch cubes ("bars") that may be rotated in 22.5° steps,
so blades and handles are single smooth cubes instead of pixel staircases.

Item frame: u runs along the item (0 = handle end … 22 = tip), v across it (+v = right side when the tip points up),
z is the thickness around the model centre. The frame is laid diagonally like vanilla tools (tip to the upper right).
Any direction in 22.5° steps can be expressed with Minecraft's element rotation (-45…45 on the z axis), using the
element's local x or y as the long axis."""
import math

from hd import rgb, mix
from swatch import SwatchModel

C45 = math.cos(math.radians(45))


def tone(c, f):
    """f > 0 lighter (warm), f < 0 darker (cool) – hex in, hex out"""
    b = rgb(c)
    m = mix(b, (255, 248, 225), f) if f > 0 else mix(b, (18, 10, 40), -f)
    return '#%02x%02x%02x' % tuple(m)


def world(u, v):
    return 8 + (u - 11) * C45 + v * C45, 8 + (u - 11) * C45 - v * C45


class BoxItem:
    def __init__(self, name, display='voxel'):
        self.m = SwatchModel(name, display)
        self.name = name

    def bar(self, p0, p1, w, d, color, style='matte', z=0.0, glow=False, name=''):
        """cube from frame point p0=(u,v) to p1 (centre line), width w across, thickness d (z offset z)"""
        (x0, y0), (x1, y1) = world(*p0), world(*p1)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        L = math.hypot(x1 - x0, y1 - y0)
        theta = math.degrees(math.atan2(y1 - y0, x1 - x0)) if L > 1e-6 else 45.0
        theta = round(theta / 22.5) * 22.5
        while theta > 135:
            theta -= 180
        while theta <= -45:
            theta += 180
        if theta <= 45:
            a, sx, sy = theta, L, w            # long axis = local x
        else:
            a, sx, sy = theta - 90, w, L       # long axis = local y
        cz = 8 + z
        frm = [cx - sx / 2, cy - sy / 2, cz - d / 2]
        to = [cx + sx / 2, cy + sy / 2, cz + d / 2]
        rot = None if abs(a) < 1e-6 else {'origin': [round(cx, 4), round(cy, 4), round(cz, 4)], 'axis': 'z', 'angle': a}
        return self.m.add(frm, to, color, style, rot=rot, glow=glow, name=name)

    def block(self, u, v, s, color, style='matte', d=None, z=0.0, glow=False, name=''):
        return self.bar((u - s / 2, v), (u + s / 2, v), s, d if d is not None else s, color, style, z, glow, name)

    def model(self):
        return self.m


# ====================================================================== parts
def handle(it, u0, u1, w, col, wrap=None, every=1.5, band=0.5, d=None):
    """shaft; the wrap bands only cover the grip zone (lower part, max 5.5 long) plus one collar at the top"""
    it.bar((u0, 0), (u1, 0), w, d or w, col, name='grip')
    if wrap:
        zone = u0 + min(5.5, (u1 - u0) * 0.45)
        u = u0 + 0.5
        while u + band < min(zone, u1 - 0.2):
            it.bar((u, 0), (u + band, 0), w + 0.3, (d or w) + 0.3, wrap, name='wrap')
            u += every
        if u1 - u0 > 8:
            it.bar((u1 - 0.9, 0), (u1 - 0.2, 0), w + 0.4, (d or w) + 0.4, wrap, name='collar')


def pommel(it, u, s, col, style='metal'):
    it.bar((u - s / 2, 0), (u + s / 2, 0), s, s, col, style, name='pommel')


def gem(it, u, v, s, col, glow=True, z=0.0):
    it.block(u, v, s, col, 'glow' if glow else 'metal', d=s + 0.5, z=z, glow=glow, name='gem')


def guard(it, u, half, h, col, d=1.6, curl=1.0, style='metal'):
    it.bar((u, -half), (u, half), h, d, col, style, name='guard')
    if curl:
        for s in (-1, 1):
            it.bar((u, s * half), (u + curl, s * (half + curl * 0.6)), h * 0.85, d * 0.9, col, style, name='guard tip')


def blade(it, u0, u1, w, col, edge=None, ridge=None, d=0.8, tip=3.2, style='metal', glow_edge=False, w_end=None):
    """straight blade: body, light edge strips, a raised ridge, a tapering tip of 3 cubes"""
    w_end = w_end or w * 0.9
    body_end = u1 - tip
    it.bar((u0, 0), (body_end, 0), (w + w_end) / 2, d, col, style, name='blade')
    if edge:
        for s in (-1, 1):
            it.bar((u0 + 0.3, s * (w / 2 - 0.15)), (body_end, s * (w_end / 2 - 0.15)), 0.4, d * 0.75, edge,
                   'glow' if glow_edge else style, glow=glow_edge, name='edge')
    if ridge:
        it.bar((u0 + 0.6, 0), (body_end - 0.4, 0), 0.45, d + 0.25, ridge, style, name='ridge')
    for k, (a, b, f) in enumerate(((0, 0.45, 0.72), (0.45, 0.8, 0.46), (0.8, 1.0, 0.2))):
        it.bar((body_end + tip * a, 0), (body_end + tip * b, 0), w_end * f, d * (1 - k * 0.15), col, style, name='tip')
    if edge:
        it.bar((body_end, 0), (u1, 0), 0.3, d * 0.6, edge, 'glow' if glow_edge else style, glow=glow_edge, name='tip edge')


def teeth(it, u0, u1, side, base_v, col, every=1.7, size=1.3, d=0.7, style='metal', glow=False):
    """saw teeth along one blade side, leaning toward the tip"""
    u = u0
    while u < u1:
        it.bar((u, side * base_v), (u + size * 0.8, side * (base_v + size)), 0.65, d, col, style, glow=glow, name='tooth')
        u += every


def pick_head(it, u, span, thick, col, edge, style='metal', d=1.4, droop=1.8):
    it.bar((u, -2), (u, 2), thick, d, col, style, name='head')
    for s in (-1, 1):
        it.bar((u, s * 1.8), (u - droop * 0.35, s * span * 0.6), thick * 0.9, d * 0.95, col, style, name='arm')
        it.bar((u - droop * 0.35, s * span * 0.6), (u - droop, s * span), thick * 0.6, d * 0.85, col, style, name='arm tip')
        it.bar((u - droop * 0.92, s * span * 0.95), (u - droop * 1.12, s * span * 1.03), thick * 0.35, d * 0.7, edge,
               style, name='point')


def axe_head(it, u, side, reach, h0, h1, col, edge, style='metal', d=0.9, glow_edge=False):
    n = 4
    step = reach / n
    for i in range(n):
        v = side * (0.9 + step * (i + 0.5))
        h = h0 + (h1 - h0) * (i / (n - 1))
        it.bar((u - h / 2, v), (u + h / 2, v), step + 0.02, d, col, style, name='axe')
    it.bar((u - h1 / 2 - 0.2, side * (0.9 + reach + 0.2)), (u + h1 / 2 + 0.2, side * (0.9 + reach + 0.2)), 0.5, d * 0.8,
           edge, 'glow' if glow_edge else style, glow=glow_edge, name='axe edge')


def socket(it, u, h, w, col, style='metal'):
    it.bar((u - h / 2, 0), (u + h / 2, 0), w, w, col, style, name='socket')


def orb(it, u, v, r, col, style='glow', glow=True, ring=None):
    it.block(u, v, r * 1.6, col, style, d=r * 1.6, glow=glow, name='orb')
    it.bar((u - r * 0.55, v), (u + r * 0.55, v), r * 2, r * 1.1, col, style, glow=glow, name='orb x')
    it.bar((u - r, v), (u + r, v), r * 1.1, r * 1.1, col, style, glow=glow, name='orb y')
    it.bar((u - r * 0.55, v), (u + r * 0.55, v), r * 1.1, r * 2, col, style, glow=glow, name='orb z')
    if ring:
        it.bar((u - r * 1.15, v - r * 1.15), (u + r * 1.15, v + r * 1.15), 0.4, 0.5, ring, 'metal', name='ring')


def shard(it, p0, p1, w, col, glow=True, d=None):
    (u0, v0), (u1, v1) = p0, p1
    um, vm = u0 + (u1 - u0) * 0.62, v0 + (v1 - v0) * 0.62
    it.bar(p0, (um, vm), w, d or w, col, 'glow' if glow else 'metal', glow=glow, name='shard')
    it.bar((um, vm), p1, w * 0.5, (d or w) * 0.6, tone(col, 0.35), 'glow' if glow else 'metal', glow=glow, name='shard tip')


def flame(it, u, v, s, col='#ff8a1c', core='#ffe066'):
    for k, (du, dv, f) in enumerate(((0, 0, 1.0), (1.1, 0.4, 0.75), (2.0, -0.2, 0.5), (2.8, 0.3, 0.28))):
        it.block(u + du * s, v + dv * s, 1.8 * s * f, col, 'glow', glow=True, name='flame')
    it.block(u + 0.2 * s, v, 1.0 * s, core, 'glow', glow=True, name='flame core')


def sparkles(it, pts, col):
    """no floating effect cubes – the items should look like solid, real objects"""
    return

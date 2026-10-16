"""Voxel-sprite items in the "premium pack" style: pixel art on a fine grid, extruded with depth (thicker cores, thin
rims, gems that pop out), hue-shifted shading ramps (cool shadows, warm lights), dark rims and floating sparkles.

An item is a list of parts. Each part is a function (u, v) -> (ramp, t, depth[, glow]) or None, where u runs along
the item (handle end → tip, diagonal like vanilla tools) and v across it (positive = upper-left, the lit side).
t in 0..1 picks the shade, depth is the thickness in voxels."""
import math
import random

from hd import Model, Box, rgb, mix

N = 28                # grid cells per side (big readable cubes like the premium packs)
DESIGN = 40           # parts are drawn in a 40-cell design space
WIDE = 1.25           # everything 25 % wider than drawn (chunkier like the reference items)
CELL = 16 / N         # model units per cell
PX = 2 / CELL         # 2 texels per voxel face
SHADOW = rgb('1b1030')
LIGHT = rgb('fff4d6')


def ramp(base, n=6):
    """dark → light, hue shifted like hand-made pixel art."""
    b = rgb(base)
    return [mix(b, SHADOW, 0.72), mix(b, SHADOW, 0.48), mix(b, SHADOW, 0.22), b, mix(b, LIGHT, 0.35), mix(b, LIGHT, 0.7)][:n]


R = {k: ramp(v) for k, v in {
    'gold': 'e0a628', 'iron': 'a7b0bd', 'steel': '8d99aa', 'dark': '3a2d52', 'void': '2b1745', 'wood': '8a5a2c',
    'darkwood': '5a3a20', 'leather': '6b3a22', 'red': 'd8313a', 'crimson': '9e1530', 'white': 'e8ecf2',
    'green': '2f9a3c', 'leaf': '58b83a', 'teal': '1fa59a', 'cyan': '46d4e8', 'ice': '9fe6ff', 'blue': '2f62d8',
    'navy': '22305e', 'purple': '7c3ad0', 'violet': 'b25cf0', 'pink': 'f27bb2', 'rose': 'e0405e', 'orange': 'f07a1c',
    'ember': 'ff9a2a', 'lava': 'ff5a1c', 'yellow': 'f6d23c', 'bone': 'e6dcc0', 'sand': 'd8b878', 'stone': '8a8a90',
    'coral': 'ff7a6a', 'aqua': '38c8c0', 'sky': '8cc8ff', 'cloud': 'f2f6ff', 'emerald': '2ed27a', 'amethyst': 'a45cf0',
    'obsidian': '231a33', 'copper': 'c8743c', 'black': '2a2a33', 'pumpkin': 'f08a1c', 'lime': '9ae03c',
    'brown': '7a4a26', 'silver': 'c8d0dc', 'rainbow': 'ff6ad5'}.items()}


def _clamp(t):
    return 0.0 if t < 0 else 1.0 if t > 1 else t


# ---------------------------------------------------------------------------------------------- parts
def rod(u0, u1, w, mat, wrap=None, every=3, depth=2):
    """handle / shaft; wrap = second ramp for bands every few cells."""
    def f(u, v):
        if u0 <= u <= u1 and abs(v) <= w / 2:
            m = wrap if wrap and int(u - u0) % every == 0 else mat
            return m, 0.5 + 0.42 * v / max(0.5, w / 2), depth
    return f


def disc(uc, vc, r, mat, depth=3, glow=False, ring=None):
    def f(u, v):
        d = math.hypot(u - uc, v - vc)
        if d <= r:
            m = ring if ring and d > r - 1.1 else mat
            t = 0.5 + 0.45 * ((v - vc) - (u - uc) * 0.3) / r + 0.15 * (1 - d / r)
            return m, t, depth, glow
    return f


def gem(uc, vc, r, mat, depth=4, glow=True):
    def f(u, v):
        d = abs(u - uc) + abs(v - vc)
        if d <= r:
            t = 0.95 if (v - vc) > 0 and d < r * 0.5 else 0.35 + 0.5 * (1 - d / r)
            return mat, t, depth, glow
    return f


def box(ua, ub, va, vb, mat, depth=3, bevel=True):
    def f(u, v):
        if ua <= u <= ub and va <= v <= vb:
            t = 0.55 + 0.35 * ((v - va) / max(0.5, vb - va) - 0.5) * 2 * 0.5
            if bevel and (u - ua < 1 or v - va < 1):
                t -= 0.2
            if bevel and (vb - v < 1):
                t += 0.25
            return mat, t, depth
    return f


def blade(u0, u1, w0, w1, mat, edge=None, curve=0.0, off=0.0, tip=0.3, depth=2, glow_edge=False, serrate=0):
    """straight or curved blade; width w0 at the hilt to w1, tapering to a point over the last `tip` share."""
    L = u1 - u0

    def f(u, v):
        if not (u0 <= u <= u1):
            return None
        s = (u - u0) / L
        c = off + curve * s * s * L
        w = w0 + (w1 - w0) * s
        if s > 1 - tip:
            w *= (1 - s) / tip
        if serrate and int(u) % serrate == 0:
            w += 0.8
        dv = v - c
        if abs(dv) > w / 2:
            return None
        rel = dv / max(0.5, w / 2)
        if edge and rel > 0.55:
            return edge, 0.8 + 0.2 * rel, max(1, depth - 1), glow_edge
        if edge and rel < -0.7:
            return edge, 0.35, max(1, depth - 1), glow_edge
        t = 0.75 if rel > 0 else 0.4  # bright upper bevel, darker lower bevel, ridge between
        if abs(rel) < 0.18:
            t = 0.6
        return mat, t, depth + (1 if abs(rel) < 0.3 else 0)
    return f


def guard(uc, half, thick, mat, depth=3, curl=0.0):
    def f(u, v):
        cu = uc + curl * (v / half) ** 2 * half if half else uc
        if abs(v) <= half and abs(u - cu) <= thick / 2:
            return mat, 0.5 + 0.35 * (u - cu) / max(0.5, thick / 2) + 0.15 * v / half, depth
    return f


def crescent(uc, R, thick, mat, bend=0.06, depth=3, edge=None, one_side=0):
    """pickaxe-like head across the shaft; one_side=+1/-1 only on one side."""
    def f(u, v):
        if abs(v) > R or (one_side and v * one_side < -1):
            return None
        th = thick * (1 - (abs(v) / R) ** 1.6) + 0.6
        cu = uc - bend * v * v
        du = u - cu
        if abs(du) <= th / 2:
            rel = du / (th / 2)
            if edge and abs(v) > R * 0.75:
                return edge, 0.8, depth - 1
            return mat, 0.55 + 0.35 * rel, depth
    return f


def axe(uc, R, h0, h1, mat, side=1, depth=2, edge=None, glow_edge=False):
    """fan shaped axe head on one side of the shaft."""
    def f(u, v):
        sv = v * side
        if not (0 <= sv <= R):
            return None
        h = h0 + (h1 - h0) * (sv / R) ** 1.4
        du = u - uc
        if abs(du) <= h / 2:
            if edge and sv > R - 1.4:
                return edge, 0.9, depth, glow_edge
            return mat, 0.45 + 0.4 * du / max(0.5, h / 2) * 0.5 + 0.25 * sv / R, depth + (1 if sv < R * 0.4 else 0)
    return f


def arc(cu, cv, r, th, a0, a1, mat, depth=2, edge=None, glow_edge=False):
    """curved blade (scythe, khopesh) along a circle, tapering toward a1."""
    def f(u, v):
        d = math.hypot(u - cu, v - cv)
        a = math.degrees(math.atan2(v - cv, u - cu))
        lo, hi = min(a0, a1), max(a0, a1)
        if not (lo <= a <= hi):
            return None
        s = (a - a0) / (a1 - a0)  # 0 at a0 (thick) … 1 at a1 (tip)
        w = th * (1 - s) + 0.6
        if not (r - w <= d <= r):
            return None
        if edge and d > r - 1.0:
            return edge, 0.9, depth, glow_edge
        return mat, 0.35 + 0.5 * (d - (r - w)) / w, depth
    return f


def star(uc, vc, r, mat, depth=3, glow=True, points=5):
    def f(u, v):
        du, dv = u - uc, v - vc
        d = math.hypot(du, dv)
        a = math.atan2(dv, du)
        rr = r * (0.5 + 0.5 * abs(math.cos(a * points / 2)))
        if d <= rr:
            return mat, 0.45 + 0.5 * (1 - d / r), depth, glow
    return f


def spiral(u0, u1, w, a, b, every=3, depth=2):
    """candy-cane stripes"""
    def f(u, v):
        if u0 <= u <= u1 and abs(v) <= w / 2:
            m = a if int(u + v * 1.2) % (every * 2) < every else b
            return m, 0.5 + 0.4 * v / (w / 2), depth
    return f


def hook(cu, cv, r, w, mat_a, mat_b=None, a0=-90, a1=180, every=3, depth=2):
    """bent top (candy cane, staff hook)"""
    def f(u, v):
        d = math.hypot(u - cu, v - cv)
        a = math.degrees(math.atan2(v - cv, u - cu))
        if a0 <= a <= a1 and abs(d - r) <= w / 2:
            m = mat_b if mat_b and int((a - a0) / 18) % 2 else mat_a
            return m, 0.5 + 0.4 * (d - r) / (w / 2), depth
    return f


def blob(points, mat, depth=3, glow=False):
    """union of circles [(u, v, r)] – leaves, flames, clouds, crystals"""
    def f(u, v):
        best = None
        for (pu, pv, r) in points:
            d = math.hypot(u - pu, v - pv)
            if d <= r:
                t = 0.4 + 0.5 * ((v - pv) / r * 0.6 + (1 - d / r) * 0.5)
                if best is None or t > best:
                    best = t
        if best is not None:
            return mat, best, depth, glow
    return f


def shard(u0, v0, u1, v1, w, mat, depth=3, glow=True):
    """crystal shard from (u0,v0) to tip (u1,v1)"""
    L = math.hypot(u1 - u0, v1 - v0)
    du, dv = (u1 - u0) / L, (v1 - v0) / L

    def f(u, v):
        s = ((u - u0) * du + (v - v0) * dv)
        if not (0 <= s <= L):
            return None
        q = (-(u - u0) * dv + (v - v0) * du)
        ww = w * (1 - (s / L) ** 1.5) / 2 + 0.3
        if abs(q) <= ww:
            return mat, 0.85 if q > 0 else 0.4, depth, glow
    return f


# ---------------------------------------------------------------------------------------------- build
class Item:
    def __init__(self, name, parts, sparkles=None, upright=False, seed=0):
        self.name = name
        self.parts = parts
        self.sparkles = sparkles or []   # (ramp, count, (u0,u1), (v0,v1))
        self.upright = upright
        self.seed = seed

    k = 1.0  # fit scale (< 1 shrinks the item so nothing is cut at the model border)

    def uv(self, x, y):
        cx, cy = (x + 0.5) * DESIGN / N, (y + 0.5) * DESIGN / N
        if self.upright:
            return (cy - 1) / self.k, (cx - DESIGN / 2) / WIDE / self.k
        return (cx + cy - 2) / math.sqrt(2) / self.k, (cy - cx) / math.sqrt(2) / WIDE / self.k

    def fit(self):
        """largest scale at which no part reaches outside the 16x16 model area"""
        for k in [1 - i * 0.03 for i in range(15)]:
            self.k = k
            if not any(any(p(*self.uv(x, y)) for p in self.parts)
                       for y in range(-12, N + 12) for x in range(-12, N + 12) if not (0 <= x < N and 0 <= y < N)):
                return k
        return self.k

    def raster(self):
        self.fit()
        cells = {}
        for y in range(N):
            for x in range(N):
                u, v = self.uv(x, y)
                hit = None
                for p in self.parts:
                    r = p(u, v)
                    if r:
                        hit = r
                if hit:
                    m, t, d = hit[0], hit[1], hit[2]
                    glow = hit[3] if len(hit) > 3 else False
                    cells[(x, y)] = [m, _clamp(t), d, glow]
        rnd = random.Random(self.seed or hash(self.name) & 0xffff)
        # real volume: distance to the silhouette edge → thickness (round handles, ridged blades, domed heads)
        dist = {k: 0 for k in cells}
        frontier = [k for k in cells if any((k[0] + dx, k[1] + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        for k in cells:
            dist[k] = None
        for k in frontier:
            dist[k] = 0
        level = 0
        while frontier:
            nxt = []
            for (x, y) in frontier:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    k = (x + dx, y + dy)
                    if k in dist and dist[k] is None:
                        dist[k] = level + 1
                        nxt.append(k)
            frontier = nxt
            level += 1
        for (x, y), c in cells.items():
            d = dist[(x, y)] or 0
            c[2] = max(2, min(c[2] * 3 // 2, 2 + 2 * d))
            if d == 0 and not c[3]:
                c[1] -= 0.3
            # lit from the upper left: cells with free space up/left are brighter
            if (x - 1, y) not in cells or (x, y + 1) not in cells:
                c[1] += 0.12
        cols = {}
        for k, (m, t, d, glow) in cells.items():
            i = int(round(_clamp(t) * (len(m) - 1)))
            cols[k] = (m[i], d, glow)
        floating = []
        for (m, count, (ua, ub), (va, vb)) in self.sparkles:
            for _ in range(count):
                u, v = rnd.uniform(ua, ub) * self.k, rnd.uniform(va, vb) * self.k * WIDE
                # back to grid
                if self.upright:
                    x, y = int((v + DESIGN / 2) * N / DESIGN), int((u + 1) * N / DESIGN)
                else:
                    x = int(round(((u * math.sqrt(2) - v * math.sqrt(2)) / 2 + 1) * N / DESIGN - 0.5))
                    y = int(round(((u * math.sqrt(2) + v * math.sqrt(2)) / 2 + 1) * N / DESIGN - 0.5))
                if 0 <= x < N and 0 <= y < N and (x, y) not in cols:
                    floating.append((x, y, m[-1] if rnd.random() < 0.6 else m[-2]))
        return cols, floating

    def model(self):
        from swatch import SwatchModel
        cols, floating = self.raster()
        m = SwatchModel(self.name, display='voxel')
        for y in range(N):
            x = 0
            while x < N:
                if (x, y) not in cols:
                    x += 1
                    continue
                key = cols[(x, y)]
                x1 = x
                while (x1 + 1, y) in cols and cols[(x1 + 1, y)] == key and x1 - x < 15:
                    x1 += 1
                c, d, glow = key
                z0, z1 = 8 - d * CELL / 2, 8 + d * CELL / 2
                m.add([x * CELL, y * CELL, z0], [(x1 + 1) * CELL, (y + 1) * CELL, z1], '#%02x%02x%02x' % tuple(c[:3]),
                      'glow' if glow else 'matte', glow=glow)
                x = x1 + 1
        for (x, y, c) in floating:
            s = CELL * 0.8
            cx, cy = x * CELL + CELL / 2, y * CELL + CELL / 2
            m.add([cx - s / 2, cy - s / 2, 8 - s / 2], [cx + s / 2, cy + s / 2, 8 + s / 2], '#%02x%02x%02x' % tuple(c[:3]), 'glow', glow=True)
        return m


def _run_painter(colors):
    def paint(tile, face, box, rnd):
        w, h = tile.size
        p = tile.load()
        n = len(colors)
        light = {'south': 1.0, 'north': 0.9, 'up': 1.12, 'down': 0.68, 'east': 0.82, 'west': 0.9}[face]
        for x in range(w):
            if face in ('east', 'west'):
                c = colors[-1] if face == 'east' else colors[0]
            else:
                i = min(n - 1, x * n // w)
                c = colors[n - 1 - i] if face == 'north' else colors[i]
            for y in range(h):
                f = light * (1.08 - 0.16 * (x / max(1, w - 1)))
                if face in ('south', 'north', 'east', 'west'):
                    if y == 0:
                        f *= 1.2
                    elif y == h - 1:
                        f *= 0.78
                p[x, y] = tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)
    return paint


# ---------------------------------------------------------------------------------------------- premium-style parts
def jagged(u0, u1, w, mat, accent=None, period=5, spike=3.5, side=1, curve=0.0, depth=2, glow=False):
    """blade body with saw-tooth spikes leaning toward the tip on one side (side=0: both sides)."""
    L = u1 - u0

    def f(u, v):
        if not (u0 <= u <= u1):
            return None
        s = (u - u0) / L
        c = curve * s * s * L
        ww = w * (1 - s ** 3)
        ph = ((u - u0) % period) / period          # 0..1 inside one tooth
        tooth = spike * (1 - ph) * (1 - s * 0.6)
        dv = v - c
        up = ww / 2 + (tooth if side >= 0 else 0)
        lo = -ww / 2 - (tooth if side <= 0 else 0)
        if not (lo <= dv <= up):
            return None
        if accent and (dv > ww / 2 - 0.6 or dv < -ww / 2 + 0.6):
            return accent, 0.8 if dv > 0 else 0.45, max(1, depth - 1), glow
        return mat, 0.7 if dv > 0 else 0.35, depth
    return f


def tri(ub, h, half, mat, depth=4):
    """triangle tier pointing to +u (christmas tree)"""
    def f(u, v):
        if ub <= u <= ub + h:
            hw = half * (1 - (u - ub) / h)
            if abs(v) <= hw:
                return mat, 0.5 + 0.4 * v / max(0.5, hw) * 0.6 + 0.2 * (u - ub) / h, depth
    return f


def bow(uc, size, mat, depth=3):
    """ribbon bow across the shaft: two loops and two tails"""
    loops = [(uc + size * 0.3, size * 0.9, size * 0.75), (uc + size * 0.3, -size * 0.9, size * 0.75)]

    def f(u, v):
        for (lu, lv, r) in loops:
            d = math.hypot(u - lu, v - lv)
            if r * 0.35 <= d <= r:
                return mat, 0.5 + 0.4 * (v - lv) / r, depth
        if math.hypot(u - uc, v) <= size * 0.4:
            return mat, 0.75, depth + 1
        for sgn in (1, -1):
            t = (uc - u) / size
            if 0 <= t <= 1.3 and abs(v - sgn * (0.3 + t * 0.8) * size) <= 0.9:
                return mat, 0.4, depth - 1
    return f


def rose(uc, vc, r, mat, depth=5):
    """layered bloom: spiral of darker petal lines"""
    def f(u, v):
        d = math.hypot(u - uc, v - vc)
        if d > r:
            return None
        a = math.atan2(v - vc, u - uc)
        petal = (d / r * 3 + a / math.pi) % 1
        t = 0.75 - 0.4 * (petal < 0.25) - 0.25 * (d / r) + 0.15 * (v - vc) / r
        return mat, t, depth
    return f


def thorns(u0, u1, every, mat, size=1.2):
    def f(u, v):
        if u0 <= u <= u1:
            k = int((u - u0) // every)
            ph = (u - u0) - k * every
            side = 1 if k % 2 else -1
            if ph < size and 1.0 <= v * side <= 1.0 + size - ph:
                return mat, 0.4, 2
    return f


def vine(u0, u1, amp, period, w, mat, depth=2):
    def f(u, v):
        if u0 <= u <= u1:
            c = amp * math.sin((u - u0) / period * 2 * math.pi)
            if abs(v - c) <= w / 2:
                return mat, 0.6 + 0.3 * (v - c) / (w / 2), depth
    return f


def ornament_hub(uc, r, mat, gemm, depth=6):
    """big gold socket with corner studs and a gem"""
    def f(u, v):
        du, dv = abs(u - uc), abs(v)
        if du + dv <= r * 1.35 and du <= r and dv <= r:
            if du + dv <= r * 0.55:
                return gemm, 0.95 if v > 0 else 0.55, depth + 2, True
            ring = du + dv > r * 1.05
            return mat, (0.85 if v > 0 else 0.45) - (0.2 if ring else 0), depth + (0 if ring else 1)
    return f

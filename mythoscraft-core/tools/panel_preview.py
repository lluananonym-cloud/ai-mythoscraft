"""Local preview of the admin website with sample data (no Minecraft server needed): python tools/panel_preview.py"""
import json
import math
import os
import random
import time
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler

WEB = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'web')
OWNER = '9f799033-c0f0-4d2a-aa6f-b68ce9207af4'
PLAYERS = [('MinePro641', OWNER, 'MYTHOS'), ('Streamerin', '069a79f4-44e9-4726-a5be-fca90e38aaf5', 'CREATOR'),
           ('Zuschauer', '853c80ef-3c37-49fd-aa49-938b674adae6', 'VIP'), ('Baumeister', 'f7c77d99-9f15-4a66-a87d-c4a51ef30d19', 'ELITE'),
           ('Lia', '61699b2e-d327-4a01-9f1e-0ea8c3f06bc6', 'SPIELER')]
ACTIONS = [
    {'id': 'eco', 'category': 'Spieler', 'label': 'Coins / Gems ändern', 'icon': '💰', 'admin': False, 'params': [
        {'key': 'player', 'label': 'Spieler', 'type': 'player', 'options': [p[0] for p in PLAYERS], 'def': ''},
        {'key': 'op', 'label': 'Aktion', 'type': 'select', 'options': ['give', 'take', 'set'], 'def': 'give'},
        {'key': 'currency', 'label': 'Währung', 'type': 'select', 'options': ['coins', 'gems'], 'def': 'coins'},
        {'key': 'amount', 'label': 'Betrag', 'type': 'number', 'options': [], 'def': '1000'}]},
    {'id': 'rank', 'category': 'Spieler', 'label': 'Rang setzen', 'icon': '👑', 'admin': False, 'params': [
        {'key': 'player', 'label': 'Spieler', 'type': 'player', 'options': [p[0] for p in PLAYERS], 'def': ''},
        {'key': 'rank', 'label': 'Rang', 'type': 'select', 'options': ['VIP', 'HELD', 'CREATOR', 'MYTHOS'], 'def': 'VIP'},
        {'key': 'duration', 'label': 'Dauer', 'type': 'select', 'options': ['perm', '7d', '10d', '30d'], 'def': 'perm'}]},
    {'id': 'item', 'category': 'Spieler', 'label': 'Custom-Item geben', 'icon': '🎁', 'admin': False, 'params': []},
    {'id': 'key', 'category': 'Spieler', 'label': 'Kisten-Schlüssel geben', 'icon': '🔑', 'admin': False, 'params': []},
    {'id': 'magic', 'category': 'Spieler', 'label': 'Zauber freischalten', 'icon': '✦', 'admin': False, 'params': []},
    {'id': 'kick', 'category': 'Spieler', 'label': 'Spieler kicken', 'icon': '🚪', 'admin': False, 'params': []},
    {'id': 'event-start', 'category': 'Events', 'label': 'Event starten', 'icon': '⚡', 'admin': False, 'params': [
        {'key': 'event', 'label': 'Event', 'type': 'select', 'options': ['coinrain', 'mythostitan', 'treasurehunt'], 'def': 'coinrain'}]},
    {'id': 'giveaway', 'category': 'Events', 'label': 'Verlosung starten', 'icon': '🎉', 'admin': False, 'params': []},
    {'id': 'boost', 'category': 'Events', 'label': 'Server-Booster', 'icon': '🚀', 'admin': False, 'params': []},
    {'id': 'season', 'category': 'Events', 'label': 'Saison starten', 'icon': '🍂', 'admin': False, 'params': []},
    {'id': 'snowman', 'category': 'Events', 'label': 'Snowman abspielen', 'icon': '☃', 'admin': False, 'params': []},
    {'id': 'say', 'category': 'Server', 'label': 'Nachricht an alle', 'icon': '📢', 'admin': False, 'params': []},
    {'id': 'reload', 'category': 'Server', 'label': 'Plugin neu laden', 'icon': '🔄', 'admin': True, 'params': []},
    {'id': 'time', 'category': 'Server', 'label': 'Zeit setzen', 'icon': '🌗', 'admin': True, 'params': []},
]
T0 = time.time()


def snapshot():
    t = time.time()
    return {
        'tps': round(19.6 + math.sin(t / 3) * 0.35, 2), 'mspt': round(21 + random.random() * 6, 1),
        'ramUsed': 3100 + int(math.sin(t / 7) * 300), 'ramMax': 8192, 'uptime': int(t - T0) + 93_000, 'online': len(PLAYERS), 'max': 100,
        'players': [{'name': n, 'uuid': u, 'rank': r, 'coins': 120_000 + i * 31_337, 'gems': 340 + i * 57, 'ping': 18 + i * 9,
                     'world': 'world', 'x': 0, 'y': 64, 'z': 0, 'health': 20, 'gamemode': 'survival'} for i, (n, u, r) in enumerate(PLAYERS)],
        'coinsOnline': 914_000, 'gemsOnline': 2830,
        'event': {'id': 'coinrain', 'name': 'Münzregen', 'seconds': 95 - int(t - T0) % 95, 'state': 'RUNNING'},
        'background': {'id': 'treasurehunt', 'name': 'Schatzsuche', 'seconds': 20000}, 'nextEvent': 1500, 'season': 'halloween',
        'giveaway': True, 'clanWar': [{'name': 'Drachenherz', 'tag': 'DRG', 'points': 412}, {'name': 'Nachtwache', 'tag': 'NW', 'points': 297}],
        'worlds': 3, 'entities': 1843, 'chunks': 2210, 'time': int(t * 1000)}


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send_json(self, obj, code=200):
        b = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        p = self.path.split('?')[0]
        if p == '/api/me':
            return self.send_json({'name': 'MinePro641', 'uuid': OWNER, 'role': 'owner', 'owner': True, 'url': 'http://localhost:8099'})
        if p == '/api/stats':
            return self.send_json(snapshot())
        if p == '/api/actions':
            return self.send_json(ACTIONS)
        if p == '/api/log':
            return self.send_json({'log': ['2026-10-14T09:12 MinePro641: Aktion event-start: /mythos event start coinrain']})
        if p == '/api/access':
            return self.send_json({'users': [{'uuid': PLAYERS[1][1], 'name': 'Streamerin', 'role': 'edit'}]})
        if p == '/api/player':
            return self.send_json({'name': 'Streamerin', 'uuid': PLAYERS[1][1], 'rank': 'CREATOR', 'coins': 151337, 'gems': 397, 'homes': 4,
                                   'health': 20, 'food': 18, 'level': 31, 'world': 'world', 'location': '120 70 -44',
                                   'jobs': {'miner': 22, 'lumberjack': 9, 'farmer': 14, 'hunter': 30, 'fisher': 5, 'builder': 11},
                                   'inventory': ['1× netherite_sword', '64× cobblestone', '3× golden_apple']})
        if p == '/api/config':
            return self.send_json({'file': 'config.yml', 'files': ['config.yml', 'messages.yml'], 'text': 'config-version: 17\nweb-panel:\n  enabled: true\n  port: 8085\n'})
        if p == '/api/live':
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.end_headers()
            try:
                while True:
                    self.wfile.write(('data: ' + json.dumps(snapshot()) + '\n\n').encode())
                    self.wfile.flush()
                    time.sleep(2)
            except Exception:
                return
        path = os.path.join(WEB, 'index.html' if p == '/' else p.lstrip('/'))
        if not os.path.isfile(path):
            self.send_response(404)
            self.end_headers()
            return
        data = open(path, 'rb').read()
        self.send_response(200)
        self.send_header('Content-Type', {'.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css'}.get(os.path.splitext(path)[1], 'application/octet-stream') + '; charset=utf-8')
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        n = int(self.headers.get('Content-Length', 0))
        body = json.loads(self.rfile.read(n) or b'{}')
        if self.path == '/api/action':
            return self.send_json({'command': 'mythos ' + body.get('id', ''), 'output': ['✓ ausgeführt (Vorschau)']})
        if self.path == '/api/command':
            return self.send_json({'output': ['Vorschau: ' + body.get('command', '')]})
        return self.send_json({'ok': True})


if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', 8099), H).serve_forever()

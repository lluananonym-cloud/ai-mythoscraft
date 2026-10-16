"""Builds the Netlify folder for the admin website: python tools/build_netlify.py <server-host> [port]"""
import os
import shutil
import sys
import zipfile

host = sys.argv[1] if len(sys.argv) > 1 else 'mythoscraftsmp.minekeep.gg'
port = sys.argv[2] if len(sys.argv) > 2 else '8085'
root = os.path.dirname(os.path.abspath(__file__))
src = os.path.join(root, '..', 'src', 'main', 'resources', 'web')
out = os.path.join(root, '..', 'netlify')
shutil.rmtree(out, ignore_errors=True)
shutil.copytree(src, out)
with open(os.path.join(out, '_redirects'), 'w', encoding='utf-8') as f:
    f.write(f'# all API calls go to the plugin on the Minecraft server\n/api/*  http://{host}:{port}/api/:splat  200\n')
with open(os.path.join(out, '_headers'), 'w', encoding='utf-8') as f:
    f.write('''/*
  X-Frame-Options: DENY
  X-Content-Type-Options: nosniff
  Referrer-Policy: no-referrer
  Content-Security-Policy: default-src 'self'; script-src 'self' https://unpkg.com; img-src 'self' data: https://mc-heads.net https://crafatar.com https://textures.minecraft.net; connect-src 'self' https://mc-heads.net https://crafatar.com; style-src 'self' https://fonts.googleapis.com 'unsafe-inline'; font-src https://fonts.gstatic.com; frame-ancestors 'none'
''')
zip_path = os.path.join(root, '..', '..', 'release', 'mythoscraft-panel-netlify.zip')
with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
    for name in os.listdir(out):
        z.write(os.path.join(out, name), name)
print('ok', out, zip_path)

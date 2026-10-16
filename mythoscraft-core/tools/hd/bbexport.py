"""Writes an hd.Model as a Blockbench project (.bbmodel, texture embedded) to look at / edit it in Blockbench."""
import base64
import io
import json
import uuid


def to_bbmodel(model, path, name=None):
    if not hasattr(model, 'tex'):
        model.build()
    w, h = model.tex.size
    buf = io.BytesIO()
    model.tex.save(buf, 'PNG')
    tex_uuid = str(uuid.uuid4())
    elements, outliner = [], []
    for bi, b in enumerate(model.boxes):
        faces = {}
        for f in ('north', 'east', 'south', 'west', 'up', 'down'):
            if (bi, f) in model.pos:
                x, y, fw, fh = model.pos[(bi, f)]
                faces[f] = {'uv': [x, y, x + fw, y + fh], 'texture': 0}
            else:
                faces[f] = {'uv': [0, 0, 0, 0], 'texture': None}
        u = str(uuid.uuid4())
        el = {'name': b.name or f'cube{bi}', 'box_uv': False, 'rescale': False, 'locked': False,
              'from': list(b.frm), 'to': list(b.to), 'autouv': 0, 'color': bi % 8,
              'origin': list(b.rot['origin']) if b.rot else [8, 8, 8], 'faces': faces, 'type': 'cube', 'uuid': u}
        if b.rot:
            el['rotation'] = [b.rot['angle'] if b.rot['axis'] == a else 0 for a in 'xyz']
        if b.glow:
            el['light_emission'] = 15
        elements.append(el)
        outliner.append(u)
    data = {
        'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
        'name': name or model.name, 'parent': '', 'ambientocclusion': True, 'front_gui_light': False,
        'resolution': {'width': w, 'height': h},
        'elements': elements, 'outliner': outliner,
        'textures': [{'path': '', 'name': model.name + '.png', 'folder': 'item', 'namespace': '', 'id': '0',
                      'width': w, 'height': h, 'uv_width': w, 'uv_height': h, 'particle': True,
                      'render_mode': 'default', 'render_sides': 'auto', 'frame_time': 1, 'frame_order_type': 'loop',
                      'frame_order': '', 'frame_interpolate': False, 'visible': True, 'internal': True, 'saved': False,
                      'uuid': tex_uuid, 'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}],
        'display': {},
    }
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(data, fh)

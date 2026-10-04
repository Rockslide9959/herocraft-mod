// v0.14.21 Iron Man suit-up assets. Run from the repo root:  node scratchpad/gen_v01421_ironman_suitup.js
//
//  1. crimson_vanguard.animation.json  + suit_lock_on (0.6 s) / suit_release (0.5 s): the per-piece clips every Iron Man
//     mark plays as a piece locks on / breaks away (bones shared by every mark_<n>.geo.json; absent bones are skipped).
//     Idempotent: re-running replaces just those two clips.
//  2. The Mark VII delivery pod: geo/iron_man_delivery_pod.geo.json, animations/iron_man_delivery_pod.animation.json,
//     textures/entity/iron_man_delivery_pod.png (+ _glowmask).
//  3. The Mark V suitcase 3D model: geo/mark_v_suitcase.geo.json, animations/mark_v_suitcase.animation.json,
//     textures/entity/mark_v_suitcase.png (+ _glowmask), models/item/mark_v_suitcase.json (builtin/entity) and
//     models/item/mark_v_suitcase_icon.json (the existing flat sprite, kept for the inventory).
//
// No python / canvas here: PNGs are hand-encoded with Node's zlib.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ASSETS = 'src/main/resources/assets/projecthero';

// ---------------------------------------------------------------- PNG
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function encodePng(w, h, px) { // px: Uint8Array RGBA
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    Buffer.from(px.buffer, px.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
function hash(x, y, s) {
  let h = (x * 374761393 + y * 668265263 + s * 2246822519) >>> 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177) >>> 0;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}

// ---------------------------------------------------------------- a tiny box-UV model builder
// mat(face, s, t, fw, fh) -> [r,g,b,a, glow?]  (s,t = texel within the face, fw/fh = face size in texels)
function makeModel(id, texW, texH) {
  return { id, texW, texH, bones: [], cubes: [], cursorX: 0, cursorY: 0, rowH: 0 };
}
function bone(m, name, parent, pivot) {
  const b = { name, pivot, cubes: [] };
  if (parent) b.parent = parent;
  m.bones.push(b);
  return b;
}
function cube(m, b, origin, size, mat, inflate = 0) {
  const [w, h, d] = size;
  const uw = 2 * (w + d), uh = d + h;
  if (m.cursorX + uw > m.texW) { m.cursorX = 0; m.cursorY += m.rowH; m.rowH = 0; }
  const u = m.cursorX, v = m.cursorY;
  if (v + uh > m.texH) throw new Error(m.id + ': texture too small for ' + b.name);
  m.cursorX += uw;
  m.rowH = Math.max(m.rowH, uh);
  const c = { origin, size, uv: [u, v] };
  if (inflate) c.inflate = inflate;
  b.cubes.push(c);
  m.cubes.push({ u, v, w, h, d, mat });
}
function faces(c) {
  const { u, v, w, h, d } = c;
  return [
    ['up', u + d, v, w, d], ['down', u + d + w, v, w, d],
    ['east', u, v + d, d, h], ['north', u + d, v + d, w, h], ['west', u + d + w, v + d, d, h], ['south', u + 2 * d + w, v + d, w, h]];
}
function paint(m) {
  const px = new Uint8Array(m.texW * m.texH * 4);
  const glow = new Uint8Array(m.texW * m.texH * 4);
  for (const c of m.cubes) {
    for (const [face, fx, fy, fw, fh] of faces(c)) {
      for (let t = 0; t < fh; t++) {
        for (let s = 0; s < fw; s++) {
          const x = fx + s, y = fy + t;
          const col = c.mat(face, s, t, fw, fh, x, y);
          if (!col) continue;
          const i = (y * m.texW + x) * 4;
          px[i] = col[0]; px[i + 1] = col[1]; px[i + 2] = col[2]; px[i + 3] = col[3] ?? 255;
          if (col[4]) { glow[i] = col[0]; glow[i + 1] = col[1]; glow[i + 2] = col[2]; glow[i + 3] = 255; }
        }
      }
    }
  }
  return { px, glow };
}
function writeModel(m, geoPath, texPath, identifier) {
  const geo = {
    format_version: '1.12.0',
    'minecraft:geometry': [{
      description: {
        identifier, texture_width: m.texW, texture_height: m.texH,
        visible_bounds_width: 4, visible_bounds_height: 4, visible_bounds_offset: [0, 1.5, 0]
      },
      bones: m.bones.map(b => {
        const o = { name: b.name };
        if (b.parent) o.parent = b.parent;
        o.pivot = b.pivot;
        if (b.cubes.length) o.cubes = b.cubes;
        return o;
      })
    }]
  };
  fs.writeFileSync(path.join(ASSETS, geoPath), JSON.stringify(geo, null, 2) + '\n');
  const { px, glow } = paint(m);
  fs.writeFileSync(path.join(ASSETS, texPath + '.png'), encodePng(m.texW, m.texH, px));
  fs.writeFileSync(path.join(ASSETS, texPath + '_glowmask.png'), encodePng(m.texW, m.texH, glow));
}

// shading helpers
function shadeMetal(base, face, s, t, fw, fh, seed, x, y) {
  const lift = face === 'up' ? 1.18 : face === 'down' ? 0.62 : face === 'north' || face === 'south' ? 1.0 : 0.84;
  const edge = s === 0 || t === 0 || s === fw - 1 || t === fh - 1 ? 0.78 : 1.0;
  const n = 0.94 + hash(x, y, seed) * 0.1;
  const k = lift * edge * n;
  return [Math.min(255, base[0] * k) | 0, Math.min(255, base[1] * k) | 0, Math.min(255, base[2] * k) | 0, 255];
}
const RED = [168, 22, 26], DARK_RED = [110, 12, 16], GOLD = [222, 170, 52], SILVER = [196, 202, 210],
  GUNMETAL = [70, 74, 82], BLACK = [26, 27, 31], CYAN = [150, 236, 255], ORANGE = [255, 150, 50];

// ---------------------------------------------------------------- 1. crimson_vanguard per-piece clips
function addSuitClips() {
  const file = path.join(ASSETS, 'animations/crimson_vanguard.animation.json');
  const text = fs.readFileSync(file, 'utf8');
  const crlf = text.includes('\r\n');
  const json = JSON.parse(text);
  const P = (o) => o; // keyframe maps are plain objects { "time": [x,y,z] }
  const mirror = (kf) => Object.fromEntries(Object.entries(kf).map(([k, v]) => [k, [-v[0], v[1], v[2]]]));
  const mirrorRot = (kf) => Object.fromEntries(Object.entries(kf).map(([k, v]) => [k, [v[0], -v[1], -v[2]]]));
  const lockOn = {
    loop: 'hold_on_last_frame',
    animation_length: 0.6,
    bones: {
      helmet: { position: P({ '0.0': [0, 6, 0], '0.3': [0, -0.4, 0], '0.4': [0, 0, 0] }),
        scale: P({ '0.0': [1.15, 1.15, 1.15], '0.3': [1, 1, 1] }) },
      faceplate: { rotation: P({ '0.0': [-95, 0, 0], '0.35': [-95, 0, 0], '0.5': [3, 0, 0], '0.6': [0, 0, 0] }) },
      chest_armor: { position: P({ '0.0': [0, 0, -6], '0.25': [0, 0, 0.4], '0.35': [0, 0, 0] }) },
      back_panel: { position: P({ '0.0': [0, 0, 6], '0.3': [0, 0, -0.4], '0.4': [0, 0, 0] }) },
      waist: { position: P({ '0.0': [0, -3, 0], '0.3': [0, 0, 0] }) },
      arc_reactor: { scale: P({ '0.0': [0, 0, 0], '0.35': [0, 0, 0], '0.5': [1.35, 1.35, 1.35], '0.6': [1, 1, 1] }) },
      right_shoulder: { position: P({ '0.0': [-5, 4, 0], '0.3': [0.3, -0.3, 0], '0.4': [0, 0, 0] }) },
      right_gauntlet: { position: P({ '0.0': [-3, -5, 0], '0.35': [0, 0.3, 0], '0.45': [0, 0, 0] }),
        rotation: P({ '0.0': [0, -90, 0], '0.35': [0, 0, 0] }) },
      right_thigh_plate: { position: P({ '0.0': [-3, 0, -2], '0.3': [0, 0, 0] }) },
      right_knee: { position: P({ '0.0': [0, -2, -3], '0.35': [0, 0, 0] }) },
      right_boot: { position: P({ '0.0': [0, -4, 0], '0.25': [0, 0.4, 0], '0.35': [0, 0, 0] }) },
    }
  };
  for (const side of ['shoulder', 'gauntlet', 'thigh_plate', 'knee', 'boot']) {
    const r = lockOn.bones['right_' + side];
    const l = {};
    if (r.position) l.position = mirror(r.position);
    if (r.rotation) l.rotation = mirrorRot(r.rotation);
    lockOn.bones['left_' + side] = l;
  }
  const release = {
    loop: 'hold_on_last_frame',
    animation_length: 0.5,
    bones: {
      helmet: { position: P({ '0.0': [0, 0, 0], '0.5': [0, 7, 0] }), scale: P({ '0.0': [1, 1, 1], '0.5': [1.15, 1.15, 1.15] }) },
      faceplate: { rotation: P({ '0.0': [0, 0, 0], '0.2': [-95, 0, 0] }) },
      chest_armor: { position: P({ '0.0': [0, 0, 0], '0.1': [0, 0, -0.6], '0.5': [0, 0, -6] }) },
      back_panel: { position: P({ '0.0': [0, 0, 0], '0.1': [0, 0, 0.6], '0.5': [0, 0, 6] }) },
      waist: { position: P({ '0.0': [0, 0, 0], '0.5': [0, -3, 0] }) },
      arc_reactor: { scale: P({ '0.0': [1, 1, 1], '0.15': [0, 0, 0] }) },
      right_shoulder: { position: P({ '0.0': [0, 0, 0], '0.1': [0.3, -0.3, 0], '0.5': [-5, 4, 0] }) },
      right_gauntlet: { position: P({ '0.0': [0, 0, 0], '0.5': [-3, -5, 0] }), rotation: P({ '0.0': [0, 0, 0], '0.5': [0, -90, 0] }) },
      right_thigh_plate: { position: P({ '0.0': [0, 0, 0], '0.5': [-3, 0, -2] }) },
      right_knee: { position: P({ '0.0': [0, 0, 0], '0.5': [0, -2, -3] }) },
      right_boot: { position: P({ '0.0': [0, 0, 0], '0.1': [0, 0.4, 0], '0.5': [0, -4, 0] }) },
    }
  };
  for (const side of ['shoulder', 'gauntlet', 'thigh_plate', 'knee', 'boot']) {
    const r = release.bones['right_' + side];
    const l = {};
    if (r.position) l.position = mirror(r.position);
    if (r.rotation) l.rotation = mirrorRot(r.rotation);
    release.bones['left_' + side] = l;
  }
  json.animations['animation.crimson_vanguard.suit_lock_on'] = lockOn;
  json.animations['animation.crimson_vanguard.suit_release'] = release;
  let out = JSON.stringify(json, null, 2);
  if (crlf) out = out.replace(/\n/g, '\r\n');
  fs.writeFileSync(file, out);
}

// ---------------------------------------------------------------- 2. the Mark VII delivery pod
function buildPod() {
  const m = makeModel('pod', 128, 128);
  const metal = (base, seed) => (face, s, t, fw, fh, x, y) => shadeMetal(base, face, s, t, fw, fh, seed, x, y);
  // red shell with a gold band at 1/3 and 2/3 height and silver vertical seams
  const shell = (face, s, t, fw, fh, x, y) => {
    if (face === 'north') {
      // the open front of the body is the interior: dark, with cyan glow ribs
      const rib = t % 6 === 3 && s > 1 && s < fw - 2;
      if (rib) return [...CYAN, 255, 1];
      return shadeMetal(BLACK, face, s, t, fw, fh, 3, x, y);
    }
    if (face === 'up' || face === 'down') return shadeMetal(GUNMETAL, face, s, t, fw, fh, 4, x, y);
    const band = fh > 10 && (Math.abs(t - Math.round(fh / 3)) < 1 || Math.abs(t - Math.round(2 * fh / 3)) < 1);
    if (band) return shadeMetal(GOLD, face, s, t, fw, fh, 5, x, y);
    const seam = fw > 6 && (s === Math.floor(fw / 2));
    return shadeMetal(seam ? SILVER : RED, face, s, t, fw, fh, 6, x, y);
  };
  const door = (side) => (face, s, t, fw, fh, x, y) => {
    if (face === 'north') {
      // outer face: red with a gold arc emblem near the top and a silver edge
      const cx = side < 0 ? fw - 0.5 : -0.5;      // the emblem straddles the seam between the doors
      const dx = s - cx, dy = t - 6;
      const r = Math.sqrt(dx * dx + dy * dy);
      if (r > 2.2 && r < 3.6) return [...GOLD, 255];
      if (r <= 1.6) return [...CYAN, 255, 1];
      if (Math.abs(t - Math.round(fh * 0.66)) < 1) return shadeMetal(GOLD, face, s, t, fw, fh, 7, x, y);
      return shadeMetal(s === (side < 0 ? 0 : fw - 1) ? SILVER : RED, face, s, t, fw, fh, 8, x, y);
    }
    if (face === 'south') return shadeMetal(GUNMETAL, face, s, t, fw, fh, 9, x, y); // inner face
    return shadeMetal(SILVER, face, s, t, fw, fh, 10, x, y);
  };
  const nozzle = (face, s, t, fw, fh, x, y) => face === 'down' ? [...ORANGE, 255, 1] : shadeMetal(GUNMETAL, face, s, t, fw, fh, 11, x, y);

  const root = bone(m, 'root', null, [0, 0, 0]);
  const thrusters = bone(m, 'thrusters', 'root', [0, 2, 0]);
  for (const [x, z] of [[-6, -6], [3, -6], [-6, 3], [3, 3]]) cube(m, thrusters, [x, 0, z], [3, 2, 3], nozzle);
  const base = bone(m, 'base', 'root', [0, 2, 0]);
  cube(m, base, [-7, 2, -7], [14, 3, 14], metal(GUNMETAL, 12));
  const shellBack = bone(m, 'shell_back', 'root', [0, 5, 0]);
  cube(m, shellBack, [-7, 5, -5], [14, 25, 12], shell);
  cube(m, shellBack, [-1, 6, 7], [2, 23, 1], metal(GOLD, 13)); // the spine
  const doorL = bone(m, 'door_left', 'root', [-7, 5, -7]);
  cube(m, doorL, [-7, 5, -7], [7, 25, 2], door(-1));
  const doorR = bone(m, 'door_right', 'root', [7, 5, -7]);
  cube(m, doorR, [0, 5, -7], [7, 25, 2], door(1));
  const cap = bone(m, 'cap', 'root', [0, 30, 0]);
  cube(m, cap, [-6, 30, -6], [12, 3, 12], metal(SILVER, 14));
  cube(m, cap, [-4, 33, -4], [8, 2, 8], metal(RED, 15));
  cube(m, cap, [-1, 35, -1], [2, 1, 2], (face) => [...CYAN, 255, 1]); // beacon
  writeModel(m, 'geo/iron_man_delivery_pod.geo.json', 'textures/entity/iron_man_delivery_pod', 'geometry.iron_man_delivery_pod');

  const anim = {
    format_version: '1.8.0',
    animations: {
      'animation.iron_man_delivery_pod.fly': {
        loop: true, animation_length: 1.0,
        bones: {
          root: { rotation: { '0.0': [0, 0, -2], '0.5': [0, 0, 2], '1.0': [0, 0, -2] } },
          thrusters: { scale: { '0.0': [1, 1, 1], '0.25': [1.05, 1.2, 1.05], '0.5': [1, 1, 1], '0.75': [1.05, 1.15, 1.05], '1.0': [1, 1, 1] } }
        }
      },
      'animation.iron_man_delivery_pod.open': {
        loop: 'hold_on_last_frame', animation_length: 0.5,
        bones: {
          door_left: { rotation: { '0.0': [0, 0, 0], '0.1': [0, 0, 0], '0.45': [0, -112, 0], '0.5': [0, -108, 0] } },
          door_right: { rotation: { '0.0': [0, 0, 0], '0.1': [0, 0, 0], '0.45': [0, 112, 0], '0.5': [0, 108, 0] } },
          cap: { position: { '0.0': [0, 0, 0], '0.15': [0, 2.5, 0], '0.5': [0, 2, 0] } },
          thrusters: { scale: { '0.0': [1, 1, 1], '0.2': [1, 0.4, 1] } }
        }
      },
      'animation.iron_man_delivery_pod.close': {
        loop: 'hold_on_last_frame', animation_length: 0.5,
        bones: {
          door_left: { rotation: { '0.0': [0, -108, 0], '0.4': [0, 2, 0], '0.5': [0, 0, 0] } },
          door_right: { rotation: { '0.0': [0, 108, 0], '0.4': [0, -2, 0], '0.5': [0, 0, 0] } },
          cap: { position: { '0.0': [0, 2, 0], '0.45': [0, 0, 0] } },
          thrusters: { scale: { '0.0': [1, 0.4, 1], '0.5': [1, 1, 1] } }
        }
      }
    }
  };
  fs.writeFileSync(path.join(ASSETS, 'animations/iron_man_delivery_pod.animation.json'), JSON.stringify(anim, null, 2) + '\n');
}

// ---------------------------------------------------------------- 3. the Mark V suitcase
function buildCase() {
  const m = makeModel('case', 64, 64);
  const metal = (base, seed) => (face, s, t, fw, fh, x, y) => shadeMetal(base, face, s, t, fw, fh, seed, x, y);
  const shellMat = (front) => (face, s, t, fw, fh, x, y) => {
    const outer = front ? face === 'north' : face === 'south';
    const inner = front ? face === 'south' : face === 'north';
    if (inner) {
      // the inside: gunmetal with folded-armour panel lines (what the suit unfolds from)
      if (s % 4 === 0 || t % 3 === 0) return shadeMetal(BLACK, face, s, t, fw, fh, 20, x, y);
      return shadeMetal(GUNMETAL, face, s, t, fw, fh, 21, x, y);
    }
    if (outer) {
      // red face with a silver rim and a darker centre stripe
      if (s === 0 || t === 0 || s === fw - 1 || t === fh - 1) return shadeMetal(SILVER, face, s, t, fw, fh, 22, x, y);
      if (Math.abs(t - Math.floor(fh / 2)) < 1) return shadeMetal(DARK_RED, face, s, t, fw, fh, 23, x, y);
      return shadeMetal(RED, face, s, t, fw, fh, 24, x, y);
    }
    return shadeMetal(SILVER, face, s, t, fw, fh, 25, x, y); // the edges
  };
  const root = bone(m, 'case', null, [0, 0, 0]);
  const back = bone(m, 'back_shell', 'case', [0, -4, 0]);
  cube(m, back, [-6, -4, 0], [12, 9, 2], shellMat(false));
  const front = bone(m, 'front_shell', 'case', [0, -4, 0]);
  cube(m, front, [-6, -4, -2], [12, 9, 2], shellMat(true));
  cube(m, front, [-4, 3, -3], [1, 1, 1], metal(GOLD, 26));  // latches
  cube(m, front, [3, 3, -3], [1, 1, 1], metal(GOLD, 27));
  const handle = bone(m, 'handle', 'case', [0, 5, 0]);
  cube(m, handle, [-3, 5, -1], [1, 2, 2], metal(BLACK, 28));
  cube(m, handle, [2, 5, -1], [1, 2, 2], metal(BLACK, 29));
  cube(m, handle, [-3, 7, -1], [6, 1, 2], metal(BLACK, 30));
  const pl = bone(m, 'panel_left', 'case', [-6, 0, -1]);
  cube(m, pl, [-7, -4, -1], [1, 9, 3], metal(SILVER, 31));
  const pr = bone(m, 'panel_right', 'case', [6, 0, -1]);
  cube(m, pr, [6, -4, -1], [1, 9, 3], metal(SILVER, 32));
  const core = bone(m, 'core', 'case', [0, 0.5, -0.5]);
  cube(m, core, [-1, -1, -1], [3, 3, 1], (face, s, t) => (s === 1 && t === 1 ? [255, 255, 255, 255, 1] : [...CYAN, 255, 1]));
  writeModel(m, 'geo/mark_v_suitcase.geo.json', 'textures/entity/mark_v_suitcase', 'geometry.mark_v_suitcase');
  const anim = {
    format_version: '1.8.0',
    animations: {
      // posed procedurally by MarkVSuitcaseRenderer from the synced suit-up clock; this clip only keeps GeckoLib happy
      'animation.mark_v_suitcase.idle': { loop: true, animation_length: 1.0, bones: { core: { scale: { '0.0': [1, 1, 1] } } } }
    }
  };
  fs.writeFileSync(path.join(ASSETS, 'animations/mark_v_suitcase.animation.json'), JSON.stringify(anim, null, 2) + '\n');

  const itemModel = {
    parent: 'builtin/entity',
    gui_light: 'front',
    textures: { particle: 'projecthero:item/mark_v_suitcase' },
    display: {
      // held by the handle, hanging at the side, broad face sideways (like any flat item); GUI = identity (the
      // renderer draws the flat icon model there)
      thirdperson_righthand: { rotation: [0, 0, 0], translation: [0, -5, 1], scale: [0.8, 0.8, 0.8] },
      thirdperson_lefthand: { rotation: [0, 0, 0], translation: [0, -5, 1], scale: [0.8, 0.8, 0.8] },
      firstperson_righthand: { rotation: [0, -90, 0], translation: [1.5, -1, 1], scale: [0.6, 0.6, 0.6] },
      firstperson_lefthand: { rotation: [0, 90, 0], translation: [1.5, -1, 1], scale: [0.6, 0.6, 0.6] },
      ground: { rotation: [0, 0, 0], translation: [0, 1, 0], scale: [0.5, 0.5, 0.5] },
      fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9] },
      head: { rotation: [0, 180, 0], translation: [0, 8, 0], scale: [0.8, 0.8, 0.8] },
      gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }
    }
  };
  fs.writeFileSync(path.join(ASSETS, 'models/item/mark_v_suitcase.json'), JSON.stringify(itemModel, null, 2) + '\n');
  fs.writeFileSync(path.join(ASSETS, 'models/item/mark_v_suitcase_icon.json'), JSON.stringify({
    parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/mark_v_suitcase' }
  }, null, 2) + '\n');
}

addSuitClips();
buildPod();
buildCase();
console.log('ok: suit clips, delivery pod, Mark V suitcase');

// v0.16.7 perf: builds a low-detail copy of a gun mesh (assets/projecthero/meshes/<gun>.json -> <gun>_lod.json) by
// vertex clustering: every vertex snaps to the average of the vertices in its grid cell, quads that collapse (fewer than
// 3 distinct corners) and duplicates are dropped. The LOD is what GunMeshModels draws for a gun in someone else's hand,
// on the ground or in a frame; first person (and the GUI) keep the full mesh.
// usage: node scratchpad/gen_gun_mesh_lod.js <gun> <cell> [write]
const fs = require('fs');
const path = require('path');
const gun = process.argv[2] || 'punisher_pistol';
const cell = parseFloat(process.argv[3] || '0.2');
const dir = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'meshes');
const src = JSON.parse(fs.readFileSync(path.join(dir, gun + '.json'), 'utf8'));
const quads = src.quads;
const key = (x, y, z) => `${Math.floor(x / cell)},${Math.floor(y / cell)},${Math.floor(z / cell)}`;
const sums = new Map();
for (const q of quads) {
  for (let i = 0; i < 4; i++) {
    const x = q.v[i * 3], y = q.v[i * 3 + 1], z = q.v[i * 3 + 2];
    const k = key(x, y, z);
    const s = sums.get(k) || [0, 0, 0, 0];
    s[0] += x; s[1] += y; s[2] += z; s[3]++;
    sums.set(k, s);
  }
}
const rep = new Map();
for (const [k, s] of sums) rep.set(k, [s[0] / s[3], s[1] / s[3], s[2] / s[3]].map(v => Math.round(v * 1000) / 1000));
const out = [];
const seen = new Set();
for (const q of quads) {
  const ks = [];
  for (let i = 0; i < 4; i++) ks.push(key(q.v[i * 3], q.v[i * 3 + 1], q.v[i * 3 + 2]));
  const distinct = new Set(ks);
  if (distinct.size < 3) continue;
  // keep winding: a duplicate is the same corners in the same cyclic order (an opposite-facing twin stays)
  let best = null;
  for (let r = 0; r < 4; r++) {
    const c = [0, 1, 2, 3].map(j => ks[(j + r) % 4]).join('|');
    if (best === null || c < best) best = c;
  }
  const dk = q.s + '#' + best;
  if (seen.has(dk)) continue;
  seen.add(dk);
  const v = [];
  for (const k of ks) v.push(...rep.get(k));
  out.push({ s: q.s, v });
}
console.log(`${gun}: ${quads.length} -> ${out.length} quads at cell ${cell}`);
if (process.argv[4] === 'write') {
  const file = path.join(dir, gun + '_lod.json');
  fs.writeFileSync(file, JSON.stringify({ credit: `LOD of ${gun}.json (vertex clustering, cell ${cell}); scratchpad/gen_gun_mesh_lod.js`, quads: out }));
  console.log('wrote', file, fs.statSync(file).size, 'bytes');
}

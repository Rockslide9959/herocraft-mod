// v0.14.9 Super Soldier assets, generated with Node (no Python on this machine). Re-runnable; overwrites its outputs.
// Run from the repo root:  node scratchpad/gen_supersoldier_v0149.js [path/to/captainamerica.bbmodel]
//
//  1. The user's Captain America suit (a GeckoLib-format Blockbench player model with one embedded 64x64 skin):
//       textures/armor/captain_america.png   -- the embedded texture, byte-for-byte
//       geo/captain_america.geo.json         -- the armour rig GeoArmorRenderer expects (armorHead / armorBody /
//         armorRightArm / armorLeftArm / armorRightLeg / armorLeftLeg + synthesised armorRightBoot / armorLeftBoot).
//     Conversion: every bbmodel element becomes one box-UV cube in its group's bone. Blockbench stores X mirrored
//     relative to Bedrock geometry, so origin.x = -to.x and pivot.x = -group.origin.x (the same flip Blockbench's own
//     GeckoLib export does). The groups' display rotations (a posed preview: head tilted, arms swung) are dropped --
//     GeoArmorRenderer drives every bone from the player's pose. Inflation: +0.3 on every cube so the suit sits clear of
//     the player's own skin (the z-fighting rule in docs/ARMOR_MODELS.md), on top of the model's own layer inflation
//     (Hat Layer 0.5 -> 0.8, the other layers 0.25 -> 0.55, bases 0 -> 0.3; same numbers as thor.geo.json). The boots
//     are the bottom 4 px of each leg (base + layer), V shifted down 8 rows so they sample the leg's own boot pixels,
//     +0.05 so they sit over the leggings -- the thor.geo.json recipe.
//  2. Item icons (16x16): the four suit pieces and the Adamantium Shield (its particle / fallback icon).
//  3. textures/entity/adamantium_shield.png (64x64): the round shield's front (32x32 disc), back (32x32 disc), rim strip
//     and handle, drawn by AdamantiumShieldRenderer.
//  4. Item models + the five crafting recipes.
const fs = require("fs");
const zlib = require("zlib");
const path = require("path");

const ROOT = path.join(__dirname, "..");
const RES = path.join(ROOT, "src", "main", "resources");
const ASSETS = path.join(RES, "assets", "projecthero");
const DATA = path.join(RES, "data", "projecthero");
const SRC = process.argv[2] || "C:/Users/ethan/OneDrive/Desktop/3d minecraft models/super solider/captainamerica.bbmodel";

// ------------------------------------------------------------------ PNG writer (copied from gen_supersoldier_textures.js)
function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    for (let x = 0; x < w; x++) {
      const p = px[y * w + x] || [0, 0, 0, 0];
      raw.set(p, y * (w * 4 + 1) + 1 + x * 4);
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
const hex = (s, a = 255) => [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16), a];
const mix = (a, b, t) => [0, 1, 2].map((i) => Math.round(a[i] + (b[i] - a[i]) * t)).concat([255]);
const write = (rel, buf) => {
  const f = path.join(ROOT, rel);
  fs.mkdirSync(path.dirname(f), { recursive: true });
  fs.writeFileSync(f, buf);
  console.log("wrote", rel);
};
const json = (o) => JSON.stringify(o, null, 2) + "\n";
const resRel = (...p) => path.relative(ROOT, path.join(...p)).replace(/\\/g, "/");

// ------------------------------------------------------------------ 1. the suit
const bb = JSON.parse(fs.readFileSync(SRC, "utf8"));
const texSrc = bb.textures[0].source;
write(resRel(ASSETS, "textures", "armor", "captain_america.png"), Buffer.from(texSrc.slice(texSrc.indexOf(",") + 1), "base64"));

const BONE = { "Head": "armorHead", "Body": "armorBody", "Right Arm": "armorRightArm", "Left Arm": "armorLeftArm",
  "Right Leg": "armorRightLeg", "Left Leg": "armorLeftLeg" };
const CLEAR = 0.3;
const r3 = (v) => Math.round(v * 1000) / 1000;
const byUuid = Object.fromEntries(bb.elements.map((e) => [e.uuid, e]));
const groups = Object.fromEntries(bb.groups.map((g) => [g.uuid, g]));
const bones = [];
(function walk(nodes) {
  for (const n of nodes) {
    if (typeof n === "string") continue;
    const g = groups[n.uuid];
    if (g && BONE[g.name]) {
      const cubes = n.children.filter((c) => typeof c === "string").map((id) => byUuid[id]).map((e) => ({
        name: e.name.toLowerCase().replace(/\s+/g, "_"),
        origin: [r3(-e.to[0]), e.from[1], e.from[2]],
        size: [r3(e.to[0] - e.from[0]), r3(e.to[1] - e.from[1]), r3(e.to[2] - e.from[2])],
        uv: e.uv_offset ? e.uv_offset.slice() : [0, 0],
        inflate: r3((e.inflate || 0) + CLEAR),
      }));
      bones.push({ name: BONE[g.name], pivot: [r3(-g.origin[0]), g.origin[1], g.origin[2]], cubes });
    }
    if (n.children) walk(n.children);
  }
})(bb.outliner);
if (bones.length !== 6) throw new Error("expected 6 body groups, got " + bones.map((b) => b.name));
function boot(name, leg) {
  return {
    name, pivot: leg.pivot.slice(),
    cubes: leg.cubes.map((c) => ({
      name: c.name.replace("leg", "boot"),
      origin: c.origin.slice(), size: [c.size[0], 4, c.size[2]],
      uv: [c.uv[0], c.uv[1] + (c.size[1] - 4)],
      inflate: r3(c.inflate + 0.05),
    })),
  };
}
bones.push(boot("armorRightBoot", bones.find((b) => b.name === "armorRightLeg")));
bones.push(boot("armorLeftBoot", bones.find((b) => b.name === "armorLeftLeg")));
write(resRel(ASSETS, "geo", "captain_america.geo.json"), json({
  format_version: "1.12.0",
  "minecraft:geometry": [{
    description: { identifier: "geometry.captain_america", texture_width: bb.resolution.width, texture_height: bb.resolution.height,
      visible_bounds_width: 3, visible_bounds_height: 4, visible_bounds_offset: [0, 16, 0] },
    bones,
  }],
}));
for (const b of bones) console.log("  ", b.name, JSON.stringify(b.pivot), b.cubes.map((c) => JSON.stringify([c.origin, c.size, c.uv, c.inflate])).join(" "));

// ------------------------------------------------------------------ 2. item icons
const PAL = {
  K: hex("101a33"), B: hex("2b4c9b"), b: hex("1d3572"), L: hex("4a6fc4"), W: hex("eeeeee"), w: hex("b9c0cc"),
  R: hex("c41e2a"), r: hex("8a141c"), S: hex("9aa3ad"), s: hex("6c747d"), N: hex("6b4a2c"),
};
function icon(rows) {
  const px = [];
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const ch = (rows[y] || "")[x] || ".";
    px.push(ch === "." ? [0, 0, 0, 0] : PAL[ch]);
  }
  return png(16, 16, px);
}
const ICONS = {
  captain_america_helmet: [
    "................",
    "................",
    "................",
    "....KKKKKKKK....",
    "...KLBBWWBBBK...",
    "..KLBBWBBWBBBK..",
    "..KBBBWWWWBBbK..",
    ".WKBBBWBBWBBbKW.",
    ".WWKBbbbbbbbKWW.",
    "..WKbK....KbKW..",
    "...KbK....KbK...",
    "...KKK....KKK...",
    "................",
    "................",
    "................",
    "................",
  ],
  captain_america_chestplate: [
    "................",
    "..KKKK....KKKK..",
    ".KLBBBKKKKBBBbK.",
    ".KLBBBBBBBBBBbK.",
    ".KBBBBBWWBBBBbK.",
    ".KKBBBWWWWBBbKK.",
    "..KBBBBWWBBBbK..",
    "..KBBBWBBWBBbK..",
    "..KRRRRRRRRRrK..",
    "..KWWWWWWWWWwK..",
    "..KRRRRRRRRRrK..",
    "..KWWWWWWWWWwK..",
    "..KRRRRRRRRRrK..",
    "..KKKKKKKKKKKK..",
    "................",
    "................",
  ],
  captain_america_leggings: [
    "................",
    "...KKKKKKKKKK...",
    "...KLBBBBBBbK...",
    "...KBBBBBBBbK...",
    "...KBBBKKBBbK...",
    "...KLBBKKBBbK...",
    "...KLBBKKBBbK...",
    "...KBBbKKBBbK...",
    "...KBBbKKBBbK...",
    "...KBBbKKBBbK...",
    "...KBBbKKBBbK...",
    "...KBBbKKBBbK...",
    "...KKKKKKKKKK...",
    "................",
    "................",
    "................",
  ],
  captain_america_boots: [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...KKKK..KKKK...",
    "...KRRK..KRRK...",
    "...KRrK..KRrK...",
    "...KRrK..KRrK...",
    "...KRRK..KRRK...",
    "..KRRrK..KRRrK..",
    ".KRRRrK..KRRRrK.",
    ".KrrrrK..KrrrrK.",
    ".KKKKKK..KKKKKK.",
    "................",
    "................",
  ],
};
for (const [id, rows] of Object.entries(ICONS)) write(resRel(ASSETS, "textures", "item", id + ".png"), icon(rows));

// the round shield face, any size: outer red, white, red, blue centre, white star (point up)
function shieldFace(size) {
  const px = [];
  const c = size / 2, R = size / 2;
  const RED = hex("c8202a"), RED_D = hex("7e1018"), WHITE = hex("f0f0f0"), BLUE = hex("1f3f9a"), STAR = hex("ffffff");
  const star = [];
  for (let i = 0; i < 10; i++) {
    const r = (i % 2 === 0 ? 0.40 : 0.16) * R;
    const a = -Math.PI / 2 + i * Math.PI / 5;
    star.push([c + Math.cos(a) * r, c + Math.sin(a) * r]);
  }
  const inStar = (x, y) => {
    let inside = false;
    for (let i = 0, j = star.length - 1; i < star.length; j = i++) {
      const [xi, yi] = star[i], [xj, yj] = star[j];
      if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
    }
    return inside;
  };
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const fx = x + 0.5, fy = y + 0.5;
    const d = Math.hypot(fx - c, fy - c) / R;
    let p = [0, 0, 0, 0];
    if (d <= 1.0) {
      p = d > 0.93 ? RED_D : d > 0.74 ? RED : d > 0.56 ? WHITE : d > 0.43 ? RED : BLUE;
      if (d <= 0.43 && inStar(fx, fy)) p = STAR;
      // a soft metallic sheen from the top-left
      const sheen = Math.max(0, 1 - Math.hypot(fx - c * 0.6, fy - c * 0.6) / (R * 0.9)) * 0.22;
      if (p[3]) p = mix(p, [255, 255, 255], sheen);
    }
    px.push(p);
  }
  return px;
}
function shieldBack(size) {
  const px = [];
  const c = size / 2, R = size / 2;
  const STEEL = hex("8d959e"), STEEL_D = hex("5d646c"), RIM = hex("7e1018");
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const d = Math.hypot(x + 0.5 - c, y + 0.5 - c) / R;
    let p = [0, 0, 0, 0];
    if (d <= 1.0) {
      p = d > 0.93 ? RIM : mix(STEEL, STEEL_D, d * 0.8);
      if (d > 0.6 && d < 0.66) p = STEEL_D; // a pressed ring
    }
    px.push(p);
  }
  return px;
}
write(resRel(ASSETS, "textures", "item", "adamantium_shield.png"), png(16, 16, shieldFace(16)));

// ------------------------------------------------------------------ 3. the shield's entity texture (64x64)
{
  const W = 64, H = 64;
  const px = new Array(W * H).fill(null).map(() => [0, 0, 0, 0]);
  const blit = (src, size, ox, oy) => { for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) px[(oy + y) * W + ox + x] = src[y * size + x]; };
  blit(shieldFace(32), 32, 0, 0);
  blit(shieldBack(32), 32, 32, 0);
  // rim strip (0..32, 32..36): dark red edge with a bright steel lip
  for (let y = 32; y < 36; y++) for (let x = 0; x < 32; x++) px[y * W + x] = y === 32 || y === 35 ? hex("aeb6bf") : hex("8e1520");
  // handle (32..40, 32..40): leather
  for (let y = 32; y < 40; y++) for (let x = 32; x < 40; x++) px[y * W + x] = (x + y) % 3 === 0 ? hex("5a3d24") : hex("6b4a2c");
  write(resRel(ASSETS, "textures", "entity", "adamantium_shield.png"), png(W, H, px));
}

// ------------------------------------------------------------------ 4. item models + recipes
for (const id of Object.keys(ICONS)) {
  write(resRel(ASSETS, "models", "item", id + ".json"), json({ parent: "minecraft:item/generated", textures: { layer0: "projecthero:item/" + id } }));
}
// the vanilla shield's display transforms (minecraft:item/shield + shield_blocking), so it is held and raised the same way
const SHIELD_DISPLAY = {
  thirdperson_righthand: { rotation: [0, 90, 0], translation: [10, 6, -4], scale: [1, 1, 1] },
  thirdperson_lefthand: { rotation: [0, 90, 0], translation: [10, 6, 12], scale: [1, 1, 1] },
  firstperson_righthand: { rotation: [0, 180, 5], translation: [-10, 2, -10], scale: [1.25, 1.25, 1.25] },
  firstperson_lefthand: { rotation: [0, 180, 5], translation: [10, 0, -10], scale: [1.25, 1.25, 1.25] },
  gui: { rotation: [15, -25, -5], translation: [2, 3, 0], scale: [0.65, 0.65, 0.65] },
  fixed: { rotation: [0, 180, 0], translation: [-4.5, 4.5, -5], scale: [0.55, 0.55, 0.55] },
  ground: { rotation: [0, 0, 0], translation: [2, 4, 2], scale: [0.25, 0.25, 0.25] },
};
const BLOCKING_DISPLAY = {
  thirdperson_righthand: { rotation: [45, 155, 0], translation: [-3.49, 11, -2], scale: [1, 1, 1] },
  thirdperson_lefthand: { rotation: [45, 155, 0], translation: [11.51, 7, 2.5], scale: [1, 1, 1] },
  firstperson_righthand: { rotation: [0, 180, -5], translation: [-15, 5, -11], scale: [1.25, 1.25, 1.25] },
  firstperson_lefthand: { rotation: [0, 180, -5], translation: [5, 5, -11], scale: [1.25, 1.25, 1.25] },
  gui: { rotation: [15, -25, -5], translation: [2, 3, 0], scale: [0.65, 0.65, 0.65] },
};
write(resRel(ASSETS, "models", "item", "adamantium_shield.json"), json({
  parent: "builtin/entity", gui_light: "front", textures: { particle: "projecthero:item/adamantium_shield" },
  display: SHIELD_DISPLAY,
  overrides: [{ predicate: { blocking: 1 }, model: "projecthero:item/adamantium_shield_blocking" }],
}));
write(resRel(ASSETS, "models", "item", "adamantium_shield_blocking.json"), json({
  parent: "builtin/entity", gui_light: "front", textures: { particle: "projecthero:item/adamantium_shield" },
  display: BLOCKING_DISPLAY,
}));

const shaped = (pattern, key, result) => json({
  type: "minecraft:crafting_shaped", category: "equipment", pattern,
  key: Object.fromEntries(Object.entries(key).map(([k, v]) => [k, { item: v }])),
  result: { id: "projecthero:" + result, count: 1 },
});
write(resRel(DATA, "recipe", "adamantium_shield.json"), shaped(["IRI", "WNW", "IBI"],
  { I: "minecraft:iron_block", R: "minecraft:red_dye", W: "minecraft:white_dye", N: "minecraft:netherite_ingot", B: "minecraft:blue_dye" },
  "adamantium_shield"));
write(resRel(DATA, "recipe", "captain_america_helmet.json"), shaped(["BWB", "I I"],
  { B: "minecraft:blue_wool", W: "minecraft:white_wool", I: "minecraft:iron_ingot" }, "captain_america_helmet"));
write(resRel(DATA, "recipe", "captain_america_chestplate.json"), shaped(["I I", "BWB", "RBR"],
  { I: "minecraft:iron_ingot", B: "minecraft:blue_wool", W: "minecraft:white_wool", R: "minecraft:red_wool" }, "captain_america_chestplate"));
write(resRel(DATA, "recipe", "captain_america_leggings.json"), shaped(["BIB", "B B", "L L"],
  { B: "minecraft:blue_wool", I: "minecraft:iron_ingot", L: "minecraft:leather" }, "captain_america_leggings"));
write(resRel(DATA, "recipe", "captain_america_boots.json"), shaped(["R R", "L L"],
  { R: "minecraft:red_wool", L: "minecraft:leather" }, "captain_america_boots"));

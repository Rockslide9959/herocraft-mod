// v0.17.3: wire the stepped regrowth look (AndyRegrowStepped) and its client setting (AndyRegrowStyle, /andystyle).
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const R = 'src/client/java/com/projecthero/mod/client/andy/AndyRegrow.java';
const D = (call) => '\t\tif (AndyRegrowStyle.stepped()) { // v0.17.3: the stepped look, picked per viewer (/andystyle)\n\t\t\t' + call + ';\n\t\t\treturn;\n\t\t}\n';
edit(R, [
  ['\tstatic void boneAndFlesh(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {\n',
   '\tstatic void boneAndFlesh(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {\n' + D('AndyRegrowStepped.boneAndFlesh(pose, buffers, light, tex, slim, part, p)')],
  ['\t\t\tboolean embers) {\n\t\tLimb lb = Limb.of(part, slim);',
   '\t\t\tboolean embers) {\n' + D('AndyRegrowStepped.limb(pose, buffers, light, tex, slim, part, p, from, embers)') + '\t\tLimb lb = Limb.of(part, slim);'],
  ['\tpublic static void bodyCut(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, float rows, boolean embers) {\n',
   '\tpublic static void bodyCut(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, float rows, boolean embers) {\n' + D('AndyRegrowStepped.bodyCut(pose, buffers, light, tex, slim, rows, embers)')],
]);
edit('src/client/java/com/projecthero/mod/client/andy/AndyClient.java', [
  ['\t\tAndySounds.initialize();', '\t\tAndyRegrowStyle.initialize(); // v0.17.3: /andystyle 3d|stepped\n\t\tAndySounds.initialize();'],
]);
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const j = JSON.parse(t);
const add = {
  'message.projecthero.andy.regrow_style': 'Regrowth look: %s (change it with /andystyle 3d or /andystyle stepped)',
  'message.projecthero.andy.regrow_style.3d': '3D (bone, muscle, skin)',
  'message.projecthero.andy.regrow_style.stepped': 'Stepped (bone and blood, flesh, skin)',
};
for (const k of Object.keys(add)) if (k in j) throw new Error('dup ' + k);
const anchor = '  "message.projecthero.andy.auto_feed": ';
if (!t.includes(anchor)) throw new Error('anchor');
t = t.replace(anchor, Object.entries(add).map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',\n').join('') + anchor);
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');

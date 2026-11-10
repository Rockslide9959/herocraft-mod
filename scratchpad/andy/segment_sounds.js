// v0.17.3: all 10 regrowth segments (hands and feet too) play the regrowth sounds; hands / feet quieter and sparser so a whole
// body re-forming is not a wall of noise.
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/andy/AndySounds.java';
let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
function rep(a, b) { if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 80)); t = t.replace(a, b); }
rep('private static final float[] PART_PITCH = { 1.04f, 0.86f, 1.06f, 1.06f, 0.96f, 0.96f };',
    'private static final float[] PART_PITCH = { 1.04f, 0.86f, 1.06f, 1.06f, 0.96f, 0.96f, 1.22f, 1.22f, 1.12f, 1.12f };\n' +
    '\t/** v0.17.3: per segment loudness -- the hands and feet (segments 6-9) are small: quieter, fewer accents. */\n' +
    '\tprivate static final float[] SEG_GAIN = { 1f, 1f, 1f, 1f, 1f, 1f, 0.45f, 0.45f, 0.45f, 0.45f };');
rep('private static final double[] PART_Y = { 1.65, 1.1, 1.25, 1.25, 0.45, 0.45 };',
    'private static final double[] PART_Y = { 1.65, 1.1, 1.25, 1.25, 0.45, 0.45, 0.75, 0.75, 0.1, 0.1 };');
rep('final PartVoice[] parts = new PartVoice[AndyState.PARTS];', 'final PartVoice[] parts = new PartVoice[AndyState.SEGS];');
rep('\t\tfor (int i = 0; i < AndyState.PARTS; i++) {\n\t\t\tPartVoice pv = v.parts[i];', '\t\tfor (int i = 0; i < AndyState.SEGS; i++) {\n\t\t\tPartVoice pv = v.parts[i];');
rep('if (mc.level == null || player == null || part < 0 || part >= AndyState.PARTS) {', 'if (mc.level == null || player == null || part < 0 || part >= AndyState.SEGS) {');
rep('if (v != null && part >= 0 && part < AndyState.PARTS && v.parts[part] != null) {', 'if (v != null && part >= 0 && part < AndyState.SEGS && v.parts[part] != null) {');
rep('\t\tfloat partPitch = PART_PITCH[part];\n', '\t\tfloat partPitch = PART_PITCH[part];\n\t\tfloat gain = SEG_GAIN[part];\n');
rep('float vol = BASE_VOLUME[k] * e * crowd * (0.85f + 0.15f * Math.min(2f, speed));', 'float vol = BASE_VOLUME[k] * gain * e * crowd * (0.85f + 0.15f * Math.min(2f, speed));');
rep('RANDOM.nextFloat() < ACCENT_RATE[k] * e * crowd * Math.min(2.2f, speed) * step) {', 'RANDOM.nextFloat() < ACCENT_RATE[k] * gain * e * crowd * Math.min(2.2f, speed) * step) {');
rep('local(player, y, accents[k], ACCENT_VOLUME + ACCENT_VOLUME_SPREAD * RANDOM.nextFloat(), ', 'local(player, y, accents[k], gain * (ACCENT_VOLUME + ACCENT_VOLUME_SPREAD * RANDOM.nextFloat()), ');
rep('local(player, y, AndySoundEvents.REGROW_BONE_CRACK, 0.75f, partPitch * 0.9f);', 'local(player, y, AndySoundEvents.REGROW_BONE_CRACK, 0.75f * gain, partPitch * 0.9f);');
rep('local(player, y, AndySoundEvents.REGROW_SQUELCH, 0.8f, partPitch * 0.85f);', 'local(player, y, AndySoundEvents.REGROW_SQUELCH, 0.8f * gain, partPitch * 0.85f);');
rep('local(player, y, AndySoundEvents.REGROW_FINISH, 0.8f, partPitch', 'local(player, y, AndySoundEvents.REGROW_FINISH, 0.8f * gain, partPitch');
fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');

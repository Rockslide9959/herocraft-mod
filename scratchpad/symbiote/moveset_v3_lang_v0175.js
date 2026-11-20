// v0.17.5 Symbiote moveset v3: sets the Symbiote move keys whose meaning changed and adds the new ones after an anchor, key by key
// (never the whole file). Keeps the file's line endings; JSON.parse checked before writing. Re-runnable (an existing key is replaced
// in place, a missing one is inserted after the anchor).
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
let text = fs.readFileSync(file, 'utf8');
const EOL = text.includes('\r\n') ? '\r\n' : '\n';

const keys = [
  // ---- changed numbers / meanings ----
  ['projecthero.symbiote.ability.tendril_strike.desc', "R (tap): a living tendril whips out of your hand along your aim, up to 30 blocks, and hits whatever it meets first for 15 damage and a solid knockback. It can miss, and blocks stop it. 1-second cooldown. Every third strike in a row that finds something is a Tendril Slam. Hold R: Impaling Spear. Shift+R: Tendril Sweep (4-second cooldown)."],
  ['projecthero.symbiote.ability.spike_shot.desc', "G (tap): fire a living spike from your hand -- a real projectile that flies fast and almost flat, so it can miss. 14 damage and Wither V for 5 seconds. 1-second cooldown. Hold G: Spike Rain. Shift+G: Spike Nova."],
  ['projecthero.symbiote.ability.barrage.desc', "Z (tap): a flurry of tendrils from alternating hands for over a second, each flying up to 30 blocks down your aim for 4 damage. It can miss. 8-second cooldown. With a weapon out, Z is that weapon's move instead (Blade Slash, Cleave, Ground Smash, Lash); in Thorns form it is Spike Surge. Hold Z: Ground Eruption. Shift + hold Z (3 s): Monster Form."],
  ['projecthero.symbiote.ability.ground_eruption.desc', "Hold Z: tendrils burst out of the ground in a line racing 12 blocks ahead of you. Everything they come up under takes 12 damage, is launched into the air and slowed for 3 seconds. 8-second cooldown."],
  ['projecthero.symbiote.ability.spikes.desc', "The Thorns form: living spikes bristle out of you. Anything within 1.5 blocks takes 3 damage every 0.75 seconds, Wither I and a small push away; attackers in melee also take reflected damage, like Thorns IV. Z: Spike Surge."],
  ['projecthero.symbiote.ability.tendril_grab.desc', "Sneak+C: lash a tendril out of your hand and hold a target helpless in front of you -- for as long as you like. C throws it, left click crushes it (6 damage a squeeze, twice a second at most), Sneak+C sets it down gently on the ground in front of you (no damage, no fall damage). Works on players and squadmates too: a squadmate or a player protected from PvP is only squeezed, never hurt. 5-second cooldown after you let go."],
  ['message.projecthero.symbiote.grab_seized', "Grabbed -- C throw, left click crush, Sneak+C set down"],
  ['projecthero.symbiote.ability.form.desc', "V (tap): change Form -- Normal, Blade, Axe, Mace, Whip, Thorns, then back to Normal. Hold V for half a second: the Form wheel, to pick any Form at once. Each weapon grows out of your forearm (more melee damage, its own Z move, feeds on 0.3 Biomass a second). Thorns: spikes that hurt anything close or hitting you. With an item in your hand the weapons are skipped. Shift+V: Symbiote Shield."],
  ['projecthero.guide.symbiote.s.move.tendril_grab', "Seize a target within 15 blocks and hold it helpless for as long as you like (players and squadmates too). C throws it, left click crushes it for 6 (twice a second at most), Sneak+C sets it down gently. A squadmate or a PvP-protected player is only squeezed."],
  ['projecthero.guide.symbiote.s.move.spikes', "Thorns form: spikes bristle out of your back and arms. Anything within 1.5 blocks takes 3 every 0.75 s, Wither I and a push away (never squadmates or your pets; players only with PvP on); melee attackers also take Thorns IV damage."],
  ['projecthero.guide.symbiote.s.move.form', "Tap: change Form -- Normal, Blade, Axe, Mace, Whip, Thorns, and round again. The key's box lights while a weapon or Thorns is up."],
  ['projecthero.guide.symbiote.s.move.barrage', "Tap: a second-long flurry of tendrils from both hands, 4 damage a hit, 30 blocks. With a weapon out, Z is that weapon's own move; in Thorns form, Spike Surge (below)."],
  ['projecthero.guide.symbiote.s.holds.body', "R, G, Z, C and V each have a second move: tap the key and the normal move fires when you let go; hold it (under half a second) and the hold move starts instead (the key's box fills while you hold). Shift moves still fire the moment you press."],
  ['hud.projecthero.symbiote.move_list.weapon_z', "Hold: %s; a weapon: its Z; Thorns: Spike Surge"],
  ['projecthero.jei.symbiote_host_moves', "Moves with the suit on: R Tendril Strike (every 3rd in a row a Tendril Slam; hold: Impaling Spear), G Spike Shot (hold: Spike Rain), X Lunge (in the air: Dive Bomb), Z Tendril Barrage (hold: Ground Eruption; in Thorns form: Spike Surge), C Maw Bite (hold: Devour), V Form (hold: the Form wheel). Shift: Sweep, Spike Nova, Grapple, Monster Form (hold 3 s), Shield, Tendril Grab (C throws, left click crushes, Shift+C sets down)."],
  // ---- new ----
  ['projecthero.symbiote.ability.spike_nova', "Spike Nova"],
  ['projecthero.symbiote.ability.spike_nova.desc', "Sneak+G: spikes burst out of your whole body in a flat ring and a shockwave of tendrils rolls out over the ground. Everything within 6 blocks takes 10 damage and Wither II for 3 seconds and is thrown back (bosses stand their ground). Its own 8-second cooldown, shown as a thin strip along the bottom of the G box."],
  ['projecthero.guide.symbiote.s.move.spike_nova', "A ring of spikes and a ground shockwave all round you: everything within 6 blocks takes 10, Wither II for 3 seconds, and is thrown back (not bosses)."],
  ['projecthero.symbiote.ability.spike_surge', "Spike Surge"],
  ['projecthero.symbiote.ability.spike_surge.desc', "Z in Thorns form: your spikes shoot out two and a half times longer for 6 seconds. The thorns now reach 3.5 blocks and hit for 5. 10-second cooldown."],
  ['projecthero.guide.symbiote.s.move.spike_surge', "Thorns form only: the spikes grow 2.5x longer for 6 seconds -- the thorns reach 3.5 blocks and hit for 5 every 0.75 s."],
  ['projecthero.symbiote.ability.form_wheel', "Form Wheel"],
  ['projecthero.symbiote.ability.form_wheel.desc', "Hold V for half a second: a wheel of every Form opens. Point at one and let go of V (or click) to take it; let go over the centre to keep your Form. The game keeps running."],
  ['projecthero.guide.symbiote.s.move.form_wheel', "Hold V half a second: a wheel of every Form. Point and let go (or click) to take one; the centre keeps your Form. The game does not pause."],
  ['screen.projecthero.symbiote.form_wheel', "Symbiote Forms"],
  ['screen.projecthero.symbiote.form_wheel.hint', "Point at a Form, let go of V (or click) -- Esc to cancel"],
  ['screen.projecthero.symbiote.form_wheel.keep', "keep"],
  ['screen.projecthero.symbiote.form_wheel.current', "current"],
  ['screen.projecthero.symbiote.form_wheel.pick', "let go"],
  ['screen.projecthero.symbiote.form_wheel.desc.normal', "No weapon, no spikes: the Symbiote's bare claws and its full kit."],
  ['screen.projecthero.symbiote.form_wheel.desc.blade', "A crescent scythe on your forearm: +5 melee, 50% faster swings, bites past armour. Z: Blade Slash."],
  ['screen.projecthero.symbiote.form_wheel.desc.axe', "A heavy axe head: +8 melee, slower swings, knocks raised shields away. Z: Cleave."],
  ['screen.projecthero.symbiote.form_wheel.desc.mace', "A spiked ball on a tendril: +6 melee, every punch knocks back. Z: Ground Smash."],
  ['screen.projecthero.symbiote.form_wheel.desc.whip', "A barbed whip: +3 melee, faster swings, 2.5 blocks more reach. Z: Lash."],
  ['screen.projecthero.symbiote.form_wheel.desc.thorns', "Spikes all over you: anything within 1.5 blocks is hurt, melee attackers are stabbed back. Z: Spike Surge."],
  ['message.projecthero.symbiote.form.hand_full', "Empty your hand to grow a weapon"],
  ['message.projecthero.symbiote.grab_crush_protected', "You can't hurt them -- only squeeze"],
  ['hud.projecthero.symbiote.grab.throw', "C: Throw"],
  ['hud.projecthero.symbiote.grab.crush', "Click: Crush"],
  ['hud.projecthero.symbiote.grab.set_down', "Shift+C: Set down"],
  ['hud.projecthero.symbiote.surge', "Surge %ss"],
];

const insertAfter = 'projecthero.symbiote.ability.devour.desc';
const esc = s => JSON.stringify(s);
const keyRe = k => new RegExp('("' + k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '"\\s*:\\s*)"(?:[^"\\\\]|\\\\.)*"');
const missing = [];
let replaced = 0;
for (const [k, v] of keys) {
  const re = keyRe(k);
  if (re.test(text)) {
    text = text.replace(re, (m, p1) => p1 + esc(v));
    replaced++;
  } else {
    missing.push([k, v]);
  }
}
if (missing.length) {
  const anchorRe = new RegExp('^.*"' + insertAfter.replace(/\./g, '\\.') + '"\\s*:.*$', 'm');
  const m = text.match(anchorRe);
  if (!m) throw new Error('missing anchor');
  const cr = m[0].endsWith('\r') ? '\r' : '';
  const anchorLine = m[0].replace(/\r$/, '');
  const hadComma = anchorLine.endsWith(',');
  const fixed = hadComma ? anchorLine : anchorLine + ',';
  let block = missing.map(([k, v]) => '  ' + esc(k) + ': ' + esc(v)).join(',' + EOL);
  if (hadComma) block += ',';
  text = text.replace(m[0], fixed + cr + EOL + block + cr);
}
JSON.parse(text);
fs.writeFileSync(file, text);
console.log('ok', replaced, 'replaced,', missing.length, 'added');

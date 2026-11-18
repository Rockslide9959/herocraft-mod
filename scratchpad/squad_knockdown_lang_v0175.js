// v0.17.5 squad knockdown & revive: inserts the new keys after an anchor, key by key (never the whole file).
// Keeps the file's line endings; JSON.parse checked before writing. Run: node scratchpad/squad_knockdown_lang_v0175.js
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
let text = fs.readFileSync(file, 'utf8');
const EOL = text.includes('\r\n') ? '\r\n' : '\n';

const insertAfter = 'projecthero.guide.squads.pets';
const add = [
  ['projecthero.guide.squads.knockdown', 'Knocked down'],
  ['projecthero.guide.squads.knockdown.body', "In a squad, a killing blow knocks you down instead of killing you, as long as someone else in your squad is still standing in the same dimension. You drop to the ground with half a heart and crawl. While you are down nothing can hurt you, and no mob or boss will target you (the ones that were after you go looking for someone else). You can't jump, sprint, attack, use items or powers, place or break blocks, or open anything. You can still look around, crawl and chat. A red bar at the top of your screen drains over 75 seconds. If it runs out, you bleed out and die. Your squad sees you outlined in red, a red face on their Locator Bar, and KNOCKED on the squad screen (P)."],
  ['projecthero.guide.squads.knockdown.revive', "To revive a squadmate, look at them and hold right-click for 5 seconds without letting go. Stay within reach (about 3 blocks). A green bar under your crosshair fills while you hold. Let go, look away, step back or get hurt and it starts again. One squadmate revives at a time. While you are being revived your own bar stops draining and turns white, and the green bar shows on your screen too. When it fills you stand back up with 6 hearts, 5 seconds of Resistance II and Regeneration, and 2 seconds in which nothing can hurt you."],
  ['projecthero.guide.squads.knockdown.rules', "The rules: only squadmates can revive you. If nobody in your squad is left standing for 5 seconds (all down, dead, gone or in another dimension), everyone who is down bleeds out. Hold jump for 3 seconds to give up and die straight away. If you are knocked down again within a minute of being revived, you get 45 seconds, then 25. Power death saves and a Totem of Undying in your hand always come first, and /kill and the void still kill. Logging out while down counts as bleeding out. The exception is the host of a single-player or LAN world closing it (or a server shutting down): you are still down when you come back."],
  ['projecthero.knockdown.bar', 'Knocked down'],
  ['projecthero.knockdown.being_revived', 'Being revived'],
  ['projecthero.knockdown.reviving', 'Reviving %s'],
  ['projecthero.knockdown.giving_up', 'Giving up'],
  ['projecthero.knockdown.down', '%s is down! Hold right-click on them to revive them.'],
  ['projecthero.knockdown.revived', '%s got %s back on their feet.'],
  ['projecthero.knockdown.busy', 'Someone is already reviving %s'],
  ['death.attack.projecthero.bled_out', '%1$s bled out'],
  ['death.attack.projecthero.bled_out.player', '%1$s bled out'],
  ['screen.projecthero.squad.knocked', 'KNOCKED %ss'],
  ['screen.projecthero.squad.tooltip.knocked', 'Knocked down: %s s left to revive them'],
];

const esc = s => JSON.stringify(s);
const anchorRe = new RegExp('^.*"' + insertAfter.replace(/\./g, '\\.') + '"\\s*:.*$', 'm');
const m = text.match(anchorRe);
if (!m) throw new Error('missing anchor');
const lines = add.filter(([k]) => !text.includes('"' + k + '"')).map(([k, v]) => '  ' + esc(k) + ': ' + esc(v) + ',');
if (lines.length) {
  let anchorLine = m[0].replace(/\r$/, '');
  const fixed = anchorLine.endsWith(',') ? anchorLine : anchorLine + ',';
  text = text.replace(m[0], fixed + (m[0].endsWith('\r') ? '\r' : '') + EOL + lines.join(EOL).replace(/,$/, anchorLine.endsWith(',') ? ',' : ''));
}
JSON.parse(text);
fs.writeFileSync(file, text);
console.log('ok', lines.length, 'added');

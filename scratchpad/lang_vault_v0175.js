// v0.17.5 Tier 4 dungeon, The Vault: adds the en_us keys key by key (never rewrites the file: other branches touch it
// too). Keeps the file's line endings and 2-space indent. Existing keys are left alone unless --force.
//   node scratchpad/lang_vault_v0175.js [root] [--force]
const fs = require('fs');
const path = require('path');
const args = process.argv.slice(2);
const force = args.includes('--force');
const root = args.find(a => !a.startsWith('--')) || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  // the place
  'structure.projecthero.the_vault': 'The Vault',
  // people
  'entity.projecthero.vault_inmate': 'Escaped Inmate',
  'entity.projecthero.riot_warden': 'Riot Warden',
  'entity.projecthero.riot_gunner': 'Riot Gunner',
  'entity.projecthero.warden_kross': 'Warden Kross',
  'projecthero.vault.kross.enraged_bar': 'Warden Kross - LOCKDOWN PROTOCOL',
  'projecthero.vault.kross.enraged': 'Warden Kross: "Nobody leaves my Vault!" He is enraged - Lockdown protocol engaged.',
  'projecthero.vault.lockdown': 'LOCKDOWN! %s doorways sealed - the guards are coming.',
  'projecthero.vault.lockdown_lifted': 'Lockdown lifted. The shutters are open.',
  'projecthero.vault.alarm': 'INTRUDER ALERT! Riot wardens are on their way.',
  'projecthero.vault.cleared.title': 'THE VAULT FALLS',
  'projecthero.vault.cleared.sub': 'Warden Kross is beaten. The Dungeon Vaults are open.',
  'projecthero.vault.cleared.chat': 'The Vault is cleared for good. Every player can take their own share from each Dungeon Vault.',
  'projecthero.vault.cleared.vaults': '%s sealed Dungeon Vaults have opened behind the arena.',
  'projecthero.vault.artifact.from': 'Found only in The Vault (Tier 4 dungeon).',
  // blocks
  'block.projecthero.vault_seal': 'Sealed Dungeon Vault',
  'block.projecthero.vault_seal.locked': 'Mag-locked. Defeat Warden Kross to open it.',
  'block.projecthero.lockdown_shutter': 'Lockdown Shutter',
  'block.projecthero.lockdown_track': 'Lockdown Track',
  'block.projecthero.lockdown_track.hint': 'Hazard-striped floor plating from The Vault. In Warden Kross\'s arena it marks the doorways his Lockdown seals with blast shutters. Decorative anywhere else.',
  'block.projecthero.vault_laser_alarm': 'Laser Tripwire Alarm',
  'block.projecthero.vault_laser_alarm.hint': 'A laser tripwire emitter from The Vault\'s Security Floor. Hang a tripwire hook on its front: when a player snaps the wire it fires once, sounding the sirens and calling a squad of riot wardens. Break it first to disarm the trap.',
  'block.projecthero.vault_turret': 'Security Turret',
  'block.projecthero.vault_turret.hint': 'A ceiling turret from The Vault. It fires three-dart bursts of stun darts (a little damage and Slowness) at any player within 14 blocks in plain sight. Mine it with a pickaxe to switch it off.',
  'block.projecthero.vault_console': 'Security Console',
  'block.projecthero.vault_console.hint': 'The Vault\'s security console. Use it to open every iron door within 32 blocks - the lockdown gate down to the Warden\'s Level. Use it again to close them.',
  'block.projecthero.vault_console.open': 'Lockdown gate released.',
  'block.projecthero.vault_console.closed': 'Lockdown gate sealed.',
  'block.projecthero.vault_console.none': 'No doors answer this console.',
  // artifacts
  'item.projecthero.wardens_keyring': "Warden's Keyring",
  'item.projecthero.wardens_keyring.tooltip': 'Use: Lockdown - every hostile within 10 blocks is shackled for 4 s (bosses for a third as long). 40 s cooldown.',
  'item.projecthero.wardens_keyring.tooltip2': 'Use it on an iron door or iron trapdoor to open or close it.',
  'item.projecthero.wardens_keyring.none': 'Nobody here to lock down.',
  'item.projecthero.cracked_mjolnir_shard': 'Cracked Mjolnir Shard',
  'item.projecthero.cracked_mjolnir_shard.tooltip': 'Hold use for 1 s, then release: lightning strikes where you look (up to 32 blocks), 10 damage to everything but players within 3 blocks. It lights no fires. 25 s cooldown.',
  'item.projecthero.riot_capacitor': 'Riot Capacitor',
  'item.projecthero.riot_capacitor.tooltip': 'Carried: when something within 6 blocks hurts you, it discharges - 6 damage to the attacker and every hostile within 3.5 blocks, and a shove away. Recharges in 15 s.',
  // guidebook: The Vault, at the end of the Villain Hideouts chapter
  'projecthero.guide.hideouts.vault': 'The Vault (Tier 4)',
  'projecthero.guide.hideouts.vault.body': 'The Vault is a super-prison sunk deep under the overworld - the rarest and most dangerous place in the world. On the surface only a fenced guard post shows; a ladder shaft inside it drops to three floors. A Dungeon Locator (25% from hideout mini-bosses, often in Hideout Vaults) points to the nearest one.',
  'projecthero.guide.hideouts.vault.floors': 'Three floors',
  'projecthero.guide.hideouts.vault.floors.body': 'The Cell Block: a two-tier hall of barred cells round a glass guard tower. Most cell doors are broken - escaped inmates roam the hall and the galleries, riot wardens hold the floor. The Security Floor: a checkpoint corridor crossed by two laser tripwires (each calls a riot squad - break the emitter to disarm it), a turret hall under four ceiling turrets (mine them), the security office, and the lockdown gate. Use the console in the office to open the gate. The Warden\'s Level: the processing room, Warden Kross\'s arena and the vault room behind it. Riot wardens carry shields: hit them from the side or behind.',
  'projecthero.guide.hideouts.vault.kross': 'Warden Kross',
  'projecthero.guide.hideouts.vault.kross.body': 'A giant in a greatcoat with a stun baton and a riot shield (900 health). His shield takes a quarter off hits from the front - flank him. Riot Shield Charge: braces, then charges in a line (12 damage, thrown and stunned); dodge it into a wall and he is dazed and takes 50% more damage. Stun Baton Shockwave: a spark ring marks the reach for a second, then everyone on the ground inside is shocked (10 damage, slowed and weakened) - jump it. Taser Line: a crackling line latches on and reels you in for a second and a half; break line of sight to snap it. He switches to whoever hurts him most and never strays far from his arena.',
  'projecthero.guide.hideouts.vault.lockdown': 'Lockdown',
  'projecthero.guide.hideouts.vault.lockdown.body': 'At half health he is enraged: faster, harder-hitting, shorter cooldowns, and Lockdown - sirens, blast shutters slam over the arena\'s doorways (over the hazard-striped track) and riot wardens and a gunner drop in. A Lockdown lasts 30 s and returns every 45 s. Nobody gets in or out until it lifts.',
  'projecthero.guide.hideouts.vault.clearing': 'Clearing The Vault',
  'projecthero.guide.hideouts.vault.clearing.body': 'Beat Warden Kross and The Vault is cleared for good: the three sealed Dungeon Vaults behind the arena open into Tier 4 Hero Vaults. Every player takes their own share from each one - Celestial Cores, Vibranium, the Celestial Upgrade template, serums and a chance at The Vault\'s own artifacts. Kross himself drops 2-3 Celestial Cores and Vibranium Shards.',
  'projecthero.guide.hideouts.vault.artifacts': 'The Vault\'s artifacts (only from its Dungeon Vaults)',
  'projecthero.guide.hideouts.vault.a.wardens_keyring': "Warden's Keyring (Epic): use to shackle every hostile within 10 blocks for 4 s (40 s cooldown); it also opens iron doors and trapdoors.",
  'projecthero.guide.hideouts.vault.a.cracked_mjolnir_shard': 'Cracked Mjolnir Shard (Legendary): hold, then release to call lightning where you look - 10 damage round the strike (25 s cooldown).',
  'projecthero.guide.hideouts.vault.a.riot_capacitor': 'Riot Capacitor (Epic): carried, it shocks whoever hurts you and the hostiles round you for 6 (recharges in 15 s).',
  'projecthero.jei.vault_artifact': 'Found only in The Vault\'s Dungeon Vaults (Tier 4), rolled per player once Warden Kross is beaten.',
};

let text = fs.readFileSync(file, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const existing = JSON.parse(text);
const add = [];
let replaced = 0;
for (const [k, v] of Object.entries(K)) {
  if (k in existing) {
    if (force && existing[k] !== v) {
      const re = new RegExp('(\\n  ' + JSON.stringify(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ': )(".*?(?<!\\\\)")(,?)(\\r?)$', 'm');
      text = text.replace(re, (m, a, b, c, d) => a + JSON.stringify(v) + c + d);
      replaced++;
    }
    continue;
  }
  add.push(`  ${JSON.stringify(k)}: ${JSON.stringify(v)}`);
}
if (add.length) {
  const close = text.lastIndexOf('}');
  let body = text.slice(0, close).replace(/\s+$/, '');
  body += ',' + eol + add.join(',' + eol) + eol;
  text = body + '}' + text.slice(close + 1);
}
JSON.parse(text);
fs.writeFileSync(file, text);
console.log(`en_us: +${add.length} keys, ${replaced} replaced`);

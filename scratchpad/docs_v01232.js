// Docs updates for v0.12.32.
const fs = require('fs');
function edit(f, pairs) {
	let s = fs.readFileSync(f, 'utf8');
	const crlf = s.includes('\r\n');
	if (crlf) s = s.replace(/\r\n/g, '\n');
	for (const [a, b] of pairs) {
		if (!s.includes(a)) { console.error('MISSING in', f, ':', a.slice(0, 90)); process.exit(1); }
		s = s.replace(a, () => b);
	}
	if (crlf) s = s.replace(/\n/g, '\r\n');
	fs.writeFileSync(f, s);
}

edit('docs/CURSEFORGE_DESCRIPTION.md', [
[`- **Passives:** the Power of Thor — bonus strength, damage resistance, and fire immunity while the
  hammer is bound to you.
- Craftable **Thor armour** rendered as a full 3D GeckoLib model.`, `- **Passives:** the Power of Thor while the hammer is bound to you — **+11 melee** bare-handed (Mjolnir itself
  hits for **11**), **+10 hearts**, **80% less damage from everything**, permanent **Regeneration I**, and immunity to
  falls and lightning. Your ability bar stays on screen while you are bound, even when the hammer is not in your hand.
- **Thor's Armour (v0.12.32):** press **H** and lightning strikes down on you as a black-and-crimson 3D GeckoLib
  armour set forms on your body (Shift+H still opens the power wheel). Press **H** again to dismiss it. It is
  conjured, never crafted: if it falls out of your inventory or you die, it despawns.`],
[`A permanent **Primary** power (v0.12.31). Craft a **Titan Serum** (4 titanium-gold plates, 2 netherite ingots, 2
magma blocks, a nether star) and use it to unlock Titan Shifting — it never transforms you by itself. Press **J**
to burst into an **11-block Titan** (lightning, steam, a 3 s transformation); press **J** again to change back.`, `A permanent **Primary** power (v0.12.31, reworked in v0.12.32). Craft a **Titan Serum** (4 titanium-gold plates, 2
netherite ingots, 2 magma blocks, a nether star) and use it to unlock Titan Shifting — it never transforms you by
itself. Press **H** (with at least **90% Titan Energy**) to burst into an **11-block Titan** — a regular player-shaped
body and hit-box scaled up to eleven blocks (lightning, steam, a 3 s transformation); press **H** again to change back.
The old J key is gone.`],
[`- **Seven abilities:** **R** Titan Punch (20; third swing of a combo = Heavy Punch 35; **Shift+R** = Titan Kick 30) ·
  **G** Heavy Smash (charge, then 50 in an area) · **X** Titan Stomp (25, 6 blocks) · **Z** Titan Leap (~3× a jump,
  landing 20 in 5 blocks) · **V** Titan Roar (12 blocks: slow, weaken, scatter; bosses resist) · **C** Titan Regeneration
  (10 HP/s for 10 s) · **H** Titan Hardening (60% less damage for 8 s, crystal skin).`, `- **Titan Energy (v0.12.32):** a 100-point bar. You need **90%** to transform; changing back (or being defeated)
  empties it and it refills **1% a second** while you are human. Inside the Titan the bar is spent by the Titan's
  **base regeneration** — 3 HP per second whenever it is hurt, costing 2 energy a second.
- **Abilities:** **R** Titan Punch (20; third swing of a combo = Heavy Punch 35; **Shift+R** = Titan Kick 30) ·
  **G** Heavy Smash (charge, then 50 in an area) · **Z** Titan Stomp (25, 6 blocks) · **X** Titan Leap (~3× a jump,
  landing 20 in 5 blocks; **Shift+X** = Titan Roar) · **V** Titan Roar (12 blocks: slow, weaken, scatter; bosses resist) ·
  **C** Titan Regeneration (10 HP/s for 10 s; **Shift+C** = Titan Hardening: 60% less damage for 8 s, crystal skin).`],
[`  After reverting or being defeated you cannot shift for 60 s. Logging out inside a Titan drops you safely on the ground.`, `  There is no lockout any more — the empty Titan Energy bar is the limit. Logging out inside a Titan drops you safely on the ground.`],
]);

edit('docs/SPIDERMAN_REFERENCE.md', [
[`(\`ImpactWebEntity\`, 10 dmg + knockback, pins to a wall for 6 s via \`SpiderWebs.cocoonFor\`; 1 s cd)`, `(\`ImpactWebEntity\`, 10 dmg + knockback, **cocoons whatever it hits for 12 s** via \`SpiderWebs.cocoonFor\` as of v0.12.32 — and still pins a target knocked into a wall; 1 s cd)`],
]);

edit('docs/TITANSHIFTER_REFERENCE.md', [
[`# Titan Shifter — reference (v0.12.31)`, `# Titan Shifter — reference (v0.12.32)

> **v0.12.32 changes** (everything below is current): the body is now a regular player model (the supplied
> \`aot/titanshifter.bbmodel\` skin) scaled to 11 blocks — hit-box 3.67 × 11; **H** transforms / detransforms (the J key
> is gone); new key layout (X Leap, Z Stomp, Shift+X Roar, Shift+C Hardening); the **Titan Energy** bar (100 max, 90% to
> transform, emptied on reverting, +1%/s as a human, spent by a 3 HP/s base regeneration); the 60 s shift cooldown is now
> 0 (the bar is the limit). Assets come from \`scratchpad/gen_titan_v2.js\` (the old \`gen_titan.js\` generator is obsolete
> for the model/animations/textures but still owns the serum icon).`],
[`| **J** (\`key.projecthero.titan_shift\`) | transform (human) / revert (Titan) — only sends a request |`, `| **H** (\`key.projecthero.power_select\`) | Titan Shift: transform (human, needs 90% Titan Energy) / revert (Titan) — only sends a request. Shift+H (human) still opens the power wheel |`],
[`| **X** Ability 3 | Titan Stomp — 25 dmg, 6 blocks, 6 s |
| **Z** Ability 4 | Titan Leap — ~3× jump (measured ~8 blocks up, ~20 forward), landing 20 dmg in 5 blocks, 5 s |
| **V** Ability 5 | Titan Roar — 12 blocks, Slowness III + Weakness II 10 s, mobs thrown back and flee; bosses take 25% duration and no knockback; 15 s |
| **C** Ability 6 | Titan Regeneration — 10 HP/s for 10 s, 45 s |
| **H** Utility 1 | Titan Hardening — 60% less damage 8 s, crystal texture, 30 s (\`controls.slot6IsHardening\` swaps C and H) |`, `| **X** Ability 3 | Titan Leap — ~3× jump (measured ~8 blocks up, ~20 forward), landing 20 dmg in 5 blocks, 5 s. **Shift+X** = Titan Roar |
| **Z** Ability 4 | Titan Stomp — 25 dmg, 6 blocks, 6 s |
| **V** Ability 5 | Titan Roar — 12 blocks, Slowness III + Weakness II 10 s, mobs thrown back and flee; bosses take 25% duration and no knockback; 15 s (shared cooldown with Shift+X) |
| **C** Ability 6 | Titan Regeneration — 10 HP/s for 10 s, 45 s. **Shift+C** = Titan Hardening — 60% less damage 8 s, crystal texture, 30 s |

## Titan Energy (v0.12.32)

\`TitanShifterState.energy\` (float, synced to the owner, codec default 100 so v0.12.31 saves start full). Config \`energy\`:
\`max\` 100, \`regenPerSecond\` 1 (human / recovering only, applied in whole steps every 20 ticks), \`transformMinFraction\` 0.9,
\`baseRegenHpPerSecond\` 3, \`baseRegenEnergyPerSecond\` 2 (a 10-tick step in \`TitanShifter#tickBaseRegen\`, only while the Titan is below
full health and energy > 0). \`grant\` fills the bar; \`finishToHuman\`, \`forceEnd\`, \`revoke\` and a join with a stale phase all zero it. The
HUD (\`TitanShifterHud\`) draws it under the status line with a white tick at 90%.`],
[`11 × 4 blocks`, `11 × 3.67 blocks (a player's 0.6 × 1.8 hit-box scaled by 6.11; eye height 0.9 × height)`],
[`\`titan_form_hardened.png\`, \`textures/item/titan_serum.png\`. All generated by \`scratchpad/gen_titan.js\` (original design,
hand-rolled PNG writer; the model is packed into a 256×128 atlas at 3 model-pixels per texel). Animation convention:
negative X swings a hanging limb forward.`, `\`titan_form_hardened.png\`, \`textures/item/titan_serum.png\`. Model, animations and both textures are generated by
\`scratchpad/gen_titan_v2.js\`: a vanilla-layout player rig (\`root > body > head / right_arm / left_arm\`, \`root > right_leg / left_leg\`,
box UVs on the 64×64 skin, second-layer cubes inflated 0.25 / 0.5) drawn at \`5.5x\` (\`TitanType#modelHeight\` = 2 blocks); the skin is
copied byte-for-byte out of the \`.bbmodel\` and the hardened texture is a crystal-blue recolour of it. Animation convention: negative
X swings a hanging limb forward; position keyframes are in model px (×5.5 in game).`],
]);

edit('docs/ARMOR_MODELS.md', [
[`* \`thor\` and the \`crimson_vanguard\` default still ship \`crimson_vanguard.png\` — no bespoke Thor art
  yet.`, `* \`thor\` (v0.12.32) ships its own skin, \`thor.png\` (byte-for-byte from the supplied \`thor.bbmodel\`), on \`geo/thor.geo.json\` —
  the standard player-armour rig (same cubes/UVs as \`spider_man.geo.json\`, \`inflate\` 0.3 / 0.55). The skin has no head art, so
  Thor's Armour is three pieces (chest, legs, boots) conjured by H — see \`com.projecthero.mod.thorarmor\`. \`crimson_vanguard\` is
  now only the fallback default.`],
[`**Sets that still need bespoke GeckoLib-UV art:** \`thor\` only — it remains on the crimson placeholder.
Every Iron Man mark is done.`, `**Sets that still need bespoke GeckoLib-UV art:** none — Thor got his in v0.12.32 and every Iron Man mark is done.`],
]);
console.log('docs updated');

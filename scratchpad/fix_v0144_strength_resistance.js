// v0.14.4 correction: the user's "strength mode should get Resistance I" meant MAX STEEL's Turbo Strength Mode, not
// Moon Knight's Marc Spector. Moves the passive from Marc to Max Steel Strength Mode (code, config, lang, docs, tests).
const fs = require('fs');
function edit(f, fn) {
	let s = fs.readFileSync(f, 'utf8');
	const nl = s.includes('\r\n') ? '\r\n' : '\n';
	s = s.replace(/\r\n/g, '\n');
	const rep = (a, b) => { if (!s.includes(a)) throw new Error(f + ': missing ' + a.slice(0, 70)); s = s.replace(a, b); };
	fn(rep, () => s, v => { s = v; });
	fs.writeFileSync(f, s.replace(/\n/g, nl));
	console.log('edited', f);
}

// ---- Moon Knight: Marc loses the Resistance ----
edit('src/main/java/com/projecthero/mod/moonknight/ability/MoonKnightAlters.java', (rep, get, set) => {
	rep('knockback resistance, Resistance I (v0.14.4); Steven', 'knockback resistance; Steven');
	rep('\t\tmarcResistance(player, marc);\n', '');
	const s = get();
	const a = s.indexOf('\t/**\n\t * v0.14.4: Marc Spector, the fighter ("strength") alter, has Resistance I');
	const b = s.indexOf('\t// ================================================================ damage passives');
	if (a < 0 || b < 0) throw new Error('marcResistance block');
	set(s.slice(0, a) + s.slice(b));
});
edit('src/main/java/com/projecthero/mod/moonknight/MoonKnightConfig.java', rep => {
	rep('\t/** Marc Spector (the fighter alter): Resistance I while suited as Marc (amplifier 0), refreshed every second. */\n'
		+ '\tpublic static final int MARC_RESISTANCE_AMPLIFIER = 0;\n\tpublic static final int MARC_RESISTANCE_REFRESH_TICKS = 60;\n', '');
});
edit('src/gametest/java/com/projecthero/mod/gametest/MoonKnightV0144GameTests.java', (rep, get, set) => {
	rep('halved falls, Marc\'s Resistance I,', 'halved falls,');
	const s = get();
	const a = s.indexOf('\t@GameTest(template = EMPTY_STRUCTURE)\n\tpublic void marcHasResistanceOne');
	const b = s.indexOf('\tprivate static void setAlter(');
	if (a < 0 || b < 0) throw new Error('marc test');
	set(s.slice(0, a) + s.slice(b));
});
edit('docs/MOONKNIGHT_REFERENCE.md', rep => {
	rep(`**Alters.** Marc Spector (the fighter / "strength" alter) gets Resistance I while suited as Marc
(\`MoonKnightAlters.marcResistance\`: a 3 s effect refreshed every reconcile, removed on switch / suit-off, never touching
a stronger or longer Resistance from elsewhere). Git history shows Moon Knight never had Resistance before -- Marc was
the closest match for "strength mode". Steven Grant:`, `**Alters.** (The "strength mode gets Resistance I" request meant Max Steel's Turbo Strength Mode -- see
MAXSTEEL_REFERENCE v0.14.4; Marc briefly had it during development and does not.) Steven Grant:`);
});
for (const f of ['scratchpad/lang_v0144_moonknight.js']) {
	edit(f, rep => rep('Marc Spector, the fighter: Resistance I, +4 armour', 'Marc Spector, the fighter: +4 armour'));
}

// ---- Max Steel: Turbo Strength Mode gets Resistance I ----
edit('src/main/java/com/projecthero/mod/maxsteel/MaxSteelConfig.java', (rep, get, set) => {
	const s = get();
	const i = s.lastIndexOf('}');
	set(s.slice(0, i) + `
	// ---------------------------------------------------------------- v0.14.4 Strength Mode Resistance
	/**
	 * Turbo Strength Mode: Resistance I (amplifier 0) while in the mode -- back from v0.9.2 (v0.9.4 had dropped it for
	 * the crouch block alone). A short effect refreshed every tick it runs low, so it never outlives the mode.
	 */
	public static final int STRENGTH_RESISTANCE_AMPLIFIER = 0;
	public static final int STRENGTH_RESISTANCE_TICKS = 60;
}
`);
});
edit('src/main/java/com/projecthero/mod/maxsteel/MaxSteelPassives.java', rep => {
	rep(`	public static void tick(ServerPlayer player) {
		if (!MaxSteel.isTransformed(player)) {
			return;
		}`, `	public static void tick(ServerPlayer player) {
		strengthResistance(player, MaxSteel.isTransformed(player) && MaxSteel.mode(player) == MaxSteelMode.STRENGTH);
		if (!MaxSteel.isTransformed(player)) {
			return;
		}`);
	rep(`			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
		}
	}`, `			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
		}
	}

	/**
	 * v0.14.4: Turbo Strength Mode has Resistance I. A short, particle-free effect topped up while the mode lasts and
	 * taken off the moment it ends; a stronger or longer Resistance from somewhere else (a potion, another power) is
	 * never touched.
	 */
	public static void strengthResistance(ServerPlayer player, boolean on) {
		MobEffectInstance current = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		boolean ours = current != null && current.getAmplifier() == MaxSteelConfig.STRENGTH_RESISTANCE_AMPLIFIER
				&& !current.isInfiniteDuration() && current.getDuration() <= MaxSteelConfig.STRENGTH_RESISTANCE_TICKS;
		if (on) {
			if (current == null || (ours && current.getDuration() < MaxSteelConfig.STRENGTH_RESISTANCE_TICKS - 20)) {
				player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MaxSteelConfig.STRENGTH_RESISTANCE_TICKS,
						MaxSteelConfig.STRENGTH_RESISTANCE_AMPLIFIER, false, false, true));
			}
		} else if (ours) {
			player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
		}
	}`);
});
edit('src/main/java/com/projecthero/mod/maxsteel/MaxSteelStrength.java', rep => {
	rep(` * <p>v0.9.4: the standing Resistance effect is gone -- Strength Mode's defence is now just the +KB
 * resistance attribute and the crouch shield block.`, ` * <p>v0.9.4: the standing Resistance effect was dropped for the +KB resistance attribute and the crouch shield block.
 * v0.14.4: Resistance I is back on top of both ({@link MaxSteelPassives#strengthResistance}).`);
});
edit('src/gametest/java/com/projecthero/mod/gametest/MaxSteelGameTests.java', (rep, get, set) => {
	const s = get();
	const i = s.lastIndexOf('}');
	set(s.slice(0, i) + `
	/** v0.14.4: Turbo Strength Mode has Resistance I, and loses it the moment the mode ends. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void strengthModeHasResistanceOne(GameTestHelper helper) {
		ServerPlayer player = suited(helper);
		helper.assertTrue(MaxSteelModes.toggle(player, MaxSteelMode.STRENGTH, 0f), "enters Strength");
		com.projecthero.mod.maxsteel.MaxSteelPassives.tick(player);
		var res = player.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == 0, "Strength Mode has Resistance I");
		MaxSteelModes.toggle(player, MaxSteelMode.STRENGTH, 0f);
		helper.assertTrue(MaxSteel.mode(player) == MaxSteelMode.BASE, "back in Base");
		com.projecthero.mod.maxsteel.MaxSteelPassives.tick(player);
		helper.assertFalse(player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE), "and loses it out of the mode");
		helper.succeed();
	}
}
`);
});
edit('docs/MAXSTEEL_REFERENCE.md', (rep, get, set) => {
	set(get().replace(/\n*$/, '\n') + `
### v0.14.4 -- Strength Mode Resistance I
Turbo Strength Mode has **Resistance I** again (it had it in v0.9.2; v0.9.4 dropped it for the crouch block alone). A
3 s particle-free effect topped up by \`MaxSteelPassives#strengthResistance\` while the mode lasts and removed as soon as it
ends; a stronger or longer Resistance from elsewhere is never touched. Test: \`MaxSteelGameTests#strengthModeHasResistanceOne\`.
`);
});

// ---- lang ----
const P = 'src/main/resources/assets/projecthero/lang/en_us.json';
const raw = fs.readFileSync(P, 'utf8');
const j = JSON.parse(raw);
const swap = (k, a, b) => { if (j[k].includes(a)) j[k] = j[k].replace(a, b); else if (!j[k].includes(b)) throw new Error('lang ' + k); };
swap('projecthero.guide.moon_knight.alters.body', 'Marc Spector, the fighter: Resistance I, +4 armour', 'Marc Spector, the fighter: +4 armour');
swap('projecthero.guide.max_steel.ability.turbo_strength', '+8 unarmed damage but slower to move and swing.', '+8 unarmed damage and Resistance I, but slower to move and swing.');
const indent = raw.match(/\n(\s+)"/)[1];
let out = JSON.stringify(j, null, indent.includes('\t') ? '\t' : indent.length);
if (raw.includes('\r\n')) out = out.replace(/\n/g, '\r\n');
if (/\r?\n$/.test(raw)) out += raw.includes('\r\n') ? '\r\n' : '\n';
fs.writeFileSync(P, out);
console.log('lang ok');

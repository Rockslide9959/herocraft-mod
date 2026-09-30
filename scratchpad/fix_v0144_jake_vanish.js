// v0.14.4: Jake's Vanish -- mobs can't target him while invisible (mixin, separate), and Vanish is now a TOGGLE with no
// time limit: Sneak+V on, Sneak+V off; also ends on an alter switch, suit off or death. Re-runnable guard: throws on a
// missing anchor.
const fs = require('fs');
function edit(f, fn) {
	let s = fs.readFileSync(f, 'utf8');
	const nl = s.includes('\r\n') ? '\r\n' : '\n';
	s = s.replace(/\r\n/g, '\n');
	const rep = (a, b) => { if (!s.includes(a)) throw new Error(f + ': missing ' + a.slice(0, 70)); s = s.replace(a, b); };
	fn(rep);
	fs.writeFileSync(f, s.replace(/\n/g, nl));
	console.log('edited', f);
}
edit('src/main/java/com/projecthero/mod/moonknight/ability/MoonKnightAlters.java', rep => {
	rep(`	/** Per player (UUID): game time Fist of Khonshu / Vanish end. Server-only; cleared on untransform + server stop. */
	private static final Map<UUID, Long> FIST_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> VANISH_UNTIL = new ConcurrentHashMap<>();`,
`	/**
	 * Per player (UUID): game time Fist of Khonshu ends. Server-only; cleared on untransform + server stop. (Vanish needs
	 * no map since v0.14.4: it is a toggle, and its state is the infinite Invisibility effect itself -- see {@link #vanish}.)
	 */
	private static final Map<UUID, Long> FIST_UNTIL = new ConcurrentHashMap<>();`);
	rep(`		FIST_UNTIL.clear();
		VANISH_UNTIL.clear();`, `		FIST_UNTIL.clear();`);
	rep(`		FIST_UNTIL.remove(id);
		VANISH_UNTIL.remove(id);`, `		FIST_UNTIL.remove(id);`);
	rep(`	public void sneak(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, SNEAK)) {
			return;
		}`, `	public void sneak(ServerPlayer player) {
		// v0.14.4: Vanish is a toggle -- Sneak+V again steps back out (always allowed), and the cooldown starts then
		if (MoonKnight.alter(player) == MoonKnightAlter.JAKE && inVanish(player)) {
			endVanish(player, true);
			MoonKnightAbilities.cooldown(player, SNEAK, MoonKnightConfig.ALTER_SPECIAL_COOLDOWN);
			return;
		}
		if (!MoonKnightAbilities.ready(player, SNEAK)) {
			return;
		}`);
	rep(`		if (used) {
			MoonKnightAbilities.cooldown(player, SNEAK, MoonKnightConfig.ALTER_SPECIAL_COOLDOWN);
		}`, `		if (used && MoonKnight.alter(player) != MoonKnightAlter.JAKE) {
			MoonKnightAbilities.cooldown(player, SNEAK, MoonKnightConfig.ALTER_SPECIAL_COOLDOWN);
		}`);
	rep(`	/** Jake: 8 s (x lunar power) of Invisibility, and every mob hunting him loses the scent. */
	public static boolean vanish(ServerPlayer player) {
		int ticks = Math.round(MoonKnightLunar.scale(MoonKnightConfig.VANISH_TICKS, MoonKnightAbilities.power(player)));
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
		VANISH_UNTIL.put(player.getUUID(), player.level().getGameTime() + ticks);`,
`	/**
	 * Jake: Invisibility until he ends it (v0.14.4: a toggle, no time limit -- Sneak+V again, an alter switch, the suit
	 * coming off or death), and every mob hunting him loses the scent. While it lasts no mob can target him at all
	 * ({@link #isVanished}). The infinite effect IS the state, so it survives a relog.
	 */
	public static boolean vanish(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));`);
	rep(`	/** Every mob within {@code radius} that is targeting {@code player} forgets him. Returns how many did. */`,
`	/** True while Jake's own Vanish is on (the infinite Invisibility it applies). */
	public static boolean inVanish(Player player) {
		MobEffectInstance inv = player.getEffect(MobEffects.INVISIBILITY);
		return inv != null && inv.isInfiniteDuration();
	}

	/** Ends Vanish (Sneak+V again, an alter switch, suit off). {@code announce}: the step-out effect and message. */
	public static void endVanish(ServerPlayer player, boolean announce) {
		if (!inVanish(player)) {
			return;
		}
		player.removeEffect(MobEffects.INVISIBILITY);
		if (announce) {
			ServerLevel level = player.serverLevel();
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.7, 0.3, 0.02);
			level.sendParticles(SHADOW, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.8, 0.4, 0.0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
					SoundSource.PLAYERS, 0.7f, 0.6f);
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.vanish_end")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
		}
	}

	/** Every mob within {@code radius} that is targeting {@code player} forgets him. Returns how many did. */`);
	rep(`		Long vanish = VANISH_UNTIL.get(player.getUUID());
		if (vanish != null && now >= vanish) {
			VANISH_UNTIL.remove(player.getUUID());
		}
`, ``);
	rep(`		VANISH_UNTIL.remove(player.getUUID());
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_FIST, false);`, `		endVanish(player, false);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_FIST, false);`);
	rep(`	public static void reconcile(ServerPlayer player) {
		boolean on = MoonKnight.isTransformed(player);
		MoonKnightAlter alter = MoonKnight.alter(player);`, `	public static void reconcile(ServerPlayer player) {
		boolean on = MoonKnight.isTransformed(player);
		MoonKnightAlter alter = MoonKnight.alter(player);
		// v0.14.4: Vanish is Jake's -- switching alter or taking the suit off ends it
		if ((!on || alter != MoonKnightAlter.JAKE) && inVanish(player)) {
			endVanish(player, on);
		}`);
});
edit('src/main/java/com/projecthero/mod/moonknight/MoonKnightConfig.java', rep => {
	rep(`	public static final int VANISH_TICKS = 160;`, `	/** Unused since v0.14.4 (Vanish is a toggle with no time limit); kept for reference. */
	public static final int VANISH_TICKS = 160;`);
});
edit('src/gametest/java/com/projecthero/mod/gametest/MoonKnightV0144GameTests.java', rep => {
	rep(`		p.removeEffect(MobEffects.INVISIBILITY);
		husk.setTarget(p);
		helper.assertTrue(husk.getTarget() == p, "once visible he can be targeted again");`,
`		helper.assertTrue(p.getEffect(MobEffects.INVISIBILITY).isInfiniteDuration(), "Vanish has no time limit");
		MoonKnightAlters.endVanish(p, true);
		helper.assertFalse(MoonKnightAlters.inVanish(p), "until he ends it");
		husk.setTarget(p);
		helper.assertTrue(husk.getTarget() == p, "once visible he can be targeted again");
		MoonKnightAlters.vanish(p);
		setAlter(p, MoonKnightAlter.STEVEN);
		MoonKnightAlters.reconcile(p);
		helper.assertFalse(MoonKnightAlters.inVanish(p), "switching away from Jake ends Vanish");`);
});
const P = 'src/main/resources/assets/projecthero/lang/en_us.json';
const raw = fs.readFileSync(P, 'utf8');
const j = JSON.parse(raw);
const k = 'projecthero.moon_knight.move.alter_special.desc';
const a = 'Jake -- Vanish: 8 s invisible, and everything hunting you loses track of you.';
const b = 'Jake -- Vanish: invisible until you press Sneak+V again (or switch alter / take the suit off). No mob can target you while you are, not even one you strike -- everything hunting you loses track of you. The 20 s cooldown starts when you step back out.';
if (j[k].includes(a)) j[k] = j[k].replace(a, b); else if (!j[k].includes(b)) throw new Error('lang ' + k);
j['message.projecthero.moon_knight.vanish_end'] = 'Jake steps out of the shadows';
const indent = raw.match(/\n(\s+)"/)[1];
let out = JSON.stringify(j, null, indent.includes('\t') ? '\t' : indent.length);
if (raw.includes('\r\n')) out = out.replace(/\n/g, '\r\n');
if (/\r?\n$/.test(raw)) out += raw.includes('\r\n') ? '\r\n' : '\n';
fs.writeFileSync(P, out);
console.log('lang ok');

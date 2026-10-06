// v0.15.1: Mark 3+ suits switch the mob highlight on by themselves when the helmet goes on. Run from the repo root.
const fs = require('fs');
function edit(f, pairs) {
	let s = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
	for (const [a, b] of pairs) {
		const n = s.split(a).length - 1;
		if (n !== 1) throw new Error(f + ': ' + n + ' matches for: ' + a.slice(0, 80));
		s = s.replace(a, b);
	}
	fs.writeFileSync(f, s.replace(/\n/g, '\r\n'));
}
const P = 'src/main/java/com/projecthero/mod/';
edit(P + 'ironman/ability/IronManAbilities.java', [[
	`	public static void clearMobHighlight(ServerPlayer player) {`,
	`	/**
	 * v0.15.1, explicit user request: the Mark 3 and every later mark switch the mob highlight on automatically -- once
	 * each time the helmet goes on (or the suit regains power). The Mark 6 / 7 toggles can still switch it off; it
	 * comes back the next time the helmet is put on. Free (no switch-on cost) and untimed.
	 */
	private static final java.util.Set<java.util.UUID> AUTO_HIGHLIGHT_ARMED = new java.util.HashSet<>();

	public static void tickAutoHighlight(ServerPlayer player, IronManSuit suit, boolean helmetPowered) {
		if (suit == null || suit.markNumber() < 3 || !helmetPowered) {
			AUTO_HIGHLIGHT_ARMED.remove(player.getUUID());
			return;
		}
		if (AUTO_HIGHLIGHT_ARMED.add(player.getUUID()) && !TonyStark.state(player).mobHighlightOn) {
			TonyStarkState s = TonyStark.state(player).copy();
			s.mobHighlightOn = true;
			player.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, s);
		}
	}

	public static void clearAutoHighlightState() {
		AUTO_HIGHLIGHT_ARMED.clear();
	}

	public static void clearMobHighlight(ServerPlayer player) {`]]);
edit(P + 'ironman/IronManSuitTicker.java', [[
	`		// v0.14.26: the Mark III targeting system (lock-on + auto-aim)
		IronManTargeting.tick(player, suit);
`,
	`		// v0.14.26: the Mark III targeting system (lock-on + auto-aim)
		IronManTargeting.tick(player, suit);
		// v0.15.1: Mark 3+ helmets switch the mob highlight on by themselves
		IronManAbilities.tickAutoHighlight(player, suit, suitId != null && IronManArmor.hasHelmet(player, suitId)
				&& IronManEnergy.energy(player, suitId) > 0f);
`]]);
edit(P + 'diagnostics/ServerStateReset.java', [[
	`		com.projecthero.mod.ironman.IronManTargeting.clearSessionState(); // v0.14.26
`,
	`		com.projecthero.mod.ironman.IronManTargeting.clearSessionState(); // v0.14.26
		com.projecthero.mod.ironman.ability.IronManAbilities.clearAutoHighlightState(); // v0.15.1
`]]);
console.log('ok');

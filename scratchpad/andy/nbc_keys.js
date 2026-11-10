// v0.17.3 NBC-style regeneration: the keys -- Shift+V tap Toggle Regen, Shift + hold V the Regen Menu, the menu's keys.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const A = 'src/main/java/com/projecthero/mod/andy/';
edit(A + 'AndyAbilityManager.java', [
  [` *   V (5)  tap: Reform               Shift+V  Regeneration speed
 *          hold 2 s: Bone Sword (AndyBoneSword: he struggles the arm off; the keys turn into the sword kit while it is out)`,
   ` *   V (5)  tap: Reform               Shift+V  Toggle Regen (auto-regrowth on / off)
 *          hold 2 s: Bone Sword (AndyBoneSword: he struggles the arm off; the keys turn into the sword kit while it is out)
 *          Shift + hold V: the Regen Menu (AndyRegenMenu: the keys grow / strip his ten segments while it is open)`],
  [`	private static final Map<UUID, long[]> V_HELD = new ConcurrentHashMap<>();
`, `	private static final Map<UUID, long[]> V_HELD = new ConcurrentHashMap<>();
	/** v0.17.3: Shift+V held down: {when it went down, 1 once the hold opened / closed the Regen Menu}. A shorter press: Toggle Regen. */
	private static final Map<UUID, long[]> SV_HELD = new ConcurrentHashMap<>();
	/** Shift + V held this long opens (or closes) the Regen Menu. */
	public static final int MENU_HOLD = 10;
`],
  [`		X_HELD.clear();
		V_HELD.clear();
`, `		X_HELD.clear();
		V_HELD.clear();
		SV_HELD.clear();
`],
  [`		X_HELD.remove(id);
		V_HELD.remove(id);
`, `		X_HELD.remove(id);
		V_HELD.remove(id);
		SV_HELD.remove(id);
`],
  [`		boolean shift = player.isShiftKeyDown();
		boolean victor = Andy.victor(player);
`, `		boolean shift = player.isShiftKeyDown();
		boolean victor = Andy.victor(player);
		// v0.17.3: Shift+V -- a tap toggles auto-regrowth, a hold opens / closes the Regen Menu (read before anything else)
		if (slot == AbilitySlot.SLOT_5 && (pressed ? shift : SV_HELD.containsKey(player.getUUID()))) {
			if (pressed) {
				V_HELD.remove(player.getUUID());
				SV_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), 0L });
			} else {
				long[] held = SV_HELD.remove(player.getUUID());
				if (held != null && held[1] == 0L && Andy.canAct(player)) {
					Andy.toggleRegen(player);
				}
			}
			return;
		}
		if (AndyRegenMenu.open(player)) {
			AndyRegenMenu.handle(player, slot, pressed, shift);
			return;
		}
`],
  [`				if (pressed) {
					if (shift) {
						V_HELD.remove(player.getUUID());
						if (Andy.canAct(player)) {
							Andy.cycleSpeed(player);
						}
					} else {
						V_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), 0L, 0L });
					}
				} else {`,
   `					if (pressed) {
					V_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), 0L, 0L });
				} else {`],
  [`		tickX(player);
		tickV(player);
`, `		tickX(player);
		tickV(player);
		tickShiftV(player);
		AndyRegenMenu.tick(player);
`],
  [`	/** Gametests: V has been down for {@code ticks}`, `	/** Shift+V held {@link #MENU_HOLD}: the Regen Menu opens (or closes); the release then does nothing. */
	private static void tickShiftV(ServerPlayer player) {
		long[] v = SV_HELD.get(player.getUUID());
		if (v == null || v[1] != 0L) {
			return;
		}
		if (player.level().getGameTime() - v[0] >= MENU_HOLD) {
			v[1] = 1L;
			if (Andy.canAct(player)) {
				AndyRegenMenu.toggle(player);
			}
		}
	}

	/** Gametests: Shift+V has been down for {@code ticks}, and the hold ticks. */
	public static void forTestsHoldShiftV(ServerPlayer player, int ticks) {
		long[] v = SV_HELD.get(player.getUUID());
		if (v != null) {
			v[0] = player.level().getGameTime() - ticks;
		}
		tickShiftV(player);
	}

	/** Gametests: V has been down for {@code ticks}`],
  [`		String[] sword = AndyBoneSword.idsOf(player, slot);
		if (sword != null) {
			return sword; // v0.17.3: the Bone Sword kit while it is out
		}`, `		if (AndyRegenMenu.open(player)) {
			String[] menu = AndyRegenMenu.idsOf(slot);
			return menu != null ? menu : new String[0]; // v0.17.3: the Regen Menu's buttons while it is open
		}
		String[] sword = AndyBoneSword.idsOf(player, slot);
		if (sword != null) {
			return sword; // v0.17.3: the Bone Sword kit while it is out
		}`],
  [`			// v0.17.3: tap V Reform, Shift+V the speed (hold V, the Bone Sword, is the third: boneSwordHoldId)`, `			// v0.17.3: tap V Reform, Shift+V Toggle Regen (hold V, the Bone Sword, is the third: boneSwordHoldId)`],
]);
edit(A + 'AndyBoneSword.java', [
  [`				// v0.17.3: V puts it away, Shift+V is still the regeneration speed
				if (pressed) {
					if (shift) {
						if (Andy.canAct(p)) {
							Andy.cycleSpeed(p);
						}
					} else {
						putAway(p, true, true);
					}
				}`, `				// v0.17.3: V puts it away (Shift+V, Toggle Regen, is read before the sword kit: AndyAbilityManager)
				if (pressed && !shift) {
					putAway(p, true, true);
				}`],
]);
edit(A + 'AndyAbilities.java', [
  [`		if (s != null && s.flags != 0) {
			AndyState n = s.copy();
			if (s.flag(AndyState.FLAG_PROPELLING)) {
				n.setEx(AndyState.X_CUT_REGROW, p.level().getGameTime()); // the hands and feet grow back
			}
			n.flags = 0;
			Andy.save(p, n);
		}`, `		if (s != null && (s.flags & ~AndyState.FLAG_REGEN_OFF) != 0) {
			AndyState n = s.copy();
			// (v0.17.3: the hands and feet are segments of their own now: they grow back by the regrowth once he has landed)
			n.flags = s.flags & AndyState.FLAG_REGEN_OFF; // auto-regrowth off stays off
			Andy.save(p, n);
		}`],
]);
edit(A + 'AndyFlight.java', [
  [` * <p>The hands and feet are a look only (the arm and leg parts stay whole); what the boost burns is really broken (kind
 * {@link AndyState#KIND_BURNT}), the stage in progress drawn shortened by {@link AndyState#X_BURN_TICKS}.`,
   ` * <p>v0.17.3: the hands and feet are segments of their own ({@link AndyState#RIGHT_HAND} ...): taking off breaks them (no burst:
 * the jets are the look) and they grow back by the regrowth once he lands. What the boost burns is really broken (kind
 * {@link AndyState#KIND_BURNT}), the stage in progress drawn shortened by {@link AndyState#X_BURN_TICKS}; a stage only part
 * burnt when he lands is lost whole (it regrows in about a second).`],
  [`		Andy.save(p, n);
		// the jets' start / loop / stop are client-side`, `		Andy.save(p, n);
		for (int end = AndyState.RIGHT_HAND; end <= AndyState.LEFT_FOOT; end++) {
			Andy.breakPart(p, end, AndyState.KIND_SPENT); // the hands and feet burst off (the jets are the look)
		}
		// the jets' start / loop / stop are client-side`],
  [`		n.setFlag(AndyState.FLAG_PROPELLING, false);
		n.setFlag(AndyState.FLAG_BOOST, false);
		n.setEx(AndyState.X_CUT_REGROW, now);
		n.abilityReadyAt.put(AndyAbilities.PROPULSION, now + AndyConfig.PROPULSION_COOLDOWN);
		Andy.save(p, n);
		p.resetFallDistance();`, `		n.setFlag(AndyState.FLAG_PROPELLING, false);
		n.setFlag(AndyState.FLAG_BOOST, false);
		n.setEx(AndyState.X_CUT_REGROW, 0L);
		n.abilityReadyAt.put(AndyAbilities.PROPULSION, now + AndyConfig.PROPULSION_COOLDOWN);
		// a stage only part burnt is lost whole: it grows back with the hands and feet
		int[] half = new int[AndyState.PARTS];
		int halves = 0;
		for (int part = AndyState.BODY; part < AndyState.PARTS; part++) {
			if (burnt(s, part) > 0f) {
				half[halves++] = part;
			}
		}
		n.setEx(AndyState.X_BURN_TICKS, 0L);
		n.setEx(AndyState.X_BURN_SEG, 0L);
		Andy.save(p, n);
		for (int k = 0; k < halves; k++) {
			Andy.breakPart(p, half[k], AndyState.KIND_BURNT);
		}
		p.resetFallDistance();`],
  [`	/** How long the hands, feet and any half-burnt stage take to grow back after a landing. */
	public static float cutRegrowTicks(AndyState s) {
		float missing = AndyConfig.HAND_ROWS / 12f;
		for (int part = AndyState.BODY; part < AndyState.PARTS; part++) {
			missing = Math.max(missing, burnt(s, part));
		}
		return AndyConfig.cutRegrowTicks(s.regenSpeed, missing);
	}

`, ``],
]);
console.log('ok');

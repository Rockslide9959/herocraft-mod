// v0.17.3 NBC-style regeneration: Andy.java -- free regrowth on ten segments, NBC's frame timing and order, Toggle Regen,
// no food, no speeds, no auto-eat; armour falls off with the parts it sits on (AndyArmour).
const fs = require('fs');
const f = 'src/main/java/com/projecthero/mod/andy/Andy.java';
let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
function rep(a, b) { if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 100)); t = t.replace(a, b); }
function cut(fromKey, toKey, repl) {
  const a = t.indexOf(fromKey); const b = t.indexOf(toKey, a + 1);
  if (a < 0 || b < 0) throw new Error('cut: ' + fromKey.slice(0, 60) + ' / ' + toKey.slice(0, 60));
  t = t.slice(0, a) + repl + t.slice(b);
}

rep(` * Six parts (head, body, both arms, both legs), each with its own HP. Every hit is placed on a part by where it lands
 * ({@link AndyParts}); a part at 0 HP breaks ({@link #breakPart}) -- every client bursts it into one cube per skin texel --
 * and is gone until it regrows: after {@link AndyConfig#REGROW_DELAY} it grows back from the joint outward over
 * {@link AndyConfig#REGROW_TICKS} (the bone pushes out, the muscle builds round it, the skin closes over), costing hunger. A
 * tap of V cycles the speed (Normal / Fast / Burst; starving, half Normal). With only the head left everything grows back in a set order
 * ({@link #planHeadOnly}) and he gets about however that lets him ({@link AndyStages}).`,
` * Six parts (head, body, both arms, both legs), each with its own HP. Every hit is placed on a part by where it lands
 * ({@link AndyParts}); a part at 0 HP breaks ({@link #breakPart}) -- every client bursts it into one cube per skin texel --
 * and is gone until it regrows. v0.17.3 (user: "build our andy power the same way theyve built theirs ... the regeneration to
 * work the same ... at the same speed"): the regrowth is NBC Heroes' Regenerative Segmentation -- ten segments (the six parts
 * plus the hands and feet), each growing one frame a tick ({@link AndyConfig#FRAMES}: about a second a part) in NBC's order
 * (the body once the head has 4 frames, the limbs once the body has 7, each hand / foot once its limb has 5), free, starting
 * at once. Shift+V turns auto-regrowth off (Toggle Regen); the Regen Menu ({@link AndyRegenMenu}) grows segments a stage at a
 * time (bone, flesh, skin) or strips them. He gets about however what is left lets him ({@link AndyStages}); worn armour falls
 * off the parts it sat on ({@link AndyArmour}).`);
rep(`	/** Food points a regrowth still owes the hunger bar, below one whole point (taken with the next part). */
	private static final Map<UUID, Float> FOOD_OWED = new ConcurrentHashMap<>();
`, `	/** v0.17.3 Regen Menu "Regenerate Whole Body": everything grows back even with auto-regrowth off, until he is whole. */
	private static final java.util.Set<UUID> GROW_ALL = ConcurrentHashMap.newKeySet();
`);
rep(`		AndyAutoEat.clearSessionState();
		FOOD_OWED.clear();
`, `		GROW_ALL.clear();
		AndyArmour.clearSessionState();
		AndyRegenMenu.clearSessionState();
`);
rep(`		AndyAutoEat.forget(id);
		FOOD_OWED.remove(id);
`, `		GROW_ALL.remove(id);
		AndyArmour.forget(id);
		AndyRegenMenu.forget(id);
`);
rep(`	public static int regenSpeed(Player player) {
		AndyState s = peek(player);
		return s == null ? AndyConfig.SPEED_NORMAL : s.regenSpeed;
	}

`, ``);
// clearTransient
rep(`			n.partHp = AndyState.full();
			for (int i = 0; i < AndyState.PARTS; i++) {
				n.setTimes(i, 0L, 0L, 0L);
			}
			n.kinds = 0;
			n.reformUntil = 0L;
			n.flags = s.flags & AndyState.FLAG_REGEN_OFF; // v0.17.3: Off stays Off`,
`			n.partHp = AndyState.full();
			for (int i = 0; i < AndyState.SEGS; i++) {
				n.setTimes(i, 0L, 0L, 0L);
			}
			n.kinds = 0;
			n.reformUntil = 0L;
			n.flags = s.flags & AndyState.FLAG_REGEN_OFF; // v0.17.3: auto-regrowth off stays off`);
// breakPart
rep(`	public static void breakPart(ServerPlayer player, int part, int kind) {
		AndyState s = state(player);
		if (!s.hasPower || s.broken(part)) {
			return;
		}
		long now = Math.max(1L, player.level().getGameTime());
		AndyState n = s.copy();
		n.partHp.set(part, 0f);
		n.setTimes(part, now, 0L, 0L);
		n.setKind(part, kind);`,
`	public static void breakPart(ServerPlayer player, int part, int kind) {
		AndyState s = state(player);
		if (!s.hasPower || (s.broken(part) && s.regrowStart(part) <= 0L)) {
			return;
		}
		long now = Math.max(1L, player.level().getGameTime());
		AndyState n = s.copy();
		if (part < AndyState.PARTS) {
			n.partHp.set(part, 0f);
		}
		n.setTimes(part, now, 0L, 0L);
		n.setKind(part, kind);
		// v0.17.3 (NBC's segments): a limb takes its hand / foot with it
		int end = AndyState.extremity(part);
		if (end >= 0 && !(n.broken(end) && n.regrowStart(end) <= 0L)) {
			n.setTimes(end, now, 0L, 0L);
			n.setKind(end, kind);
		}`);
rep(`		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = AndyParts.centre(player, part);
		if (kind == AndyState.KIND_BURST) {`,
`		save(player, n);
		reconcile(player);
		AndyArmour.partLost(player, part, kind); // v0.17.3 (user): the armour on it falls off / the shoulder pad goes
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = AndyParts.centre(player, AndyState.limbOf(part));
		if (kind == AndyState.KIND_BURST) {`);
// shatter
rep(`		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			if (!n.broken(i) || n.kind(i) != AndyState.KIND_LAUNCHED) {
				n.setKind(i, AndyState.KIND_SHATTER);
			}
			n.setTimes(i, n.broken(i) ? n.brokenAt(i) : now, 0L, 0L);
			n.partHp.set(i, 0f);
		}
		if (n.broken(AndyState.HEAD)) {
			// the head was already off (launched or burst): it re-forms first, then the rest
			long start = now + 2;
			n.setTimes(AndyState.HEAD, n.brokenAt(AndyState.HEAD), start, start + 14);
		}`,
`		for (int i = AndyState.BODY; i < AndyState.SEGS; i++) {
			if (!n.broken(i) || n.kind(i) != AndyState.KIND_LAUNCHED) {
				n.setKind(i, AndyState.KIND_SHATTER);
			}
			n.setTimes(i, n.broken(i) ? n.brokenAt(i) : now, 0L, 0L);
			if (i < AndyState.PARTS) {
				n.partHp.set(i, 0f);
			}
		}
		if (n.broken(AndyState.HEAD)) {
			// the head was already off (launched or burst): it re-forms first, then the rest (whatever auto-regrowth is set to)
			long start = now + 2;
			n.setTimes(AndyState.HEAD, n.brokenAt(AndyState.HEAD), start, start + AndyConfig.FRAMES[AndyState.HEAD]);
		}`);
rep(`		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		AndySfx.shatter(level, c);`,
`		reconcile(player);
		for (int i = AndyState.BODY; i < AndyState.SEGS; i++) {
			AndyArmour.partLost(player, i, AndyState.KIND_SHATTER);
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		AndySfx.shatter(level, c);`);
// restore
rep(`		n.partHp = AndyState.full();
		for (int i = 0; i < AndyState.PARTS; i++) {
			n.setTimes(i, 0L, 0L, 0L);
		}
		n.kinds = 0;
		n.reformUntil = 0L;
		clearExtra(n);`,
`		n.partHp = AndyState.full();
		for (int i = 0; i < AndyState.SEGS; i++) {
			n.setTimes(i, 0L, 0L, 0L);
		}
		n.kinds = 0;
		n.reformUntil = 0L;
		clearExtra(n);`);
// speed section -> Toggle Regen
cut(`	// ---------------------------------------------------------------- V: the speed`, `	/** v0.17.3: a lost part put straight back`,
`	// ---------------------------------------------------------------- Shift+V: Toggle Regen

	/**
	 * v0.17.3 Shift+V (NBC's Toggle Regen): auto-regrowth on or off. Off, nothing starts growing back on its own (a segment
	 * already growing finishes); the Regen Menu grows segments a stage at a time. Healing his health and parts goes on either way.
	 */
	public static void toggleRegen(ServerPlayer player) {
		AndyState n = state(player).copy();
		boolean off = !n.flag(AndyState.FLAG_REGEN_OFF);
		n.setFlag(AndyState.FLAG_REGEN_OFF, off);
		save(player, n);
		if (!off) {
			GROW_ALL.remove(player.getUUID());
		}
		LAST_MESSAGE.remove(player.getUUID());
		say(player, off ? "message.projecthero.andy.regen_auto_off" : "message.projecthero.andy.regen_auto_on", ChatFormatting.RED);
		player.level().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.8f, off ? 0.6f : 1.2f);
	}

	static boolean regrowingAny(AndyState s, long now) {
		for (int i = 0; i < AndyState.SEGS; i++) {
			if (s.broken(i) && s.regrowStart(i) > 0L && s.regrowStart(i) <= now && now < s.regrowEnd(i)) {
				return true;
			}
		}
		return false;
	}

`);
rep(`		AndyState n = s.copy();
		n.setTimes(part, 0L, 0L, 0L);
		n.setKind(part, AndyState.KIND_NONE);
		n.partHp.set(part, AndyConfig.PART_HP[part]);
		save(player, n);
		reconcile(player);
	}

	/** v0.17.3: regeneration Off (or back on at the current speed). */`,
`		AndyState n = s.copy();
		n.setTimes(part, 0L, 0L, 0L);
		n.setKind(part, AndyState.KIND_NONE);
		if (part < AndyState.PARTS) {
			n.partHp.set(part, AndyConfig.PART_HP[part]);
		}
		save(player, n);
		reconcile(player);
	}

	/** v0.17.3: auto-regrowth off (Shift+V, Toggle Regen) or back on. */`);
// Reform: free
cut(`	/**
	 * What Reform costs right now:`, `	/**
	 * v0.17.3 tap V (user: ripping off your arm is useless`, ``);
rep(`	 * too), he heals {@link AndyConfig#REFORM_HEAL}, and the fire and every harmful effect on him go. Costs {@link #reformCost},
	 * saturation first; without enough food it fails. Works with regeneration Off (a deliberate choice). Not while the Bone`,
`	 * too), he heals {@link AndyConfig#REFORM_HEAL}, and the fire and every harmful effect on him go. Free (v0.17.3: the
	 * regrowth is free now); works with auto-regrowth off (a deliberate choice). Not while the Bone`);
rep(`		float cost = reformCost(player);
		ServerLevel level = (ServerLevel) player.level();
		if (!canAfford(player, cost)) {
			LAST_MESSAGE.remove(player.getUUID());
			say(player, "message.projecthero.andy.reform_no_food", ChatFormatting.GRAY, String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cost)));
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.4f);
			return false;
		}
		payFood(player, cost);
		long now`, `		ServerLevel level = (ServerLevel) player.level();
		long now`);
rep(`		for (int i = 0; i < AndyState.PARTS; i++) {
			if (n.broken(i)) {
				mask |= 1L << i;
				n.setTimes(i, 0L, 0L, 0L);
				n.setKind(i, AndyState.KIND_NONE);
				n.partHp.set(i, AndyConfig.PART_HP[i]);
			}
		}`,
`		for (int i = 0; i < AndyState.SEGS; i++) {
			if (n.broken(i)) {
				mask |= 1L << AndyState.limbOf(i);
				n.setTimes(i, 0L, 0L, 0L);
				n.setKind(i, AndyState.KIND_NONE);
				if (i < AndyState.PARTS) {
					n.partHp.set(i, AndyConfig.PART_HP[i]);
				}
			}
		}`);
// tick: auto-eat out, the scheduler, healing
rep(`		// v0.17.3 (user: "remove the players passive auto feeding since this armour will do it for him"): only Andy's Suit's
		// chestplate feeds him
		if (com.projecthero.mod.andy.item.AndySuitItem.feedsHim(player)) {
			AndyAutoEat.tick(player);
		}
`, ``);
cut(`		AndyState n = null;
		boolean whole = false;
		// nothing starts growing back mid-flight`, `		AndyState cur = n != null ? n : s;
		// the bites the finger shots took`,
`		AndyState n = null;
		boolean whole = false;
		// v0.17.3 NBC's Regen_Loop: every segment that may grow (its parent far enough along, auto-regrowth on) starts at once
		// and grows one frame a tick to whole; nothing starts mid-flight (the hands and feet wait for the landing)
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		boolean auto = !s.flag(AndyState.FLAG_REGEN_OFF) || GROW_ALL.contains(player.getUUID());
		for (int i = 0; i < AndyState.SEGS; i++) {
			AndyState cur = n != null ? n : s;
			if (!cur.broken(i)) {
				continue;
			}
			long st = cur.regrowStart(i), end = cur.regrowEnd(i);
			if (st <= 0L) {
				if (flying || !auto || !mayStart(cur, i, now)) {
					continue;
				}
				if (n == null) {
					n = s.copy();
				}
				n.setTimes(i, cur.brokenAt(i), now, now + AndyConfig.FRAMES[i]);
				startedRegrowing(player, i, cur.kind(i));
			} else if (auto && GROW_ALL.contains(player.getUUID()) && end - st < AndyConfig.FRAMES[i]) {
				// Regenerate Whole Body: a segment the menu held at a stage grows on to whole
				if (n == null) {
					n = s.copy();
				}
				n.setTimes(i, cur.brokenAt(i), st, st + AndyConfig.FRAMES[i]);
			} else if (end - st >= AndyConfig.FRAMES[i] && now >= end) {
				if (n == null) {
					n = s.copy();
				}
				n.setTimes(i, 0L, 0L, 0L);
				n.setKind(i, AndyState.KIND_NONE);
				if (i < AndyState.PARTS) {
					n.partHp.set(i, AndyConfig.PART_HP[i]);
				}
				whole = true;
				// v0.17.3 Andy sounds: the wet snap of a finished part is client-side (AndySounds.regrowth -> andy.regrow.finish)
			}
		}
		if (GROW_ALL.contains(player.getUUID()) && !(n != null ? n : s).anyBroken()) {
			GROW_ALL.remove(player.getUUID());
		}
		AndyState cur = n != null ? n : s;
		// the bites the finger shots took`);
cut(`		// parts knit back slowly between fights`, `		// v0.17.3 (tester: "A way people can naturally pick up the head")`,
`		// parts knit back between fights, and his health comes back (v0.17.3: one rate now, free)
		if (now % 10 == 0 && now - cur.lastHitAt > AndyConfig.KNIT_DELAY) {
			for (int i = 0; i < AndyState.PARTS; i++) {
				if (!cur.broken(i) && cur.hp(i) < AndyConfig.PART_HP[i]) {
					if (n == null) {
						n = s.copy();
						cur = n;
					}
					n.partHp.set(i, Math.min(AndyConfig.PART_HP[i], n.hp(i) + AndyConfig.KNIT_PER_SECOND * 0.5f));
				}
			}
		}
		if (now % 10 == 0 && now - cur.lastHitAt > AndyConfig.HEAL_DELAY && player.getHealth() < player.getMaxHealth() && player.isAlive()) {
			player.heal(Math.min(AndyConfig.HEAL_PER_SECOND * 0.5f, player.getMaxHealth() - player.getHealth()));
		}
`);
rep(`		AndyAbilityManager.tickHold(player);
		AndyAbilities.tick(player);
		AndyFlight.tick(player);
	}`, `		AndyAbilityManager.tickHold(player);
		AndyAbilities.tick(player);
		AndyFlight.tick(player);
		AndyArmour.tick(player);
	}`);
// the old planning / food helpers out; NBC's rule in
cut(`	/** Only the head is left and nothing of the rest has been planned to grow back yet. */`, `	private static void startedRegrowing(`,
`	/**
	 * NBC's order: a broken segment may start growing once the one it grows from ({@link AndyState#parentOf}) has enough frames
	 * ({@link AndyConfig#BODY_NEEDS_HEAD} ...), or is whole. Not the arm (and hand) that is the Bone Sword in his hand, nor an arm
	 * used up by finger shots until he has stopped shooting for {@link AndyConfig#EATEN_REGROW_WAIT}.
	 */
	public static boolean mayStart(AndyState s, int seg, long now) {
		int sword = s.boneSwordArm();
		if (sword >= 0 && AndyState.limbOf(seg) == sword) {
			return false; // v0.17.3: it grows back once the sword is put away
		}
		if (s.kind(seg) == AndyState.KIND_SPENT && now < s.ex(AndyState.X_LAST_SHOT) + AndyConfig.EATEN_REGROW_WAIT) {
			return false;
		}
		int parent = AndyState.parentOf(seg);
		return parent < 0 || s.frame(parent, now) >= needs(seg);
	}

	/** The frames {@code seg}'s parent must have before it starts (NBC's numbers). */
	public static int needs(int seg) {
		return switch (seg) {
			case AndyState.HEAD -> 0;
			case AndyState.BODY -> AndyConfig.BODY_NEEDS_HEAD;
			case AndyState.RIGHT_ARM, AndyState.LEFT_ARM, AndyState.RIGHT_LEG, AndyState.LEFT_LEG -> AndyConfig.LIMB_NEEDS_BODY;
			case AndyState.RIGHT_HAND, AndyState.LEFT_HAND -> AndyConfig.HAND_NEEDS_ARM;
			default -> AndyConfig.FOOT_NEEDS_LEG;
		};
	}

	/** Regen Menu: Regenerate Whole Body -- everything grows back now, auto-regrowth on or off. */
	static void growAll(ServerPlayer player) {
		GROW_ALL.add(player.getUUID());
	}

`);
fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');

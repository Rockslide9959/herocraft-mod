// v0.17.3 Undead kit: rewires Z / Shift+Z / V / Shift+V in UndeadAbilityManager. Run from the repo root.
const fs = require('fs');
const f = 'src/main/java/com/projecthero/mod/undead/UndeadAbilityManager.java';
let t = fs.readFileSync(f, 'utf8');
const crlf = t.includes('\r\n');
t = t.replace(/\r\n/g, '\n');
function rep(a, b) {
	if (!t.includes(a)) throw new Error('not found: ' + a.slice(0, 90));
	t = t.replace(a, b);
}
rep(` *   Z (4)  Ten Twists: Vortex Bullet Shift+Z  Parts Bullet Head
 *   V (5)  tap: Reform               Shift+V  Toggle Regen (auto-regrowth on / off)
 *          hold 2 s: Bone Sword (UndeadBoneSword: he struggles the arm off; the keys turn into the sword kit while it is out)
 *          Shift + hold V: the Regen Menu (UndeadRegenMenu: the keys grow / strip his ten segments while it is open)`,
` *   Z (4)  Ten Twists: Vortex Bullet (UndeadVortex)   Shift + hold Z 5 s: Unluck Meteor (UndeadMeteor; let go early: cancelled)
 *   V (5)  tap: Reform               Shift+V  Rib Cage Guard (UndeadRibGuard: while held, up to 4 s; a tap keeps it up 4 s)
 *          hold 2 s: the bone katana (UndeadBoneSword: he struggles the arm off; R / G / Z / C turn into its sword skills while
 *          it is out, V or Shift+V puts it away)`);
rep(`	/** v0.17.3: Shift+V held down: {when it went down, 1 once the hold opened / closed the Regen Menu}. A shorter press: Toggle Regen. */
	private static final Map<UUID, long[]> SV_HELD = new ConcurrentHashMap<>();
	/** Shift + V held this long opens (or closes) the Regen Menu. */
	public static final int MENU_HOLD = 10;
`, ``);
rep(`		V_HELD.clear();
		SV_HELD.clear();`, `		V_HELD.clear();`);
rep(`		V_HELD.remove(id);
		SV_HELD.remove(id);`, `		V_HELD.remove(id);`);
rep(`		// v0.17.3: Shift+V -- a tap toggles auto-regrowth, a hold opens / closes the Regen Menu (read before anything else)
		if (slot == AbilitySlot.SLOT_5 && (pressed ? shift : SV_HELD.containsKey(player.getUUID()))) {
			if (pressed) {
				V_HELD.remove(player.getUUID());
				SV_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), 0L });
			} else {
				long[] held = SV_HELD.remove(player.getUUID());
				if (held != null && held[1] == 0L && Undead.canAct(player)) {
					Undead.toggleRegen(player);
				}
			}
			return;
		}
`, ``);
rep(`			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						UndeadAbilities.headBullet(player);
					} else {
						UndeadAbilities.vortexBullet(player);
					}
				}
			}`,
`			case SLOT_4 -> {
				// v0.17.3 kit: Z is Ten Twists -- Vortex Bullet (the wind-up runs in UndeadVortex.tick); Shift + hold Z charges the
				// Unluck Meteor (UndeadMeteor.tick calls it down at 5 s, still held); let go before that and it is cancelled
				if (pressed) {
					if (shift) {
						UndeadMeteor.startCharge(player);
					} else {
						UndeadAbilities.vortexBullet(player);
					}
				} else {
					UndeadMeteor.cancel(player);
				}
			}`);
rep(`				if (pressed) {
					V_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), 0L, 0L });
				} else {
					long[] held = V_HELD.remove(player.getUUID());`,
`				// v0.17.3 kit: Shift+V is the Rib Cage Guard (up while held, at most 4 s; a tap leaves it up the whole 4 s)
				if (pressed && shift) {
					V_HELD.remove(player.getUUID());
					UndeadRibGuard.raise(player);
				} else if (pressed) {
					V_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), 0L, 0L });
				} else {
					UndeadRibGuard.release(player);
					long[] held = V_HELD.remove(player.getUUID());`);
rep(`		tickV(player);
		tickShiftV(player);`, `		tickV(player);`);
rep(`	/** Shift+V held {@link #MENU_HOLD}: the Regen Menu opens (or closes); the release then does nothing. */
	private static void tickShiftV(ServerPlayer player) {
		long[] v = SV_HELD.get(player.getUUID());
		if (v == null || v[1] != 0L) {
			return;
		}
		if (player.level().getGameTime() - v[0] >= MENU_HOLD) {
			v[1] = 1L;
			if (Undead.canAct(player)) {
				UndeadRegenMenu.toggle(player);
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

`, ``);
rep(`			case SLOT_4 -> new String[] { UndeadAbilities.VORTEX, UndeadAbilities.HEAD_BULLET };
			// v0.17.3: tap V Reform, Shift+V Toggle Regen (hold V, the Bone Sword, is the third: boneSwordHoldId)
			case SLOT_5 -> new String[] { UndeadAbilities.REFORM, UndeadConfig.VICTOR_ENABLED ? UndeadAbilities.CARD : UndeadAbilities.REGEN };`,
`			// v0.17.3 kit: Z Vortex Bullet, Shift + hold Z Unluck Meteor; tap V Reform, Shift+V Rib Cage Guard (hold V, the katana, is
			// the third: boneSwordHoldId)
			case SLOT_4 -> new String[] { UndeadAbilities.VORTEX, UndeadMeteor.ID };
			case SLOT_5 -> new String[] { UndeadAbilities.REFORM, UndeadRibGuard.ID };`);
if (crlf) t = t.replace(/\n/g, '\r\n');
fs.writeFileSync(f, t);
console.log('ok');

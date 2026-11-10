package com.projecthero.mod.undead;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Bridges the universal ability slots to Undead (v0.17.3). {@link com.projecthero.mod.hero.AbilityRouter} hands him the slots
 * when {@link #hasContext} is true. Shift is read server-side. H stays the power selector; N is not his.
 *
 * <pre>
 *   R (1)  tap: Parts Bullet         Shift+R  Crimson Bullet          (Victor: Dead Vulcan / Division Bullet)
 *          hold: Stretch Punch (the arm draws back past PUNCH_HOLD_TICKS, thrown on release)
 *   G (2)  hold: Blood Spray         Shift+G  Blood Whirlwind          (Victor: Dead Blade)
 *   X (3)  tap: Eject Feet           Shift+X  Combustion Punch
 *          hold 2 s: Head Throw (UndeadHeadThrow: start at 2 s, tickHeld while held, release on let-go, cancel if cut short)
 *   Z (4)  Ten Twists: Vortex Bullet Shift+Z  Parts Bullet Head
 *   V (5)  tap: Reform               Shift+V  Toggle Regen (auto-regrowth on / off)
 *          hold 2 s: Bone Sword (UndeadBoneSword: he struggles the arm off; the keys turn into the sword kit while it is out)
 *          Shift + hold V: the Regen Menu (UndeadRegenMenu: the keys grow / strip his ten segments while it is open)
 *   (Victor -- Pull the Card and the kit in brackets -- is off: UndeadConfig.VICTOR_ENABLED)
 *   C (6)  hold: Deadline            Shift+C  Division Bullet          (Victor: Dead Road)
 *   double-tap jump in the air: the flight (UndeadFlight.toggle, from UndeadFlightPayload: doubleJump)
 * </pre>
 * The ejection moves (flight, G, Shift+G, X, Shift+X, C) all blow ends off through {@link UndeadEject}.
 */
public final class UndeadAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();
	/** A plain R held down (not Victor): when it went down. Tap = Parts Bullet on release; hold = the stretch punch. */
	private static final Map<UUID, Long> R_HELD = new ConcurrentHashMap<>();
	/**
	 * A plain X held down: {when it went down, 1 once the Head Throw started (the hold reached {@link #HEAD_THROW_HOLD})}. A
	 * release before that is a tap: Eject Feet.
	 */
	private static final Map<UUID, long[]> X_HELD = new ConcurrentHashMap<>();
	/**
	 * v0.17.3: a plain V held down: {when it went down, 1 once the hold is spoken for (the struggle started, or it could not and
	 * said why), 1 once the sword is drawn}. A release before {@link UndeadConfig#BONE_SWORD_TAP} is a tap: Reform.
	 */
	private static final Map<UUID, long[]> V_HELD = new ConcurrentHashMap<>();
	/** v0.17.3: Shift+V held down: {when it went down, 1 once the hold opened / closed the Regen Menu}. A shorter press: Toggle Regen. */
	private static final Map<UUID, long[]> SV_HELD = new ConcurrentHashMap<>();
	/** Shift + V held this long opens (or closes) the Regen Menu. */
	public static final int MENU_HOLD = 10;
	/** v0.17.3: a plain X held this long is the Head Throw (2 s). */
	public static final int HEAD_THROW_HOLD = 40;

	private UndeadAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
		R_HELD.clear();
		X_HELD.clear();
		V_HELD.clear();
		SV_HELD.clear();
		UndeadLaunch.clearSessionState();
		UndeadEject.clearSessionState();
		UndeadBloodMoves.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
		R_HELD.remove(id);
		X_HELD.remove(id);
		V_HELD.remove(id);
		SV_HELD.remove(id);
		UndeadLaunch.forget(id);
		UndeadEject.forget(id);
		UndeadBloodMoves.forget(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Undead.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	private static boolean freshPress(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		long[] last = LAST_PRESS.computeIfAbsent(player.getUUID(), k -> {
			long[] a = new long[AbilitySlot.values().length];
			java.util.Arrays.fill(a, -1L);
			return a;
		});
		int i = slot.ordinal();
		if (!pressed) {
			last[i] = -1L;
			return true;
		}
		long now = player.level().getGameTime();
		boolean repeat = last[i] >= 0L && now - last[i] <= REPEAT_WINDOW && now >= last[i];
		last[i] = now;
		return !repeat;
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!freshPress(player, slot, pressed)) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		boolean victor = Undead.victor(player);
		// v0.17.3: Shift+V -- a tap toggles auto-regrowth, a hold opens / closes the Regen Menu (read before anything else)
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
		if (UndeadRegenMenu.open(player)) {
			dropHeld(player); // v0.17.3: an X / G / C hold from before the menu opened ends here
			UndeadRegenMenu.handle(player, slot, pressed, shift);
			return;
		}
		if (Undead.state(player).boneSword() && UndeadBoneSword.handle(player, slot, pressed, shift)) {
			// v0.17.3: the Bone Sword's own kit while it is out; nothing half-held from before carries over
			R_HELD.remove(player.getUUID());
			V_HELD.remove(player.getUUID());
			dropHeld(player);
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (!pressed) {
					Long down = R_HELD.remove(player.getUUID());
					if (down != null) {
						long held = player.level().getGameTime() - down;
						if (Undead.state(player).flag(com.projecthero.mod.undead.data.UndeadState.FLAG_PUNCH)) {
							if (held >= UndeadConfig.PUNCH_MIN_HOLD) {
								UndeadAbilities.throwPunch(player);
							} else {
								UndeadAbilities.cancelPunch(player);
							}
						} else {
							UndeadAbilities.partsBullet(player); // a tap
						}
					}
				} else if (victor) {
					if (shift) {
						UndeadAbilities.division(player);
					} else {
						UndeadAbilities.vulcan(player);
					}
				} else if (shift) {
					UndeadAbilities.crimsonBullet(player);
				} else {
					R_HELD.put(player.getUUID(), player.level().getGameTime());
				}
			}
			case SLOT_2 -> {
				// v0.17.3 (user): hold G -- Blood Spray, until let go (then its cooldown); Shift+G -- Blood Whirlwind. (The old
				// Crimson Crescent Moons moved into the Bone Sword's kit.)
				if (pressed) {
					if (shift) {
						UndeadBloodMoves.startWhirl(player);
					} else if (victor) {
						UndeadAbilities.deadBlade(player);
					} else {
						UndeadBloodMoves.startSpray(player);
					}
				} else if (UndeadBloodMoves.spraying(player)) {
					UndeadBloodMoves.stopSpray(player);
				}
			}
			case SLOT_3 -> {
				// v0.17.3 (user): tap X -- Eject Feet (on release); Shift+X -- Combustion Punch (on press); a plain X held
				// HEAD_THROW_HOLD -- the Head Throw (UndeadHeadThrow, still held: tickX), thrown on release
				if (pressed) {
					if (shift) {
						X_HELD.remove(player.getUUID());
						UndeadLaunch.combustionPunch(player);
					} else {
						X_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), 0L });
					}
				} else {
					long[] held = X_HELD.remove(player.getUUID());
					if (held != null && held[1] != 0L) {
						UndeadHeadThrow.release(player);
					} else if (held != null) {
						UndeadLaunch.ejectFeet(player); // a tap
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						UndeadAbilities.headBullet(player);
					} else {
						UndeadAbilities.vortexBullet(player);
					}
				}
			}
			case SLOT_5 -> {
				// v0.17.3 (user: "make the bone sword hold V for 2 seconds and the regen speed switch shift+V please, put another
				// move on regular V"): Shift+V cycles the regeneration speed; a plain tap is Reform (on release; it replaced Tear Off); held, he
				// reaches over and struggles his off arm loose (tickV) and at BONE_SWORD_HOLD it tears off as the Bone Sword.
				// Pull the Card (UndeadAbilities.pullCard) has no key while UndeadConfig.VICTOR_ENABLED is false.
				if (pressed) {
					V_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), 0L, 0L });
				} else {
					long[] held = V_HELD.remove(player.getUUID());
					if (held != null && held[1] == 0L) {
						Undead.reform(player); // a tap
					} else if (held != null && held[2] == 0L) {
						UndeadBoneSword.cancelStruggle(player); // let go before it came off
					}
				}
			}
			case SLOT_6 -> {
				// v0.17.3 (user): hold C -- Deadline (8 s at most; let go: its cooldown). Shift+C is the Division Bullet's (its
				// own rework wires it here); the old Dead Quardead is gone from the key.
				if (!pressed) {
					UndeadBloodMoves.stopDeadline(player);
				} else if (shift) {
					// Shift+C: Division Bullet
				} else if (victor) {
					UndeadAbilities.deadRoad(player);
				} else {
					UndeadBloodMoves.startDeadline(player);
				}
			}
			default -> {
			}
		}
	}

	/** From {@link Undead#tick}: a plain R held past {@link UndeadConfig#PUNCH_HOLD_TICKS} draws the arm back for the punch. */
	static void tickHold(ServerPlayer player) {
		UndeadEject.tick(player);
		UndeadBloodMoves.tick(player);
		tickX(player);
		tickV(player);
		tickShiftV(player);
		UndeadRegenMenu.tick(player);
		UndeadLaunch.tick(player);
		Long down = R_HELD.get(player.getUUID());
		var s = Undead.state(player);
		if (down == null) {
			if (s.flag(com.projecthero.mod.undead.data.UndeadState.FLAG_PUNCH)) {
				UndeadAbilities.cancelPunch(player); // the release went missing (a screen opened)
			}
			return;
		}
		if (!s.flag(com.projecthero.mod.undead.data.UndeadState.FLAG_PUNCH) && player.level().getGameTime() - down == UndeadConfig.PUNCH_HOLD_TICKS) {
			UndeadAbilities.startPunch(player);
		}
		if (s.flag(com.projecthero.mod.undead.data.UndeadState.FLAG_PUNCH) && Undead.shootingArm(player) < 0) {
			UndeadAbilities.cancelPunch(player);
		}
	}

	/**
	 * V held: past {@link UndeadConfig#BONE_SWORD_TAP} he reaches over and starts struggling with the off arm (or, if he can't
	 * draw the sword, says why once); at {@link UndeadConfig#BONE_SWORD_HOLD} it tears off as the Bone Sword.
	 */
	private static void tickV(ServerPlayer player) {
		long[] v = V_HELD.get(player.getUUID());
		if (v == null || v[2] != 0L) {
			return;
		}
		long held = player.level().getGameTime() - v[0];
		if (v[1] == 0L) {
			if (held >= UndeadConfig.BONE_SWORD_TAP) {
				v[1] = 1L;
				if (!UndeadBoneSword.startStruggle(player)) {
					v[2] = 1L; // it could not (and said why): the rest of the hold does nothing
				}
			}
			return;
		}
		if (!Undead.state(player).boneSword() && Undead.state(player).animId != com.projecthero.mod.undead.data.UndeadState.ANIM_SWORD_STRUGGLE) {
			v[2] = 1L; // the struggle was broken off (a part lost, a re-form)
			return;
		}
		if (held >= UndeadConfig.BONE_SWORD_HOLD) {
			v[2] = 1L;
			UndeadBoneSword.draw(player);
		} else {
			UndeadBoneSword.struggling(player, held);
		}
	}

	/** Shift+V held {@link #MENU_HOLD}: the Regen Menu opens (or closes); the release then does nothing. */
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

	/** Gametests: V has been down for {@code ticks} (the press moved back in time), and the hold ticks. */
	public static void forTestsHoldV(ServerPlayer player, int ticks) {
		long[] v = V_HELD.get(player.getUUID());
		if (v != null) {
			v[0] = player.level().getGameTime() - ticks;
		}
		tickV(player);
	}

	/**
	 * X held: at {@link #HEAD_THROW_HOLD} the Head Throw starts ({@link UndeadHeadThrow#start}); every tick after that it is
	 * still held, {@link UndeadHeadThrow#tickHeld}. Shattered mid-hold: {@link UndeadHeadThrow#cancel}.
	 */
	private static void tickX(ServerPlayer player) {
		long[] x = X_HELD.get(player.getUUID());
		if (x == null) {
			return;
		}
		com.projecthero.mod.undead.data.UndeadState s = Undead.state(player);
		if (!player.isAlive() || s.reformUntil > player.level().getGameTime()) {
			X_HELD.remove(player.getUUID());
			if (x[1] != 0L) {
				UndeadHeadThrow.cancel(player);
			}
			return;
		}
		if (x[1] != 0L) {
			UndeadHeadThrow.tickHeld(player);
		} else if (player.level().getGameTime() - x[0] >= HEAD_THROW_HOLD) {
			x[1] = 1L;
			UndeadHeadThrow.start(player);
		}
	}

	/** Gametests: X has been down for {@code ticks} (the press moved back in time), and the hold ticks. */
	public static void forTestsHoldX(ServerPlayer player, int ticks) {
		long[] x = X_HELD.get(player.getUUID());
		if (x != null) {
			x[0] = player.level().getGameTime() - ticks;
		}
		tickX(player);
	}

	/** An X / G / C hold from before is cut short (the Regen Menu opened, the Bone Sword took the keys). */
	private static void dropHeld(ServerPlayer player) {
		long[] x = X_HELD.remove(player.getUUID());
		if (x != null && x[1] != 0L) {
			UndeadHeadThrow.cancel(player);
		}
		UndeadBloodMoves.stopAll(player);
	}

	/** v0.17.3: double-tap jump in the air (UndeadFlightPayload): take off, or drop out of the flight. */
	public static void doubleJump(ServerPlayer player) {
		if (!hasContext(player) || UndeadRegenMenu.open(player)) {
			return;
		}
		if (!UndeadFlight.isFlying(player) && com.projecthero.mod.hero.PowerSuppression.refuse(player)) {
			return;
		}
		UndeadFlight.toggle(player);
	}

	/** The move ids on a slot (plain, Shift), for the HUD; Victor's kit while he is Victor. */
	public static String[] idsOf(Player player, AbilitySlot slot) {
		if (UndeadRegenMenu.open(player)) {
			String[] menu = UndeadRegenMenu.idsOf(slot);
			return menu != null ? menu : new String[0]; // v0.17.3: the Regen Menu's buttons while it is open
		}
		String[] sword = UndeadBoneSword.idsOf(player, slot);
		if (sword != null) {
			return sword; // v0.17.3: the Bone Sword kit while it is out
		}
		boolean victor = Undead.victor(player);
		return switch (slot) {
			case SLOT_1 -> victor ? new String[] { UndeadAbilities.VULCAN, UndeadAbilities.DIVISION } : new String[] { UndeadAbilities.PARTS_BULLET, UndeadAbilities.CRIMSON_BULLET };
			case SLOT_2 -> new String[] { victor ? UndeadAbilities.DEAD_BLADE : UndeadBloodMoves.SPRAY, UndeadBloodMoves.WHIRL };
			case SLOT_3 -> new String[] { UndeadLaunch.EJECT_FEET, UndeadLaunch.COMBUSTION };
			case SLOT_4 -> new String[] { UndeadAbilities.VORTEX, UndeadAbilities.HEAD_BULLET };
			// v0.17.3: tap V Reform, Shift+V Toggle Regen (hold V, the Bone Sword, is the third: boneSwordHoldId)
			case SLOT_5 -> new String[] { UndeadAbilities.REFORM, UndeadConfig.VICTOR_ENABLED ? UndeadAbilities.CARD : UndeadAbilities.REGEN };
			case SLOT_6 -> new String[] { victor ? UndeadAbilities.DEAD_ROAD : UndeadBloodMoves.DEADLINE, UndeadAbilities.DIVISION }; // Shift+C: Division Bullet
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		int sword = UndeadBoneSword.maxCooldown(id);
		if (sword >= 0) {
			return sword;
		}
		return switch (id) {
			case UndeadAbilities.PARTS_BULLET -> UndeadConfig.PARTS_BULLET_COOLDOWN;
			case UndeadAbilities.PUNCH -> UndeadConfig.PUNCH_COOLDOWN;
			case UndeadAbilities.CRIMSON_BULLET -> UndeadConfig.CRIMSON_COOLDOWN;
			case UndeadAbilities.CRESCENT -> UndeadConfig.CRESCENT_COOLDOWN;
			case UndeadAbilities.GREAT_CRESCENT -> UndeadConfig.GREAT_CRESCENT_COOLDOWN;
			case UndeadBloodMoves.SPRAY -> UndeadEjectConfig.SPRAY_COOLDOWN;
			case UndeadBloodMoves.WHIRL -> UndeadEjectConfig.WHIRL_COOLDOWN;
			case UndeadBloodMoves.DEADLINE -> UndeadEjectConfig.DEADLINE_COOLDOWN;
			case UndeadLaunch.EJECT_FEET -> UndeadEjectConfig.EJECT_FEET_COOLDOWN;
			case UndeadLaunch.COMBUSTION -> UndeadEjectConfig.COMBUSTION_COOLDOWN;
			case UndeadLaunch.HEAD_LAUNCH -> UndeadConfig.HEAD_LAUNCH_COOLDOWN;
			case UndeadAbilities.VORTEX -> UndeadConfig.VORTEX_COOLDOWN;
			case UndeadAbilities.HEAD_BULLET -> UndeadConfig.HEAD_BULLET_COOLDOWN;
			case UndeadAbilities.CARD -> UndeadConfig.VICTOR_COOLDOWN;
			case UndeadAbilities.REFORM -> UndeadConfig.REFORM_COOLDOWN;
			case UndeadAbilities.DEAD_LINE -> UndeadConfig.DEAD_LINE_COOLDOWN;
			case UndeadAbilities.QUARDEAD -> UndeadConfig.QUARDEAD_COOLDOWN;
			case UndeadAbilities.VULCAN -> UndeadConfig.VULCAN_COOLDOWN;
			case UndeadAbilities.DIVISION -> UndeadConfig.DIVISION_COOLDOWN;
			case UndeadAbilities.DEAD_BLADE -> UndeadConfig.DEAD_BLADE_COOLDOWN;
			case UndeadAbilities.DEAD_ROAD -> UndeadConfig.DEAD_ROAD_COOLDOWN;
			default -> 0;
		};
	}
}

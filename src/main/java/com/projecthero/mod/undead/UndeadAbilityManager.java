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
 *   R (1)  tap: Parts Bullet: Finger Shift+R  Parts Bullet: Hand      (Victor: Dead Vulcan / Division Bullet)
 *          hold 1 s: Parts Bullet: Arm (the arm draws back from ARM_WINDUP_SHOW and flies at ARM_HOLD, still held)
 *   G (2)  hold: Blood Spray         Shift+G  Blood Whirlwind          (Victor: Dead Blade)
 *   X (3)  tap: Eject Feet           Shift+X  Combustion Punch
 *          hold 2 s: Head Throw (UndeadHeadThrow: start at 2 s, tickHeld while held, release on let-go, cancel if cut short)
 *   Z (4)  Ten Twists: Vortex Bullet Shift+Z  Parts Bullet Head
 *   V (5)  tap: Reform               Shift+V  (free: the Regen Menu and Toggle Regen were removed in v0.17.3)
 *          hold 2 s: Bone Sword (UndeadBoneSword: he struggles the arm off; the keys turn into the sword kit while it is out)
 *   (Victor -- Pull the Card and the kit in brackets -- is off: UndeadConfig.VICTOR_ENABLED)
 *   C (6)  hold: Deadline            Shift+C  Division Bullet          (Victor: Dead Road)
 *   double-tap jump in the air: the flight (UndeadFlight.toggle, from UndeadFlightPayload: doubleJump)
 * </pre>
 * The ejection moves (flight, G, Shift+G, X, Shift+X, C) all blow ends off through {@link UndeadEject}.
 */
public final class UndeadAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();
	/**
	 * A plain R held down (not Victor): {when it went down, 1 once the arm has flown, 1 once the wind-up was tried}. Let go
	 * before the arm flew = a tap: Parts Bullet: Finger; held {@link UndeadBulletConfig#ARM_HOLD} = Parts Bullet: Arm.
	 */
	private static final Map<UUID, long[]> R_HELD = new ConcurrentHashMap<>();
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
	/** v0.17.3: a plain X held this long is the Head Throw (2 s). */
	public static final int HEAD_THROW_HOLD = 40;

	private UndeadAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
		R_HELD.clear();
		X_HELD.clear();
		V_HELD.clear();
		UndeadLaunch.clearSessionState();
		UndeadEject.clearSessionState();
		UndeadBloodMoves.clearSessionState();
		UndeadHeadThrow.clearSessionState(); // v0.17.3
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
		R_HELD.remove(id);
		X_HELD.remove(id);
		V_HELD.remove(id);
		UndeadLaunch.forget(id);
		UndeadEject.forget(id);
		UndeadBloodMoves.forget(id);
		UndeadHeadThrow.forget(id); // v0.17.3
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
		// v0.17.3 (user: "remove the regen menu from this power please"): Shift+V no longer toggles auto-regrowth or opens the
		// Regen Menu -- regrowth is always automatic. A Shift+V press does nothing here (and starts no V hold).
		if (slot == AbilitySlot.SLOT_5 && pressed && shift) {
			V_HELD.remove(player.getUUID());
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
				// v0.17.3 (user): tap R Parts Bullet: Finger (on release), Shift+R Parts Bullet: Hand (on press), hold R a second
				// Parts Bullet: Arm (fired by tickHold at ARM_HOLD while still held). No cooldowns -- one shot per press, never a
				// stream from a held key (the release after the arm flew does nothing).
				if (!pressed) {
					long[] held = R_HELD.remove(player.getUUID());
					if (held != null && held[1] == 0L) {
						UndeadAbilities.cancelPunch(player); // let go before the arm flew: the wind-up drops ...
						UndeadAbilities.partsBullet(player); // ... and it is a tap
					}
				} else if (victor) {
					if (shift) {
						UndeadAbilities.division(player);
					} else {
						UndeadAbilities.vulcan(player);
					}
				} else if (shift) {
					UndeadAbilities.handBullet(player);
				} else {
					R_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), 0L, 0L });
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
					UndeadAbilities.division(player); // v0.17.3: Division Bullet (replaced Dead Quardead)
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

	/**
	 * From {@link Undead#tick}: a plain R held past {@link UndeadBulletConfig#ARM_WINDUP_SHOW} draws the arm back, and at
	 * {@link UndeadBulletConfig#ARM_HOLD} (one second) the arm flies -- Parts Bullet: Arm, on the spot, still held.
	 */
	static void tickHold(ServerPlayer player) {
		UndeadEject.tick(player);
		UndeadBloodMoves.tick(player);
		tickX(player);
		tickV(player);
		UndeadLaunch.tick(player);
		UndeadHeadThrow.tick(player); // v0.17.3: the Head Throw's aim and flight
		tickR(player);
	}

	/** R held: the arm's wind-up from {@link UndeadBulletConfig#ARM_WINDUP_SHOW}, the throw at {@link UndeadBulletConfig#ARM_HOLD}. */
	private static void tickR(ServerPlayer player) {
		long[] r = R_HELD.get(player.getUUID());
		var s = Undead.state(player);
		boolean winding = s.flag(com.projecthero.mod.undead.data.UndeadState.FLAG_PUNCH);
		if (r == null || r[1] != 0L) {
			if (winding) {
				UndeadAbilities.cancelPunch(player); // the release went missing (a screen opened)
			}
			return;
		}
		long held = player.level().getGameTime() - r[0];
		if (r[2] == 0L && held >= UndeadBulletConfig.ARM_WINDUP_SHOW) {
			r[2] = 1L;
			winding = UndeadAbilities.startPunch(player);
		}
		if (winding && Undead.shootingArm(player) < 0) {
			UndeadAbilities.cancelPunch(player); // the arm was lost meanwhile
			winding = false;
		}
		if (held >= UndeadBulletConfig.ARM_HOLD) {
			r[1] = 1L; // spoken for: the release does nothing now
			if (winding) {
				UndeadAbilities.throwPunch(player);
			}
		}
	}

	/** Gametests: R has been down for {@code ticks} (the press moved back in time), and the hold ticks. */
	public static void forTestsHoldR(ServerPlayer player, int ticks) {
		long[] r = R_HELD.get(player.getUUID());
		if (r != null) {
			r[0] = player.level().getGameTime() - ticks;
		}
		tickR(player);
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
		} else {
			UndeadHeadThrow.windUp(player, (int) (player.level().getGameTime() - x[0])); // the 2 s struggle (shows past a tap)
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
		if (!hasContext(player)) {
			return;
		}
		if (!UndeadFlight.isFlying(player) && com.projecthero.mod.hero.PowerSuppression.refuse(player)) {
			return;
		}
		UndeadFlight.toggle(player);
	}

	/** The move ids on a slot (plain, Shift), for the HUD; Victor's kit while he is Victor. */
	public static String[] idsOf(Player player, AbilitySlot slot) {
		String[] sword = UndeadBoneSword.idsOf(player, slot);
		if (sword != null) {
			return sword; // v0.17.3: the Bone Sword kit while it is out
		}
		boolean victor = Undead.victor(player);
		return switch (slot) {
			case SLOT_1 -> victor ? new String[] { UndeadAbilities.VULCAN, UndeadAbilities.DIVISION } : new String[] { UndeadAbilities.PARTS_BULLET, UndeadAbilities.PARTS_BULLET_HAND };
			case SLOT_2 -> new String[] { victor ? UndeadAbilities.DEAD_BLADE : UndeadBloodMoves.SPRAY, UndeadBloodMoves.WHIRL };
			case SLOT_3 -> new String[] { UndeadLaunch.EJECT_FEET, UndeadLaunch.COMBUSTION };
			case SLOT_4 -> new String[] { UndeadAbilities.VORTEX, UndeadAbilities.HEAD_BULLET };
			// v0.17.3: tap V Reform (hold V, the Bone Sword, is the third: boneSwordHoldId); Shift+V is free since Toggle Regen went
			case SLOT_5 -> UndeadConfig.VICTOR_ENABLED ? new String[] { UndeadAbilities.REFORM, UndeadAbilities.CARD } : new String[] { UndeadAbilities.REFORM };
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
			case UndeadAbilities.PARTS_BULLET, UndeadAbilities.PARTS_BULLET_HAND, UndeadAbilities.PUNCH -> 0; // v0.17.3: no cooldowns
			case UndeadAbilities.HEAD_THROW -> UndeadBulletConfig.HEAD_THROW_COOLDOWN;
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
			case UndeadAbilities.VULCAN -> UndeadConfig.VULCAN_COOLDOWN;
			case UndeadAbilities.DIVISION -> UndeadBulletConfig.DIVISION_COOLDOWN;
			case UndeadAbilities.DEAD_BLADE -> UndeadConfig.DEAD_BLADE_COOLDOWN;
			case UndeadAbilities.DEAD_ROAD -> UndeadConfig.DEAD_ROAD_COOLDOWN;
			default -> 0;
		};
	}
}

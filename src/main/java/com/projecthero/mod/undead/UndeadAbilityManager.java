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
 *   G (2)  Crimson Crescent Moon     Shift+G  Great Deep-Crimson Crescent Moon   (Victor: Dead Blade)
 *   X (3)  tap: Leg Launch           Shift + hold X  Blood Propulsion (flight)
 *          hold 1 s: Head Launch
 *   Z (4)  Ten Twists: Vortex Bullet Shift+Z  Parts Bullet Head
 *   V (5)  tap: Reform               Shift+V  (free: the Regen Menu and Toggle Regen were removed in v0.17.3)
 *          hold 2 s: Bone Sword (UndeadBoneSword: he struggles the arm off; the keys turn into the sword kit while it is out)
 *   (Victor -- Pull the Card and the kit in brackets -- is off: UndeadConfig.VICTOR_ENABLED)
 *   C (6)  Dead Line (hold, a beam)  Shift+C  Dead Quardead           (Victor: Dead Road)
 * </pre>
 */
public final class UndeadAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();
	/** A plain R held down (not Victor): when it went down. Tap = Parts Bullet on release; hold = the stretch punch. */
	private static final Map<UUID, Long> R_HELD = new ConcurrentHashMap<>();
	/**
	 * X held down: {when it went down, 1 if Shift was down then (flight), 1 once the hold has fired (flight started / Head
	 * Launch), 1 once the Head Launch wind-up shows}.
	 */
	private static final Map<UUID, long[]> X_HELD = new ConcurrentHashMap<>();
	/**
	 * v0.17.3: a plain V held down: {when it went down, 1 once the hold is spoken for (the struggle started, or it could not and
	 * said why), 1 once the sword is drawn}. A release before {@link UndeadConfig#BONE_SWORD_TAP} is a tap: Reform.
	 */
	private static final Map<UUID, long[]> V_HELD = new ConcurrentHashMap<>();

	private UndeadAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
		R_HELD.clear();
		X_HELD.clear();
		V_HELD.clear();
		UndeadLaunch.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
		R_HELD.remove(id);
		X_HELD.remove(id);
		V_HELD.remove(id);
		UndeadLaunch.forget(id);
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
			if (X_HELD.remove(player.getUUID()) != null) {
				UndeadAbilities.stopPropulsion(player);
			}
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
				if (pressed) {
					if (shift) {
						UndeadAbilities.greatCrescent(player);
					} else if (victor) {
						UndeadAbilities.deadBlade(player);
					} else {
						UndeadAbilities.crescent(player);
					}
				}
			}
			case SLOT_3 -> {
				// tap: Leg Launch (on release); hold HEAD_LAUNCH_HOLD: Head Launch (tickHold, while still held);
				// Shift + hold: the flight, from FLIGHT_HOLD_TICKS until release. Which one is decided by Shift when X goes down.
				if (pressed) {
					X_HELD.put(player.getUUID(), new long[] { player.level().getGameTime(), shift ? 1L : 0L, 0L, 0L });
				} else {
					long[] held = X_HELD.remove(player.getUUID());
					UndeadAbilities.stopPropulsion(player); // FLIGHT HOOK (stop)
					if (held != null && held[1] == 0L && held[2] == 0L) {
						UndeadLaunch.legLaunch(player);
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
				if (!pressed) {
					UndeadAbilities.stopDeadLine(player);
				} else if (shift) {
					UndeadAbilities.quardead(player);
				} else if (victor) {
					UndeadAbilities.deadRoad(player);
				} else {
					UndeadAbilities.startDeadLine(player);
				}
			}
			default -> {
			}
		}
	}

	/** From {@link Undead#tick}: a plain R held past {@link UndeadConfig#PUNCH_HOLD_TICKS} draws the arm back for the punch. */
	static void tickHold(ServerPlayer player) {
		tickX(player);
		tickV(player);
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

	/** Gametests: V has been down for {@code ticks} (the press moved back in time), and the hold ticks. */
	public static void forTestsHoldV(ServerPlayer player, int ticks) {
		long[] v = V_HELD.get(player.getUUID());
		if (v != null) {
			v[0] = player.level().getGameTime() - ticks;
		}
		tickV(player);
	}

	/** X held: Shift + hold starts the flight; a plain hold winds up and fires the Head Launch, still held. */
	private static void tickX(ServerPlayer player) {
		long[] x = X_HELD.get(player.getUUID());
		if (x == null || x[2] != 0L) {
			return;
		}
		long held = player.level().getGameTime() - x[0];
		if (x[1] != 0L) {
			if (held >= UndeadConfig.FLIGHT_HOLD_TICKS) {
				x[2] = 1L;
				UndeadAbilities.startPropulsion(player); // FLIGHT HOOK (start)
			}
		} else if (held >= UndeadConfig.HEAD_LAUNCH_HOLD) {
			x[2] = 1L;
			UndeadLaunch.headLaunch(player);
		} else if (held >= UndeadConfig.LAUNCH_CHARGE_SHOW) {
			if (x[3] == 0L) {
				x[3] = 1L;
				UndeadLaunch.charge(player);
			}
			UndeadLaunch.charging(player, held);
		}
	}

	/** The move ids on a slot (plain, Shift), for the HUD; Victor's kit while he is Victor. */
	public static String[] idsOf(Player player, AbilitySlot slot) {
		String[] sword = UndeadBoneSword.idsOf(player, slot);
		if (sword != null) {
			return sword; // v0.17.3: the Bone Sword kit while it is out
		}
		boolean victor = Undead.victor(player);
		return switch (slot) {
			case SLOT_1 -> victor ? new String[] { UndeadAbilities.VULCAN, UndeadAbilities.DIVISION } : new String[] { UndeadAbilities.PARTS_BULLET, UndeadAbilities.CRIMSON_BULLET };
			case SLOT_2 -> new String[] { victor ? UndeadAbilities.DEAD_BLADE : UndeadAbilities.CRESCENT, UndeadAbilities.GREAT_CRESCENT };
			case SLOT_3 -> new String[] { UndeadLaunch.LEG_LAUNCH, UndeadAbilities.PROPULSION };
			case SLOT_4 -> new String[] { UndeadAbilities.VORTEX, UndeadAbilities.HEAD_BULLET };
			// v0.17.3: tap V Reform (hold V, the Bone Sword, is the third: boneSwordHoldId); Shift+V is free since Toggle Regen went
			case SLOT_5 -> UndeadConfig.VICTOR_ENABLED ? new String[] { UndeadAbilities.REFORM, UndeadAbilities.CARD } : new String[] { UndeadAbilities.REFORM };
			case SLOT_6 -> new String[] { victor ? UndeadAbilities.DEAD_ROAD : UndeadAbilities.DEAD_LINE, UndeadAbilities.QUARDEAD };
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
			case UndeadAbilities.PROPULSION -> UndeadConfig.PROPULSION_COOLDOWN;
			case UndeadLaunch.LEG_LAUNCH -> UndeadConfig.LEG_LAUNCH_COOLDOWN;
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

package com.projecthero.mod.andy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Bridges the universal ability slots to Andy (v0.17.3). {@link com.projecthero.mod.hero.AbilityRouter} hands him the slots
 * when {@link #hasContext} is true. Shift is read server-side. H stays the power selector; N is not his.
 *
 * <pre>
 *   R (1)  tap: Parts Bullet         Shift+R  Crimson Bullet          (Victor: Dead Vulcan / Division Bullet)
 *          hold: Stretch Punch (the arm draws back past PUNCH_HOLD_TICKS, thrown on release)
 *   G (2)  Crimson Crescent Moon     Shift+G  Great Deep-Crimson Crescent Moon   (Victor: Dead Blade)
 *   X (3)  tap: Leg Launch           Shift + hold X  Blood Propulsion (flight)
 *          hold 1 s: Head Launch
 *   Z (4)  Ten Twists: Vortex Bullet Shift+Z  Parts Bullet Head
 *   V (5)  Regeneration speed        Shift+V  Bone Sword (AndyBoneSword: the keys turn into the sword kit while it is out)
 *   (Victor -- Pull the Card and the kit in brackets -- is off: AndyConfig.VICTOR_ENABLED)
 *   C (6)  Dead Line (hold, a beam)  Shift+C  Dead Quardead           (Victor: Dead Road)
 * </pre>
 */
public final class AndyAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();
	/** A plain R held down (not Victor): when it went down. Tap = Parts Bullet on release; hold = the stretch punch. */
	private static final Map<UUID, Long> R_HELD = new ConcurrentHashMap<>();
	/**
	 * X held down: {when it went down, 1 if Shift was down then (flight), 1 once the hold has fired (flight started / Head
	 * Launch), 1 once the Head Launch wind-up shows}.
	 */
	private static final Map<UUID, long[]> X_HELD = new ConcurrentHashMap<>();

	private AndyAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
		R_HELD.clear();
		X_HELD.clear();
		AndyLaunch.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
		R_HELD.remove(id);
		X_HELD.remove(id);
		AndyLaunch.forget(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Andy.hasPower(player)) {
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
		boolean victor = Andy.victor(player);
		if (Andy.state(player).boneSword() && AndyBoneSword.handle(player, slot, pressed, shift)) {
			// v0.17.3: the Bone Sword's own kit while it is out; nothing half-held from before carries over
			R_HELD.remove(player.getUUID());
			if (X_HELD.remove(player.getUUID()) != null) {
				AndyAbilities.stopPropulsion(player);
			}
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (!pressed) {
					Long down = R_HELD.remove(player.getUUID());
					if (down != null) {
						long held = player.level().getGameTime() - down;
						if (Andy.state(player).flag(com.projecthero.mod.andy.data.AndyState.FLAG_PUNCH)) {
							if (held >= AndyConfig.PUNCH_MIN_HOLD) {
								AndyAbilities.throwPunch(player);
							} else {
								AndyAbilities.cancelPunch(player);
							}
						} else {
							AndyAbilities.partsBullet(player); // a tap
						}
					}
				} else if (victor) {
					if (shift) {
						AndyAbilities.division(player);
					} else {
						AndyAbilities.vulcan(player);
					}
				} else if (shift) {
					AndyAbilities.crimsonBullet(player);
				} else {
					R_HELD.put(player.getUUID(), player.level().getGameTime());
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						AndyAbilities.greatCrescent(player);
					} else if (victor) {
						AndyAbilities.deadBlade(player);
					} else {
						AndyAbilities.crescent(player);
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
					AndyAbilities.stopPropulsion(player); // FLIGHT HOOK (stop)
					if (held != null && held[1] == 0L && held[2] == 0L) {
						AndyLaunch.legLaunch(player);
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						AndyAbilities.headBullet(player);
					} else {
						AndyAbilities.vortexBullet(player);
					}
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						// v0.17.3 (user: "remove the Victor ability for now, add an ability for shift+V where the player rips an arm
						// off and uses it as a bone sword"): Shift+V is the Bone Sword; Pull the Card (AndyAbilities.pullCard) has
						// no key while AndyConfig.VICTOR_ENABLED is false
						AndyBoneSword.toggle(player);
					} else if (Andy.canAct(player)) {
						Andy.cycleSpeed(player);
					}
				}
			}
			case SLOT_6 -> {
				if (!pressed) {
					AndyAbilities.stopDeadLine(player);
				} else if (shift) {
					AndyAbilities.quardead(player);
				} else if (victor) {
					AndyAbilities.deadRoad(player);
				} else {
					AndyAbilities.startDeadLine(player);
				}
			}
			default -> {
			}
		}
	}

	/** From {@link Andy#tick}: a plain R held past {@link AndyConfig#PUNCH_HOLD_TICKS} draws the arm back for the punch. */
	static void tickHold(ServerPlayer player) {
		tickX(player);
		AndyLaunch.tick(player);
		Long down = R_HELD.get(player.getUUID());
		var s = Andy.state(player);
		if (down == null) {
			if (s.flag(com.projecthero.mod.andy.data.AndyState.FLAG_PUNCH)) {
				AndyAbilities.cancelPunch(player); // the release went missing (a screen opened)
			}
			return;
		}
		if (!s.flag(com.projecthero.mod.andy.data.AndyState.FLAG_PUNCH) && player.level().getGameTime() - down == AndyConfig.PUNCH_HOLD_TICKS) {
			AndyAbilities.startPunch(player);
		}
		if (s.flag(com.projecthero.mod.andy.data.AndyState.FLAG_PUNCH) && Andy.shootingArm(player) < 0) {
			AndyAbilities.cancelPunch(player);
		}
	}

	/** X held: Shift + hold starts the flight; a plain hold winds up and fires the Head Launch, still held. */
	private static void tickX(ServerPlayer player) {
		long[] x = X_HELD.get(player.getUUID());
		if (x == null || x[2] != 0L) {
			return;
		}
		long held = player.level().getGameTime() - x[0];
		if (x[1] != 0L) {
			if (held >= AndyConfig.FLIGHT_HOLD_TICKS) {
				x[2] = 1L;
				AndyAbilities.startPropulsion(player); // FLIGHT HOOK (start)
			}
		} else if (held >= AndyConfig.HEAD_LAUNCH_HOLD) {
			x[2] = 1L;
			AndyLaunch.headLaunch(player);
		} else if (held >= AndyConfig.LAUNCH_CHARGE_SHOW) {
			if (x[3] == 0L) {
				x[3] = 1L;
				AndyLaunch.charge(player);
			}
			AndyLaunch.charging(player, held);
		}
	}

	/** The move ids on a slot (plain, Shift), for the HUD; Victor's kit while he is Victor. */
	public static String[] idsOf(Player player, AbilitySlot slot) {
		String[] sword = AndyBoneSword.idsOf(player, slot);
		if (sword != null) {
			return sword; // v0.17.3: the Bone Sword kit while it is out
		}
		boolean victor = Andy.victor(player);
		return switch (slot) {
			case SLOT_1 -> victor ? new String[] { AndyAbilities.VULCAN, AndyAbilities.DIVISION } : new String[] { AndyAbilities.PARTS_BULLET, AndyAbilities.CRIMSON_BULLET };
			case SLOT_2 -> new String[] { victor ? AndyAbilities.DEAD_BLADE : AndyAbilities.CRESCENT, AndyAbilities.GREAT_CRESCENT };
			case SLOT_3 -> new String[] { AndyLaunch.LEG_LAUNCH, AndyAbilities.PROPULSION };
			case SLOT_4 -> new String[] { AndyAbilities.VORTEX, AndyAbilities.HEAD_BULLET };
			case SLOT_5 -> new String[] { AndyAbilities.REGEN, AndyConfig.VICTOR_ENABLED ? AndyAbilities.CARD : AndyBoneSword.BONE_SWORD };
			case SLOT_6 -> new String[] { victor ? AndyAbilities.DEAD_ROAD : AndyAbilities.DEAD_LINE, AndyAbilities.QUARDEAD };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		int sword = AndyBoneSword.maxCooldown(id);
		if (sword >= 0) {
			return sword;
		}
		return switch (id) {
			case AndyAbilities.PARTS_BULLET -> AndyConfig.PARTS_BULLET_COOLDOWN;
			case AndyAbilities.PUNCH -> AndyConfig.PUNCH_COOLDOWN;
			case AndyAbilities.CRIMSON_BULLET -> AndyConfig.CRIMSON_COOLDOWN;
			case AndyAbilities.CRESCENT -> AndyConfig.CRESCENT_COOLDOWN;
			case AndyAbilities.GREAT_CRESCENT -> AndyConfig.GREAT_CRESCENT_COOLDOWN;
			case AndyAbilities.PROPULSION -> AndyConfig.PROPULSION_COOLDOWN;
			case AndyLaunch.LEG_LAUNCH -> AndyConfig.LEG_LAUNCH_COOLDOWN;
			case AndyLaunch.HEAD_LAUNCH -> AndyConfig.HEAD_LAUNCH_COOLDOWN;
			case AndyAbilities.VORTEX -> AndyConfig.VORTEX_COOLDOWN;
			case AndyAbilities.HEAD_BULLET -> AndyConfig.HEAD_BULLET_COOLDOWN;
			case AndyAbilities.CARD -> AndyConfig.VICTOR_COOLDOWN;
			case AndyAbilities.DEAD_LINE -> AndyConfig.DEAD_LINE_COOLDOWN;
			case AndyAbilities.QUARDEAD -> AndyConfig.QUARDEAD_COOLDOWN;
			case AndyAbilities.VULCAN -> AndyConfig.VULCAN_COOLDOWN;
			case AndyAbilities.DIVISION -> AndyConfig.DIVISION_COOLDOWN;
			case AndyAbilities.DEAD_BLADE -> AndyConfig.DEAD_BLADE_COOLDOWN;
			case AndyAbilities.DEAD_ROAD -> AndyConfig.DEAD_ROAD_COOLDOWN;
			default -> 0;
		};
	}
}

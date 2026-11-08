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
 *   X (3)  Blood Propulsion (hold)   Shift+X  Repair Glide
 *   Z (4)  Ten Twists: Vortex Bullet Shift+Z  Parts Bullet Head
 *   V (5)  Regeneration speed        Shift+V  Pull the Card (Victor)
 *   C (6)  Dead Line (hold, a beam)  Shift+C  Dead Quardead           (Victor: Dead Road)
 * </pre>
 */
public final class AndyAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();
	/** A plain R held down (not Victor): when it went down. Tap = Parts Bullet on release; hold = the stretch punch. */
	private static final Map<UUID, Long> R_HELD = new ConcurrentHashMap<>();

	private AndyAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
		R_HELD.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
		R_HELD.remove(id);
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
				if (!pressed) {
					AndyAbilities.stopPropulsion(player);
				} else if (shift) {
					AndyAbilities.glide(player);
				} else {
					AndyAbilities.startPropulsion(player);
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
						AndyAbilities.pullCard(player);
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

	/** The move ids on a slot (plain, Shift), for the HUD; Victor's kit while he is Victor. */
	public static String[] idsOf(Player player, AbilitySlot slot) {
		boolean victor = Andy.victor(player);
		return switch (slot) {
			case SLOT_1 -> victor ? new String[] { AndyAbilities.VULCAN, AndyAbilities.DIVISION } : new String[] { AndyAbilities.PARTS_BULLET, AndyAbilities.CRIMSON_BULLET };
			case SLOT_2 -> new String[] { victor ? AndyAbilities.DEAD_BLADE : AndyAbilities.CRESCENT, AndyAbilities.GREAT_CRESCENT };
			case SLOT_3 -> new String[] { AndyAbilities.PROPULSION, AndyAbilities.GLIDE };
			case SLOT_4 -> new String[] { AndyAbilities.VORTEX, AndyAbilities.HEAD_BULLET };
			case SLOT_5 -> new String[] { AndyAbilities.REGEN, AndyAbilities.CARD };
			case SLOT_6 -> new String[] { victor ? AndyAbilities.DEAD_ROAD : AndyAbilities.DEAD_LINE, AndyAbilities.QUARDEAD };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case AndyAbilities.PARTS_BULLET -> AndyConfig.PARTS_BULLET_COOLDOWN;
			case AndyAbilities.PUNCH -> AndyConfig.PUNCH_COOLDOWN;
			case AndyAbilities.CRIMSON_BULLET -> AndyConfig.CRIMSON_COOLDOWN;
			case AndyAbilities.CRESCENT -> AndyConfig.CRESCENT_COOLDOWN;
			case AndyAbilities.GREAT_CRESCENT -> AndyConfig.GREAT_CRESCENT_COOLDOWN;
			case AndyAbilities.PROPULSION -> AndyConfig.PROPULSION_COOLDOWN;
			case AndyAbilities.GLIDE -> AndyConfig.GLIDE_COOLDOWN;
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

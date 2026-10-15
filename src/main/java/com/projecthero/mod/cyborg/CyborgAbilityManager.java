package com.projecthero.mod.cyborg;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to Cyborg (v0.16.5). {@link com.projecthero.mod.hero.AbilityRouter} hands him the
 * slots when {@link #hasContext} is true -- has the power and no mutation selected. Shift is read server-side.
 *
 * <pre>
 *   R (1)  Sonic Cannon (hold, release)   Shift+R  Sonic Burst
 *   G (2)  Hydraulic Punch                Shift+G  Grapple Claw
 *   X (3)  Thruster Dash                  Shift+X  Hover (toggle)
 *   Z (4)  Techno-Override                Shift+Z  Scan
 *   V (5)  Boom Tube                      Shift+V  Waypoint
 *   C (6)  Defense Matrix                 Shift+C  Self-Repair
 * </pre>
 * H is the form (its own payload) and N does nothing.
 */
public final class CyborgAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private CyborgAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Cyborg.hasPower(player)) {
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
		switch (slot) {
			case SLOT_1 -> {
				if (!pressed) {
					CyborgAbilities.fireCannon(player);
				} else if (shift) {
					CyborgAbilities.burst(player);
				} else {
					CyborgAbilities.startCharge(player);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						CyborgAbilities.grapple(player);
					} else {
						CyborgAbilities.punch(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						CyborgAbilities.toggleHover(player);
					} else {
						CyborgAbilities.dash(player);
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						CyborgAbilities.scan(player);
					} else {
						CyborgAbilities.override(player);
					}
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						CyborgAbilities.setWaypoint(player);
					} else {
						CyborgAbilities.boomTube(player);
					}
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						CyborgAbilities.repair(player);
					} else {
						CyborgAbilities.matrix(player);
					}
				}
			}
			default -> {
			}
		}
	}

	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { CyborgAbilities.CANNON, CyborgAbilities.BURST };
			case SLOT_2 -> new String[] { CyborgAbilities.PUNCH, CyborgAbilities.GRAPPLE };
			case SLOT_3 -> new String[] { CyborgAbilities.DASH, CyborgAbilities.HOVER };
			case SLOT_4 -> new String[] { CyborgAbilities.OVERRIDE, CyborgAbilities.SCAN };
			case SLOT_5 -> new String[] { CyborgAbilities.BOOM, CyborgAbilities.WAYPOINT };
			case SLOT_6 -> new String[] { CyborgAbilities.MATRIX, CyborgAbilities.REPAIR };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case CyborgAbilities.CANNON -> CyborgConfig.CANNON_COOLDOWN;
			case CyborgAbilities.BURST -> CyborgConfig.BURST_COOLDOWN;
			case CyborgAbilities.PUNCH -> CyborgConfig.PUNCH_COOLDOWN;
			case CyborgAbilities.GRAPPLE -> CyborgConfig.GRAPPLE_COOLDOWN;
			case CyborgAbilities.DASH -> CyborgConfig.DASH_COOLDOWN;
			case CyborgAbilities.OVERRIDE -> CyborgConfig.OVERRIDE_COOLDOWN;
			case CyborgAbilities.SCAN -> CyborgConfig.SCAN_COOLDOWN;
			case CyborgAbilities.BOOM -> CyborgConfig.BOOM_COOLDOWN;
			case CyborgAbilities.MATRIX -> CyborgConfig.MATRIX_COOLDOWN;
			case CyborgAbilities.REPAIR -> CyborgConfig.REPAIR_COOLDOWN;
			default -> 0;
		};
	}

	public static float cost(String id) {
		return switch (id) {
			case CyborgAbilities.CANNON -> CyborgConfig.CANNON_COST;
			case CyborgAbilities.BURST -> CyborgConfig.BURST_COST;
			case CyborgAbilities.HOVER -> CyborgConfig.HOVER_COST_PER_SECOND;
			case CyborgAbilities.OVERRIDE -> CyborgConfig.OVERRIDE_COST;
			default -> 0f;
		};
	}
}

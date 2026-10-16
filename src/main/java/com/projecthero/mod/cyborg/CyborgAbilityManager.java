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
 *   G (2)  Hydraulic Punch                Shift+G  Arm Saw
 *   X (3)  Jet Dash                       Shift+X  Grapple Claw
 *   Z (4)  Missile Barrage                Shift+Z  Scan
 *   V (5)  Circuit Link (tap / hold)      Shift+V  Boom Tube menu
 *   C (6)  Defense Matrix                 Shift+C  Self-Repair
 * </pre>
 * H is the hologram and double-tap jump is Jet Flight (their own payload); N does nothing.
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
		// v0.16.5 (user): the blaster (R) refires as fast as its 0.5 s cooldown allows -- it skips the 25-tick repeat guard
		// (holding it can't double-fire: a running charge / blast ignores another press)
		if (slot != AbilitySlot.SLOT_1 && !freshPress(player, slot, pressed)) {
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
						CyborgAbilities.saw(player);
					} else {
						CyborgAbilities.punch(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						CyborgAbilities.grapple(player);
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
						CyborgAbilities.missiles(player);
					}
				}
			}
			case SLOT_5 -> {
				if (!pressed) {
					CyborgCircuit.release(player);
				} else if (shift) {
					CyborgBoomTube.openMenu(player);
				} else {
					CyborgCircuit.press(player);
				}
			}
			case SLOT_6 -> {
				if (!pressed) {
					CyborgAbilities.lowerMatrix(player); // v0.16.5: the Matrix is held on C
				} else if (shift) {
					CyborgAbilities.repair(player);
				} else {
					CyborgAbilities.matrix(player);
				}
			}
			default -> {
			}
		}
	}

	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { CyborgAbilities.CANNON, CyborgAbilities.BURST };
			case SLOT_2 -> new String[] { CyborgAbilities.PUNCH, CyborgAbilities.SAW };
			case SLOT_3 -> new String[] { CyborgAbilities.DASH, CyborgAbilities.GRAPPLE };
			case SLOT_4 -> new String[] { CyborgAbilities.MISSILES, CyborgAbilities.SCAN };
			case SLOT_5 -> new String[] { CyborgAbilities.LINK, CyborgAbilities.BOOM };
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
			case CyborgAbilities.SAW -> CyborgConfig.SAW_COOLDOWN;
			case CyborgAbilities.MISSILES -> CyborgConfig.MISSILE_COOLDOWN;
			case CyborgAbilities.LINK -> CyborgConfig.LINK_COOLDOWN;
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
			case CyborgAbilities.PUNCH -> CyborgConfig.PUNCH_COST;
			case CyborgAbilities.SAW -> CyborgConfig.SAW_COST;
			case CyborgAbilities.GRAPPLE -> CyborgConfig.GRAPPLE_COST;
			case CyborgAbilities.MISSILES -> CyborgConfig.MISSILE_COST;
			case CyborgAbilities.LINK -> CyborgConfig.LINK_COST;
			case CyborgAbilities.BOOM -> CyborgConfig.BOOM_COST;
			default -> 0f;
		};
	}
}

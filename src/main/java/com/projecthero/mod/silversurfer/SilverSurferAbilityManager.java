package com.projecthero.mod.silversurfer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to the Silver Surfer (v0.17.1). {@link com.projecthero.mod.hero.AbilityRouter} hands
 * him the slots when {@link #hasContext} is true -- has the power and no mutation selected. Shift is read server-side.
 *
 * <pre>
 *   R (1)  Power Cosmic Blast (tap / hold)   Shift+R  Cosmic Barrage
 *   G (2)  Board Strike                      Shift+G  Transmutation
 *   X (3)  Cosmic Dash                       Shift+X  Light-Speed
 *   Z (4)  Cosmic Barrier (hold)             Shift+Z  Molecular Phase
 *   V (5)  Cosmic Awareness (toggle)         Shift+V  Healing Touch
 *   C (6)  Cosmic Nova                       Shift+C  Herald's Meteor Storm
 * </pre>
 * Double-tap jump is the board (its own payload); H and N do nothing.
 */
public final class SilverSurferAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private SilverSurferAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!SilverSurfer.hasPower(player)) {
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
		// R refires as fast as its cooldown allows (a running hold ignores another press)
		if (slot != AbilitySlot.SLOT_1 && !freshPress(player, slot, pressed)) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> {
				if (!pressed) {
					SilverSurferAbilities.releaseBlast(player);
				} else if (shift) {
					SilverSurferAbilities.barrage(player);
				} else {
					SilverSurferAbilities.pressBlast(player);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						SilverSurferAbilities.transmute(player);
					} else {
						SilverSurferAbilities.boardStrike(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						SilverSurferAbilities.lightSpeed(player);
					} else {
						SilverSurferAbilities.dash(player);
					}
				}
			}
			case SLOT_4 -> {
				if (!pressed) {
					SilverSurferAbilities.lowerBarrier(player); // the barrier is held on Z
				} else if (shift) {
					SilverSurferAbilities.phase(player);
				} else {
					SilverSurferAbilities.raiseBarrier(player);
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						SilverSurferAbilities.heal(player);
					} else {
						SilverSurferAbilities.toggleAwareness(player);
					}
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						SilverSurferAbilities.meteorStorm(player);
					} else {
						SilverSurferAbilities.nova(player);
					}
				}
			}
			default -> {
			}
		}
	}

	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { SilverSurferAbilities.BLAST, SilverSurferAbilities.BARRAGE };
			case SLOT_2 -> new String[] { SilverSurferAbilities.BOARD, SilverSurferAbilities.TRANSMUTE };
			case SLOT_3 -> new String[] { SilverSurferAbilities.DASH, SilverSurferAbilities.LIGHT_SPEED };
			case SLOT_4 -> new String[] { SilverSurferAbilities.BARRIER, SilverSurferAbilities.PHASE };
			case SLOT_5 -> new String[] { SilverSurferAbilities.AWARENESS, SilverSurferAbilities.HEAL };
			case SLOT_6 -> new String[] { SilverSurferAbilities.NOVA, SilverSurferAbilities.METEOR };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case SilverSurferAbilities.BLAST -> SilverSurferConfig.BEAM_COOLDOWN;
			case SilverSurferAbilities.BARRAGE -> SilverSurferConfig.BARRAGE_COOLDOWN;
			case SilverSurferAbilities.BOARD -> SilverSurferConfig.BOARD_COOLDOWN;
			case SilverSurferAbilities.TRANSMUTE -> SilverSurferConfig.TRANSMUTE_COOLDOWN;
			case SilverSurferAbilities.DASH -> SilverSurferConfig.DASH_COOLDOWN;
			case SilverSurferAbilities.LIGHT_SPEED -> SilverSurferConfig.LIGHT_SPEED_COOLDOWN;
			case SilverSurferAbilities.BARRIER -> SilverSurferConfig.BARRIER_BREAK_COOLDOWN;
			case SilverSurferAbilities.PHASE -> SilverSurferConfig.PHASE_COOLDOWN;
			case SilverSurferAbilities.AWARENESS -> SilverSurferConfig.AWARENESS_COOLDOWN;
			case SilverSurferAbilities.HEAL -> SilverSurferConfig.HEAL_COOLDOWN;
			case SilverSurferAbilities.NOVA -> SilverSurferConfig.NOVA_COOLDOWN;
			case SilverSurferAbilities.METEOR -> SilverSurferConfig.METEOR_COOLDOWN;
			default -> 0;
		};
	}
}

package com.projecthero.mod.ghostrider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to Ghost Rider (v0.16.5). {@link com.projecthero.mod.hero.AbilityRouter} hands him
 * the slots when {@link #hasContext} is true -- has the power and no mutation selected. Shift is read server-side off the
 * player's sneak state. The moves themselves check the form ({@link GhostRider#canAct}).
 *
 * <pre>
 *   R (1)  Hellfire Blast (hold)   Shift+R  Hellfire Wave
 *   G (2)  Chain Whip              Shift+G  Chain Spin
 *   X (3)  Hell Cycle / riding: Cycle Jump      Shift+X  send the bike back
 *   Z (4)  Judgement Mark          Shift+Z  Penance Stare
 *   V (5)  Hellfire Rush           Shift+V  Hellfire Eruption
 *   C (6)  Soul Shield             Shift+C  Rider's Wrath
 * </pre>
 * H is the change (its own payload) and N does nothing.
 */
public final class GhostRiderAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private GhostRiderAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!GhostRider.hasPower(player)) {
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
					GhostRiderAbilities.stopBlast(player);
				} else if (shift) {
					GhostRiderAbilities.wave(player);
				} else {
					GhostRiderAbilities.startBlast(player);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						GhostRiderAbilities.spin(player);
					} else {
						GhostRiderAbilities.whip(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					// (riding the bike Shift does not dismount -- PlayerMixin -- so Shift+X reaches here)
					if (shift) {
						GhostRiderAbilities.sendCycleBack(player);
					} else {
						GhostRiderAbilities.cycleKey(player);
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						GhostRiderAbilities.stare(player);
					} else {
						GhostRiderAbilities.mark(player);
					}
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						GhostRiderAbilities.eruption(player);
					} else {
						GhostRiderAbilities.rush(player);
					}
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						GhostRiderAbilities.wrath(player);
					} else {
						GhostRiderAbilities.shield(player);
					}
				}
			}
			default -> {
			}
		}
	}

	/** The two moves on one key: [plain, shift]. */
	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { GhostRiderAbilities.BLAST, GhostRiderAbilities.WAVE };
			case SLOT_2 -> new String[] { GhostRiderAbilities.WHIP, GhostRiderAbilities.SPIN };
			case SLOT_3 -> new String[] { GhostRiderAbilities.CYCLE, GhostRiderAbilities.CYCLE_JUMP };
			case SLOT_4 -> new String[] { GhostRiderAbilities.MARK, GhostRiderAbilities.STARE };
			case SLOT_5 -> new String[] { GhostRiderAbilities.RUSH, GhostRiderAbilities.ERUPTION };
			case SLOT_6 -> new String[] { GhostRiderAbilities.SHIELD, GhostRiderAbilities.WRATH };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case GhostRiderAbilities.BLAST -> GhostRiderConfig.BLAST_COOLDOWN;
			case GhostRiderAbilities.WAVE -> GhostRiderConfig.WAVE_COOLDOWN;
			case GhostRiderAbilities.WHIP -> GhostRiderConfig.WHIP_COOLDOWN;
			case GhostRiderAbilities.SPIN -> GhostRiderConfig.SPIN_COOLDOWN;
			case GhostRiderAbilities.CYCLE -> GhostRiderConfig.CYCLE_COOLDOWN;
			case GhostRiderAbilities.CYCLE_JUMP -> GhostRiderConfig.CYCLE_JUMP_COOLDOWN;
			case GhostRiderAbilities.MARK -> GhostRiderConfig.MARK_COOLDOWN;
			case GhostRiderAbilities.STARE -> GhostRiderConfig.STARE_COOLDOWN;
			case GhostRiderAbilities.RUSH -> GhostRiderConfig.RUSH_COOLDOWN;
			case GhostRiderAbilities.ERUPTION -> GhostRiderConfig.ERUPTION_COOLDOWN;
			case GhostRiderAbilities.SHIELD -> GhostRiderConfig.SHIELD_COOLDOWN;
			case GhostRiderAbilities.WRATH -> GhostRiderConfig.WRATH_COOLDOWN;
			default -> 0;
		};
	}

	/** Hellfire cost of a move (per bolt for the held Hellfire Blast). */
	public static float cost(String id) {
		return switch (id) {
			case GhostRiderAbilities.BLAST -> GhostRiderConfig.BLAST_COST;
			case GhostRiderAbilities.WAVE -> GhostRiderConfig.WAVE_COST;
			case GhostRiderAbilities.WHIP -> GhostRiderConfig.WHIP_COST;
			case GhostRiderAbilities.SPIN -> GhostRiderConfig.SPIN_COST;
			case GhostRiderAbilities.CYCLE_JUMP -> GhostRiderConfig.CYCLE_JUMP_COST;
			case GhostRiderAbilities.MARK -> GhostRiderConfig.MARK_COST;
			case GhostRiderAbilities.STARE -> GhostRiderConfig.STARE_COST;
			case GhostRiderAbilities.RUSH -> GhostRiderConfig.RUSH_COST;
			case GhostRiderAbilities.ERUPTION -> GhostRiderConfig.ERUPTION_COST;
			case GhostRiderAbilities.SHIELD -> GhostRiderConfig.SHIELD_COST;
			case GhostRiderAbilities.WRATH -> GhostRiderConfig.WRATH_COST;
			default -> 0f;
		};
	}
}

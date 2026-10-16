package com.projecthero.mod.bluebeetle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to Blue Beetle (v0.16.5). {@link com.projecthero.mod.hero.AbilityRouter} hands him
 * the slots when {@link #hasContext} is true -- has the power and no mutation selected. Shift is read server-side.
 *
 * <pre>
 *   R (1)  Plasma Blaster (hold)    Shift+R  Mega Cannon
 *   G (2)  Scarab Hammer            Shift+G  Scarab Grab
 *   X (3)  Scarab Swarm             Shift+X  Bubble Prison
 *   Z (4)  Plasma Spikes            Shift+Z  Shield Pulse
 *   V (5)  Energy Shield (hold)     Shift+V  Camouflage
 *   C (6)  Energy Blade (toggle)    Shift+C  Khaji-Da Unleashed
 * </pre>
 * Flight is double-tap jump ({@link BlueBeetle#toggleFlight}), not a key.
 */
public final class BlueBeetleAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private BlueBeetleAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!BlueBeetle.hasPower(player)) {
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
		if (KhajiDaTakeover.active(player)) {
			return; // v0.16.5 (user): Khaji-Da has the body -- the player's keys do nothing
		}
		// v0.16.5 (user): the blaster (R) refires as fast as its 0.5 s cooldown allows -- it skips the 25-tick repeat guard
		// (holding it can't double-fire: a running charge / blast ignores another press)
		if (slot != AbilitySlot.SLOT_1 && !freshPress(player, slot, pressed)) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> {
				if (!pressed) {
					BlueBeetleAbilities.stopBlast(player);
				} else if (shift) {
					BlueBeetleAbilities.cannon(player);
				} else {
					BlueBeetleAbilities.startBlast(player);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						BlueBeetleAbilities.grab(player);
					} else {
						BlueBeetleAbilities.hammer(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						BlueBeetleAbilities.bubble(player);
					} else {
						BlueBeetleAbilities.swarm(player);
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						BlueBeetleAbilities.pulse(player);
					} else {
						BlueBeetleAbilities.spikes(player);
					}
				}
			}
			case SLOT_5 -> {
				if (!pressed) {
					BlueBeetleAbilities.lowerShield(player);
				} else if (shift) {
					BlueBeetleAbilities.camo(player);
				} else {
					BlueBeetleAbilities.raiseShield(player);
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						BlueBeetleAbilities.unleash(player);
					} else {
						BlueBeetleAbilities.toggleBlade(player);
					}
				}
			}
			default -> {
			}
		}
	}

	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { BlueBeetleAbilities.BLAST, BlueBeetleAbilities.CANNON };
			case SLOT_2 -> new String[] { BlueBeetleAbilities.HAMMER, BlueBeetleAbilities.GRAB };
			case SLOT_3 -> new String[] { BlueBeetleAbilities.SWARM, BlueBeetleAbilities.BUBBLE };
			case SLOT_4 -> new String[] { BlueBeetleAbilities.SPIKES, BlueBeetleAbilities.PULSE };
			case SLOT_5 -> new String[] { BlueBeetleAbilities.SHIELD, BlueBeetleAbilities.CAMO };
			case SLOT_6 -> new String[] { BlueBeetleAbilities.BLADE, BlueBeetleAbilities.UNLEASH };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case BlueBeetleAbilities.BLAST -> BlueBeetleConfig.BLAST_COOLDOWN;
			case BlueBeetleAbilities.CANNON -> BlueBeetleConfig.CANNON_COOLDOWN;
			case BlueBeetleAbilities.HAMMER -> BlueBeetleConfig.HAMMER_COOLDOWN;
			case BlueBeetleAbilities.GRAB -> BlueBeetleConfig.GRAB_COOLDOWN;
			case BlueBeetleAbilities.SWARM -> BlueBeetleConfig.SWARM_COOLDOWN;
			case BlueBeetleAbilities.BUBBLE -> BlueBeetleConfig.BUBBLE_COOLDOWN;
			case BlueBeetleAbilities.SPIKES -> BlueBeetleConfig.SPIKE_COOLDOWN;
			case BlueBeetleAbilities.PULSE -> BlueBeetleConfig.PULSE_COOLDOWN;
			case BlueBeetleAbilities.SHIELD -> BlueBeetleConfig.SHIELD_COOLDOWN;
			case BlueBeetleAbilities.CAMO -> BlueBeetleConfig.CAMO_COOLDOWN;
			case BlueBeetleAbilities.BLADE -> BlueBeetleConfig.BLADE_COOLDOWN;
			case BlueBeetleAbilities.UNLEASH -> BlueBeetleConfig.UNLEASH_COOLDOWN;
			default -> 0;
		};
	}

	public static float cost(String id) {
		return switch (id) {
			case BlueBeetleAbilities.BLAST -> BlueBeetleConfig.BLAST_COST;
			case BlueBeetleAbilities.CANNON -> BlueBeetleConfig.CANNON_COST;
			case BlueBeetleAbilities.HAMMER -> BlueBeetleConfig.HAMMER_COST;
			case BlueBeetleAbilities.GRAB -> BlueBeetleConfig.GRAB_COST;
			case BlueBeetleAbilities.SWARM -> BlueBeetleConfig.SWARM_COST;
			case BlueBeetleAbilities.BUBBLE -> BlueBeetleConfig.BUBBLE_COST;
			case BlueBeetleAbilities.SPIKES -> BlueBeetleConfig.SPIKE_COST;
			case BlueBeetleAbilities.PULSE -> BlueBeetleConfig.PULSE_COST;
			case BlueBeetleAbilities.CAMO -> BlueBeetleConfig.CAMO_COST;
			case BlueBeetleAbilities.BLADE -> BlueBeetleConfig.BLADE_COST;
			case BlueBeetleAbilities.UNLEASH -> BlueBeetleConfig.ENERGY_MAX;
			default -> 0f;
		};
	}
}

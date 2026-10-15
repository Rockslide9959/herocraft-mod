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
 *   G (2)  Energy Blade (combo)     Shift+G  Scarab Grip
 *   X (3)  Wings (flight toggle)    Shift+X  Afterburner
 *   Z (4)  Energy Shield (hold)     Shift+Z  Shield Pulse
 *   V (5)  Scarab Swarm             Shift+V  Bubble Prison
 *   C (6)  Camouflage               Shift+C  Khaji-Da Unleashed
 * </pre>
 * The Energy Blade combo takes quick repeated presses, so G is not run through the key-repeat guard.
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
		if (slot != AbilitySlot.SLOT_2 && !freshPress(player, slot, pressed)) {
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
						BlueBeetleAbilities.grip(player);
					} else {
						BlueBeetleAbilities.blade(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						BlueBeetleAbilities.afterburner(player);
					} else {
						BlueBeetleAbilities.toggleWings(player);
					}
				}
			}
			case SLOT_4 -> {
				if (!pressed) {
					BlueBeetleAbilities.lowerShield(player);
				} else if (shift) {
					BlueBeetleAbilities.pulse(player);
				} else {
					BlueBeetleAbilities.raiseShield(player);
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						BlueBeetleAbilities.bubble(player);
					} else {
						BlueBeetleAbilities.swarm(player);
					}
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						BlueBeetleAbilities.unleash(player);
					} else {
						BlueBeetleAbilities.camo(player);
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
			case SLOT_2 -> new String[] { BlueBeetleAbilities.BLADE, BlueBeetleAbilities.GRIP };
			case SLOT_3 -> new String[] { BlueBeetleAbilities.WINGS, BlueBeetleAbilities.AFTERBURNER };
			case SLOT_4 -> new String[] { BlueBeetleAbilities.SHIELD, BlueBeetleAbilities.PULSE };
			case SLOT_5 -> new String[] { BlueBeetleAbilities.SWARM, BlueBeetleAbilities.BUBBLE };
			case SLOT_6 -> new String[] { BlueBeetleAbilities.CAMO, BlueBeetleAbilities.UNLEASH };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case BlueBeetleAbilities.BLAST -> BlueBeetleConfig.BLAST_COOLDOWN;
			case BlueBeetleAbilities.CANNON -> BlueBeetleConfig.CANNON_COOLDOWN;
			case BlueBeetleAbilities.BLADE -> BlueBeetleConfig.BLADE_COOLDOWN;
			case BlueBeetleAbilities.GRIP -> BlueBeetleConfig.GRIP_COOLDOWN;
			case BlueBeetleAbilities.AFTERBURNER -> BlueBeetleConfig.AFTERBURNER_COOLDOWN;
			case BlueBeetleAbilities.PULSE -> BlueBeetleConfig.PULSE_COOLDOWN;
			case BlueBeetleAbilities.SWARM -> BlueBeetleConfig.SWARM_COOLDOWN;
			case BlueBeetleAbilities.BUBBLE -> BlueBeetleConfig.BUBBLE_COOLDOWN;
			case BlueBeetleAbilities.CAMO -> BlueBeetleConfig.CAMO_COOLDOWN;
			case BlueBeetleAbilities.UNLEASH -> BlueBeetleConfig.UNLEASH_COOLDOWN;
			default -> 0;
		};
	}

	public static float cost(String id) {
		return switch (id) {
			case BlueBeetleAbilities.BLAST -> BlueBeetleConfig.BLAST_COST;
			case BlueBeetleAbilities.CANNON -> BlueBeetleConfig.CANNON_COST;
			case BlueBeetleAbilities.BLADE -> BlueBeetleConfig.BLADE_COST;
			case BlueBeetleAbilities.GRIP -> BlueBeetleConfig.GRIP_COST;
			case BlueBeetleAbilities.WINGS -> BlueBeetleConfig.FLIGHT_DRAIN;
			case BlueBeetleAbilities.AFTERBURNER -> BlueBeetleConfig.AFTERBURNER_COST;
			case BlueBeetleAbilities.SHIELD -> BlueBeetleConfig.SHIELD_COST_PER_SECOND;
			case BlueBeetleAbilities.PULSE -> BlueBeetleConfig.PULSE_COST;
			case BlueBeetleAbilities.SWARM -> BlueBeetleConfig.SWARM_COST;
			case BlueBeetleAbilities.BUBBLE -> BlueBeetleConfig.BUBBLE_COST;
			case BlueBeetleAbilities.CAMO -> BlueBeetleConfig.CAMO_COST;
			case BlueBeetleAbilities.UNLEASH -> BlueBeetleConfig.ENERGY_MAX;
			default -> 0f;
		};
	}
}

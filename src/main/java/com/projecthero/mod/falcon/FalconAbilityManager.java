package com.projecthero.mod.falcon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to the Falcon (v0.17.1). {@link com.projecthero.mod.hero.AbilityRouter} hands him the
 * slots when {@link #hasContext} is true -- has the power and no mutation selected. Shift is read server-side.
 *
 * <pre>
 *   R (1)  Wrist Guns (hold)     Shift+R  Micro-Missile Volley
 *   G (2)  Wing Slash            Shift+G  Wing Shield (hold)
 *   X (3)  Afterburner           Shift+X  Barrel Roll
 *   Z (4)  Redwing Strike        Shift+Z  Redwing Bombing Run
 *   V (5)  Tactical Goggles      Shift+V  Lift &amp; Carry
 *   C (6)  Dive Bomb             Shift+C  Talon Drop
 * </pre>
 * Double-tap jump is the take-off (its own payload); H and N do nothing.
 */
public final class FalconAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private FalconAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Falcon.hasPower(player)) {
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
					FalconAbilities.stopGuns(player);
				} else if (shift) {
					FalconAbilities.missiles(player);
				} else {
					FalconAbilities.startGuns(player);
				}
			}
			case SLOT_2 -> {
				if (!pressed) {
					FalconAbilities.lowerShield(player); // the Wing Shield is held on Shift+G
				} else if (shift) {
					FalconAbilities.raiseShield(player);
				} else {
					FalconAbilities.slash(player);
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						FalconAbilities.barrelRoll(player);
					} else {
						FalconAbilities.afterburner(player);
					}
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						FalconAbilities.redwingBombingRun(player);
					} else {
						FalconAbilities.redwingStrike(player);
					}
				}
			}
			case SLOT_5 -> {
				if (!pressed) {
					FalconAbilities.gogglesRelease(player);
				} else if (shift) {
					FalconAbilities.liftAndCarry(player);
				} else {
					FalconAbilities.gogglesPress(player);
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						FalconAbilities.talonDrop(player);
					} else {
						FalconAbilities.diveBomb(player);
					}
				}
			}
			default -> {
			}
		}
	}

	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { FalconAbilities.GUNS, FalconAbilities.MISSILES };
			case SLOT_2 -> new String[] { FalconAbilities.SLASH, FalconAbilities.SHIELD };
			case SLOT_3 -> new String[] { FalconAbilities.AFTERBURNER, FalconAbilities.ROLL };
			case SLOT_4 -> new String[] { FalconAbilities.STRIKE, FalconAbilities.BOMBING };
			case SLOT_5 -> new String[] { FalconAbilities.GOGGLES, FalconAbilities.CARRY };
			case SLOT_6 -> new String[] { FalconAbilities.DIVE, FalconAbilities.TALON };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case FalconAbilities.GUNS -> FalconConfig.GUN_COOLDOWN;
			case FalconAbilities.MISSILES -> FalconConfig.MISSILE_COOLDOWN;
			case FalconAbilities.SLASH -> FalconConfig.SLASH_COOLDOWN;
			case FalconAbilities.SHIELD -> FalconConfig.SHIELD_COOLDOWN;
			case FalconAbilities.AFTERBURNER -> FalconConfig.AFTERBURNER_COOLDOWN;
			case FalconAbilities.ROLL -> FalconConfig.ROLL_COOLDOWN;
			case FalconAbilities.STRIKE -> FalconConfig.STRIKE_COOLDOWN;
			case FalconAbilities.BOMBING -> FalconConfig.BOMB_COOLDOWN;
			case FalconAbilities.GOGGLES -> FalconConfig.GOGGLES_COOLDOWN;
			case FalconAbilities.CARRY -> FalconConfig.CARRY_COOLDOWN;
			case FalconAbilities.DIVE -> FalconConfig.DIVE_COOLDOWN;
			case FalconAbilities.TALON -> FalconConfig.TALON_COOLDOWN;
			case FalconAbilities.FLAP -> FalconConfig.FLAP_COOLDOWN;
			default -> 0;
		};
	}

	public static float cost(String id) {
		return switch (id) {
			case FalconAbilities.MISSILES -> FalconConfig.MISSILE_COST;
			case FalconAbilities.AFTERBURNER -> FalconConfig.AFTERBURNER_COST;
			case FalconAbilities.ROLL -> FalconConfig.ROLL_COST;
			case FalconAbilities.STRIKE -> FalconConfig.STRIKE_COST;
			case FalconAbilities.BOMBING -> FalconConfig.BOMB_COST;
			case FalconAbilities.DIVE -> FalconConfig.DIVE_COST;
			case FalconAbilities.TALON -> FalconConfig.TALON_COST;
			case FalconAbilities.FLAP -> FalconConfig.FLAP_COST;
			default -> 0f;
		};
	}
}

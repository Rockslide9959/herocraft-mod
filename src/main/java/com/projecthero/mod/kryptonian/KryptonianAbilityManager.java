package com.projecthero.mod.kryptonian;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to the Kryptonian (v0.14.8). {@link com.projecthero.mod.hero.AbilityRouter} hands
 * him the slots when {@link #hasContext} is true -- has the power and no mutation selected on the wheel (a Primary power
 * rules the mutations out anyway). Shift is read server-side off the player's sneak state.
 *
 * <pre>
 *   v0.14.16 layout:
 *   R (1)  Kryptonian Punch           Shift+R  Thunderclap
 *   G (2)  Heat Vision (hold)         Shift+G  Ground Pound
 *   X (3)  Super Dash / Flight Boost  Shift+X  Sky Launch
 *   Z (4)  Freeze Breath (hold)       Shift+Z  SOLAR FLARE
 *   V (5)  X-Ray Vision (toggle)      Shift+V  Pick Up / Set Down  (V while carrying: throw)
 *   C (6)  Super-Speed Barrage        Shift+C  Meteor Strike
 * </pre>
 * H stays the power wheel and N does nothing: neither is ever routed here.
 *
 * <p>v0.14.16: the OS key-repeat of a held key arrives as more "pressed" packets; a press counts only after the key was
 * let go (or after {@link #REPEAT_WINDOW} ticks of silence), so a held V cannot flicker X-Ray on and off.
 */
public final class KryptonianAbilityManager {
	/** Ticks within which another "pressed" for a still-held slot is the keyboard's auto-repeat, not a new press. */
	static final int REPEAT_WINDOW = 25;

	/** Player -> per-slot game time of the last press (or repeat) while the key is held; -1 once released. */
	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private KryptonianAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Kryptonian.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	/** True for a real key-down, false for the auto-repeat of a key that is still held. Releases always pass. */
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
				if (pressed) {
					if (shift) {
						KryptonianAbilities.thunderclap(player);
					} else {
						KryptonianAbilities.punch(player);
					}
				}
			}
			case SLOT_2 -> {
				if (!pressed) {
					KryptonianAbilities.stopHeatVision(player);
				} else if (shift) {
					KryptonianAbilities.groundSlam(player);
				} else {
					KryptonianAbilities.startHeatVision(player);
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						KryptonianAbilities.skyLaunch(player);
					} else if (Kryptonian.isFlying(player)) {
						KryptonianFlight.toggleBoost(player); // v0.14.11: X in flight is Flight Boost
					} else {
						KryptonianAbilities.superDash(player);
					}
				}
			}
			case SLOT_4 -> {
				if (!pressed) {
					KryptonianAbilities.stopFreezeBreath(player);
				} else if (shift) {
					KryptonianAbilities.solarFlare(player);
				} else {
					KryptonianAbilities.startFreezeBreath(player);
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (KryptonianAbilities.holding(player)) {
						if (shift) {
							KryptonianAbilities.setDown(player); // v0.14.16: gently, no throw, no damage
						} else {
							KryptonianAbilities.throwHeld(player);
						}
					} else if (shift) {
						KryptonianAbilities.pickUp(player);
					} else {
						KryptonianAbilities.xray(player); // v0.14.16: a toggle
					}
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						KryptonianAbilities.meteorStrike(player);
					} else {
						KryptonianAbilities.barrage(player);
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
			case SLOT_1 -> new String[] { KryptonianAbilities.PUNCH, KryptonianAbilities.THUNDERCLAP };
			case SLOT_2 -> new String[] { KryptonianAbilities.HEAT_VISION, KryptonianAbilities.GROUND_SLAM };
			case SLOT_3 -> new String[] { KryptonianAbilities.SUPER_DASH, KryptonianAbilities.SKY_LAUNCH };
			case SLOT_4 -> new String[] { KryptonianAbilities.FREEZE_BREATH, KryptonianAbilities.SOLAR_FLARE };
			case SLOT_5 -> new String[] { KryptonianAbilities.XRAY, KryptonianAbilities.SUPER_GRAB };
			case SLOT_6 -> new String[] { KryptonianAbilities.BARRAGE, KryptonianAbilities.METEOR_STRIKE };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case KryptonianAbilities.PUNCH -> KryptonianConfig.PUNCH_COOLDOWN;
			case KryptonianAbilities.HEAT_VISION -> KryptonianConfig.HEAT_COOLDOWN;
			case KryptonianAbilities.FREEZE_BREATH -> KryptonianConfig.BREATH_COOLDOWN;
			case KryptonianAbilities.THUNDERCLAP -> KryptonianConfig.CLAP_COOLDOWN;
			case KryptonianAbilities.GROUND_SLAM -> KryptonianConfig.SLAM_COOLDOWN;
			case KryptonianAbilities.SOLAR_FLARE -> KryptonianConfig.FLARE_COOLDOWN;
			case KryptonianAbilities.SUPER_DASH -> KryptonianConfig.DASH_COOLDOWN;
			case KryptonianAbilities.SKY_LAUNCH -> KryptonianConfig.LAUNCH_COOLDOWN;
			case KryptonianAbilities.SUPER_GRAB -> KryptonianConfig.GRAB_COOLDOWN;
			case KryptonianAbilities.BARRAGE -> KryptonianConfig.BARRAGE_COOLDOWN;
			case KryptonianAbilities.METEOR_STRIKE -> KryptonianConfig.STRIKE_COOLDOWN;
			default -> 0;
		};
	}

	/** v0.14.16: Solar Energy cost of a move (per second for the held ones) -- the HUD / guide / tests. */
	public static float cost(String id) {
		return switch (id) {
			case KryptonianAbilities.PUNCH -> KryptonianConfig.PUNCH_COST;
			case KryptonianAbilities.HEAT_VISION -> KryptonianConfig.HEAT_COST_PER_SECOND;
			case KryptonianAbilities.FREEZE_BREATH -> KryptonianConfig.BREATH_COST_PER_SECOND;
			case KryptonianAbilities.THUNDERCLAP -> KryptonianConfig.CLAP_COST;
			case KryptonianAbilities.GROUND_SLAM -> KryptonianConfig.SLAM_COST;
			case KryptonianAbilities.SOLAR_FLARE -> KryptonianConfig.FLARE_MIN_SOLAR;
			case KryptonianAbilities.SUPER_DASH -> KryptonianConfig.DASH_COST;
			case KryptonianAbilities.SKY_LAUNCH -> KryptonianConfig.LAUNCH_COST;
			case KryptonianAbilities.BARRAGE -> KryptonianConfig.BARRAGE_COST;
			case KryptonianAbilities.METEOR_STRIKE -> KryptonianConfig.STRIKE_COST;
			default -> 0f; // X-Ray, Pick Up: free
		};
	}
}

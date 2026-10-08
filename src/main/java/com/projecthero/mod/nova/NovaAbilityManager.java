package com.projecthero.mod.nova;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to Nova (v0.15.13). {@link com.projecthero.mod.hero.AbilityRouter} hands him the
 * slots when {@link #hasContext} is true -- has the power and no mutation selected on the wheel. Shift is read
 * server-side off the player's sneak state. The moves themselves check the suit ({@link Nova#canAct}).
 *
 * <pre>
 *   R (1)  Nova Blast (hold)       Shift+R  Nova Bolt Volley
 *   G (2)  Gravimetric Pulse       Shift+G  Gravity Slam
 *   X (3)  Comet Dash              Shift+X  Orbital Launch
 *   Z (4)  Force Field (hold)      Shift+Z  NOVA OVERLOAD (ultimate)
 *   V (5)  Worldmind Scan          Shift+V  Nova Force Transfer
 *   C (6)  Gravity Well            Shift+C  Gravity Lock
 * </pre>
 * H is the suit (its own payload) and N does nothing: neither is ever routed here.
 *
 * <p>The OS key-repeat of a held key arrives as more "pressed" packets; a press only counts after the key was let go
 * (or after {@link #REPEAT_WINDOW} ticks of silence), like the Kryptonian's.
 */
public final class NovaAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private NovaAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Nova.hasPower(player)) {
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
					NovaAbilities.stopBlast(player);
				} else if (shift) {
					NovaAbilities.volley(player);
				} else {
					NovaAbilities.startBlast(player);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						NovaAbilities.gravitySlam(player);
					} else {
						NovaAbilities.pulse(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						NovaAbilities.orbitalLaunch(player);
					} else {
						NovaAbilities.cometDash(player);
					}
				}
			}
			case SLOT_4 -> {
				if (!pressed) {
					NovaAbilities.stopShield(player); // v0.15.15: the Force Field is held
				} else if (shift) {
					NovaAbilities.overload(player);
				} else {
					NovaAbilities.startShield(player);
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						NovaAbilities.forceTransfer(player);
					} else {
						NovaAbilities.scan(player);
					}
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						NovaAbilities.gravityLock(player);
					} else {
						NovaAbilities.gravityWell(player);
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
			case SLOT_1 -> new String[] { NovaAbilities.BLAST, NovaAbilities.VOLLEY };
			case SLOT_2 -> new String[] { NovaAbilities.PULSE, NovaAbilities.SLAM };
			case SLOT_3 -> new String[] { NovaAbilities.DASH, NovaAbilities.LAUNCH };
			case SLOT_4 -> new String[] { NovaAbilities.SHIELD, NovaAbilities.OVERLOAD };
			case SLOT_5 -> new String[] { NovaAbilities.SCAN, NovaAbilities.TRANSFER };
			case SLOT_6 -> new String[] { NovaAbilities.WELL, NovaAbilities.LOCK };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case NovaAbilities.BLAST -> NovaConfig.BLAST_COOLDOWN;
			case NovaAbilities.VOLLEY -> NovaConfig.VOLLEY_COOLDOWN;
			case NovaAbilities.PULSE -> NovaConfig.PULSE_COOLDOWN;
			case NovaAbilities.SLAM -> NovaConfig.SLAM_COOLDOWN;
			case NovaAbilities.SHIELD -> NovaConfig.SHIELD_COOLDOWN;
			case NovaAbilities.OVERLOAD -> NovaConfig.OVERLOAD_COOLDOWN;
			case NovaAbilities.DASH -> NovaConfig.DASH_COOLDOWN;
			case NovaAbilities.LAUNCH -> NovaConfig.LAUNCH_COOLDOWN;
			case NovaAbilities.WELL -> NovaConfig.WELL_COOLDOWN;
			case NovaAbilities.LOCK -> NovaConfig.LOCK_COOLDOWN;
			case NovaAbilities.SCAN -> NovaConfig.SCAN_COOLDOWN;
			case NovaAbilities.TRANSFER -> NovaConfig.TRANSFER_COOLDOWN;
			default -> 0;
		};
	}

	/** Nova Force needed in the bar to start a move (the held ones need a little upkeep in hand) -- the HUD. */
	public static float minForce(String id) {
		return switch (id) {
			case NovaAbilities.BLAST -> NovaConfig.BLAST_COST_PER_SECOND * 0.5f;
			case NovaAbilities.SHIELD -> NovaConfig.SHIELD_MIN_FORCE;
			default -> cost(id);
		};
	}

	/** Nova Force cost of a move (per second for the held Nova Blast and Force Field) -- the HUD / guide / tests. */
	public static float cost(String id) {
		return switch (id) {
			case NovaAbilities.BLAST -> NovaConfig.BLAST_COST_PER_SECOND;
			case NovaAbilities.VOLLEY -> NovaConfig.VOLLEY_COST;
			case NovaAbilities.PULSE -> NovaConfig.PULSE_COST;
			case NovaAbilities.SLAM -> NovaConfig.SLAM_COST;
			case NovaAbilities.SHIELD -> NovaConfig.SHIELD_COST_PER_SECOND;
			case NovaAbilities.OVERLOAD -> NovaConfig.OVERLOAD_MIN_FORCE;
			case NovaAbilities.DASH -> NovaConfig.DASH_COST;
			case NovaAbilities.LAUNCH -> NovaConfig.LAUNCH_COST;
			case NovaAbilities.WELL -> NovaConfig.WELL_COST;
			case NovaAbilities.LOCK -> NovaConfig.LOCK_COST;
			case NovaAbilities.SCAN -> NovaConfig.SCAN_COST;
			case NovaAbilities.TRANSFER -> NovaConfig.TRANSFER_COST;
			default -> 0f;
		};
	}
}

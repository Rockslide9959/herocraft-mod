package com.projecthero.mod.stand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the universal ability slots to the player's Stand (v0.17.2). {@link com.projecthero.mod.hero.AbilityRouter}
 * hands the slots over when {@link #hasContext} is true -- a Stand user with no mutation selected. Shift is read
 * server-side off the sneak state. Star Platinum:
 *
 * <pre>
 *   R (1)  ORA Rush (hold)        Shift+R  Star Finger
 *   G (2)  Power Grab / hurl      Shift+G  Ball Bearing Flick
 *   X (3)  Stand Leap / rebound   Shift+X  Stand Dash
 *   Z (4)  Star Breaker (hold)    Shift+Z  Star Platinum: The World
 *   V (5)  Stand Guard (hold)     Shift+V  Precision Sight
 *   C (6)  Super Inhale           Shift+C  ORAAA Uppercut
 *   N (8)  pose (Shift+N is the Stand Menu, client-side)
 * </pre>
 * H summons / dismisses (its own payload). A held move started with Shift ends on release however Shift is then.
 */
public final class StandAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private StandAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Stands.hasPower(player)) {
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
		if (pressed && (TimeStop.frozen(player) || !player.isAlive() || player.isSpectator())) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> {
				if (!pressed) {
					StarPlatinum.stopRush(player);
				} else if (shift) {
					StarPlatinum.starFinger(player);
				} else {
					StarPlatinum.startRush(player);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						StarPlatinum.flick(player);
					} else {
						StarPlatinum.grabKey(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					if (shift) {
						StarPlatinum.dash(player);
					} else {
						StarPlatinum.leapKey(player);
					}
				}
			}
			case SLOT_4 -> {
				if (!pressed) {
					StarPlatinum.releaseBreaker(player);
				} else if (shift) {
					StarPlatinum.theWorld(player);
				} else {
					StarPlatinum.startBreaker(player);
				}
			}
			case SLOT_5 -> {
				if (!pressed) {
					StarPlatinum.stopGuard(player);
				} else if (shift) {
					StarPlatinum.toggleSight(player);
				} else {
					StarPlatinum.startGuard(player);
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						StarPlatinum.uppercut(player);
					} else {
						StarPlatinum.inhale(player);
					}
				}
			}
			case SLOT_8 -> {
				if (pressed && !shift) {
					Stands.togglePose(player);
				}
			}
			default -> {
			}
		}
	}

	/** The two moves on one key: [plain, shift]. */
	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { StandMove.ORA_RUSH, StandMove.STAR_FINGER };
			case SLOT_2 -> new String[] { StandMove.POWER_GRAB, StandMove.BEARING_FLICK };
			case SLOT_3 -> new String[] { StandMove.LEAP, StandMove.DASH };
			case SLOT_4 -> new String[] { StandMove.STAR_BREAKER, StandMove.THE_WORLD };
			case SLOT_5 -> new String[] { StandMove.GUARD, StandMove.SIGHT };
			case SLOT_6 -> new String[] { StandMove.INHALE, StandMove.UPPERCUT };
			default -> new String[0];
		};
	}

	public static int maxCooldown(String id) {
		StandMove m = StandType.STAR_PLATINUM.move(id);
		return m == null ? 0 : m.cooldown();
	}

	public static float cost(String id) {
		StandMove m = StandType.STAR_PLATINUM.move(id);
		return m == null ? 0f : m.cost();
	}
}

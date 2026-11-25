package com.projecthero.mod.shazam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Shazam: bridges the universal ability slots to Shazam while he is transformed (champion or family).
 * {@link com.projecthero.mod.hero.AbilityRouter} hands him the slots when {@link #hasContext} is true -- early, like Moon Knight's
 * suit, so a family member's own power steps aside while the borrowed lightning is on. Shift is read server-side.
 *
 * <p>v0.17.7 Planet-tier kit, themed on S-H-A-Z-A-M, with hold moves (user: "give him hold moves"):
 * <pre>
 *   R  Hercules Combo      Shift+R  Herculean Uppercut     Hold R  Haymaker of Hercules
 *   G  Lightning of Zeus   Shift+G  Thunderhead Slam       Hold G  Zeus's Wrath (a beam)
 *   C  Mercury Blitz       Shift+C  Thunderclap            Hold C  Mercury Rush
 *   Z  Atlas Quake         Shift+Z  "SHAZAM!" Thunderstrike (the ultimate; not the family)   Hold Z  Titan's Grip
 *   X  Mercury Dash (flying: Mercury Burst)   Shift+X  Sonic Leap
 *   V  Wisdom of Solomon   Shift+V  Courage of Achilles    Hold V  Share the Power (the champion only)
 * </pre>
 * Tap vs hold: on a key with a hold move the plain press waits; let go before {@link ShazamConfig#HOLD_TICKS} (8 ticks) and it is
 * the tap (fired on the release, the convention of {@code HoldTap} and Spider-Man), still down at 8 ticks and the hold move starts;
 * the release then ends it. Shift at key-down is the Shift move, fired at once. X has no hold move, so it fires on the press. Every
 * plain, Shift and hold move has its own cooldown. The word is chat; flight is double-tap Space. H stays the power selector and N
 * does nothing for him.
 */
public final class ShazamAbilityManager {
	// R
	public static final String HERCULES_COMBO = "hercules_combo";
	public static final String UPPERCUT = "herculean_uppercut";
	public static final String HAYMAKER = "haymaker";
	// G
	public static final String ZEUS_LIGHTNING = "zeus_lightning";
	public static final String THUNDERHEAD_SLAM = "thunderhead_slam";
	public static final String ZEUS_WRATH = "zeus_wrath";
	// C
	public static final String MERCURY_BLITZ = "mercury_blitz";
	public static final String THUNDERCLAP = "thunderclap";
	public static final String MERCURY_RUSH = "mercury_rush";
	// Z
	public static final String ATLAS_QUAKE = "atlas_quake";
	public static final String THUNDERSTRIKE = "thunderstrike";
	public static final String TITANS_GRIP = "titans_grip";
	// X
	public static final String MERCURY_DASH = "mercury_dash";
	public static final String SONIC_LEAP = "sonic_leap";
	// V
	public static final String WISDOM = "wisdom";
	public static final String COURAGE = "courage";
	public static final String SHARE_POWER = "share_power";

	/** A plain key press waiting to become a tap (released) or the hold move (still down at 8 ticks). */
	private static final class Down {
		final long at;
		boolean holding;

		Down(long at) {
			this.at = at;
		}
	}

	/** player -> slot ordinal -> the pending / held plain press. Cleared in {@code ServerStateReset} via {@link #clearSessionState}. */
	private static final Map<UUID, Map<Integer, Down>> DOWN = new ConcurrentHashMap<>();

	private ShazamAbilityManager() {
	}

	static void clearSessionState() {
		DOWN.clear();
	}

	static void forget(UUID id) {
		DOWN.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Shazam.transformed(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	/** Per tick while transformed: a plain press still down at 8 ticks becomes the hold move. */
	static void tick(ServerPlayer player) {
		Map<Integer, Down> mine = DOWN.get(player.getUUID());
		if (mine == null || mine.isEmpty()) {
			return;
		}
		long now = player.level().getGameTime();
		for (Map.Entry<Integer, Down> e : mine.entrySet()) {
			Down d = e.getValue();
			if (!d.holding && now - d.at >= ShazamConfig.HOLD_TICKS) {
				d.holding = true;
				String hold = holdFor(AbilitySlot.values()[e.getKey()]);
				if (hold != null) {
					ShazamMoves.holdStart(player, hold, d.at);
				}
			}
		}
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (slot.ordinal() >= AbilitySlot.CORE_COUNT) {
			return; // H and N are never moves
		}
		Map<Integer, Down> mine = DOWN.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
		if (!pressed) {
			Down d = mine.remove(slot.ordinal());
			if (d == null || !player.isAlive() || player.isSpectator()) {
				return; // a Shift press's release, or a key that went down before he transformed
			}
			if (d.holding) {
				String hold = holdFor(slot);
				if (hold != null) {
					ShazamMoves.holdRelease(player, hold);
				}
			} else {
				String tap = moveFor(slot, false);
				if (tap != null) {
					ShazamMoves.use(player, tap);
				}
			}
			return;
		}
		if (!player.isAlive() || player.isSpectator() || mine.containsKey(slot.ordinal())) {
			return; // (a key already down: an OS repeat)
		}
		boolean shift = player.isShiftKeyDown();
		if (shift || holdFor(slot) == null) {
			String id = moveFor(slot, shift);
			if (id != null) {
				ShazamMoves.use(player, id);
			}
			return;
		}
		mine.put(slot.ordinal(), new Down(player.level().getGameTime()));
	}

	/** The move a key sends (plain or Shift). */
	public static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? UPPERCUT : HERCULES_COMBO;
			case SLOT_2 -> shift ? THUNDERHEAD_SLAM : ZEUS_LIGHTNING;
			case SLOT_3 -> shift ? SONIC_LEAP : MERCURY_DASH;
			case SLOT_4 -> shift ? THUNDERSTRIKE : ATLAS_QUAKE;
			case SLOT_5 -> shift ? COURAGE : WISDOM;
			case SLOT_6 -> shift ? THUNDERCLAP : MERCURY_BLITZ;
			default -> null;
		};
	}

	/** The key's hold move, or null (X has none). Client-safe. */
	public static String holdFor(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> HAYMAKER;
			case SLOT_2 -> ZEUS_WRATH;
			case SLOT_4 -> TITANS_GRIP;
			case SLOT_5 -> SHARE_POWER;
			case SLOT_6 -> MERCURY_RUSH;
			default -> null;
		};
	}

	/** The HUD's ids per slot: plain, then Shift. */
	public static String[] idsOf(AbilitySlot slot) {
		String a = moveFor(slot, false);
		return a == null ? new String[0] : new String[] { a, moveFor(slot, true) };
	}

	/** The key a move is on, for the Guidebook / I menu rows ("R", "Shift+R", "Hold R"). */
	public static String keyOf(String id) {
		for (AbilitySlot slot : AbilitySlot.values()) {
			if (slot.ordinal() >= AbilitySlot.CORE_COUNT) {
				continue;
			}
			String k = switch (slot) {
				case SLOT_1 -> "R";
				case SLOT_2 -> "G";
				case SLOT_3 -> "X";
				case SLOT_4 -> "Z";
				case SLOT_5 -> "V";
				default -> "C";
			};
			if (id.equals(moveFor(slot, false))) {
				return k;
			}
			if (id.equals(moveFor(slot, true))) {
				return "Shift+" + k;
			}
			if (id.equals(holdFor(slot))) {
				return "Hold " + k;
			}
		}
		return "";
	}

	/** Lightning a move costs (the HUD greys a move he can't pay for). */
	public static float cost(String id) {
		return switch (id) {
			case UPPERCUT -> ShazamConfig.UPPERCUT_COST;
			case HAYMAKER -> ShazamConfig.HAYMAKER_COST;
			case ZEUS_LIGHTNING -> ShazamConfig.ZEUS_COST;
			case THUNDERHEAD_SLAM -> ShazamConfig.SLAM_COST;
			case ZEUS_WRATH -> ShazamConfig.WRATH_COST;
			case MERCURY_BLITZ -> ShazamConfig.BLITZ_COST;
			case THUNDERCLAP -> ShazamConfig.CLAP_COST;
			case MERCURY_RUSH -> ShazamConfig.RUSH_COST;
			case ATLAS_QUAKE -> ShazamConfig.QUAKE_COST;
			case TITANS_GRIP -> ShazamConfig.GRIP_COST;
			case THUNDERSTRIKE -> ShazamConfig.ULT_COST;
			case COURAGE -> ShazamConfig.COURAGE_COST;
			default -> 0f;
		};
	}

	/** A family member can't use it (the Thunderstrike, Share the Power). Client-safe. */
	public static boolean championOnly(String id) {
		return THUNDERSTRIKE.equals(id) || SHARE_POWER.equals(id);
	}

	/** Usable as far as who he is goes (the HUD's grey-out). Client-safe. */
	public static boolean allowed(Player player, String id) {
		return !championOnly(id) || !Shazam.isFamily(player);
	}

	public static int cooldownRemaining(Player player, String id) {
		return Shazam.cooldownRemaining(player, id);
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case HERCULES_COMBO -> ShazamConfig.COMBO_FINISH_COOLDOWN;
			case UPPERCUT -> ShazamConfig.UPPERCUT_COOLDOWN;
			case HAYMAKER -> ShazamConfig.HAYMAKER_COOLDOWN;
			case ZEUS_LIGHTNING -> ShazamConfig.ZEUS_COOLDOWN;
			case THUNDERHEAD_SLAM -> ShazamConfig.SLAM_COOLDOWN;
			case ZEUS_WRATH -> ShazamConfig.WRATH_COOLDOWN;
			case MERCURY_BLITZ -> ShazamConfig.BLITZ_COOLDOWN;
			case THUNDERCLAP -> ShazamConfig.CLAP_COOLDOWN;
			case MERCURY_RUSH -> ShazamConfig.RUSH_COOLDOWN;
			case ATLAS_QUAKE -> ShazamConfig.QUAKE_COOLDOWN;
			case TITANS_GRIP -> ShazamConfig.GRIP_COOLDOWN;
			case THUNDERSTRIKE -> ShazamConfig.ULT_COOLDOWN;
			case MERCURY_DASH -> ShazamConfig.BURST_COOLDOWN;
			case SONIC_LEAP -> ShazamConfig.LEAP_COOLDOWN;
			case WISDOM -> ShazamConfig.WISDOM_COOLDOWN;
			case COURAGE -> ShazamConfig.COURAGE_COOLDOWN;
			case SHARE_POWER -> ShazamConfig.SHARE_COOLDOWN;
			default -> 1;
		};
	}
}

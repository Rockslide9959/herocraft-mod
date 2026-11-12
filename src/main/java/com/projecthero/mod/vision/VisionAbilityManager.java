package com.projecthero.mod.vision;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Vision: bridges the universal ability slots to his moves. {@link com.projecthero.mod.hero.AbilityRouter} hands him the slots
 * when {@link #hasContext} is true. Shift is read server-side; a key's release goes to the same move its press did.
 *
 * <pre>
 *   R  Mind Stone Beam (hold)   Shift+R  Solar Lance
 *   G  Density Punch            Shift+G  Phase Strike
 *   Z  Mind Stone Pulse         Shift+Z  Mind Stone Unleashed
 *   X  Phase Dash               Shift+X  Density Dive
 *   C  Synthezoid Grip          Shift+C  Diamond Density (toggle)
 *   V  Human Hologram (toggle)  Shift+V  Intangibility (toggle)
 * </pre>
 * Flight is double-tap Space ({@link VisionFlight}). H stays the power selector and N does nothing for him.
 */
public final class VisionAbilityManager {
	public static final String BEAM = "mind_beam";
	public static final String LANCE = "solar_lance";
	public static final String PUNCH = "density_punch";
	public static final String STRIKE = "phase_strike";
	public static final String PULSE = "mind_pulse";
	public static final String UNLEASH = "unleashed";
	public static final String DASH = "phase_dash";
	public static final String DIVE = "density_dive";
	public static final String GRIP = "grip";
	public static final String DENSITY = "diamond_density";
	public static final String HOLOGRAM = "hologram";
	public static final String PHASE = "intangibility";

	/** The move id each held slot's press went to (so its release reaches the same move). */
	private static final Map<UUID, String[]> HELD = new ConcurrentHashMap<>();

	private VisionAbilityManager() {
	}

	static void clearSessionState() {
		HELD.clear();
	}

	static void forget(UUID id) {
		HELD.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Vision.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	static void tick(ServerPlayer player) {
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		int i = slot.ordinal();
		if (i >= AbilitySlot.CORE_COUNT) {
			return; // H and N are never moves
		}
		String[] held = HELD.computeIfAbsent(player.getUUID(), k -> new String[AbilitySlot.values().length]);
		if (!pressed) {
			String id = held[i];
			held[i] = null;
			if (id != null && isHold(id)) {
				VisionMoves.release(player, id);
			}
			return;
		}
		held[i] = null;
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		String id = moveFor(slot, player.isShiftKeyDown());
		if (id == null) {
			return;
		}
		if (isHold(id)) {
			if (VisionMoves.startHold(player, id)) {
				held[i] = id;
			}
			return;
		}
		VisionMoves.use(player, id);
	}

	/** R (the Mind Stone Beam) is a hold. */
	public static boolean isHold(String id) {
		return BEAM.equals(id);
	}

	/** The toggles and the dash work while intangible; nothing else does (he can't touch anything). Client-safe. */
	public static boolean worksWhilePhased(String id) {
		return DASH.equals(id) || HOLOGRAM.equals(id) || PHASE.equals(id) || DENSITY.equals(id);
	}

	public static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? LANCE : BEAM;
			case SLOT_2 -> shift ? STRIKE : PUNCH;
			case SLOT_3 -> shift ? DIVE : DASH;
			case SLOT_4 -> shift ? UNLEASH : PULSE;
			case SLOT_5 -> shift ? PHASE : HOLOGRAM;
			case SLOT_6 -> shift ? DENSITY : GRIP;
			default -> null;
		};
	}

	public static String[] idsOf(AbilitySlot slot) {
		String a = moveFor(slot, false);
		return a == null ? new String[0] : new String[] { a, moveFor(slot, true) };
	}

	/** Energy a move costs (the HUD greys a move he can't pay for). The beam and the toggles: what they need to start. */
	public static float cost(String id) {
		return switch (id) {
			case BEAM -> VisionConfig.BEAM_START_COST;
			case LANCE -> VisionConfig.LANCE_COST;
			case PUNCH -> VisionConfig.PUNCH_COST;
			case STRIKE -> VisionConfig.STRIKE_COST;
			case PULSE -> VisionConfig.PULSE_COST;
			case UNLEASH -> VisionConfig.UNLEASH_COST;
			case DASH -> VisionConfig.DASH_COST;
			case DIVE -> VisionConfig.DIVE_COST;
			case GRIP -> VisionConfig.GRIP_COST;
			case DENSITY -> 1f;
			case PHASE -> VisionConfig.PHASE_START_COST;
			default -> 0f;
		};
	}

	/** Usable as far as the rules go (nothing but the dash and the toggles while intangible). Client-safe. */
	public static boolean allowed(Player player, String id) {
		return !Vision.intangible(player) || worksWhilePhased(id);
	}

	public static int cooldownRemaining(Player player, String id) {
		return Vision.cooldownRemaining(player, id);
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case BEAM -> VisionConfig.BEAM_COOLDOWN;
			case LANCE -> VisionConfig.LANCE_COOLDOWN;
			case PUNCH -> VisionConfig.PUNCH_COOLDOWN;
			case STRIKE -> VisionConfig.STRIKE_COOLDOWN;
			case PULSE -> VisionConfig.PULSE_COOLDOWN;
			case UNLEASH -> VisionConfig.UNLEASH_COOLDOWN;
			case DASH -> VisionConfig.DASH_COOLDOWN;
			case DIVE -> VisionConfig.DIVE_COOLDOWN;
			case GRIP -> VisionConfig.GRIP_COOLDOWN;
			case DENSITY -> VisionConfig.DENSITY_TOGGLE_COOLDOWN;
			case HOLOGRAM -> VisionConfig.HOLOGRAM_TOGGLE_COOLDOWN;
			case PHASE -> VisionConfig.PHASE_TOGGLE_COOLDOWN;
			default -> 1;
		};
	}
}

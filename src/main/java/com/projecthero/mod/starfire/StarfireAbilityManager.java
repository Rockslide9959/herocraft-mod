package com.projecthero.mod.starfire;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Starfire: bridges the universal ability slots to her moves. {@link com.projecthero.mod.hero.AbilityRouter} hands her
 * the slots when {@link #hasContext} is true. Shift is read server-side; a key's release goes to the same move its press did.
 *
 * <pre>
 *   R  Starbolt            Shift+R  Star Burst (hold)
 *   G  Tamaranean Grab     Shift+G  Eye Beams (hold)
 *   Z  Starbolt Barrage    Shift+Z  Supernova (full Fury)
 *   X  Star Dash           Shift+X  Comet Dive
 *   C  Tamaranean Charge   Shift+C  Ground Pound
 *   V  Language Kiss       Shift+V  Glorious Radiance (toggle)
 * </pre>
 * Flight is double-tap Space ({@link StarfireFlight}). H stays the power selector and N does nothing for her.
 */
public final class StarfireAbilityManager {
	public static final String STARBOLT = "starbolt";
	public static final String STAR_BURST = "star_burst";
	public static final String GRAB = "grab";
	public static final String EYE_BEAMS = "eye_beams";
	public static final String BARRAGE = "barrage";
	public static final String SUPERNOVA = "supernova";
	public static final String STAR_DASH = "star_dash";
	public static final String COMET_DIVE = "comet_dive";
	public static final String CHARGE = "charge";
	public static final String GROUND_POUND = "ground_pound";
	public static final String KISS = "kiss";
	public static final String RADIANCE = "radiance";

	/** The move id each held slot's press went to (so its release reaches the same move). */
	private static final Map<UUID, String[]> HELD = new ConcurrentHashMap<>();

	private StarfireAbilityManager() {
	}

	static void clearSessionState() {
		HELD.clear();
	}

	static void forget(UUID id) {
		HELD.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Starfire.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static String heldId(ServerPlayer player, AbilitySlot slot) {
		String[] held = HELD.get(player.getUUID());
		return held == null ? null : held[slot.ordinal()];
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
				StarfireMoves.release(player, id);
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
			if (StarfireMoves.startHold(player, id)) {
				held[i] = id;
			}
			return;
		}
		StarfireMoves.use(player, id);
	}

	/** Shift+R (Star Burst) and Shift+G (Eye Beams) are holds. */
	public static boolean isHold(String id) {
		return STAR_BURST.equals(id) || EYE_BEAMS.equals(id);
	}

	public static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? STAR_BURST : STARBOLT;
			case SLOT_2 -> shift ? EYE_BEAMS : GRAB;
			case SLOT_3 -> shift ? COMET_DIVE : STAR_DASH;
			case SLOT_4 -> shift ? SUPERNOVA : BARRAGE;
			case SLOT_5 -> shift ? RADIANCE : KISS;
			case SLOT_6 -> shift ? GROUND_POUND : CHARGE;
			default -> null;
		};
	}

	public static String[] idsOf(AbilitySlot slot) {
		String a = moveFor(slot, false);
		return a == null ? new String[0] : new String[] { a, moveFor(slot, true) };
	}

	/** Starbolt a move costs (the HUD greys a move she can't pay for). Eye Beams: the first second. */
	public static float cost(Player player, String id) {
		return switch (id) {
			case STARBOLT -> Starfire.boltCost(player, StarfireConfig.BOLT_COST);
			case STAR_BURST -> Starfire.boltCost(player, StarfireConfig.BURST_COST);
			case GRAB -> StarfireConfig.GRAB_COST;
			case EYE_BEAMS -> StarfireConfig.BEAM_COST_PER_SECOND;
			case BARRAGE -> Starfire.boltCost(player, StarfireConfig.BARRAGE_COST);
			case SUPERNOVA -> StarfireConfig.NOVA_COST;
			case STAR_DASH -> StarfireConfig.DASH_COST;
			case COMET_DIVE -> StarfireConfig.DIVE_COST;
			case CHARGE -> StarfireConfig.CHARGE_COST;
			case GROUND_POUND -> StarfireConfig.POUND_COST;
			default -> 0f;
		};
	}

	/** Usable as far as the rules go (Supernova needs full Fury). Client-safe. */
	public static boolean allowed(Player player, String id) {
		return !SUPERNOVA.equals(id) || Starfire.fullFury(player);
	}

	public static int cooldownRemaining(Player player, String id) {
		return Starfire.cooldownRemaining(player, id);
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case STARBOLT -> StarfireConfig.BOLT_COOLDOWN;
			case STAR_BURST -> StarfireConfig.BURST_COOLDOWN;
			case GRAB -> StarfireConfig.GRAB_COOLDOWN;
			case EYE_BEAMS -> StarfireConfig.BEAM_COOLDOWN;
			case BARRAGE -> StarfireConfig.BARRAGE_COOLDOWN;
			case SUPERNOVA -> StarfireConfig.NOVA_COOLDOWN;
			case STAR_DASH -> StarfireConfig.DASH_COOLDOWN;
			case COMET_DIVE -> StarfireConfig.DIVE_COOLDOWN;
			case CHARGE -> StarfireConfig.CHARGE_COOLDOWN;
			case GROUND_POUND -> StarfireConfig.POUND_COOLDOWN;
			case KISS -> StarfireConfig.KISS_COOLDOWN;
			case RADIANCE -> StarfireConfig.RADIANCE_COOLDOWN;
			default -> 1;
		};
	}
}

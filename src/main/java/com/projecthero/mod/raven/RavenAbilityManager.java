package com.projecthero.mod.raven;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Raven: bridges the universal ability slots to her moves. {@link com.projecthero.mod.hero.AbilityRouter} hands her the
 * slots when {@link #hasContext} is true. Shift is read server-side; a key's release goes to the same move its press did.
 *
 * <pre>
 *   R  Dark Bolt          Shift+R  Telekinesis (hold)
 *   G  Shadow Grasp       Shift+G  Soul-Self
 *   Z  Dark Shield (hold) Shift+Z  Trigon's Wrath (Rage only)
 *   X  Shadow Dash        Shift+X  Shadow Step Strike
 *   C  Telekinetic Wave   Shift+C  Debris Storm (again: fire one)
 *   V  Empath Heal        Shift+V  Meditate (hold)
 * </pre>
 * Flight is double-tap Space ({@link RavenFlight}). H stays the power selector and N does nothing for her.
 */
public final class RavenAbilityManager {
	public static final String DARK_BOLT = "dark_bolt";
	public static final String TELEKINESIS = "telekinesis";
	public static final String SHADOW_GRASP = "shadow_grasp";
	public static final String SOUL_SELF = "soul_self";
	public static final String DARK_SHIELD = "dark_shield";
	public static final String TRIGONS_WRATH = "trigons_wrath";
	public static final String SHADOW_DASH = "shadow_dash";
	public static final String SHADOW_STEP = "shadow_step";
	public static final String TELEKINETIC_WAVE = "telekinetic_wave";
	public static final String DEBRIS_STORM = "debris_storm";
	public static final String EMPATH_HEAL = "empath_heal";
	public static final String MEDITATE = "meditate";

	/** The move id each held slot's press went to (so its release reaches the same move). */
	private static final Map<UUID, String[]> HELD = new ConcurrentHashMap<>();

	private RavenAbilityManager() {
	}

	static void clearSessionState() {
		HELD.clear();
	}

	static void forget(UUID id) {
		HELD.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Raven.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static String heldId(ServerPlayer player, AbilitySlot slot) {
		String[] held = HELD.get(player.getUUID());
		return held == null ? null : held[slot.ordinal()];
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
				RavenMoves.release(player, id);
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
			if (RavenMoves.startHold(player, id)) {
				held[i] = id;
			}
			return;
		}
		RavenMoves.use(player, id);
	}

	/** Shift+R, Z and Shift+V are held. */
	public static boolean isHold(String id) {
		return TELEKINESIS.equals(id) || DARK_SHIELD.equals(id) || MEDITATE.equals(id);
	}

	/** The move a key sends. */
	public static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? TELEKINESIS : DARK_BOLT;
			case SLOT_2 -> shift ? SOUL_SELF : SHADOW_GRASP;
			case SLOT_3 -> shift ? SHADOW_STEP : SHADOW_DASH;
			case SLOT_4 -> shift ? TRIGONS_WRATH : DARK_SHIELD;
			case SLOT_5 -> shift ? MEDITATE : EMPATH_HEAL;
			case SLOT_6 -> shift ? DEBRIS_STORM : TELEKINETIC_WAVE;
			default -> null;
		};
	}

	/** The HUD's ids per slot: plain, then Shift. */
	public static String[] idsOf(AbilitySlot slot) {
		String a = moveFor(slot, false);
		return a == null ? new String[0] : new String[] { a, moveFor(slot, true) };
	}

	/** Dark Energy a move costs up front (the HUD greys a move she can't pay for). */
	public static float cost(String id) {
		return switch (id) {
			case DARK_BOLT -> RavenConfig.BOLT_COST;
			case TELEKINESIS -> RavenConfig.TK_COST;
			case SHADOW_GRASP -> RavenConfig.GRASP_COST;
			case SOUL_SELF -> RavenConfig.SOUL_COST;
			case DARK_SHIELD -> 1f;
			case SHADOW_DASH -> RavenConfig.DASH_COST;
			case SHADOW_STEP -> RavenConfig.STEP_COST;
			case TELEKINETIC_WAVE -> RavenConfig.WAVE_COST;
			case DEBRIS_STORM -> RavenConfig.DEBRIS_COST;
			case EMPATH_HEAL -> RavenConfig.HEAL_COST;
			default -> 0f;
		};
	}

	/** Usable apart from energy and cooldown: Trigon's Wrath needs Rage; Debris Storm is free while blocks orbit. Client-safe. */
	public static boolean allowed(Player player, String id) {
		if (TRIGONS_WRATH.equals(id)) {
			return Raven.rage(player) && !Raven.inWrath(player);
		}
		return true;
	}

	/** Energy actually needed right now (Debris Storm costs nothing to fire what's already orbiting). Client-safe. */
	public static float costNow(Player player, String id) {
		if (DEBRIS_STORM.equals(id)) {
			var s = Raven.peek(player);
			if (s != null && s.debris > 0) {
				return 0f;
			}
		}
		return cost(id);
	}

	public static int cooldownRemaining(Player player, String id) {
		return Raven.cooldownRemaining(player, id);
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case DARK_BOLT -> RavenConfig.BOLT_COOLDOWN;
			case TELEKINESIS -> RavenConfig.TK_COOLDOWN;
			case SHADOW_GRASP -> RavenConfig.GRASP_COOLDOWN;
			case SOUL_SELF -> RavenConfig.SOUL_COOLDOWN;
			case DARK_SHIELD -> RavenConfig.SHIELD_BROKEN_COOLDOWN;
			case TRIGONS_WRATH -> RavenConfig.WRATH_COOLDOWN;
			case SHADOW_DASH -> RavenConfig.DASH_COOLDOWN;
			case SHADOW_STEP -> RavenConfig.STEP_COOLDOWN;
			case TELEKINETIC_WAVE -> RavenConfig.WAVE_COOLDOWN;
			case DEBRIS_STORM -> RavenConfig.DEBRIS_COOLDOWN;
			case EMPATH_HEAL -> RavenConfig.HEAL_COOLDOWN;
			default -> 1;
		};
	}
}

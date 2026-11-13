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
 * v0.17.3 Raven (revamp, {@code docs/RAVEN_REVAMP_DESIGN.md}): bridges the universal ability slots to her moves.
 * {@link com.projecthero.mod.hero.AbilityRouter} hands her the slots when {@link #hasContext} is true. Shift is read server-side; a
 * key's release goes to the same move its press did.
 *
 * <pre>
 *   R  Dark Bolt            Shift+R  Telekinesis (hold)
 *   G  Soul Shield          Shift+G  Soul-Self (steer; again = teleport to it)
 *   Z  Telekinetic Crush    Shift+Z  Trigon's Wrath (Rage only)
 *   X  Shadow Dash          Shift+X  Shadow Step Strike
 *   C  Dark Energy Wave     Shift+C  Demonic Ascension
 *   V  tap: cast the picked spell / hold: the Spell Wheel     Shift+V  Meditate (hold)
 * </pre>
 * Plain V is the client's: it times the press, opens the wheel on a hold and sends a cast ({@code RavenActionPayload}) on a tap, so
 * the server ignores the slot itself. Flight is double-tap Space ({@link RavenFlight}). H stays the power selector and N does
 * nothing for her.
 */
public final class RavenAbilityManager {
	public static final String DARK_BOLT = "dark_bolt";
	public static final String TELEKINESIS = "telekinesis";
	public static final String SOUL_SHIELD = "soul_shield";
	public static final String SOUL_SELF = "soul_self";
	public static final String TELEKINETIC_CRUSH = "telekinetic_crush";
	public static final String TRIGONS_WRATH = "trigons_wrath";
	public static final String SHADOW_DASH = "shadow_dash";
	public static final String SHADOW_STEP = "shadow_step";
	public static final String ENERGY_WAVE = "energy_wave";
	public static final String ASCENSION = "demonic_ascension";
	/** V: tap casts the picked spell, hold opens the wheel (client side). */
	public static final String CAST_SPELL = "cast_spell";
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
		if (id == null || CAST_SPELL.equals(id)) {
			return; // plain V is timed on the client (tap = cast, hold = wheel)
		}
		if (isHold(id)) {
			if (RavenMoves.startHold(player, id)) {
				held[i] = id;
			}
			return;
		}
		RavenMoves.use(player, id);
	}

	/** Shift+R and Shift+V are held. */
	public static boolean isHold(String id) {
		return TELEKINESIS.equals(id) || MEDITATE.equals(id);
	}

	/** The move a key sends. */
	public static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? TELEKINESIS : DARK_BOLT;
			case SLOT_2 -> shift ? SOUL_SELF : SOUL_SHIELD;
			case SLOT_3 -> shift ? SHADOW_STEP : SHADOW_DASH;
			case SLOT_4 -> shift ? TRIGONS_WRATH : TELEKINETIC_CRUSH;
			case SLOT_5 -> shift ? MEDITATE : CAST_SPELL;
			case SLOT_6 -> shift ? ASCENSION : ENERGY_WAVE;
			default -> null;
		};
	}

	/** The HUD's ids per slot: plain, then Shift. */
	public static String[] idsOf(AbilitySlot slot) {
		String a = moveFor(slot, false);
		return a == null ? new String[0] : new String[] { a, moveFor(slot, true) };
	}

	/** Dark Energy a move costs up front, before Demonic Ascension's discount (the HUD greys a move she can't pay for). */
	public static float cost(String id) {
		return switch (id) {
			case DARK_BOLT -> RavenConfig.BOLT_COST;
			case TELEKINESIS -> RavenConfig.TK_COST;
			case SOUL_SHIELD -> RavenConfig.GUARD_COST;
			case SOUL_SELF -> RavenConfig.SOUL_COST;
			case TELEKINETIC_CRUSH -> RavenConfig.CRUSH_COST;
			case SHADOW_DASH -> RavenConfig.DASH_COST;
			case SHADOW_STEP -> RavenConfig.STEP_COST;
			case ENERGY_WAVE -> RavenConfig.WAVE_COST;
			case ASCENSION -> RavenConfig.ASCEND_COST;
			default -> {
				RavenSpell spell = RavenSpell.byId(id);
				yield spell == null ? 0f : spell.cost;
			}
		};
	}

	/** Usable apart from energy and cooldown: Trigon's Wrath needs Rage. Client-safe. */
	public static boolean allowed(Player player, String id) {
		if (TRIGONS_WRATH.equals(id)) {
			return Raven.rage(player) && !Raven.inWrath(player);
		}
		if (ASCENSION.equals(id)) {
			return !Raven.ascended(player);
		}
		return true;
	}

	/** Energy actually needed right now: Demonic Ascension's -25%, and the second presses that are free. Client-safe. */
	public static float costNow(Player player, String id) {
		if (CAST_SPELL.equals(id)) {
			return RavenSpells.costNow(player, Raven.selectedSpell(player));
		}
		if (SOUL_SELF.equals(id) && RavenMoves.soulOut(player)) {
			return 0f; // the second press teleports to it
		}
		return cost(id) * Raven.costMult(player);
	}

	/** The cooldown a slot's id shows: V shows the picked spell's. Client-safe. */
	public static int cooldownRemaining(Player player, String id) {
		if (CAST_SPELL.equals(id)) {
			return Raven.cooldownRemaining(player, Raven.selectedSpell(player).id);
		}
		return Raven.cooldownRemaining(player, id);
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case DARK_BOLT -> RavenConfig.BOLT_COOLDOWN;
			case TELEKINESIS -> RavenConfig.TK_COOLDOWN;
			case SOUL_SHIELD -> RavenConfig.GUARD_COOLDOWN;
			case SOUL_SELF -> RavenConfig.SOUL_COOLDOWN;
			case TELEKINETIC_CRUSH -> RavenConfig.CRUSH_COOLDOWN;
			case TRIGONS_WRATH -> RavenConfig.WRATH_COOLDOWN;
			case SHADOW_DASH -> RavenConfig.DASH_COOLDOWN;
			case SHADOW_STEP -> RavenConfig.STEP_COOLDOWN;
			case ENERGY_WAVE -> RavenConfig.WAVE_COOLDOWN;
			case ASCENSION -> RavenConfig.ASCEND_COOLDOWN;
			default -> {
				RavenSpell spell = RavenSpell.byId(id);
				yield spell == null ? 1 : spell.cooldown;
			}
		};
	}

	/** {@link #maxCooldown} for a slot id (V: the picked spell's). Client-safe. */
	public static int maxCooldown(Player player, String id) {
		return CAST_SPELL.equals(id) ? Raven.selectedSpell(player).cooldown : maxCooldown(id);
	}
}

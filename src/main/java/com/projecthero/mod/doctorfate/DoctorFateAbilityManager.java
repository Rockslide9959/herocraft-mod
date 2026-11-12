package com.projecthero.mod.doctorfate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Doctor Fate: bridges the universal ability slots to the spells. {@link com.projecthero.mod.hero.AbilityRouter} hands
 * him the slots when {@link #hasContext} is true. Shift is read server-side; a key's release goes to the move its press did.
 *
 * <pre>
 *   R  Ankh Bolt        Shift+R  Golden Chains
 *   G  Ankh Shield (hold)  Shift+G  Banishment
 *   X  Ankh Step        Shift+X  Gate Drop
 *   Z  Glyph Storm      Shift+Z  Avatar of Order (asks Nabu first)
 *   C  Order Blast      Shift+C  Ward of Order
 *   V  Helmet           Shift+V  Sight of Nabu
 * </pre>
 * H stays the power selector and N does nothing. Flight is double-tap Space. While the Avatar of Order stands, R, Z and C (and
 * their Shift moves) are its hand slams instead. While possessed nothing he presses counts (Space struggles, client side).
 */
public final class DoctorFateAbilityManager {
	public static final String ANKH_BOLT = "ankh_bolt";
	public static final String GOLDEN_CHAINS = "golden_chains";
	public static final String ANKH_SHIELD = "ankh_shield";
	public static final String BANISHMENT = "banishment";
	public static final String ANKH_STEP = "ankh_step";
	public static final String GATE_DROP = "gate_drop";
	public static final String GLYPH_STORM = "glyph_storm";
	public static final String AVATAR = "avatar_of_order";
	public static final String ORDER_BLAST = "order_blast";
	public static final String WARD = "ward_of_order";
	public static final String HELMET = "helmet";
	public static final String SIGHT = "sight_of_nabu";

	private static final Map<UUID, String[]> HELD = new ConcurrentHashMap<>();

	private DoctorFateAbilityManager() {
	}

	static void clearSessionState() {
		HELD.clear();
	}

	static void forget(UUID id) {
		HELD.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!DoctorFate.hasPower(player)) {
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
		// the shield's hold times out in DoctorFateSpells
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
			if (ANKH_SHIELD.equals(id)) {
				DoctorFateSpells.lowerShield(player);
			}
			return;
		}
		held[i] = null;
		if (!player.isAlive() || player.isSpectator() || DoctorFate.possessed(player)) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		String id = moveFor(slot, shift);
		if (id == null) {
			return;
		}
		// the Avatar of Order: the attack keys are its hand
		if (DoctorFate.avatarActive(player) && (slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_4 || slot == AbilitySlot.SLOT_6)) {
			DoctorFateSpells.avatarSlam(player);
			return;
		}
		if (ANKH_SHIELD.equals(id)) {
			if (DoctorFateSpells.raiseShield(player)) {
				held[i] = id;
			}
			return;
		}
		DoctorFateSpells.use(player, id);
	}

	public static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? GOLDEN_CHAINS : ANKH_BOLT;
			case SLOT_2 -> shift ? BANISHMENT : ANKH_SHIELD;
			case SLOT_3 -> shift ? GATE_DROP : ANKH_STEP;
			case SLOT_4 -> shift ? AVATAR : GLYPH_STORM;
			case SLOT_5 -> shift ? SIGHT : HELMET;
			case SLOT_6 -> shift ? WARD : ORDER_BLAST;
			default -> null;
		};
	}

	public static String[] idsOf(AbilitySlot slot) {
		String a = moveFor(slot, false);
		return a == null ? new String[0] : new String[] { a, moveFor(slot, true) };
	}

	/** Kent Nelson's own magic works without the helmet: R, G, X (weaker) and V. Everything else needs it. */
	public static boolean needsHelmet(String id) {
		return !(ANKH_BOLT.equals(id) || ANKH_SHIELD.equals(id) || ANKH_STEP.equals(id) || HELMET.equals(id));
	}

	/** Attack spells Nabu may swap for his own (Override). */
	public static boolean overridable(String id) {
		return ANKH_BOLT.equals(id) || GOLDEN_CHAINS.equals(id) || GLYPH_STORM.equals(id) || ORDER_BLAST.equals(id) || GATE_DROP.equals(id);
	}

	/** The Dominion a spell adds (before the Pull discount). Client-safe. */
	public static float dominionOf(String id) {
		return switch (id) {
			case ANKH_BOLT -> DoctorFateConfig.BOLT_DOMINION;
			case GOLDEN_CHAINS -> DoctorFateConfig.CHAINS_DOMINION;
			case BANISHMENT -> DoctorFateConfig.BANISH_DOMINION;
			case GLYPH_STORM -> DoctorFateConfig.GLYPH_DOMINION;
			case GATE_DROP -> DoctorFateConfig.GATE_DOMINION;
			case ORDER_BLAST -> DoctorFateConfig.BLAST_DOMINION;
			case WARD -> DoctorFateConfig.WARD_DOMINION;
			case SIGHT -> DoctorFateConfig.SIGHT_DOMINION;
			default -> 0f;
		};
	}

	/** Usable right now as far as the helmet goes (the HUD's grey-out). Client-safe. */
	public static boolean usable(Player player, String id) {
		if (DoctorFate.possessed(player)) {
			return false;
		}
		if (HELMET.equals(id)) {
			return DoctorFate.wearsHelmet(player) || DoctorFate.lockoutTicksLeft(player) <= 0;
		}
		return !needsHelmet(id) || DoctorFate.wearsHelmet(player);
	}

	public static int cooldownRemaining(Player player, String id) {
		return DoctorFate.cooldownRemaining(player, id);
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case ANKH_BOLT -> DoctorFateConfig.BOLT_COOLDOWN;
			case GOLDEN_CHAINS -> DoctorFateConfig.CHAINS_COOLDOWN;
			case ANKH_SHIELD -> DoctorFateConfig.SHIELD_COOLDOWN;
			case BANISHMENT -> DoctorFateConfig.BANISH_COOLDOWN;
			case ANKH_STEP -> DoctorFateConfig.STEP_COOLDOWN;
			case GATE_DROP -> DoctorFateConfig.GATE_COOLDOWN;
			case GLYPH_STORM -> DoctorFateConfig.GLYPH_COOLDOWN;
			case AVATAR -> DoctorFateConfig.AVATAR_COOLDOWN;
			case ORDER_BLAST -> DoctorFateConfig.BLAST_COOLDOWN;
			case WARD -> DoctorFateConfig.WARD_COOLDOWN;
			case HELMET -> DoctorFateConfig.HELMET_COOLDOWN;
			case SIGHT -> DoctorFateConfig.SIGHT_COOLDOWN;
			default -> 1;
		};
	}
}

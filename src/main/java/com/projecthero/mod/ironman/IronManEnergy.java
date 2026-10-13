package com.projecthero.mod.ironman;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.server.level.ServerPlayer;

/**
 * Iron Man suit integrity -- and, until v0.16.1, suit energy.
 *
 * <p><b>v0.16.1 comic rework (explicit user design): suit ENERGY IS GONE.</b> Abilities run on cooldowns only, flight is
 * unlimited, nothing charges or drains. The energy API below is kept as an inert shim so the many call sites (and old
 * saves) keep working without a code path for "out of energy": {@link #energy} / {@link #stackEnergy} always read a full
 * suit, {@link #spend} / {@link #has} always succeed and never deduct, and {@link #setEnergy} / {@link #addEnergy} do
 * nothing. Old saves' {@code suit_energy} map and the stacks' {@code SUIT_ENERGY} component are still read by the codecs
 * and simply ignored. New code must not use any of it.
 *
 * <p><b>Integrity is the resource.</b> While the worn suit has integrity it absorbs its Mark's share of every hit
 * ({@link IronManSuit#absorbShare()}; {@link IronManDamage}), 1:1 from integrity; at 0 the suit is disabled (no flight,
 * no abilities, heavy Slowness, dark visor) until a Suit Platform / Stark Gantry repairs it. The Mark 6 and Mark 7 also
 * repair themselves out of combat ({@link IronManIntegrity}); the Hulkbuster only through its Replacement Pod.
 */
public final class IronManEnergy {
	/** Fallback max integrity for contexts with no suit id (codec defaults, unknown ids). */
	public static final float MAX_INTEGRITY = 500f;

	/** v0.16.1: the inert "always full" value the energy shim reports for every suit. */
	public static final float FULL = 1000f;

	/** The full-condition integrity pool for a specific suit. */
	public static float maxIntegrity(String suitId) {
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		return suit == null ? MAX_INTEGRITY : suit.maxIntegrity();
	}

	private IronManEnergy() {
	}

	// ---------------- v0.16.1: the inert energy shim ----------------

	/** v0.16.1: no suit energy -- always reads a full suit ({@link #FULL}; 0 for an unknown id). */
	public static float energy(ServerPlayer player, String suitId) {
		return capacity(suitId);
	}

	/** v0.16.1: no suit energy -- a stack always reads full. */
	public static float stackEnergy(net.minecraft.world.item.ItemStack stack, String suitId) {
		return capacity(suitId);
	}

	/** v0.16.1: {@link #FULL} for any known suit id, else 0. */
	public static float capacity(String suitId) {
		return suitId != null && IronManSuits.byId(suitId) != null ? FULL : 0f;
	}

	/** v0.16.1: always 1 for a known suit. */
	public static float energyFraction(ServerPlayer player, String suitId) {
		return capacity(suitId) > 0f ? 1f : 0f;
	}

	/** v0.16.1: no-op (there is no energy to set). */
	public static void setEnergy(ServerPlayer player, String suitId, float value) {
	}

	/** v0.16.1: no-op. */
	public static void addEnergy(ServerPlayer player, String suitId, float delta) {
	}

	/** v0.16.1: always succeeds and deducts nothing. */
	public static boolean spend(ServerPlayer player, String suitId, float amount) {
		return true;
	}

	/** v0.16.1: always true. */
	public static boolean has(ServerPlayer player, String suitId, float amount) {
		return true;
	}

	/** v0.16.1: platforms and gantries only repair now -- 0. */
	public static float platformEnergyPerSecond(IronManSuit suit) {
		return 0f;
	}

	/** v0.16.1: no-op -- there is no worn Arc Reactor recharge (see {@link IronManIntegrity#tick} for self-repair). */
	public static void tickRecharge(ServerPlayer player, IronManSuit suit) {
	}

	// ---------------- integrity ----------------

	public static float stackIntegrity(net.minecraft.world.item.ItemStack stack, String suitId) {
		Float v = stack.get(com.projecthero.mod.item.ModDataComponents.SUIT_INTEGRITY);
		float max = maxIntegrity(suitId);
		return v != null ? Math.max(0f, Math.min(max, v)) : max;
	}

	/**
	 * Write the suit's integrity onto an armour stack that is leaving a suit (unequip / store). The {@code energy}
	 * argument is ignored since v0.16.1 (kept so existing callers compile); the old energy component is cleared.
	 */
	public static void stampStack(net.minecraft.world.item.ItemStack stack, float energy, float integrity) {
		stack.remove(com.projecthero.mod.item.ModDataComponents.SUIT_ENERGY);
		stack.set(com.projecthero.mod.item.ModDataComponents.SUIT_INTEGRITY, Math.max(0f, integrity));
	}

	/** Load an armour stack's carried integrity into the player's suit pool (equip / deploy). */
	public static void loadFromStack(ServerPlayer player, String suitId, net.minecraft.world.item.ItemStack stack) {
		setIntegrity(player, suitId, stackIntegrity(stack, suitId));
	}

	public static float integrity(ServerPlayer player, String suitId) {
		return TonyStark.state(player).suitIntegrity.getOrDefault(suitId, maxIntegrity(suitId));
	}

	public static float integrityFraction(ServerPlayer player, String suitId) {
		return Math.max(0f, Math.min(1f, integrity(player, suitId) / Math.max(1f, maxIntegrity(suitId))));
	}

	public static void setIntegrity(ServerPlayer player, String suitId, float value) {
		TonyStarkState s = TonyStark.state(player).copy();
		s.suitIntegrity.put(suitId, Math.max(0f, Math.min(maxIntegrity(suitId), value)));
		player.setAttached(ModAttachments.TONY_STARK_STATE, s);
	}

	public static void damageIntegrity(ServerPlayer player, String suitId, float amount) {
		setIntegrity(player, suitId, integrity(player, suitId) - amount);
	}

	/** Integrity per second a docked suit repairs on a Suit Platform -- a flat 5/s for every mark (v0.15.20). */
	public static float platformIntegrityPerSecond(IronManSuit suit) {
		return suit == null ? 0f
				: com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity.REGEN_INTEGRITY_PER_SECOND;
	}

	/**
	 * Integrity lost per point of ABSORBED damage (v0.16.1: the suit takes its Mark's share of a hit 1:1; the wearer
	 * takes the rest). Kept as a named constant because Ultron's hull-damage bonus scales from it.
	 */
	public static final float INTEGRITY_PER_DAMAGE = 1.0f;
}

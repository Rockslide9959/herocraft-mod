package com.projecthero.mod.ironman;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.server.level.ServerPlayer;

/**
 * The reusable Iron Man suit-energy + suit-integrity system (spec section 28). One shared
 * implementation for every mark; the capacity / recharge / per-ability cost numbers come from the
 * {@link IronManSuit} definition.
 *
 * <p>Energy powers flight, repulsors, Unibeam, missiles, suit summon, advanced weapons and the HUD.
 * At zero energy those all shut down but the armour still provides its raw {@code ArmorMaterial}
 * defense. Integrity is separate damage-state that only a Suit Platform (or creative) restores.
 *
 * <p>All values live in the {@link TonyStarkState} attachment keyed by suit id, so they are
 * server-authoritative and survive relog. A modified client cannot report "unlimited Iron Man
 * energy" -- every spend is validated here.
 */
public final class IronManEnergy {
	/**
	 * Maximum suit integrity ("condition") of a fresh or fully-repaired suit. Integrity is a damage
	 * pool separate from energy: hits bleed it, only a Suit Platform (or creative) restores it, and at
	 * zero the suit's active mitigation stops (raw {@code ArmorMaterial} defense remains). Bumped from
	 * 100 to 500 ("changes 10") so a suit can take a real beating before failing.
	 *
	 * <p>This is only the <b>default</b> now ("changes 13"): a mark can override it via
	 * {@link IronManSuit#maxIntegrity()} (Mark III = 1500). Always resolve a specific suit's ceiling
	 * through {@link #maxIntegrity(String)} rather than this constant; the constant remains the
	 * fallback for contexts with no suit id (codec defaults, unknown ids). Every place that shows
	 * integrity as a percentage divides by the resolved per-suit max, so the HUD still reads 0-100%.
	 */
	public static final float MAX_INTEGRITY = 500f;

	/** The full-condition integrity pool for a specific suit ("changes 13" -- Mark III is 1500, others 500). */
	public static float maxIntegrity(String suitId) {
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		return suit == null ? MAX_INTEGRITY : suit.maxIntegrity();
	}

	private IronManEnergy() {
	}

	public static float energy(ServerPlayer player, String suitId) {
		return TonyStark.state(player).suitEnergy.getOrDefault(suitId, 0.0f);
	}

	// ---------------- suit charge carried on the armour stack (spec "changes 9") ----------------

	/**
	 * The suit charge stored on an armour {@link net.minecraft.world.item.ItemStack}. Absent component
	 * means "full" -- a freshly fabricated suit ships charged, and this is what stops the Suit Platform
	 * reading zero for a suit that has never been worn.
	 */
	public static float stackEnergy(net.minecraft.world.item.ItemStack stack, String suitId) {
		Float v = stack.get(com.projecthero.mod.item.ModDataComponents.SUIT_ENERGY);
		return v != null ? Math.max(0f, Math.min(capacity(suitId), v)) : capacity(suitId);
	}

	public static float stackIntegrity(net.minecraft.world.item.ItemStack stack, String suitId) {
		Float v = stack.get(com.projecthero.mod.item.ModDataComponents.SUIT_INTEGRITY);
		float max = maxIntegrity(suitId);
		return v != null ? Math.max(0f, Math.min(max, v)) : max;
	}

	/** Write the current suit charge onto an armour stack that is leaving a suit (unequip / store). */
	public static void stampStack(net.minecraft.world.item.ItemStack stack, float energy, float integrity) {
		stack.set(com.projecthero.mod.item.ModDataComponents.SUIT_ENERGY, Math.max(0f, energy));
		stack.set(com.projecthero.mod.item.ModDataComponents.SUIT_INTEGRITY, Math.max(0f, integrity));
	}

	/** Load an armour stack's carried charge into the player's suit pool (equip / deploy). */
	public static void loadFromStack(ServerPlayer player, String suitId, net.minecraft.world.item.ItemStack stack) {
		setEnergy(player, suitId, stackEnergy(stack, suitId));
		setIntegrity(player, suitId, stackIntegrity(stack, suitId));
	}

	public static float integrity(ServerPlayer player, String suitId) {
		return TonyStark.state(player).suitIntegrity.getOrDefault(suitId, maxIntegrity(suitId));
	}

	public static float capacity(String suitId) {
		IronManSuit suit = IronManSuits.byId(suitId);
		return suit == null ? 0f : suit.energyCapacity();
	}

	public static float energyFraction(ServerPlayer player, String suitId) {
		float cap = capacity(suitId);
		return cap <= 0 ? 0f : Math.max(0f, Math.min(1f, energy(player, suitId) / cap));
	}

	public static void setEnergy(ServerPlayer player, String suitId, float value) {
		TonyStarkState s = TonyStark.state(player).copy();
		s.suitEnergy.put(suitId, Math.max(0f, Math.min(capacity(suitId), value)));
		player.setAttached(ModAttachments.TONY_STARK_STATE, s);
	}

	public static void addEnergy(ServerPlayer player, String suitId, float delta) {
		setEnergy(player, suitId, energy(player, suitId) + delta);
	}

	/** Returns true and deducts if the suit has at least {@code amount}; otherwise returns false and deducts nothing. */
	public static boolean spend(ServerPlayer player, String suitId, float amount) {
		float have = energy(player, suitId);
		if (have < amount) {
			return false;
		}
		setEnergy(player, suitId, have - amount);
		return true;
	}

	public static boolean has(ServerPlayer player, String suitId, float amount) {
		return energy(player, suitId) >= amount;
	}

	public static void setIntegrity(ServerPlayer player, String suitId, float value) {
		TonyStarkState s = TonyStark.state(player).copy();
		s.suitIntegrity.put(suitId, Math.max(0f, Math.min(maxIntegrity(suitId), value)));
		player.setAttached(ModAttachments.TONY_STARK_STATE, s);
	}

	public static void damageIntegrity(ServerPlayer player, String suitId, float amount) {
		setIntegrity(player, suitId, integrity(player, suitId) - amount);
	}

	/**
	 * A Suit Platform charges and repairs a docked suit at a <b>flat 0.1% per second</b> (v0.6.2) --
	 * 0.1% of the mark's energy capacity per second, and 0.1% of its max integrity per second. A full
	 * refill (or repair) of any mark therefore takes ~1000 s (~16.7 min) regardless of pool size.
	 *
	 * <p>History: "changes 22" replaced the original flat 1.5%/s with "a multiple of the worn Arc
	 * Reactor" (3x, later 5x) so the per-mark regen numbers mattered; v0.6.2 goes back to a flat
	 * fraction, just a much gentler one, so a rack is a steady top-up rather than a near-instant one
	 * and every mark behaves predictably.
	 */
	public static final float PLATFORM_FRACTION_PER_SECOND = 0.001f;

	/** Energy per second a docked suit gains on a Suit Platform -- v0.14.27: a flat 10/s for every mark (the per-mark
	 *  {@link IronManSuit#platformEnergyPerSecondOverride()} and the 0.1%-of-pool formula no longer apply). */
	public static float platformEnergyPerSecond(IronManSuit suit) {
		return suit == null ? 0f
				: com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity.REGEN_ENERGY_PER_SECOND;
	}

	/** Integrity per second a docked suit repairs on a Suit Platform -- v0.14.27: a flat 10/s for every mark. */
	public static float platformIntegrityPerSecond(IronManSuit suit) {
		return suit == null ? 0f
				: com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity.REGEN_INTEGRITY_PER_SECOND;
	}

	/**
	 * Per-tick recharge from the suit's own worn Arc Reactor ("changes 18"): a flat per-mark
	 * {@link IronManSuit#energyRegenPerSecond()} figure (Mark 1 = 0.5/s ... Mark 7 = 3.0/s). The reactor
	 * keeps a resting suit ticking over; a Suit Platform is far faster. Only tops up energy.
	 */
	public static void tickRecharge(ServerPlayer player, IronManSuit suit) {
		float current = energy(player, suit.id());
		if (current < suit.energyCapacity()) {
			addEnergy(player, suit.id(), regenPerSecond(player, suit) / 20f);
		}
		// v0.15.9, explicit user request: no suit repairs its own integrity while worn any more (the Mark 6 / Mark 7's
		// 1/s from v0.15.4 is gone too) -- docking on a Suit Platform is the only repair
	}

	/**
	 * The worn regen right now. v0.15.3, explicit user request: <b>halved while flying</b> (repulsor flight or the Mark 1's
	 * X burst), on top of the flat {@link IronManSuit#flatFlightDrainPerSecond()} drain, so flight always costs energy net.
	 */
	public static float regenPerSecond(ServerPlayer player, IronManSuit suit) {
		float rate = suit.energyRegenPerSecond();
		if (IronManFlight.isFlying(player) || TonyStark.state(player).timedFlightUntil > player.level().getGameTime()) {
			rate *= FLIGHT_REGEN_SCALE;
		}
		return rate;
	}

	/** v0.15.3: worn energy regen is multiplied by this while the suit is flying. */
	public static final float FLIGHT_REGEN_SCALE = 0.5f;

	/** v0.14.26: a Suit Platform repairs integrity at half speed. */
	public static final float PLATFORM_INTEGRITY_SCALE = 0.5f;

	// v0.15.3, explicit user request: a worn suit no longer repairs its own integrity at all (the per-mark armorRegen x
	// WORN_REGEN_SCALE trickle is gone) -- docking it on a Suit Platform (or creative) is the only repair.
	// v0.15.4 let the Mark 6 / Mark 7 repair 1 integrity/s while worn; v0.15.9 took that away again (no worn repair at all).

	/**
	 * v0.15.3: a hit that lands on a wearer with the suit's chestplate on bleeds integrity by this share of the hit, and
	 * the suit absorbs none of it. v0.15.9, explicit user request ("integrity will take 100% of the damage the player
	 * takes"): the whole hit -- 10 damage = 10 integrity, and the player still takes the full 10. Every suit, fire
	 * included. See {@link IronManDamage#onDamageTaken}.
	 */
	public static final float INTEGRITY_PER_DAMAGE = 1.0f;
}

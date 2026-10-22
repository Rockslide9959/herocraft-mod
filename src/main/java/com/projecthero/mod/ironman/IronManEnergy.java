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
 * <p><b>v0.16.7 (user): energy is back for the five ERA I suits</b> (Mk I .. Mk V, the Gantry Age) -- see the section
 * comment below and {@link com.projecthero.mod.ironman.ability.era1.IronManEraOne}. Every later suit keeps the shim.
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

	// ---------------- v0.16.1: the energy shim -- v0.16.7 (user): real again for the Era I suits ----------------
	//
	// v0.16.7 (user) ERA I: the five Gantry Age suits (IronManEraOne.SUITS) have a reactor again -- per-suit capacity and
	// regen (IronManSuits: Mk I 600 / 1.0 per s .. Mk V 1000 / 2.5), stored per suit in the synced TonyStarkState.suitEnergy
	// map while worn and on the armour stacks (SUIT_ENERGY) when racked / crated, exactly as before v0.16.1. Every move pays
	// its kit cost (IronManEraOne.pay), held / toggled moves drain per second (IronManEraOne.drainTick), the Mk III's flight
	// drains 6 (sprint 12) per second; at 0 the suit BROWNS OUT until it is back to 10% (TonyStarkState.brownout). Inside a
	// Stark Gantry (racked in range of one, or worn standing on its floor) it charges +50/s. Every LATER suit keeps the
	// v0.16.1 always-full shim: energy() / stackEnergy() read full, setEnergy() / addEnergy() do nothing for them.
	//
	// spend() / has() stay the always-true legacy shim on purpose, for EVERY suit: they are the pre-v0.16.1 helpers' own
	// costs (IronManDash, IronManGroundPound, the flamethrower...) which Era I moves may reuse -- the kit table is the one
	// price list, so those reused helpers must never charge a second time. Era I energy goes through drain().

	/** v0.16.7: an Era I suit that browned out comes back online at this fraction of its capacity. */
	public static final float BROWNOUT_RECOVER = 0.10f;
	/** v0.16.7: below this fraction the HUD warns LOW POWER. */
	public static final float LOW_POWER = 0.20f;
	/** v0.16.7 (user): what a Stark Gantry charges an Era I suit per second (racked in range, or worn on its floor). */
	public static final float GANTRY_CHARGE_PER_SECOND = 50f;
	/** v0.16.7 (user): the Mk III's flight drain per second (sprint flight: double). */
	public static final float ERA_ONE_FLIGHT_PER_SECOND = 2f; // v0.16.7 (user): was 6 -- reactors must last long missions
	public static final float ERA_ONE_SPRINT_FLIGHT_PER_SECOND = 4f; // v0.16.7 (user): was 12

	/** v0.16.7: does {@code suitId} have a real reactor (an Era I suit)? */
	public static boolean real(String suitId) {
		// v0.16.7 (user): Era II too ("all the armours should have energy levels")
		return com.projecthero.mod.ironman.ability.era1.IronManEraOne.hasReactor(suitId) && IronManSuits.byId(suitId) != null;
	}

	/** The suit's current energy: Era I -- the stored value (full until first used); every later suit -- always full. */
	public static float energy(ServerPlayer player, String suitId) {
		if (!real(suitId)) {
			return capacity(suitId);
		}
		Float v = TonyStark.state(player).suitEnergy.get(suitId);
		return v == null ? capacity(suitId) : Math.max(0f, Math.min(capacity(suitId), v));
	}

	/** A stack's carried energy: Era I -- its {@code SUIT_ENERGY} component (absent = full); later suits -- full. */
	public static float stackEnergy(net.minecraft.world.item.ItemStack stack, String suitId) {
		if (!real(suitId)) {
			return capacity(suitId);
		}
		Float v = stack.get(com.projecthero.mod.item.ModDataComponents.SUIT_ENERGY);
		return v == null ? capacity(suitId) : Math.max(0f, Math.min(capacity(suitId), v));
	}

	/** The suit's capacity ({@link IronManSuit#energyCapacity()}: Era I its own, later suits {@link #FULL}); 0 if unknown. */
	public static float capacity(String suitId) {
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		return suit == null ? 0f : suit.energyCapacity();
	}

	public static float energyFraction(ServerPlayer player, String suitId) {
		float cap = capacity(suitId);
		return cap <= 0f ? 0f : Math.max(0f, Math.min(1f, energy(player, suitId) / cap));
	}

	/** Set an Era I suit's energy (clamped); hitting 0 browns it out, 10% brings it back. No-op for the later suits. */
	public static void setEnergy(ServerPlayer player, String suitId, float value) {
		if (!real(suitId)) {
			return;
		}
		float cap = capacity(suitId);
		float v = Math.max(0f, Math.min(cap, value));
		TonyStarkState s = TonyStark.state(player).copy();
		Float old = s.suitEnergy.get(suitId);
		boolean wasOut = s.brownout.contains(suitId);
		boolean out = v <= 0f || wasOut && v < cap * BROWNOUT_RECOVER;
		if (old != null && old == v && out == wasOut) {
			return; // nothing changed -- no attachment churn / resync
		}
		s.suitEnergy.put(suitId, v);
		if (out) {
			s.brownout.add(suitId);
		} else {
			s.brownout.remove(suitId);
		}
		player.setAttached(ModAttachments.TONY_STARK_STATE, s);
		if (out && !wasOut) {
			onBrownout(player, suitId);
		}
	}

	public static void addEnergy(ServerPlayer player, String suitId, float delta) {
		if (real(suitId) && delta != 0f) {
			setEnergy(player, suitId, energy(player, suitId) + delta);
		}
	}

	/**
	 * v0.16.7: take {@code amount} from an Era I suit's reactor. False (and nothing taken) when it is browned out or holds
	 * less than that. Always true for the later suits (no energy there).
	 */
	public static boolean drain(ServerPlayer player, String suitId, float amount) {
		if (!real(suitId)) {
			return true;
		}
		if (brownedOut(player, suitId)) {
			return false;
		}
		float e = energy(player, suitId);
		if (e < amount - 1.0e-4f) {
			return false;
		}
		setEnergy(player, suitId, e - amount);
		return true;
	}

	/**
	 * v0.16.7: a running drain (held / toggled move, flight, hover) -- takes what is asked, or whatever is left; false
	 * once the reactor is empty (the suit browns out then, so the caller should stop).
	 */
	public static boolean drainRunning(ServerPlayer player, String suitId, float amount) {
		if (!real(suitId)) {
			return true;
		}
		if (brownedOut(player, suitId)) {
			return false;
		}
		float e = energy(player, suitId);
		setEnergy(player, suitId, e - amount);
		return e - amount > 0f;
	}

	/** v0.16.7: has this Era I suit browned out (0 energy, not yet back to 10%)? Always false for the later suits. */
	public static boolean brownedOut(net.minecraft.world.entity.player.Player player, String suitId) {
		if (!real(suitId)) {
			return false;
		}
		TonyStarkState s = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		return s != null && s.brownout.contains(suitId);
	}

	/** v0.16.7: the suit just browned out -- everything it runs stops (flight / hover / held moves); armour still works. */
	private static void onBrownout(ServerPlayer player, String suitId) {
		if (suitId.equals(IronManArmor.wornSuitId(player))) {
			IronManSuitTicker.shutDownAllSystems(player, TonyStark.state(player), IronManSuits.byId(suitId));
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.ironman.era1.brownout")
					.withStyle(net.minecraft.ChatFormatting.RED), true);
			IronManSounds.play(player, IronManSounds.POWER_FAIL, 0.8f, 0.8f);
		}
	}

	/** Legacy (pre-v0.16.1 helpers' own costs): always succeeds and deducts nothing -- see the note above. */
	public static boolean spend(ServerPlayer player, String suitId, float amount) {
		return true;
	}

	/** Legacy: always true -- see the note above. */
	public static boolean has(ServerPlayer player, String suitId, float amount) {
		return true;
	}

	/**
	 * v0.16.1: platforms only repair -- 0. v0.16.7 (user): an Era I suit racked in range of a complete Stark Gantry charges
	 * {@link #GANTRY_CHARGE_PER_SECOND}; the platform decides whether it is in range.
	 */
	public static float platformEnergyPerSecond(IronManSuit suit) {
		return suit == null ? 0f : suit.eraOne() ? GANTRY_CHARGE_PER_SECOND : suit.hasReactor() ? ERA_TWO_PLATFORM_CHARGE_PER_SECOND : 0f;
	}

	/**
	 * v0.16.7 (user) Era II: what any Suit Platform charges a racked Era II suit per second (they are not Gantry-bound;
	 * in range of a complete Stark Gantry it is the full {@link #GANTRY_CHARGE_PER_SECOND}).
	 */
	public static final float ERA_TWO_PLATFORM_CHARGE_PER_SECOND = 25f;

	/**
	 * v0.16.7 (user): one tick of a worn Era I suit's reactor -- once a second its own regen (+50/s and a 5/s repair instead
	 * while the wearer stands on a complete Stark Gantry floor), and the flying suits' (Mk III / IV / V) flight drain every tick. No-op for the
	 * later suits.
	 */
	public static void tickRecharge(ServerPlayer player, IronManSuit suit) {
		if (suit == null || !suit.hasReactor() || !IronManArmor.hasChestplate(player, suit.id())) {
			return;
		}
		flightDrainTick(player, suit);
		if (player.level().getGameTime() % 20L == 0L) {
			rechargeStep(player, suit);
		}
	}

	/** v0.16.7: one tick of an Era I flier's (Mk III / IV / V) flight drain (2/s, sprint 4/s); running dry lands it. Public for the gametests. */
	public static void flightDrainTick(ServerPlayer player, IronManSuit suit) {
		if (suit != null && suit.hasReactor() && suit.manualFlight() && com.projecthero.mod.ironman.IronManFlight.isFlying(player)) {
			float perSecond = player.isSprinting() ? ERA_ONE_SPRINT_FLIGHT_PER_SECOND : ERA_ONE_FLIGHT_PER_SECOND;
			if (com.projecthero.mod.ironman.ability.IronManSpaceBoost.engagedServer(player)) {
				// v0.16.7 (user): the Mk VIII Flight Boost -- 2.5x the sprint-flight drain while it runs
				perSecond = ERA_ONE_SPRINT_FLIGHT_PER_SECOND * com.projecthero.mod.ironman.ability.IronManSpaceBoost.DRAIN_MULTIPLIER;
			}
			if (!drainRunning(player, suit.id(), perSecond / 20f)) {
				com.projecthero.mod.ironman.IronManFlight.setFlying(player, false);
			}
		}
	}

	/**
	 * v0.16.7: one second of a worn Era I suit's reactor -- its own regen, or (standing on a complete Stark Gantry floor)
	 * +{@link #GANTRY_CHARGE_PER_SECOND} and a 5/s repair. Public for the gametests.
	 */
	public static void rechargeStep(ServerPlayer player, IronManSuit suit) {
		if (suit == null || !suit.hasReactor()) {
			return;
		}
		boolean gantry = com.projecthero.mod.ironman.ability.era1.EraOneGantry.onGantryFloor(player);
		addEnergy(player, suit.id(), gantry ? GANTRY_CHARGE_PER_SECOND : suit.energyRegenPerSecond());
		if (gantry) {
			float max = suit.maxIntegrity();
			float cur = integrity(player, suit.id());
			if (cur < max) {
				setIntegrity(player, suit.id(), Math.min(max,
						cur + com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity.REGEN_INTEGRITY_PER_SECOND));
			}
		}
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
	 * v0.16.7 (user): an Era I piece carries its energy again (racks / the Suit Crate keep it).
	 */
	public static void stampStack(net.minecraft.world.item.ItemStack stack, float energy, float integrity) {
		String suitId = stack.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem a ? a.suitId() : null;
		if (suitId == null) {
			// v0.16.7 (user) Era II: a packed form (briefcase, backpack, heat case, Hulkbuster Uplink) carries its suit's charge
			com.projecthero.mod.ironman.suit.PortableForm form = com.projecthero.mod.ironman.suit.PortableForms.byItem(stack.getItem());
			suitId = form == null ? null : form.suitId();
		}
		if (real(suitId)) {
			stack.set(com.projecthero.mod.item.ModDataComponents.SUIT_ENERGY, Math.max(0f, Math.min(capacity(suitId), energy)));
		} else {
			stack.remove(com.projecthero.mod.item.ModDataComponents.SUIT_ENERGY);
		}
		stack.set(com.projecthero.mod.item.ModDataComponents.SUIT_INTEGRITY, Math.max(0f, integrity));
	}

	/** Load an armour stack's carried integrity (v0.16.7: and an Era I suit's energy) into the player's suit pool. */
	public static void loadFromStack(ServerPlayer player, String suitId, net.minecraft.world.item.ItemStack stack) {
		setIntegrity(player, suitId, stackIntegrity(stack, suitId));
		if (real(suitId)) {
			setEnergy(player, suitId, stackEnergy(stack, suitId));
		}
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

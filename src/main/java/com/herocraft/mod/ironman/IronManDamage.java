package com.herocraft.mod.ironman;

import com.herocraft.mod.ironman.suit.IronManSuit;
import com.herocraft.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Iron Man suit damage mitigation + suit integrity ("changes 12" rework of spec section 28).
 *
 * <p>While a Tony Stark player wears a valid, powered (energy &gt; 0) full suit, every hit is split by
 * a flat rule instead of the old per-suit {@code damageReduction} multiplier:
 * <ul>
 *   <li><b>Integrity intact</b> ({@code integrity > 0}): the suit's plating and stabilisers absorb
 *       {@code 90%} of the hit -- the wearer only takes the remaining {@code 10%}. The absorbed 90%
 *       bleeds suit integrity, and running the mitigation drains a little energy too.</li>
 *   <li><b>Integrity failed</b> ({@code integrity <= 0}): the active systems that did that absorbing are
 *       gone, but the suit's raw plating hasn't -- the wearer now takes {@code 80%} of the hit (still a
 *       little better than bare skin), and {@link IronManSuitTicker} keeps them Slowed + Weakened for as
 *       long as it stays at zero (life-support and stabilisers failing).</li>
 *   <li><b>Energy fully depleted</b>: nothing is running at all -- raw {@code ArmorMaterial} defense
 *       only, exactly as before.</li>
 * </ul>
 * <p>"changes 22": <b>fire is special-cased</b>. Anything tagged {@code IS_FIRE} bleeds integrity at
 * {@value #FIRE_INTEGRITY_MULTIPLIER} and energy at {@value #FIRE_ENERGY_MULTIPLIER} of the normal
 * rate. The wearer's own damage share is unchanged -- only the wear-and-tear on the armour is.
 *
 * <p>Iron Man boots alone (even without the rest of the suit) still give strong fall-damage protection,
 * and the Repulsor Barrier still eats most of a hit while it holds -- both unchanged by this rework.
 *
 * <p>Uses the same cancel-and-re-apply-smaller pattern as {@code HeroDamageRules} because Fabric's
 * {@code ALLOW_DAMAGE} is a boolean veto with no "reduce amount".
 */
public final class IronManDamage {
	/** Fraction of a raw hit the wearer takes while integrity is intact -- the other 90% is absorbed. */
	private static final float PLAYER_SHARE_INTEGRITY_OK = 0.10f;
	/** Fraction of a raw hit the wearer takes once integrity has failed (systems down, plating remains). */
	private static final float PLAYER_SHARE_INTEGRITY_FAILED = 0.80f;
	/** Share of the raw hit that bleeds integrity while it's still absorbing its 90% share. */
	private static final float INTEGRITY_DAMAGE_SHARE = 0.90f;
	/** Suit energy spent per point of raw incoming damage, running the mitigation either way. */
	private static final float ENERGY_COST_SHARE_INTEGRITY_OK = 0.20f;
	private static final float ENERGY_COST_SHARE_INTEGRITY_FAILED = 0.10f;
	/**
	 * "changes 22": burning is <b>heat</b>, not impact, and the suit is a sealed heat-shielded shell --
	 * so ordinary fire barely touches its condition.
	 *
	 * <p>Fire is a 1-damage tick every half second that never stops until you leave the flames, so the
	 * flat 90%-of-the-hit integrity rule charged a full-suit wearer ~1.8 integrity per second just for
	 * standing in a campfire. Thirty seconds alight cost a Mark 1 nearly a fifth of its entire pool for
	 * an amount of damage a player in leather would shrug off. Every fire / lava / hot-floor source now
	 * bleeds integrity and energy at these multiples of the normal rate instead; the wearer's own
	 * damage share is untouched, so the suit protects them exactly as well as before.
	 */
	private static final float FIRE_INTEGRITY_MULTIPLIER = 0.05f;
	private static final float FIRE_ENERGY_MULTIPLIER = 0.10f;

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private IronManDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(IronManDamage::onAllowDamage);
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || !TonyStark.hasPower(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return true;
		}
		// "changes 17": Protocol Phoenix makes the player invulnerable while the emergency suit is
		// inbound (except the two truly un-survivable sources handled above).
		if (ProtocolPhoenix.incapacitated(player)) {
			return false;
		}

		// Boots-only fall protection (partial armour -- spec section 32).
		if (source.is(DamageTypeTags.IS_FALL)) {
			String bootsSuit = suitOfPiece(player, EquipmentSlot.FEET);
			if (bootsSuit != null && IronManEnergy.energy(player, bootsSuit) > 1.0f) {
				IronManEnergy.addEnergy(player, bootsSuit, -Math.min(200f, amount * 15f));
				player.resetFallDistance();
				// "changes 17": some marks (Mark 1) only *reduce* fall damage rather than negating it.
				IronManSuit bootsSuitDef = IronManSuits.byId(bootsSuit);
				float takeFraction = bootsSuitDef == null ? 0f : bootsSuitDef.fallDamageFraction();
				if (takeFraction > 0f) {
					return reduce(player, source, amount, takeFraction);
				}
				return false;
			}
		}

		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);

		// Repulsor Shield (slot 2 / G): a deployed frontal shield blocks 90% of any hit that lands in the
		// 180-degree arc in front of the player ("changes 13") while it holds, even without the full suit
		// -- it only needs the chestplate the ability itself requires. Hits from outside that arc pass
		// straight through to the ordinary suit mitigation below.
		if (suit != null && IronManArmor.hasChestplate(player, suitId)
				&& com.herocraft.mod.ironman.ability.IronManAbilities.barrierActive(player)
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				&& (suit.fullBodyShield() || isFrontal(player, source))) {
			IronManEnergy.addEnergy(player, suitId, -Math.min(400f, amount * 12f));
			return reduce(player, source,
					amount * com.herocraft.mod.ironman.ability.IronManAbilities.BARRIER_DAMAGE_MULT, 1.0f);
		}

		// "changes 14": the integrity split protects you whenever the suit's core (the chestplate that
		// houses the arc reactor and plating) is on and powered -- not only with all four pieces worn.
		if (suit == null || !IronManArmor.hasChestplate(player, suitId)) {
			return true;
		}
		if (IronManEnergy.energy(player, suitId) <= 0f) {
			return true; // suit fully unpowered -- physical protection only
		}

		// "changes 22": fire is cheap for the suit to shrug off -- see FIRE_INTEGRITY_MULTIPLIER.
		boolean fire = source.is(DamageTypeTags.IS_FIRE);
		float integrityMult = fire ? FIRE_INTEGRITY_MULTIPLIER : 1.0f;
		float energyMult = fire ? FIRE_ENERGY_MULTIPLIER : 1.0f;

		boolean integrityOk = IronManEnergy.integrity(player, suitId) > 0f;
		if (integrityOk) {
			// The suit's condition pool eats 90% of every hit; the wearer takes the remaining 10%
			// "through the armour". A 10-damage hit -> 9 off integrity, 1 to the player.
			float beforeIntegrity = IronManEnergy.integrity(player, suitId);
			IronManEnergy.damageIntegrity(player, suitId, amount * INTEGRITY_DAMAGE_SHARE * integrityMult);
			IronManEnergy.addEnergy(player, suitId, -amount * ENERGY_COST_SHARE_INTEGRITY_OK * energyMult);
			if (beforeIntegrity > 0f && IronManEnergy.integrity(player, suitId) <= 0f) {
				ServerLevel level = (ServerLevel) player.level();
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
				player.displayClientMessage(net.minecraft.network.chat.Component
						.translatable("message.herocraft.ironman.integrity_failed")
						.withStyle(net.minecraft.ChatFormatting.RED), true);
			}
		} else {
			IronManEnergy.addEnergy(player, suitId, -amount * ENERGY_COST_SHARE_INTEGRITY_FAILED * energyMult);
		}
		return reduce(player, source, amount, integrityOk ? PLAYER_SHARE_INTEGRITY_OK : PLAYER_SHARE_INTEGRITY_FAILED);
	}

	/**
	 * Is this hit coming from the 180-degree arc the player is facing? Used to gate the Repulsor Shield
	 * ("changes 13" -- it only covers the front). A hit with no locatable source position (starvation,
	 * magic, drowning, ...) counts as frontal so the shield still helps against it.
	 */
	private static boolean isFrontal(ServerPlayer player, DamageSource source) {
		net.minecraft.world.phys.Vec3 sourcePos = source.getSourcePosition();
		if (sourcePos == null) {
			return true;
		}
		net.minecraft.world.phys.Vec3 toSource = sourcePos.subtract(player.getEyePosition());
		if (toSource.lengthSqr() < 1.0E-6) {
			return true;
		}
		return player.getLookAngle().dot(toSource.normalize()) > 0.0;
	}

	private static String suitOfPiece(ServerPlayer player, EquipmentSlot slot) {
		return player.getItemBySlot(slot).getItem() instanceof com.herocraft.mod.ironman.item.IronManArmorItem p
				? p.suitId() : null;
	}

	private static boolean reduce(ServerPlayer player, DamageSource source, float amount, float factor) {
		float reduced = amount * factor;
		if (reduced < 0.5f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, reduced);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}
}

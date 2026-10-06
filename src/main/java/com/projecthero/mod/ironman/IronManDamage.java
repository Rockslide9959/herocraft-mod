package com.projecthero.mod.ironman;

import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * Iron Man suit damage rules + suit integrity.
 *
 * <p><b>v0.15.3 rework (explicit user request):</b> the suit no longer soaks any part of a hit through its integrity.
 * The wearer takes every hit in full (vanilla armour points / Resistance still apply as for any armour), and the suit's
 * integrity separately loses {@link IronManEnergy#INTEGRITY_PER_DAMAGE 75%} of the damage the wearer actually took --
 * 10 damage taken = 7.5 integrity lost, the player still loses the full 10. That bleed happens in
 * {@link #onDamageTaken} off Fabric's {@code AFTER_DAMAGE}, so it is always the real, landed amount. The old 50/50
 * and 90/10 splits and the per-hit energy cost of "running the mitigation" are gone with it. At zero integrity the
 * wearer is still Slowed + Weakened ({@link IronManSuitTicker}).
 *
 * <p>Special rules kept as they were: no fall damage in any Iron Man piece, bulletproof (gunfire does nothing with
 * the chestplate on), the Mark III-line Energy Shield, the Repulsor Shield's 90% frontal block, arrows + fire do
 * nothing to a powered Mark 1-5, and fire / lava only wear integrity at {@value #FIRE_INTEGRITY_MULTIPLIER} of the
 * normal rate (heat, not impact). New in v0.15.3: the wearer is immune to all damage while a suit is assembling onto
 * them ({@link #suitUpImmune}).
 *
 * <p>The Repulsor Shield still uses the cancel-and-re-apply-smaller pattern of {@code HeroDamageRules}, because
 * Fabric's {@code ALLOW_DAMAGE} is a boolean veto with no "reduce amount".
 */
public final class IronManDamage {
	/**
	 * "changes 22": burning is <b>heat</b>, not impact, and the suit is a sealed heat-shielded shell -- so fire / lava /
	 * hot floors only bleed integrity at this multiple of the normal 75%-of-damage rate.
	 */
	public static final float FIRE_INTEGRITY_MULTIPLIER = 0.05f;

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private IronManDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(IronManDamage::onAllowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player) {
				onDamageTaken(player, source, taken);
			}
		});
		IronManCombo.initialize(); // v0.14.29 agent F: repulsor -> melee combo
		com.projecthero.mod.ironman.ability.IronManMark6.initialize(); // v0.14.29 (agent C): Arc Reactor Surge damage boost
	}

	/** v0.14.27: wearing any Iron Man piece = no fall damage (and no suit cost for the fall). */
	public static boolean fallImmune(net.minecraft.world.entity.player.Player player) {
		return IronManArmor.wearingAnyIronMan(player);
	}

	/**
	 * v0.15.3, explicit user request: while any suit is assembling onto the player -- the piece-by-piece C suit-up, the
	 * Mark 5 suitcase, a called suit's flying pieces, the Mark 7 delivery pod, the Suit Platform's robotic-arm deploy --
	 * nothing hurts them. Every one of those paths holds {@code IronManSuitUpManager#assembling} for exactly as long as
	 * it runs, so the immunity ends the tick the suit comes online (or the suit-up is cancelled). Suit-downs don't count.
	 */
	public static boolean suitUpImmune(ServerPlayer player) {
		return TonyStark.hasPower(player) && com.projecthero.mod.ironman.suit.IronManSuitUpManager.assembling(player);
	}

	/** Fabric {@code ALLOW_DAMAGE}: false cancels the hit. Public for the gametests (mock players never fire it). */
	public static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
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
		// v0.15.3: nothing lands while a suit is building itself onto you
		if (suitUpImmune(player)) {
			return false;
		}

		// v0.14.27, explicit user request: any Iron Man armour makes the wearer immune to fall damage -- every mark
		// (Mark 1's old 20% fallDamageFraction no longer applies), and a fall never costs the suit energy or integrity.
		if (source.is(DamageTypeTags.IS_FALL) && fallImmune(player)) {
			player.resetFallDistance();
			return false;
		}

		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);

		// v0.14.31, explicit user request: Iron Man armour is bulletproof -- gunfire does nothing to a wearer whose
		// chestplate is on (powered or not), and never touches the suit's integrity or energy
		if (suit != null && IronManArmor.hasChestplate(player, suitId) && com.projecthero.mod.firearm.Gunfire.active()) {
			return false;
		}

		// v0.14.27 (agent D): the Mark III Energy Shield (Sneak+V) blocks every hit outright, 10% paid in energy.
		if (suit != null && com.projecthero.mod.ironman.ability.IronManMark3.absorb(player, suitId, source, amount)) {
			return false;
		}

		// Repulsor Shield (slot 2 / G): a deployed frontal shield blocks 90% of any hit that lands in the
		// 180-degree arc in front of the player ("changes 13") while it holds, even without the full suit
		// -- it only needs the chestplate the ability itself requires. The 10% that gets through then wears
		// integrity like any other landed hit (onDamageTaken).
		if (suit != null && IronManArmor.hasChestplate(player, suitId)
				&& com.projecthero.mod.ironman.ability.IronManAbilities.barrierActive(player)
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				&& (suit.fullBodyShield() || isFrontal(player, source))) {
			IronManEnergy.addEnergy(player, suitId, -Math.min(400f, amount * 12f));
			return reduce(player, source,
					amount * com.projecthero.mod.ironman.ability.IronManAbilities.BARRIER_DAMAGE_MULT, 1.0f);
		}

		// v0.14.27 (Marks 1 - 5): arrows and fire do nothing at all while the powered chestplate is on.
		if (suit != null && IronManArmor.hasChestplate(player, suitId) && IronManEnergy.energy(player, suitId) > 0f
				&& suit.arrowFireImmune() && isArrowOrFire(source)) {
			return false;
		}
		// v0.15.3: everything else lands in full -- the suit no longer absorbs any share of it
		return true;
	}

	/**
	 * v0.15.3, explicit user request: a hit has landed on the player ({@code AFTER_DAMAGE}, {@code taken} = the damage
	 * that actually got through). With an Iron Man chestplate on, the suit loses {@link IronManEnergy#INTEGRITY_PER_DAMAGE}
	 * of it as integrity (fire at {@link #FIRE_INTEGRITY_MULTIPLIER} of that), stored as an exact float so fractions
	 * accumulate; any landed hit also seals an open faceplate. Public so the gametests can drive it (mock players never
	 * fire the damage events).
	 */
	public static void onDamageTaken(ServerPlayer player, DamageSource source, float taken) {
		if (taken <= 0f || !TonyStark.hasPower(player)) {
			return;
		}
		if (IronManArmor.wearingAnyIronMan(player)) {
			IronManFaceplate.autoClose(player);
		}
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null || IronManSuits.byId(suitId) == null || !IronManArmor.hasChestplate(player, suitId)) {
			return;
		}
		float before = IronManEnergy.integrity(player, suitId);
		if (before <= 0f) {
			return;
		}
		float bleed = taken * IronManEnergy.INTEGRITY_PER_DAMAGE
				* (source.is(DamageTypeTags.IS_FIRE) ? FIRE_INTEGRITY_MULTIPLIER : 1.0f);
		IronManEnergy.damageIntegrity(player, suitId, bleed);
		if (IronManEnergy.integrity(player, suitId) <= 0f) {
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					IronManSounds.POWER_FAIL, SoundSource.PLAYERS, 1.0f, 1.0f);
			player.displayClientMessage(net.minecraft.network.chat.Component
					.translatable("message.projecthero.ironman.integrity_failed")
					.withStyle(net.minecraft.ChatFormatting.RED), true);
		}
	}

	/** v0.14.27: an arrow (anything shot as an {@code AbstractArrow}) or any fire / lava / hot-floor source. */
	public static boolean isArrowOrFire(DamageSource source) {
		return source.is(DamageTypeTags.IS_FIRE)
				|| source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow;
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

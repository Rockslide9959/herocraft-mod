package com.projecthero.mod.silversurfer;

import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What the Power Cosmic does to damage (v0.17.1).
 * <ul>
 *   <li>The Surfer takes no fall, fire / lava, drowning or suffocation damage, and nothing at all while phased.</li>
 *   <li>The Cosmic Barrier soaks every hit from the front until its 40 points run out (projectiles are reflected by
 *       {@link SilverSurferAbilities} before they arrive).</li>
 *   <li>A silver statue (Transmutation) can't hurt anyone, and the next hit on it shatters it for
 *       {@link SilverSurferConfig#SHATTER_BONUS} more.</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} is a veto, so a change cancels the hit and re-issues the new amount behind a re-entrancy
 * guard.
 */
public final class SilverSurferDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private SilverSurferDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SilverSurferDamage::allowDamage);
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0.0f) {
			return true;
		}
		boolean bypass = source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		// a silver statue can't strike back
		if (!bypass && source.getEntity() instanceof LivingEntity attacker && SilverSurferStatues.isStatue(attacker)) {
			return false;
		}
		// the next hit on a statue shatters it
		if (!bypass && SilverSurferStatues.isStatue(entity) && SilverSurferStatues.shatter(entity)) {
			reissue(entity, source, amount + SilverSurferConfig.SHATTER_BONUS);
			return false;
		}
		if (!(entity instanceof ServerPlayer player) || !SilverSurfer.hasPower(player) || bypass) {
			return true;
		}
		if (SilverSurfer.phasing(player)) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			player.resetFallDistance();
			return false;
		}
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL)
				|| source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.STARVE)) {
			if (source.is(DamageTypeTags.IS_FIRE)) {
				player.clearFire();
			}
			return false;
		}
		// v0.17.1 (user): the Power Cosmic turns aside 90% of every hit that still reaches him (re-issued below; a hit
		// inside his hit-invulnerability window is dropped, as vanilla would, so the re-issue never bypasses it)
		if (player.invulnerableTime > 10) {
			return false;
		}
		float taken = amount * SilverSurferConfig.DAMAGE_TAKEN;
		if (SilverSurfer.barrierUp(player) && fromFront(player, source)) {
			SilverSurferState s = SilverSurfer.state(player);
			float soak = Math.min(s.barrierHp, taken);
			SilverSurferState n = s.copy();
			n.barrierHp = s.barrierHp - soak;
			if (n.barrierHp <= 0.01f) {
				n.barrierUntil = 0L;
				n.barrierHp = 0f;
				n.abilityReadyAt.put(SilverSurferAbilities.BARRIER, player.level().getGameTime() + SilverSurferConfig.BARRIER_BREAK_COOLDOWN);
			}
			SilverSurfer.save(player, n);
			ServerLevel level = (ServerLevel) player.level();
			Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(1.2));
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y - 0.3, at.z, 10, 0.5, 0.5, 0.5, 0.06);
			level.playSound(null, at.x, at.y, at.z, n.barrierHp <= 0f ? SoundEvents.SHIELD_BREAK : SoundEvents.AMETHYST_BLOCK_HIT,
					SoundSource.PLAYERS, 1.0f, 1.6f);
			float rest = taken - soak;
			if (rest > 0.05f) {
				reissue(player, source, rest);
			}
			return false;
		}
		reissue(player, source, taken);
		return false;
	}

	/** Did the hit come from in front of him? */
	static boolean fromFront(ServerPlayer player, DamageSource source) {
		Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		Vec3 pos = from != null ? from.position() : source.getSourcePosition();
		if (pos == null) {
			return false;
		}
		return inFront(player, pos);
	}

	static boolean inFront(ServerPlayer player, Vec3 pos) {
		Vec3 to = pos.subtract(player.position());
		Vec3 flat = new Vec3(to.x, 0, to.z);
		if (flat.lengthSqr() < 1.0e-4) {
			return true;
		}
		Vec3 look = player.getLookAngle();
		Vec3 lookFlat = new Vec3(look.x, 0, look.z);
		if (lookFlat.lengthSqr() < 1.0e-4) {
			return true;
		}
		return flat.normalize().dot(lookFlat.normalize()) >= SilverSurferConfig.BARRIER_ARC;
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer && source.scalesWithDifficulty()) {
			amount = switch (target.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			int inv = target.invulnerableTime;
			target.invulnerableTime = 0;
			if (!target.hurt(source, amount)) {
				target.invulnerableTime = inv;
			}
		} finally {
			REENTRANT.set(false);
		}
	}
}

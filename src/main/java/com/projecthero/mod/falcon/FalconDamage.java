package com.projecthero.mod.falcon;

import com.projecthero.mod.falcon.data.FalconState;
import com.projecthero.mod.falcon.item.FalconItems;

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
 * What hurts the Falcon (v0.17.1).
 * <ul>
 *   <li>No fall damage and no flying-into-walls damage while geared (the full set on).</li>
 *   <li>The Barrel Roll's brief invulnerability.</li>
 *   <li>The Wing Shield soaks every hit and projectile from the front until its 40 points run out.</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} is a veto, so a partial soak cancels the hit and re-issues the rest behind a re-entrancy guard
 * (the difficulty scaling vanilla applied before the veto is undone first, so it is not applied twice).
 */
public final class FalconDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private FalconDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(FalconDamage::allowDamage);
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0.0f || !(entity instanceof ServerPlayer player)) {
			return true;
		}
		boolean power = Falcon.hasPower(player);
		if (!power && !FalconItems.fullSet(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		// no fall damage in the gear (and gliding into a wall doesn't hurt either)
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			player.resetFallDistance();
			return false;
		}
		if (!power) {
			return true;
		}
		if (Falcon.rolling(player)) {
			ServerLevel level = (ServerLevel) player.level();
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 4, 0.4, 0.4, 0.4, 0.05);
			return false;
		}
		if (Falcon.shieldUp(player) && fromFront(player, source)) {
			FalconState s = Falcon.state(player);
			float soak = Math.min(s.shieldHp, amount);
			FalconState n = s.copy();
			n.shieldHp = s.shieldHp - soak;
			Falcon.save(player, n);
			ServerLevel level = (ServerLevel) player.level();
			Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.8));
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y - 0.3, at.z, 8, 0.4, 0.4, 0.4, 0.15);
			level.playSound(null, at.x, at.y, at.z, n.shieldHp <= 0f ? SoundEvents.SHIELD_BREAK : SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f,
					1.2f);
			float rest = amount - soak;
			if (rest > 0.05f) {
				reissue(player, source, rest);
			}
			return false;
		}
		return true;
	}

	/** Did the hit come from in front of him (with no direction at all, never)? */
	private static boolean fromFront(ServerPlayer player, DamageSource source) {
		Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		Vec3 pos = from != null ? from.position() : source.getSourcePosition();
		if (pos == null) {
			return false;
		}
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
		return flat.normalize().dot(lookFlat.normalize()) >= FalconConfig.SHIELD_ARC;
	}

	private static void reissue(ServerPlayer player, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			amount = switch (player.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}

package com.projecthero.mod.cyborg;

import com.projecthero.mod.cyborg.data.CyborgState;

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
 * What hurts Cyborg (v0.16.5).
 * <ul>
 *   <li>Lightning: in combat form it fills the Power Core and does nothing else; in civilian form it shocks him double.</li>
 *   <li>Combat form: no drowning, half fall damage.</li>
 *   <li>The Defense Matrix soaks every hit (and projectile) from the front until its 30 points run out.</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} is a veto, so a change cancels the hit and re-issues the new amount behind a re-entrancy
 * guard (the difficulty scaling vanilla applied before the veto is undone first, so it is not applied twice).
 */
public final class CyborgDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private CyborgDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CyborgDamage::allowDamage);
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		// a machine Cyborg hacked never hurts him or his squad
		if (!REENTRANT.get() && CyborgAbilities.blocksHackedHit(entity, source.getEntity())) {
			return false;
		}
		if (REENTRANT.get() || amount <= 0.0f || !(entity instanceof ServerPlayer player) || !Cyborg.hasPower(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		boolean combat = Cyborg.combat(player);
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			if (Cyborg.lightning(player)) {
				return false;
			}
			reissue(player, source, amount * CyborgConfig.CIVILIAN_SHOCK);
			return false;
		}
		if (!combat) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_DROWNING)) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			float half = amount * CyborgConfig.FALL_FACTOR;
			if (half < 0.5f) {
				return false;
			}
			reissue(player, source, half);
			return false;
		}
		if (Cyborg.matrixUp(player) && fromFront(player, source)) {
			CyborgState s = Cyborg.state(player);
			float soak = Math.min(s.shieldHp, amount);
			CyborgState n = s.copy();
			n.shieldHp = s.shieldHp - soak;
			if (n.shieldHp <= 0.01f) {
				n.shieldUntil = 0L;
				n.shieldHp = 0f;
			}
			Cyborg.save(player, n);
			ServerLevel level = (ServerLevel) player.level();
			Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(1.0));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y - 0.3, at.z, 10, 0.4, 0.4, 0.4, 0.1);
			level.playSound(null, at.x, at.y, at.z, n.shieldHp <= 0f ? SoundEvents.SHIELD_BREAK : SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f,
					1.5f);
			float rest = amount - soak;
			if (rest > 0.05f) {
				reissue(player, source, rest);
			}
			return false;
		}
		return true;
	}

	/** Did the hit come from in front of him (the attacker / projectile, or with no direction at all, never)? */
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
		return flat.normalize().dot(lookFlat.normalize()) >= CyborgConfig.MATRIX_ARC;
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

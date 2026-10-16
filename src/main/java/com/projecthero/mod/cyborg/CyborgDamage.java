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
 *   <li>Lightning fills the Power Core and does nothing else.</li>
 *   <li>No fall damage, no drowning (he is always Cyborg; the hologram is only a disguise).</li>
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
		// the Arm Saw (Shift+G): a melee hit knocks a raised shield aside, then lands
		if (!REENTRANT.get() && source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
				&& entity instanceof net.minecraft.world.entity.player.Player victim && victim.isBlocking()
				&& Cyborg.hasPower(attacker) && Cyborg.state(attacker).fx.sawUntil > attacker.level().getGameTime()) {
			victim.getCooldowns().addCooldown(net.minecraft.world.item.Items.SHIELD, 100);
			victim.stopUsingItem();
			victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 0.9f);
		}
		if (REENTRANT.get() || amount <= 0.0f || !(entity instanceof ServerPlayer player) || !Cyborg.hasPower(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		// v0.16.5 (user): Cyborg takes no fall damage at all
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			player.resetFallDistance();
			return false;
		}
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			Cyborg.lightning(player);
			return false;
		}
		if (source.is(DamageTypeTags.IS_DROWNING)) {
			return false;
		}
		if (Cyborg.matrixUp(player) && fromFront(player, source)) {
			CyborgState s = Cyborg.state(player);
			float soak = Math.min(s.shieldHp, amount);
			CyborgState n = s.copy();
			n.shieldHp = s.shieldHp - soak;
			if (n.shieldHp <= 0.01f) {
				// run dry: it breaks, and takes longer to come back than letting go
				n.shieldUntil = 0L;
				n.shieldHp = 0f;
				n.abilityReadyAt.put(CyborgAbilities.MATRIX, player.level().getGameTime() + CyborgConfig.MATRIX_BREAK_COOLDOWN);
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

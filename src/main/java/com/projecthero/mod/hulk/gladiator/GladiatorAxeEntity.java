package com.projecthero.mod.hulk.gladiator;

import com.projecthero.mod.hulk.HulkConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.3: C -- Axe Throw. The Gladiator's axe spins out along his look for {@code axeThrowRange} blocks, cutting
 * through everything on the way ({@code axeThrowDamage} each), turns round at the range limit or the first wall, and
 * flies back to his left hand, cutting again on the way home.
 */
public class GladiatorAxeEntity extends GladiatorThrownWeapon {
	/** Out longer than this and it simply comes home. */
	public static final int MAX_OUT_TICKS = 60;

	private double flown;

	public GladiatorAxeEntity(EntityType<? extends GladiatorAxeEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public boolean isAxe() {
		return true;
	}

	@Override
	protected void serverTick(ServerLevel level, ServerPlayer player) {
		if (age % 4 == 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.4f);
		}
		level.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		if (phase() == PHASE_RETURN) {
			flyHome(level, player);
			return;
		}
		Vec3 next = position().add(velocity);
		if (!ticking(level, next) || age > MAX_OUT_TICKS) {
			startReturn();
			return;
		}
		BlockHitResult wall = clip(next);
		Vec3 end = wall == null ? next : wall.getLocation().subtract(velocity.normalize().scale(0.3));
		sweepHits(player, position(), end, outHit);
		setPos(end);
		flown += velocity.length();
		if (wall != null) {
			level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 10, 0.2, 0.2, 0.2, 0.3);
			level.playSound(null, end.x, end.y, end.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.6f);
			startReturn();
		} else if (flown >= HulkConfig.gladiator().axeThrowRange) {
			startReturn();
		}
	}

	@Override
	protected void onStruck(ServerLevel level, LivingEntity e) {
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.6, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.1);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.7f);
	}
}

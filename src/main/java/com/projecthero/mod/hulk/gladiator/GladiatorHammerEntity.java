package com.projecthero.mod.hulk.gladiator;

import com.projecthero.mod.hulk.HulkCombat;
import com.projecthero.mod.hulk.HulkConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.3: Shift+C -- Hammer Hurl. The hammer flies in an arc and lands with a shockwave ({@code hammerHurlDamage} in
 * {@code hammerHurlRadius}), then stays stuck in the ground. Any C calls it home ({@link #recall}): it rips out of the
 * ground and flies back to his right hand, hitting everything on the way ({@code hammerRecallDamage}). Left alone it
 * comes home on its own after {@code hammerStuckTicks}. Hitting a mob mid-flight sets the shockwave off there and the
 * hammer drops to the ground.
 */
public class GladiatorHammerEntity extends GladiatorThrownWeapon {
	private static final double GRAVITY = 0.06;
	/** Flying (out or falling) longer than this: it lands where it is. */
	private static final int MAX_FLIGHT_TICKS = 100;

	private int stuckAt = -1;
	private boolean impacted;

	public GladiatorHammerEntity(EntityType<? extends GladiatorHammerEntity> type, Level level) {
		super(type, level);
	}

	@Override
	public boolean isAxe() {
		return false;
	}

	public boolean stuck() {
		return phase() == PHASE_STUCK;
	}

	/** C while it is away: fly home. */
	public void recall() {
		if (phase() == PHASE_RETURN) {
			return;
		}
		if (level() instanceof ServerLevel level) {
			BlockState ground = level.getBlockState(BlockPos.containing(position()).below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.2, getZ(), 20, 0.4, 0.2, 0.4, 0.15);
			}
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		}
		startReturn();
	}

	@Override
	protected void serverTick(ServerLevel level, ServerPlayer player) {
		switch (phase()) {
			case PHASE_RETURN -> flyHome(level, player);
			case PHASE_STUCK -> {
				if (age - stuckAt > HulkConfig.gladiator().hammerStuckTicks) {
					recall();
				} else if (age % 10 == 0) {
					level.sendParticles(GladiatorAbilities.BRONZE, getX(), getY() + 0.4, getZ(), 1, 0.2, 0.2, 0.2, 0.0);
				}
			}
			default -> fly(level, player);
		}
	}

	private void fly(ServerLevel level, ServerPlayer player) {
		velocity = phase() == PHASE_FALL ? new Vec3(0, Math.max(-1.2, velocity.y - 0.1), 0)
				: velocity.add(0, -GRAVITY, 0).scale(0.99);
		Vec3 next = position().add(velocity);
		if (!ticking(level, next) || age > MAX_FLIGHT_TICKS) {
			land(level, player, position(), !impacted);
			return;
		}
		if (phase() == PHASE_OUT && age % 3 == 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 0.6f);
		}
		BlockHitResult ground = clip(next);
		Vec3 end = ground == null ? next : ground.getLocation().subtract(velocity.normalize().scale(0.15));
		if (phase() == PHASE_OUT && sweepHits(player, position(), end, outHit) > 0) {
			// it hit a mob in the air: the shockwave goes off there and the hammer drops
			shockwave(level, player, end);
			impacted = true;
			setPhase(PHASE_FALL);
			velocity = Vec3.ZERO;
			setPos(end);
			return;
		}
		face(velocity);
		setPos(end);
		if (ground != null) {
			land(level, player, end, !impacted);
		}
	}

	private void land(ServerLevel level, ServerPlayer player, Vec3 at, boolean wave) {
		if (wave) {
			shockwave(level, player, at);
		}
		impacted = true;
		setPhase(PHASE_STUCK);
		stuckAt = age;
		velocity = Vec3.ZERO;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	private void shockwave(ServerLevel level, ServerPlayer player, Vec3 at) {
		HulkConfig.Gladiator cfg = HulkConfig.gladiator();
		HulkCombat.radial(player, at, cfg.hammerHurlRadius, new HulkCombat.Hit(cfg.hammerHurlDamage, 1.4, 0.55), true);
		BlockState ground = level.getBlockState(BlockPos.containing(at).below());
		if (ground.isAir()) {
			ground = Blocks.DIRT.defaultBlockState();
		}
		BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (double r = 1.0; r <= cfg.hammerHurlRadius; r += 1.0) {
			HulkCombat.ring(level, dirt, at.add(0, 0.2, 0), r, (int) (r * 8));
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.5, 0.1, 0.5, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 20, cfg.hammerHurlRadius * 0.5, 0.1, cfg.hammerHurlRadius * 0.5, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4f, 0.7f);
		HulkCombat.shake(level, at, 0.5f, 10, 20.0);
	}

	@Override
	protected void onStruck(ServerLevel level, LivingEntity e) {
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.6, 0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.3);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.6f);
	}
}

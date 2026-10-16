package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shift+Z -- Geyser: the ground under the target bubbles for {@link #TELEGRAPH} ticks, then a column of water erupts
 * there and launches whatever stands on it about {@link WaterHandlers#GEYSER_HEIGHT} blocks straight up (fire put out).
 * The column (this entity; the client draws a churning translucent pillar topped by a spray crown) rises, holds and falls
 * back over {@link #LIFE} ticks. Never water blocks.
 */
public class GeyserEntity extends WaterFxEntity {
	public static final int TELEGRAPH = 5;
	public static final int LIFE = 34;
	public static final double RADIUS = 1.3;
	/** Vertical launch speed that carries a mob ~10 blocks up under vanilla gravity and drag (0.08, 0.98). */
	public static final double LAUNCH_SPEED = 1.35;

	private boolean erupted;

	public GeyserEntity(EntityType<? extends GeyserEntity> type, Level level) {
		super(type, level);
	}

	public static GeyserEntity under(ServerPlayer owner, LivingEntity target, Vec3 at) {
		ServerLevel level = AbilityHelpers.level(owner);
		GeyserEntity e = new GeyserEntity(WaterEntities.GEYSER, level);
		e.setup(owner, target, (float) WaterHandlers.GEYSER_HEIGHT);
		e.setPos(at.x, at.y, at.z);
		level.addFreshEntity(e);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 1.4f, 0.8f);
		return e;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = owner();
		if (owner == null || tickCount > LIFE) {
			discard();
			return;
		}
		if (!erupted && tickCount < TELEGRAPH) {
			// the target still moving: the geyser follows it until it erupts
			LivingEntity t = target();
			if (t != null && t.distanceToSqr(this) < 9.0) {
				setPos(t.getX(), Math.min(getY(), t.getY()), t.getZ());
			}
			level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.1, getZ(), 6, 0.6, 0.05, 0.6, 0.02);
			level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 4, 0.6, 0.05, 0.6, 0.05);
			return;
		}
		if (!erupted) {
			erupt(owner);
		}
		if (tickCount % 3 == 0 && tickCount < LIFE - 8) {
			level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + WaterHandlers.GEYSER_HEIGHT * 0.9, getZ(), 8, 0.6, 0.3, 0.6, 0.2);
		}
	}

	/** The eruption: everything on the column is thrown ~10 blocks up and doused. Returns how many were launched. */
	public int erupt(ServerPlayer owner) {
		erupted = true;
		ServerLevel level = (ServerLevel) level();
		int n = 0;
		AABB box = new AABB(getX() - RADIUS, getY() - 0.5, getZ() - RADIUS, getX() + RADIUS, getY() + 3.0, getZ() + RADIUS);
		for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, le -> le != owner && le.isAlive()
				&& com.projecthero.mod.combat.HeroTargets.canHarm(owner, le) && !com.projecthero.mod.squad.Squads.areAllies(owner, le))) {
			Vec3 v = le.getDeltaMovement();
			le.setDeltaMovement(v.x * 0.2, LAUNCH_SPEED, v.z * 0.2);
			le.hurtMarked = true;
			le.hasImpulse = true;
			le.setOnGround(false);
			le.clearFire();
			n++;
		}
		WaterHandlers.douse(level, BlockPos.containing(getX(), getY() + 0.2, getZ()), 1);
		level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 60, 1.0, 0.2, 1.0, 0.4);
		level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 1.0, getZ(), 20, 0.4, 1.0, 0.4, 0.3);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.6f, 0.6f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.6f, 0.7f);
		return n;
	}

	public boolean erupted() {
		return erupted;
	}
}

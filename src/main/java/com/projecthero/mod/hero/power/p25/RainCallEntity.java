package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * v0.16.6 hold C -- Rain Call: a rain cloud gathers over the caster and rain falls in a {@link WaterHandlers#RAIN_RADIUS}
 * -block circle round the spot for {@link WaterHandlers#RAIN_TICKS} ticks. It is local -- the world's weather is never
 * touched: this entity is the shower, the client draws the cloud ceiling, the falling streaks and the splashes where they
 * land ({@code WaterFxRenderer}) and adds rain particles round the camera, and the server plays the rain sound. While the
 * caster stands in it his Water refills twice as fast and he heals a heart every 5 s (see
 * {@link WaterHandlers#inRainCall}). The synced size is the radius.
 */
public class RainCallEntity extends WaterFxEntity {
	/** How far over the spot the cloud hangs. */
	public static final double CLOUD_HEIGHT = 9.0;

	public RainCallEntity(EntityType<? extends RainCallEntity> type, Level level) {
		super(type, level);
	}

	public static RainCallEntity gather(ServerPlayer owner) {
		ServerLevel level = AbilityHelpers.level(owner);
		RainCallEntity e = new RainCallEntity(WaterEntities.RAIN_CALL, level);
		e.setup(owner, null, (float) WaterHandlers.RAIN_RADIUS);
		e.setPos(owner.getX(), owner.getY(), owner.getZ());
		level.addFreshEntity(e);
		return e;
	}

	/** 0..1 how gathered the cloud is (it builds over 2 s and thins out over the last 3). Either side. */
	public float strength(float partial) {
		float t = tickCount + partial;
		return Math.max(0f, Math.min(1f, Math.min(t / 40f, (WaterHandlers.RAIN_TICKS - t) / 60f)));
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			// the drops landing: rain splashes on the ground under the cloud
			if (strength(0) > 0.3f) {
				for (int i = 0; i < 6; i++) {
					double a = random.nextDouble() * Math.PI * 2;
					double r = Math.sqrt(random.nextDouble()) * size();
					double x = getX() + Math.cos(a) * r;
					double z = getZ() + Math.sin(a) * r;
					int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
					level().addParticle(ParticleTypes.RAIN, x, y + 0.05, z, 0, 0, 0);
				}
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = owner();
		if (owner == null || tickCount > WaterHandlers.RAIN_TICKS || !WaterHandlers.rainCalled(owner)) {
			discard();
			return;
		}
		if (strength(0) > 0.3f && tickCount % 6 == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(random.nextDouble()) * size();
			double x = getX() + Math.cos(a) * r;
			double z = getZ() + Math.sin(a) * r;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
			level.playSound(null, x, y, z, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.55f, 0.9f + random.nextFloat() * 0.2f);
			level.sendParticles(ParticleTypes.SPLASH, x, y + 0.05, z, 6, 1.5, 0.0, 1.5, 0.0);
		}
		// the rain puts out fire under it, a patch at a time
		if (tickCount % 10 == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(random.nextDouble()) * size();
			int x = (int) Math.floor(getX() + Math.cos(a) * r);
			int z = (int) Math.floor(getZ() + Math.sin(a) * r);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			WaterHandlers.douse(level, new net.minecraft.core.BlockPos(x, y, z), 2);
		}
	}
}

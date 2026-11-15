package com.projecthero.mod.gear;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the Flashbang going off. Every mob within {@link #RADIUS} is blinded, loses its target and is stunned
 * (Slowness VII + Weakness V, so it can barely move or hurt anything) for a moment. Players are never affected.
 */
public final class Flashbang {
	public static final double RADIUS = 5.0;
	public static final double UPGRADED_RADIUS = 8.0;
	public static final int BLIND_TICKS = 100;
	public static final int UPGRADED_BLIND_TICKS = 160;
	public static final int STUN_TICKS = 40;
	public static final int UPGRADED_STUN_TICKS = 70;

	private Flashbang() {
	}

	/** Returns how many mobs were caught. */
	public static int detonate(Level level, Vec3 at, LivingEntity owner, boolean upgraded) {
		double r = upgraded ? UPGRADED_RADIUS : RADIUS;
		int blind = upgraded ? UPGRADED_BLIND_TICKS : BLIND_TICKS;
		int stun = upgraded ? UPGRADED_STUN_TICKS : STUN_TICKS;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 1.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.9f);
		if (level instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
			sl.sendParticles(ParticleTypes.FIREWORK, at.x, at.y + 0.4, at.z, 40, 0.2, 0.2, 0.2, 0.25);
			sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.4, at.z, 16, 0.3, 0.3, 0.3, 0.15);
		}
		int n = 0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(r), m -> m.isAlive() && m.distanceToSqr(at) <= r * r)) {
			SmokeCloudEntity.blind(mob, blind);
			int s = SmokeCloudEntity.resists(mob) ? stun / 3 : stun;
			mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, s, 6, false, true));
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, s, 4, false, true));
			mob.getNavigation().stop();
			n++;
		}
		return n;
	}
}

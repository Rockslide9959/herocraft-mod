package com.projecthero.mod.event.boss.power;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Cryokinesis: control rather than damage. A frost cone in front of it heavily slows whoever is caught,
 * and a frost pulse chills everyone nearby.
 *
 * <p>The cone is a facing check, so walking around behind it beats it entirely -- which is exactly the
 * counterplay a slow-heavy boss needs to have, since a slow that cannot be avoided is just a stat
 * check. Damage is low on purpose: this boss makes the <em>rest</em> of the wave dangerous.
 */
public class CryokinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_09_cryokinesis";

	private static final int SLOT_CONE = 0;
	private static final int SLOT_PULSE = 1;
	private static final double CONE_RANGE = 10.0;
	/** Cosine of the cone's half-angle: about 50 degrees to each side. */
	private static final double CONE_COS = 0.64;

	public CryokinesisBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 7.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.SNOWFLAKE;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		Vec3 anchor = areaAnchor(level, target, 6.0, 3);
		if (anchor != null && ready(SLOT_PULSE) && freshChoice(SLOT_PULSE)) {
			pulse(level);
			startCooldown(SLOT_PULSE, 240);
			return;
		}
		if (boss.distanceTo(target) < CONE_RANGE && ready(SLOT_CONE)) {
			cone(level);
			startCooldown(SLOT_CONE, 130);
		}
	}

	private void cone(ServerLevel level) {
		Vec3 look = boss.getLookAngle();
		sound(level, SoundEvents.PLAYER_HURT_FREEZE, 1.0f, 0.7f);
		for (int step = 1; step <= 8; step++) {
			Vec3 p = boss.getEyePosition().add(look.scale(step * 1.2));
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 6, 0.5, 0.4, 0.5, 0.02);
		}
		for (Player player : playersNear(level, CONE_RANGE)) {
			Vec3 to = player.position().subtract(boss.position());
			if (to.lengthSqr() < 1.0e-4 || to.normalize().dot(look) < CONE_COS) {
				continue; // outside the cone -- walking around it works
			}
			hurt(player, 4.0f);
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1));
			player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze(), player.getTicksFrozen() + 80));
		}
	}

	private void pulse(ServerLevel level) {
		sound(level, SoundEvents.GLASS_BREAK, 1.0f, 0.6f);
		level.sendParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + 1.0, boss.getZ(),
				50, 2.5, 1.0, 2.5, 0.05);
		for (Player player : playersNear(level, 7.0)) {
			hurt(player, 3.0f);
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
		}
	}
}

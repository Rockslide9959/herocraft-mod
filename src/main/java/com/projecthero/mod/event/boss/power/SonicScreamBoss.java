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
 * Sonic Scream: a directional shout that damages, pushes and disorients everyone in front of it, plus
 * a short disarming shriek at point blank.
 *
 * <p>Both are cone-shaped, so the counterplay is to not be in front of it. The disorientation is
 * Nausea and a brief Blindness rather than anything that removes control -- being unable to see for a
 * moment is unpleasant but recoverable, whereas a stun on a boss in a twelve-wave raid is not.
 */
public class SonicScreamBoss extends BossPowerController {
	public static final String POWER_KEY = "power_14_sonic_scream";

	private static final int SLOT_SCREAM = 0;
	private static final int SLOT_SHRIEK = 1;
	private static final double SCREAM_RANGE = 14.0;
	private static final double CONE_COS = 0.6;

	public SonicScreamBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 8.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.SONIC_BOOM;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PINK;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double distance = boss.distanceTo(target);
		if (distance < 5.0 && ready(SLOT_SHRIEK) && freshChoice(SLOT_SHRIEK)) {
			shriek(level);
			startCooldown(SLOT_SHRIEK, 180);
			return;
		}
		if (distance < SCREAM_RANGE && ready(SLOT_SCREAM)) {
			scream(level, target);
			startCooldown(SLOT_SCREAM, 150);
		}
	}

	private void scream(ServerLevel level, LivingEntity target) {
		// Face the target first so the cone matches what the player can see it looking at.
		boss.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		Vec3 look = boss.getLookAngle();
		sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.2f);
		for (int step = 1; step <= 10; step++) {
			Vec3 p = boss.getEyePosition().add(look.scale(step * 1.3));
			level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		for (Player player : playersNear(level, SCREAM_RANGE)) {
			Vec3 to = player.position().subtract(boss.position());
			if (to.lengthSqr() < 1.0e-4 || to.normalize().dot(look) < CONE_COS) {
				continue;
			}
			hurt(player, 7.0f);
			knockAway(player, boss.position(), 1.4, 0.4);
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
		}
	}

	private void shriek(ServerLevel level) {
		sound(level, SoundEvents.WARDEN_ROAR, 1.0f, 1.4f);
		level.sendParticles(ParticleTypes.NOTE, boss.getX(), boss.getY() + 1.5, boss.getZ(),
				30, 1.5, 0.8, 1.5, 0.4);
		for (Player player : playersNear(level, 5.0)) {
			hurt(player, 4.0f);
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
			knockAway(player, boss.position(), 1.0, 0.3);
		}
	}
}

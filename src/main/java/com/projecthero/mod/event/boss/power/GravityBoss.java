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
 * Gravity Manipulation: it moves people rather than hurting them, and it is the roster's specific
 * answer to fliers.
 *
 * <ul>
 *   <li><b>Well</b> -- drags everyone nearby toward a point, bunching a spread-out group up and
 *       pulling ranged players out of position.</li>
 *   <li><b>Crush</b> -- slams airborne targets down and pins them briefly with Slowness. Aimed
 *       squarely at Iron Man, Thor and experimental flight.</li>
 * </ul>
 *
 * <p>Crush deliberately deals no fall damage of its own: it applies downward velocity and lets the
 * game resolve the landing. Falling from a height the player chose to be at is fair; a scripted
 * "you take 20 damage because you were flying" is not. It also never applies Levitation to players,
 * because launching someone helplessly upward in a wave fight is the kind of unavoidable punishment
 * the brief rules out.
 */
public class GravityBoss extends BossPowerController {
	public static final String POWER_KEY = "power_23_gravity_manipulation";

	private static final int SLOT_WELL = 0;
	private static final int SLOT_CRUSH = 1;
	private static final int SLOT_SLAM = 2;

	public GravityBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 9.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.PORTAL;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		boolean airborne = !target.onGround() && target.getY() > boss.getY() + 2.0;
		if (airborne && ready(SLOT_CRUSH)) {
			crush(level, target);
			startCooldown(SLOT_CRUSH, 160);
			return;
		}
		if (ready(SLOT_WELL) && !playersNear(level, 16.0).isEmpty() && freshChoice(SLOT_WELL)) {
			well(level);
			startCooldown(SLOT_WELL, 220);
			return;
		}
		// A grounded target close in gets a localised high-gravity crush: pinned briefly and shoved
		// down. Gives the boss something to do between wells instead of only its long-cooldown pull.
		if (boss.distanceTo(target) < 6.0 && ready(SLOT_SLAM)) {
			groundSlam(level, target);
			startCooldown(SLOT_SLAM, 150);
		}
	}

	private void groundSlam(ServerLevel level, LivingEntity target) {
		sound(level, SoundEvents.ANVIL_LAND, 0.9f, 0.7f);
		for (Player player : playersNear(level, 4.0)) {
			hurt(player, 5.0f);
			player.setDeltaMovement(player.getDeltaMovement().x * 0.4, -0.8, player.getDeltaMovement().z * 0.4);
			player.hurtMarked = true;
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
		}
		particles(level, ParticleTypes.PORTAL, target.position().add(0, 1.0, 0), 24, 0.5);
	}

	private void well(ServerLevel level) {
		Vec3 center = boss.position().add(0, 1.0, 0);
		sound(level, SoundEvents.PORTAL_AMBIENT, 1.2f, 0.5f);
		level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 60, 3.0, 1.5, 3.0, 0.4);
		for (Player player : playersNear(level, 16.0)) {
			Vec3 pull = center.subtract(player.position());
			if (pull.lengthSqr() < 4.0) {
				continue; // already on top of it
			}
			pull = pull.normalize().scale(0.55);
			player.setDeltaMovement(player.getDeltaMovement().add(pull.x, pull.y * 0.4, pull.z));
			player.hurtMarked = true;
		}
	}

	private void crush(ServerLevel level, LivingEntity target) {
		sound(level, SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
		particleLine(level, ParticleTypes.PORTAL, target.position().add(0, 2.0, 0), target.position(), 3.0);
		target.setDeltaMovement(target.getDeltaMovement().x * 0.3, -1.6, target.getDeltaMovement().z * 0.3);
		target.hurtMarked = true;
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
		hurt(target, 5.0f);
	}
}

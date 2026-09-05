package com.herocraft.mod.event.boss.power;

import com.herocraft.mod.event.boss.BossPowerController;
import com.herocraft.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Super Strength: a brawler that refuses to let anyone stay away from it.
 *
 * <ul>
 *   <li><b>Leap</b> -- launches itself at a distant target on a visible arc. This is its answer to
 *       ranged attackers, and the reason a Super Strength boss cannot be kited.</li>
 *   <li><b>Ground slam</b> -- when players bunch up around it, a radial shockwave that damages and
 *       throws everyone in the ring. Preferred over the leap whenever a cluster exists.</li>
 *   <li><b>Haymaker</b> -- a heavier melee blow with a big knockback, on its own cooldown.</li>
 * </ul>
 *
 * <p>Every one of those is positional and telegraphed by the boss physically moving or winding up,
 * so all three are avoidable by moving.
 */
public class SuperStrengthBoss extends BossPowerController {
	public static final String POWER_KEY = "power_01_super_strength";

	private static final int SLOT_LEAP = 0;
	private static final int SLOT_SLAM = 1;
	private static final int SLOT_HAYMAKER = 2;

	public SuperStrengthBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 2.5; // it wants to be in your face
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.CRIT;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double distance = boss.distanceTo(target);

		// Ground slam whenever anyone is in reach of the ring -- a lone target counts, so a melee
		// fighter sees the slam and the haymaker trade off instead of only ever the haymaker.
		Vec3 anchor = areaAnchor(level, target, 5.5, 2);
		if (anchor != null && ready(SLOT_SLAM) && freshChoice(SLOT_SLAM)) {
			slam(level, anchor);
			startCooldown(SLOT_SLAM, 200);
			return;
		}
		if (distance > 8.0 && distance < 26.0 && ready(SLOT_LEAP) && boss.hasLineOfSight(target)) {
			leap(level, target);
			startCooldown(SLOT_LEAP, 160);
			return;
		}
		if (distance <= 4.5 && ready(SLOT_HAYMAKER)) {
			haymaker(level, target);
			startCooldown(SLOT_HAYMAKER, 120);
		}
	}

	private void leap(ServerLevel level, LivingEntity target) {
		Vec3 to = target.position().subtract(boss.position());
		double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
		Vec3 launch = new Vec3(to.x, 0.0, to.z).normalize().scale(Math.min(1.4, 0.35 + horizontal * 0.05));
		boss.setDeltaMovement(launch.x, 0.62, launch.z);
		boss.hasImpulse = true;
		sound(level, SoundEvents.RAVAGER_ROAR, 1.1f, 0.8f);
		particles(level, ParticleTypes.CLOUD, boss.position(), 12, 0.4);
	}

	private void slam(ServerLevel level, Vec3 at) {
		sound(level, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.6f);
		particles(level, ParticleTypes.EXPLOSION, boss.position().add(0, 0.2, 0), 4, 0.6);
		level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.1, boss.getZ(), 40, 3.0, 0.1, 3.0, 0.02);
		for (Player player : playersNear(level, 7.0)) {
			// Damage falls off with distance, so the edge of the ring is survivable and stepping out
			// of it is always the right call.
			double d = player.distanceTo(boss);
			float damage = (float) Math.max(3.0, 12.0 - d * 1.2);
			hurt(player, damage);
			knockAway(player, boss.position(), 1.5, 0.55);
		}
	}

	private void haymaker(ServerLevel level, LivingEntity target) {
		if (hurt(target, 9.0f)) {
			knockAway(target, boss.position(), 2.2, 0.4);
			sound(level, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
			particles(level, ParticleTypes.CRIT, target.position().add(0, target.getBbHeight() * 0.5, 0), 12, 0.3);
		}
	}
}

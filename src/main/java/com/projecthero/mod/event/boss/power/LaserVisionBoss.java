package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.p02.LaserBeams;
import com.projecthero.mod.network.LaserBeamPayload;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Laser Vision (v0.14.5 kit): Heat Vision (a held, ramping beam), Sweeping Arc (150 degrees over half a second),
 * Recoil Blast (blast at a close target, the boss is thrown back) and -- once badly hurt -- Maximum Output, a long,
 * slowly tracking beam with splash bursts that overheats the boss afterwards (a punish window).
 *
 * <p>Every beam is sent as real beam geometry through {@link LaserBeams} so every nearby player sees it (the player
 * renderer's {@link LaserBeamPayload} kinds), on top of a flame particle line. The boss tracks a heat gauge like the
 * player: moves add heat, and at 100 it overheats and cannot fire for 3 s.
 */
public class LaserVisionBoss extends BossPowerController {
	public static final String POWER_KEY = "power_02_laser_vision";

	private static final int HEAT_VISION = 0;
	private static final int SWEEP = 1;
	private static final int RECOIL = 2;
	private static final int MAX_OUTPUT = 3;
	private static final List<String> IDS = List.of("heat_vision", "sweeping_arc", "recoil_blast", "maximum_output");
	private static final double REACH = 40.0;

	private float heat;
	private int overheat;
	/** Held Heat Vision: ticks left and ticks held so far. */
	private int beamTicks;
	private int beamHeld;
	/** Sweeping Arc: ticks left, base yaw (radians) and pitch target height. */
	private int sweepTicks;
	private double sweepBaseYaw;
	private double sweepY;
	/** Maximum Output: charge ticks, then beam ticks, and where the beam currently points. */
	private int maxCharge;
	private int maxTicks;
	private Vec3 maxAim;

	public LaserVisionBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public List<String> abilityIds() {
		return IDS;
	}

	@Override
	public double preferredRange() {
		return 13.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.FLAME;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	private boolean busy() {
		return beamTicks > 0 || sweepTicks > 0 || maxCharge > 0 || maxTicks > 0;
	}

	private void addHeat(ServerLevel level, float amount) {
		heat = Math.min(100.0f, heat + amount);
		if (heat >= 100.0f) {
			overheat = 60;
			heat = 60.0f;
			particles(level, ParticleTypes.LARGE_SMOKE, boss.getEyePosition(), 16, 0.3);
			sound(level, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.6f);
		}
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (busy() || overheat > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		if (d > REACH || !sees(target)) {
			return;
		}
		if (lowHealth() && heat <= 50.0f && ready(MAX_OUTPUT)) {
			maxCharge = 30;
			maxAim = mid(target);
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 5, false, false));
			sound(level, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.8f);
			startCooldown(MAX_OUTPUT, 1100);
			return;
		}
		if (d <= 6.0 && ready(RECOIL) && freshChoice(RECOIL)) {
			recoilBlast(level, target);
			startCooldown(RECOIL, 85);
			return;
		}
		if (d <= 18.0 && ready(SWEEP) && freshChoice(SWEEP)) {
			Vec3 to = target.position().subtract(boss.position());
			sweepBaseYaw = Math.atan2(to.z, to.x);
			sweepY = target.getY() + target.getBbHeight() * 0.5;
			sweepTicks = 10;
			boss.getNavigation().stop();
			sound(level, SoundEvents.BLAZE_SHOOT, 1.0f, 0.8f);
			addHeat(level, 10.0f);
			startCooldown(SWEEP, 170);
			return;
		}
		if (ready(HEAT_VISION)) {
			// a half-second "eyes flare" telegraph, then a 2 s held beam that ramps up like the player's
			particles(level, ParticleTypes.SMALL_FLAME, boss.getEyePosition(), 10, 0.15);
			sound(level, SoundEvents.CONDUIT_ACTIVATE, 0.8f, 1.4f);
			beamTicks = 50;
			beamHeld = 0;
			startCooldown(HEAT_VISION, 110);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (overheat > 0) {
			overheat--;
			if (overheat % 10 == 0) {
				particles(level, ParticleTypes.SMOKE, boss.getEyePosition(), 3, 0.2);
			}
		} else if (!busy() && heat > 0) {
			heat = Math.max(0.0f, heat - 0.15f);
		}

		if (beamTicks > 0) {
			beamTicks--;
			beamHeld++;
			if (target == null || beamHeld <= 10) {
				return; // wind-up
			}
			boss.getNavigation().stop();
			face(target);
			Vec3 from = boss.getEyePosition();
			// tracks with a small lag: aims where the target was a few ticks ago, so strafing beats it
			Vec3 aim = mid(target).subtract(target.getDeltaMovement().scale(4.0));
			Vec3 to = clipEnd(level, from, from.add(aimFromEyes(aim).scale(REACH)));
			if (beamHeld % 3 == 0) {
				LaserBeams.send(level, boss, from, to, LaserBeamPayload.KIND_BEAM, LaserBeamPayload.REFRESH_TICKS);
				particleLine(level, ParticleTypes.FLAME, from, to, 0.8);
			}
			addHeat(level, 1.0f / 20.0f * 2.0f);
			if (beamHeld % 10 == 0) {
				float ramp = 1.0f + Math.min(1.5f, (beamHeld - 10) / 40.0f);
				for (LivingEntity e : strikeLine(level, from, to, 0.6, bossDamage(4.8f) * ramp)) {
					ignite(e, 3);
					particles(level, ParticleTypes.SMALL_FLAME, mid(e), 6, 0.2);
				}
			}
			return;
		}

		if (sweepTicks > 0) {
			float progress = (10 - sweepTicks) / 9.0f;
			sweepTicks--;
			double yaw = sweepBaseYaw + Math.toRadians(-75.0 + 150.0 * progress);
			Vec3 from = boss.getEyePosition();
			Vec3 dir = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
			double flatReach = 22.0;
			Vec3 end = new Vec3(from.x + dir.x * flatReach, sweepY, from.z + dir.z * flatReach);
			Vec3 to = clipEnd(level, from, end);
			LaserBeams.send(level, boss, from, to, LaserBeamPayload.KIND_SWEEP, 2);
			particleLine(level, ParticleTypes.FLAME, from, to, 0.6);
			for (LivingEntity e : strikeLine(level, from, to, 1.0, bossDamage(14.4f))) {
				ignite(e, 3);
			}
			return;
		}

		if (maxCharge > 0) {
			maxCharge--;
			boss.getNavigation().stop();
			if (maxCharge % 4 == 0) {
				sound(level, SoundEvents.BLAZE_AMBIENT, 1.0f, 0.6f);
				particles(level, ParticleTypes.FLAME, boss.getEyePosition(), 6, 0.3);
			}
			if (maxCharge == 0) {
				maxTicks = 100; // the player's runs 200 t; a boss's is half as long
				sound(level, SoundEvents.BLAZE_SHOOT, 1.4f, 0.35f);
				sound(level, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.8f);
			}
			return;
		}

		if (maxTicks > 0) {
			maxTicks--;
			boss.getNavigation().stop();
			if (target != null) {
				// a slow, heavy sweep toward the target: you can outrun it, not out-wait it
				maxAim = maxAim.lerp(mid(target), 0.08);
				face(target);
			}
			Vec3 from = boss.getEyePosition();
			Vec3 to = clipEnd(level, from, from.add(aimFromEyes(maxAim).scale(REACH)));
			LaserBeams.send(level, boss, from, to, LaserBeamPayload.KIND_MAX, LaserBeamPayload.REFRESH_TICKS);
			if (maxTicks % 2 == 0) {
				particleLine(level, ParticleTypes.FLAME, from, to, 0.5);
			}
			if (maxTicks % 10 == 0) {
				for (LivingEntity e : strikeLine(level, from, to, 0.9, bossDamage(14.0f))) {
					ignite(e, 3);
				}
				for (LivingEntity e : strikeArea(level, to, 3.5, bossDamage(12.0f), false, 0.3, 0.1)) {
					ignite(e, 6);
				}
				particles(level, ParticleTypes.EXPLOSION, to, 1, 0.1);
			}
			if (maxTicks % 20 == 0) {
				sound(level, SoundEvents.BEACON_AMBIENT, 1.0f, 1.2f);
			}
			if (maxTicks == 0) {
				heat = 100.0f;
				addHeat(level, 1.0f); // overheats: three seconds where it cannot fire
			}
		}
	}

	private void recoilBlast(ServerLevel level, LivingEntity target) {
		face(target);
		Vec3 from = boss.getEyePosition();
		Vec3 at = clipEnd(level, from, mid(target));
		LaserBeams.send(level, boss, from, at, LaserBeamPayload.KIND_RECOIL, 7);
		for (LivingEntity e : strikeArea(level, at, 2.5, bossDamage(9.6f), false, 1.2, 0.2)) {
			ignite(e, 3);
		}
		particles(level, ParticleTypes.EXPLOSION, at, 1, 0.1);
		sound(level, SoundEvents.FIRECHARGE_USE, 1.0f, 1.0f);
		sound(level, SoundEvents.GENERIC_EXPLODE, 0.7f, 1.3f);
		Vec3 back = flatDirTo(target.position()).scale(-1.3);
		boss.setDeltaMovement(back.x, 0.3, back.z);
		boss.hasImpulse = true;
		boss.hurtMarked = true;
		addHeat(level, 5.0f);
	}
}

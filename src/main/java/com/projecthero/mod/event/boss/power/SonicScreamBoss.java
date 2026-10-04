package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sonic Scream (v0.14.1 kit): Sonic Blast (a short cone), Focused Scream (a telegraphed long line), Resonance (a stun
 * zone where its voice lands) and -- once badly hurt -- the charged Supersonic Scream cone. The "visible sound" rings
 * are the player power's ({@link BatchCFx#ringTrail}); hits apply its static stun (Slowness IV + Nausea).
 */
public class SonicScreamBoss extends BossPowerController {
	public static final String POWER_KEY = "power_14_sonic_scream";

	private static final int BLAST = 0;
	private static final int FOCUSED = 1;
	private static final int RESONANCE = 2;
	private static final int SUPERSONIC = 3;
	private static final List<String> IDS = List.of("sonic_blast", "focused_scream", "resonance", "supersonic_scream");

	private static final ParticleOptions SOUND = BatchCFx.dust(0x9FF6FF, 0.9f);

	public SonicScreamBoss(EmpoweredZombie boss) {
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
		return 8.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.NOTE;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PINK;
	}

	private void staticStun(LivingEntity e) {
		control(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 3);
		control(e, MobEffects.CONFUSION, 30, 0);
	}

	private Vec3 mouth() {
		return boss.getEyePosition().add(boss.getLookAngle().scale(0.6)).subtract(0, 0.15, 0);
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		if (lowHealth() && d < 20.0 && ready(SUPERSONIC)) {
			BatchCFx.flatRing(level, boss.position().add(0, 0.2, 0), 3.0, 32, SOUND, -0.2);
			beginCast(level, target, SUPERSONIC, 1200, 4, SoundEvents.WARDEN_SONIC_CHARGE, SOUND, t -> supersonic(level, t));
			return;
		}
		if (d < 6.0 && ready(BLAST) && freshChoice(BLAST)) {
			face(target);
			Vec3 m = mouth();
			Vec3 look = aimFromEyes(mid(target));
			for (LivingEntity e : victimsInCone(level, m, look, 6.0, 60.0)) {
				hurt(e, bossDamage(12.0f));
				knockAway(e, m, 1.5, 0.3);
				staticStun(e);
			}
			BatchCFx.ringTrail(level, m, look, 5.0, 1.25, 0.3, 0.35, SOUND);
			particles(level, ParticleTypes.SONIC_BOOM, m.add(look.scale(2.5)), 1, 0.0);
			sound(level, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.6f);
			startCooldown(BLAST, 85);
			return;
		}
		if (d < 18.0 && ready(RESONANCE) && freshChoice(RESONANCE)) {
			Vec3 at = target.position();
			BatchCFx.flatRing(level, at.add(0, 0.2, 0), 3.5, 24, SOUND, 0.0);
			sound(level, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
			schedule(1, () -> {
				for (LivingEntity e : victimsAround(level, at.add(0, 0.5, 0), 3.5)) {
					hurt(e, bossDamage(6.0f) + 1.0f);
					control(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 5);
					control(e, MobEffects.CONFUSION, 60, 0);
				}
				for (int i = 1; i <= 3; i++) {
					BatchCFx.flatRing(level, at.add(0, 0.3 * i, 0), i * 1.1, 20, SOUND, 0.1);
				}
				particles(level, ParticleTypes.NOTE, at.add(0, 1, 0), 10, 1.0);
				soundAt(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 1.6f);
			});
			startCooldown(RESONANCE, 85);
			return;
		}
		if (d < 30.0 && sees(target) && ready(FOCUSED)) {
			beginRangedCast(level, target, FOCUSED, 170, SoundEvents.WARDEN_SONIC_CHARGE, SOUND, t -> {
				Vec3 m = mouth();
				Vec3 dir = aimFromEyes(mid(t));
				Vec3 to = clipEnd(level, m, m.add(dir.scale(30.0)));
				for (LivingEntity e : strikeLine(level, m, to, 0.8, bossDamage(19.0f))) {
					knockAway(e, m, 1.0, 0.2);
					control(e, MobEffects.CONFUSION, 80, 0);
					staticStun(e);
				}
				BatchCFx.ringTrail(level, m, dir, m.distanceTo(to), 1.25, 0.35, 0.02, SOUND);
				particles(level, ParticleTypes.SONIC_BOOM, to, 1, 0.0);
				sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.0f);
			});
		}
	}

	private void supersonic(ServerLevel level, LivingEntity target) {
		Vec3 m = mouth();
		Vec3 look = aimFromEyes(mid(target));
		for (LivingEntity e : victimsInCone(level, m, look, 20.0, 53.0)) {
			hurt(e, bossDamage(60.0f) * (float) (1.0 - Math.min(0.55, e.distanceTo(boss) / 20.0)));
			knockAway(e, m, 3.0, 0.5);
			control(e, MobEffects.CONFUSION, 120, 0);
			staticStun(e);
		}
		BatchCFx.ringTrail(level, m, look, 20.0, 1.25, 0.5, 0.3, SOUND);
		for (int i = 2; i < 20; i += 2) {
			Vec3 p = m.add(look.scale(i));
			particles(level, ParticleTypes.SONIC_BOOM, p, 1, 0.0);
		}
		sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.6f);
	}
}

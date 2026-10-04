package com.projecthero.mod.event.boss.power;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.revamp.d.BatchDFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Teleportation (v0.14.1 kit): Blink in on a far target, Bamf Strike (a hop behind the target that chains on to up to
 * two more victims), Swap (trades places with a target that is hanging back) and Escape Blink (out of a heavy hit,
 * with Resistance III). Teleports leave the player power's purple silhouette and puff ({@link BatchDFx}).
 */
public class TeleportationBoss extends BossPowerController {
	public static final String POWER_KEY = "power_11_teleportation";

	private static final int BLINK = 0;
	private static final int BAMF = 1;
	private static final int SWAP = 2;
	private static final int ESCAPE = 3;
	private static final List<String> IDS = List.of("blink", "bamf_strike", "swap", "escape_blink");

	public TeleportationBoss(EmpoweredZombie boss) {
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
		return 3.0;
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
	public boolean compatibleWith(String otherPowerKey) {
		return super.compatibleWith(otherPowerKey) && !otherPowerKey.equals(FlightBoss.POWER_KEY);
	}

	private void depart(ServerLevel level) {
		Vec3 at = boss.position();
		BatchDFx.silhouette(level, at, boss.getYRot(), BatchDFx.TELE_GLOW);
		BatchDFx.puff(level, at);
		particles(level, ParticleTypes.PORTAL, at.add(0, 1, 0), 30, 0.4);
	}

	/** Teleport the boss just behind {@code target} (or beside it). Returns false if nowhere is safe. */
	private boolean behind(ServerLevel level, LivingEntity target) {
		Vec3 look = target.getLookAngle();
		Vec3 back = new Vec3(look.x, 0, look.z);
		back = back.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : back.normalize();
		Vec3 side = new Vec3(-back.z, 0, back.x);
		double dd = 1.2 + target.getBbWidth() / 2.0 + boss.getBbWidth() / 2.0;
		for (Vec3 off : new Vec3[] { back.scale(-dd), side.scale(dd), side.scale(-dd), back.scale(dd) }) {
			Vec3 spot = safeSpotNear(level, target.position().add(off));
			if (spot != null) {
				depart(level);
				blinkTo(level, spot, ParticleTypes.REVERSE_PORTAL, SoundEvents.ENDERMAN_TELEPORT);
				face(target);
				return true;
			}
		}
		return false;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		if (d > 14.0 && d < 40.0 && ready(SWAP) && freshChoice(SWAP)) {
			Vec3 mine = boss.position();
			Vec3 theirs = target.position();
			if (safeSpotNear(level, theirs) != null) {
				BatchDFx.tether(level, mine.add(0, 1, 0), theirs.add(0, 1, 0), BatchDFx.TELE_GLOW, 24);
				BatchDFx.silhouette(level, mine, boss.getYRot(), BatchDFx.TELE_GLOW);
				BatchDFx.silhouette(level, theirs, target.getYRot(), BatchDFx.TELE_GLOW);
				boss.teleportTo(theirs.x, theirs.y, theirs.z);
				if (isVictim(target)) {
					target.teleportTo(mine.x, mine.y, mine.z);
					target.fallDistance = 0.0f;
					control(target, MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
				}
				sound(level, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.8f);
				sound(level, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0f, 1.2f);
				startCooldown(SWAP, 160);
				return;
			}
		}
		if (d < 30.0 && sees(target) && ready(BAMF) && freshChoice(BAMF)) {
			bamf(level, target);
			startCooldown(BAMF, 200);
			return;
		}
		if (d > 9.0 && d < 48.0 && ready(BLINK)) {
			Vec3 toward = flatDirTo(target.position());
			Vec3 spot = safeSpotNear(level, target.position().subtract(toward.scale(3.0)));
			if (spot != null) {
				depart(level);
				blinkTo(level, spot, ParticleTypes.PORTAL, SoundEvents.ENDERMAN_TELEPORT);
				startCooldown(BLINK, 90);
			}
		}
	}

	/** Bamf Strike: behind the target, a hit, then a hop to the next victim within 10 every 6 ticks (3 total). */
	private void bamf(ServerLevel level, LivingEntity first) {
		List<LivingEntity> done = new ArrayList<>();
		LivingEntity[] cur = { first };
		task(level, age -> {
			if (age % 6 != 0) {
				return true;
			}
			LivingEntity t = cur[0];
			if (t == null || !t.isAlive() || done.size() >= 3) {
				return false;
			}
			done.add(t);
			if (behind(level, t)) {
				hurtFresh(t, bossDamage(11.0f));
				knockAway(t, boss.position(), 0.5, 0.1);
				particles(level, ParticleTypes.SWEEP_ATTACK, mid(t), 1, 0.0);
				particles(level, BatchDFx.TELE_SMOKE, mid(t), 8, 0.3);
				sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.3f + 0.1f * done.size());
			}
			LivingEntity next = null;
			double best = 100.0;
			for (LivingEntity e : victimsAround(level, t.position(), 10.0)) {
				double dd = e.distanceToSqr(t);
				if (!done.contains(e) && dd < best) {
					best = dd;
					next = e;
				}
			}
			cur[0] = next;
			return next != null;
		});
	}

	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		if ((amount >= 12.0f || (lowHealth() && amount >= 5.0f)) && readyReactive(ESCAPE) && source.getEntity() != null) {
			Vec3 away = boss.position().subtract(source.getEntity().position());
			away = new Vec3(away.x, 0, away.z);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			for (double dist = 9.5; dist >= 4.0; dist -= 1.5) {
				Vec3 spot = safeSpotNear(level, boss.position().add(away.scale(dist)));
				if (spot != null) {
					depart(level);
					blinkTo(level, spot, ParticleTypes.PORTAL, SoundEvents.ENDERMAN_TELEPORT);
					boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 24, 2, false, true));
					startCooldown(ESCAPE, 200);
					return;
				}
			}
		}
	}
}

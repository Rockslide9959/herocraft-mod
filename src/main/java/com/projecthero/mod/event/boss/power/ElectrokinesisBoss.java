package com.projecthero.mod.event.boss.power;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.p07.ElectrokinesisHandlers;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Electrokinesis (v0.14.1 kit): Electric Bolt, Chain Lightning that prefers charged victims, Overcharge (detonates every
 * victim's static stacks) and Electrical Storm (a marked strike, then four follow-up bolts). It uses the player power's
 * real static-stack system ({@link ElectrokinesisHandlers#addStack}): three stacks stun, and the stacked victims show the
 * same spark rings the player's targets do.
 */
public class ElectrokinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_07_electrokinesis";

	private static final int BOLT = 0;
	private static final int CHAIN = 1;
	private static final int OVERCHARGE = 2;
	private static final int STORM = 3;
	private static final List<String> IDS = List.of("electric_bolt", "chain_lightning", "overcharge", "electrical_storm");

	private static final ParticleOptions BLUE = BatchCFx.dust(0x4FB8FF, 0.8f);

	public ElectrokinesisBoss(EmpoweredZombie boss) {
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
		return 9.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.ELECTRIC_SPARK;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	private void bolt(ServerLevel level, Vec3 a, Vec3 b) {
		BatchCFx.arc(level, a, b, ParticleTypes.ELECTRIC_SPARK, 0.6);
		BatchCFx.arc(level, a, b, BLUE, 0.35);
	}

	/** The player's {@code zap}: base damage x (1 + 0.1 per stack), then one more stack. */
	private void zap(LivingEntity e, float base) {
		if (!isVictim(e)) {
			return;
		}
		int stacks = ElectrokinesisHandlers.stacks(e);
		hurt(e, base * (1.0f + 0.1f * stacks));
		ElectrokinesisHandlers.addStack(null, e, 1);
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		int charged = 0;
		for (LivingEntity e : victimsNear(level, 16.0)) {
			charged += ElectrokinesisHandlers.stacks(e);
		}
		if (charged >= 3 && ready(OVERCHARGE)) {
			overcharge(level);
			startCooldown(OVERCHARGE, 200);
			return;
		}
		if (d < 30.0 && sees(target) && ready(STORM) && (lowHealth() || freshChoice(STORM))) {
			Vec3 mark = target.position();
			BatchCFx.flatRing(level, mark.add(0, 0.2, 0), 5.0, 32, BLUE, 0.0);
			sound(level, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
			beginCast(level, target, STORM, 1100, 3, SoundEvents.LIGHTNING_BOLT_IMPACT, ParticleTypes.ELECTRIC_SPARK,
					t -> storm(level, t.position()));
			return;
		}
		if (d < 20.0 && sees(target) && ready(CHAIN) && freshChoice(CHAIN)) {
			beginRangedCast(level, target, CHAIN, 140, SoundEvents.CONDUIT_ATTACK_TARGET, ParticleTypes.ELECTRIC_SPARK,
					t -> chain(level, t));
			return;
		}
		if (d < 26.0 && sees(target) && ready(BOLT)) {
			face(target);
			Vec3 from = boss.getEyePosition();
			Vec3 to = clipEnd(level, from, from.add(aimFromEyes(lead(target, 2.0)).scale(26.0)));
			bolt(level, from, to);
			for (LivingEntity e : victimsOnSegment(level, from, to, 0.7)) {
				zap(e, bossDamage(12.0f));
			}
			sound(level, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
			startCooldown(BOLT, 40);
		}
	}

	/** Up to 6 links, 11 each (halved), hopping to a stacked victim within 14, else the nearest within 8. */
	private void chain(ServerLevel level, LivingEntity first) {
		List<LivingEntity> hit = new ArrayList<>();
		LivingEntity cur = first;
		Vec3 from = boss.getEyePosition();
		for (int i = 0; i < 6 && cur != null; i++) {
			bolt(level, from, mid(cur));
			zap(cur, bossDamage(11.0f));
			hit.add(cur);
			from = mid(cur);
			LivingEntity next = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : victimsAround(level, from, 14.0)) {
				if (hit.contains(e)) {
					continue;
				}
				boolean stacked = ElectrokinesisHandlers.stacks(e) > 0;
				double dist = e.distanceToSqr(from);
				if (!stacked && dist > 64.0) {
					continue;
				}
				double score = dist - (stacked ? 1000.0 : 0.0);
				if (score < best) {
					best = score;
					next = e;
				}
			}
			cur = next;
		}
		sound(level, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
	}

	private void overcharge(ServerLevel level) {
		for (LivingEntity e : victimsNear(level, 16.0)) {
			int n = ElectrokinesisHandlers.consumeStacks(e);
			if (n <= 0) {
				continue;
			}
			bolt(level, boss.getEyePosition(), mid(e));
			hurt(e, bossDamage(8.0f * n));
			knockAway(e, boss.position(), 0.4 + 0.4 * n, 0.2);
			if (n >= 3) {
				control(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 9);
				control(e, MobEffects.WEAKNESS, 30, 2);
			}
			particles(level, ParticleTypes.ELECTRIC_SPARK, mid(e), 20 + 10 * n, 0.4);
		}
		particles(level, ParticleTypes.FLASH, boss.position().add(0, 1.2, 0), 1, 0.0);
		BatchCFx.flatRing(level, boss.position().add(0, 0.5, 0), 1.2, 24, BLUE, 0.5);
		sound(level, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9f, 1.7f);
	}

	private void storm(ServerLevel level, Vec3 at) {
		strike(level, at, 5.0, bossDamage(36.0f), 2);
		particles(level, ParticleTypes.EXPLOSION_EMITTER, at, 1, 0.0);
		BatchCFx.flatRing(level, at.add(0, 0.2, 0), 1.0, 32, BatchCFx.dust(0x4FB8FF, 1.5f), 0.6);
		sound(level, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0f, 0.8f);
		// four follow-up strikes, one every 12 ticks, on the most-charged victim within 9 of the centre
		task(level, age -> {
			if (age == 0 || age % 12 != 0) {
				return age < 50;
			}
			LivingEntity pick = null;
			int best = -1;
			for (LivingEntity e : victimsAround(level, at, 9.0)) {
				int s = ElectrokinesisHandlers.stacks(e);
				if (s > best) {
					best = s;
					pick = e;
				}
			}
			if (pick != null) {
				strike(level, pick.position(), 1.5, bossDamage(10.0f), 1);
			}
			return age < 48;
		});
	}

	/** A visual-only lightning bolt and the damage under it. */
	private void strike(ServerLevel level, Vec3 at, double radius, float damage, int stacks) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		for (LivingEntity e : victimsAround(level, at, radius)) {
			hurt(e, damage);
			ElectrokinesisHandlers.addStack(null, e, stacks);
		}
		particles(level, ParticleTypes.ELECTRIC_SPARK, at.add(0, 0.5, 0), 40, 1.0);
	}
}

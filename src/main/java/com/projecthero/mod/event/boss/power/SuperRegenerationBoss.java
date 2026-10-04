package com.projecthero.mod.event.boss.power;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.revamp.BatchCFx;
import com.projecthero.mod.hero.mutation.ModMobEffects;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Super Regeneration (v0.14.5: passive only -- no ability keys), as a boss: the three passives. <b>Regen</b> heals it
 * steadily (the player's 2 HP a tick would make a boss unkillable, so 0.5% of max health a second, paused for 2 s after
 * a big hit), <b>Cleanse</b> dissolves any harmful effect 2 s after it lands, and <b>Revive</b> -- one charge (two on
 * the final boss) -- catches a killing blow and stands it back up at 35% health with a second of immunity, the
 * player's blood burst and heartbeat. It fights as a relentless melee brawler that simply will not stay down.
 */
public class SuperRegenerationBoss extends BossPowerController {
	public static final String POWER_KEY = "power_12_super_regeneration";

	private static final int REGEN = 0;
	private static final int CLEANSE = 1;
	private static final int REVIVE = 2;
	private static final List<String> IDS = List.of("regen", "cleanse", "revive");
	private static final ParticleOptions BLOOD = BatchCFx.dust(0xD90D14, 1.6f);

	private int sinceBigHit = 100;
	private int revives = -1;
	private int immuneTicks;
	/** Harmful effects seen, with the tick they were first seen. */
	private final java.util.Map<Holder<MobEffect>, Integer> seen = new java.util.HashMap<>();

	public SuperRegenerationBoss(EmpoweredZombie boss) {
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
		return 2.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.HEART;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PINK;
	}

	private int revivesLeft() {
		if (revives < 0) {
			revives = boss.isFinalBoss() ? 2 : 1;
		}
		return revives;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		// the work happens in serverTick (passives run with or without a target)
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		sinceBigHit++;
		if (immuneTicks > 0) {
			immuneTicks--;
		}
		if (boss.tickCount % 20 == 0 && sinceBigHit >= 40 && boss.getHealth() < boss.getMaxHealth()) {
			boss.heal(boss.getMaxHealth() * 0.005f);
			particles(level, BLOOD, boss.position().add(0, 1.2, 0), 3, 0.4);
			if (ready(REGEN)) {
				startCooldown(REGEN, 200); // bookkeeping only: regen never stops
			}
		}
		if (boss.tickCount % 10 == 0) {
			cleanse(level);
		}
	}

	private void cleanse(ServerLevel level) {
		for (MobEffectInstance inst : new ArrayList<>(boss.getActiveEffects())) {
			Holder<MobEffect> effect = inst.getEffect();
			if (effect.value().getCategory() != MobEffectCategory.HARMFUL || effect.equals(ModMobEffects.UNSTABLE_MUTATION)) {
				continue;
			}
			int first = seen.computeIfAbsent(effect, k -> boss.tickCount);
			if (boss.tickCount - first >= 40) {
				boss.removeEffect(effect);
				seen.remove(effect);
				int rgb = effect.value().getColor();
				for (int i = 0; i < 10; i++) {
					level.sendParticles(BatchCFx.dust(rgb, 1.0f), boss.getX(), boss.getY() + i * 0.25, boss.getZ(), 2, 0.3, 0.05,
							0.3, 0.0);
				}
				sound(level, SoundEvents.BREWING_STAND_BREW, 0.35f, 1.8f);
				sound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 0.7f);
				startCooldown(CLEANSE, 10);
			}
		}
		seen.keySet().removeIf(h -> !boss.hasEffect(h));
	}

	@Override
	public float modifyIncomingDamage(DamageSource source, float amount) {
		if (immuneTicks > 0 && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
			return 0.0f;
		}
		if (amount >= 8.0f) {
			sinceBigHit = 0;
		}
		if (amount >= boss.getHealth() && revivesLeft() > 0 && !source.is(DamageTypes.FELL_OUT_OF_WORLD)
				&& !source.is(DamageTypes.GENERIC_KILL) && boss.level() instanceof ServerLevel level) {
			revives--;
			boss.setHealth(boss.getMaxHealth() * 0.35f);
			boss.clearFire();
			for (MobEffectInstance inst : new ArrayList<>(boss.getActiveEffects())) {
				if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
					boss.removeEffect(inst.getEffect());
				}
			}
			immuneTicks = 20;
			Vec3 c = boss.position().add(0, 1.2, 0);
			particles(level, BLOOD, c, 60, 0.6);
			particles(level, ParticleTypes.DAMAGE_INDICATOR, c, 12, 0.5);
			sound(level, SoundEvents.WARDEN_HEARTBEAT, 1.6f, 1.0f);
			sound(level, SoundEvents.TOTEM_USE, 0.5f, 1.4f);
			startCooldown(REVIVE, 20);
			return 0.0f;
		}
		return amount;
	}
}

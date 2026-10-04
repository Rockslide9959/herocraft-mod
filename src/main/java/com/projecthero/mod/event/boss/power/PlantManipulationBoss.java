package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Plant Manipulation (v0.14.1 kit): Thorn Shot volleys (poison), Thorn Snare (Vine Grab: roots), Vine Swing (reels a
 * far target in), Spore Cloud (a lingering poison/blindness cloud that also heals the boss standing in it) and -- once
 * badly hurt -- Overgrowth. The player's thorn projectile refuses to hit players for a mob owner, so the boss's thorns
 * are visible particle darts.
 */
public class PlantManipulationBoss extends BossPowerController {
	public static final String POWER_KEY = "power_22_plant_manipulation_chlorokinesis";

	private static final int THORN = 0;
	private static final int SNARE = 1;
	private static final int SWING = 2;
	private static final int SPORES = 3;
	private static final int OVERGROWTH = 4;
	private static final List<String> IDS = List.of("thorn_shot", "vine_grab", "vine_swing", "spore_cloud", "overgrowth");

	private static final ParticleOptions SPORE = BatchCFx.dust(0x8CBF40, 1.6f);

	public PlantManipulationBoss(EmpoweredZombie boss) {
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
		return ParticleTypes.HAPPY_VILLAGER;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.GREEN;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		if (lowHealth() && d < 14.0 && ready(OVERGROWTH)) {
			beginCast(level, target, OVERGROWTH, 1020, 4, SoundEvents.GRASS_BREAK, ParticleTypes.HAPPY_VILLAGER, t -> overgrowth(level));
			return;
		}
		if (d < 16.0 && sees(target) && ready(SNARE) && freshChoice(SNARE)) {
			particleLine(level, ParticleTypes.COMPOSTER, boss.getEyePosition(), mid(target), 2.0);
			hurt(target, bossDamage(3.6f) + 1.0f);
			control(target, MobEffects.MOVEMENT_SLOWDOWN, 160, 8);
			control(target, MobEffects.WEAKNESS, 160, 1);
			fling(target, new Vec3(0, Math.min(0.0, target.getDeltaMovement().y), 0));
			particles(level, ParticleTypes.HAPPY_VILLAGER, mid(target), 16, 0.5);
			soundAt(level, target.position(), SoundEvents.WEEPING_VINES_BREAK, 1.0f, 0.8f);
			startCooldown(SNARE, 160);
			return;
		}
		if (d > 12.0 && d < 30.0 && sees(target) && ready(SWING)) {
			particleLine(level, ParticleTypes.COMPOSTER, boss.getEyePosition(), mid(target), 2.0);
			pullToward(target, boss.position(), 1.6, 0.2);
			sound(level, SoundEvents.WEEPING_VINES_HIT, 1.0f, 0.8f);
			startCooldown(SWING, 140);
			return;
		}
		if (d < 18.0 && ready(SPORES) && freshChoice(SPORES)) {
			Vec3 at = target.position().add(0, 0.5, 0);
			sound(level, SoundEvents.PUFFER_FISH_BLOW_OUT, 1.0f, 0.8f);
			task(level, age -> {
				if (age % 10 == 0) {
					level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 0.5, at.z, 20, 2.0, 0.8, 2.0, 0.0);
					level.sendParticles(SPORE, at.x, at.y + 0.5, at.z, 12, 2.0, 0.8, 2.0, 0.0);
					for (LivingEntity e : victimsAround(level, at, 4.5)) {
						control(e, MobEffects.POISON, 60, 1);
						control(e, MobEffects.BLINDNESS, 60, 0);
					}
					if (boss.position().distanceTo(at) <= 4.5) {
						boss.heal(1.5f);
					}
				}
				return age < 160;
			});
			startCooldown(SPORES, 320);
			return;
		}
		if (d < 24.0 && sees(target) && ready(THORN)) {
			face(target);
			for (int i = 0; i < 3; i++) {
				int n = i;
				task(level, age -> {
					if (age < n * 3) {
						return true;
					}
					Vec3 from = boss.getEyePosition().add(boss.getLookAngle().scale(0.8));
					Vec3 aim = target.isAlive() ? aimFromEyes(lead(target, 4.0)) : boss.getLookAngle();
					aim = aim.add((random().nextDouble() - 0.5) * 0.1, (random().nextDouble() - 0.5) * 0.05,
							(random().nextDouble() - 0.5) * 0.1);
					projectile(level, from, aim, 1.7, 30, 0.4, ParticleTypes.COMPOSTER, 3, (at, hit) -> {
						if (hit != null) {
							hurt(hit, bossDamage(9.5f));
							control(hit, MobEffects.POISON, 160, 1);
						}
						particles(level, ParticleTypes.HAPPY_VILLAGER, at, 6, 0.2);
					});
					sound(level, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.4f);
					return false;
				});
			}
			startCooldown(THORN, 50);
		}
	}

	private void overgrowth(ServerLevel level) {
		Vec3 c = boss.position();
		for (LivingEntity e : strikeArea(level, c, 14.0, bossDamage(54.0f), true, 0.0, 0.0)) {
			control(e, MobEffects.MOVEMENT_SLOWDOWN, 200, 8);
			control(e, MobEffects.POISON, 200, 2);
			e.setDeltaMovement(e.getDeltaMovement().multiply(0.1, 1.0, 0.1));
		}
		for (int i = 0; i < 60; i++) {
			double a = random().nextDouble() * Math.PI * 2.0;
			double r = 2.0 + random().nextDouble() * 12.0;
			BlockPos pos = BlockPos.containing(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
			placeTemp(level, pos, random().nextBoolean() ? Blocks.OAK_LEAVES.defaultBlockState()
					: Blocks.MOSS_CARPET.defaultBlockState(), 120);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.5, c.z, 150, 7.0, 1.0, 7.0, 0.0);
		sound(level, SoundEvents.GRASS_BREAK, 1.6f, 0.4f);
	}
}

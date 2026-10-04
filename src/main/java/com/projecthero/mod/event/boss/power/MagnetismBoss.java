package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.p26.MagneticMass;
import com.projecthero.mod.hero.power.p26.MagneticMaterials;
import com.projecthero.mod.hero.revamp.d.BatchDFx;

import net.minecraft.core.particles.BlockParticleOption;
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
 * Magnetic Manipulation (v0.14.1 kit): Ferrous Shot (a slug of iron), Metal Storm (iron shards orbit it, then fire),
 * Magnetic Crush (worse the more metal armour the target wears -- {@link MagneticMaterials#loadout}) and Polarity Leap
 * (it pulls itself across the arena). Damage values come from the player's {@link MagneticMass} table. It never uses
 * Disarm on players -- taking a player's gear would be miserable, not hard.
 */
public class MagnetismBoss extends BossPowerController {
	public static final String POWER_KEY = "power_26_magnetic_manipulation";

	private static final int SHOT = 0;
	private static final int STORM = 1;
	private static final int CRUSH = 2;
	private static final int LEAP = 3;
	private static final List<String> IDS = List.of("ferrous_shot", "metal_storm", "magnetic_crush", "polarity_leap");
	private static final float DAMAGE_MULT = 1.2f;

	private static final ParticleOptions IRON = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState());

	private int stormTicks;
	private LivingEntity stormTarget;

	public MagnetismBoss(EmpoweredZombie boss) {
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
		return ParticleTypes.ELECTRIC_SPARK;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (stormTicks > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		MagneticMaterials.Loadout gear = MagneticMaterials.loadout(target);
		if (gear.pieces() > 0 && d < 18.0 && sees(target) && ready(CRUSH) && freshChoice(CRUSH)) {
			int pieces = gear.pieces();
			float dmg = bossDamage((3.0f + 4.0f * pieces) * DAMAGE_MULT) * (gear.netherite() ? 0.6f : 1.0f);
			hurt(target, dmg);
			control(target, MobEffects.MOVEMENT_SLOWDOWN, 100, Math.min(4, pieces + 1));
			control(target, MobEffects.WEAKNESS, 100, 1);
			if (pieces >= 3) {
				control(target, MobEffects.MOVEMENT_SLOWDOWN, 50, 7);
			}
			BatchDFx.tether(level, boss.getEyePosition(), mid(target), BatchDFx.MAGNET, 20);
			particles(level, ParticleTypes.ELECTRIC_SPARK, mid(target), 24, 0.4);
			particles(level, ParticleTypes.CRIT, mid(target), 10, 0.4);
			soundAt(level, target.position(), SoundEvents.ANVIL_LAND, 0.7f, 1.3f);
			startCooldown(CRUSH, 152);
			return;
		}
		if (d < 24.0 && sees(target) && ready(STORM) && freshChoice(STORM)) {
			stormTicks = 30;
			stormTarget = target;
			sound(level, SoundEvents.IRON_GOLEM_DAMAGE, 1.1f, 0.6f);
			startCooldown(STORM, 300);
			return;
		}
		if (d > 9.0 && d < 24.0 && ready(LEAP)) {
			double strength = Math.max(1.0, Math.min(2.4, 0.9 + 0.06 * d));
			Vec3 dir = target.position().subtract(boss.position()).normalize();
			boss.setDeltaMovement(dir.x * strength, Math.max(0.35, dir.y * strength + 0.3), dir.z * strength);
			boss.hurtMarked = true;
			particleLine(level, ParticleTypes.ELECTRIC_SPARK, boss.position().add(0, 1, 0), mid(target), 1.0);
			sound(level, SoundEvents.IRON_GOLEM_REPAIR, 1.0f, 1.2f);
			startCooldown(LEAP, 80);
			return;
		}
		if (d < 30.0 && sees(target) && ready(SHOT)) {
			face(target);
			MagneticMass mass = MagneticMass.MEDIUM;
			Vec3 from = boss.getEyePosition().add(boss.getLookAngle());
			float dmg = bossDamage((float) mass.damage * DAMAGE_MULT) + 1.0f;
			projectile(level, from, aimFromEyes(lead(target, 6.0)), 1.4, 35, 0.6, IRON, 4, (at, hit) -> {
				if (hit != null) {
					hurt(hit, dmg);
					knockAway(hit, boss.position(), mass.knockback + 0.3, 0.15);
				}
				particles(level, IRON, at, 16, 0.3);
				particles(level, ParticleTypes.ELECTRIC_SPARK, at, 8, 0.3);
				soundAt(level, at, SoundEvents.ANVIL_LAND, 0.5f, 1.6f);
			});
			sound(level, SoundEvents.IRON_GOLEM_HURT, 0.6f, 1.7f);
			startCooldown(SHOT, 51);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (stormTicks <= 0) {
			return;
		}
		stormTicks--;
		Vec3 c = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
		for (int i = 0; i < 6; i++) {
			double a = stormTicks * 0.35 + i * Math.PI / 3.0;
			level.sendParticles(IRON, c.x + Math.cos(a) * 2.6, c.y, c.z + Math.sin(a) * 2.6, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (stormTicks == 0) {
			LivingEntity t = stormTarget != null && stormTarget.isAlive() ? stormTarget : target;
			if (t == null) {
				return;
			}
			float each = bossDamage((float) MagneticMass.LIGHT.damage * DAMAGE_MULT) + 1.0f;
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3.0;
				Vec3 from = c.add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
				Vec3 dir = mid(t).subtract(from).normalize().add((random().nextDouble() - 0.5) * 0.08, 0,
						(random().nextDouble() - 0.5) * 0.08);
				projectile(level, from, dir, 1.6, 30, 0.6, ParticleTypes.ELECTRIC_SPARK, 2, (at, hit) -> {
					if (hit != null) {
						hurtFresh(hit, each);
					}
					particles(level, IRON, at, 8, 0.2);
				});
			}
			sound(level, SoundEvents.IRON_GOLEM_ATTACK, 1.2f, 0.8f);
		}
	}
}

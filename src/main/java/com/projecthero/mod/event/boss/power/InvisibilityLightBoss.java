package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.revamp.BatchCFx;
import com.projecthero.mod.hero.revamp.d.BatchDFx;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Invisibility / Light Manipulation (v0.14.1 kit): Light Blast, Flash (a blinding burst around itself), Cloaking (it
 * fades out -- the boss's glow outline still shows where it is -- and is revealed for 2 s whenever it attacks), Hard
 * Light Blade (a conjured blade that adds melee damage and makes its victims glow) and -- once badly hurt -- Holy Light,
 * a charged beam with bursts. Hits in the sun are 10% stronger, as the player's.
 */
public class InvisibilityLightBoss extends BossPowerController {
	public static final String POWER_KEY = "power_15_invisibility_light_manipulation";

	private static final int BLAST = 0;
	private static final int FLASH = 1;
	private static final int CLOAK = 2;
	private static final int BLADE = 3;
	private static final int HOLY = 4;
	private static final List<String> IDS = List.of("light_blast", "flash", "cloaking_toggle", "hard_light_blade", "perfect_cloak");
	private static final ResourceLocation BLADE_MOD = ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "boss_hard_light_blade");
	private static final ParticleOptions YELLOW = BatchCFx.dust(0xFFDB33, 2.0f);

	private int cloakTicks;
	private int bladeTicks;
	private int holyTicks;
	private LivingEntity holyTarget;

	public InvisibilityLightBoss(EmpoweredZombie boss) {
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
		return bladeTicks > 0 ? 2.5 : 9.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.END_ROD;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.YELLOW;
	}

	private float sun(ServerLevel level) {
		return level.isDay() && !level.isRaining() && level.canSeeSky(boss.blockPosition().above()) ? 1.1f : 1.0f;
	}

	private void reveal() {
		if (cloakTicks > 40) {
			cloakTicks = 40; // attacking breaks the cloak for 2 s
			boss.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (holyTicks > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		if (lowHealth() && d < 30.0 && sees(target) && ready(HOLY)) {
			beginCast(level, target, HOLY, 1280, 4, SoundEvents.BEACON_ACTIVATE, YELLOW, t -> {
				holyTicks = 40;
				holyTarget = t;
				reveal();
			});
			return;
		}
		if (d < 8.0 && ready(FLASH) && freshChoice(FLASH)) {
			reveal();
			Vec3 c = boss.position().add(0, 1.2, 0);
			for (LivingEntity e : victimsAround(level, c, 8.0)) {
				hurt(e, bossDamage(12.0f) * sun(level));
				control(e, MobEffects.BLINDNESS, 200, 0);
				control(e, MobEffects.MOVEMENT_SLOWDOWN, 200, 1);
			}
			particles(level, ParticleTypes.FLASH, c, 1, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, 4.0, 1.0, 4.0, 0.05);
			particles(level, YELLOW, c, 50, 2.0);
			sound(level, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.5f);
			startCooldown(FLASH, 170);
			return;
		}
		if (d < 4.5 && bladeTicks <= 0 && ready(BLADE)) {
			bladeTicks = 400;
			AttributeInstance atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
			if (atk != null && !atk.hasModifier(BLADE_MOD)) {
				atk.addTransientModifier(new AttributeModifier(BLADE_MOD, 2.5, AttributeModifier.Operation.ADD_VALUE));
			}
			particles(level, BatchDFx.LIGHT, boss.position().add(0, 1.2, 0), 30, 0.5);
			sound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
			startCooldown(BLADE, 600);
			return;
		}
		if (cloakTicks <= 0 && d > 5.0 && ready(CLOAK)) {
			cloakTicks = 160;
			boss.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false));
			particles(level, BatchDFx.PRISM, boss.position().add(0, 1.2, 0), 30, 0.6);
			sound(level, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.3f);
			startCooldown(CLOAK, 400);
			return;
		}
		if (d < 22.0 && sees(target) && ready(BLAST)) {
			reveal();
			face(target);
			Vec3 from = boss.getEyePosition();
			Vec3 to = clipEnd(level, from, from.add(aimFromEyes(lead(target, 2.0)).scale(22.0)));
			particleLine(level, ParticleTypes.END_ROD, from, to, 3.0);
			for (LivingEntity e : strikeLine(level, from, to, 0.6, bossDamage(17.0f) * sun(level))) {
				control(e, MobEffects.BLINDNESS, 80, 0);
				particles(level, YELLOW, mid(e), 12, 0.3);
			}
			sound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
			startCooldown(BLAST, 45);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (cloakTicks > 0) {
			cloakTicks--;
			if (cloakTicks == 0) {
				boss.removeEffect(MobEffects.INVISIBILITY);
				particles(level, BatchDFx.PRISM, boss.position().add(0, 1.2, 0), 20, 0.6);
			}
		}
		if (bladeTicks > 0) {
			bladeTicks--;
			if (bladeTicks % 5 == 0) {
				particles(level, BatchDFx.LIGHT, boss.position().add(boss.getLookAngle().scale(0.8)).add(0, 1.2, 0), 2, 0.1);
			}
			if (bladeTicks == 0) {
				AttributeInstance atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
				if (atk != null) {
					atk.removeModifier(BLADE_MOD);
				}
			}
		}
		if (holyTicks > 0) {
			holyTicks--;
			LivingEntity t = holyTarget;
			if (t == null || !t.isAlive()) {
				holyTicks = 0;
				return;
			}
			boss.getNavigation().stop();
			face(t);
			Vec3 from = boss.getEyePosition();
			Vec3 to = clipEnd(level, from, from.add(aimFromEyes(mid(t)).scale(40.0)));
			if (holyTicks % 2 == 0) {
				particleLine(level, ParticleTypes.END_ROD, from, to, 1.5);
			}
			if (holyTicks % 10 == 0) {
				strikeLine(level, from, to, 0.6, bossDamage(14.0f) * 0.5f);
				for (LivingEntity e : strikeArea(level, to, 5.0, bossDamage(29.0f) * 0.6f, true, 0.4, 0.1)) {
					control(e, MobEffects.GLOWING, 60, 0);
					control(e, MobEffects.BLINDNESS, 40, 0);
				}
				particles(level, ParticleTypes.FLASH, to, 1, 0.0);
				particles(level, YELLOW, to, 20, 1.0);
			}
		}
	}

	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		if (bladeTicks > 0 && source.getEntity() instanceof LivingEntity attacker && attacker.distanceTo(boss) < 4.0) {
			control(attacker, MobEffects.GLOWING, 60, 0); // the blade marks whoever it clashes with
		}
	}
}

package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.p27.ShrunkenEffect;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Size Manipulation (v0.14.1 kit): Giant Punch, Stomp, Shrink Punch (the player power's real Shrunken effect) and two
 * growth phases -- Large Form below 60% health and Giant Form below 30% (scaled down for a boss: x1.4 and x1.9 of its
 * already-large body, with the player's attack / reach bonuses). Its punch and stomp reach grow with it.
 */
public class SizeManipulationBoss extends BossPowerController {
	public static final String POWER_KEY = "power_27_size_manipulation";

	private static final int PUNCH = 0;
	private static final int STOMP = 1;
	private static final int SHRINK = 2;
	private static final int LARGE = 3;
	private static final int GIANT = 4;
	private static final List<String> IDS = List.of("giant_punch", "stomp", "shrink_punch", "large_form", "giant_form");
	private static final ResourceLocation FORM = ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "boss_size_form");

	/** 0 normal, 1 Large, 2 Giant. */
	private int form;

	public SizeManipulationBoss(EmpoweredZombie boss) {
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
		return 2.5 + form;
	}

	@Override
	public ParticleOptions auraParticle() {
		return BatchCFx.dust(0x9C6BFF, 1.0f);
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	/** Reach multiplier (the player's reachScale: Large 2, Giant 4 -- halved for a boss). */
	private double reach() {
		return form == 2 ? 2.0 : (form == 1 ? 1.4 : 1.0);
	}

	private float formBonus() {
		return form == 2 ? 9.0f : (form == 1 ? 3.0f : 0.0f);
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		if (form < 2 && healthFraction() < 0.3f && ready(GIANT)) {
			grow(level, 2);
			startCooldown(GIANT, 20);
			return;
		}
		if (form < 1 && healthFraction() < 0.6f && ready(LARGE)) {
			grow(level, 1);
			startCooldown(LARGE, 20);
			return;
		}
		double stompR = (3.5 + form) * reach();
		if (d < stompR && ready(STOMP) && freshChoice(STOMP)) {
			Vec3 c = boss.position();
			for (LivingEntity e : strikeArea(level, c, stompR, bossDamage(9.6f) + formBonus() * 0.5f, false, 1.2, 0.3)) {
				control(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
			}
			particles(level, ParticleTypes.EXPLOSION, c, 1, 0.0);
			BatchCFx.flatRing(level, c.add(0, 0.2, 0), stompR * 0.8, 32, ParticleTypes.CLOUD, 0.2);
			sound(level, SoundEvents.GENERIC_EXPLODE, 0.7f, 0.5f);
			startCooldown(STOMP, 136);
			return;
		}
		if (d < 4.5 && ready(SHRINK) && freshChoice(SHRINK)) {
			face(target);
			hurt(target, bossDamage(8.0f) + 1.0f);
			if (target.getMaxHealth() <= 200.0f) {
				control(target, ShrunkenEffect.HOLDER, 160, 0);
			}
			knockAway(target, boss.position(), 0.5, 0.1);
			particles(level, ParticleTypes.REVERSE_PORTAL, mid(target), 30, 0.4);
			particles(level, ParticleTypes.POOF, mid(target), 8, 0.3);
			sound(level, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0f, 1.8f);
			sound(level, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
			startCooldown(SHRINK, 200);
			return;
		}
		double punchReach = 4.5 * reach();
		if (d < punchReach && ready(PUNCH)) {
			face(target);
			Vec3 c = boss.getEyePosition().add(flatDirTo(target.position()).scale(Math.min(d, punchReach) * 0.6));
			for (LivingEntity e : victimsAround(level, c, Math.max(2.0, punchReach * 0.4))) {
				hurt(e, bossDamage(12.0f) + formBonus());
				knockAway(e, boss.position(), 1.6, 0.3);
			}
			particles(level, ParticleTypes.SWEEP_ATTACK, c, 2, 0.4);
			sound(level, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.4f);
			startCooldown(PUNCH, 60);
		}
	}

	private void grow(ServerLevel level, int newForm) {
		form = newForm;
		setModifier(Attributes.SCALE, form == 2 ? 0.9 : 0.4);
		setModifier(Attributes.ENTITY_INTERACTION_RANGE, form == 2 ? 3.0 : 1.5);
		setModifier(Attributes.STEP_HEIGHT, form == 2 ? 1.5 : 0.6);
		boss.refreshDimensions();
		Vec3 c = boss.position().add(0, 1.0, 0);
		particles(level, ParticleTypes.CLOUD, c, 40, 1.0);
		particles(level, ParticleTypes.END_ROD, c, 20, 1.0);
		particles(level, ParticleTypes.EXPLOSION, c, 1, 0.0);
		sound(level, SoundEvents.BREEZE_INHALE, 1.2f, 0.5f);
		if (form == 2) {
			sound(level, SoundEvents.RAVAGER_ROAR, 1.2f, 0.3f);
		}
	}

	private void setModifier(Holder<Attribute> attribute, double amount) {
		AttributeInstance inst = boss.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		inst.removeModifier(FORM);
		inst.addTransientModifier(new AttributeModifier(FORM, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}
}

package com.herocraft.mod.event.boss.power;

import com.herocraft.mod.event.boss.BossPowerController;
import com.herocraft.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Super Durability: the defensive power, and the one that most needed care not to become "the boring
 * one with more health".
 *
 * <p>It does not get a bigger health pool. Instead it gets a <b>Brace</b>: a short, visible window of
 * heavy damage resistance that it raises in response to being hurt badly, and which ends on its own.
 * That turns it into a timing problem -- burst it while it is open, hold your cooldowns while it is
 * braced -- rather than a longer fight. Between braces it stays aggressive with a shoulder charge
 * that closes ground and knocks people back.
 *
 * <p>Because Brace is a timed buff on the boss rather than a permanent multiplier, a player who reads
 * it correctly kills this boss <em>faster</em> than one who does not, which is the whole design goal.
 */
public class DurabilityBoss extends BossPowerController {
	public static final String POWER_KEY = "power_13_super_durability";

	private static final int SLOT_BRACE = 0;
	private static final int SLOT_SHOULDER = 1;
	private static final int SLOT_SLAM = 2;
	private static final int BRACE_TICKS = 5 * 20;

	public DurabilityBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 2.5;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.ENCHANTED_HIT;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.GREEN;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double distance = boss.distanceTo(target);
		if (distance > 6.0 && distance < 18.0 && ready(SLOT_SHOULDER) && freshChoice(SLOT_SHOULDER)) {
			shoulder(level, target);
			startCooldown(SLOT_SHOULDER, 150);
			return;
		}
		// Close-range answer so a Durability boss being fought toe-to-toe is not purely a melee zombie
		// between braces: a guard-break slam that throws everyone in reach back.
		if (areaAnchor(level, target, 4.0, 2) != null && ready(SLOT_SLAM)) {
			slam(level);
			startCooldown(SLOT_SLAM, 170);
		}
	}

	/** Brace is reactive: it goes up when a real hit lands, not on a metronome. */
	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		if (!readyReactive(SLOT_BRACE) || amount < 10.0f) {
			return;
		}
		boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BRACE_TICKS, 2, false, true));
		sound(level, SoundEvents.SHIELD_BLOCK, 1.2f, 0.6f);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, boss.getX(), boss.getY() + 1.2, boss.getZ(),
				30, 0.8, 0.8, 0.8, 0.05);
		startCooldown(SLOT_BRACE, 420);
	}

	private void shoulder(ServerLevel level, LivingEntity target) {
		net.minecraft.world.phys.Vec3 dir = target.position().subtract(boss.position());
		if (dir.lengthSqr() < 1.0e-4) {
			return;
		}
		dir = new net.minecraft.world.phys.Vec3(dir.x, 0, dir.z).normalize();
		boss.setDeltaMovement(dir.x * 1.1, 0.25, dir.z * 1.1);
		boss.hasImpulse = true;
		sound(level, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.7f);

		for (Player player : playersNear(level, 3.5)) {
			hurt(player, 7.0f);
			knockAway(player, boss.position(), 1.8, 0.4);
		}
	}

	private void slam(ServerLevel level) {
		sound(level, SoundEvents.IRON_GOLEM_ATTACK, 1.1f, 0.5f);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, boss.getX(), boss.getY() + 0.3, boss.getZ(),
				36, 2.4, 0.2, 2.4, 0.05);
		for (Player player : playersNear(level, 4.5)) {
			hurt(player, 6.0f);
			knockAway(player, boss.position(), 1.9, 0.5);
		}
	}
}

package com.herocraft.mod.event.boss.power;

import java.util.ArrayList;
import java.util.List;

import com.herocraft.mod.event.boss.BossPowerController;
import com.herocraft.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Electrokinesis: chain lightning that punishes standing together, and a close-range static burst.
 *
 * <p>The chain is the whole point -- it starts on one target and hops to the nearest player who has
 * not been hit yet, losing damage each hop. Spreading out breaks it after one or two links; bunching
 * up feeds it. That makes it the cleanest "AoE against clustered players" in the roster, and its
 * counterplay is entirely positional rather than reactive.
 */
public class ElectrokinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_07_electrokinesis";

	private static final int SLOT_CHAIN = 0;
	private static final int SLOT_BURST = 1;
	private static final int MAX_CHAIN_LINKS = 4;
	private static final double CHAIN_HOP_RANGE = 7.0;
	/** How far the target may have moved from the locked strike point and still be caught by the bolt. */
	private static final double CHAIN_DODGE_RADIUS = 3.0;

	/**
	 * v0.6.22: the chain no longer hitscans the instant it is off cooldown -- that was the one boss
	 * attack a running player genuinely could not avoid. It now locks onto the target's position,
	 * telegraphs a growing spark line to that <em>fixed</em> point for one ability cycle (~0.5 s), and
	 * only then discharges. Anyone still standing near the locked point is the seed of the chain; a
	 * player who used the wind-up to move clear takes nothing. Damage and hop behaviour are unchanged.
	 */
	private boolean chainCharging;
	private Vec3 chainLockPoint;

	public ElectrokinesisBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
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
		if (chainCharging) {
			// Rooted while the charge builds -- a visible "about to unload" beat.
			boss.getNavigation().stop();
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 5, false, false));
			dischargeChain(level);
			return;
		}
		double distance = boss.distanceTo(target);
		if (distance < 4.5 && ready(SLOT_BURST) && freshChoice(SLOT_BURST)) {
			burst(level);
			startCooldown(SLOT_BURST, 160);
			return;
		}
		if (distance < 20.0 && ready(SLOT_CHAIN) && boss.hasLineOfSight(target)) {
			// Lock the strike point and telegraph -- resolved on the next ability cycle.
			chainCharging = true;
			// Lock slightly ahead of a moving target so simply running in a straight line does not beat it;
			// a change of direction still does (the point is fixed once locked).
			chainLockPoint = target.position().add(0, target.getBbHeight() * 0.5, 0)
					.add(target.getDeltaMovement().scale(3.0));
			sound(level, SoundEvents.CONDUIT_ATTACK_TARGET, 0.8f, 1.4f);
			sound(level, SoundEvents.BEACON_ACTIVATE, 0.6f, 1.8f);
			particleLine(level, ParticleTypes.ELECTRIC_SPARK, boss.getEyePosition(), chainLockPoint, 2.0);
			particles(level, ParticleTypes.ELECTRIC_SPARK, chainLockPoint, 8, 0.3);
			startCooldown(SLOT_CHAIN, 140);
		}
	}

	/** Fire the charged bolt at the locked point. Fizzles harmlessly if nobody is standing there. */
	private void dischargeChain(ServerLevel level) {
		chainCharging = false;
		Vec3 point = chainLockPoint;
		chainLockPoint = null;
		if (point == null) {
			return;
		}
		sound(level, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7f, 1.8f);
		particleLine(level, ParticleTypes.ELECTRIC_SPARK, boss.getEyePosition(), point, 3.0);

		LivingEntity seed = null;
		for (Player player : level.getEntitiesOfClass(Player.class, box(point, CHAIN_DODGE_RADIUS),
				p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
			if (player.position().add(0, player.getBbHeight() * 0.5, 0).distanceTo(point) <= CHAIN_DODGE_RADIUS) {
				seed = player;
				break;
			}
		}
		if (seed == null) {
			// A clean dodge -- the bolt cracks into empty ground.
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 24, 0.5, 0.5, 0.5, 0.4);
			return;
		}
		chain(level, seed);
	}

	private void chain(ServerLevel level, LivingEntity firstTarget) {
		List<Player> hit = new ArrayList<>();
		LivingEntity current = firstTarget;
		float damage = 9.0f;

		for (int link = 0; link < MAX_CHAIN_LINKS && current != null; link++) {
			particleLine(level, ParticleTypes.ELECTRIC_SPARK,
					link == 0 ? boss.getEyePosition() : boss.getEyePosition(), current.getEyePosition(), 2.5);
			hurt(current, damage);
			if (current instanceof Player p) {
				hit.add(p);
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
			}
			particles(level, ParticleTypes.ELECTRIC_SPARK,
					current.position().add(0, current.getBbHeight() * 0.5, 0), 12, 0.3);
			damage *= 0.7f;
			current = nextLink(level, current, hit);
		}
	}

	/** Nearest not-yet-hit player within hop range of the last one. */
	private Player nextLink(ServerLevel level, LivingEntity from, List<Player> alreadyHit) {
		Player best = null;
		double bestSq = CHAIN_HOP_RANGE * CHAIN_HOP_RANGE;
		for (Player player : level.getEntitiesOfClass(Player.class, from.getBoundingBox().inflate(CHAIN_HOP_RANGE),
				p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
			if (alreadyHit.contains(player)) {
				continue;
			}
			double d = player.distanceToSqr(from);
			if (d < bestSq) {
				bestSq = d;
				best = player;
			}
		}
		return best;
	}

	private void burst(ServerLevel level) {
		sound(level, SoundEvents.TRIDENT_THUNDER.value(), 1.0f, 1.5f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 1.0, boss.getZ(),
				40, 1.6, 1.0, 1.6, 0.2);
		for (Player player : playersNear(level, 5.0)) {
			hurt(player, 6.0f);
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
			knockAway(player, boss.position(), 0.6, 0.3);
		}
	}
}

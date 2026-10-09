package com.projecthero.mod.punisher;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * v0.15.18: the Punisher's crowd control, shared by Brutal Strike / Breach Kick (stun), Smoke Screen and the Flashbang
 * (no target). Both are plain server-side timers keyed on the live entity:
 *
 * <ul>
 *   <li><b>Stun</b> -- the target cannot move (Slowness X, its path is dropped every tick) and cannot attack (it may not
 *       hold a target, and Weakness III on top). Knockback still carries it, so a Breach Kick throws a stunned body.</li>
 *   <li><b>No target</b> -- a mob drops whatever it was after and cannot pick anything again until the window ends;
 *       refused at {@code Mob#setTarget} ({@code MobMindLockMixin}), so its goals cannot race the clear.</li>
 * </ul>
 *
 * <p>Static, so cleared on server stop ({@link com.projecthero.mod.diagnostics.ServerStateReset}); entries for a removed
 * entity drop out on the next tick.
 */
public final class PunisherControl {
	private static final Map<LivingEntity, Long> STUNNED = new ConcurrentHashMap<>();
	private static final Map<Mob, Long> NO_TARGET = new ConcurrentHashMap<>();

	private PunisherControl() {
	}

	public static void clearSessionState() {
		STUNNED.clear();
		NO_TARGET.clear();
	}

	/** Stun {@code target} for {@code ticks} (extends, never shortens, a stun already running). */
	public static void stun(LivingEntity target, int ticks) {
		long until = target.level().getGameTime() + ticks;
		STUNNED.merge(target, until, Math::max);
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, ticks, 9);
		AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, ticks, 2);
		if (target instanceof Mob mob) {
			suppressTarget(mob, ticks);
			mob.getNavigation().stop();
		}
	}

	public static boolean isStunned(LivingEntity target) {
		Long until = STUNNED.get(target);
		return until != null && target.level().getGameTime() < until;
	}

	/** {@code mob} drops its target now and may not take one for {@code ticks}. */
	public static void suppressTarget(Mob mob, int ticks) {
		long until = mob.level().getGameTime() + ticks;
		NO_TARGET.merge(mob, until, Math::max);
		dropTarget(mob);
	}

	/** Asked by {@code MobMindLockMixin}: may this mob not take a target right now? */
	public static boolean refusesTarget(Mob mob) {
		Long until = NO_TARGET.get(mob);
		return until != null && mob.level().getGameTime() < until;
	}

	private static void dropTarget(Mob mob) {
		if (mob.getTarget() != null) {
			mob.setTarget(null);
		}
		if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	/** Once per server tick. */
	public static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<LivingEntity, Long>> it = STUNNED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, Long> e = it.next();
			LivingEntity target = e.getKey();
			if (target.isRemoved() || !target.isAlive() || target.level().getGameTime() >= e.getValue()) {
				it.remove();
				continue;
			}
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if (target.tickCount % 5 == 0 && target.level() instanceof ServerLevel level) {
				// dazed: a little ring of sparks over the head
				double a = target.tickCount * 0.6;
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX() + Math.cos(a) * 0.4,
						target.getY() + target.getBbHeight() + 0.25, target.getZ() + Math.sin(a) * 0.4, 2, 0.05, 0.02, 0.05, 0.0);
			}
		}
		for (Iterator<Map.Entry<Mob, Long>> it = NO_TARGET.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Mob, Long> e = it.next();
			Mob mob = e.getKey();
			if (mob.isRemoved() || !mob.isAlive() || mob.level().getGameTime() >= e.getValue()) {
				it.remove();
				continue;
			}
			dropTarget(mob);
		}
	}
}

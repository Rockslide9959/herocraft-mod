package com.projecthero.mod.spin;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spin.data.SpinMark;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 the Spin: what the Spin does to the creatures it touches, kept on each one's {@link SpinMark} (synced, so every client
 * draws the twisting skin, the aged cracks and a spun player's view drift).
 * <ul>
 *   <li><b>Spun</b> (a ball hit): a mob loses its target and can't take a new one ({@code mixin.SpinMobTargetMixin}), staggers
 *   sideways and turns about -- it can't aim. A player's view drifts on his own client.</li>
 *   <li><b>Locked</b> (Spin Touch): it can't move.</li>
 *   <li><b>Aged</b> (Ball Breaker): the effects are vanilla's; the mark is the look.</li>
 *   <li><b>Healing</b> (Healing Spin): health back every tick.</li>
 * </ul>
 * {@link #MARKED} is the creatures with a running mark, ticked once a server tick and dropped when idle; it holds entities, so
 * {@code ServerStateReset} clears it ({@link #clearSessionState}).
 */
public final class SpinMarks {
	private static final List<LivingEntity> MARKED = new ArrayList<>();

	private SpinMarks() {
	}

	static void clearSessionState() {
		MARKED.clear();
	}

	public static SpinMark mark(LivingEntity e) {
		return e.getAttachedOrElse(ModAttachments.SPIN_MARK, SpinMark.NONE);
	}

	private static void set(LivingEntity e, SpinMark m) {
		e.setAttached(ModAttachments.SPIN_MARK, m);
		synchronized (MARKED) {
			if (!MARKED.contains(e)) {
				MARKED.add(e);
			}
		}
	}

	private static long now(LivingEntity e) {
		return e.level().getGameTime();
	}

	public static void spin(LivingEntity e, int ticks) {
		set(e, mark(e).withSpun(now(e) + ticks));
		if (e instanceof Mob mob) {
			dropTarget(mob);
		}
	}

	public static void lock(LivingEntity e, int ticks) {
		set(e, mark(e).withLocked(now(e) + ticks));
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false, true));
		e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
		e.hurtMarked = true;
	}

	/** True while {@link #tick} deals an aging bite (so {@link SpinDamage} lets that one wither hit through). */
	static final ThreadLocal<Boolean> BITING = ThreadLocal.withInitial(() -> false);

	/**
	 * Ball Breaker's aging: Weakness II, Slowness III, Wither, and the grey cracked look. The Wither is the look (black hearts, the
	 * icon); while aged, its own ticks are cancelled and {@link #tick} bites for {@link SpinConfig#AGING_BITE_DAMAGE} every
	 * {@link SpinConfig#AGING_BITE_EVERY} ticks instead (scaled with the strike's 100).
	 */
	public static void age(LivingEntity e, int ticks) {
		set(e, mark(e).withAged(now(e) + ticks));
		e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 2));
		e.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, 0));
	}

	public static void heal(LivingEntity e, float perTick, int ticks) {
		set(e, mark(e).withHeal(now(e) + ticks, perTick));
	}

	public static boolean spun(LivingEntity e) {
		return mark(e).spun(now(e));
	}

	public static boolean locked(LivingEntity e) {
		return mark(e).locked(now(e));
	}

	public static boolean aged(LivingEntity e) {
		return mark(e).aged(now(e));
	}

	private static void dropTarget(Mob mob) {
		mob.setTarget(null);
		if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
		mob.getNavigation().stop();
	}

	/** Once a server tick for every marked creature. */
	public static void tickAll() {
		List<LivingEntity> copy;
		synchronized (MARKED) {
			copy = new ArrayList<>(MARKED);
		}
		for (LivingEntity e : copy) {
			if (e.isRemoved() || !e.isAlive() || !tick(e)) {
				synchronized (MARKED) {
					MARKED.remove(e);
				}
				if (!e.isRemoved() && e.hasAttached(ModAttachments.SPIN_MARK)) {
					e.removeAttached(ModAttachments.SPIN_MARK);
				}
			}
		}
	}

	/** One tick of {@code e}'s mark. False once nothing runs any more. */
	public static boolean tick(LivingEntity e) {
		if (!(e.level() instanceof ServerLevel)) {
			return false;
		}
		SpinMark m = mark(e);
		long now = now(e);
		if (m.idle(now)) {
			return false;
		}
		if (m.spun(now) && e instanceof Mob mob) {
			// it can't aim: no target, and it staggers about
			if (mob.getTarget() != null || mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
				dropTarget(mob);
			}
			if (e.tickCount % 6 == 0) {
				float a = e.getRandom().nextFloat() * (float) (Math.PI * 2);
				Vec3 v = e.getDeltaMovement();
				e.setDeltaMovement(v.x * 0.3 + Math.cos(a) * 0.18, v.y, v.z * 0.3 + Math.sin(a) * 0.18);
				e.hurtMarked = true;
			}
			float yaw = e.getYRot() + 23f;
			e.setYRot(yaw);
			e.setYHeadRot(yaw);
			e.yBodyRot = yaw;
		}
		if (m.locked(now)) {
			Vec3 v = e.getDeltaMovement();
			if (v.x != 0 || v.z != 0) {
				e.setDeltaMovement(0, Math.min(0, v.y), 0);
				e.hurtMarked = true;
			}
			if (e instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		if (m.aged(now)) {
			long left = m.agedUntil() - now;
			if ((left + SpinConfig.AGING_BITE_EVERY / 2) % SpinConfig.AGING_BITE_EVERY == 0L) {
				bite(e);
			}
		}
		if (m.healing(now) && e.getHealth() < e.getMaxHealth()) {
			e.heal(m.healPerTick());
		}
		return true;
	}

	/** One aging bite: {@link SpinConfig#AGING_BITE_DAMAGE} as wither damage. Public for the tests. */
	public static void bite(LivingEntity e) {
		BITING.set(true);
		try {
			e.hurt(e.damageSources().wither(), SpinConfig.AGING_BITE_DAMAGE);
		} finally {
			BITING.set(false);
		}
	}

	/** Is {@code player} spun (his client drifts his view)? Client-safe. */
	public static boolean playerSpun(Player player) {
		SpinMark m = player.getAttachedOrElse(ModAttachments.SPIN_MARK, null);
		return m != null && m.spun(player.level().getGameTime());
	}
}

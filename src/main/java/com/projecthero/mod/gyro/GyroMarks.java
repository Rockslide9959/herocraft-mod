package com.projecthero.mod.gyro;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.gyro.data.GyroMark;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Gyro Zeppeli: what the Spin does to the creatures it touches, kept on each one's {@link GyroMark} (synced, so every client
 * draws the twisting skin, the aged cracks and a spun player's view drift).
 * <ul>
 *   <li><b>Spun</b> (a ball hit): a mob loses its target and can't take a new one ({@code mixin.GyroMobTargetMixin}), staggers
 *   sideways and turns about -- it can't aim. A player's view drifts on his own client.</li>
 *   <li><b>Locked</b> (Spin Touch): it can't move.</li>
 *   <li><b>Aged</b> (Ball Breaker): the effects are vanilla's; the mark is the look.</li>
 *   <li><b>Healing</b> (Healing Spin): health back every tick.</li>
 * </ul>
 * {@link #MARKED} is the creatures with a running mark, ticked once a server tick and dropped when idle; it holds entities, so
 * {@code ServerStateReset} clears it ({@link #clearSessionState}).
 */
public final class GyroMarks {
	private static final List<LivingEntity> MARKED = new ArrayList<>();

	private GyroMarks() {
	}

	static void clearSessionState() {
		MARKED.clear();
	}

	public static GyroMark mark(LivingEntity e) {
		return e.getAttachedOrElse(ModAttachments.GYRO_MARK, GyroMark.NONE);
	}

	private static void set(LivingEntity e, GyroMark m) {
		e.setAttached(ModAttachments.GYRO_MARK, m);
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

	/** Ball Breaker's aging: Weakness II, Slowness III, Wither, and the grey cracked look. */
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
				if (!e.isRemoved() && e.hasAttached(ModAttachments.GYRO_MARK)) {
					e.removeAttached(ModAttachments.GYRO_MARK);
				}
			}
		}
	}

	/** One tick of {@code e}'s mark. False once nothing runs any more. */
	public static boolean tick(LivingEntity e) {
		if (!(e.level() instanceof ServerLevel)) {
			return false;
		}
		GyroMark m = mark(e);
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
		if (m.healing(now) && e.getHealth() < e.getMaxHealth()) {
			e.heal(m.healPerTick());
		}
		return true;
	}

	/** Is {@code player} spun (his client drifts his view)? Client-safe. */
	public static boolean playerSpun(Player player) {
		GyroMark m = player.getAttachedOrElse(ModAttachments.GYRO_MARK, null);
		return m != null && m.spun(player.level().getGameTime());
	}
}

package com.projecthero.mod.quirk;

import java.lang.reflect.Method;
import java.util.Optional;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.oathbreaker.entity.OathbreakerThreat;

import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.1 Quirks revamp (Erasure / Zero Gravity builds; a small shared helper, see {@code docs/QUIRK_FRAMEWORK_0171.md}): the
 * boss threat table for Quirk moves that do no damage. A hit already feeds the table through the boss's own {@code hurt}; a
 * stare, a bind or a lift does not, so a boss would ignore whoever just erased or rooted it. {@link #add} credits the
 * {@code attacker} on the boss's shared {@link BossThreat} (looked up by its public {@code threat()} / {@code debugThreat()}
 * method, the Rena Rouge pattern) or the Oathbreaker's own table. Anything else (a plain mob) is left alone.
 */
public final class QuirkThreat {
	private static final ClassValue<Optional<Method>> THREAT = new ClassValue<>() {
		@Override
		protected Optional<Method> computeValue(Class<?> type) {
			for (String name : new String[] { "threat", "debugThreat" }) {
				try {
					Method m = type.getMethod(name);
					if (BossThreat.class.isAssignableFrom(m.getReturnType()) || OathbreakerThreat.class.isAssignableFrom(m.getReturnType())) {
						return Optional.of(m);
					}
				} catch (NoSuchMethodException ignored) {
					// next
				}
			}
			return Optional.empty();
		}
	};

	private QuirkThreat() {
	}

	/** Credits {@code attacker} with {@code amount} threat on {@code boss}'s table. True if it has one. */
	public static boolean add(LivingEntity boss, LivingEntity attacker, float amount) {
		if (boss == null || attacker == null || boss.level().isClientSide()) {
			return false;
		}
		Optional<Method> m = THREAT.get(boss.getClass());
		if (m.isEmpty()) {
			return false;
		}
		try {
			Object table = m.get().invoke(boss);
			if (table instanceof BossThreat t) {
				t.add(attacker, amount);
				return true;
			}
			if (table instanceof OathbreakerThreat t) {
				t.addHit(attacker, amount, boss.tickCount);
				return true;
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
		return false;
	}
}

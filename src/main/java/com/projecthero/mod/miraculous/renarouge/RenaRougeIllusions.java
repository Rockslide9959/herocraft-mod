package com.projecthero.mod.miraculous.renarouge;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.miraculous.renarouge.entity.RenaIllusionEntity;
import com.projecthero.mod.miraculous.renarouge.entity.RenaRougeEntityTypes;
import com.projecthero.mod.oathbreaker.entity.OathbreakerThreat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Rena Rouge's illusions -- making them, finding them, and the aggro they draw.
 *
 * <p><b>Aggro:</b> a mob that is after her (or idle and close) turns on the nearest illusion instead. A <b>boss</b> is
 * fooled through its <b>threat table</b> (the shared {@link BossThreat}, or the Oathbreaker's own), never by a bare
 * {@code setTarget} it would undo on its next rethink: the illusion is credited with more threat than whoever the boss
 * is on, and the table is applied at once. When the illusion dissolves, the table forgets it (it is no longer a valid
 * victim) and the boss goes back to the real fighters. The Titan's table counts players only, so it is never fooled.
 */
public final class RenaRougeIllusions {
	/** {@code threat()} / {@code debugThreat()} on a boss class, looked up once per class. */
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

	private RenaRougeIllusions() {
	}

	/** A new illusion of {@code owner} at {@code at}, facing {@code yaw}. */
	public static @Nullable RenaIllusionEntity spawn(ServerPlayer owner, int kind, Vec3 at, float yaw, int lifeTicks, int hits) {
		ServerLevel level = (ServerLevel) owner.level();
		RenaIllusionEntity e = RenaRougeEntityTypes.ILLUSION.create(level);
		if (e == null) {
			return null;
		}
		e.moveTo(at.x, at.y, at.z, yaw, 0f);
		e.setYHeadRot(yaw);
		e.yBodyRot = yaw;
		e.setup(owner, kind, lifeTicks, hits);
		level.addFreshEntity(e);
		RenaRougeCombat.glow(level, at.add(0, 1.0, 0), 10, 0.35);
		return e;
	}

	/** Her illusions within {@code range} (whole ones only). */
	public static List<RenaIllusionEntity> own(ServerPlayer owner, double range) {
		return owner.serverLevel().getEntitiesOfClass(RenaIllusionEntity.class, owner.getBoundingBox().inflate(range),
				e -> !e.dissolving() && owner.getUUID().equals(e.getOwnerUUID()));
	}

	/** Every one of her illusions dissolves (detransformation, logout...). */
	public static void dissolveAll(ServerPlayer owner) {
		for (RenaIllusionEntity e : owner.serverLevel().getEntitiesOfClass(RenaIllusionEntity.class, owner.getBoundingBox().inflate(256.0),
				e -> owner.getUUID().equals(e.getOwnerUUID()))) {
			e.dissolve();
		}
	}

	/**
	 * Every few ticks from each illusion: monsters after its owner (and idle ones within 10 blocks) go for it instead;
	 * bosses after her are fooled through their threat table.
	 */
	public static void drawAggro(RenaIllusionEntity illusion, ServerPlayer owner) {
		ServerLevel level = (ServerLevel) illusion.level();
		double r = RenaRougeConfig.ILLUSION_LURE_RANGE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, illusion.getBoundingBox().inflate(r),
				m -> m instanceof Enemy && !(m instanceof RenaIllusionEntity) && m.isAlive())) {
			LivingEntity t = m.getTarget();
			if (t instanceof RenaIllusionEntity) {
				continue;
			}
			boolean afterHer = t == owner;
			boolean idle = t == null && m.distanceToSqr(illusion) < 10 * 10;
			if (!afterHer && !idle) {
				continue;
			}
			if (RenaRougeCombat.isBoss(m)) {
				if (afterHer || illusion.kind() == RenaIllusionEntity.MIRAGE || illusion.kind() == RenaIllusionEntity.RUNNER) {
					taunt(m, illusion);
				}
			} else if (RenaRougeCombat.isFoolable(owner, m)) {
				retarget(m, illusion);
			}
		}
	}

	/** A plain mob turns on {@code target} (brain-driven mobs get the memory too). */
	public static void retarget(Mob m, LivingEntity target) {
		m.setTarget(target);
		if (m.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
			m.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
		}
	}

	/** A boss is fooled through its threat table (see the class comment). */
	public static void taunt(Mob boss, LivingEntity illusion) {
		Object table = null;
		Optional<Method> m = THREAT.get(boss.getClass());
		if (m.isPresent()) {
			try {
				table = m.get().invoke(boss);
			} catch (ReflectiveOperationException | RuntimeException e) {
				table = null;
			}
		}
		LivingEntity current = boss.getTarget();
		if (table instanceof BossThreat t) {
			float top = current == null ? 0f : t.threatOf(current);
			t.add(illusion, Math.max(60f, top * 2f + 30f));
			t.applyNow();
			return;
		}
		if (table instanceof OathbreakerThreat t) {
			float top = current == null ? 0f : t.threatOf(current, boss.tickCount);
			t.addHit(illusion, Math.max(60f, top * 2f + 30f), boss.tickCount);
			boss.setTarget(illusion);
			return;
		}
		boss.setTarget(illusion);
	}

	/** The nearest whole illusion of hers to {@code m}, or null. */
	public static @Nullable RenaIllusionEntity nearest(ServerPlayer owner, Mob m, double range) {
		RenaIllusionEntity best = null;
		double bestD = range * range;
		for (RenaIllusionEntity e : owner.serverLevel().getEntitiesOfClass(RenaIllusionEntity.class, m.getBoundingBox().inflate(range),
				e -> !e.dissolving() && owner.getUUID().equals(e.getOwnerUUID()))) {
			double d = e.distanceToSqr(m);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}
}

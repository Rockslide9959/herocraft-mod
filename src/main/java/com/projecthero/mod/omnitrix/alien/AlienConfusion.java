package com.projecthero.mod.omnitrix.alien;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.4 (Eye Guy's Hypno Gaze, Benwolf's Disorienting Howl): mobs turned on each other for a while. A turned mob forgets the alien who
 * turned it and goes for the nearest other mob instead, re-picked whenever its foe dies or wanders off. Never a boss (they keep their
 * threat tables), never a player. Kept by the owner's UUID and the mob's entity id only (no entity references), ticked from the owner's
 * kit, dropped when the owner turns back ({@link #clear}) and on a world switch ({@link #clearSessionState}, through
 * {@code OmnitrixMod.clearSessionState} / {@code ServerStateReset}).
 */
public final class AlienConfusion {
	private AlienConfusion() {
	}

	/** How far a turned mob looks for something else to fight. */
	public static final double FOE_RANGE = 16;

	private static final Map<UUID, Map<Integer, Long>> TURNED = new ConcurrentHashMap<>();

	public static void clearSessionState() {
		TURNED.clear();
	}

	/** Whether {@code e} can be turned at all (a mob, not a boss). */
	public static boolean turnable(LivingEntity e) {
		return e instanceof Mob && !AlienStatus.isBoss(e) && e.isAlive();
	}

	/** Turns {@code m} on the mobs round it for {@code ticks}. False if it cannot be turned. */
	public static boolean turn(ServerPlayer owner, LivingEntity e, int ticks) {
		if (!turnable(e)) {
			return false;
		}
		Mob m = (Mob) e;
		TURNED.computeIfAbsent(owner.getUUID(), k -> new ConcurrentHashMap<>()).put(m.getId(), owner.level().getGameTime() + ticks);
		AlienStatus.loseTrack(m, owner);
		if (m.getTarget() == owner) {
			m.setTarget(null);
		}
		retarget(owner, m);
		return true;
	}

	/** Whether {@code e} is turned by {@code owner} right now. */
	public static boolean turned(ServerPlayer owner, Entity e) {
		Map<Integer, Long> mine = TURNED.get(owner.getUUID());
		Long until = mine == null ? null : mine.get(e.getId());
		return until != null && until > owner.level().getGameTime();
	}

	/** Every tick from the owner's kit: keep each turned mob on another mob; let the expired ones go. */
	public static void tick(ServerPlayer owner) {
		Map<Integer, Long> mine = TURNED.get(owner.getUUID());
		if (mine == null || mine.isEmpty()) {
			return;
		}
		ServerLevel l = (ServerLevel) owner.level();
		long now = l.getGameTime();
		for (Map.Entry<Integer, Long> en : mine.entrySet()) {
			Entity e = l.getEntity(en.getKey());
			if (!(e instanceof Mob m) || !m.isAlive()) {
				mine.remove(en.getKey());
				continue;
			}
			if (now >= en.getValue()) {
				mine.remove(en.getKey());
				if (m.getTarget() instanceof Mob) {
					m.setTarget(null); // back to its senses
				}
				continue;
			}
			LivingEntity t = m.getTarget();
			if (t == null || !t.isAlive() || t instanceof Player || t.distanceToSqr(m) > FOE_RANGE * FOE_RANGE) {
				retarget(owner, m);
			}
			if (now % 10 == 0) {
				l.sendParticles(ParticleTypes.WITCH, m.getX(), m.getY() + m.getBbHeight() + 0.2, m.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
			}
		}
		if (mine.isEmpty()) {
			TURNED.remove(owner.getUUID());
		}
	}

	/** Lets every mob {@code owner} turned go at once (he turned back). */
	public static void clear(ServerPlayer owner) {
		Map<Integer, Long> mine = TURNED.remove(owner.getUUID());
		if (mine == null) {
			return;
		}
		for (Integer id : mine.keySet()) {
			if (owner.level().getEntity(id) instanceof Mob m && m.getTarget() instanceof Mob) {
				m.setTarget(null);
			}
		}
	}

	private static void retarget(ServerPlayer owner, Mob m) {
		LivingEntity foe = nearestFoe(owner, m);
		m.setTarget(foe);
	}

	/** The nearest other mob to {@code m} (never a player, never a stand, never a squadmate's pet). */
	@Nullable
	private static LivingEntity nearestFoe(ServerPlayer owner, Mob m) {
		LivingEntity best = null;
		double bd = Double.MAX_VALUE;
		for (LivingEntity o : m.level().getEntitiesOfClass(LivingEntity.class, new AABB(m.blockPosition()).inflate(FOE_RANGE),
				o -> o != m && o.isAlive() && !(o instanceof Player) && !(o instanceof ArmorStand) && o instanceof Mob && !o.isSpectator())) {
			if (com.projecthero.mod.omnitrix.OmnitrixRiding.partners(owner, o)) {
				continue;
			}
			double d = o.distanceToSqr(m);
			if (d < bd) {
				bd = d;
				best = o;
			}
		}
		return best;
	}
}

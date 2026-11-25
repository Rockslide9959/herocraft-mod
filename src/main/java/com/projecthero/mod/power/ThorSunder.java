package com.projecthero.mod.power;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * v0.17.7 Cosmic revamp: Sunder, from Stormbreaker's Bifrost Hurl -- the victim takes {@link #BONUS} (+20%) more damage from
 * every source for {@link #TICKS} (8 s). Applied in the one hurt hook ({@link ThorStorm#enter}), so it stacks with the storm
 * buff exactly once and never on a hit re-issued inside the same {@code hurt}. A Thor attacker's hit is still capped at
 * {@link ThorKit#MAX_HIT} there.
 *
 * <p>State is a static map of victim UUID to expiry (game time of the victim's level) plus the entity id for the rainbow
 * shimmer -- no entity or world references; cleared on server stop by {@code ServerStateReset}.
 */
public final class ThorSunder {
	public static final float BONUS = 0.20f;
	public static final int TICKS = 8 * 20;

	private record Mark(long until, ResourceKey<Level> dim, int entityId) {
	}

	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	private ThorSunder() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(ThorSunder::tick);
	}

	public static void apply(LivingEntity victim) {
		if (victim.level().isClientSide()) {
			return;
		}
		MARKS.put(victim.getUUID(), new Mark(victim.level().getGameTime() + TICKS, victim.level().dimension(), victim.getId()));
		if (victim.level() instanceof ServerLevel level) {
			ThorKit.prism(level, victim.position().add(0, victim.getBbHeight() * 0.5, 0), 26, 0.45, victim.getBbHeight() * 0.4);
		}
	}

	public static boolean isSundered(Entity victim) {
		if (MARKS.isEmpty()) {
			return false;
		}
		Mark m = MARKS.get(victim.getUUID());
		return m != null && victim.level().getGameTime() < m.until;
	}

	/** The incoming-damage multiplier on {@code victim}: 1.2 while Sundered, else 1. */
	public static float multiplier(Entity victim) {
		return isSundered(victim) ? 1.0f + BONUS : 1.0f;
	}

	private static void tick(MinecraftServer server) {
		if (MARKS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Mark>> it = MARKS.entrySet().iterator();
		while (it.hasNext()) {
			Mark m = it.next().getValue();
			ServerLevel level = server.getLevel(m.dim);
			if (level == null || level.getGameTime() >= m.until) {
				it.remove();
				continue;
			}
			if (level.getGameTime() % 4 == 0) {
				Entity e = level.getEntity(m.entityId);
				if (e == null || !e.isAlive()) {
					it.remove();
					continue;
				}
				// the cracked, prismatic shimmer of a sundered foe
				ThorKit.prism(level, e.position().add(0, e.getBbHeight() * 0.5, 0), 3, 0.3, e.getBbHeight() * 0.35);
			}
		}
	}

	public static void clearSessionState() {
		MARKS.clear();
	}
}

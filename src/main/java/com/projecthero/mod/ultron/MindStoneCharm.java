package com.projecthero.mod.ultron;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * v0.15.12: the Mind Stone's charm. A charmed mob, for its few seconds, never targets a player and is pointed at the
 * nearest hostile instead (re-asserted every tick, so its own target goals can't win it back); when the time is up it
 * simply forgets its target and goes back to its nature. Static per-server state (entity ids only), cleared in
 * {@code ServerStateReset}.
 */
public final class MindStoneCharm {
	private record Charm(ResourceKey<Level> dimension, UUID owner, long until) {
	}

	private static final Map<UUID, Charm> CHARMED = new HashMap<>();
	public static final double HUNT_RADIUS = 16.0;

	private MindStoneCharm() {
	}

	public static void clearSessionState() {
		CHARMED.clear();
	}

	public static void charm(ServerLevel level, Mob mob, ServerPlayer owner, int ticks) {
		CHARMED.put(mob.getUUID(), new Charm(level.dimension(), owner.getUUID(), level.getGameTime() + ticks));
		mob.setTarget(null);
		mob.getNavigation().stop();
		retarget(level, mob);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 10, 0.4, 0.3, 0.4, 0.0);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.7f);
	}

	public static boolean isCharmed(Entity e) {
		return CHARMED.containsKey(e.getUUID());
	}

	/** Every server tick. */
	public static void tick(MinecraftServer server) {
		if (CHARMED.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Charm>> it = CHARMED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Charm> en = it.next();
			Charm c = en.getValue();
			ServerLevel level = server.getLevel(c.dimension());
			Entity e = level == null ? null : level.getEntity(en.getKey());
			if (!(e instanceof Mob mob) || !mob.isAlive()) {
				it.remove();
				continue;
			}
			if (level.getGameTime() >= c.until()) {
				it.remove();
				mob.setTarget(null);
				level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 6, 0.3, 0.2, 0.3, 0.01);
				continue;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || t instanceof Player || isCharmed(t)) {
				retarget(level, mob);
			}
			if (mob.tickCount % 10 == 0) {
				level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getEyeY() + 0.5, mob.getZ(), 4, 0.3, 0.2, 0.3, 0.2);
			}
		}
	}

	private static void retarget(ServerLevel level, Mob mob) {
		LivingEntity best = null;
		double bestD = HUNT_RADIUS * HUNT_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(HUNT_RADIUS),
				x -> x instanceof Enemy && x != mob && x.isAlive() && !isCharmed(x))) {
			double d = e.distanceToSqr(mob);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		mob.setTarget(best);
	}
}

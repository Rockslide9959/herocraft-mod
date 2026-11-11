package com.projecthero.mod.gyro;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.3 Gyro Zeppeli, Shift+V Face Spin: the Spin reshapes his face for 20 s. His name tag is hidden (client,
 * {@code GyroNameTagMixin}) and mobs leave him alone -- except the <b>witnesses</b>: anything he strikes while disguised, and every
 * mob within {@link GyroConfig#FACE_WITNESS_RANGE} that can see him when he does. The disguise starts with no witnesses (whoever
 * was chasing him loses him); {@code mixin.GyroMobTargetMixin} refuses a non-witness's target, and {@link #tick} sweeps the
 * brain-driven mobs that keep theirs in a memory.
 *
 * <p>{@link #WITNESSES} holds UUIDs only, per disguised player; {@code ServerStateReset} clears it all the same.
 */
public final class GyroDisguise {
	private static final Map<UUID, Set<UUID>> WITNESSES = new ConcurrentHashMap<>();

	private GyroDisguise() {
	}

	static void clearSessionState() {
		WITNESSES.clear();
	}

	static void forget(UUID id) {
		WITNESSES.remove(id);
	}

	/** Face Spin begins: no witnesses, and everything hunting him drops him. */
	static void start(ServerPlayer player) {
		WITNESSES.put(player.getUUID(), ConcurrentHashMap.newKeySet());
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0), m -> hunts(m, player))) {
			mob.setTarget(null);
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	private static boolean hunts(Mob mob, Player player) {
		return mob.getTarget() == player
				|| mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).map(t -> t == player).orElse(false);
	}

	/** May {@code mob} target the (possibly disguised) {@code player}? */
	public static boolean mayTarget(Mob mob, Player player) {
		if (!Gyro.disguised(player)) {
			return true;
		}
		Set<UUID> seen = WITNESSES.get(player.getUUID());
		return seen != null && seen.contains(mob.getUUID());
	}

	/** He hit {@code victim} while disguised: the victim and every mob that saw it are witnesses now. */
	static void onStrike(ServerPlayer player, LivingEntity victim) {
		if (!Gyro.disguised(player)) {
			return;
		}
		Set<UUID> seen = WITNESSES.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
		seen.add(victim.getUUID());
		AABB box = player.getBoundingBox().inflate(GyroConfig.FACE_WITNESS_RANGE);
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box, m -> m.hasLineOfSight(player))) {
			seen.add(mob.getUUID());
		}
	}

	/** For the tests. */
	public static boolean isWitness(Player player, LivingEntity mob) {
		Set<UUID> seen = WITNESSES.get(player.getUUID());
		return seen != null && seen.contains(mob.getUUID());
	}

	static void tick(ServerPlayer player) {
		if (!Gyro.disguised(player)) {
			if (WITNESSES.containsKey(player.getUUID())) {
				WITNESSES.remove(player.getUUID());
			}
			return;
		}
		if (player.tickCount % 10 != 0) {
			return;
		}
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0), m -> hunts(m, player))) {
			if (!mayTarget(mob, player)) {
				mob.setTarget(null);
				mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
		}
	}
}

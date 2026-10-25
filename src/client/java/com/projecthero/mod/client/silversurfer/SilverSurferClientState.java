package com.projecthero.mod.client.silversurfer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.silversurfer.SilverSurfer;
import com.projecthero.mod.silversurfer.network.SurferStatuePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Silver Surfer's client-side scratch state -- Transmutation's silver statues (from {@link SurferStatuePayload}),
 * each surfer's bank angle into turns, and the recent positions his silver streak is drawn through. Cleared on disconnect.
 */
public final class SilverSurferClientState {
	/** Entity id -> client game time its statue ends. */
	private static final Map<Integer, Long> STATUES = new HashMap<>();
	/** Entity id -> [last yaw, bank, previous bank]. */
	private static final Map<Integer, float[]> BANK = new HashMap<>();
	/** Entity id -> recent positions (newest first). */
	private static final Map<Integer, Deque<Vec3>> TRAILS = new HashMap<>();

	private SilverSurferClientState() {
	}

	static void reset() {
		STATUES.clear();
		BANK.clear();
		TRAILS.clear();
	}

	static void receive(SurferStatuePayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (payload.ticks() <= 0) {
			STATUES.remove(payload.entityId());
		} else {
			STATUES.put(payload.entityId(), mc.level.getGameTime() + payload.ticks());
		}
	}

	public static boolean statue(Entity e) {
		Long until = STATUES.get(e.getId());
		return until != null && e.level().isClientSide() && until > e.level().getGameTime();
	}

	/** Degrees he leans into the turn (negative = left). */
	public static float bank(Player p, float partial) {
		float[] b = BANK.get(p.getId());
		return b == null ? 0f : Mth.lerp(partial, b[2], b[1]);
	}

	static Deque<Vec3> trail(Player p) {
		return TRAILS.get(p.getId());
	}

	static void tick(Minecraft mc) {
		if (mc.level == null) {
			reset();
			return;
		}
		long now = mc.level.getGameTime();
		STATUES.values().removeIf(until -> until <= now);
		for (Player p : mc.level.players()) {
			int id = p.getId();
			if (!SilverSurfer.surfing(p)) {
				BANK.remove(id);
				TRAILS.remove(id);
				continue;
			}
			float[] b = BANK.computeIfAbsent(id, k -> new float[] { p.getYRot(), 0f, 0f });
			float dyaw = Mth.wrapDegrees(p.getYRot() - b[0]);
			b[0] = p.getYRot();
			float target = Mth.clamp(dyaw * 2.2f, -38f, 38f);
			b[2] = b[1];
			b[1] += (target - b[1]) * 0.25f;
			boolean light = SilverSurfer.lightSpeed(p);
			Deque<Vec3> trail = TRAILS.computeIfAbsent(id, k -> new ArrayDeque<>());
			trail.addFirst(p.position());
			int max = light ? 26 : 14;
			while (trail.size() > max) {
				trail.removeLast();
			}
			// the cosmic sparkle off the board
			Vec3 v = p.getDeltaMovement();
			int n = light ? 4 : v.lengthSqr() > 1.0 ? 2 : 1;
			for (int i = 0; i < n; i++) {
				mc.level.addParticle(i == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
						p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.9, p.getY() - 0.1, p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.9,
						-v.x * 0.05, 0.01, -v.z * 0.05);
			}
		}
	}
}

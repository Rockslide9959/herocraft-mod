package com.herocraft.mod.maxsteel;

import com.herocraft.mod.network.MaxSteelWarningPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Steel's threat-awareness passive: a quiet chirp and a short directional HUD marker when a hostile
 * projectile is closing on the player from <em>outside their view</em>.
 *
 * <p><b>This is deliberately not Spider-Sense.</b> It never dodges, never catches an arrow, never
 * slows time -- that behaviour belongs to the Spider-Man power. It only tells the player something is
 * coming. The scan is cheap: once every few ticks, only while transformed, over a small box, and
 * rate-limited so it cannot spam.
 */
public final class MaxSteelSense {
	private static final String COOLDOWN_KEY = "steel_warning";
	private static final int SCAN_INTERVAL = 4;
	private static final int WARN_COOLDOWN_TICKS = 30;
	private static final double SCAN_RADIUS = 16.0;

	private MaxSteelSense() {
	}

	public static void tick(ServerPlayer player) {
		if (!MaxSteel.isTransformed(player) || player.tickCount % SCAN_INTERVAL != 0) {
			return;
		}
		long now = player.level().getGameTime();
		Long readyAt = MaxSteel.state(player).abilityReadyAt.get(COOLDOWN_KEY);
		if (readyAt != null && now < readyAt) {
			return;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		AABB box = player.getBoundingBox().inflate(SCAN_RADIUS);

		for (Projectile p : player.level().getEntitiesOfClass(Projectile.class, box, MaxSteelSense::isThreatening)) {
			if (p.getOwner() == player) {
				continue;
			}
			Vec3 toPlayer = eye.subtract(p.position());
			double dist = toPlayer.length();
			if (dist < 1.5 || dist > SCAN_RADIUS) {
				continue;
			}
			Vec3 vel = p.getDeltaMovement();
			if (vel.lengthSqr() < 0.01) {
				continue;
			}
			// heading roughly at the player?
			if (vel.normalize().dot(toPlayer.normalize()) < 0.6) {
				continue;
			}
			// outside the player's view? (they are not looking near it)
			Vec3 toThreat = p.position().subtract(eye).normalize();
			if (look.dot(toThreat) > 0.25) {
				continue;
			}

			float yaw = (float) (Math.atan2(toThreat.z, toThreat.x) * (180.0 / Math.PI)) - 90.0f;
			ServerPlayNetworking.send(player, new MaxSteelWarningPayload(yaw));
			player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.35f, 2.0f);
			MaxSteel.triggerCooldown(player, COOLDOWN_KEY, WARN_COOLDOWN_TICKS);
			return;
		}
	}

	private static boolean isThreatening(Projectile p) {
		return p.isAlive() && !p.onGround();
	}
}

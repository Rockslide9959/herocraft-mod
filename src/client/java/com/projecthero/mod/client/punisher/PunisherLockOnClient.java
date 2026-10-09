package com.projecthero.mod.client.punisher;

import com.projecthero.mod.client.firearm.FirearmClient;
import com.projecthero.mod.network.PunisherLockOnPayload;
import com.projecthero.mod.punisher.ability.PunisherWeaponAbilities;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.18: the client half of the Punisher's Shift+V lock-on (rifle / sniper Weapon Ability). While the server has
 * us locked onto an entity, mouse look is ignored ({@code PunisherLockOnMouseMixin}) and the camera turns smoothly onto
 * the target's upper body every frame instead; the sniper's lock also raises the scope. The server decides every shot's
 * direction itself -- this is only so the player sees what the gun is doing.
 */
public final class PunisherLockOnClient {
	/** How quickly the view closes on the target (per second, exponential). */
	private static final double TURN_RATE = 14.0;
	/** Release on our own if the target stays missing this long (e.g. it unloaded) and the server's release is lost. */
	private static final int MISSING_TICKS = 20;

	private static int targetId = -1;
	private static boolean scoped;
	private static int missing;
	private static long lastFrameNanos;

	private PunisherLockOnClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PunisherLockOnPayload.TYPE,
				(payload, context) -> context.client().execute(() -> accept(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(PunisherLockOnClient::tick);
	}

	private static void accept(PunisherLockOnPayload payload) {
		if (payload.entityId() < 0) {
			release();
			return;
		}
		targetId = payload.entityId();
		missing = 0;
		lastFrameNanos = 0L;
		if (payload.scope() != scoped) {
			scoped = payload.scope();
			FirearmClient.forceAim(scoped);
		}
	}

	private static void release() {
		targetId = -1;
		missing = 0;
		if (scoped) {
			scoped = false;
			FirearmClient.forceAim(false);
		}
	}

	public static boolean locked() {
		return targetId >= 0;
	}

	private static void tick(Minecraft mc) {
		if (targetId < 0) {
			return;
		}
		if (mc.player == null || mc.level == null) {
			release();
			return;
		}
		Entity target = mc.level.getEntity(targetId);
		missing = target == null || !target.isAlive() ? missing + 1 : 0;
		if (missing > MISSING_TICKS) {
			release();
		}
	}

	/** Called every frame in place of mouse look while locked: turn the view toward the target. */
	public static void steer() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		Entity target = mc.level.getEntity(targetId);
		long nowNanos = System.nanoTime();
		double dt = lastFrameNanos == 0L ? 0.0 : Math.min(0.1, (nowNanos - lastFrameNanos) / 1.0e9);
		lastFrameNanos = nowNanos;
		if (target == null || dt <= 0.0) {
			return;
		}
		float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
		Vec3 base = target.getPosition(partial);
		Vec3 aim = base.add(PunisherWeaponAbilities.aimPoint(target).subtract(target.position()));
		Vec3 to = aim.subtract(player.getEyePosition(partial));
		double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
		float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
		float wantPitch = (float) (-(Mth.atan2(to.y, horiz) * Mth.RAD_TO_DEG));
		double k = 1.0 - Math.exp(-TURN_RATE * dt);
		float dYaw = (float) (Mth.wrapDegrees(wantYaw - player.getYRot()) * k);
		float dPitch = (float) ((wantPitch - player.getXRot()) * k);
		// Entity#turn scales its input by 0.15 (mouse units), so feed it degrees / 0.15
		player.turn(dYaw / 0.15, dPitch / 0.15);
	}
}

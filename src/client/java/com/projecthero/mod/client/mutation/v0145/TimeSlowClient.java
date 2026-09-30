package com.projecthero.mod.client.mutation.v0145;

import java.util.UUID;

import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.network.TimeSlowStatePayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;

/**
 * v0.14.7: the client half of the game-wide Time Slow.
 *
 * <p>Vanilla syncs the slowed tick rate to every client, which then runs its whole simulation at 1 tick a second --
 * that is how everyone but the caster gets slowed. The caster's own client must keep living at 20: its timer stays
 * at 50 ms a tick ({@code SuperSpeedTimeSlowMinecraftMixin} on {@code Minecraft#getTickTargetMillis}), and on 19 of every
 * 20 of its ticks the rest of the world -- other entities, particles, block entities, the level clock (sky, day /
 * night), clouds, rain and animated textures -- is held back ({@link #skipWorldTick}), so it all moves at the same 5%
 * the server runs it at while the caster's own movement, camera, hands and HUD run at full speed.
 */
public final class TimeSlowClient {
	private static boolean active;
	private static UUID caster;
	private static long counter;
	private static boolean skip;
	/** Real-time stamps (ms) of the last start / end, for the screen effect's ripples. */
	static long startedMs = Long.MIN_VALUE / 2;
	static long endedMs = Long.MIN_VALUE / 2;

	private TimeSlowClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(TimeSlowStatePayload.TYPE,
				(payload, context) -> context.client().execute(() -> accept(payload)));
		ClientTickEvents.START_CLIENT_TICK.register(TimeSlowClient::tick);
	}

	private static void accept(TimeSlowStatePayload p) {
		if (p.active() && !active) {
			startedMs = Util.getMillis();
		} else if (!p.active() && active) {
			endedMs = Util.getMillis();
		}
		active = p.active();
		caster = p.active() ? p.caster() : null;
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			active = false;
			caster = null;
			skip = false;
			return;
		}
		counter++;
		skip = isLocalCaster() && counter % SuperSpeedTimeSlow.TICK_DIVISOR != 0;
	}

	public static boolean active() {
		return active;
	}

	/** Whether this client's player is the one who cast the running Time Slow. */
	public static boolean isLocalCaster() {
		Minecraft mc = Minecraft.getInstance();
		return active && caster != null && mc.player != null && caster.equals(mc.player.getUUID());
	}

	/** Whether the rest of the world should sit this client tick out (only ever on the caster's own client). */
	public static boolean skipWorldTick() {
		return skip;
	}
}

package com.projecthero.mod.client.punisher;

import com.projecthero.mod.network.PunisherIntelPayload;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

/**
 * v0.15.18: the local Punisher's private target picture -- his Target Designation mark and what his Threat Assessment
 * picked up -- fed by {@link PunisherIntelPayload} and drawn as a glow by {@code EntityGlowMixin} in his own render
 * only. Nothing is ever set on the entities, so no other player sees it.
 */
public final class PunisherIntelClient {
	/** Mark: red. Threat Assessment: orange for hostiles, pale grey for everything else. */
	public static final int MARK_COLOR = 0xFF1E1E;
	public static final int THREAT_HOSTILE_COLOR = 0xFF9A2E;
	public static final int THREAT_OTHER_COLOR = 0xE2E2E2;

	private static int markId = -1;
	private static long markUntil;
	private static long markTotal;
	private static final Int2LongOpenHashMap THREATS = new Int2LongOpenHashMap();

	private PunisherIntelClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(PunisherIntelPayload.TYPE, (payload, context) -> context.client().execute(() -> {
			long now = context.client().level != null ? context.client().level.getGameTime() : 0L;
			accept(payload, now);
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	static void accept(PunisherIntelPayload p, long now) {
		if (p.markId() == PunisherIntelPayload.CLEAR_MARK) {
			markId = -1;
		} else if (p.markId() >= 0) {
			markId = p.markId();
			markUntil = now + p.markTicks();
			markTotal = p.markTicks();
		}
		for (int id : p.threats()) {
			THREATS.put(id, now + p.threatTicks());
		}
		THREATS.values().removeIf(t -> t < now);
	}

	public static boolean isMarked(int id, long now) {
		return markId >= 0 && id == markId && now < markUntil;
	}

	public static boolean isThreat(int id, long now) {
		return THREATS.getOrDefault(id, -1L) >= now;
	}

	/** Ticks left on the mark, or 0 when there is none (the HUD). */
	public static int markTicksLeft(long now) {
		if (markId < 0 || now >= markUntil) {
			return 0;
		}
		// the marked thing is gone from this client's world (killed / out of range): nothing to show
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level.getEntity(markId) == null) {
			return 0;
		}
		return (int) (markUntil - now);
	}

	public static void clear() {
		markId = -1;
		THREATS.clear();
	}
}

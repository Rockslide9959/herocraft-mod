package com.projecthero.mod.client.alarm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.alarm.AlarmState;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.network.AlarmStatusPayload;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * v0.17.7: the alarm framework's client side -- what the server last said about the zone this player stands in (the
 * HUD eye), which guards show a "?" or "!", and the Snipers' laser lines; plus the Security Camera / Laser Tripwire
 * renderers. Client-only statics, cleared on disconnect and when the server says the player left the zone (they hold
 * entity ids, never entities).
 */
public final class AlarmClient {
	/** -1 = not in a zone. */
	private static int state = AlarmStatusPayload.NONE;
	private static final Map<Integer, Integer> MARKS = new HashMap<>();
	private static final List<int[]> LASERS = new ArrayList<>();
	private static int staleTicks;

	private AlarmClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(AlarmStatusPayload.TYPE, (payload, context) -> receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (state != AlarmStatusPayload.NONE && ++staleTicks > 40) {
				clear(); // the server stopped talking (left the zone without the "none", world change)
			}
		});
		HudRenderCallback.EVENT.register(AlarmHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(AlarmWorldRender::render);
		BlockEntityRenderers.register(AlarmBlocks.CAMERA_BE, SecurityCameraRenderer::new);
		BlockEntityRenderers.register(AlarmBlocks.LASER_BE, LaserTripwireRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(AlarmBlocks.CAMERA, RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(AlarmBlocks.LASER, RenderType.cutout());
	}

	static void receive(AlarmStatusPayload p) {
		staleTicks = 0;
		state = p.state();
		MARKS.clear();
		LASERS.clear();
		if (state == AlarmStatusPayload.NONE) {
			return;
		}
		for (int v : p.marks()) {
			MARKS.put(v >> 2, v & 3);
		}
		List<Integer> l = p.lasers();
		for (int i = 0; i + 2 < l.size(); i += 3) {
			LASERS.add(new int[] { l.get(i), l.get(i + 1), l.get(i + 2) });
		}
	}

	static void clear() {
		state = AlarmStatusPayload.NONE;
		MARKS.clear();
		LASERS.clear();
		staleTicks = 0;
	}

	/** The zone state for the HUD, or null outside zones. */
	public static AlarmState state() {
		return state < 0 ? null : AlarmState.byId(state);
	}

	/** Entity id -> 1 ("?") or 2 ("!"). */
	public static Map<Integer, Integer> marks() {
		return MARKS;
	}

	/** {sniperId, targetId, bright} triples. */
	public static List<int[]> lasers() {
		return LASERS;
	}
}

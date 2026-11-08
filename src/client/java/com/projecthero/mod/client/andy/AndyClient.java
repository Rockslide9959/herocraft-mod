package com.projecthero.mod.client.andy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyCloneEntity;
import com.projecthero.mod.andy.entity.AndyEntityTypes;
import com.projecthero.mod.andy.entity.AndyPartEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Andy's client side -- the layer, the HUD, the pixel cubes, the beams, the flying pieces and clones. Watches every
 * Andy in view: a part that has just broken (the synced break time changed) bursts into pixels here, so every viewer sees it.
 */
public final class AndyClient {
	/** Per player: the last break time seen for each part. */
	private static final Map<UUID, long[]> SEEN = new HashMap<>();

	private AndyClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new AndyLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(AndyHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(AndyEffectsRenderer::render);
		EntityRendererRegistry.register(AndyEntityTypes.PART, AndyPartRenderer::new);
		EntityRendererRegistry.register(AndyEntityTypes.CLONE, AndyCloneRenderer::new);
		AndyPartEntity.clientRemoval = AndyPartRenderer::removed;
		AndyCloneEntity.clientRemoval = AndyCloneRenderer::removed;
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(AndyClient::tick);
		AndySounds.initialize(); // v0.17.3 Andy sounds: regrowth beds + accents, jets, burn, punch
	}

	static void reset() {
		SEEN.clear();
		PixelCubes.reset();
		SkinPixels.reset();
		AndyRender.reset();
		AndyPartRenderer.LAST.clear();
		AndyCloneRenderer.forgetAll();
		AndyEffectsRenderer.reset();
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			if (!SEEN.isEmpty() || PixelCubes.count() > 0) {
				reset();
			}
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		PixelCubes.tick();
		long now = mc.level.getGameTime();
		java.util.Set<UUID> present = new java.util.HashSet<>();
		for (Player player : mc.level.players()) {
			AndyState s = Andy.peek(player);
			if (s == null || !s.hasPower) {
				continue;
			}
			present.add(player.getUUID());
			long[] seen = SEEN.computeIfAbsent(player.getUUID(), k -> {
				long[] a = new long[AndyState.PARTS];
				for (int i = 0; i < AndyState.PARTS; i++) {
					a[i] = s.brokenAt(i); // first sight: nothing bursts that broke before we saw him
				}
				return a;
			});
			for (int i = 0; i < AndyState.PARTS; i++) {
				long at = s.brokenAt(i);
				if (at != seen[i]) {
					seen[i] = at;
					int kind = s.kind(i);
					if (at > 0L && now - at < 20L && (kind == AndyState.KIND_BURST || kind == AndyState.KIND_SHATTER)) {
						burst(player, i, kind == AndyState.KIND_SHATTER ? 1.5f : 1f);
					}
				}
			}
			regrowSounds(mc, player, s, now);
			AndyEffectsRenderer.watch(player, s, now);
		}
		if (now % 100 == 0) {
			SEEN.keySet().retainAll(present);
		}
	}

	/** A part of {@code player} bursts into its skin's pixels, from where it was drawn. */
	public static void burst(Player player, int part, float power) {
		Vec3 push = player.getDeltaMovement().scale(0.6);
		PixelCubes.burst(AndyRender.skin(player), AndyRender.slim(player), part, AndyRender.partMatrix(player, part), push, 0xFFFFFFFF, power);
	}

	/** The flesh rebuilding itself: bone grind, wet muscle, tightening skin and a snap per part ({@link AndySounds#watch}). */
	private static void regrowSounds(Minecraft mc, Player player, AndyState s, long now) {
		AndySounds.watch(player, s, now);
	}
}

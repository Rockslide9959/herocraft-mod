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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Andy's client side -- the layer, the HUD, the pixel cubes, the beams, the flying pieces and clones. Watches every
 * Andy in view: a part that has just broken (the synced break time changed) bursts into pixels here, so every viewer sees it.
 */
public final class AndyClient {
	/** Per player: the last break time seen for each part. */
	private static final Map<UUID, long[]> SEEN = new HashMap<>();
	/** Per player: flying last tick, the arms' bites last tick (x1000), the burn ticks last tick. */
	private static final Map<UUID, long[]> SEEN_CUTS = new HashMap<>();
	/** The boost (right click held while flying) as last told to the server. */
	private static boolean boostSent;

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
	}

	static void reset() {
		SEEN.clear();
		SEEN_CUTS.clear();
		boostSent = false;
		BloodFx.reset();
		AndyMotion.reset();
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
		BloodFx.tick();
		boost(mc);
		AndyFirstPerson.tick(mc.player == null ? null : Andy.peek(mc.player));
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
					} else if (at > 0L && now - at < 20L && kind == AndyState.KIND_LAUNCHED) {
						AndyBlood.stumpSpurt(player, i, 1.2f); // thrown off: the stump spurts
					}
				}
			}
			regrowSounds(mc, player, s, now);
			AndyEffectsRenderer.watch(player, s, now);
			AndyMotion.tick(player, s);
			watchCuts(player, s, now);
			AndyBlood.tick(player, s, now);
		}
		AndyEffectsRenderer.tickBlood(now);
		if (now % 100 == 0) {
			SEEN.keySet().retainAll(present);
			SEEN_CUTS.keySet().retainAll(present);
			AndyMotion.forgetMissing(present);
		}
	}

	/** The local player's right click while flying is the boost: sent on the edges. */
	private static void boost(Minecraft mc) {
		boolean want = mc.player != null && mc.screen == null && mc.options.keyUse.isDown() && com.projecthero.mod.andy.AndyFlight.isFlying(mc.player);
		if (want != boostSent && mc.getConnection() != null) {
			boostSent = want;
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.projecthero.mod.network.AndyBoostPayload(want));
		}
	}

	/**
	 * The ends that come off without the part breaking: at take-off the hands and feet burst off (their texels as pixel cubes,
	 * blood spurting); a finger shot's bite out of an arm bursts the texels it took.
	 */
	private static void watchCuts(Player player, AndyState s, long now) {
		long[] seen = SEEN_CUTS.computeIfAbsent(player.getUUID(), k -> new long[] { s.flags & AndyState.FLAG_PROPELLING, s.ex(AndyState.X_EATEN_R),
				s.ex(AndyState.X_EATEN_L) });
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		if (flying && seen[0] == 0L) {
			float cut = 12f - com.projecthero.mod.andy.AndyConfig.HAND_ROWS;
			for (int part = AndyState.RIGHT_ARM; part <= AndyState.LEFT_LEG; part++) {
				if (!s.broken(part)) {
					burstEnd(player, part, cut, 99f, 1.1f);
					AndyBlood.stumpEndSpurt(player, part, cut, 1.4f);
				}
			}
		}
		seen[0] = flying ? 1L : 0L;
		for (int k = 0; k < 2; k++) {
			int arm = AndyState.RIGHT_ARM + k;
			long e = s.ex(k == 0 ? AndyState.X_EATEN_R : AndyState.X_EATEN_L);
			if (e > seen[1 + k] && !s.broken(arm)) {
				float from = 12f * (1f - e / 1000f), to = 12f * (1f - seen[1 + k] / 1000f);
				burstEnd(player, arm, from, seen[1 + k] == 0L ? 99f : to, 0.6f);
				AndyBlood.stumpEndSpurt(player, arm, from, e - seen[1 + k] > 200 ? 1.4f : 0.6f);
			}
			seen[1 + k] = e;
		}
	}

	/** The texels of a limb between {@code from} and {@code to} rows burst off as pixel cubes. */
	static void burstEnd(Player player, int part, float from, float to, float power) {
		boolean slim = AndyRender.slim(player);
		PixelCubes.burst(AndyRender.skin(player), slim, part, AndyRender.partMatrix(player, part), player.getDeltaMovement().scale(0.5), 0xFFFFFFFF, power,
				AndyRegrow.texelsBetween(part, slim, from - 0.3f, to));
	}

	/** A part of {@code player} bursts into its skin's pixels, from where it was drawn. */
	public static void burst(Player player, int part, float power) {
		Vec3 push = player.getDeltaMovement().scale(0.6);
		PixelCubes.burst(AndyRender.skin(player), AndyRender.slim(player), part, AndyRender.partMatrix(player, part), push, 0xFFFFFFFF, power);
		AndyBlood.partBurst(player, part, power);
	}

	/** A soft run of clicks while a part re-forms, rising as it fills. */
	private static void regrowSounds(Minecraft mc, Player player, AndyState s, long now) {
		if (now % 3 != 0 || mc.player == null || player.distanceToSqr(mc.player) > 24 * 24) {
			return;
		}
		for (int i = 0; i < AndyState.PARTS; i++) {
			float p = s.regrowth(i, now);
			if (s.broken(i) && p > 0.05f && p < 1f) {
				mc.level.playLocalSound(player.getX(), player.getY() + 1, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
						0.18f, 1.1f + p * 0.9f, false);
				return;
			}
		}
	}
}

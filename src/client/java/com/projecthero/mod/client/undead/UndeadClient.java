package com.projecthero.mod.client.undead;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;
import com.projecthero.mod.undead.entity.UndeadEntityTypes;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

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
 * v0.17.3: Undead's client side -- the layer, the HUD, the pixel cubes, the beams, the flying pieces and clones. Watches every
 * Undead in view: a part that has just broken (the synced break time changed) bursts into pixels here, so every viewer sees it.
 */
public final class UndeadClient {
	/** Per player: the last break time seen for each part. */
	private static final Map<UUID, long[]> SEEN = new HashMap<>();
	/** Per player: flying last tick, the arms' bites last tick (x1000), the burn ticks last tick. */
	private static final Map<UUID, long[]> SEEN_CUTS = new HashMap<>();
	/** Other players: the stage their box was last refreshed for. */
	private static final Map<UUID, Integer> SEEN_STAGE = new HashMap<>();
	/** The boost (right click held while flying) as last told to the server. */
	private static boolean boostSent;

	private UndeadClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new UndeadLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(UndeadHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(UndeadEffectsRenderer::render);
		EntityRendererRegistry.register(UndeadEntityTypes.PART, UndeadPartRenderer::new);
		EntityRendererRegistry.register(UndeadEntityTypes.CLONE, UndeadCloneRenderer::new);
		UndeadPartEntity.clientRemoval = UndeadPartRenderer::removed;
		UndeadCloneEntity.clientRemoval = UndeadCloneRenderer::removed;
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(UndeadClient::tick);
		UndeadSounds.initialize(); // v0.17.3 Undead sounds: regrowth beds + accents, jets, burn, punch
	}

	static void reset() {
		SEEN.clear();
		SEEN_CUTS.clear();
		SEEN_STAGE.clear();
		boostSent = false;
		BloodFx.reset();
		UndeadMotion.reset();
		PixelCubes.reset();
		SkinPixels.reset();
		UndeadRender.reset();
		UndeadPartRenderer.LAST.clear();
		UndeadCloneRenderer.forgetAll();
		UndeadEffectsRenderer.reset();
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
		UndeadFirstPerson.tick(mc.player == null ? null : Undead.peek(mc.player));
		long now = mc.level.getGameTime();
		java.util.Set<UUID> present = new java.util.HashSet<>();
		for (Player player : mc.level.players()) {
			UndeadState s = Undead.peek(player);
			if (s == null || !s.hasPower) {
				continue;
			}
			present.add(player.getUUID());
			long[] seen = SEEN.computeIfAbsent(player.getUUID(), k -> {
				long[] a = new long[UndeadState.PARTS];
				for (int i = 0; i < UndeadState.PARTS; i++) {
					a[i] = s.brokenAt(i); // first sight: nothing bursts that broke before we saw him
				}
				return a;
			});
			for (int i = 0; i < UndeadState.PARTS; i++) {
				long at = s.brokenAt(i);
				if (at != seen[i]) {
					seen[i] = at;
					int kind = s.kind(i);
					if (at > 0L && now - at < 20L && (kind == UndeadState.KIND_BURST || kind == UndeadState.KIND_SHATTER)) {
						burst(player, i, kind == UndeadState.KIND_SHATTER ? 1.5f : 1f);
					} else if (at > 0L && now - at < 20L && kind == UndeadState.KIND_LAUNCHED) {
						UndeadBlood.stumpSpurt(player, i, 1.2f); // thrown off: the stump spurts
					}
				}
			}
			regrowSounds(mc, player, s, now);
			UndeadEffectsRenderer.watch(player, s, now);
			UndeadSwordFx.watch(player, s, now); // v0.17.3: the Bone Sword's effects and the hold-V draw
			UndeadMotion.tick(player, s);
			if (player != mc.player) {
				// their box follows the stage on this client too (UndeadCrawlPoseMixin reads it straight off the part times)
				Integer last = SEEN_STAGE.put(player.getUUID(), com.projecthero.mod.undead.UndeadStages.stage(s, now));
				if (last == null || last != SEEN_STAGE.get(player.getUUID())) {
					player.refreshDimensions();
				}
			}
			watchCuts(player, s, now);
			UndeadBlood.tick(player, s, now);
		}
		UndeadEffectsRenderer.tickBlood(now);
		UndeadSwordFx.tick(now);
		if (now % 100 == 0) {
			SEEN.keySet().retainAll(present);
			SEEN_CUTS.keySet().retainAll(present);
			SEEN_STAGE.keySet().retainAll(present);
			UndeadMotion.forgetMissing(present);
		}
	}

	/** The local player's right click while flying is the boost: sent on the edges. */
	private static void boost(Minecraft mc) {
		boolean want = mc.player != null && mc.screen == null && mc.options.keyUse.isDown() && com.projecthero.mod.undead.UndeadFlight.isFlying(mc.player);
		if (want != boostSent && mc.getConnection() != null) {
			boostSent = want;
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.projecthero.mod.network.UndeadBoostPayload(want));
		}
	}

	/**
	 * The ends that come off without the part breaking: at take-off the hands and feet burst off (their texels as pixel cubes,
	 * blood spurting); a finger shot's bite out of an arm bursts the texels it took.
	 */
	private static void watchCuts(Player player, UndeadState s, long now) {
		long[] seen = SEEN_CUTS.computeIfAbsent(player.getUUID(), k -> new long[] { s.flags & UndeadState.FLAG_PROPELLING, s.ex(UndeadState.X_EATEN_R),
				s.ex(UndeadState.X_EATEN_L) });
		boolean flying = s.flag(UndeadState.FLAG_PROPELLING);
		if (flying && seen[0] == 0L) {
			float cut = 12f - com.projecthero.mod.undead.UndeadConfig.HAND_ROWS;
			for (int part = UndeadState.RIGHT_ARM; part <= UndeadState.LEFT_LEG; part++) {
				if (!s.broken(part)) {
					burstEnd(player, part, cut, 99f, 1.1f);
					UndeadBlood.stumpEndSpurt(player, part, cut, 1.4f);
				}
			}
		}
		seen[0] = flying ? 1L : 0L;
		for (int k = 0; k < 2; k++) {
			int arm = UndeadState.RIGHT_ARM + k;
			long e = s.ex(k == 0 ? UndeadState.X_EATEN_R : UndeadState.X_EATEN_L);
			if (e > seen[1 + k] && !s.broken(arm)) {
				float from = 12f * (1f - e / 1000f), to = 12f * (1f - seen[1 + k] / 1000f);
				burstEnd(player, arm, from, seen[1 + k] == 0L ? 99f : to, 0.6f);
				UndeadBlood.stumpEndSpurt(player, arm, from, e - seen[1 + k] > 200 ? 1.4f : 0.6f);
			}
			seen[1 + k] = e;
		}
	}

	/**
	 * The texels of a limb between {@code from} and {@code to} rows come off. v0.17.3 (user: "remove the body pixels that fall
	 * off the players body when undead loses a limb"): no pixel cubes any more -- the blood spurt alone shows it.
	 */
	static void burstEnd(Player player, int part, float from, float to, float power) {
	}

	/** A part of {@code player} bursts into its skin's pixels, from where it was drawn. */
	public static void burst(Player player, int part, float power) {
		// v0.17.3 (user): no falling skin pixels when a part comes off -- just the blood
		UndeadBlood.partBurst(player, part, power);
	}

	/** The flesh rebuilding itself: bone grind, wet muscle, tightening skin and a snap per part ({@link UndeadSounds#watch}). */
	private static void regrowSounds(Minecraft mc, Player player, UndeadState s, long now) {
		UndeadSounds.watch(player, s, now);
	}
}

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
		UndeadBloodMovesFx.reset();
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
			UndeadBloodMovesFx.tick(player, s, now); // v0.17.3: Blood Spray / Whirlwind streams, Combustion Punch
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

	/**
	 * The ends that come off without the part breaking: an ejection blowing off a hand / foot with half the forearm / calf (the
	 * stump erupts: a crown of blood, a fast core, globs, torn flesh) or cutting the torso down to its top third; a finger shot's
	 * bite out of an arm (a spurt).
	 */
	private static void watchCuts(Player player, UndeadState s, long now) {
		long[] seen = SEEN_CUTS.computeIfAbsent(player.getUUID(), k -> new long[] { s.ex(UndeadState.X_EJECTED), s.ex(UndeadState.X_EATEN_R),
				s.ex(UndeadState.X_EATEN_L) });
		long ej = s.ex(UndeadState.X_EJECTED);
		long fresh = ej & ~seen[0];
		if (fresh != 0L) {
			Minecraft mc = Minecraft.getInstance();
			boolean firstPersonSelf = player == mc.player && mc.options.getCameraType().isFirstPerson();
			Vec3 carry = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo).scale(0.4);
			float rows = com.projecthero.mod.undead.UndeadEjectConfig.EJECT_ROWS;
			for (int seg = UndeadState.RIGHT_HAND; seg <= UndeadState.LEFT_FOOT; seg++) {
				int limb = UndeadState.limbOf(seg);
				if ((fresh & (1L << seg)) == 0L || s.broken(limb)) {
					continue;
				}
				Vec3 at = UndeadBlood.endOf(player, limb, rows);
				if (firstPersonSelf && limb <= UndeadState.LEFT_ARM) {
					// (his own view: the arm's stump is drawn at the bottom of the screen; a smaller burst out in front, not in his face)
					Vec3 look = player.getLookAngle();
					at = player.getEyePosition().add(look.scale(1.4)).add(0, -0.55, 0);
					BloodFx.eruption(at, look.add(0, 0.3, 0), carry, 0.45f);
				} else {
					BloodFx.eruption(at, UndeadBlood.axis(player, limb), carry, 0.85f);
				}
				UndeadBlood.stumpEndSpurt(player, limb, rows, 0.8f);
			}
			if ((fresh & (1L << UndeadState.BODY)) != 0L && !s.broken(UndeadState.BODY)) {
				Vec3 at = UndeadBlood.at(player, UndeadState.BODY, 0f, com.projecthero.mod.undead.UndeadEjectConfig.COMBUSTION_BODY_ROWS, 0f);
				BloodFx.eruption(at, UndeadBlood.axis(player, UndeadState.BODY), carry, firstPersonSelf ? 0.6f : 1.3f);
				UndeadSounds.bloodBurst(at, 1f);
			}
		}
		seen[0] = ej;
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

package com.projecthero.mod.client.hulk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.flight.BodyGlow;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.hulk.GammaOverload;
import com.projecthero.mod.hulk.HulkMind;
import com.projecthero.mod.hulk.data.GammaAbsorb;
import com.projecthero.mod.hulk.entity.MirrorHulkEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): the client side of the Gamma origin.
 * <ul>
 *   <li><b>The green flash</b> -- for the 10 s an overloading reactor flashes, a pulsing green tint over the screen of
 *       everyone within {@link GammaOverload#FLASH_RANGE} blocks (fading with distance, strongest for the one absorbing
 *       it), burning to white-green just before the blast. Read from the absorbing player's synced
 *       {@link ModAttachments#GAMMA_ABSORB}.</li>
 *   <li><b>The energy</b> -- a pulsing glow round the reactor core and, once he is absorbing, glowing green wisps (an orb
 *       with a trail) streaming from the flash along curving paths into his chest, drawn for every viewer; and his body
 *       taking on a green skin of light ({@link BodyGlow}) that strengthens as it builds.</li>
 *   <li><b>The mind</b> -- arriving fades in from black; while the Hulk grabs him his view is turned onto the Hulk and he is
 *       pulled into its hands, the screen burning white-green; back home it fades out of white-green.</li>
 * </ul>
 */
public final class GammaOriginClient {
	private static final int GREEN = 0x5CFF4A;
	private static final int PALE = 0xD8FFC8;
	private static final BodyGlow.Palette GAMMA_GLOW = new BodyGlow.Palette(0x5CFF4A, 0x2FB82A, 0.6f, 1.6f);

	private static ResourceKey<Level> lastDimension;
	private static long arrivedAt = -1L;
	private static long returnedAt = -1L;

	private GammaOriginClient() {
	}

	public static void init() {
		HudRenderCallback.EVENT.register(GammaOriginClient::hud);
		WorldRenderEvents.AFTER_ENTITIES.register(GammaOriginClient::world);
		ClientTickEvents.END_CLIENT_TICK.register(GammaOriginClient::tick);
	}

	// ---------------------------------------------------------------- shared clocks

	/** Ticks into {@code p}'s reactor flash, or -1. */
	private static float flashTicks(Player p, float partial) {
		GammaAbsorb a = p.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null);
		if (a == null) {
			return -1.0f;
		}
		float t = (p.level().getGameTime() - a.startedAt()) + partial;
		return t < 0.0f || t > GammaOverload.FLASH_TICKS + 2 ? -1.0f : t;
	}

	/** 0..1 how far {@code p} is into absorbing (0 before it starts). */
	private static float build(float t) {
		return Mth.clamp((t - GammaOverload.ABSORB_START) / (GammaOverload.FLASH_TICKS - GammaOverload.ABSORB_START), 0.0f, 1.0f);
	}

	private static MirrorHulkEntity myMirrorHulk(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.level.dimension() != HulkMind.DIMENSION) {
			return null;
		}
		for (MirrorHulkEntity h : mc.level.getEntitiesOfClass(MirrorHulkEntity.class, mc.player.getBoundingBox().inflate(24.0))) {
			if (h.ownerId().isPresent() && h.ownerId().get().equals(mc.player.getUUID())) {
				return h;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- tick: fades and the grab

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			lastDimension = null;
			arrivedAt = -1L;
			returnedAt = -1L;
			return;
		}
		ResourceKey<Level> dim = mc.level.dimension();
		if (lastDimension != null && dim != lastDimension) {
			if (dim == HulkMind.DIMENSION) {
				arrivedAt = mc.level.getGameTime();
			} else if (lastDimension == HulkMind.DIMENSION) {
				returnedAt = mc.level.getGameTime();
			}
		}
		lastDimension = dim;
		if (mc.isPaused()) {
			return;
		}
		// the Hulk has him: his view is dragged onto it and he is pulled into its hands
		MirrorHulkEntity hulk = myMirrorHulk(mc);
		if (hulk == null || !hulk.smashing()) {
			return;
		}
		float st = hulk.smashTicks(0.0f);
		if (st < MirrorHulkEntity.SMASH_SHATTER_AT) {
			return;
		}
		Player me = mc.player;
		Vec3 eye = me.getEyePosition();
		Vec3 face = hulk.position().add(0.0, hulk.getBbHeight() * 0.85, 0.0);
		Vec3 d = face.subtract(eye);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		float k = st >= MirrorHulkEntity.SMASH_GRAB_AT ? 0.5f : 0.25f;
		me.setYRot(Mth.rotLerp(k, me.getYRot(), yaw));
		me.setXRot(Mth.lerp(k, me.getXRot(), Mth.clamp(pitch, -90.0f, 90.0f)));
		if (st >= MirrorHulkEntity.SMASH_GRAB_AT) {
			Vec3 hands = hulk.position().add(0.0, hulk.getBbHeight() * 0.72, 0.0)
					.add(Vec3.directionFromRotation(0.0f, hulk.getYRot()).scale(0.9));
			Vec3 pull = hands.subtract(me.position().add(0.0, me.getBbHeight() * 0.5, 0.0));
			double len = pull.length();
			me.setDeltaMovement(len < 0.05 ? Vec3.ZERO : pull.scale(Math.min(0.35, len * 0.3) / len));
		}
	}

	// ---------------------------------------------------------------- the screen

	private static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		int w = g.guiWidth();
		int h = g.guiHeight();

		// the green flash
		float best = 0.0f;
		float white = 0.0f;
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		for (Player p : mc.level.players()) {
			float t = flashTicks(p, partial);
			if (t < 0.0f) {
				continue;
			}
			GammaAbsorb a = p.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null);
			double dist = Math.sqrt(cam.distanceToSqr(Vec3.atCenterOf(a.reactor())));
			// v0.16.4 (screenshot check): his own tint is lighter so he can still see the flash and the wisps pour in
			float near = p == mc.player ? 0.4f : 0.85f * (float) Mth.clamp(1.0 - dist / GammaOverload.FLASH_RANGE, 0.0, 1.0);
			if (near <= 0.0f) {
				continue;
			}
			float f = t / GammaOverload.FLASH_TICKS;
			float pulse = 0.5f + 0.5f * Mth.sin(t * (0.22f + 0.35f * f));
			float alpha = (0.10f + 0.28f * f) + (0.06f + 0.14f * f) * pulse;
			float end = Mth.clamp((t - (GammaOverload.FLASH_TICKS - 14)) / 14.0f, 0.0f, 1.0f);
			alpha = Mth.lerp(end, alpha * near, 0.95f * (p == mc.player ? 1.0f : near));
			if (alpha > best) {
				best = alpha;
				white = end;
			}
		}
		if (best > 0.01f) {
			fill(g, w, h, lerpColour(GREEN, PALE, white), best);
		}

		// the mind: in from black on arrival, the white-green burn of the grab, out of white-green back home
		if (arrivedAt >= 0L) {
			float t = (now - arrivedAt) + partial;
			float a = t < 10.0f ? 1.0f : 1.0f - (t - 10.0f) / 30.0f;
			if (a <= 0.0f || t < 0.0f) {
				arrivedAt = -1L;
			} else {
				fill(g, w, h, 0x000000, a);
			}
		}
		MirrorHulkEntity hulk = myMirrorHulk(mc);
		if (hulk != null && hulk.smashing()) {
			float st = hulk.smashTicks(partial);
			float a = Mth.clamp((st - MirrorHulkEntity.SMASH_GRAB_AT) / (MirrorHulkEntity.SMASH_RETURN_AT - MirrorHulkEntity.SMASH_GRAB_AT), 0.0f,
					1.0f);
			if (a > 0.0f) {
				fill(g, w, h, lerpColour(GREEN, PALE, a), a * a);
			}
		}
		if (returnedAt >= 0L) {
			float t = (now - returnedAt) + partial;
			float a = 1.0f - t / 40.0f;
			if (a <= 0.0f || t < 0.0f) {
				returnedAt = -1L;
			} else {
				fill(g, w, h, PALE, a);
			}
		}
	}

	private static void fill(GuiGraphics g, int w, int h, int rgb, float alpha) {
		int a = Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
		if (a > 2) {
			g.fill(0, 0, w, h, (a << 24) | (rgb & 0xFFFFFF));
		}
	}

	private static int lerpColour(int from, int to, float t) {
		int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int gr = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return (r << 16) | (gr << 8) | b;
	}

	// ---------------------------------------------------------------- the world: core glow and energy wisps

	private static void world(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack poseStack = context.matrixStack();
		VertexConsumer vc = null;
		for (Player p : mc.level.players()) {
			float t = flashTicks(p, partial);
			if (t < 0.0f) {
				continue;
			}
			GammaAbsorb a = p.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null);
			Vec3 core = Vec3.atCenterOf(a.reactor());
			if (core.distanceToSqr(cam) > 96.0 * 96.0) {
				continue;
			}
			if (vc == null) {
				vc = ThorDraw.buffer(context.consumers());
			}
			Vec3 c = core.subtract(cam);
			float f = t / GammaOverload.FLASH_TICKS;
			float pulse = 0.5f + 0.5f * Mth.sin(t * (0.22f + 0.35f * f));
			// the core: a white-green heart in a swelling, pulsing shell of green light
			poseStack.pushPose();
			poseStack.translate(c.x, c.y, c.z);
			TurboDraw.sphere(vc, poseStack, 1.0f + 2.2f * f + 0.5f * pulse, GREEN, 0.07f + 0.06f * pulse);
			TurboDraw.sphere(vc, poseStack, 0.7f + 0.6f * f, 0x9CFF84, 0.12f + 0.08f * pulse);
			TurboDraw.orb(vc, poseStack, 0.55f + 0.25f * pulse, PALE, t * 9.0f, 0.75f);
			poseStack.popPose();
			if (t < GammaOverload.ABSORB_START) {
				continue;
			}
			// the wisps: glowing motes with trails, curving out of the flash into his chest
			Vec3 chest = new Vec3(Mth.lerp(partial, p.xo, p.getX()), Mth.lerp(partial, p.yo, p.getY()) + p.getBbHeight() * 0.42,
					Mth.lerp(partial, p.zo, p.getZ())).subtract(cam);
			float since = t - GammaOverload.ABSORB_START;
			float in = Mth.clamp(since / 10.0f, 0.0f, 1.0f);
			int wisps = 6 + Math.round(build(t) * 6.0f);
			float period = 26.0f;
			PoseStack.Pose pose = poseStack.last();
			for (int i = 0; i < wisps; i++) {
				double seed = i * 7.31 + p.getId() * 1.7;
				float u = (float) ((since / period + i / (double) wisps) % 1.0);
				// each launch takes a fresh path
				double launch = Math.floor(since / period + i / (double) wisps);
				double s2 = seed + launch * 3.97;
				double ang = ThorDraw.hash(s2) * Math.PI * 2.0;
				double r = 0.5 + ThorDraw.hash(s2 + 3.3) * 1.3;
				Vec3 start = c.add(Math.cos(ang) * r, ThorDraw.hash(s2 + 5.1) * 1.8 - 0.4, Math.sin(ang) * r);
				Vec3 mid = start.add(chest).scale(0.5);
				Vec3 side = chest.subtract(start).cross(new Vec3(0, 1, 0));
				side = side.lengthSqr() < 1.0e-6 ? Vec3.ZERO : side.normalize().scale((ThorDraw.hash(s2 + 7.7) - 0.5) * 3.0);
				Vec3 ctrl = mid.add(0.0, 1.0 + ThorDraw.hash(s2 + 9.2) * 1.5, 0.0).add(side);
				float alpha = in * Mth.sin((float) Math.PI * Math.min(1.0f, u * 1.15f));
				if (alpha <= 0.02f) {
					continue;
				}
				Vec3[] trail = new Vec3[6];
				for (int j = 0; j < trail.length; j++) {
					trail[j] = bezier(start, ctrl, chest, Math.max(0.0f, u - (trail.length - 1 - j) * 0.035f));
				}
				// v0.16.4 (screenshot check): fade out right at the camera (his own wisps end in his chest, i.e. his face)
				float camFade = (float) Mth.clamp((trail[trail.length - 1].length() - 0.6) / 1.0, 0.0, 1.0);
				if (camFade <= 0.02f) {
					continue;
				}
				alpha *= camFade;
				ThorDraw.ribbon(vc, pose, trail, 0.07f, GREEN, 0.35f * alpha);
				ThorDraw.ribbon(vc, pose, trail, 0.025f, PALE, 0.7f * alpha);
				Vec3 head = trail[trail.length - 1];
				poseStack.pushPose();
				poseStack.translate(head.x, head.y, head.z);
				TurboDraw.orb(vc, poseStack, 0.16f + 0.05f * pulse, 0x7CFF5A, t * 14.0f + i * 40.0f, alpha);
				poseStack.popPose();
				pose = poseStack.last();
			}
		}
	}

	private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, float u) {
		float v = 1.0f - u;
		return a.scale(v * v).add(b.scale(2.0f * v * u)).add(c.scale(u * u));
	}

	// ---------------------------------------------------------------- his body glow

	/** From {@code PowerRingLayer}: the absorbing player's skin of green light, stronger as the radiation builds. */
	public static void renderGlow(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
			float ageInTicks) {
		float t = flashTicks(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (t < GammaOverload.KNEEL_AT) {
			return;
		}
		float k = 0.15f + 0.85f * build(t);
		BodyGlow.render(pose, buffers, player, model, ageInTicks, k, build(t), GAMMA_GLOW, false);
	}

}

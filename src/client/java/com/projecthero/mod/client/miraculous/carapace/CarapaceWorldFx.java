package com.projecthero.mod.client.miraculous.carapace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.carapace.Carapace;
import com.projecthero.mod.miraculous.carapace.CarapaceConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Carapace's green energy shell for every viewer, from the synced state -- the Shelter dome (radius
 * {@link CarapaceConfig#SHELTER_RADIUS}, swelling up out of the ground when raised, fading at the end), the Shell
 * Barrier wall (rising from the ground) and the Protect bubble round whoever wears it. All three use the tiling hexagon
 * texture ({@link CarapaceRender#HEX}) on an emissive, unculled translucent type, its glow pulsing slowly.
 */
public final class CarapaceWorldFx {
	/** One hexagon tile covers this many blocks across / up. */
	private static final float TILE_U = 1.2f;
	private static final float TILE_V = 0.7f;

	private CarapaceWorldFx() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		float t = now + partial;
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		VertexConsumer vc = null;
		for (Player player : mc.level.players()) {
			MiraculousState s = Miraculous.peek(player);
			if (s == null || !s.transformed || !Kwami.WAYZZ.id.equals(s.kwami)) {
				continue;
			}
			long shelter = s.val(Carapace.V_SHELTER);
			long barrier = s.val(Carapace.V_BARRIER);
			long protect = s.val(Carapace.V_PROTECT);
			if (shelter <= now && barrier <= now && protect <= now) {
				continue;
			}
			if (vc == null) {
				vc = context.consumers().getBuffer(RenderType.entityTranslucentEmissive(CarapaceRender.HEX));
			}
			pose.pushPose();
			pose.translate(-cam.x, -cam.y, -cam.z);
			PoseStack.Pose last = pose.last();
			if (shelter > now) {
				Vec3 c = Miraculous.vec(s, Carapace.X_SHELTER);
				if (c != null) {
					float age = t - (shelter - CarapaceConfig.SHELTER_TICKS);
					float left = shelter - t;
					float grow = ease(Mth.clamp(age / CarapaceConfig.SHELTER_RAISE_TICKS, 0f, 1f));
					float fade = Mth.clamp(left / 20f, 0f, 1f);
					float r = (float) CarapaceConfig.SHELTER_RADIUS * (0.15f + 0.85f * grow);
					float a = (0.75f + 0.12f * Mth.sin(t * 0.12f)) * fade * (0.4f + 0.6f * grow);
					// the dome: the upper half, and a lip a little into the ground so it never floats
					sphere(vc, last, c, r, -0.12f, 1f, a, t * 0.004f);
				}
			}
			if (barrier > now) {
				Vec3 c = Miraculous.vec(s, Carapace.X_BARRIER);
				Vec3 dir = Miraculous.vec(s, Carapace.X_BARRIER_DIR);
				if (c != null && dir != null) {
					float age = t - (barrier - CarapaceConfig.BARRIER_TICKS);
					float grow = ease(Mth.clamp(age / 6f, 0f, 1f));
					float fade = Mth.clamp((barrier - t) / 15f, 0f, 1f);
					float a = (0.8f + 0.12f * Mth.sin(t * 0.2f)) * fade;
					wall(vc, last, c, dir, CarapaceConfig.BARRIER_WIDTH, CarapaceConfig.BARRIER_HEIGHT * grow, a);
				}
			}
			if (protect > now) {
				Entity e = mc.level.getEntity((int) (s.val(Carapace.V_PROTECT_ID) - 1L));
				if (e != null && !(e == mc.player && mc.options.getCameraType().isFirstPerson())) {
					float age = t - (protect - CarapaceConfig.PROTECT_TICKS);
					float grow = ease(Mth.clamp(age / 6f, 0f, 1f));
					float fade = Mth.clamp((protect - t) / 15f, 0f, 1f);
					Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
					float r = (Math.max(e.getBbWidth(), e.getBbHeight()) * 0.62f + 0.25f) * (0.3f + 0.7f * grow);
					float a = (0.55f + 0.15f * Mth.sin(t * 0.25f)) * fade;
					sphere(vc, last, c, r, -1f, 1f, a, t * 0.01f);
				}
			}
			pose.popPose();
		}
	}

	/** A sphere (or the slice of it from latitude {@code from} to {@code to}, in -1..1 of the half-turn) of hexagon light. */
	private static void sphere(VertexConsumer vc, PoseStack.Pose last, Vec3 c, float r, float from, float to, float alpha, float scroll) {
		int lon = Math.max(16, Math.min(48, Math.round(r * 6f)));
		int lat = Math.max(8, Math.min(24, Math.round(r * 3f)));
		float circ = (float) (Math.PI * 2 * r);
		float us = Math.max(1f, Math.round(circ / TILE_U)); // whole tiles round, so the seam matches
		float vs = (float) (Math.PI * r) / TILE_V;
		int a = Math.round(255 * Mth.clamp(alpha, 0f, 1f));
		for (int j = 0; j < lat; j++) {
			float l0 = Mth.lerp(j / (float) lat, from, to), l1 = Mth.lerp((j + 1) / (float) lat, from, to);
			float p0 = l0 * (float) Math.PI / 2f, p1 = l1 * (float) Math.PI / 2f;
			float y0 = Mth.sin(p0) * r, y1 = Mth.sin(p1) * r, q0 = Mth.cos(p0) * r, q1 = Mth.cos(p1) * r;
			float v0 = (l0 + 1f) * 0.5f * vs, v1 = (l1 + 1f) * 0.5f * vs;
			for (int i = 0; i < lon; i++) {
				float a0 = (float) (Math.PI * 2 * i / lon), a1 = (float) (Math.PI * 2 * (i + 1) / lon);
				float u0 = i / (float) lon * us + scroll, u1 = (i + 1) / (float) lon * us + scroll;
				float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
				vtx(vc, last, c.x + q0 * c0, c.y + y0, c.z + q0 * s0, u0, v0, a);
				vtx(vc, last, c.x + q0 * c1, c.y + y0, c.z + q0 * s1, u1, v0, a);
				vtx(vc, last, c.x + q1 * c1, c.y + y1, c.z + q1 * s1, u1, v1, a);
				vtx(vc, last, c.x + q1 * c0, c.y + y1, c.z + q1 * s0, u0, v1, a);
			}
		}
	}

	/** The Shell Barrier: a {@code w} x {@code h} sheet standing on {@code c}, across {@code dir}, two layers a hand apart. */
	private static void wall(VertexConsumer vc, PoseStack.Pose last, Vec3 c, Vec3 dir, float w, float h, float alpha) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		int a = Math.round(255 * Mth.clamp(alpha, 0f, 1f));
		float us = w / TILE_U, vs = h / TILE_V;
		for (int layer = -1; layer <= 1; layer += 2) {
			Vec3 o = c.add(dir.scale(layer * 0.06));
			Vec3 l = o.add(side.scale(-w / 2f)), rr = o.add(side.scale(w / 2f));
			vtx(vc, last, l.x, l.y, l.z, 0f, vs, a);
			vtx(vc, last, rr.x, rr.y, rr.z, us, vs, a);
			vtx(vc, last, rr.x, rr.y + h, rr.z, us, 0f, a);
			vtx(vc, last, l.x, l.y + h, l.z, 0f, 0f, a);
		}
	}

	private static void vtx(VertexConsumer vc, PoseStack.Pose last, double x, double y, double z, float u, float v, int a) {
		vc.addVertex(last, (float) x, (float) y, (float) z).setColor(255, 255, 255, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, 1f, 0f);
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return 1f - (1f - x) * (1f - x);
	}
}

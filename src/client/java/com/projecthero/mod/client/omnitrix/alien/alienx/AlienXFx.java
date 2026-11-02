package com.projecthero.mod.client.omnitrix.alien.alienx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.alien.AlienXClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Alien X's rendered effects -- drawn in the world pass from the synced state (anim id + start, the kit's flags and
 * position timers), so every viewer sees the same thing and nothing needs its own packet. Everything is additive
 * ({@code RenderType.eyes}: glows, ribbons, bands, rings via {@link PyroDraw}) or the extras sheet's starfield patch drawn
 * camera-facing (the rifts and the blink tears): starfield rifts, beams and rings, not particle spam.
 */
public final class AlienXFx {
	private static final int WHITE = 0xFFFFFFFF, STAR_BLUE = 0xFF9FC4FF, VIOLET = 0xFF8A4DFF, NEBULA = 0xFF3A2A9A, GREEN = 0xFF7CFF6A,
			GOLD = 0xFFFFE6A0, RED = 0xFFFF4A3A;
	private static boolean registered;

	private AlienXFx() {
	}

	public static void init() {
		if (!registered) {
			registered = true;
			WorldRenderEvents.AFTER_ENTITIES.register(AlienXFx::render);
		}
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (!AlienXKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			try {
				draw(pl, st, partial, cam, pose, buffers, mc);
			} catch (RuntimeException e) {
				// a draw must never take the frame down
			}
		}
	}

	private static void draw(Player pl, OmnitrixState st, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers, Minecraft mc) {
		long now = pl.level().getGameTime();
		float time = now + partial;
		float a = now - st.animStart + partial;
		Vec3 feet = pl.getPosition(partial).subtract(cam);
		float h = pl.getBbHeight();
		boolean self = pl == mc.player && mc.options.getCameraType().isFirstPerson();
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);

		if ((st.kitFlags & AlienXKit.F_DEBATE) != 0) {
			debate(pl, st, feet, h, time, pose, add, glow, self);
		}
		// the move effects, keyed by the synced anim
		switch (st.animId) {
			case AlienXKit.ANIM_BOLT -> beam(pl, partial, cam, pose, add, glow, a, 9f, AlienXKit.BOLT_RANGE, 0.35f, false);
			case AlienXKit.ANIM_RAY -> beam(pl, partial, cam, pose, add, glow, a, 16f, AlienXKit.RAY_RANGE, 1.4f, true);
			case AlienXKit.ANIM_HURL -> hurl(pl, st, partial, cam, pose, add, glow, a);
			case AlienXKit.ANIM_CRUSH -> crush(feet, pose, add, a, time);
			case AlienXKit.ANIM_BLINK, AlienXKit.ANIM_REWIND -> blink(pl, st, feet, h, cam, pose, buffers, add, glow, a, time,
					st.animId == AlienXKit.ANIM_REWIND);
			case AlienXKit.ANIM_FLIGHT, AlienXKit.ANIM_SEATED -> risingRings(feet, h, pose, add, a, 12f, STAR_BLUE, 2);
			case AlienXKit.ANIM_RESTORE -> {
				risingRings(feet, h, pose, add, a, 20f, GREEN, 3);
				if (a < 16f && !self) {
					PyroDraw.glow(pose, glow, feet.add(0, h * 0.55, 0), 1.3f, PyroDraw.dim(GREEN, 0.5f * (1f - a / 16f)));
				}
			}
			case AlienXKit.ANIM_SHOCKWAVE -> shockwave(feet, h, pose, add, glow, a);
			case AlienXKit.ANIM_BANG -> bigBang(feet, h, pose, buffers, add, glow, a, time, self);
			case AlienXKit.ANIM_DENIED -> denied(feet, h, pose, add, a);
			default -> {
			}
		}
		// lasting effects: the time-stop field and the rift
		long stopEnd = st.readyAt(AlienXKit.ID + "." + AlienXKit.T_STOP_END);
		Vec3 stopAt = AlienXKit.pos(pl, AlienXKit.P_STOP);
		if (stopEnd > now - 10 && stopAt != null) {
			timeStop(stopAt.subtract(cam), pose, add, glow, (stopEnd - now - partial), time);
		}
		long riftEnd = st.readyAt(AlienXKit.ID + "." + AlienXKit.T_RIFT_END);
		Vec3 riftAt = AlienXKit.pos(pl, AlienXKit.P_RIFT);
		if (riftEnd > now - 8 && riftAt != null) {
			float left = riftEnd - now - partial;
			float grow = Mth.clamp((AlienXKit.RIFT_TICKS - left) / 6f, 0f, 1f) * Mth.clamp((left + 8f) / 8f, 0f, 1f);
			rift(riftAt.subtract(cam), pose, buffers, add, glow, 1.8f * grow, time, true);
		}
	}

	// ---------------------------------------------------------------- the debate

	/** Two small voices circling his head (Serena pale blue, Bellicus red; the one speaking flares) and a faint council ring. */
	private static void debate(Player pl, OmnitrixState st, Vec3 feet, float h, float time, PoseStack pose, VertexConsumer add, VertexConsumer glow,
			boolean self) {
		long start = st.readyAt(AlienXKit.ID + "." + AlienXKit.T_DEBATE_START);
		float elapsed = pl.level().getGameTime() - start;
		boolean opening = (st.kitFlags & AlienXKit.F_OPENING) != 0;
		boolean serenaFirst = st.readyAt(AlienXKit.ID + ".debate_first") != 2L;
		int who = AlienXKit.speaker(opening, serenaFirst, elapsed);
		pose.pushPose();
		pose.translate(feet.x, feet.y + 0.03, feet.z);
		PyroDraw.annulus(pose, add, 0.9f, 1.15f, PyroDraw.dim(NEBULA, 0.35f), 0xFF000000, 0f);
		pose.popPose();
		if (self) {
			return; // his own view: the voices would sit on the camera
		}
		Vec3 head = feet.add(0, h + 0.25, 0);
		for (int k = 0; k < 2; k++) {
			float ang = time * 0.22f + k * Mth.PI;
			Vec3 at = head.add(Mth.cos(ang) * 0.7, 0.12 * Mth.sin(time * 0.3f + k), Mth.sin(ang) * 0.7);
			boolean speaking = who == k + 1;
			int col = k == 0 ? AlienXClient.SERENA_COLOUR : AlienXClient.BELLICUS_COLOUR;
			PyroDraw.glow(pose, glow, at, speaking ? 0.32f : 0.18f, PyroDraw.dim(col, speaking ? 0.95f : 0.45f));
		}
	}

	// ---------------------------------------------------------------- beams

	/** Where the right hand is in world space (camera-relative), an arm aimed along the look. */
	private static Vec3 hand(Player p, float partial, Vec3 cam, boolean both) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 look = p.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return p.getEyePosition(partial).add(look.scale(0.6)).add(right.scale(both ? 0.05 : 0.3)).subtract(up.scale(0.3)).subtract(cam);
		}
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 bodyRight = new Vec3(-Math.cos(bodyYaw), 0, -Math.sin(bodyYaw));
		Vec3 shoulder = p.getPosition(partial).add(0, 1.38 * p.getScale(), 0).add(bodyRight.scale(both ? 0.0 : 0.36 * p.getScale()));
		return shoulder.add(look.scale(0.75 * p.getScale())).subtract(cam);
	}

	/** Reality Bolt / Cosmic Ray: a white-hot core in a blue-violet sheath, stars drifting down it, rings along a wide ray. */
	private static void beam(Player pl, float partial, Vec3 cam, PoseStack pose, VertexConsumer add, VertexConsumer glow, float a, float life,
			double range, float width, boolean wide) {
		if (a > life) {
			return;
		}
		float fade = a < 2f ? a / 2f : Mth.clamp((life - a) / 5f, 0f, 1f);
		Vec3 eye = pl.getEyePosition(partial);
		Vec3 look = pl.getViewVector(partial);
		var hit = pl.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
		Vec3 end = (hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation()).subtract(cam);
		Vec3 start = hand(pl, partial, cam, wide);
		List<Vec3> pts = new ArrayList<>();
		int n = 24;
		for (int i = 0; i <= n; i++) {
			pts.add(start.lerp(end, i / (double) n));
		}
		final float f = fade;
		final float flick = 0.85f + 0.15f * Mth.sin(a * 2.7f);
		PyroDraw.ribbon(pose, add, pts, u -> width * 2.2 * flick, u -> PyroDraw.dim(NEBULA, 0.6f * f));
		PyroDraw.ribbon(pose, add, pts, u -> width * 1.1 * flick, u -> PyroDraw.dim(PyroDraw.lerpRgb(STAR_BLUE, VIOLET, (float) u), 0.85f * f));
		PyroDraw.ribbon(pose, add, pts, u -> width * 0.35, u -> PyroDraw.dim(WHITE, f));
		PyroDraw.glow(pose, glow, start, width * 1.6f, PyroDraw.dim(STAR_BLUE, f));
		PyroDraw.glow(pose, glow, end, width * 2.4f, PyroDraw.dim(WHITE, f));
		if (wide) {
			// rings riding out along the ray
			Vec3 dir = end.subtract(start);
			double len = dir.length();
			for (int k = 0; k < 4; k++) {
				double u = ((a * 0.9 + k * len / 4.0) % len) / len;
				pose.pushPose();
				Vec3 at = start.add(dir.scale(u));
				pose.translate(at.x, at.y, at.z);
				PyroDraw.alignY(pose, dir);
				PyroDraw.band(pose, add, width * 1.4f, 0.12f, PyroDraw.dim(STAR_BLUE, 0.7f * f), 0xFF000000);
				pose.popPose();
			}
		}
	}

	/** Telekinetic Hurl: a violet tether from his hand to the creature, a ring of force round it. */
	private static void hurl(Player pl, OmnitrixState st, float partial, Vec3 cam, PoseStack pose, VertexConsumer add, VertexConsumer glow, float a) {
		if (a > 12f) {
			return;
		}
		long id = st.readyAt(AlienXKit.ID + "." + AlienXKit.T_HURL_TARGET) - 1;
		Entity t = id >= 0 ? pl.level().getEntity((int) id) : null;
		if (t == null) {
			return;
		}
		float fade = Mth.clamp((12f - a) / 5f, 0f, 1f);
		Vec3 h = hand(pl, partial, cam, false);
		Vec3 c = t.getPosition(partial).add(0, t.getBbHeight() * 0.5, 0).subtract(cam);
		List<Vec3> pts = new ArrayList<>();
		for (int i = 0; i <= 20; i++) {
			double u = i / 20.0;
			pts.add(h.lerp(c, u).add(0, Math.sin(u * Math.PI) * 0.4 + Math.sin(u * 12 - a) * 0.05, 0));
		}
		PyroDraw.ribbon(pose, add, pts, u -> 0.3, u -> PyroDraw.dim(VIOLET, 0.6f * fade));
		PyroDraw.ribbon(pose, add, pts, u -> 0.08, u -> PyroDraw.dim(WHITE, fade));
		pose.pushPose();
		pose.translate(c.x, c.y, c.z);
		pose.mulPose(Axis.YP.rotation(a * 0.4f));
		PyroDraw.band(pose, add, t.getBbWidth() * 0.8f + 0.3f, 0.1f, PyroDraw.dim(STAR_BLUE, 0.9f * fade), 0xFF000000);
		pose.popPose();
		PyroDraw.glow(pose, glow, c, 0.9f, PyroDraw.dim(VIOLET, 0.6f * fade));
	}

	// ---------------------------------------------------------------- rings

	/** Gravity Crush: a ring slamming down out of the sky onto the ground, then spreading out to the edge. */
	private static void crush(Vec3 feet, PoseStack pose, VertexConsumer add, float a, float time) {
		if (a > 16f) {
			return;
		}
		float r = (float) AlienXKit.CRUSH_RADIUS;
		float fall = Mth.clamp(a / 4f, 0f, 1f);
		float fade = Mth.clamp((16f - a) / 6f, 0f, 1f);
		pose.pushPose();
		pose.translate(feet.x, feet.y + 0.05 + (1f - fall) * 4.0, feet.z);
		PyroDraw.band(pose, add, r * (0.5f + 0.5f * fall), 0.15f, PyroDraw.dim(VIOLET, fade), 0xFF000000);
		pose.popPose();
		if (fall >= 1f) {
			float k = Mth.clamp((a - 4f) / 8f, 0f, 1f);
			pose.pushPose();
			pose.translate(feet.x, feet.y + 0.04, feet.z);
			PyroDraw.annulus(pose, add, r * k * 0.8f, r * k, PyroDraw.dim(STAR_BLUE, 0.8f * fade), 0xFF000000, 0f);
			PyroDraw.annulus(pose, add, 0f, r * k * 0.8f, PyroDraw.dim(NEBULA, 0.45f * fade), PyroDraw.dim(NEBULA, 0.1f * fade), 0f);
			pose.popPose();
		}
	}

	/** Rings of light climbing his body (Cosmic Flight, the council seated, Restore). */
	private static void risingRings(Vec3 feet, float h, PoseStack pose, VertexConsumer add, float a, float life, int colour, int count) {
		if (a > life) {
			return;
		}
		for (int k = 0; k < count; k++) {
			float u = (a - k * 4f) / (life - 4f);
			if (u < 0f || u > 1f) {
				continue;
			}
			float fade = Mth.sin(u * Mth.PI);
			pose.pushPose();
			pose.translate(feet.x, feet.y + u * (h + 0.4), feet.z);
			PyroDraw.band(pose, add, 0.65f - 0.2f * u, 0.07f, PyroDraw.dim(colour, fade), 0xFF000000);
			pose.popPose();
		}
	}

	/** Cosmic Shockwave: a band of starlight racing out from his chest, and a flash on the ground under it. */
	private static void shockwave(Vec3 feet, float h, PoseStack pose, VertexConsumer add, VertexConsumer glow, float a) {
		if (a > 14f) {
			return;
		}
		float k = Mth.clamp(a / 10f, 0f, 1f);
		float r = (float) AlienXKit.SHOCKWAVE_RADIUS * (1f - (1f - k) * (1f - k));
		float fade = Mth.clamp((14f - a) / 6f, 0f, 1f);
		pose.pushPose();
		pose.translate(feet.x, feet.y + h * 0.55, feet.z);
		PyroDraw.band(pose, add, r, 0.35f, PyroDraw.dim(STAR_BLUE, fade), 0xFF000000);
		PyroDraw.band(pose, add, r * 0.96f, 0.12f, PyroDraw.dim(WHITE, fade), 0xFF000000);
		pose.popPose();
		pose.pushPose();
		pose.translate(feet.x, feet.y + 0.04, feet.z);
		PyroDraw.annulus(pose, add, Math.max(0f, r - 1.2f), r, 0xFF000000, PyroDraw.dim(VIOLET, 0.7f * fade), 0f);
		pose.popPose();
		if (a < 4f) {
			PyroDraw.glow(pose, glow, feet.add(0, h * 0.55, 0), 1.5f, PyroDraw.dim(WHITE, 1f - a / 4f));
		}
	}

	/** The motion is denied: a red ring closing in on him. */
	private static void denied(Vec3 feet, float h, PoseStack pose, VertexConsumer add, float a) {
		if (a > 10f) {
			return;
		}
		float k = a / 10f;
		pose.pushPose();
		pose.translate(feet.x, feet.y + h * 0.5, feet.z);
		PyroDraw.band(pose, add, 1.6f * (1f - k) + 0.4f, 0.06f, PyroDraw.dim(RED, 1f - k), 0xFF000000);
		pose.popPose();
	}

	// ---------------------------------------------------------------- rifts, blink, time stop, big bang

	/**
	 * A tear in reality: a camera-facing disc of the starfield (the extras patch, additive) over a star-black backing, a pale rim
	 * and a slow violet swirl of ribbons drawn into it.
	 */
	private static void rift(Vec3 at, PoseStack pose, MultiBufferSource buffers, VertexConsumer add, VertexConsumer glow, float r, float time, boolean swirl) {
		if (r <= 0.02f) {
			return;
		}
		PyroDraw.glow(pose, glow, at, r * 1.9f, PyroDraw.dim(VIOLET, 0.55f));
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		PyroDraw.faceCamera(pose);
		pose.mulPose(Axis.ZP.rotation(time * 0.02f));
		// the backing: solid star-black, so the tear reads as a hole in the world
		VertexConsumer solid = buffers.getBuffer(RenderType.entityTranslucentEmissive(AlienXClient.EXTRAS));
		disc(pose, solid, r * 0.98f, AlienXClient.VOID, 0xF0FFFFFF);
		// the starfield on top, drifting
		VertexConsumer stars = buffers.getBuffer(RenderType.eyes(AlienXClient.EXTRAS));
		disc(pose, stars, r, AlienXClient.STARFIELD, 0xFFFFFFFF);
		pose.popPose();
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		PyroDraw.faceCamera(pose);
		// the rim: a jagged pale ring
		PoseStack.Pose m = pose.last();
		VertexConsumer rim = add;
		int segs = 24;
		for (int i = 0; i < segs; i++) {
			float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
			float j0 = 1f + 0.12f * Mth.sin(i * 2.7f + time * 0.3f), j1 = 1f + 0.12f * Mth.sin((i + 1) * 2.7f + time * 0.3f);
			float ri = r * 0.92f, ro0 = r * 1.08f * j0, ro1 = r * 1.08f * j1;
			AlienParts.quad(rim, m, PyroDraw.dim(STAR_BLUE, 0.9f), LightTexture.FULL_BRIGHT, Mth.cos(a0) * ri, Mth.sin(a0) * ri, 0, Mth.cos(a0) * ro0,
					Mth.sin(a0) * ro0, 0, Mth.cos(a1) * ro1, Mth.sin(a1) * ro1, 0, Mth.cos(a1) * ri, Mth.sin(a1) * ri, 0, 0, 0, 1, 0, 0, 1, 1);
			AlienParts.quad(rim, m, PyroDraw.dim(STAR_BLUE, 0.9f), LightTexture.FULL_BRIGHT, Mth.cos(a1) * ri, Mth.sin(a1) * ri, 0, Mth.cos(a1) * ro1,
					Mth.sin(a1) * ro1, 0, Mth.cos(a0) * ro0, Mth.sin(a0) * ro0, 0, Mth.cos(a0) * ri, Mth.sin(a0) * ri, 0, 0, 0, -1, 0, 0, 1, 1);
		}
		pose.popPose();
		if (swirl) {
			for (int k = 0; k < 3; k++) {
				List<Vec3> pts = new ArrayList<>();
				for (int i = 0; i <= 16; i++) {
					double u = i / 16.0;
					double rad = r * (2.6 - 1.7 * u);
					double ang = time * 0.25 + k * Mth.TWO_PI / 3 + u * 3.2;
					pts.add(at.add(Math.cos(ang) * rad, Math.sin(ang * 0.7) * 0.35 * (1 - u), Math.sin(ang) * rad));
				}
				PyroDraw.ribbon(pose, add, pts, u -> 0.12 * (1.0 - 0.6 * u), u -> PyroDraw.dim(VIOLET, (float) (0.25 + 0.6 * u)));
			}
		}
	}

	/** A disc (a 20-sided fan of quads) in the pose's XY plane, the UV rectangle {@code uv} mapped across it. */
	private static void disc(PoseStack pose, VertexConsumer vc, float r, float[] uv, int argb) {
		PoseStack.Pose m = pose.last();
		float cu = (uv[0] + uv[2]) * 0.5f, cv = (uv[1] + uv[3]) * 0.5f, hu = (uv[2] - uv[0]) * 0.5f, hv = (uv[3] - uv[1]) * 0.5f;
		int segs = 20;
		for (int i = 0; i < segs; i++) {
			float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			// a degenerate quad (centre twice) per wedge: both faces
			vtx(vc, m, 0, 0, cu, cv, argb, 1);
			vtx(vc, m, c0 * r, s0 * r, cu + c0 * hu, cv + s0 * hv, argb, 1);
			vtx(vc, m, c1 * r, s1 * r, cu + c1 * hu, cv + s1 * hv, argb, 1);
			vtx(vc, m, 0, 0, cu, cv, argb, 1);
			vtx(vc, m, 0, 0, cu, cv, argb, -1);
			vtx(vc, m, c1 * r, s1 * r, cu + c1 * hu, cv + s1 * hv, argb, -1);
			vtx(vc, m, c0 * r, s0 * r, cu + c0 * hu, cv + s0 * hv, argb, -1);
			vtx(vc, m, 0, 0, cu, cv, argb, -1);
		}
	}

	private static void vtx(VertexConsumer vc, PoseStack.Pose m, float x, float y, float u, float v, int argb, float nz) {
		vc.addVertex(m, x, y, 0).setColor(argb).setUv(u, v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(m, 0, 0, nz);
	}

	/** Blink / Rewind: a tear where he was, closing; a ring of starlight where he is now. */
	private static void blink(Player pl, OmnitrixState st, Vec3 feet, float h, Vec3 cam, PoseStack pose, MultiBufferSource buffers, VertexConsumer add,
			VertexConsumer glow, float a, float time, boolean rewind) {
		if (a > 16f) {
			return;
		}
		float k = 1f - a / 16f;
		Vec3 from = AlienXKit.pos(pl, AlienXKit.P_BLINK);
		if (from != null) {
			rift(from.subtract(cam), pose, buffers, add, glow, 1.1f * k, time, false);
		}
		pose.pushPose();
		pose.translate(feet.x, feet.y + h * 0.5, feet.z);
		pose.mulPose(Axis.YP.rotation(time * (rewind ? -0.5f : 0.5f)));
		PyroDraw.band(pose, add, 0.5f + (1f - k) * 1.2f, 0.4f * k, PyroDraw.dim(rewind ? GOLD : STAR_BLUE, k), 0xFF000000);
		pose.popPose();
	}

	/**
	 * Time Stop: the frozen moment -- a pale clock-face on the ground (rim, twelve marks, two hands standing still), a dim dome over
	 * it and a slowly turning ring of light round its edge; it fades over its last half second.
	 */
	private static void timeStop(Vec3 c, PoseStack pose, VertexConsumer add, VertexConsumer glow, float left, float time) {
		float r = (float) AlienXKit.STOP_RADIUS;
		float age = AlienXKit.STOP_TICKS - left;
		float grow = Mth.clamp(age / 6f, 0f, 1f);
		float fade = Mth.clamp((left + 10f) / 10f, 0f, 1f) * grow;
		if (fade <= 0.01f) {
			return;
		}
		float rr = r * (0.3f + 0.7f * grow);
		pose.pushPose();
		pose.translate(c.x, c.y + 0.05, c.z);
		PyroDraw.annulus(pose, add, rr - 0.25f, rr, PyroDraw.dim(GOLD, 0.8f * fade), PyroDraw.dim(GOLD, 0.3f * fade), 0f);
		PyroDraw.annulus(pose, add, 0f, rr - 0.25f, PyroDraw.dim(NEBULA, 0.08f * fade), PyroDraw.dim(NEBULA, 0.22f * fade), 0f);
		PoseStack.Pose m = pose.last();
		for (int i = 0; i < 12; i++) {
			float ang = Mth.TWO_PI * i / 12f;
			float ca = Mth.cos(ang), sa = Mth.sin(ang), px = -sa, pz = ca;
			float r0 = rr - (i % 3 == 0 ? 1.4f : 0.8f), r1 = rr - 0.35f, w = i % 3 == 0 ? 0.12f : 0.07f;
			AlienParts.quad(add, m, PyroDraw.dim(GOLD, 0.9f * fade), LightTexture.FULL_BRIGHT, ca * r0 - px * w, 0.01f, sa * r0 - pz * w, ca * r0 + px * w, 0.01f,
					sa * r0 + pz * w, ca * r1 + px * w, 0.01f, sa * r1 + pz * w, ca * r1 - px * w, 0.01f, sa * r1 - pz * w, 0, 1, 0, 0, 0, 1, 1);
		}
		// the hands, stopped
		for (int k = 0; k < 2; k++) {
			float ang = k == 0 ? -0.6f : 1.9f, len = k == 0 ? rr * 0.55f : rr * 0.8f, w = k == 0 ? 0.18f : 0.1f;
			float ca = Mth.cos(ang), sa = Mth.sin(ang), px = -sa, pz = ca;
			AlienParts.quad(add, m, PyroDraw.dim(WHITE, 0.75f * fade), LightTexture.FULL_BRIGHT, -px * w, 0.02f, -pz * w, px * w, 0.02f, pz * w, ca * len + px * w * 0.3f,
					0.02f, sa * len + pz * w * 0.3f, ca * len - px * w * 0.3f, 0.02f, sa * len - pz * w * 0.3f, 0, 1, 0, 0, 0, 1, 1);
		}
		pose.popPose();
		// the dome and the turning ring
		pose.pushPose();
		pose.translate(c.x, c.y, c.z);
		PyroDraw.sphere(pose, add, rr, PyroDraw.dim(0xFF2A3A8A, 0.12f * fade), PyroDraw.dim(0xFF10142A, 0.08f * fade), 0f, time, PyroDraw.FULL);
		pose.translate(0, 1.2, 0);
		pose.mulPose(Axis.YP.rotation(time * 0.01f));
		pose.mulPose(Axis.XP.rotation(0.25f));
		PyroDraw.band(pose, add, rr * 0.99f, 0.08f, PyroDraw.dim(STAR_BLUE, 0.6f * fade), 0xFF000000);
		pose.popPose();
		PyroDraw.glow(pose, glow, c.add(0, 1.0, 0), 0.6f, PyroDraw.dim(GOLD, 0.5f * fade));
	}

	/**
	 * The Big Bang: a point of white light at his chest swells into a sphere of starlight and nebula racing out to the edge of
	 * the blast, a shock ring on the ground, a tear of starfield at the heart of it closing behind.
	 */
	private static void bigBang(Vec3 feet, float h, PoseStack pose, MultiBufferSource buffers, VertexConsumer add, VertexConsumer glow, float a, float time,
			boolean self) {
		if (a > 30f) {
			return;
		}
		Vec3 c = feet.add(0, h * 0.55, 0);
		float r = (float) AlienXKit.BANG_RADIUS;
		float k = Mth.clamp(a / 14f, 0f, 1f);
		float ease = 1f - (1f - k) * (1f - k) * (1f - k);
		float fade = Mth.clamp((30f - a) / 14f, 0f, 1f);
		pose.pushPose();
		pose.translate(c.x, c.y, c.z);
		PyroDraw.sphere(pose, add, Math.max(0.1f, r * ease), PyroDraw.dim(STAR_BLUE, 0.35f * fade), PyroDraw.dim(VIOLET, 0.2f * fade), 0.08f, time,
				PyroDraw.FULL);
		if (!self || a > 6f) {
			PyroDraw.sphere(pose, add, Math.max(0.05f, r * ease * 0.55f), PyroDraw.dim(WHITE, 0.25f * fade), PyroDraw.dim(NEBULA, 0.2f * fade), 0.12f,
					time * 1.5f, PyroDraw.FULL);
		}
		pose.popPose();
		pose.pushPose();
		pose.translate(feet.x, feet.y + 0.05, feet.z);
		PyroDraw.annulus(pose, add, Math.max(0f, r * ease - 1.5f), r * ease, 0xFF000000, PyroDraw.dim(WHITE, 0.8f * fade), 0f);
		pose.popPose();
		if (a < 8f) {
			PyroDraw.glow(pose, glow, c, 1.0f + a * 0.6f, PyroDraw.dim(WHITE, 1f - a / 8f));
		}
		if (!self) {
			rift(c, pose, buffers, add, glow, 1.2f * fade * Mth.clamp(a / 3f, 0f, 1f), time, false);
		}
	}
}

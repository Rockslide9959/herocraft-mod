package com.projecthero.mod.client.omnitrix.alien.spidermonkey;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, basic Spidermonkey): his rendered webs -- strands (thin white lines with a little sag) from his hand
 * to an anchor or a creature, the web glob in flight with its line back to him, the spinning net (spokes, rings and a scalloped
 * rim), webbing wrapped round a stuck creature (a few loops) or a full cocoon, the ground trap and the web wall. Solid white,
 * lit by the world.
 */
public final class SpidermonkeyFx {
	private SpidermonkeyFx() {
	}

	private static boolean registered;
	private static final int WEB = 0xFFF2F4F8, WEB_DIM = 0xFFD8DCE6;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("spidermonkey_strand", SpidermonkeyFx::strand);
		OmnitrixRenderers.projectileLook("spidermonkey_web", SpidermonkeyFx::glob);
		OmnitrixRenderers.projectileLook("spidermonkey_net", SpidermonkeyFx::net);
		OmnitrixRenderers.projectileLook("spidermonkey_wrap", SpidermonkeyFx::wrap);
		OmnitrixRenderers.projectileLook("spidermonkey_trap", SpidermonkeyFx::trap);
		OmnitrixRenderers.projectileLook("spidermonkey_shield", SpidermonkeyFx::shield);
	}

	private static int light(Entity e) {
		return LevelRenderer.getLightColor(e.level(), BlockPos.containing(e.position().add(0, 0.5, 0)));
	}

	/** A sagging line from {@code a} to {@code b} (world points), drawn camera-relative. */
	static void line(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float width, float sag, int colour) {
		Vec3 c = BrainstormFx.cam();
		List<Vec3> pts = new ArrayList<>();
		int n = 10;
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			pts.add(a.add(b.subtract(a).scale(t)).add(0, -sag * 4 * t * (1 - t), 0).subtract(c));
		}
		PyroDraw.ribbon(pose, vc, pts, s -> width, s -> colour);
	}

	/** A strand ending here: from the owner's hand (colour 0) or from creature colour - 1. */
	private static void strand(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 end = e.getPosition(partial);
		Vec3 start = null;
		if (e.colour() > 0) {
			Entity t = mc.level.getEntity(e.colour() - 1);
			if (t != null) {
				start = t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
			}
		} else if (e.getOwner() instanceof Player owner) {
			start = BrainstormFx.hand(owner, partial);
		}
		if (start == null) {
			return;
		}
		pose.pushPose();
		toCamera(pose, e, partial);
		line(pose, PyroDraw.solidBuffer(buffers), start, end, 0.045f, (float) Math.min(0.25, start.distanceTo(end) * 0.01), WEB);
		pose.popPose();
	}

	private static void toCamera(PoseStack pose, Entity e, float partial) {
		Vec3 at = e.getPosition(partial);
		Vec3 c = BrainstormFx.cam();
		pose.translate(c.x - at.x, c.y - at.y, c.z - at.z);
	}

	/** The web glob in flight: a white blob of crossed strands, and its line back to his hand for the first few ticks. */
	private static void glob(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		PyroDraw.sphere(pose, vc, e.size() * 0.45f, WEB, WEB_DIM, 0.2f, t * 3f, light(e));
		pose.popPose();
		if (t < 6f && e.getOwner() instanceof Player owner) {
			pose.pushPose();
			toCamera(pose, e, partial);
			line(pose, vc, BrainstormFx.hand(owner, partial), e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0), 0.03f, 0f, WEB);
			pose.popPose();
		}
	}

	/** A flat web of {@code spokes} spokes and {@code rings} rings in the pose's XZ plane, radius {@code r}. */
	static void webDisc(PoseStack pose, VertexConsumer vc, float r, int spokes, int rings, float w, int colour) {
		Vec3 c0 = Vec3.ZERO;
		for (int i = 0; i < spokes; i++) {
			double a = i * Math.PI * 2 / spokes;
			flat(pose, vc, c0, new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r), w, colour);
		}
		for (int k = 1; k <= rings; k++) {
			float rr = r * k / (rings + 0.3f);
			for (int i = 0; i < spokes; i++) {
				double a0 = i * Math.PI * 2 / spokes, a1 = (i + 1) * Math.PI * 2 / spokes;
				// each ring segment bows in a little toward the centre, like a real web
				Vec3 p0 = new Vec3(Math.cos(a0) * rr, 0, Math.sin(a0) * rr), p1 = new Vec3(Math.cos(a1) * rr, 0, Math.sin(a1) * rr);
				Vec3 mid = p0.add(p1).scale(0.5 * 0.9);
				flat(pose, vc, p0, mid, w, colour);
				flat(pose, vc, mid, p1, w, colour);
			}
		}
	}

	/** A flat strip from a to b in the pose's XZ plane (both faces). */
	static void flat(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float w, int colour) {
		Vec3 d = b.subtract(a);
		Vec3 s = new Vec3(-d.z, 0, d.x);
		s = s.lengthSqr() < 1.0e-8 ? new Vec3(w * 0.5, 0, 0) : s.normalize().scale(w * 0.5);
		var last = pose.last();
		int r = (colour >> 16) & 255, g = (colour >> 8) & 255, bl = colour & 255, al = (colour >>> 24) & 255;
		float[][] q = { { (float) (a.x + s.x), (float) a.y, (float) (a.z + s.z) }, { (float) (a.x - s.x), (float) a.y, (float) (a.z - s.z) },
				{ (float) (b.x - s.x), (float) b.y, (float) (b.z - s.z) }, { (float) (b.x + s.x), (float) b.y, (float) (b.z + s.z) } };
		for (int face = 0; face < 2; face++) {
			for (int i = 0; i < 4; i++) {
				float[] v = q[face == 0 ? i : 3 - i];
				vc.addVertex(last, v[0], v[1], v[2]).setColor(r, g, bl, al).setUv(0.5f, 0.5f)
						.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(PyroDraw.FULL)
						.setNormal(last, 0, face == 0 ? 1 : -1, 0);
			}
		}
	}

	/** The net in flight: it springs open, spins and faces where it flies. */
	private static void net(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		Vec3 v = e.getDeltaMovement();
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		PyroDraw.alignY(pose, v.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : v);
		pose.mulPose(Axis.YP.rotation(t * 0.3f));
		float r = e.size() * 1.6f * Mth.clamp(t / 5f, 0.25f, 1f);
		webDisc(pose, PyroDraw.solidBuffer(buffers), r, 8, 3, 0.05f, WEB);
		pose.popPose();
	}

	/** Webbing on a creature (entity |colour| - 1): a few loops, or a full cocoon when colour is negative. */
	private static void wrap(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		int c = e.colour();
		Entity t = mc.level.getEntity(Math.abs(c) - 1);
		if (t == null) {
			return;
		}
		Vec3 off = t.getPosition(partial).subtract(e.getPosition(partial));
		float w = t.getBbWidth() * 0.5f + 0.06f;
		float h = t.getBbHeight();
		int light = LevelRenderer.getLightColor(t.level(), t.blockPosition());
		pose.pushPose();
		pose.translate(off.x, off.y, off.z);
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		if (c < 0) {
			// a cocoon: a white egg round the whole creature, slowly squeezing
			float sq = 1f - 0.05f * Mth.sin((e.tickCount + partial) * 0.6f);
			pose.pushPose();
			pose.translate(0, h * 0.5f, 0);
			pose.scale(w * 1.25f * sq, h * 0.6f, w * 1.25f * sq);
			PyroDraw.sphere(pose, vc, 1f, WEB, 0xFFB8BCC8, 0.08f, (e.tickCount + partial) * 0.5f, light);
			pose.popPose();
		}
		// loops of strand round it at a few heights, and two crossing diagonals
		for (int k = 0; k < 4; k++) {
			float y = h * (0.15f + k * 0.23f);
			pose.pushPose();
			pose.translate(0, y, 0);
			pose.mulPose(Axis.ZP.rotation((k % 2 == 0 ? 0.25f : -0.25f)));
			PyroDraw.band(pose, vc, w * (c < 0 ? 1.3f : 1.05f), 0.035f, WEB, WEB);
			pose.popPose();
		}
		pose.popPose();
	}

	/** The ground trap: a flat web, radius size, fading at the end. */
	private static void trap(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float r = e.size() * Mth.clamp(t / 4f, 0.2f, 1f);
		pose.pushPose();
		pose.translate(0, 0.03, 0);
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		webDisc(pose, PyroDraw.solidBuffer(buffers), r, 10, 4, 0.06f, WEB_DIM);
		pose.popPose();
	}

	/** The web wall: a big vertical web across his front (yaw = his facing). */
	private static void shield(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float r = e.size() * Mth.clamp(t / 4f, 0.2f, 1f);
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		pose.mulPose(Axis.XP.rotationDegrees(90f));
		webDisc(pose, PyroDraw.solidBuffer(buffers), r, 12, 5, 0.06f, WEB);
		pose.popPose();
	}
}

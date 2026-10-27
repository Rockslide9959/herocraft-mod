package com.projecthero.mod.client.quirk.explosion;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.client.quirk.fx.QuirkFxDraw;
import com.projecthero.mod.quirk.explosion.Explosion;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (Quirks revamp): Bakugo's code-drawn effects, for every Explosion player near the viewer, from the synced state:
 * <ul>
 *   <li>the <b>Howitzer vortex</b> (the C move and the ultimate): a double helix of fire ribbons spiralling round him along
 *       his rise or dive, white-hot at the core, orange at the edge, trailing behind him;</li>
 *   <li>the <b>Pierce charge</b>: a white-hot ball of compressed blast swelling and crackling at his right palm;</li>
 *   <li>the <b>Crush Grip</b>: his palm glowing on the face he holds.</li>
 * </ul>
 * Two passes as in {@code HellflameRender}: the orange glow alpha-blended, then the hot cores in the additive buffer.
 */
public final class ExplosionRender {
	private static final int WHITE = 0xFFF6D8;
	private static final int YELLOW = 0xFFC040;
	private static final int ORANGE = 0xFF6A10;

	private ExplosionRender() {
	}

	static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(ExplosionRender::render);
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || ctx.consumers() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		List<Player> who = new ArrayList<>();
		for (Player p : level.players()) {
			if (Explosion.is(p) && p.distanceToSqr(mc.player) < 160 * 160 && (Explosion.spinning(p) || Explosion.piercing(p) || Explosion.gripping(p))) {
				who.add(p);
			}
		}
		if (who.isEmpty()) {
			return;
		}
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		for (boolean glowPass : new boolean[] { true, false }) {
			VertexConsumer vc = buffers.getBuffer(glowPass ? RenderType.debugQuads() : RenderType.lightning());
			for (Player p : who) {
				if (Explosion.spinning(p)) {
					vortex(vc, last, p, cam, partial, glowPass, p == mc.player && mc.options.getCameraType().isFirstPerson());
				}
			}
		}
		for (Player p : who) {
			boolean fp = p == mc.player && mc.options.getCameraType().isFirstPerson();
			if (Explosion.piercing(p)) {
				palmBall(pose, buffers, p, partial, fp, Math.max(0f, Explosion.pierceCharge(p, partial)));
			} else if (Explosion.gripping(p)) {
				palmBall(pose, buffers, p, partial, fp, 0.35f + 0.25f * Mth.sin((p.tickCount + partial) * 1.3f));
			}
		}
		pose.popPose();
	}

	/** The corkscrew: two flame strands wound round his long axis, turning fast, streaming out behind him. */
	private static void vortex(VertexConsumer vc, PoseStack.Pose last, Player p, Vec3 cam, float partial, boolean glow, boolean firstPerson) {
		Vec3 c = p.getPosition(partial).add(0, 0.9, 0);
		Vec3 vel = p.getDeltaMovement();
		Vec3 axis = vel.lengthSqr() < 0.04 ? new Vec3(0, 1, 0) : vel.normalize();
		Vec3 ref = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = axis.cross(ref).normalize();
		Vec3 w = axis.cross(u).normalize();
		float t = p.tickCount + partial;
		int segs = 22;
		double len = 3.6;
		for (int strand = 0; strand < 2; strand++) {
			Vec3 prev = null;
			for (int i = 0; i <= segs; i++) {
				double k = i / (double) segs; // 0 at him, 1 at the tail
				double ang = t * 0.95 + strand * Math.PI + k * Math.PI * 3.0;
				double r = (firstPerson ? 1.5 : 1.05) + 0.5 * k;
				Vec3 q = c.subtract(axis.scale(k * len - 0.6)).add(u.scale(Math.cos(ang) * r)).add(w.scale(Math.sin(ang) * r));
				if (prev != null) {
					float a0 = (float) (1.0 - (k - 1.0 / segs)), a1 = (float) (1.0 - k);
					if (glow) {
						BeamDraw.segment(vc, last, prev, q, cam, 0.42f * a0 + 0.08f, 0.42f * a1 + 0.08f, ORANGE, 0.5f * a0, 0.5f * a1);
					} else {
						BeamDraw.segment(vc, last, prev, q, cam, 0.18f * a0 + 0.03f, 0.18f * a1 + 0.03f, k < 0.35 ? WHITE : YELLOW, 0.85f * a0, 0.85f * a1);
					}
				}
				prev = q;
			}
		}
	}

	/** A ball of compressed blast at his right palm, {@code k} 0..1 (bigger and whiter as it charges). */
	private static void palmBall(PoseStack pose, MultiBufferSource buffers, Player p, float partial, boolean firstPerson, float k) {
		Vec3 eye = p.getEyePosition(partial);
		Vec3 look = p.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 at = firstPerson ? eye.add(look.scale(0.85)).add(right.scale(0.22)).add(0, -0.22, 0)
				: eye.add(look.scale(0.85)).add(right.scale(0.3)).add(0, -0.38, 0);
		float t = p.tickCount + partial;
		float r = (firstPerson ? 0.06f : 0.1f) + (firstPerson ? 0.1f : 0.2f) * k;
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		VertexConsumer vc = QuirkFxDraw.glow(buffers, QuirkFxDraw.WHITE);
		QuirkFxDraw.shell(vc, pose.last(), r * 0.6f, 0.3f, p.getId() * 13L, t * 0.9f, 0xFFF8E0, 0.9f);
		QuirkFxDraw.shell(vc, pose.last(), r, 0.35f, p.getId() * 29L, t * 0.7f, 0xFFA030, 0.45f + 0.3f * k);
		QuirkFxDraw.shell(vc, pose.last(), r * 1.6f, 0.4f, p.getId() * 37L, t * 0.5f, 0xFF5A10, 0.2f * k);
		pose.popPose();
	}
}

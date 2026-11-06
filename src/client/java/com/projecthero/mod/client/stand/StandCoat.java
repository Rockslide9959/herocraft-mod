package com.projecthero.mod.client.stand;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.stand.data.StandPoseState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "recreate it exactly how it looks in these reference images"): the long coat of the pointing pose, flaring out behind
 * the user on both sides from the waist -- the skirt of Jotaro's coat, dark outside, pale inside, its hem rippling. It belongs to the
 * user's pose, not the Stand, so every viewer sees it (a player's skin has no coat to flare). Two cloth panels, opening with the pose.
 */
public final class StandCoat {
	private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
	private static final int OUTER = 0x18232E;
	private static final int INNER = 0xD8D2C4;
	private static final int ACROSS = 22;
	private static final int DOWN = 10;
	/** How far round from the middle of the back each side of the skirt comes: the front stays open. */
	private static final float WRAP = 1.65f;

	private StandCoat() {
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		for (Player p : mc.level.players()) {
			StandPoseState s = p.getAttachedOrElse(ModAttachments.STAND_POSE, null);
			if (s == null || s.pose() == 0 || p.isInvisible() || p.isSpectator() || p.distanceToSqr(cam) > 64 * 64) {
				continue;
			}
			if (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
				continue;
			}
			float age = p.level().getGameTime() - s.start() + partial;
			float open = Mth.clamp(age / 6f, 0f, 1f);
			open = 1f - (1f - open) * (1f - open);
			Vec3 at = p.getPosition(partial);
			float yaw = (Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) + StarPlatinumPose.TURN * 0.5f * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
			PoseStack pose = ctx.matrixStack();
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			Matrix4f m = pose.last().pose();
			VertexConsumer vc = ctx.consumers().getBuffer(RenderType.entityTranslucent(WHITE));
			int light = LevelRenderer.getLightColor(p.level(), BlockPos.containing(at.add(0, 1, 0)));
			float t = p.level().getGameTime() + partial;
			panel(vc, pose, m, yaw, 1f, open, t, light);
			panel(vc, pose, m, yaw, -1f, open, t + 9f, light);
			pose.popPose();
		}
	}

	/** One side's skirt. {@code side}: +1 the wearer's right, -1 the left. */
	private static void panel(VertexConsumer vc, PoseStack pose, Matrix4f m, float yaw, float side, float open, float t, int light) {
		Vector3f fwd = new Vector3f(-Mth.sin(yaw), 0f, Mth.cos(yaw));
		Vector3f right = new Vector3f(-fwd.z, 0f, fwd.x);
		Vector3f[][] p = new Vector3f[ACROSS + 1][DOWN + 1];
		for (int a = 0; a <= ACROSS; a++) {
			for (int d = 0; d <= DOWN; d++) {
				float u = a / (float) ACROSS, v = d / (float) DOWN;
				float th = u * WRAP; // 0 at the middle of the back, round to the side
				// the skirt flares most off the sides, and rises there as it swings out
				float flare = Mth.sin(Math.min(th * 1.15f, Mth.PI)) * open;
				float pleat = (Mth.sin(th * 9f + side) * 0.1f + Mth.sin(th * 17f + side * 2f + t * 0.06f) * 0.04f) * v * (0.4f + flare);
				float r = 0.19f + v * (0.12f + 0.66f * flare) + pleat;
				float blown = 0.22f * v * open; // the whole hem is carried back
				float wave = (0.02f + 0.07f * v * flare) * Mth.sin(t * 0.2f + v * 3.5f + th * 2.5f);
				float drop = v * (0.86f - 0.24f * flare) + Mth.sin(th * 7f + side) * 0.07f * v;
				p[a][d] = new Vector3f(right).mul(Mth.sin(th) * r * side).add(new Vector3f(fwd).mul(-Mth.cos(th) * r - blown + wave))
						.add(0f, 0.98f - drop, 0f);
			}
		}
		for (int a = 0; a < ACROSS; a++) {
			for (int d = 0; d < DOWN; d++) {
				Vector3f p0 = p[a][d], p1 = p[a + 1][d], p2 = p[a + 1][d + 1], p3 = p[a][d + 1];
				Vector3f n = new Vector3f(p1).sub(p0).cross(new Vector3f(p3).sub(p0)).normalize();
				// the outside faces away from the wearer, the lining toward them, a hair apart
				Vector3f centre = new Vector3f(p0).add(p2).mul(0.5f);
				centre.y = 0f; // radial: the height plays no part in which way is out
				if (n.dot(centre) < 0f) {
					n.negate();
				}
				quad(vc, pose, m, p0, p1, p2, p3, n, 0.006f, OUTER, light);
				quad(vc, pose, m, p0, p1, p2, p3, new Vector3f(n).negate(), 0.006f, INNER, light);
			}
		}
	}

	private static void quad(VertexConsumer vc, PoseStack pose, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f n, float off, int rgb,
			int light) {
		Vector3f wn = pose.last().normal().transform(new Vector3f(n));
		for (Vector3f v : new Vector3f[] { a, b, c, d }) {
			Vector3f w = m.transformPosition(new Vector3f(v).add(new Vector3f(n).mul(off)));
			vc.addVertex(w.x, w.y, w.z, 0xFF000000 | rgb, 0.5f, 0.5f, OverlayTexture.NO_OVERLAY, light, wn.x, wn.y, wn.z);
		}
	}
}

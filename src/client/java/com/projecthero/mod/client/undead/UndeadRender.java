package com.projecthero.mod.client.undead;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.client.render.OverlayStrips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3: how Undead looks. The vanilla parts of a broken part are hidden ({@link #hideParts}, every frame, every viewer,
 * first person too); a part that is regrowing is drawn layer by layer ({@link #regrow}: bone, muscle, skin -- {@link UndeadRegrow}),
 * and a part missing only its end ({@link UndeadCuts}: hands and feet in flight, a burnt stage, a bitten arm) is drawn cut. Also the
 * flesh caps on a stump, the Undead Card on the forehead, Kurikara and the Dead Blade in his hand, and Victor's red haze.
 *
 * <p>The model's per-part world transform is remembered every frame ({@link #capture}) so a part bursts from exactly where
 * it was drawn.
 */
public final class UndeadRender {
	public static final ResourceLocation CARD = ProjectHeroMod.id("textures/entity/undead/card.png");
	public static final ResourceLocation FLESH = ProjectHeroMod.id("textures/entity/undead/flesh.png");
	public static final ResourceLocation PALETTE = ProjectHeroMod.id("textures/entity/undead/palette.png");

	private record Captured(Matrix4f[] parts, long nanos) {
	}

	private static final Map<UUID, Captured> CAPTURED = new ConcurrentHashMap<>();

	private UndeadRender() {
	}

	public static void reset() {
		CAPTURED.clear();
	}

	static void forget(UUID id) {
		CAPTURED.remove(id);
	}

	public static ModelPart part(HumanoidModel<?> model, int part) {
		return switch (part) {
			case UndeadState.HEAD -> model.head;
			case UndeadState.BODY -> model.body;
			case UndeadState.RIGHT_ARM -> model.rightArm;
			case UndeadState.LEFT_ARM -> model.leftArm;
			case UndeadState.RIGHT_LEG -> model.rightLeg;
			default -> model.leftLeg;
		};
	}

	public static ModelPart overlay(PlayerModel<?> model, int part) {
		return switch (part) {
			case UndeadState.HEAD -> model.hat;
			case UndeadState.BODY -> model.jacket;
			case UndeadState.RIGHT_ARM -> model.rightSleeve;
			case UndeadState.LEFT_ARM -> model.leftSleeve;
			case UndeadState.RIGHT_LEG -> model.rightPants;
			default -> model.leftPants;
		};
	}

	/** Hides every broken part (and its overlay) on the shared model. From {@code PlayerModel.setupAnim} TAIL. */
	public static void hideParts(Player player, PlayerModel<?> model) {
		UndeadState s = Undead.peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		float now = player.level().getGameTime();
		for (int i = 0; i < UndeadState.PARTS; i++) {
			if (s.broken(i) || UndeadCuts.of(s, i, now) != null) {
				part(model, i).visible = false;
				overlay(model, i).visible = false;
			}
		}
	}

	// ---------------------------------------------------------------- where the parts are

	/** Remembers the world transform of each part as drawn this frame (from the layer). */
	static void capture(Player player, PoseStack pose, PlayerModel<?> model) {
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Matrix4f[] out = new Matrix4f[UndeadState.PARTS];
		for (int i = 0; i < UndeadState.PARTS; i++) {
			pose.pushPose();
			part(model, i).translateAndRotate(pose);
			out[i] = new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose());
			pose.popPose();
		}
		CAPTURED.put(player.getUUID(), new Captured(out, System.nanoTime()));
	}

	/** The world transform of a part (its own space in blocks): as last drawn, or the standing pose if not drawn lately. */
	public static Matrix4f partMatrix(Player player, int part) {
		Captured c = CAPTURED.get(player.getUUID());
		if (c != null && System.nanoTime() - c.nanos() < 250_000_000L) {
			return new Matrix4f(c.parts()[part]);
		}
		return standing(player.position(), player.yBodyRot, slim(player), part);
	}

	/** A part in the standing pose of a player at {@code pos} facing {@code bodyYaw}. */
	public static Matrix4f standing(Vec3 pos, float bodyYaw, boolean slim, int part) {
		Matrix4f m = new Matrix4f().translation((float) pos.x, (float) pos.y, (float) pos.z);
		m.rotateY((180f - bodyYaw) * Mth.DEG_TO_RAD);
		m.scale(-1f, -1f, 1f);
		m.scale(0.9375f);
		m.translate(0f, -1.501f, 0f);
		float[] pivot = switch (part) {
			case UndeadState.RIGHT_ARM -> new float[] { -5f, slim ? 2.5f : 2f, 0f };
			case UndeadState.LEFT_ARM -> new float[] { 5f, slim ? 2.5f : 2f, 0f };
			case UndeadState.RIGHT_LEG -> new float[] { -1.9f, 12f, 0f };
			case UndeadState.LEFT_LEG -> new float[] { 1.9f, 12f, 0f };
			default -> new float[] { 0f, 0f, 0f };
		};
		return m.translate(pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f);
	}

	public static boolean slim(Player player) {
		return player instanceof AbstractClientPlayer acp && acp.getSkin().model() == PlayerSkin.Model.SLIM;
	}

	public static ResourceLocation skin(Player player) {
		return player instanceof AbstractClientPlayer acp ? acp.getSkin().texture() : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
	}

	// ---------------------------------------------------------------- the layer

	static void renderLayer(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
			float partial) {
		capture(player, pose, model);
		UndeadState s = Undead.peek(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float now = player.level().getGameTime() + partial;
		ResourceLocation tex = skin(player);
		boolean slim = slim(player);
		boolean bodyGone = s.broken(UndeadState.BODY);
		for (int i = 0; i < UndeadState.PARTS; i++) {
			if (!s.broken(i)) {
				UndeadCuts.Cut cut = UndeadCuts.of(s, i, now);
				if (cut != null) {
					pose.pushPose();
					part(model, i).translateAndRotate(pose);
					drawCut(pose, buffers, light, tex, slim, i, cut);
					pose.popPose();
				}
				continue;
			}
			float p = s.regrowth(i, now);
			if (p < UndeadRegrow.SKIN_START + 0.04f) {
				// the wet stump face, until the skin has crept over the joint
				stump(pose, buffers, light, model, i, slim, bodyGone);
			}
			if (p > 0f) {
				// (p >= 1: the server has not cleared it yet -- drawn whole)
				pose.pushPose();
				part(model, i).translateAndRotate(pose);
				int end = UndeadState.extremity(i);
				UndeadRegrow.drawPart(pose, buffers, light, tex, slim, i, Math.min(1f, p), end < 0 ? 0f : s.regrowth(end, now));
				pose.popPose();
			}
		}
		if (bodyGone) {
			// the limbs and head still there float on their own: a cap where each met the body
			for (int i = 0; i < UndeadState.PARTS; i++) {
				if (i != UndeadState.BODY && !s.broken(i)) {
					ownCap(pose, buffers, light, model, i, slim);
				}
			}
		}
		long age = (long) (now - s.animStart);
		boolean victor = com.projecthero.mod.undead.UndeadConfig.VICTOR_ENABLED && s.victorUntil > now;
		if (!s.broken(UndeadState.HEAD)) {
			float out = 0f;
			if (s.animId == UndeadState.ANIM_CARD && age < 12) {
				out = age / 12f;
			} else if (s.animId == UndeadState.ANIM_CARD_BACK && age < 10) {
				out = 1f - age / 10f;
			} else if (victor) {
				out = 1f;
			}
			if (out < 1f) {
				pose.pushPose();
				model.head.translateAndRotate(pose);
				card(pose, buffers, light, out);
				pose.popPose();
			}
		}
		renderHeld(pose, buffers, light, player, model, s, now);
		UndeadHeadThrowRender.layer(pose, buffers, light, player, model, s); // v0.17.3 hold X: the raw neck while the head is held back
		UndeadBoneSwordRender.thirdPerson(pose, buffers, light, player, model, s, now); // v0.17.3 Shift+V
		if (victor) {
			float pulse = 0.55f + 0.45f * Mth.sin(now * 0.25f);
			int a = (int) (40 + 40 * pulse);
			int tint = (a << 24) | 0xFF2626;
			OverlayStrips.renderSkin(pose, buffers, 0xF000F0, player, model, (u, v) -> {
				int part = UndeadTexels.partOfBase(u, v);
				return part < 0 || s.broken(part) ? 0 : tint;
			}, true, true, 0.4f);
		}
	}

	/** Kurikara (G) and the Dead Blade (Victor's G) in the main hand while the swing runs. */
	private static void renderHeld(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, UndeadState s, float now) {
		float age = now - s.animStart;
		int arm = Undead.mainArm(player);
		if (s.broken(arm)) {
			return;
		}
		boolean sword = (s.animId == UndeadState.ANIM_CRESCENT && age < 14f) || (s.animId == UndeadState.ANIM_GREAT_CRESCENT && age < 16f);
		boolean blade = s.animId == UndeadState.ANIM_DEAD_BLADE && age < 16f;
		if (!sword && !blade) {
			return;
		}
		pose.pushPose();
		part(model, arm).translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PALETTE));
		float cx = arm == UndeadState.RIGHT_ARM ? -1f : 1f;
		// drawn out of the forearm: the blade slides out over the first few ticks
		float draw = Mth.clamp(age / 3f, 0.2f, 1f);
		if (sword) {
			box(vc, pose, cx - 0.5f, 8.5f, -0.6f, cx + 0.5f, 11.5f, 0.6f, 0, 0, light); // the grip, in the fist
			box(vc, pose, cx - 1.3f, 11.5f, -0.9f, cx + 1.3f, 12.2f, 0.9f, 4, 0, light); // the guard
			float len = 20f * draw;
			box(vc, pose, cx - 0.35f, 12.2f, -0.8f, cx + 0.35f, 12.2f + len, 0.5f, 1, 0, light); // the blade
			box(vc, pose, cx - 0.36f, 12.2f, 0.5f, cx + 0.36f, 12.2f + len, 0.85f, 2, 0, light); // its bright edge
		} else {
			float len = 34f * draw;
			box(vc, pose, cx - 0.6f, 8.5f, -0.7f, cx + 0.6f, 11.5f, 0.7f, 3, 0, 0xF000F0);
			box(vc, pose, cx - 2.6f, 11.5f, -1.2f, cx + 2.6f, 12.6f, 1.2f, 3, 0, 0xF000F0);
			box(vc, pose, cx - 0.9f, 12.6f, -2.2f, cx + 0.9f, 12.6f + len, 1.6f, 3, 0, 0xF000F0);
			box(vc, pose, cx - 0.92f, 12.6f, 1.6f, cx + 0.92f, 12.6f + len * 0.96f, 2.6f, 2, 0, 0xF000F0);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- the regrowth

	/** A part re-forming, in its own space (the pose already at the part), {@code p} 0..1 ({@link UndeadRegrow}). */
	public static void regrow(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		UndeadRegrow.draw(pose, buffers, light, tex, slim, part, p);
	}

	/** A part missing its end ({@link UndeadCuts}), in its own space: cut, or growing back from the cut. */
	public static void drawCut(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, UndeadCuts.Cut cut) {
		UndeadRegrow.drawEnd(pose, buffers, light, tex, slim, part, cut);
	}

	/** The four corners of a texel's square, {@code out} px along its normal, grown by {@code grow} px. */
	private static float[][] corners(UndeadTexels.Texel t, float out, float grow) {
		float x = t.x + t.nx * out, y = t.y + t.ny * out, z = t.z + t.nz * out;
		float a = t.ha + grow, b = t.hb + grow;
		return switch (t.plane) {
			case 0 -> new float[][] { { x, y - b, z - a }, { x, y - b, z + a }, { x, y + b, z + a }, { x, y + b, z - a } };
			case 1 -> new float[][] { { x - a, y, z - b }, { x + a, y, z - b }, { x + a, y, z + b }, { x - a, y, z + b } };
			default -> new float[][] { { x - a, y - b, z }, { x + a, y - b, z }, { x + a, y + b, z }, { x - a, y + b, z } };
		};
	}

	static float[][] cornersPublic(UndeadTexels.Texel t, float out, float grow) {
		return corners(t, out, grow);
	}

	private static void cube(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] p, float u0, float v0, float u1, float v1, int light) {
		int[][] faces = { { 0, 4, 6, 2 }, { 1, 3, 7, 5 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 2, 3, 1 }, { 4, 5, 7, 6 } };
		Matrix4f m = pose.pose();
		for (int[] f : faces) {
			Vector3f a = p[f[0]], b = p[f[1]], c = p[f[2]];
			Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
			if (n.lengthSquared() > 1.0e-12f) {
				n.normalize();
			} else {
				n.set(0, 1, 0);
			}
			float[][] uv = { { u0, v0 }, { u1, v0 }, { u1, v1 }, { u0, v1 } };
			for (int i = 0; i < 4; i++) {
				Vector3f v = p[f[i]];
				vc.addVertex(m, v.x, v.y, v.z).setColor(255, 255, 255, 255).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light).setNormal(pose, n.x, n.y, n.z);
			}
		}
	}

	static void cubePublic(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] p, float u0, float v0, float u1, float v1, int light) {
		cube(vc, pose, p, u0, v0, u1, v1, light);
	}

	// ---------------------------------------------------------------- stumps, the card, boxes

	/** The flesh cap where a missing part met the body (drawn on the body). */
	private static void stump(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<?> model, int part, boolean slim, boolean bodyGone) {
		if (part == UndeadState.BODY) {
			return;
		}
		if (bodyGone) {
			return; // the caps go on the floating parts instead (ownCap)
		}
		pose.pushPose();
		model.body.translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FLESH));
		switch (part) {
			case UndeadState.HEAD -> capY(vc, pose, -3.2f, 3.2f, -1.9f, 1.9f, -0.04f, light);
			case UndeadState.RIGHT_ARM -> capX(vc, pose, -4.04f, 0.2f, 3.8f, -1.9f, 1.9f, light);
			case UndeadState.LEFT_ARM -> capX(vc, pose, 4.04f, 0.2f, 3.8f, -1.9f, 1.9f, light);
			case UndeadState.RIGHT_LEG -> capY(vc, pose, -3.9f, -0.1f, -1.9f, 1.9f, 12.04f, light);
			default -> capY(vc, pose, 0.1f, 3.9f, -1.9f, 1.9f, 12.04f, light);
		}
		pose.popPose();
	}

	/** With the body gone: a cap on the part itself where it met the body. */
	private static void ownCap(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<?> model, int part, boolean slim) {
		pose.pushPose();
		part(model, part).translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FLESH));
		float[] b = UndeadTexels.box(part, slim);
		switch (part) {
			case UndeadState.HEAD -> capY(vc, pose, -3.4f, 3.4f, -3.4f, 3.4f, 0.04f, light);
			case UndeadState.RIGHT_ARM -> capX(vc, pose, b[0] + b[3] + 0.04f, -1.8f, 3.8f, -1.9f, 1.9f, light);
			case UndeadState.LEFT_ARM -> capX(vc, pose, b[0] - 0.04f, -1.8f, 3.8f, -1.9f, 1.9f, light);
			default -> capY(vc, pose, -1.9f, 1.9f, -1.9f, 1.9f, -0.04f, light);
		}
		pose.popPose();
	}

	private static void capY(VertexConsumer vc, PoseStack pose, float x0, float x1, float z0, float z1, float y, int light) {
		Matrix4f m = pose.last().pose();
		float[][] p = { { x0, z0, 0, 0 }, { x1, z0, 1, 0 }, { x1, z1, 1, 1 }, { x0, z1, 0, 1 } };
		for (float[] v : p) {
			vc.addVertex(m, v[0] / 16f, y / 16f, v[1] / 16f).setColor(255, 255, 255, 255).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(pose.last(), 0, -1, 0);
		}
	}

	private static void capX(VertexConsumer vc, PoseStack pose, float x, float y0, float y1, float z0, float z1, int light) {
		Matrix4f m = pose.last().pose();
		float[][] p = { { y0, z0, 0, 0 }, { y0, z1, 1, 0 }, { y1, z1, 1, 1 }, { y1, z0, 0, 1 } };
		for (float[] v : p) {
			vc.addVertex(m, x / 16f, v[0] / 16f, v[1] / 16f).setColor(255, 255, 255, 255).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(pose.last(), Math.signum(x), 0, 0);
		}
	}

	/** The Undead Card above the left eye ({@code out} 0..1: pulled away from the forehead). */
	static void card(PoseStack pose, MultiBufferSource buffers, int light, float out) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CARD));
		pose.pushPose();
		float fwd = out * 6f;
		pose.translate(1.6f / 16f, -6.3f / 16f, (-4.56f - fwd) / 16f);
		pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-9f + out * 40f));
		float s = 1f - out * 0.6f;
		float hw = 1.15f * s, hh = 1.55f * s;
		Matrix4f m = pose.last().pose();
		float[][] p = { { -hw, -hh, 0, 0 }, { hw, -hh, 1, 0 }, { hw, hh, 1, 1 }, { -hw, hh, 0, 1 } };
		for (float[] v : p) {
			vc.addVertex(m, v[0] / 16f, v[1] / 16f, 0f).setColor(255, 255, 255, 255).setUv(1f - v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(pose.last(), 0, 0, -1);
		}
		pose.popPose();
	}

	/** A box in model px textured with one texel ({@code tu}, {@code tv}) of the 16x16 palette. */
	static void box(VertexConsumer vc, PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int tu, int tv, int light) {
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f(((i & 1) == 0 ? x0 : x1) / 16f, ((i & 2) == 0 ? y0 : y1) / 16f, ((i & 4) == 0 ? z0 : z1) / 16f);
		}
		cube(vc, pose.last(), c, (tu + 0.2f) / 16f, (tv + 0.2f) / 16f, (tu + 0.8f) / 16f, (tv + 0.8f) / 16f, light);
	}
}

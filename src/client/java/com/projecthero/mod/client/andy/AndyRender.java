package com.projecthero.mod.client.andy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.data.AndyState;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.3: how Andy looks. The vanilla parts of a broken part are hidden ({@link #hideParts}, every frame, every viewer,
 * first person too); a part that is regrowing is drawn here texel by texel ({@link #regrow}): the skin's own pixels fly out
 * of the stump one by one, each to its exact place, and click in from the joint outward behind a thin glowing line. Also the
 * flesh caps on a stump, the Undead Card on the forehead, Kurikara and the Dead Blade in his hand, and Victor's red haze.
 *
 * <p>The model's per-part world transform is remembered every frame ({@link #capture}) so a part bursts from exactly where
 * it was drawn.
 */
public final class AndyRender {
	public static final ResourceLocation CARD = ProjectHeroMod.id("textures/entity/andy/card.png");
	public static final ResourceLocation FLESH = ProjectHeroMod.id("textures/entity/andy/flesh.png");
	public static final ResourceLocation PALETTE = ProjectHeroMod.id("textures/entity/andy/palette.png");

	/** The share of a regrowth each pixel spends flying in from the stump. */
	static final float FLIGHT = 0.34f;
	/** How long (as a share of the regrowth) a pixel flashes after it clicks in. */
	static final float FLASH = 0.08f;

	private record Captured(Matrix4f[] parts, long nanos) {
	}

	private static final Map<UUID, Captured> CAPTURED = new ConcurrentHashMap<>();

	private AndyRender() {
	}

	public static void reset() {
		CAPTURED.clear();
	}

	static void forget(UUID id) {
		CAPTURED.remove(id);
	}

	public static ModelPart part(HumanoidModel<?> model, int part) {
		return switch (part) {
			case AndyState.HEAD -> model.head;
			case AndyState.BODY -> model.body;
			case AndyState.RIGHT_ARM -> model.rightArm;
			case AndyState.LEFT_ARM -> model.leftArm;
			case AndyState.RIGHT_LEG -> model.rightLeg;
			default -> model.leftLeg;
		};
	}

	public static ModelPart overlay(PlayerModel<?> model, int part) {
		return switch (part) {
			case AndyState.HEAD -> model.hat;
			case AndyState.BODY -> model.jacket;
			case AndyState.RIGHT_ARM -> model.rightSleeve;
			case AndyState.LEFT_ARM -> model.leftSleeve;
			case AndyState.RIGHT_LEG -> model.rightPants;
			default -> model.leftPants;
		};
	}

	/** Hides every broken part (and its overlay) on the shared model. From {@code PlayerModel.setupAnim} TAIL. */
	public static void hideParts(Player player, PlayerModel<?> model) {
		AndyState s = Andy.peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		for (int i = 0; i < AndyState.PARTS; i++) {
			if (s.broken(i)) {
				part(model, i).visible = false;
				overlay(model, i).visible = false;
			}
		}
	}

	// ---------------------------------------------------------------- where the parts are

	/** Remembers the world transform of each part as drawn this frame (from the layer). */
	static void capture(Player player, PoseStack pose, PlayerModel<?> model) {
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Matrix4f[] out = new Matrix4f[AndyState.PARTS];
		for (int i = 0; i < AndyState.PARTS; i++) {
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
			case AndyState.RIGHT_ARM -> new float[] { -5f, slim ? 2.5f : 2f, 0f };
			case AndyState.LEFT_ARM -> new float[] { 5f, slim ? 2.5f : 2f, 0f };
			case AndyState.RIGHT_LEG -> new float[] { -1.9f, 12f, 0f };
			case AndyState.LEFT_LEG -> new float[] { 1.9f, 12f, 0f };
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
		AndyState s = Andy.peek(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return;
		}
		float now = player.level().getGameTime() + partial;
		ResourceLocation tex = skin(player);
		boolean slim = slim(player);
		boolean bodyGone = s.broken(AndyState.BODY);
		for (int i = 0; i < AndyState.PARTS; i++) {
			if (!s.broken(i)) {
				continue;
			}
			float p = s.regrowth(i, now);
			if (p < 0.22f) {
				stump(pose, buffers, light, model, i, slim, bodyGone);
			}
			if (p > 0f && p < 1f) {
				pose.pushPose();
				part(model, i).translateAndRotate(pose);
				regrow(pose, buffers, light, tex, slim, i, p);
				pose.popPose();
			} else if (p >= 1f) {
				// the server has not cleared it yet: draw it whole
				pose.pushPose();
				part(model, i).translateAndRotate(pose);
				regrow(pose, buffers, light, tex, slim, i, 1f);
				pose.popPose();
			}
		}
		if (bodyGone) {
			// the limbs and head still there float on their own: a cap where each met the body
			for (int i = 0; i < AndyState.PARTS; i++) {
				if (i != AndyState.BODY && !s.broken(i)) {
					ownCap(pose, buffers, light, model, i, slim);
				}
			}
		}
		long age = (long) (now - s.animStart);
		boolean victor = s.victorUntil > now;
		if (!s.broken(AndyState.HEAD)) {
			float out = 0f;
			if (s.animId == AndyState.ANIM_CARD && age < 12) {
				out = age / 12f;
			} else if (s.animId == AndyState.ANIM_CARD_BACK && age < 10) {
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
		if (victor) {
			float pulse = 0.55f + 0.45f * Mth.sin(now * 0.25f);
			int a = (int) (40 + 40 * pulse);
			int tint = (a << 24) | 0xFF2626;
			OverlayStrips.renderSkin(pose, buffers, 0xF000F0, player, model, (u, v) -> {
				int part = AndyTexels.partOfBase(u, v);
				return part < 0 || s.broken(part) ? 0 : tint;
			}, true, true, 0.4f);
		}
	}

	/** Kurikara (G) and the Dead Blade (Victor's G) in the main hand while the swing runs. */
	private static void renderHeld(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, AndyState s, float now) {
		float age = now - s.animStart;
		int arm = Andy.mainArm(player);
		if (s.broken(arm)) {
			return;
		}
		boolean sword = (s.animId == AndyState.ANIM_CRESCENT && age < 14f) || (s.animId == AndyState.ANIM_GREAT_CRESCENT && age < 16f);
		boolean blade = s.animId == AndyState.ANIM_DEAD_BLADE && age < 16f;
		if (!sword && !blade) {
			return;
		}
		pose.pushPose();
		part(model, arm).translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PALETTE));
		float cx = arm == AndyState.RIGHT_ARM ? -1f : 1f;
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

	/**
	 * A part re-forming, in its own space (the pose already at the part): at {@code p} (0..1) the pixels whose turn has come
	 * are in place, the next ones are flying in from the joint, and a thin glowing line runs along the frontier.
	 */
	public static void regrow(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		List<AndyTexels.Texel> texels = AndyTexels.of(part, slim);
		float[] joint = AndyTexels.joint(part, slim);
		PoseStack.Pose last = pose.last();
		Matrix4f m = last.pose();
		Quaternionf q = new Quaternionf();
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f();
		}
		java.util.List<AndyTexels.Texel> flashing = new java.util.ArrayList<>();
		for (AndyTexels.Texel t : texels) {
			if (!SkinPixels.shows(tex, t.u, t.v)) {
				continue;
			}
			float click = t.order * (1f - FLIGHT) + FLIGHT;
			float launch = click - FLIGHT;
			float u0 = (t.u + 0.04f) / 64f, v0 = (t.v + 0.04f) / 64f, u1 = (t.u + 0.96f) / 64f, v1 = (t.v + 0.96f) / 64f;
			if (p >= click) {
				square(skin, last, t, u0, v0, u1, v1, light, 0f);
				if (p - click < FLASH) {
					flashing.add(t);
				}
			} else if (p >= launch) {
				float f = (p - launch) / FLIGHT;
				float e = f * f * (3f - 2f * f);
				// from the stump (a spot on the cut), bulging out past the surface, home to its own place
				float sx = joint[0] + (t.seed - 0.5f) * 2.4f, sy = joint[1], sz = joint[2] + (((t.seed * 7.31f) % 1f) - 0.5f) * 2.4f;
				float tx = t.x - t.nx * 0.5f, ty = t.y - t.ny * 0.5f, tz = t.z - t.nz * 0.5f;
				float bulge = 2.2f + t.seed * 2.2f;
				float mx = (sx + tx) * 0.5f + t.nx * bulge, my = (sy + ty) * 0.5f + t.ny * bulge, mz = (sz + tz) * 0.5f + t.nz * bulge;
				float a = 1f - e;
				float x = a * a * sx + 2 * a * e * mx + e * e * tx;
				float y = a * a * sy + 2 * a * e * my + e * e * ty;
				float z = a * a * sz + 2 * a * e * mz + e * e * tz;
				float size = (0.55f + 0.45f * e) * 0.5f;
				float spin = (1f - e) * (1f - e) * (4f + t.seed * 5f);
				q.setAngleAxis(spin, 0.4f + t.seed * 0.6f, 1f - t.seed, 0.3f);
				for (int i = 0; i < 8; i++) {
					c[i].set((i & 1) == 0 ? -size : size, (i & 2) == 0 ? -size : size, (i & 4) == 0 ? -size : size).rotate(q).add(x, y, z).mul(1f / 16f);
				}
				cube(skin, last, c, u0, v0, u1, v1, light);
			}
		}
		VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
		for (AndyTexels.Texel t : flashing) {
			float click = t.order * (1f - FLIGHT) + FLIGHT;
			float k = 1f - (p - click) / FLASH;
			glowSquare(glow, m, t, 0.06f, 1f, 0.82f, 0.7f, 0.55f * k);
		}
		float frontier = (p - FLIGHT) / (1f - FLIGHT);
		if (frontier > 0f && frontier < 1f) {
			ring(glow, m, part, slim, frontier);
		}
	}

	/** One placed texel: its square on the surface (pushed {@code out} px further out). */
	private static void square(VertexConsumer vc, PoseStack.Pose pose, AndyTexels.Texel t, float u0, float v0, float u1, float v1, int light, float out) {
		float[][] p = corners(t, out, 0f);
		Matrix4f m = pose.pose();
		for (int i = 0; i < 4; i++) {
			float uu = (i == 1 || i == 2) ? u1 : u0;
			float vv = (i >= 2) ? v1 : v0;
			vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(255, 255, 255, 255).setUv(uu, vv)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, t.nx, t.ny, t.nz);
		}
	}

	/** The four corners of a texel's square, {@code out} px along its normal, grown by {@code grow} px. */
	private static float[][] corners(AndyTexels.Texel t, float out, float grow) {
		float x = t.x + t.nx * out, y = t.y + t.ny * out, z = t.z + t.nz * out;
		float a = t.ha + grow, b = t.hb + grow;
		return switch (t.plane) {
			case 0 -> new float[][] { { x, y - b, z - a }, { x, y - b, z + a }, { x, y + b, z + a }, { x, y + b, z - a } };
			case 1 -> new float[][] { { x - a, y, z - b }, { x + a, y, z - b }, { x + a, y, z + b }, { x - a, y, z + b } };
			default -> new float[][] { { x - a, y - b, z }, { x + a, y - b, z }, { x + a, y + b, z }, { x - a, y + b, z } };
		};
	}

	private static void glowSquare(VertexConsumer vc, Matrix4f m, AndyTexels.Texel t, float out, float r, float g, float b, float a) {
		float[][] p = corners(t, out, 0.05f);
		for (int i = 0; i < 4; i++) {
			vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(r, g, b, a);
		}
		for (int i = 3; i >= 0; i--) {
			vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(r, g, b, a);
		}
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

	/** The thin bright line round the part where the pixels are clicking in. */
	private static void ring(VertexConsumer vc, Matrix4f m, int part, boolean slim, float frontier) {
		float[] b = AndyTexels.box(part, slim);
		float y = switch (part) {
			case AndyState.HEAD -> -frontier * 8f;
			case AndyState.RIGHT_ARM, AndyState.LEFT_ARM -> b[1] + frontier * b[4];
			default -> frontier * b[4];
		};
		float grow = (part == AndyState.HEAD ? 0.5f : 0.25f) + 0.18f;
		float x0 = b[0] - grow, x1 = b[0] + b[3] + grow, z0 = b[2] - grow, z1 = b[2] + b[5] + grow;
		band(vc, m, x0, x1, z0, z1, y, 0.22f, 1f, 0.92f, 0.85f, 0.95f);
		band(vc, m, x0 - 0.15f, x1 + 0.15f, z0 - 0.15f, z1 + 0.15f, y, 0.75f, 1f, 0.25f, 0.2f, 0.35f);
	}

	private static void band(VertexConsumer vc, Matrix4f m, float x0, float x1, float z0, float z1, float y, float half, float r, float g, float b, float a) {
		float ya = (y - half) / 16f, yb = (y + half) / 16f;
		float[][] sides = { { x0, z0, x1, z0 }, { x1, z0, x1, z1 }, { x1, z1, x0, z1 }, { x0, z1, x0, z0 } };
		for (float[] s : sides) {
			float ax = s[0] / 16f, az = s[1] / 16f, bx = s[2] / 16f, bz = s[3] / 16f;
			vc.addVertex(m, ax, ya, az).setColor(r, g, b, a * 0.4f);
			vc.addVertex(m, bx, ya, bz).setColor(r, g, b, a * 0.4f);
			vc.addVertex(m, bx, (ya + yb) * 0.5f, bz).setColor(r, g, b, a);
			vc.addVertex(m, ax, (ya + yb) * 0.5f, az).setColor(r, g, b, a);
			vc.addVertex(m, ax, (ya + yb) * 0.5f, az).setColor(r, g, b, a);
			vc.addVertex(m, bx, (ya + yb) * 0.5f, bz).setColor(r, g, b, a);
			vc.addVertex(m, bx, yb, bz).setColor(r, g, b, a * 0.4f);
			vc.addVertex(m, ax, yb, az).setColor(r, g, b, a * 0.4f);
			// and the back faces
			vc.addVertex(m, ax, yb, az).setColor(r, g, b, a * 0.4f);
			vc.addVertex(m, bx, yb, bz).setColor(r, g, b, a * 0.4f);
			vc.addVertex(m, bx, (ya + yb) * 0.5f, bz).setColor(r, g, b, a);
			vc.addVertex(m, ax, (ya + yb) * 0.5f, az).setColor(r, g, b, a);
			vc.addVertex(m, ax, (ya + yb) * 0.5f, az).setColor(r, g, b, a);
			vc.addVertex(m, bx, (ya + yb) * 0.5f, bz).setColor(r, g, b, a);
			vc.addVertex(m, bx, ya, bz).setColor(r, g, b, a * 0.4f);
			vc.addVertex(m, ax, ya, az).setColor(r, g, b, a * 0.4f);
		}
	}

	// ---------------------------------------------------------------- stumps, the card, boxes

	/** The flesh cap where a missing part met the body (drawn on the body). */
	private static void stump(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<?> model, int part, boolean slim, boolean bodyGone) {
		if (part == AndyState.BODY) {
			return;
		}
		if (bodyGone) {
			return; // the caps go on the floating parts instead (ownCap)
		}
		pose.pushPose();
		model.body.translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FLESH));
		switch (part) {
			case AndyState.HEAD -> capY(vc, pose, -3.2f, 3.2f, -1.9f, 1.9f, -0.04f, light);
			case AndyState.RIGHT_ARM -> capX(vc, pose, -4.04f, 0.2f, 3.8f, -1.9f, 1.9f, light);
			case AndyState.LEFT_ARM -> capX(vc, pose, 4.04f, 0.2f, 3.8f, -1.9f, 1.9f, light);
			case AndyState.RIGHT_LEG -> capY(vc, pose, -3.9f, -0.1f, -1.9f, 1.9f, 12.04f, light);
			default -> capY(vc, pose, 0.1f, 3.9f, -1.9f, 1.9f, 12.04f, light);
		}
		pose.popPose();
	}

	/** With the body gone: a cap on the part itself where it met the body. */
	private static void ownCap(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<?> model, int part, boolean slim) {
		pose.pushPose();
		part(model, part).translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FLESH));
		float[] b = AndyTexels.box(part, slim);
		switch (part) {
			case AndyState.HEAD -> capY(vc, pose, -3.4f, 3.4f, -3.4f, 3.4f, 0.04f, light);
			case AndyState.RIGHT_ARM -> capX(vc, pose, b[0] + b[3] + 0.04f, -1.8f, 3.8f, -1.9f, 1.9f, light);
			case AndyState.LEFT_ARM -> capX(vc, pose, b[0] - 0.04f, -1.8f, 3.8f, -1.9f, 1.9f, light);
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

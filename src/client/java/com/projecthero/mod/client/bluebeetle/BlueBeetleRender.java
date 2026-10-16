package com.projecthero.mod.client.bluebeetle;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.BlueBeetleConfig;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.joml.Matrix4f;

/**
 * v0.16.5: Blue Beetle's armour over a player -- the user's Planet Minecraft skin ("Blue Beetle Young Justice Version",
 * file 19182617, 64x64) on a wide player model a hair larger than the wearer's (the Nova technique).
 *
 * <p>v0.16.5 rework (user: "remove the blue glow, make the armour build itself pixel by pixel starting from where the
 * scarab is on the players back and moving outwards across the rest of the body"): every skin texel has an {@link #order}
 * -- how far its spot on the standing body is from the Scarab (upper back), with a little hash jitter so it fills in one
 * texel at a time -- and the armour shows every texel up to the current point. No edge glow. The bonding (5 s) and H
 * both build it this way; H off runs it backwards. The wearer's own second layer goes texel by texel in step
 * ({@code OverlayStrips} with the same order). The eyes and studs glow once it is complete; Khaji-Da Unleashed washes it
 * red. Extras drawn in the posed parts' own space:
 * <ul>
 *   <li><b>wings</b> (while flying): four glowing blue insect wings spread from the back plate, still;</li>
 *   <li><b>the energy blade</b> (C toggle): a blue blade out of the main fist along the forearm;</li>
 *   <li><b>the energy shield</b> (held V): a round blue shield on the off arm.</li>
 * </ul>
 */
public final class BlueBeetleRender {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/blue_beetle/blue_beetle.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/blue_beetle/blue_beetle_glow.png");
	public static final int FRAMES = 64;
	/** The Scarab on the back, in standing-body model pixels (y down from the neck, +z the back). */
	private static final float SX = 0f, SY = 4.5f, SZ = 2f;

	private static PlayerModel<LivingEntity> model;
	private static ResourceLocation[] frames;
	private static float[] orders;

	private BlueBeetleRender() {
	}

	/** {@code cover}: how far the build has got, 0..1 (every texel with {@link #order} at or below it is armour). */
	public record Anim(float cover, boolean fresh) {
		static final Anim NONE = new Anim(0f, false);
		static final Anim FULL = new Anim(1f, false);

		public boolean anything() {
			return cover > 0f;
		}

		public boolean full() {
			return cover >= 1f;
		}
	}

	public static PlayerModel<LivingEntity> model() {
		if (model == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.04f), false);
			model = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return model;
	}

	static BlueBeetleState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
	}

	public static Anim anim(Player player, float partial) {
		BlueBeetleState s = state(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return Anim.NONE;
		}
		float age = player.level().getGameTime() - s.suitChangeAt + partial;
		if (age < 0f && age > -40f) {
			age = 0f;
		}
		if (s.suited) {
			float total = s.animId == BlueBeetleState.ANIM_BOND ? BlueBeetleConfig.BOND_TICKS : BlueBeetleConfig.SUIT_UP_TICKS;
			if (s.suitChangeAt <= 0L || age >= total || age < 0f) {
				return Anim.FULL;
			}
			return new Anim(Mth.clamp(age / total, 0f, 1f), true);
		}
		float total = BlueBeetleConfig.SUIT_DOWN_TICKS;
		if (s.suitChangeAt <= 0L || age >= total || age < 0f) {
			return Anim.NONE;
		}
		return new Anim(1f - Mth.clamp(age / total, 0f, 1f), true);
	}

	public static float progress(Player player, float partial) {
		return anim(player, partial).cover();
	}

	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent) {
		PlayerModel<LivingEntity> m = model();
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		m.setAllVisible(true);
		return m;
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, Player player, Anim a, float partial,
			float ageInTicks) {
		BlueBeetleState s = state(player);
		if (a.anything()) {
			PlayerModel<LivingEntity> m = posed(parent);
			ResourceLocation tex = a.full() ? SKIN : frame(a.cover());
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			if (a.full()) {
				float pulse = 0.75f + 0.25f * Mth.sin(ageInTicks * 0.2f);
				int g = Math.round(255 * pulse);
				m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
						(g << 24) | 0xFFFFFF);
				if (s != null && s.unleashedUntil > player.level().getGameTime()) {
					float k = 0.3f + 0.12f * Mth.sin(ageInTicks * 0.6f);
					int red = (255 << 24) | (Math.round(255 * k) << 16) | (Math.round(40 * k) << 8) | Math.round(30 * k);
					m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(SKIN)), light, OverlayTexture.NO_OVERLAY, red);
				}
			}
		}
		if (s == null || !s.suited || !a.full()) {
			return;
		}
		boolean rightMain = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
		if (s.flying) {
			renderWings(pose, buffers, parent.body);
		}
		if (s.fx.blade) {
			renderBlade(pose, buffers, rightMain ? parent.rightArm : parent.leftArm, ageInTicks);
		}
		if (s.fx.shield) {
			renderShield(pose, buffers, rightMain ? parent.leftArm : parent.rightArm, ageInTicks);
		}
	}

	// ---------------------------------------------------------------- the wings

	/** The outline of a wing, from its root (0, 0) out along -y (up in model space), x across. */
	private static final float[][] WING = { { 0f, 0f }, { 0.16f, -0.12f }, { 0.26f, -0.42f }, { 0.27f, -0.75f }, { 0.2f, -1.0f },
			{ 0.08f, -1.15f }, { -0.02f, -1.17f }, { -0.1f, -1.05f }, { -0.12f, -0.7f }, { -0.09f, -0.35f }, { -0.05f, -0.1f } };

	/**
	 * v0.16.5 (user: "change the wings to be glowing blue, dont make them flap anymore"): four glowing blue insect wings
	 * spread from the back plate, still -- a soft blue membrane, a brighter rim and a pale vein down each.
	 */
	private static void renderWings(PoseStack pose, MultiBufferSource buffers, ModelPart body) {
		pose.pushPose();
		body.translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		for (int side = -1; side <= 1; side += 2) {
			for (int pair = 0; pair < 2; pair++) {
				pose.pushPose();
				// root behind the Scarab on the back plate, between the shoulder blades
				pose.translate(side * 0.07f, 0.17f + pair * 0.14f, 0.27f);
				pose.mulPose(com.mojang.math.Axis.ZP.rotation(side * (pair == 0 ? 0.85f : 1.35f)));
				pose.mulPose(com.mojang.math.Axis.XP.rotation(-0.42f)); // tilted back, away from the body
				float scale = pair == 0 ? 1.05f : 0.8f;
				pose.scale(scale * side, scale, scale);
				Matrix4f m = pose.last().pose();
				// the membrane: a fan of triangles from the root
				for (int i = 1; i < WING.length - 1; i++) {
					tri(vc, m, 0f, -0.05f, WING[i], WING[i + 1], 0.2f, 0.55f, 1.0f, 0.42f, 0.18f);
				}
				// the rim and the vein
				for (int i = 1; i < WING.length - 1; i++) {
					line(vc, m, WING[i][0], WING[i][1], WING[i + 1][0], WING[i + 1][1], 0.018f, 0.45f, 0.8f, 1.0f, 0.75f);
				}
				line(vc, m, 0.02f, -0.02f, 0.06f, -1.1f, 0.014f, 0.75f, 0.92f, 1.0f, 0.8f);
				line(vc, m, 0.04f, -0.45f, 0.24f, -0.7f, 0.01f, 0.6f, 0.85f, 1.0f, 0.6f);
				pose.popPose();
			}
		}
		pose.popPose();
	}

	/** A triangle (root, a, b), root brighter-alpha fading to the edge; both windings (the lightning type is culled). */
	private static void tri(VertexConsumer vc, Matrix4f m, float rx, float ry, float[] a, float[] b, float r, float g, float bl, float rootA, float edgeA) {
		vc.addVertex(m, rx, ry, 0f).setColor(r, g, bl, rootA);
		vc.addVertex(m, a[0], a[1], 0f).setColor(r, g, bl, edgeA);
		vc.addVertex(m, b[0], b[1], 0f).setColor(r, g, bl, edgeA);
		vc.addVertex(m, b[0], b[1], 0f).setColor(r, g, bl, edgeA);
		vc.addVertex(m, rx, ry, 0f).setColor(r, g, bl, rootA);
		vc.addVertex(m, b[0], b[1], 0f).setColor(r, g, bl, edgeA);
		vc.addVertex(m, a[0], a[1], 0f).setColor(r, g, bl, edgeA);
		vc.addVertex(m, a[0], a[1], 0f).setColor(r, g, bl, edgeA);
	}

	/** A thin glowing strip from (x0, y0) to (x1, y1) in the wing plane, both windings. */
	private static void line(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float w, float r, float g, float b, float a) {
		float dx = x1 - x0, dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1.0e-4f) {
			return;
		}
		float nx = -dy / len * w, ny = dx / len * w;
		vc.addVertex(m, x0 - nx, y0 - ny, 0f).setColor(r, g, b, a);
		vc.addVertex(m, x0 + nx, y0 + ny, 0f).setColor(r, g, b, a);
		vc.addVertex(m, x1 + nx, y1 + ny, 0f).setColor(r, g, b, a);
		vc.addVertex(m, x1 - nx, y1 - ny, 0f).setColor(r, g, b, a);
		vc.addVertex(m, x1 - nx, y1 - ny, 0f).setColor(r, g, b, a);
		vc.addVertex(m, x1 + nx, y1 + ny, 0f).setColor(r, g, b, a);
		vc.addVertex(m, x0 + nx, y0 + ny, 0f).setColor(r, g, b, a);
		vc.addVertex(m, x0 - nx, y0 - ny, 0f).setColor(r, g, b, a);
	}

	// ---------------------------------------------------------------- the blade and the shield

	/** A blue energy blade out of the fist, along the forearm. */
	private static void renderBlade(PoseStack pose, MultiBufferSource buffers, ModelPart arm, float age) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		Matrix4f m = pose.last().pose();
		float y0 = 0.55f;
		float y1 = 1.75f;
		float w = 0.07f;
		float shimmer = 0.85f + 0.15f * Mth.sin(age * 1.9f);
		// two crossed sheets, a pale core inside a blue glow
		glowQuad(vc, m, -w * 1.8f, y0, 0f, w * 1.8f, y0, 0f, 0f, y1 + 0.15f, 0f, 0.2f, 0.55f, 1.0f, 0.55f * shimmer);
		glowQuad(vc, m, 0f, y0, -w * 1.8f, 0f, y0, w * 1.8f, 0f, y1 + 0.15f, 0f, 0.2f, 0.55f, 1.0f, 0.55f * shimmer);
		glowQuad(vc, m, -w, y0, 0f, w, y0, 0f, 0f, y1, 0f, 0.8f, 0.95f, 1.0f, 0.9f * shimmer);
		glowQuad(vc, m, 0f, y0, -w, 0f, y0, w, 0f, y1, 0f, 0.8f, 0.95f, 1.0f, 0.9f * shimmer);
		pose.popPose();
	}

	/** A round shield of blue light on the off arm, square to the arm (so it faces forward when the arm is raised). */
	private static void renderShield(PoseStack pose, MultiBufferSource buffers, ModelPart arm, float age) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		Matrix4f m = pose.last().pose();
		float y = 0.62f;
		float r = 0.62f;
		float pulse = 0.8f + 0.2f * Mth.sin(age * 0.5f);
		for (int i = 0; i < 8; i++) {
			double a0 = Math.PI * 2 * i / 8 + Math.PI / 8;
			double a1 = Math.PI * 2 * (i + 1) / 8 + Math.PI / 8;
			float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
			float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
			vc.addVertex(m, 0f, y, 0f).setColor(0.3f, 0.65f, 1.0f, 0.25f * pulse);
			vc.addVertex(m, x0, y, z0).setColor(0.4f, 0.75f, 1.0f, 0.55f * pulse);
			vc.addVertex(m, x1, y, z1).setColor(0.4f, 0.75f, 1.0f, 0.55f * pulse);
			vc.addVertex(m, 0f, y, 0f).setColor(0.3f, 0.65f, 1.0f, 0.25f * pulse);
			// and the other winding, so it shows from both sides
			vc.addVertex(m, 0f, y, 0f).setColor(0.3f, 0.65f, 1.0f, 0.25f * pulse);
			vc.addVertex(m, x1, y, z1).setColor(0.4f, 0.75f, 1.0f, 0.55f * pulse);
			vc.addVertex(m, x0, y, z0).setColor(0.4f, 0.75f, 1.0f, 0.55f * pulse);
			vc.addVertex(m, 0f, y, 0f).setColor(0.3f, 0.65f, 1.0f, 0.25f * pulse);
		}
		pose.popPose();
	}

	/** A tapering sheet from edge (0)-(1) to a tip, both windings (the lightning type is culled). */
	private static void glowQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float tx, float ty,
			float tz, float r, float g, float b, float a) {
		vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, a * 0.6f);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, a * 0.6f);
		vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, a * 0.6f);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, a * 0.6f);
	}

	// ---------------------------------------------------------------- first person

	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, float progress) {
		PlayerModel<LivingEntity> m = model();
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		boolean full = progress >= 1f;
		ResourceLocation tex = full ? SKIN : frame(progress);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		if (full) {
			a.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		}
	}

	/** First person: the energy blade, the blaster and the hammer on the main arm too, so he sees them. */
	public static void renderArmExtras(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right, float age) {
		BlueBeetleState s = state(player);
		if (s == null || !s.suited) {
			return;
		}
		boolean main = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) == right;
		if (main && s.fx.blade) {
			renderBlade(pose, buffers, arm, age);
		}
		BlueBeetleTech.renderArmTechFirstPerson(pose, buffers, light, player, arm, right);
	}

	// ---------------------------------------------------------------- the order of the build

	/** Base cubes: u, v, width, height, depth, and the corner (min x, min y, min z) on the standing body in model pixels. */
	private static final float[][] CUBES = {
			{ 0, 0, 8, 8, 8, -4, -8, -4 }, // head
			{ 16, 16, 8, 12, 4, -4, 0, -2 }, // body
			{ 40, 16, 4, 12, 4, -8, 0, -2 }, // right arm (wide)
			{ 32, 48, 4, 12, 4, 4, 0, -2 }, // left arm
			{ 0, 16, 4, 12, 4, -4, 12, -2 }, // right leg
			{ 16, 48, 4, 12, 4, 0, 12, -2 } }; // left leg

	/**
	 * When base-layer texel ({@code u}, {@code v}) (64x64; overlay texels map to the base texel under them) turns into
	 * armour, 0..1: its distance on the standing body from the Scarab, the front costing a little extra so the armour
	 * wraps round rather than cutting through; 2 for unused texels.
	 */
	public static float order(int u, int v) {
		if (orders == null) {
			float[] raw = new float[64 * 64];
			float max = 0f;
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					float d = distance(x, y);
					raw[y * 64 + x] = d;
					if (d >= 0f) {
						max = Math.max(max, d);
					}
				}
			}
			float[] o = new float[64 * 64];
			for (int i = 0; i < o.length; i++) {
				o[i] = raw[i] < 0f ? 2f : Mth.clamp(0.93f * raw[i] / max + 0.07f * hash(i % 64, i / 64), 0f, 1f);
			}
			orders = o;
		}
		return u < 0 || v < 0 || u >= 64 || v >= 64 ? 2f : orders[v * 64 + u];
	}

	/** The distance (model pixels, -1 if unused) from the Scarab to base texel ({@code u}, {@code v}), overlay mapped. */
	private static float distance(int u, int v) {
		// overlay regions -> the base texel under them
		if (v < 16 && u >= 32) {
			u -= 32; // hat -> head
		} else if (v >= 32 && v < 48) {
			v -= 16; // jacket / right sleeve / right trousers -> body / right arm / right leg
		} else if (v >= 48 && u < 16) {
			u += 16; // left trousers -> left leg
		} else if (v >= 48 && u >= 48) {
			u -= 16; // left sleeve -> left arm
		}
		float[] p = position(u, v);
		if (p == null) {
			return -1f;
		}
		float dx = p[0] - SX, dy = p[1] - SY, dz = p[2] - SZ;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.7f * Math.max(0f, SZ - p[2]);
	}

	/** Where base texel ({@code u}, {@code v}) sits on the standing body (model pixels), or null for an unused texel. */
	static float[] position(int u, int v) {
		for (float[] c : CUBES) {
			int u0 = (int) c[0], v0 = (int) c[1], w = (int) c[2], h = (int) c[3], d = (int) c[4];
			float minX = c[5], minY = c[6], minZ = c[7], maxX = minX + w, maxY = minY + h, maxZ = minZ + d;
			if (u < u0 || u >= u0 + 2 * (d + w) || v < v0 || v >= v0 + d + h) {
				continue;
			}
			if (v < v0 + d) {
				int k = u - u0 - d;
				if (k >= 0 && k < w) {
					return new float[] { minX + k + 0.5f, minY, maxZ - (v - v0) - 0.5f }; // top
				}
				k -= w;
				if (k >= 0 && k < w) {
					return new float[] { minX + k + 0.5f, maxY, maxZ - (v - v0) - 0.5f }; // bottom
				}
				return null;
			}
			float y = minY + (v - v0 - d) + 0.5f;
			int k = u - u0;
			if (k < d) {
				return new float[] { minX, y, maxZ - k - 0.5f }; // west
			}
			k -= d;
			if (k < w) {
				return new float[] { minX + k + 0.5f, y, minZ }; // front
			}
			k -= w;
			if (k < d) {
				return new float[] { maxX, y, minZ + k + 0.5f }; // east
			}
			k -= d;
			return new float[] { maxX - k - 0.5f, y, maxZ }; // back
		}
		return null;
	}

	private static float hash(int u, int v) {
		int h = (u * 73856093) ^ (v * 19349663) ^ 0x5bd1e995;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h >>> 8) & 0xFFFF) / 65536f;
	}

	// ---------------------------------------------------------------- the frames of the build

	private static ResourceLocation frame(float cover) {
		if (frames == null) {
			build();
		}
		if (frames.length == 0) {
			return SKIN;
		}
		int k = Math.max(0, Math.min(FRAMES, (int) Math.floor(cover * FRAMES)));
		return frames[k];
	}

	/** Frame {@code k} shows every armour texel whose {@link #order} is at most {@code k / FRAMES}. No glow. */
	private static void build() {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(SKIN);
		if (res.isEmpty()) {
			frames = new ResourceLocation[0];
			return;
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[blue_beetle] could not read the skin for the armour", e);
			frames = new ResourceLocation[0];
			return;
		}
		int size = src.getWidth();
		int scale = Math.max(1, size / 64);
		frames = new ResourceLocation[FRAMES + 1];
		for (int k = 0; k <= FRAMES; k++) {
			float line = k / (float) FRAMES;
			NativeImage img = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					boolean on = k > 0 && order(x / scale, y / scale) <= line;
					img.setPixelRGBA(x, y, on ? src.getPixelRGBA(x, y) : 0);
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_blue_beetle_build_" + k, new DynamicTexture(img));
		}
		src.close();
	}
}

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
 * v0.16.5: Blue Beetle's armour over a player -- the NovaSkin skin (post 5809773610, 128x128 HD) on a wide player model
 * a hair larger than the wearer's (the Nova technique). It spreads out from the scarab between the shoulder blades on
 * the bonding (6 s, jerky) and on H (about a second), a blue edge on the newest texels, and folds back the same way.
 * The eyes and studs glow; Khaji-Da Unleashed washes it red. Extras drawn in the posed parts' own space:
 * <ul>
 *   <li><b>wings</b> (while flying): four translucent insect wings from the back plate, flicking;</li>
 *   <li><b>the energy blade</b> (G): a blue blade out of the main fist along the forearm;</li>
 *   <li><b>the energy shield</b> (held Z): a round blue shield on the off arm.</li>
 * </ul>
 */
public final class BlueBeetleRender {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/blue_beetle/blue_beetle.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/blue_beetle/blue_beetle_glow.png");
	public static final ResourceLocation WING = ProjectHeroMod.id("textures/entity/blue_beetle/blue_beetle_wing.png");
	private static final float CORE_HEIGHT = 11f;
	public static final int STEPS = 21;
	private static final int EDGE_ARGB = 0xFF4AA8FF;

	private static PlayerModel<LivingEntity> model;
	private static ResourceLocation[] frames;
	private static ResourceLocation[] edges;

	private BlueBeetleRender() {
	}

	public record Anim(float spread, boolean fresh) {
		static final Anim NONE = new Anim(0f, false);
		static final Anim FULL = new Anim(STEPS, false);

		public boolean anything() {
			return spread > 0f;
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
			float t = Mth.clamp(age / total, 0f, 1f);
			if (s.animId == BlueBeetleState.ANIM_BOND) {
				t = Mth.clamp(t + 0.04f * Mth.sin(age * 1.3f), 0f, 1f); // the scarab forcing its way across him
			}
			return new Anim(STEPS * t, true);
		}
		float total = BlueBeetleConfig.SUIT_DOWN_TICKS;
		if (s.suitChangeAt <= 0L || age >= total || age < 0f) {
			return Anim.NONE;
		}
		return new Anim(STEPS * (1f - Mth.clamp(age / total, 0f, 1f)), true);
	}

	public static float progress(Player player, float partial) {
		return anim(player, partial).spread() / STEPS;
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
		if (a.spread() > 0f) {
			PlayerModel<LivingEntity> m = posed(parent);
			boolean full = a.spread() >= STEPS - 0.01f;
			ResourceLocation tex = full ? SKIN : frame(a.spread(), false);
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			if (full) {
				float pulse = 0.75f + 0.25f * Mth.sin(ageInTicks * 0.2f);
				int g = Math.round(255 * pulse);
				m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
						(g << 24) | 0xFFFFFF);
				if (s != null && s.unleashedUntil > player.level().getGameTime()) {
					float k = 0.3f + 0.12f * Mth.sin(ageInTicks * 0.6f);
					int red = (255 << 24) | (Math.round(255 * k) << 16) | (Math.round(40 * k) << 8) | Math.round(30 * k);
					m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(SKIN)), light, OverlayTexture.NO_OVERLAY, red);
				}
			} else if (a.fresh()) {
				m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(frame(a.spread(), true))), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			}
		}
		if (s == null || !s.suited || a.spread() < STEPS - 0.01f) {
			return;
		}
		long now = player.level().getGameTime();
		boolean rightMain = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
		if (s.flying) {
			renderWings(pose, buffers, parent.body, ageInTicks);
		}
		if (s.fx.bladeUntil > now) {
			renderBlade(pose, buffers, rightMain ? parent.rightArm : parent.leftArm, ageInTicks);
		}
		if (s.fx.shield) {
			renderShield(pose, buffers, rightMain ? parent.leftArm : parent.rightArm, ageInTicks);
		}
	}

	/** Four insect wings fanning out of the back plate, flicking fast. */
	private static void renderWings(PoseStack pose, MultiBufferSource buffers, ModelPart body, float age) {
		pose.pushPose();
		body.translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WING));
		float flick = 0.18f * Mth.sin(age * 2.6f);
		for (int side = -1; side <= 1; side += 2) {
			for (int pair = 0; pair < 2; pair++) {
				pose.pushPose();
				// root on the back plate, between the shoulder blades
				pose.translate(side * 0.08f, 0.18f + pair * 0.14f, 0.16f);
				float spread = (pair == 0 ? 0.75f : 1.15f) + flick * (pair == 0 ? 1f : -1f);
				pose.mulPose(com.mojang.math.Axis.ZP.rotation(side * spread));
				pose.mulPose(com.mojang.math.Axis.XP.rotation(0.35f));
				float len = pair == 0 ? 1.15f : 0.85f;
				float w = pair == 0 ? 0.28f : 0.22f;
				Matrix4f m = pose.last().pose();
				PoseStack.Pose last = pose.last();
				// the wing hangs along -y (up, in model space) from its root
				vertex(vc, m, last, -w, 0f, 0f, 0f, 0f);
				vertex(vc, m, last, w, 0f, 0f, 1f, 0f);
				vertex(vc, m, last, w, -len, 0f, 1f, 1f);
				vertex(vc, m, last, -w, -len, 0f, 0f, 1f);
				pose.popPose();
			}
		}
		pose.popPose();
	}

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

	private static void glowQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float tx, float ty,
			float tz, float r, float g, float b, float a) {
		vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, a * 0.6f);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, a * 0.6f);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, float x, float y, float z, float u, float v) {
		vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
				.setNormal(last, 0f, 0f, 1f);
	}

	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, float progress) {
		PlayerModel<LivingEntity> m = model();
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		float spread = progress * STEPS;
		boolean full = spread >= STEPS - 0.01f;
		ResourceLocation tex = full ? SKIN : frame(spread, false);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		if (full) {
			a.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		}
	}

	/** First person: the energy blade on the main arm too, so he sees it. */
	public static void renderArmExtras(PoseStack pose, MultiBufferSource buffers, Player player, ModelPart arm, boolean right, float age) {
		BlueBeetleState s = state(player);
		if (s == null || !s.suited) {
			return;
		}
		boolean main = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) == right;
		if (main && s.fx.bladeUntil > player.level().getGameTime()) {
			renderBlade(pose, buffers, arm, age);
		}
	}

	// ---------------------------------------------------------------- the spread frames

	private static ResourceLocation frame(float spread, boolean edge) {
		if (frames == null) {
			build();
		}
		if (frames.length == 0) {
			return SKIN;
		}
		int k = Math.max(0, Math.min(STEPS, Math.round(spread)));
		return edge ? edges[k] : frames[k];
	}

	private static float[] heights() {
		float[] h = new float[64 * 64];
		java.util.Arrays.fill(h, -1f);
		int[][] cubes = {
				{ 0, 0, 8, 8, 8, 0 }, { 32, 0, 8, 8, 8, 0 },
				{ 16, 16, 8, 12, 4, 8 }, { 16, 32, 8, 12, 4, 8 },
				{ 40, 16, 4, 12, 4, 8 }, { 40, 32, 4, 12, 4, 8 }, { 32, 48, 4, 12, 4, 8 }, { 48, 48, 4, 12, 4, 8 },
				{ 0, 16, 4, 12, 4, 20 }, { 0, 32, 4, 12, 4, 20 }, { 16, 48, 4, 12, 4, 20 }, { 0, 48, 4, 12, 4, 20 } };
		for (int[] c : cubes) {
			int u = c[0], v = c[1], w = c[2], hh = c[3], d = c[4], top = c[5];
			float bottom = top == 0 ? hh - 0.1f : top + hh;
			for (int y = v; y < v + d; y++) {
				for (int x = u + d; x < u + d + w; x++) {
					h[y * 64 + x] = top;
				}
				for (int x = u + d + w; x < u + d + 2 * w; x++) {
					h[y * 64 + x] = bottom;
				}
			}
			for (int y = v + d; y < v + d + hh; y++) {
				for (int x = u; x < u + 2 * d + 2 * w; x++) {
					h[y * 64 + x] = top + (y - v - d) + 0.5f;
				}
			}
		}
		return h;
	}

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
		float[] h = heights();
		frames = new ResourceLocation[STEPS + 1];
		edges = new ResourceLocation[STEPS + 1];
		int edgeAbgr = toAbgr(EDGE_ARGB);
		for (int k = 0; k <= STEPS; k++) {
			NativeImage img = new NativeImage(size, size, true);
			NativeImage edge = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int px = src.getPixelRGBA(x, y);
					float th = h[(y / scale) * 64 + (x / scale)];
					float dist = th < 0f ? 99f : Math.abs(th - CORE_HEIGHT);
					boolean on = dist <= k && k > 0;
					boolean rim = on && k < STEPS && dist > k - 1.6f;
					int alpha = (px >>> 24) & 0xFF;
					img.setPixelRGBA(x, y, on ? (rim && alpha > 0 ? edgeAbgr : px) : 0);
					edge.setPixelRGBA(x, y, rim && alpha > 0 ? edgeAbgr : 0);
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_blue_beetle_spread_" + k, new DynamicTexture(img));
			edges[k] = mc.getTextureManager().register("projecthero_blue_beetle_spread_edge_" + k, new DynamicTexture(edge));
		}
		src.close();
	}

	private static int toAbgr(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}

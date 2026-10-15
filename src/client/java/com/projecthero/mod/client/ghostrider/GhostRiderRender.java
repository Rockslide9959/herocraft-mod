package com.projecthero.mod.client.ghostrider;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ghostrider.GhostRiderConfig;
import com.projecthero.mod.ghostrider.data.GhostRiderState;

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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.joml.Matrix4f;

/**
 * v0.16.5: Ghost Rider drawn over a player -- the user's NovaSkin skin (post 4891213136, 128x128 HD, wide arms) on a
 * second, wide player model a hair larger than the wearer's (the Nova uniform technique: nothing to equip or lose; the
 * wearer's own overlay layer and armour are hidden while any of it shows).
 *
 * <ul>
 *   <li><b>The change</b> ({@link #anim}): the Spirit burns in from the boots up -- texel rows revealed bottom-up with a
 *       glowing hellfire edge on the newest rows (frames pre-built once from the vanilla cube UV layout) -- and when the
 *       flames reach the head the skull ignites. Reverting runs it the other way: the skull goes out and the body burns
 *       away from the head down.</li>
 *   <li><b>The flaming skull</b> ({@link #renderSkullFire}): a crown and a ring of the vanilla animated fire sprite
 *       (fire_0 / fire_1 from the block atlas, so it flickers exactly like real fire) streaming up off the skull, each
 *       tongue swaying and pulsing on its own clock; full-bright.</li>
 *   <li>The eye sockets burn orange (an emissive pass of {@code ghost_rider_glow.png}).</li>
 * </ul>
 */
public final class GhostRiderRender {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/ghost_rider/ghost_rider.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/ghost_rider/ghost_rider_glow.png");
	/** Model pixels from the top of the head to the soles. */
	public static final int ROWS = 32;
	private static final int EDGE_ARGB = 0xFFFF8A1E;

	private static PlayerModel<LivingEntity> model;
	private static ResourceLocation[] frames;
	private static ResourceLocation[] edges;

	private GhostRiderRender() {
	}

	/**
	 * Where the change is at this frame.
	 *
	 * @param cover how much of the body is Ghost Rider, in model pixels from the soles up (0 = none, {@link #ROWS} = all)
	 * @param skull 0..1 how much the skull is burning
	 * @param fresh the newest rows glow (a change is running)
	 */
	public record Anim(float cover, float skull, boolean fresh) {
		static final Anim NONE = new Anim(0f, 0f, false);
		static final Anim FULL = new Anim(ROWS, 1f, false);

		public boolean anything() {
			return cover > 0f || skull > 0f;
		}
	}

	public static PlayerModel<LivingEntity> model() {
		if (model == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.04f), false);
			model = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return model;
	}

	private static GhostRiderState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
	}

	public static Anim anim(Player player, float partial) {
		GhostRiderState s = state(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return Anim.NONE;
		}
		float age = player.level().getGameTime() - s.formChangeAt + partial;
		if (age < 0f && age > -40f) {
			age = 0f; // the client clock can trail the server's by a few ticks
		}
		if (s.transformed) {
			float total = GhostRiderConfig.TRANSFORM_TICKS;
			if (s.formChangeAt <= 0L || age >= total || age < 0f) {
				return Anim.FULL;
			}
			float body = Mth.clamp(age / (total - 10f), 0f, 1f);
			float skull = Mth.clamp((age - (total - 12f)) / 6f, 0f, 1f);
			return new Anim(ROWS * smooth(body), skull, true);
		}
		float total = GhostRiderConfig.REVERT_TICKS;
		if (s.formChangeAt <= 0L || age >= total || age < 0f) {
			return Anim.NONE;
		}
		float t = Mth.clamp(age / total, 0f, 1f);
		return new Anim(ROWS * (1f - t), Math.max(0f, 1f - age / 6f), true);
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** How much of the body is Ghost Rider, 0..1 (the first-person arm uses it). */
	public static float progress(Player player, float partial) {
		return anim(player, partial).cover() / ROWS;
	}

	/** The wearer's own skin overlay and armour are hidden whenever any of Ghost Rider shows. */
	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f;
	}

	// ---------------------------------------------------------------- the body

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

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, Anim a, float ageInTicks) {
		if (a.cover() > 0f) {
			PlayerModel<LivingEntity> m = posed(parent);
			boolean full = a.cover() >= ROWS - 0.01f;
			ResourceLocation tex = full ? SKIN : frame(a.cover(), false);
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			if (a.skull() > 0f) {
				// the burning eye sockets (the skull is whole by now)
				int k = Math.round(255 * Mth.clamp(a.skull(), 0f, 1f));
				m.head.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(k, 255, 255, 255));
			}
			if (!full && a.fresh()) {
				m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(frame(a.cover(), true))), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			}
		}
		if (a.skull() > 0.01f) {
			renderSkullFire(pose, buffers, parent.head, a.skull(), ageInTicks);
		}
	}

	/** First person: the Ghost Rider arm over the arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, float progress) {
		PlayerModel<LivingEntity> m = model();
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		float cover = progress * ROWS;
		boolean full = cover >= ROWS - 0.01f;
		ResourceLocation tex = full ? SKIN : frame(cover, false);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		if (!full) {
			a.render(pose, buffers.getBuffer(RenderType.eyes(frame(cover, true))), light, OverlayTexture.NO_OVERLAY);
			sleeve.render(pose, buffers.getBuffer(RenderType.eyes(frame(cover, true))), light, OverlayTexture.NO_OVERLAY);
		}
	}

	// ---------------------------------------------------------------- the flaming skull

	/** Tongues of fire in a ring round the top of the skull. */
	private static final int RING = 10;

	/**
	 * The burning skull: the vanilla animated fire sprites in head space (the head part is already posed for this
	 * frame): a ring of tongues leaning out round the crown and two crossed sheets rising from the top, each swaying and
	 * pulsing on its own phase. {@code strength} 0..1 grows them in and out (ignition / going out).
	 */
	public static void renderSkullFire(PoseStack pose, MultiBufferSource buffers, ModelPart head, float strength, float ageInTicks) {
		TextureAtlasSprite fire0 = ModelBakery.FIRE_0.sprite();
		TextureAtlasSprite fire1 = ModelBakery.FIRE_1.sprite();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
		pose.pushPose();
		head.translateAndRotate(pose);
		Matrix4f m = pose.last().pose();
		PoseStack.Pose last = pose.last();
		float s = Mth.clamp(strength, 0f, 1f);
		float t = ageInTicks;
		// head space: the head cube spans x/z -0.25..0.25 and y -0.5 (top) .. 0 (chin); -y is up
		float r = 0.27f;
		for (int i = 0; i < RING; i++) {
			double a = Math.PI * 2 * i / RING;
			float cx = (float) Math.cos(a) * r;
			float cz = (float) Math.sin(a) * r;
			// the tongue's sheet runs along the tangent of the ring
			float tx = (float) -Math.sin(a) * 0.17f;
			float tz = (float) Math.cos(a) * 0.17f;
			float pulse = 0.82f + 0.18f * Mth.sin(t * 0.55f + i * 1.7f);
			float h = (0.42f + 0.16f * ((i * 7) % 3) / 2f) * pulse * s;
			float lean = 0.10f + 0.04f * Mth.sin(t * 0.3f + i);
			float y0 = -0.30f;
			float y1 = y0 - h;
			float ox = cx * (1f + lean * 2.2f);
			float oz = cz * (1f + lean * 2.2f);
			quad(vc, m, last, (i & 1) == 0 ? fire0 : fire1, cx - tx, y0, cz - tz, cx + tx, y0, cz + tz, ox + tx, y1, oz + tz, ox - tx, y1, oz - tz);
		}
		// the crown: two crossed sheets straight up off the top, swaying
		float sway = 0.05f * Mth.sin(t * 0.25f);
		float crown = (0.78f + 0.12f * Mth.sin(t * 0.7f)) * s;
		float w = 0.30f;
		float y0 = -0.42f;
		float y1 = y0 - crown;
		quad(vc, m, last, fire0, -w, y0, 0f, w, y0, 0f, w + sway, y1, sway, -w + sway, y1, sway);
		quad(vc, m, last, fire1, 0f, y0, -w, 0f, y0, w, sway, y1, w + sway, sway, y1, -w + sway);
		// and a second, smaller pair turned 45 degrees, so it reads as a full blaze from every side
		float w2 = 0.22f;
		float c2 = crown * 0.75f;
		quad(vc, m, last, fire1, -w2, y0, -w2, w2, y0, w2, w2 - sway, y0 - c2, w2 + sway, -w2 - sway, y0 - c2, -w2 + sway);
		quad(vc, m, last, fire0, -w2, y0, w2, w2, y0, -w2, w2 + sway, y0 - c2, -w2 - sway, -w2 + sway, y0 - c2, w2 - sway);
		pose.popPose();
	}

	/** One fire quad: (x0,y0,z0)-(x1,y1,z1) is the base, (x2..)-(x3..) the top; the sprite's flame base sits on the base. */
	private static void quad(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, TextureAtlasSprite sp, float x0, float y0, float z0, float x1,
			float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
		vertex(vc, m, last, x0, y0, z0, u0, v1);
		vertex(vc, m, last, x1, y1, z1, u1, v1);
		vertex(vc, m, last, x2, y2, z2, u1, v0);
		vertex(vc, m, last, x3, y3, z3, u0, v0);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, float x, float y, float z, float u, float v) {
		vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, -1f, 0f);
	}

	// ---------------------------------------------------------------- the burn-in frames

	private static ResourceLocation frame(float cover, boolean edge) {
		if (frames == null) {
			build();
		}
		if (frames.length == 0) {
			return SKIN;
		}
		int k = Math.max(0, Math.min(ROWS, Math.round(cover)));
		return edge ? edges[k] : frames[k];
	}

	/** Every 64-grid texel's height on the body in model pixels from the top of the head (see NovaSuitRender#heights). */
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

	/** Bottom-up: frame {@code k} shows every texel within {@code k} model pixels of the soles. */
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
			ProjectHeroMod.LOGGER.warn("[ghost_rider] could not read the skin for the change", e);
			frames = new ResourceLocation[0];
			return;
		}
		int size = src.getWidth();
		int scale = Math.max(1, size / 64);
		float[] h = heights();
		frames = new ResourceLocation[ROWS + 1];
		edges = new ResourceLocation[ROWS + 1];
		for (int k = 0; k <= ROWS; k++) {
			NativeImage img = new NativeImage(size, size, true);
			NativeImage edge = new NativeImage(size, size, true);
			float line = ROWS - k; // texels at or below this height (from the top) are on
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int px = src.getPixelRGBA(x, y);
					float th = h[(y / scale) * 64 + (x / scale)];
					boolean on = th >= 0f && th >= line && k > 0;
					boolean rim = on && k < ROWS && th < line + 2.0f;
					int alpha = (px >>> 24) & 0xFF;
					// the rim flickers between orange and yellow texel by texel
					int edgeArgb = ((x * 7 + y * 13) % 3 == 0) ? 0xFFFFD040 : EDGE_ARGB;
					int edgeAbgr = toAbgr(edgeArgb);
					img.setPixelRGBA(x, y, on ? (rim && alpha > 0 ? edgeAbgr : px) : 0);
					edge.setPixelRGBA(x, y, rim && alpha > 0 ? edgeAbgr : 0);
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_ghost_rider_burn_" + k, new DynamicTexture(img));
			edges[k] = mc.getTextureManager().register("projecthero_ghost_rider_burn_edge_" + k, new DynamicTexture(edge));
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

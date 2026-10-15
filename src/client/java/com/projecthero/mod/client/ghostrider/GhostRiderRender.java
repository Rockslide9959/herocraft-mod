package com.projecthero.mod.client.ghostrider;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.render.OverlayStrips;
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
 * second, wide player model a hair larger than the wearer's (the Nova uniform technique).
 *
 * <p><b>The change</b> ({@link #anim}, reworked to the user's design): three stages over {@code TRANSFORM_TICKS}:
 * <ol>
 *   <li>the leather jacket, trousers and boots <b>fold round the body from the back</b> -- every texel of the body and limbs
 *       appears in order of how far round its cube it sits from the middle of the back, a dark crease on the newest
 *       ones (no glow);</li>
 *   <li>the face <b>burns away</b> into the skull -- the head's texels in patches, a charred edge eating in;</li>
 *   <li>the skull <b>erupts</b>: the flames burst up past full size and settle.</li>
 * </ol>
 * Reverting runs it backwards. The wearer's own second layer goes texel by texel in step ({@link OverlayStrips} with
 * {@link #order}). The skull fire is the vanilla animated fire sprite; the eye sockets glow.
 */
public final class GhostRiderRender {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/ghost_rider/ghost_rider.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/ghost_rider/ghost_rider_glow.png");
	/** Frames of the change. */
	private static final int FRAMES = 48;
	/** Where in the change each stage sits (0..1). */
	public static final float FOLD_END = 0.55f;
	public static final float BURN_START = 0.58f;
	public static final float BURN_END = 0.84f;
	public static final float ERUPT = 0.86f;

	private static PlayerModel<LivingEntity> model;
	private static ResourceLocation[] frames;

	private GhostRiderRender() {
	}

	/**
	 * Where the change is at this frame.
	 *
	 * @param cover how far the change has got, 0..1 (every texel with {@link #order} at or below it is Ghost Rider)
	 * @param skull how much the skull is burning: 0..1, briefly up to 1.5 as it erupts
	 * @param fresh a change is running (the newest texels carry the crease / char)
	 */
	public record Anim(float cover, float skull, boolean fresh) {
		static final Anim NONE = new Anim(0f, 0f, false);
		static final Anim FULL = new Anim(1f, 1f, false);

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

	/** The skull's fire for change progress {@code p}: nothing, then an eruption past full size settling back to 1. */
	private static float skullFor(float p) {
		if (p < ERUPT) {
			return 0f;
		}
		float t = (p - ERUPT) / (1f - ERUPT);
		return t < 0.45f ? 1.5f * (t / 0.45f) : 1.5f - 0.5f * ((t - 0.45f) / 0.55f);
	}

	public static Anim anim(Player player, float partial) {
		GhostRiderState s = state(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return Anim.NONE;
		}
		float age = player.level().getGameTime() - s.formChangeAt + partial;
		if (age < 0f && age > -40f) {
			age = 0f;
		}
		if (s.transformed) {
			float total = GhostRiderConfig.TRANSFORM_TICKS;
			if (s.formChangeAt <= 0L || age >= total || age < 0f) {
				return Anim.FULL;
			}
			float p = Mth.clamp(age / total, 0f, 1f);
			return new Anim(p, skullFor(p), true);
		}
		float total = GhostRiderConfig.REVERT_TICKS;
		if (s.formChangeAt <= 0L || age >= total || age < 0f) {
			return Anim.NONE;
		}
		// backwards: the fire goes out, the face comes back, the clothes unwrap toward the back
		float p = 1f - Mth.clamp(age / total, 0f, 1f);
		return new Anim(p, p > ERUPT ? (p - ERUPT) / (1f - ERUPT) : 0f, true);
	}

	public static float progress(Player player, float partial) {
		return anim(player, partial).cover();
	}

	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f;
	}

	// ---------------------------------------------------------------- the order of the change

	private static float[] orders;

	/**
	 * When base-layer texel ({@code u}, {@code v}) (64x64 skin coordinates; overlay-layer texels map to the base texel
	 * under them) turns into Ghost Rider, 0..1; 2 for unused texels.
	 */
	public static float order(int u, int v) {
		if (orders == null) {
			float[] o = new float[64 * 64];
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					o[y * 64 + x] = computeOrder(x, y);
				}
			}
			orders = o;
		}
		return u < 0 || v < 0 || u >= 64 || v >= 64 ? 2f : orders[v * 64 + u];
	}

	/** Base cubes of the body and limbs: u, v, width, depth, height. */
	private static final int[][] LIMBS = { { 16, 16, 8, 4, 12 }, { 40, 16, 4, 4, 12 }, { 32, 48, 4, 4, 12 }, { 0, 16, 4, 4, 12 },
			{ 16, 48, 4, 4, 12 } };

	private static float computeOrder(int u, int v) {
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
		if (OverlayStrips.height(u, v) < 0f) {
			return 2f;
		}
		if (v < 16) {
			// the head: burns away in patches (a 2x2-texel value noise with a finer grain on top)
			float n = 0.7f * hash(u / 2, v / 2) + 0.3f * hash(u, v + 97);
			return BURN_START + (BURN_END - BURN_START) * n;
		}
		for (int[] c : LIMBS) {
			int u0 = c[0], v0 = c[1], w = c[2], d = c[3], h = c[4];
			if (u < u0 || u >= u0 + 2 * (w + d) || v < v0 || v >= v0 + d + h) {
				continue;
			}
			if (v < v0 + d) {
				return FOLD_END - 0.02f; // the top / bottom caps close last
			}
			// round the cube: west d, north (front) w, east d, south (back) w -- the back's middle goes first
			float perimeter = 2f * (w + d);
			float back = 2f * d + 1.5f * w;
			float dist = Math.abs(u - u0 + 0.5f - back);
			dist = Math.min(dist, perimeter - dist) / (perimeter / 2f);
			float row = (v - v0 - d) / (float) h;
			return 0.02f + (FOLD_END - 0.08f) * dist + 0.05f * row;
		}
		return FOLD_END;
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
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
			boolean full = a.cover() >= 0.999f;
			ResourceLocation tex = full ? SKIN : frame(a.cover());
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			if (a.skull() > 0f) {
				// the burning eye sockets (the skull is whole by now)
				int k = Math.round(255 * Mth.clamp(a.skull(), 0f, 1f));
				m.head.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(k, 255, 255, 255));
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
		boolean full = progress >= 0.999f;
		ResourceLocation tex = full ? SKIN : frame(progress);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
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
		float s = Mth.clamp(strength, 0f, 1.6f); // up to 1.5 while the skull erupts
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

	// ---------------------------------------------------------------- the frames of the change

	private static ResourceLocation frame(float cover) {
		if (frames == null) {
			build();
		}
		if (frames.length == 0) {
			return SKIN;
		}
		int k = Math.max(0, Math.min(FRAMES, Math.round(cover * FRAMES)));
		return frames[k];
	}

	/**
	 * Frame {@code k} shows every texel whose {@link #order} is at most {@code k / FRAMES}. The newest texels are drawn
	 * darker -- a fold's crease on the clothes, a charred rim on the burning face -- never a glow.
	 */
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
		frames = new ResourceLocation[FRAMES + 1];
		for (int k = 0; k <= FRAMES; k++) {
			float line = k / (float) FRAMES;
			NativeImage img = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int px = src.getPixelRGBA(x, y);
					float o = order(x / scale, y / scale);
					if (o > line || k == 0) {
						img.setPixelRGBA(x, y, 0);
						continue;
					}
					float age = line - o;
					boolean head = (y / scale) < 16;
					if (k < FRAMES && age < (head ? 0.045f : 0.06f) && ((px >>> 24) & 0xFF) > 0) {
						img.setPixelRGBA(x, y, head ? charred(px, x, y) : crease(px));
					} else {
						img.setPixelRGBA(x, y, px);
					}
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_ghost_rider_change_" + k, new DynamicTexture(img));
		}
		src.close();
	}

	/** A fold's crease: the texel at half brightness (NativeImage pixels are ABGR). */
	private static int crease(int abgr) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		return (a << 24) | ((b / 2) << 16) | ((g / 2) << 8) | (r / 2);
	}

	/** A charred rim: soot black with the odd dull ember-red texel (not emissive). */
	private static int charred(int abgr, int x, int y) {
		boolean ember = ((x * 7 + y * 13) % 5) == 0;
		int r = ember ? 120 : 28, g = ember ? 22 : 18, b = ember ? 10 : 16;
		return (0xFF << 24) | (b << 16) | (g << 8) | r;
	}
}

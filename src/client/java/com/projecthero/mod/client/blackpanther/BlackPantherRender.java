package com.projecthero.mod.client.blackpanther;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.client.bluebeetle.BlueBeetleRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Black Panther's suit -- the user's Planet Minecraft skin ("The Black Panther (MCU)", file 11967850, 64x64) on the
 * same hair-larger player model as Blue Beetle ({@link BlueBeetleRender#posed}).
 *
 * <p>The user: "Make the suit retract into a necklace". The necklace is a small voxel chain of silver beads and fangs round the
 * wearer's collar, there whenever they hold the power. The suit runs out of it texel by texel across the skin (the Blue Beetle
 * geodesic, seeded at the necklace), the head last, behind a glowing violet edge; H again plays it backwards, so it drains back
 * into the necklace. The necklace is drawn over the suit too (v0.17.3 tester round), and not at all once it is taken off (hold
 * H). N adds three vibranium claws to each fist; Shift+N hides the suit's head. The trim glows purple with the Kinetic bar.
 */
public final class BlackPantherRender {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/black_panther/black_panther.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/black_panther/black_panther_glow.png");
	public static final ResourceLocation NECKLACE = ProjectHeroMod.id("textures/entity/black_panther/necklace.png");
	public static final int FRAMES = 64;
	/** The body takes this share of the build; the head takes the rest, so the mask closes last. */
	private static final float BODY_SHARE = 0.74f;

	private static ResourceLocation[] frames;
	private static float[] orders;
	private static ModelPart necklace;

	private BlackPantherRender() {
	}

	/** {@code cover}: how far the suit has run, 0..1. */
	public record Anim(float cover) {
		static final Anim NONE = new Anim(0f);
		static final Anim FULL = new Anim(1f);

		public boolean anything() {
			return cover > 0f;
		}

		public boolean full() {
			return cover >= 1f;
		}
	}

	static BlackPantherState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
	}

	public static Anim anim(Player player, float partial) {
		BlackPantherState s = state(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return Anim.NONE;
		}
		float age = player.level().getGameTime() - s.suitChangeAt + partial;
		if (age < 0f && age > -40f) {
			age = 0f;
		}
		if (s.suited) {
			float total = BlackPantherConfig.SUIT_UP_TICKS;
			if (s.suitChangeAt <= 0L || age >= total || age < 0f) {
				return Anim.FULL;
			}
			return new Anim(Mth.clamp(age / total, 0f, 1f));
		}
		float total = BlackPantherConfig.SUIT_DOWN_TICKS;
		if (s.suitChangeAt <= 0L || age >= total || age < 0f) {
			return Anim.NONE;
		}
		return new Anim(1f - Mth.clamp(age / total, 0f, 1f));
	}

	public static float progress(Player player, float partial) {
		return anim(player, partial).cover();
	}

	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f;
	}

	/** The trim's glow, 0..1: the Kinetic bar. */
	private static float glow(Player player) {
		BlackPantherState s = state(player);
		return s == null ? 0f : Mth.clamp(s.kinetic / BlackPantherConfig.KINETIC_MAX, 0f, 1f);
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, Player player, Anim a, float ageInTicks) {
		PlayerModel<LivingEntity> m = BlueBeetleRender.posed(parent);
		// Shift+N: unmasked, the suit's head (and its hat layer) is not drawn -- the wearer's own face and hair show
		boolean unmasked = unmasked(player);
		if (unmasked) {
			m.head.visible = false;
			m.hat.visible = false;
		}
		try {
			ResourceLocation tex = a.full() ? SKIN : frame(a.cover());
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			float k = glow(player);
			if (a.full() && k > 0.01f) {
				float pulse = 0.8f + 0.2f * Mth.sin(ageInTicks * 0.15f);
				int alpha = Mth.clamp(Math.round(255 * k * pulse), 0, 255);
				m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
						(alpha << 24) | 0xFFFFFF);
			}
		} finally {
			// the model is shared with Blue Beetle
			m.head.visible = true;
			m.hat.visible = true;
		}
	}

	/** Shift+N: the mask is off (synced state). */
	public static boolean unmasked(Player player) {
		BlackPantherState s = state(player);
		return s != null && s.hasPower && s.suited && s.unmasked;
	}

	/** The wearer's own hat / hair layer shows: the mask is off and the suit is on. */
	public static boolean showsWearerHat(Player player) {
		return unmasked(player);
	}

	// ---------------------------------------------------------------- the claws (N)

	private static ModelPart clawsWorld;
	private static ModelPart clawsHand;

	/** Three thin vibranium blades out of a fist, along the arm, in the arm bone's space (centred on x = 0). */
	private static ModelPart claws(boolean firstPerson) {
		ModelPart cached = firstPerson ? clawsHand : clawsWorld;
		if (cached != null) {
			return cached;
		}
		float length = firstPerson ? 7.0f : 4.2f;
		CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
		for (int i = -1; i <= 1; i++) {
			float len = length * (i == 0 ? 1.0f : 0.86f);
			float z = i * 1.25f;
			// the blade, out of the knuckles (the fist ends at y = 10), and a narrower point
			c.addBox(-0.2f, 9.6f, z - 0.27f, 0.4f, len, 0.54f);
			c.addBox(-0.13f, 9.6f + len, z - 0.16f, 0.26f, 0.9f, 0.32f);
		}
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("claws", c, PartPose.ZERO);
		ModelPart part = LayerDefinition.create(mesh, 16, 16).bakeRoot();
		if (firstPerson) {
			clawsHand = part;
		} else {
			clawsWorld = part;
		}
		return part;
	}

	/** The claws are out (N), the suit is fully on. */
	public static boolean clawsShown(Player player) {
		BlackPantherState s = state(player);
		return s != null && s.hasPower && s.suited && s.clawsOut && !s.necklaceOff;
	}

	/** Both hands' claws on the third-person model (the pose already in the entity's model space). */
	public static void renderClaws(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> model) {
		renderClaws(pose, buffers, light, player, model.rightArm, true, false);
		renderClaws(pose, buffers, light, player, model.leftArm, false, false);
	}

	/** One hand's claws; {@code firstPerson} draws the longer blades the player sees on their own hand. */
	public static void renderClaws(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right, boolean firstPerson) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.translate((right ? -1f : 1f) / 16f, 0f, 0f); // the suit's arms are 4 px wide: the middle of the fist
		ModelPart blades = claws(firstPerson);
		blades.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(NECKLACE)), light, OverlayTexture.NO_OVERLAY);
		float k = glow(player);
		if (k > 0.01f) {
			blades.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(NECKLACE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					(Mth.clamp(Math.round(150 * k), 0, 255) << 24) | 0xB070FF);
		}
		pose.popPose();
	}

	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right, float progress) {
		PlayerModel<LivingEntity> m = BlueBeetleRender.model();
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		ResourceLocation tex = progress >= 1f ? SKIN : frame(progress);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		float k = glow(player);
		if (progress >= 1f && k > 0.01f) {
			int color = (Mth.clamp(Math.round(255 * k), 0, 255) << 24) | 0xFFFFFF;
			a.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
			sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
		}
	}

	// ---------------------------------------------------------------- the necklace

	private static ModelPart necklace() {
		if (necklace == null) {
			// v0.17.3 tester round: one continuous chain. Body space, y down, the chest is -z (its face at z = -2, the suit's
			// jacket at -2.25). The head covers the top of the body, so the collar runs along the top edges of the chest and the
			// back, joined by two short links on top (under the head from most angles; still joined when it turns).
			CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
			for (int i = 0; i < 8; i++) {
				float x = -3.15f + i * 0.9f; // 0.9 wide, 0.9 apart: touching
				c.addBox(x - 0.45f, -0.45f, -2.6f, 0.9f, 0.85f, 0.7f); // front collar
				c.addBox(x - 0.45f, -0.45f, 1.9f, 0.9f, 0.85f, 0.7f); // back collar
			}
			for (int side = -1; side <= 1; side += 2) {
				c.addBox(side > 0 ? 2.95f : -3.65f, -0.5f, -1.95f, 0.7f, 0.7f, 3.9f); // the links over the shoulders
			}
			// the front V: beads from both ends of the collar down to a pendant on the breastbone, each overlapping the last
			int beads = 7;
			for (int side = -1; side <= 1; side += 2) {
				for (int i = 0; i < beads; i++) {
					float t = i / (float) beads;
					float x = side * 3.0f * (1f - t);
					float y = 0.35f + 3.2f * t;
					c.addBox(x - 0.42f, y - 0.42f, -2.7f, 0.84f, 0.84f, 0.6f);
				}
			}
			c.addBox(-0.65f, 3.0f, -2.8f, 1.3f, 1.3f, 0.7f); // the pendant
			// the panther's fangs hang from the V: one from the pendant, two pairs from the beads, longest in the middle
			c.texOffs(8, 0);
			c.addBox(-0.32f, 4.2f, -2.7f, 0.64f, 1.3f, 0.5f);
			c.addBox(-0.18f, 5.5f, -2.65f, 0.36f, 0.8f, 0.4f);
			for (int side = -1; side <= 1; side += 2) {
				for (int j = 0; j < 2; j++) {
					float t = (j == 0 ? 5f : 3f) / beads; // the bead it hangs from
					float x = side * 3.0f * (1f - t);
					float top = 0.35f + 3.2f * t + 0.38f;
					float len = j == 0 ? 1.0f : 0.75f;
					c.addBox(x - 0.27f, top, -2.68f, 0.54f, len, 0.45f);
					c.addBox(x - 0.15f, top + len, -2.63f, 0.3f, len * 0.6f, 0.35f);
				}
			}
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("chain", c, PartPose.ZERO);
			necklace = LayerDefinition.create(mesh, 16, 16).bakeRoot();
		}
		return necklace;
	}

	/**
	 * Round the collar, in the posed body's space, drawn after the suit and a little proud of it so it stays on show over the
	 * full suit. Pushed out over worn armour (which the suit hides, so only while none of the suit is on).
	 */
	public static void renderNecklace(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart body) {
		pose.pushPose();
		body.translateAndRotate(pose);
		if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && progress(player, 0f) <= 0f) {
			pose.scale(1.28f, 1f, 1.6f);
		}
		ModelPart chain = necklace();
		chain.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(NECKLACE)), light, OverlayTexture.NO_OVERLAY);
		float k = glow(player);
		if (k > 0.01f) {
			chain.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(NECKLACE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					(Mth.clamp(Math.round(200 * k), 0, 255) << 24) | 0xB070FF);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- the order of the build

	/** When base-layer texel (u, v) turns into suit, 0..1 (2 = unused); overlay texels follow the base texel under them. */
	public static float order(int u, int v) {
		if (orders == null) {
			float[] from = BlueBeetleRender.geodesicFrom(0f, 1.5f, -2f);
			float bodyMax = 0f, headMin = Float.MAX_VALUE, headMax = 0f;
			for (int i = 0; i < from.length; i++) {
				if (from[i] < 0f) {
					continue;
				}
				if (head(i % 64, i / 64)) {
					headMin = Math.min(headMin, from[i]);
					headMax = Math.max(headMax, from[i]);
				} else {
					bodyMax = Math.max(bodyMax, from[i]);
				}
			}
			float[] o = new float[64 * 64];
			java.util.Arrays.fill(o, 2f);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int base = BlueBeetleRender.baseTexel(x, y);
					if (base < 0 || from[base] < 0f) {
						continue;
					}
					float jitter = 0.05f * Mth.frac(Mth.sin(x * 12.9898f + y * 78.233f) * 43758.547f);
					float t = head(base % 64, base / 64)
							? BODY_SHARE + (1f - BODY_SHARE) * (from[base] - headMin) / Math.max(1f, headMax - headMin)
							: BODY_SHARE * from[base] / Math.max(1f, bodyMax);
					o[y * 64 + x] = Mth.clamp(0.95f * t + jitter, 0f, 1f);
				}
			}
			orders = o;
		}
		return u < 0 || v < 0 || u >= 64 || v >= 64 ? 2f : orders[v * 64 + u];
	}

	private static boolean head(int u, int v) {
		return v < 16 && u < 32;
	}

	private static ResourceLocation frame(float cover) {
		if (frames == null) {
			build();
		}
		if (frames.length == 0) {
			return SKIN;
		}
		return frames[Math.max(0, Math.min(FRAMES, (int) Math.floor(cover * FRAMES)))];
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
			ProjectHeroMod.LOGGER.warn("[black_panther] could not read the suit skin", e);
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
					float o = order(x / scale, y / scale);
					int px = src.getPixelRGBA(x, y);
					if (k == 0 || o > line) {
						img.setPixelRGBA(x, y, 0);
					} else if (k < FRAMES && line - o < EDGE && ((px >>> 24) & 0xFF) > 0) {
						img.setPixelRGBA(x, y, edge(px, (line - o) / EDGE)); // the build's frontier glows violet
					} else {
						img.setPixelRGBA(x, y, px);
					}
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_black_panther_build_" + k, new DynamicTexture(img));
		}
		src.close();
	}

	/** How far behind the build's frontier the violet edge band reaches (in build order, 0..1). */
	private static final float EDGE = 0.07f;

	/** The suit's moving edge: hot violet at the frontier, fading into the suit behind it ({@code t} 0 = the frontier). */
	private static int edge(int abgr, float t) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		r = (int) Mth.lerp(k, r, 205);
		g = (int) Mth.lerp(k, g, 130);
		b = (int) Mth.lerp(k, b, 255);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}

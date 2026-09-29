package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.maxsteel.data.MaxSteelFx;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.2: the Turbo Cannon's arm cannon -- a sleeve, a ribbed barrel, a glowing muzzle ring, a spine fin and two
 * power cells, built over the right forearm. It exists only while the cannon is charging and for the recoil after a
 * shot, and it forms (and folds away) texel by texel, sleeve first and muzzle last, like the rest of the suit.
 *
 * <p>The cubes mirror {@code scratchpad/gen_maxsteel_v0142.js} (which paints {@code max_steel_arm_cannon.png} and its
 * order map). The part sits on the same pivot as the vanilla right arm and copies that arm's pose, so it follows the
 * aim pose in third person and the hand in first person with no extra maths.
 */
public final class MaxSteelArmCannon {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectHeroMod.id("max_steel_arm_cannon"), "main");
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/max_steel_arm_cannon.png");
	private static final ResourceLocation ORDER = ProjectHeroMod.id("textures/entity/max_steel_arm_cannon_order.png");
	/** Ticks the cannon takes to form at the start of a charge. */
	public static final float FORM_TICKS = 8f;
	/** After a shot it holds this long, then folds away over {@link #FOLD_TICKS}. */
	private static final float HOLD_TICKS = 12f;
	private static final float FOLD_TICKS = 8f;
	private static final int STEPS = 16;
	/** Muzzle, in the arm's model space (pixels, y down the arm). */
	public static final float MUZZLE_X = -1.0f;
	public static final float MUZZLE_Y = 16.5f;

	private static ModelPart cannon;
	private static ResourceLocation[] frames;

	private MaxSteelArmCannon() {
	}

	public static void initialize() {
		EntityModelLayerRegistry.registerModelLayer(LAYER, MaxSteelArmCannon::createLayer);
	}

	private static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("cannon", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-3.5f, 3.0f, -2.5f, 5, 7, 5)    // sleeve over the forearm
				.texOffs(20, 0).addBox(-2.5f, 9.0f, -1.5f, 3, 7, 3)   // barrel, past the fist
				.texOffs(32, 0).addBox(-3.0f, 14.0f, -2.0f, 4, 2, 4)  // muzzle ring
				.texOffs(48, 0).addBox(-1.5f, 3.5f, -3.5f, 1, 6, 1)   // spine fin
				.texOffs(0, 12).addBox(-4.5f, 5.0f, -1.0f, 1, 4, 2)   // power cell (outer)
				.texOffs(8, 12).addBox(1.5f, 5.0f, -1.0f, 1, 4, 2),   // power cell (inner)
				PartPose.offset(-5.0f, 2.0f, 0.0f));
		return LayerDefinition.create(mesh, 64, 32);
	}

	private static boolean ensureBaked() {
		if (cannon != null) {
			return true;
		}
		try {
			cannon = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER).getChild("cannon");
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** How formed the cannon is right now (0 = not there .. 1 = whole), from the synced charge / shot clocks. */
	public static float formed(Player player, float partialTick) {
		if (!MaxSteel.isTransformed(player)) {
			return 0f;
		}
		MaxSteelFx fx = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FX, null);
		if (fx == null) {
			return 0f;
		}
		long now = player.level().getGameTime();
		if (fx.cannonChargeStart() != 0L) {
			return Math.min(1f, (now - fx.cannonChargeStart() + partialTick) / FORM_TICKS);
		}
		if (fx.anim() == MaxSteelFx.ANIM_CANNON) {
			float age = now - fx.animStart() + partialTick;
			if (age < HOLD_TICKS) {
				return 1f;
			}
			return Math.max(0f, 1f - (age - HOLD_TICKS) / FOLD_TICKS);
		}
		return 0f;
	}

	/**
	 * Draws the cannon on {@code arm}'s pose at the current pose-stack origin (the model root in third person, the
	 * hand's parent space in first person).
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, float formed) {
		if (formed <= 0.001f || !ensureBaked()) {
			return;
		}
		cannon.copyFrom(arm);
		cannon.visible = true;
		ResourceLocation tex = formed >= 0.999f ? TEXTURE : frame(formed);
		cannon.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
	}

	private static ResourceLocation frame(float formed) {
		if (frames == null) {
			frames = build();
		}
		if (frames.length == 0) {
			return TEXTURE;
		}
		return frames[Math.max(0, Math.min(STEPS - 1, (int) (formed * STEPS)))];
	}

	private static ResourceLocation[] build() {
		NativeImage src = MaxSteelNano.read(TEXTURE);
		NativeImage ord = MaxSteelNano.read(ORDER);
		if (src == null || ord == null) {
			return new ResourceLocation[0];
		}
		int w = src.getWidth();
		int h = src.getHeight();
		int edge = 0xFF000000 | (0xF8 << 16) | (0xFF << 8) | 0x8F; // ABGR of 0x8FFFF8 (nanite cyan)
		ResourceLocation[] out = new ResourceLocation[STEPS];
		for (int k = 0; k < STEPS; k++) {
			float p = k / (float) STEPS;
			NativeImage img = new NativeImage(w, h, true);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int o = ord.getPixelRGBA(x, y);
					float t = (o & 0xFF) / 255f;
					int px = src.getPixelRGBA(x, y);
					if (((o >>> 24) & 0xFF) == 0 || t <= p - 0.08f) {
						img.setPixelRGBA(x, y, px);
					} else if (t <= p) {
						img.setPixelRGBA(x, y, ((px >>> 24) & 0xFF) == 0 ? 0 : edge);
					} else {
						img.setPixelRGBA(x, y, 0);
					}
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/max_steel_arm_cannon_" + k);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
			out[k] = id;
		}
		src.close();
		ord.close();
		return out;
	}

	/** Leaving a world: drop the cached frames (re-baked on demand). */
	public static void clear() {
		if (frames != null) {
			for (ResourceLocation id : frames) {
				Minecraft.getInstance().getTextureManager().release(id);
			}
		}
		frames = null;
		cannon = null;
	}
}

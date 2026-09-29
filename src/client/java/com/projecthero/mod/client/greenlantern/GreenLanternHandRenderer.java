package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.item.GreenLanternArmorItem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.13.21: everything a Green Lantern wears or shapes on the right hand, drawn in both views -- third person through
 * {@code PowerRingLayer}, first person through {@code GreenLanternRingHandMixin} (the arm the player sees).
 * <ul>
 *   <li><b>The Power Ring</b> (explicit user request, "a new ring model that shows up on the player's hand while they
 *   are a Green Lantern"): a real little ring -- a band wrapped round the knuckles and a raised bezel with a
 *   full-bright lantern-green gem on the back of the hand -- replacing the flat item-icon fleck of v0.11.10. It sits a
 *   touch wider over the suit's gauntlet than over a bare hand, and follows slim (Alex) arms.</li>
 *   <li><b>The Energy Blade</b> and <b>Mining Drill</b> while switched on (see
 *   {@link ModAttachments#GREEN_LANTERN_HAND_CONSTRUCTS}): a translucent full-bright blade out of the fist, or a
 *   stepped spinning drill bit -- the constructs used to be nothing but a few particles.</li>
 * </ul>
 * Geometry is in the arm bone's own pixel space (y runs down the arm, the fist ends at y = 10), built once in code
 * with {@link LayerDefinition#bakeRoot()} -- no model-layer registration needed.
 */
public final class GreenLanternHandRenderer {
	private static final ResourceLocation RING_TEXTURE = ProjectHeroMod.id("textures/entity/green_lantern/power_ring_worn.png");
	private static final ResourceLocation LIGHT_TEXTURE = ProjectHeroMod.id("textures/entity/green_lantern/hard_light_construct.png");

	/** [slim][suited] */
	private static final ModelPart[][] BANDS = new ModelPart[2][2];
	private static final ModelPart[][] GEMS = new ModelPart[2][2];
	private static final ModelPart[] BLADES = new ModelPart[2];
	private static final ModelPart[][] DRILL_TIERS = new ModelPart[2][];
	private static boolean baked;

	private GreenLanternHandRenderer() {
	}

	/** Anything to draw on this player's right hand at all? */
	public static boolean hasAnything(AbstractClientPlayer player) {
		return GreenLantern.hasPower(player) && !player.isInvisible();
	}

	/**
	 * Draws the ring (+ any switched-on hand construct) on {@code arm}, which must be the right arm, posed exactly as it
	 * was just drawn. {@code pose} is the stack the arm itself was rendered with (not yet transformed into the arm).
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			float ageInTicks) {
		if (!hasAnything(player)) {
			return;
		}
		bake();
		int slim = player.getSkin().model() == PlayerSkin.Model.SLIM ? 1 : 0;
		int suited = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof GreenLanternArmorItem ? 1 : 0;
		pose.pushPose();
		arm.translateAndRotate(pose);
		BANDS[slim][suited].render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(RING_TEXTURE)), light,
				OverlayTexture.NO_OVERLAY);
		GEMS[slim][suited].render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(RING_TEXTURE)),
				LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

		int hand = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_HAND_CONSTRUCTS, 0);
		if ((hand & GreenLanternConstructs.HAND_BLADE) != 0) {
			BLADES[slim].render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(LIGHT_TEXTURE)),
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xDDFFFFFF);
		}
		if ((hand & GreenLanternConstructs.HAND_DRILL) != 0) {
			float cx = slim == 1 ? -0.5f / 16f : -1.0f / 16f;
			for (int i = 0; i < DRILL_TIERS[slim].length; i++) {
				pose.pushPose();
				pose.translate(cx, 0.0f, 0.0f);
				// alternate tiers counter-rotate a little so the bit reads as a twisting flute while it spins
				pose.mulPose(Axis.YP.rotationDegrees(ageInTicks * 36.0f + i * 22.5f));
				pose.translate(-cx, 0.0f, 0.0f);
				DRILL_TIERS[slim][i].render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(LIGHT_TEXTURE)),
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xDDFFFFFF);
				pose.popPose();
			}
		}
		pose.popPose();
	}

	private static void bake() {
		if (baked) {
			return;
		}
		baked = true;
		for (int slim = 0; slim < 2; slim++) {
			float xMin = slim == 1 ? -2f : -3f;
			float xMax = 1f;
			for (int suited = 0; suited < 2; suited++) {
				float gap = suited == 1 ? 0.62f : 0.08f;
				BANDS[slim][suited] = band(xMin, xMax, gap);
				GEMS[slim][suited] = gem(xMin, gap);
			}
			float cx = (xMin + xMax) / 2f;
			BLADES[slim] = blade(cx, xMin, xMax);
			DRILL_TIERS[slim] = drill(cx);
		}
	}

	/** A thin (0.8 px tall, 0.35 px thick) square band round the hand at the knuckles, {@code gap} proud of its surface. */
	private static ModelPart band(float xMin, float xMax, float gap) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float t = 0.35f;
		float y = 8.2f;
		float x0 = xMin - gap - t;
		float x1 = xMax + gap + t;
		float z0 = -2f - gap - t;
		float z1 = 2f + gap + t;
		CubeListBuilder cubes = CubeListBuilder.create().texOffs(0, 0)
				.addBox(x0, y, z0, x1 - x0, 0.8f, t)                      // front
				.addBox(x0, y, z1 - t, x1 - x0, 0.8f, t)                  // back
				.addBox(x0, y, z0 + t, t, 0.8f, z1 - z0 - 2 * t)          // outer side (back of the hand)
				.addBox(x1 - t, y, z0 + t, t, 0.8f, z1 - z0 - 2 * t)      // inner side (palm)
				// the bezel the gem sits in, raised off the back of the hand
				.addBox(x0 - 0.5f, 7.8f, -0.85f, 0.5f, 1.6f, 1.7f, new CubeDeformation(0f));
		root.addOrReplaceChild("band", cubes, PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16).bakeRoot();
	}

	/** The glowing lantern-green gem, set in the bezel on the back of the hand. */
	private static ModelPart gem(float xMin, float gap) {
		MeshDefinition mesh = new MeshDefinition();
		float x0 = xMin - gap - 0.35f - 0.5f;
		mesh.getRoot().addOrReplaceChild("gem", CubeListBuilder.create().texOffs(0, 8)
				.addBox(x0 - 0.35f, 8.05f, -0.55f, 0.35f, 1.1f, 1.1f), PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16).bakeRoot();
	}

	/** A broad, flat blade of light out of the fist with a small crossguard, 14 px (almost a block) long. */
	private static ModelPart blade(float cx, float xMin, float xMax) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 0)
				.addBox(xMin - 0.6f, 9.6f, -2.6f, xMax - xMin + 1.2f, 0.8f, 5.2f)   // crossguard over the fist
				.addBox(cx - 0.4f, 10.4f, -1.5f, 0.8f, 12.0f, 3.0f)                  // blade
				.addBox(cx - 0.3f, 22.4f, -1.0f, 0.6f, 2.0f, 2.0f)                   // tapering tip
				.addBox(cx - 0.2f, 24.4f, -0.45f, 0.4f, 1.4f, 0.9f), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32).bakeRoot();
	}

	/** Four stepped tiers narrowing to a point, each drawn separately so they can twist against each other. */
	private static ModelPart[] drill(float cx) {
		float[][] tiers = {{3.8f, 10.0f, 2.2f}, {2.8f, 12.2f, 2.2f}, {1.8f, 14.4f, 2.2f}, {0.8f, 16.6f, 1.8f}};
		ModelPart[] parts = new ModelPart[tiers.length];
		for (int i = 0; i < tiers.length; i++) {
			float w = tiers[i][0];
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("tier", CubeListBuilder.create().texOffs(0, 0)
					.addBox(cx - w / 2f, tiers[i][1], -w / 2f, w, tiers[i][2], w), PartPose.ZERO);
			parts[i] = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		}
		return parts;
	}
}

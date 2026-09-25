package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.wolverine.data.ClawTier;
import com.projecthero.mod.wolverine.data.WolverineState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The adamantium claws: three curved blades per hand (six in all), each a chain of four tapering
 * segments nested on child parts so the blade bends gently forward and ends in a needle point --
 * 12 boxes a hand, no more geometry than a pair of small items. Hand-local space is the player arm
 * bone's: the fist ends 10 px below the shoulder pivot, and the blades run on down from there.
 *
 * <p>Deployment is driven purely by the synced {@code WolverineState} (so every viewer sees the same
 * thing): the blades lengthen from the knuckles over {@link #EXTEND_TICKS} ticks and shorten back the
 * same way, using the parts' Y scale about their knuckle-end pivot.
 */
public final class WolverineClawsModel {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectHeroMod.id("wolverine_claws"), "main");
	public static final ModelLayerLocation BONE_LAYER = new ModelLayerLocation(ProjectHeroMod.id("wolverine_bone_claws"), "main");
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/wolverine_claws.png");
	public static final ResourceLocation BONE_TEXTURE = ProjectHeroMod.id("textures/entity/wolverine_bone_claws.png");

	/** The texture for a claw tier: ivory bone for {@link ClawTier#BONE}, silver adamantium otherwise. */
	public static ResourceLocation textureFor(ClawTier tier) {
		return tier == ClawTier.BONE ? BONE_TEXTURE : TEXTURE;
	}

	/** The model layer for a claw tier. */
	public static ModelLayerLocation layerFor(ClawTier tier) {
		return tier == ClawTier.BONE ? BONE_LAYER : LAYER;
	}

	/** Ticks the blades take to slide out / back in. */
	private static final float EXTEND_TICKS = 6.0f;
	/** Arm-bone Y of the end of the fist. */
	private static final float HAND_END = 9.0f;
	private static final float BLADE_SPACING = 1.3f;

	private final ModelPart rightHand;
	private final ModelPart leftHand;

	public WolverineClawsModel(ModelPart root) {
		this.rightHand = root.getChild("right_hand");
		this.leftHand = root.getChild("left_hand");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (String side : new String[] {"right_hand", "left_hand"}) {
			// blades bend toward the body: about the arm bone Z axis, opposite sign per side
			float s = side.startsWith("right") ? -1.0F : 1.0F;
			PartDefinition hand = root.addOrReplaceChild(side, CubeListBuilder.create(), PartPose.offset(0.0F, HAND_END, 0.0F));
			for (int i = 0; i < 3; i++) {
				float z = (i - 1) * BLADE_SPACING;
				PartDefinition base = hand.addOrReplaceChild("blade" + i, CubeListBuilder.create()
						.texOffs(0, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
						PartPose.offset(0.0F, 0.0F, z));
				PartDefinition mid = base.addOrReplaceChild("mid", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-0.45F, 0.0F, -0.45F, 0.9F, 5.0F, 0.9F),
						PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.04F * s));
				PartDefinition tip = mid.addOrReplaceChild("tip", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-0.35F, 0.0F, -0.35F, 0.7F, 4.0F, 0.7F),
						PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.06F * s));
				tip.addOrReplaceChild("point", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-0.2F, 0.0F, -0.2F, 0.4F, 2.0F, 0.4F),
						PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.08F * s));
			}
		}
		return LayerDefinition.create(mesh, 16, 16);
	}

	/**
	 * The bone claws: three thicker, shorter, organic blades per hand -- five tapering segments each that bend
	 * inward more than the adamantium blades, with the outer two fanned slightly apart and a touch shorter, so
	 * they read as grown bone rather than forged metal. Segments use four quadrants of the 16x16 bone texture
	 * (darkest at the base, palest at the tip). 15 boxes a hand.
	 */
	public static LayerDefinition createBoneLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// per segment: length, thickness, texture u, texture v
		float[][] seg = {{4.0F, 1.5F, 0, 0}, {3.5F, 1.3F, 8, 0}, {3.0F, 1.1F, 0, 8}, {2.5F, 0.85F, 8, 8}, {1.5F, 0.5F, 8, 8}};
		for (String side : new String[] {"right_hand", "left_hand"}) {
			float s = side.startsWith("right") ? -1.0F : 1.0F;
			PartDefinition hand = root.addOrReplaceChild(side, CubeListBuilder.create(), PartPose.offset(0.0F, HAND_END, 0.0F));
			for (int i = 0; i < 3; i++) {
				float z = (i - 1) * 1.55F;
				float k = i == 1 ? 1.0F : 0.9F; // the outer blades are shorter
				float fan = (i - 1) * 0.07F;    // ...and splay outward
				float curve = (i == 1 ? 0.10F : 0.13F) * s;
				PartDefinition parent = hand;
				float prevLen = 0.0F;
				for (int j = 0; j < seg.length; j++) {
					float len = seg[j][0] * k;
					float w = seg[j][1];
					PartPose pose = j == 0
							? PartPose.offsetAndRotation(0.0F, 0.0F, z, fan, 0.0F, 0.0F)
							: PartPose.offsetAndRotation(0.0F, prevLen, 0.0F, 0.0F, 0.0F, curve * (0.7F + 0.25F * j));
					parent = parent.addOrReplaceChild("seg" + j, CubeListBuilder.create()
							.texOffs((int) seg[j][2], (int) seg[j][3]).addBox(-w / 2, 0.0F, -w / 2, w, len, w), pose);
					prevLen = len;
				}
			}
		}
		return LayerDefinition.create(mesh, 16, 16);
	}

	/** How far out the claws are for {@code player} right now: 0 (hidden) to 1 (fully deployed). */
	public static float extension(AbstractClientPlayer player, float partialTick) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (s == null || !s.hasPower || !s.clawTier.hasClaws()) {
			return 0.0f;
		}
		float t = (player.level().getGameTime() + partialTick - s.clawsChangedAt) / EXTEND_TICKS;
		t = Math.max(0.0f, Math.min(1.0f, t));
		return s.clawsOut ? t : 1.0f - t;
	}

	/** Whether a player currently needs the claws drawn at all (cheap early-out for the layer / mixin). */
	public static boolean visible(AbstractClientPlayer player, float partialTick) {
		return extension(player, partialTick) > 0.02f;
	}

	/**
	 * Draw one hand's three blades. The pose stack must already be in that arm bone's space (after
	 * {@code arm.translateAndRotate}).
	 */
	public void render(PoseStack pose, VertexConsumer buffer, int light, int overlay, boolean rightArm, boolean slim,
			float extension, int argb) {
		ModelPart hand = rightArm ? rightHand : leftHand;
		float center = slim ? 1.25F : 1.75F;
		hand.x = rightArm ? -center : center;
		hand.yScale = extension;
		hand.render(pose, buffer, light, overlay, argb);
	}
}

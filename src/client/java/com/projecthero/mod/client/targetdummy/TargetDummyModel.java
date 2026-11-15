package com.projecthero.mod.client.targetdummy;

import com.projecthero.mod.targetdummy.TargetDummyEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * v0.17.5: the Target Dummy -- a burlap-sack head, a straw torso with a red target, straw arms held out like a scarecrow
 * and a wooden post instead of legs (64x64 {@code textures/entity/target_dummy.png}, drawn by
 * {@code scratchpad/gen_target_dummy_v0175.js}). It is a {@link HumanoidModel} so vanilla armour fits it: a helmet on the
 * sack, a chestplate on the torso and arms, leggings and boots round the post. The base plate is drawn separately by the
 * renderer so it stays planted while the rest wobbles.
 */
public class TargetDummyModel extends HumanoidModel<TargetDummyEntity> {
	/** Arms held out, drooping a little. */
	private static final float ARM_Z = 1.40f;

	private final ModelPart post;

	public TargetDummyModel(ModelPart root) {
		super(root);
		this.post = root.getChild("post");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f)
				// the sack's tied-off top
				.texOffs(32, 0).addBox(-1.5f, -10.0f, -1.5f, 3.0f, 2.0f, 3.0f), PartPose.ZERO);
		root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-2.5f, -2.0f, -2.0f, 4.0f, 11.0f, 4.0f),
				PartPose.offset(-5.0f, 2.0f, 0.0f));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).mirror().addBox(-1.5f, -2.0f, -2.0f, 4.0f, 11.0f, 4.0f),
				PartPose.offset(5.0f, 2.0f, 0.0f));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12.0f, 0.0f));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12.0f, 0.0f));
		// the post from the waist to the base plate, and a straw tuft where the torso is tied to it
		root.addOrReplaceChild("post", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 12.0f, 2.0f)
				.texOffs(0, 52).addBox(-3.0f, -1.0f, -1.5f, 6.0f, 3.0f, 3.0f), PartPose.offset(0.0f, 12.0f, 0.0f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** The base plate, drawn on its own so it never wobbles. */
	public static LayerDefinition createBaseLayer() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 32).addBox(-6.0f, -2.0f, -6.0f, 12.0f, 2.0f, 12.0f),
				PartPose.offset(0.0f, 24.0f, 0.0f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(TargetDummyEntity dummy, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		head.setRotation(0.0f, 0.0f, 0.0f);
		hat.copyFrom(head);
		body.setRotation(0.0f, 0.0f, 0.0f);
		rightArm.setRotation(0.0f, 0.0f, ARM_Z);
		leftArm.setRotation(0.0f, 0.0f, -ARM_Z);
		rightLeg.setRotation(0.0f, 0.0f, 0.0f);
		leftLeg.setRotation(0.0f, 0.0f, 0.0f);
		post.setRotation(0.0f, 0.0f, 0.0f);
	}

	@Override
	protected Iterable<ModelPart> bodyParts() {
		return com.google.common.collect.ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg, hat, post);
	}
}

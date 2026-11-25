package com.projecthero.mod.client.shazam;

import com.projecthero.mod.shazam.entity.WizardShazamEntity;
import com.projecthero.mod.shazam.journey.ThroneWizardEntity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * v0.17.7 Shazam acquisition: the Wizard on his throne. The standing pose is {@link WizardShazamModel}'s (hunched on his staff);
 * seated, he sits upright with his knees forward, his left hand on the throne's arm and his right on the staff, and as he rises
 * ({@link ThroneWizardEntity#sitAmount}) the pose blends into the standing one.
 */
public class ThroneWizardModel extends WizardShazamModel {
	public ThroneWizardModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(WizardShazamEntity entity, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
		super.setupAnim(entity, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
		if (!(entity instanceof ThroneWizardEntity throne)) {
			return;
		}
		float pt = age - entity.tickCount;
		float s = throne.sitAmount(Mth.clamp(pt, 0f, 1f));
		if (s <= 0.001f) {
			return;
		}
		body.xRot = Mth.lerp(s, body.xRot, 0.04f);
		head.z = Mth.lerp(s, head.z, 0f);
		head.xRot = Mth.lerp(s, head.xRot, Mth.clamp(head.xRot - 0.15f, -0.4f, 0.4f));
		rightLeg.xRot = Mth.lerp(s, rightLeg.xRot, -1.45f);
		leftLeg.xRot = Mth.lerp(s, leftLeg.xRot, -1.45f);
		rightLeg.yRot = Mth.lerp(s, rightLeg.yRot, 0.1f);
		leftLeg.yRot = Mth.lerp(s, leftLeg.yRot, -0.1f);
		rightLeg.z = Mth.lerp(s, rightLeg.z, 0.2f);
		leftLeg.z = Mth.lerp(s, leftLeg.z, 0.2f);
		leftArm.xRot = Mth.lerp(s, leftArm.xRot, -0.5f);
		leftArm.zRot = Mth.lerp(s, leftArm.zRot, -0.05f);
		rightArm.xRot = Mth.lerp(s, rightArm.xRot, -0.6f);
		hat.copyFrom(head);
		jacket.copyFrom(body);
		rightSleeve.copyFrom(rightArm);
		leftSleeve.copyFrom(leftArm);
		rightPants.copyFrom(rightLeg);
		leftPants.copyFrom(leftLeg);
	}
}

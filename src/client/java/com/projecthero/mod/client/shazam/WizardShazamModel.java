package com.projecthero.mod.client.shazam;

import com.projecthero.mod.shazam.entity.WizardShazamEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Shazam: the Wizard's pose on the ordinary player model. An old man leaning on his staff: the torso hunched forward a
 * little, the head bowed (still turning toward the nearest player through his look goal), the right hand out on the staff, the
 * left arm hanging loose under the drape, a slow breath that lifts the shoulders. {@link WizardShazamLayer} hangs the hair, beard,
 * robes and staff on these parts.
 */
public class WizardShazamModel extends PlayerModel<WizardShazamEntity> {
	/** How far the torso leans forward (radians), and where that leaves the hips (model pixels behind the neck). */
	static final float HUNCH = 0.2f;
	static final float HIP_BACK = 12f * Mth.sin(HUNCH);

	public WizardShazamModel(ModelPart root) {
		super(root, false);
	}

	@Override
	public void setupAnim(WizardShazamEntity entity, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
		super.setupAnim(entity, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
		float breath = Mth.sin(age * 0.07f); // about one breath every 4.5 s
		// the hunch: the body pivots at the neck, so the hips (and legs) sit a little further back
		body.xRot = HUNCH + breath * 0.012f;
		body.y = 0.4f;
		head.z = -1.4f;
		head.y = 0.6f - breath * 0.15f;
		// the bowed head: a little lower than the look goal asks, never past the chest
		head.xRot = Mth.clamp(head.xRot + 0.22f, -0.5f, 0.75f);
		head.yRot = Mth.clamp(head.yRot, -1.0f, 1.0f);
		// right hand out on the staff (the staff itself is placed from this arm in WizardShazamLayer)
		rightArm.xRot = -0.55f - breath * 0.02f;
		rightArm.yRot = -0.12f;
		rightArm.zRot = 0.16f;
		rightArm.y = 2.2f - breath * 0.2f;
		// left arm hangs loose, a little forward, out of the drape's way
		leftArm.xRot = -0.12f + Mth.sin(age * 0.05f) * 0.03f;
		leftArm.yRot = 0.05f;
		leftArm.zRot = -0.1f - breath * 0.02f;
		leftArm.y = 2.2f - breath * 0.2f;
		rightLeg.z = HIP_BACK;
		leftLeg.z = HIP_BACK;
		rightLeg.y = 12f;
		leftLeg.y = 12f;
		hat.copyFrom(head);
		jacket.copyFrom(body);
		rightSleeve.copyFrom(rightArm);
		leftSleeve.copyFrom(leftArm);
		rightPants.copyFrom(rightLeg);
		leftPants.copyFrom(leftLeg);
	}
}

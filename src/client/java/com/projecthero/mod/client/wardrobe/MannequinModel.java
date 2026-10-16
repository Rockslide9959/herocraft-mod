package com.projecthero.mod.client.wardrobe;

import com.projecthero.mod.wardrobe.MannequinEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Rotations;
import net.minecraft.util.Mth;

/**
 * v0.16.5: the Mannequin's body -- the wide player model (so every armour, suit and held item fits it exactly as on a
 * player), posed from the armour stand's six pose angles the way vanilla's armour stand model is. The mannequin skin has
 * no outer layer, so the hat / jacket / sleeve / trouser parts are hidden.
 */
public class MannequinModel extends PlayerModel<MannequinEntity> {
	public MannequinModel(ModelPart root) {
		super(root, false);
	}

	@Override
	public void setupAnim(MannequinEntity mannequin, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		pose(head, mannequin.getHeadPose());
		pose(body, mannequin.getBodyPose());
		pose(leftArm, mannequin.getLeftArmPose());
		pose(rightArm, mannequin.getRightArmPose());
		pose(leftLeg, mannequin.getLeftLegPose());
		pose(rightLeg, mannequin.getRightLegPose());
		hat.copyFrom(head);
		jacket.copyFrom(body);
		leftSleeve.copyFrom(leftArm);
		rightSleeve.copyFrom(rightArm);
		leftPants.copyFrom(leftLeg);
		rightPants.copyFrom(rightLeg);
		hat.visible = false;
		jacket.visible = false;
		leftSleeve.visible = false;
		rightSleeve.visible = false;
		leftPants.visible = false;
		rightPants.visible = false;
	}

	private static void pose(ModelPart part, Rotations r) {
		part.xRot = Mth.DEG_TO_RAD * r.getX();
		part.yRot = Mth.DEG_TO_RAD * r.getY();
		part.zRot = Mth.DEG_TO_RAD * r.getZ();
	}
}

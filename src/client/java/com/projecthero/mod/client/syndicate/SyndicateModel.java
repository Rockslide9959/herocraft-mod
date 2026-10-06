package com.projecthero.mod.client.syndicate;

import com.projecthero.mod.syndicate.KingpinCaneSwing;
import com.projecthero.mod.syndicate.entity.KingpinEntity;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.syndicate.entity.SyndicateEnforcer;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * v0.14.25: the player model, posed from each crook's synced action ({@link SyndicateCriminal#action}): gunmen raise
 * their guns (one arm for a pistol, both for a long gun), the Enforcer stamps, charges head-down and slams with both
 * fists, and the Kingpin leans into his rush, grabs with both hands, aims his cane, raises both arms for the pound and
 * whistles with a hand to his mouth. The sleeve and jacket layers are re-copied after posing so the skin's outer layer
 * follows.
 */
public class SyndicateModel extends PlayerModel<SyndicateCriminal> {
	public SyndicateModel(ModelPart root, boolean slim) {
		super(root, slim);
	}

	@Override
	public void setupAnim(SyndicateCriminal e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
		byte action = e.action();
		this.crouching = e instanceof KingpinEntity ? action == KingpinEntity.ACTION_RUSH || action == KingpinEntity.ACTION_RUSH_WINDUP
				: e instanceof SyndicateEnforcer && (action == SyndicateEnforcer.ACTION_CHARGE || action == SyndicateEnforcer.ACTION_WINDUP);
		super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
		float hx = head.xRot;
		float hy = head.yRot;

		if (e instanceof SyndicateGunman g && action == SyndicateCriminal.ACTION_AIM) {
			// the Punisher guns' third-person transforms are tuned for a lowered arm, so the crooks fire from the hip:
			// the forearm comes up a little and follows the head, which keeps the barrel level and on target
			rightArm.xRot = -0.35f + hx * 0.6f;
			rightArm.yRot = hy * 0.8f - 0.05f;
			rightArm.zRot = 0;
			if (g.kind() != SyndicateGunman.Kind.PISTOL) {
				// a long gun: the left hand comes across to the fore-grip
				leftArm.xRot = -0.9f + hx * 0.6f;
				leftArm.yRot = hy + 0.55f;
				leftArm.zRot = 0;
			}
		} else if (e instanceof SyndicateEnforcer) {
			switch (action) {
				case SyndicateEnforcer.ACTION_WINDUP -> {
					rightArm.xRot = 0.9f;
					leftArm.xRot = 0.9f;
					rightLeg.xRot = Mth.sin(age * 1.4f) * 0.5f;
				}
				case SyndicateEnforcer.ACTION_CHARGE -> {
					rightArm.xRot = 0.6f;
					leftArm.xRot = 0.6f;
					rightArm.zRot = 0.3f;
					leftArm.zRot = -0.3f;
				}
				case SyndicateEnforcer.ACTION_SLAM -> slam(age);
				case SyndicateEnforcer.ACTION_DAZED -> dazed(age);
				default -> {
				}
			}
		} else if (e instanceof KingpinEntity) {
			switch (action) {
				case KingpinEntity.ACTION_RUSH_WINDUP -> {
					rightArm.xRot = 0.5f;
					leftArm.xRot = 0.5f;
					rightLeg.xRot = Mth.sin(age * 1.2f) * 0.4f;
				}
				case KingpinEntity.ACTION_RUSH -> {
					rightArm.xRot = 0.4f;
					leftArm.xRot = 0.4f;
					rightArm.zRot = 0.35f;
					leftArm.zRot = -0.35f;
				}
				case KingpinEntity.ACTION_GRAB -> {
					rightArm.xRot = -1.3f;
					leftArm.xRot = -1.3f;
					rightArm.yRot = -0.25f;
					leftArm.yRot = 0.25f;
				}
				case KingpinEntity.ACTION_HOLD -> {
					rightArm.xRot = -2.2f;
					leftArm.xRot = -2.2f;
					rightArm.yRot = -0.15f;
					leftArm.yRot = 0.15f;
				}
				case KingpinEntity.ACTION_GUN -> aimRight(hx, hy);
				case KingpinEntity.ACTION_POUND -> slam(age);
				case KingpinEntity.ACTION_WHISTLE -> {
					rightArm.xRot = -2.1f;
					rightArm.yRot = -0.5f;
					rightArm.zRot = 0.4f;
				}
				case KingpinEntity.ACTION_STUNNED -> dazed(age);
				default -> {
				}
			}
		}
		// v0.14.31: the Kingpin's cane blows play the cane strikes (overhead strike / side swipe / thrust); his move
		// poses above own the body while a move runs, and he never swings the cane during one
		if (action == SyndicateCriminal.ACTION_NONE && KingpinCaneSwing.wielding(e)) {
			KingpinCanePose.apply(e, this);
		}
		leftSleeve.copyFrom(leftArm);
		rightSleeve.copyFrom(rightArm);
		leftPants.copyFrom(leftLeg);
		rightPants.copyFrom(rightLeg);
		jacket.copyFrom(body);
		hat.copyFrom(head);
	}

	private void aimRight(float hx, float hy) {
		rightArm.xRot = -Mth.HALF_PI + hx;
		rightArm.yRot = hy - 0.1f;
		rightArm.zRot = 0;
	}

	private void slam(float age) {
		float t = (Mth.sin(age * 0.35f) + 1f) * 0.5f;
		rightArm.xRot = -2.8f + t * 0.4f;
		leftArm.xRot = -2.8f + t * 0.4f;
		rightArm.zRot = 0.2f;
		leftArm.zRot = -0.2f;
	}

	private void dazed(float age) {
		head.zRot = Mth.sin(age * 0.5f) * 0.25f;
		head.xRot = 0.3f;
		rightArm.xRot = 0.2f;
		leftArm.xRot = 0.2f;
	}
}

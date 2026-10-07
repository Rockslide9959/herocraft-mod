package com.projecthero.mod.client.ultron;

import com.projecthero.mod.ultron.entity.UltronDroneEntity;
import com.projecthero.mod.ultron.entity.UltronHeavyEntity;
import com.projecthero.mod.ultron.entity.UltronPrimeEntity;
import com.projecthero.mod.ultron.entity.UltronRobot;
import com.projecthero.mod.ultron.entity.UltronSentinelDroneEntity;
import com.projecthero.mod.ultron.entity.UltronSentryEntity;
import com.projecthero.mod.ultron.entity.UltronSniperEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * v0.15.12: the player model, posed from each robot's synced action ({@link UltronRobot#action}): the flyers (drones,
 * Prime) hang with their legs trailing and raise a palm to fire; the Sentinel Drone claws; the Heavy throws both arms up
 * for its barrage and stamps; the Sniper Frame shoulders an invisible rifle; Prime spreads, pulls, punches and dives; the
 * Sentry raises both fists, charges its chest and staggers. Anything EMP-stunned sags with its head lolling. The skin's
 * outer layer is re-copied after posing so it follows.
 */
public class UltronModel extends PlayerModel<UltronRobot> {
	public UltronModel(ModelPart root, boolean slim) {
		super(root, slim);
	}

	@Override
	public void setupAnim(UltronRobot e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
		boolean flyer = e instanceof UltronDroneEntity || e instanceof UltronPrimeEntity;
		this.crouching = false;
		super.setupAnim(e, flyer ? 0f : limbSwing, flyer ? 0f : limbSwingAmount, age, headYaw, headPitch);
		float hx = head.xRot;
		float hy = head.yRot;
		byte action = e.action();
		if (flyer && !e.isDeadOrDying()) {
			// hanging in the air: legs together and trailing, arms a little out, a slow bob of the limbs
			float bob = Mth.sin(age * 0.12f) * 0.06f;
			rightLeg.xRot = 0.25f + bob;
			leftLeg.xRot = 0.18f - bob;
			rightLeg.yRot = 0;
			leftLeg.yRot = 0;
			rightArm.xRot = 0.15f;
			leftArm.xRot = 0.15f;
			rightArm.zRot = 0.18f + bob;
			leftArm.zRot = -0.18f - bob;
		}
		if (e.isStunned()) {
			head.zRot = Mth.sin(age * 0.6f) * 0.3f;
			head.xRot = 0.5f;
			rightArm.xRot = 0.3f;
			leftArm.xRot = 0.3f;
			rightArm.zRot = 0.05f;
			leftArm.zRot = -0.05f;
		} else if (e instanceof UltronDroneEntity) {
			if (action == UltronRobot.ACTION_AIM) {
				aimRight(hx, hy);
			}
		} else if (e instanceof UltronSentinelDroneEntity) {
			if (action == UltronRobot.ACTION_AIM) {
				float s = Mth.sin(age * 0.9f) * 0.5f;
				rightArm.xRot = -1.4f + s;
				leftArm.xRot = -1.4f - s;
				rightArm.zRot = -0.2f;
				leftArm.zRot = 0.2f;
			}
		} else if (e instanceof UltronHeavyEntity) {
			if (action == UltronHeavyEntity.ACTION_BARRAGE) {
				rightArm.xRot = -2.7f;
				leftArm.xRot = -2.7f;
				rightArm.zRot = 0.35f;
				leftArm.zRot = -0.35f;
			} else if (action == UltronHeavyEntity.ACTION_STOMP) {
				rightLeg.xRot = -0.9f;
				rightArm.xRot = 0.6f;
				leftArm.xRot = 0.6f;
				rightArm.zRot = 0.5f;
				leftArm.zRot = -0.5f;
			}
		} else if (e instanceof UltronSniperEntity) {
			if (action == UltronRobot.ACTION_AIM) {
				aimRight(hx, hy);
				leftArm.xRot = -Mth.HALF_PI + hx;
				leftArm.yRot = hy + 0.5f;
				leftArm.zRot = 0;
			}
		} else if (e instanceof UltronPrimeEntity) {
			posePrime(action, hx, hy, age);
		} else if (e instanceof UltronSentryEntity) {
			poseSentry(action, age);
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

	private void posePrime(byte action, float hx, float hy, float age) {
		switch (action) {
			case UltronPrimeEntity.ACTION_BARRAGE -> {
				boolean right = ((int) (age / 3f)) % 2 == 0;
				if (right) {
					aimRight(hx, hy);
				} else {
					leftArm.xRot = -Mth.HALF_PI + hx;
					leftArm.yRot = hy + 0.1f;
					leftArm.zRot = 0;
				}
			}
			case UltronPrimeEntity.ACTION_TELEGRAPH, UltronPrimeEntity.ACTION_BEAM -> {
				rightArm.xRot = 0.4f;
				leftArm.xRot = 0.4f;
				rightArm.zRot = 0.7f;
				leftArm.zRot = -0.7f;
			}
			case UltronPrimeEntity.ACTION_SWARM -> {
				rightArm.xRot = -0.2f;
				leftArm.xRot = -0.2f;
				rightArm.zRot = 2.3f;
				leftArm.zRot = -2.3f;
			}
			case UltronPrimeEntity.ACTION_PULL -> {
				float s = Mth.sin(age * 1.2f) * 0.1f;
				rightArm.xRot = -Mth.HALF_PI + s;
				leftArm.xRot = -Mth.HALF_PI - s;
				rightArm.yRot = -0.3f;
				leftArm.yRot = 0.3f;
				rightArm.zRot = 0;
				leftArm.zRot = 0;
			}
			case UltronPrimeEntity.ACTION_PUNCH -> {
				rightArm.xRot = -Mth.HALF_PI - 0.1f;
				rightArm.yRot = 0.1f;
				rightArm.zRot = 0;
				leftArm.xRot = 0.6f;
			}
			case UltronPrimeEntity.ACTION_DIVE_UP -> {
				rightArm.xRot = -3.0f;
				leftArm.xRot = -3.0f;
				rightArm.zRot = 0.1f;
				leftArm.zRot = -0.1f;
				rightLeg.xRot = 0.05f;
				leftLeg.xRot = 0.05f;
			}
			case UltronPrimeEntity.ACTION_DIVE_DOWN -> {
				rightArm.xRot = -2.6f;
				leftArm.xRot = -2.6f;
				rightArm.zRot = -0.15f;
				leftArm.zRot = 0.15f;
				rightLeg.xRot = 0.5f;
				leftLeg.xRot = 0.5f;
			}
			default -> {
			}
		}
	}

	private void poseSentry(byte action, float age) {
		switch (action) {
			case UltronSentryEntity.ACTION_SLAM_UP -> {
				rightArm.xRot = -2.9f;
				leftArm.xRot = -2.9f;
				rightArm.zRot = 0.15f;
				leftArm.zRot = -0.15f;
			}
			case UltronSentryEntity.ACTION_SLAM -> {
				rightArm.xRot = -0.9f;
				leftArm.xRot = -0.9f;
				rightArm.zRot = -0.25f;
				leftArm.zRot = 0.25f;
			}
			case UltronSentryEntity.ACTION_CHARGE -> {
				float s = Mth.sin(age * 0.8f) * 0.06f;
				rightArm.xRot = 0.5f + s;
				leftArm.xRot = 0.5f - s;
				rightArm.zRot = 0.9f;
				leftArm.zRot = -0.9f;
				head.xRot = -0.25f;
			}
			case UltronSentryEntity.ACTION_CANNON -> {
				rightArm.xRot = 0.7f;
				leftArm.xRot = 0.7f;
				rightArm.zRot = 1.2f;
				leftArm.zRot = -1.2f;
				head.xRot = -0.35f;
			}
			case UltronSentryEntity.ACTION_RAIN -> {
				rightArm.xRot = -3.0f;
				rightArm.zRot = 0.2f;
			}
			case UltronSentryEntity.ACTION_STAGGER -> {
				head.zRot = Mth.sin(age * 0.5f) * 0.25f;
				head.xRot = 0.45f;
				rightArm.xRot = 0.35f;
				leftArm.xRot = 0.35f;
			}
			default -> {
			}
		}
	}
}

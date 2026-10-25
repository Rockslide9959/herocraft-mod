package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.silversurfer.SilverSurfer;
import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Silver Surfer poses, at the very end of {@code PlayerModel.setupAnim} so they win: the surf stance on the board
 * (feet apart along it, knees soft, arms out for balance; lower and arms swept back sprinting / at Light-Speed), and the
 * move poses (the blast hand thrown at the aim, the barrier arms, the throw, the nova / meteor call, the healing hands).
 * The model's own arms and sleeves are hidden while he has the power -- they are drawn wide-armed in his skin by
 * {@code SilverSurferLayer} (and in first person by {@code SilverSurferSkinMixin}).
 */
@Mixin(PlayerModel.class)
public abstract class SilverSurferPoseMixin<T extends LivingEntity> {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$surferPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
			CallbackInfo ci) {
		if (!(entity instanceof Player player) || !SilverSurfer.hasPower(player)) {
			return;
		}
		PlayerModel<?> m = (PlayerModel<?>) (Object) this;
		SilverSurferState s = SilverSurfer.peek(player);
		long now = player.level().getGameTime();
		float pt = ageInTicks - player.tickCount;
		float bob = Mth.sin(ageInTicks * 0.08f);
		if (s.surfing) {
			boolean fast = player.isSprinting() || s.lightSpeedUntil > now;
			m.leftLeg.xRot = fast ? -0.55f : -0.38f;
			m.rightLeg.xRot = fast ? 0.45f : 0.32f;
			m.leftLeg.yRot = 0f;
			m.rightLeg.yRot = 0f;
			m.leftLeg.zRot = -0.07f;
			m.rightLeg.zRot = 0.07f;
			m.body.xRot = fast ? 0.32f : 0.1f;
			if (fast) {
				// swept back, low
				m.leftArm.xRot = m.rightArm.xRot = 0.9f;
				m.leftArm.zRot = -0.3f;
				m.rightArm.zRot = 0.3f;
			} else {
				m.leftArm.xRot = -0.1f;
				m.rightArm.xRot = -0.1f;
				m.leftArm.zRot = -0.8f - bob * 0.06f;
				m.rightArm.zRot = 0.8f + bob * 0.06f;
			}
			m.leftArm.yRot = m.rightArm.yRot = 0f;
		}
		// the move poses
		float age = now - s.animStart + pt;
		boolean beam = s.beamStart != 0L;
		if (beam || (s.animId == SilverSurferState.ANIM_BLAST && age < 12) || (s.animId == SilverSurferState.ANIM_TRANSMUTE && age < 16)) {
			m.rightArm.xRot = -Mth.HALF_PI + headPitch * Mth.DEG_TO_RAD;
			m.rightArm.yRot = m.head.yRot;
			m.rightArm.zRot = 0f;
		} else if (s.animId == SilverSurferState.ANIM_THROW && age < 10) {
			float k = Mth.clamp(age / 6f, 0f, 1f);
			m.rightArm.xRot = Mth.lerp(k, -2.6f, -1.2f);
			m.rightArm.yRot = Mth.lerp(k, 0.6f, -0.5f);
			m.rightArm.zRot = 0f;
		} else if ((s.animId == SilverSurferState.ANIM_NOVA && age < 14) || (s.animId == SilverSurferState.ANIM_METEOR && age < 20)
				|| (s.animId == SilverSurferState.ANIM_BOND && age < 30)) {
			m.leftArm.xRot = m.rightArm.xRot = -2.8f;
			m.leftArm.zRot = -0.35f;
			m.rightArm.zRot = 0.35f;
			m.leftArm.yRot = m.rightArm.yRot = 0f;
		} else if ((s.animId == SilverSurferState.ANIM_BARRAGE && age < 12) || (s.animId == SilverSurferState.ANIM_HEAL && age < 20)) {
			m.leftArm.xRot = m.rightArm.xRot = -1.4f;
			m.leftArm.yRot = 0.35f;
			m.rightArm.yRot = -0.35f;
			m.leftArm.zRot = m.rightArm.zRot = 0f;
		}
		if (s.barrierUntil > now && s.barrierHp > 0f) {
			m.leftArm.xRot = m.rightArm.xRot = -1.45f;
			m.leftArm.yRot = 0.55f;
			m.rightArm.yRot = -0.55f;
			m.leftArm.zRot = m.rightArm.zRot = 0f;
		}
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		// his arms are drawn wide in his skin by the layer
		m.leftArm.visible = false;
		m.rightArm.visible = false;
		m.leftSleeve.visible = false;
		m.rightSleeve.visible = false;
	}
}

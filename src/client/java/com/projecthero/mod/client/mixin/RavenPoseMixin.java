package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.data.RavenState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Raven poses, at the very end of {@code PlayerModel.setupAnim} so they win:
 * <ul>
 *   <li><b>Meditating</b>: floating cross-legged, hands resting out over the knees, a slow breathing bob of the head.</li>
 *   <li><b>Levitating</b> (double-tap Space flight): upright and calm, legs together and trailing a little, arms held a touch out from
 *   the sides, drifting -- not the superhero lean.</li>
 *   <li><b>Casting</b>: Telekinesis and Soul Devour's beam raise the right hand toward the target; the Soul Shield snapping up and
 *   the Dark Energy Wave's charge push both hands forward; Soul Restoration's channel spreads the arms, palms up; Demonic Ascension
 *   holds the arms a little out from the sides.</li>
 *   <li><b>A streak of shadow</b> (Shadow Dash, sunk for Shadow Step): the body is hidden (the visibility is reset by vanilla every
 *   frame, so this never leaks onto another player).</li>
 * </ul>
 */
@Mixin(PlayerModel.class)
public abstract class RavenPoseMixin<T extends LivingEntity> {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$ravenPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
			CallbackInfo ci) {
		if (!(entity instanceof Player player) || !Raven.hasPower(player)) {
			return;
		}
		PlayerModel<?> m = (PlayerModel<?>) (Object) this;
		RavenState s = Raven.peek(player);
		if (Raven.inShadow(player)) {
			m.setAllVisible(false);
			return;
		}
		float breathe = Mth.sin(ageInTicks * 0.06f);
		boolean posed = false;
		if (s.meditating) {
			m.rightLeg.xRot = -1.42f;
			m.leftLeg.xRot = -1.42f;
			m.rightLeg.yRot = -0.62f;
			m.leftLeg.yRot = 0.62f;
			m.rightLeg.zRot = 0.0f;
			m.leftLeg.zRot = 0.0f;
			m.rightArm.xRot = -0.55f;
			m.leftArm.xRot = -0.55f;
			m.rightArm.yRot = 0.0f;
			m.leftArm.yRot = 0.0f;
			m.rightArm.zRot = 0.42f + breathe * 0.03f;
			m.leftArm.zRot = -0.42f - breathe * 0.03f;
			m.body.xRot = 0f;
			m.head.xRot = 0.12f + breathe * 0.03f;
			m.head.yRot = 0f;
			m.hat.copyFrom(m.head);
			posed = true;
		} else if (s.flying && !player.isSprinting()) {
			m.rightLeg.xRot = 0.18f + breathe * 0.04f;
			m.leftLeg.xRot = 0.08f - breathe * 0.04f;
			m.rightLeg.yRot = m.leftLeg.yRot = 0f;
			m.rightLeg.zRot = 0.02f;
			m.leftLeg.zRot = -0.02f;
			m.rightArm.xRot = 0.12f;
			m.leftArm.xRot = 0.12f;
			m.rightArm.yRot = m.leftArm.yRot = 0f;
			m.rightArm.zRot = 0.32f + breathe * 0.05f;
			m.leftArm.zRot = -0.32f - breathe * 0.05f;
			posed = true;
		}
		if (s.tkTarget >= 0) {
			m.rightArm.xRot = -Mth.HALF_PI + headPitch * Mth.DEG_TO_RAD;
			m.rightArm.yRot = m.head.yRot - 0.1f;
			m.rightArm.zRot = 0f;
			posed = true;
		}
		long now = player.level().getGameTime();
		if (Raven.ascended(player) && !s.meditating) {
			m.rightArm.zRot = 0.45f + breathe * 0.05f;
			m.leftArm.zRot = -0.45f - breathe * 0.05f;
			m.rightArm.xRot = Math.min(m.rightArm.xRot, 0.05f);
			m.leftArm.xRot = Math.min(m.leftArm.xRot, 0.05f);
			posed = true;
		}
		if (com.projecthero.mod.client.raven.RavenFx.running(com.projecthero.mod.raven.network.RavenFxPayload.CHANNEL, player.getId(), now)) {
			m.rightArm.xRot = m.leftArm.xRot = -0.35f;
			m.rightArm.yRot = m.leftArm.yRot = 0f;
			m.rightArm.zRot = 1.05f + breathe * 0.05f;
			m.leftArm.zRot = -1.05f - breathe * 0.05f;
			posed = true;
		}
		if (com.projecthero.mod.client.raven.RavenFx.running(com.projecthero.mod.raven.network.RavenFxPayload.BEAM, player.getId(), now)) {
			m.rightArm.xRot = -Mth.HALF_PI + headPitch * Mth.DEG_TO_RAD;
			m.rightArm.yRot = m.head.yRot - 0.1f;
			m.rightArm.zRot = 0f;
			posed = true;
		}
		boolean guardUp = s.guardUntil > now && now - (s.guardUntil - com.projecthero.mod.raven.RavenConfig.GUARD_TICKS) < 12;
		if (guardUp || com.projecthero.mod.client.raven.RavenFx.running(com.projecthero.mod.raven.network.RavenFxPayload.CHARGE, player.getId(), now)) {
			m.rightArm.xRot = m.leftArm.xRot = -1.35f + headPitch * Mth.DEG_TO_RAD * 0.5f;
			m.rightArm.yRot = -0.35f;
			m.leftArm.yRot = 0.35f;
			m.rightArm.zRot = m.leftArm.zRot = 0f;
			posed = true;
		}
		if (posed) {
			m.leftPants.copyFrom(m.leftLeg);
			m.rightPants.copyFrom(m.rightLeg);
			m.jacket.copyFrom(m.body);
			m.leftSleeve.copyFrom(m.leftArm);
			m.rightSleeve.copyFrom(m.rightArm);
		}
	}
}

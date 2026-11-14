package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.raven.RavenPose;
import com.projecthero.mod.raven.Raven;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Raven poses, at the very end of {@code PlayerModel.setupAnim} so they win over every shared pose (the HumanoidModel ones run
 * inside the super call, before this). v0.17.4: the poses themselves are {@link RavenPose}'s -- the flight (no walking swing in the
 * air), meditation, the holds and every move and spell, eased in and out. A streak of shadow (Shadow Dash, sunk for Shadow Step)
 * hides the body (the visibility is reset by vanilla every frame, so this never leaks onto another player).
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
		if (Raven.inShadow(player)) {
			m.setAllVisible(false);
			return;
		}
		RavenPose.apply(player, m);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.hat.copyFrom(m.head);
	}
}

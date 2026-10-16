package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.mutation.ElasticityClient;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;

/** v0.16.5 Elasticity rework: a held item rides at the end of a stretched arm at its normal size. */
@Mixin(PlayerModel.class)
public abstract class ElasticityHandMixin {
	@Inject(method = "translateToHand", at = @At("TAIL"))
	private void projecthero$itemAtStretchedHand(HumanoidArm side, PoseStack pose, CallbackInfo ci) {
		PlayerModel<?> self = (PlayerModel<?>) (Object) this;
		ElasticityClient.unstretchHand(side == HumanoidArm.RIGHT ? self.rightArm : self.leftArm, pose);
	}
}

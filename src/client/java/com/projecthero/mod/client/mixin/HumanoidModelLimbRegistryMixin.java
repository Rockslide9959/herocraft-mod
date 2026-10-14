package com.projecthero.mod.client.mixin;

import java.util.function.Function;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.render.JointedLimbs;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** v0.16.2: every humanoid model's arms and legs are known to {@link JointedLimbs} (bendable while a pose asks). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelLimbRegistryMixin {
	@Shadow
	@Final
	public ModelPart rightArm;
	@Shadow
	@Final
	public ModelPart leftArm;
	@Shadow
	@Final
	public ModelPart rightLeg;
	@Shadow
	@Final
	public ModelPart leftLeg;

	@Inject(method = "<init>(Lnet/minecraft/client/model/geom/ModelPart;Ljava/util/function/Function;)V", at = @At("RETURN"))
	private void projecthero$registerLimbs(ModelPart root, Function<ResourceLocation, RenderType> renderType, CallbackInfo ci) {
		JointedLimbs.register(rightArm, JointedLimbs.RIGHT_ARM);
		JointedLimbs.register(leftArm, JointedLimbs.LEFT_ARM);
		JointedLimbs.register(rightLeg, JointedLimbs.RIGHT_LEG);
		JointedLimbs.register(leftLeg, JointedLimbs.LEFT_LEG);
	}

	/**
	 * v0.16.2: vanilla never resets the legs' x pivots (always +-1.9 for it), and the model is shared between players --
	 * the perch spreads the hips, so put them back before every player's animation.
	 */
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
	private void projecthero$resetHipX(net.minecraft.world.entity.LivingEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof net.minecraft.world.entity.player.Player) {
			rightLeg.x = -1.9f;
			leftLeg.x = 1.9f;
		}
	}
}

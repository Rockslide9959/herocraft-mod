package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.thor.BifrostTravelClient;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7: Bifrost Travel -- Stormbreaker raised to the sky: the axe arm straight up, the free arm out and down, the head
 * tipped back to the bridge, weighted by {@link BifrostTravelClient#raise} so it eases up, holds through the trip and comes
 * down as they land. The axe itself is turned to point skyward by {@code BifrostRaiseGripMixin}.
 */
@Mixin(HumanoidModel.class)
public abstract class BifrostRaisePoseMixin<T extends LivingEntity> {
	@Shadow
	@Final
	public ModelPart rightArm;
	@Shadow
	@Final
	public ModelPart leftArm;
	@Shadow
	@Final
	public ModelPart head;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$bifrostRaise(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		if (!(entity instanceof Player p)) {
			return;
		}
		float partial = ageInTicks - (float) Math.floor(ageInTicks);
		float w = BifrostTravelClient.raise(p, partial);
		if (w <= 0f) {
			return;
		}
		boolean right = p.getMainArm() == HumanoidArm.RIGHT;
		ModelPart up = right ? rightArm : leftArm;
		ModelPart out = right ? leftArm : rightArm;
		float side = right ? 1f : -1f;
		float tremble = Mth.sin(ageInTicks * 2.1f) * 0.025f;
		up.xRot = Mth.lerp(w, up.xRot, -Mth.PI + 0.1f + tremble);
		up.yRot = Mth.lerp(w, up.yRot, 0f);
		up.zRot = Mth.lerp(w, up.zRot, side * 0.1f);
		out.xRot = Mth.lerp(w, out.xRot, -0.15f);
		out.yRot = Mth.lerp(w, out.yRot, 0f);
		out.zRot = Mth.lerp(w, out.zRot, -side * 0.6f);
		head.xRot = Mth.lerp(w, head.xRot, -0.5f);
	}
}

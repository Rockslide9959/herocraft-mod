package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.thoracquire.MjolnirLiftClient;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7: the Mjolnir lift cutscene pose -- the hammer arm raised straight to the sky, the free arm thrown out and down, the
 * head tipped back to the storm, weighted by {@link MjolnirLiftClient#raise} so it eases up and back down.
 */
@Mixin(HumanoidModel.class)
public abstract class ThorAcquireLiftPoseMixin<T extends LivingEntity> {
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
	private void projecthero$liftPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		if (!(entity instanceof Player p)) {
			return;
		}
		float partial = ageInTicks - (float) Math.floor(ageInTicks);
		float w = MjolnirLiftClient.raise(MjolnirLiftClient.elapsed(p, partial));
		if (w <= 0f) {
			return;
		}
		boolean right = p.getMainArm() == HumanoidArm.RIGHT;
		ModelPart up = right ? rightArm : leftArm;
		ModelPart out = right ? leftArm : rightArm;
		float side = right ? 1f : -1f;
		float tremble = Mth.sin(ageInTicks * 2.3f) * 0.03f;
		up.xRot = Mth.lerp(w, up.xRot, -Mth.PI + 0.12f + tremble);
		up.yRot = Mth.lerp(w, up.yRot, 0f);
		up.zRot = Mth.lerp(w, up.zRot, side * 0.08f);
		out.xRot = Mth.lerp(w, out.xRot, -0.2f);
		out.yRot = Mth.lerp(w, out.yRot, 0f);
		out.zRot = Mth.lerp(w, out.zRot, -side * 0.75f);
		head.xRot = Mth.lerp(w, head.xRot, -0.55f);
	}
}

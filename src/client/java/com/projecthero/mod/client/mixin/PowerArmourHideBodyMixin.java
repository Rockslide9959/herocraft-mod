package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.powerarmour.SuitLooks;
import com.projecthero.mod.powerarmour.PowerArmour;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 Power Armour round 2: the wearer's own model (and overlay) is not drawn from the moment they step into the open frame
 * until they have stepped out -- third person and, because {@code renderHand} runs {@code setupAnim} too, the first-person
 * hand. The suit is a scaled-up player with its plates inside the box, so the body would poke through it; {@code PowerArmourLayer}
 * draws the body inside the suit instead while the suit is open. Vanilla's {@code setModelProperties} turns every part back on
 * each frame.
 */
@Mixin(PlayerModel.class)
public abstract class PowerArmourHideBodyMixin<T extends LivingEntity> {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$powerArmourHideBody(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player && SuitLooks.hidesBody(PowerArmour.state(player))) {
			((PlayerModel<?>) (Object) this).setAllVisible(false);
		}
	}
}

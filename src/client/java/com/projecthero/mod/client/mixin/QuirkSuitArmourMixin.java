package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.quirk.item.QuirkSuitItem;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.7: a Quirk hero suit piece has no vanilla armour model -- it is drawn as the costume skin on the parts it
 * covers ({@code QuirkCostumeLayer}) -- so the armour layer skips it, slot by slot (other armour still draws).
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class QuirkSuitArmourMixin {
	@Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$skipQuirkSuit(PoseStack pose, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot, int light,
			HumanoidModel<?> model, CallbackInfo ci) {
		if (entity.getItemBySlot(slot).getItem() instanceof QuirkSuitItem) {
			ci.cancel();
		}
	}
}

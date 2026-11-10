package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.client.andy.AndyRender;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** v0.17.3: worn armour is not drawn on a part Andy doesn't have, nor a shoulder pad torn off with an arm ({@code AndyArmour}). */
@Mixin(HumanoidArmorLayer.class)
public abstract class AndyArmorPartsMixin {
	@Unique
	private static final ThreadLocal<LivingEntity> PROJECTHERO$WEARER = new ThreadLocal<>();

	@Inject(method = "renderArmorPiece", at = @At("HEAD"))
	private void projecthero$andyWearer(PoseStack pose, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot, int light,
			HumanoidModel<?> model, CallbackInfo ci) {
		PROJECTHERO$WEARER.set(entity);
	}

	@Inject(method = "setPartVisibility", at = @At("TAIL"))
	private void projecthero$andyHideArmour(HumanoidModel<?> model, EquipmentSlot slot, CallbackInfo ci) {
		if (!(PROJECTHERO$WEARER.get() instanceof Player p)) {
			return;
		}
		AndyState s = Andy.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		for (int i = 0; i < AndyState.PARTS; i++) {
			if (s.broken(i)) {
				AndyRender.part(model, i).visible = false;
				if (i == AndyState.HEAD) {
					model.hat.visible = false;
				}
			}
		}
		// v0.17.3 (user: "if an arm gets destroyed that shoulder pad doesnt grow back"): a torn-off pad is not drawn
		if (slot == EquipmentSlot.CHEST) {
			int pads = com.projecthero.mod.andy.AndyArmour.wornLostPads(p);
			if ((pads & com.projecthero.mod.andy.AndyArmour.PAD_RIGHT) != 0) {
				model.rightArm.visible = false;
			}
			if ((pads & com.projecthero.mod.andy.AndyArmour.PAD_LEFT) != 0) {
				model.leftArm.visible = false;
			}
		}
	}
}

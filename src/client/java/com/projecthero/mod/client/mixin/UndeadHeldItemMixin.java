package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** v0.17.3: nothing is held in a hand Undead doesn't have (third person). */
@Mixin(ItemInHandLayer.class)
public abstract class UndeadHeldItemMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$undeadNoHand(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		int part = arm == HumanoidArm.RIGHT ? UndeadState.RIGHT_ARM : UndeadState.LEFT_ARM;
		if (entity instanceof Player p && Undead.missing(p, part)) {
			ci.cancel();
			return;
		}
		// v0.17.3: the Bone Sword is what the sword hand holds (and the hold-V struggle's hand is busy)
		UndeadState s = entity instanceof Player p ? Undead.peek(p) : null;
		if (s != null && s.hasPower && (s.boneSword() && com.projecthero.mod.undead.UndeadBoneSword.hand(s) == part
				|| s.animId == UndeadState.ANIM_SWORD_STRUGGLE)) {
			ci.cancel();
		}
	}
}

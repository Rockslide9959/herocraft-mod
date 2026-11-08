package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3: first person, a hand Andy doesn't have shows nothing -- no item and no arm -- until it starts to regrow (an empty
 * regrowing hand is then drawn re-forming by {@link AndyFirstPersonArmMixin}).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class AndyFirstPersonItemMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$andyNoHand(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack,
			float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		AndyState s = Andy.peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		int part = side == HumanoidArm.RIGHT ? AndyState.RIGHT_ARM : AndyState.LEFT_ARM;
		if (!s.broken(part)) {
			return;
		}
		float p = s.regrowth(part, player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (p <= 0f || !stack.isEmpty() || hand != InteractionHand.MAIN_HAND) {
			ci.cancel();
		}
	}
}

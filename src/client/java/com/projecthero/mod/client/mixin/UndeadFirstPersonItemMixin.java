package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3: first person, a hand Undead doesn't have shows nothing -- no item and no arm -- until it starts to regrow (an empty
 * regrowing hand is then drawn re-forming by {@link UndeadFirstPersonArmMixin}).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class UndeadFirstPersonItemMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$undeadNoHand(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack,
			float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		UndeadState s = Undead.peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		if (com.projecthero.mod.client.undead.UndeadFirstPerson.arms((ItemInHandRenderer) (Object) this, player, partial, hand, pose, buffers, light)) {
			ci.cancel();
			return;
		}
		HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		int part = side == HumanoidArm.RIGHT ? UndeadState.RIGHT_ARM : UndeadState.LEFT_ARM;
		if (!s.broken(part)) {
			return;
		}
		float p = s.regrowth(part, player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (p <= 0f || !stack.isEmpty() || hand != InteractionHand.MAIN_HAND) {
			ci.cancel();
		}
	}
}

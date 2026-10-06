package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.projecthero.mod.hulk.gladiator.GladiatorSwing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * v0.15.5: Gladiator Hulk's left-click swings alternate hands -- hammer, axe, hammer... ({@link GladiatorSwing}). The
 * swing packet carries the hand, so every viewer sees the same arm swing; the hit itself is vanilla's.
 */
@Mixin(Minecraft.class)
public abstract class GladiatorAttackMixin {
	@ModifyArg(method = "startAttack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
	private InteractionHand projecthero$gladiatorAlternateHand(InteractionHand hand) {
		LocalPlayer p = Minecraft.getInstance().player;
		return p == null ? hand : GladiatorSwing.next(p, hand);
	}
}

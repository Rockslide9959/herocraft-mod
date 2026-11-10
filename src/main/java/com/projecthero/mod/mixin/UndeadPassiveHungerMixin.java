package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.undead.Undead;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 (user: "a player with this power should lose hunger 50% slower"): Undead's body runs on less -- all the food exhaustion
 * he takes (running, jumping, fighting, healing, his moves) is halved. His regrowth is paid separately, as flat food costs.
 */
@Mixin(Player.class)
public abstract class UndeadPassiveHungerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float projecthero$undeadHungerSlower(float exhaustion) {
		if (exhaustion > 0f && (Object) this instanceof ServerPlayer sp && Undead.hasPower(sp)) {
			return exhaustion * 0.5f;
		}
		return exhaustion;
	}
}

package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.ironman.PlatformFacingLock;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * v0.15.3: while a Suit Platform's arms are putting the suit on / taking it off, mouse look can't turn the local player
 * left or right ({@link PlatformFacingLock}) -- they stay facing away from the platform. Looking up and down still works.
 */
@Mixin(Entity.class)
public abstract class EntityTurnLockMixin {
	@ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double projecthero$platformFacingLock(double yRot) {
		if (PlatformFacingLock.locked() && (Object) this == Minecraft.getInstance().player) {
			return 0.0;
		}
		return yRot;
	}
}

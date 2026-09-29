package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * v0.13.19: vanilla's creative-mode hotbar save / load activators (default C and X) share keys with Moon Knight's
 * Alters (C) and Cape (X). While a Moon Knight is transformed his keys take priority: the two activators read as
 * "not held" in {@code handleKeybinds}, so pressing a number key never saves or loads a hotbar underneath an ability.
 * Every other time vanilla behaves exactly as before.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftHotbarKeysMixin {
	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
	private boolean projecthero$moonKnightOwnsHotbarKeys(KeyMapping key, Operation<Boolean> original) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player != null && (key == mc.options.keySaveHotbarActivator || key == mc.options.keyLoadHotbarActivator)
				&& com.projecthero.mod.moonknight.MoonKnight.isTransformed(mc.player)) {
			return false;
		}
		return original.call(key);
	}
}

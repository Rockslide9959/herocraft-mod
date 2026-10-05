package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.ironman.furnace.StarkFurnaceBlockEntity;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** v0.14.26: the Stark Furnace / Smelter / Smoker cook every item in their own fixed time, whatever the recipe says. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityStarkMixin {
	@Inject(method = "getTotalCookTime", at = @At("RETURN"), cancellable = true)
	private static void projecthero$starkCookTime(Level level, AbstractFurnaceBlockEntity furnace, CallbackInfoReturnable<Integer> cir) {
		if (furnace instanceof StarkFurnaceBlockEntity stark) {
			cir.setReturnValue(stark.cookTicks());
		}
	}
}

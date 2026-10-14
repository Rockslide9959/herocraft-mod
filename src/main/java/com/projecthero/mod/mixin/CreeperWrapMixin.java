package com.projecthero.mod.mixin;

import com.projecthero.mod.spider.SpiderWrap;

import net.minecraft.world.entity.monster.Creeper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.16.1: a creeper cocooned by Spider-Man's Web Shot ({@link SpiderWrap}) can never explode -- not even one already lit
 * with flint and steel (its swell is also held at rest every tick by {@code SpiderWrap}).
 */
@Mixin(Creeper.class)
public abstract class CreeperWrapMixin {
	@Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
	private void projecthero$cocoonedNeverExplodes(CallbackInfo ci) {
		if (SpiderWrap.isWrapped((Creeper) (Object) this)) {
			ci.cancel();
		}
	}
}

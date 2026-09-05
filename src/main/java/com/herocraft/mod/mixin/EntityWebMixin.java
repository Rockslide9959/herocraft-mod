package com.herocraft.mod.mixin;

import com.herocraft.mod.spider.SpiderWebs;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spider-Man passes through webbing. Cobweb normally clamps an entity to a crawl by rewriting its
 * velocity through {@code makeStuckInBlock}; for him that call simply does not happen, so his own Web
 * Nets are footing and cover rather than a trap he has to get out of, and naturally-occurring cobweb
 * in a mineshaft stops slowing him down too.
 *
 * <p>Deliberately narrow: only cobweb, only a player with the power. Every other entity -- including
 * other players -- is stuck by his nets exactly as they would be by any other web, which is what
 * makes Web Net a usable trap.
 */
@Mixin(Entity.class)
public abstract class EntityWebMixin {
	@Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
	private void herocraft$spiderIgnoresWebbing(BlockState state, Vec3 motionMultiplier, CallbackInfo ci) {
		if (state.is(Blocks.COBWEB) && SpiderWebs.movesFreelyThroughWebbing((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}

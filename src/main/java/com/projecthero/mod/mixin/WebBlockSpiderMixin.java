package com.projecthero.mod.mixin;

import com.projecthero.mod.spider.SpiderCobwebs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.16.1: Spider-Man's own cobwebs (Web Bomb / Impact Web / Web Blossom) never hold him or his squad -- decided on both
 * sides from {@link SpiderCobwebs} (the server's ledger, the client's synced copy), so a squadmate's movement never
 * rubber-bands. Everyone else is caught exactly as by any cobweb.
 */
@Mixin(WebBlock.class)
public abstract class WebBlockSpiderMixin {
	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void projecthero$spiderCobwebAllies(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
		if (SpiderCobwebs.exempt(level, pos, entity)) {
			ci.cancel();
		}
	}
}

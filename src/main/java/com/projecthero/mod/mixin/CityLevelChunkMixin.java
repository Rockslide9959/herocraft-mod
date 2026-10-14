package com.projecthero.mod.mixin;

import com.projecthero.mod.city.CityDamageTracker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.16.2 City part B: every block change in a loaded (full) chunk is offered to the City damage tracker. Outside a
 * City this is one hash lookup ({@code CityLocator.isCityChunk}); world generation writes proto-chunks and never gets
 * here.
 */
@Mixin(LevelChunk.class)
public abstract class CityLevelChunkMixin {
	@Shadow
	@Final
	Level level;

	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void projecthero$cityBlockChanged(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
		BlockState old = cir.getReturnValue();
		if (old != null) {
			CityDamageTracker.onBlockChanged(level, pos, old, state);
		}
	}
}

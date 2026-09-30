package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.moonknight.ability.MoonKnightAlters;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.14.4: Moon Knight's Steven Grant mines with Fortune III. Every player block break ends in
 * {@code Block.getDrops(state, level, pos, blockEntity, miner, tool)}; the tool handed to the loot table is swapped
 * for {@link MoonKnightAlters#fortuneTool} (a copy with Fortune raised to at least III -- the higher level wins, so it
 * never stacks with a real Fortune tool). Anyone who isn't a suited Steven gets his own tool back untouched.
 */
@Mixin(Block.class)
public abstract class MoonKnightStevenFortuneMixin {
	@ModifyVariable(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
			at = @At("HEAD"), argsOnly = true)
	private static ItemStack projecthero$stevenFortune(ItemStack tool, BlockState state, ServerLevel level, BlockPos pos,
			BlockEntity blockEntity, Entity miner) {
		return MoonKnightAlters.fortuneTool(miner, tool, level);
	}
}

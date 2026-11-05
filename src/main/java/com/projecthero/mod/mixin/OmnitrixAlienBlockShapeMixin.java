package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.2 Omnitrix framework (Wildvine; user: "Make wildvine able to walk through nature blocks like wood, leaves and stuff please"):
 * {@link AlienKit#passesBlock}, the block-by-block version of {@link AlienKit#phasing} -- a block the alien's body passes has no
 * collision shape FOR THAT PLAYER, on whichever side asks (the owner's client simulates, the server replays). Only a collision query
 * that names a player gets past the first line; rays with no entity and mobs are untouched.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class OmnitrixAlienBlockShapeMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"), cancellable = true)
	private void projecthero$alienPassesBlock(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (!(context instanceof EntityCollisionContext ec) || !(ec.getEntity() instanceof Player p)) {
			return;
		}
		VoxelShape shape = cir.getReturnValue();
		if (shape.isEmpty()) {
			return;
		}
		AlienKit kit = AlienKit.activeKit(p);
		if (kit != null && kit.passesBlock(p, (BlockState) (Object) this, level, pos, shape)) {
			cir.setReturnValue(Shapes.empty());
		}
	}
}

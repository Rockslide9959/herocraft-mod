package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.p22.PlantManipulationHandlers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * "Swing through grass" for Chlorokinesis (Power 22) players only -- v0.11.9, explicit user request
 * ("make chlorokinesis players able to swing through grass if the mod swing through grass isn't in the
 * mod, make this a feature that players with this power get and dont make it conflict with any mods
 * that enable this feature for players normally"). This mod had no such feature anywhere already
 * (confirmed before adding this).
 *
 * <p>Without a dedicated fix, vanilla's crosshair targeting ({@code Entity#pick}, which every attack and
 * block/entity interaction goes through) raycasts against a block's ordinary {@code OUTLINE} shape --
 * the same shape used to render its selection box -- even for decorative, non-collidable plants like
 * short grass or ferns. That shape is non-empty even though the block has no actual collision, so aiming
 * through a tuft of grass makes the game target (and, on a left-click, break) the grass instead of
 * whatever is behind it. {@link ClipContext#getBlockShape} is the one choke point every block-shape
 * lookup along that ray passes through, and it already carries the raycasting entity (via
 * {@link ClipContext}'s {@link CollisionContext}, when built through the entity-aware constructor
 * {@code Entity#pick} uses) -- so this can be gated per-player without touching the block's shape for
 * anyone else, unlike a mixin on the block class itself (whose {@code getShape} has no caller identity to
 * check at all). Grass having an empty shape here converges it with the {@code COLLIDER} shape grass
 * already has for every player (movement was never blocked by it), so this is inherently a narrowing,
 * not a new capability -- and because it only ever fires for a Chlorokinesis-flagged entity, it cannot
 * fight with a separate general-purpose "swing through grass" mod (which would apply to a different,
 * global code path) or double-apply visibly (an already-empty shape overridden again is still empty).
 */
@Mixin(ClipContext.class)
public abstract class ClipContextMixin {
	@Shadow
	private CollisionContext collisionContext;

	@Inject(method = "getBlockShape", at = @At("HEAD"), cancellable = true)
	private void projecthero$swingThroughGrassForChlorokinesis(BlockState state, BlockGetter level, BlockPos pos,
			CallbackInfoReturnable<VoxelShape> cir) {
		if (!isGrassFamily(state) || !(this.collisionContext instanceof EntityCollisionContext ecc)) {
			return;
		}
		Entity entity = ecc.getEntity();
		if (!(entity instanceof Player player) || !PlantManipulationHandlers.isActiveFor(player)) {
			return;
		}
		cir.setReturnValue(Shapes.empty());
	}

	private static boolean isGrassFamily(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS)
				|| state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH);
	}
}

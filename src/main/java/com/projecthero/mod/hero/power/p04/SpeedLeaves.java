package com.projecthero.mod.hero.power.p04;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * v0.14.21: a speedster in Speed Mode or Overdrive runs straight through leaves, so forests stop snagging the run.
 *
 * <p>Leaves stay solid from ABOVE: a leaf block only lets the player through when their feet are below its top face
 * (the same "is the entity above this shape" test vanilla uses for scaffolding / powder snow). So you can still land
 * on, stand on and run across a canopy, but anything at body height -- or above you when you jump -- is passable.
 * Leaves are never broken.
 *
 * <p>Read from the synced {@code EXPERIMENTAL_STATE} attachment, so the owner's client (which simulates the movement)
 * and the server (which checks it) agree. Hooked in through {@code SuperSpeedLeavesMixin} on
 * {@code BlockStateBase#getCollisionShape(BlockGetter, BlockPos, CollisionContext)}.
 */
public final class SpeedLeaves {
	private SpeedLeaves() {
	}

	/** Whether {@code entity} is a player with Super Speed's Speed Mode or Overdrive running (either side). */
	public static boolean active(Entity entity) {
		if (!(entity instanceof Player player) || player.isSpectator()) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(SuperSpeedHandlers.KEY)) {
			return false;
		}
		if (st.activeToggles.contains(SuperSpeedHandlers.KEY + "/speed_mode")) {
			return true;
		}
		Float until = st.resources.get(SuperSpeedHandlers.KEY + "/" + SuperSpeedHandlers.OVERDRIVE_UNTIL);
		return until != null && until > player.level().getGameTime();
	}

	/**
	 * Whether the leaf block {@code state} at {@code pos} has no collision for {@code context}: the colliding entity is a
	 * speedster in Speed Mode / Overdrive whose feet are below the block's top (not standing on it).
	 */
	public static boolean passThrough(BlockState state, BlockPos pos, CollisionContext context) {
		return context instanceof EntityCollisionContext ec && ec.getEntity() instanceof Player
				&& state.is(BlockTags.LEAVES) && !context.isAbove(Shapes.block(), pos, false) && active(ec.getEntity());
	}
}

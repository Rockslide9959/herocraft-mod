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
 * v0.14.21: a speedster in Speed Mode or Overdrive runs straight through leaves, so forests stop snagging the run (v0.17.1:
 * the Speed Force Speedster in Speed Mode too).
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
		// v0.17.1 (user): the Speed Force Speedster runs through leaves too, in Speed Mode
		if (com.projecthero.mod.speedforce.SpeedForceSpeedster.speedActive(player)) {
			return true;
		}
		// v0.17.2 (user: "make xlr8 run through leaves like the other speedsters"): XLR8 in Speed Mode (the synced Omnitrix state)
		if (com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit.is(player)
				&& (com.projecthero.mod.omnitrix.Omnitrix.state(player).kitFlags & com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit.F_SPEED) != 0) {
			return true;
		}
		// v0.17.2 round 3 (user: "when in ball form player can roll through leaves like a speedster")
		if (com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltKit.rolling(player)) {
			return true;
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

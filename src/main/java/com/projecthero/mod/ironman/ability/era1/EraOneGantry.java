package com.projecthero.mod.ironman.ability.era1;

import com.projecthero.mod.ironman.gantry.StarkGantry;
import com.projecthero.mod.ironman.gantry.StarkGantryFloorBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * v0.16.7 (user) ERA I -- the Gantry rules (plan: {@code docs/IRONMAN_ERA1_PLAN.md}, "The Gantry rules"):
 * <ul>
 *   <li>an Era I suit goes on and comes off ONLY in a Stark Gantry (H on its floor, or a Suit Crate used on it); every
 *       other path -- the C hand build / store, calls, the glasses picker, the bracelets, Protocol Phoenix, commands --
 *       refuses it with {@link #refuse} ("Era I suits suit up in a Stark Gantry");</li>
 *   <li>it is repaired ONLY in a Gantry: racked on a Suit Platform within {@value StarkGantry#RANGE} blocks of a complete
 *       floor ({@link #platformInRange}), or worn standing on the floor ({@link #onGantryFloor});</li>
 *   <li>its reactor charges +50/s there (IronManEnergy), slowly anywhere else.</li>
 * </ul>
 * The one way out of an Era I suit away from a Gantry is the emergency eject ({@link EraOneEject}) into a Suit Crate.
 */
public final class EraOneGantry {
	private EraOneGantry() {
	}

	/** Is the player standing on a complete Stark Gantry floor? (Both sides: reads blocks only.) */
	public static boolean onGantryFloor(Player player) {
		return StarkGantry.centreUnder(player) != null;
	}

	/**
	 * Is the Suit Platform at {@code platform} within range of a complete Stark Gantry floor? Scans the loaded floor tiles
	 * near it (each 5 x 5 floor is found through its tiles' block entities). Callers cache it (the platform re-checks once
	 * a second).
	 */
	public static boolean platformInRange(Level level, BlockPos platform) {
		if (level == null || platform == null) {
			return false;
		}
		int reach = StarkGantry.RANGE + StarkGantry.HALF;
		int cx0 = (platform.getX() - reach) >> 4;
		int cx1 = (platform.getX() + reach) >> 4;
		int cz0 = (platform.getZ() - reach) >> 4;
		int cz1 = (platform.getZ() + reach) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity e : chunk.getBlockEntities().values()) {
					if (e instanceof StarkGantryFloorBlockEntity && !e.isRemoved() && StarkGantry.inRange(e.getBlockPos(), platform)
							&& StarkGantry.complete(level, e.getBlockPos())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The wrapped refusal every non-Gantry way on / off an Era I suit gives. */
	public static void refuse(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.era1.gantry_only")
				.withStyle(ChatFormatting.GOLD), true);
	}

	/** True (and the refusal shown) when {@code suitId} is an Era I suit -- for the non-Gantry suit-up / removal paths. */
	public static boolean refuses(ServerPlayer player, String suitId) {
		if (IronManEraOne.isEraOne(suitId)) {
			refuse(player);
			return true;
		}
		return false;
	}
}

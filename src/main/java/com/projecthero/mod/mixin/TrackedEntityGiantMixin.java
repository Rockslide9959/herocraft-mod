package com.projecthero.mod.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.projecthero.mod.entity.GiantTracking;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.4: {@link GiantTracking.AlwaysTracked} giants (Galactus, the Worldship) are paired with every player of their
 * level within their own tracking range: vanilla also caps the range at the player's view distance and needs the
 * entity's chunk to be in the player's chunk view, so at render distance 2-4 the Devourer (50-120 blocks out) was never
 * sent. The rest of the decision (same level, {@code broadcastToPlayer}, the horizontal distance check) stays vanilla.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityGiantMixin implements GiantTracking.Tracked {
	@Shadow
	@Final
	Entity entity;

	@Shadow
	@Final
	private Set<ServerPlayerConnection> seenBy;

	@Shadow
	public abstract void updatePlayer(ServerPlayer player);

	/** {@code min(effectiveRange, viewDistance * 16)}: a giant uses its own tracking range alone. */
	@WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
	private int projecthero$giantRange(int effectiveRange, int viewBlocks, Operation<Integer> original) {
		if (entity instanceof GiantTracking.AlwaysTracked) {
			return effectiveRange;
		}
		return original.call(effectiveRange, viewBlocks);
	}

	/** A giant is sent even when its chunk is outside the player's chunk view. */
	@WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
	private boolean projecthero$giantChunk(ChunkMap map, ServerPlayer player, int x, int z, Operation<Boolean> original) {
		if (entity instanceof GiantTracking.AlwaysTracked) {
			return true;
		}
		return original.call(map, player, x, z);
	}

	@Override
	public boolean projecthero$sees(ServerPlayer player) {
		return seenBy.contains(player.connection);
	}

	@Override
	public void projecthero$updatePlayer(ServerPlayer player) {
		updatePlayer(player);
	}
}

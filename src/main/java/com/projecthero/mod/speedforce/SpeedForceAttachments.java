package com.projecthero.mod.speedforce;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.speedforce.data.SpeedForceState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

/** v0.17.1: the Speed Force Speedster's attachment, kept beside the power. Loaded from {@link SpeedForceMod#initialize}. */
public final class SpeedForceAttachments {
	/** The power, the momentum, the Speed Force, the move timers. Persistent, kept on death, synced to all. */
	public static final AttachmentType<SpeedForceState> STATE = AttachmentRegistry.create(ProjectHeroMod.id("speed_force_state"),
			builder -> builder.persistent(SpeedForceState.CODEC)
					.copyOnDeath()
					.initializer(SpeedForceState::new)
					.syncWith(ByteBufCodecs.fromCodec(SpeedForceState.CODEC), AttachmentSyncPredicate.all()));

	/** v0.17.1: a trip through the Speed Force dimension while it lasts (persistent, server-only). */
	public static final AttachmentType<com.projecthero.mod.speedforce.data.SpeedForceTrip> TRIP = AttachmentRegistry.create(
			ProjectHeroMod.id("speed_force_trip"),
			builder -> builder.persistent(com.projecthero.mod.speedforce.data.SpeedForceTrip.CODEC));

	/**
	 * v0.17.1 (user, Speed Force Memories): the marked places and the exit history -- persistent (they survive a logout),
	 * kept on death, synced to the owner only (the Shift+N menu lists the marked places).
	 */
	public static final AttachmentType<com.projecthero.mod.speedforce.data.SpeedForceMemories> MEMORIES = AttachmentRegistry.create(
			ProjectHeroMod.id("speed_force_memories"),
			builder -> builder.persistent(com.projecthero.mod.speedforce.data.SpeedForceMemories.CODEC)
					.copyOnDeath()
					.initializer(() -> com.projecthero.mod.speedforce.data.SpeedForceMemories.EMPTY)
					.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.speedforce.data.SpeedForceMemories.CODEC), AttachmentSyncPredicate.targetOnly()));

	private SpeedForceAttachments() {
	}

	static void init() {
	}
}

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

	/**
	 * v0.17.1 (user: "if the black flash kills the player they lose their powers for 20 minutes"): ticks left without the
	 * Speed Force (absent = none). Persistent, kept on death (it starts AT a death), synced to all (clients hide the run,
	 * the HUD and the keys while it lasts). Counted down while the player is online.
	 */
	public static final AttachmentType<Integer> POWER_LOST = AttachmentRegistry.create(ProjectHeroMod.id("speed_force_power_lost"),
			builder -> builder.persistent(com.mojang.serialization.Codec.INT)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/**
	 * v0.17.1 (user, lightning charge-up): the static the run has built, 0..{@code STATIC_MAX} (absent = none). Not saved
	 * (it is the run's), synced to the owner only (the HUD's pip).
	 */
	public static final AttachmentType<Integer> STATIC_CHARGE = AttachmentRegistry.create(ProjectHeroMod.id("speed_force_static"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** v0.17.1 (user, race ghosts): the race course and its best times. Persistent, kept on death, synced to the owner. */
	public static final AttachmentType<com.projecthero.mod.speedforce.data.SpeedForceRace> RACE = AttachmentRegistry.create(
			ProjectHeroMod.id("speed_force_race"),
			builder -> builder.persistent(com.projecthero.mod.speedforce.data.SpeedForceRace.CODEC)
					.copyOnDeath()
					.initializer(() -> com.projecthero.mod.speedforce.data.SpeedForceRace.EMPTY)
					.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.speedforce.data.SpeedForceRace.CODEC), AttachmentSyncPredicate.targetOnly()));

	/** v0.17.1: the best race run's track, for its ghost. Persistent, kept on death, server-only. */
	public static final AttachmentType<com.projecthero.mod.speedforce.data.SpeedForceRace.Ghost> RACE_GHOST = AttachmentRegistry.create(
			ProjectHeroMod.id("speed_force_race_ghost"),
			builder -> builder.persistent(com.projecthero.mod.speedforce.data.SpeedForceRace.Ghost.CODEC).copyOnDeath());

	private SpeedForceAttachments() {
	}

	static void init() {
	}
}

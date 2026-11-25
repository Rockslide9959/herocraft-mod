package com.projecthero.mod.thoracquire;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

/**
 * v0.17.7 Thor acquisition: the per-player data. Kept out of {@code ModAttachments} (touched by nearly every branch) so this
 * feature merges on its own.
 */
public final class ThorAcquireAttachments {
	/** Valor, 0..{@link Valor#MAX}: persistent, kept through death, synced to the owner (HUD bar, Guidebook, tooltip). */
	public static final AttachmentType<Integer> VALOR = AttachmentRegistry.create(ProjectHeroMod.id("valor"),
			builder -> builder.persistent(Codec.INT).copyOnDeath().initializer(() -> 0)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** Overworld game time before which saving a villager earns nothing more (anti-farm). Persistent, kept through death. */
	public static final AttachmentType<Long> VALOR_SAVE_READY = AttachmentRegistry.create(ProjectHeroMod.id("valor_save_ready"),
			builder -> builder.persistent(Codec.LONG).copyOnDeath().initializer(() -> 0L));

	/**
	 * The Mjolnir lift cutscene clock: the game time the lift started, or 0. Synced to everyone (they all see the arm rise
	 * and the lightning), never saved.
	 */
	public static final AttachmentType<Long> MJOLNIR_LIFT = AttachmentRegistry.create(ProjectHeroMod.id("mjolnir_lift"),
			builder -> builder.initializer(() -> 0L).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Game time before which the "Valor is earned by the unpowered" hint stays quiet. Not saved: once per session at most often. */
	public static final AttachmentType<Long> VALOR_HINT_READY = AttachmentRegistry.create(ProjectHeroMod.id("valor_hint_ready"),
			builder -> builder.initializer(() -> 0L));

	/** Game time before which the hammer's refusal (message + clunk + shake) stays quiet, so spam-clicking doesn't spam. */
	public static final AttachmentType<Long> LIFT_FEEDBACK_READY = AttachmentRegistry.create(ProjectHeroMod.id("lift_feedback_ready"),
			builder -> builder.initializer(() -> 0L));

	/** Trial of Asgard won: the next storm lets this player lift the hammer without fighting again. Persistent, kept through death. */
	public static final AttachmentType<Boolean> TRIAL_PASSED = AttachmentRegistry.create(ProjectHeroMod.id("trial_passed"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath().initializer(() -> false));

	/** The storm number ({@link Storms#current}) in which this player last lost the trial, or -1. One attempt per storm. */
	public static final AttachmentType<Long> TRIAL_FAILED_STORM = AttachmentRegistry.create(ProjectHeroMod.id("trial_failed_storm"),
			builder -> builder.persistent(Codec.LONG).copyOnDeath().initializer(() -> -1L));

	private ThorAcquireAttachments() {
	}

	/** Forces the class to load (registration happens in the static initialisers). */
	public static void initialize() {
	}
}

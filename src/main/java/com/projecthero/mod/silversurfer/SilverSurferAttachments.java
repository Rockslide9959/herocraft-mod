package com.projecthero.mod.silversurfer;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

/**
 * v0.17.1: the Silver Surfer's attachment, kept beside the power (not in {@code ModAttachments}) so the whole power is
 * self-contained. Loaded from {@link SilverSurferMod#initialize} -- before any player can carry it.
 */
public final class SilverSurferAttachments {
	/** The power, the Power Cosmic, the board, the move timers and the visible effects. Persistent, kept on death, synced to all. */
	public static final AttachmentType<SilverSurferState> STATE = AttachmentRegistry.create(ProjectHeroMod.id("silver_surfer_state"),
			builder -> builder.persistent(SilverSurferState.CODEC)
					.copyOnDeath()
					.initializer(SilverSurferState::new)
					.syncWith(ByteBufCodecs.fromCodec(SilverSurferState.CODEC), AttachmentSyncPredicate.all()));

	private SilverSurferAttachments() {
	}

	/** Forces the class (and so the registration) to load. */
	static void init() {
	}
}

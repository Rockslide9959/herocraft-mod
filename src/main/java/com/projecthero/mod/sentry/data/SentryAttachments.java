package com.projecthero.mod.sentry.data;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

/**
 * v0.17.5 Sentry's attachment, kept in the power's own package (registered when {@code SentryMod.initialize} touches this class), the
 * Vision / Doctor Fate pattern, so parallel builds don't all edit {@code ModAttachments}.
 */
public final class SentryAttachments {
	/** The power, both meters, the form, flight, the running moves and the takeover. Persistent, kept on death, synced to everyone. */
	public static final AttachmentType<SentryState> STATE = AttachmentRegistry.create(ProjectHeroMod.id("sentry_state"),
			builder -> builder.persistent(SentryState.CODEC)
					.copyOnDeath()
					.initializer(SentryState::new)
					.syncWith(ByteBufCodecs.fromCodec(SentryState.CODEC), AttachmentSyncPredicate.all()));

	private SentryAttachments() {
	}

	public static void initialize() {
		// loading the class registers the attachment
	}
}

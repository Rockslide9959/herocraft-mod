package com.projecthero.mod.doctorfate.data;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

/**
 * v0.17.3 Doctor Fate's attachment, kept in the power's own package (registered when {@code DoctorFateMod.initialize} touches this
 * class) so the parallel builds don't all edit {@code ModAttachments}.
 */
public final class FateAttachments {
	/** The power, the helmet, Dominion, Possession, the bargains, timers. Persistent, kept on death, synced to everyone. */
	public static final AttachmentType<FateState> STATE = AttachmentRegistry.create(ProjectHeroMod.id("doctor_fate_state"),
			builder -> builder.persistent(FateState.CODEC)
					.copyOnDeath()
					.initializer(FateState::new)
					.syncWith(ByteBufCodecs.fromCodec(FateState.CODEC), AttachmentSyncPredicate.all()));

	private FateAttachments() {
	}

	public static void initialize() {
		// loading the class registers the attachment
	}
}

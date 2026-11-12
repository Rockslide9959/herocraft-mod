package com.projecthero.mod.vision.data;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

/**
 * v0.17.3 Vision's attachment, kept in the power's own package (registered when {@code VisionMod.initialize} touches this class) so
 * the parallel builds don't all edit {@code ModAttachments} (Doctor Fate's {@code FateAttachments} pattern).
 */
public final class VisionAttachments {
	/** The power, the Mind Stone bar, flight, the beams, the toggles, timers. Persistent, kept on death, synced to everyone. */
	public static final AttachmentType<VisionState> STATE = AttachmentRegistry.create(ProjectHeroMod.id("vision_state"),
			builder -> builder.persistent(VisionState.CODEC)
					.copyOnDeath()
					.initializer(VisionState::new)
					.syncWith(ByteBufCodecs.fromCodec(VisionState.CODEC), AttachmentSyncPredicate.all()));

	private VisionAttachments() {
	}

	public static void initialize() {
		// loading the class registers the attachment
	}
}

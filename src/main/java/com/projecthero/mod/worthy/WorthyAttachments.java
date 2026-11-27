package com.projecthero.mod.worthy;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

/**
 * v0.17.7 Thor x Super Soldier: the kit's attachment, kept in its own package (registered when {@code WorthyMod.initialize} touches
 * this class) so this build does not edit {@code ModAttachments}. Persistent and synced to everyone; not kept on death (a death
 * ends every loan of the dead Super Soldier anyway, and drops him out of the sky).
 */
public final class WorthyAttachments {
	public static final AttachmentType<WorthyState> STATE = AttachmentRegistry.create(ProjectHeroMod.id("worthy_state"),
			builder -> builder.persistent(WorthyState.CODEC)
					.initializer(WorthyState::new)
					.syncWith(ByteBufCodecs.fromCodec(WorthyState.CODEC), AttachmentSyncPredicate.all()));

	private WorthyAttachments() {
	}

	public static void initialize() {
		// loading the class registers the attachment
	}
}

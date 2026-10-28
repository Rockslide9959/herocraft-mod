package com.projecthero.mod.omnitrix;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

/** v0.17.1: the Omnitrix's attachments. Loaded from {@link OmnitrixMod#initialize}. */
public final class OmnitrixAttachments {
	/** The watch, the alien, the timer, the recharge, the cooldowns. Persistent, KEPT ON DEATH, synced to all. */
	public static final AttachmentType<OmnitrixState> STATE = AttachmentRegistry.create(ProjectHeroMod.id("omnitrix_state"),
			builder -> builder.persistent(OmnitrixState.CODEC)
					.copyOnDeath()
					.initializer(OmnitrixState::new)
					.syncWith(ByteBufCodecs.fromCodec(OmnitrixState.CODEC), AttachmentSyncPredicate.all()));

	/**
	 * The Containment Pod extraction running on this player: ticks elapsed (absent = none). Not saved (a relog aborts it),
	 * synced to all (the red self-destruct HUD for the wearer, the pod's beam for everyone).
	 */
	public static final AttachmentType<Integer> EXTRACTION = AttachmentRegistry.create(ProjectHeroMod.id("omnitrix_extraction"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private OmnitrixAttachments() {
	}

	static void init() {
	}
}

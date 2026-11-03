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

	/**
	 * v0.17.2 (user: "if the player wears armour while transformed make the armour disappear and come back once the player detransforms, the aliens arent supposed to wear armour since that would make them far too powerful"): the armour taken off him while he is an alien -- head, chest, legs, feet. Persistent and KEPT ON DEATH, so nothing is
	 * ever lost (a crash, a relog, a death mid-transformation); put back by {@link OmnitrixBody#restoreArmour}.
	 */
	public static final AttachmentType<java.util.List<net.minecraft.world.item.ItemStack>> STASHED_ARMOUR = AttachmentRegistry.create(
			ProjectHeroMod.id("omnitrix_stashed_armour"),
			builder -> builder.persistent(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath());

	private OmnitrixAttachments() {
	}

	static void init() {
	}
}

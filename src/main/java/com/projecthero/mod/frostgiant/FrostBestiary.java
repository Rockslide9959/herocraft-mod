package com.projecthero.mod.frostgiant;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.1: the Frost Giant <b>bestiary</b> -- the first kill of each unit type (by you, or by anyone fighting the same
 * assault alongside you) unlocks its entry in the Guidebook's Frost Giant chapter. A persistent bitmask attachment, synced
 * to its owner so the book can show what they have learned ({@code HeroPackGuide.frostGiantsChapter}).
 */
public final class FrostBestiary {
	public static final int HOUND = 0, RAIDER = 1, SHAMAN = 2, BRUTE = 3, WARLORD = 4, LAUFEY = 5;
	public static final int COUNT = 6;
	/** Entity ids, in bestiary order (also the lang keys: {@code entity.projecthero.<id>}). */
	public static final String[] IDS = { "rime_hound", "jotun_raider", "frost_shaman", "ice_brute", "frost_giant_warlord", "king_laufey" };

	public static final AttachmentType<Integer> UNLOCKED = AttachmentRegistry.create(ProjectHeroMod.id("frost_giant_bestiary"),
			builder -> builder.persistent(Codec.INT).copyOnDeath().initializer(() -> 0)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private FrostBestiary() {
	}

	public static void initialize() {
		// loads UNLOCKED
	}

	public static boolean isUnlocked(Player player, int id) {
		return (player.getAttachedOrElse(UNLOCKED, 0) & (1 << id)) != 0;
	}

	/** Unlocks entry {@code id} for {@code player}; tells them the first time. Returns whether it was new. */
	public static boolean unlock(ServerPlayer player, int id) {
		if (id < 0 || id >= COUNT) {
			return false;
		}
		int mask = player.getAttachedOrElse(UNLOCKED, 0);
		if ((mask & (1 << id)) != 0) {
			return false;
		}
		player.setAttached(UNLOCKED, mask | (1 << id));
		player.sendSystemMessage(Component.translatable("message.projecthero.frost_giants.bestiary",
				Component.translatable("entity.projecthero." + IDS[id]).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
		return true;
	}
}

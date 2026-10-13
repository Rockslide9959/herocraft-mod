package com.projecthero.mod.galactus;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.reward.Valuables;
import com.projecthero.mod.galactus.item.GalactusItems;
import com.projecthero.mod.horde.HordeRewards;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * v0.16.1: what driving Galactus off pays.
 * <ul>
 *   <li>A <b>reward chest</b> at the arena's centre (the Herald's Beacon becomes it): Galactus' Helmet Crest trophy, diamonds,
 *       netherite scrap, emeralds, amethyst and enchanted books, scaled for the party ({@link Valuables#partyScale}).</li>
 *   <li>Every winner's <b>first</b> clear ({@link #CLEARS}, persistent, once per player ever): the Silver Surfer's Board and the
 *       Power Cosmic, straight into their pack. Never in the shared chest.</li>
 * </ul>
 */
public final class GalactusRewards {
	public static final AttachmentType<Integer> CLEARS = AttachmentRegistry.create(ProjectHeroMod.id("galactus_clears"),
			builder -> builder.persistent(Codec.INT).copyOnDeath().initializer(() -> 0));

	private GalactusRewards() {
	}

	public static void initialize() {
		// loads CLEARS
	}

	public static List<ItemStack> roll(RandomSource r, RegistryAccess registries, int fighters) {
		GalactusConfig.Rewards cfg = GalactusConfig.rewards();
		double more = Valuables.partyScale(fighters);
		double val = more * Math.max(0.0, cfg.valuablesMultiplier);
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(GalactusItems.HELMET_CREST));
		Valuables.add(out, r, Items.DIAMOND, 10, 18, val);
		Valuables.add(out, r, Items.NETHERITE_SCRAP, cfg.netheriteMin, Math.max(cfg.netheriteMin, cfg.netheriteMax), more);
		Valuables.add(out, r, Items.EMERALD, 16, 28, val);
		Valuables.add(out, r, Items.AMETHYST_SHARD, 12, 24, val);
		Valuables.add(out, r, Items.EXPERIENCE_BOTTLE, 8, 16, more);
		int books = Math.max(0, cfg.books) + (fighters >= 3 ? 1 : 0);
		for (int i = 0; i < books; i++) {
			out.add(Valuables.book(r, registries, 30));
		}
		return out;
	}

	public static void placeChest(ServerLevel level, BlockPos pos, int fighters) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			HordeRewards.fill(chest, roll(level.random, level.registryAccess(), fighters), level.random);
			chest.setChanged();
		}
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 80, 0.5, 0.8, 0.5, 0.3);
		level.sendParticles(GalactusFx.COSMIC, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.6, 0.8, 0.6, 0.1);
	}

	public static int clears(Player player) {
		return player.getAttachedOrElse(CLEARS, 0);
	}

	/** Records a clear; on the first, returns the items owed (the Board and the Power Cosmic), else none. Pure bookkeeping. */
	public static List<ItemStack> recordClear(Player player) {
		int before = clears(player);
		player.setAttached(CLEARS, before + 1);
		List<ItemStack> out = new ArrayList<>();
		if (before == 0 && GalactusConfig.rewards().firstClearItems) {
			out.add(new ItemStack(GalactusItems.SURFBOARD));
			out.add(new ItemStack(GalactusItems.POWER_COSMIC));
		}
		return out;
	}

	/** Records the clear and hands over the first-clear items (into the pack, or at their feet). True if it was the first. */
	public static boolean grantClear(ServerPlayer player) {
		List<ItemStack> owed = recordClear(player);
		if (owed.isEmpty()) {
			return false;
		}
		for (ItemStack s : owed) {
			if (!player.getInventory().add(s)) {
				player.drop(s, false);
			}
		}
		player.sendSystemMessage(Component.translatable("message.projecthero.galactus.first_clear").withStyle(ChatFormatting.LIGHT_PURPLE));
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
				1.5f, 0.5f);
		return true;
	}
}

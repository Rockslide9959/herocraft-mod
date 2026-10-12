package com.projecthero.mod.frostgiant;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.reward.Valuables;
import com.projecthero.mod.frostgiant.item.FrostGiantItems;
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
 * v0.16.1: what breaking King Laufey's assault pays. The Rift Anchor becomes a chest ({@link #placeChest}):
 * <ul>
 *   <li><b>Rime Shards</b> x (4 + 2 per fighter) -- the new material;</li>
 *   <li>one <b>Jotun Skull</b> (the trophy block);</li>
 *   <li>diamonds, blue ice, packed ice, emeralds, bottles o' enchanting and a level-30 enchanted book, scaled for the
 *       party like every raid chest ({@link Valuables#partyScale}).</li>
 * </ul>
 * And every winner who has never beaten it before is handed the <b>Casket of Ancient Winters</b> ({@link #grantCasket};
 * tracked in the persistent {@link #CLEARS} attachment, so it is once per player, ever). Never in the shared chest.
 */
public final class FrostGiantRewards {
	/** How many times this player has broken a Frost Giant Assault. Persistent, survives death. */
	public static final AttachmentType<Integer> CLEARS = AttachmentRegistry.create(ProjectHeroMod.id("frost_giant_clears"),
			builder -> builder.persistent(Codec.INT).copyOnDeath().initializer(() -> 0));

	private FrostGiantRewards() {
	}

	public static void initialize() {
		// loads CLEARS
	}

	/** Rime Shards for a party of {@code fighters}. */
	public static int shardsFor(int fighters) {
		FrostGiantConfig.Rewards cfg = FrostGiantConfig.rewards();
		return Math.max(0, cfg.shardBase + cfg.shardPerFighter * Math.max(1, fighters));
	}

	/** The chest's contents for {@code fighters} winners. */
	public static List<ItemStack> roll(RandomSource r, RegistryAccess registries, int fighters) {
		FrostGiantConfig.Rewards cfg = FrostGiantConfig.rewards();
		double more = Valuables.partyScale(fighters);
		double val = more * Math.max(0.0, cfg.valuablesMultiplier);
		List<ItemStack> out = new ArrayList<>();
		Valuables.stacks(out, FrostGiantItems.RIME_SHARD, shardsFor(fighters));
		out.add(new ItemStack(FrostGiantItems.JOTUN_SKULL));
		Valuables.add(out, r, Items.DIAMOND, 6, 12, val);
		Valuables.add(out, r, Items.BLUE_ICE, 8, 16, val);
		Valuables.add(out, r, Items.PACKED_ICE, 16, 32, val);
		Valuables.add(out, r, Items.EMERALD, 12, 20, val);
		Valuables.add(out, r, Items.EXPERIENCE_BOTTLE, cfg.experienceBottlesMin, Math.max(cfg.experienceBottlesMin, cfg.experienceBottlesMax), more);
		out.add(Valuables.book(r, registries, 30));
		return out;
	}

	/** The Rift Anchor at {@code pos} becomes the reward chest. */
	public static void placeChest(ServerLevel level, BlockPos pos, int fighters) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			HordeRewards.fill(chest, roll(level.random, level.registryAccess(), fighters), level.random);
			chest.setChanged();
		}
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 60, 0.5, 0.8, 0.5, 0.3);
		level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.6, 0.8, 0.6, 0.1);
	}

	public static int clears(Player player) {
		return player.getAttachedOrElse(CLEARS, 0);
	}

	/** Records a clear; on the first, returns the Casket they are owed (else empty). Pure bookkeeping. */
	public static ItemStack recordClear(Player player) {
		int before = clears(player);
		player.setAttached(CLEARS, before + 1);
		return before == 0 && FrostGiantConfig.rewards().casketOnFirstClear ? new ItemStack(FrostGiantItems.CASKET) : ItemStack.EMPTY;
	}

	/** Records the clear and, on a first clear, puts the Casket of Ancient Winters in the player's inventory (or at their feet). */
	public static void grantCasket(ServerPlayer player) {
		ItemStack casket = recordClear(player);
		if (casket.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(casket)) {
			player.drop(casket, false);
		}
		player.sendSystemMessage(Component.translatable("message.projecthero.frost_giants.casket").withStyle(ChatFormatting.AQUA));
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
				1.5f, 0.5f);
	}
}

package com.projecthero.mod.ultron;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.reward.Valuables;
import com.projecthero.mod.horde.HordeRewards;
import com.projecthero.mod.ultron.item.UltronItems;

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
 * v0.15.12: what purging Ultron pays. The uplink turns into a chest ({@link #placeChest}):
 * <ul>
 *   <li><b>Vibranium Plating</b> x (2 + fighters) -- the Iron Man armour upgrade;</li>
 *   <li>one <b>Ultron Core</b> (the trophy block);</li>
 *   <li>1-3 netherite scrap, diamonds, redstone, gold, emeralds, an enchanted book and bottles o' enchanting -- the
 *       stacks scaled for the party like every horde / raid chest ({@link Valuables#partyScale}).</li>
 * </ul>
 * And every winner who has never beaten Ultron before is handed a <b>Mind Stone</b> ({@link #grantMindStone}; tracked
 * per player in the persistent {@link #CLEARS} attachment, so it is once per player, ever).
 */
public final class UltronRewards {
	/** How many times this player has purged Ultron. Persistent, survives death. */
	public static final AttachmentType<Integer> CLEARS = AttachmentRegistry.create(ProjectHeroMod.id("ultron_clears"),
			builder -> builder.persistent(Codec.INT).copyOnDeath().initializer(() -> 0));

	private UltronRewards() {
	}

	public static void initialize() {
		// loads CLEARS
	}

	/** Vibranium Plating for a party of {@code fighters}. */
	public static int platingFor(int fighters) {
		return Math.max(0, UltronConfig.rewards().plateBase + Math.max(1, fighters));
	}

	/** The chest's contents for {@code fighters} winners. */
	public static List<ItemStack> roll(RandomSource r, RegistryAccess registries, int fighters) {
		UltronConfig.Rewards cfg = UltronConfig.rewards();
		double more = Valuables.partyScale(fighters);
		double val = more * Math.max(0.0, cfg.valuablesMultiplier);
		List<ItemStack> out = new ArrayList<>();
		Valuables.stacks(out, UltronItems.VIBRANIUM_PLATING, platingFor(fighters));
		out.add(new ItemStack(UltronItems.ULTRON_CORE));
		Valuables.add(out, r, Items.NETHERITE_SCRAP, Math.max(0, cfg.netheriteMin), Math.max(cfg.netheriteMin, cfg.netheriteMax), 1.0);
		Valuables.add(out, r, Items.DIAMOND, 6, 12, val);
		Valuables.add(out, r, Items.REDSTONE, 20, 36, val);
		Valuables.add(out, r, Items.GOLD_INGOT, 12, 20, val);
		Valuables.add(out, r, Items.EMERALD, 12, 20, val);
		Valuables.add(out, r, Items.EXPERIENCE_BOTTLE, cfg.experienceBottlesMin, Math.max(cfg.experienceBottlesMin, cfg.experienceBottlesMax), more);
		out.add(Valuables.book(r, registries, 30));
		return out;
	}

	/** The uplink at {@code pos} becomes the reward chest. */
	public static void placeChest(ServerLevel level, BlockPos pos, int fighters) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			HordeRewards.fill(chest, roll(level.random, level.registryAccess(), fighters), level.random);
			chest.setChanged();
		}
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 60, 0.5, 0.8, 0.5, 0.3);
	}

	/** How many times {@code player} has purged Ultron. */
	public static int clears(Player player) {
		return player.getAttachedOrElse(CLEARS, 0);
	}

	/**
	 * Records a clear for {@code player}; on their first, returns the Mind Stone they are owed (else empty). Pure
	 * bookkeeping -- {@link #grantMindStone} hands it over.
	 */
	public static ItemStack recordClear(Player player) {
		int before = clears(player);
		player.setAttached(CLEARS, before + 1);
		return before == 0 && UltronConfig.rewards().mindStoneOnFirstClear ? new ItemStack(UltronItems.MIND_STONE) : ItemStack.EMPTY;
	}

	/** Records the clear and, on a first clear, puts a Mind Stone in the player's inventory (or at their feet). */
	public static void grantMindStone(ServerPlayer player) {
		ItemStack stone = recordClear(player);
		if (stone.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stone)) {
			player.drop(stone, false);
		}
		player.sendSystemMessage(Component.translatable("message.projecthero.ultron.mind_stone").withStyle(ChatFormatting.GOLD));
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
				SoundSource.PLAYERS, 1.5f, 0.6f);
	}
}

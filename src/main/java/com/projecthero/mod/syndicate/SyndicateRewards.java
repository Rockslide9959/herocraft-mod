package com.projecthero.mod.syndicate;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.event.reward.Valuables;
import com.projecthero.mod.firearm.item.FirearmItems;
import com.projecthero.mod.horde.HordeRewards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * v0.14.25: the busted stash. A street-level haul -- between the Zombie Horde and the Supervillain Raid -- with the
 * things only the Syndicate pays: a <b>Villain Dossier</b> for every fighter (up to four; each starts a Supervillain
 * Raid on demand), the <b>Kingpin's Cane</b>, and Punisher ammunition and gun parts. Party size scales the stacks the
 * same way the horde chests do ({@link Valuables#partyScale}).
 */
public final class SyndicateRewards {
	private SyndicateRewards() {
	}

	public static void placeChest(ServerLevel level, BlockPos pos, Direction forward, int party) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, forward.getOpposite()),
				Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			HordeRewards.fill(chest, roll(level.random, level.registryAccess(), party), level.random);
			chest.setChanged();
		}
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.1);
		level.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.2);
	}

	/** The chest's contents (at most 27 stacks). */
	public static List<ItemStack> roll(RandomSource r, RegistryAccess registries, int party) {
		double more = Valuables.partyScale(party);
		List<ItemStack> out = new ArrayList<>();
		if (SyndicateItems.VILLAIN_DOSSIER != null) {
			out.add(new ItemStack(SyndicateItems.VILLAIN_DOSSIER, Math.min(4, Math.max(1, party))));
		}
		if (SyndicateItems.KINGPIN_CANE != null) {
			out.add(new ItemStack(SyndicateItems.KINGPIN_CANE));
		}
		Valuables.add(out, r, Items.EMERALD, 14, 26, more);
		Valuables.add(out, r, Items.GOLD_INGOT, 6, 12, more);
		Valuables.add(out, r, Items.IRON_INGOT, 12, 24, more);
		Valuables.add(out, r, Items.DIAMOND, 2, 4, more);
		Valuables.add(out, r, Items.GUNPOWDER, 8, 16, more);
		Valuables.add(out, r, Items.GOLDEN_APPLE, 1, 3, more);
		Valuables.add(out, r, Items.EXPERIENCE_BOTTLE, 6, 12, more);
		ammo(out, r, FirearmItems.PISTOL_AMMO, 16, 32, more);
		ammo(out, r, FirearmItems.SHOTGUN_SHELL, 8, 16, more);
		ammo(out, r, FirearmItems.SNIPER_AMMO, 4, 8, more);
		ammo(out, r, FirearmItems.WEAPON_PARTS, 2, 4, more);
		if (FirearmItems.GUN_BARREL != null) {
			Valuables.chance(out, r, 0.4, FirearmItems.GUN_BARREL, 1, 1);
		}
		if (FirearmItems.WEAPON_SCOPE != null) {
			Valuables.chance(out, r, 0.2, FirearmItems.WEAPON_SCOPE, 1, 1);
		}
		Valuables.chance(out, r, 0.25, Items.ENCHANTED_GOLDEN_APPLE, 1, 1);
		out.add(Valuables.book(r, registries, 20));
		return out;
	}

	private static void ammo(List<ItemStack> out, RandomSource r, Item item, int lo, int hi, double more) {
		if (item != null) {
			Valuables.add(out, r, item, lo, hi, more);
		}
	}
}

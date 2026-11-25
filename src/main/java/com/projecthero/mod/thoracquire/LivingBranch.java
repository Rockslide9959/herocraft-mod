package com.projecthero.mod.thoracquire;

import java.util.List;

import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: the <b>Living Branch</b> -- the haft of Stormbreaker. When lightning strikes a jungle tree (natural lightning or
 * any of Thor's bolts, which are visual-only lightning bolts too) there is a {@link #CHANCE} chance the tree gives one up: it
 * drops out of the struck log / leaves with green motes and a chime. Also a small chance in jungle temple chests and from the
 * Ancient Temple's king. Glints like an enchanted item; never burns.
 */
public final class LivingBranch {
	public static final float CHANCE = 0.10f;
	/** Tests force the roll (1 = always, 0 = never); null = {@link #CHANCE}. */
	public static volatile Float chanceOverride;
	public static final int SEARCH_H = 3;
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.4f, 1f, 0.5f), 1.3f);

	private LivingBranch() {
	}

	public static boolean isJungleWood(BlockState s) {
		return s.is(Blocks.JUNGLE_LOG) || s.is(Blocks.JUNGLE_WOOD) || s.is(Blocks.JUNGLE_LEAVES)
				|| s.is(Blocks.STRIPPED_JUNGLE_LOG) || s.is(Blocks.STRIPPED_JUNGLE_WOOD);
	}

	/** A bolt landed at {@code at}: is there a jungle tree it hit, and does the roll give a branch? Returns the drop or null. */
	public static ItemEntity onStrike(ServerLevel level, BlockPos at) {
		BlockPos hit = findJungle(level, at);
		if (hit == null) {
			return null;
		}
		Float f = chanceOverride;
		float chance = f != null ? f : CHANCE;
		if (level.random.nextFloat() >= chance) {
			return null;
		}
		ItemEntity drop = new ItemEntity(level, hit.getX() + 0.5, hit.getY() + 0.2, hit.getZ() + 0.5, new ItemStack(ThorAcquire.LIVING_BRANCH));
		drop.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.15, 0.2, (level.random.nextDouble() - 0.5) * 0.15);
		drop.setUnlimitedLifetime();
		level.addFreshEntity(drop);
		level.sendParticles(GREEN, drop.getX(), drop.getY() + 0.3, drop.getZ(), 30, 0.5, 0.5, 0.5, 0);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, drop.getX(), drop.getY() + 0.3, drop.getZ(), 12, 0.5, 0.5, 0.5, 0);
		level.playSound(null, hit, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4f, 1.2f);
		level.playSound(null, hit, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1.2f, 0.8f);
		return drop;
	}

	/** The jungle log or leaves nearest the strike (the struck tree), or null. */
	static BlockPos findJungle(ServerLevel level, BlockPos at) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-SEARCH_H, -3, -SEARCH_H), at.offset(SEARCH_H, 2, SEARCH_H))) {
			if (isJungleWood(level.getBlockState(p))) {
				double d = p.distSqr(at);
				if (d < bestD) {
					bestD = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** The item: glints, never burns, a short tooltip. */
	public static class BranchItem extends Item {
		public BranchItem(Properties properties) {
			super(properties);
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return true;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
			tooltip.add(Component.translatable("item.projecthero.living_branch.line1").withStyle(ChatFormatting.GREEN));
			tooltip.add(Component.translatable("item.projecthero.living_branch.line2").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}

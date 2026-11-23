package com.projecthero.mod.temple.block;

import com.projecthero.mod.temple.item.TempleItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: what lies on the Weight Altar's pan -- a count per weight value (1-4). Exactly {@link #TARGET} cracks the Seal of
 * Weight (the weights stay on the scale for good); more tips the scale and throws them all back. Saved.
 */
public class WeightAltarBlockEntity extends BlockEntity {
	/** The scale balances at seven: Lion + Jackal (4 + 3), or Lion + Ibis + Feather (4 + 2 + 1). */
	public static final int TARGET = 7;
	/** How far from the altar the Seal of Weight is looked for. */
	public static final int SEAL_SEARCH = 16;

	private final int[] counts = new int[5];

	public WeightAltarBlockEntity(BlockPos pos, BlockState state) {
		super(TempleBlocks.WEIGHT_ALTAR_BE, pos, state);
	}

	public int total() {
		int t = 0;
		for (int w = 1; w <= 4; w++) {
			t += w * counts[w];
		}
		return t;
	}

	private boolean solved() {
		return getBlockState().getValue(WeightAltarBlock.SOLVED);
	}

	/** Lays a weight of {@code weight} on the pan. Returns whether the item was taken. */
	public boolean lay(ServerLevel level, int weight, ServerPlayer player) {
		if (solved()) {
			player.displayClientMessage(Component.translatable("block.projecthero.weight_altar.done").withStyle(s -> s.withColor(0xE8C060)), true);
			return false;
		}
		counts[weight]++;
		setChanged();
		int t = total();
		level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f + weight * 0.08f);
		if (t == TARGET) {
			level.setBlock(worldPosition, getBlockState().setValue(WeightAltarBlock.SOLVED, true).setValue(WeightAltarBlock.LOAD, 2), 3);
			level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6f, 1.6f);
			level.sendParticles(ParticleTypes.WAX_ON, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 20, 0.4, 0.2, 0.4, 0.5);
			player.displayClientMessage(Component.translatable("block.projecthero.weight_altar.balanced").withStyle(s -> s.withColor(0xE8C060)), true);
			TempleSealBlockEntity seal = TempleSealBlockEntity.find(level, worldPosition, TempleSealBlock.Kind.WEIGHT, SEAL_SEARCH);
			if (seal != null) {
				seal.breakSeal(level);
			}
		} else if (t > TARGET) {
			player.displayClientMessage(Component.translatable("block.projecthero.weight_altar.tipped", t, TARGET).withStyle(s -> s.withColor(0xC06040)), true);
			level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8f, 0.6f);
			ejectAll(level);
		} else {
			level.setBlock(worldPosition, getBlockState().setValue(WeightAltarBlock.LOAD, 1), 3);
			player.displayClientMessage(Component.translatable("block.projecthero.weight_altar.holds", t, TARGET).withStyle(s -> s.withColor(0xE8C060)), true);
		}
		return true;
	}

	public void takeBack(ServerLevel level, ServerPlayer player) {
		if (solved() || total() == 0) {
			explain(player);
			return;
		}
		ejectAll(level);
		level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
	}

	public void explain(ServerPlayer player) {
		String key = solved() ? "block.projecthero.weight_altar.done" : total() > 0 ? "block.projecthero.weight_altar.holds" : "block.projecthero.weight_altar.hint";
		player.displayClientMessage(Component.translatable(key, total(), TARGET).withStyle(s -> s.withColor(0xE8C060)), true);
	}

	private void ejectAll(ServerLevel level) {
		for (int w = 1; w <= 4; w++) {
			if (counts[w] > 0) {
				ItemEntity e = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
						new ItemStack(TempleItems.weight(w), counts[w]));
				e.setDefaultPickUpDelay();
				level.addFreshEntity(e);
				counts[w] = 0;
			}
		}
		setChanged();
		level.setBlock(worldPosition, getBlockState().setValue(WeightAltarBlock.LOAD, 0), 3);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putIntArray("Counts", counts);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		int[] c = tag.getIntArray("Counts");
		for (int i = 0; i < Math.min(c.length, counts.length); i++) {
			counts[i] = Math.max(0, c[i]);
		}
	}
}

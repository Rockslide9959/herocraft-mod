package com.projecthero.mod.temple.block;

import com.projecthero.mod.temple.TempleClearing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * v0.17.7: a Seal's memory. For the {@link TempleSealBlock.Kind#GLYPH} seal: the glyph order (written by the temple's
 * builder from its seed and carved on the mural) and how far into it the player is. Saved.
 */
public class TempleSealBlockEntity extends BlockEntity {
	private int[] order = { 0, 1, 2, 3 };
	private int progress;

	public TempleSealBlockEntity(BlockPos pos, BlockState state) {
		super(TempleBlocks.SEAL_BE, pos, state);
	}

	public int[] order() {
		return order.clone();
	}

	public void setOrder(int[] order) {
		this.order = order.clone();
		setChanged();
	}

	public int progress() {
		return progress;
	}

	public TempleSealBlock.Kind kind() {
		return getBlockState().getValue(TempleSealBlock.KIND);
	}

	public boolean broken() {
		return getBlockState().getValue(TempleSealBlock.BROKEN);
	}

	/** A glyph plate showing {@code glyph} at {@code plate} was stepped on. */
	public void stepGlyph(ServerLevel level, BlockPos plate, int glyph) {
		if (broken() || kind() != TempleSealBlock.Kind.GLYPH) {
			return;
		}
		if (order[progress] == glyph) {
			progress++;
			BlockState s = level.getBlockState(plate);
			if (s.is(TempleBlocks.GLYPH_PLATE)) {
				level.setBlock(plate, s.setValue(GlyphPlateBlock.LIT, true), 3);
			}
			level.playSound(null, plate, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.6f + progress * 0.25f);
			level.sendParticles(ParticleTypes.ENCHANT, plate.getX() + 0.5, plate.getY() + 0.6, plate.getZ() + 0.5, 25, 0.3, 0.3, 0.3, 0.5);
			if (progress >= order.length) {
				breakSeal(level);
			}
		} else {
			// a wrong glyph: every plate goes out and the order starts again (unless it was the first glyph again)
			boolean restart = order[0] == glyph;
			resetPlates(level);
			progress = 0;
			level.playSound(null, plate, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.6f);
			level.sendParticles(ParticleTypes.SMOKE, plate.getX() + 0.5, plate.getY() + 0.4, plate.getZ() + 0.5, 15, 0.3, 0.2, 0.3, 0.02);
			for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(plate).inflate(6))) {
				p.displayClientMessage(Component.translatable("block.projecthero.temple_seal.glyph_wrong").withStyle(s -> s.withColor(0xC06040)), true);
			}
			if (restart) {
				stepGlyph(level, plate, glyph);
			}
		}
		setChanged();
	}

	private void resetPlates(ServerLevel level) {
		int r = GlyphPlateBlock.SEAL_SEARCH;
		for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-r, -3, -r), worldPosition.offset(r, 3, r))) {
			BlockState s = level.getBlockState(p);
			if (s.is(TempleBlocks.GLYPH_PLATE) && s.getValue(GlyphPlateBlock.LIT)) {
				level.setBlock(p, s.setValue(GlyphPlateBlock.LIT, false), 3);
			}
		}
	}

	/** The trial is solved: the seal cracks open, and the hall's gates open once all three are broken. */
	public void breakSeal(ServerLevel level) {
		BlockState s = getBlockState();
		if (s.getValue(TempleSealBlock.BROKEN)) {
			return;
		}
		level.setBlock(worldPosition, s.setValue(TempleSealBlock.BROKEN, true), 3);
		double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.7, z = worldPosition.getZ() + 0.5;
		level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0f, 0.7f);
		level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 2.0f, 0.6f);
		level.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.3f), 1.5f), x, y, z, 40, 0.4, 0.5, 0.4, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.3, 0.5, 0.3, 0.08);
		Component msg = Component.translatable("block.projecthero.temple_seal.cracked." + kind().getSerializedName()).withStyle(c -> c.withColor(0xE8C060));
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(32))) {
			p.displayClientMessage(msg, false);
		}
		TempleClearing.trialsCheck(level, worldPosition);
	}

	/** The nearest unbroken-or-broken seal of {@code kind} within {@code r} (horizontal; ±4 vertical) of {@code pos}, or null. */
	public static TempleSealBlockEntity find(ServerLevel level, BlockPos pos, TempleSealBlock.Kind kind, int r) {
		TempleSealBlockEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -4, -r), pos.offset(r, 4, r))) {
			BlockState s = level.getBlockState(p);
			if (s.is(TempleBlocks.SEAL) && s.getValue(TempleSealBlock.KIND) == kind && level.getBlockEntity(p) instanceof TempleSealBlockEntity be) {
				double d = p.distSqr(pos);
				if (d < bestSq) {
					bestSq = d;
					best = be;
				}
			}
		}
		return best;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putIntArray("Order", order);
		tag.putInt("Progress", progress);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		int[] o = tag.getIntArray("Order");
		if (o.length == 4) {
			order = o;
		}
		progress = Math.max(0, Math.min(3, tag.getInt("Progress")));
	}
}

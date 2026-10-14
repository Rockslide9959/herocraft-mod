package com.projecthero.mod.city;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * TEMPORARY (v0.16.2 City part A only). The City templates place three blocks that belong to City part B (the City
 * systems): {@code projecthero:precinct_desk} (City Cop job site, police precinct), {@code projecthero:repair_bench}
 * (Repair Worker job site, maintenance depot) and {@code projecthero:safe_deposit} (the bank vault). Until part B is
 * merged they don't exist in this branch, so this file registers bare placeholders with exactly those ids (a
 * horizontal {@code facing}, no item, no POI, no model) so the NBT templates load and the world generates.
 * <p>
 * <b>At merge, part B's real registrations replace this file:</b> delete it and its {@code initialize()} call in
 * {@link ProjectHeroMod}. The templates store each block as {@code projecthero:<id>[facing=...]}; a real block without
 * a {@code facing} property simply loads its default state.
 */
public final class CityBlocksPlaceholder {
	public static final Block PRECINCT_DESK = register("precinct_desk");
	public static final Block REPAIR_BENCH = register("repair_bench");
	public static final Block SAFE_DEPOSIT = register("safe_deposit");

	private CityBlocksPlaceholder() {
	}

	public static void initialize() {
		// the static fields above register the blocks when the class loads
	}

	private static Block register(String path) {
		Block block = new Placeholder(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD));
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	/** A plain block that remembers which way it faces. */
	static final class Placeholder extends HorizontalDirectionalBlock {
		static final MapCodec<Placeholder> CODEC = simpleCodec(Placeholder::new);

		Placeholder(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext ctx) {
			return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
		}
	}
}

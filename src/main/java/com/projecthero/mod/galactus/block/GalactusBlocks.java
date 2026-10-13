package com.projecthero.mod.galactus.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.16.1: Galactus: The Devourer's blocks -- the <b>Herald's Beacon</b> (starts it; the arena's centre), <b>Consumed
 * Ground</b> (what an Element Converter turns the land into, always put back), <b>Cosmic Hazard</b> (Matter Transmutation's
 * patches, put back when they expire) and <b>Galactus' Helmet Crest</b> (the trophy).
 */
public final class GalactusBlocks {
	/** Natural blocks an Element Converter / Matter Transmutation may change (grass, dirt, stone, sand, gravel...). */
	public static final TagKey<Block> CONSUMABLE = TagKey.create(Registries.BLOCK, ProjectHeroMod.id("galactus_consumable"));

	public static HeraldsBeaconBlock HERALDS_BEACON;
	public static Block CONSUMED_GROUND;
	public static Block COSMIC_HAZARD;
	public static HelmetCrestBlock HELMET_CREST;

	private GalactusBlocks() {
	}

	public static void initialize() {
		HERALDS_BEACON = register("heralds_beacon", new HeraldsBeaconBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_PURPLE)
				.strength(5.0f, 1200.0f)
				.requiresCorrectToolForDrops()
				.sound(SoundType.AMETHYST)
				.lightLevel(s -> s.getValue(HeraldsBeaconBlock.ACTIVE) ? 15 : 7)));
		// never drops anything (it is always put back), never pushed: a piston can't carry it off the ledger
		CONSUMED_GROUND = register("consumed_ground", new Block(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GRAY)
				.strength(0.8f, 6.0f)
				.sound(SoundType.TUFF)
				.pushReaction(PushReaction.BLOCK)));
		COSMIC_HAZARD = register("cosmic_hazard", new CosmicHazardBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_MAGENTA)
				.strength(1.0f, 6.0f)
				.sound(SoundType.AMETHYST)
				.lightLevel(s -> 10)
				.emissiveRendering((s, l, p) -> true)
				.pushReaction(PushReaction.BLOCK)));
		HELMET_CREST = register("galactus_helmet_crest", new HelmetCrestBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_PURPLE)
				.strength(3.0f, 1200.0f)
				.sound(SoundType.NETHERITE_BLOCK)
				.noOcclusion()
				.lightLevel(s -> 8)));
	}

	/** Is {@code b} one of the blocks the event puts down (and only ever takes back)? */
	public static boolean isEventBlock(Block b) {
		return b == CONSUMED_GROUND || b == COSMIC_HAZARD;
	}

	private static <T extends Block> T register(String path, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	/** Matter Transmutation's glowing violet ground (the damage is the event's zone; this is the look). */
	public static class CosmicHazardBlock extends Block {
		public CosmicHazardBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
						0, 0.04, 0);
			}
		}
	}

	/** The trophy: a horned violet crest that hums with Power Cosmic. Faces whoever placed it. */
	public static class HelmetCrestBlock extends HorizontalDirectionalBlock {
		public static final MapCodec<HelmetCrestBlock> CODEC = simpleCodec(HelmetCrestBlock::new);
		private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);

		public HelmetCrestBlock(BlockBehaviour.Properties properties) {
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

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.0,
						pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
			}
		}
	}
}

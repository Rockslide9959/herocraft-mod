package com.projecthero.mod.doomsday.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doomsday.Doomsday;
import com.projecthero.mod.doomsday.DoomsdayConfig;
import com.projecthero.mod.doomsday.DoomsdayFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.2: Doomsday's blocks -- the <b>Calatonian Casket</b> (right-click it to open it: the event begins, the block becomes
 * the casket he pounds his way out of) and <b>Doomsday's Skull</b> (the trophy).
 */
public final class DoomsdayBlocks {
	public static CasketBlock CALATONIAN_CASKET;
	public static SkullBlock SKULL;

	private DoomsdayBlocks() {
	}

	public static void initialize() {
		CALATONIAN_CASKET = register("calatonian_casket", new CasketBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_GREEN)
				.strength(5.0f, 1200.0f)
				.requiresCorrectToolForDrops()
				.sound(SoundType.NETHERITE_BLOCK)
				.noOcclusion()));
		SKULL = register("doomsday_skull", new SkullBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.TERRACOTTA_WHITE)
				.strength(3.0f, 1200.0f)
				.sound(SoundType.BONE_BLOCK)
				.noOcclusion()));
	}

	private static <T extends Block> T register(String path, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	/** The burial vault: a long, low, cabled coffin. Right-click (Survival, not Peaceful) to let him out. */
	public static class CasketBlock extends HorizontalDirectionalBlock {
		public static final MapCodec<CasketBlock> CODEC = simpleCodec(CasketBlock::new);
		private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);

		public CasketBlock(BlockBehaviour.Properties properties) {
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
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			String refusal = null;
			if (!DoomsdayConfig.trigger().enabled) {
				refusal = "message.projecthero.doomsday.disabled";
			} else if (player.isCreative() || player.isSpectator()) {
				refusal = "message.projecthero.doomsday.survival_only";
			} else if (server.getDifficulty() == Difficulty.PEACEFUL) {
				refusal = "message.projecthero.doomsday.peaceful";
			}
			if (refusal != null) {
				player.displayClientMessage(Component.translatable(refusal).withStyle(ChatFormatting.GRAY), true);
				return InteractionResult.CONSUME;
			}
			float yaw = state.getValue(FACING).toYRot();
			if (Doomsday.start(server, pos, sp, Doomsday.Source.CASKET, yaw) == null) {
				player.displayClientMessage(Component.translatable("message.projecthero.doomsday.refused").withStyle(ChatFormatting.DARK_RED), true);
			}
			return InteractionResult.CONSUME;
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(6) == 0) {
				level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.7, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
			}
		}
	}

	/** The trophy: his bone-spiked skull, red eyes still faintly lit. Faces whoever placed it. */
	public static class SkullBlock extends HorizontalDirectionalBlock {
		public static final MapCodec<SkullBlock> CODEC = simpleCodec(SkullBlock::new);
		private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 11, 13);

		public SkullBlock(BlockBehaviour.Properties properties) {
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
			if (random.nextInt(8) == 0) {
				level.addParticle(DoomsdayFx.RED, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 0, 0.01, 0);
			}
		}
	}
}

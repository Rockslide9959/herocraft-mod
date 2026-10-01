package com.projecthero.mod.horde;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.14.12: a Horde block (zombie / skeleton / spider). Right-click it to wake the horde: a {@link HordeRaid} starts
 * centred on it. While its horde runs it is {@link #ACTIVE} -- glowing, and impossible to mine -- and when the horde is
 * beaten it becomes the reward chest.
 */
public class HordeBlock extends Block {
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
	private final HordeKind kind;

	public HordeBlock(HordeKind kind, BlockBehaviour.Properties properties) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		// the kind is fixed per registered block, so no codec round-trip is needed for world data
		return simpleCodec(p -> new HordeBlock(kind, p));
	}

	public HordeKind kind() {
		return kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(ACTIVE)) {
			player.displayClientMessage(Component.translatable("event.projecthero.horde.already").withStyle(ChatFormatting.RED), true);
			return InteractionResult.CONSUME;
		}
		if (player.isCreative() || player.isSpectator()) {
			player.displayClientMessage(Component.translatable("event.projecthero.horde.survival_only").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (!Hordes.start(server, pos, kind)) {
			player.displayClientMessage(Component.translatable("event.projecthero.horde.refused").withStyle(ChatFormatting.RED), true);
		}
		return InteractionResult.CONSUME;
	}

	/** While its horde runs nobody can mine it. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return state.getValue(ACTIVE) ? 0f : super.getDestroyProgress(state, player, level, pos);
	}

	/** Its horde's event, if one is running here. */
	public static EventInstance raidAt(ServerLevel level, BlockPos pos) {
		return EventManager.at(level, pos);
	}
}

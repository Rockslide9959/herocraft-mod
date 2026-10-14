package com.projecthero.mod.city.bank;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.city.CityConfig;
import com.projecthero.mod.city.CityFacingBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.16.2 City part B: the Safe Deposit ({@code projecthero:safe_deposit}), the City Bank's vault. Opens the user's own
 * emerald account (the same one from every Safe Deposit), paying any interest owed first. The block itself holds
 * nothing, so breaking it (or a repair rebuilding it) can never lose or duplicate anyone's emeralds.
 */
public class SafeDepositBlock extends CityFacingBlock {
	public static final MapCodec<SafeDepositBlock> CODEC = simpleCodec(SafeDepositBlock::new);

	public SafeDepositBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			open(sp, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/** Opens {@code player}'s vault (after paying interest). */
	public static void open(ServerPlayer player, BlockPos pos) {
		BankAccounts bank = BankAccounts.get(player.server);
		BankAccounts.Account acc = bank.account(player.getUUID());
		int credited = bank.applyInterest(acc, BankAccounts.day(player.server));
		if (credited > 0) {
			player.sendSystemMessage(Component.translatable("block.projecthero.safe_deposit.interest", credited).withStyle(ChatFormatting.GREEN));
		}
		ContainerData data = new ContainerData() {
			@Override
			public int get(int index) {
				return switch (index) {
					case 0 -> acc.value();
					case 1 -> acc.interestPaid;
					default -> CityConfig.bank().interestCapEmeralds;
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int getCount() {
				return 3;
			}
		};
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SafeDepositMenu(id, inv, acc.items, data, pos),
				Component.translatable("container.projecthero.safe_deposit")));
		player.level().playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.7f, 0.8f);
		BankAccounts.fireOpen(player, pos, acc);
	}
}

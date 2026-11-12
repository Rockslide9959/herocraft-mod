package com.projecthero.mod.raven;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * v0.17.3 Raven: getting her power. The <b>Book of Azarath</b> is found in ancient city chests (1 in 12) and stronghold library chests
 * (1 in 8). Read it (right-click) <b>at night</b> -- in daylight its pages stay blank -- and "Azarath Metrion Zinthos" settles the power
 * in: the Hero-Tier power {@code raven}, replacing whatever Primary you held (the unique-powers gamerule may refuse). The book is spent
 * (creative keeps it). Random serums never give it ({@code PowerGrants.NOT_FROM_SERUMS}).
 */
public final class RavenLearning {
	public static final int ANCIENT_CITY_EMPTY = 11;
	public static final int LIBRARY_EMPTY = 7;

	private RavenLearning() {
	}

	public static boolean isBook(ItemStack stack) {
		return RavenItems.BOOK_OF_AZARATH != null && stack.is(RavenItems.BOOK_OF_AZARATH);
	}

	public static void initialize() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!isBook(stack)) {
				return InteractionResultHolder.pass(stack);
			}
			if (level.isClientSide()) {
				return InteractionResultHolder.success(stack);
			}
			return learn((ServerPlayer) player, hand) ? InteractionResultHolder.consume(player.getItemInHand(hand))
					: InteractionResultHolder.fail(stack);
		});
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			int empty;
			if (key.equals(BuiltInLootTables.ANCIENT_CITY)) {
				empty = ANCIENT_CITY_EMPTY;
			} else if (key.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)) {
				empty = LIBRARY_EMPTY;
			} else {
				return;
			}
			builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(RavenItems.BOOK_OF_AZARATH).setWeight(1))
					.add(EmptyLootItem.emptyItem().setWeight(empty)));
		});
	}

	/** Night (or a sky with no day: the Nether, the End). */
	public static boolean night(Level level) {
		return !level.isDay();
	}

	/** Right-click: read the book. False (with the reason) in daylight, if she already has it, or if the unique-powers rule refuses. */
	public static boolean learn(ServerPlayer player, InteractionHand hand) {
		if (Raven.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.raven.already").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!night(player.level())) {
			player.displayClientMessage(Component.translatable("message.projecthero.raven.daylight").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!Raven.grant(player)) {
			return false;
		}
		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}
		return true;
	}
}

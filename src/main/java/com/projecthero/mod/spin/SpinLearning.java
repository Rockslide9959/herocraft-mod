package com.projecthero.mod.spin;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * v0.17.3 the Spin: getting the Spin. The <b>Zeppeli Steel Balls</b> item ({@code projecthero:zeppeli_steel_balls}, registered
 * by {@code SpinItems}) is found like the Stand Arrow: desert pyramid chests (1 in 12) and trail ruins' rare archaeology (1 in 20).
 * Right-click it to learn the Spin: the Hero-Tier power {@code spin}, replacing whatever Primary you held (the unique powers
 * gamerule may refuse). The balls become yours, so the item is spent. Random serums never give it ({@code PowerGrants}).
 *
 * <p>Both hooks look the item up by id, so this works whichever class registers it.
 */
public final class SpinLearning {
	public static final ResourceLocation ITEM_ID = ProjectHeroMod.id("zeppeli_steel_balls");

	private SpinLearning() {
	}

	/** The registered item, or air if nothing is registered under the id. */
	public static Item item() {
		return BuiltInRegistries.ITEM.get(ITEM_ID);
	}

	public static boolean isBalls(ItemStack stack) {
		Item item = item();
		return item != Items.AIR && stack.is(item);
	}

	public static void initialize() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!isBalls(stack)) {
				return InteractionResultHolder.pass(stack);
			}
			if (level.isClientSide()) {
				return Spin.hasPower(player) ? InteractionResultHolder.fail(stack) : InteractionResultHolder.success(stack);
			}
			return learn((ServerPlayer) player, hand) ? InteractionResultHolder.consume(player.getItemInHand(hand))
					: InteractionResultHolder.fail(stack);
		});
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			int empty;
			if (key.equals(BuiltInLootTables.DESERT_PYRAMID)) {
				empty = 11;
			} else if (key.equals(BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE)) {
				empty = 19;
			} else {
				return;
			}
			Item balls = item();
			if (balls == Items.AIR) {
				return;
			}
			builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(balls).setWeight(1))
					.add(EmptyLootItem.emptyItem().setWeight(empty)));
		});
	}

	/** Right-click: learn the Spin. False (with the reason) if he already has it or the unique-powers rule refuses. */
	public static boolean learn(ServerPlayer player, InteractionHand hand) {
		if (Spin.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.spin.already").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!Spin.grant(player)) {
			return false;
		}
		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}
		return true;
	}
}

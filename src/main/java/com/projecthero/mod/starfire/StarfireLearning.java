package com.projecthero.mod.starfire;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.starfire.item.StarfireItems;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Starfire: getting the power. The <b>Tamaranean Starstone</b> ({@code projecthero:tamaranean_starstone}) is found in End
 * City chests (1 in {@link #END_CITY_ODDS}), in the Meteor Impact sites' chests (1 in {@link #METEOR_SITE_ODDS}) and in the crater of
 * a falling Omnitrix meteor (1 in {@link #CRATER_ODDS}, lying by the pod: {@link #onMeteorCrater}). Right-click it <b>in daylight</b>
 * (under the open sky) to become Starfire, replacing whatever Primary you held (the unique powers gamerule may refuse). The stone is
 * spent. Random serums never give it ({@code PowerGrants.NOT_FROM_SERUMS}).
 */
public final class StarfireLearning {
	public static final int END_CITY_ODDS = 8;
	public static final int METEOR_SITE_ODDS = 10;
	public static final int CRATER_ODDS = 3;
	public static final ResourceKey<LootTable> METEOR_SITE = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/meteor_impact"));

	private StarfireLearning() {
	}

	public static boolean isStarstone(ItemStack stack) {
		return StarfireItems.STARSTONE != null && stack.is(StarfireItems.STARSTONE);
	}

	public static void initialize() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!isStarstone(stack)) {
				return InteractionResultHolder.pass(stack);
			}
			if (level.isClientSide()) {
				return Starfire.hasPower(player) ? InteractionResultHolder.fail(stack) : InteractionResultHolder.success(stack);
			}
			return learn((ServerPlayer) player, hand) ? InteractionResultHolder.consume(player.getItemInHand(hand))
					: InteractionResultHolder.fail(stack);
		});
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			int odds;
			if (source.isBuiltin() && key.equals(BuiltInLootTables.END_CITY_TREASURE)) {
				odds = END_CITY_ODDS;
			} else if (key.equals(METEOR_SITE)) {
				odds = METEOR_SITE_ODDS;
			} else {
				return;
			}
			builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(StarfireItems.STARSTONE).setWeight(1))
					.add(EmptyLootItem.emptyItem().setWeight(odds - 1)));
		});
	}

	/** Daylight under the open sky (the stone wakes in the sun). */
	public static boolean daylight(Player player) {
		return Starfire.sun(player) == Starfire.Sun.DIRECT || (player.level().isDay() && player.level().canSeeSky(player.blockPosition().above()));
	}

	/** Right-click: become Starfire. False (with the reason) if she already is, it is dark, or the unique-powers rule refuses. */
	public static boolean learn(ServerPlayer player, InteractionHand hand) {
		if (Starfire.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.starfire.already").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!daylight(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.starfire.needs_sun").withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		if (!Starfire.grant(player)) {
			return false;
		}
		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}
		return true;
	}

	/** An Omnitrix meteor finished carving its crater: 1 in 3 a Starstone lies at the bottom, by the pod. */
	public static void onMeteorCrater(ServerLevel level, BlockPos bottom) {
		if (level.random.nextInt(CRATER_ODDS) != 0) {
			return;
		}
		dropInCrater(level, bottom);
	}

	/** Leaves a Starstone at {@code bottom} (tests call it directly). */
	public static ItemEntity dropInCrater(ServerLevel level, BlockPos bottom) {
		Vec3 at = Vec3.atBottomCenterOf(bottom).add(1.2, 0.2, 0.0);
		ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(StarfireItems.STARSTONE));
		e.setDeltaMovement(Vec3.ZERO);
		e.setUnlimitedLifetime();
		level.addFreshEntity(e);
		return e;
	}
}

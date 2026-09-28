package com.projecthero.mod.hulk;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Where the Hulk touches the rest of the game (v0.13.11, Phase 1):
 * <ul>
 *   <li><b>Rage</b> -- every hit a Gamma player takes (and, as Banner, every hit he deals) feeds
 *       {@link Hulk#onHurt} / {@link Hulk#onDealt}.</li>
 *   <li><b>Fists only</b> -- the Hulk cannot use tools, weapons or bows: attacking with one, drawing a bow /
 *       crossbow / trident, and breaking blocks with a tool are all refused (bare hands and everything else
 *       -- food, blocks, potions -- still work). Firearms are refused in {@code ModNetworking}'s fire
 *       receiver, since firing is its own packet.</li>
 * </ul>
 */
public final class HulkDamage {
	private HulkDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0f) {
				return;
			}
			if (entity instanceof ServerPlayer hurt && Hulk.hasPower(hurt)) {
				Hulk.onHurt(hurt, taken);
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Hulk.hasPower(attacker)
					&& entity instanceof LivingEntity) {
				Hulk.onDealt(attacker, taken);
			}
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (Hulk.isHulk(player) && isForbidden(player.getMainHandItem())) {
				refuse(player);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (Hulk.isHulk(player) && isForbidden(stack)) {
				refuse(player);
				return InteractionResultHolder.fail(stack);
			}
			return InteractionResultHolder.pass(stack);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (Hulk.isHulk(player) && isForbidden(player.getMainHandItem())) {
				refuse(player);
				return false;
			}
			return true;
		});
	}

	/** Tools, swords, axes, the mace, bows, crossbows, tridents and the mod's firearms. */
	public static boolean isForbidden(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		var item = stack.getItem();
		return item instanceof net.minecraft.world.item.TieredItem
				|| item instanceof net.minecraft.world.item.ProjectileWeaponItem
				|| item instanceof net.minecraft.world.item.TridentItem
				|| item instanceof net.minecraft.world.item.MaceItem
				|| item instanceof com.projecthero.mod.firearm.item.FirearmItem;
	}

	private static void refuse(Player player) {
		if (player instanceof ServerPlayer sp) {
			Hulk.say(sp, "message.projecthero.hulk.fists_only", ChatFormatting.GREEN);
		}
	}
}

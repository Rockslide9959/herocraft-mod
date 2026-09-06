package com.projecthero.mod.firearm.item;

import java.util.List;

import com.projecthero.mod.firearm.FirearmData;
import com.projecthero.mod.firearm.FirearmHooks;
import com.projecthero.mod.firearm.FirearmStack;
import com.projecthero.mod.firearm.Firearms;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Base class for the four Punisher firearms. Each is a plain held {@link Item} -- no durability (so
 * it can never break a recipe or be lost to wear), not enchantable, stacks to one. Firing is
 * left-click (a client mixin routes it to the server); reloading is a tap of the ability-1 key;
 * aiming is holding right-click. This {@link #use} just swallows the right-click so a firearm never
 * places or activates a block; the actual aim state is driven by the client
 * ({@link com.projecthero.mod.network.FirearmActionPayload}).
 */
public class FirearmItem extends Item {
	private final String firearmId;

	public FirearmItem(String firearmId, Properties properties) {
		super(properties.stacksTo(1));
		this.firearmId = firearmId;
	}

	public String firearmId() {
		return firearmId;
	}

	public FirearmData data() {
		return Firearms.get(firearmId);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		// FAIL, not CONSUME: swallow the right-click so a firearm never places / opens a block, and
		// crucially do NOT trigger the vanilla use-swing (aiming is a hold of right-click and must not
		// look like the player is throwing a punch). Aim state is driven client-side.
		return InteractionResultHolder.fail(player.getItemInHand(hand));
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}

	@Override
	public int getEnchantmentValue() {
		return 0;
	}

	/** No mining / attack speed penalty behaviour -- this is not a tool. */
	@Override
	public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level,
			net.minecraft.core.BlockPos pos, Player player) {
		return false;
	}

	@Override
	public void onCraftedBy(ItemStack stack, Level level, Player player) {
		super.onCraftedBy(stack, level, player);
		if (player instanceof ServerPlayer sp) {
			FirearmHooks.get().onCrafted(sp, firearmId);
		}
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		return true; // melee pistol-whip does default (tiny) damage; nothing special
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		FirearmData d = data();
		if (d == null) {
			return;
		}
		int mag = FirearmStack.magazine(stack, d);
		tooltip.add(Component.translatable("firearm.projecthero.tooltip.ammo",
				Component.translatable("firearm.projecthero.ammo." + d.ammo.lower())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("firearm.projecthero.tooltip.magazine", mag, d.magazineSize)
				.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("firearm.projecthero.tooltip.damage",
				fmt(d.bodyDamage), fmt(d.headDamage)).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable(d.automatic
				? "firearm.projecthero.tooltip.automatic" : "firearm.projecthero.tooltip.semi")
				.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("firearm.projecthero.tooltip.controls").withStyle(ChatFormatting.DARK_GRAY));
	}

	private static String fmt(float f) {
		return f == Math.rint(f) ? Integer.toString((int) f) : Float.toString(f);
	}
}

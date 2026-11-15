package com.projecthero.mod.hideout.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.5 artifact (Symbiote Hive): <b>Hive Heart</b> -- the Hive Mother's still-beating heart. Carried anywhere in your
 * inventory: while you are below half health you regenerate 1 health every {@link #INTERVAL} ticks (2 s). No effect icon,
 * only black wisps. Several hearts don't stack: only the first one in your inventory beats.
 */
public class HiveHeartItem extends Item {
	public static final int INTERVAL = 40;

	public HiveHeartItem(Properties props) {
		super(props);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (!(level instanceof ServerLevel server) || !(entity instanceof Player player) || player.tickCount % INTERVAL != 0) {
			return;
		}
		if (!player.isAlive() || player.getHealth() >= player.getMaxHealth() * 0.5f) {
			return;
		}
		if (firstHeart(player) != stack) {
			return;
		}
		player.heal(1.0f);
		server.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.3, 0.4, 0.3, 0.01);
	}

	private static ItemStack firstHeart(Player player) {
		for (ItemStack s : player.getInventory().items) {
			if (s.getItem() instanceof HiveHeartItem) {
				return s;
			}
		}
		for (ItemStack s : player.getInventory().offhand) {
			if (s.getItem() instanceof HiveHeartItem) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.hive_heart.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.hideout.artifact.from", Component.translatable("structure.projecthero.symbiote_hive"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}

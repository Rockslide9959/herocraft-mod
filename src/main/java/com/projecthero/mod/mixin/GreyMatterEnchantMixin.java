package com.projecthero.mod.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * v0.17.2 Grey Matter revamp, passive Galvan Intellect ("the smartest being in three galaxies"): at an enchanting table he reads
 * all three offers in full -- every enchantment each one would give (the same seeded list the table rolls when he clicks it),
 * written to his chat whenever the offers change. Vanilla shows only one hint per offer. Server side only.
 */
@Mixin(EnchantmentMenu.class)
public abstract class GreyMatterEnchantMixin {
	@Shadow
	@Final
	private Container enchantSlots;
	@Shadow
	@Final
	private ContainerLevelAccess access;
	@Shadow
	@Final
	public int[] costs;

	@Unique
	private String projecthero$lastRead = "";

	@Shadow
	private List<EnchantmentInstance> getEnchantmentList(RegistryAccess registryAccess, ItemStack stack, int slot, int cost) {
		throw new AssertionError();
	}

	@Inject(method = "slotsChanged", at = @At("TAIL"))
	private void projecthero$galvanReadsTheTable(Container container, CallbackInfo ci) {
		EnchantmentMenu self = (EnchantmentMenu) (Object) this;
		this.access.execute((level, pos) -> {
			if (!(level instanceof ServerLevel server)) {
				return;
			}
			ItemStack stack = this.enchantSlots.getItem(0);
			for (ServerPlayer p : server.players()) {
				if (p.containerMenu != self || !GreyMatterKit.galvan(p)) {
					continue;
				}
				StringBuilder key = new StringBuilder();
				List<MutableComponent> lines = new java.util.ArrayList<>();
				for (int i = 0; i < 3; i++) {
					if (stack.isEmpty() || this.costs[i] <= 0) {
						continue;
					}
					List<EnchantmentInstance> list = getEnchantmentList(server.registryAccess(), stack, i, this.costs[i]);
					if (list.isEmpty()) {
						continue;
					}
					MutableComponent line = Component.translatable("message.projecthero.omnitrix.grey_matter.enchant_offer", i + 1, this.costs[i])
							.withStyle(ChatFormatting.AQUA);
					for (int k = 0; k < list.size(); k++) {
						EnchantmentInstance e = list.get(k);
						line.append(Component.literal(k == 0 ? " " : ", ").withStyle(ChatFormatting.GRAY))
								.append(Enchantment.getFullname(e.enchantment, e.level).copy().withStyle(ChatFormatting.WHITE));
						key.append(e.enchantment.getRegisteredName()).append(e.level).append(';');
					}
					key.append('|');
					lines.add(line);
				}
				String read = key.toString();
				if (lines.isEmpty() || read.equals(this.projecthero$lastRead)) {
					this.projecthero$lastRead = lines.isEmpty() ? "" : read;
					continue;
				}
				this.projecthero$lastRead = read;
				p.sendSystemMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.enchant_read").withStyle(ChatFormatting.DARK_AQUA));
				for (MutableComponent line : lines) {
					p.sendSystemMessage(line);
				}
			}
		});
	}
}

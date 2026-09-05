package com.herocraft.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.herocraft.mod.ironman.IronManCrafting;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/**
 * "changes 20": no Iron Man suit comes off a crafting grid unless the crafter has the Tony Stark
 * power. See {@link IronManCrafting} for the policy and why the Arc Reactor / components /
 * blueprints stay open.
 *
 * <p>Targets {@code CraftingMenu.slotChangedCraftingGrid}, which is deliberately the single static
 * helper vanilla routes <em>both</em> crafting grids through -- the 3x3 table and the player's own
 * 2x2 inventory grid ({@code InventoryMenu.slotsChanged} calls it too) -- so one injection covers
 * every recipe-book, shift-click and hand-placed craft. It is also server-only (vanilla's body is
 * wrapped in a client-side check), which makes the gate server-authoritative: a modified client
 * cannot talk the server into filling the slot.
 *
 * <p>Injecting at TAIL rather than HEAD is what keeps this simple. Vanilla resolves the recipe and
 * writes the result itself; by the time we run, {@code resultSlots} holds the real outcome, so the
 * gate is a plain "is this a suit?" question about a finished ItemStack instead of a reimplementation
 * of recipe lookup. The cost is that vanilla has already pushed the filled slot to the client, so
 * blanking the slot has to be followed by the same resync vanilla does -- {@code setRemoteSlot} plus
 * a fresh {@link ClientboundContainerSetSlotPacket} -- or the client would keep showing a phantom
 * suit in an output slot the server considers empty.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
	@Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
	private static void herocraft$gateIronManSuits(AbstractContainerMenu menu, Level level, Player player,
			CraftingContainer craftSlots, ResultContainer resultSlots, RecipeHolder<CraftingRecipe> recipe,
			CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		ItemStack result = resultSlots.getItem(0);
		if (result.isEmpty()) {
			return;
		}

		boolean ironManBlocked = !IronManCrafting.canCraft(serverPlayer, result);
		// v0.8.6: the Punisher tactical armour is only craftable by a player who has completed
		// Vigilante Training (i.e. actually has the Punisher power).
		boolean punisherBlocked = result.getItem() instanceof com.herocraft.mod.punisher.item.PunisherArmorItem
				&& !com.herocraft.mod.punisher.Punisher.hasPower(serverPlayer);
		if (!ironManBlocked && !punisherBlocked) {
			return;
		}

		resultSlots.setItem(0, ItemStack.EMPTY);
		menu.setRemoteSlot(0, ItemStack.EMPTY);
		serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
				menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
		if (punisherBlocked) {
			com.herocraft.mod.punisher.Punisher.warnCraftLocked(serverPlayer);
		} else {
			IronManCrafting.warnLocked(serverPlayer);
		}
	}
}

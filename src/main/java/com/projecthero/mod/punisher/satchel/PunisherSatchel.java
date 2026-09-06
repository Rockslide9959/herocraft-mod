package com.projecthero.mod.punisher.satchel;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherArmorSet;
import com.projecthero.mod.punisher.data.PunisherState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The Tactical Satchel: a persistent 9-slot personal container for the Punisher power (spec / user
 * request). The contents live on {@link PunisherState#satchel} (persistent, {@code copyOnDeath},
 * synced), so they ride the player through death, power swaps and dimension changes -- an Ender Chest
 * bound to a power rather than a location.
 *
 * <p>The container is always intact in the capability data. The <em>GUI</em> (a vanilla one-row chest
 * screen -- no bespoke menu type or texture needed) only opens while the player wears the full
 * Punisher tactical armour. No armour, no access -- but nothing is lost. Every edit writes straight
 * back to the player's state, so contents are safe even if the session ends with the menu open.
 */
public final class PunisherSatchel {
	public static final int SLOTS = 9;

	private PunisherSatchel() {
	}

	/**
	 * Open the satchel GUI for {@code player}. Server-authoritative armour check: a client that asks
	 * without the full set gets a brief action-bar message and nothing else.
	 */
	public static void open(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return;
		}
		if (!PunisherArmorSet.fullSet(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.punisher.satchel_locked")
					.withStyle(ChatFormatting.RED), true);
			player.playNotifySound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.6f, 0.7f);
			return;
		}

		Container container = liveContainer(player);
		player.openMenu(new SimpleMenuProvider(
				(syncId, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, syncId, inv, container, 1),
				Component.translatable("container.projecthero.tactical_satchel")));
		player.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.6f, 0.9f);
	}

	/**
	 * A live 9-slot container seeded from the player's stored satchel. It re-checks the power and the
	 * full tactical armour every tick the menu is open (so stripping the vest closes it), and any
	 * change writes straight back to {@link PunisherState}.
	 */
	private static Container liveContainer(ServerPlayer player) {
		NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		Punisher.state(player).satchel.copyInto(items);
		return new SimpleContainer(items.toArray(new ItemStack[0])) {
			@Override
			public boolean stillValid(Player p) {
				return p == player && Punisher.hasPower(player) && PunisherArmorSet.fullSet(player);
			}

			@Override
			public void setChanged() {
				super.setChanged();
				save(player, this);
			}
		};
	}

	/** Serialise {@code container} back onto the player's persistent state. */
	public static void save(ServerPlayer player, Container container) {
		List<ItemStack> stacks = new ArrayList<>(SLOTS);
		for (int i = 0; i < Math.min(SLOTS, container.getContainerSize()); i++) {
			stacks.add(container.getItem(i));
		}
		PunisherState s = Punisher.state(player).copy();
		s.satchel = ItemContainerContents.fromItems(stacks);
		Punisher.save(player, s);
	}
}

package com.projecthero.mod.armor;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Makes a synthesised power-armour stack (Max Steel, the Symbiote suit, ...) impossible for the
 * player to pull out of its armour slot -- no shift-click, no drag, no number-key swap, no drop key.
 *
 * <h2>Why this exists</h2>
 * Every "suit that equips itself" system in the mod puts a real {@link ItemStack} into an armour slot
 * so it renders and contributes real defence. Without this, nothing stopped a player from simply
 * taking that piece off through the ordinary inventory screen: the mod would then synthesise a
 * <em>replacement</em> piece for the now-empty slot on its next tick (so the wearer never looked
 * unsuited), while the original stayed behind as a real, tradeable, storable, duplicable item --
 * "infinite netherite/diamond-look armour" from repeating the removal. The per-tick "delete any stray
 * copy" sweeps each power already runs are a backstop for edge cases, not a substitute for actually
 * preventing the removal.
 *
 * <h2>The fix is a vanilla mechanic, not a new one</h2>
 * {@code Curse of Binding} is exactly "this armour cannot be removed from its slot except by death or
 * in Creative" -- {@code ArmorSlot.mayPickup} checks
 * {@code EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)} before
 * allowing a pickup/shift-click/swap/drop out of an armour slot. Applying the real
 * {@link Enchantments#BINDING_CURSE} enchantment to a synthesised piece gets that entire, well-tested
 * behaviour for free -- including the one thing every homegrown "cancel the click" approach tends to
 * miss, that a Creative-mode player (an admin testing) can still take it off normally.
 *
 * <p>Binding Curse does <em>not</em> stop the item dropping on death -- callers that synthesise armour
 * must still strip it (or restore the wearer's real armour) as part of their own death handling
 * <em>before</em> vanilla's equipment-drop code runs, exactly as every power here already does.
 */
public final class PowerEquipmentLock {
	private PowerEquipmentLock() {
	}

	/**
	 * Curse {@code stack} with Binding so it cannot be removed from its armour slot while worn. Also
	 * suppresses the enchantment glint and the "Curse of Binding" tooltip line
	 * ({@code ENCHANTMENT_GLINT_OVERRIDE} / {@code HIDE_ADDITIONAL_TOOLTIP}) -- the point is a suit that
	 * looks like itself, not a shimmering piece of loot with an enchant line on it.
	 */
	public static void bind(ServerPlayer player, ItemStack stack) {
		Holder<Enchantment> bindingCurse = player.level().registryAccess()
				.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.BINDING_CURSE);
		stack.enchant(bindingCurse, 1);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
		stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
	}
}

package com.projecthero.mod.item;

import com.projecthero.mod.ironman.item.IronManItems;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Old item ids that saved worlds may still hold, pointed at the item that replaced them. 1.21.1 has no registry aliases, so
 * {@code LegacyItemIdMixin} / {@code LegacyItemIdDefaultedMixin} run every by-id registry lookup through {@link #redirect}:
 * <ul>
 *   <li>v0.15.6: the roman-numeral Iron Man armour ids ({@link IronManItems#legacyId});</li>
 *   <li>v0.17.7: {@code superman_suit_boots} -- the Superman Suit's boots became part of its leggings, so an old pair loads as
 *       a pair of Superman Suit leggings ({@code SupermanSuit.fixMisplacedPieces} moves one still worn in the feet slot).</li>
 * </ul>
 * Kept free of item / class references beyond plain ids: it is first touched from inside vanilla's registry bootstrap.
 */
public final class LegacyItemIds {
	private static final String NAMESPACE = "projecthero";
	/** v0.17.7: the removed Superman Suit boots (the id {@code SupermanSuit.LEGACY_BOOTS_ID}). */
	private static final String SUPERMAN_BOOTS = "superman_suit_boots";
	private static final String SUPERMAN_LEGGINGS = "superman_suit_leggings";

	private LegacyItemIds() {
	}

	/** An old id -> the id of the item that replaced it (anything else unchanged, the same instance). */
	public static ResourceLocation redirect(ResourceLocation id) {
		if (id == null || !NAMESPACE.equals(id.getNamespace())) {
			return id;
		}
		if (SUPERMAN_BOOTS.equals(id.getPath())) {
			return ResourceLocation.fromNamespaceAndPath(NAMESPACE, SUPERMAN_LEGGINGS);
		}
		return IronManItems.legacyId(id);
	}

	/** As {@link #redirect(ResourceLocation)} for a registry key. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static ResourceKey<?> redirect(ResourceKey<?> key) {
		if (key == null) {
			return null;
		}
		ResourceLocation to = redirect(key.location());
		return to == key.location() ? key : ResourceKey.create((ResourceKey) key.registryKey(), to);
	}
}

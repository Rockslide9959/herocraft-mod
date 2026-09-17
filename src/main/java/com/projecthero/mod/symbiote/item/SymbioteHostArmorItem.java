package com.projecthero.mod.symbiote.item;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * One piece of a Normal Symbiote Host's "living black armour" -- a
 * {@link com.projecthero.mod.armor.SuperheroArmorItem}/GeckoLib suit, like every other synthesised
 * power armour in the mod (v0.9.19 shipped it as a plain, fully-transparent vanilla
 * {@code ArmorItem} instead, relying only on {@code SymbioteFxClient}'s shoulder-wisp particles to
 * read as "the Symbiote is active"; that was a deliberate scope cut for the time, now superseded by
 * a real user-supplied model). Renders on its own bespoke {@code geo/symbiote_host.geo.json} +
 * {@code textures/armor/symbiote_host.png} (see {@code SuperheroArmorVisuals}), converted from a
 * Blockbench-supplied rig the same way Green Lantern's suit was.
 *
 * <p>Synthesised onto the player by {@link com.projecthero.mod.symbiote.SymbioteSuit} when a Normal
 * host suits up (H) and removed when it retracts -- never crafted, never in a creative tab, no
 * durability (unbreakable while bonded), curse-of-binding locked via
 * {@link com.projecthero.mod.armor.PowerEquipmentLock#bind} so it cannot be pulled out of its slot.
 */
public class SymbioteHostArmorItem extends SuperheroArmorItem {
	public SymbioteHostArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "symbiote_host";
	}
}

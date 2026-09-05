package com.herocraft.mod.symbiote.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * One piece of a Normal Symbiote Host's "living black armour" -- a plain vanilla {@link ArmorItem},
 * deliberately <b>not</b> a {@link com.herocraft.mod.armor.SuperheroArmorItem}/GeckoLib suit like
 * every other synthesised power armour in the mod. Rendering is vanilla's own
 * {@code HumanoidArmorLayer} off {@code ModArmorMaterials.SYMBIOTE_HOST}'s
 * {@code symbiote_host_layer_1/2.png} textures -- the same technique
 * {@link com.herocraft.mod.ironman.item.RepulsorItem} already uses to skip GeckoLib entirely -- but
 * (v0.9.19) those two textures are now fully transparent on purpose: the spec's "no Spider-Man eyes,
 * no spider logo, no web pattern" silhouette is now taken further, to no cloth silhouette at all.
 * What actually reads as "the Symbiote is active" is the ambient black-particle wisps
 * {@code SymbioteFxClient} draws at the shoulders every tick the suit is worn, not a texture -- the
 * armour rating, curse-of-binding lock and every other real-armour property below are completely
 * unaffected, only what gets drawn on screen changed.
 *
 * <p>Synthesised onto the player by {@link com.herocraft.mod.symbiote.SymbioteSuit} when a Normal
 * host suits up (H) and removed when it retracts -- never crafted, never in a creative tab, no
 * durability (unbreakable while bonded), curse-of-binding locked via
 * {@link com.herocraft.mod.armor.PowerEquipmentLock#bind} so it cannot be pulled out of its slot.
 */
public class SymbioteHostArmorItem extends ArmorItem {
	public SymbioteHostArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}
}

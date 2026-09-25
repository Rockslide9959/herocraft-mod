package com.projecthero.mod.allmight.item;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.armor.ArmorRenderContext;
import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;

/**
 * One piece of the All Might costume (v0.12.33) -- synthesised onto the wearer by {@code AllMightSuit}, never crafted.
 * {@link #armorSetId()} is resolved from the wearer (like Max Steel's): {@code all_might_full} while they are in the
 * full-power form (huge, muscular, caped) and {@code all_might_base} in the contained form (leaner) -- one set of four
 * pieces, two renderings, no second rendering system.
 */
public class AllMightArmorItem extends SuperheroArmorItem {
	public AllMightArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		if (ArmorRenderContext.wearer() instanceof Player wearer && AllMight.isFullPower(wearer)) {
			return "all_might_full";
		}
		return "all_might_base";
	}
}

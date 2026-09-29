package com.projecthero.mod.moonknight.item;

import com.projecthero.mod.armor.ArmorRenderContext;
import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;

/**
 * One piece of the Moon Knight suit, synthesised onto the wearer by {@code MoonKnightSuit} when they transform (H) --
 * never crafted, never dropped. Drawn from the user's own Blockbench models ({@code geo/moon_knight.geo.json} +
 * {@code textures/armor/moon_knight_<alter>.png}, converted by {@code scratchpad/gen_moonknight_alters.js}).
 *
 * <p>Per alter (v0.13.21): while rendering, the set id resolves to {@code moon_knight_<alter>} -- Marc's armour,
 * Steven's Mr. Knight suit or Jake's -- so the suit always matches who is in control.
 */
public class MoonKnightArmorItem extends SuperheroArmorItem {
	public static final String SET_ID = "moon_knight";

	public MoonKnightArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		if (ArmorRenderContext.wearer() instanceof Player p) {
			MoonKnightAlter alter = MoonKnight.alter(p);
			String perAlter = SET_ID + "_" + alter.id();
			if (SuperheroArmorVisuals.has(perAlter)) {
				return perAlter;
			}
		}
		return SET_ID;
	}
}

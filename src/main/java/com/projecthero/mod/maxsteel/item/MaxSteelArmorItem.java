package com.projecthero.mod.maxsteel.item;

import com.projecthero.mod.armor.ArmorRenderContext;
import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.maxsteel.MaxSteelMode;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;

/**
 * A piece of the Max Steel suit. Never crafted, never in a creative tab, never in a loot table -- it
 * is <em>synthesised</em> onto the player when they Go Turbo ({@link com.projecthero.mod.maxsteel.MaxSteelSuitArmor})
 * and deleted on suit-down. It exists only so the suit renders through the mod's shared GeckoLib armour
 * path (the geometry {@code geo/max_steel.geo.json} was authored to GeckoLib's armour bone structure).
 *
 * <p>{@link #armorSetId()} is resolved <em>from the wearer</em>, not baked into the item: it returns
 * {@code "max_steel_<mode>"} while the wearer is in a specialised Turbo Mode (so the model + skin swap
 * to the matching form) and {@code "max_steel"} otherwise. The wearer is handed in through
 * {@link ArmorRenderContext}, which the shared GeckoLib armour renderer fills in before each render.
 * This keeps it to <b>one</b> set of four synthetic pieces rather than six.
 */
public class MaxSteelArmorItem extends SuperheroArmorItem {
	public MaxSteelArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		if (ArmorRenderContext.wearer() instanceof Player wearer && MaxSteel.hasPower(wearer)) {
			MaxSteelMode mode = MaxSteel.mode(wearer);
			if (mode.isSpecialised()) {
				return "max_steel_" + mode.lower();
			}
			if (mode == MaxSteelMode.CANNON) {
				return "max_steel_cannon";
			}
		}
		return "max_steel";
	}
}

package com.projecthero.mod.client.thor;

import java.util.Set;

import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.client.render.ArmorSweepReveal;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorVisuals;
import com.projecthero.mod.thorarmor.ThorArmor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.4: Thor's Armour materialises piece by piece -- boots, then greaves, then the chestplate and cape -- each one
 * sweeping up the body from a bright, crackling blue-white edge ({@link ArmorSweepReveal} restricted to that piece's
 * bones), and dissolves chest-first on the way off. Read off the synced suit clock ({@link ThorFx#suitDir()} /
 * {@link ThorFx#suitStart()}) with {@link ThorArmor#pieceProgress}, so every viewer sees the same thing.
 */
public final class ThorSuitReveal {
	private static final int EDGE = 0xFFE8F6FF;
	private static final int TRAIL = 0xFF6FB8FF;
	private static final ArmorSweepReveal.Sweep FEET = new ArmorSweepReveal.Sweep("thor_feet",
			Set.of("armorRightBoot", "armorLeftBoot"), true, EDGE, TRAIL);
	private static final ArmorSweepReveal.Sweep LEGS = new ArmorSweepReveal.Sweep("thor_legs",
			Set.of("armorRightLeg", "armorLeftLeg"), true, EDGE, TRAIL);
	private static final ArmorSweepReveal.Sweep CHEST = new ArmorSweepReveal.Sweep("thor_chest",
			Set.of("armorBody", "armorRightArm", "armorLeftArm"), true, EDGE, TRAIL);

	private ThorSuitReveal() {
	}

	/** How much of the piece in {@code slot} is showing on {@code player} right now (0..1). */
	public static float progress(Player player, EquipmentSlot slot, float partialTick) {
		ThorFx fx = ThorVisuals.fx(player);
		if (fx.suitDir() == ThorFx.SUIT_NONE) {
			return 1.0f;
		}
		float elapsed = player.level().getGameTime() - fx.suitStart() + partialTick;
		return ThorArmor.pieceProgress(fx.suitDir(), slot, elapsed);
	}

	/** The texture to draw the piece in {@code slot} with right now (the plain one outside a transition). */
	public static ResourceLocation texture(Player player, EquipmentSlot slot, ResourceLocation base, float partialTick) {
		if (slot == null) {
			return base;
		}
		float p = progress(player, slot, partialTick);
		if (p >= 1.0f) {
			return base;
		}
		ArmorVisualDefinition def = SuperheroArmorVisuals.get("thor");
		if (def == null) {
			return base;
		}
		ArmorSweepReveal.Sweep sweep = switch (slot) {
			case FEET -> FEET;
			case LEGS -> LEGS;
			default -> CHEST;
		};
		return ArmorSweepReveal.texture(def.geometry(), base, p, sweep);
	}
}

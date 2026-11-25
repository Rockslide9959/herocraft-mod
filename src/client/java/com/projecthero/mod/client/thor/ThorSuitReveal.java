package com.projecthero.mod.client.thor;

import java.util.Set;

import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.client.render.ArmorSweepReveal;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorVisuals;
import com.projecthero.mod.thorarmor.ThorArmor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.4: Thor's Armour materialises on the suit clock, each part sweeping on behind a bright, crackling blue-white edge
 * ({@link ArmorSweepReveal}), and comes off the same way in reverse. v0.17.7 (user: "remove the skin from it so players can see
 * their skin underneath and keep the helmet", then "just make the suit build itself onto the player like it does right now"): the
 * look is only the winged helmet and the red cape now -- the helmet sweeps up over the head, then the cape unrolls down from the
 * collar ({@link ThorCapeLayer}), while the server calls lightning down round the wearer. Read off the synced suit clock
 * ({@link ThorFx#suitDir()} / {@link ThorFx#suitStart()}) with {@link ThorArmor#partProgress}, so every viewer sees the same thing.
 */
public final class ThorSuitReveal {
	private static final int EDGE = 0xFFE8F6FF;
	private static final int TRAIL = 0xFF6FB8FF;
	private static final ArmorSweepReveal.Sweep HELMET = new ArmorSweepReveal.Sweep("thor_helmet", Set.of("armorHead"), true, EDGE,
			TRAIL);

	private ThorSuitReveal() {
	}

	/** How much of {@code part} ({@link ThorArmor#PART_HELMET} / {@link ThorArmor#PART_CAPE}) is showing right now (0..1). */
	public static float progress(Player player, int part, float partialTick) {
		ThorFx fx = ThorVisuals.fx(player);
		if (fx.suitDir() == ThorFx.SUIT_NONE) {
			return 1.0f;
		}
		float elapsed = player.level().getGameTime() - fx.suitStart() + partialTick;
		return ThorArmor.partProgress(fx.suitDir(), part, elapsed);
	}

	/** The armour texture (the helmet) to draw right now -- the plain one outside a transition. */
	public static ResourceLocation helmetTexture(Player player, ResourceLocation base, float partialTick) {
		float p = progress(player, ThorArmor.PART_HELMET, partialTick);
		if (p >= 1.0f) {
			return base;
		}
		ArmorVisualDefinition def = SuperheroArmorVisuals.get("thor");
		if (def == null) {
			return base;
		}
		return ArmorSweepReveal.texture(def.geometry(), base, p, HELMET);
	}
}

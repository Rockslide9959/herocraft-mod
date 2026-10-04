package com.projecthero.mod.client.ironman;

import java.util.Set;

import org.joml.Vector3f;

import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.client.render.ArmorSweepReveal;
import com.projecthero.mod.ironman.suit.IronManSuitFx;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.21: an Iron Man piece <b>builds on</b> instead of popping in. While a piece's synced lock-on clock
 * ({@link IronManSuitFx}) runs, it is drawn with a frame of {@link ArmorSweepReveal} restricted to that piece's own
 * bones: the plates sweep into place up the limb (boots from the sole, the chest from the waist, the helmet from the
 * jaw) behind a white-hot edge and an orange spark trail, over {@link IronManSuitFx#LOCK_TICKS}. Coming off it plays
 * backwards over {@link IronManSuitFx#RELEASE_TICKS} before the piece leaves the slot. The Mark V's case build
 * ({@link IronManSuitFx#STYLE_CASE}) instead spreads each piece outward from the suitcase in the right hand.
 *
 * <p>Works for every mark: the sweep is built per (mark geometry, mark texture, piece), lazily, once per session.
 * Read off synced state only, so every viewer sees the same frame.
 */
public final class IronManSuitReveal {
	private static final int EDGE = 0xFFFFF4D6;   // white-hot weld line
	private static final int TRAIL = 0xFFFF9A2E;  // orange spark trail just behind it
	/** The right hand (bottom-front of the fist) in armour model units, where the Mark V case is held. */
	private static final Vector3f CASE_HAND = new Vector3f(-6.0f, 12.0f, -1.0f);

	private static final Set<String> HEAD = Set.of("armorHead");
	private static final Set<String> CHEST = Set.of("armorBody", "armorRightArm", "armorLeftArm");
	private static final Set<String> LEGS = Set.of("armorRightLeg", "armorLeftLeg");
	private static final Set<String> FEET = Set.of("armorRightBoot", "armorLeftBoot");

	private static final ArmorSweepReveal.Sweep[] PLATES = {
			new ArmorSweepReveal.Sweep("im_head", HEAD, true, EDGE, TRAIL),
			new ArmorSweepReveal.Sweep("im_chest", CHEST, true, EDGE, TRAIL),
			new ArmorSweepReveal.Sweep("im_legs", LEGS, true, EDGE, TRAIL),
			new ArmorSweepReveal.Sweep("im_feet", FEET, true, EDGE, TRAIL) };
	private static final ArmorSweepReveal.Sweep[] FROM_CASE = {
			new ArmorSweepReveal.Sweep("im_case_head", HEAD, false, EDGE, TRAIL, CASE_HAND),
			new ArmorSweepReveal.Sweep("im_case_chest", CHEST, false, EDGE, TRAIL, CASE_HAND),
			new ArmorSweepReveal.Sweep("im_case_legs", LEGS, false, EDGE, TRAIL, CASE_HAND),
			new ArmorSweepReveal.Sweep("im_case_feet", FEET, false, EDGE, TRAIL, CASE_HAND) };

	private IronManSuitReveal() {
	}

	/** How much of the piece in {@code slot} is built on (0..1), for every viewer alike. */
	public static float progress(Player player, EquipmentSlot slot, float partialTick) {
		if (slot == null || player.level() == null) {
			return 1f;
		}
		return IronManSuitFx.of(player).pieceProgress(slot, player.level().getGameTime(), partialTick);
	}

	/** The texture to draw an Iron Man piece in {@code slot} with right now (the plain one outside a lock-on / release). */
	public static ResourceLocation texture(Player player, String setId, EquipmentSlot slot, ResourceLocation base,
			float partialTick) {
		float p = progress(player, slot, partialTick);
		if (p >= 1f) {
			return base;
		}
		int bit = IronManSuitFx.bit(slot);
		ArmorVisualDefinition def = SuperheroArmorVisuals.get(setId);
		if (bit < 0 || def == null) {
			return base;
		}
		boolean fromCase = IronManSuitFx.of(player).style() == IronManSuitFx.STYLE_CASE;
		return ArmorSweepReveal.texture(def.geometry(), base, p, (fromCase ? FROM_CASE : PLATES)[bit]);
	}
}

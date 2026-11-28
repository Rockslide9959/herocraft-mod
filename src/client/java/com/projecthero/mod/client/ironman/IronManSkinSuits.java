package com.projecthero.mod.client.ironman;

import java.util.Set;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.7 (user: "Use these skins for the iron man suits"): the Iron Man suits drawn as a PlanetMinecraft skin worn skin-tight --
 * Mk I ({@code mark_1}), Mk III ({@code mark_iii}), Mk IV Hydro ({@code mark_4}, + its glass dome), Mk VI ({@code mark_2}, the
 * Renaissance / Heroes Return skin) and Mk VII Silver Centurion ({@code mark_6}); the Mk II Golden Avenger ({@code mark_1_v2}) shares
 * the Mk I's rig. Textures, glowmasks and the thin rigs ({@code geo/<set>.geo.json}: base layer 0.05 px over the body, outer layer
 * at vanilla's overlay inflation) come from {@code scratchpad/v0177/make_im_suits.js}; the sources are listed there.
 *
 * <p>One wide texture per suit serves every wearer: on a slim (3 px arm) wearer {@code SuperheroArmorRenderer} narrows the arm
 * cubes by a pixel (the 4-texel faces squeezed, not cropped), so the build reveal, the gantry part textures, battle damage and the
 * glowmask -- all made from that one texture -- stay aligned. The first-person arm does the same ({@link IronManFirstPersonGauntlets}).
 */
public final class IronManSkinSuits {
	/** The suits on the skin-tight PMC skins. */
	public static final Set<String> SETS = Set.of("mark_1", "mark_1_v2", "mark_iii", "mark_4", "mark_2", "mark_6");
	/** The suit drawn translucent: the Mk IV Hydro's glass dome. */
	public static final String GLASS_HELMET = "mark_4";

	private IronManSkinSuits() {
	}

	public static boolean skinTight(String setId) {
		return SETS.contains(setId);
	}

	/** Does this wearer have slim (3 px) arms? */
	public static boolean slim(Entity wearer) {
		return wearer instanceof AbstractClientPlayer cp && cp.getSkin().model() == PlayerSkin.Model.SLIM;
	}
}

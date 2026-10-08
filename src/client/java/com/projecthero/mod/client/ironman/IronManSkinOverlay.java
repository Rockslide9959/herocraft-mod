package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.suit.IronManMk5Suitcase;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitRemoval;

import net.minecraft.world.entity.player.Player;

/**
 * v0.15.15, explicit user request ("the mark 5 suit-down doesn't show the player's skin second layer until the very end"):
 * the skin's outer layer (hat, jacket, sleeves, trousers) is hidden under an armour slot that holds a superhero piece
 * ({@code PlayerModelMixin}) -- but the Mark 5 suitcase build / fold keeps its pieces in their slots while their texels
 * come and go part by part, so the overlay stayed off over bare skin. While one of those per-texel sequences runs, a
 * body region's overlay shows again as soon as every texel over it is gone (taking it off) / until the first one is on
 * (putting it on) -- like the Symbiote / Max Steel reveals. The Marks 2-7 C retract ({@link IronManSuitRemoval}) gets
 * the same treatment.
 */
public final class IronManSkinOverlay {
	public static final int HAT = 0, JACKET = 1, SLEEVES = 2, PANTS = 3;

	private IronManSkinOverlay() {
	}

	/** Should region {@code region}'s skin overlay show on {@code player} although armour is in its slot right now? */
	public static boolean bare(Player player, int region) {
		if (player.level() == null) {
			return false;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		int kind = fx.poseKind();
		float age = fx.poseAge(player.level().getGameTime(), 0f);
		if (age < 0f) {
			return false;
		}
		if (fx.mk5() && (kind == IronManSuitFx.POSE_MK5_UP || kind == IronManSuitFx.POSE_MK5_DOWN)) {
			float frame = IronManMk5Suitcase.frame(kind == IronManSuitFx.POSE_MK5_UP, age);
			int stage = switch (region) {
				case HAT -> IronManMk5Suitcase.S_HELMET;
				case JACKET -> IronManMk5Suitcase.S_CHEST_TOP;
				case SLEEVES -> IronManMk5Suitcase.S_ARMS;
				default -> IronManMk5Suitcase.S_LEGS;
			};
			// the region's first texel goes on at appear(stage, 0) -- before that (or after it is gone again) it is bare
			return frame < IronManMk5Suitcase.appear(stage, 0f) - 0.5f;
		}
		if (kind == IronManSuitFx.POSE_SLEEK_OFF) {
			int r = switch (region) {
				case HAT -> IronManSuitRemoval.R_HELMET;
				case JACKET -> IronManSuitRemoval.R_TORSO;
				case SLEEVES -> IronManSuitRemoval.R_ARMS;
				default -> IronManSuitRemoval.R_LEGS;
			};
			return IronManSuitRemoval.regionGone(r, age);
		}
		return false;
	}
}

package com.projecthero.mod.client.cutscene;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * v0.17.7 (user: "during the cutscenes I can see my hands, I shouldnt be able to"): one shared switch for hiding the
 * first-person hands and held items while a cutscene plays. Each cutscene registers a supplier that is true while it has the
 * camera; {@code CutsceneHandsMixin} cancels {@code GameRenderer#renderItemInHand} while {@link #hide()} is true. Vanilla only
 * hides the hand in third person, and the cutscene cameras move the view without changing the camera type, so the hands stayed
 * on screen during every cinematic.
 *
 * <p>Registered: the Shazam journey (every scene, descent to home), the Mjolnir lift, the Galactus raid cinematics, the
 * Omnitrix show sequences, and (not a cutscene, same need) the Speed Force Speedster's hold-C set position. (The Miraculous sequences hide the whole GUI and the Alien X hall forces third person, so their
 * hands were already hidden.)
 */
public final class CutsceneHands {
	private static final List<BooleanSupplier> ACTIVE = new CopyOnWriteArrayList<>();

	private CutsceneHands() {
	}

	/** Adds a cutscene: {@code activeCutscene} is true while it has the camera. Call once, at client init. */
	public static void register(BooleanSupplier activeCutscene) {
		ACTIVE.add(activeCutscene);
	}

	/** True while any registered cutscene plays: no first-person hands or items. */
	public static boolean hide() {
		for (BooleanSupplier s : ACTIVE) {
			try {
				if (s.getAsBoolean()) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// a cutscene's state mid-reset: don't take the hands away for it
			}
		}
		return false;
	}
}

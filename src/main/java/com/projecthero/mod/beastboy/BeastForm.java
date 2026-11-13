package com.projecthero.mod.beastboy;

/**
 * v0.17.3 Beast Boy: every shape he can take. Shared contract between the power (BEASTBOY-POWER agent) and the art
 * (BEASTBOY-ART agent), see docs/TEEN_TITANS_DESIGN.md. Width/height are the player's hitbox in the form (blocks);
 * {@code customArt} = drawn by {@code client/beastboy/BeastFormArt}, otherwise a green-tinted vanilla mob model (or the player).
 * Add values freely, but don't rename or remove ones the other side may use.
 */
public enum BeastForm {
	HUMAN(0.6f, 1.8f, 0f, false),
	// context morphs (X / Shift+X)
	CHEETAH(0.6f, 0.7f, 20f, false), // vanilla ocelot, tinted
	HAWK(0.5f, 0.5f, 15f, true), // HQ round: custom art (was the tinted vanilla parrot)
	DOLPHIN(0.9f, 0.6f, 25f, false), // vanilla dolphin, tinted
	FLY(0.2f, 0.2f, 2f, true),
	// sustained forms (V wheel)
	TREX(1.6f, 3.4f, 80f, true),
	GORILLA(1.0f, 2.0f, 50f, true),
	RHINO(1.3f, 1.5f, 50f, true),
	ELEPHANT(1.8f, 2.6f, 70f, true),
	WOLF(0.6f, 0.85f, 30f, true), // fix round: custom art (was the tinted vanilla wolf); sits when crouching
	BEAR(1.4f, 1.4f, 50f, false), // vanilla polar bear, tinted
	PTERODACTYL(1.2f, 1.0f, 40f, true),
	BAT(0.5f, 0.9f, 10f, true), // HQ round: custom art (was the tinted vanilla bat)
	SHARK(1.2f, 0.8f, 40f, true),
	OCTOPUS(1.0f, 0.9f, 40f, true),
	WHALE(3.5f, 2.2f, 120f, true),
	MOUSE(0.3f, 0.3f, 5f, true),
	// ultimate (Shift+Z)
	BEAST(1.0f, 2.6f, 60f, true),
	// split-second quick-morph shapes (human-form attacks); drawn as partial/full flashes
	SNAKE(0.6f, 0.4f, 0f, true),
	TIGER(0.7f, 0.9f, 0f, false); // vanilla ocelot scaled up, tinted

	public final float width;
	public final float height;
	/** The form's own health pool (0 = quick-morph only, no pool). */
	public final float formHealth;
	public final boolean customArt;

	BeastForm(float width, float height, float formHealth, boolean customArt) {
		this.width = width;
		this.height = height;
		this.formHealth = formHealth;
		this.customArt = customArt;
	}

	public static BeastForm byId(int id) {
		BeastForm[] v = values();
		return id >= 0 && id < v.length ? v[id] : HUMAN;
	}
}

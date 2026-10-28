package com.projecthero.mod.flight.landing;

/**
 * v0.17.1: the look of one power's superhero landing ({@link HeroLanding}). Shared by both sides: the server picks the
 * style and plays its effects ({@link HeroLandingFx}); every client poses the lander from the same table
 * ({@code client.flight.HeroLandingPose}). Ids are synced: append only.
 *
 * <p>{@code colourA} is the bright core of the shockwave, {@code colourB} its edge (RGB). {@code holdTicks} is how long
 * the pose holds before the lander rises on their own; {@code lockTicks} is how long moving cannot cut it short (the
 * Silver Surfer's skid would otherwise cancel it at once).
 */
public enum HeroLandingStyle {
	NONE(0, Pose.FIST, 0xFFFFFF, 0xFFFFFF, Element.IMPACT, 16, 4),
	/** Three-point landing, the ring fist punched into the ground: a green hard-light shockwave and a glowing emblem crack. */
	GREEN_LANTERN(1, Pose.FIST, 0xD8FFD0, 0x1FE04A, Element.HARD_LIGHT, 18, 4),
	/** The classic crouched three-point landing: a crater-style dust ring and a heavy shake. */
	KRYPTONIAN(2, Pose.FIST, 0xFFF6E8, 0xB8A890, Element.CRATER, 18, 4),
	/** Hammer-first: Mjolnir / Stormbreaker strikes the ground in a lightning-crackle ring. */
	THOR(3, Pose.HAMMER, 0xF0F8FF, 0x4A8CFF, Element.THUNDER, 18, 4),
	/** A cosmic flare ring in Nova gold and red. */
	NOVA(4, Pose.FIST, 0xFFE27A, 0xE8282E, Element.COSMIC, 16, 4),
	/** Crouched on the board with a skid, then he steps off; a silver cosmic ripple. */
	SILVER_SURFER(5, Pose.BOARD, 0xFFFFFF, 0x9FC4FF, Element.COSMIC, 24, 11),
	/** Max Steel stays blue (user rule): a Turbo energy burst. */
	MAX_STEEL(6, Pose.FIST, 0xDDF6FF, 0x1E90FF, Element.TURBO, 16, 4),
	/** Both boots down hard on the jets, sparks and a red tech ring. */
	CYBORG(7, Pose.CROUCH, 0xFFFFFF, 0xFF2A2A, Element.TECH, 14, 4),
	/** The Flight power: a clean three-point landing in a gust ring. */
	FLIGHT(8, Pose.FIST, 0xFFFFFF, 0xBFE4FF, Element.WIND, 16, 4),
	/** Blue Beetle: the scarab armour's blue energy. */
	BLUE_BEETLE(9, Pose.FIST, 0xC8F8FF, 0x1F7FFF, Element.TECH, 16, 4),
	/** Hellflame's Fire Jet Flight: a fire ring and a scorch. */
	HELLFLAME(10, Pose.CROUCH, 0xFFF0B0, 0xFF5A10, Element.FIRE, 14, 4),
	/** Dark Shadow's Black Fallen Angel: the wings fold as he lands in a pool of shadow. */
	DARK_SHADOW(11, Pose.WINGS, 0xC8A8FF, 0x3A0A6A, Element.SHADOW, 16, 4),
	/** Heatblast's Fire Jet: boots of flame slam down, a scorch ring. */
	HEATBLAST(12, Pose.CROUCH, 0xFFF4C0, 0xFF6A10, Element.FIRE, 14, 4),
	/** Pyrokinesis' Flame Jets. */
	PYRO(13, Pose.CROUCH, 0xFFE8A0, 0xFF4A10, Element.FIRE, 14, 4),
	/** Cryokinesis' Ice Wings: the wings fold, frost cracks out. */
	CRYO(14, Pose.WINGS, 0xF0FFFF, 0x7AD8FF, Element.ICE, 16, 4),
	/** Hydrokinesis' Hydro Jets: a splash ring. */
	HYDRO(15, Pose.CROUCH, 0xE0F6FF, 0x2F8FFF, Element.WATER, 14, 4),
	/** Geokinesis' rock surf / rock flight: earth breaks under the fist. */
	ROCK(16, Pose.FIST, 0xE8D8B8, 0x7A6040, Element.CRATER, 16, 4),
	/** Hawk Moth's Swarm Flight: the swarm settles, purple light. */
	HAWK_MOTH(17, Pose.WINGS, 0xF0D8FF, 0x8A3AD8, Element.SHADOW, 14, 4),
	/** Moon Knight's Cape Glide: the cape folds round him, moon-white. */
	MOON_KNIGHT(18, Pose.WINGS, 0xFFFFFF, 0xC8D4E8, Element.WIND, 16, 4);

	/** How the body lands (client: {@code HeroLandingPose}). */
	public enum Pose {
		/** One knee down, the right fist punched into the ground, the free arm swept back. */
		FIST,
		/** One knee down, the main arm driving what it holds (the hammer) into the ground ahead. */
		HAMMER,
		/** Crouched on the board, one hand down to it, then a step off. */
		BOARD,
		/** One knee down, both arms swept back and up (wings / a cape folding in). */
		WINGS,
		/** Both feet down in a deep crouch, the arms out low for balance (jet landings). */
		CROUCH
	}

	/** The element layer of the effects and sound ({@link HeroLandingFx}). */
	public enum Element {
		IMPACT, HARD_LIGHT, CRATER, THUNDER, COSMIC, TURBO, TECH, WIND, FIRE, SHADOW, ICE, WATER
	}

	public final int id;
	public final Pose pose;
	public final int colourA;
	public final int colourB;
	public final Element element;
	public final int holdTicks;
	public final int lockTicks;

	HeroLandingStyle(int id, Pose pose, int colourA, int colourB, Element element, int holdTicks, int lockTicks) {
		this.id = id;
		this.pose = pose;
		this.colourA = colourA;
		this.colourB = colourB;
		this.element = element;
		this.holdTicks = holdTicks;
		this.lockTicks = lockTicks;
	}

	public static HeroLandingStyle byId(int id) {
		for (HeroLandingStyle s : values()) {
			if (s.id == id) {
				return s;
			}
		}
		return NONE;
	}
}

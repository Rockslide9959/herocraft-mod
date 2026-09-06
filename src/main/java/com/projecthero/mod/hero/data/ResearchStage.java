package com.projecthero.mod.hero.data;

/**
 * Per-power discovery progression (spec section 7). Advances monotonically; once
 * {@link #MUTATION_CONFIRMED} is reached the full serum recipe + trigger are permanently readable in
 * the guide for that player and reproducible for teammates.
 */
public enum ResearchStage {
	UNKNOWN,
	RESEARCH_FOUND,
	SERUM_STABILIZED,
	EXPOSURE_SURVIVED,
	MUTATION_CONFIRMED;

	public static ResearchStage byOrdinal(int ordinal) {
		ResearchStage[] values = values();
		if (ordinal < 0) {
			return UNKNOWN;
		}
		return ordinal >= values.length ? MUTATION_CONFIRMED : values[ordinal];
	}

	public boolean atLeast(ResearchStage other) {
		return ordinal() >= other.ordinal();
	}
}

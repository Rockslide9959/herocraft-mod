package com.projecthero.mod.omnitrix;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import net.minecraft.core.HolderLookup;

/**
 * v0.17.1 (user: "release everything in this update besides ben 10, ben 10 isnt done yet") held the Omnitrix back behind this
 * switch. v0.17.2: it is public, so the switch is on everywhere; the checks stay in place, and the gametests still turn it off.
 *
 * <h2>v0.17.3 release switches (user: "I want to exclude the new Ben 10 aliens and all the transformation scenes from this update")</h2>
 * The code stays in; players just cannot get or see it. To ship it later, flip ONE constant each:
 * <ul>
 *   <li>{@link #PROTOTYPE_ALIENS_0173} ({@link #prototypeAliens0173()}): the six hidden prototype aliens added after v0.17.2 --
 *       {@link #HELD_ALIENS_0173} (Snare-oh, Shock Rock, Frankenstrike, Eye Guy, Benwolf/Blitzwolfer, Arctiguana). Off, they are
 *       filtered out of {@code Aliens} itself (all / recalibrated / dial / playable / found / available / the mistransform draw and
 *       the {@code /omnitrix} discover-all), so the dial, the alien wheel, the I menu, the Guidebook roster and every unlock path skip
 *       them, and {@code Omnitrix.transform} refuses one (a dev world that already found one falls back to the first playable alien
 *       when the watch comes up; one already transformed into one is turned back).</li>
 *   <li>{@link #SHOW_SEQUENCES} ({@link #showSequences()}): the v0.17.3 show transformations (the right-click sequences in
 *       {@code omnitrix/sequence}). Off, {@code ShowSequences.get/has/byHash} only answer for {@link #SHIPPED_SEQUENCES} -- Four
 *       Arms, whose right-click long transformation already shipped in v0.17.2 -- so a right-click on any other alien is the normal
 *       quick slap, exactly as in v0.17.2, and no other soundtrack plays.</li>
 * </ul>
 * Both default to ON inside the gametest server (system property {@code fabric-api.gametest}) so the existing alien / sequence
 * tests keep running; {@code OmnitrixHoldBackGameTests} turns them off for its own (synchronous) checks. Guide:
 * {@code docs/OMNITRIX_SEQUENCES.md}.
 */
public final class OmnitrixFeature {
	private static boolean enabled = true;

	/** v0.17.3 release switch: the six new hidden prototype aliens. {@code false} for v0.17.3; {@code true} to ship them. */
	public static final boolean PROTOTYPE_ALIENS_0173 = false;
	/** v0.17.3 release switch: the new show transformations (all but Four Arms'). {@code false} for v0.17.3; {@code true} to ship them. */
	public static final boolean SHOW_SEQUENCES = false;
	/** The aliens {@link #PROTOTYPE_ALIENS_0173} holds back. */
	public static final java.util.Set<String> HELD_ALIENS_0173 = java.util.Set.of("snare_oh", "shock_rock", "frankenstrike", "eye_guy", "benwolf",
			"arctiguana");
	/** The show sequences that play whatever {@link #SHOW_SEQUENCES} says: Four Arms' shipped in v0.17.2. */
	public static final java.util.Set<String> SHIPPED_SEQUENCES = java.util.Set.of("four_arms");

	private static final boolean IN_GAMETEST = System.getProperty("fabric-api.gametest") != null;
	private static boolean prototypeAliens = PROTOTYPE_ALIENS_0173 || IN_GAMETEST;
	private static boolean showSequences = SHOW_SEQUENCES || IN_GAMETEST;

	private OmnitrixFeature() {
	}

	public static boolean enabled() {
		return enabled;
	}

	/** Gametests only: force the switch off ({@code null} = back on). */
	public static void forceForTests(@Nullable Boolean on) {
		enabled = on == null || on;
	}

	/** v0.17.3: the six new hidden prototype aliens are in the game ({@link #PROTOTYPE_ALIENS_0173}). */
	public static boolean prototypeAliens0173() {
		return prototypeAliens;
	}

	/** v0.17.3: {@code alienId} is one of the held-back aliens and the switch is off. */
	public static boolean heldBack(String alienId) {
		return !prototypeAliens && alienId != null && HELD_ALIENS_0173.contains(alienId);
	}

	/** v0.17.3: the new show transformations play ({@link #SHOW_SEQUENCES}). */
	public static boolean showSequences() {
		return showSequences;
	}

	/** v0.17.3: {@code alienId}'s show transformation may play (always for {@link #SHIPPED_SEQUENCES}). */
	public static boolean sequenceAllowed(String alienId) {
		return showSequences || (alienId != null && SHIPPED_SEQUENCES.contains(alienId));
	}

	/** Gametests only: force the prototype-alien switch ({@code null} = back to the default). */
	public static void forcePrototypeAliensForTests(@Nullable Boolean on) {
		prototypeAliens = on == null ? PROTOTYPE_ALIENS_0173 || IN_GAMETEST : on;
	}

	/** Gametests only: force the show-sequence switch ({@code null} = back to the default). */
	public static void forceShowSequencesForTests(@Nullable Boolean on) {
		showSequences = on == null ? SHOW_SEQUENCES || IN_GAMETEST : on;
	}

	/** The recipe load condition: the Omnitrix recipes only load while the feature is on. */
	public record Condition() implements ResourceCondition {
		public static final MapCodec<Condition> CODEC = MapCodec.unit(Condition::new);
		public static final ResourceConditionType<Condition> TYPE = ResourceConditionType.create(ProjectHeroMod.id("omnitrix_enabled"), CODEC);

		@Override
		public ResourceConditionType<?> getType() {
			return TYPE;
		}

		@Override
		public boolean test(@Nullable HolderLookup.Provider registryLookup) {
			return enabled();
		}
	}

	static void initialize() {
		ResourceConditions.register(Condition.TYPE);
	}
}

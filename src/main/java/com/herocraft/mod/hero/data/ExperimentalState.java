package com.herocraft.mod.hero.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Every bit of experimental-mutation player state, in one object under one namespaced attachment key
 * ({@code herocraft:experimental_state}). Deliberately isolated from all Thor attachments so that
 * saved Thor data can never be read as mutation data or vice versa.
 *
 * <p>Keys used inside the maps/sets:
 * <ul>
 *   <li>owned powers / research: the power's short key path, e.g. {@code power_01_super_strength}</li>
 *   <li>ability cooldowns / toggles / cycles / resources: {@code powerKey + "/" + abilityId}
 *       (resources: {@code powerKey + "/" + resourceName})</li>
 * </ul>
 *
 * <p>Cooldowns are stored as an absolute "ready-at game time" so they survive relog, death, dimension
 * change and power switching unchanged (spec section 20). Toggles/cycles/resources persist too, but
 * are re-validated when a power is activated so a stale toggle can never silently keep an effect on.
 */
public final class ExperimentalState {
	public final Set<String> ownedPowers;
	/** "" means no experimental power is currently mapped to the six slots. */
	public String activePower;
	public final Map<String, Long> abilityReadyAt;
	public final Set<String> activeToggles;
	public final Map<String, Integer> cycleModes;
	public final Map<String, Float> resources;
	/** power key -> research stage ordinal (see {@link ResearchStage}). */
	public final Map<String, Integer> researchStage;
	/**
	 * Named world positions (e.g. the Teleportation power's return marker), stored as
	 * {@link net.minecraft.core.BlockPos#asLong()}. Kept separate from {@link #resources} because
	 * resources are clamped non-negative and cannot hold negative coordinates.
	 */
	public final Map<String, Long> markers;
	/**
	 * The dimension each entry in {@link #markers} was recorded in, as a dimension
	 * {@link net.minecraft.resources.ResourceLocation} string (e.g. {@code minecraft:overworld}).
	 * A marker set in the Nether sends you back to the Nether even if you press the key in the End.
	 */
	public final Map<String, String> markerDims;

	// --- transient (not persisted): the in-progress "drank the serum, now do the exposure" attempt ---
	public transient String pendingMutationPower = "";
	public transient long pendingMutationExpiryTick = 0L;
	/** kinds of energy damage already survived this attempt (for Energy Absorption's two-source trigger). */
	public final transient Set<String> pendingExposureProgress = new HashSet<>();
	/** consecutive ticks the current ambient exposure condition has been satisfied. */
	public transient int pendingExposureTicks = 0;

	public ExperimentalState() {
		this(new HashSet<>(), "", new HashMap<>(), new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
				new HashMap<>(), new HashMap<>());
	}

	public ExperimentalState(Set<String> ownedPowers, String activePower, Map<String, Long> abilityReadyAt,
			Set<String> activeToggles, Map<String, Integer> cycleModes, Map<String, Float> resources,
			Map<String, Integer> researchStage, Map<String, Long> markers, Map<String, String> markerDims) {
		this.ownedPowers = new HashSet<>(ownedPowers);
		this.activePower = activePower == null ? "" : activePower;
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.activeToggles = new HashSet<>(activeToggles);
		this.cycleModes = new HashMap<>(cycleModes);
		this.resources = new HashMap<>(resources);
		this.researchStage = new HashMap<>(researchStage);
		this.markers = new HashMap<>(markers);
		this.markerDims = new HashMap<>(markerDims);
	}

	public ExperimentalState copy() {
		ExperimentalState c = new ExperimentalState(ownedPowers, activePower, abilityReadyAt,
				activeToggles, cycleModes, resources, researchStage, markers, markerDims);
		c.pendingMutationPower = pendingMutationPower;
		c.pendingMutationExpiryTick = pendingMutationExpiryTick;
		c.pendingExposureProgress.addAll(pendingExposureProgress);
		c.pendingExposureTicks = pendingExposureTicks;
		return c;
	}

	public static final Codec<ExperimentalState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.listOf().xmap(HashSet::new, java.util.ArrayList::new)
					.optionalFieldOf("owned_powers", new HashSet<>())
					.forGetter(s -> new HashSet<>(s.ownedPowers)),
			Codec.STRING.optionalFieldOf("active_power", "").forGetter(s -> s.activePower),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.STRING.listOf().xmap(HashSet::new, java.util.ArrayList::new)
					.optionalFieldOf("active_toggles", new HashSet<>())
					.forGetter(s -> new HashSet<>(s.activeToggles)),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("cycle_modes", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.cycleModes)),
			Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("resources", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.resources)),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("research_stage", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.researchStage)),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("markers", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.markers)),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("marker_dims", new HashMap<>())
					.forGetter(s -> new HashMap<>(s.markerDims))
	).apply(instance, ExperimentalState::new));
}

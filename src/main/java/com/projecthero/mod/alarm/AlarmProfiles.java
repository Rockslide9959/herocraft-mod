package com.projecthero.mod.alarm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.7: the start-up registry of {@link AlarmProfile}s and which structures are alarm zones. Filled once from mod
 * initialisers (immutable descriptions: no worlds, entities or block states -- nothing for {@code ServerStateReset}).
 *
 * <pre>
 * AlarmProfiles.register(new AlarmProfile("sky_fortress", 4, 0x6FA8DC, Component.translatable("structure.projecthero.sky_fortress"),
 *         (level, spot, random) -&gt; MyGuards.spawn(level, spot, random), SoundEvents.BELL_BLOCK, 0.6f));
 * AlarmProfiles.registerStructure(ProjectHeroMod.id("sky_fortress"), "sky_fortress");
 * </pre>
 * A registered structure becomes a zone (its whole bounding box) the first time a player walks into it.
 */
public final class AlarmProfiles {
	/** Reinforcement waves per alarm. */
	public static final int WAVES = 2;

	private static final Map<String, AlarmProfile> PROFILES = new LinkedHashMap<>();
	private static final Map<ResourceLocation, String> STRUCTURES = new LinkedHashMap<>();

	private AlarmProfiles() {
	}

	public static synchronized AlarmProfile register(AlarmProfile profile) {
		PROFILES.put(profile.id(), profile);
		return profile;
	}

	/** Makes every start of the structure {@code structureId} an alarm zone running {@code profileId}. */
	public static synchronized void registerStructure(ResourceLocation structureId, String profileId) {
		STRUCTURES.put(structureId, profileId);
	}

	public static AlarmProfile get(String id) {
		return id == null ? null : PROFILES.get(id);
	}

	public static Map<ResourceLocation, String> structures() {
		return Collections.unmodifiableMap(STRUCTURES);
	}

	/**
	 * Mobs per reinforcement wave: 2 / 3 / 4 / 6 for tiers 1-4, plus one per extra player in the zone, at most 10.
	 */
	public static int waveSize(int tier, int players) {
		int base = switch (Mth.clamp(tier, 1, 4)) {
			case 1 -> 2;
			case 2 -> 3;
			case 3 -> 4;
			default -> 6;
		};
		return Math.min(10, base + Math.max(0, players - 1));
	}
}

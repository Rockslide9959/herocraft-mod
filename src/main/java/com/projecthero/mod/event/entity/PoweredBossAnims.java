package com.projecthero.mod.event.entity;

import java.util.Locale;

import com.projecthero.mod.event.entity.RaidAnims.Clip;

/**
 * v0.16.1: how the Powered Zombie Boss model shows its power.
 * <ul>
 *   <li>{@link #forAbility}: which exertion clip an instant ability plays (by its player ability id): ground hits slam,
 *       strikes punch, movement dashes, surges / screams / forms roar, everything else is a thrust-out cast.</li>
 *   <li>{@link #look}: the accent set (bones {@code acc_<set>...} of the model, the rest hidden) and the colour the
 *       emissive pass is tinted with -- the texture's glowing parts are white, so one texture serves all 26 powers.</li>
 * </ul>
 * Common (no client classes) so the gametests can check the mapping.
 */
public final class PoweredBossAnims {
	private PoweredBossAnims() {
	}

	/** The clip an instant ability plays, or null for passives that should not animate (regeneration ticks). */
	public static Clip forAbility(String abilityId) {
		String id = abilityId == null ? "" : abilityId.toLowerCase(Locale.ROOT);
		if (id.isEmpty() || id.equals("regen") || id.endsWith("_toggle")) {
			return null;
		}
		if (has(id, "slam", "quake", "stomp", "ground", "impact", "aftershock", "tectonic", "earth_spike", "crush", "dive_bomb", "orbital", "eruption", "geyser", "hammer", "heavy")) {
			return RaidAnims.BOSS_SLAM;
		}
		if (has(id, "punch", "haymaker", "strike", "touch", "bite", "blade", "whip", "stretch", "rapid_assault", "parry")) {
			return RaidAnims.BOSS_PUNCH;
		}
		if (has(id, "dash", "blink", "leap", "rush", "step", "pounce", "slingshot", "swing", "swap", "bamf", "blitz", "roll", "dodge", "sense", "intangible", "escape")) {
			return RaidAnims.BOSS_DASH;
		}
		if (has(id, "scream", "sonic", "resonance", "armor", "form", "overdrive", "maximum", "overload", "cleanse", "revive", "cloak", "shield", "anchor",
				"zero_density", "flash", "darkness", "inferno", "absolute", "effort", "spore", "overgrowth", "hurricane", "storm", "well", "wave", "detonation", "explosion", "healing", "vacuum", "levitate", "redirect")) {
			return RaidAnims.BOSS_BURST;
		}
		return RaidAnims.BOSS_CAST_RELEASE;
	}

	private static boolean has(String id, String... parts) {
		for (String p : parts) {
			if (id.contains(p)) {
				return true;
			}
		}
		return false;
	}

	/** One power's look: accent set name ("shards", "horns", "coils", "vines", "orbit", "wings", "spines") and RGB tint. */
	public record Look(String accent, int rgb) {
	}

	/** The look of the power {@code powerKey} (e.g. {@code power_08_pyrokinesis}); unknown powers get violet shards. */
	public static Look look(String powerKey) {
		String k = powerKey == null ? "" : powerKey;
		if (k.contains("cryokinesis")) return new Look("shards", 0x8FE0FF);
		if (k.contains("crystalkinesis")) return new Look("shards", 0xE38CFF);
		if (k.contains("geokinesis")) return new Look("shards", 0xFFB24A);
		if (k.contains("energy_absorption")) return new Look("shards", 0xFFE36B);
		if (k.contains("density")) return new Look("shards", 0x9AA8FF);
		if (k.contains("pyrokinesis")) return new Look("horns", 0xFF7A22);
		if (k.contains("shadow")) return new Look("horns", 0xA45BFF);
		if (k.contains("super_strength")) return new Look("horns", 0xFF4A3A);
		if (k.contains("sonic")) return new Look("horns", 0x7CF2FF);
		if (k.contains("electrokinesis")) return new Look("coils", 0x7FB8FF);
		if (k.contains("magnetic")) return new Look("coils", 0xFF5560);
		if (k.contains("laser")) return new Look("coils", 0xFF3030);
		if (k.contains("plant")) return new Look("vines", 0x8CFF5A);
		if (k.contains("regeneration")) return new Look("vines", 0x66FF9A);
		if (k.contains("water")) return new Look("vines", 0x4AB8FF);
		if (k.contains("telekinesis")) return new Look("orbit", 0xD07CFF);
		if (k.contains("gravity")) return new Look("orbit", 0x8A6CFF);
		if (k.contains("teleport")) return new Look("orbit", 0x5CFFE0);
		if (k.contains("wind")) return new Look("orbit", 0xD8FFF2);
		if (k.contains("invisibility") || k.contains("light")) return new Look("orbit", 0xFFF6C8);
		if (k.contains("flight")) return new Look("wings", 0xFFE9A8);
		if (k.contains("super_speed")) return new Look("spines", 0xFFE14A);
		if (k.contains("spider")) return new Look("spines", 0xFF3A5A);
		if (k.contains("elasticity")) return new Look("spines", 0xFF8CD0);
		if (k.contains("size")) return new Look("spines", 0xFFA040);
		if (k.contains("shockwave")) return new Look("spines", 0xB8E8FF);
		return new Look("shards", 0xB070FF);
	}

	/** Every accent set the model carries. */
	public static final String[] ACCENTS = { "shards", "horns", "coils", "vines", "orbit", "wings", "spines" };
}

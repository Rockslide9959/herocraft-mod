package com.projecthero.mod.loot.sites;

import java.util.List;

import com.projecthero.mod.loot.sites.SiteGuards.Kind;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.17.5 Tier 2 Sites: what each of the 19 old sites gets on top of what it already had -- the annex building's
 * {@link Style}, the window glass, how the vault room is secured ({@link Entry}) and the guard squad (a roster drawn in
 * order, {@code min}..{@code max} of them, picked from the piece seed).
 */
public enum SiteTheme {
	DIG_SITE("dig_site", Style.CAMP, null, Entry.HATCH, 4, 5, Kind.THUG, Kind.PISTOL, Kind.THUG, Kind.SHOTGUN, Kind.THUG),
	FALLEN_LANTERN_SITE("fallen_lantern_site", Style.STATION, Blocks.LIME_STAINED_GLASS_PANE, Entry.IRON_DOOR, 3, 4,
			Kind.PARADEMON, Kind.PARADEMON_RANGED, Kind.PARADEMON, Kind.PARADEMON),
	GAMMA_LAB("gamma_lab", Style.MILITARY, Blocks.LIME_STAINED_GLASS_PANE, Entry.IRON_DOOR, 4, 6,
			Kind.SOLDIER, Kind.SOLDIER_MELEE, Kind.SOLDIER_RIFLE, Kind.SOLDIER, Kind.SOLDIER_MELEE, Kind.SOLDIER),
	GEOLOGICAL_SITE("geological_site", Style.CAMP, null, Entry.HATCH, 3, 4, Kind.THUG, Kind.PISTOL, Kind.THUG, Kind.SHOTGUN),
	GOVERNMENT_SITE("government_site", Style.MILITARY, null, Entry.IRON_DOOR, 4, 6,
			Kind.AGENT, Kind.AGENT_SHOTGUN, Kind.AGENT, Kind.SOLDIER_RIFLE, Kind.AGENT, Kind.AGENT_SHOTGUN),
	GRAVEYARD("graveyard", Style.CRYPT, null, Entry.HATCH, 4, 6,
			Kind.RAID_ZOMBIE, Kind.SWORD_SKELETON, Kind.RAID_ZOMBIE, Kind.ARMOURED_ZOMBIE, Kind.SWORD_SKELETON, Kind.RAID_ZOMBIE),
	HYDROSTATIC_FACILITY("hydrostatic_facility", Style.BUNKER, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Entry.IRON_DOOR, 3, 5,
			Kind.PISTOL, Kind.THUG, Kind.SHOTGUN, Kind.THUG, Kind.PISTOL),
	KRYPTONITE_CRATER("kryptonite_crater", Style.STATION, Blocks.GREEN_STAINED_GLASS_PANE, Entry.IRON_DOOR, 3, 5,
			Kind.AGENT, Kind.AGENT_SHOTGUN, Kind.AGENT, Kind.SOLDIER_RIFLE, Kind.AGENT),
	METEOR_IMPACT("meteor_impact", Style.STATION, Blocks.PURPLE_STAINED_GLASS_PANE, Entry.IRON_DOOR, 3, 4,
			Kind.PARADEMON, Kind.PARADEMON, Kind.PARADEMON_RANGED, Kind.PARADEMON),
	MJOLNIR_CRATER("mjolnir_crater", Style.ICE, null, Entry.HATCH, 4, 5,
			Kind.FROST_RAIDER, Kind.FROST_HOUND, Kind.FROST_RAIDER, Kind.FROST_HOUND, Kind.FROST_RAIDER),
	NOVA_POD_SITE("nova_pod_site", Style.STATION, Blocks.BLUE_STAINED_GLASS_PANE, Entry.IRON_DOOR, 3, 4,
			Kind.DRONE, Kind.DRONE, Kind.SENTINEL, Kind.DRONE),
	POWER_STATION("power_station", Style.BUNKER, Blocks.YELLOW_STAINED_GLASS_PANE, Entry.IRON_DOOR, 4, 5,
			Kind.DRONE, Kind.SENTINEL, Kind.DRONE, Kind.DRONE, Kind.SENTINEL),
	RESEARCH_FACILITY("research_facility", Style.LAB, null, Entry.IRON_DOOR, 3, 5,
			Kind.DRONE, Kind.DRONE, Kind.SENTINEL, Kind.DRONE, Kind.DRONE),
	STEEL_CRASH_SITE("steel_crash_site", Style.STATION, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Entry.IRON_DOOR, 3, 4,
			Kind.DRONE, Kind.SENTINEL, Kind.DRONE, Kind.DRONE),
	SYMBIOTE_LAB("symbiote_lab", Style.INFESTED, null, Entry.IRON_DOOR, 4, 5,
			Kind.HOST_HUSK, Kind.HOST_SPIDER, Kind.HOST_VINDICATOR, Kind.HOST_HUSK, Kind.HOST_SPIDER),
	SYMBIOTE_METEOR("symbiote_meteor", Style.INFESTED, null, Entry.HATCH, 3, 5,
			Kind.HOST_HUSK, Kind.HOST_SPIDER, Kind.HOST_HUSK, Kind.HOST_VINDICATOR, Kind.HOST_SPIDER),
	TEMPLE_OF_KHONSHU("temple_of_khonshu", Style.TEMPLE, null, Entry.HATCH, 4, 5,
			Kind.CULTIST, Kind.TOMB_GUARDIAN, Kind.CULTIST, Kind.TOMB_GUARDIAN, Kind.CULTIST),
	TOMB_OF_NABU("tomb_of_nabu", Style.TEMPLE, Blocks.PURPLE_STAINED_GLASS_PANE, Entry.HATCH, 3, 5,
			Kind.CULTIST, Kind.CULTIST, Kind.CULTIST, Kind.CULTIST, Kind.CULTIST),
	VIGILANTE_SAFEHOUSE("vigilante_safehouse", Style.CAMP, null, Entry.IRON_DOOR, 4, 5,
			Kind.THUG, Kind.PISTOL, Kind.SHOTGUN, Kind.THUG, Kind.SNIPER);

	/** The annex building's look and furniture. */
	public enum Style {
		/** white lab wing: brewing stands, cauldron, sea lanterns. */
		LAB,
		/** grey barracks: bunks, barrels, weapon racks. */
		MILITARY,
		/** concrete plant room: pipes, generators, redstone lamps. */
		BUNKER,
		/** timber field camp / shack: tools, crates, a bedroll. */
		CAMP,
		/** prefab research station on a crater rim: instruments, sample crates. */
		STATION,
		/** stone mausoleum: candles, cobwebs, soul lanterns. */
		CRYPT,
		/** sandstone shrine: candles, pots, a hidden crypt below. */
		TEMPLE,
		/** an overrun lab: black biomass, webs, cracked glass. */
		INFESTED,
		/** a Frost Giant ice hut: packed ice, blue ice, snow. */
		ICE
	}

	/** How the vault room is secured. */
	public enum Entry {
		/** a reinforced room in the back corner behind an iron door with a button outside. */
		IRON_DOOR,
		/** a hidden basement under a carpet-covered trapdoor, down a ladder. */
		HATCH
	}

	public final String id;
	public final Style style;
	/** The window glass, or null for the style's own. */
	public final Block window;
	public final Entry entry;
	public final int minGuards;
	public final int maxGuards;
	public final List<Kind> roster;

	SiteTheme(String id, Style style, Block window, Entry entry, int minGuards, int maxGuards, Kind... roster) {
		this.id = id;
		this.style = style;
		this.window = window;
		this.entry = entry;
		this.minGuards = minGuards;
		this.maxGuards = Math.min(maxGuards, roster.length);
		this.roster = List.of(roster);
	}

	/** The theme of a site by its structure id path ({@code "gamma_lab"}), or null. */
	public static SiteTheme byId(String id) {
		for (SiteTheme t : values()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return null;
	}
}

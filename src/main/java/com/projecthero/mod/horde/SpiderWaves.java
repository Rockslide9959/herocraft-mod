package com.projecthero.mod.horde;

import com.projecthero.mod.horde.entity.BroodSpider;
import com.projecthero.mod.horde.entity.HordeEntityTypes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * v0.14.16: what the Spider Horde block sends, wave by wave -- the web-spitting Horde Spider and cave spiders from the
 * start, then the {@link BroodSpider} variants as the waves climb, the elites growing more common towards the end
 * (escorts during the boss fight roll as wave 8):
 * <pre>
 *   kind            from wave  weight
 *   Horde Spider        1       30
 *   cave spider         1       18
 *   Hunter              1       16
 *   Venom Spitter       2       12
 *   Trapdoor Leaper     3       10
 *   Acid Burster        3       10
 *   Ironback Brute      4       8 + wave
 *   Shadow Stalker      5       8
 *   Broodmother         6       3 + wave / 2
 * </pre>
 */
public final class SpiderWaves {
	private SpiderWaves() {
	}

	/** What a roll picks, so the GameTests can check the table without spawning anything. */
	public enum Pick { HORDE_SPIDER, CAVE_SPIDER, HUNTER, VENOM, LEAPER, BURSTER, BRUTE, STALKER, BROODMOTHER }

	public static int weight(Pick p, int wave) {
		return switch (p) {
			case HORDE_SPIDER -> 30;
			case CAVE_SPIDER -> 18;
			case HUNTER -> 16;
			case VENOM -> wave >= 2 ? 12 : 0;
			case LEAPER, BURSTER -> wave >= 3 ? 10 : 0;
			case BRUTE -> wave >= 4 ? 8 + wave : 0;
			case STALKER -> wave >= 5 ? 8 : 0;
			case BROODMOTHER -> wave >= 6 ? 3 + wave / 2 : 0;
		};
	}

	public static Pick pick(RandomSource r, int wave) {
		int total = 0;
		for (Pick p : Pick.values()) {
			total += weight(p, wave);
		}
		int roll = r.nextInt(total);
		for (Pick p : Pick.values()) {
			roll -= weight(p, wave);
			if (roll < 0) {
				return p;
			}
		}
		return Pick.HORDE_SPIDER;
	}

	/** One unplaced spider for wave {@code wave}. */
	public static Mob create(ServerLevel level, int wave) {
		Pick p = pick(level.random, wave);
		return switch (p) {
			case HORDE_SPIDER -> HordeEntityTypes.HORDE_SPIDER.create(level);
			case CAVE_SPIDER -> EntityType.CAVE_SPIDER.create(level);
			default -> {
				BroodSpider s = HordeEntityTypes.BROOD_SPIDER.create(level);
				if (s != null) {
					s.setVariant(BroodSpider.Variant.valueOf(p.name()));
				}
				yield s;
			}
		};
	}
}

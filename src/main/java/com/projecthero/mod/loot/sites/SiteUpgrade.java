package com.projecthero.mod.loot.sites;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * v0.17.5 Tier 2 Sites: the shared upgrade every one of the 19 old sites calls from its structure's
 * {@code generatePieces}, right after adding its own piece:
 * <pre>{@code SiteUpgrade.addAnnex(builder, context, mainPiece.getBoundingBox(), SiteTheme.GAMMA_LAB);}</pre>
 * It plans a {@link SiteAnnexPiece} just outside the site's bounding box (3 blocks clear of it, on the flattest dry
 * side, its door facing the site), so the original build -- and whatever power / item / event it gives -- is untouched.
 * See {@code docs/LOOT_PROGRESSION_REFERENCE.md} (Tier 2 Sites).
 */
public final class SiteUpgrade {
	public static final StructurePieceType ANNEX_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
			ResourceKey.create(Registries.STRUCTURE_PIECE, ProjectHeroMod.id("site_annex")), SiteAnnexPiece::new);

	/** Blocks of clear ground between the site's bounding box and the annex porch. */
	static final int GAP = 3;

	private SiteUpgrade() {
	}

	/** v0.17.7: the ward stone (the old-world sites' tripwire sensor). */
	public static SiteWardBlock WARD;
	public static net.minecraft.world.item.BlockItem WARD_ITEM;

	/**
	 * Loads the class (registers the piece type); v0.17.7: the ward stone, and every site as an alarm zone (profile
	 * {@code site/<id>}, tier 2; waves are the site's own guard kinds). Called from {@code LootMod.initialize}.
	 */
	public static void initialize() {
		WARD = Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("site_ward")),
				new SiteWardBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
						.mapColor(net.minecraft.world.level.material.MapColor.STONE).strength(-1.0f, 3600000.0f)
						.sound(net.minecraft.world.level.block.SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 4).noLootTable()));
		WARD_ITEM = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id("site_ward")),
				new net.minecraft.world.item.BlockItem(WARD, new net.minecraft.world.item.Item.Properties()));
		for (SiteTheme theme : SiteTheme.values()) {
			boolean old = theme.style == SiteTheme.Style.CRYPT || theme.style == SiteTheme.Style.TEMPLE || theme.style == SiteTheme.Style.ICE;
			com.projecthero.mod.alarm.AlarmProfiles.register(new com.projecthero.mod.alarm.AlarmProfile(profileId(theme), 2, theme.colour(),
					net.minecraft.network.chat.Component.translatable("structure.projecthero." + theme.id),
					(level, spot, random) -> {
						SiteGuards.Kind kind = theme.roster.get(random.nextInt(theme.roster.size()));
						net.minecraft.world.entity.Mob m = SiteGuards.spawn(level, kind, spot, random.nextFloat() * 360f, 12);
						if (m != null) {
							level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, m.getX(), m.getY() + 0.8, m.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
						}
						return m;
					}, old ? net.minecraft.sounds.SoundEvents.BELL_BLOCK : net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), old ? 0.5f : 0.7f));
			com.projecthero.mod.alarm.AlarmProfiles.registerStructure(ProjectHeroMod.id(theme.id), profileId(theme));
		}
	}

	/** The alarm profile id of a site: {@code site/<id>}. */
	public static String profileId(SiteTheme theme) {
		return "site/" + theme.id;
	}

	/** Adds the site's annex (vault room + guards) beside {@code site}. */
	public static SiteAnnexPiece addAnnex(StructurePiecesBuilder builder, Structure.GenerationContext context, BoundingBox site, SiteTheme theme) {
		SiteAnnexPiece piece = plan(theme, site, context.chunkGenerator(), context.heightAccessor(), context.randomState());
		builder.addPiece(piece);
		return piece;
	}

	/**
	 * Picks the side: each of the four (in a seed-chosen order) is scored on the terrain under the annex -- the
	 * height range across its corners plus three times any water depth -- and the lowest score wins. The floor is the
	 * median ground height of the five samples (one block above the water on a wet side).
	 */
	public static SiteAnnexPiece plan(SiteTheme theme, BoundingBox site, ChunkGenerator gen, LevelHeightAccessor heights, RandomState rs) {
		int mcx = (site.minX() + site.maxX()) / 2;
		int mcz = (site.minZ() + site.maxZ()) / 2;
		long seed = (mcx * 341873128712L) ^ (mcz * 132897987541L) ^ ((theme.ordinal() + 1) * 0x9E3779B97F4A7C15L);
		int first = (int) Math.floorMod(seed >>> 5, 4L);
		int lateral = (int) Math.floorMod(seed >>> 9, 7L) - 3;
		int reach = GAP + SiteAnnexPiece.PORCH;

		int bestScore = Integer.MAX_VALUE, bestX = 0, bestZ = 0, bestRot = 0, bestFloor = 0;
		for (int k = 0; k < 4; k++) {
			int side = (first + k) % 4;
			int ax, az, rot;
			switch (side) {
				case 0 -> { // north of the site, door facing south
					ax = mcx + lateral;
					az = site.minZ() - reach;
					rot = 0;
				}
				case 1 -> { // east, door facing west
					ax = site.maxX() + reach;
					az = mcz + lateral;
					rot = 1;
				}
				case 2 -> { // south, door facing north
					ax = mcx + lateral;
					az = site.maxZ() + reach;
					rot = 2;
				}
				default -> { // west, door facing east
					ax = site.minX() - reach;
					az = mcz + lateral;
					rot = 3;
				}
			}
			int[] samples = new int[5];
			int water = 0;
			// v0.17.7: the back corners sit behind the new back wing
			int[][] pts = { { 0, 0 }, { -SiteAnnexPiece.HX, -SiteAnnexPiece.BACK }, { SiteAnnexPiece.HX, -SiteAnnexPiece.BACK },
					{ -SiteAnnexPiece.HX, SiteAnnexPiece.HZ }, { SiteAnnexPiece.HX, SiteAnnexPiece.HZ } };
			for (int i = 0; i < pts.length; i++) {
				int x = ax + rotX(rot, pts[i][0], pts[i][1]);
				int z = az + rotZ(rot, pts[i][0], pts[i][1]);
				int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, rs);
				int ground = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, rs);
				samples[i] = surface;
				water = Math.max(water, surface - ground);
			}
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int s : samples) {
				lo = Math.min(lo, s);
				hi = Math.max(hi, s);
			}
			int score = (hi - lo) + water * 3;
			if (score < bestScore) {
				int[] sorted = samples.clone();
				java.util.Arrays.sort(sorted);
				bestScore = score;
				bestX = ax;
				bestZ = az;
				bestRot = rot;
				bestFloor = sorted[2];
			}
		}
		return new SiteAnnexPiece(theme, bestX, bestFloor, bestZ, bestRot, seed);
	}

	/**
	 * Builds a whole annex at once (tests, harness). v0.17.7: with no structure start on a live build, the annex's box is
	 * registered as the site's alarm zone by hand (a rebuild starts a fresh zone).
	 */
	public static void buildAll(ServerLevel level, SiteAnnexPiece piece) {
		piece.build(level, piece.getBoundingBox());
		com.projecthero.mod.alarm.AlarmZones zones = com.projecthero.mod.alarm.AlarmZones.get(level);
		zones.removeWithin(piece.getBoundingBox());
		zones.register(piece.getBoundingBox(), profileId(piece.theme()));
	}

	private static int rotX(int rotation, int dx, int dz) {
		return switch (rotation) {
			case 1 -> -dz;
			case 2 -> -dx;
			case 3 -> dz;
			default -> dx;
		};
	}

	private static int rotZ(int rotation, int dx, int dz) {
		return switch (rotation) {
			case 1 -> dx;
			case 2 -> -dz;
			case 3 -> -dx;
			default -> dz;
		};
	}
}

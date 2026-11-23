package com.projecthero.mod.temple.worldgen;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.AlarmLampBlock;
import com.projecthero.mod.alarm.block.AlarmPanelBlock;
import com.projecthero.mod.alarm.block.AlarmStyle;
import com.projecthero.mod.alarm.block.BlastDoorBlock;
import com.projecthero.mod.hideout.worldgen.HideoutCanvas;
import com.projecthero.mod.temple.TempleEntityTypes;
import com.projecthero.mod.temple.TempleGuards;
import com.projecthero.mod.temple.block.CanopicJarBlock;
import com.projecthero.mod.temple.block.CrumblingFloorBlock;
import com.projecthero.mod.temple.block.CrusherCoreBlockEntity;
import com.projecthero.mod.temple.block.DartTrapBlock;
import com.projecthero.mod.temple.block.GlyphPlateBlock;
import com.projecthero.mod.temple.block.SunLensBlock;
import com.projecthero.mod.temple.block.TempleBlocks;
import com.projecthero.mod.temple.block.TempleGateBlock;
import com.projecthero.mod.temple.block.TempleMirrorBlock;
import com.projecthero.mod.temple.block.TempleSealBlock;
import com.projecthero.mod.temple.block.TempleSealBlockEntity;
import com.projecthero.mod.temple.block.TempleVaultSealBlock;
import com.projecthero.mod.temple.block.WardGongBlock;
import com.projecthero.mod.temple.entity.AnkhatepBoss;
import com.projecthero.mod.temple.entity.TombSentinelBoss;
import com.projecthero.mod.temple.item.TempleItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: draws the whole <b>Ancient Temple</b> on a {@link HideoutCanvas} (local coordinates: the entrance faces local
 * north, {@code y = 0} is the surface floor; every write rotated and clipped to the chunk being generated; mobs spawned
 * only by the chunk that holds their feet). The temple runs south (+z) and down:
 * <ol>
 * <li><b>Surface</b> (y 0..18, z -18..10): a three-tier ziggurat on a platform, a 5-wide doorway between obelisks and
 * braziers, a hypostyle hall inside with the pilgrim's lectern and the stairwell down.</li>
 * <li><b>Antechamber</b> (floor y -12): the vestibule (ward stone #1, Shield-bearer, Runner), the <b>Dart Hall</b>
 * (knee / head-high dart traps, ward plates, a gong tripwire), the <b>Collapsing Floor</b> over a revenant pit (one solid
 * ledge along the east wall).</li>
 * <li><b>Hall of Trials</b> (z 50..81): the glyph plates + candle mural (west), the Sun Lens + three mirrors (east), the
 * weight altar and the urns (centre), the three Seals before the trials gate; ward stone #2, a sniper's dais.</li>
 * <li><b>Crushing Corridor</b> (z 81..112): 30 long, a hurdle and a crouch slab, then a stair down.</li>
 * <li><b>Flooded Crypt</b> (floor y -20, z 119..143): knee-deep water, sarcophagi, the Tomb Sentinel; its gate opens when he
 * falls.</li>
 * <li><b>Hall of the Bound</b> (z 143..156): the Captain (the seal key), Medic, Runner, Sniper, ward stone #3, the locked
 * ancient Blast Door.</li>
 * <li><b>Throne of the Bound King</b> (floor y -22, z 156..187): pillars, hanging chains, four canopic jars, the throne,
 * Ankhatep; behind it the Treasury (three sealed vaults).</li>
 * </ol>
 */
public final class TempleBuilder {
	public static final TempleBuilder INSTANCE = new TempleBuilder();

	public final int minX = -17;
	public final int maxX = 17;
	public final int minY = -24;
	public final int maxY = 20;
	public final int minZ = -20;
	public final int maxZ = 196;

	public static final int F = -12;
	public static final int CRYPT_F = -20;
	public static final int THRONE_F = -22;

	public static final BlockPos SEAL_GLYPH = new BlockPos(-4, -10, 78);
	public static final BlockPos SEAL_WEIGHT = new BlockPos(0, -10, 78);
	public static final BlockPos SEAL_LIGHT = new BlockPos(4, -10, 78);
	public static final BlockPos[] GLYPH_PLATES = { new BlockPos(-10, -11, 58), new BlockPos(-7, -11, 58), new BlockPos(-10, -11, 61),
			new BlockPos(-7, -11, 61) };
	/** Mural carvings: order position k (0..3) is carved at MURAL[k] with k + 1 candles below. */
	public static final BlockPos[] MURAL = { new BlockPos(-13, -9, 63), new BlockPos(-13, -9, 61), new BlockPos(-13, -9, 59),
			new BlockPos(-13, -9, 57) };
	public static final BlockPos LENS = new BlockPos(13, -10, 58);
	public static final BlockPos[] MIRRORS = { new BlockPos(7, -10, 58), new BlockPos(7, -10, 70), new BlockPos(4, -10, 70) };
	/** The local facing each mirror needs (the solution), and the one it starts in. */
	public static final Direction[] MIRROR_SOLVED = { Direction.EAST, Direction.WEST, Direction.EAST };
	public static final Direction[] MIRROR_START = { Direction.NORTH, Direction.SOUTH, Direction.WEST };
	public static final BlockPos DECOY_MIRROR = new BlockPos(10, -10, 66);
	public static final BlockPos ALTAR = new BlockPos(0, -11, 72);
	public static final BlockPos[] URNS = { new BlockPos(-11, -11, 70), new BlockPos(11, -11, 64), new BlockPos(-11, -11, 76),
			new BlockPos(11, -11, 76), new BlockPos(-4, -11, 53), new BlockPos(4, -11, 53) };
	/** What each urn holds: a weight 1-4, or 0 for junk. */
	public static final int[] URN_WEIGHTS = { 3, 1, 4, 2, 0, 0 };
	public static final BlockPos CRUSHER_CORE = new BlockPos(0, -12, 82);
	public static final int CORRIDOR_LENGTH = 30;
	public static final BlockPos SENTINEL_HOME = new BlockPos(0, -20, 131);
	public static final BlockPos KING_HOME = new BlockPos(0, -21, 175);
	public static final BlockPos[] JARS = { new BlockPos(-9, -20, 165), new BlockPos(9, -20, 165), new BlockPos(-9, -20, 175),
			new BlockPos(9, -20, 175) };
	public static final BlockPos[] VAULTS = { new BlockPos(-2, -21, 192), new BlockPos(0, -21, 192), new BlockPos(2, -21, 192) };
	public static final BlockPos TRIALS_GATE = new BlockPos(0, -10, 81);
	public static final BlockPos CRYPT_GATE = new BlockPos(0, -18, 143);
	public static final BlockPos VAULT_GATE = new BlockPos(0, -20, 187);
	public static final BlockPos BLAST_DOOR = new BlockPos(0, -18, 156);
	public static final BlockPos[] PANELS = { new BlockPos(5, -11, 12), new BlockPos(12, -11, 66), new BlockPos(-7, -19, 148) };
	public static final BlockPos GONG_DART_HALL = new BlockPos(-3, -11, 34);
	public static final BlockPos GONG_TRIALS = new BlockPos(-2, -11, 52);
	public static final BlockPos LECTERN_GUIDE = new BlockPos(-3, 1, -9);
	public static final BlockPos LECTERN_TRIALS = new BlockPos(-11, -11, 66);
	/** Dart traps: x, y, z, facing (0 east / 1 west), high (0/1). */
	public static final int[][] DARTS = { { -3, -11, 19, 0, 0 }, { 3, -10, 22, 1, 1 }, { 3, -11, 25, 1, 0 }, { -3, -10, 27, 0, 1 },
			{ -3, -11, 31, 0, 0 }, { 3, -10, 32, 1, 1 }, { -7, -10, 43, 0, 1 } };
	public static final int[][] WARD_PLATES = { { -1, -11, 21 }, { 1, -11, 24 }, { 0, -11, 29 }, { -2, -11, 33 }, { 2, -11, 33 }, { -2, -11, 71 },
			{ 2, -11, 74 }, { 0, -19, 146 } };

	private TempleBuilder() {
	}

	/** The glyph order for this temple (a permutation of 0..3 from the seed): the mural and the seal agree on it. */
	public static int[] glyphOrder(HideoutCanvas c) {
		int[] o = { 0, 1, 2, 3 };
		for (int i = 3; i > 0; i--) {
			int j = (int) (c.noise(i, 0, 900 + i) * (i + 1));
			int t = o[i];
			o[i] = o[j];
			o[j] = t;
		}
		if (o[0] == 0 && o[1] == 1 && o[2] == 2 && o[3] == 3) {
			o = new int[] { 2, 0, 3, 1 }; // never the plain order
		}
		return o;
	}

	/** Which glyph each plate in {@link #GLYPH_PLATES} shows. */
	public static int[] plateGlyphs(HideoutCanvas c) {
		int[] g = { 0, 1, 2, 3 };
		for (int i = 3; i > 0; i--) {
			int j = (int) (c.noise(i, 0, 950 + i) * (i + 1));
			int t = g[i];
			g[i] = g[j];
			g[j] = t;
		}
		return g;
	}

	public void build(HideoutCanvas c, TemplePalette p) {
		surface(c, p);
		stairwell(c, p);
		shells(c, p);
		openings(c, p);
		vestibule(c, p);
		dartHall(c, p);
		collapse(c, p);
		hall(c, p);
		corridor(c, p);
		crypt(c, p);
		guardRoom(c, p);
		throne(c, p);
		treasury(c, p);
	}

	// ================================================================ helpers

	/** A hollow room: palette walls / floor / ceiling, air inside. */
	private void shell(HideoutCanvas c, TemplePalette p, int x0, int y0, int z0, int x1, int y1, int z1) {
		c.fill(x0, y0, z0, x1, y1, z1, a -> {
			int x = a[0], y = a[1], z = a[2];
			boolean edge = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
			if (!edge) {
				return Blocks.AIR.defaultBlockState();
			}
			return y == y0 ? p.floor(c, x, y, z) : p.wall(c, x, y, z);
		});
	}

	private Mob guard(HideoutCanvas c, TempleGuards.Role role, int x, int y, int z, Direction facing, int leash, GuardRole ar) {
		BlockPos at = c.at(x, y, z);
		if (!c.inClip(at)) {
			return null;
		}
		return TempleGuards.spawn(c.level, role, at, c.yaw(facing), leash, ar);
	}

	private void lamp(HideoutCanvas c, int x, int y, int z) {
		c.set(x, y, z, AlarmBlocks.LAMP.defaultBlockState().setValue(AlarmLampBlock.STYLE, AlarmStyle.ANCIENT));
	}

	private void panel(HideoutCanvas c, BlockPos l, Direction facing) {
		c.set(l.getX(), l.getY(), l.getZ(), AlarmBlocks.PANEL.defaultBlockState().setValue(AlarmPanelBlock.FACING, facing)
				.setValue(AlarmPanelBlock.STYLE, AlarmStyle.ANCIENT));
	}

	private void gate(HideoutCanvas c, int x0, int y0, int z, int x1, int y1, TempleGateBlock.Group g) {
		c.fill(x0, y0, z, x1, y1, z, TempleBlocks.GATE.defaultBlockState().setValue(TempleGateBlock.GATE, g));
	}

	private void brazier(HideoutCanvas c, TemplePalette p, int x, int y, int z, boolean soul) {
		c.set(x, y, z, p.trim());
		c.set(x, y + 1, z, (soul ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).defaultBlockState().setValue(CampfireBlock.LIT, true)
				.setValue(CampfireBlock.SIGNAL_FIRE, false));
	}

	private void hangingLantern(HideoutCanvas c, int x, int yCeil, int z, int chain, boolean soul) {
		for (int i = 1; i <= chain; i++) {
			c.set(x, yCeil - i, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
		}
		c.set(x, yCeil - chain - 1, z, (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, true));
	}

	private void carving(HideoutCanvas c, int x, int y, int z, int glyph) {
		c.set(x, y, z, TempleBlocks.GLYPH_CARVING.defaultBlockState().setValue(GlyphPlateBlock.GLYPH, glyph & 3));
	}

	private void wardPlate(HideoutCanvas c, int x, int y, int z) {
		c.set(x, y, z, TempleBlocks.WARD_PLATE.defaultBlockState());
	}

	private void dart(HideoutCanvas c, int x, int y, int z, Direction facing, boolean high) {
		c.set(x, y, z, TempleBlocks.DART_TRAP.defaultBlockState().setValue(DartTrapBlock.FACING, facing).setValue(DartTrapBlock.HIGH, high));
	}

	private void lectern(HideoutCanvas c, BlockPos l, Direction facing, ItemStack book) {
		BlockPos at = c.at(l.getX(), l.getY(), l.getZ());
		if (!c.inClip(at)) {
			return;
		}
		c.set(l.getX(), l.getY(), l.getZ(), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK, true));
		if (c.level.getBlockEntity(at) instanceof LecternBlockEntity lec) {
			lec.setBook(book);
		}
	}

	/** A book of translatable pages {@code <prefix>.p1 .. pN}. */
	public static ItemStack book(String prefix, String title, int pages) {
		List<Filterable<Component>> list = new ArrayList<>();
		for (int i = 1; i <= pages; i++) {
			list.add(Filterable.passThrough(Component.translatable(prefix + ".p" + i)));
		}
		ItemStack b = new ItemStack(Items.WRITTEN_BOOK);
		b.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), "?", 0, list, true));
		return b;
	}

	public static ItemStack guideBook() {
		return book("projecthero.temple.book.guide", "The Pilgrim's Warning", 4);
	}

	public static ItemStack trialsBook() {
		return book("projecthero.temple.book.trials", "The Three Trials", 4);
	}

	private void urn(HideoutCanvas c, BlockPos l, int weight) {
		BlockPos at = c.at(l.getX(), l.getY(), l.getZ());
		if (!c.inClip(at)) {
			return;
		}
		c.set(l.getX(), l.getY(), l.getZ(), Blocks.DECORATED_POT.defaultBlockState());
		if (c.level.getBlockEntity(at) instanceof DecoratedPotBlockEntity pot) {
			pot.setTheItem(weight > 0 ? new ItemStack(TempleItems.weight(weight)) : new ItemStack(weight == 0 ? Items.BONE : Items.GOLD_NUGGET, 3));
		}
	}

	private void vinesOn(HideoutCanvas c, TemplePalette p, int x, int y0, int y1, int z, Direction attachedTo, int salt) {
		if (p.desert) {
			return;
		}
		int len = (int) (c.noise(x, z, salt) * (y1 - y0 + 1));
		for (int y = y1; y > y1 - len; y--) {
			c.set(x, y, z, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(attachedTo), true));
		}
	}

	// ================================================================ surface

	private void surface(HideoutCanvas c, TemplePalette p) {
		// clear the site (trees, hills) and lay the platform with footing
		c.air(-17, 1, -20, 17, 22, 11);
		for (int x = -16; x <= 16; x++) {
			for (int z = -19; z <= 10; z++) {
				boolean rim = x == -16 || x == 16 || z == -19 || z == 10;
				c.set(x, 0, z, rim ? p.trim() : p.floor(c, x, 0, z));
				c.footing(x, -1, z, 14, p.wall(c, x, -1, z));
			}
		}
		for (int x = -17; x <= 17; x++) {
			for (int z = -20; z <= 11; z++) {
				if (x == -17 || x == 17 || z == -20 || z == 11) {
					c.footing(x, 0, z, 6, p.ground());
				}
			}
		}
		// tier 1: the hypostyle hall
		shell(c, p, -13, 0, -14, 13, 7, 8);
		c.fill(-13, 7, -14, 13, 7, 8, p.trim());
		c.fill(-13, 5, -14, 13, 5, -14, p.band());
		c.fill(-13, 6, -14, 13, 6, -14, p.band2());
		for (int x = -12; x <= 12; x += 2) {
			carving(c, x, 6, -14, (x / 2) & 3);
		}
		for (int x : new int[] { -13, -9, -5, 5, 9, 13 }) {
			c.fill(x, 1, -15, x, 6, -15, p.pillar()); // pilasters on the facade
			c.set(x, 7, -15, p.trim());
		}
		// the doorway between braziers and two obelisks
		c.air(-2, 1, -14, 2, 5, -14);
		c.fill(-3, 6, -15, 3, 6, -15, p.gold());
		c.fill(-3, 1, -15, -3, 5, -15, p.trim());
		c.fill(3, 1, -15, 3, 5, -15, p.trim());
		brazier(c, p, -5, 1, -17, false);
		brazier(c, p, 5, 1, -17, false);
		for (int x : new int[] { -10, 10 }) {
			c.fill(x - 1, 1, -18, x + 1, 1, -16, p.trim());
			c.fill(x, 2, -17, x, 10, -17, p.pillar());
			for (int y = 3; y <= 9; y += 2) {
				carving(c, x, y, -17, y & 3);
			}
			c.set(x, 11, -17, p.gold());
			vinesOn(c, p, x, 2, 9, -18, Direction.SOUTH, 31 + x);
		}
		// tier 2 (solid) and the tier 3 shrine with its brazier
		c.fill(-10, 8, -11, 10, 12, 5, a -> a[1] == 12 ? p.trim() : p.wall(c, a[0], a[1], a[2]));
		c.fill(-10, 10, -11, 10, 10, -11, p.band());
		shell(c, p, -6, 12, -8, 6, 18, 2);
		c.fill(-6, 18, -8, 6, 18, 2, p.trim());
		c.air(-2, 13, -8, 2, 16, -8);
		c.set(-3, 13, -8, p.pillar());
		c.set(3, 13, -8, p.pillar());
		c.fill(-3, 14, -8, -3, 16, -8, p.pillar());
		c.fill(3, 14, -8, 3, 16, -8, p.pillar());
		c.fill(-3, 17, -9, 3, 17, -9, p.gold());
		brazier(c, p, 0, 12, -3, true);
		c.set(0, 19, -3, p.gold());
		for (int x = -12; x <= 12; x += 3) {
			vinesOn(c, p, x, 1, 6, -15, Direction.SOUTH, 40 + x);
		}
		for (int z = -12; z <= 6; z += 3) {
			vinesOn(c, p, -14, 1, 6, z, Direction.EAST, 60 + z);
			vinesOn(c, p, 14, 1, 6, z, Direction.WEST, 80 + z);
		}
		if (p.desert) {
			// sand drifting against the walls
			for (int x = -16; x <= 16; x++) {
				for (int z = -19; z <= 10; z++) {
					boolean nearWall = (x >= -14 && x <= 14 && (z == -15 || z == 9)) || ((x == -14 || x == 14) && z >= -15 && z <= 9);
					if (nearWall && c.noise(x, z, 77) < 0.45 && Math.abs(x) > 3) {
						c.set(x, 1, z, Blocks.SAND.defaultBlockState());
					}
				}
			}
		} else {
			for (int x = -16; x <= 16; x++) {
				for (int z = -19; z <= 10; z++) {
					if (c.noise(x, z, 78) < 0.18 && c.get(x, 1, z).isAir()) {
						c.set(x, 0, z, Blocks.MOSS_BLOCK.defaultBlockState());
					}
				}
			}
		}
		// inside: pillars, frieze, lights, the lectern
		for (int x = -12; x <= 12; x++) {
			for (int z = -13; z <= 7; z++) {
				c.set(x, 0, z, (x + z) % 4 == 0 ? p.smooth() : p.floor(c, x, 0, z));
			}
		}
		for (int x : new int[] { -7, 7 }) {
			for (int z : new int[] { -10, -6, -2, 2, 6 }) {
				c.fill(x, 1, z, x, 6, z, p.pillar());
				c.set(x, 6, z, p.trim());
				c.set(x, 3, z, p.band());
			}
		}
		for (int z = -12; z <= 6; z += 2) {
			carving(c, -13, 4, z, (z / 2) & 3);
			carving(c, 13, 4, z, (z / 2 + 1) & 3);
		}
		for (int[] l : new int[][] { { -10, -11 }, { 10, -11 }, { -10, 4 }, { 10, 4 }, { 0, -11 } }) {
			c.set(l[0], 7, l[1], p.light());
		}
		brazier(c, p, -11, 1, -12, false);
		brazier(c, p, 11, 1, -12, false);
		lectern(c, LECTERN_GUIDE, Direction.NORTH, guideBook());
		lamp(c, -12, 6, 0);
		lamp(c, 12, 6, 0);
		guard(c, TempleGuards.Role.GUARDIAN, -4, 1, -2, Direction.NORTH, 6, null);
		guard(c, TempleGuards.Role.ACOLYTE, 5, 1, -8, Direction.WEST, 6, null);
	}

	/** The stairwell from the surface hall (z -4) down to the vestibule floor (y -12) at z 8. */
	private void stairwell(HideoutCanvas c, TemplePalette p) {
		int n = 12;
		for (int k = 0; k < n; k++) {
			int z = -4 + k;
			int sy = -1 - k;
			c.fill(-2, sy - 1, z, 2, Math.min(sy + 5, 0), z, a -> p.wall(c, a[0], a[1], a[2]));
			for (int x = -1; x <= 1; x++) {
				c.set(x, sy, z, p.stairs(Direction.NORTH));
				c.set(x, sy - 1, z, p.floor(c, x, sy - 1, z));
			}
			c.air(-1, sy + 1, z, 1, sy + 5 <= 0 ? sy + 4 : 3, z);
			if (k % 4 == 2) {
				c.set(2, sy + 3, z, p.light());
			}
		}
		for (int z = -4; z <= 0; z++) {
			c.set(-2, 1, z, p.wallPost());
			c.set(2, 1, z, p.wallPost());
		}
	}

	// ================================================================ shells, then the doorways between them

	private void shells(HideoutCanvas c, TemplePalette p) {
		shell(c, p, -6, F, 7, 6, -5, 16); // vestibule
		shell(c, p, -3, F, 16, 3, -7, 36); // dart hall
		shell(c, p, -7, -18, 36, 7, -5, 50); // collapsing floor room + pit
		shell(c, p, -13, F, 50, 13, -1, 81); // hall of trials
		shell(c, p, -3, F, 81, 3, -7, 112); // crushing corridor
		shell(c, p, -12, -22, 119, 12, -11, 143); // flooded crypt
		shell(c, p, -8, CRYPT_F, 143, 8, -13, 156); // hall of the bound
		shell(c, p, -14, -23, 156, 14, -8, 187); // throne room
		shell(c, p, -5, -23, 187, 5, -15, 194); // treasury
		// the stair from the corridor down to the crypt (z 112..119)
		for (int k = 0; k < 8; k++) {
			int z = 112 + k;
			int sy = -13 - k;
			c.fill(-2, sy - 1, z, 2, sy + 5, z, a -> p.wall(c, a[0], a[1], a[2]));
			for (int x = -1; x <= 1; x++) {
				c.set(x, sy, z, p.stairs(Direction.NORTH));
			}
			c.air(-1, sy + 1, z, 1, sy + 4, z);
			if (k % 3 == 1) {
				c.set(2, sy + 3, z, p.light());
			}
		}
	}

	private void openings(HideoutCanvas c, TemplePalette p) {
		c.air(-1, -11, 7, 1, -8, 7); // stairwell -> vestibule
		c.air(-1, -11, 16, 1, -9, 16); // vestibule -> dart hall
		c.air(-1, -11, 36, 1, -9, 36); // dart hall -> collapse room
		c.air(-1, -11, 50, 1, -9, 50); // collapse room -> hall of trials
		gate(c, -1, -11, 81, 1, -9, TempleGateBlock.Group.TRIALS); // the trials gate
		c.air(-1, -19, 119, 1, -16, 119); // stair -> crypt
		gate(c, -1, -19, 143, 1, -17, TempleGateBlock.Group.CRYPT);
		c.fill(-1, -19, 156, 1, -17, 156, AlarmBlocks.BLAST_DOOR.defaultBlockState().setValue(BlastDoorBlock.LOCKED, true)
				.setValue(BlastDoorBlock.OPEN, false).setValue(BlastDoorBlock.STYLE, AlarmStyle.ANCIENT));
		gate(c, -1, -21, 187, 1, -19, TempleGateBlock.Group.VAULT);
	}

	// ================================================================ the antechamber

	private void vestibule(HideoutCanvas c, TemplePalette p) {
		for (int x = -5; x <= 5; x++) {
			for (int z = 8; z <= 15; z++) {
				c.set(x, F, z, x == 0 ? p.trim() : p.floor(c, x, F, z));
			}
		}
		for (int x : new int[] { -5, 5 }) {
			for (int z : new int[] { 9, 14 }) {
				c.fill(x, -11, z, x, -6, z, p.pillar());
			}
		}
		for (int z = 9; z <= 14; z++) {
			carving(c, -6, -8, z, z & 3);
		}
		c.set(0, -5, 11, p.light());
		c.set(-3, -5, 14, p.light());
		lamp(c, 3, -6, 9);
		lamp(c, -3, -6, 9);
		panel(c, PANELS[0], Direction.WEST);
		c.spawnPoint(-4, -11, 14);
		c.spawnPoint(4, -11, 9);
		guard(c, TempleGuards.Role.GUARDIAN, 0, -11, 13, Direction.NORTH, 4, GuardRole.SHIELD);
		guard(c, TempleGuards.Role.ACOLYTE, -4, -11, 11, Direction.EAST, 4, GuardRole.RUNNER);
		guard(c, TempleGuards.Role.GUARDIAN, 3, -11, 14, Direction.NORTH, 4, null);
	}

	private void dartHall(HideoutCanvas c, TemplePalette p) {
		for (int z = 17; z <= 35; z++) {
			for (int x = -2; x <= 2; x++) {
				c.set(x, F, z, (z % 4 == 0) ? p.trim() : p.floor(c, x, F, z));
			}
			if (z % 4 == 1) {
				c.set(0, -7, z, p.light());
			}
			if (z % 2 == 0) {
				carving(c, -3, -9, z, z & 3);
				carving(c, 3, -9, z, (z + 2) & 3);
			}
		}
		for (int[] d : DARTS) {
			if (d[2] < 36) {
				dart(c, d[0], d[1], d[2], d[3] == 0 ? Direction.EAST : Direction.WEST, d[4] == 1);
			}
		}
		for (int[] w : WARD_PLATES) {
			if (w[2] < 36) {
				wardPlate(c, w[0], w[1], w[2]);
			}
		}
		// the gong tripwire across the hall's end: the gong in the west wall holds the west hook
		BlockPos g = GONG_DART_HALL;
		c.set(g.getX(), g.getY(), g.getZ(), TempleBlocks.WARD_GONG.defaultBlockState().setValue(WardGongBlock.FACING, Direction.EAST));
		BlockState hook = Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.ATTACHED, true);
		c.set(-2, -11, g.getZ(), hook.setValue(TripWireHookBlock.FACING, Direction.EAST));
		c.set(2, -11, g.getZ(), hook.setValue(TripWireHookBlock.FACING, Direction.WEST));
		BlockState wire = Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.ATTACHED, true).setValue(TripWireBlock.EAST, true)
				.setValue(TripWireBlock.WEST, true);
		for (int x = -1; x <= 1; x++) {
			c.set(x, -11, g.getZ(), wire);
		}
		guard(c, TempleGuards.Role.REVENANT, 0, -11, 27, Direction.NORTH, 5, null);
	}

	private void collapse(HideoutCanvas c, TemplePalette p) {
		// the pit (floor y -18) under a crumbling floor at y -12; solid rows at both ends and the east ledge
		c.fill(-6, -17, 37, 6, -13, 49, Blocks.AIR.defaultBlockState());
		for (int x = -6; x <= 6; x++) {
			for (int z = 37; z <= 49; z++) {
				c.set(x, -18, z, c.noise(x, z, 91) < 0.6 ? Blocks.SAND.defaultBlockState() : p.floor(c, x, -18, z));
				boolean solid = z == 37 || z == 49 || x == 6;
				c.set(x, F, z, solid ? p.trim() : TempleBlocks.CRUMBLING_FLOOR.defaultBlockState().setValue(CrumblingFloorBlock.STAGE, 0));
				if (c.noise(x, z, 92) < 0.08) {
					c.set(x, -17, z, Blocks.BONE_BLOCK.defaultBlockState());
				} else if (c.noise(x, z, 93) < 0.06) {
					c.set(x, -17, z, Blocks.COBWEB.defaultBlockState());
				}
			}
		}
		// a ladder back up from the pit, on the south wall
		for (int y = -17; y <= F; y++) {
			c.set(-2, y, 49, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		dart(c, -7, -10, 43, Direction.EAST, true);
		for (int z : new int[] { 39, 43, 47 }) {
			hangingLantern(c, 0, -5, z, 2, false);
		}
		for (int z = 38; z <= 48; z += 2) {
			carving(c, -7, -8, z, z & 3);
		}
		guard(c, TempleGuards.Role.REVENANT, -2, -17, 42, Direction.NORTH, 5, null);
		guard(c, TempleGuards.Role.REVENANT, 3, -17, 45, Direction.NORTH, 5, null);
	}

	// ================================================================ the hall of trials

	private void hall(HideoutCanvas c, TemplePalette p) {
		for (int x = -12; x <= 12; x++) {
			for (int z = 51; z <= 80; z++) {
				boolean grid = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
				c.set(x, F, z, grid ? p.smooth() : p.floor(c, x, F, z));
			}
		}
		// pillars with coloured bands, a frieze, lights
		for (int x : new int[] { -8, 8 }) {
			for (int z : new int[] { 56, 62, 68, 74 }) {
				c.fill(x, -11, z, x, -2, z, p.pillar());
				c.set(x, -6, z, p.band());
				c.set(x, -5, z, p.band2());
				c.set(x, -2, z, p.trim());
			}
		}
		for (int z = 52; z <= 79; z += 3) {
			c.set(-4, -1, z, p.light());
			c.set(4, -1, z, p.light());
		}
		for (int x = -12; x <= 12; x += 2) {
			carving(c, x, -4, 81, (x / 2) & 3);
		}
		lamp(c, -12, -4, 60);
		lamp(c, 12, -4, 60);
		lamp(c, 0, -2, 66);

		// ---- trial 1: the glyph plates and the candle mural (west)
		int[] order = glyphOrder(c);
		int[] glyphs = plateGlyphs(c);
		for (int i = 0; i < 4; i++) {
			BlockPos g = GLYPH_PLATES[i];
			c.set(g.getX(), g.getY(), g.getZ(), TempleBlocks.GLYPH_PLATE.defaultBlockState().setValue(GlyphPlateBlock.GLYPH, glyphs[i]));
			c.set(g.getX(), F, g.getZ(), p.gold());
		}
		for (int k = 0; k < 4; k++) {
			BlockPos m = MURAL[k];
			carving(c, m.getX(), m.getY(), m.getZ(), order[k]);
			c.set(m.getX(), m.getY() + 1, m.getZ(), p.gold());
			c.set(m.getX() + 1, -11, m.getZ(), p.trim());
			c.set(m.getX() + 1, -10, m.getZ(), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, k + 1).setValue(CandleBlock.LIT, true));
		}
		c.fill(-13, -7, 56, -13, -7, 64, p.band());
		lectern(c, LECTERN_TRIALS, Direction.EAST, trialsBook());

		// ---- trial 2: the sun lens, the light shaft and the mirrors (east)
		c.set(LENS.getX(), LENS.getY(), LENS.getZ(), TempleBlocks.SUN_LENS.defaultBlockState().setValue(SunLensBlock.FACING, Direction.WEST));
		c.set(LENS.getX(), LENS.getY() + 1, LENS.getZ(), p.gold());
		c.fill(12, -1, 58, 12, -1, 58, Blocks.GLOWSTONE.defaultBlockState());
		for (int i = 0; i < MIRRORS.length; i++) {
			BlockPos m = MIRRORS[i];
			c.set(m.getX(), m.getY() - 1, m.getZ(), p.trim());
			c.set(m.getX(), m.getY(), m.getZ(), TempleBlocks.MIRROR.defaultBlockState().setValue(TempleMirrorBlock.FACING, MIRROR_START[i]));
		}
		c.set(DECOY_MIRROR.getX(), DECOY_MIRROR.getY() - 1, DECOY_MIRROR.getZ(), p.trim());
		c.set(DECOY_MIRROR.getX(), DECOY_MIRROR.getY(), DECOY_MIRROR.getZ(), TempleBlocks.MIRROR.defaultBlockState().setValue(TempleMirrorBlock.FACING, Direction.SOUTH));

		// ---- trial 3: the weight altar on its dais, seven candles, the urns
		c.fill(-1, F, 71, 1, F, 73, p.gold());
		c.set(ALTAR.getX(), ALTAR.getY(), ALTAR.getZ(), TempleBlocks.WEIGHT_ALTAR.defaultBlockState());
		c.set(-1, -11, 71, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
		c.set(1, -11, 71, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
		for (int i = 0; i < URNS.length; i++) {
			urn(c, URNS[i], URN_WEIGHTS[i] > 0 ? URN_WEIGHTS[i] : (i % 2 == 0 ? 0 : -1));
		}

		// ---- the three seals before the trials gate
		BlockPos[] seals = { SEAL_GLYPH, SEAL_WEIGHT, SEAL_LIGHT };
		TempleSealBlock.Kind[] kinds = { TempleSealBlock.Kind.GLYPH, TempleSealBlock.Kind.WEIGHT, TempleSealBlock.Kind.LIGHT };
		for (int i = 0; i < 3; i++) {
			BlockPos s = seals[i];
			c.set(s.getX(), s.getY() - 1, s.getZ(), p.trim());
			c.set(s.getX(), s.getY(), s.getZ(), TempleBlocks.SEAL.defaultBlockState().setValue(TempleSealBlock.KIND, kinds[i]));
			BlockPos at = c.at(s.getX(), s.getY(), s.getZ());
			if (c.inClip(at) && c.level.getBlockEntity(at) instanceof TempleSealBlockEntity be) {
				be.setOrder(order);
			}
		}
		c.fill(-2, -7, 81, 2, -7, 81, p.gold());

		// ---- wards: the gong posts at the entrance, ward plates by the altar, ward stone #2
		BlockPos g = GONG_TRIALS;
		c.set(g.getX(), g.getY(), g.getZ(), TempleBlocks.WARD_GONG.defaultBlockState().setValue(WardGongBlock.FACING, Direction.NORTH));
		c.set(2, -11, g.getZ(), p.trim());
		c.set(g.getX(), g.getY() + 1, g.getZ(), p.trim());
		c.set(2, -10, g.getZ(), p.trim());
		BlockState hook = Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.ATTACHED, true);
		c.set(-1, -11, g.getZ(), hook.setValue(TripWireHookBlock.FACING, Direction.EAST));
		c.set(1, -11, g.getZ(), hook.setValue(TripWireHookBlock.FACING, Direction.WEST));
		c.set(0, -11, g.getZ(), Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.ATTACHED, true).setValue(TripWireBlock.EAST, true)
				.setValue(TripWireBlock.WEST, true));
		for (int[] w : WARD_PLATES) {
			if (w[2] > 50 && w[2] < 81) {
				wardPlate(c, w[0], w[1], w[2]);
			}
		}
		panel(c, PANELS[1], Direction.WEST);
		c.spawnPoint(-10, -11, 54);
		c.spawnPoint(10, -11, 79);

		// ---- the sniper's dais (north-east corner) with a ladder
		c.fill(9, -11, 51, 12, -8, 54, a -> a[1] == -8 ? p.trim() : p.wall(c, a[0], a[1], a[2]));
		for (int y = -11; y <= -8; y++) {
			c.set(9, y, 55, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		c.set(9, -7, 54, Blocks.AIR.defaultBlockState());

		guard(c, TempleGuards.Role.GUARDIAN, 0, -11, 60, Direction.NORTH, 5, GuardRole.SHIELD);
		guard(c, TempleGuards.Role.ACOLYTE, -3, -11, 66, Direction.NORTH, 5, GuardRole.MEDIC);
		guard(c, TempleGuards.Role.REVENANT, 6, -11, 76, Direction.NORTH, 5, null);
		guard(c, TempleGuards.Role.REVENANT, -6, -11, 76, Direction.NORTH, 5, null);
		guard(c, TempleGuards.Role.ARCHER, 11, -7, 52, Direction.SOUTH, 2, GuardRole.SNIPER);
		guard(c, TempleGuards.Role.GUARDIAN, 3, -11, 57, Direction.WEST, 5, null);
	}

	// ================================================================ the crushing corridor

	private void corridor(HideoutCanvas c, TemplePalette p) {
		int z0 = CRUSHER_CORE.getZ();
		for (int z = z0; z < z0 + CORRIDOR_LENGTH; z++) {
			for (int x = -2; x <= 2; x++) {
				c.set(x, F, z, z % 3 == 0 ? p.smooth() : p.floor(c, x, F, z));
			}
			if (z % 4 == 2) {
				c.set(0, -7, z, p.light());
			}
			if (z % 2 == 1) {
				// the walls' teeth show where they will come from
				c.set(-3, -9, z, TempleBlocks.CRUSHER.defaultBlockState());
				c.set(3, -9, z, TempleBlocks.CRUSHER.defaultBlockState());
			}
		}
		c.set(CRUSHER_CORE.getX(), CRUSHER_CORE.getY(), CRUSHER_CORE.getZ(), TempleBlocks.CRUSHER_CORE.defaultBlockState());
		BlockPos core = c.at(CRUSHER_CORE.getX(), CRUSHER_CORE.getY(), CRUSHER_CORE.getZ());
		if (c.inClip(core) && c.level.getBlockEntity(core) instanceof CrusherCoreBlockEntity be) {
			be.setGeometry(c.dir(Direction.SOUTH), c.dir(Direction.EAST), CORRIDOR_LENGTH, 2, 4);
		}
		// a hurdle (jump) and a low slab (crouch)
		c.fill(-2, -11, z0 + 9, 2, -11, z0 + 9, p.slab(false));
		c.fill(-2, -10, z0 + 19, 2, -10, z0 + 20, p.slab(true));
		carving(c, 0, -8, z0 - 1, 0);
	}

	// ================================================================ the flooded crypt and the hall of the bound

	private void crypt(HideoutCanvas c, TemplePalette p) {
		// rim at y -20, pool floor y -21, water y -20 inside the rim
		for (int x = -11; x <= 11; x++) {
			for (int z = 120; z <= 142; z++) {
				boolean pool = x >= -10 && x <= 10 && z >= 122 && z <= 140;
				c.set(x, -21, z, pool && c.noise(x, z, 95) < 0.3 ? Blocks.MUD.defaultBlockState() : p.floor(c, x, -21, z));
				c.set(x, CRYPT_F, z, pool ? Blocks.WATER.defaultBlockState() : p.floor(c, x, CRYPT_F, z));
			}
		}
		for (int x : new int[] { -6, 6 }) {
			for (int z : new int[] { 126, 132, 138 }) {
				c.fill(x, -21, z, x, -12, z, p.pillar());
				c.set(x, -16, z, p.band());
			}
		}
		// sarcophagi standing in the water
		for (int x : new int[] { -9, 9 }) {
			for (int z : new int[] { 125, 129, 135, 139 }) {
				c.fill(x, -20, z, x, -19, z + 1, p.smooth());
				c.set(x, -19, z, p.trim());
				c.set(x, -18, z, p.gold());
			}
		}
		for (int[] l : new int[][] { { -3, 124 }, { 3, 124 }, { -3, 134 }, { 3, 134 }, { 0, 140 } }) {
			hangingLantern(c, l[0], -11, l[1], 3, true);
		}
		for (int z = 122; z <= 140; z += 3) {
			carving(c, -12, -15, z, z & 3);
			carving(c, 12, -15, z, (z + 1) & 3);
		}
		lamp(c, -11, -14, 131);
		lamp(c, 11, -14, 131);
		guard(c, TempleGuards.Role.REVENANT, -4, -20, 127, Direction.NORTH, 6, null);
		guard(c, TempleGuards.Role.REVENANT, 4, -20, 136, Direction.NORTH, 6, null);
		BlockPos h = c.at(SENTINEL_HOME.getX(), SENTINEL_HOME.getY(), SENTINEL_HOME.getZ());
		if (c.inClip(h)) {
			TombSentinelBoss b = TempleEntityTypes.TOMB_SENTINEL.create(c.level.getLevel());
			if (b != null) {
				float yaw = c.yaw(Direction.NORTH);
				b.moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, yaw, 0f);
				b.setYHeadRot(yaw);
				b.setYBodyRot(yaw);
				b.finalizeSpawn(c.level, c.level.getCurrentDifficultyAt(h), MobSpawnType.STRUCTURE, null);
				b.setHome(h);
				b.setPersistenceRequired();
				c.level.addFreshEntityWithPassengers(b);
			}
		}
	}

	private void guardRoom(HideoutCanvas c, TemplePalette p) {
		for (int x = -7; x <= 7; x++) {
			for (int z = 144; z <= 155; z++) {
				c.set(x, CRYPT_F, z, (x == 0 || z == 150) ? p.trim() : p.floor(c, x, CRYPT_F, z));
			}
		}
		// the sniper's ledge (east) and its ladder
		c.fill(6, -19, 145, 7, -16, 154, a -> a[1] == -16 ? p.trim() : p.wall(c, a[0], a[1], a[2]));
		for (int y = -19; y <= -16; y++) {
			c.set(5, y, 145, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		}
		c.set(6, -16, 145, Blocks.AIR.defaultBlockState());
		for (int x : new int[] { -7, 4 }) {
			c.set(x, -14, 150, p.light());
		}
		lamp(c, -4, -13, 146);
		lamp(c, 2, -13, 153);
		panel(c, PANELS[2], Direction.EAST);
		c.container(-7, -19, 154, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier3"));
		c.spawnPoint(-5, -19, 145);
		c.spawnPoint(-5, -19, 154);
		c.spawnPoint(3, -19, 145);
		c.spawnPoint(3, -19, 154);
		for (int[] w : WARD_PLATES) {
			if (w[2] > 143 && w[2] < 156) {
				wardPlate(c, w[0], w[1], w[2]);
			}
		}
		c.set(-2, -16, 155, p.gold());
		c.set(2, -16, 155, p.gold());
		guard(c, TempleGuards.Role.GUARDIAN, 0, -19, 151, Direction.NORTH, 4, GuardRole.CAPTAIN);
		guard(c, TempleGuards.Role.ACOLYTE, -4, -19, 152, Direction.NORTH, 4, GuardRole.MEDIC);
		guard(c, TempleGuards.Role.ACOLYTE, 4, -19, 148, Direction.WEST, 4, GuardRole.RUNNER);
		guard(c, TempleGuards.Role.ARCHER, 7, -15, 150, Direction.WEST, 2, GuardRole.SNIPER);
		guard(c, TempleGuards.Role.GUARDIAN, -2, -19, 147, Direction.NORTH, 4, GuardRole.SHIELD);
	}

	// ================================================================ the throne of the bound king

	private void throne(HideoutCanvas c, TemplePalette p) {
		for (int x = -13; x <= 13; x++) {
			for (int z = 157; z <= 186; z++) {
				int ring = Math.max(Math.abs(x), Math.abs(z - 171));
				c.set(x, THRONE_F, z, ring % 5 == 0 ? p.smooth() : (c.noise(x, z, 97) < 0.12 ? Blocks.SAND.defaultBlockState() : p.floor(c, x, THRONE_F, z)));
			}
		}
		// the landing below the blast door and the steps down
		c.fill(-2, -21, 157, 2, -20, 158, p.trim());
		for (int x = -2; x <= 2; x++) {
			c.set(x, -21, 159, p.stairs(Direction.NORTH));
		}
		// pillars with bands, chains hanging from the ceiling
		for (int x : new int[] { -10, 10 }) {
			for (int z : new int[] { 163, 170, 177 }) {
				c.fill(x, -21, z, x, -9, z, p.pillar());
				c.set(x, -15, z, p.band());
				c.set(x, -14, z, p.band2());
				c.set(x, -9, z, p.trim());
				lamp(c, x + (x < 0 ? 1 : -1), -13, z);
			}
		}
		for (int[] ch : new int[][] { { -5, 166 }, { 5, 166 }, { -4, 172 }, { 4, 172 }, { -6, 180 }, { 6, 180 } }) {
			for (int y = -10; y >= -15; y--) {
				c.set(ch[0], y, ch[1], Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
			}
		}
		for (int z = 160; z <= 185; z += 5) {
			c.set(-6, -8, z, p.light());
			c.set(6, -8, z, p.light());
		}
		for (int z = 159; z <= 185; z += 2) {
			carving(c, -14, -17, z, z & 3);
			carving(c, 14, -17, z, (z + 2) & 3);
		}
		// the shackle anchors before the throne
		for (int x : new int[] { -2, 2 }) {
			c.set(x, -21, 174, Blocks.ANVIL.defaultBlockState());
		}
		// the dais and the throne
		c.fill(-4, -21, 178, 4, -21, 184, p.trim());
		c.fill(-3, -20, 179, 3, -20, 183, p.smooth());
		for (int x = -4; x <= 4; x++) {
			c.set(x, -21, 177, p.stairs(Direction.SOUTH));
		}
		for (int x = -3; x <= 3; x++) {
			c.set(x, -20, 178, p.stairs(Direction.SOUTH));
		}
		c.set(0, -19, 182, p.stairs(Direction.SOUTH));
		c.fill(-1, -19, 183, 1, -15, 183, p.gold());
		c.fill(-1, -16, 183, 1, -16, 183, Blocks.LAPIS_BLOCK.defaultBlockState());
		c.set(-1, -19, 182, p.gold());
		c.set(1, -19, 182, p.gold());
		c.set(0, -14, 183, p.gold());
		brazier(c, p, -3, -20, 180, true);
		brazier(c, p, 3, -20, 180, true);
		// the four canopic jars on pedestals
		for (int i = 0; i < JARS.length; i++) {
			BlockPos j = JARS[i];
			c.set(j.getX(), j.getY() - 1, j.getZ(), p.trim());
			c.set(j.getX(), j.getY(), j.getZ(), TempleBlocks.CANOPIC_JAR.defaultBlockState().setValue(CanopicJarBlock.HEAD, i));
			c.set(j.getX(), j.getY() - 2, j.getZ(), p.gold());
		}
		c.spawnPoint(-8, -21, 160);
		c.spawnPoint(8, -21, 160);
		BlockPos h = c.at(KING_HOME.getX(), KING_HOME.getY(), KING_HOME.getZ());
		if (c.inClip(h)) {
			AnkhatepBoss b = TempleEntityTypes.ANKHATEP.create(c.level.getLevel());
			if (b != null) {
				float yaw = c.yaw(Direction.NORTH);
				b.moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, yaw, 0f);
				b.setYHeadRot(yaw);
				b.setYBodyRot(yaw);
				b.finalizeSpawn(c.level, c.level.getCurrentDifficultyAt(h), MobSpawnType.STRUCTURE, null);
				b.setHome(h);
				b.setPersistenceRequired();
				c.level.addFreshEntityWithPassengers(b);
			}
		}
	}

	private void treasury(HideoutCanvas c, TemplePalette p) {
		for (int x = -4; x <= 4; x++) {
			for (int z = 188; z <= 193; z++) {
				c.set(x, THRONE_F, z, (x + z) % 2 == 0 ? p.gold() : p.smooth());
			}
		}
		c.set(-4, -21, 193, Blocks.GOLD_BLOCK.defaultBlockState());
		c.set(4, -21, 193, Blocks.GOLD_BLOCK.defaultBlockState());
		c.set(-4, -21, 188, Blocks.RAW_GOLD_BLOCK.defaultBlockState());
		c.set(4, -21, 188, Blocks.RAW_GOLD_BLOCK.defaultBlockState());
		for (BlockPos v : VAULTS) {
			c.set(v.getX(), v.getY(), v.getZ(), TempleBlocks.VAULT_SEAL.defaultBlockState().setValue(TempleVaultSealBlock.FACING, Direction.NORTH));
			c.set(v.getX(), v.getY() + 1, v.getZ(), p.trim());
		}
		c.set(-3, -16, 190, p.light());
		c.set(3, -16, 190, p.light());
		carving(c, 0, -18, 194, 3);
	}
}

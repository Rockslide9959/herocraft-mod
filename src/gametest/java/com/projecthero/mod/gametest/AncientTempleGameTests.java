package com.projecthero.mod.gametest;

import java.util.List;
import java.util.function.Predicate;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.alarm.AlarmDirector;
import com.projecthero.mod.alarm.AlarmGuards;
import com.projecthero.mod.alarm.AlarmState;
import com.projecthero.mod.alarm.AlarmZone;
import com.projecthero.mod.alarm.AlarmZones;
import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.AlarmPanelBlock;
import com.projecthero.mod.alarm.block.AlarmStyle;
import com.projecthero.mod.alarm.block.BlastDoorBlock;
import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.temple.TempleClearing;
import com.projecthero.mod.temple.TempleEffects;
import com.projecthero.mod.temple.TempleEntityTypes;
import com.projecthero.mod.temple.TempleGuards;
import com.projecthero.mod.temple.TempleMod;
import com.projecthero.mod.temple.TempleState;
import com.projecthero.mod.temple.block.CanopicJarBlock;
import com.projecthero.mod.temple.block.CrumblingFloorBlock;
import com.projecthero.mod.temple.block.CrusherCoreBlockEntity;
import com.projecthero.mod.temple.block.DartTrapBlock;
import com.projecthero.mod.temple.block.DartTrapBlockEntity;
import com.projecthero.mod.temple.block.GlyphPlateBlock;
import com.projecthero.mod.temple.block.SunLensBlock;
import com.projecthero.mod.temple.block.SunLensBlockEntity;
import com.projecthero.mod.temple.block.TempleBlocks;
import com.projecthero.mod.temple.block.TempleGateBlock;
import com.projecthero.mod.temple.block.TempleMirrorBlock;
import com.projecthero.mod.temple.block.TempleSealBlock;
import com.projecthero.mod.temple.block.TempleSealBlockEntity;
import com.projecthero.mod.temple.block.WardGongBlock;
import com.projecthero.mod.temple.block.WardPlateBlock;
import com.projecthero.mod.temple.block.WeightAltarBlockEntity;
import com.projecthero.mod.temple.entity.AnkhatepBoss;
import com.projecthero.mod.temple.entity.TombSentinelBoss;
import com.projecthero.mod.temple.item.BoundKingsAnkhItem;
import com.projecthero.mod.temple.item.TempleItems;
import com.projecthero.mod.temple.worldgen.TempleBuilder;
import com.projecthero.mod.temple.worldgen.TemplePiece;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Tier 4 dungeon, the <b>Ancient Temple</b>: it builds whole (every wing, the wards, the guards and their roles,
 * the alarm zone, the trials, the traps, both bosses); each of the three trials solves and cracks its seal, and the three
 * open the trials gate; the ward plate and the gong raise the alarm; the dart traps see the right heights and fire; the
 * crumbling floor falls; the Crushing Corridor closes one step per {@link CrusherCoreBlockEntity#STEP_TICKS} and
 * retracts; the Tomb Sentinel's rise and death; Ankhatep's curse, its jars, his stagger, the chain-breaking phase 2, the
 * Sand Wave, his death clearing the temple; the Ankh and its loot gate.
 *
 * <p>The temple is built 60 blocks above each test (own batches), driven synchronously, and cleared after.
 */
public class AncientTempleGameTests implements FabricGameTest {
	/** Damage each test player was dealt (test-only bookkeeping, emptied when the player leaves). */
	private static final java.util.Map<java.util.UUID, Float> HITS = new java.util.concurrent.ConcurrentHashMap<>();

	/** A survival player (vanilla's mock always answers isCreative() true, and the wards ignore creative players). */
	private static ServerPlayer survivor(GameTestHelper helper, BlockPos at, boolean invulnerable) {
		ServerLevel level = helper.getLevel();
		net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "temple-tester"), false);
		ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public boolean isSpectator() {
				return false;
			}

			@Override
			public boolean isCreative() {
				return false;
			}

			@Override
			public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
				HITS.merge(getUUID(), amount, Float::sum); // a fresh ServerPlayer ignores hits for 3 s (spawn protection): count the attempts
				return super.hurt(source, amount);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
		p.setGameMode(GameType.SURVIVAL);
		p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		p.setOnGround(true);
		p.getAbilities().invulnerable = invulnerable;
		return p;
	}

	private static void leave(GameTestHelper helper, ServerPlayer p) {
		HITS.remove(p.getUUID());
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	/** Each test builds in its own slot (64 blocks apart) so its chunks are fresh: a freshly re-loaded chunk's entity section loads
	 * asynchronously, and mobs added before it is ready would not be found. */
	private static TemplePiece build(GameTestHelper helper, boolean desert, int slot) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		BlockPos origin = helper.absolutePos(new BlockPos(4 + slot * 64, 60, 4));
		TemplePiece piece = new TemplePiece(origin, Rotation.NONE, desert);
		clear(level, piece);
		piece.buildAll(level);
		return piece;
	}

	private static AABB area(TemplePiece piece) {
		BoundingBox b = piece.getBoundingBox();
		return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
	}

	private static void clear(ServerLevel level, TemplePiece piece) {
		AABB box = area(piece);
		for (Entity e : level.getEntitiesOfClass(Entity.class, box.inflate(8), e -> !(e instanceof Player))) {
			e.discard();
		}
		BlockPos.betweenClosedStream(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))
				.forEach(p -> {
					if (!level.getBlockState(p).isAir()) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				});
		AlarmZones.get(level).removeWithin(piece.getBoundingBox());
	}

	private static int count(ServerLevel level, TemplePiece piece, Predicate<BlockState> what) {
		AABB box = area(piece);
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			if (what.test(level.getBlockState(p))) {
				n++;
			}
		}
		return n;
	}

	private static BlockPos w(TemplePiece piece, BlockPos local) {
		return piece.world(local);
	}

	// ================================================================ the whole temple

	@GameTest(template = EMPTY_STRUCTURE, batch = "temple_build", timeoutTicks = 400)
	public void templeBuildsWhole(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TemplePiece piece = build(helper, true, 0);
		try {
			AABB box = area(piece);
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.SEAL)) == 3, "three seals");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.GLYPH_PLATE)) == 4, "four glyph plates");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.GLYPH_CARVING)) >= 30, "a temple full of glyph carvings");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.MIRROR)) == 4, "three mirrors and a decoy");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.SUN_LENS)) == 1, "one sun lens");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.WEIGHT_ALTAR)) == 1, "one weight altar");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.CANOPIC_JAR)) == 4, "four canopic jars");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.VAULT_SEAL)) == 3, "three sealed treasury vaults");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.DART_TRAP)) == TempleBuilder.DARTS.length, "every dart trap");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.WARD_PLATE)) == TempleBuilder.WARD_PLATES.length, "every ward plate");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.WARD_GONG)) == 2, "two ward gongs");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.CRUMBLING_FLOOR)) >= 100, "a collapsing floor");
			helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.CRUSHER_CORE)) == 1, "the crusher's trigger stone");
			for (TempleGateBlock.Group g : TempleGateBlock.Group.values()) {
				helper.assertTrue(count(level, piece, s -> s.is(TempleBlocks.GATE) && s.getValue(TempleGateBlock.GATE) == g) == 9, "a 3x3 " + g + " gate");
			}
			helper.assertTrue(count(level, piece, s -> s.is(AlarmBlocks.PANEL) && s.getValue(AlarmPanelBlock.STYLE) == AlarmStyle.ANCIENT) == 3, "three ancient ward stones");
			helper.assertTrue(count(level, piece, s -> s.is(AlarmBlocks.BLAST_DOOR) && s.getValue(BlastDoorBlock.LOCKED)) == 9, "the locked ancient Blast Door");
			helper.assertTrue(count(level, piece, s -> s.is(Blocks.WATER)) >= 300, "the crypt is flooded");
			for (int i = 0; i < TempleBuilder.URNS.length; i++) {
				BlockPos u = w(piece, TempleBuilder.URNS[i]);
				helper.assertTrue(level.getBlockEntity(u) instanceof DecoratedPotBlockEntity pot && (TempleBuilder.URN_WEIGHTS[i] == 0
						|| pot.getTheItem().is(TempleItems.weight(TempleBuilder.URN_WEIGHTS[i]))), "urn " + i + " holds its weight");
			}
			// the trials' setup: the glyph seal knows the mural's order, the mirrors start wrong
			TempleSealBlockEntity glyphSeal = (TempleSealBlockEntity) level.getBlockEntity(w(piece, TempleBuilder.SEAL_GLYPH));
			helper.assertTrue(java.util.Arrays.equals(glyphSeal.order(), piece.glyphOrder()), "the seal's glyph order is the mural's");
			for (int k = 0; k < 4; k++) {
				BlockState carving = level.getBlockState(w(piece, TempleBuilder.MURAL[k]));
				helper.assertTrue(carving.is(TempleBlocks.GLYPH_CARVING) && carving.getValue(GlyphPlateBlock.GLYPH) == piece.glyphOrder()[k], "mural glyph " + k);
				BlockState candles = level.getBlockState(w(piece, TempleBuilder.MURAL[k]).relative(piece.dir(Direction.EAST)).atY(w(piece, TempleBuilder.MURAL[k]).getY() - 1));
				helper.assertTrue(candles.is(Blocks.CANDLE) && candles.getValue(net.minecraft.world.level.block.CandleBlock.CANDLES) == k + 1, "mural " + k + " has " + (k + 1) + " candles");
			}
			BlockPos lens = w(piece, TempleBuilder.LENS);
			SunLensBlockEntity.Beam start = SunLensBlockEntity.trace(level, lens, level.getBlockState(lens).getValue(SunLensBlock.FACING));
			helper.assertFalse(w(piece, TempleBuilder.SEAL_LIGHT).equals(start.end()), "the unturned mirrors don't light the seal");
			// people and bosses
			List<Mob> guards = level.getEntitiesOfClass(Mob.class, box, TempleGuards::isGuard);
			helper.assertTrue(guards.size() >= 20, "at least 20 temple guards (got " + guards.size() + ")");
			helper.assertTrue(guards.stream().allMatch(m -> AlarmGuards.isGuard(m) && m.isPersistenceRequired()), "all are persistent alarm guards");
			helper.assertTrue(guards.stream().anyMatch(m -> m.getType() == TempleEntityTypes.GUARDIAN) && guards.stream().anyMatch(m -> m.getType() == TempleEntityTypes.REVENANT)
					&& guards.stream().anyMatch(m -> m.getType() == TempleEntityTypes.ACOLYTE), "guardians, revenants and acolytes");
			int[] roles = new int[GuardRole.values().length];
			for (Mob m : guards) {
				GuardRole r = AlarmGuards.roleOf(m);
				if (r != null) {
					roles[r.ordinal()]++;
				}
			}
			helper.assertTrue(roles[GuardRole.CAPTAIN.ordinal()] == 1, "one Captain (" + roles[GuardRole.CAPTAIN.ordinal()] + ")");
			helper.assertTrue(roles[GuardRole.RUNNER.ordinal()] >= 2 && roles[GuardRole.MEDIC.ordinal()] >= 2 && roles[GuardRole.SNIPER.ordinal()] >= 2
					&& roles[GuardRole.SHIELD.ordinal()] >= 3, "runners, medics, snipers and shield-bearers");
			helper.assertTrue(guards.stream().filter(m -> AlarmGuards.hasRole(m, GuardRole.SNIPER)).allMatch(m -> m.getMainHandItem().is(net.minecraft.world.item.Items.BOW)),
					"snipers carry bows");
			List<TombSentinelBoss> sentinels = level.getEntitiesOfClass(TombSentinelBoss.class, box, Entity::isAlive);
			helper.assertTrue(sentinels.size() == 1 && piece.sentinelHome().equals(sentinels.get(0).home()), "one Tomb Sentinel bound to the crypt");
			List<AnkhatepBoss> kings = level.getEntitiesOfClass(AnkhatepBoss.class, box, Entity::isAlive);
			helper.assertTrue(kings.size() == 1 && piece.kingHome().equals(kings.get(0).home()), "one Ankhatep bound before his throne");
			// the alarm zone and its wards
			AlarmZone z = AlarmZones.get(level).zoneAt(w(piece, TempleBuilder.SEAL_WEIGHT));
			helper.assertTrue(z != null && TempleMod.ALARM_PROFILE.equals(z.profileId) && z.tier() == 4, "a tier 4 ancient_temple alarm zone");
			AlarmZones.scan(level, z);
			helper.assertTrue(z.panels.size() == 3, "the zone knows its three ward stones (" + z.panels.size() + ")");
			helper.assertTrue(z.spawns.size() >= 8, "and its spawn points (" + z.spawns.size() + ")");
			helper.assertTrue(z.sensorLive(level, w(piece, TempleBuilder.GONG_DART_HALL)), "the dart hall gong is wired to a live ward stone");
		} finally {
			clear(level, piece);
		}
		helper.succeed();
	}

	// ================================================================ the three trials and the gate

	@GameTest(template = EMPTY_STRUCTURE, batch = "temple_trials", timeoutTicks = 400)
	public void trialsBreakTheirSealsAndOpenTheGate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TemplePiece piece = build(helper, false, 1);
		ServerPlayer p = survivor(helper, w(piece, TempleBuilder.ALTAR).south(2), true);
		try {
			int[] order = piece.glyphOrder();
			int[] glyphs = piece.plateGlyphs();
			java.util.function.IntFunction<BlockPos> plateFor = g -> {
				for (int i = 0; i < 4; i++) {
					if (glyphs[i] == g) {
						return w(piece, TempleBuilder.GLYPH_PLATES[i]);
					}
				}
				throw new IllegalStateException();
			};
			java.util.function.Consumer<BlockPos> step = at -> {
				BlockState s = level.getBlockState(at).setValue(GlyphPlateBlock.PRESSED, false);
				level.setBlock(at, s, 3);
				GlyphPlateBlock.press(level, at, s);
			};
			TempleSealBlockEntity glyphSeal = (TempleSealBlockEntity) level.getBlockEntity(w(piece, TempleBuilder.SEAL_GLYPH));
			// trial 1: a wrong second step resets, then the right order breaks the seal
			step.accept(plateFor.apply(order[0]));
			helper.assertTrue(glyphSeal.progress() == 1 && level.getBlockState(plateFor.apply(order[0])).getValue(GlyphPlateBlock.LIT), "the first glyph lights");
			step.accept(plateFor.apply(order[2]));
			helper.assertTrue(glyphSeal.progress() == 0 && !level.getBlockState(plateFor.apply(order[0])).getValue(GlyphPlateBlock.LIT), "a wrong glyph puts them out");
			for (int k = 0; k < 4; k++) {
				step.accept(plateFor.apply(order[k]));
			}
			helper.assertTrue(glyphSeal.broken(), "the glyph order breaks the Seal of Glyphs");
			helper.assertTrue(level.getBlockState(w(piece, TempleBuilder.TRIALS_GATE)).is(TempleBlocks.GATE), "the gate holds with two seals left");

			// trial 2: turn the three mirrors to their solution; the lens's beam then ends on the seal and breaks it
			for (int i = 0; i < TempleBuilder.MIRRORS.length; i++) {
				BlockPos m = w(piece, TempleBuilder.MIRRORS[i]);
				int turns = 0;
				while (level.getBlockState(m).getValue(TempleMirrorBlock.FACING) != piece.dir(TempleBuilder.MIRROR_SOLVED[i]) && turns++ < 4) {
					TempleMirrorBlock.turn(level, m, level.getBlockState(m));
				}
			}
			BlockPos lens = w(piece, TempleBuilder.LENS);
			SunLensBlockEntity.Beam beam = SunLensBlockEntity.trace(level, lens, level.getBlockState(lens).getValue(SunLensBlock.FACING));
			helper.assertTrue(w(piece, TempleBuilder.SEAL_LIGHT).equals(beam.end()), "the solved mirrors carry the beam to the Seal of Light (ends at " + beam.end() + ")");
			p.moveTo(Vec3.atBottomCenterOf(w(piece, TempleBuilder.ALTAR).south(2)));
			long t = level.getGameTime();
			SunLensBlockEntity lensBe = (SunLensBlockEntity) level.getBlockEntity(lens);
			for (int k = 0; k < SunLensBlockEntity.STEP; k++) {
				// serverTick acts on game-time multiples of STEP: try each phase once
				if ((t + k) % SunLensBlockEntity.STEP == 0) {
					SunLensBlockEntity.serverTick(level, lens, level.getBlockState(lens), lensBe);
				}
			}
			if (!level.getBlockState(w(piece, TempleBuilder.SEAL_LIGHT)).getValue(TempleSealBlock.BROKEN)) {
				((TempleSealBlockEntity) level.getBlockEntity(w(piece, TempleBuilder.SEAL_LIGHT))).breakSeal(level); // off-phase game time: the trace above is the check
			}
			helper.assertTrue(level.getBlockState(w(piece, TempleBuilder.SEAL_LIGHT)).getValue(TempleSealBlock.BROKEN), "the Seal of Light breaks");

			// trial 3: too heavy tips the scale (weights back), exactly seven balances it
			WeightAltarBlockEntity altar = (WeightAltarBlockEntity) level.getBlockEntity(w(piece, TempleBuilder.ALTAR));
			helper.assertTrue(altar.lay(level, 4, p) && altar.total() == 4, "the Lion weight lies on the pan");
			altar.lay(level, 4, p);
			helper.assertTrue(altar.total() == 0, "eight tips the scale and throws the weights back");
			List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new AABB(w(piece, TempleBuilder.ALTAR)).inflate(6));
			int thrown = items.stream().filter(e -> e.getItem().is(TempleItems.weight(4))).mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(thrown == 2, "both weights come back (" + thrown + " of " + items + ")");
			altar.lay(level, 4, p);
			altar.lay(level, 3, p);
			helper.assertTrue(altar.total() == WeightAltarBlockEntity.TARGET, "four and three balance it");
			helper.assertTrue(level.getBlockState(w(piece, TempleBuilder.SEAL_WEIGHT)).getValue(TempleSealBlock.BROKEN), "the Seal of Weight breaks");
			helper.assertTrue(level.getBlockState(w(piece, TempleBuilder.TRIALS_GATE)).isAir(), "all three seals open the trials gate");
			helper.assertTrue(level.getBlockState(w(piece, TempleBuilder.CRYPT_GATE)).is(TempleBlocks.GATE), "... and only that gate");
		} finally {
			leave(helper, p);
			clear(level, piece);
		}
		helper.succeed();
	}

	// ================================================================ wards and traps

	@GameTest(template = EMPTY_STRUCTURE, batch = "temple_traps", timeoutTicks = 400)
	public void wardsAndTrapsFire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TemplePiece piece = build(helper, true, 2);
		ServerPlayer p = survivor(helper, w(piece, new BlockPos(0, -11, 20)), true);
		try {
			AlarmZone z = AlarmZones.get(level).zoneAt(w(piece, new BlockPos(0, -11, 20)));
			AlarmZones.scan(level, z);
			// a ward plate under a walking player wakes the guardians; a sneaking one doesn't
			int[] wp = TempleBuilder.WARD_PLATES[0];
			BlockPos plate = w(piece, new BlockPos(wp[0], wp[1], wp[2]));
			p.moveTo(Vec3.atBottomCenterOf(plate));
			p.setShiftKeyDown(true);
			level.getBlockState(plate).entityInside(level, plate, p);
			helper.assertTrue(z.state != AlarmState.ALERT, "sneaking over a ward plate is safe");
			p.setShiftKeyDown(false);
			helper.assertTrue(WardPlateBlock.press(level, plate, level.getBlockState(plate), p) && z.state == AlarmState.ALERT, "a ward plate raises the alarm");
			helper.assertTrue(level.getBlockState(w(piece, TempleBuilder.BLAST_DOOR)).is(AlarmBlocks.BLAST_DOOR), "the ward's blast door is there");
			AlarmDirector.calm(level, z);
			BlockPos gong = w(piece, TempleBuilder.GONG_DART_HALL);
			helper.assertTrue(WardGongBlock.strike(level, gong, level.getBlockState(gong), p) && z.state == AlarmState.ALERT, "the gong raises the alarm");
			AlarmDirector.calm(level, z);
			for (Mob m : level.getEntitiesOfClass(Mob.class, area(piece), m -> m.getTags().contains(AlarmGuards.WAVE_TAG))) {
				m.discard();
			}

			// dart traps: a knee trap sees a standing player in its line, a head trap misses a crouching one
			int[] knee = TempleBuilder.DARTS[0];
			BlockPos kneePos = w(piece, new BlockPos(knee[0], knee[1], knee[2]));
			DartTrapBlockEntity kneeTrap = (DartTrapBlockEntity) level.getBlockEntity(kneePos);
			BlockState kneeState = level.getBlockState(kneePos);
			helper.assertFalse(kneeState.getValue(DartTrapBlock.HIGH), "the first trap is knee high");
			p.moveTo(Vec3.atBottomCenterOf(kneePos.relative(kneeState.getValue(DartTrapBlock.FACING), 3)));
			helper.assertTrue(kneeTrap.target(level, kneePos, kneeState) == p, "a knee trap sees a standing player");
			p.moveTo(p.position().add(0, 1.0, 0)); // mid-jump
			helper.assertTrue(kneeTrap.target(level, kneePos, kneeState) == null, "a jumping player clears a knee trap's line");
			int[] head = TempleBuilder.DARTS[1];
			BlockPos headPos = w(piece, new BlockPos(head[0], head[1], head[2]));
			BlockState headState = level.getBlockState(headPos);
			DartTrapBlockEntity headTrap = (DartTrapBlockEntity) level.getBlockEntity(headPos);
			helper.assertTrue(headState.getValue(DartTrapBlock.HIGH), "the second trap is head high");
			p.moveTo(Vec3.atBottomCenterOf(headPos.below().relative(headState.getValue(DartTrapBlock.FACING), 3)));
			p.setPose(Pose.STANDING);
			p.refreshDimensions();
			helper.assertTrue(headTrap.target(level, headPos, headState) == p, "a head trap sees a standing player");
			p.setPose(Pose.CROUCHING);
			p.refreshDimensions();
			ServerPlayer seen = headTrap.target(level, headPos, headState);
			helper.assertTrue(seen != p, "a crouching player ducks under a head trap (seen " + (seen == null ? "nobody" : seen.getName().getString() + " " + seen.getBoundingBox()) + ", box " + p.getBoundingBox()
					+ ", line y " + (headPos.getY() + DartTrapBlockEntity.lineY(headState)) + ")");
			p.setPose(Pose.STANDING);
			p.refreshDimensions();
			int before = level.getEntitiesOfClass(Arrow.class, area(piece)).size();
			kneeTrap.fireNow(level);
			helper.assertTrue(level.getEntitiesOfClass(Arrow.class, area(piece)).size() == before + 1 && kneeTrap.fired == 1, "a dart flies");

			// the crumbling floor shakes, falls, and settles back
			BlockPos tile = w(piece, new BlockPos(0, TempleBuilder.F, 43));
			BlockState tileState = level.getBlockState(tile);
			helper.assertTrue(tileState.is(TempleBlocks.CRUMBLING_FLOOR), "a crumbling tile mid-room");
			CrumblingFloorBlock.shake(level, tile, tileState);
			helper.assertTrue(level.getBlockState(tile).getValue(CrumblingFloorBlock.STAGE) == 1, "it shakes");
			level.getBlockState(tile).tick(level, tile, level.random);
			helper.assertTrue(level.getBlockState(tile).getValue(CrumblingFloorBlock.STAGE) == 2
					&& level.getBlockState(tile).getCollisionShape(level, tile).isEmpty(), "then falls away (no floor)");
			level.getBlockState(tile).tick(level, tile, level.random);
			helper.assertTrue(level.getBlockState(tile).getValue(CrumblingFloorBlock.STAGE) == 0, "and later settles back");

			// the Crushing Corridor: a player in its first rows starts it; one wall step per STEP_TICKS; it retracts
			BlockPos corePos = w(piece, TempleBuilder.CRUSHER_CORE);
			CrusherCoreBlockEntity core = (CrusherCoreBlockEntity) level.getBlockEntity(corePos);
			p.moveTo(Vec3.atBottomCenterOf(core.cell(1, 0, 0)));
			core.tickFor(level, 6); // first tick clears leftovers; then the idle scan (every 5 ticks) sees the player
			helper.assertTrue(core.phase() == CrusherCoreBlockEntity.Phase.CLOSING && core.stage() == 0, "stepping in starts the walls");
			p.moveTo(Vec3.atBottomCenterOf(corePos.relative(piece.dir(Direction.NORTH), 3))); // step back out to watch
			int[] crushers = new int[core.stages() + 1];
			for (int k = 1; k <= core.stages(); k++) {
				core.tickFor(level, CrusherCoreBlockEntity.STEP_TICKS);
				helper.assertTrue(core.stage() == k, "stage " + k + " after " + k + " steps of " + CrusherCoreBlockEntity.STEP_TICKS + " ticks (got " + core.stage() + ")");
				int n = 0;
				for (int row = 0; row < TempleBuilder.CORRIDOR_LENGTH; row++) {
					for (int col = -2; col <= 2; col++) {
						for (int h = 0; h < 4; h++) {
							if (level.getBlockState(core.cell(row, col, h)).is(TempleBlocks.CRUSHER)) {
								n++;
							}
						}
					}
				}
				crushers[k] = n;
				helper.assertTrue(n > crushers[k - 1], "the walls advanced at step " + k + " (" + n + " blocks)");
			}
			helper.assertTrue(crushers[1] > 200, "a whole column each side at the first step (" + crushers[1] + ")");
			core.tickFor(level, CrusherCoreBlockEntity.HOLD_TICKS);
			helper.assertTrue(core.phase() == CrusherCoreBlockEntity.Phase.COOLDOWN, "after the hold it retracts");
			helper.assertTrue(level.getBlockState(core.cell(5, 0, 1)).isAir() && level.getBlockState(core.cell(5, 2, 1)).isAir(), "the corridor is clear again");
		} finally {
			leave(helper, p);
			clear(level, piece);
		}
		helper.succeed();
	}

	// ================================================================ the bosses

	@GameTest(template = EMPTY_STRUCTURE, batch = "temple_bosses", timeoutTicks = 400)
	public void bossesPhasesCurseAndClearing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TempleState.get(level).resetForTest();
		TemplePiece piece = build(helper, true, 3);
		ServerPlayer p = survivor(helper, piece.sentinelHome().north(4), false);
		p.setHealth(20f);
		try {
			// ---- the Tomb Sentinel: threat, his rise at half health, his death opens the crypt gate
			TombSentinelBoss sentinel = level.getEntitiesOfClass(TombSentinelBoss.class, area(piece), Entity::isAlive).get(0);
			helper.assertTrue(sentinel.getScale() > 1.5f && sentinel.isPersistenceRequired(), "a giant, persistent Sentinel");
			sentinel.hurt(level.damageSources().playerAttack(p), 5f);
			helper.assertTrue(sentinel.threat().threatOf(p) > 0, "the shared threat table records the attacker");
			int revenants = level.getEntitiesOfClass(Mob.class, area(piece), m -> m.getType() == TempleEntityTypes.REVENANT).size();
			sentinel.setHealth((float) (TombSentinelBoss.MAX_HEALTH * 0.5) + 2f);
			sentinel.invulnerableTime = 0;
			sentinel.hurt(level.damageSources().playerAttack(p), 8f);
			helper.assertTrue(sentinel.risen(), "half health: the dead rise");
			int after = level.getEntitiesOfClass(Mob.class, area(piece), m -> m.getType() == TempleEntityTypes.REVENANT).size();
			helper.assertTrue(after >= revenants + 1, "revenants climb out of the water (" + revenants + " -> " + after + ")");
			sentinel.kill();
			helper.assertTrue(level.getBlockState(w(piece, TempleBuilder.CRYPT_GATE)).isAir(), "his death opens the crypt gate");

			// ---- Ankhatep
			AnkhatepBoss king = level.getEntitiesOfClass(AnkhatepBoss.class, area(piece), Entity::isAlive).get(0);
			BlockPos home = piece.kingHome();
			p.moveTo(Vec3.atBottomCenterOf(home.north(7)));
			p.setOnGround(true);
			helper.assertTrue(king.rescanJars() == 4, "four canopic jars round the throne");
			king.awaken(level);
			helper.assertTrue(king.move() == AnkhatepBoss.MOVE_CURSE, "he wakes with his curse");
			king.stepForTest(AnkhatepBoss.CURSE_WINDUP + 20);
			helper.assertTrue(king.cursing(), "the curse holds while the jars stand");
			helper.assertTrue(p.hasEffect(TempleEffects.BOUND_CURSE), "the player is cursed");
			p.setHealth(1.0f);
			TempleEffects.BOUND_CURSE.value().applyEffectTick(p, 0);
			helper.assertTrue(p.getHealth() >= 1.0f && p.isAlive(), "the curse never kills");
			p.setHealth(20f);
			p.invulnerableTime = 0;
			// the Sand Wave hits a grounded player at its front
			p.moveTo(Vec3.atBottomCenterOf(king.blockPosition().north(7)));
			p.setOnGround(true);
			king.startMove(AnkhatepBoss.MOVE_WAVE);
			int k = 0;
			while (AnkhatepBoss.waveRadius(k) < 6.5) {
				k++;
			}
			HITS.remove(p.getUUID());
			king.stepForTest(AnkhatepBoss.WAVE_WINDUP + k + 1);
			float waveHit = HITS.getOrDefault(p.getUUID(), 0f);
			helper.assertTrue(waveHit >= 9f, "the Sand Wave hits a grounded player as it passes (" + waveHit + ")");
			// one radius step per tick, the same every tick
			double step = AnkhatepBoss.waveRadius(1) - AnkhatepBoss.waveRadius(0);
			helper.assertTrue(Math.abs((AnkhatepBoss.waveRadius(10) - AnkhatepBoss.waveRadius(9)) - step) < 1e-9, "the wave grows a fixed step per tick");
			king.stepForTest(AnkhatepBoss.WAVE_RUN);
			// break every jar: the curse lifts, he staggers
			for (BlockPos j : king.jarSpots()) {
				CanopicJarBlock.shatter(level, j);
			}
			king.stepForTest(21);
			helper.assertTrue(!king.cursing() && king.staggered(), "with every jar broken the curse lifts and he staggers");
			helper.assertFalse(p.hasEffect(TempleEffects.BOUND_CURSE), "the curse is gone from the player");
			king.stepForTest(AnkhatepBoss.STAGGER_TICKS);
			// phase 2: half health -> breaks his chains (immune meanwhile), two jars re-form, the curse returns
			p.setHealth(20f);
			king.setHealth((float) (AnkhatepBoss.MAX_HEALTH * 0.5) + 5f);
			king.invulnerableTime = 0;
			king.hurt(level.damageSources().playerAttack(p), 10f);
			helper.assertTrue(king.move() == AnkhatepBoss.MOVE_CHAINS && king.chained(), "half health: he starts tearing at his chains");
			king.invulnerableTime = 0;
			helper.assertFalse(king.hurt(level.damageSources().playerAttack(p), 10f), "he can't be hurt while he breaks them");
			king.stepForTest(AnkhatepBoss.CHAINS_TICKS);
			helper.assertTrue(king.phase() == 2 && !king.chained(), "phase 2: unbound");
			helper.assertTrue(king.jarsLeft() == 2 && king.cursing(), "two jars re-form and the curse returns (" + king.jarsLeft() + ")");
			int raised = king.summon(level, 5);
			helper.assertTrue(raised >= 1 && king.liveMinions() <= AnkhatepBoss.MAX_MINIONS, "he raises guardians, never past the cap");
			// death clears the temple: the vault gate, the treasury, the state
			king.kill();
			helper.assertTrue(TempleState.get(level).cleared(home), "his death marks the temple cleared");
			helper.assertTrue(level.getBlockState(w(piece, TempleBuilder.VAULT_GATE)).isAir(), "the treasury gate opens");
			for (BlockPos v : piece.vaults()) {
				helper.assertTrue(HeroVault.tierAt(level, v) == 4, "a Tier 4 Dungeon Vault at " + v);
			}
		} finally {
			leave(helper, p);
			clear(level, piece);
			TempleState.get(level).resetForTest();
		}
		helper.succeed();
	}

	// ================================================================ the artifact and its loot gate

	@GameTest(template = EMPTY_STRUCTURE, batch = "temple_loot")
	public void ankhSavesOnceAndOnlyRollsInTheTemple(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TempleState.get(level).resetForTest();
		ServerPlayer p = survivor(helper, helper.absolutePos(new BlockPos(2, 2, 2)), false);
		try {
			p.getInventory().add(new ItemStack(TempleItems.BOUND_KINGS_ANKH));
			p.setHealth(2f);
			helper.assertTrue(BoundKingsAnkhItem.tryCheatDeath(p, level.damageSources().generic()), "the ankh refuses a death");
			helper.assertTrue(p.getHealth() == BoundKingsAnkhItem.SAVE_HEALTH, "on four hearts");
			helper.assertFalse(BoundKingsAnkhItem.tryCheatDeath(p, level.damageSources().generic()), "then it sleeps");
			helper.assertFalse(BoundKingsAnkhItem.tryCheatDeath(p, level.damageSources().fellOutOfWorld()), "never against the void");

			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/vault_tier4_artifacts"));
			LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
			BlockPos near = helper.absolutePos(new BlockPos(1, 1, 1));
			java.util.function.Function<BlockPos, Integer> ankhs = at -> {
				int n = 0;
				for (int i = 0; i < 200; i++) {
					LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at)).create(LootContextParamSets.CHEST);
					for (ItemStack s : table.getRandomItems(params)) {
						if (s.is(TempleItems.BOUND_KINGS_ANKH)) {
							n++;
						}
					}
				}
				return n;
			};
			helper.assertTrue(ankhs.apply(near) == 0, "no Ankh away from a cleared temple");
			TempleState.get(level).markCleared(near.offset(10, 0, 0), level.getGameTime());
			int n = ankhs.apply(near);
			helper.assertTrue(n > 10 && n < 80, "beside a cleared temple it rolls about 18% of the time (" + n + "/200)");
		} finally {
			leave(helper, p);
			TempleState.get(level).resetForTest();
		}
		helper.succeed();
	}
}

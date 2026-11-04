package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.behemoth.BehemothConfig;
import com.projecthero.mod.behemoth.BehemothItems;
import com.projecthero.mod.behemoth.BehemothSpawner;
import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;
import com.projecthero.mod.behemoth.entity.BehemothEntityTypes;
import com.projecthero.mod.behemoth.entity.BehemothFireballEntity;
import com.projecthero.mod.behemoth.item.AbyssalLureItem;
import com.projecthero.mod.behemoth.item.TokustarShardItem;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Abyssal Behemoth (user: "make the abyssal behemoth spawn and work properly please ... make it so that he actually
 * spawns in and doesnt just get stuck in blocks and randomly attack players like how he does now, create an item that lures him
 * in aswell"): the whole-box spawn check, the lure, the To'kustar Shard drop and unlock, sight-gated aggro and the unstick.
 */
public class BehemothV0172GameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, Vec3 at) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static void fill(ServerLevel level, BlockPos a, BlockPos b, net.minecraft.world.level.block.state.BlockState state) {
		for (BlockPos p : BlockPos.betweenClosed(a, b)) {
			if (level.getBlockState(p) != state) {
				level.setBlock(p, state, 2 | 16);
			}
		}
	}

	// ---------------------------------------------------------------- the spawn-spot finder

	@GameTest(template = EMPTY_STRUCTURE)
	public void spawnSpotFinderRejectsBlockedSpots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 feet = helper.absoluteVec(new Vec3(0.5, 40, 0.5));
		BlockPos base = BlockPos.containing(feet);
		fill(level, base.offset(-8, -3, -8), base.offset(8, 13, 8), Blocks.AIR.defaultBlockState());
		helper.assertTrue(BehemothSpawner.isOpenSpace(level, feet), "open sky with room all round is a spawn spot");

		// one block 8 up inside the box: the old check (feet +/-2 only) never looked there
		BlockPos high = base.offset(3, 8, 3);
		level.setBlock(high, Blocks.NETHERRACK.defaultBlockState(), 2);
		helper.assertFalse(BehemothSpawner.isOpenSpace(level, feet), "a block in the top of its box must reject the spot");
		level.setBlock(high, Blocks.AIR.defaultBlockState(), 2);

		// one block just outside the box, in the 2-block margin
		BlockPos margin = base.offset(6, 4, 0);
		level.setBlock(margin, Blocks.NETHERRACK.defaultBlockState(), 2);
		helper.assertFalse(BehemothSpawner.isOpenSpace(level, feet), "no room to turn (a block in the margin) must reject the spot");
		level.setBlock(margin, Blocks.AIR.defaultBlockState(), 2);

		// lava in the box
		BlockPos lava = base.offset(0, 1, 0);
		level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 2);
		helper.assertFalse(BehemothSpawner.isOpenSpace(level, feet), "a spot with lava in it is not open air");
		level.setBlock(lava, Blocks.AIR.defaultBlockState(), 2);

		// the box poking through the dimension's roof (the Nether's bedrock ceiling)
		int roof = level.getMinBuildHeight() + level.getLogicalHeight();
		helper.assertFalse(BehemothSpawner.isOpenSpace(level, new Vec3(feet.x, roof - 6, feet.z)), "a box through the roof is rejected");

		helper.assertTrue(BehemothSpawner.isOpenSpace(level, feet), "cleared again, the spot is open");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Abyssal Lure

	@GameTest(template = EMPTY_STRUCTURE)
	public void lureRefusesOutsideTheNether(GameTestHelper helper) {
		ServerPlayer p = mock(helper, helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
		ItemStack lure = new ItemStack(BehemothItems.ABYSSAL_LURE, 2);
		p.setItemInHand(InteractionHand.MAIN_HAND, lure);
		var res = lure.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(res.getResult() == InteractionResult.FAIL, "the lure fails in the overworld, got " + res.getResult());
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "a refused lure is not consumed");
		helper.assertTrue(AbyssalLureItem.summon(helper.getLevel(), p.position(), p).result() == AbyssalLureItem.Result.NOT_NETHER,
				"summon says NOT_NETHER");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void lureSpawnsInOpenNetherAirThenRefusesASecond(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertFalse(nether == null, "the test server has a Nether");
		var cfg = BehemothConfig.world();
		double min = cfg.lureMinDistance, max = cfg.lureMaxDistance;
		// a smaller ring keeps the carved cavern (and the chunks generated for it) small; the rules are the same
		cfg.lureMinDistance = 12.0;
		cfg.lureMaxDistance = 16.0;
		AbyssalBehemothEntity boss = null;
		try {
			Vec3 at = new Vec3(3000.5, 70.0, 3000.5);
			BlockPos c = BlockPos.containing(at);
			for (int cx = (c.getX() - 32) >> 4; cx <= (c.getX() + 32) >> 4; cx++) {
				for (int cz = (c.getZ() - 32) >> 4; cz <= (c.getZ() + 32) >> 4; cz++) {
					nether.getChunk(cx, cz);
				}
			}
			// a cavern: air from y 60 to 100, 27 blocks out each way, a netherrack floor under it
			fill(nether, c.offset(-27, -10, -27), c.offset(27, 30, 27), Blocks.AIR.defaultBlockState());
			fill(nether, c.offset(-27, -11, -27), c.offset(27, -11, 27), Blocks.NETHERRACK.defaultBlockState());

			var s = AbyssalLureItem.summon(nether, at, null);
			helper.assertTrue(s.result() == AbyssalLureItem.Result.SUMMONED, "the lure calls it in a cavern, got " + s.result());
			boss = s.behemoth();
			helper.assertTrue(boss != null && boss.isAlive() && boss.level() == nether, "a live Behemoth in the Nether");
			helper.assertTrue(BehemothSpawner.isOpenSpace(nether, boss.position()), "it rose in open air (its whole box clear), at " + boss.position());
			helper.assertTrue(nether.noCollision(boss.getBoundingBox()), "its box touches no block");
			double flat = Math.sqrt(Math.pow(boss.getX() - at.x, 2) + Math.pow(boss.getZ() - at.z, 2));
			helper.assertTrue(flat >= 11.0 && flat <= 17.0, "it rose a sensible distance out, not on top of the user: " + flat);
			helper.assertTrue(AbyssalLureItem.summon(nether, at, null).result() == AbyssalLureItem.Result.ALREADY_ACTIVE,
					"a second lure is refused while it lives");
		} finally {
			cfg.lureMinDistance = min;
			cfg.lureMaxDistance = max;
			if (boss != null) {
				boss.discard();
			}
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the To'kustar Shard

	@GameTest(template = EMPTY_STRUCTURE)
	public void lootTableAlwaysDropsOneShard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(level);
		boss.moveTo(helper.absoluteVec(new Vec3(1, 30, 1)));
		var table = level.getServer().reloadableRegistries().getLootTable(
				ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("entities/abyssal_behemoth")));
		for (int i = 0; i < 25; i++) {
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, boss)
					.withParameter(LootContextParams.ORIGIN, boss.position())
					.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic()).create(LootContextParamSets.ENTITY);
			int shards = 0;
			for (ItemStack st : table.getRandomItems(params)) {
				if (st.is(BehemothItems.TOKUSTAR_SHARD)) {
					shards += st.getCount();
				}
			}
			helper.assertTrue(shards == 1, "every kill drops exactly one To'kustar Shard, roll " + i + " gave " + shards);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shardUnlocksWayBigOnPrototypeOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String key = Aliens.foundKey(TokustarShardItem.ALIEN);

		ServerPlayer none = mock(helper, helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
		ItemStack s0 = new ItemStack(BehemothItems.TOKUSTAR_SHARD, 1);
		none.setItemInHand(InteractionHand.MAIN_HAND, s0);
		helper.assertTrue(s0.use(level, none, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "no watch: refused");
		helper.assertTrue(none.getMainHandItem().getCount() == 1, "no watch: not consumed");

		ServerPlayer recal = mock(helper, helper.absoluteVec(new Vec3(3.5, 2, 1.5)));
		helper.assertTrue(Omnitrix.grant(recal, true), "the Recalibrated Omnitrix goes on");
		ItemStack s1 = new ItemStack(BehemothItems.TOKUSTAR_SHARD, 1);
		recal.setItemInHand(InteractionHand.MAIN_HAND, s1);
		helper.assertTrue(s1.use(level, recal, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "recalibrated: refused");
		helper.assertTrue(Omnitrix.state(recal).readyAt(key) != 1L, "recalibrated: Way Big not unlocked");

		ServerPlayer p = mock(helper, helper.absoluteVec(new Vec3(5.5, 2, 1.5)));
		helper.assertTrue(Omnitrix.grant(p), "the prototype Omnitrix goes on");
		helper.assertTrue(Omnitrix.state(p).readyAt(key) != 1L, "sanity: Way Big starts locked");
		ItemStack shards = new ItemStack(BehemothItems.TOKUSTAR_SHARD, 2);
		p.setItemInHand(InteractionHand.MAIN_HAND, shards);
		var first = shards.use(level, p, InteractionHand.MAIN_HAND);
		helper.assertTrue(first.getResult().consumesAction(), "the prototype absorbs the shard, got " + first.getResult());
		helper.assertTrue(Omnitrix.state(p).readyAt(key) == 1L, "way_big is unlocked");
		helper.assertTrue(p.getMainHandItem().getCount() == 1, "one shard consumed, got " + p.getMainHandItem().getCount());
		var second = p.getMainHandItem().use(level, p, InteractionHand.MAIN_HAND);
		helper.assertTrue(second.getResult() == InteractionResult.FAIL, "a second shard is refused (already unlocked)");
		helper.assertTrue(p.getMainHandItem().getCount() == 1, "the refused shard is kept");
		helper.succeed();
	}

	// ---------------------------------------------------------------- sight-gated aggro

	/** High above the test floor (no other test's mock players within its 40-block sight range). */
	private static final double SKY = 150;

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void noTargetAndNoAttackWithoutLineOfSight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 bossAt = helper.absoluteVec(new Vec3(0.5, SKY, 0.5));
		Vec3 playerAt = helper.absoluteVec(new Vec3(18.5, SKY + 2, 0.5));
		BlockPos pp = BlockPos.containing(playerAt);
		// a sealed stone cell round the player, 18 blocks from the boss
		fill(level, pp.offset(-1, -1, -1), pp.offset(1, 2, 1), Blocks.STONE.defaultBlockState());
		fill(level, pp, pp.offset(0, 1, 0), Blocks.AIR.defaultBlockState());
		// a plain survival mock (the mock SERVER player reports isCreative() == true, which no boss may target)
		net.minecraft.world.entity.player.Player p = helper.makeMockPlayer(GameType.SURVIVAL);
		p.moveTo(playerAt.x, playerAt.y, playerAt.z, 0f, 0f);
		p.setNoGravity(true);

		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(level);
		boss.moveTo(bossAt.x, bossAt.y, bossAt.z, 0f, 0f);
		level.addFreshEntity(boss);
		float hp = p.getHealth();

		helper.runAfterDelay(40, () -> {
			helper.assertTrue(boss.getTarget() != p, "it must not pick a fight with a player it cannot see");
			helper.assertFalse(boss.canStrike(p), "no line of sight: it may not strike");
			// now the player hurts it (an arrow from a murder hole, say): it takes the player as its target...
			boss.invulnerableTime = 0;
			boolean landed = boss.hurt(level.damageSources().playerAttack(p), 30.0f);
			boss.threat().applyNow();
			helper.assertTrue(landed && boss.getTarget() == p, "being hurt gives it a reason to fight (landed " + landed + ", threat "
					+ boss.threat().threatOf(p) + ", target " + boss.getTarget() + ")");
		});
		helper.runAfterDelay(200, () -> {
			// ...but it still never attacks through the stone
			var shots = level.getEntitiesOfClass(BehemothFireballEntity.class, new AABB(bossAt, bossAt).inflate(80),
					f -> f.getOwner() == boss);
			helper.assertTrue(shots.isEmpty(), "no fireballs at a target it cannot see, found " + shots.size());
			helper.assertFalse(boss.canStrike(p), "still no line of sight through the cell");
			helper.assertTrue(p.getHealth() >= hp,"the sealed player took no damage: " + p.getHealth() + " < " + hp);
			helper.assertTrue(boss.getTarget() == null || boss.getTarget() == p, "it did not wander onto anyone else");
			boss.discard();
			fill(level, pp.offset(-1, -1, -1), pp.offset(1, 2, 1), Blocks.AIR.defaultBlockState());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- unstick

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void aWedgedBehemothShakesItselfFree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 at = helper.absoluteVec(new Vec3(0.5, SKY - 40, 0.5));
		BlockPos c = BlockPos.containing(at);
		fill(level, c.offset(-14, -4, -14), c.offset(14, 24, 14), Blocks.AIR.defaultBlockState());
		// a lump of rock right through the middle of its body
		fill(level, c.offset(-1, 3, -1), c.offset(1, 5, 1), Blocks.NETHERRACK.defaultBlockState());
		AbyssalBehemothEntity boss = BehemothEntityTypes.ABYSSAL_BEHEMOTH.create(level);
		boss.moveTo(at.x, at.y, at.z, 0f, 0f);
		level.addFreshEntity(boss);
		helper.assertFalse(level.noCollision(boss.getBoundingBox().deflate(0.3)), "sanity: it starts wedged");
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(level.noCollision(boss.getBoundingBox()), "it got itself out of the rock, now at " + boss.position());
			helper.assertTrue(boss.getHealth() >= boss.getMaxHealth(), "and it did not suffocate in it");
			boss.discard();
			fill(level, c.offset(-1, 3, -1), c.offset(1, 5, 1), Blocks.AIR.defaultBlockState());
			helper.succeed();
		});
	}
}

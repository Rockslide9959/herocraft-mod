package com.projecthero.mod.gametest;

import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.entity.PillagerSpy;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.raid.SupervillainMark;
import com.projecthero.mod.event.raid.SupervillainRaid;
import com.projecthero.mod.event.raid.SupervillainRaidStarter;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.21: the Pillager Spy marks the PLAYER -- Supervillain's Mark (Bad Omen) -> Supervillain Omen (Raid Omen) ->
 * Supervillain Raid. Mock players never fire ALLOW_DAMAGE, so the hit is driven through the AFTER_DAMAGE handler's own
 * entry point ({@link SupervillainRaidStarter#onPlayerDamaged}) with a real spy-owned arrow, and the per-tick step
 * through {@link SupervillainMark#tickPlayer}. The village tests run in their own batches: raids are world-global, and
 * the Peaceful test flips the server difficulty (restored in the same tick).
 */
public class V01421SpyMarkGameTests implements FabricGameTest {
	private static final String BATCH = "v01421_spy_mark";

	private static ServerPlayer survivor(GameTestHelper helper, Vec3 rel) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // the spy is a Monster
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		SupervillainMark.clear(p);
		return p;
	}

	private static PillagerSpy spy(GameTestHelper helper, Vec3 rel) {
		ServerLevel level = helper.getLevel();
		PillagerSpy spy = RaidEntityTypes.PILLAGER_SPY.create(level);
		helper.assertTrue(spy != null, "the spy must create");
		Vec3 at = helper.absoluteVec(rel);
		spy.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		spy.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.COMMAND, null);
		level.addFreshEntity(spy);
		return spy;
	}

	private static BlockPos placeBell(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel.below(), Blocks.STONE);
		helper.setBlock(rel, Blocks.BELL);
		return helper.absolutePos(rel);
	}

	private static void cleanupRaids(ServerLevel level) {
		for (EventInstance e : EventManager.active(level.getServer())) {
			if (e instanceof SupervillainRaid raid) {
				raid.abort(level);
				EventSavedData.get(level).remove(raid.id());
			}
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void spyBoltHitMarksThePlayerAnywhere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivor(helper, new Vec3(1.5, 2, 1.5));
		PillagerSpy spy = spy(helper, new Vec3(4.5, 2, 4.5));
		Arrow bolt = new Arrow(EntityType.ARROW, level);
		bolt.setOwner(spy);

		// The shared gametest world keeps bells from earlier batches, so "no village here" cannot be assumed.
		boolean village = PillagerSpy.insideVillage(level, player.blockPosition());
		SupervillainRaidStarter.onPlayerDamaged(player, bolt, spy);
		helper.assertTrue(SupervillainMark.isMarked(player), "the spy's bolt marks the player, village or not");
		MobEffectInstance mark = player.getEffect(SupervillainMark.MARK);
		helper.assertTrue(mark.getDuration() > 99 * 60 * 20 && mark.getDuration() <= 100 * 60 * 20,
				"the mark lasts 100 minutes, like Bad Omen (got " + mark.getDuration() + ")");
		helper.assertFalse(SupervillainMark.hasOmen(player), "the hit itself only marks");
		helper.assertTrue(EventManager.at(level, player.blockPosition()) == null, "no raid from the hit itself");
		if (!village) {
			SupervillainMark.tickPlayer(player, true);
			helper.assertTrue(SupervillainMark.isMarked(player) && !SupervillainMark.hasOmen(player),
					"outside a village the mark just waits");
		}

		SupervillainMark.clear(player);
		spy.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void killingTheSpyMarksTheKiller(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivor(helper, new Vec3(1.5, 2, 1.5));
		PillagerSpy spy = spy(helper, new Vec3(3.5, 2, 3.5));

		helper.assertFalse(SupervillainMark.isMarked(player), "not marked before the kill");
		spy.hurt(level.damageSources().playerAttack(player), 1000.0f);
		helper.assertFalse(spy.isAlive(), "the spy dies");
		helper.assertTrue(SupervillainMark.isMarked(player), "killing the spy marks the killer, like a raid captain");

		SupervillainMark.clear(player);
		spy.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void anOrdinaryPillagerMarksNobody(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivor(helper, new Vec3(1.5, 2, 1.5));
		Pillager pillager = EntityType.PILLAGER.create(level);
		helper.assertTrue(pillager != null, "the pillager must create");
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
		pillager.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		level.addFreshEntity(pillager);
		Arrow bolt = new Arrow(EntityType.ARROW, level);
		bolt.setOwner(pillager);

		SupervillainRaidStarter.onPlayerDamaged(player, bolt, pillager);
		helper.assertFalse(SupervillainMark.isMarked(player), "an ordinary Pillager's bolt does nothing");
		pillager.hurt(level.damageSources().playerAttack(player), 1000.0f);
		helper.assertFalse(SupervillainMark.isMarked(player), "killing an ordinary Pillager does nothing");

		pillager.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void milkClearsTheMarkAndTheOmen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivor(helper, new Vec3(1.5, 2, 1.5));

		helper.assertTrue(SupervillainMark.mark(player), "marked");
		new ItemStack(Items.MILK_BUCKET).finishUsingItem(level, player);
		helper.assertFalse(SupervillainMark.isMarked(player), "milk clears the Supervillain's Mark, like Bad Omen");

		// An omen in progress (as if it had converted here), then milk: no raid when it would have fired.
		player.addEffect(new MobEffectInstance(SupervillainMark.OMEN, 600, 0, false, false, true));
		player.setAttached(SupervillainMark.OMEN_STATE,
				new SupervillainMark.OmenState(player.blockPosition().asLong(), level.getGameTime()));
		new ItemStack(Items.MILK_BUCKET).finishUsingItem(level, player);
		helper.assertFalse(SupervillainMark.hasOmen(player), "milk clears the omen too");
		SupervillainMark.tickPlayer(player, true);
		helper.assertTrue(player.getAttached(SupervillainMark.OMEN_STATE) == null, "the cleared omen's record is dropped");
		helper.assertTrue(EventManager.at(level, player.blockPosition()) == null, "a milked-away omen starts no raid");
		helper.assertFalse(SupervillainMark.isMarked(player), "and does not hand the mark back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = BATCH + "_village")
	public void aMarkedPlayerInAVillageGetsTheOmenThenTheRaid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		placeBell(helper, new BlockPos(1, 2, 1));
		ServerPlayer player = survivor(helper, new Vec3(4.5, 2, 4.5));
		boolean[] done = {false};

		helper.succeedWhen(() -> {
			if (done[0]) {
				return;
			}
			if (!PillagerSpy.insideVillage(level, player.blockPosition())) {
				throw new GameTestAssertException("waiting: the bell to register as a village POI");
			}
			try {
				helper.assertTrue(SupervillainMark.mark(player), "marked");
				SupervillainMark.tickPlayer(player, true);
				helper.assertTrue(SupervillainMark.hasOmen(player), "in a village the mark turns into the omen");
				helper.assertFalse(SupervillainMark.isMarked(player), "the mark itself is gone (converted)");
				MobEffectInstance omen = player.getEffect(SupervillainMark.OMEN);
				helper.assertTrue(omen.getDuration() >= 30 * 20 && omen.getDuration() <= 31 * 20,
						"a ~30 s omen countdown (got " + omen.getDuration() + ")");
				helper.assertTrue(EventManager.at(level, player.blockPosition()) == null, "no raid during the countdown");

				SupervillainMark.expireOmenNow(player);
				SupervillainMark.tickPlayer(player, false);
				EventInstance raid = EventManager.at(level, player.blockPosition());
				helper.assertTrue(raid instanceof SupervillainRaid, "the omen ran out: a Supervillain Raid starts (got " + raid + ")");
				helper.assertFalse(SupervillainMark.isMarked(player) || SupervillainMark.hasOmen(player),
						"the mark is consumed");
				helper.assertTrue(player.getAttached(SupervillainMark.OMEN_STATE) == null, "omen record cleared");

				// A raid is already running here: a new mark waits instead of converting.
				helper.assertTrue(SupervillainMark.mark(player), "marked again");
				helper.assertFalse(SupervillainMark.tryConvert(player), "no omen while this village's raid runs");
				helper.assertTrue(SupervillainMark.isMarked(player), "the mark is kept until a raid can start");
				done[0] = true;
			} finally {
				SupervillainMark.clear(player);
				cleanupRaids(level);
			}
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = BATCH + "_peaceful")
	public void noRaidOnPeaceful(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		placeBell(helper, new BlockPos(1, 2, 1));
		ServerPlayer player = survivor(helper, new Vec3(4.5, 2, 4.5));
		boolean[] done = {false};

		helper.succeedWhen(() -> {
			if (done[0]) {
				return;
			}
			if (!PillagerSpy.insideVillage(level, player.blockPosition())) {
				throw new GameTestAssertException("waiting: the bell to register as a village POI");
			}
			var server = level.getServer();
			try {
				server.setDifficulty(Difficulty.PEACEFUL, true);
				helper.assertTrue(SupervillainMark.mark(player), "marked");
				SupervillainMark.tickPlayer(player, true);
				helper.assertFalse(SupervillainMark.hasOmen(player), "Peaceful: the mark never turns into the omen");
				helper.assertTrue(SupervillainMark.isMarked(player), "Peaceful: the mark is kept");

				// The world goes Peaceful mid-omen: no raid, and the mark comes back.
				server.setDifficulty(Difficulty.NORMAL, true);
				helper.assertTrue(SupervillainMark.tryConvert(player), "on Normal it converts");
				server.setDifficulty(Difficulty.PEACEFUL, true);
				SupervillainMark.expireOmenNow(player);
				SupervillainMark.tickPlayer(player, false);
				helper.assertTrue(EventManager.at(level, player.blockPosition()) == null, "no raid on Peaceful");
				helper.assertTrue(SupervillainMark.isMarked(player) && !SupervillainMark.hasOmen(player),
						"the omen fizzles and the mark is handed back");
				done[0] = true;
			} finally {
				server.setDifficulty(Difficulty.NORMAL, true);
				SupervillainMark.clear(player);
				cleanupRaids(level);
			}
		});
	}
}

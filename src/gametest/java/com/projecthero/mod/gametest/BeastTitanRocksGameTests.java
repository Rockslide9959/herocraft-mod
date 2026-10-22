package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.titanshifter.TitanBoulders;
import com.projecthero.mod.titanshifter.TitanCombat;
import com.projecthero.mod.titanshifter.TitanKit;
import com.projecthero.mod.titanshifter.TitanMove;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;
import com.projecthero.mod.titanshifter.entity.TitanRockEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): the Beast Titan's rocks are real entities -- Rock Scatter flings a spray of debris chunks that hit what
 * they touch and are gone a few seconds later; the Boulder Throw / Volley boulders fly as block clusters and burst into
 * debris; Pitcher's Fastball (Shift+R, replaced the Backhand) scoops, crushes and pitches a shotgun of stones that hurt one
 * target at most three times; ripping up the ground follows the terrain config and mobGriefing; the rock cap holds.
 */
public class BeastTitanRocksGameTests implements FabricGameTest {
	private static final int SETTLE = 80; // > transformTicks

	private static ServerPlayer beast(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-9, 0, -9), base.offset(9, 18, 22))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		TitanShifter.grant(p, TitanType.BEAST_TITAN);
		helper.onEachTick(() -> TitanShifter.tick(p));
		return p;
	}

	private static <T extends Mob> T dummy(GameTestHelper helper, EntityType<T> type, Vec3 at) {
		T z = type.create(helper.getLevel());
		z.moveTo(at.x, at.y, at.z, 0f, 0f);
		z.setNoAi(true);
		z.setNoGravity(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static List<TitanRockEntity> rocks(GameTestHelper helper, TitanFormEntity form) {
		return helper.getLevel().getEntitiesOfClass(TitanRockEntity.class, form.getBoundingBox().inflate(48.0), r -> r.formId() == form.getId());
	}

	private static String describe(GameTestHelper helper, TitanFormEntity form) {
		StringBuilder sb = new StringBuilder();
		for (TitanRockEntity r : rocks(helper, form)) {
			sb.append(String.format(java.util.Locale.ROOT, "[t%d %.1f,%.1f,%.1f v%.2f tick=%s]", r.tickCount, r.getX() - form.getX(), r.getY() - form.getY(),
					r.getZ() - form.getZ(), r.getDeltaMovement().length(), helper.getLevel().isPositionEntityTicking(r.blockPosition())));
		}
		return sb.toString();
	}

	private static Vec3 fwd(TitanFormEntity form) {
		return Vec3.directionFromRotation(0, form.getYRot());
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void rockScatterFlingsRealDebrisThatHitsAndCleansUp(GameTestHelper helper) {
		ServerPlayer p = beast(helper);
		helper.assertTrue(TitanMove.of(TitanType.BEAST_TITAN, 2, true) == TitanMove.ROCK_SCATTER, "Shift+G is Rock Scatter");
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			helper.assertTrue(form != null, "in the Beast Titan");
			p.setXRot(0f);
			p.setYRot(form.getYRot());
			Vec3 at = form.position().add(fwd(form).scale(form.getBbWidth() * 0.5 + 6.0)).add(0, 0.2, 0);
			LivingEntity z = dummy(helper, EntityType.ZOMBIE, at);
			float before = z.getHealth();
			TitanKit.handle(p, 2, true);
			helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanMove.ROCK_SCATTER.id) > 0, "Rock Scatter is on cooldown");
			helper.runAfterDelay(TitanMove.ROCK_SCATTER.impactTick + 1, () -> {
				List<TitanRockEntity> flying = rocks(helper, form);
				helper.assertTrue(flying.size() >= TitanKit.SCATTER_CHUNKS * 2 / 3, "a spray of real debris chunks: " + flying.size());
				helper.assertTrue(flying.stream().noneMatch(TitanRockEntity::isBoulder), "chunks, not boulders");
				helper.assertTrue(flying.stream().map(TitanRockEntity::scale).distinct().count() > 3, "of different sizes");
				helper.assertTrue(flying.stream().allMatch(r -> r.damage() > 0f), "every chunk can hit");
			});
			helper.runAfterDelay(TitanMove.ROCK_SCATTER.impactTick + 30, () -> {
				helper.assertTrue(z.getHealth() < before || !z.isAlive(), "a chunk hit the zombie in the cone");
				float dealt = before - z.getHealth();
				helper.assertTrue(dealt < TitanCombat.scaled(form, z, (float) TitanMove.ROCK_SCATTER.damage()) * 1.5f,
						"hit once (" + dealt + "), not by every chunk");
			});
			helper.runAfterDelay(TitanMove.ROCK_SCATTER.impactTick + 70, () -> {
				helper.assertTrue(rocks(helper, form).isEmpty(), "every chunk is gone (" + rocks(helper, form).size() + ")");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void boulderThrowFliesARealBoulderThatBurstsIntoDebris(GameTestHelper helper) {
		ServerPlayer p = beast(helper);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			Vec3 at = form.position().add(fwd(form).scale(form.getBbWidth() * 0.5 + 8.0)).add(0, 0.2, 0);
			LivingEntity z = dummy(helper, EntityType.IRON_GOLEM, at);
			p.lookAt(EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1.0, 0));
			float before = z.getHealth();
			TitanKit.handle(p, 3, true);
			helper.runAfterDelay(8, () -> {
				helper.assertTrue(form.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_BOULDER), "the boulder is in the hand");
				helper.assertTrue(rocks(helper, form).isEmpty(), "no entity yet while it is held");
			});
			helper.runAfterDelay(TitanMove.BOULDER_THROW.impactTick + 1, () -> {
				helper.assertFalse(form.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_BOULDER), "thrown: the hand is empty");
				List<TitanRockEntity> b = rocks(helper, form);
				helper.assertTrue(b.size() == 1 && b.get(0).isBoulder() && b.get(0).scale() >= 2.5f, "one big boulder flies");
			});
			helper.runAfterDelay(TitanMove.BOULDER_THROW.impactTick + 30, () -> {
				helper.assertTrue(z.getHealth() < before || !z.isAlive(), "the boulder hit the golem");
				helper.assertTrue(rocks(helper, form).stream().noneMatch(TitanRockEntity::isBoulder), "the boulder burst");
			});
			helper.runAfterDelay(TitanMove.BOULDER_THROW.impactTick + 90, () -> {
				helper.assertTrue(rocks(helper, form).isEmpty(), "its debris is gone too " + describe(helper, form));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void boulderVolleyThrowsThreeBoulders(GameTestHelper helper) {
		ServerPlayer p = beast(helper);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			p.setXRot(10f);
			p.setYRot(form.getYRot());
			TitanKit.handle(p, 4, true);
			helper.runAfterDelay(6, () -> helper.assertTrue(form.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_ROCKS), "stones in the hand"));
			helper.runAfterDelay(TitanMove.BOULDER_VOLLEY.impactTick + 5, () -> {
				// some may have burst already: boulders still flying, or the debris of the ones that landed
				helper.assertTrue(rocks(helper, form).size() >= 3, "the volley's boulders are real");
				helper.assertFalse(form.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_ROCKS), "the hand is empty after the third");
				helper.succeed();
			});
			helper.runAfterDelay(TitanMove.BOULDER_VOLLEY.impactTick + 1, () -> {
				long boulders = rocks(helper, form).stream().filter(TitanRockEntity::isBoulder).count();
				helper.assertTrue(boulders >= 1, "boulders in flight: " + boulders);
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void pitchersFastballReplacedTheBackhandAndSpraysStones(GameTestHelper helper) {
		helper.assertTrue(TitanMove.of(TitanType.BEAST_TITAN, 1, true) == TitanMove.FASTBALL, "Shift+R is Pitcher's Fastball");
		helper.assertTrue(TitanMove.byId("backhand") == null, "the Backhand is gone");
		ServerPlayer p = beast(helper);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			Vec3 at = form.position().add(fwd(form).scale(form.getBbWidth() * 0.5 + 7.0)).add(0, 0.2, 0);
			LivingEntity g = dummy(helper, EntityType.IRON_GOLEM, at);
			p.lookAt(EntityAnchorArgument.Anchor.EYES, g.position().add(0, 1.4, 0));
			float before = g.getHealth();
			TitanKit.handle(p, 1, true);
			helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanMove.FASTBALL.id) > 0, "on cooldown");
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(form.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_ROCKS), "a handful of stones scooped up");
				helper.assertTrue(rocks(helper, form).isEmpty(), "nothing thrown yet");
			});
			helper.runAfterDelay(TitanMove.FASTBALL.impactTick + 1, () -> {
				helper.assertFalse(form.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_ROCKS), "pitched: the hand is empty");
				List<TitanRockEntity> stones = rocks(helper, form);
				helper.assertTrue(stones.size() >= TitanKit.FASTBALL_STONES - 4, "a shotgun of stones: " + stones.size());
				helper.assertTrue(stones.stream().allMatch(r -> r.getDeltaMovement().length() > 2.0), "fast");
			});
			helper.runAfterDelay(TitanMove.FASTBALL.impactTick + 12, () -> {
				float dealt = before - g.getHealth();
				float one = TitanCombat.scaled(form, g, (float) TitanMove.FASTBALL.damage());
				helper.assertTrue(dealt > 0.0f, "the stones hit the golem");
				helper.assertTrue(dealt <= one * TitanKit.FASTBALL_PER_TARGET + 0.5f, "at most three stones count: " + dealt + " vs " + one);
			});
			helper.runAfterDelay(TitanMove.FASTBALL.impactTick + TitanKit.FASTBALL_LIFE + 5, () -> {
				helper.assertTrue(rocks(helper, form).isEmpty(), "every stone is gone " + describe(helper, form));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void rippingUpTheGroundFollowsTheTerrainRules(GameTestHelper helper) {
		ServerPlayer p = beast(helper);
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			ServerLevel level = helper.getLevel();
			Vec3 spot = form.position().add(fwd(form).scale(form.getBbWidth() * 0.5 + 3.0));
			BlockPos c = BlockPos.containing(spot.x, form.getY() - 0.5, spot.z);
			for (BlockPos pos : BlockPos.betweenClosed(c.offset(-2, 0, -2), c.offset(2, 0, 2))) {
				level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
			}
			Vec3 top = Vec3.atCenterOf(c).add(0, 0.5, 0);
			GameRules.BooleanValue rule = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
			boolean wasRule = rule.get();
			boolean wasCfg = HeroConfig.get().abilityTerrainDamage;
			try {
				rule.set(false, level.getServer());
				HeroConfig.get().abilityTerrainDamage = true;
				helper.assertTrue(TitanBoulders.ripGround(level, form, top, 2.0, 6) == 0, "mobGriefing off: nothing torn up");
				rule.set(true, level.getServer());
				HeroConfig.get().abilityTerrainDamage = false;
				helper.assertTrue(TitanBoulders.ripGround(level, form, top, 2.0, 6) == 0, "terrain damage off: nothing torn up");
				HeroConfig.get().abilityTerrainDamage = true;
				int n = TitanBoulders.ripGround(level, form, top, 2.0, 6);
				helper.assertTrue(n > 0 && n <= 6, "both on: a few blocks torn up (" + n + ")");
				int dirt = 0;
				for (BlockPos pos : BlockPos.betweenClosed(c.offset(-2, 0, -2), c.offset(2, 0, 2))) {
					dirt += level.getBlockState(pos).is(Blocks.DIRT) ? 1 : 0;
				}
				helper.assertTrue(dirt == 25 - n, "exactly those blocks went (" + dirt + " dirt left)");
			} finally {
				rule.set(wasRule, level.getServer());
				HeroConfig.get().abilityTerrainDamage = wasCfg;
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cosmeticDebrisStopsAtTheCap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 4.0, 1.5));
		int spawned = 0;
		for (int i = 0; i < TitanBoulders.CAP + 20; i++) {
			TitanRockEntity r = TitanRockEntity.create(level, TitanRockEntity.KIND_CHUNK, Blocks.STONE.defaultBlockState(), 0.4f, at,
					Vec3.ZERO, 0.0f).maxAge(5);
			if (TitanBoulders.spawn(level, r)) {
				spawned++;
			}
		}
		helper.assertTrue(TitanBoulders.liveCount() <= TitanBoulders.CAP, "never more than the cap: " + TitanBoulders.liveCount());
		helper.assertTrue(spawned <= TitanBoulders.CAP, "the extra cosmetic chunks were skipped: " + spawned);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(level.getEntitiesOfClass(TitanRockEntity.class, new AABB(at, at).inflate(4.0)).isEmpty(), "all aged out");
			helper.succeed();
		});
	}
}

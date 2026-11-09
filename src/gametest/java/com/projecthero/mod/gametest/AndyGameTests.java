package com.projecthero.mod.gametest;

import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyAbilities;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.AndyParts;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyCloneEntity;
import com.projecthero.mod.andy.entity.AndyPartEntity;
import com.projecthero.mod.andy.item.AndyItems;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Andy (Undead Unluck): where hits land, a part breaking and regrowing (for hunger), Undead (lethal damage shatters,
 * /kill still kills), the card and the one-Primary rule, the speed cycle, Victor, and each move's server side. Mock players
 * are not ticked by the server, so tests over time drive {@link Andy#tick}.
 */
public class AndyGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		p.invulnerableTime = 0;
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer andy(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Andy.grant(p);
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<AndyState> fn) {
		AndyState n = Andy.state(p).copy();
		fn.accept(n);
		p.setAttached(ModAttachments.ANDY_STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zo = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zo.setNoAi(true);
		zo.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		zo.setHealth(200);
		return zo;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void hitsLandOnThePartTheyTouch(GameTestHelper helper) {
		ServerPlayer p = andy(helper); // yaw 0: facing +z, his right is -x
		AABB b = p.getBoundingBox();
		Vec3 c = b.getCenter();
		double h = b.getYsize();
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x, b.minY + h * 0.9, c.z)) == AndyState.HEAD, "the top quarter is the head");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x, b.minY + h * 0.55, c.z)) == AndyState.BODY, "the middle is the body");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x - 0.28, b.minY + h * 0.55, c.z)) == AndyState.RIGHT_ARM, "his right side is the right arm");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x + 0.28, b.minY + h * 0.55, c.z)) == AndyState.LEFT_ARM, "his left side is the left arm");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x - 0.1, b.minY + h * 0.2, c.z)) == AndyState.RIGHT_LEG, "low right is the right leg");
		helper.assertTrue(AndyParts.locate(p, new Vec3(c.x + 0.1, b.minY + h * 0.2, c.z)) == AndyState.LEFT_LEG, "low left is the left leg");
		float[] fall = AndyParts.assign(p, p.damageSources().fall(), 6f, p.getRandom());
		helper.assertTrue(fall[AndyState.RIGHT_LEG] == 3f && fall[AndyState.LEFT_LEG] == 3f && fall[AndyState.HEAD] == 0f, "a fall lands on both legs");
		float[] blast = AndyParts.assign(p, p.damageSources().explosion(null, null), 10f, p.getRandom());
		for (float f : blast) {
			helper.assertTrue(f > 0f, "an explosion hits every part");
		}
		float[] magic = AndyParts.assign(p, p.damageSources().magic(), 10f, p.getRandom());
		for (float f : magic) {
			helper.assertTrue(f == 0f, "magic hits no part");
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anArrowBreaksTheArmItHits(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		AABB b = p.getBoundingBox();
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), b.getCenter().x - 0.27,
				b.minY + b.getYsize() * 0.55, b.getCenter().z, new ItemStack(net.minecraft.world.item.Items.ARROW), null);
		boolean hurt = p.hurt(p.damageSources().arrow(arrow, null), 9f);
		helper.assertTrue(Andy.missing(p, AndyState.RIGHT_ARM), "9 damage on the right arm (8 HP) breaks it: hurt=" + hurt + " hp=" + Andy.state(p).partHp);
		helper.assertTrue(!Andy.missing(p, AndyState.LEFT_ARM) && !Andy.missing(p, AndyState.BODY), "nothing else breaks");
		helper.assertTrue(Andy.state(p).kind(AndyState.RIGHT_ARM) == AndyState.KIND_BURST, "it bursts into pixels");
		helper.succeed();
	}

	// v0.17.3: the three-layer regrowth takes 170 ticks at Normal -- with the delay that is past 200
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
	public void aBrokenPartRegrowsAfterTheDelayForHunger(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_NORMAL);
		p.getFoodData().setFoodLevel(20);
		Andy.breakPart(p, AndyState.LEFT_LEG, AndyState.KIND_BURST);
		helper.assertTrue(Andy.missing(p, AndyState.LEFT_LEG), "broken");
		Andy.reconcile(p);
		helper.assertTrue(Andy.stage(p) == com.projecthero.mod.andy.AndyStages.LIMP, "one leg gone: limping on the other");
		double walk = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(walk < 0.1 * 0.75, "and slowed: " + walk);
		long start = helper.getLevel().getGameTime();
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(AndyConfig.REGROW_DELAY[1] - 5, () -> helper.assertTrue(Andy.state(p).regrowStart(AndyState.LEFT_LEG) == 0L,
				"still waiting before the delay"));
		helper.runAfterDelay(AndyConfig.REGROW_DELAY[1] + 3, () -> {
			helper.assertTrue(Andy.state(p).regrowStart(AndyState.LEFT_LEG) > 0L, "regrowing after the delay");
			helper.assertTrue(p.getFoodData().getFoodLevel() + p.getFoodData().getSaturationLevel() <= 20f + 5f - AndyConfig.partFood(1) + 1.0e-3f,
					"its share was paid as it started");
		});
		helper.runAfterDelay(AndyConfig.REGROW_DELAY[1] + AndyConfig.REGROW_TICKS[1] + 4, () -> {
			helper.assertTrue(!Andy.missing(p, AndyState.LEFT_LEG), "whole again after " + (helper.getLevel().getGameTime() - start) + " ticks");
			helper.assertTrue(Andy.state(p).hp(AndyState.LEFT_LEG) == AndyConfig.PART_HP[AndyState.LEFT_LEG], "full HP");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.1) < 1.0e-4, "no longer slowed");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBodyRegrowsBeforeTheRest(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_FAST);
		Andy.breakPart(p, AndyState.BODY, AndyState.KIND_BURST);
		Andy.breakPart(p, AndyState.RIGHT_ARM, AndyState.KIND_BURST);
		edit(p, n -> {
			long old = Math.max(1L, helper.getLevel().getGameTime() - 200);
			n.setTimes(AndyState.BODY, old, 0L, 0L);
			n.setTimes(AndyState.RIGHT_ARM, old, 0L, 0L);
		});
		Andy.tick(p);
		helper.assertTrue(Andy.state(p).regrowStart(AndyState.BODY) > 0L, "the body starts");
		helper.assertTrue(Andy.state(p).regrowStart(AndyState.RIGHT_ARM) == 0L, "the arm waits for the body");
		helper.succeed();
	}

	/** Breaks {@code parts} now; they may start regrowing as soon as their own wait is up (the tests then drive the ticks). */
	private static void breakAll(ServerPlayer p, int... parts) {
		for (int part : parts) {
			Andy.breakPart(p, part, AndyState.KIND_BURST);
		}
	}

	/** v0.17.3 (user): the torso first; both arms start together the moment its shoulders are there. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
	public void bothArmsStartTogetherOnceTheTorsoHasShoulders(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_NORMAL);
		p.getFoodData().setFoodLevel(20);
		breakAll(p, AndyState.BODY, AndyState.RIGHT_ARM, AndyState.LEFT_ARM);
		helper.onEachTick(() -> {
			Andy.tick(p);
			AndyState s = Andy.state(p);
			long now = helper.getLevel().getGameTime();
			long bs = s.regrowStart(AndyState.BODY), be = s.regrowEnd(AndyState.BODY);
			long r = s.regrowStart(AndyState.RIGHT_ARM), l = s.regrowStart(AndyState.LEFT_ARM);
			if (bs <= 0L) {
				helper.assertTrue(r == 0L && l == 0L, "nothing before the torso");
				return;
			}
			helper.assertTrue(be - bs == AndyConfig.regrowTicks(AndyState.BODY, AndyConfig.SPEED_NORMAL) && be - bs > AndyConfig.REGROW_TICKS[1],
					"the torso takes a little longer than a limb: " + (be - bs));
			long mark = Andy.torsoMark(bs, be, AndyConfig.SHOULDER_SHARE);
			if (now < mark) {
				helper.assertTrue(r == 0L && l == 0L, "no arms before the shoulders (" + now + " < " + mark + ")");
			} else {
				helper.assertTrue(r == mark && l == mark, "both arms start together at the shoulders: " + r + " / " + l + ", mark " + mark);
				helper.assertTrue(s.broken(AndyState.BODY) && s.regrowth(AndyState.BODY, now) < 0.5f, "while the torso is still growing");
				helper.succeed();
			}
		});
	}

	/** v0.17.3 (user): "once torso is done both legs at the same time". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 500)
	public void bothLegsStartTogetherWhenTheTorsoIsDone(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_NORMAL);
		p.getFoodData().setFoodLevel(20);
		breakAll(p, AndyState.BODY, AndyState.RIGHT_LEG, AndyState.LEFT_LEG);
		helper.onEachTick(() -> {
			Andy.tick(p);
			AndyState s = Andy.state(p);
			long now = helper.getLevel().getGameTime();
			if (s.broken(AndyState.BODY)) {
				helper.assertTrue(s.regrowStart(AndyState.RIGHT_LEG) == 0L && s.regrowStart(AndyState.LEFT_LEG) == 0L, "no legs before the hips");
			} else {
				helper.assertTrue(s.regrowStart(AndyState.RIGHT_LEG) == now && s.regrowStart(AndyState.LEFT_LEG) == now,
						"both legs start together the tick the torso is done");
				helper.succeed();
			}
		});
	}

	/** Limbs lost while the torso is whole regrow at once, all together. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void limbsLostWithTheTorsoWholeRegrowInParallel(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_NORMAL);
		p.getFoodData().setFoodLevel(20);
		int[] parts = { AndyState.RIGHT_ARM, AndyState.LEFT_ARM, AndyState.LEFT_LEG };
		breakAll(p, parts);
		helper.onEachTick(() -> {
			Andy.tick(p);
			AndyState s = Andy.state(p);
			long now = helper.getLevel().getGameTime();
			if (s.regrowStart(AndyState.RIGHT_ARM) > 0L) {
				for (int part : parts) {
					helper.assertTrue(s.regrowStart(part) == now && s.regrowEnd(part) - now == AndyConfig.REGROW_TICKS[1], "part " + part + " starts with the rest");
				}
				helper.succeed();
			}
		});
	}

	/** v0.17.3 (user): flat costs, paid as each part starts: saturation first, then the bar. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aPartPaysItsShareUpFrontSaturationFirst(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_FAST);
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(1f);
		breakAll(p, AndyState.LEFT_ARM);
		helper.onEachTick(() -> {
			Andy.tick(p);
			AndyState s = Andy.state(p);
			if (s.regrowStart(AndyState.LEFT_ARM) > 0L) {
				// Fast: 3 points -- the 1 of saturation, then 2 off the bar, all at once
				helper.assertTrue(p.getFoodData().getSaturationLevel() < 1.0e-4f, "the saturation went first");
				helper.assertTrue(p.getFoodData().getFoodLevel() == 18, "then the bar: " + p.getFoodData().getFoodLevel());
				helper.assertTrue(s.regrowEnd(AndyState.LEFT_ARM) - s.regrowStart(AndyState.LEFT_ARM) == AndyConfig.REGROW_TICKS[2], "paid: at Fast");
				helper.succeed();
			}
		});
	}

	/** v0.17.3 (user): a part that can not be paid for in full regrows at half Normal whatever the mode, after a doubled wait. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 900)
	public void anUnpaidPartTakesTwiceNormalInAnyMode(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		int[] speeds = { AndyConfig.SPEED_NORMAL, AndyConfig.SPEED_FAST, AndyConfig.SPEED_BURST };
		int[] k = { 0 };
		long[] broke = { 0L };
		Runnable begin = () -> {
			Andy.restore(p);
			Andy.setSpeed(p, speeds[k[0]]);
			p.getFoodData().setFoodLevel(1); // less than any share
			p.getFoodData().setSaturation(0f);
			Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
			broke[0] = helper.getLevel().getGameTime();
		};
		begin.run();
		helper.onEachTick(() -> {
			Andy.tick(p);
			AndyState s = Andy.state(p);
			long now = helper.getLevel().getGameTime();
			if (s.regrowStart(AndyState.LEFT_ARM) <= 0L) {
				return;
			}
			int speed = speeds[k[0]];
			long len = s.regrowEnd(AndyState.LEFT_ARM) - s.regrowStart(AndyState.LEFT_ARM);
			helper.assertTrue(now - broke[0] >= AndyConfig.starvedDelay(), "speed " + speed + ": the wait doubles from Normal's (" + (now - broke[0]) + ")");
			helper.assertTrue(len == AndyConfig.STARVED_TICKS && len == 2 * AndyConfig.REGROW_TICKS[AndyConfig.SPEED_NORMAL],
					"speed " + speed + " unpaid takes " + len + " ticks, not 340");
			helper.assertTrue(Andy.starvedRegrowth(s, AndyState.LEFT_ARM), "and shows as starved");
			helper.assertTrue(p.getFoodData().getFoodLevel() == 0, "what there was is taken");
			if (++k[0] == speeds.length) {
				helper.succeed();
			} else {
				begin.run();
			}
		});
	}

	/** Paid and unpaid parts side by side: the one started with food is at its speed, the one started without at half Normal. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
	public void onlyTheUnpaidPartIsSlow(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_NORMAL);
		p.getFoodData().setFoodLevel(2); // one Normal share
		p.getFoodData().setSaturation(0f);
		breakAll(p, AndyState.RIGHT_ARM, AndyState.LEFT_ARM);
		helper.onEachTick(() -> {
			Andy.tick(p);
			AndyState s = Andy.state(p);
			if (s.regrowStart(AndyState.RIGHT_ARM) > 0L && s.regrowStart(AndyState.LEFT_ARM) > 0L) {
				long r = s.regrowEnd(AndyState.RIGHT_ARM) - s.regrowStart(AndyState.RIGHT_ARM);
				long l = s.regrowEnd(AndyState.LEFT_ARM) - s.regrowStart(AndyState.LEFT_ARM);
				helper.assertTrue(r == AndyConfig.REGROW_TICKS[1] && l == AndyConfig.STARVED_TICKS, "the first paid (Normal), the second not (half): " + r + " / " + l);
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lethalDamageShattersButKillStillKills(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.hurt(p.damageSources().magic(), 1000f);
		helper.assertTrue(p.isAlive() && p.getHealth() == p.getMaxHealth(), "Undead: alive, health refilled");
		AndyState s = Andy.state(p);
		helper.assertTrue(!s.broken(AndyState.HEAD), "the head stays");
		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			helper.assertTrue(s.broken(i) && s.kind(i) == AndyState.KIND_SHATTER, "part " + i + " shattered");
		}
		helper.assertTrue(Andy.reforming(p), "the grace after the shatter");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 5f);
		helper.assertTrue(p.getHealth() == hp, "nothing hurts while re-forming");
		p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
		helper.assertTrue(!p.isAlive(), "/kill still kills");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCardGrantsThePowerAndTakesThePrimarySlot(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		ItemStack card = new ItemStack(AndyItems.UNDEAD_CARD);
		p.setItemInHand(InteractionHand.MAIN_HAND, card);
		card.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(Andy.hasPower(p), "the card grants the power");
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the card is used up");
		helper.assertTrue(!Kryptonian.hasPower(p), "the old Primary is replaced");
		helper.assertTrue(HeroTiers.holdsHero(p, Andy.KEY), "listed as the held hero");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(Andy.KEY), "never from a serum");
		helper.assertTrue(!Andy.grant(p), "a second grant is refused");
		Andy.revoke(p);
		helper.assertTrue(!Andy.hasPower(p), "revoked");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void vTogglesFastRegeneration(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "starts on Normal");
		Andy.cycleSpeed(p);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "then Fast");
		Andy.cycleSpeed(p);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "then Burst");
		Andy.cycleSpeed(p);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "and back to Normal (no Slow to pick)");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tapVCyclesTheSpeedOnThePress(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "a press of V cycles the speed (there is no style to hold for any more)");
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_FAST, "the release does nothing");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSpeedIsSavedAndOldSavesWithAStyleStillLoad(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_SLOW);
		AndyState s = Andy.state(p);
		net.minecraft.nbt.Tag tag = AndyState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, s).getOrThrow();
		AndyState back = AndyState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.regenSpeed == AndyConfig.SPEED_SLOW, "saved and loaded: " + tag);
		net.minecraft.nbt.CompoundTag old = ((net.minecraft.nbt.CompoundTag) tag).copy();
		old.remove("extra");
		old.putInt("regen_style", 2); // a save from before there was only one look
		AndyState fromOld = AndyState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(fromOld.hasPower && fromOld.regenSpeed == AndyConfig.SPEED_SLOW && fromOld.extra.size() == AndyState.EXTRA,
				"an old save with regen_style still loads (the key is ignored)");
		Andy.breakPart(p, AndyState.RIGHT_ARM, AndyState.KIND_BURST);
		Andy.clearTransient(p); // a real death, a new world, logging out
		helper.assertTrue(!Andy.missing(p, AndyState.RIGHT_ARM), "made whole");
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_SLOW, "the speed stays");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aRegrowthTakesTheThreeLayerTime(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(AndyConfig.REGROW_TICKS[AndyConfig.SPEED_NORMAL] == 170, "Normal: 8.5 s");
		helper.assertTrue(Math.abs(AndyConfig.LAYER_MUSCLE[0] * 170f - 40f) < 0.01f && Math.abs(AndyConfig.LAYER_SKIN[0] * 170f - 80f) < 0.01f,
				"the muscle starts 2 s after the bone, the skin 2 s after that");
		helper.assertTrue(Math.abs(AndyConfig.LAYER_SKIN[0] + AndyConfig.LAYER_SKIN[1] - 1f) < 1.0e-4f, "the skin closing is the end of it");
		int start = AndyConfig.REGROW_DELAY[AndyConfig.SPEED_SLOW] + 10;
		for (int k = 0; k < AndyConfig.SPEEDS; k++) {
			int speed = k;
			helper.runAfterDelay(start + k, () -> {
				Andy.restore(p);
				p.getFoodData().setFoodLevel(20);
				Andy.setSpeed(p, speed);
				Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
				long now = helper.getLevel().getGameTime();
				edit(p, n -> n.setTimes(AndyState.LEFT_ARM, now - AndyConfig.REGROW_DELAY[speed], 0L, 0L));
				Andy.tick(p);
				AndyState s = Andy.state(p);
				long len = s.regrowEnd(AndyState.LEFT_ARM) - s.regrowStart(AndyState.LEFT_ARM);
				helper.assertTrue(s.regrowStart(AndyState.LEFT_ARM) > 0L, "speed " + speed + " starts regrowing");
				helper.assertTrue(len == AndyConfig.REGROW_TICKS[speed], "speed " + speed + " takes " + len + " ticks, not " + AndyConfig.REGROW_TICKS[speed]);
			});
		}
		helper.runAfterDelay(start + 5, helper::succeed);
	}

	/** v0.17.3 (user): a whole body costs 10 / 15 / 20 food on Normal / Fast / Burst, each part paid as it starts, saturation first. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1200)
	public void aWholeBodyCostsTheSpeedsShareOfTheHungerBar(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(AndyConfig.REGROW_TICKS[AndyConfig.SPEED_BURST] < AndyConfig.REGROW_TICKS[AndyConfig.SPEED_FAST], "Burst is faster than Fast");
		int[] speeds = { AndyConfig.SPEED_NORMAL, AndyConfig.SPEED_FAST, AndyConfig.SPEED_BURST };
		int[] want = { 10, 15, 20 };
		int[] k = { 0 };
		int[] startFood = { 0 };
		Runnable begin = () -> {
			Andy.restore(p);
			Andy.setSpeed(p, speeds[k[0]]);
			p.getFoodData().setFoodLevel(20);
			p.getFoodData().setSaturation(5f);
			for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
				Andy.breakPart(p, i, AndyState.KIND_BURST);
			}
			startFood[0] = p.getFoodData().getFoodLevel() + 5; // the bar and the saturation together
		};
		begin.run();
		helper.onEachTick(() -> {
			Andy.tick(p);
			AndyState s = Andy.state(p);
			boolean any = false;
			for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
				any |= s.broken(i);
			}
			if (!any) {
				helper.assertTrue(p.getFoodData().getSaturationLevel() < 1.0e-4f, "the saturation went first");
				int spent = startFood[0] - p.getFoodData().getFoodLevel();
				helper.assertTrue(Math.abs(spent - want[k[0]]) <= 1, "speed " + speeds[k[0]] + ": the whole body cost " + spent + ", not " + want[k[0]]);
				if (++k[0] == speeds.length) {
					helper.succeed();
				} else {
					begin.run();
				}
			}
		});
	}

	// ---------------------------------------------------------------- R: finger shots eat the arm, the stretch punch

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fingerShotNumbers(GameTestHelper helper) {
		helper.assertTrue(AndyConfig.PARTS_BULLET_COOLDOWN == 10, "Parts Bullet: half a second");
		helper.assertTrue(AndyConfig.CRIMSON_DAMAGE == 12f && AndyConfig.CRIMSON_COOLDOWN == 20, "Crimson Bullet: 12 damage, 1 s");
		helper.assertTrue(AndyConfig.PUNCH_DAMAGE == 20f && AndyConfig.PUNCH_COOLDOWN == 20, "Stretch Punch: 20 damage, 1 s");
		helper.assertTrue(Math.abs(AndyConfig.CRIMSON_EAT - 4f * AndyConfig.PARTS_EAT) < 1.0e-5f, "a Crimson Bullet takes four times the bite");
		helper.assertTrue(AndyConfig.EATEN_REGROW_WAIT == 40, "regrowth waits 2 s without a shot");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void fingerShotsEatTheArmAndItWaitsToRegrow(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f);
		int arm = Andy.mainArm(p);
		AndyAbilities.partsBullet(p);
		float one = Andy.state(p).eaten(arm, helper.getLevel().getGameTime());
		helper.assertTrue(Math.abs(one - AndyConfig.PARTS_EAT) < 0.002f, "one shot takes a twelfth: " + one);
		edit(p, n -> n.abilityReadyAt.clear());
		AndyAbilities.partsBullet(p);
		edit(p, n -> n.abilityReadyAt.clear());
		AndyAbilities.crimsonBullet(p);
		float six = Andy.state(p).eaten(arm, helper.getLevel().getGameTime());
		helper.assertTrue(Math.abs(six - 6f / 12f) < 0.003f, "two Parts and a Crimson: half the arm: " + six);
		long shot = helper.getLevel().getGameTime();
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(AndyConfig.EATEN_REGROW_WAIT - 4, () -> helper.assertTrue(
				Math.abs(Andy.state(p).eaten(arm, helper.getLevel().getGameTime()) - six) < 0.001f, "nothing grows back before 2 s"));
		helper.runAfterDelay(AndyConfig.EATEN_REGROW_WAIT + 2, () -> helper.assertTrue(Andy.state(p).eatenRegrow(arm, helper.getLevel().getGameTime()) > 0f,
				"it starts growing back after 2 s without a shot (" + (helper.getLevel().getGameTime() - shot) + " ticks)"));
		helper.runAfterDelay(AndyConfig.EATEN_REGROW_WAIT + 4, () -> {
			// shooting again stops it and the bite carries on from what is still gone
			edit(p, n -> n.abilityReadyAt.clear());
			AndyAbilities.partsBullet(p);
			AndyState s = Andy.state(p);
			helper.assertTrue(s.eatenRegrow(arm, helper.getLevel().getGameTime()) == 0f, "a new shot restarts the wait");
			// eat the rest: the arm is used up and gone, and the other arm shoots
			for (int i = 0; i < 8 && !Andy.missing(p, arm); i++) {
				edit(p, n -> n.abilityReadyAt.clear());
				AndyAbilities.crimsonBullet(p);
			}
			helper.assertTrue(Andy.missing(p, arm) && Andy.state(p).kind(arm) == AndyState.KIND_SPENT, "a used-up arm is gone");
			helper.assertTrue(Andy.shootingArm(p) == Andy.otherArm(arm), "the other arm shoots now");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noArmsNoShot(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.breakPart(p, AndyState.RIGHT_ARM, AndyState.KIND_BURST);
		Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
		AndyAbilities.partsBullet(p);
		AndyAbilities.crimsonBullet(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> true).isEmpty(), "nothing fires with no arms");
		helper.assertTrue(!AndyAbilities.startPunch(p), "and no punch winds up");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void holdRThrowsTheArmAndUsesTheOtherWhenTheMainIsGone(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f);
		int main = Andy.mainArm(p);
		Andy.breakPart(p, main, AndyState.KIND_BURST);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(AndyConfig.PUNCH_HOLD_TICKS + 3, () -> helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_PUNCH), "held: the arm draws back"));
		helper.runAfterDelay(AndyConfig.PUNCH_MIN_HOLD + 4, () -> {
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, false);
			int other = Andy.otherArm(main);
			helper.assertTrue(Andy.missing(p, other) && Andy.state(p).kind(other) == AndyState.KIND_LAUNCHED, "the other arm was thrown");
			AABB box = new AABB(p.blockPosition()).inflate(6);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_PUNCH && e.bodyPart() == other)
					.size() == 1, "it flies as the punch");
			helper.assertTrue(Andy.cooldownRemaining(p, AndyAbilities.PUNCH) > 0, "1 s cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aTapOfRIsStillAPartsBullet(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(2, () -> {
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, r, false);
			AABB box = new AABB(p.blockPosition()).inflate(6);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_FINGER).size() == 1,
					"a tap fires a finger");
			helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_PUNCH), "and winds nothing up");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- flight, the boost, the head-only regrowth

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flightSpeeds(GameTestHelper helper) {
		helper.assertTrue(Math.abs(AndyConfig.PROPULSION_SPEED * 20 - 25) < 1.0e-6, "25 b/s");
		helper.assertTrue(Math.abs(AndyConfig.BOOST_SPEED * 20 - 40) < 1.0e-6, "40 b/s boosting");
		helper.assertTrue(Math.abs(AndyConfig.HEAD_FLIGHT_SPEED * 20 - 32) < 1.0e-6, "32 b/s as a head");
		ServerPlayer p = andy(helper);
		helper.assertTrue(com.projecthero.mod.andy.AndyFlight.start(p) && com.projecthero.mod.andy.AndyFlight.isFlying(p), "takes off");
		AndyState s = Andy.state(p);
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(com.projecthero.mod.andy.AndyFlight.speed(s, now) == AndyConfig.PROPULSION_SPEED, "flying at 25");
		com.projecthero.mod.andy.AndyFlight.setBoost(p, true);
		helper.assertTrue(com.projecthero.mod.andy.AndyFlight.speed(Andy.state(p), now) == AndyConfig.BOOST_SPEED, "boosting at 40");
		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			int part = i;
			edit(p, n -> n.setTimes(part, now, 0L, 0L));
		}
		helper.assertTrue(com.projecthero.mod.andy.AndyFlight.speed(Andy.state(p), now) == AndyConfig.HEAD_FLIGHT_SPEED, "a head flies at 32, boost or not");
		helper.assertTrue(!Andy.state(p).headOnly(now) || Andy.stage(p) == com.projecthero.mod.andy.AndyStages.ROLL, "and is a rolling head");
		com.projecthero.mod.andy.AndyFlight.stop(p);
		helper.assertTrue(!com.projecthero.mod.andy.AndyFlight.isFlying(p) && !Andy.state(p).flag(AndyState.FLAG_BOOST), "lands");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theBoostBurnsLegsThenArmsThenBodyDownToTheHead(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f);
		float health = p.getHealth();
		com.projecthero.mod.andy.AndyFlight.start(p);
		com.projecthero.mod.andy.AndyFlight.setBoost(p, true);
		int legs = AndyConfig.BURN_TICKS[0], arms = legs + AndyConfig.BURN_TICKS[1], body = arms + AndyConfig.BURN_TICKS[2];
		helper.assertTrue(body == 80, "4 s from whole to a head");
		int[] ticks = { 0 };
		helper.onEachTick(() -> {
			edit(p, n -> n.holdStart = helper.getLevel().getGameTime()); // no time limit in the test
			Andy.tick(p);
			ticks[0]++;
			AndyState s = Andy.state(p);
			if (ticks[0] == legs - 2) {
				helper.assertTrue(!s.broken(AndyState.RIGHT_LEG) && !s.broken(AndyState.LEFT_LEG), "the legs are still burning");
			}
			if (ticks[0] == legs + 2) {
				helper.assertTrue(s.broken(AndyState.RIGHT_LEG) && s.broken(AndyState.LEFT_LEG) && s.kind(AndyState.LEFT_LEG) == AndyState.KIND_BURNT,
						"the legs went first");
				helper.assertTrue(!s.broken(AndyState.RIGHT_ARM) && !s.broken(AndyState.BODY), "nothing else yet");
			}
			if (ticks[0] == arms + 2) {
				helper.assertTrue(s.broken(AndyState.RIGHT_ARM) && s.broken(AndyState.LEFT_ARM) && !s.broken(AndyState.BODY), "then the arms");
			}
			if (ticks[0] == body + 2) {
				helper.assertTrue(s.broken(AndyState.BODY) && !s.broken(AndyState.HEAD), "then the body: just the head");
				helper.assertTrue(s.regrowStart(AndyState.BODY) == 0L, "nothing regrows mid-flight");
				helper.assertTrue(p.isAlive() && p.getHealth() >= health, "burning never hurts him");
				com.projecthero.mod.andy.AndyFlight.stop(p);
			}
			if (ticks[0] == body + 4) {
				helper.assertTrue(Andy.state(p).regrowStart(AndyState.BODY) > 0L, "landed: the head-only regrowth is planned");
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void headOnlyRegrowthOrder(GameTestHelper helper) {
		for (int speed = AndyConfig.SPEED_NORMAL; speed <= AndyConfig.SPEED_BURST; speed++) {
			long[][] plan = Andy.headOnlyPlan(speed, 1000L);
			long[] body = plan[0], main = plan[1], off = plan[2], leg1 = plan[3], leg2 = plan[4];
			helper.assertTrue(body[0] == 1000L && body[1] - body[0] == AndyConfig.regrowTicks(AndyState.BODY, speed), "the torso first");
			float share = (main[0] - body[0]) / (float) (body[1] - body[0]);
			helper.assertTrue(main[0] > body[0] && Math.abs(share - AndyConfig.SHOULDER_SHARE) < 0.02f, "the arms start at the shoulders: " + share);
			helper.assertTrue(main[0] == off[0] && main[1] == off[1], "both arms together");
			helper.assertTrue(main[1] - main[0] == AndyConfig.REGROW_TICKS[speed], "an arm takes a limb's time");
			helper.assertTrue(leg1[0] == body[1], "the legs start when the torso is done");
			helper.assertTrue(leg1[0] == leg2[0] && leg1[1] == leg2[1], "both legs together");
			helper.assertTrue(main[0] + AndyConfig.REGROW_TICKS[speed] * com.projecthero.mod.andy.AndyStages.ARM_USE < leg1[0],
					"the arms can carry him before the legs start (the hand walk)");
		}
		long[][] n = Andy.headOnlyPlan(AndyConfig.SPEED_NORMAL, 0L);
		long walk = n[4][1] + AndyConfig.STAGE_DELAY[1];
		helper.assertTrue(walk >= 18 * 20 && walk <= 22 * 20, "head to walking at Normal: 18-22 s (" + walk + " ticks)");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStagesFollowTheParts(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.shatter(p);
		Andy.tick(p); // plans the head-only regrowth
		AndyState s = Andy.state(p);
		long bodyStart = s.regrowStart(AndyState.BODY), bodyEnd = s.regrowEnd(AndyState.BODY);
		long arms = s.regrowStart(AndyState.RIGHT_ARM), legs = s.regrowStart(AndyState.RIGHT_LEG);
		helper.assertTrue(bodyStart > 0L, "planned at once");
		helper.assertTrue(arms == s.regrowStart(AndyState.LEFT_ARM) && arms == Andy.torsoMark(bodyStart, bodyEnd, AndyConfig.SHOULDER_SHARE),
				"both arms at the shoulders");
		helper.assertTrue(legs == s.regrowStart(AndyState.LEFT_LEG) && legs == bodyEnd, "both legs when the torso is done");
		int limb = AndyConfig.REGROW_TICKS[s.regenSpeed];
		java.util.function.LongFunction<Integer> at = t -> com.projecthero.mod.andy.AndyStages.stage(s, t);
		helper.assertTrue(at.apply(bodyStart - 1) == com.projecthero.mod.andy.AndyStages.ROLL, "nothing growing yet: a rolling head");
		helper.assertTrue(at.apply(bodyStart + 2) == com.projecthero.mod.andy.AndyStages.CRAWL, "the torso growing out of the neck: lying on it");
		helper.assertTrue(at.apply(arms + 2) == com.projecthero.mod.andy.AndyStages.CRAWL, "both arms growing: crawling on his belly");
		long armUse = arms + (long) Math.ceil(com.projecthero.mod.andy.AndyStages.ARM_USE * limb) + 1;
		helper.assertTrue(at.apply(armUse - 3) == com.projecthero.mod.andy.AndyStages.CRAWL, "still crawling until the arm muscle is in");
		helper.assertTrue(at.apply(armUse) == com.projecthero.mod.andy.AndyStages.ARM_WALK, "the arms carry him: walking on his hands");
		helper.assertTrue(at.apply(legs - 1) == com.projecthero.mod.andy.AndyStages.ARM_WALK, "while the torso finishes");
		helper.assertTrue(at.apply(legs + 2) == com.projecthero.mod.andy.AndyStages.SHUFFLE, "both legs growing: shuffling on the stumps");
		long stand = legs + (long) Math.ceil(com.projecthero.mod.andy.AndyStages.LEG_STAND * limb) + 1;
		helper.assertTrue(at.apply(stand) == com.projecthero.mod.andy.AndyStages.WOBBLE, "the legs full length: a wobbly walk");
		helper.assertTrue(at.apply(s.regrowEnd(AndyState.LEFT_LEG) - 2) == com.projecthero.mod.andy.AndyStages.WOBBLE, "until they are done");
		helper.assertTrue(at.apply(s.regrowEnd(AndyState.LEFT_LEG) + 1) == com.projecthero.mod.andy.AndyStages.NORMAL, "both legs: walking");
		// any way of losing parts gives the stage: just both legs gone (another move, a hit) is the hand walk, one leg a limp
		Andy.restore(p);
		Andy.breakPart(p, AndyState.RIGHT_LEG, AndyState.KIND_BURST);
		helper.assertTrue(Andy.stage(p) == com.projecthero.mod.andy.AndyStages.LIMP, "one leg gone: a limp");
		Andy.breakPart(p, AndyState.LEFT_LEG, AndyState.KIND_BURST);
		helper.assertTrue(Andy.stage(p) == com.projecthero.mod.andy.AndyStages.ARM_WALK, "both legs gone: on his hands");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void victorRunsOnATimerAndSwapsTheKit(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		AndyAbilities.pullCard(p);
		helper.assertTrue(Andy.victor(p), "Victor");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base - (AndyConfig.VICTOR_MELEE - AndyConfig.BLOODY_ARTS_MELEE)) < 0.01,
				"Victor hits harder");
		helper.assertTrue(AndyAbilities.VULCAN.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0]),
				"R is Dead Vulcan");
		helper.assertTrue(AndyAbilities.DEAD_ROAD.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6)[0]),
				"C is Dead Road");
		edit(p, n -> n.victorUntil = helper.getLevel().getGameTime());
		Andy.tick(p);
		helper.assertTrue(!Andy.victor(p), "the card goes back when the time is up");
		helper.assertTrue(AndyAbilities.PARTS_BULLET.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0]),
				"R is Parts Bullet again");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 0.01, "back to Bloody Arts");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void slashesHitWhatIsInFront(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Zombie z = zombie(helper, 2, 4);
		AndyAbilities.crescent(p);
		float after = z.getHealth();
		helper.assertTrue(after < 200f, "Crimson Crescent Moon hits");
		AndyAbilities.pullCard(p);
		z.invulnerableTime = 0;
		AndyAbilities.deadBlade(p);
		helper.assertTrue(z.getHealth() < after, "Dead Blade hits");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fingerAndCrimsonBulletsFly(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		AndyAbilities.partsBullet(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_FINGER).size() == 1,
				"a finger flies");
		AndyAbilities.crimsonBullet(p);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_CRIMSON).size() == 1,
				"a crimson bullet flies");
		helper.assertTrue(Andy.state(p).slowRegenUntil > helper.getLevel().getGameTime(), "and slows the regeneration");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void vortexBulletLaunchesTheArmAndHeadBulletTheHead(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		int arm = Andy.mainArm(p);
		AndyAbilities.vortexBullet(p);
		helper.assertTrue(Andy.missing(p, arm) && Andy.state(p).kind(arm) == AndyState.KIND_LAUNCHED, "the arm is gone, as the projectile");
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.bodyPart() == arm).size() == 1, "the drill flies");
		edit(p, n -> n.abilityReadyAt.clear());
		AndyAbilities.partsBullet(p);
		// v0.17.3: a one-arm move falls back to the other arm -- the finger comes from the hand he still has
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.isFinger()).size() == 1, "the other hand shoots instead");
		helper.assertTrue(Andy.shootingArm(p) == (arm == AndyState.RIGHT_ARM ? AndyState.LEFT_ARM : AndyState.RIGHT_ARM), "from the other arm");
		AndyAbilities.headBullet(p);
		helper.assertTrue(Andy.missing(p, AndyState.HEAD), "the head is gone");
		Andy.reconcile(p);
		helper.assertTrue(p.hasEffect(MobEffects.BLINDNESS), "and he is blind");
		helper.assertTrue(com.projecthero.mod.andy.AndyMod.handGone(p, InteractionHand.MAIN_HAND), "the missing hand can't be used");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void quardeadSplitsIntoThreeClones(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		AndyAbilities.quardead(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		var clones = helper.getLevel().getEntitiesOfClass(AndyCloneEntity.class, box, e -> true);
		helper.assertTrue(clones.size() == 3, "three copies: " + clones.size());
		helper.assertTrue(clones.stream().map(AndyCloneEntity::tint).distinct().count() == 3, "skin, blood and bones");
		helper.assertTrue(!com.projecthero.mod.combat.HeroTargets.canHarm(p, clones.get(0)), "his own moves pass through them");
		clones.forEach(e -> e.discard());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deadLineIsABeamThatHurts(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Zombie z = zombie(helper, 2, 5);
		p.setXRot(8f);
		AndyAbilities.startDeadLine(p);
		helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_DEAD_LINE), "the jet is on");
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(z.getHealth() < 200f, "Dead Line hurts what it touches: " + z.getHealth());
			AndyAbilities.stopDeadLine(p);
			helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_DEAD_LINE), "and stops on release");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void propulsionAndWavesRun(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Zombie z = zombie(helper, 2, 5);
		AndyAbilities.startPropulsion(p);
		helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_PROPELLING), "flying");
		Andy.tick(p);
		helper.assertTrue(Math.abs(p.getDeltaMovement().length() - AndyConfig.PROPULSION_SPEED) < 1.0e-6, "along the look at 25 b/s");
		AndyAbilities.stopPropulsion(p);
		helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_PROPELLING), "stops on release");
		p.setDeltaMovement(Vec3.ZERO);
		p.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 1.5)));
		AndyAbilities.greatCrescent(p);
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.getHealth() < 200f, "the blood waves reach the zombie");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void victorVulcanAndDivisionFire(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.setXRot(-85f); // up, out of the walled test area
		AndyAbilities.pullCard(p);
		AndyAbilities.vulcan(p);
		AndyAbilities.division(p);
		AABB box = new AABB(p.blockPosition()).inflate(6);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.style() == AndyPartEntity.STYLE_DIVISION).size() == 3,
				"three Division fingers");
		AndyAbilities.divisionClone(p, p.position());
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AndyCloneEntity.class, box, e -> true).isEmpty(), "a finger grows into a copy");
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(AndyAbilities.vulcanLeft(p) > 0 && AndyAbilities.vulcanLeft(p) <= AndyConfig.VULCAN_SHOTS - 2, "Dead Vulcan keeps firing: " + AndyAbilities.vulcanLeft(p));
			AndyAbilities.deadRoad(p);
			helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_DEAD_ROAD), "Dead Road fires");
			helper.getLevel().getEntitiesOfClass(AndyCloneEntity.class, box, e -> true).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X: Leg Launch, Head Launch, Shift + hold flight (v0.17.3)

	private static final com.projecthero.mod.hero.AbilitySlot X = com.projecthero.mod.hero.AbilitySlot.SLOT_3;

	private static java.util.List<AndyPartEntity> leftBehind(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, new AABB(p.blockPosition()).inflate(8), e -> e.isLeftBehind() && e.ownerIs(p));
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tapXLaunchesTheBodyOffTheLegs(GameTestHelper helper) {
		ServerPlayer p = andy(helper); // facing +z
		helper.onEachTick(() -> Andy.tick(p));
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, true);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(!Andy.missing(p, AndyState.RIGHT_LEG), "nothing yet while X is still down");
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, false);
			AndyState s = Andy.state(p);
			helper.assertTrue(s.broken(AndyState.RIGHT_LEG) && s.broken(AndyState.LEFT_LEG), "a tap breaks both legs");
			helper.assertTrue(s.kind(AndyState.RIGHT_LEG) == AndyState.KIND_LAUNCHED, "left behind, not burst");
			helper.assertTrue(!s.broken(AndyState.HEAD) && !s.broken(AndyState.BODY), "the rest of him goes");
			Vec3 v = p.getDeltaMovement();
			helper.assertTrue(v.z > 1.0 && v.y > 0.0, "thrown forward along the look with a small arc: " + v);
			helper.assertTrue(com.projecthero.mod.andy.AndyLaunch.flying(p), "the launch runs");
			helper.assertTrue(com.projecthero.mod.andy.AndyLaunch.fallImmune(p), "no fall damage from it");
			helper.assertTrue(leftBehind(helper, p).size() == 2, "the two legs stay where he stood: " + leftBehind(helper, p).size());
			helper.assertTrue(Andy.cooldownRemaining(p, com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH) > 0, "on cooldown");
			leftBehind(helper, p).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void holdXLaunchesTheHeadAndLeavesTheBody(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.onEachTick(() -> Andy.tick(p));
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, true);
		helper.runAfterDelay(AndyConfig.LAUNCH_CHARGE_SHOW + 3, () -> {
			helper.assertTrue(Andy.state(p).animId == AndyState.ANIM_HEAD_CHARGE, "the wind-up shows while X is held");
			helper.assertTrue(!Andy.state(p).anyBroken(), "nothing breaks during the wind-up");
		});
		helper.runAfterDelay(AndyConfig.HEAD_LAUNCH_HOLD + 3, () -> {
			// still held: it fired on its own at the full hold
			AndyState s = Andy.state(p);
			helper.assertTrue(!s.broken(AndyState.HEAD), "the player is the head");
			for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
				helper.assertTrue(s.broken(i), "part " + i + " is left behind");
			}
			helper.assertTrue(leftBehind(helper, p).size() == 5, "the body, arms and legs stay where he stood: " + leftBehind(helper, p).size());
			helper.assertTrue(Andy.cooldownRemaining(p, com.projecthero.mod.andy.AndyLaunch.HEAD_LAUNCH) > 0, "on cooldown");
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, false);
			helper.assertTrue(Andy.cooldownRemaining(p, com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH) == 0, "letting go after it does not also Leg Launch");
			leftBehind(helper, p).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void headLaunchGivesTheHeadFlight(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		com.projecthero.mod.andy.AndyLaunch.headLaunch(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.z > 1.0 && v.y > 0.4, "the head shoots off forward and up: " + v);
		helper.assertTrue(com.projecthero.mod.andy.AndyLaunch.flying(p), "the launch runs");
		leftBehind(helper, p).forEach(e -> e.discard());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftHoldXFliesAndGlideIsGone(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(java.util.Arrays.equals(com.projecthero.mod.andy.AndyAbilityManager.idsOf(p, X),
				new String[] { com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH, AndyAbilities.PROPULSION }), "X: Leg Launch, Shift: the flight");
		boolean glide = true;
		try {
			AndyAbilities.class.getField("GLIDE");
		} catch (NoSuchFieldException e) {
			glide = false;
		}
		helper.assertTrue(!glide, "Repair Glide is gone");
		helper.assertTrue(com.projecthero.mod.andy.AndyAbilityManager.maxCooldown(com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH) == AndyConfig.LEG_LAUNCH_COOLDOWN
				&& AndyConfig.LEG_LAUNCH_COOLDOWN == 60, "Leg Launch 3s cooldown");
		helper.assertTrue(com.projecthero.mod.andy.AndyAbilityManager.maxCooldown(com.projecthero.mod.andy.AndyLaunch.HEAD_LAUNCH) == AndyConfig.HEAD_LAUNCH_COOLDOWN
				&& AndyConfig.HEAD_LAUNCH_COOLDOWN == 200, "Head Launch 10s cooldown");
		helper.assertTrue(AndyConfig.HEAD_LAUNCH_HOLD == 20, "the hold is a second");
		p.setShiftKeyDown(true);
		helper.onEachTick(() -> Andy.tick(p));
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, true);
		helper.runAfterDelay(1, () -> helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_PROPELLING), "a Shift tap does not fly yet"));
		helper.runAfterDelay(AndyConfig.FLIGHT_HOLD_TICKS + 3, () -> {
			helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_PROPELLING), "Shift + hold X flies");
			helper.assertTrue(!Andy.state(p).anyBroken(), "and launches nothing");
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, X, false);
			helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_PROPELLING), "letting go stops it");
			helper.assertTrue(Andy.cooldownRemaining(p, com.projecthero.mod.andy.AndyLaunch.LEG_LAUNCH) == 0, "no Leg Launch on release");
			helper.succeed();
		});
	}
}

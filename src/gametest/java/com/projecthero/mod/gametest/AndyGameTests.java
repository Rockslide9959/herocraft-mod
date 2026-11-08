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

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aBrokenPartRegrowsAfterTheDelayForHunger(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_NORMAL);
		p.getFoodData().setFoodLevel(20);
		Andy.breakPart(p, AndyState.LEFT_LEG, AndyState.KIND_BURST);
		helper.assertTrue(Andy.missing(p, AndyState.LEFT_LEG), "broken");
		Andy.reconcile(p);
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "one leg gone: slowed");
		long start = helper.getLevel().getGameTime();
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(AndyConfig.REGROW_DELAY[1] - 5, () -> helper.assertTrue(Andy.state(p).regrowStart(AndyState.LEFT_LEG) == 0L,
				"still waiting before the delay"));
		helper.runAfterDelay(AndyConfig.REGROW_DELAY[1] + 3, () -> {
			helper.assertTrue(Andy.state(p).regrowStart(AndyState.LEFT_LEG) > 0L, "regrowing after the delay");
			helper.assertTrue(p.getFoodData().getFoodLevel() == 20 - AndyConfig.REGROW_FOOD[1], "it cost hunger: " + p.getFoodData().getFoodLevel());
		});
		helper.runAfterDelay(AndyConfig.REGROW_DELAY[1] + AndyConfig.REGROW_TICKS[1] + 4, () -> {
			helper.assertTrue(!Andy.missing(p, AndyState.LEFT_LEG), "whole again after " + (helper.getLevel().getGameTime() - start) + " ticks");
			helper.assertTrue(Andy.state(p).hp(AndyState.LEFT_LEG) == AndyConfig.PART_HP[AndyState.LEFT_LEG], "full HP");
			helper.assertTrue(!p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "no longer slowed");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBodyRegrowsBeforeTheRest(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setSpeed(p, AndyConfig.SPEED_BURST);
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
		helper.assertTrue(Andy.reforming(p), "re-forming");
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
	public void regenSpeedCyclesSlowNormalBurst(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "starts on Normal");
		Andy.cycleSpeed(p);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "then Burst");
		Andy.cycleSpeed(p);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_SLOW, "then Slow");
		Andy.cycleSpeed(p);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "and round");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void tapVCyclesTheSpeedAndHoldVCyclesTheStyle(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		helper.assertTrue(Andy.regenStyle(p) == AndyState.STYLE_BONE, "starts on Bone and flesh (the default)");
		// a quick tap: the speed, on release, and not the style
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "nothing on the press itself");
		com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "a tap cycles the speed");
		helper.assertTrue(Andy.regenStyle(p) == AndyState.STYLE_BONE, "a tap leaves the style");
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(2, () -> com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true));
		helper.runAfterDelay(2 + AndyConfig.STYLE_HOLD_TICKS - 4, () -> helper.assertTrue(Andy.regenStyle(p) == AndyState.STYLE_BONE,
				"not yet before the hold time"));
		helper.runAfterDelay(2 + AndyConfig.STYLE_HOLD_TICKS + 3, () -> {
			helper.assertTrue(Andy.regenStyle(p) == AndyState.STYLE_STREAMING, "held V cycles to Streaming: " + Andy.regenStyle(p));
		});
		helper.runAfterDelay(2 + AndyConfig.STYLE_HOLD_TICKS + 12, () -> {
			helper.assertTrue(Andy.regenStyle(p) == AndyState.STYLE_STREAMING, "once per hold, however long");
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
			helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_BURST, "letting go after a hold does not touch the speed");
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, true);
		});
		helper.runAfterDelay(2 + AndyConfig.STYLE_HOLD_TICKS * 2 + 18, () -> {
			com.projecthero.mod.andy.AndyAbilityManager.handle(p, v, false);
			helper.assertTrue(Andy.regenStyle(p) == AndyState.STYLE_BUILD, "then Build in place");
			Andy.cycleStyle(p);
			helper.assertTrue(Andy.regenStyle(p) == AndyState.STYLE_BONE, "and round to Bone and flesh");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRegrowthStyleIsSavedAndKeptThroughDeath(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setStyle(p, AndyState.STYLE_BUILD);
		Andy.setSpeed(p, AndyConfig.SPEED_SLOW);
		AndyState s = Andy.state(p);
		net.minecraft.nbt.Tag tag = AndyState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, s).getOrThrow();
		AndyState back = AndyState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.regenStyle == AndyState.STYLE_BUILD && back.regenSpeed == AndyConfig.SPEED_SLOW, "saved and loaded: " + tag);
		AndyState old = AndyState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, new net.minecraft.nbt.CompoundTag()).getOrThrow();
		helper.assertTrue(old.regenStyle == AndyState.STYLE_BONE, "a save without the field loads as Bone and flesh, the default");
		helper.assertTrue(s.copy().regenStyle == AndyState.STYLE_BUILD, "copies keep it");
		Andy.breakPart(p, AndyState.RIGHT_ARM, AndyState.KIND_BURST);
		Andy.clearTransient(p); // a real death, a new world, logging out
		helper.assertTrue(!Andy.missing(p, AndyState.RIGHT_ARM), "made whole");
		helper.assertTrue(Andy.regenStyle(p) == AndyState.STYLE_BUILD, "the style stays");
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_SLOW, "and the speed");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void everyStyleRegrowsInTheSameTime(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		// one style and speed a tick, once the game clock is past the longest wait (each is back-dated to start at once)
		int start = AndyConfig.REGROW_DELAY[AndyConfig.SPEED_SLOW] + 10;
		for (int k = 0; k < 3 * AndyState.STYLES; k++) {
			int speed = k / AndyState.STYLES, style = k % AndyState.STYLES;
			helper.runAfterDelay(start + k, () -> {
				Andy.restore(p);
				p.getFoodData().setFoodLevel(20);
				Andy.setSpeed(p, speed);
				Andy.setStyle(p, style);
				Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
				long now = helper.getLevel().getGameTime();
				edit(p, n -> n.setTimes(AndyState.LEFT_ARM, now - AndyConfig.REGROW_DELAY[speed], 0L, 0L));
				Andy.tick(p);
				AndyState s = Andy.state(p);
				long len = s.regrowEnd(AndyState.LEFT_ARM) - s.regrowStart(AndyState.LEFT_ARM);
				helper.assertTrue(s.regrowStart(AndyState.LEFT_ARM) > 0L, "style " + style + " speed " + speed + " starts regrowing");
				helper.assertTrue(len == AndyConfig.REGROW_TICKS[speed], "style " + style + " speed " + speed + " takes " + len + " ticks, not "
						+ AndyConfig.REGROW_TICKS[speed]);
				helper.assertTrue(s.regenStyle == style, "and keeps its style");
			});
		}
		helper.runAfterDelay(start + 3 * AndyState.STYLES + 1, helper::succeed);
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
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, box, e -> e.isFinger()).isEmpty(), "no fingers from a missing hand");
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
	public void glidePropulsionAndWavesRun(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Zombie z = zombie(helper, 2, 5);
		AndyAbilities.startPropulsion(p);
		helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_PROPELLING), "flying");
		Andy.tick(p);
		helper.assertTrue(p.getDeltaMovement().lengthSqr() > 0.5, "pushed along the look");
		AndyAbilities.stopPropulsion(p);
		helper.assertTrue(!Andy.state(p).flag(AndyState.FLAG_PROPELLING), "stops on release");
		p.setDeltaMovement(Vec3.ZERO);
		p.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 1.5)));
		AndyAbilities.greatCrescent(p);
		helper.onEachTick(() -> Andy.tick(p));
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.getHealth() < 200f, "the blood waves reach the zombie");
			AndyAbilities.glide(p);
			helper.assertTrue(Andy.state(p).flag(AndyState.FLAG_GLIDING), "Repair Glide runs");
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
}

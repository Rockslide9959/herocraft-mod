package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.MiraculousPhrases;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.item.MiraculousBoxItem;
import com.projecthero.mod.miraculous.item.MiraculousItems;
import com.projecthero.mod.miraculous.multimouse.Multimouse;
import com.projecthero.mod.miraculous.multimouse.MultimouseConfig;
import com.projecthero.mod.miraculous.multimouse.MultimouseMoves;
import com.projecthero.mod.miraculous.multimouse.Multitude;
import com.projecthero.mod.miraculous.multimouse.entity.MiniMouseEntity;
import com.projecthero.mod.punisher.PunisherControl;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Multimouse (Mullo): one test per move -- the server effect and its number -- plus Multitude (ten copies, her
 * size, half damage, the recombining shockwave, the clean-up), the two passives, the phrases, the Cheese Wedge, the
 * wiring (key, box, jewel, guide chapter). Mock players are not ticked by the server, so tests that run over time drive
 * the Miraculous tick themselves ({@link #pump}).
 */
public class MultimouseGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** Multimouse facing +Z, the transformation finished, every move ready. */
	private static ServerPlayer multimouse(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.MULLO), "Mullo is granted");
		Miraculous.transform(p);
		long now = helper.getLevel().getGameTime();
		Miraculous.edit(p, n -> {
			n.formChangeAt = now - 200L;
			n.animId = MiraculousState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
		helper.assertTrue(Miraculous.canAct(p), "the transformation has finished");
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Miraculous.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health, boolean ai) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(!ai);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static float taken(LivingEntity e, double max) {
		return (float) max - e.getHealth();
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-3;
	}

	// ---------------------------------------------------------------- the numbers

	@GameTest(template = EMPTY_STRUCTURE)
	public void theApprovedNumbers(GameTestHelper helper) {
		helper.assertTrue(MultimouseConfig.LASH_DAMAGE == 8f && MultimouseConfig.LASH_REACH == 5.0 && MultimouseConfig.LASH_HITS == 3, "Rope Lash 8 x3, 5 blocks");
		helper.assertTrue(MultimouseConfig.LASSO_RANGE == 16.0 && MultimouseConfig.LASSO_DAMAGE == 6f && MultimouseConfig.LASSO_STUN_TICKS == 30
				&& MultimouseConfig.LASSO_COOLDOWN == 160, "Lasso Yank 16 blocks, 6 + 1.5 s stun, 8 s");
		helper.assertTrue(MultimouseConfig.TWIRL_TICKS == 80 && MultimouseConfig.TWIRL_RADIUS == 3.5 && MultimouseConfig.TWIRL_DAMAGE == 4f
				&& MultimouseConfig.TWIRL_EVERY == 10 && MultimouseConfig.TWIRL_COOLDOWN == 200, "Rope Twirl 4 s, 3.5, 4 every 0.5 s, 10 s");
		helper.assertTrue(MultimouseConfig.TRIP_RADIUS == 6.0 && MultimouseConfig.TRIP_DAMAGE == 6f && MultimouseConfig.TRIP_TICKS == 40
				&& MultimouseConfig.TRIP_COOLDOWN == 160, "Skipping Trip 6, 6 damage, 2 s, 8 s");
		helper.assertTrue(MultimouseConfig.SWING_RANGE == 30.0 && MultimouseConfig.SWING_COOLDOWN == 60, "Rope Swing 30 blocks, 3 s");
		helper.assertTrue(MultimouseConfig.SCURRY_TICKS == 400 && MultimouseConfig.SCURRY_SPEED == 0.4, "Scurry 20 s, 40% faster");
		helper.assertTrue(MultimouseConfig.SWARM_COUNT == 3 && MultimouseConfig.SWARM_TICKS == 120 && MultimouseConfig.SWARM_BITE == 2f
				&& MultimouseConfig.SWARM_COOLDOWN == 280, "Mouse Swarm 3, 6 s, 2 a bite, 14 s");
		helper.assertTrue(MultimouseConfig.MULTITUDE_COPIES == 10 && MultimouseConfig.MULTITUDE_TICKS == 1200 && MultimouseConfig.MINI_BITE == 3f
				&& MultimouseConfig.MINI_HEALTH == 6.0 && MultimouseConfig.MINI_DAMAGE_FACTOR == 0.5f && MultimouseConfig.RECOMBINE_RADIUS == 6.0
				&& MultimouseConfig.RECOMBINE_DAMAGE_PER_COPY == 2f, "Multitude: 10 copies, 60 s, 3 a bite, 6 HP, half damage, 6-block shockwave, 2 a copy");
		helper.assertTrue(MultimouseConfig.HEARING_RANGE == 32.0 && MultimouseConfig.GNAW_DEPTH == 3 && MultimouseConfig.GNAW_COOLDOWN == 80, "Hearing 32, Gnaw 3 deep, 4 s");
		helper.assertTrue(MultimouseConfig.BIND_RANGE == 14.0 && MultimouseConfig.BIND_TICKS == 80 && MultimouseConfig.BIND_COOLDOWN == 240, "Rope Bind 14, 4 s, 12 s");
		helper.assertTrue(MultimouseConfig.TRAP_MAX == 3 && MultimouseConfig.TRAP_SNARE_TICKS == 100 && MultimouseConfig.TRAP_DAMAGE == 8f, "Mouse Trap 3, 5 s, 8");
		helper.assertTrue(MultimouseConfig.FALL_DAMAGE_FACTOR == 0.25f && MultimouseConfig.QUIET_RANGE == 6.0, "75% less fall damage, quiet past 6 blocks");
		helper.assertTrue(MiraculousConfig.SAFE_LANDING_MOVES.contains(MultimouseConfig.SWING), "the swing's landing is free");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void ropeLashIsThreePressesOfEight(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100, false);
		pump(helper, p);
		helper.assertTrue(MultimouseMoves.lash(p) == 1 && Miraculous.state(p).animId == Multimouse.ANIM_LASH, "press 1");
		helper.assertTrue(Miraculous.ready(p, MultimouseConfig.LASH), "no cooldown mid-combo");
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(Math.abs(taken(z, 100) - MultimouseConfig.LASH_DAMAGE) < EPS, "one swing, 8 (3 blocks off: inside the 5-block reach): took " + taken(z, 100));
			helper.assertTrue(MultimouseMoves.lash(p) == 2, "press 2");
			helper.runAfterDelay(6, () -> {
				helper.assertTrue(MultimouseMoves.lash(p) == 3, "press 3");
				helper.assertFalse(Miraculous.ready(p, MultimouseConfig.LASH), "the cooldown starts with the third");
				helper.runAfterDelay(6, () -> {
					helper.assertTrue(Math.abs(taken(z, 100) - 3 * MultimouseConfig.LASH_DAMAGE) < EPS, "three swings, 24: took " + taken(z, 100));
					helper.succeed();
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lassoYankHurtsStunsAndPulls(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 7.5, 100, false);
		helper.assertTrue(MultimouseMoves.lasso(p) == z, "lassoed");
		helper.assertTrue(Math.abs(taken(z, 100) - MultimouseConfig.LASSO_DAMAGE) < EPS, "6 damage, took " + taken(z, 100));
		helper.assertTrue(PunisherControl.isStunned(z), "stunned");
		helper.assertTrue(z.getDeltaMovement().z < -0.5, "yanked toward her: " + z.getDeltaMovement());
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE)
	public void ropeTwirlPulsesFourRoundHer(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 3.5);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 1.5, 100, false);
		Zombie far = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 8.5, 100, false);
		MultimouseMoves.twirl(p);
		helper.assertTrue(Multimouse.twirling(p), "spinning");
		helper.assertTrue(Math.abs(taken(behind, 100) - MultimouseConfig.TWIRL_DAMAGE) < EPS, "4 to the one behind her too, took " + taken(behind, 100));
		helper.assertTrue(far.getHealth() == 100f, "not the one 5 blocks off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void skippingTripHurtsAndTrips(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 1.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 1.0, 2.0, 5.5, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.0, 2.0, 4.5, 100, false);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.0, 400, false);
		helper.assertTrue(MultimouseMoves.trip(p) == 3, "three in the arc");
		helper.assertTrue(Math.abs(taken(a, 100) - MultimouseConfig.TRIP_DAMAGE) < EPS && Math.abs(taken(b, 100) - MultimouseConfig.TRIP_DAMAGE) < EPS, "6 each");
		helper.assertTrue(PunisherControl.isStunned(a) && PunisherControl.isStunned(b), "tripped: can't attack");
		helper.assertTrue(!PunisherControl.isStunned(boss) && boss.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a boss is only slowed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void ropeSwingHooksTheBlockAhead(GameTestHelper helper) {
		floor(helper);
		for (int y = 2; y <= 5; y++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, y, 7)), Blocks.STONE.defaultBlockState(), 3);
		}
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 0.5);
		Vec3 hook = MultimouseMoves.ropeSwing(p);
		helper.assertTrue(hook != null && Multimouse.running(p, Multimouse.V_SWING), "hooked: " + hook);
		helper.assertTrue(p.getDeltaMovement().z > 0.2, "pulled toward the hook: " + p.getDeltaMovement());
		helper.assertTrue(Miraculous.hasSafeLanding(p), "the landing is free");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void scurryShrinksAndGrowsBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 3.5);
		double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(MultimouseMoves.scurry(p) && Multimouse.scurrying(p), "small");
		helper.assertTrue(near(p.getAttributeValue(Attributes.SCALE), MultimouseConfig.SCURRY_SCALE), "a third of her size: " + p.getAttributeValue(Attributes.SCALE));
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > speed * 1.35, "40% faster");
		helper.assertFalse(MultimouseMoves.scurry(p), "pressed again: grows back");
		helper.assertTrue(near(p.getAttributeValue(Attributes.SCALE), 1.0) && !Multimouse.scurrying(p), "full size again");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE)
	public void mouseSwarmSendsThreeBitingTwo(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100, false);
		List<MiniMouseEntity> swarm = MultimouseMoves.swarm(p);
		helper.assertTrue(swarm.size() == 3, "three copies, got " + swarm.size());
		helper.assertTrue(swarm.get(0).biteDamage() == MultimouseConfig.SWARM_BITE && swarm.get(0).kind() == MiniMouseEntity.SWARM, "2 a bite");
		helper.assertTrue(MultimouseMoves.bite(p, swarm.get(0), z, swarm.get(0).biteDamage()), "a bite lands");
		helper.assertTrue(Math.abs(taken(z, 100) - MultimouseConfig.SWARM_BITE) < EPS, "2 damage, took " + taken(z, 100));
		helper.assertTrue(z.getLastHurtByMob() == p, "the bite is hers (a boss's threat table counts it for her)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void multitudeMakesTenCopiesAndShrinksHer(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.0, 100, false);
		List<MiniMouseEntity> copies = Multitude.start(p);
		helper.assertTrue(copies.size() == 10 && Multitude.copies(p).size() == 10, "ten copies (her plus 10), got " + copies.size());
		helper.assertTrue(copies.stream().allMatch(m -> m.biteDamage() == MultimouseConfig.MINI_BITE && m.getMaxHealth() == 6f
				&& near(m.getAttributeValue(Attributes.SCALE), MultimouseConfig.MINI_SCALE)), "3 a bite, 6 health, a quarter size");
		helper.assertTrue(near(p.getAttributeValue(Attributes.SCALE), MultimouseConfig.MULTITUDE_SCALE), "she is a quarter of her size: " + p.getAttributeValue(Attributes.SCALE));
		helper.assertTrue(Multimouse.copiesLeft(p) == 10 && Multimouse.multitude(p), "the HUD count");
		helper.assertTrue(Miraculous.countdownLeft(p) > 0, "it starts the countdown");
		helper.assertTrue(MultimouseMoves.hit(p, z, p.position(), 8f, 0.0, 0.0), "her own hit");
		helper.assertTrue(Math.abs(taken(z, 100) - 4f) < EPS, "half damage while split: 8 -> 4, took " + taken(z, 100));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void recombiningCleansUpRestoresHerAndShockwaves(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 7.5, 100, false);
		List<MiniMouseEntity> copies = Multitude.start(p);
		// Shift+Z again: the kit routes it to recombine, never on the cooldown
		MiraculousKits.of(Kwami.MULLO).handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4, true, true);
		helper.assertTrue(Multimouse.val(p, Multimouse.V_RECOMBINE) != 0L, "recombining");
		pump(helper, p);
		helper.runAfterDelay(MultimouseConfig.RECOMBINE_TICKS + 4, () -> {
			helper.assertFalse(Multimouse.multitude(p), "whole again");
			helper.assertTrue(copies.stream().allMatch(MiniMouseEntity::isRemoved) && Multitude.copies(p).isEmpty(), "every copy is gone");
			helper.assertTrue(near(p.getAttributeValue(Attributes.SCALE), 1.0), "full size again: " + p.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(Math.abs(taken(z, 100) - 10 * MultimouseConfig.RECOMBINE_DAMAGE_PER_COPY) < EPS,
					"the shockwave: 2 for each of the 10 copies back, took " + taken(z, 100));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void detransformingDropsTheCopiesAndRestoresHerSize(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 3.5);
		List<MiniMouseEntity> copies = Multitude.start(p);
		Miraculous.detransform(p, false);
		helper.assertTrue(copies.stream().allMatch(MiniMouseEntity::isRemoved), "the copies pop");
		helper.assertTrue(near(p.getAttributeValue(Attributes.SCALE), 1.0) && Miraculous.hungry(p), "full size, and Mullo is hungry");
		ServerPlayer q = multimouse(helper, 1.5, 2.0, 1.5);
		MultimouseMoves.scurry(q);
		Miraculous.clearTransient(q); // death / logout
		helper.assertTrue(near(q.getAttributeValue(Attributes.SCALE), 1.0), "death / logout restores the size too");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE)
	public void mouseHearingToggles(GameTestHelper helper) {
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(MultimouseMoves.hearing(p) && Multimouse.hearing(p), "on");
		helper.assertFalse(MultimouseMoves.hearing(p) || Multimouse.hearing(p), "off");
		helper.succeed();
	}

	/** v0.17.1 (user): Gnaw chews hard blocks too, stone included -- never bedrock. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void gnawChewsStoneButNotBedrock(GameTestHelper helper) {
		floor(helper);
		var level = helper.getLevel();
		BlockPos low1 = helper.absolutePos(new BlockPos(3, 2, 2)), high1 = helper.absolutePos(new BlockPos(3, 3, 2));
		BlockPos bedrock = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos behind = helper.absolutePos(new BlockPos(3, 2, 4)), high3 = helper.absolutePos(new BlockPos(3, 3, 4));
		level.setBlock(low1, Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(high1, Blocks.DEEPSLATE.defaultBlockState(), 3);
		level.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 3);
		level.setBlock(behind, Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(high3, Blocks.OBSIDIAN.defaultBlockState(), 3);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 1.5);
		int n = MultimouseMoves.gnaw(p);
		helper.assertTrue(level.getBlockState(low1).isAir() && level.getBlockState(high1).isAir(), "stone and deepslate chewed");
		helper.assertTrue(level.getBlockState(bedrock).is(Blocks.BEDROCK), "bedrock never");
		helper.assertTrue(level.getBlockState(behind).is(Blocks.STONE), "the low hole stops at the bedrock");
		helper.assertTrue(level.getBlockState(high3).isAir(), "the high hole goes on 3 deep (obsidian too)");
		helper.assertTrue(n == 3, "three chewed, got " + n);
		level.setBlock(low1, Blocks.BARRIER.defaultBlockState(), 3);
		helper.assertFalse(MultimouseMoves.chewable(level, p, low1), "a barrier never");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void ropeBindTiesUpButOnlySlowsABoss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100, false);
		helper.assertTrue(MultimouseMoves.bind(p) == z, "tied up");
		helper.assertTrue(MultimouseMoves.bound(p, z) && PunisherControl.isStunned(z), "rooted and unable to attack");
		ServerPlayer q = multimouse(helper, 6.5, 2.0, 0.5);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 6.5, 400, false);
		helper.assertTrue(MultimouseMoves.bind(q) == boss, "the boss is roped");
		helper.assertTrue(!PunisherControl.isStunned(boss) && boss.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a boss is only slowed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void mouseTrapSnaresTheFirstMob(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 3.5);
		Vec3 at = MultimouseMoves.trap(p);
		helper.assertTrue(MultimouseMoves.traps(p) == 1, "one snare set");
		MultimouseMoves.trap(p);
		MultimouseMoves.trap(p);
		MultimouseMoves.trap(p);
		helper.assertTrue(MultimouseMoves.traps(p) == MultimouseConfig.TRAP_MAX, "never more than three");
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.6, 100, false);
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(Math.abs(taken(z, 100) - MultimouseConfig.TRAP_DAMAGE) < EPS, "8 damage, took " + taken(z, 100) + " (snare at " + at + ")");
			helper.assertTrue(MultimouseMoves.snared(p, z) && PunisherControl.isStunned(z), "snared");
			helper.assertTrue(MultimouseMoves.traps(p) == MultimouseConfig.TRAP_MAX - 1, "the snare is used up");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- passives

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightOnHerFeet(GameTestHelper helper) {
		ServerPlayer p = multimouse(helper, 3.5, 2.0, 3.5);
		var kit = MiraculousKits.of(Kwami.MULLO);
		helper.assertTrue(Math.abs(kit.modifyFallDamage(p, 20f) - 5f) < 0.01f, "a quarter of a fall's damage");
		p.setHealth(4f);
		helper.assertTrue(kit.modifyFallDamage(p, 60f) < 4f, "a fall never kills her");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Kwami, the phrases, the wiring

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void phrasesTransformAndTheCheeseWedgeFeedsMullo(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(PowerGrants.grantHero(p, "multimouse").ok() && Miraculous.holds(p, Kwami.MULLO) && HeroTiers.holdsHero(p, "multimouse"),
				"the command grants Multimouse");
		helper.assertTrue("Multimouse".equals(MiraculousPhrases.heroName(Kwami.MULLO)), "the hero's name");
		helper.assertTrue(MiraculousPhrases.match(Kwami.MULLO, "mullo multiply") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.MULLO, "Multiply!") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.MULLO, "DIVIDE") == MiraculousPhrases.Spoken.DETRANSFORM
				&& MiraculousPhrases.match(Kwami.MULLO, "Mullo, divide!") == MiraculousPhrases.Spoken.DETRANSFORM, "the phrase table");
		helper.assertTrue(Miraculous.onChat(p, "Mullo, multiply!") && Miraculous.transformedAs(p, Kwami.MULLO), "Multimouse");
		Miraculous.edit(p, n -> n.formChangeAt -= 100L);
		helper.assertTrue(Miraculous.onChat(p, "Divide!") && !Miraculous.transformed(p) && Miraculous.hungry(p), "back, and Mullo is hungry");
		helper.assertTrue(Miraculous.foodOf(Kwami.MULLO) == MiraculousItems.CHEESE_WEDGE, "the Cheese Wedge");
		ItemStack cheese = new ItemStack(MiraculousItems.CHEESE_WEDGE, 4);
		helper.assertTrue(cheese.has(DataComponents.FOOD), "players can eat it too");
		helper.assertTrue(Miraculous.wantsFood(p, cheese), "eating a wedge while she is hungry feeds her instead");
		helper.assertTrue(Miraculous.feed(p, cheese) && cheese.getCount() == 3, "she eats one");
		helper.assertTrue(Miraculous.finishMeal(p) && !Miraculous.hungry(p), "fed");
		var recipe = helper.getLevel().getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("cheese_wedge"));
		helper.assertTrue(recipe.isPresent(), "the wedge is craftable");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boxJewelAndGuideChapter(GameTestHelper helper) {
		helper.assertTrue(MiraculousBoxItem.BOX_KWAMIS.contains(Kwami.MULLO), "the Mouse Pendant is in the box's pool");
		helper.assertTrue(MiraculousItems.jewelOf(Kwami.MULLO) == MiraculousItems.MOUSE_PENDANT && "mouse_pendant".equals(Kwami.MULLO.jewel)
				&& "multimouse".equals(Kwami.MULLO.heroKey), "jewelOf, the key");
		ServerPlayer r = mock(helper, 3.5, 2.0, 3.5);
		r.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(MiraculousItems.MOUSE_PENDANT));
		MiraculousItems.MOUSE_PENDANT.use(helper.getLevel(), r, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(Miraculous.holds(r, Kwami.MULLO) && r.getMainHandItem().isEmpty(), "the Pendant grants Mullo and is used up");
		var chapters = HeroPackGuide.chapters();
		var ch = HeroPackGuide.miraculousChapter(Kwami.MULLO);
		helper.assertTrue(ch.title().getContents() instanceof TranslatableContents t && t.getKey().equals("projecthero.guide.multimouse"), "her own chapter");
		helper.assertTrue(chapters.indexOf(ch) == 60, "after the Speed Force Speedster, at " + chapters.indexOf(ch));
		StringBuilder all = new StringBuilder();
		for (var line : ch.lines()) {
			all.append(line.getString()).append('\n');
		}
		helper.assertTrue(all.indexOf("Multitude") >= 0 && all.indexOf("Mullo, multiply!") >= 0 && all.indexOf("Cheese Wedge") >= 0
				&& all.indexOf("projecthero.") < 0, "the Multitude section, the phrase, the food, no untranslated lines");
		helper.succeed();
	}
}

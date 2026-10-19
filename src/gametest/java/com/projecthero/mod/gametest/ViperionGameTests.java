package com.projecthero.mod.gametest;

import java.util.UUID;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.MiraculousPhrases;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.item.MiraculousBoxItem;
import com.projecthero.mod.miraculous.item.MiraculousItems;
import com.projecthero.mod.miraculous.viperion.SecondChance;
import com.projecthero.mod.miraculous.viperion.Viperion;
import com.projecthero.mod.miraculous.viperion.ViperionConfig;
import com.projecthero.mod.miraculous.viperion.ViperionMoves;
import com.projecthero.mod.punisher.PunisherControl;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Viperion (Sass): one test per move -- the server effect and its number -- plus Second Chance (the checkpoint,
 * manual and automatic rewinds of him, a mob, a dead mob, new mobs and blocks; three rewinds then spent; the 90 s
 * window closing), poison immunity, the phrases, eggs, the wiring (key, box, jewel, guide chapter). Mock players are
 * not ticked by the server, so tests that run over time drive the Miraculous tick themselves ({@link #pump}).
 */
public class ViperionGameTests implements FabricGameTest {
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

	/** Viperion facing +Z, the transformation finished, every move ready. */
	private static ServerPlayer viperion(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.SASS), "Sass is granted");
		Miraculous.transform(p);
		ready(helper, p);
		helper.assertTrue(Miraculous.canAct(p), "the transformation has finished");
		return p;
	}

	private static void ready(GameTestHelper helper, ServerPlayer p) {
		long now = helper.getLevel().getGameTime();
		Miraculous.edit(p, n -> {
			n.formChangeAt = now - 200L;
			n.animId = MiraculousState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
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

	// ---------------------------------------------------------------- R

	/** v0.16.7 (user): three presses, three swings, as Cat Noir's Baton Strike (his numbers); the cooldown only after the third. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void lyreStrikeIsThreePressesLikeCatNoir(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.5, 100, false);
		pump(helper, p);
		helper.assertTrue(ViperionConfig.STRIKE_DAMAGE == com.projecthero.mod.miraculous.catnoir.CatNoirConfig.STRIKE_DAMAGE
				&& ViperionConfig.STRIKE_REACH == com.projecthero.mod.miraculous.catnoir.CatNoirConfig.STRIKE_REACH
				&& ViperionConfig.STRIKE_CONE == com.projecthero.mod.miraculous.catnoir.CatNoirConfig.STRIKE_CONE
				&& ViperionConfig.STRIKE_KNOCKBACK == com.projecthero.mod.miraculous.catnoir.CatNoirConfig.STRIKE_KNOCKBACK
				&& ViperionConfig.STRIKE_FINISHER_KNOCKBACK == com.projecthero.mod.miraculous.catnoir.CatNoirConfig.STRIKE_FINISHER_KNOCKBACK
				&& ViperionConfig.STRIKE_COOLDOWN == com.projecthero.mod.miraculous.catnoir.CatNoirConfig.STRIKE_COOLDOWN, "Cat Noir's numbers");
		helper.assertTrue(ViperionMoves.strike(p) == 1 && Miraculous.state(p).animId == Viperion.ANIM_STRIKE, "press 1: the forehand");
		helper.assertTrue(Miraculous.ready(p, ViperionConfig.STRIKE), "no cooldown mid-combo");
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(Math.abs(taken(z, 100) - ViperionConfig.STRIKE_DAMAGE) < EPS, "one swing, 9: took " + taken(z, 100));
			helper.assertTrue(ViperionMoves.strike(p) == 2 && Miraculous.state(p).animId == Viperion.ANIM_STRIKE_2, "press 2: the backhand");
			helper.runAfterDelay(6, () -> {
				helper.assertTrue(Math.abs(taken(z, 100) - 2 * ViperionConfig.STRIKE_DAMAGE) < EPS, "two swings: took " + taken(z, 100));
				helper.assertTrue(ViperionMoves.strike(p) == 3 && Miraculous.state(p).animId == Viperion.ANIM_STRIKE_3, "press 3: the overhead");
				helper.assertFalse(Miraculous.ready(p, ViperionConfig.STRIKE), "the cooldown starts with the finisher");
				helper.runAfterDelay(6, () -> {
					helper.assertTrue(Math.abs(taken(z, 100) - 3 * ViperionConfig.STRIKE_DAMAGE) < EPS, "three swings, 27: took " + taken(z, 100));
					helper.succeed();
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void lyreStrikeComboStartsOverAfterTheWindow(GameTestHelper helper) {
		ServerPlayer p = viperion(helper, 3.5, 2.0, 2.5);
		pump(helper, p);
		helper.assertTrue(ViperionMoves.strike(p) == 1, "swing 1");
		helper.assertTrue(ViperionMoves.strike(p) == 0, "a press too soon is queued");
		helper.runAfterDelay(ViperionConfig.STRIKE_GAP + 2, () -> {
			helper.assertTrue(ViperionMoves.comboStep(p) == 2, "the queued press swung");
			helper.runAfterDelay(ViperionConfig.STRIKE_WINDOW + 3, () -> {
				helper.assertTrue(ViperionMoves.strike(p) == 1, "too late: the combo starts over");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lyreSweepHitsTheArcInFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 3.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 5.5, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 5.0, 100, false);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 0.5, 100, false);
		helper.assertTrue(ViperionMoves.sweep(p) == 2, "two in the arc");
		helper.assertTrue(Math.abs(taken(a, 100) - ViperionConfig.SWEEP_DAMAGE) < EPS && Math.abs(taken(b, 100) - ViperionConfig.SWEEP_DAMAGE) < EPS, "12 each");
		helper.assertTrue(behind.getHealth() == 100f, "not the one behind");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE)
	public void sonicChordConeHitsAlongTheLook(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 0.5);
		Zombie far = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 7.0, 100, false);
		Zombie side = mob(helper, EntityType.ZOMBIE, 7.5, 2.0, 1.5, 100, false);
		helper.assertTrue(ViperionMoves.chord(p) == 1, "one in the cone");
		helper.assertTrue(Math.abs(taken(far, 100) - ViperionConfig.CHORD_DAMAGE) < EPS, "10, took " + taken(far, 100));
		helper.assertTrue(far.getDeltaMovement().z > 0.3, "blown back");
		helper.assertTrue(side.getHealth() == 100f, "not the one off to the side");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void resonanceStunsEverythingNear(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 3.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 0.5, 2.0, 3.5, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 0.5, 100, false);
		helper.assertTrue(ViperionMoves.resonance(p) == 2, "both round him");
		helper.assertTrue(Math.abs(taken(a, 100) - ViperionConfig.RESONANCE_DAMAGE) < EPS, "4 damage");
		helper.assertTrue(PunisherControl.isStunned(a) && PunisherControl.isStunned(b), "stunned");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void serpentDashSlithersLowAndFast(GameTestHelper helper) throws Exception {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 0.5);
		ViperionMoves.dash(p);
		helper.assertTrue(p.getDeltaMovement().z >= ViperionConfig.DASH_SPEED - 0.05, "driven along the look: " + p.getDeltaMovement());
		helper.assertTrue(Viperion.slithering(p), "slithering");
		java.lang.reflect.Method m = Player.class.getDeclaredMethod("updatePlayerPose");
		m.setAccessible(true);
		m.invoke(p);
		helper.assertTrue(p.getPose() == Pose.SWIMMING, "low: the swim / crawl pose, got " + p.getPose());
		helper.assertTrue(Miraculous.hasSafeLanding(p) && MiraculousConfig.SAFE_LANDING_MOVES.contains(ViperionConfig.DASH), "the landing is free");
		helper.succeed();
	}

	/** v0.16.7 (user): further (18 blocks), and when it ends his momentum is not taken away. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void serpentDashKeepsItsMomentumWhenItEnds(GameTestHelper helper) {
		ServerPlayer p = viperion(helper, 3.5, 2.0, 0.5);
		helper.assertTrue(ViperionConfig.DASH_DISTANCE >= 18.0, "1.5x further");
		pump(helper, p);
		ViperionMoves.dash(p);
		int ticks = (int) Math.ceil(ViperionConfig.DASH_DISTANCE / ViperionConfig.DASH_SPEED);
		helper.runAfterDelay(ticks + 2, () -> {
			helper.assertTrue(p.getDeltaMovement().horizontalDistance() >= ViperionConfig.DASH_SPEED - 0.05,
					"the slither is over and nothing braked him: " + p.getDeltaMovement());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void coilLeapSpringsForwardAndUp(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 0.5);
		Vec3 v = ViperionMoves.coil(p);
		helper.assertTrue(ViperionConfig.COIL_BLOCKS >= 17.0, "1.25x further");
		helper.assertTrue(v.y >= ViperionConfig.COIL_UP - 0.01 && v.z > 1.4, "up and forward: " + v);
		helper.assertTrue(Miraculous.hasSafeLanding(p) && MiraculousConfig.SAFE_LANDING_MOVES.contains(ViperionConfig.COIL), "the landing is free");
		// in the air (the mock is never moved): the fall is forgotten each tick, and a landing hit does nothing
		p.setOnGround(false);
		pump(helper, p);
		helper.runAfterDelay(8, () -> p.fallDistance = 30f);
		helper.runAfterDelay(11, () -> {
			helper.assertTrue(p.fallDistance == 0f, "the fall is forgotten while he is in the air");
			float hp = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().fall(), 12f);
			helper.assertTrue(p.getHealth() == hp, "no fall damage on the landing");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE)
	public void venomStrikePoisonsHard(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.5, 100, false);
		helper.assertTrue(ViperionMoves.venom(p) == z, "the jab lands");
		helper.assertTrue(Math.abs(taken(z, 100) - ViperionConfig.VENOM_DAMAGE) < EPS, "6 damage");
		MobEffectInstance poison = z.getEffect(MobEffects.POISON);
		// zombies are undead: poison is refused by vanilla, so check on a creature that takes it
		helper.assertTrue(poison == null || poison.getAmplifier() == ViperionConfig.VENOM_POISON_AMP, "Poison III if it takes poison");
		net.minecraft.world.entity.animal.Pig pig = mob(helper, EntityType.PIG, 4.5, 2.0, 4.0, 100, false);
		ready(helper, p);
		z.discard();
		helper.assertTrue(ViperionMoves.venom(p) == pig, "the pig is jabbed");
		MobEffectInstance pp = pig.getEffect(MobEffects.POISON);
		helper.assertTrue(pp != null && pp.getAmplifier() == ViperionConfig.VENOM_POISON_AMP && pp.getDuration() > ViperionConfig.VENOM_POISON_TICKS - 5, "Poison III, 8 s");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void poisonDoesNotTakeOnViperion(GameTestHelper helper) {
		ServerPlayer p = viperion(helper, 3.5, 2.0, 3.5);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2));
		p.tickCount++;
		Miraculous.tick(p);
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "the poison is gone");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+Z: Second Chance

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "viperion_checkpointAndRewindRestoreHimAndAMob")
	public void checkpointAndRewindRestoreHimAndAMob(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 1.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 5.5, 100, false);
		pump(helper, p);
		Vec3 home = p.position();
		Vec3 zHome = z.position();
		float hp = p.getHealth();
		p.getFoodData().setFoodLevel(18);
		helper.assertTrue(SecondChance.checkpoint(p), "the checkpoint is set");
		helper.assertTrue(Miraculous.countdownLeft(p) > 0, "it starts the countdown");
		helper.assertTrue(Viperion.checkpointTicksLeft(p) == ViperionConfig.SECOND_CHANCE_WINDOW && Viperion.rewindsLeft(p) == 3, "90 s, 3 rewinds");
		helper.runAfterDelay(25, () -> {
			p.teleportTo(home.x + 4, home.y, home.z + 3);
			p.setHealth(4f);
			p.getFoodData().setFoodLevel(3);
			p.setRemainingFireTicks(100);
			z.teleportTo(zHome.x - 3, zHome.y, zHome.z - 2);
			z.setHealth(30f);
			helper.assertTrue(SecondChance.rewind(p, false), "Shift+Z rewinds");
			helper.assertTrue(p.position().distanceTo(home) < 0.05, "he is back where he was");
			helper.assertTrue(Math.abs(p.getHealth() - hp) < 0.01f && p.getFoodData().getFoodLevel() == 18 && p.getRemainingFireTicks() <= 0,
					"his health, hunger and fire as they were");
			helper.assertTrue(z.position().distanceTo(zHome) < 0.05 && z.getHealth() == 100f, "the zombie is back with its health: " + z.position().distanceTo(zHome) + " hp " + z.getHealth() + " removed " + z.isRemoved());
			helper.assertTrue(Viperion.rewindsLeft(p) == 2, "two rewinds left");
			helper.assertFalse(SecondChance.rewind(p, false), "not a second rewind on the same press");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "viperion_aKillingBlowRewindsInstead")
	public void aKillingBlowRewindsInstead(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 1.5, 2.0, 1.5);
		pump(helper, p);
		Vec3 home = p.position();
		SecondChance.checkpoint(p);
		helper.runAfterDelay(5, () -> {
			p.teleportTo(home.x + 3, home.y, home.z + 3);
			p.hurt(p.damageSources().generic(), 1000f);
			helper.assertTrue(p.isAlive() && p.getHealth() > 1f, "he did not die");
			helper.assertTrue(p.position().distanceTo(home) < 0.05, "rewound to the checkpoint");
			helper.assertTrue(Viperion.rewindsLeft(p) == 2, "one rewind used");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "viperion_threeRewindsThenSpent")
	public void threeRewindsThenSpent(GameTestHelper helper) {
		ServerPlayer p = viperion(helper, 3.5, 2.0, 3.5);
		pump(helper, p);
		SecondChance.checkpoint(p);
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(SecondChance.rewind(p, true), "rewind " + (i + 1));
		}
		helper.assertFalse(SecondChance.live(p) || SecondChance.rewind(p, true), "spent after three");
		helper.assertTrue(Viperion.checkpointTicksLeft(p) == 0, "the HUD's ring is gone");
		helper.assertFalse(Miraculous.ready(p, ViperionConfig.SECOND_CHANCE), "and the special is on its cooldown");
		// with no checkpoint, a killing blow is a killing blow
		helper.assertFalse(SecondChance.cheatDeath(p, p.damageSources().generic()), "no checkpoint, no rewind");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "viperion_theNinetySecondWindowCloses")
	public void theNinetySecondWindowCloses(GameTestHelper helper) {
		ServerPlayer p = viperion(helper, 3.5, 2.0, 3.5);
		SecondChance.checkpoint(p);
		helper.assertTrue(ViperionConfig.SECOND_CHANCE_WINDOW == 1800, "90 s (user)");
		SecondChance.ageCheckpoint(p, ViperionConfig.SECOND_CHANCE_WINDOW - 1);
		helper.assertTrue(SecondChance.live(p), "still live at 89.95 s");
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertFalse(SecondChance.live(p), "the window has closed");
			helper.assertTrue(Viperion.checkpointTicksLeft(p) == 0, "and the HUD knows");
			helper.assertFalse(SecondChance.rewind(p, true), "nothing to rewind to");
			helper.succeed();
		});
	}

	/**
	 * v0.16.7 (user): a rewind turns back TIME -- the Miraculous countdown and every cooldown have the time left they had
	 * at the checkpoint, a move used since is ready again, and the 90 s window starts over.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "viperion_rewindTurnsBackTheTimers")
	public void rewindTurnsBackTheTimers(GameTestHelper helper) {
		ServerPlayer p = viperion(helper, 3.5, 2.0, 3.5);
		pump(helper, p);
		Miraculous.setCooldown(p, ViperionConfig.CHORD, ViperionConfig.CHORD_COOLDOWN);
		Miraculous.setVal(p, Viperion.V_SHIELD, helper.getLevel().getGameTime() + 100);
		SecondChance.checkpoint(p);
		int countdown = Miraculous.countdownLeft(p);
		int chord = Miraculous.cooldownRemaining(p, ViperionConfig.CHORD);
		int special = Miraculous.cooldownRemaining(p, ViperionConfig.SECOND_CHANCE);
		helper.assertTrue(countdown > 0 && chord == ViperionConfig.CHORD_COOLDOWN, "the countdown runs, Sonic Chord is cooling");
		helper.runAfterDelay(30, () -> {
			ViperionMoves.dash(p); // used since the checkpoint
			helper.assertTrue(Miraculous.countdownLeft(p) <= countdown - 29 && Viperion.checkpointTicksLeft(p) <= ViperionConfig.SECOND_CHANCE_WINDOW - 29,
					"time has passed");
			helper.assertTrue(SecondChance.rewind(p, false), "rewound");
			helper.assertTrue(Math.abs(Miraculous.countdownLeft(p) - countdown) <= 1, "the 3-minute countdown is back where it was: "
					+ Miraculous.countdownLeft(p) + " vs " + countdown);
			helper.assertTrue(Math.abs(Miraculous.cooldownRemaining(p, ViperionConfig.CHORD) - chord) <= 1, "Sonic Chord's cooldown too");
			helper.assertTrue(Math.abs(Miraculous.cooldownRemaining(p, ViperionConfig.SECOND_CHANCE) - special) <= 1, "and the special's");
			helper.assertTrue(Miraculous.ready(p, ViperionConfig.DASH), "the dash used since is ready again");
			helper.assertTrue(Viperion.running(p, Viperion.V_SHIELD) && Math.abs(Viperion.val(p, Viperion.V_SHIELD) - helper.getLevel().getGameTime() - 100) <= 1,
					"a kit timer (the String Shield) has the time it had left");
			helper.assertTrue(Viperion.checkpointTicksLeft(p) == ViperionConfig.SECOND_CHANCE_WINDOW && Viperion.rewindsLeft(p) == 2,
					"the 90 s window starts over; two rewinds left");
			helper.assertTrue(SecondChance.live(p), "still live");
			helper.succeed();
		});
	}

	/** v0.16.7 (user): a squadmate's Miraculous countdown and cooldowns are turned back too. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "viperion_rewindTurnsBackASquadmatesTimers")
	public void rewindTurnsBackASquadmatesTimers(GameTestHelper helper) {
		ServerPlayer p = viperion(helper, 2.5, 2.0, 2.5);
		ServerPlayer mate = mock(helper, 5.5, 2.0, 5.5);
		helper.assertTrue(Miraculous.grant(mate, Kwami.TIKKI), "Tikki");
		Miraculous.transform(mate);
		ready(helper, mate);
		Miraculous.startCountdown(mate);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("ViperionRewind" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		pump(helper, p);
		helper.runAfterDelay(10, () -> {
			SecondChance.checkpoint(p);
			int mateCountdown = Miraculous.countdownLeft(mate);
			helper.assertTrue(mateCountdown > 0, "her countdown is running");
			helper.runAfterDelay(30, () -> {
				try {
					Miraculous.setCooldown(mate, "ladybug_lucky_charm", 600);
					helper.assertTrue(Miraculous.countdownLeft(mate) <= mateCountdown - 29, "her time has run down");
					helper.assertTrue(SecondChance.rewind(p, false), "rewound");
					helper.assertTrue(Math.abs(Miraculous.countdownLeft(mate) - mateCountdown) <= 1, "her countdown is back where it was: "
							+ Miraculous.countdownLeft(mate) + " vs " + mateCountdown);
					helper.assertTrue(Miraculous.ready(mate, "ladybug_lucky_charm"), "her move used since is ready again");
					helper.succeed();
				} finally {
					squads.disband(squad);
				}
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "viperion_rewindBringsBackTheDeadAndTheBlocks")
	public void rewindBringsBackTheDeadAndTheBlocks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 1.5, 2.0, 1.5);
		Zombie dead = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 5.5, 40, false);
		UUID id = dead.getUUID();
		BlockPos wall = helper.absolutePos(new BlockPos(6, 2, 1));
		BlockPos hole = helper.absolutePos(new BlockPos(4, 1, 4));
		helper.getLevel().setBlock(wall, Blocks.OAK_PLANKS.defaultBlockState(), 3);
		pump(helper, p);
		SecondChance.checkpoint(p);
		helper.runAfterDelay(5, () -> {
			dead.hurt(helper.getLevel().damageSources().generic(), 1000f);
			helper.getLevel().destroyBlock(wall, false);
			helper.getLevel().setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
			BlockPos placed = helper.absolutePos(new BlockPos(2, 2, 6));
			helper.getLevel().setBlock(placed, Blocks.COBBLESTONE.defaultBlockState(), 3);
			Zombie newcomer = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 3.5, 20, false);
			helper.assertTrue(SecondChance.trackedBlocks(p) >= 3, "the changes were remembered: " + SecondChance.trackedBlocks(p));
			helper.runAfterDelay(25, () -> {
				helper.assertTrue(SecondChance.rewind(p, false), "rewound");
				Entity back = helper.getLevel().getEntity(id);
				helper.assertTrue(back instanceof Zombie bz && bz.isAlive() && bz.getHealth() == 40f, "the zombie killed since is back, whole");
				helper.assertTrue(helper.getLevel().getBlockState(wall).is(Blocks.OAK_PLANKS) && helper.getLevel().getBlockState(hole).is(Blocks.STONE),
						"the broken blocks are back");
				helper.assertTrue(helper.getLevel().getBlockState(placed).isAir(), "the block placed since is gone");
				helper.assertTrue(newcomer.isRemoved(), "the hostile that came since is gone");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void constrictRootsAndSqueezes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100, true);
		pump(helper, p);
		helper.assertTrue(ViperionMoves.constrict(p) == z, "the strings wrap the zombie");
		Vec3 at = z.position();
		helper.runAfterDelay(2, () -> z.teleportTo(at.x + 1.5, at.y, at.z));
		helper.runAfterDelay(42, () -> {
			helper.assertTrue(z.position().distanceTo(at) < 0.2, "rooted on its spot");
			float t = taken(z, 100);
			helper.assertTrue(t >= 3 * ViperionConfig.CONSTRICT_SQUEEZE_DAMAGE - EPS && t <= 5 * ViperionConfig.CONSTRICT_SQUEEZE_DAMAGE + EPS, "squeezed, took " + t + " at " + z.position().distanceTo(at) + " cons " + Viperion.val(p, Viperion.V_CONSTRICT) + " now " + helper.getLevel().getGameTime() + " ticks " + p.tickCount + " inv " + z.invulnerableTime + " hp " + z.getHealth() + "/" + z.getMaxHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void constrictOnlySlowsABoss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 0.5);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 400, false);
		helper.assertTrue(ViperionMoves.constrict(p) == boss, "wrapped");
		helper.assertTrue(boss.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a boss is slowed, not rooted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stringShieldBlocksTheFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 3.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.0, 100, false);
		Zombie back = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 2.0, 100, false);
		ViperionMoves.shield(p);
		helper.assertTrue(Viperion.shielding(p), "the Lyre spins");
		float hp = p.getHealth();
		p.hurt(p.damageSources().mobAttack(front), 6f);
		helper.assertTrue(p.getHealth() == hp, "a hit from the front is stopped");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(back), 6f);
		helper.assertTrue(p.getHealth() < hp, "a hit from behind is not");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void heatSenseToggles(GameTestHelper helper) {
		ServerPlayer p = viperion(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(ViperionMoves.heatSense(p) && Viperion.heatSense(p), "on");
		helper.assertFalse(ViperionMoves.heatSense(p) || Viperion.heatSense(p), "off");
		helper.assertTrue(ViperionConfig.HEAT_RANGE >= 32.0, "32 blocks");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hissScaresMobsButNotBosses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = viperion(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 6.5, 20, true);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 0.5, 2.0, 0.5, 400, true);
		helper.assertTrue(ViperionMoves.hiss(p) == 1, "one scared");
		helper.assertTrue(ViperionMoves.fleeing(p, z) && !ViperionMoves.fleeing(p, boss), "the zombie runs, the boss does not");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Kwami, the phrases, the wiring

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void phrasesTransformAndEggsFeed(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(PowerGrants.grantHero(p, "viperion").ok() && Miraculous.holds(p, Kwami.SASS) && HeroTiers.holdsHero(p, "viperion"),
				"the command grants Viperion");
		helper.assertTrue("Viperion".equals(MiraculousPhrases.heroName(Kwami.SASS)), "the hero's name");
		helper.assertTrue(MiraculousPhrases.match(Kwami.SASS, "sass scales slither") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.SASS, "Scales slither!") == MiraculousPhrases.Spoken.TRANSFORM
				&& MiraculousPhrases.match(Kwami.SASS, "SCALES REST") == MiraculousPhrases.Spoken.DETRANSFORM
				&& MiraculousPhrases.match(Kwami.SASS, "Sass, scales rest!") == MiraculousPhrases.Spoken.DETRANSFORM, "the phrase table");
		helper.assertTrue(Miraculous.onChat(p, "Sass, scales slither!") && Miraculous.transformedAs(p, Kwami.SASS), "Viperion");
		Miraculous.edit(p, n -> n.formChangeAt -= 100L);
		helper.assertTrue(Miraculous.onChat(p, "Scales rest!") && !Miraculous.transformed(p) && Miraculous.hungry(p), "back, and Sass is hungry");
		helper.assertTrue(Miraculous.foodOf(Kwami.SASS) == Items.EGG, "eggs");
		ItemStack eggs = new ItemStack(Items.EGG, 4);
		helper.assertTrue(Miraculous.wantsFood(p, eggs), "using an egg feeds him instead of throwing it");
		helper.assertTrue(Miraculous.feed(p, eggs) && eggs.getCount() == 3, "he eats one");
		helper.assertTrue(Miraculous.finishMeal(p) && !Miraculous.hungry(p), "fed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boxJewelAndGuideChapter(GameTestHelper helper) {
		helper.assertTrue(MiraculousBoxItem.BOX_KWAMIS.contains(Kwami.SASS), "the Snake Bracelet is in the box's pool");
		helper.assertTrue(MiraculousItems.jewelOf(Kwami.SASS) == MiraculousItems.SNAKE_BRACELET, "jewelOf");
		ServerPlayer r = mock(helper, 3.5, 2.0, 3.5);
		r.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(MiraculousItems.SNAKE_BRACELET));
		MiraculousItems.SNAKE_BRACELET.use(helper.getLevel(), r, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(Miraculous.holds(r, Kwami.SASS) && r.getMainHandItem().isEmpty(), "the Bracelet grants Sass and is used up");
		var chapters = HeroPackGuide.chapters();
		var ch = HeroPackGuide.miraculousChapter(Kwami.SASS);
		helper.assertTrue(ch.title().getContents() instanceof TranslatableContents t && t.getKey().equals("projecthero.guide.viperion"), "his own chapter");
		helper.assertTrue(chapters.indexOf(ch) == 56, "after the Quirks, at " + chapters.indexOf(ch));
		StringBuilder all = new StringBuilder();
		for (var line : ch.lines()) {
			all.append(line.getString()).append('\n');
		}
		helper.assertTrue(all.indexOf("Second Chance") >= 0 && all.indexOf("Scales slither!") >= 0 && all.indexOf("projecthero.") < 0,
				"the Second Chance section, the phrase, no untranslated lines");
		helper.succeed();
	}
}

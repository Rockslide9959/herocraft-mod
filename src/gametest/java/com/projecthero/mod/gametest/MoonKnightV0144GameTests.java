package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.MoonKnightDamage;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.ability.MoonKnightAlters;
import com.projecthero.mod.moonknight.ability.MoonKnightCape;
import com.projecthero.mod.moonknight.ability.MoonKnightDarts;
import com.projecthero.mod.moonknight.ability.MoonKnightGrapple;
import com.projecthero.mod.moonknight.ability.MoonKnightKhonshu;
import com.projecthero.mod.moonknight.data.MoonKnightState;
import com.projecthero.mod.moonknight.entity.CrescentDartEntity;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4 Moon Knight balance pass: the three lunar states (Nether / End = day), halved falls,
 * the AoE Moonbeam, the minute-long Eye of Khonshu, the Crescent Fan's targets, the Grappling Line pulling a
 * squad-mate, and the Grapple Kick's aim assist / lead / hit check. Mock players are not reliably ticked, so every
 * move is driven directly; mobs are NoAI husks, and every test that hurts mobs has its own batch.
 */
public class MoonKnightV0144GameTests implements FabricGameTest {
	private static ServerPlayer knight(GameTestHelper helper, Vec3 rel, float yaw) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, yaw, 0.0f);
		p.setYHeadRot(yaw);
		p.yRotO = yaw;
		p.yHeadRotO = yaw;
		p.xRotO = 0.0f;
		MoonKnight.grant(p, false);
		MoonKnight.setTransformedForTesting(p, true);
		return p;
	}

	private static ServerPlayer mate(GameTestHelper helper, ServerPlayer leader, Vec3 absolute, String tag) {
		ServerPlayer mate = helper.makeMockServerPlayerInLevel();
		mate.setGameMode(GameType.SURVIVAL);
		mate.moveTo(absolute.x, absolute.y, absolute.z, 0.0f, 0.0f);
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("mk0144" + tag + leader.getUUID().toString().substring(0, 6), leader.getUUID());
		squads.addMember(squad, mate.getUUID());
		return mate;
	}

	private static void disband(GameTestHelper helper, ServerPlayer leader) {
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad s = squads.squadOf(leader.getUUID());
		if (s != null) {
			squads.disband(s);
		}
	}

	private static Husk husk(GameTestHelper helper, Vec3 absolute) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		h.moveTo(absolute.x, absolute.y, absolute.z, 0.0f, 0.0f);
		h.setNoAi(true);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0);
		h.setHealth(300.0f);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	/** Clear a box of air (relative coords) so blocks other tests left behind can't block a line of sight. */
	private static void clear(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = 1; y <= 5; y++) {
				for (int z = z0; z <= z1; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	// ---------------------------------------------------------------- lunar power

	@GameTest(template = EMPTY_STRUCTURE)
	public void exactlyThreeLunarStatesAndTheNetherAndEndAreDay(GameTestHelper helper) {
		helper.assertTrue(MoonKnightLunar.State.values().length == 3, "three states");
		helper.assertTrue(MoonKnightLunar.resolve(true, false, 0) == MoonKnightLunar.State.DAY, "day is DAY, even on a full-moon day");
		for (int phase = 1; phase < 8; phase++) {
			helper.assertTrue(MoonKnightLunar.resolve(true, true, phase) == MoonKnightLunar.State.NIGHT, "phase " + phase + " is NIGHT");
		}
		helper.assertTrue(MoonKnightLunar.resolve(true, true, 0) == MoonKnightLunar.State.FULL_MOON, "phase 0 at night is FULL MOON");
		helper.assertTrue(MoonKnightLunar.resolve(false, true, 0) == MoonKnightLunar.State.DAY, "no moon (Nether / End) is always DAY");
		helper.assertTrue(MoonKnightLunar.State.DAY.power() < MoonKnightLunar.State.NIGHT.power()
				&& MoonKnightLunar.State.NIGHT.power() < MoonKnightLunar.State.FULL_MOON.power(), "day weakest, full moon strongest");
		helper.assertTrue(MoonKnightLunar.State.NIGHT.power() == 1.0f, "the base numbers are the night numbers");
		helper.assertFalse(MoonKnightLunar.State.DAY.isNight(), "day is not night");
		helper.assertTrue(MoonKnightLunar.State.FULL_MOON.isNight(), "full moon is night");
		var server = helper.getLevel().getServer();
		for (var key : List.of(Level.NETHER, Level.END)) {
			ServerLevel other = server.getLevel(key);
			if (other != null) {
				helper.assertTrue(MoonKnightLunar.state(other) == MoonKnightLunar.State.DAY, key.location() + " counts as day");
				helper.assertFalse(MoonKnightLunar.isMoonNight(other), key.location() + " has no night");
				helper.assertTrue(MoonKnightLunar.power(other, BlockPos.ZERO) == MoonKnightConfig.LUNAR_DAY, key.location() + " x0.7");
			}
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- suit passives

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitedFallsHurtHalfAsMuch(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		float fall = MoonKnightDamage.incomingFactor(p, p.damageSources().fall());
		float generic = MoonKnightDamage.incomingFactor(p, p.damageSources().generic());
		helper.assertTrue(Math.abs(fall - generic * MoonKnightConfig.SUIT_FALL_DAMAGE_TAKEN) < 1.0e-4f && MoonKnightConfig.SUIT_FALL_DAMAGE_TAKEN == 0.5f,
				"a fall is halved on top of the suit's -20% (" + fall + " vs " + generic + ")");
		MoonKnight.setTransformedForTesting(p, false);
		helper.assertTrue(MoonKnightDamage.incomingFactor(p, p.damageSources().fall()) == 1.0f, "only while suited");
		helper.succeed();
	}

	private static void setAlter(ServerPlayer p, MoonKnightAlter alter) {
		MoonKnightState c = MoonKnight.state(p).copy();
		c.alter = alter.ordinal();
		MoonKnight.saveState(p, c);
		MoonKnightAlters.reconcile(p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitStepsUpFullBlocks(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		MoonKnightAlters.reconcile(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 1.0) < 1.0e-6,
				"suited: step height 1.0 (" + p.getAttributeValue(Attributes.STEP_HEIGHT) + ")");
		var mod = p.getAttribute(Attributes.STEP_HEIGHT).getModifier(MoonKnightAlters.SUIT_STEP);
		helper.assertTrue(mod != null && mod.amount() == 0.4, "one fixed-id +0.4 modifier");
		MoonKnightAlters.reconcile(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 1.0) < 1.0e-6, "re-applying never stacks");
		MoonKnight.setTransformedForTesting(p, false);
		MoonKnightAlters.reconcile(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 0.6) < 1.0e-6, "out of the suit: vanilla 0.6");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stevenHasNoCape(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		setAlter(p, MoonKnightAlter.MARC);
		helper.assertTrue(MoonKnightCape.canBlock(p), "Marc can raise the cape");
		setAlter(p, MoonKnightAlter.STEVEN);
		helper.assertFalse(MoonKnightAlter.STEVEN.hasCape(), "Steven's Mr. Knight suit has no cape");
		helper.assertFalse(MoonKnightCape.canBlock(p), "so no Cape Block as Steven");
		helper.assertTrue(MoonKnightAlter.MARC.hasCape() && MoonKnightAlter.JAKE.hasCape(), "Marc and Jake keep theirs");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stevenMinesWithFortuneThree(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		ServerLevel level = helper.getLevel();
		var fortune = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState ore = Blocks.DIAMOND_ORE.defaultBlockState();

		setAlter(p, MoonKnightAlter.MARC);
		helper.assertTrue(MoonKnightAlters.stevenFortuneLevel(p) == 0 && MoonKnightAlters.fortuneTool(p, pick, level) == pick,
				"Marc's tool is untouched");
		int marc = 0;
		for (int i = 0; i < 20; i++) {
			marc += Block.getDrops(ore, level, pos, null, p, pick).stream().mapToInt(ItemStack::getCount).sum();
		}
		helper.assertTrue(marc == 20, "no Fortune as Marc: one diamond each (" + marc + ")");

		setAlter(p, MoonKnightAlter.STEVEN);
		helper.assertTrue(MoonKnightAlters.stevenFortuneLevel(p) == 3, "Steven mines with Fortune III");
		helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(fortune, MoonKnightAlters.fortuneTool(p, ItemStack.EMPTY, level)) == 3,
				"bare-handed too");
		ItemStack fortuneOne = pick.copy();
		fortuneOne.enchant(fortune, 1);
		helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(fortune, MoonKnightAlters.fortuneTool(p, fortuneOne, level)) == 3,
				"a weaker Fortune tool is raised to III (the higher counts, never stacked)");
		helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(fortune, fortuneOne) == 1, "his real tool is never changed");
		int steven = 0;
		for (int i = 0; i < 20; i++) {
			steven += Block.getDrops(ore, level, pos, null, p, pick).stream().mapToInt(ItemStack::getCount).sum();
		}
		helper.assertTrue(steven > 20, "Fortune III: more diamonds from the same 20 ores (" + steven + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theRequestedNumbers(GameTestHelper helper) {
		helper.assertTrue(MoonKnightConfig.MOONBEAM_DAMAGE == 35.0f && MoonKnightConfig.MOONBEAM_COOLDOWN == 100
				&& MoonKnightConfig.MOONBEAM_COST == 10.0f, "Moonbeam 35 / 5 s / 10%");
		helper.assertTrue(MoonKnightConfig.EYE_DURATION == 1200 && MoonKnightConfig.EYE_RADIUS == 30.0, "Eye: a minute, 30 blocks");
		helper.assertTrue(MoonKnightConfig.MOON_MARK_COOLDOWN == 100, "Moon Mark 5 s");
		helper.assertTrue(MoonKnightConfig.DART_FAN_COUNT == 5, "five fan darts");
		helper.assertTrue(MoonKnightConfig.DASH_SPEED * MoonKnightConfig.DASH_TICKS > 10.0, "the dash goes further (>10 blocks)");
		helper.assertTrue(MoonKnightConfig.GROUND_SLAM_DAMAGE == 18.0f, "Crescent Slam 18");
		helper.assertTrue(MoonKnightConfig.DIVE_KICK_DAMAGE == 20.0f, "Grapple Kick 20");
		helper.assertTrue(MoonKnightConfig.JUDGEMENT_DAMAGE_PER_SECOND == 10.0f, "Judgement 10/s");
		helper.assertTrue(MoonKnightConfig.VENGEANCE_PROTECTOR_KILL > 5.0f && MoonKnightConfig.VENGEANCE_NIGHT_KILL > 2.0f
				&& MoonKnightConfig.VENGEANCE_DAY_KILL > 1.0f, "kills give a little more Vengeance");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z: Moonbeam AoE, Eye of Khonshu

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk0144_moonbeam")
	public void moonbeamHitsEveryHostileInTheRadius(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(1.5, 2.0, 1.5), 0.0f);
		Vec3 at = helper.absoluteVec(new Vec3(8.5, 2.0, 8.5));
		Husk centre = husk(helper, at);
		Husk side = husk(helper, at.add(3.0, 0.0, 0.0));
		Husk outside = husk(helper, at.add(0.0, 0.0, 7.0));
		ServerPlayer ally = mate(helper, p, at.add(-2.0, 0.0, 0.0), "mb");
		helper.assertFalse(MoonKnightKhonshu.isFoe(p, ally), "a squad-mate is never a foe");
		helper.assertFalse(MoonKnightKhonshu.isFoe(p, p), "nor is he");
		helper.assertTrue(MoonKnightKhonshu.isFoe(p, side), "a hostile is");
		int hits = MoonKnightKhonshu.strikeMoonbeam(p, at, 1.0f, false);
		float centreLoss = 300.0f - centre.getHealth();
		float sideLoss = 300.0f - side.getHealth();
		helper.assertTrue(hits == 2 && centreLoss > 0.0f && sideLoss > 0.0f,
				"both hostiles in the radius are hit (" + hits + " hits, " + centreLoss + " / " + sideLoss + ")");
		helper.assertTrue(outside.getHealth() == 300.0f, "one 7 blocks out is not");
		helper.assertTrue(centreLoss >= sideLoss, "falloff: the centre takes the most (" + centreLoss + " vs " + sideLoss + ")");
		helper.assertTrue(Math.abs(MoonKnightKhonshu.moonbeamFalloff(0.0) - 1.0f) < 1.0e-4f
				&& Math.abs(MoonKnightKhonshu.moonbeamFalloff(MoonKnightConfig.MOONBEAM_RADIUS) - 0.6f) < 1.0e-4f,
				"35 at the centre, 60% at the edge");
		disband(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk0144_eye")
	public void eyeLastsAMinuteDebuffsAndRainsMoonbeams(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(1.5, 2.0, 1.5), 0.0f);
		Husk foe = husk(helper, p.position().add(8.0, 0.0, 8.0));
		ServerPlayer ally = mate(helper, p, p.position().add(3.0, 0.0, 0.0), "eye");
		MoonKnight.setVengeance(p, 100.0f);
		MoonKnightKhonshu.openEye(p);
		helper.assertTrue(MoonKnightKhonshu.eyeTicksLeft(p) == 1200, "the Eye stays open a full minute");
		var weak = foe.getEffect(MobEffects.WEAKNESS);
		helper.assertTrue(weak != null && weak.getDuration() > 1100, "hostiles are weakened for the whole minute");
		var str = p.getEffect(MobEffects.DAMAGE_BOOST);
		helper.assertTrue(str != null && str.getDuration() > 1100 && p.hasEffect(MobEffects.MOVEMENT_SPEED),
				"and he is empowered for the whole minute");
		// a mob that walks in later is caught by the next pulse
		Husk late = husk(helper, p.position().add(-6.0, 0.0, 6.0));
		MoonKnightKhonshu.eyePulse(p);
		helper.assertTrue(late.hasEffect(MobEffects.WEAKNESS) && late.hasEffect(MobEffects.GLOWING), "late arrivals are debuffed too");
		helper.assertFalse(ally.hasEffect(MobEffects.WEAKNESS), "a squad-mate is never debuffed");
		List<LivingEntity> targets = MoonKnightKhonshu.eyeTargets(p);
		helper.assertFalse(targets.contains(ally) || targets.contains(p), "the random Moonbeams never pick him or his squad");
		LivingEntity struck = MoonKnightKhonshu.eyeStrike(p);
		helper.assertTrue(struck != null && MoonKnightKhonshu.isFoe(p, struck), "a random Moonbeam falls on a foe (" + struck + ")");
		helper.assertTrue(struck.getHealth() < struck.getMaxHealth(), "and hurts it");
		helper.assertTrue(targets.contains(foe) && targets.contains(late), "both husks in the area are candidates");
		disband(helper, p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- R: the Crescent Fan

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk0144_fan")
	public void fanLocksOnToTheFiveClosestHostiles(GameTestHelper helper) {
		clear(helper, -3, 12, 0, 13);
		ServerPlayer p = knight(helper, new Vec3(1.5, 2.0, 1.5), 0.0f);
		Vec3 base = p.position();
		Husk[] husks = new Husk[6];
		for (int i = 0; i < 6; i++) {
			husks[i] = husk(helper, base.add(1.5 * i - 2.0, 0.0, 3.0 + 1.5 * i)); // each one further away
		}
		ServerPlayer ally = mate(helper, p, base.add(1.0, 0.0, 1.0), "fan");
		List<LivingEntity> targets = MoonKnightDarts.fanTargets(p);
		helper.assertTrue(targets.size() == 5, "five targets (" + targets.size() + ")");
		for (int i = 0; i < 5; i++) {
			helper.assertTrue(targets.contains(husks[i]), "husk " + i + " (one of the closest five) is targeted");
		}
		helper.assertFalse(targets.contains(husks[5]), "the sixth, furthest one is not");
		helper.assertFalse(targets.contains(ally), "never a squad-mate");
		helper.assertTrue(targets.get(0) == husks[0], "closest first");
		int thrown = MoonKnightDarts.throwFan(p);
		List<CrescentDartEntity> darts = helper.getLevel().getEntitiesOfClass(CrescentDartEntity.class, new AABB(p.blockPosition()).inflate(4.0));
		helper.assertTrue(thrown == 5 && darts.size() == 5, "five darts (" + darts.size() + ")");
		for (CrescentDartEntity d : darts) {
			helper.assertTrue(d.isLocked(), "every dart is locked on");
		}
		for (int i = 0; i < 5; i++) {
			int id = husks[i].getId();
			helper.assertTrue(darts.stream().anyMatch(d -> d.targetId() == id), "one dart per target (" + i + ")");
		}
		darts.forEach(CrescentDartEntity::discard);
		disband(helper, p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- X / G: the line and the kick

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "mk0144_line")
	public void grapplingLineReelsInASquadMate(GameTestHelper helper) {
		clear(helper, 0, 6, 0, 10);
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 1.5), 0.0f);
		ServerPlayer ally = mate(helper, p, p.position().add(0.0, 0.0, 6.0), "line");
		p.lookAt(EntityAnchorArgument.Anchor.EYES, ally.position().add(0.0, 1.0, 0.0));
		helper.assertTrue(MoonKnightGrapple.pullableFriend(p, ally), "a squad-mate can be pulled");
		MoonKnightGrapple.fireLine(p);
		helper.assertTrue(MoonKnightGrapple.isReeling(p) && MoonKnightGrapple.reelTargetId(p) == ally.getId(),
				"Sneak+X at a squad-mate reels him in");
		helper.assertFalse(ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "no Slowness on a friend");
		helper.onEachTick(() -> MoonKnightGrapple.INSTANCE.tick(p));
		helper.runAfterDelay(MoonKnightConfig.GRAPPLE_LINE_TRAVEL_TICKS + 2, () -> {
			helper.assertTrue(ally.getDeltaMovement().z < -0.3, "he is dragged toward the Moon Knight (" + ally.getDeltaMovement() + ")");
			disband(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk0144_kick")
	public void grappleKickAimAssistPicksTheEnemyNearTheCrosshair(GameTestHelper helper) {
		clear(helper, 0, 8, 0, 20);
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 1.5), 0.0f); // yaw 0: looking straight down +Z
		Vec3 look = p.getViewVector(1.0f);
		helper.assertTrue(look.z > 0.99, "looking down +Z (" + look + ")");
		// a squad-mate dead centre, and an enemy a little off the crosshair behind him
		ServerPlayer ally = mate(helper, p, p.position().add(0.0, 0.0, 6.0), "kick");
		Husk offAxis = husk(helper, p.position().add(1.8, 0.0, 15.0));
		helper.assertTrue(MoonKnightGrapple.kickTarget(p) == offAxis,
				"the ally is skipped and the husk ~7 degrees off the crosshair is picked (" + MoonKnightGrapple.kickTarget(p) + ")");
		// a closer enemy much further off the crosshair (45 degrees) never wins
		husk(helper, p.position().add(4.0, 0.0, 4.0));
		helper.assertTrue(MoonKnightGrapple.kickTarget(p) == offAxis, "outside the cone is ignored");
		// the pull leads a moving target, and the kick lands on a generous box check
		offAxis.setDeltaMovement(0.5, 0.0, 0.0);
		Vec3 anchor = MoonKnightGrapple.kickAnchor(p, offAxis);
		helper.assertTrue(anchor.x > offAxis.getBoundingBox().getCenter().x + 0.5, "the pull leads a moving target (" + anchor + ")");
		helper.assertFalse(MoonKnightGrapple.kickConnects(p, offAxis), "no hit from 15 blocks");
		Husk close = husk(helper, p.position().add(0.0, 0.0, 1.6));
		helper.assertTrue(MoonKnightGrapple.kickConnects(p, close), "a generous hit from 1.6 blocks");
		disband(helper, p);
		helper.succeed();
	}

	/** v0.14.4: an invisible Jake cannot be targeted -- not even by a mob he has just hit. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void anInvisibleJakeCannotBeTargeted(GameTestHelper helper) {
		helper.getLevel().getServer().getWorldData().setDifficulty(net.minecraft.world.Difficulty.NORMAL);
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		setAlter(p, MoonKnightAlter.JAKE);
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 2));
		husk.setTarget(p);
		helper.assertTrue(husk.getTarget() == p, "a visible Jake can be targeted");
		helper.assertTrue(MoonKnightAlters.vanish(p), "Jake vanishes");
		helper.assertTrue(MoonKnightAlters.isVanished(p), "and counts as vanished");
		helper.assertTrue(husk.getTarget() == null, "Vanish drops the aggro");
		husk.setTarget(p);
		husk.setLastHurtByMob(p);
		helper.assertTrue(husk.getTarget() == null, "and nothing can target him while he is invisible");
		helper.assertTrue(MoonKnightAlters.detectionFactor(p) == 0.0, "nor even notice him");
		helper.assertTrue(p.getEffect(MobEffects.INVISIBILITY).isInfiniteDuration(), "Vanish has no time limit");
		MoonKnightAlters.endVanish(p, true);
		helper.assertFalse(MoonKnightAlters.inVanish(p), "until he ends it");
		husk.setTarget(p);
		helper.assertTrue(husk.getTarget() == p, "once visible he can be targeted again");
		MoonKnightAlters.vanish(p);
		setAlter(p, MoonKnightAlter.STEVEN);
		MoonKnightAlters.reconcile(p);
		helper.assertFalse(MoonKnightAlters.inVanish(p), "switching away from Jake ends Vanish");
		husk.discard();
		helper.succeed();
	}
}

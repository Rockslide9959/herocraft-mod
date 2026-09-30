package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.MoonKnightDamage;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.ability.MoonKnightAlters;
import com.projecthero.mod.moonknight.ability.MoonKnightKhonshu;
import com.projecthero.mod.moonknight.ability.MoonKnightSkull;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Moon Knight Phases 5 (C Alters) and 6 (V Khonshu + Khonshu's Resurrection). Mock players aren't reliably ticked and
 * report creative, so everything is driven through the move classes directly; the night / full-moon checks are
 * injected rather than changing the world clock (other tests run at the same time). Tests that put hostile mobs in
 * an area of effect run in their own batches.
 */
public class MoonKnightPowerGameTests implements FabricGameTest {
	private static ServerPlayer knight(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		MoonKnight.grant(p, false);
		MoonKnight.setTransformedForTesting(p, true);
		return p;
	}

	private static void setAlter(ServerPlayer p, MoonKnightAlter alter) {
		MoonKnightState c = MoonKnight.state(p).copy();
		c.alter = alter.ordinal();
		MoonKnight.saveState(p, c);
		MoonKnightAlters.reconcile(p);
	}

	private static void clearCooldown(ServerPlayer p, String id) {
		MoonKnightState c = MoonKnight.state(p).copy();
		c.abilityReadyAt.remove(id);
		MoonKnight.saveState(p, c);
	}

	private static boolean hasModifier(ServerPlayer p, net.minecraft.core.Holder<Attribute> attribute, String id) {
		var inst = p.getAttribute(attribute);
		return inst != null && inst.getModifier(ProjectHeroMod.id(id)) != null;
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	// ================================================================ C: Alters

	@GameTest(template = EMPTY_STRUCTURE)
	public void tapCyclesAltersWithACooldown(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.MARC, "Marc is in control first");
		MoonKnightAlters.INSTANCE.tap(p);
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.STEVEN, "tap: Marc -> Steven");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "alter") > 0, "switching starts the alter cooldown");
		MoonKnightAlters.INSTANCE.tap(p);
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.STEVEN, "a second tap inside the cooldown does nothing");
		clearCooldown(p, "alter");
		MoonKnightAlters.INSTANCE.tap(p);
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.JAKE, "tap: Steven -> Jake");
		clearCooldown(p, "alter");
		MoonKnightAlters.INSTANCE.tap(p);
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.MARC, "tap: Jake -> Marc");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.ALTER_SWAP, "the swap pose plays");
		helper.assertTrue(MoonKnightAnim.action(p).swapFrom == MoonKnightAlter.JAKE.ordinal()
				&& MoonKnightAnim.action(p).swapStart == p.level().getGameTime(),
				"v0.13.21: Marc's suit rematerialises over Jake's from now (synced swapFrom / swapStart)");
		helper.assertTrue(MoonKnightAlter.MARC.suitTexture().equals("textures/armor/moon_knight_marc.png")
				&& com.projecthero.mod.armor.SuperheroArmorVisuals.has("moon_knight_steven")
				&& com.projecthero.mod.armor.SuperheroArmorVisuals.has("moon_knight_jake"), "each alter has his own suit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void radialSelectSetsTheAlterDirectly(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		MoonKnightAlters.select(p, MoonKnightAlter.JAKE.ordinal());
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.JAKE, "the picker jumps straight to Jake");
		clearCooldown(p, "alter");
		MoonKnightAlters.select(p, 7);
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.JAKE, "an out-of-range choice is ignored");
		MoonKnightAlters.select(p, MoonKnightAlter.JAKE.ordinal());
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "alter") == 0, "choosing who is already in control costs nothing");
		MoonKnightAlters.select(p, MoonKnightAlter.STEVEN.ordinal());
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.STEVEN, "then to Steven");
		// the server-side hold only flags the picker open / closed; the choice itself is the payload
		MoonKnightAlters.INSTANCE.holdStart(p);
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_ALTER_PICKER), "holding C flags the picker open");
		MoonKnightAlters.INSTANCE.holdRelease(p, 20);
		helper.assertFalse(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_ALTER_PICKER), "releasing closes it");
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.STEVEN, "and the release itself changes nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aFractureBlocksSwitching(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		MoonKnightState c = MoonKnight.state(p).copy();
		c.alter = MoonKnightAlter.STEVEN.ordinal();
		c.fractureReturnAlter = MoonKnightAlter.MARC.ordinal();
		c.fractureUntil = p.level().getGameTime() + 200L;
		MoonKnight.saveState(p, c);
		MoonKnightAlters.INSTANCE.tap(p);
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.STEVEN, "tap can't switch during a Fracture");
		MoonKnightAlters.select(p, MoonKnightAlter.JAKE.ordinal());
		helper.assertTrue(MoonKnight.alter(p) == MoonKnightAlter.STEVEN, "nor can the picker");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void alterPassivesFollowTheAlterAndTheSuit(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		helper.assertTrue(hasModifier(p, Attributes.ARMOR, "moon_knight_marc_armor"), "Marc: +4 armour");
		helper.assertTrue(hasModifier(p, Attributes.KNOCKBACK_RESISTANCE, "moon_knight_marc_knockback"), "Marc: knockback resistance");
		float marcOut = MoonKnightAlters.outgoingFactor(p, p, helper.getLevel().damageSources().playerAttack(p));
		helper.assertTrue(Math.abs(marcOut - 1.2f) < 1.0e-4f, "Marc: +20% melee (" + marcOut + ")");

		setAlter(p, MoonKnightAlter.JAKE);
		helper.assertFalse(hasModifier(p, Attributes.ARMOR, "moon_knight_marc_armor"), "Marc's armour leaves with him");
		helper.assertTrue(hasModifier(p, Attributes.SNEAKING_SPEED, "moon_knight_jake_sneak"), "Jake: faster sneaking");
		helper.assertTrue(MoonKnightAlters.detectionFactor(p) == MoonKnightConfig.JAKE_DETECTION_FACTOR, "Jake: half detection range");

		Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, 6, 2, 6);
		husk.setYRot(0.0f);
		husk.setYBodyRot(0.0f); // facing +Z
		p.moveTo(husk.getX(), husk.getY(), husk.getZ() - 1.5, 0.0f, 0.0f);
		helper.assertTrue(MoonKnightAlters.fromBehind(p, husk), "standing at its back counts as behind");
		float back = MoonKnightAlters.outgoingFactor(p, husk, helper.getLevel().damageSources().playerAttack(p));
		helper.assertTrue(Math.abs(back - 1.5f) < 1.0e-4f, "Jake: +50% from behind (" + back + ")");
		p.moveTo(husk.getX(), husk.getY(), husk.getZ() + 1.5, 180.0f, 0.0f);
		float front = MoonKnightAlters.outgoingFactor(p, husk, helper.getLevel().damageSources().playerAttack(p));
		helper.assertTrue(front == 1.0f, "no bonus face to face");

		setAlter(p, MoonKnightAlter.STEVEN);
		helper.assertFalse(hasModifier(p, Attributes.SNEAKING_SPEED, "moon_knight_jake_sneak"), "Jake's sneak leaves with him");
		float taken = MoonKnightAlters.incomingFactor(p, helper.getLevel().damageSources().mobAttack(husk));
		helper.assertTrue(Math.abs(taken - MoonKnightConfig.STEVEN_MELEE_TAKEN) < 1.0e-4f, "Steven: -15% melee taken");
		helper.assertTrue(MoonKnightAlters.incomingFactor(p, helper.getLevel().damageSources().magic()) == 1.0f,
				"but only melee");

		setAlter(p, MoonKnightAlter.MARC);
		helper.assertTrue(hasModifier(p, Attributes.ARMOR, "moon_knight_marc_armor"), "Marc again");
		MoonKnight.setTransformedForTesting(p, false);
		helper.assertFalse(hasModifier(p, Attributes.ARMOR, "moon_knight_marc_armor"), "un-transforming clears the armour");
		helper.assertFalse(hasModifier(p, Attributes.KNOCKBACK_RESISTANCE, "moon_knight_marc_knockback"), "and the knockback resistance");
		helper.assertTrue(MoonKnightAlters.detectionFactor(p) == 1.0, "no stealth out of the suit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fistOfKhonshuCostsFifteenVengeance(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		MoonKnight.setVengeance(p, 50.0f);
		MoonKnightAlters.INSTANCE.sneak(p);
		helper.assertTrue(Math.abs(MoonKnight.vengeance(p) - 35.0f) < 0.01f, "Fist of Khonshu costs 15 Vengeance");
		var strength = p.getEffect(MobEffects.DAMAGE_BOOST);
		helper.assertTrue(strength != null && strength.getAmplifier() == 1, "Strength II");
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_FIST), "FLAG_FIST while it lasts");
		helper.assertTrue(hasModifier(p, Attributes.KNOCKBACK_RESISTANCE, "moon_knight_fist_knockback"), "and extra knockback resistance");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "alter_sneak") > 0, "30 s special cooldown");
		MoonKnightAlters.INSTANCE.sneak(p);
		helper.assertTrue(Math.abs(MoonKnight.vengeance(p) - 35.0f) < 0.01f, "not again while cooling down");

		// not enough Vengeance: nothing happens and no cooldown
		ServerPlayer poor = knight(helper);
		MoonKnight.setVengeance(poor, 5.0f);
		MoonKnightAlters.INSTANCE.sneak(poor);
		helper.assertTrue(MoonKnight.cooldownRemaining(poor, "alter_sneak") == 0, "a refused Fist costs no cooldown");

		MoonKnight.setTransformedForTesting(p, false);
		helper.assertFalse(hasModifier(p, Attributes.KNOCKBACK_RESISTANCE, "moon_knight_fist_knockback"), "the suit coming off ends it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "moonknight_vanish")
	public void vanishMakesMobsLoseTheScent(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		setAlter(p, MoonKnightAlter.JAKE);
		Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, 5, 2, 5);
		husk.setTarget(p);
		helper.assertTrue(husk.getTarget() == p, "the husk is hunting him");
		MoonKnightAlters.INSTANCE.sneak(p);
		helper.assertTrue(husk.getTarget() == null, "Vanish: it loses him");
		helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "and he is invisible");
		helper.assertTrue(MoonKnightAlters.detectionFactor(p) == MoonKnightConfig.VANISH_DETECTION_FACTOR, "and very hard to notice");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "alter_sneak") > 0, "30 s special cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void scholarsSightFindsTreasureAndOres(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 2, 1), Blocks.DIAMOND_ORE);
		helper.setBlock(new BlockPos(5, 2, 1), Blocks.SPAWNER);
		BlockPos centre = helper.absolutePos(new BlockPos(3, 2, 3));
		var found = MoonKnightAlters.scan(helper.getLevel(), centre, 4);
		long chest = helper.absolutePos(new BlockPos(1, 2, 1)).asLong();
		long ore = helper.absolutePos(new BlockPos(3, 2, 1)).asLong();
		long spawner = helper.absolutePos(new BlockPos(5, 2, 1)).asLong();
		helper.assertTrue(found.stream().anyMatch(e -> e[0] == chest), "chests show");
		helper.assertTrue(found.stream().anyMatch(e -> e[0] == ore), "ores show");
		helper.assertTrue(found.stream().anyMatch(e -> e[0] == spawner), "spawners show");
		helper.assertTrue(MoonKnightAlters.interestColour(Blocks.STONE.defaultBlockState()) < 0, "stone doesn't");
		// leave nothing behind for later tests at this spot
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
		helper.setBlock(new BlockPos(3, 2, 1), Blocks.AIR);
		helper.setBlock(new BlockPos(5, 2, 1), Blocks.AIR);
		helper.succeed();
	}

	// ================================================================ V: Khonshu

	@GameTest(template = EMPTY_STRUCTURE)
	public void moonbeamIsRefusedByDay(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		MoonKnight.setVengeance(p, 50.0f);
		helper.assertFalse(MoonKnightKhonshu.moonbeam(p, false), "Khonshu cannot hear you in daylight");
		helper.assertTrue(Math.abs(MoonKnight.vengeance(p) - 50.0f) < 0.01f, "nothing is spent");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "khonshu") == 0, "and no cooldown starts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "moonknight_moonbeam")
	public void moonbeamBurnsTheUndeadTwiceAsHard(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		MoonKnight.setVengeance(p, 50.0f);
		Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, 5, 2, 2);
		Vindicator vindicator = helper.spawnWithNoFreeWill(EntityType.VINDICATOR, 5, 2, 3);
		for (LivingEntity e : new LivingEntity[] { husk, vindicator }) {
			e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
			e.setHealth(200.0f);
		}
		// look straight at the husk from four blocks away, at night (injected)
		// the empty template is tiny, so blocks other tests left behind can stand here: clear the line of sight
		for (int x = 0; x <= 7; x++) {
			for (int y = 2; y <= 5; y++) {
				for (int z = 1; z <= 4; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		// (the test structure may be rotated, so the yaw is worked out from the real positions)
		Vec3 from = helper.absoluteVec(new Vec3(1.5, 2.0, 2.5));
		float yaw = (float) Math.toDegrees(Math.atan2(-(husk.getX() - from.x), husk.getZ() - from.z));
		p.moveTo(from.x, from.y, from.z, yaw, 0.0f);
		p.setYHeadRot(yaw);
		// the entity ray reads the previous tick's rotation, which an unticked mock player never updates
		// (a living entity's view yaw is its HEAD yaw)
		p.yRotO = yaw;
		p.yHeadRotO = yaw;
		p.xRotO = 0.0f;
		Vec3 aim = MoonKnightKhonshu.moonbeamTarget(p);
		helper.assertTrue(MoonKnightKhonshu.moonbeam(p, true), "the beam falls at night");
		float power = MoonKnightLunar.power(p);
		float huskLoss = 200.0f - husk.getHealth();
		float vindLoss = 200.0f - vindicator.getHealth();
		helper.assertTrue(vindLoss > 0.0f, "the living take the moonlight (" + vindLoss + "; husk " + huskLoss + ", aimed at "
				+ aim + ", husk at " + husk.position() + ", vindicator at " + vindicator.position() + ", alive "
				+ vindicator.isAlive() + "/" + husk.isAlive() + ")");
		helper.assertTrue(huskLoss > vindLoss * 1.8f, "the undead take double (" + huskLoss + " vs " + vindLoss + ")");
		helper.assertTrue(Math.abs(MoonKnightKhonshu.moonbeamDamage(vindicator, 1.0f) - MoonKnightConfig.MOONBEAM_DAMAGE) < 1.0e-3f
				&& Math.abs(MoonKnightKhonshu.moonbeamDamage(husk, 1.0f) - 2.0f * MoonKnightConfig.MOONBEAM_DAMAGE) < 1.0e-3f,
				"base 35, 70 to undead (live power " + power + ")");
		helper.assertTrue(Math.abs(MoonKnight.vengeance(p) - 40.0f) < 0.01f, "costs 10 Vengeance");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "khonshu") > 0, "5 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "moonknight_eye")
	public void eyeOfKhonshuNeedsAFullMoonAndFullVengeance(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		helper.assertTrue(key(MoonKnightKhonshu.eyeBlocker(p, false)).endsWith("eye_need_full_moon"), "no full moon, no Eye");
		MoonKnight.setVengeance(p, 60.0f);
		helper.assertTrue(key(MoonKnightKhonshu.eyeBlocker(p, true)).endsWith("eye_need_vengeance"), "needs 100 Vengeance");
		MoonKnight.setVengeance(p, 100.0f);
		helper.assertTrue(MoonKnightKhonshu.eyeBlocker(p, true) == null, "full moon + 100: ready");

		Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, 5, 2, 5);
		MoonKnightKhonshu.openEye(p);
		helper.assertTrue(MoonKnight.vengeance(p) == 0.0f, "the Eye consumes every point of Vengeance");
		helper.assertFalse(MoonKnight.fractured(p), "a deliberate spend never fractures the mind");
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST) && p.hasEffect(MobEffects.MOVEMENT_SPEED), "Strength + Speed");
		var weak = husk.getEffect(MobEffects.WEAKNESS);
		helper.assertTrue(husk.hasEffect(MobEffects.GLOWING) && weak != null && weak.getAmplifier() == 1,
				"hostiles glow and are weakened (II)");
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_EYE), "FLAG_EYE while it lasts");

		MoonKnight.setVengeance(p, 100.0f);
		helper.assertTrue(key(MoonKnightKhonshu.eyeBlocker(p, true)).endsWith("eye_used"), "once per night");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "moonknight_judgement")
	public void judgementBurnsAndHealsForFifteenSeconds(GameTestHelper helper) {
		// v0.14.4: 15 s, 10/s (x lunar power) burn, every point dealt to the judged target heals; no kill refund
		ServerPlayer p = knight(helper);
		MoonKnight.setVengeance(p, 50.0f);
		p.setHealth(4.0f);
		Husk judged = helper.spawnWithNoFreeWill(EntityType.HUSK, 5, 2, 5);
		Husk bystander = helper.spawnWithNoFreeWill(EntityType.HUSK, 6, 2, 3);
		for (Husk h : new Husk[] { judged, bystander }) {
			h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
			h.setHealth(200.0f);
		}
		MoonKnightKhonshu.judge(p, judged);
		helper.assertTrue(MoonKnightKhonshu.isJudged(p, judged), "the mark is set");
		helper.assertTrue(MoonKnightConfig.JUDGEMENT_TICKS == 300 && MoonKnightConfig.JUDGEMENT_COOLDOWN == 400
				&& MoonKnightConfig.JUDGEMENT_COST == 10.0f, "15 s, 20 s cooldown, 10 Vengeance");

		float power = MoonKnightLunar.power(p);
		helper.assertTrue(MoonKnightKhonshu.judgementBurn(p), "the judged target burns");
		float dealt = 200.0f - judged.getHealth();
		helper.assertTrue(Math.abs(dealt - 10.0f * power) < 0.05f, "10 x lunar power a second (" + dealt + ", power " + power + ")");
		float healed = p.getHealth() - 4.0f;
		helper.assertTrue(Math.abs(healed - Math.min(dealt, p.getMaxHealth() - 4.0f)) < 0.05f,
				"and he heals what it took (" + healed + " of " + dealt + ")");

		// his own hits on the judged target heal too; hits on anyone else don't
		p.setHealth(4.0f);
		judged.invulnerableTime = 0;
		judged.hurt(p.damageSources().playerAttack(p), 3.0f);
		helper.assertTrue(p.getHealth() > 4.0f, "a hit on the judged target heals (" + p.getHealth() + ")");
		p.setHealth(4.0f);
		bystander.hurt(p.damageSources().playerAttack(p), 3.0f);
		helper.assertTrue(p.getHealth() == 4.0f, "a hit on anyone else doesn't");

		MoonKnightKhonshu.onEntityKilled(judged, helper.getLevel().damageSources().playerAttack(p));
		helper.assertFalse(MoonKnightKhonshu.isJudged(p, judged), "its death ends the Judgement");
		helper.assertTrue(Math.abs(MoonKnight.vengeance(p) - 50.0f) < 0.01f, "no kill refund any more");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void resurrectionSavesOncePerCycle(GameTestHelper helper) {
		ServerPlayer p = knight(helper);
		p.setHealth(1.0f);
		helper.assertTrue(MoonKnightDamage.tryResurrect(p), "the first fatal hit is refused");
		helper.assertTrue(Math.abs(p.getHealth() - MoonKnightConfig.RESURRECT_HEALTH) < 0.01f, "back to 6 hearts");
		MoonKnightState s = MoonKnight.state(p);
		helper.assertFalse(s.resurrectionCharged, "the charge is spent");
		helper.assertTrue(s.resurrectionCycle == MoonKnightLunar.moonCycle(p.level()), "in this lunar cycle");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.RESURRECT, "the resurrection pose plays");
		p.setHealth(1.0f);
		helper.assertFalse(MoonKnightDamage.tryResurrect(p), "not again in the same cycle");
		MoonKnight.tickSecond(p);
		helper.assertFalse(MoonKnight.state(p).resurrectionCharged, "and it doesn't recharge within the cycle");

		// out of the suit, Khonshu doesn't answer at all
		ServerPlayer bare = knight(helper);
		MoonKnight.setTransformedForTesting(bare, false);
		helper.assertFalse(MoonKnightDamage.tryResurrect(bare), "only while transformed");
		helper.assertTrue(MoonKnight.state(bare).resurrectionCharged, "and the charge is kept");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void khonshusSkullIsAWholeDrawing(GameTestHelper helper) {
		var pts = MoonKnightSkull.points();
		helper.assertTrue(pts.size() > 200, "enough points for a recognisable skull (" + pts.size() + ")");
		double minY = pts.stream().mapToDouble(v -> v[1]).min().orElse(0);
		double maxY = pts.stream().mapToDouble(v -> v[1]).max().orElse(0);
		double maxX = pts.stream().mapToDouble(v -> Math.abs(v[0])).max().orElse(0);
		helper.assertTrue(minY < -10.0 && maxY > 11.5 && maxX < 6.0, "beak to crescent, about 11 wide (" + minY + ".." + maxY + ", " + maxX + ")");
		helper.succeed();
	}
}

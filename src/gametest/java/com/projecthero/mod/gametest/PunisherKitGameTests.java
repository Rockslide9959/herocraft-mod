package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherAbilityManager;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.punisher.ability.PunisherGrenade;
import com.projecthero.mod.punisher.ability.PunisherMark;
import com.projecthero.mod.punisher.ability.PunisherMelee;
import com.projecthero.mod.punisher.ability.PunisherRoll;
import com.projecthero.mod.punisher.ability.PunisherSmoke;
import com.projecthero.mod.punisher.ability.PunisherWarzone;
import com.projecthero.mod.punisher.entity.C4ChargeEntity;
import com.projecthero.mod.punisher.entity.FlashbangEntity;
import com.projecthero.mod.punisher.entity.PunisherEntityTypes;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.18: the Punisher's new kit -- Target Designation (+30%, one mark), Threat Assessment, Brutal Strike, Breach
 * Kick, Warzone (spares the caller and his squad, breaks nothing), Smoke Screen, Flashbang, Tactical Advance, the
 * Shift routing and every move's separate cooldown, and the retired C4 entity clearing itself out of old saves.
 * Mob targets are pigs-in-all-but-name: 100-health cows (no armour, so damage reads exactly) and no-AI zombies.
 */
public class PunisherKitGameTests implements FabricGameTest {

	private static ServerPlayer at(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		// mock players are placed at world spawn -- bring them into the test's own area
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(v.x, v.y, v.z, 0f, 0f);
		return p;
	}

	private static ServerPlayer punisher(GameTestHelper helper, double x, double z) {
		ServerPlayer p = at(helper, x, z);
		Punisher.grant(p);
		return p;
	}

	private static Cow cow(GameTestHelper helper, double x, double z) {
		Cow c = EntityType.COW.create(helper.getLevel());
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		c.moveTo(v.x, v.y, v.z, 0f, 0f);
		c.setNoAi(true);
		c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		c.setHealth(100f);
		helper.getLevel().addFreshEntity(c);
		return c;
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie zm = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		zm.moveTo(v.x, v.y, v.z, 0f, 0f);
		zm.setNoAi(true);
		helper.getLevel().addFreshEntity(zm);
		return zm;
	}

	private static void lookAt(ServerPlayer p, LivingEntity t) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, t.position().add(0, t.getBbHeight() * 0.5, 0));
	}

	private static void hit(LivingEntity target, ServerPlayer by, float amount) {
		target.invulnerableTime = 0;
		target.hurt(by.level().damageSources().playerAttack(by), amount);
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.01f;
	}

	private static Squad squad(GameTestHelper helper, ServerPlayer leader, ServerPlayer mate) {
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("pun" + leader.getUUID().toString().substring(0, 8), leader.getUUID());
		squads.addMember(squad, mate.getUUID());
		return squad;
	}

	// ---------------- R / Shift+R ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void markedTargetTakesThirtyPercentMoreAndOnlyOneMarkAtATime(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		ServerPlayer other = at(helper, 6.5, 1.5);
		Cow a = cow(helper, 1.5, 5.5);
		Cow b = cow(helper, 4.5, 5.5);

		PunisherMark.mark(p, a);
		hit(a, p, 10f);
		hit(b, p, 10f);
		helper.assertTrue(near(a.getHealth(), 87f), "the marked cow takes 13 from a 10 hit, has " + a.getHealth());
		helper.assertTrue(near(b.getHealth(), 90f), "the unmarked cow takes the plain 10, has " + b.getHealth());
		hit(a, other, 10f);
		helper.assertTrue(near(a.getHealth(), 77f), "someone else's hit on the mark is not raised, has " + a.getHealth());

		PunisherMark.mark(p, b);
		helper.assertTrue(PunisherMark.markOf(p) == b, "marking B replaces the mark");
		hit(a, p, 10f);
		hit(b, p, 10f);
		helper.assertTrue(near(a.getHealth(), 67f), "A is no longer marked, has " + a.getHealth());
		helper.assertTrue(near(b.getHealth(), 77f), "B now takes +30%, has " + b.getHealth());
		PunisherMark.clear(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void designationAndThreatAssessmentHaveTheirOwnCooldowns(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		Cow c = cow(helper, 1.5, 5.5);
		lookAt(p, c);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(PunisherMark.markOf(p) == c, "R marks the cow the Punisher is looking at");
		helper.assertFalse(Punisher.abilityReady(p, PunisherMark.ABILITY), "Target Designation goes on cooldown");
		helper.assertTrue(Punisher.abilityReady(p, PunisherMark.THREAT), "Threat Assessment is still ready");

		List<LivingEntity> seen = PunisherMark.threatsAround(p);
		helper.assertTrue(seen.contains(c) && !seen.contains(p), "Threat Assessment sees the cow, never the Punisher");
		p.setShiftKeyDown(true);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		p.setShiftKeyDown(false);
		helper.assertFalse(Punisher.abilityReady(p, PunisherMark.THREAT), "Shift+R spends Threat Assessment's own cooldown");
		helper.assertTrue(Punisher.cooldownRemaining(p, PunisherMark.THREAT) > PunisherConfig.MARK_COOLDOWN_TICKS,
				"12 s, not the mark's 5 s");
		PunisherMark.clear(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aSquadmateCannotBeMarked(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		ServerPlayer mate = at(helper, 1.5, 4.5);
		Squad squad = squad(helper, p, mate);
		helper.assertFalse(PunisherMark.canMark(p, mate), "a squadmate is never a valid mark");
		SquadManager.get(helper.getLevel().getServer()).disband(squad);
		helper.succeed();
	}

	// ---------------- G / Shift+G ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void brutalStrikeDealsEighteenAndStuns(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		Cow c = cow(helper, 1.5, 4.0);
		lookAt(p, c);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(near(c.getHealth(), 100f - PunisherConfig.BRUTAL_STRIKE_DAMAGE), "10 damage, has " + c.getHealth());
		helper.assertTrue(PunisherControl.isStunned(c), "the target is stunned");
		MobEffectInstance slow = c.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() >= 9, "stunned = it cannot move");
		helper.assertFalse(Punisher.abilityReady(p, PunisherMelee.STRIKE), "Brutal Strike goes on cooldown");
		helper.assertTrue(Punisher.abilityReady(p, PunisherMelee.KICK), "Breach Kick keeps its own cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "punisher_breach_kick")
	public void breachKickDealsTwentyFiveThrowsAndStuns(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		Cow c = cow(helper, 1.5, 4.0);
		lookAt(p, c);
		p.setShiftKeyDown(true);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(near(c.getHealth(), 100f - PunisherConfig.BREACH_KICK_DAMAGE), "15 damage, has " + c.getHealth());
		Vec3 v = c.getDeltaMovement();
		Vec3 away = c.position().subtract(p.position()).multiply(1, 0, 1).normalize();
		double out = v.x * away.x + v.z * away.z;
		helper.assertTrue(out >= PunisherConfig.BREACH_KICK_SPEED * 0.9, "thrown hard straight away from the kicker, got " + out);
		helper.assertTrue(PunisherControl.isStunned(c), "and stunned");
		helper.assertFalse(Punisher.abilityReady(p, PunisherMelee.KICK), "Breach Kick goes on cooldown");
		helper.assertTrue(Punisher.abilityReady(p, PunisherMelee.STRIKE), "Brutal Strike keeps its own cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aStunnedMobCannotTakeATarget(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		Zombie zm = zombie(helper, 1.5, 4.5);
		zm.setTarget(p);
		helper.assertTrue(zm.getTarget() == p, "control: the zombie can target the player");
		PunisherControl.stun(zm, 40);
		helper.assertTrue(zm.getTarget() == null, "the stun drops its target");
		zm.setTarget(p);
		helper.assertTrue(zm.getTarget() == null, "and it cannot pick one while stunned");
		zm.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swingingAtNothingCostsNoCooldown(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		p.setXRot(-80f); // at the sky
		PunisherMelee.brutalStrike(p);
		helper.assertTrue(Punisher.abilityReady(p, PunisherMelee.STRIKE), "a whiff spends nothing");
		helper.succeed();
	}

	// ---------------- Z / Shift+Z ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "punisher_warzone")
	public void warzoneSparesTheCallerAndHisSquadAndBreaksNoBlocks(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 2.5, 2.5);
		ServerPlayer mate = at(helper, 3.5, 2.5);
		Squad squad = squad(helper, p, mate);
		Cow c = cow(helper, 2.5, 4.5);
		BlockPos stone = helper.absolutePos(new BlockPos(2, 1, 3));
		helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
		Vec3 at = Vec3.atCenterOf(stone).add(0, 0.5, 0);

		List<LivingEntity> hit = PunisherWarzone.blastTargets(helper.getLevel(), p, at);
		helper.assertTrue(hit.contains(c), "the cow inside the blast is hit");
		helper.assertFalse(hit.contains(p), "never the caller");
		helper.assertFalse(hit.contains(mate), "never a squadmate");

		float pBefore = p.getHealth();
		float mateBefore = mate.getHealth();
		PunisherWarzone.detonate(helper.getLevel(), p, at);
		helper.assertTrue(c.getHealth() < 100f - PunisherConfig.WARZONE_DAMAGE * PunisherConfig.WARZONE_EDGE_DAMAGE + 0.01f,
				"the cow takes a missile's damage, has " + c.getHealth());
		helper.assertTrue(p.getHealth() == pBefore && mate.getHealth() == mateBefore, "the caller and his squad are untouched");
		helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "no block is broken");
		SquadManager.get(helper.getLevel().getServer()).disband(squad);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "punisher_warzone")
	public void warzoneCallIsHeldAndHasItsOwnLongCooldown(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 2.5, 2.5);
		p.setShiftKeyDown(true);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(PunisherWarzone.charging(p), "Shift+Z starts the call");
		helper.assertTrue(Punisher.abilityReady(p, PunisherGrenade.ABILITY), "it is not the grenade");
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
		helper.assertFalse(PunisherWarzone.charging(p), "letting go of Z before 5 s calls it off");
		helper.assertTrue(Punisher.abilityReady(p, PunisherWarzone.ABILITY), "a cancelled call costs nothing");

		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		p.setShiftKeyDown(false);
		PunisherWarzone.tickCharge(p);
		helper.assertFalse(PunisherWarzone.charging(p), "letting go of Shift calls it off too");

		PunisherWarzone.call(p, p.position());
		helper.assertTrue(PunisherWarzone.activeStrikes() > 0, "the barrage is running");
		helper.assertTrue(Punisher.cooldownRemaining(p, PunisherWarzone.ABILITY) > 100 * 20, "2-minute cooldown");
		helper.assertTrue(Punisher.abilityReady(p, PunisherGrenade.ABILITY), "the grenade's cooldown is separate");
		PunisherWarzone.clearSessionState(); // no stray missiles into the neighbouring tests
		helper.succeed();
	}

	// ---------------- X / Shift+X ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void tacticalAdvanceGivesSpeedFourWithItsOwnCooldown(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		p.setShiftKeyDown(true);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		p.setShiftKeyDown(false);
		MobEffectInstance speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
		helper.assertTrue(speed != null && speed.getAmplifier() == 3, "Speed IV");
		helper.assertTrue(speed.getDuration() > 29 * 20, "for 30 s");
		helper.assertFalse(Punisher.abilityReady(p, PunisherRoll.ADVANCE), "Tactical Advance goes on cooldown");
		helper.assertTrue(Punisher.abilityReady(p, PunisherRoll.ABILITY), "the roll is still ready");
		helper.succeed();
	}

	// ---------------- C / Shift+C ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "punisher_smoke")
	public void smokeMakesAMobLoseItsTarget(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		Zombie zm = zombie(helper, 2.5, 3.5);
		zm.setTarget(p);
		helper.assertTrue(zm.getTarget() == p, "control: it has the Punisher targeted");
		PunisherSmoke.deploy(p, zm.position());
		PunisherSmoke.tick(helper.getLevel().getServer());
		helper.assertTrue(PunisherSmoke.inSmoke(zm), "the zombie is inside the cloud");
		helper.assertTrue(zm.getTarget() == null, "the smoke makes it lose its target");
		zm.setTarget(p);
		helper.assertTrue(zm.getTarget() == null, "and it cannot pick a new one while inside");
		zm.discard();
		PunisherSmoke.clearSessionState();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void smokeScreenAndFlashbangHaveSeparateCooldowns(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.assertFalse(Punisher.abilityReady(p, PunisherSmoke.SMOKE), "C: Smoke Screen on cooldown");
		helper.assertTrue(Punisher.abilityReady(p, PunisherSmoke.FLASH), "the Flashbang is still ready");
		p.setShiftKeyDown(true);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		p.setShiftKeyDown(false);
		helper.assertFalse(Punisher.abilityReady(p, PunisherSmoke.FLASH), "Shift+C: Flashbang on cooldown");
		List<FlashbangEntity> thrown = helper.getLevel().getEntitiesOfClass(FlashbangEntity.class,
				new AABB(p.blockPosition()).inflate(4));
		helper.assertTrue(!thrown.isEmpty(), "a flashbang was thrown");
		thrown.forEach(FlashbangEntity::discard);
		PunisherSmoke.clearSessionState();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "punisher_flashbang")
	public void flashbangBlindsSlowsAndConfusesButNotTheThrowerOrSquad(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		ServerPlayer mate = at(helper, 2.5, 1.5);
		Squad squad = squad(helper, p, mate);
		Cow c = cow(helper, 3.5, 3.5);
		Zombie zm = zombie(helper, 4.5, 2.5);
		zm.setTarget(p);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.2, 2.5));

		PunisherSmoke.flash(helper.getLevel(), at, p);
		helper.assertTrue(c.hasEffect(MobEffects.BLINDNESS) && c.hasEffect(MobEffects.CONFUSION), "blinded and dazed");
		MobEffectInstance slow = c.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() == PunisherConfig.FLASHBANG_SLOW_AMP, "Slowness II");
		helper.assertTrue(c.getEffect(MobEffects.BLINDNESS).getDuration() > 7 * 20, "for 8 s");
		helper.assertTrue(zm.getTarget() == null, "a mob drops its target");
		helper.assertFalse(p.hasEffect(MobEffects.BLINDNESS), "never the thrower");
		helper.assertFalse(mate.hasEffect(MobEffects.BLINDNESS), "never a squadmate");
		zm.discard();
		SquadManager.get(helper.getLevel().getServer()).disband(squad);
		helper.succeed();
	}

	// ---------------- V / N / save safety ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void vIsLeftForTheWeaponAbilities(GameTestHelper helper) {
		ServerPlayer p = punisher(helper, 1.5, 1.5);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		PunisherAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(Punisher.state(p).abilityReadyAt.isEmpty(), "V does nothing in the kit itself");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aLeftoverC4ChargeFromAnOldSaveRemovesItself(GameTestHelper helper) {
		C4ChargeEntity c4 = PunisherEntityTypes.C4_CHARGE.create(helper.getLevel());
		Vec3 v = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		c4.moveTo(v.x, v.y, v.z, 0f, 0f);
		helper.getLevel().addFreshEntity(c4);
		helper.succeedWhen(() -> helper.assertTrue(c4.isRemoved(), "the retired charge discards itself"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stunAndNoTargetWindowsOnlyTouchMobs(GameTestHelper helper) {
		Cow c = cow(helper, 2.5, 2.5);
		helper.assertFalse(PunisherControl.refusesTarget((Mob) c), "control: nothing on it yet");
		PunisherControl.stun(c, 20);
		helper.assertTrue(PunisherControl.refusesTarget((Mob) c), "a stunned mob may not take a target");
		helper.succeed();
	}
}

package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternConstructAttacks;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;
import com.projecthero.mod.greenlantern.item.GreenLanternItems;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.ability.MoonKnightDash;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * v0.14.3: the Green Lantern revamp's new keys and hard-light entities, the Hulk's rampage hitting his squad, and the
 * Moon Knight tuning (100-block grapple, faster glide, 3D dash).
 */
public class V0143GameTests implements FabricGameTest {
	private static ServerPlayer lantern(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		// mock players are placed at world spawn -- bring them into the test's own (ticking) area
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2.0, 1.5));
		player.moveTo(at.x, at.y, at.z, 0f, 0f);
		GreenLantern.bond(player);
		return player;
	}

	private static long count(GameTestHelper helper, ServerPlayer near, HardLightConstructEntity.Shape shape) {
		return helper.getLevel().getEntitiesOfClass(HardLightConstructEntity.class, new AABB(near.blockPosition()).inflate(40),
				e -> e.shape() == shape).size();
	}

	private static Husk husk(GameTestHelper helper, ServerPlayer player, double ahead) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		h.moveTo(player.getX(), player.getY(), player.getZ() + ahead, 180f, 0f);
		h.setNoAi(true);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	// ---------------------------------------------------------------- Green Lantern

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftCFiresTheMissileBarrageInsteadOfDismissing(GameTestHelper helper) {
		ServerPlayer player = lantern(helper);
		GreenLanternConstructs.deploy(player, ConstructType.ENERGY_BLADE);
		helper.assertTrue(!GreenLanternConstructs.of(player.getUUID()).isEmpty(), "the blade should be equipped");
		float before = GreenLantern.state(player).ringCharge;
		player.setShiftKeyDown(true);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_6, true);
		player.setShiftKeyDown(false);
		helper.assertTrue(!GreenLanternConstructs.of(player.getUUID()).isEmpty(), "Shift+C must no longer dismiss constructs");
		helper.assertTrue(GreenLantern.cooldownRemaining(player, GreenLanternConstructAttacks.MISSILE_CD) > 0,
				"Shift+C should start the Missile Barrage cooldown");
		helper.assertTrue(GreenLantern.state(player).ringCharge < before, "the barrage should cost charge");
		helper.assertTrue(count(helper, player, HardLightConstructEntity.Shape.MISSILE) == GreenLanternConfig.MISSILE_COUNT,
				"six missiles should be in flight");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void nDismissesEveryConstruct(GameTestHelper helper) {
		ServerPlayer player = lantern(helper);
		GreenLanternConstructs.deploy(player, ConstructType.ENERGY_BLADE);
		GreenLanternConstructs.deploy(player, ConstructType.EMERALD_WARRIOR);
		helper.assertTrue(GreenLanternConstructAttacks.liveCount(player.getUUID(), HardLightConstructEntity.Shape.WARRIOR) == 1,
				"the Emerald Warrior should be summoned");
		GreenLanternAbilityManager.clearConstructs(player);
		helper.assertTrue(GreenLanternConstructs.of(player.getUUID()).isEmpty(), "N should dismiss the block / marker constructs");
		helper.assertTrue(GreenLanternConstructAttacks.liveCount(player.getUUID(), HardLightConstructEntity.Shape.WARRIOR) == 0,
				"N should dismiss the hard-light entities too");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void holdingSneakNTakesTheRingOff(GameTestHelper helper) {
		ServerPlayer player = lantern(helper);
		player.setShiftKeyDown(true);
		GreenLanternAbilityManager.ringRemoveStart(player);
		helper.assertTrue(player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY)
				.has(GreenLanternFx.CH_RING_REMOVE), "the removal should be visible to every client");
		helper.runAfterDelay(20, () -> helper.assertTrue(GreenLantern.hasPower(player), "one second in, the ring is still on"));
		helper.runAfterDelay(GreenLanternConfig.RING_REMOVE_HOLD_TICKS + 10, () -> {
			helper.assertFalse(GreenLantern.hasPower(player), "after 5 s the power should be gone");
			helper.assertTrue(player.getInventory().contains(new net.minecraft.world.item.ItemStack(GreenLanternItems.POWER_RING)),
					"the Power Ring should be back in the inventory");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void lettingGoOfSneakKeepsTheRingOn(GameTestHelper helper) {
		ServerPlayer player = lantern(helper);
		player.setShiftKeyDown(true);
		GreenLanternAbilityManager.ringRemoveStart(player);
		helper.runAfterDelay(20, () -> player.setShiftKeyDown(false));
		helper.runAfterDelay(GreenLanternConfig.RING_REMOVE_HOLD_TICKS + 10, () -> {
			helper.assertTrue(GreenLantern.hasPower(player), "releasing Sneak should cancel the removal");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void giantHandGrabsThenHurls(GameTestHelper helper) {
		ServerPlayer player = lantern(helper);
		Husk target = husk(helper, player, 4.0);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition().add(0, -0.4, 0));
		GreenLanternConstructAttacks.giantHand(player);
		helper.assertTrue(GreenLanternConstructAttacks.isHolding(player), "H should close the hand round the husk");
		helper.runAfterDelay(15, () -> {
			GreenLanternConstructAttacks.giantHand(player);
			helper.assertFalse(GreenLanternConstructAttacks.isHolding(player), "H again should hurl it");
			helper.assertTrue(GreenLantern.cooldownRemaining(player, GreenLanternConstructAttacks.HAND_CD) > 0,
					"the throw starts the Giant Hand cooldown");
			helper.assertTrue(target.getHealth() < target.getMaxHealth(), "being crushed should hurt");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chainSnarePinsNearbyHostiles(GameTestHelper helper) {
		ServerPlayer player = lantern(helper);
		husk(helper, player, 3.0);
		GreenLanternConstructs.deploy(player, ConstructType.CHAIN_SNARE);
		helper.assertTrue(GreenLanternConstructAttacks.liveCount(player.getUUID(), HardLightConstructEntity.Shape.CHAINS) >= 1,
				"a set of chains per hostile in range");
		helper.assertTrue(GreenLanternConstructs.cooldownRemainingFor(player, ConstructType.CHAIN_SNARE) > 0,
				"Chain Snare goes on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void launchPadFlingsWhateverStepsOnIt(GameTestHelper helper) {
		ServerPlayer player = lantern(helper);
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1.0, 3.5));
		HardLightConstructEntity pad = HardLightConstructEntity.create(helper.getLevel(), HardLightConstructEntity.Shape.LAUNCH_PAD,
				player.getUUID(), at, 1.0f, 200);
		helper.getLevel().addFreshEntity(pad);
		Husk h = EntityType.HUSK.create(helper.getLevel());
		h.moveTo(at.x, at.y + 0.2, at.z, 0f, 0f);
		helper.getLevel().addFreshEntity(h); // not NoAI -- a NoAI mob ignores every push in vanilla
		double startY = h.getY();
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(h.getY() > startY + 2.0, "the pad should have flung the husk up, it is at " + (h.getY() - startY));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gatlingSpinsWhileShiftXIsHeld(GameTestHelper helper) {
		ServerPlayer player = lantern(helper);
		player.setShiftKeyDown(true);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_3, true);
		helper.assertTrue(GreenLanternConstructAttacks.isGatling(player), "Shift+X should spin up the Gatling");
		helper.assertFalse(player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L) > 0L,
				"Shift+X must not start the Oath");
		float before = GreenLantern.state(player).ringCharge;
		helper.runAfterDelay(GreenLanternConfig.GATLING_SPINUP_TICKS + 10, () -> {
			helper.assertTrue(GreenLantern.state(player).ringCharge < before, "the spinning Gatling should be firing");
			GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_3, false);
			helper.assertFalse(GreenLanternConstructAttacks.isGatling(player), "letting go of X should stop it");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void greenLanternIsStronger(GameTestHelper helper) {
		helper.assertTrue(GreenLanternConfig.RING_RESISTANCE_AMPLIFIER == 1, "the bond now grants Resistance II");
		helper.assertTrue(GreenLanternConfig.SUIT_MELEE_BONUS == 10f, "the suit adds +10 melee");
		helper.assertTrue(GreenLanternConfig.SHIELD_HP == 140f && GreenLanternConfig.DOME_HP == 400f, "tougher shield and dome");
		helper.assertTrue(GreenLanternConfig.OATH_MODE_DURATION_TICKS == 600, "the Oath lasts 30 s");
		helper.assertTrue(ConstructType.values().length == 19, "five new constructs join the wheel");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Hulk

	@GameTest(template = EMPTY_STRUCTURE)
	public void aRampagingHulkNoLongerSparesHisSquad(GameTestHelper helper) {
		ServerPlayer hulk = helper.makeMockServerPlayerInLevel();
		ServerPlayer mate = helper.makeMockServerPlayerInLevel();
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("v0143test" + hulk.getUUID().toString().substring(0, 6), hulk.getUUID());
		squads.addMember(squad, mate.getUUID());
		helper.assertTrue(Squads.shields(hulk, mate), "squad-mates are normally protected");
		HulkState s = hulk.getAttachedOrCreate(ModAttachments.HULK_STATE).copy();
		s.hasPower = true;
		s.hulk = true;
		s.combat.rampageUntil = helper.getLevel().getGameTime() + 200;
		hulk.setAttached(ModAttachments.HULK_STATE, s);
		helper.assertFalse(Squads.shields(hulk, mate), "a rampaging Hulk hits his squad-mates");
		// v0.14.4: and his squad can fight back (V0144GraveHulkGameTests covers the rest of the rule)
		helper.assertFalse(Squads.shields(mate, hulk), "the squad-mate can hurt a rampaging Hulk back");
		squads.disband(squad);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Moon Knight

	@GameTest(template = EMPTY_STRUCTURE)
	public void moonKnightDashFollowsTheAim(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setXRot(-60f);
		player.setYRot(0f);
		MoonKnightDash.dash(player);
		helper.assertTrue(player.getDeltaMovement().y > 1.0, "aiming up should dash up, got " + player.getDeltaMovement());
		helper.assertTrue(MoonKnightConfig.GRAPPLE_RANGE == 100.0 && MoonKnightConfig.GLIDE_SPEED > 0.55
				&& MoonKnightConfig.SUIT_JUMP_BONUS > 0.1, "grapple 100, faster glide, higher jump");
		helper.succeed();
	}
}

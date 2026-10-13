package com.projecthero.mod.gametest;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.mixin.LivingEntityAccessor;
import com.projecthero.mod.power.ThorConfig;
import com.projecthero.mod.power.ThorStorm;
import com.projecthero.mod.power.ThorTwinThunder;
import com.projecthero.mod.power.ThunderField;
import com.projecthero.mod.power.WeaponCombo;
import com.projecthero.mod.power.WeaponComboState;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1 Thor: the thunderstorm buff ({@link ThorStorm}: +30% only while it thunders, applied once), the Mjolnir +
 * Stormbreaker dual-wield combo ({@link WeaponCombo}: 4 steps, window, reset), its Twin Thunder Strike finisher
 * ({@link ThorTwinThunder}: fires on the 4th hit in the window and not otherwise, never hurts allies) and Thunder Field
 * ({@link ThunderField}: shocks an enemy but not an ally, then expires; the Shift+G throw pins the weapon and forms a
 * field). Every test has its own batch, high above the structure, so its area hits never meet another suite's mobs.
 * The weather is set and restored inside one tick (nothing else runs in between), so no other test ever sees the storm.
 */
public class ThorV0161GameTests implements FabricGameTest {
	private static final int ALT = 40;

	private static Vec3 at(GameTestHelper helper, double x, double z) {
		return helper.absoluteVec(new Vec3(x, 2.0 + ALT, z));
	}

	private static ServerPlayer thor(GameTestHelper helper, boolean dual) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 pos = at(helper, 1.5, 1.5);
		p.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f); // facing +Z
		p.setNoGravity(true);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		ItemStack stack = new ItemStack(ModItems.MJOLNIR);
		p.getInventory().selected = 0;
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		if (dual) {
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.STORMBREAKER));
		}
		// a mock player is never ticked, so vanilla never applies the held item's modifiers
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> p.getAttribute(attr).addOrUpdateTransientModifier(mod));
		return p;
	}

	private static Zombie dummy(GameTestHelper helper, double x, double z) {
		// a Husk (a Zombie that never burns): the test structures sit under the open daytime sky, and a burning zombie's
		// fire damage looked like the field "still shocking" after it had expired
		Zombie z0 = EntityType.HUSK.create(helper.getLevel());
		Vec3 pos = at(helper, x, z);
		z0.moveTo(pos.x, pos.y, pos.z, 180.0f, 0.0f);
		z0.setNoAi(true);
		z0.setNoGravity(true);
		z0.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		z0.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		z0.getAttribute(Attributes.ARMOR).setBaseValue(0.0); // so damage ratios are exact
		z0.setHealth(500.0f);
		helper.getLevel().addFreshEntity(z0);
		return z0;
	}

	private static Wolf pet(GameTestHelper helper, ServerPlayer owner, double x, double z) {
		Wolf w = EntityType.WOLF.create(helper.getLevel());
		Vec3 pos = at(helper, x, z);
		w.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
		w.setNoAi(true);
		w.setNoGravity(true);
		w.tame(owner);
		helper.getLevel().addFreshEntity(w);
		return w;
	}

	private static float swing(ServerPlayer p, LivingEntity target, boolean strong) {
		((LivingEntityAccessor) p).projecthero$setAttackStrengthTicker(strong ? 100 : 1);
		target.invulnerableTime = 0;
		float before = target.getHealth();
		p.attack(target);
		return before - target.getHealth();
	}

	private static float hurt(ServerPlayer p, LivingEntity target, float amount) {
		target.invulnerableTime = 0;
		float before = target.getHealth();
		AbilityHelpers.hurt(p, target, amount);
		return before - target.getHealth();
	}

	private static void cleanUp(GameTestHelper helper, ServerPlayer p, Mob... mobs) {
		for (Mob m : mobs) {
			m.discard();
		}
		if (!p.isRemoved()) {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static void removePlayer(GameTestHelper helper, ServerPlayer p) {
		if (!p.isRemoved()) {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	// ---------------- the thunderstorm buff ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0161_storm")
	public void stormBuffAppliesOnlyWhileThunderingAndOnlyOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = thor(helper, false);
		ServerPlayer mortal = helper.makeMockServerPlayerInLevel();
		mortal.setGameMode(GameType.SURVIVAL);
		Zombie z = dummy(helper, 1.5, 3.5);
		float bonus = (float) ThorConfig.get().storm.damageBonus;

		helper.assertFalse(ThorStorm.isCharged(p), "no storm, no charge");
		float calmAbility = hurt(p, z, 10.0f);
		float calmMelee = swing(p, z, true);
		float mortalCalm = hurt(mortal, z, 10.0f);
		helper.assertTrue(Math.abs(calmAbility - 10.0f) < 0.01f, "calm: an ability hit is its own 10, got " + calmAbility);

		float rain = level.getRainLevel(1.0f);
		float thunder = level.getThunderLevel(1.0f) / Math.max(1.0e-4f, rain);
		float stormAbility;
		float stormMelee;
		float mortalStorm;
		float finisherRatio;
		boolean charged;
		try {
			level.setRainLevel(1.0f);
			level.setThunderLevel(1.0f);
			charged = ThorStorm.isCharged(p);
			stormAbility = hurt(p, z, 10.0f);
			WeaponCombo.setForTests(p, 0, WeaponComboState.WEAPON_NONE, 0);
			stormMelee = swing(p, z, true);
			swing(p, z, true);
			float fin = swing(p, z, true); // the Mjolnir finisher re-hurts with +50%: the buff must not stack on it
			finisherRatio = fin / stormMelee;
			mortalStorm = hurt(mortal, z, 10.0f);
		} finally {
			level.setRainLevel(rain);
			level.setThunderLevel(Math.min(1.0f, thunder));
		}
		helper.assertTrue(charged, "thundering in a rainy biome: Thor is storm-charged");
		helper.assertTrue(Math.abs(stormAbility - 10.0f * (1.0f + bonus)) < 0.05f,
				"storm: an ability hit is +30% exactly once (13), got " + stormAbility);
		helper.assertTrue(Math.abs(stormMelee - calmMelee * (1.0f + bonus)) < 0.1f,
				"storm: a melee hit is +30% exactly once: " + stormMelee + " vs calm " + calmMelee);
		helper.assertTrue(finisherRatio > 1.45f && finisherRatio < 1.55f,
				"the finisher stays +50% of the (buffed) hit, never buffed twice: ratio " + finisherRatio);
		helper.assertTrue(Math.abs(mortalStorm - mortalCalm) < 0.01f, "a non-Thor's hits are never buffed");
		helper.assertFalse(ThorStorm.isCharged(p), "weather restored: no charge");
		helper.assertTrue(Math.abs(hurt(p, z, 10.0f) - 10.0f) < 0.01f, "and the bonus is gone again");
		removePlayer(helper, mortal);
		cleanUp(helper, p, z);
		helper.succeed();
	}

	// ---------------- the dual-wield combo ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0161_dual_steps")
	public void dualComboAdvancesFourStepsAndResets(GameTestHelper helper) {
		ServerPlayer p = thor(helper, true);
		Zombie z = dummy(helper, 1.5, 3.5);
		helper.assertTrue(WeaponCombo.isDualWielding(p), "Mjolnir + Stormbreaker in hand");
		helper.assertTrue(WeaponCombo.comboWeapon(p) == WeaponComboState.WEAPON_DUAL, "the dual combo");
		float first = swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 1, "step 1");
		for (int expected : new int[] { 2, 3, 4 }) {
			float d = swing(p, z, true);
			helper.assertTrue(Math.abs(d - first) < 0.01f, "every dual hit is the plain weapon hit: " + d + " vs " + first);
			helper.assertTrue(WeaponCombo.step(p) == expected, "step " + expected + ", got " + WeaponCombo.step(p));
		}
		helper.assertTrue(WeaponCombo.state(p).weapon() == WeaponComboState.WEAPON_DUAL, "the synced state says dual");
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 1, "after the 4th it starts over");
		// a weak hit neither advances nor resets
		swing(p, z, false);
		helper.assertTrue(WeaponCombo.step(p) == 1, "a weak hit leaves it");
		// the window
		int window = WeaponCombo.windowTicks(p);
		WeaponCombo.setForTests(p, 2, WeaponComboState.WEAPON_DUAL, window - 1);
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 3, "inside the window it continues");
		WeaponCombo.setForTests(p, 2, WeaponComboState.WEAPON_DUAL, window + 1);
		helper.assertTrue(WeaponCombo.step(p) == 0, "past the window it is dropped");
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 1, "and the next hit starts over");
		// putting Stormbreaker away: back to Mjolnir's own 3-hit combo
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.state(p).weapon() == WeaponComboState.WEAPON_MJOLNIR && WeaponCombo.step(p) == 1,
				"one weapon: the single-weapon combo, from the top");
		cleanUp(helper, p, z);
		helper.succeed();
	}

	// ---------------- the Twin Thunder Strike ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0161_twin_gate")
	public void twinThunderOnlyOnTheFourthHitInTheWindow(GameTestHelper helper) {
		ServerPlayer p = thor(helper, true);
		Zombie z = dummy(helper, 1.5, 3.5);
		swing(p, z, true);
		swing(p, z, true);
		swing(p, z, true);
		helper.assertFalse(ThorTwinThunder.isActive(p), "3 hits: no finisher");
		// the 4th hit after the window has closed is a fresh step 1, not the finisher
		WeaponCombo.setForTests(p, 3, WeaponComboState.WEAPON_DUAL, WeaponCombo.windowTicks(p) + 2);
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 1, "late: step 1");
		helper.assertFalse(ThorTwinThunder.isActive(p), "a late 4th hit is no finisher");
		// with only Mjolnir in hand the 3-hit combo never queues it either
		ServerPlayer single = thor(helper, false);
		for (int i = 0; i < 4; i++) {
			swing(single, z, true);
		}
		helper.assertFalse(ThorTwinThunder.isActive(single), "one weapon: never");
		// and inside the window it fires
		swing(p, z, true);
		swing(p, z, true);
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 4, "the 4th hit in the window");
		helper.assertTrue(ThorTwinThunder.isActive(p), "queues the Twin Thunder Strike");
		removePlayer(helper, single);
		cleanUp(helper, p, z);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0161_twin_allies", timeoutTicks = 200)
	public void twinThunderHitsEnemiesButNeverAllies(GameTestHelper helper) {
		ServerPlayer p = thor(helper, true);
		ServerPlayer ally = helper.makeMockServerPlayerInLevel();
		ally.setGameMode(GameType.SURVIVAL);
		Vec3 allyAt = at(helper, 3.5, 2.5);
		ally.moveTo(allyAt.x, allyAt.y, allyAt.z, 0f, 0f);
		ally.setNoGravity(true);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("TwinTestSquad" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, ally.getUUID());
		Zombie z = dummy(helper, 1.5, 3.0);
		Zombie bystander = dummy(helper, -1.5, 3.0);
		Wolf wolf = pet(helper, p, 0.0, 0.0);
		float allyHealth = ally.getHealth();
		float wolfHealth = wolf.getHealth();
		for (int i = 0; i < 4; i++) {
			swing(p, z, true);
		}
		helper.assertTrue(ThorTwinThunder.isActive(p), "the finisher is queued");
		float bystanderBefore = bystander.getHealth();
		int done = ThorTwinThunder.DELAY + ThorTwinThunder.RING_TICK + 4;
		helper.runAfterDelay(done, () -> {
			float dealt = bystanderBefore - bystander.getHealth();
			helper.assertTrue(dealt >= (float) ThorConfig.get().twinThunder.damage - 0.5f,
					"the slam hits an enemy within 6 blocks for 20, got " + dealt);
			helper.assertTrue(ally.getHealth() >= allyHealth, "never a squad ally");
			helper.assertTrue(wolf.getHealth() >= wolfHealth, "never his own pet");
			squads.disband(squad);
			removePlayer(helper, ally);
			wolf.discard();
			cleanUp(helper, p, z, bystander);
			helper.succeed();
		});
	}

	// ---------------- Thunder Field ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0161_field", timeoutTicks = 300)
	public void thunderFieldShocksEnemiesNotAlliesThenExpires(GameTestHelper helper) {
		ServerPlayer p = thor(helper, false);
		Vec3 center = at(helper, 1.5, 8.5);
		Zombie z = dummy(helper, 3.0, 8.5);
		Wolf wolf = pet(helper, p, 0.5, 8.5);
		ServerPlayer ally = helper.makeMockServerPlayerInLevel();
		ally.setGameMode(GameType.SURVIVAL);
		Vec3 allyAt = at(helper, 1.5, 10.0);
		ally.moveTo(allyAt.x, allyAt.y, allyAt.z, 0f, 0f);
		ally.setNoGravity(true);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("FieldTestSquad" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, ally.getUUID());
		float zBefore = z.getHealth();
		float wolfHealth = wolf.getHealth();
		float allyHealth = ally.getHealth();
		ThunderField.spawnField(p, helper.getLevel(), center);
		helper.assertTrue(ThunderField.hasField(p), "the field is live");
		int duration = ThorConfig.get().thunderField.durationTicks;
		int pulse = ThorConfig.get().thunderField.pulseTicks;
		helper.runAfterDelay(pulse * 2 + 2, () -> {
			helper.assertTrue(z.getHealth() < zBefore, "a zombie standing in it is shocked");
			helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
			helper.assertTrue(wolf.getHealth() >= wolfHealth, "his pet is never touched");
			helper.assertTrue(ally.getHealth() >= allyHealth, "nor a squad ally");
		});
		float[] atEnd = new float[1];
		helper.runAfterDelay(duration + 3, () -> {
			helper.assertFalse(ThunderField.hasField(p), "after 4 s it is gone");
			atEnd[0] = z.getHealth();
		});
		helper.runAfterDelay(duration + 3 + pulse * 3, () -> {
			helper.assertTrue(z.getHealth() >= atEnd[0], "and it shocks no more");
			squads.disband(squad);
			removePlayer(helper, ally);
			wolf.discard();
			cleanUp(helper, p, z);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0161_field_throw", timeoutTicks = 400)
	public void shiftGThrowsTheWeaponWhichPinsAFieldThenComesHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = thor(helper, false);
		// a stone floor 3 blocks under him, in front
		BlockPos base = BlockPos.containing(at(helper, 1.5, 1.5)).below(3);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = 0; dz <= 10; dz++) {
				level.setBlockAndUpdate(base.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
			}
		}
		p.setXRot(35.0f); // looking down and ahead at the floor
		p.setYHeadRot(0.0f);
		ThunderField.cast(p);
		helper.assertTrue(p.getMainHandItem().isEmpty(), "Mjolnir has left his hand");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(ThunderField.hasField(p), "it landed and formed a field");
			var hammers = level.getEntitiesOfClass(MjolnirEntity.class, new AABB(base).inflate(12.0));
			helper.assertTrue(!hammers.isEmpty() && hammers.get(0).isPinned(), "the hammer stays slammed into the ground");
		});
		int back = 20 + ThorConfig.get().thunderField.durationTicks + 40;
		helper.runAfterDelay(back, () -> {
			helper.assertTrue(p.getInventory().hasAnyMatching(s -> s.is(ModItems.MJOLNIR)), "then it flies home");
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = 0; dz <= 10; dz++) {
					level.setBlockAndUpdate(base.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
				}
			}
			cleanUp(helper, p);
			helper.succeed();
		});
	}
}

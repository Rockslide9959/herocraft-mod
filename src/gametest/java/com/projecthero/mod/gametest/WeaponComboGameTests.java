package com.projecthero.mod.gametest;

import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.mixin.LivingEntityAccessor;
import com.projecthero.mod.power.WeaponCombo;
import com.projecthero.mod.power.WeaponComboState;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.20: the Mjolnir / Stormbreaker 3-hit melee combo ({@link WeaponCombo}). Hits go through the real
 * {@code Player.attack} (so the attack-strength read and the landed-hit check in {@code PlayerAttackComboMixin} are
 * covered); a mock player is never ticked, so its held weapon's attribute modifiers are applied by hand and the swing
 * charge is set through {@link LivingEntityAccessor}. Every test runs in its own batch, well above the structure, so
 * the finisher's area hit can't meet another suite's mobs or players.
 */
public class WeaponComboGameTests implements FabricGameTest {
	private static final String BATCH = "weapon_combo_v01420";
	private static final int ALT = 30;

	private static Vec3 at(GameTestHelper helper, double x, double z) {
		return helper.absoluteVec(new Vec3(x, 2.0 + ALT, z));
	}

	private static ServerPlayer wielder(GameTestHelper helper, Item weapon) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 pos = at(helper, 1.5, 1.5);
		p.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f); // facing +Z
		p.setNoGravity(true);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		ItemStack stack = new ItemStack(weapon);
		p.getInventory().selected = 0;
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		// a mock player is never ticked, so vanilla never applies the held item's modifiers
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> p.getAttribute(attr).addOrUpdateTransientModifier(mod));
		return p;
	}

	private static Zombie dummy(GameTestHelper helper, double x, double z) {
		Zombie z0 = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 pos = at(helper, x, z);
		z0.moveTo(pos.x, pos.y, pos.z, 180.0f, 0.0f);
		z0.setNoAi(true);
		z0.setNoGravity(true);
		z0.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		z0.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		z0.setHealth(500.0f);
		helper.getLevel().addFreshEntity(z0);
		return z0;
	}

	/** One melee attack at full ({@code strong}) or barely-charged strength; returns the damage the target took. */
	private static float swing(ServerPlayer p, LivingEntity target, boolean strong) {
		((LivingEntityAccessor) p).projecthero$setAttackStrengthTicker(strong ? 100 : 1);
		target.invulnerableTime = 0;
		float before = target.getHealth();
		p.attack(target);
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

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void comboAdvancesOneTwoThreeOnFullStrengthHits(GameTestHelper helper) {
		ServerPlayer p = wielder(helper, ModItems.MJOLNIR);
		Zombie z = dummy(helper, 1.5, 3.5);
		helper.assertTrue(WeaponCombo.step(p) == 0, "no combo yet");
		for (int expected : new int[] { 1, 2, 3, 1 }) {
			helper.assertTrue(swing(p, z, true) > 0.0f, "the hit lands");
			helper.assertTrue(WeaponCombo.step(p) == expected, "step " + expected + ", got " + WeaponCombo.step(p));
		}
		WeaponComboState s = WeaponCombo.state(p);
		helper.assertTrue(s.weapon() == WeaponComboState.WEAPON_MJOLNIR, "the synced state names Mjolnir");
		cleanUp(helper, p, z);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void weakHitsNeitherAdvanceNorFinish(GameTestHelper helper) {
		ServerPlayer p = wielder(helper, ModItems.MJOLNIR);
		Zombie z = dummy(helper, 1.5, 3.5);
		helper.assertTrue(swing(p, z, false) > 0.0f, "a weak hit still lands");
		helper.assertTrue(WeaponCombo.step(p) == 0, "but doesn't start the combo");
		swing(p, z, true);
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 2, "two full hits: step 2");
		for (int i = 0; i < 4; i++) {
			swing(p, z, false);
		}
		helper.assertTrue(WeaponCombo.step(p) == 2, "spam-clicked weak hits leave it at step 2, got " + WeaponCombo.step(p));
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 3, "the next full hit is the finisher");
		cleanUp(helper, p, z);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void comboResetsAfterTheWindow(GameTestHelper helper) {
		ServerPlayer p = wielder(helper, ModItems.MJOLNIR);
		Zombie z = dummy(helper, 1.5, 3.5);
		int window = WeaponCombo.windowTicks(p);
		helper.assertTrue(window > WeaponCombo.COMBO_WINDOW_TICKS && window < WeaponCombo.COMBO_WINDOW_TICKS + 25,
				"window = Mjolnir's swing recharge + 1.25 s, got " + window);
		WeaponCombo.setForTests(p, 2, WeaponComboState.WEAPON_MJOLNIR, window - 1);
		helper.assertTrue(WeaponCombo.step(p) == 2, "inside the window the combo stands");
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 3, "and a hit continues it");
		WeaponCombo.setForTests(p, 2, WeaponComboState.WEAPON_MJOLNIR, window + 1);
		helper.assertTrue(WeaponCombo.step(p) == 0, "past the window it is dropped");
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 1, "so the next hit starts over, got " + WeaponCombo.step(p));
		// switching weapons starts over too
		WeaponCombo.setForTests(p, 1, WeaponComboState.WEAPON_STORMBREAKER, 2);
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 1, "a Mjolnir hit after a Stormbreaker step starts over");
		cleanUp(helper, p, z);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void mjolnirFinisherDealsTheBonusAndAShockwave(GameTestHelper helper) {
		ServerPlayer p = wielder(helper, ModItems.MJOLNIR);
		Zombie z = dummy(helper, 1.5, 3.5);
		Zombie bystander = dummy(helper, 3.0, 4.5);
		Wolf pet = EntityType.WOLF.create(helper.getLevel());
		Vec3 petAt = at(helper, 0.0, 4.5);
		pet.moveTo(petAt.x, petAt.y, petAt.z, 0f, 0f);
		pet.setNoAi(true);
		pet.setNoGravity(true);
		pet.tame(p);
		helper.getLevel().addFreshEntity(pet);
		float petHealth = pet.getHealth();

		float first = swing(p, z, true);
		float second = swing(p, z, true);
		helper.assertTrue(Math.abs(first - second) < 0.01f, "hits 1 and 2 are plain hits: " + first + " / " + second);
		float bystanderBefore = bystander.getHealth();
		float finisher = swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 3, "that was the finisher");
		helper.assertTrue(finisher >= first * 1.45f && finisher <= first * 1.55f,
				"the finisher deals +50%: " + finisher + " vs " + first);
		helper.assertTrue(bystander.getHealth() < bystanderBefore, "the shockwave hits a mob next to the target");
		helper.assertTrue(pet.getHealth() >= petHealth, "but never the wielder's own pet");
		cleanUp(helper, p, z, bystander, pet);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void stormbreakerFinisherCleavesInFrontOnly(GameTestHelper helper) {
		ServerPlayer p = wielder(helper, ModItems.STORMBREAKER);
		ServerPlayer ally = helper.makeMockServerPlayerInLevel();
		ally.setGameMode(GameType.SURVIVAL);
		Vec3 allyAt = at(helper, 3.5, 3.0);
		ally.moveTo(allyAt.x, allyAt.y, allyAt.z, 0f, 0f);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("ComboTestSquad" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, ally.getUUID());
		Zombie z = dummy(helper, 1.5, 3.0);
		Zombie inFront = dummy(helper, -0.5, 4.0);
		Zombie behind = dummy(helper, 1.5, -1.5);
		float allyHealth = ally.getHealth();

		float first = swing(p, z, true);
		swing(p, z, true);
		float frontBefore = inFront.getHealth();
		float behindBefore = behind.getHealth();
		float finisher = swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 3, "that was the finisher");
		helper.assertTrue(finisher >= first * 1.45f && finisher <= first * 1.55f,
				"the finisher deals +50%: " + finisher + " vs " + first);
		helper.assertTrue(inFront.getHealth() < frontBefore, "the cleave cuts a mob in front");
		helper.assertTrue(behind.getHealth() >= behindBefore, "but not one behind the wielder");
		helper.assertTrue(ally.getHealth() >= allyHealth, "and never a squad ally");
		squads.disband(squad);
		if (!ally.isRemoved()) {
			helper.getLevel().getServer().getPlayerList().remove(ally);
		}
		cleanUp(helper, p, z, inFront, behind);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void otherWeaponsHaveNoCombo(GameTestHelper helper) {
		ServerPlayer p = wielder(helper, net.minecraft.world.item.Items.IRON_AXE);
		Zombie z = dummy(helper, 1.5, 3.5);
		swing(p, z, true);
		swing(p, z, true);
		helper.assertTrue(WeaponCombo.step(p) == 0, "an iron axe has no combo");
		cleanUp(helper, p, z);
		helper.succeed();
	}
}

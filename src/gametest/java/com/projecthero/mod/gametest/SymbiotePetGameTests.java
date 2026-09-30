package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteHost;
import com.projecthero.mod.symbiote.SymbioteMobGoals;
import com.projecthero.mod.symbiote.SymbiotePet;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4: the Symbiote takes over passive animals (which turn hostile) and tamed wolves / cats (which become loyal,
 * buffed, regenerating Symbiote Pets).
 */
public class SymbiotePetGameTests implements FabricGameTest {

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static ServerPlayer player(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static Wolf petWolf(GameTestHelper helper, ServerPlayer owner, double x, double z) {
		Wolf w = EntityType.WOLF.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		w.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		w.tame(owner);
		helper.getLevel().addFreshEntity(w);
		return w;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void passiveAnimalsAndTamedPetsAreValidHosts(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = player(helper, 1.5, 1.5);
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
		Cow calf = helper.spawn(EntityType.COW, new BlockPos(5, 1, 3));
		calf.setAge(-24000);
		Wolf pet = petWolf(helper, p, 3.5, 5.5);
		Parrot parrot = helper.spawn(EntityType.PARROT, new BlockPos(5, 1, 5));
		parrot.tame(p);
		helper.assertTrue(SymbioteEntity.isValidHost(cow), "a grown cow can be taken over now");
		helper.assertFalse(SymbioteEntity.isValidHost(calf), "a calf cannot");
		helper.assertTrue(SymbioteEntity.isValidHost(pet), "a tamed wolf can (it becomes a Symbiote Pet)");
		helper.assertFalse(SymbioteEntity.isValidHost(parrot), "other owned animals stay off-limits");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void aFreeSymbioteInfestsACow(GameTestHelper helper) {
		floor(helper, 6);
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 1, 2));
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		SymbioteEntity blob = SymbioteEntity.spawn(helper.getLevel(), at.x, at.y, at.z);
		helper.assertTrue(blob != null, "the Symbiote spawns");
		float baseHealth = cow.getMaxHealth();

		helper.succeedWhen(() -> {
			helper.assertTrue(SymbioteHost.is(cow), "the cow becomes a Symbiote Host");
			helper.assertTrue(blob.isRemoved(), "the free Symbiote is consumed");
			helper.assertFalse(SymbiotePet.is(cow), "an untamed animal is no pet");
			helper.assertTrue(SymbioteMobGoals.installed(cow), "its AI is rewritten to fight");
			helper.assertTrue(cow.getMaxHealth() > baseHealth, "and it is tougher");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void anInfestedCowHuntsPlayers(GameTestHelper helper) {
		floor(helper, 8);
		ServerPlayer p = player(helper, 6.5, 6.5);
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 1, 1));
		Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(1, 1, 6));
		helper.assertTrue(cow.getTarget() == null, "a normal cow hunts nobody");
		SymbioteHost.takeOver(cow);
		SymbioteHost.takeOver(sheep);
		helper.assertTrue(sheep.getColor() == DyeColor.BLACK, "an infested sheep's wool turns black");
		// idempotent: re-running the load hook must not stack a second set of goals
		SymbioteMobGoals.onLoad(cow);
		helper.succeedWhen(() -> helper.assertTrue(cow.getTarget() == p, "the infested cow goes for the player"));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aTamedWolfBecomesALoyalSymbiotePet(GameTestHelper helper) {
		floor(helper, 8);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		Wolf wolf = petWolf(helper, owner, 3.5, 3.5);
		Wolf otherPet = petWolf(helper, owner, 5.5, 3.5);
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 1, 6));
		husk.setNoAi(true);
		float baseMax = wolf.getMaxHealth();
		double baseDamage = wolf.getAttributeValue(Attributes.ATTACK_DAMAGE);

		SymbioteHost.takeOver(wolf); // what a free Symbiote reaching it does
		helper.assertTrue(SymbiotePet.is(wolf), "a tamed wolf becomes a Symbiote Pet");
		helper.assertTrue(SymbiotePet.origin(wolf) == SymbiotePet.WILD, "from a wild Symbiote");
		helper.assertFalse(SymbioteHost.is(wolf), "not a hostile host");
		helper.assertTrue(wolf.isTame() && wolf.isOwnedBy(owner), "and it stays yours");
		helper.assertTrue(Math.abs(wolf.getMaxHealth() - (baseMax + 20.0f)) < 0.01f, "+20 max health, got " + wolf.getMaxHealth());
		helper.assertTrue(Math.abs(wolf.getAttributeValue(Attributes.ATTACK_DAMAGE) - (baseDamage + 4.0)) < 0.01, "+4 attack");
		helper.assertTrue(SymbiotePet.isTransformed(wolf), "bonding announces itself with a transform");
		helper.assertTrue(wolf.getAttributeValue(Attributes.ARMOR) >= 8.0, "armoured");

		// loyalty: never its owner, never its owner's other pets -- but a hostile mob, yes
		wolf.setTarget(owner);
		helper.assertTrue(wolf.getTarget() == null, "it refuses to target its owner");
		wolf.setTarget(otherPet);
		helper.assertTrue(wolf.getTarget() == null, "or its owner's other pet");
		float petHealth = otherPet.getHealth();
		helper.assertFalse(otherPet.hurt(wolf.damageSources().mobAttack(wolf), 4.0f), "its hits on a friendly pet are vetoed");
		helper.assertTrue(otherPet.getHealth() == petHealth, "and do no damage");
		wolf.setTarget(husk);
		helper.assertTrue(wolf.getTarget() == husk, "a hostile mob is fair game");
		wolf.setTarget(null);
		wolf.setOrderedToSit(true); // keep it still while it heals

		wolf.setHealth(20.0f);
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(wolf.getHealth() > 20.5f, "it regenerates, health now " + wolf.getHealth());
			helper.assertTrue(Math.abs(wolf.getAttributeValue(Attributes.SCALE) - 1.3) < 0.01, "30% bigger once the transform has eased in");
			SymbiotePet.release(wolf, false);
			helper.assertFalse(SymbiotePet.is(wolf), "released");
			helper.assertTrue(Math.abs(wolf.getMaxHealth() - baseMax) < 0.01f, "its buffs go with the Symbiote");
			helper.assertTrue(Math.abs(wolf.getAttributeValue(Attributes.SCALE) - 1.0) < 0.01, "back to its own size");
			husk.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aSuitedOwnerSharesAndRecallsTheSymbiote(GameTestHelper helper) {
		floor(helper, 6);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		Wolf wolf = petWolf(helper, owner, 3.5, 3.5);
		helper.assertTrue(Symbiote.grant(owner, false), "the owner bonds");
		owner.setShiftKeyDown(true);

		// not suited: sneak-clicking your wolf is vanilla (sit), the Symbiote stays put
		UseEntityCallback.EVENT.invoker().interact(owner, helper.getLevel(), InteractionHand.MAIN_HAND, wolf, null);
		helper.assertFalse(SymbiotePet.is(wolf), "no sharing without the suit on");

		SymbioteState suited = Symbiote.state(owner).copy();
		suited.active = true;
		owner.setAttached(ModAttachments.SYMBIOTE_STATE, suited);
		float before = SymbioteVitalsManager.vitals(owner).hp;
		UseEntityCallback.EVENT.invoker().interact(owner, helper.getLevel(), InteractionHand.MAIN_HAND, wolf, null);
		helper.assertTrue(SymbiotePet.is(wolf), "suited, sneak + empty hand shares the Symbiote");
		helper.assertTrue(SymbiotePet.origin(wolf) == SymbiotePet.SHARED, "a shared Symbiote");
		helper.assertTrue(Math.abs(before - SymbioteVitalsManager.vitals(owner).hp - SymbiotePet.SHARE_BIOMASS_COST) < 0.01f,
				"a Normal host pays Biomass for it");
		// the same click reaching the server twice (interactAt + interact) must not undo it
		UseEntityCallback.EVENT.invoker().interact(owner, helper.getLevel(), InteractionHand.MAIN_HAND, wolf, null);
		helper.assertTrue(SymbiotePet.is(wolf), "one click, one toggle");

		helper.runAtTickTime(20, () -> {
			owner.setAttached(ModAttachments.SYMBIOTE_STATE, suited);
			owner.setShiftKeyDown(true);
			UseEntityCallback.EVENT.invoker().interact(owner, helper.getLevel(), InteractionHand.MAIN_HAND, wolf, null);
			helper.assertFalse(SymbiotePet.is(wolf), "doing it again calls the Symbiote back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void tamingAnInfestedWolfMakesItAPet(GameTestHelper helper) {
		floor(helper, 6);
		ServerPlayer p = player(helper, 1.5, 1.5);
		Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(4, 1, 4));
		SymbioteHost.takeOver(wolf);
		helper.assertTrue(SymbioteHost.is(wolf) && !SymbiotePet.is(wolf), "a wild wolf becomes a hostile host");
		wolf.tame(p);
		helper.succeedWhen(() -> {
			helper.assertTrue(SymbiotePet.is(wolf), "once tamed, the Symbiote takes its owner's side");
			helper.assertFalse(SymbioteHost.is(wolf), "and it is no longer a hostile host");
			helper.assertFalse(SymbioteMobGoals.installed(wolf), "its hunting AI is gone (wolves need no pet goals)");
		});
	}
}

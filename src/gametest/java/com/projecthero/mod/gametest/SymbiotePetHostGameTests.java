package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.SymbioteHost;
import com.projecthero.mod.symbiote.SymbiotePet;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4 pet hosts: a free Symbiote seeks out and bonds with a tamed pet, which stays loyal and obedient; it
 * transforms (buffs on) in combat and detransforms (buffs off) after the combat timeout; a sit order holds.
 */
public class SymbiotePetHostGameTests implements FabricGameTest {

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

	private static <T extends TamableAnimal> T pet(GameTestHelper helper, EntityType<T> type, ServerPlayer owner,
			double x, double z, boolean sit) {
		T pet = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		pet.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		pet.tame(owner);
		if (sit) {
			pet.setOrderedToSit(true);
			pet.setInSittingPose(true);
		}
		helper.getLevel().addFreshEntity(pet);
		return pet;
	}

	private static Husk dummy(GameTestHelper helper, int x, int z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(x, 1, z));
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		return husk;
	}

	private static boolean buffed(TamableAnimal pet, float baseMax, double baseDamage) {
		return Math.abs(pet.getMaxHealth() - (baseMax + 20.0f)) < 0.01f
				&& Math.abs(pet.getAttributeValue(Attributes.ATTACK_DAMAGE) - (baseDamage + 4.0)) < 0.01
				&& pet.getAttributeValue(Attributes.ARMOR) >= 8.0;
	}

	private static boolean plain(TamableAnimal pet, float baseMax, double baseDamage) {
		return Math.abs(pet.getMaxHealth() - baseMax) < 0.01f
				&& Math.abs(pet.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseDamage) < 0.01
				&& pet.getAttributeValue(Attributes.ARMOR) < 8.0;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 240)
	public void aFreeSymbioteSeeksOutAndBondsWithATamedWolf(GameTestHelper helper) {
		floor(helper, 9);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, 6.5, 4.5, true);
		Husk husk = dummy(helper, 4, 2); // closer to the goo than the wolf -- but the goo wants the pet
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 4.5));
		SymbioteEntity blob = SymbioteEntity.spawn(helper.getLevel(), at.x, at.y, at.z);
		helper.assertTrue(blob != null, "the Symbiote spawns");

		helper.succeedWhen(() -> {
			helper.assertTrue(SymbiotePet.is(wolf), "the free Symbiote bonds with the tamed wolf");
			helper.assertTrue(blob.isRemoved(), "and is consumed by the bond");
			helper.assertFalse(SymbioteHost.is(husk), "it went for the pet, not the monster");
			helper.assertFalse(SymbioteHost.is(wolf), "the wolf is a loyal host, not a hostile one");
			helper.assertTrue(wolf.isTame() && wolf.isOwnedBy(owner), "it is still the owner's wolf");
			helper.assertTrue(wolf.isOrderedToSit(), "and still sitting where it was told to");
			wolf.setTarget(owner);
			helper.assertTrue(wolf.getTarget() == null, "it will not target its owner");
			husk.discard();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
	public void aPetHostTransformsInCombatAndCalmsDownAfter(GameTestHelper helper) {
		floor(helper, 9);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, 3.5, 3.5, false);
		float baseMax = wolf.getMaxHealth();
		double baseDamage = wolf.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(SymbiotePet.bond(wolf, SymbiotePet.SHARED), "the pet takes the Symbiote");
		helper.assertTrue(SymbiotePet.isTransformed(wolf), "bonding announces itself with a transform");
		helper.assertTrue(buffed(wolf, baseMax, baseDamage), "transformed = combat buffs on");

		// skip the announce: pretend the last fight ended a whole timeout ago
		wolf.getAttachedOrCreate(ModAttachments.SYMBIOTE_PET_BRAIN).lastCombatAt =
				helper.getLevel().getGameTime() - SymbiotePet.COMBAT_TIMEOUT - 1;

		Husk[] husk = new Husk[1];
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(SymbiotePet.isTransformed(wolf), "out of combat it detransforms"))
				.thenExecute(() -> {
					helper.assertTrue(plain(wolf, baseMax, baseDamage), "and the buffs are gone");
					helper.assertTrue(SymbiotePet.is(wolf) && wolf.isOwnedBy(owner), "the bond itself stays");
				})
				.thenWaitUntil(() -> helper.assertTrue(Math.abs(wolf.getAttributeValue(Attributes.SCALE) - 1.0) < 0.01,
						"the size eases back, scale now " + wolf.getAttributeValue(Attributes.SCALE)))
				.thenExecute(() -> {
					husk[0] = dummy(helper, 7, 7);
					wolf.setTarget(husk[0]);
				})
				.thenWaitUntil(() -> helper.assertTrue(SymbiotePet.isTransformed(wolf), "a target puts it in its combat form"))
				.thenExecute(() -> helper.assertTrue(buffed(wolf, baseMax, baseDamage), "buffs back on"))
				.thenWaitUntil(() -> helper.assertTrue(wolf.getAttributeValue(Attributes.SCALE) > 1.29,
						"it grows as the skin spreads, scale now " + wolf.getAttributeValue(Attributes.SCALE)))
				.thenExecute(() -> {
					husk[0].discard(); // the fight is over
					wolf.setTarget(null);
				})
				.thenWaitUntil(() -> helper.assertFalse(SymbiotePet.isTransformed(wolf), "the combat timeout detransforms it"))
				.thenExecute(() -> helper.assertTrue(plain(wolf, baseMax, baseDamage), "buffs off again"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void aSitOrderHoldsThroughCombat(GameTestHelper helper) {
		floor(helper, 9);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, 2.5, 4.5, true);
		Cat cat = pet(helper, EntityType.CAT, owner, 4.5, 2.5, true);
		Husk husk = dummy(helper, 6, 6);
		SymbiotePet.bond(wolf, SymbiotePet.WILD);
		SymbiotePet.bond(cat, SymbiotePet.SHARED);
		helper.assertTrue(SymbiotePet.is(wolf) && SymbiotePet.is(cat), "both are pet hosts");
		Vec3 wolfAt = wolf.position();
		Vec3 catAt = cat.position();
		float huskHealth = husk.getHealth();
		wolf.setTarget(husk);
		cat.setTarget(husk);

		helper.runAtTickTime(80, () -> {
			helper.assertTrue(wolf.isOrderedToSit() && cat.isOrderedToSit(), "the Symbiote's AI never clears a sit order");
			helper.assertTrue(horizontal(wolf.position(), wolfAt) < 0.5, "the sitting wolf stays put");
			helper.assertTrue(horizontal(cat.position(), catAt) < 0.5, "the sitting cat stays put");
			helper.assertTrue(husk.getHealth() == huskHealth, "and neither lashes, pounces or bites from its seat");
			husk.discard();
			helper.succeed();
		});
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aPreReworkPetKeepsItsBondButLosesItsPermanentBuffs(GameTestHelper helper) {
		floor(helper, 6);
		ServerPlayer owner = player(helper, 1.5, 1.5);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, 3.5, 3.5, true);
		float baseMax = wolf.getMaxHealth();
		double baseDamage = wolf.getAttributeValue(Attributes.ATTACK_DAMAGE);
		// what a pet saved by the first v0.14.4 slice loads as: the pet flag plus permanent modifiers
		wolf.setAttached(ModAttachments.SYMBIOTE_PET, SymbiotePet.SHARED);
		wolf.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(
				ProjectHeroMod.id("symbiote_pet_health"), 20.0, AttributeModifier.Operation.ADD_VALUE));
		wolf.getAttribute(Attributes.ATTACK_DAMAGE).addPermanentModifier(new AttributeModifier(
				ProjectHeroMod.id("symbiote_pet_damage"), 4.0, AttributeModifier.Operation.ADD_VALUE));
		wolf.getAttribute(Attributes.SCALE).addPermanentModifier(new AttributeModifier(
				ProjectHeroMod.id("symbiote_pet_scale"), 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		wolf.setHealth(wolf.getMaxHealth());

		helper.succeedWhen(() -> {
			helper.assertTrue(SymbiotePet.is(wolf) && SymbiotePet.origin(wolf) == SymbiotePet.SHARED, "the bond survives");
			helper.assertFalse(SymbiotePet.isTransformed(wolf), "it loads in its normal form");
			helper.assertTrue(Math.abs(wolf.getMaxHealth() - baseMax) < 0.01f, "the old permanent health bonus is gone");
			helper.assertTrue(Math.abs(wolf.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseDamage) < 0.01, "and the attack bonus");
			helper.assertTrue(Math.abs(wolf.getAttributeValue(Attributes.SCALE) - 1.0) < 0.01, "and the size");
			helper.assertTrue(wolf.getHealth() <= wolf.getMaxHealth(), "health clamped to the normal maximum");
		});
	}
}

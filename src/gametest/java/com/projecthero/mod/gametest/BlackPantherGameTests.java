package com.projecthero.mod.gametest;

import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherAbilities;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Black Panther: the soup and the Ancestral Plane, one Primary, the suit, the Kinetic bar, the deflection and Absorb
 * Stance. Mock players are not ticked by the server, so tests over time drive {@link BlackPanther#tick}.
 */
public class BlackPantherGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	/** Spawn protection off, the suit finished. */
	private static ServerPlayer suited(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		BlackPanther.grant(p);
		BlackPanther.suitUp(p);
		var s = BlackPanther.state(p).copy();
		s.suitChangeAt = p.level().getGameTime() - BlackPantherConfig.SUIT_UP_TICKS - 1;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.BLACK_PANTHER_STATE, s);
		return p;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void soupOpensTheAncestralPlaneThenGrantsThePower(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		ItemStack soup = new ItemStack(BlackPantherItems.SOUP);
		p.setItemInHand(InteractionHand.MAIN_HAND, soup);
		helper.assertTrue(BlackPanther.canDrink(p), "a fresh player can drink");
		ItemStack left = soup.finishUsingItem(p.level(), p);
		helper.assertTrue(left.is(Items.BOWL), "the bowl comes back");
		helper.assertTrue(BlackPanther.inVision(p) && !BlackPanther.hasPower(p), "the vision comes first");
		var s = BlackPanther.state(p).copy();
		s.visionUntil = p.level().getGameTime();
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.BLACK_PANTHER_STATE, s);
		BlackPanther.tick(p);
		helper.assertTrue(BlackPanther.hasPower(p), "the power follows the vision");
		helper.assertTrue(!BlackPanther.suited(p), "the suit stays in the necklace until H");
		helper.assertTrue(!BlackPanther.canDrink(p), "a second soup is refused");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herbPatchGrowsOnGrassAndTheSoupHasARecipe(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
				helper.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.AIR);
			}
		}
		var level = helper.getLevel();
		var feature = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
				.get(com.projecthero.mod.ProjectHeroMod.id("heart_shaped_herb"));
		helper.assertTrue(feature != null, "the configured feature loads");
		helper.assertTrue(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE)
				.containsKey(BlackPantherItems.HERB_FEATURE), "the placed feature loads");
		BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
		for (int i = 0; i < 6; i++) {
			feature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), centre);
		}
		int herbs = 0;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				if (helper.getBlockState(new BlockPos(x, 2, z)).is(BlackPantherItems.HERB_BLOCK)) {
					herbs++;
				}
			}
		}
		helper.assertTrue(herbs > 0, "the patch grows herbs on grass");
		helper.assertTrue(level.getServer().getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("heart_shaped_herb_soup")).isPresent(), "the soup recipe loads");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void takesTheOnePrimarySlot(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		BlackPanther.grant(p);
		helper.assertTrue(BlackPanther.hasPower(p), "granted");
		helper.assertTrue(!Kryptonian.hasPower(p), "the old Primary is replaced");
		helper.assertTrue(HeroTiers.holdsHero(p, BlackPanther.KEY), "listed as the held hero");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void suitGivesTheClawsAndGoesBackIntoTheNecklace(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		BlackPanther.suitDown(p);
		helper.assertTrue(!BlackPanther.suited(p), "H again takes it off");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < base, "the claws go with it");
		helper.assertTrue(BlackPanther.hasPower(p), "the power stays");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHitIsFortyPercentSoakedAndCharged(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
		float before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 10f);
		// ratios, not amounts: another test in the batch may have changed the difficulty, which scales a mob's hit
		float lost = before - p.getHealth();
		float soaked = lost / 0.6f * 0.4f;
		helper.assertTrue(lost > 0f && lost < 10f, "a hit is soaked, lost " + lost);
		helper.assertTrue(Math.abs(BlackPanther.kinetic(p) - soaked * 4f) < 0.6f, "4 Kinetic per soaked point, got " + BlackPanther.kinetic(p));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fallDoesNothingInTheSuit(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		float before = p.getHealth();
		p.hurt(p.damageSources().fall(), 12f);
		helper.assertTrue(p.getHealth() == before, "no fall damage");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void absorbStanceTakesFrontalHitsAndBreaksAtForty(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4)); // yaw 0 faces south, +z
		BlackPantherAbilities.raiseStance(p);
		helper.assertTrue(BlackPanther.absorbing(p), "raised");
		float before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 5f);
		helper.assertTrue(p.getHealth() == before, "a frontal hit does nothing");
		helper.assertTrue(BlackPanther.kinetic(p) >= 19.9f && BlackPanther.kinetic(p) < 39.9f, "it all goes into the bar, got " + BlackPanther.kinetic(p));
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(z), 8f);
		helper.assertTrue(Math.abs(BlackPanther.kinetic(p) - 40f) < 0.6f, "capped at 40 a stance, got " + BlackPanther.kinetic(p));
		helper.assertTrue(!BlackPanther.absorbing(p), "the stance breaks at the cap");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pulseSpendsThirtyAndHurts(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
		float hp = z.getHealth();
		BlackPantherAbilities.pulse(p);
		helper.assertTrue(z.getHealth() == hp, "an empty bar does nothing");
		BlackPanther.setKinetic(p, 50f);
		BlackPantherAbilities.pulse(p);
		helper.assertTrue(z.getHealth() < hp, "the shockwave hits");
		helper.assertTrue(Math.abs(BlackPanther.kinetic(p) - 20f) < 0.1f, "30 spent");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void overloadNeedsAFullBar(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
		BlackPanther.setKinetic(p, 90f);
		BlackPantherAbilities.overload(p);
		helper.assertTrue(BlackPanther.kinetic(p) == 90f, "refused under a full bar");
		BlackPanther.setKinetic(p, 100f);
		BlackPantherAbilities.overload(p);
		helper.assertTrue(BlackPanther.kinetic(p) == 0f, "the bar goes all at once");
		helper.assertTrue(!z.isAlive() || z.getHealth() < z.getMaxHealth(), "the blast hits");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void movesNeedTheSuit(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		BlackPanther.grant(p);
		BlackPanther.setKinetic(p, 50f);
		BlackPantherAbilities.pulse(p);
		helper.assertTrue(BlackPanther.kinetic(p) == 50f, "nothing fires without the suit");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void revokeClearsEverything(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		BlackPanther.revoke(p);
		helper.assertTrue(!BlackPanther.hasPower(p), "gone");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < base, "the claws go too");
		helper.succeed();
	}
}

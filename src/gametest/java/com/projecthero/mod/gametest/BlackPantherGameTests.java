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
		BlackPanther.bindHabit(p); // v0.17.4: the suit is crafted
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
	public void clawsGiveTheMeleeOnlyWhileOutAndGoBackIntoTheNecklace(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		BlackPanther.toggleClaws(p);
		helper.assertTrue(BlackPanther.clawsOut(p), "N pops the claws out");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (base + BlackPantherConfig.SUIT_MELEE)) < 0.01, "+5 with the claws out");
		BlackPanther.toggleClaws(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 0.01, "and nothing with them in");
		BlackPanther.toggleClaws(p);
		BlackPanther.suitDown(p);
		helper.assertTrue(!BlackPanther.suited(p), "H again takes it off");
		helper.assertTrue(!BlackPanther.clawsOut(p) && Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 0.01, "the claws go with it");
		helper.assertTrue(BlackPanther.hasPower(p), "the power stays");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void maskTogglesOnlyInTheSuit(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		BlackPanther.toggleMask(p);
		helper.assertTrue(BlackPanther.unmasked(p), "Shift+N takes the mask off");
		BlackPanther.toggleMask(p);
		helper.assertTrue(!BlackPanther.unmasked(p), "and puts it back on");
		BlackPanther.toggleMask(p);
		BlackPanther.suitDown(p);
		helper.assertTrue(!BlackPanther.state(p).unmasked, "the suit always forms masked");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void clawComboFirstSlashLandsOnThePress(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4)); // yaw 0 faces south, +z: right in front
		z.setNoAi(true);
		float hp = z.getHealth();
		BlackPantherAbilities.claws(p);
		helper.assertTrue(z.getHealth() < hp, "slash 1 hits at once, before any tick");
		helper.assertTrue(BlackPanther.clawsOut(p), "the combo pops the claws out");
		float afterFirst = z.getHealth();
		float first = hp - afterFirst;
		// the mock player isn't ticked by the server: step the run by hand, once a game tick (slash 2 at CLAW_STEP, 3 at twice that)
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.runAfterDelay(BlackPantherConfig.CLAW_STEP * 2 + 3, () -> {
			float total = hp - (z.isAlive() ? z.getHealth() : 0f);
			helper.assertTrue(!z.isAlive() || total > first * 2.2f, "slashes 2 and 3 land too: first " + first + ", total " + total);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void suitGivesArmourAndToughness(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		BlackPanther.grant(p);
		BlackPanther.bindHabit(p); // v0.17.4: the suit is crafted
		double armour = p.getAttributeValue(Attributes.ARMOR);
		double tough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		BlackPanther.suitUp(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - (armour + 8.0)) < 0.01, "+8 armour suited");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - (tough + 2.0)) < 0.01, "+2 toughness suited");
		BlackPanther.suitDown(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - armour) < 0.01, "gone with the suit");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void necklaceOffKeepsTheHerbButNotTheSuitOrMoves(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		BlackPanther.toggleNecklace(p);
		helper.assertTrue(BlackPanther.necklaceOff(p) && BlackPanther.hasPower(p), "the necklace comes off, the power stays");
		helper.assertTrue(!BlackPanther.suited(p), "taking it off suits down first");
		BlackPanther.tick(p); // tickCount 0: the passives are applied
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED), "Speed I stays");
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.JUMP), "Jump Boost I stays");
		BlackPanther.suitUp(p);
		helper.assertTrue(!BlackPanther.suited(p), "no suit without the necklace");
		BlackPanther.setKinetic(p, 50f);
		BlackPantherAbilities.pulse(p);
		helper.assertTrue(BlackPanther.kinetic(p) == 50f, "no suited moves without the necklace");
		// v0.17.4: the unsuited kit still works with the necklace off
		com.projecthero.mod.blackpanther.BlackPantherUnsuited.vault(p);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, com.projecthero.mod.blackpanther.BlackPantherUnsuited.VAULT) > 0,
				"the unsuited moves stay with the necklace off");
		BlackPanther.toggleNecklace(p);
		helper.assertTrue(!BlackPanther.necklaceOff(p), "held again, it goes back on");
		BlackPanther.suitUp(p);
		helper.assertTrue(BlackPanther.suited(p), "and the suit works again");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void sensesIsAToggleWithNoCooldown(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		BlackPantherAbilities.senses(p);
		helper.assertTrue(BlackPanther.sensing(p), "on");
		BlackPantherAbilities.senses(p);
		helper.assertTrue(!BlackPanther.sensing(p), "pressed again: off");
		BlackPantherAbilities.senses(p);
		helper.assertTrue(BlackPanther.sensing(p), "straight back on: no cooldown");
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.SENSES) == 0, "no cooldown");
		BlackPanther.suitDown(p);
		helper.assertTrue(!BlackPanther.state(p).senses, "the suit coming off ends it");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kineticReturnScalesWithTheShotAndTheBar(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		net.minecraft.world.entity.monster.Skeleton s = helper.spawn(EntityType.SKELETON, new BlockPos(2, 2, 5));
		s.setNoAi(true);
		s.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		s.setHealth(200f);
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(p.level(), s,
				new ItemStack(Items.ARROW), null);
		p.hurt(p.damageSources().arrow(arrow, s), 6f);
		var shot = com.projecthero.mod.blackpanther.BlackPantherDamage.lastShot(p);
		// (the difficulty may scale a skeleton's arrow: whatever arrived is what counts)
		helper.assertTrue(shot != null && shot.damage() > 0f && shot.shooter().equals(s.getUUID()),
				"the raw shot is remembered even though it bounced off, got " + (shot == null ? null : shot.damage()));
		// empty bar: max(4, shot x1.25)
		BlackPanther.setKinetic(p, 0f);
		float hp = s.getHealth();
		BlackPantherAbilities.kineticReturn(p);
		float plain = hp - s.getHealth();
		float expected = Math.max(4f, shot.damage() * 1.25f);
		helper.assertTrue(Math.abs(plain - expected) < 0.05f, "empty bar: " + expected + " expected, dealt " + plain);
		// 60 stored: 40 spent, +10
		var n = BlackPanther.state(p).copy();
		n.abilityReadyAt.clear();
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.BLACK_PANTHER_STATE, n);
		BlackPanther.setKinetic(p, 60f);
		hp = s.getHealth();
		BlackPantherAbilities.kineticReturn(p);
		float charged = hp - s.getHealth();
		helper.assertTrue(Math.abs(charged - (expected + 10f)) < 0.05f, "40 Kinetic adds 10: " + (expected + 10f) + " expected, dealt " + charged);
		helper.assertTrue(Math.abs(BlackPanther.kinetic(p) - 20f) < 0.05f, "40 spent, got " + BlackPanther.kinetic(p));
		helper.assertTrue(Math.abs(BlackPantherAbilities.returnDamage(10f, 100f) - 22.5f) < 0.01f, "the spend caps at 40");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stalkBackstabIsSofterOnPlayers(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		ServerPlayer victim = mock(helper);
		helper.assertTrue(com.projecthero.mod.blackpanther.BlackPantherDamage.backstabFactor(victim) == 1.75f, "x1.75 on a player");
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		helper.assertTrue(com.projecthero.mod.blackpanther.BlackPantherDamage.backstabFactor(z) == 3f, "x3 on a creature");
		helper.assertTrue(BlackPantherConfig.STALK_COOLDOWN == 200, "Stalk has a 10s cooldown");
		if (!p.server.isPvpAllowed()) {
			helper.succeed();
			return;
		}
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(victim, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		// the victim faces south (+z); the panther stands behind them, to the north
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.2));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		victim.setYHeadRot(0f);
		BlackPantherAbilities.toggleStalk(p);
		helper.assertTrue(BlackPanther.stalking(p), "stalking");
		float before = victim.getHealth();
		victim.hurt(p.damageSources().playerAttack(p), 4f);
		float lost = before - victim.getHealth();
		helper.assertTrue(Math.abs(lost - 7f) < 0.05f, "4 x 1.75 = 7 from behind, lost " + lost);
		helper.assertTrue(!BlackPanther.stalking(p), "the hit ends Stalk");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHitIsFortyPercentSoakedAndCharged(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
		float before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 10f);
		// another test in the batch may have changed the difficulty, which scales a mob's hit (Easy 6, Normal 10, Hard 15); the
		// suit's +8 armour then takes its share of the 60% that gets through
		float lost = before - p.getHealth();
		float k = BlackPanther.kinetic(p);
		float incoming = k / (0.4f * 4f);
		helper.assertTrue(Math.abs(incoming - 6f) < 0.05f || Math.abs(incoming - 10f) < 0.05f || Math.abs(incoming - 15f) < 0.05f,
				"40% soaked at 4 Kinetic a point, got " + k);
		helper.assertTrue(lost > 0f && lost < incoming * 0.6f + 0.01f, "the rest gets through, less armour: lost " + lost + " of " + incoming);
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
		BlackPanther.toggleClaws(p);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		BlackPanther.revoke(p);
		helper.assertTrue(!BlackPanther.hasPower(p), "gone");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < base, "the claws go too");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) < armour, "and the suit's armour");
		helper.succeed();
	}

	// ================================================================ v0.17.4: the crafted suit, the two movesets, the passives

	/** Spawn protection off, the power held, no suit yet. */
	private static ServerPlayer bare(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		noSpawnProtection(p);
		BlackPanther.grant(p);
		return p;
	}

	private static void noSpawnProtection(ServerPlayer p) {
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The suit finished forming (no waiting out the 2 s change). */
	private static void finishChange(ServerPlayer p) {
		var s = BlackPanther.state(p).copy();
		s.suitChangeAt = p.level().getGameTime() - BlackPantherConfig.SUIT_UP_TICKS - 1;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.BLACK_PANTHER_STATE, s);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSuitNeedsTheCraftedHabit(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		helper.assertTrue(!BlackPanther.hasHabit(p), "a new Black Panther has no suit");
		BlackPanther.toggleSuit(p);
		helper.assertTrue(!BlackPanther.suited(p), "H does nothing without the habit");
		ItemStack habit = new ItemStack(BlackPantherItems.HABIT);
		p.setItemInHand(InteractionHand.MAIN_HAND, habit);
		habit.use(p.level(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(BlackPanther.hasHabit(p), "using the habit binds it to the necklace");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the habit is used up");
		ItemStack second = new ItemStack(BlackPantherItems.HABIT);
		p.setItemInHand(InteractionHand.MAIN_HAND, second);
		second.use(p.level(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getMainHandItem().getCount() == 1, "a second habit is refused and kept");
		BlackPanther.suitUp(p);
		helper.assertTrue(BlackPanther.suited(p), "now H suits up");
		ServerPlayer other = mock(helper);
		ItemStack stray = new ItemStack(BlackPantherItems.HABIT);
		other.setItemInHand(InteractionHand.MAIN_HAND, stray);
		stray.use(other.level(), other, InteractionHand.MAIN_HAND);
		helper.assertTrue(other.getMainHandItem().getCount() == 1, "only the Black Panther can wear it");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHabitHasTwoRecipes(GameTestHelper helper) {
		var recipes = helper.getLevel().getServer().getRecipeManager();
		for (String id : new String[] { "panther_habit", "panther_habit_echo" }) {
			var r = recipes.byKey(com.projecthero.mod.ProjectHeroMod.id(id));
			helper.assertTrue(r.isPresent(), id + " loads");
			helper.assertTrue(r.get().value().getResultItem(helper.getLevel().registryAccess()).is(BlackPantherItems.HABIT), id + " makes the habit");
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aPowerFromBeforeTheHabitKeepsItsSuit(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		var old = new com.projecthero.mod.blackpanther.data.BlackPantherState();
		old.hasPower = true; // saved by 0.17.3: no habit, not migrated
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.BLACK_PANTHER_STATE, old);
		BlackPanther.onPlayerJoin(p);
		helper.assertTrue(BlackPanther.hasHabit(p), "migrated: the habit is already in the necklace");
		ServerPlayer fresh = bare(helper);
		BlackPanther.onPlayerJoin(fresh);
		helper.assertTrue(!BlackPanther.hasHabit(fresh), "a new power is not given one");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void losingThePowerHandsTheHabitBack(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		BlackPanther.bindHabit(p);
		BlackPanther.revoke(p);
		helper.assertTrue(p.getInventory().countItem(BlackPantherItems.HABIT) == 1, "the habit comes back out as the item");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theMovesetSwitchesOnSuitUpAndSuitOff(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		BlackPanther.bindHabit(p);
		var R = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		var X = com.projecthero.mod.hero.AbilitySlot.SLOT_3;
		helper.assertTrue(!com.projecthero.mod.blackpanther.BlackPantherAbilityManager.idsOf(R, false)[0]
				.equals(com.projecthero.mod.blackpanther.BlackPantherAbilityManager.idsOf(R, true)[0]), "two different kits on R");
		// out of the suit: R is the Warrior's Combo
		com.projecthero.mod.blackpanther.BlackPantherAbilityManager.handle(p, R, true);
		com.projecthero.mod.blackpanther.BlackPantherAbilityManager.handle(p, R, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, com.projecthero.mod.blackpanther.BlackPantherUnsuited.COMBO) > 0, "unsuited R: the combo");
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.PULSE) == 0, "not the Kinetic Pulse");
		// suit up: the same key is now the Kinetic Pulse
		BlackPanther.suitUp(p);
		finishChange(p);
		BlackPanther.setKinetic(p, 50f);
		com.projecthero.mod.blackpanther.BlackPantherAbilityManager.handle(p, R, true);
		com.projecthero.mod.blackpanther.BlackPantherAbilityManager.handle(p, R, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.PULSE) > 0, "suited R: the Kinetic Pulse");
		helper.assertTrue(Math.abs(BlackPanther.kinetic(p) - 20f) < 0.01f, "it spent 30 Kinetic");
		com.projecthero.mod.blackpanther.BlackPantherAbilityManager.handle(p, X, true);
		com.projecthero.mod.blackpanther.BlackPantherAbilityManager.handle(p, X, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.LEAP) > 0, "suited X: Panther Leap");
		helper.assertTrue(BlackPanther.cooldownRemaining(p, com.projecthero.mod.blackpanther.BlackPantherUnsuited.VAULT) == 0, "not the Vault");
		// suit off: back to the unsuited kit
		BlackPanther.suitDown(p);
		p.setDeltaMovement(Vec3.ZERO);
		com.projecthero.mod.blackpanther.BlackPantherAbilityManager.handle(p, X, true);
		com.projecthero.mod.blackpanther.BlackPantherAbilityManager.handle(p, X, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, com.projecthero.mod.blackpanther.BlackPantherUnsuited.VAULT) > 0, "unsuited X: the Vault");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void suitingUpEndsTheUnsuitedToggles(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		BlackPanther.bindHabit(p);
		com.projecthero.mod.blackpanther.BlackPantherUnsuited.toggleTracking(p);
		com.projecthero.mod.blackpanther.BlackPantherUnsuited.raiseParry(p);
		helper.assertTrue(BlackPanther.state(p).senses && BlackPanther.state(p).absorbing, "tracking and parry up");
		BlackPanther.suitUp(p);
		helper.assertTrue(!BlackPanther.state(p).senses && !BlackPanther.state(p).absorbing, "the suit ends them");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHerbAddsMeleeAndHalvesFalls(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		noSpawnProtection(p);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		BlackPanther.grant(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base - BlackPantherConfig.HERB_MELEE) < 1.0e-6, "+2 melee from the herb");
		float before = p.getHealth();
		p.hurt(p.damageSources().fall(), 10f);
		float lost = before - p.getHealth();
		helper.assertTrue(Math.abs(lost - 5f) < 0.05f, "Cat's Landing: a 10 fall does 5 out of the suit, lost " + lost);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void herbVitalityHealsOneStepAtATime(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		p.setHealth(10f);
		float[] last = { p.getHealth() };
		boolean[] stepped = { false };
		helper.onEachTick(() -> {
			BlackPanther.tick(p);
			float h = p.getHealth();
			if (h > last[0] + 0.01f) {
				helper.assertTrue(Math.abs(h - last[0] - 1f) < 0.01f, "one health a step, got " + (h - last[0]));
				stepped[0] = true;
			}
			last[0] = h;
		});
		helper.succeedWhen(() -> helper.assertTrue(stepped[0], "Herb Vitality has healed"));
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void vitalityWaitsAfterAHit(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		p.hurt(p.damageSources().mobAttack(z), 2f);
		helper.assertTrue(com.projecthero.mod.blackpanther.BlackPanther.state(p).hasPower, "power held");
		helper.assertTrue(!BlackPanther.healing(p), "no healing right after a hit");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void nightEyesInTheDarkAndAlwaysInTheSuit(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		BlackPanther.tick(p);
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "the suit's lenses: night vision");
		BlackPanther.suitDown(p);
		// sealed in a stone box, out of the suit: the herb's Night Eyes come on once the light has settled
		BlockPos c = new BlockPos(4, 2, 4);
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 2; y++) {
				for (int z = -1; z <= 1; z++) {
					boolean inside = x == 0 && z == 0 && (y == 0 || y == 1);
					helper.setBlock(c.offset(x, y, z), inside ? net.minecraft.world.level.block.Blocks.AIR : net.minecraft.world.level.block.Blocks.STONE);
				}
			}
		}
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.succeedWhen(() -> helper.assertTrue(BlackPanther.nightEyes(p) && p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION),
				"Night Eyes in the dark"));
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void hunterInstinctCountsWhatHuntsHim(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 2)).setNoAi(true);
		z.setTarget(p);
		helper.assertTrue(com.projecthero.mod.blackpanther.BlackPantherUnsuited.hunters(p) == 1, "the one hunting him");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aPerfectParryTurnsTheHitAside(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4)); // yaw 0 faces south, +z
		com.projecthero.mod.blackpanther.BlackPantherUnsuited.raiseParry(p);
		float before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 6f);
		helper.assertTrue(p.getHealth() == before, "caught at once: no damage");
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "the attacker staggers");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aLateParryCutsTheHit(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		ServerPlayer control = mock(helper);
		noSpawnProtection(control);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
		com.projecthero.mod.blackpanther.BlackPantherUnsuited.raiseParry(p);
		helper.runAfterDelay(BlackPantherConfig.PARRY_PERFECT_TICKS + 3, () -> {
			float before = p.getHealth();
			p.hurt(p.damageSources().mobAttack(z), 10f);
			float lost = before - p.getHealth();
			float cBefore = control.getHealth();
			control.hurt(control.damageSources().mobAttack(z), 10f);
			float plain = cBefore - control.getHealth();
			helper.assertTrue(plain > 0f && Math.abs(lost / plain - (1f - BlackPantherConfig.PARRY_REDUCTION)) < 0.05f,
					"60% cut: lost " + lost + " of " + plain);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBackflipDodgesTheNextHit(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
		com.projecthero.mod.blackpanther.BlackPantherUnsuited.backflip(p);
		float before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 6f);
		helper.assertTrue(p.getHealth() == before, "the evade: it misses");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(z), 6f);
		helper.assertTrue(p.getHealth() < before, "only the one hit");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kimoyoScanMarksForMoreDamage(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		Zombie marked = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 5));
		Zombie plain = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 5));
		marked.setNoAi(true);
		plain.setNoAi(true);
		// he looks straight at the first (it stands 2.5 blocks south of him)
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, marked.getEyePosition().add(0, -0.4, 0));
		com.projecthero.mod.blackpanther.BlackPantherUnsuited.scan(p);
		helper.assertTrue(com.projecthero.mod.blackpanther.BlackPantherUnsuited.marked(p, marked), "scanned");
		helper.assertTrue(!com.projecthero.mod.blackpanther.BlackPantherUnsuited.marked(p, plain), "only the one aimed at");
		float m0 = marked.getHealth(), p0 = plain.getHealth();
		com.projecthero.mod.blackpanther.BlackPantherCombat.strike(p, marked, p.position(), 4f, 0, 0);
		com.projecthero.mod.blackpanther.BlackPantherCombat.strike(p, plain, p.position(), 4f, 0, 0);
		float ratio = (m0 - marked.getHealth()) / Math.max(0.01f, p0 - plain.getHealth());
		helper.assertTrue(Math.abs(ratio - BlackPantherConfig.SCAN_BONUS) < 0.06f, "+25% on the marked one, ratio " + ratio);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void takedownSlamsAndDazes(GameTestHelper helper) {
		ServerPlayer p = bare(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
		z.setNoAi(true);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition().add(0, -0.4, 0));
		float before = z.getHealth();
		com.projecthero.mod.blackpanther.BlackPantherUnsuited.takedown(p);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, com.projecthero.mod.blackpanther.BlackPantherUnsuited.TAKEDOWN) > 0, "grabbed");
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.succeedWhen(() -> {
			helper.assertTrue(z.getHealth() < before, "slammed");
			helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "dazed");
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void silentPawsInTheSuitOnly(GameTestHelper helper) {
		ServerPlayer p = suited(helper);
		BlockPos sensorAt = new BlockPos(5, 2, 2);
		helper.setBlock(sensorAt, net.minecraft.world.level.block.Blocks.SCULK_SENSOR);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			t[0]++;
			if (t[0] == 40) {
				helper.assertTrue(net.minecraft.world.level.block.SculkSensorBlock.getPhase(helper.getBlockState(sensorAt))
						!= net.minecraft.world.level.block.state.properties.SculkSensorPhase.ACTIVE, "in the suit the sensor never hears him");
				BlackPanther.suitDown(p);
			}
			p.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(t[0] > 40, "the suited phase ran");
			helper.assertTrue(net.minecraft.world.level.block.SculkSensorBlock.getPhase(helper.getBlockState(sensorAt))
					== net.minecraft.world.level.block.state.properties.SculkSensorPhase.ACTIVE, "out of it, it does");
		});
	}
}

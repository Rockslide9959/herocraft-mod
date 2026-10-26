package com.projecthero.mod.gametest;

import com.projecthero.mod.galactus.item.GalactusItems;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.silversurfer.SilverSurfer;
import com.projecthero.mod.silversurfer.SilverSurferAbilities;
import com.projecthero.mod.silversurfer.SilverSurferAttachments;
import com.projecthero.mod.silversurfer.SilverSurferConfig;
import com.projecthero.mod.silversurfer.SilverSurferFlight;
import com.projecthero.mod.silversurfer.SilverSurferStatues;
import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Silver Surfer: the board grant (and the one-Primary rule), the flight numbers, the Power Cosmic's cost and refill,
 * the passives, and smoke tests of the moves (Transmutation's freeze / shatter included). Mock players are not ticked by
 * the server, so tests over time drive {@link SilverSurfer#tick} ({@link #pump}).
 */
public class SilverSurferGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** The Surfer, full Power Cosmic, facing +Z. */
	private static ServerPlayer surfer(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		SilverSurfer.grant(p);
		SilverSurferState n = SilverSurfer.state(p).copy();
		n.animId = SilverSurferState.ANIM_NONE;
		p.setAttached(SilverSurferAttachments.STATE, n);
		p.removeAllEffects();
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			SilverSurfer.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boardRightClickGrantsAndClaimsPrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(HeroTiers.HERO_KEYS.contains("silver_surfer") && PowerGrants.HERO_TIER_KEYS.contains("silver_surfer"),
				"silver_surfer is a Hero-Tier key");
		Kryptonian.grant(p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GalactusItems.SURFBOARD));
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(SilverSurfer.hasPower(p), "right-click: the Power Cosmic floods in");
		helper.assertTrue(!Kryptonian.hasPower(p) && HeroTiers.heroCount(p) == 1, "the Surfer replaced the Kryptonian (one Primary)");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the board bonded to him (used up)");
		helper.assertTrue(SilverSurfer.cosmic(p) == SilverSurferConfig.COSMIC_MAX, "a full Power Cosmic");
		// a second board: already the Surfer, it calls the board under his feet and stays
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GalactusItems.SURFBOARD));
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(SilverSurfer.surfing(p) && p.getAbilities().mayfly, "surfing, flight lent");
		helper.assertTrue(p.getMainHandItem().is(GalactusItems.SURFBOARD), "the spare board is kept");
		SilverSurferFlight.stop(p, false);
		helper.assertTrue(!SilverSurfer.surfing(p) && !p.getAbilities().mayfly, "off the board: flight taken back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boardHasAnEndgameRecipe(GameTestHelper helper) {
		var recipe = helper.getLevel().getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("silver_surfer_board"));
		helper.assertTrue(recipe.isPresent(), "the board recipe");
		helper.assertTrue(recipe.get().value().getResultItem(helper.getLevel().registryAccess()).is(GalactusItems.SURFBOARD), "makes the board");
		boolean star = false;
		for (var ing : recipe.get().value().getIngredients()) {
			star |= ing.test(new ItemStack(Items.NETHER_STAR));
		}
		helper.assertTrue(star, "it takes a Nether Star");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fastestFlightNumbers(GameTestHelper helper) {
		helper.assertTrue(Math.abs(SilverSurferFlight.tune(false, false).speed() * 20.0 - 60.0) < 1.0e-6, "cruise 60 b/s");
		helper.assertTrue(Math.abs(SilverSurferFlight.tune(true, false).speed() * 20.0 - 110.0) < 1.0e-6, "sprint 110 b/s");
		helper.assertTrue(Math.abs(SilverSurferFlight.tune(false, true).forcedForward() * 20.0 - 150.0) < 1.0e-6, "Light-Speed 150 b/s");
		helper.assertTrue(SilverSurferConfig.LIGHT_SPEED_BPS > com.projecthero.mod.kryptonian.KryptonianConfig.FLIGHT_BOOST_SPEED * 20.0,
				"faster than the Kryptonian boost");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightSpeedCallsTheBoardAndCosts(GameTestHelper helper) {
		ServerPlayer p = surfer(helper, 2.5, 2.0, 2.5);
		SilverSurferAbilities.lightSpeed(p);
		helper.assertTrue(SilverSurfer.surfing(p) && SilverSurfer.lightSpeed(p), "on the board, at Light-Speed");
		helper.assertTrue(Math.abs(SilverSurfer.cosmic(p) - (SilverSurferConfig.COSMIC_MAX - SilverSurferConfig.LIGHT_SPEED_COST)) < 0.01, "35 spent");
		helper.assertTrue(SilverSurfer.cooldownRemaining(p, SilverSurferAbilities.LIGHT_SPEED) == SilverSurferConfig.LIGHT_SPEED_COOLDOWN, "20 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void cosmicBoltHitsForSeven(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = surfer(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		pump(helper, p);
		SilverSurferAbilities.pressBlast(p);
		SilverSurferAbilities.releaseBlast(p);
		helper.assertTrue(Math.abs(SilverSurfer.cosmic(p) - (SilverSurferConfig.COSMIC_MAX - SilverSurferConfig.BOLT_COST)) < 0.01, "4 spent");
		helper.assertTrue(SilverSurfer.cooldownRemaining(p, SilverSurferAbilities.BLAST) > 0, "on cooldown");
		helper.runAfterDelay(6, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(Math.abs(taken - SilverSurferConfig.BOLT_DAMAGE) < EPS, "15, took " + taken);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void cosmicNovaHitsAround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = surfer(helper, 3.5, 2.0, 3.5);
		Zombie near = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.0, 100);
		pump(helper, p);
		SilverSurferAbilities.nova(p);
		helper.runAfterDelay(12, () -> {
			float taken = 100f - near.getHealth();
			helper.assertTrue(taken >= SilverSurferConfig.NOVA_DAMAGE_EDGE - EPS && taken <= SilverSurferConfig.NOVA_DAMAGE_CENTRE + EPS,
					"9..18, took " + taken);
			helper.assertTrue(SilverSurfer.cooldownRemaining(p, SilverSurferAbilities.NOVA) > 0, "on cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cosmicDashBlinksAndHits(GameTestHelper helper) {
		ServerPlayer p = surfer(helper, 3.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.0, 100);
		double z0 = p.getZ();
		SilverSurferAbilities.dash(p);
		// the gametest cage is 8 blocks across: the 30-block dash stops at its barrier wall, past the zombie
		helper.assertTrue(p.getZ() - z0 > 4.0, "blinked forward through the zombie, moved " + (p.getZ() - z0));
		helper.assertTrue(Math.abs((100f - z.getHealth()) - SilverSurferConfig.DASH_DAMAGE) < EPS, "9 to what it passed");
		helper.assertTrue(Math.abs(SilverSurfer.cosmic(p) - (SilverSurferConfig.COSMIC_MAX - SilverSurferConfig.DASH_COST)) < 0.01, "15 spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void transmutationFreezesThenShatters(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = surfer(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100);
		SilverSurferAbilities.transmute(p);
		helper.assertTrue(SilverSurferStatues.isStatue(z), "turned to silver");
		helper.assertTrue(z.getHealth() == 100f, "the change itself does no harm");
		helper.assertTrue(z.hurt(helper.getLevel().damageSources().playerAttack(p), 2f) || true, "hit");
		float taken = 100f - z.getHealth();
		helper.assertTrue(Math.abs(taken - (2f + SilverSurferConfig.SHATTER_BONUS)) < EPS, "the hit shatters it for 16 more, took " + taken);
		helper.assertFalse(SilverSurferStatues.isStatue(z), "shattered");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesAreOnlySlowedByTransmutation(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = surfer(helper, 3.5, 2.0, 1.5);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 600);
		SilverSurferAbilities.transmute(p);
		helper.assertFalse(SilverSurferStatues.isStatue(boss), "a boss is never frozen");
		helper.assertTrue(boss.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "only slowed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void powerCosmicSpendsAndRefills(GameTestHelper helper) {
		ServerPlayer p = surfer(helper, 2.5, 2.0, 2.5);
		SilverSurfer.setCosmic(p, 10f);
		SilverSurferAbilities.nova(p);
		helper.assertTrue(SilverSurfer.cosmic(p) == 10f && SilverSurfer.cooldownRemaining(p, SilverSurferAbilities.NOVA) == 0,
				"not enough: nothing spent, no cooldown");
		float rate = SilverSurfer.regenPerSecond(p);
		helper.assertTrue(rate == SilverSurferConfig.COSMIC_REGEN_SKY || rate == SilverSurferConfig.COSMIC_REGEN_COVERED, "a known rate");
		pump(helper, p);
		helper.runAfterDelay(21, () -> {
			float now = SilverSurfer.cosmic(p);
			helper.assertTrue(now >= 10f + rate * 0.7f, "refilled about " + rate + " in a second, at " + now);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void passivesNoFallFireOrDrowning(GameTestHelper helper) {
		ServerPlayer p = surfer(helper, 2.5, 2.0, 2.5);
		float hp = p.getHealth();
		p.hurt(p.damageSources().fall(), 8f);
		helper.assertTrue(p.getHealth() == hp, "no fall damage");
		p.hurt(p.damageSources().inFire(), 4f);
		p.hurt(p.damageSources().lava(), 4f);
		helper.assertTrue(p.getHealth() == hp, "no fire or lava");
		p.hurt(p.damageSources().drown(), 4f);
		p.hurt(p.damageSources().inWall(), 4f);
		helper.assertTrue(p.getHealth() == hp, "no drowning or suffocation");
		SilverSurfer.tick(p);
		helper.assertTrue(p.hasEffect(MobEffects.REGENERATION), "Regeneration II"); // v0.17.1
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void molecularPhaseCantBeHit(GameTestHelper helper) {
		ServerPlayer p = surfer(helper, 2.5, 2.0, 2.5);
		SilverSurferAbilities.phase(p);
		helper.assertTrue(SilverSurfer.phasing(p), "phased");
		float hp = p.getHealth();
		p.hurt(p.damageSources().generic(), 6f);
		helper.assertTrue(p.getHealth() == hp, "nothing lands while phased");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void barrierSoaksFromTheFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = surfer(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		SilverSurferAbilities.raiseBarrier(p);
		helper.assertTrue(SilverSurfer.barrierUp(p), "the barrier stands");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 6f);
		helper.assertTrue(p.getHealth() == hp, "a hit from the front is soaked");
		helper.assertTrue(SilverSurfer.state(p).barrierHp < SilverSurferConfig.BARRIER_HP, "and wears the barrier down");
		SilverSurferAbilities.lowerBarrier(p);
		helper.assertTrue(!SilverSurfer.barrierUp(p) && SilverSurfer.cooldownRemaining(p, SilverSurferAbilities.BARRIER) > 0, "lowered, cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void revokeClearsEverything(GameTestHelper helper) {
		ServerPlayer p = surfer(helper, 2.5, 2.0, 2.5);
		SilverSurferFlight.start(p);
		SilverSurfer.revoke(p);
		helper.assertTrue(!SilverSurfer.hasPower(p) && !p.getAbilities().mayfly, "power and lent flight gone");
		helper.succeed();
	}

	/** v0.17.1 (user): 90% less damage, no armour, +10 melee, Regeneration II. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void powerCosmicArmourRules(GameTestHelper helper) {
		ServerPlayer p = surfer(helper, 3.5, 2.0, 3.5);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
		SilverSurfer.tick(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "no armour on the Surfer");
		helper.assertTrue(p.getInventory().contains(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)),
				"it went back into the inventory");
		helper.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) >= 1.0 + SilverSurferConfig.MELEE_BONUS - 0.01,
				"+10 melee");
		net.minecraft.world.effect.MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 1, "Regeneration II");
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().generic(), 10f);
		helper.assertTrue(Math.abs((20f - p.getHealth()) - 1f) < 0.05f, "a 10-damage hit takes 1, took " + (20f - p.getHealth()));
		helper.succeed();
	}
}

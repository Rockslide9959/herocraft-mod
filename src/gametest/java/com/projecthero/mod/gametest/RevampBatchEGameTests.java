package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p16.SpiderClimbingHandlers;
import com.projecthero.mod.hero.power.p17.ElasticityHandlers;
import com.projecthero.mod.hero.power.p18.CrushingDensityEffect;
import com.projecthero.mod.hero.power.p18.DensityManipulationHandlers;
import com.projecthero.mod.hero.power.p22.PlantEntities;
import com.projecthero.mod.hero.power.p22.PlantManipulationHandlers;
import com.projecthero.mod.hero.power.p22.ThornSentryEntity;
import com.projecthero.mod.hero.power.p27.ShrunkenEffect;
import com.projecthero.mod.hero.power.p27.SizeHandlers;
import com.projecthero.mod.hero.revamp.RevampBatchE;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** v0.13.22 mutation revamp, batch E: regression coverage for the reworked kits (16, 17, 18, 22, 27). */
public class RevampBatchEGameTests implements FabricGameTest {
	private static final String[] KEYS = { RevampBatchE.P16, RevampBatchE.P17, RevampBatchE.P18, RevampBatchE.P22, RevampBatchE.P27 };

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static Power grantActive(ServerPlayer player, String key) {
		Power p = Powers.byKey(key);
		ExperimentalPowers.grant(player, p);
		ExperimentalPowers.setActive(player, p);
		return p;
	}

	/** Puts the player at (2,2,2) looking at a freshly spawned mob {@code dist} blocks along +Z. */
	private static <T extends net.minecraft.world.entity.Mob> T mobInFront(GameTestHelper helper, ServerPlayer player,
			EntityType<T> type, int dist) {
		BlockPos origin = new BlockPos(2, 2, 2);
		Vec3 pp = Vec3.atBottomCenterOf(helper.absolutePos(origin));
		player.setPos(pp.x, pp.y, pp.z);
		T mob = helper.spawn(type, origin.offset(0, 0, dist));
		player.lookAt(EntityAnchorArgument.Anchor.EYES, mob.getEyePosition());
		return mob;
	}

	private static boolean onCooldown(ServerPlayer player, Power power, AbilitySlot slot) {
		return !ExperimentalPowers.cooldownReady(player, power, power.ability(slot));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void batchPowersRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.count() == 26, "expected 26 powers (Super Durability removed in v0.14.5)");
		helper.succeed();
	}

	// ---------------------------------------------------------------- framework conformance

	@GameTest(template = EMPTY_STRUCTURE)
	public void batchEPowersHaveEightHandledAbilities(GameTestHelper helper) {
		for (String key : KEYS) {
			Power p = Powers.byKey(key);
			helper.assertTrue(p.abilities().size() == 8, key + " must have 8 abilities, has " + p.abilities().size());
			for (AbilitySlot slot : AbilitySlot.values()) {
				Ability a = p.ability(slot);
				helper.assertTrue(a != null && a.slot() == slot, key + " slot " + slot + " mismapped");
				helper.assertTrue(AbilityHandlers.has(p, a), key + "/" + a.id() + " has no handler");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void batchEVisualsAndMetersRegistered(GameTestHelper helper) {
		for (String flag : new String[] { "p16.grip", "p16.rush", "p16.sense", "p17.arm", "p17.shield", "p17.glide", "p17.form",
				"p18.shell", "p18.intangible", "p18.crush", "p22.blessing", "p22.overgrowth" }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), "visual flag " + flag + " not registered");
		}
		helper.assertTrue(MutationMeters.get(RevampBatchE.P17, "rubber") != null, "Rubber meter");
		helper.assertTrue(MutationMeters.get(RevampBatchE.P18, "phase").max() > 100.0f, "Phase reserve is +15%");
		helper.assertTrue(MutationMeters.get(RevampBatchE.P27, "giant_form").always(), "Size Strain is the always-on fuel gauge");
		helper.assertTrue(MutationMeters.get(RevampBatchE.P22, "sentry_ticks") != null, "Thorn Sentry timer");
		helper.succeed();
	}

	// ---------------------------------------------------------------- 16 Spider Adhesion

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderSenseDodgesTheNextAttack(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power spider = grantActive(player, RevampBatchE.P16);
		Zombie z = mobInFront(helper, player, EntityType.ZOMBIE, 2);

		AbilityRouter.handleInput(player, 7, true); // H = Spider-Sense Dodge
		helper.assertTrue(SpiderClimbingHandlers.senseActive(player), "Spider-Sense should be primed");
		helper.assertTrue(onCooldown(player, spider, AbilitySlot.SLOT_7), "Spider-Sense should go on cooldown");

		boolean allowed = ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(player,
				player.damageSources().mobAttack(z), 5.0f);
		helper.assertFalse(allowed, "the first attack inside the window must be dodged");
		helper.assertFalse(SpiderClimbingHandlers.senseActive(player), "the dodge consumes the sense");
		boolean second = ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(player,
				player.damageSources().mobAttack(z), 5.0f);
		helper.assertTrue(second, "only the NEXT attack is dodged");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderSenseIgnoresEnvironmentalDamage(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grantActive(player, RevampBatchE.P16);
		AbilityRouter.handleInput(player, 7, true);
		ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(player, player.damageSources().inFire(), 2.0f);
		helper.assertTrue(SpiderClimbingHandlers.senseActive(player), "fire is not an attack -- the sense must stay primed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void venomBitePoisonsAndSlows(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power spider = grantActive(player, RevampBatchE.P16);
		// not an undead mob: undead are immune to poison
		var z = mobInFront(helper, player, EntityType.VINDICATOR, 2);
		float before = z.getHealth();

		AbilityRouter.handleInput(player, 8, true); // N = Venom Bite

		helper.assertTrue(z.hasEffect(MobEffects.POISON), "Venom Bite should poison");
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Venom Bite should slow");
		helper.assertTrue(z.getHealth() < before, "Venom Bite should deal damage");
		helper.assertTrue(onCooldown(player, spider, AbilitySlot.SLOT_8), "a landed bite starts the cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void venomBiteWhiffCostsNothing(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power spider = grantActive(player, RevampBatchE.P16);
		Vec3 pp = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		player.setPos(pp.x, pp.y, pp.z); // away from the shared mock-player spawn point
		player.setXRot(-90.0f); // straight up at nothing
		AbilityRouter.handleInput(player, 8, true);
		helper.assertFalse(onCooldown(player, spider, AbilitySlot.SLOT_8), "a missed bite must not start the cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void adhesionStillEvolvesIntoSpiderMan(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grantActive(player, RevampBatchE.P16);
		AbilityRouter.handleInput(player, 7, true); // leave a transient state running
		helper.assertTrue(com.projecthero.mod.spider.SpiderMan.evolveFromAdhesion(player), "evolution still works on the revamped kit");
		helper.assertFalse(ExperimentalPowers.owns(player, RevampBatchE.P16), "and still consumes the mutation");
		helper.succeed();
	}

	// ---------------------------------------------------------------- 17 Elasticity

	@GameTest(template = EMPTY_STRUCTURE)
	public void stretchPunchStretchesTheArm(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grantActive(player, RevampBatchE.P17);
		Zombie z = mobInFront(helper, player, EntityType.ZOMBIE, 5);
		float before = z.getHealth();

		AbilityRouter.handleInput(player, 1, true);
		AbilityRouter.handleInput(player, 1, false);

		helper.assertTrue(z.getHealth() < before, "Stretch Punch should reach 5 blocks");
		helper.assertTrue(ElasticityHandlers.armStretched(player), "the arm should be drawn stretched");
		helper.assertTrue(ElasticityHandlers.armLength(player) > 3.0f, "stretched toward the target, got " + ElasticityHandlers.armLength(player));
		helper.assertTrue(ElasticityHandlers.armStart(player) >= 1.0f, "the stretch start tick is synced for the client animation");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rubberShieldBouncesProjectilesAndCleansUp(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power elastic = grantActive(player, RevampBatchE.P17);
		Zombie shooter = mobInFront(helper, player, EntityType.ZOMBIE, 8);
		double speedBefore = player.getAttributeValue(Attributes.MOVEMENT_SPEED);

		AbilityRouter.handleInput(player, 7, true); // H down = inflate
		helper.assertTrue(ElasticityHandlers.shielding(player), "holding H inflates the Rubber Shield");
		helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < speedBefore, "the balloon is slow");

		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		Vec3 at = player.position().add(0, 1.0, 2.0);
		arrow.setPos(at.x, at.y, at.z);
		arrow.setOwner(shooter);
		arrow.setDeltaMovement(0, 0, -1.5); // straight at the player
		helper.getLevel().addFreshEntity(arrow);
		int bounced = ElasticityHandlers.reflectProjectiles(player);
		helper.assertTrue(bounced >= 1, "the incoming arrow should bounce");
		helper.assertTrue(arrow.getOwner() == player, "a bounced projectile belongs to the elastic hero now");
		helper.assertTrue(arrow.getDeltaMovement().z > 0, "and flies back the way it came");

		AbilityRouter.handleInput(player, 7, false); // H up = deflate
		helper.assertFalse(ElasticityHandlers.shielding(player), "releasing H deflates");
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - speedBefore) < 1.0e-6,
				"the shield's slow is removed on release");
		helper.assertTrue(onCooldown(player, elastic, AbilitySlot.SLOT_7), "the shield cools down after use");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void parachuteGlideRunsAndFoldsUp(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power elastic = grantActive(player, RevampBatchE.P17);
		AbilityRouter.handleInput(player, 8, true); // N = glide
		helper.assertTrue(ElasticityHandlers.gliding(player), "Parachute Glide should start");
		ExperimentalPowers.serverTick(player);
		helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "the canopy holds you up (server-side Slow Falling)");
		helper.assertTrue(ExperimentalPowers.getResource(player, elastic, "glide_ticks") <= ElasticityHandlers.GLIDE_TICKS,
				"the glide is bounded");
		AbilityRouter.handleInput(player, 8, true); // N again = fold up
		helper.assertFalse(ElasticityHandlers.gliding(player), "pressing N again ends the glide");
		helper.assertTrue(onCooldown(player, elastic, AbilitySlot.SLOT_8), "ending the glide starts its cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- 18 Density

	@GameTest(template = EMPTY_STRUCTURE)
	public void intangibleDodgeIsImmune(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grantActive(player, RevampBatchE.P18);
		Zombie z = mobInFront(helper, player, EntityType.ZOMBIE, 2);
		AbilityRouter.handleInput(player, 7, true); // H
		helper.assertTrue(DensityManipulationHandlers.intangible(player), "Intangible Dodge should be running");
		helper.assertFalse(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(player, player.damageSources().mobAttack(z), 6.0f),
				"attacks pass straight through while intangible");
		for (int i = 0; i < DensityManipulationHandlers.DODGE_TICKS + 2; i++) {
			ExperimentalPowers.serverTick(player);
		}
		helper.assertFalse(DensityManipulationHandlers.intangible(player), "it lasts only half a second");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crushingTouchMakesTheNextHitSuperDense(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grantActive(player, RevampBatchE.P18);
		Zombie z = mobInFront(helper, player, EntityType.ZOMBIE, 2);
		AbilityRouter.handleInput(player, 8, true); // N = arm
		helper.assertTrue(DensityManipulationHandlers.crushArmed(player), "Crushing Touch should arm the hand");

		player.attack(z); // a plain melee hit

		helper.assertTrue(z.hasEffect(CrushingDensityEffect.HOLDER), "the hit target becomes super-dense");
		helper.assertFalse(DensityManipulationHandlers.crushArmed(player), "the armed hand is spent");
		helper.assertTrue(z.getAttributeValue(Attributes.JUMP_STRENGTH) < 0.01, "a super-dense target cannot jump");
		double slowSpeed = z.getAttributeValue(Attributes.MOVEMENT_SPEED);
		z.removeEffect(CrushingDensityEffect.HOLDER);
		helper.assertTrue(z.getAttributeValue(Attributes.MOVEMENT_SPEED) > slowSpeed, "the modifiers come off with the effect");
		helper.assertTrue(z.getAttributeValue(Attributes.JUMP_STRENGTH) > 0.1, "jump restored when it ends");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void crushingDensityDragsFliersDown(GameTestHelper helper) {
		net.minecraft.world.entity.animal.Bee bee = helper.spawn(EntityType.BEE, new BlockPos(2, 6, 2));
		bee.setNoGravity(true);
		double startY = bee.getY();
		bee.addEffect(new net.minecraft.world.effect.MobEffectInstance(CrushingDensityEffect.HOLDER, 100, 0));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(bee.getY() < startY - 1.0 || bee.onGround(), "a crushed flier drops out of the air, y "
					+ bee.getY() + " from " + startY);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void densityPhaseReserveIsBigger(GameTestHelper helper) {
		helper.assertTrue(DensityManipulationHandlers.MAX_PHASE >= 114.9f, "Phase holds 15% more than the old 100");
		ServerPlayer player = player(helper);
		Power d = grantActive(player, RevampBatchE.P18);
		ExperimentalPowers.serverTick(player);
		helper.assertTrue(ExperimentalPowers.getResource(player, d, "density") >= 99.0f, "density is seeded for the HUD gauge");
		helper.assertFalse(DensityManipulationHandlers.shellVisible(player), "no shell at a plain 100%");
		AbilityRouter.handleInput(player, 6, true); // C = Zero Density
		helper.assertTrue(DensityManipulationHandlers.shellVisible(player), "the density shell shows when light");
		helper.succeed();
	}

	// ---------------------------------------------------------------- 22 Plant

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void thornSentryPlantsAndShootsHostiles(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power plant = grantActive(player, RevampBatchE.P22);
		BlockPos origin = new BlockPos(2, 2, 1);
		Vec3 pp = Vec3.atBottomCenterOf(helper.absolutePos(origin));
		player.setPos(pp.x, pp.y, pp.z);
		player.setYRot(0.0f);
		player.setXRot(60.0f);
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(2, 2, 6));
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(6, 2, 2));

		AbilityRouter.handleInput(player, 7, true); // H = Thorn Sentry
		var sentries = helper.getLevel().getEntities(PlantEntities.THORN_SENTRY, e -> player.getUUID().equals(e.ownerId()));
		helper.assertTrue(sentries.size() == 1, "one sentry should be planted, found " + sentries.size());
		helper.assertTrue(onCooldown(player, plant, AbilitySlot.SLOT_7), "planting starts the cooldown");
		helper.assertTrue(ThornSentryEntity.isHostileTo(husk, player), "hostiles are targets");
		helper.assertFalse(ThornSentryEntity.isHostileTo(cow, player), "passive animals are not");
		helper.assertFalse(ThornSentryEntity.isHostileTo(player, player), "the owner never is");
		ServerPlayer other = player(helper);
		helper.assertFalse(ThornSentryEntity.isHostileTo(other, player), "no player ever is (owner, squad-mate or stranger)");

		PlantManipulationHandlers.plantSentry(player); // a second one replaces the first
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getLevel().getEntities(PlantEntities.THORN_SENTRY, e -> player.getUUID().equals(e.ownerId())).size() == 1,
					"only one sentry per player");
		});
		helper.runAfterDelay(60, () -> {
			var list = helper.getLevel().getEntities(PlantEntities.THORN_SENTRY, e -> player.getUUID().equals(e.ownerId()));
			helper.assertTrue(!list.isEmpty() && list.get(0).shots() > 0, "the sentry should have fired at the husk");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sporeCloudHealsAlliesAndPoisonsEnemies(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power plant = grantActive(player, RevampBatchE.P22);
		var husk = mobInFront(helper, player, EntityType.VINDICATOR, 2); // not undead: undead shrug off poison
		player.setHealth(10.0f);

		AbilityRouter.handleInput(player, 8, true); // N = Spore Cloud
		helper.assertTrue(ExperimentalPowers.getResource(player, plant, "spore_ticks") > 0.5f, "the cloud should be running");
		PlantManipulationHandlers.pulseSpores(player, player.position().add(0, 0.5, 1.0));

		helper.assertTrue(player.getHealth() > 10.0f, "the cloud heals its owner");
		helper.assertTrue(husk.hasEffect(MobEffects.POISON), "and poisons enemies inside it");
		helper.assertTrue(husk.hasEffect(MobEffects.BLINDNESS), "and blinds them");
		helper.assertFalse(player.hasEffect(MobEffects.POISON), "never the owner");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void naturesBlessingRaisesTheBarkShell(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grantActive(player, RevampBatchE.P22);
		helper.assertFalse(PlantManipulationHandlers.blessingOn(player), "off by default");
		AbilityRouter.handleInput(player, 6, true);
		helper.assertTrue(PlantManipulationHandlers.blessingOn(player), "C turns on the bark-and-leaf skin");
		helper.succeed();
	}

	// ---------------------------------------------------------------- 27 Size

	@GameTest(template = EMPTY_STRUCTURE)
	public void shrinkPunchShrinksTheTargetTemporarily(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power size = grantActive(player, RevampBatchE.P27);
		Zombie z = mobInFront(helper, player, EntityType.ZOMBIE, 2);
		double scaleBefore = z.getAttributeValue(Attributes.SCALE);

		AbilityRouter.handleInput(player, 7, true); // H = Shrink Punch

		helper.assertTrue(z.hasEffect(ShrunkenEffect.HOLDER), "Shrink Punch applies Shrunken");
		helper.assertTrue(z.getAttributeValue(Attributes.SCALE) < scaleBefore * 0.6, "the target is half size");
		helper.assertTrue(z.getEffect(ShrunkenEffect.HOLDER).getDuration() <= SizeHandlers.SHRINK_TICKS, "for 8 s at most");
		helper.assertTrue(onCooldown(player, size, AbilitySlot.SLOT_7), "Shrink Punch goes on cooldown");
		z.removeEffect(ShrunkenEffect.HOLDER);
		helper.assertTrue(Math.abs(z.getAttributeValue(Attributes.SCALE) - scaleBefore) < 1.0e-6, "the scale is restored cleanly");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mountNeedsTinyOrGiant(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power size = grantActive(player, RevampBatchE.P27);
		Cow cow = mobInFront(helper, player, EntityType.COW, 2);
		AbilityRouter.handleInput(player, 8, true); // N in normal form
		helper.assertFalse(player.isPassenger(), "normal form cannot mount");
		helper.assertFalse(onCooldown(player, size, AbilitySlot.SLOT_8), "and it costs nothing");
		helper.assertTrue(cow.isAlive(), "cow untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tinyRiderIsReleasedOnFormChange(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power size = grantActive(player, RevampBatchE.P27);
		Cow cow = mobInFront(helper, player, EntityType.COW, 2);
		AbilityRouter.handleInput(player, 3, true); // X = Tiny Form
		helper.assertTrue(SizeHandlers.mountTarget(player, cow), "a tiny hero can ride a cow");
		helper.assertTrue(player.getVehicle() == cow, "riding the cow");
		helper.assertTrue(SizeHandlers.riding(player), "the ride is tracked");

		AbilityRouter.handleInput(player, 3, true); // X again = back to normal
		ExperimentalPowers.serverTick(player);
		helper.assertFalse(player.isPassenger(), "leaving Tiny form dismounts");
		helper.assertFalse(SizeHandlers.riding(player), "and clears the ride");
		helper.assertTrue(onCooldown(player, size, AbilitySlot.SLOT_8), "the release starts Mount's cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void largeFormCarriesAndThrows(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grantActive(player, RevampBatchE.P27);
		Vec3 mid = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 4)));
		player.setPos(mid.x, mid.y, mid.z);
		AbilityRouter.handleInput(player, 6, true); // C = Large Form
		Chicken chicken = helper.spawn(EntityType.CHICKEN, new BlockPos(4, 1, 6));
		helper.assertTrue(SizeHandlers.mountTarget(player, chicken), "a large hero can pick up a chicken");
		helper.assertTrue(SizeHandlers.carrying(player), "carrying");
		ExperimentalPowers.serverTick(player);
		helper.assertTrue(chicken.getY() > player.getY() + 0.5, "the chicken is held up in the hand");

		AbilityRouter.handleInput(player, 8, true); // N again = throw
		helper.assertFalse(SizeHandlers.carrying(player), "pressing N again lets go");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carriedMobIsReleasedWhenThePowerGoes(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Power size = grantActive(player, RevampBatchE.P27);
		Vec3 mid = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 4)));
		player.setPos(mid.x, mid.y, mid.z);
		AbilityRouter.handleInput(player, 6, true);
		Chicken chicken = helper.spawn(EntityType.CHICKEN, new BlockPos(4, 1, 6));
		SizeHandlers.mountTarget(player, chicken);
		ExperimentalPowers.forget(player, size);
		helper.assertFalse(SizeHandlers.carrying(player), "no carry survives the power");
		helper.assertTrue(chicken.hasEffect(MobEffects.SLOW_FALLING) || chicken.onGround(), "and the mob is let down safely");
		helper.succeed();
	}
}

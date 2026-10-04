package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;
import com.projecthero.mod.flash.SpeedForce;
import com.projecthero.mod.grave.CurseSource;
import com.projecthero.mod.grave.GraveboundCurse;
import com.projecthero.mod.grave.GraveboundEffect;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedMoves;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

/** v0.14.13: Hero-Tier Super Speed + the Speed Force origin, Gravebound as a status effect, Flash Suit mending in the ring. */
public class V01413GameTests implements FabricGameTest {

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	private static void catalysts(ServerPlayer p) {
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));
		p.addEffect(new MobEffectInstance(MobEffects.JUMP, 600));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void speedForceNeedsAllThreeEffects(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));
		helper.assertTrue(SpeedForce.tryAwaken(p, true) == SpeedForce.Outcome.NOT_READY, "no Jump Boost, no surge");
		helper.assertFalse(SpeedForce.hasPower(p), "and no Super Speed");
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST), "the effects it did have are left alone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void speedForceRejectionBurnsTheEffects(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		catalysts(p);
		helper.assertTrue(SpeedForce.tryAwaken(p, false) == SpeedForce.Outcome.REJECTED, "the failed half of the roll");
		helper.assertFalse(SpeedForce.hasPower(p), "rejected = no power");
		helper.assertFalse(p.hasEffect(MobEffects.DAMAGE_BOOST) || p.hasEffect(MobEffects.MOVEMENT_SPEED) || p.hasEffect(MobEffects.JUMP),
				"all three effects are burned off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void speedForceAwakensTheSuperSpeedMutation(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		com.projecthero.mod.ironman.TonyStark.grant(p);
		catalysts(p);
		helper.assertTrue(SpeedForce.tryAwaken(p, true) == SpeedForce.Outcome.AWAKENED, "the successful half of the roll");
		helper.assertTrue(SpeedForce.hasPower(p) && !HeroTiers.holdsHero(p, "super_speed"), "v0.14.21: Super Speed, held as a mutation");
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.hasPower(p), "a mutation replaces the hero they had");
		helper.assertTrue(HeroTiers.hasExperimental(p) && HeroTiers.heroCount(p) == 0, "and it counts as a mutation");
		helper.assertTrue(ExperimentalPowers.getActive(p) == SpeedForce.power(), "its keys are live at once");
		helper.assertFalse(p.hasEffect(MobEffects.JUMP), "the surge used the effects up");
		var regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 2, "Regeneration III");
		helper.assertTrue(SpeedForce.tryAwaken(p, true) == SpeedForce.Outcome.ALREADY, "a second surge does nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void superSpeedIsAMutationAgain(GameTestHelper helper) {
		// v0.14.21: back to an experimental power (it was Hero-Tier v0.14.13-0.14.20)
		var speed = Powers.byKey(SuperSpeedHandlers.KEY);
		helper.assertTrue(Powers.isMutation(speed) && !Powers.isHeroTier(speed), "a mutation, not Hero-Tier");
		helper.assertTrue(Powers.mutations().contains(speed), "every mutation path offers it");
		helper.assertFalse(com.projecthero.mod.hero.PowerGrants.HERO_TIER_KEYS.contains("super_speed")
				|| HeroTiers.HERO_KEYS.contains("super_speed"), "no longer a hero key");
		ServerPlayer p = player(helper);
		helper.assertTrue(com.projecthero.mod.hero.PowerGrants.grantExperimental(p, speed), "a random serum / mutation grant gives it");
		helper.assertTrue(SpeedForce.hasPower(p) && HeroTiers.hasExperimental(p), "held as a mutation");
		helper.assertTrue(com.projecthero.mod.hero.PowerGrants.grantExperimental(p, Powers.byKey("power_02_laser_vision")),
				"another mutation can still be gained");
		helper.assertFalse(SpeedForce.hasPower(p), "and it replaces Super Speed (the solo rule)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void superSpeedSerumTakesHoldLikeAnyMutation(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		var speed = Powers.byKey(SuperSpeedHandlers.KEY);
		int amp = com.projecthero.mod.hero.mutation.ModSerums.amplifierFor(speed);
		p.addEffect(new MobEffectInstance(com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION, 1200, amp, false, true, true));
		com.projecthero.mod.hero.mutation.MutationManager.serverTick(p);
		helper.assertTrue(speed.key().equals(ExperimentalPowers.state(p).pendingMutationPower), "the Hypermetabolic Serum takes hold");
		com.projecthero.mod.hero.mutation.MutationManager.triggerExposure(p,
				com.projecthero.mod.hero.MutationTrigger.Kind.ELECTRICAL_DISCHARGE);
		helper.assertTrue(ExperimentalPowers.owns(p, speed), "and the electrical exposure grants Super Speed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void speedForceRespectsFullMutationSlots(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		int n = 0;
		for (var pw : Powers.all()) { // (grant itself is ungated, so this fills any configured capacity)
			if (n >= ExperimentalPowers.capacity()) {
				break;
			}
			if (!pw.key().equals(SuperSpeedHandlers.KEY)) {
				ExperimentalPowers.grant(p, pw);
				n++;
			}
		}
		helper.assertTrue(ExperimentalPowers.atCapacity(p), "precondition: every mutation slot is full (" + n + ")");
		catalysts(p);
		helper.assertTrue(SpeedForce.tryAwaken(p, true) == SpeedForce.Outcome.NOT_READY, "no room: the surge does nothing");
		helper.assertFalse(SpeedForce.hasPower(p), "no Super Speed");
		helper.assertTrue(p.hasEffect(MobEffects.JUMP) && p.hasEffect(MobEffects.DAMAGE_BOOST), "and the effects are kept");
		helper.assertTrue(ExperimentalPowers.ownedCount(p) == n, "nothing was lost");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anOldHeroTierSpeedsterKeepsSuperSpeedAsAMutation(GameTestHelper helper) {
		// a v0.14.13-0.14.20 save: Super Speed owned through the mutation state, plus "super_speed" in the Primary order
		ServerPlayer p = player(helper);
		var speed = Powers.byKey(SuperSpeedHandlers.KEY);
		ExperimentalPowers.grant(p, speed);
		ExperimentalPowers.setActive(p, speed);
		p.setAttached(ModAttachments.PRIMARY_ORDER, "super_speed");
		helper.assertFalse(ExperimentalPowers.pruneRemovedPowers(p), "join: nothing is pruned");
		HeroTiers.enforceLimit(p); // join
		helper.assertTrue(ExperimentalPowers.owns(p, speed) && ExperimentalPowers.getActive(p) == speed, "still a speedster, keys live");
		helper.assertTrue(HeroTiers.hasExperimental(p) && HeroTiers.heroCount(p) == 0 && !HeroTiers.hasHeroTier(p),
				"now counted as a mutation, not a hero");
		helper.assertFalse(p.getAttachedOrCreate(ModAttachments.PRIMARY_ORDER).contains("super_speed"),
				"the stale Primary-order entry is dropped");
		// and the mutation rules apply from now on: a hero power replaces it
		com.projecthero.mod.ironman.TonyStark.grant(p);
		helper.assertFalse(SpeedForce.hasPower(p), "gaining a hero replaces the mutation");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void speedModeWalksAtHalfItsSprint(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		SpeedForce.grant(p);
		ExperimentalPowers.serverTick(p);
		AbilityRouter.handleInput(p, 6, true); // C: Speed Mode on
		AbilityRouter.handleInput(p, 6, false);
		p.setSprinting(false);
		ExperimentalPowers.serverTick(p);
		var walk = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ProjectHeroMod.id("speed_mode_speed"));
		helper.assertTrue(walk != null && walk.amount() == SuperSpeedHandlers.SPEED_MODE_WALK_BONUS, "walking: the ~20 b/s tier");
		p.setSprinting(true);
		ExperimentalPowers.serverTick(p);
		var run = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ProjectHeroMod.id("speed_mode_speed"));
		helper.assertTrue(run != null && run.amount() == SuperSpeedHandlers.SPEED_MODE_BONUS, "sprinting: the ~40 b/s tier");
		helper.assertTrue(SuperSpeedMoves.SWEEP_CD == 200, "Speed Sweep is a 10 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void graveboundShowsAsAStatusEffectThatMilkCannotRemove(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		helper.assertTrue(GraveboundCurse.apply(p, CurseSource.GRAVEYARD), "cursed");
		MobEffectInstance e = p.getEffect(GraveboundEffect.HOLDER);
		helper.assertTrue(e != null && Math.abs(e.getDuration() - GraveboundCurse.remainingTicks(p)) <= 40,
				"the effect shows the time left");
		p.removeAllEffects(); // milk
		for (int i = 0; i < 20; i++) {
			GraveboundCurse.tick(p);
		}
		helper.assertTrue(p.hasEffect(GraveboundEffect.HOLDER), "the effect comes back -- the curse is still on");
		GraveboundCurse.clear(p, false);
		helper.assertFalse(p.hasEffect(GraveboundEffect.HOLDER), "breaking the curse removes it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void kryptoniteMeteorIsAWorldGeneratedCrater(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
		var sets = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
		helper.assertTrue(structures.containsKey(ProjectHeroMod.id("kryptonite_crater")), "the crater structure loads");
		helper.assertTrue(sets.containsKey(ProjectHeroMod.id("kryptonite_crater")), "and is placed in the world");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flashSuitMendsInsideTheRing(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		ItemStack chest = new ItemStack(FlashSuit.CHESTPLATE);
		chest.setDamageValue(50);
		ItemStack ring = new ItemStack(FlashSuit.RING);
		ring.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(chest)));
		p.setAttached(ModAttachments.FLASH_RING, ring);
		for (int i = 0; i < 5; i++) {
			FlashRing.repairStored(p);
		}
		ItemStack stored = FlashRing.worn(p).get(DataComponents.CONTAINER).nonEmptyItemsCopy().iterator().next();
		helper.assertTrue(stored.getDamageValue() == 50 - 5 * FlashRing.REPAIR_AMOUNT, "mended 1 a step, got " + stored.getDamageValue());
		helper.succeed();
	}
}

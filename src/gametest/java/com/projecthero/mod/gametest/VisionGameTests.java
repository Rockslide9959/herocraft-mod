package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.ultron.item.UltronItems;
import com.projecthero.mod.vision.Vision;
import com.projecthero.mod.vision.VisionAbilityManager;
import com.projecthero.mod.vision.VisionConfig;
import com.projecthero.mod.vision.VisionCradle;
import com.projecthero.mod.vision.VisionDamage;
import com.projecthero.mod.vision.VisionFlight;
import com.projecthero.mod.vision.VisionMoves;
import com.projecthero.mod.vision.data.VisionAttachments;
import com.projecthero.mod.vision.data.VisionState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Vision: the Cradle and the grant, the synthezoid body (health, resistance, immunities, no hunger), the Mind Stone bar (step
 * sizes), the Human Hologram, Phase Dash and Intangibility (nothing lands, he can't attack, he can't turn solid in a wall, the push-out),
 * Diamond Density, and the moves' costs and damage. Mock players are not ticked by the server, so the tests call the power's pieces
 * directly. Mob hits are asserted as ratios of the expected damage (the world may be on Hard); every mock player is discarded at the end.
 */
public class VisionGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer vision(GameTestHelper helper, double x, double z) {
		ServerPlayer p = mock(helper, x, z);
		helper.assertTrue(Vision.grant(p), "the grant works");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<VisionState> change) {
		VisionState n = Vision.state(p).copy();
		change.accept(n);
		p.setAttached(VisionAttachments.STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2.0, z));
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zb.setHealth((float) maxHealth);
		return zb;
	}

	private static void look(ServerPlayer p, LivingEntity at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.position().add(0, at.getBbHeight() * 0.55, 0));
	}

	private static float ratio(float before, LivingEntity e, float expected) {
		return (before - e.getHealth()) / expected;
	}

	private static boolean near(float ratio) {
		return ratio > 0.85f && ratio < 1.15f;
	}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1.0e-3;
	}

	// ---------------------------------------------------------------- the power

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void cradleAwakensVisionAsThePrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		com.projecthero.mod.kryptonian.Kryptonian.grant(p);
		BlockPos cradle = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.getLevel().setBlockAndUpdate(cradle, VisionCradle.CRADLE.defaultBlockState());
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
		helper.assertTrue(!VisionCradle.awaken(p, cradle, InteractionHand.MAIN_HAND) && !Vision.hasPower(p), "the Cradle needs the Mind Stone");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UltronItems.MIND_STONE));
		helper.assertTrue(VisionCradle.awaken(p, cradle, InteractionHand.MAIN_HAND), "the Mind Stone wakes the body");
		helper.assertTrue(Vision.hasPower(p) && HeroTiers.holdsHero(p, Vision.KEY), "Vision is a Hero-Tier power");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the stone is used up");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(p), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(!PowerGrants.missingHeroTiers(p).contains(Vision.KEY) && PowerGrants.NOT_FROM_SERUMS.contains(Vision.KEY),
				"random serums never give it");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UltronItems.MIND_STONE));
		helper.assertTrue(!VisionCradle.awaken(p, cradle, InteractionHand.MAIN_HAND) && !p.getMainHandItem().isEmpty(),
				"a second stone is refused and kept");
		helper.assertTrue(!Vision.grant(p), "a second grant is refused");
		Vision.revoke(p);
		helper.assertTrue(!Vision.hasPower(p) && p.getMaxHealth() == 20f, "revoked: no power, 20 health again");
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void synthezoidBody(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		helper.assertTrue(p.getMaxHealth() == 45f && p.getHealth() == 45f, "45 health, got " + p.getMaxHealth());
		helper.assertTrue(close(VisionDamage.reduced(p, 10f), 7f), "30% off every hit");
		var src = p.damageSources();
		helper.assertTrue(VisionDamage.immune(p, src.fall()) && VisionDamage.immune(p, src.drown()) && VisionDamage.immune(p, src.inWall())
				&& VisionDamage.immune(p, src.wither()), "no fall, drowning, suffocation or wither damage");
		helper.assertTrue(!VisionDamage.immune(p, src.generic()), "ordinary damage still lands (reduced)");
		p.getFoodData().setFoodLevel(3);
		p.getFoodData().setSaturation(5f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
		p.setAirSupply(0);
		Vision.tickBody(p);
		helper.assertTrue(p.getFoodData().getFoodLevel() == VisionConfig.FOOD_LEVEL && p.getFoodData().getSaturationLevel() == 0f,
				"no hunger: food held at 18, no saturation");
		helper.assertTrue(!p.hasEffect(MobEffects.POISON), "poison is cleared");
		helper.assertTrue(p.getAirSupply() == p.getMaxAirSupply(), "he never runs out of air");
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mindStoneBarRegenerates(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		Vision.setEnergy(p, 50f);
		float step = Vision.regenPerSecond(Vision.sun(p)) / 20f;
		Vision.tickBar(p, Vision.state(p));
		helper.assertTrue(close(Vision.energy(p), 50f + step), "one tick refills regen/20 (" + step + ")");
		helper.assertTrue(VisionConfig.REGEN_SUN > VisionConfig.REGEN_SHADE && VisionConfig.REGEN_SHADE > VisionConfig.REGEN_DARK,
				"faster in the sun, slower in the dark");
		edit(p, s -> s.intangible = true);
		float before = Vision.energy(p);
		Vision.tickBar(p, Vision.state(p));
		helper.assertTrue(close(Vision.energy(p), before), "no regeneration while something drains it");
		Vision.tickToggles(p);
		helper.assertTrue(close(Vision.energy(p), before - VisionConfig.PHASE_DRAIN_PER_SECOND / 20f), "Intangibility drains 8 a second");
		edit(p, s -> s.intangible = false);
		Vision.revoke(p);
		p.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- V: the hologram

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void hologramTogglesAndPowersStillWork(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		Zombie zb = zombie(helper, 3.5, 1.5, 100);
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.HOLOGRAM), "V projects the hologram");
		helper.assertTrue(Vision.hologram(p) && Vision.state(p).holoChangeAt == now, "the hologram is up, its shimmer starts now");
		helper.assertTrue(Vision.cooldownRemaining(p, VisionAbilityManager.HOLOGRAM) == VisionConfig.HOLOGRAM_TOGGLE_COOLDOWN, "a short cooldown");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX), "it's free");
		float before = zb.getHealth();
		VisionMoves.punchHit(p, zb);
		helper.assertTrue(near(ratio(before, zb, VisionConfig.PUNCH_DAMAGE)), "powers still work under the hologram");
		helper.assertTrue(Vision.hologram(p), "a move doesn't drop it (unlike Cyborg's)");
		Vision.clearCooldowns(p);
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.HOLOGRAM) && !Vision.hologram(p), "V again drops it");
		zb.discard();
		p.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- X / Shift+V: phasing

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void phaseDashIsIntangible(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		Zombie zb = zombie(helper, 5.5, 5.5, 20);
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.DASH), "X dashes");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX - VisionConfig.DASH_COST), "8 energy");
		helper.assertTrue(Vision.dashing(p) && Vision.phased(p), "intangible during the dash");
		helper.assertTrue(close(VisionMoves.dashStep(), 2.0), "10 blocks over 5 ticks: 2 a tick");
		float hp = p.getHealth();
		boolean allowed = VisionDamage.allowDamage(p, p.damageSources().mobAttack(zb), 6f);
		helper.assertTrue(!allowed && p.getHealth() == hp, "a hit during the dash doesn't land");
		helper.assertTrue(VisionDamage.allowDamage(p, p.damageSources().genericKill(), 1f), "/kill still works");
		helper.assertTrue(!VisionMoves.use(p, VisionAbilityManager.DASH), "on cooldown (1 s)");
		zb.discard();
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void intangibilityBlocksHitsBothWays(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		Zombie zb = zombie(helper, 3.5, 1.5, 40);
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.PHASE) && Vision.intangible(p), "Shift+V: intangible");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX), "it drains over time, nothing up front (10 needed to start)");
		helper.assertTrue(!VisionDamage.allowDamage(p, p.damageSources().mobAttack(zb), 6f), "hits pass through him");
		float before = zb.getHealth();
		zb.hurt(p.damageSources().playerAttack(p), 6f);
		helper.assertTrue(zb.getHealth() == before, "his fists pass through too");
		look(p, zb);
		helper.assertTrue(!VisionMoves.use(p, VisionAbilityManager.PUNCH) && zb.getHealth() == before, "no attacks while phased");
		helper.assertTrue(Vision.wallPhasing(p), "he walks through walls");
		// can't turn solid inside a wall; an empty bar pushes him out
		BlockPos feet = p.blockPosition();
		helper.getLevel().setBlockAndUpdate(feet, Blocks.STONE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(feet.above(), Blocks.STONE.defaultBlockState());
		helper.assertTrue(Vision.insideWall(p), "standing in the stone");
		Vision.clearCooldowns(p);
		helper.assertTrue(!VisionMoves.use(p, VisionAbilityManager.PHASE) && Vision.intangible(p), "refused inside a wall");
		Vision.setEnergy(p, 0.1f);
		Vision.tickToggles(p);
		helper.assertTrue(!Vision.intangible(p) && !Vision.insideWall(p), "the bar ran dry: solid again, pushed out of the wall");
		helper.getLevel().setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
		zb.discard();
		p.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+C: Diamond Density

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void diamondDensityResists(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		VisionFlight.start(p);
		helper.assertTrue(Vision.isFlying(p), "flying");
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.DENSITY) && Vision.dense(p), "Shift+C: Diamond Density");
		helper.assertTrue(!Vision.isFlying(p), "too dense to fly: it drops him out of the sky");
		helper.assertTrue(close(VisionDamage.reduced(p, 10f), 3.5f), "65% off in all");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "no knockback");
		helper.assertTrue(close(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.0 + VisionConfig.MELEE_BONUS + VisionConfig.DENSITY_MELEE),
				"+6 melee on top of the +6");
		VisionFlight.toggle(p);
		helper.assertTrue(!Vision.isFlying(p), "flight won't start while dense");
		float before = Vision.energy(p);
		Vision.tickToggles(p);
		helper.assertTrue(close(Vision.energy(p), before - VisionConfig.DENSITY_DRAIN_PER_SECOND / 20f), "3 energy a second");
		Vision.clearCooldowns(p);
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.DENSITY) && !Vision.dense(p), "off again");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0, "knockback back to normal");
		p.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- the moves

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mindStoneBeamDamage(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		Zombie zb = zombie(helper, 4.5, 1.5, 100);
		look(p, zb);
		helper.assertTrue(VisionMoves.startHold(p, VisionAbilityManager.BEAM) && Vision.beaming(p), "R held: the beam burns");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX - VisionConfig.BEAM_START_COST), "6 to start");
		float before = zb.getHealth();
		helper.assertTrue(VisionMoves.beamHit(p, zb), "a beam hit lands");
		helper.assertTrue(near(ratio(before, zb, VisionConfig.BEAM_DAMAGE)), "7 a hit");
		VisionMoves.release(p, VisionAbilityManager.BEAM);
		helper.assertTrue(!Vision.beaming(p) && Vision.cooldownRemaining(p, VisionAbilityManager.BEAM) == VisionConfig.BEAM_COOLDOWN,
				"release: off, 1.5 s cooldown");
		zb.discard();
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void densityDiveThroughATarget(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		Zombie zb = zombie(helper, 4.5, 4.5, 100);
		look(p, zb);
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.DIVE), "Shift+X: the dive");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX - VisionConfig.DIVE_COST), "25 energy");
		helper.assertTrue(VisionMoves.runOf(p).diveMode() == VisionMoves.DIVE_TARGET && Vision.phased(p), "at the target, phased the whole way");
		float before = zb.getHealth();
		VisionMoves.diveThrough(p, zb);
		helper.assertTrue(near(ratio(before, zb, VisionConfig.DIVE_DAMAGE)), "22 when he turns solid inside it");
		helper.assertTrue(zb.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "stunned");
		helper.assertTrue(VisionMoves.runOf(p).diveMode() == VisionMoves.DIVE_NONE, "the dive is over");
		helper.assertTrue(close(VisionMoves.meteorDamage(0), VisionConfig.METEOR_DAMAGE)
				&& close(VisionMoves.meteorDamage(10) - VisionMoves.meteorDamage(9), VisionConfig.METEOR_PER_BLOCK)
				&& close(VisionMoves.meteorDamage(200), VisionConfig.METEOR_DAMAGE + VisionConfig.METEOR_BONUS_MAX),
				"meteor drop: 14, +0.6 a block, at most +16");
		zb.discard();
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void phaseStrikeIgnoresArmour(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		Zombie zb = zombie(helper, 3.5, 1.5, 100);
		zb.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		zb.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		look(p, zb);
		float before = zb.getHealth();
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.STRIKE), "Shift+G: Phase Strike");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX - VisionConfig.STRIKE_COST), "15 energy");
		helper.assertTrue(near(ratio(before, zb, VisionConfig.STRIKE_DAMAGE)), "14 through diamond armour, got " + (before - zb.getHealth()));
		helper.assertTrue(zb.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && zb.hasEffect(MobEffects.WEAKNESS), "stunned");
		zb.discard();
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void energyCostsAndAreaMoves(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		Zombie a = zombie(helper, 3.5, 1.5, 100);
		Zombie b = zombie(helper, 5.5, 1.5, 100);
		// Z: Mind Stone Pulse
		float ba = a.getHealth();
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.PULSE), "Z: the pulse");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX - VisionConfig.PULSE_COST), "25 energy");
		helper.assertTrue(near(ratio(ba, a, VisionConfig.PULSE_DAMAGE)) && a.hasEffect(MobEffects.GLOWING), "12 and Glowing");
		// Shift+R: Solar Lance pays up front and charges
		Vision.setEnergy(p, VisionConfig.ENERGY_MAX);
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.LANCE) && Vision.chargingLance(p), "Shift+R charges the lance");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX - VisionConfig.LANCE_COST), "30 energy");
		// the lance and Unleashed pierce a line
		a.setHealth(100f);
		b.setHealth(100f);
		a.invulnerableTime = 0;
		b.invulnerableTime = 0;
		Vec3 from = p.getEyePosition();
		Vec3 end = new Vec3(b.getX() + 2, b.getY() + 1.0, b.getZ());
		int hit = VisionMoves.unleashHit(p, new Vec3(from.x, a.getY() + 1.0, a.getZ()), end);
		helper.assertTrue(hit == 2, "Unleashed hits everything along the beam, hit " + hit);
		helper.assertTrue(near(ratio(100f, a, VisionConfig.UNLEASH_DAMAGE)) && near(ratio(100f, b, VisionConfig.UNLEASH_DAMAGE)), "7 each");
		// Shift+Z needs 80
		Vision.setEnergy(p, 79f);
		helper.assertTrue(!VisionMoves.use(p, VisionAbilityManager.UNLEASH) && !Vision.unleashing(p), "Unleashed needs 80 energy");
		Vision.setEnergy(p, VisionConfig.ENERGY_MAX);
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.UNLEASH) && Vision.unleashing(p), "Shift+Z: Unleashed");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX - VisionConfig.UNLEASH_COST), "80 energy");
		helper.assertTrue(!VisionMoves.use(p, VisionAbilityManager.PULSE), "nothing else while it channels");
		Vision.clearTransient(p);
		helper.assertTrue(!Vision.unleashing(p), "it ends with the transient state");
		a.discard();
		b.discard();
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void gripHoldsAndThrows(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		Zombie zb = zombie(helper, 3.5, 1.5, 40);
		look(p, zb);
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.GRIP) && VisionMoves.gripped(p) == zb, "C grabs it");
		helper.assertTrue(close(Vision.energy(p), VisionConfig.ENERGY_MAX - VisionConfig.GRIP_COST), "12 energy");
		helper.assertTrue(VisionMoves.use(p, VisionAbilityManager.GRIP) && VisionMoves.gripped(p) == null, "C again throws it");
		helper.assertTrue(zb.getDeltaMovement().length() > 1.5, "hurled");
		helper.assertTrue(Vision.cooldownRemaining(p, VisionAbilityManager.GRIP) == VisionConfig.GRIP_COOLDOWN, "3 s cooldown after the throw");
		Zombie big = zombie(helper, 3.5, 3.5, 300);
		look(p, big);
		Vision.clearCooldowns(p);
		helper.assertTrue(!VisionMoves.grippable(p, big), "too big to grab (over 120 health)");
		zb.discard();
		big.discard();
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void keysFollowTheHouseRules(GameTestHelper helper) {
		ServerPlayer p = vision(helper, 1.5, 1.5);
		helper.assertTrue(VisionAbilityManager.DASH.equals(VisionAbilityManager.moveFor(com.projecthero.mod.hero.AbilitySlot.SLOT_3, false))
				&& VisionAbilityManager.DIVE.equals(VisionAbilityManager.moveFor(com.projecthero.mod.hero.AbilitySlot.SLOT_3, true)),
				"X = Phase Dash, Shift+X = Density Dive");
		helper.assertTrue(VisionAbilityManager.HOLOGRAM.equals(VisionAbilityManager.moveFor(com.projecthero.mod.hero.AbilitySlot.SLOT_5, false)),
				"V = the hologram");
		helper.assertTrue(VisionAbilityManager.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_7).length == 0
				&& VisionAbilityManager.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_8).length == 0, "H and N do nothing");
		p.hurt(p.damageSources().source(DamageTypes.FALL), 10f);
		helper.assertTrue(p.getHealth() == p.getMaxHealth(), "no fall damage");
		p.discard();
		helper.succeed();
	}
}

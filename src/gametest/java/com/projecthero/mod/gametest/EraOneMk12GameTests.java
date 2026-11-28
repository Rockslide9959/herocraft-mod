package com.projecthero.mod.gametest;

import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.IronManComicRouter;
import com.projecthero.mod.ironman.ability.IronManComicState;
import com.projecthero.mod.ironman.ability.IronManDash;
import com.projecthero.mod.ironman.ability.era1.EraOneMk12State;
import com.projecthero.mod.ironman.ability.era1.EraOneMk1Moves;
import com.projecthero.mod.ironman.ability.era1.EraOneMk2Moves;
import com.projecthero.mod.ironman.ability.era1.IronManEraOne;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Iron Man Era I: every Mk I Cave-Forged / Mk II Golden Avenger move (the MK12 rows of
 * {@code docs/IRONMAN_ERA1_PLAN.md}) does what its row says -- its damage, knockback, effect, duration and fall safety.
 * Each test is its own batch: the area moves (repel, pull, glare...) would otherwise reach a neighbour test's dummies.
 */
public class EraOneMk12GameTests implements FabricGameTest {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };
	private static final float HP = 200f;
	private static final float EPS = 0.05f;
	// ------------------------------------------------------------------ helpers

	/** A Tony Stark mock player in the given suit at (1, 1, 3), facing +X. */
	private static ServerPlayer tony(GameTestHelper h, String suit) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 3));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		face(p, 0f);
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(suit, TYPES[i])));
		}
		TonyStark.setActiveSuit(p, suit);
		IronManEnergy.setIntegrity(p, suit, IronManEnergy.maxIntegrity(suit));
		p.setOnGround(true);
		return p;
	}

	/** Face +X, pitched {@code pitch} degrees down. */
	private static void face(ServerPlayer p, float pitch) {
		p.setYRot(-90f);
		p.setYHeadRot(-90f);
		p.setXRot(pitch);
	}

	/** A still 200 HP spider (no AI, no natural armour, no daylight burning) at {@code rel} (keep x <= 6: a 1.4-wide spider at 7 sticks into the wall round the 8-block test area). */
	private static Spider dummy(GameTestHelper h, BlockPos rel) {
		Spider s = h.spawn(EntityType.SPIDER, rel);
		s.setNoAi(true);
		s.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HP);
		s.setHealth(HP);
		s.setPersistenceRequired();
		return s;
	}

	private static float dealt(Spider s) {
		return HP - s.getHealth();
	}

	private static void near(GameTestHelper h, float got, float want, String what) {
		h.assertTrue(Math.abs(got - want) < EPS, what + ": expected " + want + ", got " + got);
	}

	private static IronManSuit mk1() {
		return IronManSuits.byId("mark_1");
	}

	private static IronManSuit mk2() {
		return IronManSuits.byId("mark_1_v2");
	}

	private static boolean fire(ServerPlayer p, IronManSuit suit, int slot, boolean shift) {
		p.setShiftKeyDown(shift);
		boolean ok = IronManComicRouter.fireForTests(p, suit, slot, shift);
		p.setShiftKeyDown(false);
		return ok;
	}

	private static boolean edge(ServerPlayer p, IronManSuit suit, String moveId, boolean pressed) {
		return IronManEraOne.fire(p, suit, IronManComicKit.byId(moveId), pressed);
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	// ------------------------------------------------------------------ registry

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_registry")
	public void everyMkOneAndMkTwoMoveHasAHandlerAndText(GameTestHelper h) {
		int n = 0;
		for (String suit : new String[] { "mark_1", "mark_1_v2" }) {
			IronManComicKit.Kit kit = IronManComicKit.kit(suit);
			h.assertTrue(kit != null && kit.keys().size() == 6, suit + ": a six-key Era I kit");
			for (IronManComicKit.Key k : kit.keys()) {
				for (IronManComicKit.Move m : new IronManComicKit.Move[] { k.tap(), k.shift() }) {
					if (m == null) {
						continue;
					}
					h.assertTrue(IronManEraOne.has(m.id()), "a handler for " + m.id());
					h.assertTrue(net.minecraft.locale.Language.getInstance().has(m.nameKey()), "a name for " + m.id());
					h.assertTrue(net.minecraft.locale.Language.getInstance().has(m.descKey()), "a description for " + m.id());
					n++;
				}
			}
		}
		h.assertTrue(n == 22, "22 moves (11 per suit), got " + n);
		h.succeed();
	}

	// ================================================================== MK I
	// v0.17.7 (user) moveset: R Iron Fist / Gauntlet Smash, G Flamethrower / Arm Rocket, X Rocket Jump / Leap, Z Scrap Shotgun /
	// Fire Nova, V Iron Shell / Scrap Repair, C tap Smoke Screen / hold Reactor Overload

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_flame", timeoutTicks = 100)
	public void mkOneFlamethrowerBurnsEightASecondInSixBlocks(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		Spider near = dummy(h, new BlockPos(4, 1, 3));
		Spider far = dummy(h, new BlockPos(10, 1, 3));
		face(p, 15f);
		h.assertTrue(edge(p, mk1(), "e1mk1_flamethrower", true), "the stream lights");
		h.assertTrue(EraOneMk12State.flaming(p), "flaming while held");
		h.runAfterDelay(20, () -> {
			float d = dealt(near);
			h.assertTrue(d >= 8f - EPS && d <= 11f, "about 8 a second (+ burning), got " + d);
			h.assertTrue(near.getRemainingFireTicks() > 0, "and it burns");
			h.assertTrue(dealt(far) < EPS, "nothing 8 blocks out (6-block cone)");
			edge(p, mk1(), "e1mk1_flamethrower", false);
			h.assertFalse(EraOneMk12State.flaming(p), "the release stops it");
			leave(h, p);
			h.succeed();
		});
	}

	/** v0.17.7 (user): the stream melts ice to water and burns cobwebs away where it is aimed. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_flame_melt")
	public void mkOneFlamethrowerMeltsIceAndBurnsCobwebs(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		face(p, 0f);
		BlockPos web = new BlockPos(3, 2, 3);
		BlockPos ice = new BlockPos(5, 2, 3);
		h.setBlock(web, Blocks.COBWEB);
		h.setBlock(ice, Blocks.ICE);
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		double len = com.projecthero.mod.ironman.ability.IronManComicAccess.wallDistance(p, eye, dir, EraOneMk1Moves.FLAME_RANGE);
		int n = EraOneMk1Moves.meltAlong(p, eye, dir, len);
		h.assertTrue(n == 2, "two blocks changed, got " + n);
		h.assertTrue(h.getBlockState(web).isAir(), "the cobweb burned away");
		h.assertTrue(h.getBlockState(ice).is(Blocks.WATER), "the ice melted to water, got " + h.getBlockState(ice));
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_fist")
	public void mkOneIronFistHitsTwelveWithBigKnockback(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		Spider s = dummy(h, new BlockPos(3, 1, 3));
		face(p, 30f);
		h.assertTrue(fire(p, mk1(), 1, false), "Iron Fist goes off");
		near(h, dealt(s), EraOneMk1Moves.IRON_FIST_DAMAGE, "Iron Fist damage");
		h.assertTrue(EraOneMk1Moves.IRON_FIST_DAMAGE == 12f, "the user's 12");
		h.assertTrue(s.getDeltaMovement().x > 1.5, "thrown back hard, vx " + s.getDeltaMovement().x);
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_smash", timeoutTicks = 100)
	public void mkOneGauntletSmashChargesToFifteenAndHammersItDown(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		Spider s = dummy(h, new BlockPos(3, 1, 3));
		face(p, 30f);
		h.assertTrue(edge(p, mk1(), "e1mk1_gauntlet_smash", true), "the press commits the Gauntlet Smash");
		h.assertTrue(EraOneMk12State.smashing(p) && dealt(s) < EPS, "nothing lands while the fists go up");
		h.runAfterDelay(EraOneMk1Moves.GAUNTLET_SMASH_CHARGE_TICKS + 2, () -> {
			edge(p, mk1(), "e1mk1_gauntlet_smash", false);
			near(h, dealt(s), EraOneMk1Moves.GAUNTLET_SMASH_DAMAGE, "a full Gauntlet Smash");
			h.assertTrue(EraOneMk1Moves.GAUNTLET_SMASH_DAMAGE == 15f && EraOneMk1Moves.GAUNTLET_SMASH_SPLASH_DAMAGE == 10f, "the user's 15 / 10");
			h.assertTrue(s.getDeltaMovement().y < -0.5, "hammered down, vy " + s.getDeltaMovement().y);
			MobEffectInstance slow = s.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			h.assertTrue(slow != null && slow.getAmplifier() >= 4, "staggered");
			h.assertTrue(EraOneMk1Moves.chargeShare(5) == 0.5f && EraOneMk1Moves.chargeShare(20) == 1f, "early release = half power");
			h.assertFalse(EraOneMk12State.smashing(p), "over");
			leave(h, p);
			h.succeed();
		});
	}

	/** v0.17.7 (user): 15 to the target, 10 to everything else within 3 blocks of it, nothing 5 blocks away. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_smash_splash", timeoutTicks = 100)
	public void mkOneGauntletSmashSplashesTenWithinThreeBlocks(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		face(p, 30f);
		Spider target = dummy(h, new BlockPos(3, 1, 1));
		Spider close = dummy(h, new BlockPos(3, 1, 3)); // 2 blocks from the target
		Spider far = dummy(h, new BlockPos(6, 1, 5)); // 5 blocks from the target (3 across, 4 along)
		h.assertTrue(edge(p, mk1(), "e1mk1_gauntlet_smash", true), "the press commits the Gauntlet Smash");
		h.runAfterDelay(EraOneMk1Moves.GAUNTLET_SMASH_CHARGE_TICKS + 2, () -> {
			double closeZ = close.getZ();
			edge(p, mk1(), "e1mk1_gauntlet_smash", false);
			near(h, dealt(target), 15f, "the target takes 15");
			near(h, dealt(close), 10f, "a mob 2 blocks from it takes the 10 splash");
			h.assertTrue(close.getDeltaMovement().z > 0.1 || close.getZ() > closeZ, "and is shoved away, vz " + close.getDeltaMovement().z);
			h.assertTrue(dealt(far) < EPS, "a mob 5 blocks away is untouched, took " + dealt(far));
			h.assertTrue(p.getHealth() >= p.getMaxHealth() - EPS, "never the player");
			leave(h, p);
			h.succeed();
		});
	}

	/** v0.17.7 (user): the forearm rocket flies straight -- 14 to what it hits, 8 to everything within 3 blocks of the blast. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_arm_rocket", timeoutTicks = 100)
	public void mkOneArmRocketHitsFourteenAndSplashesEight(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		Spider target = dummy(h, new BlockPos(4, 1, 3));
		Spider close = dummy(h, new BlockPos(4, 1, 5)); // 2 blocks to the side of the target
		Spider far = dummy(h, new BlockPos(6, 1, 6)); // over 3 blocks from the blast
		face(p, 15f);
		h.assertTrue(fire(p, mk1(), 2, true), "Arm Rocket fires");
		h.assertFalse(TonyStark.abilityReady(p, "mark_1", "e1mk1_arm_rocket"), "its 6 s cooldown runs");
		h.runAfterDelay(15, () -> {
			near(h, dealt(target), EraOneMk1Moves.ARM_ROCKET_DAMAGE, "14 to what it hits");
			near(h, dealt(close), EraOneMk1Moves.ARM_ROCKET_SPLASH_DAMAGE, "8 to a mob 2 blocks from the blast");
			h.assertTrue(dealt(far) < EPS, "nothing past 3 blocks, took " + dealt(far));
			h.assertTrue(p.getHealth() >= p.getMaxHealth() - EPS, "never the player");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_rocketjump", timeoutTicks = 60)
	public void mkOneRocketJumpGoesEightBlocksUpFallSafe(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		h.assertTrue(fire(p, mk1(), 3, false), "Rocket Jump goes off");
		near(h, (float) p.getDeltaMovement().y, (float) EraOneMk1Moves.ROCKET_JUMP_VY, "launch speed");
		// 1.18 b/t under vanilla gravity + drag tops out ~8 blocks up
		double y = 0;
		double vy = EraOneMk1Moves.ROCKET_JUMP_VY;
		double top = 0;
		for (int t = 0; t < 60; t++) {
			y += vy;
			top = Math.max(top, y);
			vy = (vy - 0.08) * 0.98;
		}
		h.assertTrue(top > 7.5 && top < 8.6, "about 8 blocks up, sim " + top);
		h.assertTrue(EraOneMk12State.rocketSafe(p), "fall-safe");
		p.fallDistance = 25f;
		h.runAfterDelay(2, () -> {
			h.assertTrue(p.fallDistance == 0f, "fall distance wiped while rocketing");
			h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().fall(), 12f), "no fall damage");
			leave(h, p);
			h.succeed();
		});
	}

	/** v0.17.7 (user): the leap follows the look pitch -- aimed up it goes higher and less far than a level leap. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_rocketleap", timeoutTicks = 60)
	public void mkOneRocketLeapFollowsThePitchFallSafe(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		face(p, -35f); // looking 35 degrees up
		h.assertTrue(fire(p, mk1(), 3, true), "Rocket Leap goes off");
		Vec3 v = p.getDeltaMovement();
		Vec3 want = EraOneMk1Moves.leapVelocity(p.getLookAngle(), true);
		h.assertTrue(v.distanceTo(want) < 0.02, "the launch for where it looks, " + v + " vs " + want);
		Vec3 level = EraOneMk1Moves.leapVelocity(new Vec3(1, 0, 0), true);
		Vec3 up = EraOneMk1Moves.leapVelocity(Vec3.directionFromRotation(-35f, -90f), true);
		Vec3 down = EraOneMk1Moves.leapVelocity(Vec3.directionFromRotation(40f, -90f), true);
		h.assertTrue(v.x > 0.3 && Math.abs(v.z) < 0.05, "forward (+X), " + v);
		h.assertTrue(up.y / Math.hypot(up.x, up.z) > level.y / Math.hypot(level.x, level.z) + 0.3, "aimed up = a steeper launch: " + up + " vs " + level);
		h.assertTrue(level.y > 0.2, "a level leap still arcs, " + level);
		h.assertTrue(down.distanceTo(level) < 1.0e-6, "from the ground a downward look leaps level, never into the floor");
		Vec3 old = com.projecthero.mod.hero.power.AbilityHelpers.ballisticLaunch(new Vec3(1, 0.45, 0), EraOneMk1Moves.ROCKET_LEAP_BLOCKS, true);
		h.assertTrue(v.distanceTo(old) > 0.05, "not the old fixed arc any more");
		h.assertTrue(EraOneMk12State.rocketSafe(p), "fall-safe");
		p.fallDistance = 25f;
		h.runAfterDelay(2, () -> {
			h.assertTrue(p.fallDistance == 0f, "fall distance wiped");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_scrap")
	public void mkOneScrapShotgunFiresFivePelletsOfFourAndKnocksBack(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		Spider s = dummy(h, new BlockPos(4, 1, 3));
		face(p, 22f);
		h.assertTrue(fire(p, mk1(), 4, false), "Scrap Shotgun goes off");
		h.assertTrue(EraOneMk1Moves.SCRAP_PELLETS == 5 && EraOneMk1Moves.SCRAP_PELLET_DAMAGE == 4f, "the user's 5 x 4");
		near(h, dealt(s), 20f, "5 x 4 at close range");
		h.assertTrue(s.getDeltaMovement().x > 0.4, "a small mob is knocked back, vx " + s.getDeltaMovement().x);
		h.assertFalse(TonyStark.abilityReady(p, "mark_1", "e1mk1_scrap_shotgun"), "its 5 s cooldown runs");
		leave(h, p);
		h.succeed();
	}

	/** v0.17.7 (user): 10 + burning to everything within 5 blocks; nothing further out; no fire left on the ground. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_nova", timeoutTicks = 60)
	public void mkOneFireNovaBurnsTenWithinFive(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Spider in = dummy(h, new BlockPos(3, 1, 2));
		Spider edge = dummy(h, new BlockPos(5, 1, 3)); // about 3.6 blocks off
		Spider far = dummy(h, new BlockPos(6, 1, 6)); // over 6 blocks off
		h.assertTrue(fire(p, mk1(), 4, true), "Fire Nova goes off");
		near(h, dealt(in), EraOneMk1Moves.FIRE_NOVA_DAMAGE, "10 close in");
		near(h, dealt(edge), EraOneMk1Moves.FIRE_NOVA_DAMAGE, "10 further out, inside 5");
		h.assertTrue(in.getRemainingFireTicks() > 0 && edge.getRemainingFireTicks() > 0, "and set burning");
		h.assertTrue(dealt(far) < EPS && far.getRemainingFireTicks() <= 0, "nothing past 5 blocks");
		h.assertTrue(p.getHealth() >= p.getMaxHealth() - EPS, "never the player");
		h.runAfterDelay(EraOneMk1Moves.FIRE_NOVA_RING_TICKS + 2, () -> {
			for (int x = 0; x < 8; x++) {
				for (int z = 0; z < 8; z++) {
					h.assertFalse(h.getBlockState(new BlockPos(x, 1, z)).is(Blocks.FIRE), "no fire blocks left at " + x + ", " + z);
				}
			}
			leave(h, p);
			h.succeed();
		});
	}

	/** v0.17.7 (user): the old Brace + projectiles bounce off. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_shell", timeoutTicks = 140)
	public void mkOneIronShellStopsSixtyPercentRootsAndBouncesArrows(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		Spider shooter = dummy(h, new BlockPos(6, 1, 3));
		h.assertTrue(fire(p, mk1(), 5, false), "Iron Shell goes off");
		h.assertTrue(EraOneMk12State.shelled(p), "shell up");
		near(h, IronManComicState.incomingFactor(p, mk1(), h.getLevel().damageSources().generic()), 0.4f, "60% stopped");
		h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "full knockback resistance");
		h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1.0e-4, "rooted (no walking)");
		h.assertTrue(p.getAttributeValue(Attributes.JUMP_STRENGTH) < 1.0e-4, "rooted (no jumping)");
		p.setDeltaMovement(Vec3.ZERO);
		p.knockback(2.0, 1.0, 0.0);
		h.assertTrue(p.getDeltaMovement().lengthSqr() < 1.0e-6, "no knockback, " + p.getDeltaMovement());
		Arrow arrow = new Arrow(h.getLevel(), p.getX() + 1.2, p.getY() + 1.0, p.getZ(), new ItemStack(Items.ARROW), null);
		arrow.setOwner(shooter);
		arrow.setDeltaMovement(-0.6, 0, 0);
		h.getLevel().addFreshEntity(arrow);
		h.assertTrue(EraOneMk1Moves.bounce(p) == 1, "the arrow bounces off");
		h.assertTrue(arrow.getDeltaMovement().x > 0.2 && arrow.getOwner() == shooter, "turned back the way it came (still the shooter's), "
				+ arrow.getDeltaMovement());
		h.assertTrue(EraOneMk1Moves.bounce(p) == 0, "and is left alone once it is going away");
		h.assertTrue(IronManComicState.incomingFactor(p, mk1(), h.getLevel().damageSources().arrow(arrow, shooter)) == 0f,
				"a projectile never hurts through it");
		h.runAfterDelay(EraOneMk1Moves.IRON_SHELL_TICKS + 2, () -> {
			h.assertFalse(EraOneMk12State.shelled(p), "over after 4 s");
			h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.05, "walking again");
			h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0, "knockback back");
			near(h, IronManComicState.incomingFactor(p, mk1(), h.getLevel().damageSources().generic()), 1f, "hits land in full again");
			arrow.discard();
			leave(h, p);
			h.succeed();
		});
	}

	/** v0.17.7 (user): one iron ingot = +150 integrity; refused (no cooldown, nothing used) without one or with the suit full. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_repair")
	public void mkOneScrapRepairUsesAnIngotForOneHundredFifty(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		float max = IronManEnergy.maxIntegrity("mark_1");
		IronManEnergy.setIntegrity(p, "mark_1", max - 400f);
		float energy = IronManEnergy.energy(p, "mark_1");
		h.assertFalse(fire(p, mk1(), 5, true), "refused with no iron ingot");
		h.assertTrue(TonyStark.abilityReady(p, "mark_1", "e1mk1_scrap_repair"), "a refusal costs no cooldown");
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_1") - energy) < 1.0e-3f, "nor energy");
		near(h, IronManEnergy.integrity(p, "mark_1"), max - 400f, "integrity untouched");
		p.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
		h.assertTrue(fire(p, mk1(), 5, true), "with an ingot it goes off");
		near(h, IronManEnergy.integrity(p, "mark_1"), max - 250f, "+150 integrity");
		h.assertTrue(p.getInventory().countItem(Items.IRON_INGOT) == 1, "one ingot used, " + p.getInventory().countItem(Items.IRON_INGOT) + " left");
		h.assertFalse(TonyStark.abilityReady(p, "mark_1", "e1mk1_scrap_repair"), "its 20 s cooldown runs");
		near(h, IronManEnergy.energy(p, "mark_1"), energy - IronManComicKit.energyCost("e1mk1_scrap_repair"), "it paid its energy");
		// straight to the handler (past the cooldown): a full suit refuses and keeps the ingot
		IronManEnergy.setIntegrity(p, "mark_1", max);
		h.assertFalse(edge(p, mk1(), "e1mk1_scrap_repair", true), "refused at full integrity");
		h.assertTrue(p.getInventory().countItem(Items.IRON_INGOT) == 1, "the ingot is kept");
		// capped at the max
		IronManEnergy.setIntegrity(p, "mark_1", max - 40f);
		h.assertTrue(edge(p, mk1(), "e1mk1_scrap_repair", true), "goes off with 40 missing");
		near(h, IronManEnergy.integrity(p, "mark_1"), max, "capped at the suit's max");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_smoke", timeoutTicks = 60)
	public void mkOneSmokeScreenMakesMobsLoseYou(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		Spider in = dummy(h, new BlockPos(3, 1, 3));
		Spider out = dummy(h, new BlockPos(13, 1, 3));
		in.setTarget(p);
		out.setTarget(p);
		fire(p, mk1(), 6, false); // C tapped (pressed and let go at once)
		h.assertFalse(TonyStark.abilityReady(p, "mark_1", EraOneMk1Moves.SMOKE_ID), "the Smoke Screen went off (its cooldown runs)");
		h.assertFalse(EraOneMk12State.overloadArmed(p), "a tap is never the overload");
		h.assertTrue(in.getTarget() == null && out.getTarget() == null, "everything near drops you");
		h.runAfterDelay(3, () -> {
			h.assertTrue(in.hasEffect(MobEffects.BLINDNESS), "blinded inside the cloud");
			out.setTarget(p);
		});
		h.runAfterDelay(5, () -> {
			h.assertTrue(out.getTarget() == null, "standing in your smoke: no mob within 16 keeps you");
			leave(h, p);
			h.succeed();
		});
	}

	/**
	 * v0.17.7 (user): tap C = Smoke Screen, hold C 1 s = Reactor Overload -- never both. Held, the overload goes off at the 1 s mark
	 * (still held) and letting go after it does nothing more; each keeps its own cooldown and energy.
	 */
	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_c_key", timeoutTicks = 80)
	public void mkOneTapCIsSmokeHoldCIsOverload(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		float e0 = IronManEnergy.energy(p, "mark_1");
		h.assertFalse(edge(p, mk1(), EraOneMk1Moves.SMOKE_ID, true), "the press only marks the time");
		h.assertTrue(EraOneMk1Moves.cHeld(p), "C is down");
		h.runAfterDelay(EraOneMk1Moves.OVERLOAD_HOLD_TICKS - 6, () -> h.assertFalse(EraOneMk12State.overloadArmed(p), "not yet at 0.7 s"));
		h.runAfterDelay(EraOneMk1Moves.OVERLOAD_HOLD_TICKS + 3, () -> {
			h.assertTrue(EraOneMk12State.overloadArmed(p), "held 1 s: the overload goes off while still held");
			near(h, IronManEnergy.energy(p, "mark_1"), e0 - EraOneMk1Moves.OVERLOAD_ENERGY, "it paid its 50");
			edge(p, mk1(), EraOneMk1Moves.SMOKE_ID, false);
			h.assertTrue(TonyStark.abilityReady(p, "mark_1", EraOneMk1Moves.SMOKE_ID), "letting go after the hold is not a Smoke Screen");
			h.assertFalse(TonyStark.abilityReady(p, "mark_1", EraOneMk1Moves.OVERLOAD_ID), "the overload's 20 s cooldown runs");
			float e1 = IronManEnergy.energy(p, "mark_1");
			// a tap (under 1 s) is the Smoke Screen, even with the overload cooling down
			h.assertTrue(EraOneMk1Moves.cKeyForTests(p, mk1(), 5), "a tap is the Smoke Screen");
			h.assertFalse(TonyStark.abilityReady(p, "mark_1", EraOneMk1Moves.SMOKE_ID), "its 14 s cooldown runs");
			near(h, IronManEnergy.energy(p, "mark_1"), e1 - EraOneMk1Moves.SMOKE_ENERGY, "it paid its 15");
			// another hold is refused by the overload's own cooldown (and does not smoke instead)
			h.assertFalse(EraOneMk1Moves.cKeyForTests(p, mk1(), 25), "the overload is cooling down");
			h.assertTrue(EraOneMk1Moves.SMOKE_COOLDOWN_TICKS == 280 && EraOneMk1Moves.OVERLOAD_COOLDOWN_TICKS == 400, "14 s / 20 s");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_overload1", timeoutTicks = 60)
	public void mkOneReactorOverloadDoublesTheNextHitOrTheFlame(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		Spider s = dummy(h, new BlockPos(3, 1, 3));
		face(p, 30f);
		h.assertTrue(EraOneMk1Moves.cKeyForTests(p, mk1(), EraOneMk1Moves.OVERLOAD_HOLD_TICKS + 2), "Reactor Overload goes off (C held)");
		h.assertTrue(EraOneMk12State.overloadArmed(p), "armed");
		h.assertTrue(fire(p, mk1(), 1, false), "Iron Fist");
		near(h, dealt(s), EraOneMk1Moves.IRON_FIST_DAMAGE * 2f, "the next hit lands x2 (12 x 2)");
		h.assertFalse(EraOneMk12State.overloadArmed(p), "spent");
		// re-armed (straight to the handler, past its cooldown): the flamethrower takes it as 3 s of double flame
		EraOneMk1Moves.armOverloadForTests(p, mk1());
		s.discard();
		Spider s2 = dummy(h, new BlockPos(4, 1, 3));
		face(p, 15f);
		edge(p, mk1(), "e1mk1_flamethrower", true);
		h.runAfterDelay(2, () -> {
			float d = dealt(s2);
			// (the burning it lit may add its 1 on the next tick: the reissued x2 hit leaves no hit cooldown behind)
			h.assertTrue(d >= 4f - EPS && d <= 5f + EPS, "the first flame hit doubled (2 x 2), got " + d);
			h.assertFalse(EraOneMk12State.overloadArmed(p), "the flame spent it");
			edge(p, mk1(), "e1mk1_flamethrower", false);
			leave(h, p);
			h.succeed();
		});
	}

	/** v0.17.7 (user) passives: fire and lava do not hurt the full Mk I, and it is hard to knock back. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_passives")
	public void mkOneIsFireProofAndHeavy(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1");
		h.assertTrue(IronManSuits.MARK_1.fireImmune(), "fire-proof suit");
		h.assertTrue(IronManDamage.fireProof(p, IronManSuits.MARK_1, p.damageSources().lava()), "lava does nothing (full suit)");
		h.assertTrue(IronManDamage.fireProof(p, IronManSuits.MARK_1, p.damageSources().inFire()), "fire does nothing");
		h.assertFalse(IronManDamage.fireProof(p, IronManSuits.MARK_1, p.damageSources().generic()), "an ordinary hit is not stopped");
		p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		h.assertFalse(IronManDamage.fireProof(p, IronManSuits.MARK_1, p.damageSources().lava()), "only with the whole suit on");
		h.assertTrue(EraOneMk1Moves.KNOCKBACK_RESISTANCE >= 0.5, "high knockback resistance");
		h.assertTrue(IronManSuits.MARK_1.walkSpeedBonus() == -0.10 && IronManSuits.MARK_1.strengthBonus() == 8f, "walks 10% slower, melee +8");
		leave(h, p);
		h.succeed();
	}

	// ================================================================== MK II

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_repulsor")
	public void mkTwoRepulsorHitsTwelve(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		Spider s = dummy(h, new BlockPos(6, 1, 3));
		face(p, 12f);
		h.assertTrue(fire(p, mk2(), 1, false), "Repulsor goes off");
		near(h, dealt(s), 12f, "Repulsor damage");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_charged", timeoutTicks = 100)
	public void mkTwoChargedRepulsorHitsTwentySixAfterASecond(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		Spider s = dummy(h, new BlockPos(6, 1, 3));
		face(p, 12f);
		h.assertTrue(edge(p, mk2(), "e1mk2_charged_repulsor", true), "the press commits the charge");
		h.runAfterDelay(EraOneMk12State.CHARGE_TICKS + 2, () -> {
			h.assertTrue(dealt(s) < EPS, "nothing lands while it charges");
			edge(p, mk2(), "e1mk2_charged_repulsor", false);
			near(h, dealt(s), 26f, "a full Charged Repulsor");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_uppercut")
	public void mkTwoGoldUppercutHitsFourteenAndLaunchesUp(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		Spider s = dummy(h, new BlockPos(3, 1, 3));
		face(p, 30f);
		h.assertTrue(fire(p, mk2(), 2, false), "Gold Uppercut goes off");
		near(h, dealt(s), 14f, "Uppercut damage");
		h.assertTrue(s.getDeltaMovement().y >= 1.0, "launched up, vy " + s.getDeltaMovement().y);
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_pound", timeoutTicks = 60)
	public void mkTwoGroundPoundFromTheAirHitsSixteenInFourFallSafe(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		Spider s = dummy(h, new BlockPos(3, 1, 3));
		Spider far = dummy(h, new BlockPos(10, 1, 3));
		h.assertFalse(fire(p, mk2(), 2, true), "refused on the ground");
		h.assertTrue(TonyStark.abilityReady(p, "mark_1_v2", "e1mk2_ground_pound"), "a refusal costs no cooldown");
		BlockPos air = h.absolutePos(new BlockPos(1, 5, 3));
		p.teleportTo(air.getX() + 0.5, air.getY(), air.getZ() + 0.5);
		p.setOnGround(false);
		h.assertTrue(fire(p, mk2(), 2, true), "from the air it goes off");
		h.assertTrue(EraOneMk12State.pounding(p) && p.getDeltaMovement().y < -1.5, "driving down");
		p.fallDistance = 30f;
		h.runAfterDelay(2, () -> {
			h.assertTrue(p.fallDistance == 0f, "fall-safe on the way down");
			BlockPos floor = h.absolutePos(new BlockPos(1, 0, 3)); // the floor under the empty structure
			p.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
		});
		h.runAfterDelay(5, () -> {
			h.assertFalse(EraOneMk12State.pounding(p), "landed");
			near(h, dealt(s), 16f, "16 within 4 blocks");
			h.assertTrue(dealt(far) < EPS, "nothing 9 blocks out");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_hover", timeoutTicks = 160)
	public void mkTwoBootHoverLastsFiveSeconds(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		h.assertTrue(fire(p, mk2(), 3, false), "Boot Hover goes off");
		h.assertTrue(IronManFlight.isFlying(p) && EraOneMk12State.hovering(p), "airborne on the boot jets");
		p.setOnGround(false); // the hop took it off the floor (a mock player has no physics; touching down ends a flight)
		h.runAfterDelay(EraOneMk12State.HOVER_TICKS - 10, () -> {
			h.assertTrue(IronManFlight.isFlying(p), "still up at 4.5 s");
			p.fallDistance = 10f;
		});
		h.runAfterDelay(EraOneMk12State.HOVER_TICKS - 8, () -> h.assertTrue(p.fallDistance == 0f, "fall-safe while hovering"));
		h.runAfterDelay(EraOneMk12State.HOVER_TICKS + 3, () -> {
			h.assertFalse(IronManFlight.isFlying(p), "the jets cut out after 5 s");
			h.assertFalse(EraOneMk12State.hovering(p), "hover over");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_jetdash")
	public void mkTwoJetDashHitsFourteen(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		Spider s = dummy(h, new BlockPos(3, 1, 3));
		h.assertTrue(fire(p, mk2(), 3, true), "Jet Dash goes off");
		h.assertTrue(IronManDash.dashing(p) && p.getDeltaMovement().x > 0.5, "dashing forward");
		IronManDash.tick(p); // the suit ticker drives the dash
		near(h, dealt(s), 14f, "14 to what it passes");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_glare", timeoutTicks = 120)
	public void mkTwoGoldenGlareBlindsATwelveBlockConeForFourSeconds(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		Spider s = dummy(h, new BlockPos(6, 1, 3));
		Spider behind = dummy(h, new BlockPos(0, 1, 6));
		face(p, 10f);
		s.setTarget(p);
		h.assertTrue(fire(p, mk2(), 4, false), "Golden Glare goes off");
		MobEffectInstance blind = s.getEffect(MobEffects.BLINDNESS);
		h.assertTrue(blind != null && blind.getDuration() >= EraOneMk12State.GLARE_TICKS - 2, "blinded 4 s");
		h.assertTrue(s.getTarget() == null && EraOneMk12State.glared(p, s), "it loses you");
		h.assertFalse(behind.hasEffect(MobEffects.BLINDNESS), "nothing outside the cone");
		s.setTarget(p);
		h.runAfterDelay(2, () -> h.assertTrue(s.getTarget() == null, "still dazzled: it cannot pick you again"));
		h.runAfterDelay(EraOneMk12State.GLARE_TICKS + 3, () -> {
			h.assertFalse(EraOneMk12State.glared(p, s), "over after 4 s");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_skates", timeoutTicks = 220)
	public void mkTwoJetSkatesGlideFastForEightSeconds(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		double step = p.getAttributeValue(Attributes.STEP_HEIGHT);
		h.assertTrue(fire(p, mk2(), 4, true), "Jet Skates go off");
		MobEffectInstance speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
		h.assertTrue(speed != null && speed.getAmplifier() == EraOneMk2Moves.SKATES_SPEED_AMP
				&& speed.getDuration() >= EraOneMk12State.SKATES_TICKS - 2, "Speed IV for 8 s");
		h.assertTrue(EraOneMk12State.skating(p) && p.getAttributeValue(Attributes.STEP_HEIGHT) >= 1.0, "steps up full blocks");
		h.runAfterDelay(EraOneMk12State.SKATES_TICKS + 3, () -> {
			h.assertFalse(EraOneMk12State.skating(p), "over after 8 s");
			near(h, (float) p.getAttributeValue(Attributes.STEP_HEIGHT), (float) step, "step height back");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_pull")
	public void mkTwoMagneticPullYanksMobsAndItemsInFourteen(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		Spider s = dummy(h, new BlockPos(11, 1, 3));
		Spider far = dummy(h, new BlockPos(17, 1, 3));
		BlockPos ip = h.absolutePos(new BlockPos(7, 1, 3));
		ItemEntity item = new ItemEntity(h.getLevel(), ip.getX() + 0.5, ip.getY(), ip.getZ() + 0.5, new ItemStack(Items.IRON_INGOT));
		item.setDeltaMovement(Vec3.ZERO);
		h.getLevel().addFreshEntity(item);
		h.assertTrue(fire(p, mk2(), 5, false), "Magnetic Pull goes off");
		h.assertTrue(s.getDeltaMovement().x < -1.0, "the spider 10 blocks out is yanked in, vx " + s.getDeltaMovement().x);
		h.assertTrue(far.getDeltaMovement().lengthSqr() < 0.1, "nothing 16 blocks out");
		h.assertTrue(item.getDeltaMovement().x < -0.3, "loose items too");
		h.assertTrue(dealt(s) < EPS, "no damage");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_shield", timeoutTicks = 140)
	public void mkTwoMagneticShieldTurnsBackProjectilesForFiveSeconds(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		Spider shooter = dummy(h, new BlockPos(6, 1, 3));
		h.assertTrue(fire(p, mk2(), 5, true), "Magnetic Shield goes off");
		h.assertTrue(EraOneMk12State.magnetShield(p), "up");
		Arrow arrow = new Arrow(h.getLevel(), p.getX() + 2.5, p.getY() + 1.0, p.getZ(), new ItemStack(Items.ARROW), null);
		arrow.setOwner(shooter);
		arrow.setDeltaMovement(-0.2, 0, 0);
		h.getLevel().addFreshEntity(arrow);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		h.assertTrue(EraOneMk2Moves.deflect(p, c) == 1, "the arrow is caught");
		h.assertTrue(arrow.getDeltaMovement().x > 0.3 && arrow.getOwner() == p, "and turned back (now yours), " + arrow.getDeltaMovement());
		float f = IronManComicState.incomingFactor(p, mk2(), h.getLevel().damageSources().arrow(arrow, shooter));
		h.assertTrue(f == 0f, "a projectile never hurts through it");
		near(h, IronManComicState.incomingFactor(p, mk2(), h.getLevel().damageSources().generic()), 1f, "other hits are not stopped");
		h.runAfterDelay(EraOneMk12State.SHIELD_TICKS + 3, () -> {
			h.assertFalse(EraOneMk12State.magnetShield(p), "down after 5 s");
			arrow.discard();
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "e1mk12_overload2", timeoutTicks = 260)
	public void mkTwoReactorOverloadDoublesTheNextHitAndFades(GameTestHelper h) {
		ServerPlayer p = tony(h, "mark_1_v2");
		Spider s = dummy(h, new BlockPos(6, 1, 3));
		face(p, 12f);
		h.assertTrue(fire(p, mk2(), 6, false), "Reactor Overload goes off");
		h.assertTrue(fire(p, mk2(), 1, false), "Repulsor");
		near(h, dealt(s), 24f, "the next hit lands x2 (12 x 2)");
		h.assertFalse(EraOneMk12State.overloadArmed(p), "spent");
		// armed but unused: it fades after 10 s
		edge(p, mk2(), "e1mk2_reactor_overload", true);
		h.runAfterDelay(EraOneMk12State.OVERLOAD_ARMED_TICKS + 3, () -> {
			h.assertFalse(EraOneMk12State.overloadArmed(p), "faded after 10 s");
			leave(h, p);
			h.succeed();
		});
	}
}

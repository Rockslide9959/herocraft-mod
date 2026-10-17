package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p17.ElasticityHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Elasticity rework (docs/V0165_POWERS_PROPOSAL.md section 6): six keys (+ Shift versions) on a 0-100 Stretch
 * meter -- the meter and the limp state, the damage numbers, grab / hurl, the shield bounce and the passives (fall
 * immunity, the fire weakness, the blunt reduction). Mock players are not reliably ticked by the server, so the tests
 * drive {@link ExperimentalPowers#serverTick} themselves. Target dummies are no-AI iron golems (100 HP, no armour, so
 * the damage numbers come through exactly).
 */
public class ElasticityV0165GameTests implements FabricGameTest {
	private static final String K = ElasticityHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock elastic hero at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 5x4x9 room. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -1), base.offset(2, 4, 7))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -1), base.offset(2, -1, 7))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Power power = Powers.byKey(K);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	private static void press(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
	}

	private static void shiftPress(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	/** A no-AI iron golem dummy (100 HP, no armour) at relative (x, 2, z). */
	private static IronGolem dummy(GameTestHelper helper, int x, int z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zb = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zb.setPersistenceRequired();
		zb.setNoAi(true);
		return zb;
	}

	private static void assertNear(GameTestHelper helper, float expected, float got, String what) {
		helper.assertTrue(Math.abs(expected - got) < 0.05f, what + ": expected " + expected + ", got " + got);
	}

	// ---------------- registry / HUD ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysStretchMeterAndEnabled(GameTestHelper helper) {
		Power power = Powers.byKey(K);
		helper.assertTrue(Powers.isEnabled(power) && Powers.isMutation(power), "Elasticity is an enabled mutation again");
		helper.assertTrue(power.abilities().size() == 6, "six abilities (no H / N), got " + power.abilities().size());
		String[] ids = { "stretch_punch", "elastic_grab", "slingshot", "giant_hammer_fist", "rubber_shield", "long_reach" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && a.id().equals(ids[i]), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		var stretch = MutationMeters.get(K, "stretch");
		helper.assertTrue(stretch != null && stretch.always(), "Stretch is an always-on HUD meter");
		helper.assertTrue(MutationMeters.get(K, "coil") != null, "the coil bar");
		for (String flag : new String[] { "p17.arm", "p17.coiling", "p17.balloon", "p17.canopy", "p17.ball", "p17.stilts" }) {
			helper.assertTrue(com.projecthero.mod.hero.visual.MutationVisuals.registeredFlags().contains(flag), flag + " is synced");
		}
		helper.succeed();
	}

	// ---------------- Stretch / limp ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void stretchSpendsAndRefills(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		dummy(helper, 2, 6);
		helper.assertTrue(ElasticityHandlers.stretch(p) >= 99.9f, "the meter starts full");
		press(p, 1); // tap R: Stretch Punch
		assertNear(helper, 90.0f, ElasticityHandlers.stretch(p), "a punch costs 10");
		// v0.16.5 (user): 3 a second, standing or moving
		ElasticityHandlers.setStretch(p, 40.0f);
		tick(p, 20);
		assertNear(helper, 43.0f, ElasticityHandlers.stretch(p), "refills 3 a second");
		p.setKnownMovement(new Vec3(0.2, 0.0, 0.0));
		tick(p, 20);
		assertNear(helper, 46.0f, ElasticityHandlers.stretch(p), "the same moving");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void runningDryGoesLimp(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		dummy(helper, 2, 6);
		ElasticityHandlers.setStretch(p, 5.0f);
		press(p, 1); // the punch that empties the meter still lands
		helper.assertTrue(ElasticityHandlers.limp(p), "running dry goes limp");
		var slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() == 1 && slow.getDuration() <= 160 && slow.getDuration() > 140, "Slowness II for 8s");
		AbilityRouter.handleInput(p, 4, true); // Z: refused while limp
		helper.assertTrue(res(p, "coil_kind") < 0.5f, "no moves while limp");
		AbilityRouter.handleInput(p, 4, false);
		tick(p, 150); // the meter is long full again, but limp is a flat 8 s
		helper.assertTrue(ElasticityHandlers.limp(p), "limp for a flat 8 seconds");
		tick(p, 11);
		helper.assertFalse(ElasticityHandlers.limp(p), "after 8 seconds: moves work again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void heldMovesDrainAndPauseTheRefill(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.handleInput(p, 5, true); // hold V: Rubber Shield
		helper.assertTrue(ElasticityHandlers.shielding(p), "holding V inflates the balloon");
		tick(p, 20);
		assertNear(helper, 92.0f, ElasticityHandlers.stretch(p), "the balloon costs 8 a second and nothing refills meanwhile");
		AbilityRouter.handleInput(p, 5, false);
		helper.assertFalse(ElasticityHandlers.shielding(p), "letting go deflates");
		helper.succeed();
	}

	// ---------------- damage numbers ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void stretchPunchDamage(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 7); // ~5.5 blocks: well past melee
		float before = g.getHealth();
		press(p, 1);
		assertNear(helper, ElasticityHandlers.PUNCH_DAMAGE, before - g.getHealth(), "a tapped Stretch Punch");
		helper.assertTrue(ElasticityHandlers.armStretched(p) && ElasticityHandlers.armLength(p) > 4.0f,
				"the arm is drawn stretched out to the target, got " + ElasticityHandlers.armLength(p));
		helper.assertTrue(ElasticityHandlers.punchDamage(1.0f) == 24.0f, "fully coiled: 24");
		// a real 1.5 s coil
		IronGolem g2 = dummy(helper, 2, 7);
		g.discard();
		float before2 = g2.getHealth();
		ElasticityHandlers.setStretch(p, 100.0f);
		AbilityRouter.handleInput(p, 1, true);
		helper.assertTrue(res(p, "coil_kind") > 0.5f, "holding R coils");
		helper.runAfterDelay(32, () -> {
			AbilityRouter.handleInput(p, 1, false);
			assertNear(helper, 24.0f, before2 - g2.getHealth(), "a fully coiled Stretch Punch");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hammerGatlingStompAndBallDamage(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 5);
		float h0 = g.getHealth();
		press(p, 4); // Z tapped: an uncoiled Hammer Fist
		assertNear(helper, ElasticityHandlers.HAMMER_DAMAGE_MIN, h0 - g.getHealth(), "an uncoiled Hammer Fist");
		helper.assertTrue(res(p, "hammer_cd") > 150.0f, "the Hammer Fist has its 8s cooldown");
		helper.assertTrue(ElasticityHandlers.hammerDamage(1.0f) == 34.0f, "fully coiled: 34");
		helper.assertTrue(g.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "plus Slowness");

		float h1 = g.getHealth();
		g.invulnerableTime = 0;
		int hit = ElasticityHandlers.gatlingVolley(p, helper.getLevel());
		helper.assertTrue(hit == 1, "one Gatling punch hits what is in the cone");
		assertNear(helper, ElasticityHandlers.GATLING_DAMAGE, h1 - g.getHealth(), "a Gatling punch");

		// Spring Stomp: the landing ring
		IronGolem near = dummy(helper, 4, 2);
		float h2 = near.getHealth();
		ElasticityHandlers.stompImpact(p);
		assertNear(helper, ElasticityHandlers.STOMP_DAMAGE, h2 - near.getHealth(), "the Spring Stomp landing");
		shiftPress(p, 4);
		helper.assertTrue(res(p, "stomp_cd") > 100.0f && Math.round(res(p, "stomp")) == 1, "Shift+Z goes up on stilts, 6s cooldown");

		// Rubber Ball: bowling
		ElasticityHandlers.setStretch(p, 100.0f);
		ElasticityHandlers.endBall(p);
		shiftPress(p, 3);
		helper.assertTrue(ElasticityHandlers.balled(p), "Shift+X rolls into a ball");
		IronGolem bowled = dummy(helper, 2, 2);
		bowled.setPos(p.getX() + 0.6, p.getY(), p.getZ());
		float h3 = bowled.getHealth();
		ElasticityHandlers.ballBowl(p, helper.getLevel());
		assertNear(helper, ElasticityHandlers.BALL_HIT_DAMAGE, h3 - bowled.getHealth(), "the ball bowls through for 6");
		// v0.16.5 (user): no time limit -- it drains 3 Stretch a second instead (and nothing refills meanwhile)
		float st = ElasticityHandlers.stretch(p);
		tick(p, 200);
		helper.assertTrue(ElasticityHandlers.balled(p), "still a ball after 10 s");
		assertNear(helper, st - ElasticityHandlers.COST_BALL_PER_SECOND * 10, ElasticityHandlers.stretch(p), "3 Stretch a second");
		helper.assertTrue(ElasticityHandlers.damageFactor(p, p.damageSources().flyIntoWall()) < 0, "no kinetic-energy deaths");
		ElasticityHandlers.endBall(p);
		helper.succeed();
	}

	// ---------------- G: grab / hurl / wrap ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void grabReelsInAndHurls(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 7);
		press(p, 2); // G: grab
		helper.assertTrue(Math.round(ElasticityHandlers.grabId(p)) == g.getId(), "G grabs the creature it is aimed at");
		assertNear(helper, 85.0f, ElasticityHandlers.stretch(p), "a grab costs 15");
		tick(p, 10);
		helper.assertTrue(g.distanceTo(p) < 3.6f, "reeled in to arm's length, got " + g.distanceTo(p));
		float h0 = g.getHealth();
		press(p, 2); // G again: hurl
		helper.assertTrue(ElasticityHandlers.grabId(p) < 0.5f, "hurled: the grab is over");
		helper.assertTrue(g.getDeltaMovement().z > 1.0, "thrown where the hero looks");
		helper.assertTrue(res(p, "thrown_ticks") > 0.5f, "the throw is tracked for its impact");
		ElasticityHandlers.hurlLanded(p, g, true);
		assertNear(helper, ElasticityHandlers.HURL_DAMAGE + ElasticityHandlers.HURL_IMPACT_BONUS, h0 - g.getHealth(),
				"a hurl into a wall");
		float h1 = g.getHealth();
		g.invulnerableTime = 0;
		ElasticityHandlers.hurlLanded(p, g, false);
		assertNear(helper, ElasticityHandlers.HURL_DAMAGE, h1 - g.getHealth(), "a hurl that just lands");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grabRefusesBosses(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem big = dummy(helper, 2, 6);
		big.getAttribute(Attributes.MAX_HEALTH).setBaseValue(250.0);
		big.setHealth(250.0f);
		press(p, 2);
		helper.assertTrue(ElasticityHandlers.grabId(p) < 0.5f, "nothing over 200 HP is grabbed");
		assertNear(helper, 100.0f, ElasticityHandlers.stretch(p), "a refused grab costs nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wrapAndSqueezeHoldsAndHurts(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 4);
		shiftPress(p, 2); // Shift+G
		helper.assertTrue(Math.round(ElasticityHandlers.wrapId(p)) == z.getId(), "Shift+G wraps the aimed creature");
		assertNear(helper, 75.0f, ElasticityHandlers.stretch(p), "a wrap costs 25");
		helper.assertTrue(ElasticityHandlers.isWrapped(z), "a wrapped creature cannot attack (its hits are vetoed)");
		float hp = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 4.0f);
		helper.assertTrue(p.getHealth() >= hp - 0.01f, "its hit does nothing, took " + (hp - p.getHealth()));
		float h0 = z.getHealth();
		tick(p, 20);
		helper.assertTrue(h0 - z.getHealth() > 3.0f, "4 a second, took " + (h0 - z.getHealth()));
		press(p, 2); // G again: let go
		helper.assertTrue(ElasticityHandlers.wrapId(p) < 0.5f, "pressing G lets go");
		helper.succeed();
	}

	// ---------------- V: the shield bounce ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void rubberShieldBouncesProjectiles(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie shooter = zombie(helper, 2, 7);
		double speedBefore = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		AbilityRouter.handleInput(p, 5, true);
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < speedBefore, "the balloon is slow");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		Vec3 at = p.position().add(0, 1.0, 2.0);
		arrow.setPos(at.x, at.y, at.z);
		arrow.setOwner(shooter);
		arrow.setDeltaMovement(0, 0, -1.5);
		helper.getLevel().addFreshEntity(arrow);
		helper.assertTrue(ElasticityHandlers.reflectProjectiles(p, 3.5) >= 1, "the incoming arrow bounces");
		helper.assertTrue(arrow.getOwner() == p && arrow.getDeltaMovement().z > 0, "back at its shooter, and it is the hero's now");
		helper.assertTrue(ElasticityHandlers.damageFactor(p, p.damageSources().arrow(arrow, shooter)) < 0.0f,
				"a projectile that still reaches the balloon is bounced, not taken");
		assertNear(helper, 0.75f * 0.4f, ElasticityHandlers.damageFactor(p, p.damageSources().mobAttack(shooter)),
				"melee on the balloon: blunt -25%, then -60%");
		AbilityRouter.handleInput(p, 5, false);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speedBefore) < 1.0e-6, "the slow is gone on release");
		helper.succeed();
	}

	// ---------------- passives ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void noFallDamage(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		float h = p.getHealth();
		p.fallDistance = 30.0f;
		p.hurt(p.damageSources().fall(), 18.0f);
		helper.assertTrue(p.getHealth() >= h - 0.01f, "no fall damage, took " + (h - p.getHealth()));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fireWeakness(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		float h = p.getHealth();
		p.hurt(p.damageSources().lava(), 4.0f);
		assertNear(helper, 5.0f, h - p.getHealth(), "lava: +25%");
		helper.assertTrue(ElasticityHandlers.damageFactor(p, p.damageSources().onFire()) > 1.24f, "burning: +25%");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bluntHitsAreSoftened(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 3);
		float h = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 8.0f);
		assertNear(helper, 6.0f, h - p.getHealth(), "a melee hit: -25%");
		helper.assertTrue(Math.abs(ElasticityHandlers.damageFactor(p, p.damageSources().explosion(null, null)) - 0.75f) < 1.0e-4,
				"an explosion: -25%");
		helper.assertTrue(Math.abs(ElasticityHandlers.damageFactor(p, p.damageSources().magic()) - 1.0f) < 1.0e-4,
				"anything else: unchanged");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void reachAndKnockbackPassives(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		double reach = p.getAttributeBaseValue(Attributes.ENTITY_INTERACTION_RANGE);
		tick(p, 1);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - (reach + 3.0)) < 1.0e-6, "+3 melee reach");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.5 - 1.0e-6, "50% knockback resistance");
		press(p, 6); // C: Long Reach
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 12.0) < 1.0e-6, "Long Reach: 12 for hitting");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) - 12.0) < 1.0e-6, "and 12 for blocks");
		press(p, 6);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) - 4.5) < 1.0e-6, "toggled back off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shapeShiftHidesFromMobs(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 6);
		shiftPress(p, 6); // Shift+C
		helper.assertTrue(ElasticityHandlers.shapeShifted(p), "Shift+C squashes into a block");
		helper.assertTrue(res(p, "shape_cd") > 150.0f, "10s cooldown");
		((Mob) z).setTarget(p);
		helper.assertTrue(z.getTarget() == null, "mobs cannot target a Shape Shift");
		// v0.16.5 (user): you can creep about as the block
		p.setPos(p.getX() + 1.0, p.getY(), p.getZ());
		tick(p, 1);
		helper.assertTrue(ElasticityHandlers.shapeShifted(p), "moving keeps it");
		// ... but sprinting gives it away
		p.setSprinting(true);
		tick(p, 1);
		helper.assertFalse(ElasticityHandlers.shapeShifted(p), "sprinting ends it");
		p.setSprinting(false);
		// and so does a hit
		ExperimentalPowers.setResource(p, Powers.byKey(K), "shape_cd", 0f, 100000f);
		shiftPress(p, 6);
		helper.assertTrue(ElasticityHandlers.shapeShifted(p), "squashed again");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 1f);
		helper.assertFalse(ElasticityHandlers.shapeShifted(p), "a hit ends it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void slingshotScalesWithCoilAndDistance(GameTestHelper helper) {
		helper.assertTrue(ElasticityHandlers.SLING_RANGE >= 80.0, "80-block reach");
		double tap = ElasticityHandlers.slingSpeed(0f, 30.0), full = ElasticityHandlers.slingSpeed(1f, 30.0);
		helper.assertTrue(full > tap * 2.0, "a full coil more than doubles the launch: " + tap + " -> " + full);
		helper.assertTrue(ElasticityHandlers.slingSpeed(1f, 40.0) > ElasticityHandlers.slingSpeed(1f, 8.0), "a farther anchor flings harder");
		helper.assertTrue(ElasticityHandlers.slingSpeed(1f, 80.0) <= ElasticityHandlers.SLING_MAX_SPEED, "capped");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theParachuteGlidesLikeAnElytra(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setPos(p.getX(), p.getY() + 4.0, p.getZ());
		p.setOnGround(false);
		shiftPress(p, 5); // Shift+V in the air
		helper.assertTrue(ElasticityHandlers.gliding(p), "the canopy is out");
		helper.assertTrue(p.isFallFlying(), "elytra flight");
		shiftPress(p, 5);
		helper.assertFalse(p.isFallFlying(), "folded up: no more elytra flight");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gearTwoFromALavaBucketThenWornOut(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET));
		p.setShiftKeyDown(true);
		var r = net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p, helper.getLevel(), net.minecraft.world.InteractionHand.MAIN_HAND);
		p.setShiftKeyDown(false);
		helper.assertTrue(r.getResult().consumesAction() && ElasticityHandlers.gear2(p), "Sneak + use a Lava Bucket: Gear 2");
		helper.assertTrue(p.getMainHandItem().is(net.minecraft.world.item.Items.BUCKET), "the bucket empties");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.15, "much faster");
		ElasticityHandlers.setStretch(p, 100.0f);
		press(p, 1); // a Stretch Punch: half the usual 10
		assertNear(helper, 95.0f, ElasticityHandlers.stretch(p), "moves at half the Stretch");
		ExperimentalPowers.setResource(p, Powers.byKey(K), "gear2", 2f, 100000f);
		tick(p, 3);
		helper.assertFalse(ElasticityHandlers.gear2(p), "it runs out");
		helper.assertTrue(ElasticityHandlers.gear2Fatigued(p) && ElasticityHandlers.limp(p), "then limp and worn out");
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS) && p.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN), "the drawback");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.11, "the speed is gone");
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET));
		p.setShiftKeyDown(true);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p, helper.getLevel(), net.minecraft.world.InteractionHand.MAIN_HAND);
		p.setShiftKeyDown(false);
		helper.assertFalse(ElasticityHandlers.gear2(p), "no Gear 2 again while worn out");
		helper.succeed();
	}
}

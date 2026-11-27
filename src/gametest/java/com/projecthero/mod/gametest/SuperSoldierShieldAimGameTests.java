package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.SuperSoldierAbilityManager;
import com.projecthero.mod.supersoldier.SuperSoldierConfig;
import com.projecthero.mod.supersoldier.SuperSoldierSerum;
import com.projecthero.mod.supersoldier.entity.SoldierShieldEntity;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Super Soldier shield / passives pass (user: "make it so that the shield throws where the player aims with some aim
 * assist, the shield can bounce between enemies if they are in close proximity to whats being hit, maximum 5 bounces, make
 * the shield get thrown slower" and "players should be able to jump 2 blocks, player should have passive resistance 1"):
 * the throw follows the look vector (pitch included) at the slower speed, the aim assist bends it only for an enemy inside
 * the cone, bounces go only to enemies close to the one hit and stop at 5, the jump clears two blocks but not three, and
 * Resistance I stays on while the power is held and comes off with it.
 */
public class SuperSoldierShieldAimGameTests implements FabricGameTest {
	private static ServerPlayer soldier(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		helper.assertTrue(SuperSoldierSerum.drink(p, true, 0.99f) == SuperSoldierSerum.Outcome.GRANTED, "the refined serum always takes");
		return p;
	}

	private static Zombie zombie(GameTestHelper helper, ServerPlayer p, double side, double forward) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.moveTo(p.getX() + side, p.getY(), p.getZ() + forward, 180.0f, 0.0f);
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		z.setHealth(200.0f);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		z.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000, 0, false, false));
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	/** Points his look at {@code to} from his eyes, turned {@code yawOffset} degrees to the side. */
	private static void lookAt(ServerPlayer p, Vec3 to, float yawOffset) {
		Vec3 d = to.subtract(p.getEyePosition());
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)) + yawOffset;
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.setXRot(pitch);
	}

	private static SoldierShieldEntity shieldOf(GameTestHelper helper, ServerPlayer p) {
		List<SoldierShieldEntity> l = helper.getLevel().getEntitiesOfClass(SoldierShieldEntity.class, new AABB(p.blockPosition()).inflate(40),
				s -> p.getUUID().equals(s.ownerId()));
		return l.isEmpty() ? null : l.get(0);
	}

	private static double angleDeg(Vec3 a, Vec3 b) {
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.normalize().dot(b.normalize())))));
	}

	private static float took(Zombie z) {
		return 200.0f - z.getHealth();
	}

	// ---------------------------------------------------------------- aim + speed

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void throwFliesAlongTheAimPitchIncludedAtTheSlowerSpeed(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 3.5, 1.5);
		p.setYRot(25.0f);
		p.setXRot(-30.0f); // looking up and to the side: nothing to hit
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		Vec3 look = p.getLookAngle();
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown");
		SoldierShieldEntity s = shieldOf(helper, p);
		helper.assertTrue(s != null, "a shield in flight");
		Vec3 v = s.getDeltaMovement();
		helper.assertTrue(angleDeg(v, look) < 0.5, "flies along his look (pitch included), off by " + angleDeg(v, look) + " deg");
		helper.assertTrue(v.y > 0.4, "pitched up 30 degrees, it climbs: vy " + v.y);
		helper.assertTrue(s.assistTargetId() == -1, "nothing to assist onto");
		// slower: 1.0 b/t, was 1.6 (and the Shield Storm 1.3, was 2.1)
		helper.assertTrue(Math.abs(v.length() - SuperSoldierConfig.SHIELD_SPEED) < 1.0e-6, "speed " + v.length());
		helper.assertTrue(SuperSoldierConfig.SHIELD_SPEED <= 1.6 * 0.7 && SuperSoldierConfig.SHIELD_SPEED >= 1.6 * 0.5,
				"noticeably slower than the old 1.6");
		helper.assertTrue(SuperSoldierConfig.STORM_SHIELD_SPEED <= 2.1 * 0.7, "the Storm's throw slowed too");
		helper.assertTrue(Math.abs(s.flightSpeed() - SuperSoldierConfig.SHIELD_SPEED) < 1.0e-9, "entity speed");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(shieldOf(helper, p) == null, "it came back"))
				.thenExecute(() -> helper.assertTrue(p.getMainHandItem().is(SuperSoldierItems.ADAMANTIUM_SHIELD), "caught"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aimAssistOnlyInsideTheCone(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 3.5, 1.0);
		Zombie z = zombie(helper, p, 0.0, 5.0);
		Vec3 eye = p.getEyePosition();
		Vec3 c = z.getBoundingBox().getCenter();
		Vec3 straight = c.subtract(eye).normalize();
		// turned about the vertical axis: 5 degrees (inside the 8-degree cone, the crosshair off its box) -> assisted
		Vec3 in = straight.yRot((float) Math.toRadians(5.0));
		helper.assertTrue(!z.getBoundingBox().clip(eye, eye.add(in.scale(25))).isPresent(), "5 deg: the crosshair is off its box");
		helper.assertTrue(SoldierShieldEntity.assistTarget(p, eye, in, SuperSoldierConfig.SHIELD_RANGE) == z, "5 deg off: assisted");
		// 12 degrees: outside the cone -> no assist
		Vec3 out = straight.yRot((float) Math.toRadians(12.0));
		helper.assertTrue(SoldierShieldEntity.assistTarget(p, eye, out, SuperSoldierConfig.SHIELD_RANGE) == null, "12 deg off: no assist");
		// behind him / out of range: never
		helper.assertTrue(SoldierShieldEntity.assistTarget(p, eye, straight.scale(-1), SuperSoldierConfig.SHIELD_RANGE) == null, "behind: no");
		helper.assertTrue(SoldierShieldEntity.assistTarget(p, eye, straight, 3.0) == null, "out of range: no");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void assistedThrowBendsOntoTheEnemyAndHitsIt(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 3.5, 1.0);
		Zombie z = zombie(helper, p, 0.0, 5.0);
		lookAt(p, z.getBoundingBox().getCenter(), 6.0f);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		Vec3 look = p.getLookAngle();
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown");
		SoldierShieldEntity s = shieldOf(helper, p);
		helper.assertTrue(s.assistTargetId() == z.getId(), "the zombie 6 deg off the aim is the assist target");
		Vec3 toZ = z.getBoundingBox().getCenter().subtract(s.position());
		helper.assertTrue(angleDeg(s.getDeltaMovement(), toZ) < 0.5, "bent onto it");
		helper.assertTrue(angleDeg(s.getDeltaMovement(), look) <= SuperSoldierConfig.SHIELD_ASSIST_CONE_DEG + 1.0, "a nudge, not a lock-on");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Math.abs(took(z) - SuperSoldierConfig.SHIELD_DAMAGE) < 0.05f, "hit for 9, took " + took(z)))
				.thenWaitUntil(() -> helper.assertTrue(shieldOf(helper, p) == null, "it came back"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void throwOutsideTheConeFliesStraightAndMisses(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 3.5, 1.0);
		Zombie z = zombie(helper, p, 0.0, 5.0);
		lookAt(p, z.getBoundingBox().getCenter(), 16.0f);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		Vec3 look = p.getLookAngle();
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown");
		SoldierShieldEntity s = shieldOf(helper, p);
		helper.assertTrue(s.assistTargetId() == -1, "16 deg off: no assist (the old throw homed on anything in a 57-degree cone)");
		helper.assertTrue(angleDeg(s.getDeltaMovement(), look) < 0.5, "straight along the aim");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(shieldOf(helper, p) == null, "it came back"))
				.thenExecute(() -> helper.assertTrue(took(z) == 0.0f, "the zombie it was not aimed at is untouched, took " + took(z)))
				.thenSucceed();
	}

	// ---------------------------------------------------------------- bounces

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void bouncesStopAtFive(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 3.5, 1.0);
		// seven zombies, all within 6 blocks of each other: the first hit plus 5 bounces = 6 hit, one left over
		double[][] at = { { 0, 3 }, { 1.5, 3 }, { -1.5, 3 }, { 1.5, 5 }, { -1.5, 5 }, { 0, 5 }, { 2.5, 4 } };
		List<Zombie> zs = new ArrayList<>();
		for (double[] a : at) {
			zs.add(zombie(helper, p, a[0], a[1]));
		}
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown");
		int[] maxBounces = { 0 };
		helper.onEachTick(() -> {
			SoldierShieldEntity s = shieldOf(helper, p);
			if (s != null) {
				maxBounces[0] = Math.max(maxBounces[0], s.bounceCount());
				helper.assertTrue(s.bounceCount() <= SuperSoldierConfig.SHIELD_MAX_BOUNCES, "never more than 5 bounces");
			}
		});
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(zs.stream().filter(z -> took(z) > 0).count() >= 6, "six hit"))
				.thenWaitUntil(() -> helper.assertTrue(shieldOf(helper, p) == null, "it came back"))
				.thenExecute(() -> {
					long hitOnce = zs.stream().filter(z -> Math.abs(took(z) - SuperSoldierConfig.SHIELD_DAMAGE) < 0.05f).count();
					long untouched = zs.stream().filter(z -> took(z) == 0.0f).count();
					helper.assertTrue(hitOnce == 6 && untouched == 1, "first hit + 5 bounces: " + hitOnce + " hit once, " + untouched + " untouched");
					helper.assertTrue(maxBounces[0] == SuperSoldierConfig.SHIELD_MAX_BOUNCES, "5 bounces made, saw " + maxBounces[0]);
					helper.assertTrue(p.getMainHandItem().is(SuperSoldierItems.ADAMANTIUM_SHIELD), "caught");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void bounceOnlyToEnemiesCloseToTheOneHit(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 0.5, 0.5);
		Zombie first = zombie(helper, p, 0.0, 1.5);
		Zombie far = zombie(helper, p, 6.0, 6.0); // 7.5 blocks from the first: too far to bounce to
		double gap = first.getBoundingBox().getCenter().distanceTo(far.getBoundingBox().getCenter());
		helper.assertTrue(gap > SuperSoldierConfig.SHIELD_BOUNCE_RANGE, "set-up: the far one is " + gap + " away");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
		int[] maxBounces = { 0 };
		helper.onEachTick(() -> {
			SoldierShieldEntity s = shieldOf(helper, p);
			if (s != null) {
				maxBounces[0] = Math.max(maxBounces[0], s.bounceCount());
			}
		});
		helper.assertTrue(SuperSoldierAbilities.shieldThrow(p), "thrown");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(took(first) > 0, "the one in front is hit"))
				.thenWaitUntil(() -> helper.assertTrue(shieldOf(helper, p) == null, "it came back"))
				.thenExecute(() -> {
					helper.assertTrue(took(far) == 0.0f, "no bounce to the far one, took " + took(far));
					helper.assertTrue(maxBounces[0] == 0, "no bounce at all, saw " + maxBounces[0]);
				})
				.thenSucceed();
	}

	// ---------------------------------------------------------------- passives

	@GameTest(template = EMPTY_STRUCTURE)
	public void jumpClearsTwoBlocksButNotThree(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 3.5, 3.5);
		p.setOnGround(true);
		p.setDeltaMovement(Vec3.ZERO);
		p.jumpFromGround(); // the real vanilla jump: JUMP_STRENGTH x the block's jump factor
		double v = p.getDeltaMovement().y;
		helper.assertTrue(Math.abs(v - p.getAttributeValue(Attributes.JUMP_STRENGTH)) < 1.0e-6, "launch = the jump attribute, " + v);
		// vanilla air physics (move, then gravity 0.08 and drag 0.98)
		double y = 0.0;
		double apex = 0.0;
		int above2 = 0;
		for (int t = 0; t < 80 && (t == 0 || y > 0.0); t++) {
			y += v;
			apex = Math.max(apex, y);
			if (y > 2.0) {
				above2++;
			}
			v = (v - 0.08) * 0.98;
		}
		helper.assertTrue(apex >= 2.0 && apex < 3.0, "jump apex 2..3 blocks, got " + apex);
		helper.assertTrue(above2 >= 4, "feet over a two-block wall for " + above2 + " ticks: enough to step onto it");
		helper.assertTrue(Math.abs(apex - SuperSoldierConfig.JUMP_BLOCKS) < 0.05, "apex ~" + SuperSoldierConfig.JUMP_BLOCKS + ", got " + apex);
		SuperSoldier.revoke(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.JUMP_STRENGTH) - 0.42) < 1.0e-6, "revoked: vanilla jump again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void passiveResistanceOneWhileThePowerIsHeld(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 3.5, 3.5);
		SuperSoldierAbilityManager.serverTick(p);
		MobEffectInstance r = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(r != null && r.getAmplifier() == 0, "Resistance I on");
		// it never runs out: let it run low, the tick tops it up
		p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 0, true, false, true));
		SuperSoldierAbilityManager.serverTick(p);
		r = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(r != null && r.getAmplifier() == 0 && r.getDuration() > SuperSoldierConfig.PASSIVE_RESISTANCE_REFRESH,
				"topped up, duration " + (r == null ? -1 : r.getDuration()));
		// milk / cleared: back next tick
		p.removeAllEffects();
		SuperSoldierAbilityManager.serverTick(p);
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "back after being cleared");
		// a stronger potion is left alone
		p.removeAllEffects();
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2));
		SuperSoldierAbilityManager.serverTick(p);
		helper.assertTrue(p.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 2, "Resistance III potion kept");
		p.removeAllEffects();
		SuperSoldierAbilityManager.serverTick(p);
		// power lost: gone
		SuperSoldier.revoke(p);
		helper.assertFalse(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "revoked: Resistance removed");
		SuperSoldierAbilityManager.serverTick(p);
		helper.assertFalse(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "and not given back");
		helper.succeed();
	}

	/** v0.17.7 (user: "shield throw should be able to target players"): the assist and the bounces pick other players, never squadmates. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aimAssistAndBouncesTargetPlayers(GameTestHelper helper) {
		helper.getLevel().getServer().setPvpAllowed(true);
		ServerPlayer p = soldier(helper, 3.5, 1.0);
		ServerPlayer rival = helper.makeMockServerPlayerInLevel();
		rival.setGameMode(GameType.SURVIVAL);
		rival.moveTo(p.getX(), p.getY(), p.getZ() + 5.0, 180.0f, 0.0f);
		Vec3 eye = p.getEyePosition();
		Vec3 straight = rival.getBoundingBox().getCenter().subtract(eye).normalize();
		Vec3 in = straight.yRot((float) Math.toRadians(5.0));
		helper.assertTrue(SoldierShieldEntity.seeks(p, rival), "another player is sought");
		helper.assertTrue(SoldierShieldEntity.canHit(p, rival), "and may be struck (PvP on, survival, not a squadmate)");
		helper.assertTrue(SoldierShieldEntity.assistTarget(p, eye, in, SuperSoldierConfig.SHIELD_RANGE) == rival, "5 deg off a player: assisted");
		helper.assertTrue(!SoldierShieldEntity.seeks(p, p), "never the thrower");
		helper.succeed();
	}
}

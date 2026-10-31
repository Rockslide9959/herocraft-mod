package com.projecthero.mod.gametest;

import java.util.List;

import com.mojang.serialization.JsonOps;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Wildmutt (Wildvine's replacement on the dial): his body (34 health, x1.35 speed, the 0.9 x 1.55 quadruped hitbox with
 * the eyes at 1.25 -- rework 2's slim, leggy body -- the paws that use no tools or weapons, and the player's box back after turning back), every move's damage or effect and its cooldown, the Scent Sense
 * range (24, 48 sniffing, 6 underwater, 0 deafened) and the blind immunities, Wall Climb on / off, and that a save made as
 * Wildvine loads as Wildmutt. Mock players are not ticked by the server: tests over time drive {@link Omnitrix#tick} themselves
 * (and {@code refreshDimensions}, which {@code Player.tick} does through {@code OmnitrixAlienBodyMixin}).
 */
public class OmnitrixWildmuttGameTests implements FabricGameTest {
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

	private static ServerPlayer wildmutt(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Omnitrix.select(p, WildmuttKit.ID);
		helper.assertTrue(Omnitrix.transform(p), "transformed into Wildmutt");
		p.setOnGround(true);
		p.refreshDimensions();
		return p;
	}

	private static WildmuttKit kit() {
		return (WildmuttKit) Aliens.kit(WildmuttKit.ID);
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, float health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05, what + " (expected " + expected + ", got " + actual + ")");
	}

	/** Puts {@code z} {@code dist} blocks straight ahead of {@code p} (who faces +Z). */
	private static void ahead(Zombie z, ServerPlayer p, double dist) {
		z.moveTo(p.getX(), p.getY(), p.getZ() + dist, 180f, 0f);
		z.setDeltaMovement(Vec3.ZERO);
		z.invulnerableTime = 0;
	}

	// ================================================================ the body

	@GameTest(template = EMPTY_STRUCTURE)
	public void wildmuttsBodyIsALowWideQuadrupedAndComesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		near(helper, p.getMaxHealth(), WildmuttKit.HEALTH, "34 health");
		near(helper, p.getAttributeValue(Attributes.MOVEMENT_SPEED), p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * WildmuttKit.SPEED, "x1.35 speed");
		near(helper, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.3, "knockback resistance 0.3");
		near(helper, p.getAttributeValue(Attributes.STEP_HEIGHT), 1.0, "steps up a full block");
		near(helper, p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.0 + WildmuttKit.ATTACK, "claws +4");
		near(helper, p.getBbWidth(), WildmuttKit.BODY_WIDTH, "a wide body");
		near(helper, p.getBbHeight(), WildmuttKit.BODY_HEIGHT, "a low body");
		near(helper, p.getEyeHeight(), WildmuttKit.EYE_HEIGHT, "the eyes (gills) low");
		helper.assertTrue(p.getBbWidth() > 0.6f && p.getBbHeight() < 1.8f, "wider and lower than a player");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
		p.refreshDimensions();
		near(helper, p.getBbWidth(), 0.6, "the player's width back");
		near(helper, p.getBbHeight(), 1.8, "the player's height back");
		near(helper, p.getEyeHeight(), 1.62, "the player's eyes back");
		near(helper, p.getMaxHealth(), 20.0, "20 health back");
		helper.succeed();
	}

	/**
	 * v0.17.1 rework 2 (user: "make it so that the player cant use tools"): paws -- a held pickaxe mines stone like a bare
	 * paw and harvests nothing, a held sword adds nothing to the claw hit; all back once he turns back.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void wildmuttsPawsUseNoToolsOrWeapons(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		helper.assertTrue(com.projecthero.mod.omnitrix.alien.AlienKit.pawsOnlyNow(p), "paws only");
		net.minecraft.world.level.block.state.BlockState stone = Blocks.STONE.defaultBlockState();
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		near(helper, p.getDestroySpeed(stone), 1.0, "a pickaxe mines like a bare paw");
		helper.assertFalse(p.hasCorrectToolForDrops(stone), "and harvests no stone");
		// v0.17.2: right-click is his own again -- food in the paw is eaten, not swallowed by a Sniff
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
		helper.assertTrue(kit().onUse(p, net.minecraft.world.InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.PASS, "right-click passes through (he can eat)");
		helper.assertTrue(com.projecthero.mod.omnitrix.alien.AlienKit.pawsCanUse(p.getMainHandItem()), "and paws may eat bread");
		// the claw hit with a sword in the slot: his own claws only
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		Zombie z = zombie(helper, 3.5, 2, 3.5, 100f);
		ahead(z, p, 1.5);
		helper.assertTrue(kit().onAttack(p, z), "the kit makes the hit itself");
		helper.assertTrue(z.getHealth() > 100f - (1f + WildmuttKit.ATTACK) - 0.01f, "no sword damage on top (" + z.getHealth() + ")");
		helper.assertTrue(z.getHealth() < 100f, "but the claws hit");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
		helper.assertFalse(com.projecthero.mod.omnitrix.alien.AlienKit.pawsOnlyNow(p), "hands again");
		helper.assertTrue(p.getDestroySpeed(stone) > 4f, "the pickaxe works again");
		helper.assertTrue(p.hasCorrectToolForDrops(stone), "and harvests stone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyMoveHasItsKeyAndNeverHOrN(GameTestHelper helper) {
		List<AlienMove> moves = kit().moves();
		helper.assertTrue(moves.size() == 14, "14 moves, Dig on V (" + moves.size() + ")");
		for (AlienMove m : moves) {
			if (m.onSlot()) {
				char k = m.slot().defaultKey();
				helper.assertTrue("RGXZCV".indexOf(Character.toUpperCase(k)) >= 0, m.id() + " sits on R / G / X / Z / C / V (" + k + ")");
			}
		}
		helper.assertTrue(kit().moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_3, false, true) != null, "a hold move on X");
		helper.succeed();
	}

	// ================================================================ the moves

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void wildmuttsMovesHitAndCoolDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		Zombie z = zombie(helper, 3.5, 2, 3.0, 200f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 5f);
		// Pounce: a lunge, then the bite on contact
		ahead(z, p, 1.3);
		helper.assertTrue(k.pounce(p) && k.flag(p, WildmuttKit.F_POUNCE), "Pounce");
		helper.assertTrue(p.getDeltaMovement().z > 0.3, "lunging at it");
		helper.assertFalse(k.pounce(p), "Pounce cools down");
		p.tickCount++;
		Omnitrix.tick(p);
		near(helper, z.getHealth(), 200f - WildmuttKit.POUNCE_DAMAGE, "the bite does 12");
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and pins it");
		helper.assertFalse(k.flag(p, WildmuttKit.F_POUNCE), "the pounce is over");
		// the timed moves: Maul, Quill Volley, Savage Roar, Ground Slam
		ahead(z, p, 2.0);
		helper.assertTrue(k.maul(p) && !k.maul(p), "Maul, then it cools down");
		helper.assertTrue(k.quills(p) && !k.quills(p), "Quill Volley, then it cools down");
		helper.assertTrue(k.roar(p) && !k.roar(p), "Savage Roar, then it cools down");
		helper.assertTrue(k.slam(p) && !k.slam(p), "Ground Slam, then it cools down");
		float before = z.getHealth();
		pump(helper, p);
		helper.runAfterDelay(WildmuttKit.MAUL_HITS[WildmuttKit.MAUL_HITS.length - 1] + 3, () -> {
			float lost = before - z.getHealth();
			float maul = 3 * WildmuttKit.MAUL_RAKE + WildmuttKit.MAUL_SLAM;
			// everything lands on the one zombie: the maul (18), the roar (6) and the slam (10..16); quills may add
			helper.assertTrue(lost >= maul + WildmuttKit.ROAR_DAMAGE + WildmuttKit.SLAM_FAR - 0.1f,
					"the maul, the roar and the slam all hit (" + lost + ")");
			helper.assertTrue(z.hasEffect(MobEffects.WEAKNESS), "the roar weakens");
			AABB box = p.getBoundingBox().inflate(20);
			int quills = helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, box, e -> "wildmutt_quill".equals(e.kind())).size();
			helper.assertTrue(quills > 0 || z.getHealth() < before - maul - WildmuttKit.ROAR_DAMAGE - WildmuttKit.SLAM_FAR, "the quills flew (" + quills + " still out)");
			// Quill Roll: rolls through it once
			z.setHealth(200f);
			ahead(z, p, 0.9);
			helper.assertTrue(k.roll(p) && k.flag(p, WildmuttKit.F_ROLL), "Quill Roll");
			helper.assertFalse(k.roll(p), "the roll cools down");
			helper.runAfterDelay(2, () -> {
				near(helper, z.getHealth(), 200f - WildmuttKit.ROLL_DAMAGE, "the roll hits it once for 12");
				float hp = p.getHealth();
				p.invulnerableTime = 0;
				p.hurt(helper.getLevel().damageSources().generic(), 10f);
				helper.assertTrue(hp - p.getHealth() < 10f * WildmuttKit.ROLL_FACTOR + 0.6f, "rolling, he takes 40% less (" + (hp - p.getHealth()) + ")");
				// Feral Frenzy
				double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
				helper.assertTrue(k.frenzy(p) && k.flag(p, WildmuttKit.F_FRENZY), "Feral Frenzy");
				near(helper, p.getAttributeValue(Attributes.ATTACK_DAMAGE), base + WildmuttKit.FRENZY_CLAWS, "+4 claws");
				helper.assertFalse(k.frenzy(p), "the frenzy cools down");
				// Scent Track: the zombie in front
				helper.assertTrue(k.track(p) && k.tracked(p) == z, "Scent Track locks onto the zombie");
				helper.assertFalse(k.track(p), "the track cools down");
				// Leap
				helper.assertTrue(k.leap(p) && k.flag(p, WildmuttKit.F_LEAPING) && p.getDeltaMovement().y > 0.4, "Leap");
				helper.assertFalse(k.leap(p), "the leap cools down");
				helper.assertFalse(p.hurt(helper.getLevel().damageSources().fall(), 6f), "no fall damage from his own leap");
				// Sniff
				helper.assertTrue(k.sniff(p) && k.flag(p, WildmuttKit.F_SNIFF), "Sniff");
				helper.assertFalse(k.sniff(p), "the sniff cools down");
				z.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theCoiledLeapGoesFurtherTheLongerItIsHeld(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		k.holdStart(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3);
		helper.assertTrue(k.flag(p, WildmuttKit.F_COIL), "coiling");
		helper.runAfterDelay(WildmuttKit.COIL_MAX, () -> {
			k.holdEnd(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3, WildmuttKit.COIL_MAX);
			helper.assertFalse(k.flag(p, WildmuttKit.F_COIL), "released");
			double full = p.getDeltaMovement().horizontalDistance();
			helper.assertTrue(full > 1.15 * 1.6, "a full coil springs ~1.8x as far (" + full + ")");
			helper.assertTrue(k.cooldownLeft(p, WildmuttKit.COILED_LEAP) > 0, "the coiled leap cools down");
			helper.succeed();
		});
	}

	// ================================================================ the senses

	@GameTest(template = EMPTY_STRUCTURE)
	public void theScentSenseWidensShrinksAndGoesDeaf(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		near(helper, k.senseRange(p), WildmuttKit.SENSE_RANGE, "24 blocks");
		k.sniff(p);
		near(helper, k.senseRange(p), WildmuttKit.SNIFF_RANGE, "48 while sniffing");
		k.setFlag(p, WildmuttKit.F_SNIFF, false);
		// no eyes: blindness and darkness do nothing
		p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100));
		p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100));
		p.tickCount++;
		Omnitrix.tick(p);
		helper.assertTrue(!p.hasEffect(MobEffects.BLINDNESS) && !p.hasEffect(MobEffects.DARKNESS), "blindness and darkness are shrugged off");
		// an explosion deafens him
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().explosion(null, null), 2f);
		near(helper, k.senseRange(p), 0, "deafened");
		k.setTimer(p, "deaf_until", 0);
		near(helper, k.senseRange(p), WildmuttKit.SENSE_RANGE, "and back after it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void wallClimbTurnsOnAndOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = wildmutt(helper);
		WildmuttKit k = kit();
		p.horizontalCollision = true;
		helper.assertFalse(k.climbs(p), "no climbing with Wall Climb off");
		helper.assertTrue(k.toggleClimb(p) && k.flag(p, WildmuttKit.F_CLIMB), "Wall Climb on");
		helper.assertTrue(k.climbs(p) && p.onClimbable(), "pressed into a wall he climbs it");
		p.setShiftKeyDown(true);
		helper.assertFalse(k.climbs(p), "Sneak lets go");
		p.setShiftKeyDown(false);
		helper.assertFalse(p.hurt(helper.getLevel().damageSources().fall(), 5f), "no fall damage with Wall Climb on");
		helper.assertFalse(k.toggleClimb(p), "the toggle has a short cooldown");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(k.toggleClimb(p) && !k.flag(p, WildmuttKit.F_CLIMB), "Wall Climb off");
			helper.assertFalse(k.climbs(p), "no more climbing");
			p.horizontalCollision = false;
			helper.succeed();
		});
	}

	// ================================================================ saves made as Wildvine

	@GameTest(template = EMPTY_STRUCTURE)
	public void aWildvineSaveLoadsAsWildmutt(GameTestHelper helper) {
		OmnitrixState old = new OmnitrixState();
		old.hasPower = true;
		old.selected = "wildvine";
		old.active = "wildvine";
		old.kitFlags = 5;
		old.animId = 7;
		old.timers.put("wildvine.regrow_end", 1234L);
		old.timers.put("heatblast.fire_blast", 99L);
		var json = OmnitrixState.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow();
		OmnitrixState back = OmnitrixState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue("wildmutt".equals(back.selected), "selected wildvine -> wildmutt (" + back.selected + ")");
		helper.assertTrue("wildmutt".equals(back.active), "active wildvine -> wildmutt (" + back.active + ")");
		helper.assertTrue(back.kitFlags == 0 && back.animId == 0, "Wildvine's bits and pose dropped");
		helper.assertTrue(!back.timers.containsKey("wildvine.regrow_end") && back.timers.get("heatblast.fire_blast") == 99L,
				"Wildvine's timers dropped, the rest kept");
		helper.assertTrue(Aliens.playable(back.selected), "and Wildmutt is playable");
		helper.assertTrue(Aliens.byId("wildvine") == null, "Wildvine is off the dial");
		// a player loaded that way transforms into Wildmutt without trouble
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		OmnitrixState n = Omnitrix.state(p).copy();
		n.selected = back.selected;
		Omnitrix.save(p, n);
		helper.assertTrue(Omnitrix.transform(p) && WildmuttKit.ID.equals(Omnitrix.state(p).active), "transforms into Wildmutt");
		helper.succeed();
	}
}

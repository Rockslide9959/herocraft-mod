package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.waybig.WayBigKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Way Big (user: "add waybig too please ... Make him 100 blocks tall please. Does not get slowed down by water or lava and can
 * walk safely through both ... create an item that lets the user unlock this alien"): unlocked only by the item, his giant body (eyes
 * 90 blocks up, a small hitbox, fluids ignored, the To'kustar immunities, the head fin), Size Shift, and his moves reaching the ground
 * from the sky.
 */
public class OmnitrixWayBigGameTests implements FabricGameTest {
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

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
				// the test's barrier ceiling would stop his look from 90 blocks up: open the sky over the floor
				for (int y = 2; y <= 24; y++) {
					BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
					if (helper.getLevel().getBlockState(at).is(Blocks.BARRIER)) {
						helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	private static ServerPlayer wayBig(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, WayBigKit.ID, true); // the To'kustar Shard (its own test below)
		helper.assertTrue(Omnitrix.select(p, WayBigKit.ID), "Way Big is on the prototype's dial once unlocked");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Way Big");
		p.refreshDimensions();
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		return p;
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
		m.setHealth(400);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	/**
	 * v0.17.2 (user: "save the data to the player and not the omnitrix so if the player takes off the omnitrix and puts it back on
	 * they still have all these aliens unlocked please"): the unlocks and the mistransform count outlive the watch.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void unlockedAliensStayWithThePlayerWhenTheWatchComesOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, WayBigKit.ID, true);
		Aliens.discover(p, Aliens.CANNONBOLT.id(), true);
		Omnitrix.setMistransforms(p, 7);
		Omnitrix.revoke(p);
		helper.assertFalse(Omnitrix.hasPower(p), "the watch is off");
		helper.assertTrue(Omnitrix.mistransforms(p) == 0, "no watch, no count shown");
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		Omnitrix.revoke(p);
		helper.assertTrue(Omnitrix.grant(p), "the prototype goes back on");
		helper.assertTrue(Aliens.found(p, WayBigKit.ID) && Aliens.found(p, Aliens.CANNONBOLT.id()), "both unlocks kept");
		helper.assertTrue(Aliens.playable(p).contains(Aliens.WAY_BIG) && Aliens.playable(p).contains(Aliens.CANNONBOLT), "still on the dial");
		helper.assertTrue(Omnitrix.mistransforms(p) == 7, "the mistransform count kept: " + Omnitrix.mistransforms(p));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wayBigIsUnlockedOnlyByHisItemNeverByAMistransform(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertTrue(Aliens.hidden(WayBigKit.ID) && Aliens.itemOnly(WayBigKit.ID), "Way Big is hidden and item-only");
		helper.assertFalse(Aliens.playable(p).contains(Aliens.WAY_BIG), "not on the dial before he is unlocked");
		helper.assertFalse(Omnitrix.select(p, WayBigKit.ID), "and cannot be dialled");
		helper.assertTrue(Aliens.nextHidden(p) != Aliens.WAY_BIG, "a mistransform never finds him");
		Omnitrix.setMistransforms(p, 1000);
		for (Aliens.Alien a : Aliens.all()) {
			if (Aliens.hidden(a.id()) && !Aliens.itemOnly(a.id())) {
				Aliens.discover(p, a.id(), true);
			}
		}
		helper.assertTrue(Aliens.nextHidden(p) == null, "with the mistransform aliens found, nothing is left for a glitch to find");
		Aliens.discover(p, WayBigKit.ID, true);
		helper.assertTrue(Aliens.playable(p).contains(Aliens.WAY_BIG) && Omnitrix.select(p, WayBigKit.ID), "unlocked: on the dial");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wayBigIsAHundredBlockGiantOnASmallHitbox(GameTestHelper helper) {
		ServerPlayer p = wayBig(helper);
		helper.assertTrue(Math.abs(p.getEyeHeight() - WayBigKit.COLOSSAL_EYE) < 0.5f, "his eyes are 90 blocks up: " + p.getEyeHeight());
		helper.assertTrue(Math.abs(p.getBbHeight() - WayBigKit.COLOSSAL_BOX_H) < 0.1f, "the hitbox is his feet and shins: " + p.getBbHeight());
		helper.assertTrue(p.getBoundingBoxForCulling().getYsize() > 99, "drawn 100 blocks tall (culling): " + p.getBoundingBoxForCulling().getYsize());
		helper.assertFalse(p.isAffectedByFluids() || p.isPushedByFluid(), "water and lava never slow or push him");
		helper.assertTrue(p.maxUpStep() >= 40, "steps over hills: " + p.maxUpStep());
		// V: Size Shift to 15 blocks and back
		tap(p, AbilitySlot.SLOT_5, false);
		p.refreshDimensions();
		helper.assertTrue(Math.abs(p.getEyeHeight() - WayBigKit.COMPACT_EYE) < 0.5f, "Compact: eyes 13.5 up: " + p.getEyeHeight());
		helper.assertTrue(Aliens.kit(WayBigKit.ID).keyActive(p, AbilitySlot.SLOT_5), "V lights while Compact");
		Aliens.kit(WayBigKit.ID).cooldown(p, WayBigKit.SHIFT, 0);
		tap(p, AbilitySlot.SLOT_5, false);
		p.refreshDimensions();
		helper.assertTrue(Math.abs(p.getEyeHeight() - WayBigKit.COLOSSAL_EYE) < 0.5f, "back to 100 blocks");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		p.refreshDimensions();
		helper.assertTrue(p.getEyeHeight() < 2f && p.isAffectedByFluids(), "turning back gives him his own body");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theTokustarBodyShrugsOffLavaFireDrowningAndFallsButNotTheFin(GameTestHelper helper) {
		ServerPlayer p = wayBig(helper);
		var src = helper.getLevel().damageSources();
		float hp = p.getHealth();
		for (DamageSource s : new DamageSource[] { src.lava(), src.inFire(), src.onFire(), src.drown(), src.fall(), src.inWall() }) {
			p.invulnerableTime = 0;
			p.hurt(s, 20f);
		}
		helper.assertTrue(p.getHealth() == hp, "lava, fire, drowning, falls and walls never hurt him: " + p.getHealth());
		AlienKit kit = Aliens.kit(WayBigKit.ID);
		var magic = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
				.getHolderOrThrow(DamageTypes.MAGIC);
		float low = kit.incomingDamage(p, new DamageSource(magic, p.position().add(0, 3, 0)), 10f);
		float high = kit.incomingDamage(p, new DamageSource(magic, p.position().add(0, 95, 0)), 10f);
		helper.assertTrue(low == 10f && high == 20f, "the head fin: a blow from above his shoulders doubles (" + low + ", " + high + ")");
		helper.succeed();
	}

	/**
	 * v0.17.2 (audit: the fin almost never fired): a shot's source position is where it strikes -- his shins -- so what counts is who threw
	 * it and how it came in. A shooter up at his head, or a shot falling on him from overhead, is on the fin; a flat shot from the ground and
	 * a zombie at his feet are not. Compact, the fin is 12.3 blocks up.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theHeadFinCountsWhereTheBlowCameFromNotWhereTheShotLanded(GameTestHelper helper) {
		ServerPlayer p = wayBig(helper);
		AlienKit kit = Aliens.kit(WayBigKit.ID);
		Zombie archer = zombie(helper, 3.5, 2, 6.5);
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.moveTo(p.getX(), p.getY() + 3, p.getZ());
		// a flat shot from the ground, the archer level with his feet
		arrow.setDeltaMovement(0, -0.1, -2);
		helper.assertTrue(kit.incomingDamage(p, helper.getLevel().damageSources().arrow(arrow, archer), 10f) == 10f, "a flat shot on his shins is no fin hit");
		helper.assertTrue(kit.incomingDamage(p, helper.getLevel().damageSources().mobAttack(archer), 10f) == 10f, "nor a zombie at his feet");
		// the same shot landing in the same place, shot from up at his head
		archer.moveTo(archer.getX(), p.getY() + 90, archer.getZ());
		helper.assertTrue(kit.incomingDamage(p, helper.getLevel().damageSources().arrow(arrow, archer), 10f) == 20f, "shot from up at his head: on the fin");
		// a shot dropping out of the sky, from no shooter at all
		arrow.setDeltaMovement(0.2, -2, 0);
		helper.assertTrue(kit.incomingDamage(p, helper.getLevel().damageSources().arrow(arrow, null), 10f) == 20f, "falling straight down on him: on the fin");
		arrow.setDeltaMovement(1.6, -1.2, 0);
		helper.assertTrue(kit.incomingDamage(p, helper.getLevel().damageSources().arrow(arrow, null), 10f) == 10f, "a shot at a shallow slant is not");
		// Compact, his shoulders are 12.3 blocks up
		tap(p, AbilitySlot.SLOT_5, false);
		archer.moveTo(archer.getX(), p.getY() + 13, archer.getZ());
		arrow.setDeltaMovement(0, -0.1, -2);
		helper.assertTrue(kit.incomingDamage(p, helper.getLevel().damageSources().arrow(arrow, archer), 10f) == 20f, "Compact: a shooter 13 blocks up is above his shoulders");
		helper.succeed();
	}

	/** v0.17.2 (user: "make his moves stronger since hes huge and really powerful please"): every move past every other alien's ultimate scale. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyMoveHitsFarPastTheNormalScale(GameTestHelper helper) {
		helper.assertTrue(WayBigKit.FIST_DAMAGE == 100f && WayBigKit.CHOP_DAMAGE == 90f && WayBigKit.STOMP_DAMAGE == 85f && WayBigKit.STRIDE_DAMAGE == 70f
				&& WayBigKit.CLAP_DAMAGE == 65f, "the blows");
		helper.assertTrue(WayBigKit.RAY_DAMAGE == 20f && WayBigKit.RAY_BLAST == 100f, "the Cosmic Ray");
		helper.assertTrue(WayBigKit.CYCLONE_DAMAGE == 15f && WayBigKit.CYCLONE_FLING == 100f, "the cyclone");
		ServerPlayer p = wayBig(helper);
		Zombie z = zombie(helper, 3.5, 2, 6.5);
		z.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.position());
		float before = z.getHealth();
		helper.runAfterDelay(30, () -> tap(p, AbilitySlot.SLOT_1, false));
		helper.runAfterDelay(30 + WayBigKit.FIST_DELAY + 2, () -> {
			helper.assertTrue(before - z.getHealth() >= 80f, "the Titan Fist really lands for about 100: " + (before - z.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theTitanFistAndTheQuakeStompReachTheGroundFromTheSky(GameTestHelper helper) {
		ServerPlayer p = wayBig(helper);
		Zombie far = zombie(helper, 3.5, 2, 6.5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, far.position());
		float before = far.getHealth();
		helper.runAfterDelay(30, () -> tap(p, AbilitySlot.SLOT_1, false)); // after the transform's flash
		helper.runAfterDelay(30 + WayBigKit.FIST_DELAY + 3, () -> {
			helper.assertTrue(far.getHealth() < before, "the Titan Fist lands from 90 blocks up: " + far.getHealth() + " look " + WayBigKit.lookPoint(p, 200) + " zombie " + far.position() + " eye " + p.getEyePosition());
			float b2 = far.getHealth();
			tap(p, AbilitySlot.SLOT_4, false);
			helper.assertTrue(far.getHealth() == b2, "the foot is still in the air");
			helper.assertTrue(Aliens.kit(WayBigKit.ID).cooldownLeft(p, WayBigKit.STOMP) > 0, "Z cools down");
			helper.runAfterDelay(WayBigKit.STOMP_DELAY + 2, () -> {
				helper.assertTrue(far.getHealth() < b2, "the Quake Stomp's ring reaches it when the foot lands: " + far.getHealth());
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theCosmicRayBurnsWhatHeLooksAt(GameTestHelper helper) {
		ServerPlayer p = wayBig(helper);
		Zombie z = zombie(helper, 3.5, 2, 6.5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
		float before = z.getHealth();
		helper.runAfterDelay(30, () -> {
			tap(p, AbilitySlot.SLOT_2, false);
			helper.assertTrue(Aliens.kit(WayBigKit.ID).keyActive(p, AbilitySlot.SLOT_2), "G lights while the ray fires");
		});
		helper.runAfterDelay(30 + WayBigKit.RAY_TAP_TICKS + 4, () -> {
			helper.assertTrue(z.getHealth() < before - WayBigKit.RAY_DAMAGE, "the beam burns it: " + z.getHealth() + " look " + WayBigKit.lookPoint(p, 200) + " from " + WayBigKit.rayOrigin(p));
			helper.assertFalse(Aliens.kit(WayBigKit.ID).keyActive(p, AbilitySlot.SLOT_2), "the burst ends");
			helper.assertTrue(Aliens.kit(WayBigKit.ID).cooldownLeft(p, WayBigKit.RAY) > 0, "then G cools down");
			helper.succeed();
		});
	}
}

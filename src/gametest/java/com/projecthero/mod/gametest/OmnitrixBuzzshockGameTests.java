package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.buzzshock.BuzzshockCopyEntity;
import com.projecthero.mod.omnitrix.alien.buzzshock.BuzzshockKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Buzzshock (user: "Also build buzzshock please heres the skin: https://www.planetminecraft.com/skin/buzzshock-ben-10-5321154/"
 * ... "Make this another alien you unlock on the 11th mistransformation ... make it an even chance"): a hidden prototype alien found by
 * a mistransform (the draw itself is {@code OmnitrixCannonboltGameTests} / {@code OmnitrixDittoGameTests}, which take every hidden
 * alien); his dial glyph; every key has a move; his bolts hit machines harder; Overload knocks them offline; his flying spark copies
 * shock, merge back and are spent by Megavolt; Spark Zip, Spark Flight and Power Line Ride move him; water shorts him out and
 * lightning heals him.
 */
public class OmnitrixBuzzshockGameTests implements FabricGameTest {
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
			}
		}
	}

	private static ServerPlayer buzzshock(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, BuzzshockKit.ID, true); // found by a mistransform (the draw's own tests)
		helper.assertTrue(Omnitrix.select(p, BuzzshockKit.ID), "Buzzshock is on the prototype's dial once found");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Buzzshock");
		helper.onEachTick(() -> Omnitrix.tick(p));
		return p;
	}

	private static BuzzshockKit kit() {
		return (BuzzshockKit) Aliens.kit(BuzzshockKit.ID);
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		if (m instanceof Zombie) {
			m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		}
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void lookAt(ServerPlayer p, LivingEntity z) {
		Vec3 d = z.position().add(0, z.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
		float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
		float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
	}

	// ---------------------------------------------------------------- the dial

	@GameTest(template = EMPTY_STRUCTURE)
	public void buzzshockIsAHiddenPrototypeAlien(GameTestHelper helper) {
		helper.assertTrue(Aliens.byId(BuzzshockKit.ID) == Aliens.BUZZSHOCK && !Aliens.BUZZSHOCK.recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.hidden(BuzzshockKit.ID) && !Aliens.itemOnly(BuzzshockKit.ID) && Aliens.mistransformFinds().contains(Aliens.BUZZSHOCK),
				"hidden, found by a mistransform (no item)");
		helper.assertTrue(Aliens.dial(false).contains(Aliens.BUZZSHOCK) && !Aliens.dial(true).contains(Aliens.BUZZSHOCK), "on the prototype's dial only");
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		helper.assertFalse(Aliens.playable(p).contains(Aliens.BUZZSHOCK), "not on the dial before he is found");
		helper.assertFalse(Omnitrix.select(p, BuzzshockKit.ID), "and cannot be dialled");
		helper.assertTrue(Aliens.unfound(p).contains(Aliens.BUZZSHOCK), "a mistransform can find him");
		Aliens.discover(p, BuzzshockKit.ID, true);
		helper.assertTrue(Aliens.playable(p).contains(Aliens.BUZZSHOCK) && !Aliens.unfound(p).contains(Aliens.BUZZSHOCK), "found: on the dial, out of the draw");
		helper.assertTrue(kit().stats().scale() < 0.5f, "a tiny alien");
		String glyph = OmnitrixInfo.icon(Aliens.BUZZSHOCK).getString();
		helper.assertTrue(glyph.length() == 1 && glyph.charAt(0) == '', "his dial glyph is U+E019, got " + (glyph.isEmpty() ? "none" : Integer.toHexString(glyph.charAt(0))));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasAMoveAndNoneIsOnHOrN(GameTestHelper helper) {
		AlienKit k = kit();
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(k.moveOn(s, false, false) != null, "a move on " + s);
			helper.assertTrue(k.moveOn(s, true, false) != null, "a Shift move on " + s);
		}
		for (var m : k.moves()) {
			helper.assertTrue(!m.onSlot() || m.slot().ordinal() <= AbilitySlot.SLOT_6.ordinal(), m.id() + " on R/G/X/Z/V/C only");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- shocks

	/** R hits a zombie for about 10; the same bolt on an iron golem (a machine) does half again. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void lightningBoltHitsMachinesHarder(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2, 6.5);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 5.5, 2, 6.5);
		helper.assertTrue(BuzzshockKit.machine(g) && !BuzzshockKit.machine(z), "a golem is a machine, a zombie is not");
		helper.runAfterDelay(2, () -> {
			lookAt(p, z);
			float zh = z.getHealth();
			tap(p, AbilitySlot.SLOT_1, false);
			float zd = zh - z.getHealth();
			helper.assertTrue(zd > 8f && zd <= 10.01f, "R: about 10 to the zombie, got " + zd);
			kit().cooldown(p, BuzzshockKit.BOLT, 0);
			lookAt(p, g);
			float gh = g.getHealth();
			tap(p, AbilitySlot.SLOT_1, false);
			float gd = gh - g.getHealth();
			helper.assertTrue(gd > 14f && gd <= 15.01f, "R on a machine: 15 (x1.5), got " + gd);
			helper.succeed();
		});
	}

	/** Shift+R: the bolt jumps from the first creature to the others near it. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void chainLightningJumps(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		Zombie a = mob(helper, EntityType.ZOMBIE, 3.5, 2, 5.5);
		Zombie b = mob(helper, EntityType.ZOMBIE, 5.5, 2, 6.5);
		Zombie c = mob(helper, EntityType.ZOMBIE, 1.5, 2, 6.5);
		helper.runAfterDelay(2, () -> {
			lookAt(p, a);
			tap(p, AbilitySlot.SLOT_1, true);
			helper.assertTrue(a.getHealth() < 20f - 12f, "the first takes 14: " + a.getHealth());
			helper.assertTrue(b.getHealth() < 20f - 8f && c.getHealth() < 20f - 8f, "it jumps to both others: " + b.getHealth() + " / " + c.getHealth());
			helper.assertTrue(kit().cooldownLeft(p, BuzzshockKit.CHAIN) > 0, "Shift+R cools down");
			helper.succeed();
		});
	}

	/** Z: an iron golem in the pulse takes about twice what a zombie does, and both are stunned. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void overloadShortsMachinesOut(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		Zombie z = mob(helper, EntityType.ZOMBIE, 1.5, 2, 4.5);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 5.5, 2, 4.5);
		helper.runAfterDelay(2, () -> {
			float zh = z.getHealth(), gh = g.getHealth();
			tap(p, AbilitySlot.SLOT_4, false);
			float zd = zh - z.getHealth(), gd = gh - g.getHealth();
			helper.assertTrue(zd > 10f, "the zombie takes about 12: " + zd);
			helper.assertTrue(gd > 23f && gd <= 24.01f, "the golem takes 24 (x2): " + gd);
			helper.assertTrue(g.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN) && z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
					"both stunned");
			helper.assertTrue(g.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN).getDuration() > z.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN).getDuration(),
					"the machine longer (offline)");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- copies

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void vSplitsFlyingCopiesUpToFourAndShiftVMergesThemBack(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		BuzzshockKit k = kit();
		for (int i = 0; i < 6; i++) {
			k.cooldown(p, BuzzshockKit.SPLIT, 0);
			tap(p, AbilitySlot.SLOT_5, false);
		}
		List<BuzzshockCopyEntity> l = BuzzshockKit.copies(p);
		helper.assertTrue(l.size() == BuzzshockKit.MAX_COPIES, "four copies at most: " + l.size());
		helper.assertTrue(l.stream().allMatch(BuzzshockCopyEntity::isNoGravity), "sparks fly");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(BuzzshockKit.copies(p).stream().allMatch(c -> c.getY() > p.getY() + 0.1), "they hover round him, off the ground");
			p.setHealth(6f);
			tap(p, AbilitySlot.SLOT_5, true);
			helper.assertTrue(BuzzshockKit.copies(p).isEmpty(), "Shift+V merges them all back");
			helper.assertTrue(p.getHealth() > 6f + 7f, "+2 health each: " + p.getHealth());
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(helper.getLevel().getEntitiesOfClass(BuzzshockCopyEntity.class, p.getBoundingBox().inflate(8)).isEmpty(), "gone from the world");
				helper.succeed();
			});
		});
	}

	/** C: a swarm of eight copies; they shock the creature near him on their own, then the extras fizzle and four stay. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void megawattSwarmShocksAndEnds(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		BuzzshockKit k = kit();
		helper.runAfterDelay(2, () -> {
			tap(p, AbilitySlot.SLOT_6, false);
			helper.assertTrue(k.flag(p, BuzzshockKit.F_SWARM) && BuzzshockKit.copies(p).size() == BuzzshockKit.SWARM_COPIES, "C: eight copies at once");
			float before = g.getHealth();
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(g.getHealth() < before - 10f, "the swarm shocks it on its own: " + (before - g.getHealth()));
				k.setTimer(p, BuzzshockKit.SWARM_UNTIL, 1);
				helper.runAfterDelay(2, () -> {
					helper.assertTrue(!k.flag(p, BuzzshockKit.F_SWARM) && BuzzshockKit.copies(p).size() <= BuzzshockKit.MAX_COPIES, "the swarm ends: back to four");
					Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
					helper.assertTrue(BuzzshockKit.copies(p).isEmpty(), "turning back fizzles every copy");
					helper.succeed();
				});
			});
		});
	}

	/** Shift+C: the copies dive in and discharge as one -- 16 + 3 a copy (x1.5 on a machine) -- and are spent. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void megavoltSpendsTheCopies(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		BuzzshockKit k = kit();
		helper.runAfterDelay(2, () -> {
			for (int i = 0; i < 3; i++) {
				k.cooldown(p, BuzzshockKit.SPLIT, 0);
				tap(p, AbilitySlot.SLOT_5, false);
			}
			lookAt(p, g);
			float before = g.getHealth();
			tap(p, AbilitySlot.SLOT_6, true);
			helper.assertTrue(BuzzshockKit.megavoltPending(p), "the copies are on their way");
			helper.runAfterDelay(BuzzshockKit.MEGAVOLT_DELAY + 3, () -> {
				float d = before - g.getHealth();
				helper.assertTrue(d > (16f + 3f * 3) * 1.5f - 2f, "16 + 3 for each of three copies, x1.5 on a machine (plus any copy zaps): " + d);
				helper.assertTrue(BuzzshockKit.copies(p).isEmpty(), "the copies are spent");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- movement

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void sparkZipStreaksForward(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		helper.runAfterDelay(2, () -> {
			p.setYRot(0f);
			p.setXRot(0f);
			double z0 = p.getZ();
			tap(p, AbilitySlot.SLOT_3, false);
			helper.assertTrue(p.getZ() - z0 > 4.0, "X streaks him forward: " + (p.getZ() - z0));
			helper.assertTrue(kit().flag(p, BuzzshockKit.F_ZIP), "no fall damage for a moment");
			helper.assertFalse(kit().allowDamage(p, p.damageSources().fall(), 5f), "fall damage is off while zipping");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void shiftXTogglesFlight(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		helper.runAfterDelay(2, () -> {
			tap(p, AbilitySlot.SLOT_3, true);
			helper.assertTrue(p.getAbilities().flying && p.getAbilities().mayfly, "Shift+X: flying");
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(kit().flag(p, BuzzshockKit.F_FLY) && kit().keyActive(p, AbilitySlot.SLOT_3), "the X box lights");
				kit().cooldown(p, BuzzshockKit.FLIGHT, 0);
				tap(p, AbilitySlot.SLOT_3, true);
				helper.assertFalse(p.getAbilities().flying || p.getAbilities().mayfly, "again: landed, flight taken away");
				helper.succeed();
			});
		});
	}

	/** Shift+Z: he rides into the lightning rod he looks at and comes out on it. With no conductor in view nothing happens. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void powerLineRideGoesToAConductor(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		helper.runAfterDelay(2, () -> {
			p.setYRot(0f);
			p.setXRot(0f);
			tap(p, AbilitySlot.SLOT_4, true);
			helper.assertTrue(kit().cooldownLeft(p, BuzzshockKit.LINE) == 0, "no conductor ahead: no ride, no cooldown");
			BlockPos rod = helper.absolutePos(new BlockPos(3, 2, 7));
			helper.getLevel().setBlock(rod, Blocks.LIGHTNING_ROD.defaultBlockState(), 3);
			helper.assertTrue(BuzzshockKit.conductor(helper.getLevel().getBlockState(rod)), "a lightning rod conducts");
			helper.assertTrue(BuzzshockKit.conductor(Blocks.REDSTONE_WIRE.defaultBlockState()) && BuzzshockKit.conductor(Blocks.COPPER_BLOCK.defaultBlockState())
					&& !BuzzshockKit.conductor(Blocks.STONE.defaultBlockState()), "redstone and copper conduct, stone does not");
			BlockPos seen = BuzzshockKit.conductorAhead(p);
			Vec3 was = p.position();
			tap(p, AbilitySlot.SLOT_4, true);
			helper.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(rod)) < 1.8, "he comes out at the rod: " + was + " -> " + p.position() + " vs " + rod + " (found " + seen + ", look " + p.getLookAngle() + ")");
			helper.assertTrue(kit().cooldownLeft(p, BuzzshockKit.LINE) > 0, "Shift+Z cools down");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- weakness / passives

	/** In water his electric moves fizzle (no cooldown spent) and he takes 1 a second. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void waterShortsHimOut(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		helper.getLevel().setBlock(p.blockPosition(), Blocks.WATER.defaultBlockState(), 3);
		helper.getLevel().setBlock(p.blockPosition().above(), Blocks.WATER.defaultBlockState(), 3);
		helper.onEachTick(p::baseTick); // a mock player runs no vanilla tick: the water check needs one
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(p.isInWater(), "in the water");
			Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2, 5.5);
			lookAt(p, z);
			tap(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(kit().cooldownLeft(p, BuzzshockKit.BOLT) == 0 && z.getHealth() == z.getMaxHealth(), "R fizzles in water");
			tap(p, AbilitySlot.SLOT_4, false);
			helper.assertTrue(kit().cooldownLeft(p, BuzzshockKit.OVERLOAD) == 0, "so does Overload");
			float h = p.getHealth();
			helper.runAfterDelay(45, () -> {
				helper.assertTrue(kit().flag(p, BuzzshockKit.F_WET), "flagged wet");
				helper.assertTrue(p.getHealth() < h - 0.5f, "the water hurts him: " + h + " -> " + p.getHealth());
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void lightningHealsHim(GameTestHelper helper) {
		ServerPlayer p = buzzshock(helper);
		helper.runAfterDelay(2, () -> {
			p.setHealth(6f);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().lightningBolt(), 5f);
			helper.assertTrue(p.getHealth() >= 6f + BuzzshockKit.LIGHTNING_HEAL - 0.01f, "a strike charges him up: " + p.getHealth());
			helper.succeed();
		});
	}
}

package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.erasure.ErasureConfig;
import com.projecthero.mod.quirk.erasure.ErasureMobs;
import com.projecthero.mod.quirk.erasure.ErasureMoves;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Erasure (Eraser Head): one test per move -- the server effect and its number -- plus the Dry Eyes meter and the
 * forced blink, and what Erasure does to mobs (a creeper never explodes, a skeleton never shoots). Mock players are not
 * ticked by the server, so tests that run over time drive the Quirk tick themselves ({@link #pump}).
 */
public class QuirkErasureGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
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

	/** Eraser Head facing +Z, the meter full, every move ready. */
	private static ServerPlayer eraser(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Quirks.grant(p, Quirk.ERASURE), "Erasure is granted");
		Quirks.tick(p); // the first tick fills Dry Eyes
		Quirks.edit(p, n -> {
			n.abilityReadyAt.clear();
			n.animId = QuirkState.ANIM_NONE;
		});
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	private static void look(ServerPlayer p, Entity e) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, e.position().add(0, e.getBbHeight() * 0.5, 0));
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health, boolean ai) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(!ai);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static long val(ServerPlayer p, String key) {
		return Quirks.state(p).val(key);
	}

	// ---------------------------------------------------------------- the kit

	@GameTest(template = EMPTY_STRUCTURE)
	public void erasureKitHasTwelveMoves(GameTestHelper helper) {
		int n = 0;
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4,
				AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 }) {
			String[] ids = QuirkKits.of(Quirk.ERASURE).idsOf(slot);
			helper.assertTrue(ids.length == 2, "two moves on " + slot);
			for (String id : ids) {
				helper.assertTrue(id.startsWith("quirk_erasure_") && QuirkKits.of(Quirk.ERASURE).maxCooldown(id) > 0, "id / cooldown " + id);
				n++;
			}
		}
		helper.assertTrue(n == 12, "twelve");
		helper.assertTrue(QuirkKits.of(Quirk.ERASURE).usableWhileOverloaded(AbilitySlot.SLOT_6, true)
				&& !QuirkKits.of(Quirk.ERASURE).usableWhileOverloaded(AbilitySlot.SLOT_2, false), "only Eye Drops works mid-blink");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void captureLashHitsForThreeAndYanksIn(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.0, 100, true);
		look(p, z);
		LivingEntity hit = ErasureMoves.lash(p);
		helper.assertTrue(hit == z, "the zombie is lashed");
		helper.assertTrue(Math.abs((100f - z.getHealth()) - ErasureConfig.LASH_DAMAGE) < EPS, "3 damage, took " + (100f - z.getHealth()));
		helper.assertTrue(z.getDeltaMovement().z < -0.3, "yanked toward him, v=" + z.getDeltaMovement());
		helper.assertTrue(!Quirks.ready(p, ErasureConfig.LASH), "the cooldown is set");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void captureDisarmTakesTheHeldItem(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, false);
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		look(p, z);
		ItemStack taken = ErasureMoves.disarm(p);
		helper.assertTrue(taken.is(Items.IRON_SWORD), "the sword was taken");
		helper.assertTrue(z.getMainHandItem().isEmpty(), "its hand is empty");
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(1.5),
				e -> e.getItem().is(Items.IRON_SWORD));
		helper.assertTrue(drops.size() == 1, "it lies at his feet");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void eraseCancelsTheLookedAtTargetsPower(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, false);
		Zombie off = mob(helper, EntityType.ZOMBIE, 0.8, 2.0, 6.5, 100, false);
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.eraseDown(p, false), "the stare starts");
		helper.assertTrue(PowerSuppression.isSuppressed(z), "the looked-at zombie is erased at once");
		helper.assertTrue(!PowerSuppression.isSuppressed(off), "the one off to the side is not (narrow cone)");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(PowerSuppression.isSuppressed(z), "still erased while he stares");
			helper.assertTrue(Quirks.meter(p) < ErasureConfig.METER_FULL - 3f, "Dry Eyes drain, " + Quirks.meter(p));
			helper.assertTrue(val(p, ErasureConfig.V_ERASE) == 1L, "the hair is up (synced)");
			ErasureMoves.eraseUp(p);
			helper.runAfterDelay(ErasureConfig.SUPPRESS_TICKS + 2, () -> {
				helper.assertTrue(!PowerSuppression.isSuppressed(z), "the power comes back a moment after the stare breaks");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void eraseGlareCatchesEveryoneInFrontAndDrainsThreeTimesFaster(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.0, 2.0, 3.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 2.0, 2.0, 7.0, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.0, 2.0, 7.0, 100, false);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 4.0, 2.0, 0.5, 100, false);
		p.setYRot(0f);
		p.setXRot(0f);
		p.setYHeadRot(0f);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.eraseDown(p, true), "the glare starts");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(PowerSuppression.isSuppressed(a) && PowerSuppression.isSuppressed(b), "both in front are erased");
			helper.assertTrue(!PowerSuppression.isSuppressed(behind), "not the one behind him");
			float lost = ErasureConfig.METER_FULL - Quirks.meter(p);
			float expect = 11 * ErasureConfig.ERASE_DRAIN * ErasureConfig.GLARE_DRAIN_MULT;
			helper.assertTrue(Math.abs(lost - expect) < 2.0f, "3x drain: lost " + lost + ", expected about " + expect);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void captureSwingHooksABlockAndLetsGo(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		BlockPos anchor = new BlockPos(4, 6, 5);
		helper.getLevel().setBlock(helper.absolutePos(anchor), Blocks.STONE.defaultBlockState(), 3);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(anchor)));
		helper.assertTrue(ErasureMoves.swingDown(p), "the scarf hooks the block");
		helper.assertTrue(val(p, ErasureConfig.V_LINE) == 1L && Quirks.vec(Quirks.state(p), ErasureConfig.P_HOOK) != null, "the line is synced");
		ErasureMoves.swingUp(p);
		helper.assertTrue(val(p, ErasureConfig.V_LINE) == 0L, "letting go releases it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void captureZipReelsHimInAndKicksForFour(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.0, 100, false);
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.zip(p) == z, "the zip catches the zombie");
		helper.assertTrue(val(p, ErasureConfig.V_LINE) == 2L && val(p, ErasureConfig.V_ZIP_TARGET) == z.getId() + 1L, "zip synced");
		// the owning client moves him; here he arrives by hand
		helper.runAfterDelay(3, () -> {
			p.teleportTo(z.getX(), z.getY(), z.getZ() - 1.4);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(val(p, ErasureConfig.V_LINE) == 0L, "the zip ends on arrival");
				helper.assertTrue(Math.abs((100f - z.getHealth()) - ErasureConfig.ZIP_KICK_DAMAGE) < EPS, "4 on arrival, took " + (100f - z.getHealth()));
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void captureBindRootsForFourSeconds(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, true);
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.bind(p, false) == z, "bound");
		Vec3 pin = z.position();
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(ErasureMoves.rooted(z), "still rooted");
			helper.assertTrue(z.position().subtract(pin).horizontalDistance() < 0.3, "it has not walked, moved " + z.position().subtract(pin).horizontalDistance());
			helper.assertTrue(ErasureMoves.boundTarget(p) == z, "his scarf holds it");
		});
		helper.runAfterDelay(ErasureConfig.BIND_TICKS + 5, () -> {
			helper.assertTrue(!ErasureMoves.rooted(z), "free after 4 s");
			helper.assertTrue(val(p, ErasureConfig.V_BIND) == 0L, "the bind has ended");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void captureHoistHangsItOffTheGround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 100, true);
		double y0 = z.getY();
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.bind(p, true) == z, "hoisted");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(ErasureMoves.hoisted(z), "hanging");
			helper.assertTrue(Math.abs(z.getY() - (y0 + ErasureConfig.HOIST_HEIGHT)) < 0.35, "up 2.5, at " + (z.getY() - y0));
		});
		helper.runAfterDelay(ErasureConfig.BIND_TICKS + 5, () -> {
			helper.assertTrue(!ErasureMoves.hoisted(z) && !z.isNoGravity(), "let down, gravity back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bindAndHoistNeverHoldABoss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 400, false);
		look(p, boss);
		helper.assertTrue(ErasureMoves.bind(p, false) == null && !ErasureMoves.rooted(boss), "a boss is never bound");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(ErasureMoves.bind(p, true) == null && !ErasureMoves.hoisted(boss), "nor hoisted");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void bindSlamLiftsAndSlamsForEight(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 100, true);
		double y0 = z.getY();
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.bind(p, false) == z, "bound first");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(ErasureMoves.slam(p) == z, "the slam takes the bound one");
		helper.assertTrue(!ErasureMoves.rooted(z), "the bind is let go for the throw");
		double[] top = { y0 };
		helper.onEachTick(() -> top[0] = Math.max(top[0], z.getY()));
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(top[0] > y0 + 1.5, "lifted, top " + (top[0] - y0));
			helper.assertTrue(100f - z.getHealth() >= ErasureConfig.SLAM_DAMAGE - EPS, "8 on the slam, took " + (100f - z.getHealth()));
			helper.assertTrue(val(p, ErasureConfig.V_SLAM) == 0L, "the slam is over");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void doubleBindSmashesTwoTogetherForSevenEach(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.0, 2.0, 1.0);
		Zombie a = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 6.0, 100, true);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.0, 2.0, 6.0, 100, true);
		look(p, a);
		pump(helper, p);
		List<LivingEntity> both = ErasureMoves.doubleBind(p);
		helper.assertTrue(both.size() == 2 && both.contains(a) && both.contains(b), "both caught");
		helper.runAfterDelay(ErasureConfig.DOUBLE_MAX_TICKS + 4, () -> {
			for (Zombie z : new Zombie[] { a, b }) {
				helper.assertTrue(Math.abs((100f - z.getHealth()) - ErasureConfig.DOUBLE_DAMAGE) < 1.5f, "7 each, took " + (100f - z.getHealth()));
			}
			helper.assertTrue(val(p, ErasureConfig.V_DB_A) == 0L, "over");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void gogglesGiveNightVisionAndPickOutSpecialMobs(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Creeper c = mob(helper, EntityType.CREEPER, 2.5, 2.0, 5.5, 20, false);
		Pig pig = mob(helper, EntityType.PIG, 6.5, 2.0, 5.5, 10, false);
		helper.assertTrue(ErasureMoves.goggles(p), "goggles down");
		helper.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION) && val(p, ErasureConfig.V_GOGGLES) == 1L, "night vision");
		List<Integer> marks = ErasureMoves.gogglesMarks(p);
		helper.assertTrue(marks.contains(c.getId()) && !marks.contains(pig.getId()), "the creeper glows, the pig does not");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(!ErasureMoves.goggles(p), "goggles up");
		helper.assertTrue(!p.hasEffect(MobEffects.NIGHT_VISION), "night vision off again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void eyeDropsRefillDryEyesEvenMidBlink(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		pump(helper, p);
		ErasureMoves.blink(p);
		Quirks.setMeter(p, 0f);
		helper.assertTrue(Quirks.overloaded(p), "blinking");
		ErasureMoves.eyeDrops(p);
		helper.assertTrue(Quirks.meter(p) >= ErasureConfig.METER_FULL - 0.01f, "Dry Eyes full");
		helper.assertTrue(!Quirks.overloaded(p), "the blink is over");
		helper.assertTrue(!Quirks.ready(p, ErasureConfig.DROPS), "long cooldown set");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the meter

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void dryEyesRunOutIntoAForcedBlinkThenRefill(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.0, 100, false);
		helper.assertTrue(Math.abs(Quirks.meter(p) - ErasureConfig.METER_FULL) < 0.01f, "fresh eyes: 100");
		look(p, z);
		pump(helper, p);
		Quirks.setMeter(p, 4f);
		helper.assertTrue(ErasureMoves.eraseDown(p, false), "stare");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(Quirks.overloaded(p), "forced blink at 0");
			helper.assertTrue(val(p, ErasureConfig.V_ERASE) == 0L, "the stare ended");
			helper.assertTrue(!ErasureMoves.eraseDown(p, false) || Quirks.overloaded(p), "still blinking");
		});
		helper.runAfterDelay(ErasureConfig.BLINK_TICKS + 20, () -> {
			helper.assertTrue(!Quirks.overloaded(p), "eyes open again");
			helper.assertTrue(Quirks.meter(p) > 40f, "refilled during and after the blink, " + Quirks.meter(p));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- mobs

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void anErasedCreeperNeverExplodes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Creeper c = mob(helper, EntityType.CREEPER, 4.5, 2.0, 3.5, 20, true);
		c.ignite();
		look(p, c);
		pump(helper, p);
		ErasureMoves.eraseDown(p, false);
		helper.onEachTick(() -> {
			Quirks.setMeter(p, ErasureConfig.METER_FULL);
			look(p, c);
		});
		helper.runAfterDelay(70, () -> {
			helper.assertTrue(PowerSuppression.isSuppressed(c), "erased");
			helper.assertTrue(c.isAlive() && !c.isRemoved(), "a lit creeper under the stare never went off");
			helper.assertTrue(p.getHealth() >= p.getMaxHealth() - 0.01f, "and nobody was hurt");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void anErasedSkeletonCannotShoot(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 0.5);
		Skeleton s = mob(helper, EntityType.SKELETON, 4.5, 2.0, 7.0, 40, true);
		s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		s.setTarget(p);
		look(p, s);
		pump(helper, p);
		ErasureMoves.eraseDown(p, false);
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
		helper.onEachTick(() -> {
			if (Quirks.state(p).val(ErasureConfig.V_ERASE) != 0L) {
				Quirks.setMeter(p, ErasureConfig.METER_FULL);
				look(p, s);
				s.setTarget(p);
			}
		});
		// the spawn hook: whatever it fires is never added
		Arrow fired = new Arrow(helper.getLevel(), s, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		helper.assertTrue(ErasureMobs.blocksSpawn(fired) && !helper.getLevel().addFreshEntity(fired), "its arrow never enters the world");
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, area, a -> a.getOwner() == s).isEmpty(),
					"no arrows while erased");
			helper.assertTrue(!s.isUsingItem(), "it does not even draw");
			ErasureMoves.eraseUp(p);
			// control: with its power back it shoots
			helper.succeedWhen(() -> helper.assertTrue(
					!helper.getLevel().getEntitiesOfClass(AbstractArrow.class, area, a -> a.getOwner() == s).isEmpty(), "it shoots again"));
		});
	}
}

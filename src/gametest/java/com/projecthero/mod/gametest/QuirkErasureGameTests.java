package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.erasure.ErasureConfig;
import com.projecthero.mod.quirk.erasure.ErasureExposed;
import com.projecthero.mod.quirk.erasure.ErasureItems;
import com.projecthero.mod.quirk.erasure.ErasureKit;
import com.projecthero.mod.quirk.erasure.ErasureMeter;
import com.projecthero.mod.quirk.erasure.ErasureMobs;
import com.projecthero.mod.quirk.erasure.ErasureMoves;
import com.projecthero.mod.quirk.erasure.ErasureSleepingBag;
import com.projecthero.mod.quirk.erasure.ErasureUltimate;

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
		Quirks.tick(p); // the first tick sets Dry Eyes fresh (0)
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
	public void erasureKitFollowsTheSlotRule(GameTestHelper helper) {
		var kit = QuirkKits.of(Quirk.ERASURE);
		int n = 0;
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4,
				AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 }) {
			String[] ids = kit.idsOf(slot);
			helper.assertTrue(ids.length == 2, "tap + Shift on " + slot);
			for (String id : ids) {
				helper.assertTrue(id.startsWith("quirk_erasure_") && kit.maxCooldown(id) > 0, "id / cooldown " + id);
				n++;
			}
		}
		helper.assertTrue(n == 12, "twelve");
		// v0.17.1 revamp layout (docs/QUIRKS_REVAMP_PROPOSAL.md 3.5)
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_3)[0].equals(ErasureConfig.ZIP) && kit.idsOf(AbilitySlot.SLOT_3)[1].equals(ErasureConfig.FLIP),
				"X: Scarf Pull / Evade Flip (movement)");
		helper.assertTrue(ErasureConfig.SWING.equals(kit.holdIdOf(AbilitySlot.SLOT_3)), "hold X: Scarf Swing");
		helper.assertTrue(ErasureConfig.REEL.equals(kit.holdIdOf(AbilitySlot.SLOT_1)), "hold R: Reel Lock");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_5)[0].equals(ErasureConfig.ERASE) && kit.idsOf(AbilitySlot.SLOT_5)[1].equals(ErasureConfig.GOGGLES),
				"V: the stare / Goggles (utility)");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_6)[0].equals(ErasureConfig.WHIP) && kit.idsOf(AbilitySlot.SLOT_6)[1].equals(ErasureConfig.MULTI),
				"C: Scarf Whip / Multi-Bind (hold C = the ultimate)");
		for (AbilitySlot slot : AbilitySlot.values()) {
			for (String id : kit.idsOf(slot)) {
				helper.assertFalse(id.equals(ErasureConfig.DROPS), "Eye Drops left the keys");
			}
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void captureBindTiesItUpForFourSeconds(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, true);
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.bind(p) == z, "bound");
		helper.assertTrue(QuirkKits.of(Quirk.ERASURE).active(p, AbilitySlot.SLOT_1), "the R box lights");
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void capturePullHitsForSixAndYanksIn(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.0, 100, true);
		look(p, z);
		helper.assertTrue(ErasureMoves.bind(p) == z, "bound first");
		LivingEntity hit = ErasureMoves.pull(p);
		helper.assertTrue(hit == z, "the bound zombie is pulled");
		helper.assertTrue(!ErasureMoves.rooted(z), "the bind lets go for the pull");
		helper.assertTrue(Math.abs((100f - z.getHealth()) - ErasureConfig.LASH_DAMAGE) < EPS, "6 damage, took " + (100f - z.getHealth()));
		helper.assertTrue(z.getDeltaMovement().z < -0.3, "yanked toward him, v=" + z.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bindAndSpinNeverHoldABoss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 400, false);
		look(p, boss);
		helper.assertTrue(ErasureMoves.bind(p) == null && !ErasureMoves.rooted(boss), "a boss is never bound");
		helper.assertTrue(ErasureMoves.spin(p) == null && !ErasureMoves.spinning(p), "nor spun");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void closeCombatLandsFourStrikes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 2.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.0, 100, false);
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.combo(p), "the combo starts");
		helper.onEachTick(() -> look(p, z));
		helper.runAfterDelay(ErasureConfig.COMBO_TICKS + 2, () -> {
			float total = 0f;
			for (float d : ErasureConfig.COMBO_DAMAGE) {
				total += d;
			}
			float took = 100f - z.getHealth();
			helper.assertTrue(Math.abs(took - total) < 1.5f, "four strikes, " + total + " in all; took " + took);
			helper.assertTrue(val(p, ErasureConfig.V_COMBO_AT) == 0L, "over");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void counterCatchesAHitAndAnswers(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 2.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 3.5, 100, false);
		var kit = QuirkKits.of(Quirk.ERASURE);
		var hit = helper.getLevel().damageSources().mobAttack(z);
		helper.assertTrue(kit.modifyIncomingDamage(p, hit, 5f) == 5f, "no window: the hit lands");
		helper.assertTrue(ErasureMoves.counter(p), "parry stance");
		helper.assertTrue(kit.modifyIncomingDamage(p, hit, 5f) == 0f, "the hit is caught");
		helper.assertTrue(Math.abs((100f - z.getHealth()) - ErasureConfig.COUNTER_DAMAGE) < EPS, "answered for 10, took " + (100f - z.getHealth()));
		helper.assertTrue(ErasureMoves.rooted(z), "and tied up");
		helper.assertTrue(kit.modifyIncomingDamage(p, hit, 5f) == 5f, "one catch per window");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X (movement)

	@GameTest(template = EMPTY_STRUCTURE)
	public void scarfSwingLatchesABlockAndLetsGo(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		BlockPos anchor = new BlockPos(4, 6, 5);
		helper.getLevel().setBlock(helper.absolutePos(anchor), Blocks.STONE.defaultBlockState(), 3);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(anchor)));
		helper.assertTrue(ErasureMoves.swingDown(p), "the scarf latches the block");
		helper.assertTrue(val(p, ErasureConfig.V_LINE) == 1L && Quirks.vec(Quirks.state(p), ErasureConfig.P_HOOK) != null, "the line is synced");
		helper.assertTrue(p.getAbilities().mayfly, "no kick for floating while he swings");
		ErasureMoves.swingUp(p);
		helper.assertTrue(val(p, ErasureConfig.V_LINE) == 0L, "letting go releases it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void scarfPullReelsHimToTheLatchedPoint(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		BlockPos anchor = new BlockPos(4, 6, 6);
		helper.getLevel().setBlock(helper.absolutePos(anchor), Blocks.STONE.defaultBlockState(), 3);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(anchor)));
		pump(helper, p);
		helper.assertTrue(ErasureMoves.swingDown(p), "swinging");
		helper.assertTrue(ErasureMoves.zip(p), "Scarf Pull mid-swing: up the line");
		helper.assertTrue(val(p, ErasureConfig.V_LINE) == 3L, "reeling to the block");
		// the owning client moves him; here he arrives by hand
		helper.runAfterDelay(3, () -> {
			Vec3 hook = Quirks.vec(Quirks.state(p), ErasureConfig.P_HOOK);
			p.teleportTo(hook.x, hook.y - p.getEyeHeight() - 0.2, hook.z - 0.6);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(val(p, ErasureConfig.V_LINE) == 0L, "the pull ends on arrival");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void scarfPullToACreatureKicksForSix(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.0, 100, false);
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.zip(p), "the scarf catches the zombie");
		helper.assertTrue(val(p, ErasureConfig.V_LINE) == 2L && val(p, ErasureConfig.V_ZIP_TARGET) == z.getId() + 1L, "pull synced");
		helper.runAfterDelay(3, () -> {
			p.teleportTo(z.getX(), z.getY(), z.getZ() - 1.4);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(val(p, ErasureConfig.V_LINE) == 0L, "the pull ends on arrival");
				helper.assertTrue(Math.abs((100f - z.getHealth()) - ErasureConfig.ZIP_KICK_DAMAGE) < EPS, "6 on arrival, took " + (100f - z.getHealth()));
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void bindAndSpinSmashesItIntoOthersThenFlingsIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.0, 2.0, 4.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.0, 2.0, 6.5, 100, false);
		// a bystander on the orbit, a quarter turn round
		Zombie other = mob(helper, EntityType.ZOMBIE, 1.0, 2.0, 4.0, 100, false);
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.bind(p) == z, "bound first");
		helper.assertTrue(ErasureMoves.spin(p) == z, "spun");
		helper.assertTrue(ErasureMoves.spinning(p) && !ErasureMoves.rooted(z), "the spin runs, the bind is let go");
		helper.runAfterDelay(ErasureConfig.SPIN_TICKS + 3, () -> {
			helper.assertFalse(ErasureMoves.spinning(p), "over");
			helper.assertTrue(100f - other.getHealth() >= ErasureConfig.SPIN_HIT_DAMAGE - EPS, "the bystander was smashed, took " + (100f - other.getHealth()));
			helper.assertTrue(100f - z.getHealth() >= ErasureConfig.SPIN_THROW_DAMAGE - EPS, "the spun one took the throw, " + (100f - z.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void disarmThrowRipsTheItemBackToHim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, false);
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		look(p, z);
		ItemStack taken = ErasureMoves.disarm(p);
		helper.assertTrue(taken.is(Items.IRON_SWORD), "the sword was ripped away");
		helper.assertTrue(z.getMainHandItem().isEmpty(), "its hand is empty");
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, z.getBoundingBox().inflate(2),
				e -> e.getItem().is(Items.IRON_SWORD));
		helper.assertTrue(drops.size() == 1 && drops.get(0).getDeltaMovement().z < 0, "thrown back toward him");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void scarfWhipHitsForSeven(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.0, 100, false);
		look(p, z);
		helper.assertTrue(ErasureMoves.whip(p) == z, "lashed");
		helper.assertTrue(Math.abs((100f - z.getHealth()) - ErasureConfig.WHIP_DAMAGE) < EPS, "7 damage, took " + (100f - z.getHealth()));
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "staggered");
		helper.succeed();
	}

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

	// ---------------------------------------------------------------- V: the stare, Dry Eyes, Eye Drops

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void theStareDisablesTheLookedAtTargetsPowers(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, false);
		Zombie off = mob(helper, EntityType.ZOMBIE, 0.8, 2.0, 6.5, 100, false);
		look(p, z);
		pump(helper, p);
		helper.assertTrue(Quirks.meter(p) < 0.01f, "fresh eyes: Dry Eyes 0");
		helper.assertTrue(ErasureMoves.eraseDown(p), "the stare starts");
		helper.assertTrue(PowerSuppression.isSuppressed(z), "the looked-at zombie is erased at once");
		helper.assertTrue(!PowerSuppression.isSuppressed(off), "the one off to the side is not (narrow cone)");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(PowerSuppression.isSuppressed(z), "still erased while he stares");
			helper.assertTrue(Quirks.meter(p) > 0.5f, "his eyes dry (gently, v0.17.1 pacing), " + Quirks.meter(p));
			helper.assertTrue(val(p, ErasureConfig.V_ERASE) == 1L && val(p, ErasureConfig.V_STARE_TARGET) == z.getId() + 1L, "the stare is synced");
			ErasureMoves.eraseUp(p);
			helper.runAfterDelay(ErasureConfig.SUPPRESS_TICKS + 2, () -> {
				helper.assertTrue(!PowerSuppression.isSuppressed(z), "the power comes back a moment after the blink");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void theStareHoldsAPoweredPlayersKeys(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		ServerPlayer hero = mock(helper, 4.5, 2.0, 6.5);
		helper.assertTrue(Quirks.grant(hero, Quirk.OVERHAUL), "the other player holds a Quirk");
		look(p, hero);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.eraseDown(p), "the stare starts");
		helper.assertTrue(PowerSuppression.isSuppressed(hero), "the player is erased");
		helper.assertTrue(PowerSuppression.blocksAbility(hero, AbilitySlot.SLOT_1, true), "his power keys are refused");
		helper.assertFalse(PowerSuppression.blocksAbility(hero, AbilitySlot.SLOT_1, false), "releases still pass");
		Quirks.revoke(hero);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 30)
	public void gogglesLockTheStareOn(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, false);
		look(p, z);
		helper.assertTrue(ErasureMoves.goggles(p), "goggles down");
		helper.assertTrue(ErasureMoves.eraseDown(p), "the stare locks on");
		// it dodges 25 degrees off his eyeline
		z.teleportTo(z.getX() + 2.5, z.getY(), z.getZ());
		helper.assertTrue(ErasureMoves.stareTargets(p).contains(z), "goggles: it can't dodge out of the stare");
		Quirks.setVal(p, ErasureConfig.V_GOGGLES, 0L);
		helper.assertFalse(ErasureMoves.stareTargets(p).contains(z), "bare eyes: out of the narrow cone, lost");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void dryEyesForceTheEyesShutThenRecover(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.0, 100, false);
		look(p, z);
		pump(helper, p);
		Quirks.setMeter(p, 99.5f); // v0.17.1: Dry Eyes build paced (ErasureMeter.BUILD)
		helper.assertTrue(ErasureMoves.eraseDown(p), "stare");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(ErasureMoves.eyesShut(p), "forced shut at 100");
			helper.assertTrue(val(p, ErasureConfig.V_ERASE) == 0L, "the stare ended");
			Quirks.edit(p, n -> n.abilityReadyAt.clear());
			helper.assertFalse(ErasureMoves.eraseDown(p), "no stare with his eyes shut");
			helper.assertTrue(QuirkKits.of(Quirk.ERASURE).keyLocked(p, AbilitySlot.SLOT_5, false), "the V key dims");
			helper.assertTrue(ErasureMoves.whip(p) == z, "the scarf still works blind");
		});
		helper.runAfterDelay(ErasureConfig.SHUT_MIN_TICKS + 60, () -> {
			helper.assertFalse(ErasureMoves.eyesShut(p), "eyes open again");
			helper.assertTrue(Quirks.meter(p) <= ErasureConfig.SHUT_OPEN_AT + 0.01f, "recovered to " + Quirks.meter(p));
			Quirks.edit(p, n -> n.abilityReadyAt.clear());
			helper.assertTrue(ErasureMoves.eraseDown(p), "and the stare works");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void eyeDropsItemByHandOpensShutEyes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Quirks.setMeter(p, 100f);
		ErasureMoves.shutEyes(p);
		ItemStack drops = new ItemStack(ErasureItems.EYE_DROPS, 2);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, drops);
		ErasureItems.EYE_DROPS.use(helper.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(Quirks.meter(p) < 0.01f && !ErasureMoves.eyesShut(p), "right-clicked: Dry Eyes 0, eyes open");
		helper.assertTrue(p.getMainHandItem().getCount() == 1, "one used up");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void eyeDropsNeedTheItemAndOpenShutEyes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		pump(helper, p);
		Quirks.setMeter(p, 100f);
		ErasureMoves.shutEyes(p);
		helper.assertTrue(ErasureMoves.eyesShut(p), "shut");
		helper.assertFalse(ErasureMoves.eyeDrops(p), "no Eye Drops in the inventory: refused");
		p.getInventory().add(new ItemStack(ErasureItems.EYE_DROPS, 2));
		helper.assertTrue(ErasureMoves.eyeDrops(p), "drops in");
		helper.assertTrue(Quirks.meter(p) < 0.01f, "Dry Eyes back to 0");
		helper.assertFalse(ErasureMoves.eyesShut(p), "his eyes open");
		helper.assertTrue(p.getInventory().countItem(ErasureItems.EYE_DROPS) == 1, "one used up");
		helper.assertTrue(!Quirks.ready(p, ErasureConfig.DROPS), "cooldown set");
		helper.succeed();
	}

	// ================================================================ v0.17.1 revamp

	@GameTest(template = EMPTY_STRUCTURE)
	public void exposedTakesMoreDamageCannotSprintOrJumpHigher(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie erased = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 5.0, 100, false);
		Zombie plain = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 5.0, 100, false);
		PowerSuppression.suppress(erased, 100);
		helper.assertTrue(ErasureExposed.exposed(erased) && !ErasureExposed.exposed(plain), "erased = Exposed");
		erased.hurt(helper.getLevel().damageSources().playerAttack(p), 10f);
		plain.hurt(helper.getLevel().damageSources().playerAttack(p), 10f);
		helper.assertTrue(Math.abs((100f - plain.getHealth()) - 10f) < EPS, "the plain one takes 10, took " + (100f - plain.getHealth()));
		helper.assertTrue(Math.abs((100f - erased.getHealth()) - 10f * ErasureConfig.EXPOSED_DAMAGE_MULT) < EPS,
				"the Exposed one takes +20%, took " + (100f - erased.getHealth()));
		erased.setSprinting(true);
		plain.setSprinting(true);
		helper.assertTrue(!erased.isSprinting() && plain.isSprinting(), "no sprint while Exposed");
		erased.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.JUMP, 200, 2));
		plain.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.JUMP, 200, 2));
		helper.assertTrue(erased.getJumpBoostPower() == 0f && plain.getJumpBoostPower() > 0f, "no jump boost while Exposed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void theStareLeavesItsTargetExposed(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, false);
		look(p, z);
		helper.assertTrue(ErasureMoves.eraseDown(p), "the stare");
		helper.assertTrue(ErasureExposed.exposed(z), "a plain zombie has no power to lose, but it is Exposed");
		helper.assertTrue(ErasureMoves.whip(p) == z, "whipped");
		helper.assertTrue(Math.abs((100f - z.getHealth()) - ErasureConfig.WHIP_DAMAGE * ErasureConfig.EXPOSED_DAMAGE_MULT) < EPS,
				"7 x 1.2, took " + (100f - z.getHealth()));
		helper.assertTrue(ErasureMoves.rooted(z), "and a scarf hit on an erased target ties it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void reelLockStringsABoundTargetUpWhileHeld(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.0, 100, false);
		double y0 = z.getY();
		look(p, z);
		pump(helper, p);
		helper.assertFalse(ErasureMoves.reelStart(p), "nothing bound: no reel");
		helper.assertTrue(ErasureMoves.bind(p) == z, "bound");
		helper.assertTrue(ErasureMoves.reelStart(p) && ErasureMoves.reeling(p) && ErasureMoves.hoisted(z), "strung up");
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(z.getY() > y0 + 0.6, "hoisted off the ground, +" + (z.getY() - y0));
			helper.assertTrue(100f - z.getHealth() >= 2f * ErasureConfig.REEL_DAMAGE, "2 a second, took " + (100f - z.getHealth()));
			helper.assertTrue(Quirks.meter(p) > 0.5f, "it dries his eyes, " + Quirks.meter(p));
			helper.assertTrue(ErasureMoves.rooted(z), "the bind is kept alive while he reels");
			ErasureMoves.reelStop(p);
			helper.assertTrue(!ErasureMoves.reeling(p) && !ErasureMoves.hoisted(z), "let go: it comes down");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void holdingRBindsThenReels(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.0, 100, false);
		look(p, z);
		pump(helper, p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(ErasureMoves.boundTarget(p) == z, "R down: Capture Bind at once");
		helper.runAfterDelay(ErasureKit.holdTicks(AbilitySlot.SLOT_1) + 3, () -> {
			helper.assertTrue(ErasureMoves.reeling(p), "still held: Reel Lock");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
			helper.assertFalse(ErasureMoves.reeling(p), "R up: the reel ends");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void tapXPullsHoldXSwings(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		BlockPos anchor = new BlockPos(4, 7, 5);
		helper.getLevel().setBlock(helper.absolutePos(anchor), Blocks.STONE.defaultBlockState(), 3);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(anchor)));
		pump(helper, p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		helper.runAfterDelay(1, () -> {
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, false);
			helper.assertTrue(val(p, ErasureConfig.V_LINE) == 3L, "a tap: Scarf Pull to the block");
			ErasureMoves.detach(p, false);
			Quirks.edit(p, n -> n.abilityReadyAt.clear());
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		});
		helper.runAfterDelay(4 + ErasureConfig.SWING_HOLD_TICKS + 3, () -> {
			helper.assertTrue(val(p, ErasureConfig.V_LINE) == 1L, "held: the Scarf Swing");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, false);
			helper.assertTrue(val(p, ErasureConfig.V_LINE) == 0L, "let go: released");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void evadeFlipDodgesWhileItsIFramesRun(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 4.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.0, 100, false);
		var kit = QuirkKits.of(Quirk.ERASURE);
		var hit = helper.getLevel().damageSources().mobAttack(z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.flip(p), "flip");
		helper.assertTrue(p.getDeltaMovement().z < -0.5 && p.getDeltaMovement().y > 0.3, "back and up, away from where he looks: " + p.getDeltaMovement());
		helper.assertTrue(kit.modifyIncomingDamage(p, hit, 6f) == 0f, "dodged");
		helper.runAfterDelay(ErasureConfig.FLIP_IFRAMES + 2, () -> {
			helper.assertTrue(kit.modifyIncomingDamage(p, hit, 6f) == 6f, "the i-frames are short");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void multiBindTiesUpToThreeInTheCone(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 0.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 3.0, 2.0, 5.0, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 5.5, 100, false);
		Zombie c = mob(helper, EntityType.ZOMBIE, 6.0, 2.0, 6.0, 100, false);
		Zombie d = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.5, 100, false);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 2.0, 2.0, 7.0, 400, false);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, b.position().add(0, 1.0, 0));
		pump(helper, p);
		List<LivingEntity> caught = ErasureMoves.multiBind(p);
		helper.assertTrue(caught.size() == ErasureConfig.MULTI_TARGETS, "three tied up, got " + caught.size());
		int rooted = (ErasureMoves.rooted(a) ? 1 : 0) + (ErasureMoves.rooted(b) ? 1 : 0) + (ErasureMoves.rooted(c) ? 1 : 0) + (ErasureMoves.rooted(d) ? 1 : 0);
		helper.assertTrue(rooted == 3, "the nearest three, rooted " + rooted);
		helper.assertFalse(ErasureMoves.rooted(boss), "a boss breaks free");
		helper.assertTrue(QuirkKits.of(Quirk.ERASURE).active(p, AbilitySlot.SLOT_6), "the C box lights");
		helper.runAfterDelay(ErasureConfig.MULTI_TICKS + 4, () -> {
			helper.assertTrue(!ErasureMoves.rooted(a) && !ErasureMoves.rooted(b) && !ErasureMoves.rooted(c), "free after 2.5 s");
			helper.assertTrue(ErasureMoves.multiBound(p).isEmpty() && val(p, ErasureConfig.V_MULTI_UNTIL) == 0L, "over");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dryEyesBandsChangeTheStare(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.0, 2.0, 6.5, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 5.2, 2.0, 6.5, 100, false);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, a.position().add(0.6, 1.0, 0));
		Quirks.setMeter(p, 10f);
		helper.assertTrue(ErasureMoves.stareRange(p) == ErasureConfig.ERASE_RANGE, "Fresh: 24 blocks");
		helper.assertTrue(ErasureMoves.stareTargets(p).size() == 1, "Fresh: one target");
		Quirks.setMeter(p, 60f);
		helper.assertTrue(ErasureMoves.stareRange(p) == ErasureConfig.ERASE_RANGE_STRAINED, "Strained: 32 blocks");
		Quirks.setMeter(p, 92f);
		helper.assertTrue(ErasureMoves.stareTargets(p).size() == ErasureConfig.BURNING_TARGETS, "Burning: two at once");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void burningEyesHurtWhileHeStares(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.0, 100, false);
		look(p, z);
		pump(helper, p);
		Quirks.setMeter(p, 95f);
		float hp = p.getHealth();
		helper.assertTrue(ErasureMoves.eraseDown(p), "stare");
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(p.getHealth() < hp, "0.5 a second while Burning: " + hp + " -> " + p.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void bindThenSpinIsTheStrungUpCombo(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.0, 2.0, 4.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.0, 2.0, 6.5, 120, false);
		look(p, z);
		pump(helper, p);
		helper.assertTrue(ErasureMoves.bind(p) == z && ErasureMoves.spin(p) == z, "bind, then spin the same one");
		helper.assertTrue(QuirkCombos.last(p) == ErasureMeter.BIND_SPIN, "Strung Up landed");
		helper.runAfterDelay(ErasureConfig.SPIN_TICKS + 3, () -> {
			helper.assertTrue(120f - z.getHealth() >= ErasureConfig.SPIN_THROW_DAMAGE * 1.5f - EPS, "a 1.5x fling, took " + (120f - z.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void whipThenPullIsCrackAndYank(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.0, 100, false);
		look(p, z);
		helper.assertTrue(ErasureMoves.whip(p) == z, "whip");
		float after = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(ErasureMoves.pull(p) == z, "pull");
		helper.assertTrue(QuirkCombos.last(p) == ErasureMeter.CRACK_YANK, "Crack and Yank landed");
		helper.assertTrue(Math.abs((after - z.getHealth()) - ErasureConfig.LASH_DAMAGE * 1.4f) < EPS, "a 1.4x yank, took " + (after - z.getHealth()));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void capturePullDragsAnAllyToSafety(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		net.minecraft.world.entity.animal.Wolf w = mob(helper, EntityType.WOLF, 4.5, 4.0, 6.0, 20, false);
		w.tame(p);
		w.fallDistance = 6f;
		w.setDeltaMovement(0, -0.9, 0);
		look(p, w);
		float gauge = PlusUltra.gauge(p);
		helper.assertTrue(ErasureMoves.pull(p) == w, "the pull takes his own wolf");
		helper.assertTrue(w.getHealth() >= 20f - 0.01f, "no harm to an ally");
		helper.assertTrue(w.getDeltaMovement().z < -0.3 && w.fallDistance == 0f && w.hasEffect(MobEffects.SLOW_FALLING), "dragged to him, landing soft");
		helper.assertTrue(PlusUltra.gauge(p) >= gauge + PlusUltra.ALLY_SAVE - 0.01f, "a rescue fills Plus Ultra");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void undergroundTakedownErasesTheConeChainsAndStringsThemUp(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 0.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.0, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 7.0, 100, false);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 9.0, 400, false);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, -3.0, 100, false);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, b.position().add(-1.0, 1.0, 0));
		int erased = ErasureUltimate.fire(p);
		helper.assertTrue(erased >= 3, "the cone: " + erased);
		helper.assertTrue(PowerSuppression.isSuppressed(a) && PowerSuppression.isSuppressed(b) && PowerSuppression.isSuppressed(boss), "all erased");
		helper.assertFalse(PowerSuppression.isSuppressed(behind), "not the one behind him");
		helper.assertTrue(PowerSuppression.remaining(boss) <= ErasureConfig.TD_BOSS_ERASE_TICKS, "a boss for 4 s");
		helper.assertTrue(val(p, ErasureConfig.V_GOGGLES) == 1L, "goggles snapped down");
		helper.assertTrue(ErasureUltimate.chain(p).contains(a.getId()) && ErasureUltimate.chain(p).contains(b.getId()), "both in the chain");
		ErasureUltimate.hopForTest(p);
		ErasureUltimate.hopForTest(p);
		helper.assertTrue(100f - a.getHealth() >= ErasureConfig.TD_KICK_DAMAGE * ErasureConfig.EXPOSED_DAMAGE_MULT - EPS, "kicked (Exposed), took "
				+ (100f - a.getHealth()));
		helper.assertTrue(ErasureMoves.rooted(a) && ErasureMoves.rooted(b), "and bound");
		ErasureUltimate.completeForTest(p);
		helper.assertFalse(ErasureMoves.rooted(boss), "a boss is never bound");
		helper.assertTrue(!ErasureUltimate.running(p) && ErasureMoves.eyesShut(p) && Quirks.meter(p) >= 99.9f, "after it: his eyes are spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void plusUltraHoldFiresTheTakedown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.0, 100, false);
		look(p, z);
		pump(helper, p);
		PlusUltra.set(p, 100f);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.runAfterDelay(PlusUltra.HOLD_TICKS + 3, () -> {
			helper.assertTrue(ErasureUltimate.running(p) || ErasureMoves.eyesShut(p), "hold C at 100: Underground Takedown");
			helper.assertTrue(PowerSuppression.isSuppressed(z), "the zombie was erased");
			helper.assertTrue(PlusUltra.gauge(p) < 1f, "the gauge is spent");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void undergroundHalvesHowFarMobsSeeHimInTheDark(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		p.setShiftKeyDown(true);
		p.setPose(net.minecraft.world.entity.Pose.CROUCHING);
		double v = ErasureExposed.visibility(p, 1.0);
		helper.assertTrue(v == (ErasureExposed.dark(p) ? ErasureConfig.UNDERGROUND_VISIBILITY : 1.0), "sneaking: half range only in the dark, " + v);
		p.setShiftKeyDown(false);
		p.setPose(net.minecraft.world.entity.Pose.STANDING);
		helper.assertTrue(ErasureExposed.visibility(p, 1.0) == 1.0, "standing: no change");
		ServerPlayer other = mock(helper, 2.5, 2.0, 1.0);
		other.setShiftKeyDown(true);
		other.setPose(net.minecraft.world.entity.Pose.CROUCHING);
		helper.assertTrue(ErasureExposed.visibility(other, 1.0) == 1.0, "not an Eraser: no change");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void sleepingBagSleepsAnywhereKeepsTheSpawnAndRefreshesHisEyes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = eraser(helper, 4.5, 2.0, 1.0);
		p.setOnGround(true);
		long time = helper.getLevel().getDayTime();
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ErasureSleepingBag.SLEEPING_BAG));
		p.setGameMode(GameType.CREATIVE); // (no monster check: another test's zombies may stand close)
		helper.getLevel().setDayTime(6000L);
		helper.getLevel().updateSkyBrightness();
		helper.assertTrue("block.minecraft.bed.no_sleep".equals(ErasureSleepingBag.trySleep(p)), "not by day");
		helper.getLevel().setDayTime(18000L);
		helper.getLevel().updateSkyBrightness();
		var spawn = p.getRespawnPosition();
		Quirks.setMeter(p, 80f);
		helper.assertTrue(ErasureSleepingBag.trySleep(p) == null, "at night: in the bag");
		helper.assertTrue(p.isSleeping() && ErasureSleepingBag.inBag(p), "asleep in the Sleeping Bag");
		helper.assertTrue(java.util.Objects.equals(spawn, p.getRespawnPosition()), "the spawn point never moved");
		var air = helper.getLevel().getBlockState(p.blockPosition());
		helper.assertTrue(net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_BED.invoker().allowBed(p, p.blockPosition(), air, false)
				== net.minecraft.world.InteractionResult.SUCCESS, "the bag counts as a bed while held");
		p.stopSleeping();
		helper.assertTrue(!p.isSleeping() && Quirks.meter(p) < 0.01f, "woke with Dry Eyes at 0");
		helper.assertTrue(helper.getLevel().getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("sleeping_bag")).isPresent(), "it has a recipe");
		helper.getLevel().setDayTime(time);
		helper.getLevel().updateSkyBrightness();
		helper.succeed();
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
		ErasureMoves.eraseDown(p);
		helper.onEachTick(() -> {
			Quirks.setMeter(p, 0f);
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
		ErasureMoves.eraseDown(p);
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
		helper.onEachTick(() -> {
			if (Quirks.state(p).val(ErasureConfig.V_ERASE) != 0L) {
				Quirks.setMeter(p, 0f);
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

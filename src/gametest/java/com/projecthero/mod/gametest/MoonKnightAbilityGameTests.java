package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.MoonKnightDamage;
import com.projecthero.mod.moonknight.ability.MoonKnightAbilities;
import com.projecthero.mod.moonknight.ability.MoonKnightAbilityManager;
import com.projecthero.mod.moonknight.ability.MoonKnightAlters;
import com.projecthero.mod.moonknight.ability.MoonKnightCape;
import com.projecthero.mod.moonknight.ability.MoonKnightDash;
import com.projecthero.mod.moonknight.ability.MoonKnightDarts;
import com.projecthero.mod.moonknight.ability.MoonKnightGrapple;
import com.projecthero.mod.moonknight.ability.MoonKnightKhonshu;
import com.projecthero.mod.moonknight.ability.MoonKnightTruncheon;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.entity.CrescentDartEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Moon Knight Phases 3-4 (keys as of v0.13.21): R Crescent Darts, G Grapple Kick / Shadow Step, X Dash / Grappling
 * Line, C Truncheon / Staff, and the Cape (jump + Sneak glide, right-click block). Mock players are not
 * reliably ticked, so each move's own tick is driven directly; mobs are NoAI husks (no daylight burning, no wandering),
 * and every test that hits mobs has its own batch so no neighbour's AoE can reach them. Numbers are asserted relative
 * to the live lunar power, since the test world's time of day isn't fixed.
 */
public class MoonKnightAbilityGameTests implements FabricGameTest {
	private static ServerPlayer knight(GameTestHelper helper, Vec3 rel, float yaw) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, yaw, 0.0f);
		MoonKnight.grant(p, false);
		MoonKnight.setTransformedForTesting(p, true);
		return p;
	}

	private static Husk husk(GameTestHelper helper, Vec3 absolute) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		h.moveTo(absolute.x, absolute.y, absolute.z, 0.0f, 0.0f);
		h.setNoAi(true);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	/** A fresh (never-ticked) mock player keeps vanilla's 3 s spawn invulnerability; clear it so it can be hurt. */
	private static void clearSpawnInvulnerability(ServerPlayer p) {
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void lookAt(ServerPlayer p, Husk h) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, h.position().add(0, h.getBbHeight() * 0.5, 0));
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "mk_dart_hit")
	public void dartDamagesAHusk(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 1.5), 0.0f);
		Husk h = husk(helper, p.position().add(0, 0, 5));
		lookAt(p, h);
		MoonKnightDarts.INSTANCE.tap(p);
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "darts") > 0, "the tap starts the dart cooldown");
		helper.succeedWhen(() -> helper.assertTrue(h.getHealth() < h.getMaxHealth(), "the crescent dart hits the husk"));
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_dart_fan")
	public void fanThrowsThreeDarts(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		MoonKnightDarts.INSTANCE.holdStart(p);
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_CHARGING), "holding R charges (FLAG_CHARGING)");
		helper.assertTrue(MoonKnightAnim.action(p).chargeKey == 1, "chargeKey is R's slot");
		MoonKnightDarts.INSTANCE.holdRelease(p, MoonKnightConfig.HOLD_THRESHOLD_TICKS + MoonKnightConfig.DART_FAN_MAX_CHARGE);
		int expected = MoonKnightAbilities.fullMoon(p) ? MoonKnightConfig.DART_FAN_COUNT_FULL_MOON : MoonKnightConfig.DART_FAN_COUNT;
		List<CrescentDartEntity> darts = helper.getLevel().getEntitiesOfClass(CrescentDartEntity.class,
				new AABB(p.blockPosition()).inflate(4.0));
		helper.assertTrue(darts.size() == expected, "the fan throws " + expected + " darts (" + darts.size() + ")");
		helper.assertFalse(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_CHARGING), "releasing ends the charge");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "darts_hold") > 0, "and starts the fan cooldown");
		darts.forEach(d -> d.discard());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_moon_mark")
	public void moonMarkAddsThirtyPercent(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		Husk marked = husk(helper, p.position().add(2, 0, 2));
		Husk plain = husk(helper, p.position().add(-2, 0, 2));
		MoonKnightDarts.applyMark(p, marked, 200);
		helper.assertTrue(marked.hasEffect(MobEffects.GLOWING), "a marked target glows");
		helper.assertTrue(Math.abs(MoonKnightDarts.outgoingFactor(p, marked) - 1.3f) < 1.0e-4f, "+30% against the mark");
		helper.assertTrue(MoonKnightDarts.outgoingFactor(p, plain) == 1.0f, "nothing extra against anyone else");
		DamageSource hit = p.damageSources().playerAttack(p);
		marked.hurt(hit, 10.0f);
		plain.hurt(hit, 10.0f);
		float onMarked = marked.getMaxHealth() - marked.getHealth();
		float onPlain = plain.getMaxHealth() - plain.getHealth();
		helper.assertTrue(onPlain > 0.0f && Math.abs(onMarked / onPlain - 1.3f) < 0.02f,
				"the same blow does 30% more to the marked husk (" + onMarked + " vs " + onPlain + ")");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.13.21 key layout

	@GameTest(template = EMPTY_STRUCTURE)
	public void keysFollowTheNewLayout(GameTestHelper helper) {
		helper.assertTrue(MoonKnightAbilityManager.moveFor(AbilitySlot.SLOT_1) == MoonKnightDarts.INSTANCE, "R = Crescent Darts");
		helper.assertTrue(MoonKnightAbilityManager.moveFor(AbilitySlot.SLOT_2) == MoonKnightGrapple.INSTANCE, "G = Grapple Kick");
		helper.assertTrue(MoonKnightAbilityManager.moveFor(AbilitySlot.SLOT_3) == MoonKnightDash.INSTANCE, "X = Dash");
		helper.assertTrue(MoonKnightAbilityManager.moveFor(AbilitySlot.SLOT_4) == MoonKnightKhonshu.INSTANCE, "Z = Khonshu (Moonbeam)");
		helper.assertTrue(MoonKnightAbilityManager.moveFor(AbilitySlot.SLOT_5) == MoonKnightAlters.INSTANCE, "V = Alters");
		helper.assertTrue(MoonKnightAbilityManager.moveFor(AbilitySlot.SLOT_6) == MoonKnightTruncheon.INSTANCE, "C = Truncheon");
		helper.assertTrue(MoonKnightDash.INSTANCE.firesOnPress() && MoonKnightGrapple.INSTANCE.firesOnPress(),
				"X and G have no hold move, so they fire on the press");
		helper.assertFalse(MoonKnightAlters.INSTANCE.firesOnPress() || MoonKnightKhonshu.INSTANCE.firesOnPress(),
				"V and Z still wait for a hold");
		helper.assertTrue(MoonKnightConfig.DART_DAMAGE == 15.0f && MoonKnightConfig.DART_COOLDOWN == 20,
				"the dart: 15 damage, 1 s cooldown (base, before the moon)");
		helper.assertTrue(MoonKnightConfig.GRAPPLE_RANGE == 60.0, "the grappling line reaches 60 blocks");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Cape (no key since v0.13.21)

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_shroud")
	public void capeBlockCutsDamageWithNoTimeLimit(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		clearSpawnInvulnerability(p);
		Arrow shot = new Arrow(p.level(), p.getX(), p.getY() + 4.0, p.getZ(), new ItemStack(Items.ARROW), null);
		DamageSource arrow = p.damageSources().arrow(shot, null);
		helper.assertTrue(Math.abs(MoonKnightDamage.incomingFactor(p, arrow) - MoonKnightConfig.SUIT_DAMAGE_TAKEN) < 1.0e-4f,
				"the suit alone takes 20% off");
		p.hurt(arrow, 8.0f);
		float open = p.getMaxHealth() - p.getHealth();
		p.setHealth(p.getMaxHealth());
		p.invulnerableTime = 0;

		helper.assertTrue(MoonKnightCape.canBlock(p), "an empty main hand can raise the cape");
		MoonKnightCape.startBlock(p);
		helper.assertTrue(MoonKnightCape.isBlocking(p), "holding right click raises the cape (FLAG_CAPE_BLOCK)");
		helper.assertTrue(Math.abs(MoonKnightCape.incomingFactor(p, arrow) - MoonKnightConfig.CAPE_BLOCK_FACTOR) < 1.0e-4f,
				"every hit does 70%");
		helper.assertTrue(Math.abs(MoonKnightDamage.incomingFactor(p, arrow)
				- MoonKnightConfig.SUIT_DAMAGE_TAKEN * MoonKnightConfig.CAPE_BLOCK_FACTOR) < 1.0e-4f, "on top of the suit's 20%");
		p.hurt(arrow, 8.0f);
		float blocked = p.getMaxHealth() - p.getHealth();
		helper.assertTrue(open > 0.0f && blocked < open * 0.8f,
				"the blocked arrow hurts less (" + blocked + " vs " + open + ")");
		for (int i = 0; i < 400; i++) {
			MoonKnightCape.INSTANCE.tick(p);
		}
		helper.assertTrue(MoonKnightCape.isBlocking(p), "no time limit: still up 20 s later");
		MoonKnightCape.stopBlock(p);
		helper.assertFalse(MoonKnightCape.isBlocking(p), "letting go lowers it");
		p.getInventory().items.set(p.getInventory().selected, new ItemStack(Items.DIRT));
		helper.assertFalse(MoonKnightCape.canBlock(p), "not with a block in the hand (right click keeps its vanilla use)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void glideIsJumpThenHoldSneak(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 3.0, 2.5), 0.0f);
		p.setOnGround(true);
		p.setShiftKeyDown(true);
		for (int i = 0; i < 5; i++) {
			MoonKnightCape.tickGlide(p);
		}
		helper.assertFalse(MoonKnightCape.isGliding(p), "sneaking on the ground is just sneaking");
		p.setOnGround(false);
		MoonKnightCape.tickGlide(p);
		helper.assertFalse(MoonKnightCape.isGliding(p), "not the instant he leaves the ground");
		for (int i = 0; i < MoonKnightConfig.GLIDE_MIN_AIR_TICKS; i++) {
			MoonKnightCape.tickGlide(p);
		}
		helper.assertTrue(MoonKnightCape.isGliding(p), "airborne with Sneak held: the cape glide starts");
		p.setShiftKeyDown(false);
		MoonKnightCape.tickGlide(p);
		helper.assertFalse(MoonKnightCape.isGliding(p), "letting go of Sneak ends it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_glide_kick")
	public void glidingIntoAMobKicksIt(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f); // facing +Z
		Husk h = husk(helper, p.position().add(0, 0, 1.0));
		MoonKnightCape.startGlide(p);
		helper.assertTrue(MoonKnightCape.glideKick(p) == 1, "the gliding body kicks the husk in its path");
		helper.assertTrue(h.getHealth() < h.getMaxHealth(), "and hurts it");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.GLIDE_KICK, "with the kick pose");
		helper.assertTrue(MoonKnightCape.glideKick(p) == 0, "one kick at a time");
		helper.assertTrue(MoonKnightConfig.GLIDE_KICK_DAMAGE == 12.0f, "12 damage (base)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void glideCarriesFurtherAtNight(GameTestHelper helper) {
		double day = MoonKnightCape.settledSink(MoonKnightConfig.LUNAR_DAY);
		double fullMoon = MoonKnightCape.settledSink(MoonKnightConfig.LUNAR_FULL_MOON);
		helper.assertTrue(fullMoon < day, "a full-moon glide sinks slower (" + fullMoon + " vs " + day + ")");
		helper.assertTrue(fullMoon >= MoonKnightConfig.GLIDE_MIN_SINK - 1.0e-6, "but never slower than the anti-float floor");
		helper.assertTrue(Math.abs(day - MoonKnightConfig.GLIDE_SINK / MoonKnightConfig.LUNAR_DAY) < 0.005,
				"a level glide settles exactly on the configured sink (" + day + ")");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G: Grapple Kick / Shadow Step

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_shadow_step")
	public void shadowStepMovesBackAndHides(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(3.5, 2.0, 7.5), 0.0f); // facing +Z, so "back" is -Z inside the plot
		double z0 = p.getZ();
		MoonKnightGrapple.INSTANCE.sneak(p);
		double moved = z0 - p.getZ();
		double min = MoonKnightConfig.SHADOW_STEP_DISTANCE * MoonKnightConfig.LUNAR_MIN - 0.3;
		helper.assertTrue(moved >= min, "Sneak+G Shadow Step blinks straight back (" + moved + " blocks)");
		helper.assertTrue(Math.abs(p.getX() - helper.absoluteVec(new Vec3(3.5, 2.0, 7.5)).x) < 0.01, "and only back");
		helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "cloaked in shadow (invisibility)");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "kick_sneak") > 0, "cooldown started");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_kick")
	public void grappleKickPullsInFeetFirst(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 1.5), 0.0f);
		Husk h = husk(helper, p.position().add(0, 0, 6));
		lookAt(p, h);
		MoonKnightGrapple.INSTANCE.tap(p);
		helper.assertTrue(MoonKnightGrapple.isPulling(p), "G fires the line into the husk and pulls him in");
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_DIVING), "feet first (FLAG_DIVING)");
		helper.assertTrue(MoonKnightAnim.action(p).lineTargetId == h.getId(), "the rope is drawn to it");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "kick") > 0, "cooldown started");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X: Dash / Grappling Line

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "mk_dash")
	public void dashBurstsAlongTheLook(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 1.5), 0.0f); // facing +Z
		MoonKnightDash.INSTANCE.tap(p);
		helper.assertTrue(MoonKnightDash.isDashing(p), "X dashes");
		helper.assertTrue(p.getDeltaMovement().z > MoonKnightConfig.DASH_SPEED * 0.9 && Math.abs(p.getDeltaMovement().x) < 0.05,
				"hard along the look, flat (" + p.getDeltaMovement() + ")");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "dash") > 0, "cooldown started");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.DASH, "DASH pose");
		helper.onEachTick(() -> MoonKnightDash.INSTANCE.tick(p));
		helper.runAfterDelay(MoonKnightConfig.DASH_TICKS + 3, () -> {
			helper.assertFalse(MoonKnightDash.isDashing(p), "and it ends after a moment");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "mk_grapple")
	public void grapplingLinePullsTowardABlock(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 1.5), 0.0f);
		for (int x = 0; x < 6; x++) {
			for (int y = 1; y < 6; y++) {
				helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
			}
		}
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 3.5, 7.0)));
		MoonKnightDash.INSTANCE.sneak(p);
		MoonKnightAction a = MoonKnightAnim.action(p);
		helper.assertTrue(a.lineStart >= 0 && a.lineTargetId < 0, "Sneak+X fastens the line to the wall (synced for the rope)");
		helper.assertTrue(MoonKnightGrapple.isPulling(p), "and starts pulling");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "dash_sneak") > 0, "cooldown started");
		helper.onEachTick(() -> MoonKnightGrapple.INSTANCE.tick(p));
		helper.runAfterDelay(MoonKnightConfig.GRAPPLE_LINE_TRAVEL_TICKS + 2, () -> {
			helper.assertTrue(p.getDeltaMovement().z > 0.5, "pulled hard toward the wall (" + p.getDeltaMovement() + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "mk_yank")
	public void grapplingLineReelsAMobIn(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 1.5), 0.0f);
		Husk h = husk(helper, p.position().add(0, 0, 6));
		lookAt(p, h);
		MoonKnightGrapple.fireLine(p);
		helper.assertTrue(MoonKnightGrapple.isReeling(p), "Sneak+X at a mob reels it in");
		helper.assertFalse(MoonKnightGrapple.isPulling(p), "(it comes to him, not the other way round)");
		helper.assertTrue(h.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
				&& h.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == MoonKnightConfig.YANK_SLOW_AMPLIFIER,
				"held with Slowness IV while it is dragged");
		helper.assertTrue(MoonKnightAnim.action(p).lineTargetId == h.getId(), "the rope is drawn to it");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "dash_sneak") > 0, "cooldown started");
		helper.onEachTick(() -> MoonKnightGrapple.INSTANCE.tick(p));
		helper.runAfterDelay(MoonKnightConfig.GRAPPLE_LINE_TRAVEL_TICKS + 2, () -> {
			helper.assertTrue(h.getDeltaMovement().z < -0.3, "the husk is dragged toward him (" + h.getDeltaMovement() + ")");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_truncheon")
	public void truncheonSummonAndStowKeepsItems(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		p.getInventory().selected = 0;
		p.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 7));
		MoonKnightTruncheon.INSTANCE.tap(p);
		helper.assertTrue(MoonKnightTruncheon.isTruncheon(p.getMainHandItem()), "C puts the truncheon in the hand");
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_TRUNCHEON), "FLAG_TRUNCHEON is on");
		helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 7, "the held diamonds moved into the inventory, not deleted");
		MoonKnightTruncheon.INSTANCE.tap(p);
		helper.assertFalse(MoonKnightTruncheon.isTruncheon(p.getMainHandItem()), "C again stows it");
		helper.assertFalse(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_TRUNCHEON), "flag off");
		helper.assertTrue(p.getMainHandItem().is(Items.DIAMOND) && p.getMainHandItem().getCount() == 7,
				"and the diamonds come back to the hand");

		// a full inventory refuses rather than deleting anything
		for (int i = 0; i < p.getInventory().items.size(); i++) {
			p.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 1));
		}
		MoonKnightTruncheon.INSTANCE.tap(p);
		helper.assertFalse(MoonKnightTruncheon.isTruncheon(p.getMainHandItem()), "a full inventory refuses the summon");
		helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == p.getInventory().items.size(), "nothing was lost");

		// un-transforming takes it away
		p.getInventory().items.set(5, ItemStack.EMPTY);
		MoonKnightTruncheon.INSTANCE.tap(p);
		helper.assertTrue(MoonKnightTruncheon.isTruncheon(p.getMainHandItem()), "summoned again");
		MoonKnight.setTransformedForTesting(p, false);
		helper.assertFalse(p.getInventory().hasAnyMatching(MoonKnightTruncheon::isTruncheon), "the suit coming off takes it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_combo")
	public void everyThirdTruncheonHitSlams(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		Husk h = husk(helper, p.position().add(0, 0, 2));
		MoonKnightTruncheon.summon(p);
		MoonKnightTruncheon.onMeleeHit(p, h, 6.0f);
		MoonKnightTruncheon.onMeleeHit(p, h, 6.0f);
		helper.assertTrue(h.getHealth() == h.getMaxHealth(), "the first two hits are plain swings (no slam)");
		MoonKnightTruncheon.onMeleeHit(p, h, 6.0f);
		helper.assertTrue(h.getHealth() < h.getMaxHealth(), "the third consecutive hit is a slam");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.TRUNCHEON_SLAM, "with its pose");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_staff")
	public void staffSpinHitsAllAround(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(3.5, 2.0, 3.5), 0.0f);
		Husk[] ring = {
				husk(helper, p.position().add(1.8, 0, 0)), husk(helper, p.position().add(-1.8, 0, 0)),
				husk(helper, p.position().add(0, 0, 1.8)), husk(helper, p.position().add(0, 0, -1.8)) };
		MoonKnightTruncheon.INSTANCE.holdStart(p);
		for (Husk h : ring) {
			helper.assertTrue(h.getHealth() < h.getMaxHealth(), "the staff spin hits every side");
		}
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_STAFF), "the truncheon is extended into the staff");
		helper.assertTrue(MoonKnightTruncheon.isTruncheon(p.getMainHandItem()), "(summoned first, since it wasn't out)");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "truncheon_hold") > 0, "cooldown started");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_ground_slam")
	public void groundSlamLaunches(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(3.5, 2.0, 3.5), 0.0f);
		Husk a = husk(helper, p.position().add(1.8, 0, 0));
		Husk b = husk(helper, p.position().add(0, 0, -1.8));
		p.setOnGround(true);
		MoonKnightTruncheon.INSTANCE.sneak(p);
		helper.assertTrue(a.getHealth() < a.getMaxHealth() && b.getHealth() < b.getMaxHealth(), "the shockwave hits all round");
		helper.assertTrue(a.getDeltaMovement().y > 0.4 && b.getDeltaMovement().y > 0.4, "and launches them upward");
		helper.assertTrue(MoonKnightAnim.action(p).animId == MoonKnightAnim.GROUND_SLAM, "GROUND_SLAM pose");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "truncheon_sneak") > 0, "cooldown started");
		helper.succeed();
	}
}

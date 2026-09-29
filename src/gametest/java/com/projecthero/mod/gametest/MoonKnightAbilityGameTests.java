package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.ability.MoonKnightAbilities;
import com.projecthero.mod.moonknight.ability.MoonKnightCape;
import com.projecthero.mod.moonknight.ability.MoonKnightDarts;
import com.projecthero.mod.moonknight.ability.MoonKnightGrapple;
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
 * Moon Knight Phases 3-4: R Crescent Darts, X Cape, G Grappling Line, Z Truncheon / Staff. Mock players are not
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

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_shroud")
	public void shroudReducesProjectileDamage(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		clearSpawnInvulnerability(p);
		Arrow shot = new Arrow(p.level(), p.getX(), p.getY() + 4.0, p.getZ(), new ItemStack(Items.ARROW), null);
		DamageSource arrow = p.damageSources().arrow(shot, null);
		p.hurt(arrow, 8.0f);
		float open = p.getMaxHealth() - p.getHealth();
		p.setHealth(p.getMaxHealth());
		p.invulnerableTime = 0;

		MoonKnightCape.INSTANCE.holdStart(p);
		helper.assertTrue(MoonKnightCape.isShrouded(p), "holding X wraps the cape (FLAG_SHROUD)");
		helper.assertTrue(Math.abs(MoonKnightCape.incomingFactor(p, arrow) - MoonKnightConfig.SHROUD_PROJECTILE_FACTOR) < 1.0e-4f,
				"projectiles do 40%");
		p.hurt(arrow, 8.0f);
		float shrouded = p.getMaxHealth() - p.getHealth();
		helper.assertTrue(open > 0.0f && shrouded < open * 0.6f,
				"the shrouded arrow hurts much less (" + shrouded + " vs " + open + ")");
		MoonKnightCape.INSTANCE.holdRelease(p, 20);
		helper.assertFalse(MoonKnightCape.isShrouded(p), "letting go unwraps it");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "cape_hold") > 0, "and starts the shroud cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_shadow_step")
	public void shadowStepMovesBackAndHides(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(3.5, 2.0, 7.5), 0.0f); // facing +Z, so "back" is -Z inside the plot
		double z0 = p.getZ();
		MoonKnightCape.INSTANCE.sneak(p);
		double moved = z0 - p.getZ();
		double min = MoonKnightConfig.SHADOW_STEP_DISTANCE * MoonKnightConfig.LUNAR_MIN - 0.3;
		helper.assertTrue(moved >= min, "Shadow Step blinks straight back (" + moved + " blocks)");
		helper.assertTrue(Math.abs(p.getX() - helper.absoluteVec(new Vec3(3.5, 2.0, 7.5)).x) < 0.01, "and only back");
		helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "cloaked in shadow (invisibility)");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "cape_sneak") > 0, "cooldown started");
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

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "mk_grapple")
	public void grappleTapPullsTowardABlock(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 1.5), 0.0f);
		for (int x = 0; x < 6; x++) {
			for (int y = 1; y < 6; y++) {
				helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
			}
		}
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 3.5, 7.0)));
		MoonKnightGrapple.INSTANCE.tap(p);
		MoonKnightAction a = MoonKnightAnim.action(p);
		helper.assertTrue(a.lineStart >= 0 && a.lineTargetId < 0, "the line is fastened to the wall (synced for the rope)");
		helper.assertTrue(MoonKnightGrapple.isPulling(p), "and starts pulling");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "grapple") > 0, "cooldown started");
		helper.onEachTick(() -> MoonKnightGrapple.INSTANCE.tick(p));
		helper.runAfterDelay(MoonKnightConfig.GRAPPLE_LINE_TRAVEL_TICKS + 2, () -> {
			helper.assertTrue(p.getDeltaMovement().z > 0.5, "pulled hard toward the wall (" + p.getDeltaMovement() + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_yank")
	public void yankPullsAndSlows(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 1.5), 0.0f);
		Husk h = husk(helper, p.position().add(0, 0, 6));
		lookAt(p, h);
		MoonKnightGrapple.INSTANCE.sneak(p);
		helper.assertTrue(h.getDeltaMovement().z < -0.3, "the husk is yanked toward him (" + h.getDeltaMovement() + ")");
		helper.assertTrue(h.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
				&& h.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == MoonKnightConfig.YANK_SLOW_AMPLIFIER,
				"and stunned with Slowness IV");
		helper.assertTrue(MoonKnightAnim.action(p).lineTargetId == h.getId(), "the rope is drawn to it");
		helper.assertTrue(MoonKnight.cooldownRemaining(p, "grapple_sneak") > 0, "cooldown started");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, batch = "mk_truncheon")
	public void truncheonSummonAndStowKeepsItems(GameTestHelper helper) {
		ServerPlayer p = knight(helper, new Vec3(2.5, 2.0, 2.5), 0.0f);
		p.getInventory().selected = 0;
		p.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 7));
		MoonKnightTruncheon.INSTANCE.tap(p);
		helper.assertTrue(MoonKnightTruncheon.isTruncheon(p.getMainHandItem()), "Z puts the truncheon in the hand");
		helper.assertTrue(MoonKnightAnim.flag(p, MoonKnightAction.FLAG_TRUNCHEON), "FLAG_TRUNCHEON is on");
		helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 7, "the held diamonds moved into the inventory, not deleted");
		MoonKnightTruncheon.INSTANCE.tap(p);
		helper.assertFalse(MoonKnightTruncheon.isTruncheon(p.getMainHandItem()), "Z again stows it");
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

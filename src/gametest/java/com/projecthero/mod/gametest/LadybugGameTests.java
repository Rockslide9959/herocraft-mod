package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ladybug.LadybugConfig;
import com.projecthero.mod.miraculous.ladybug.LadybugKeys;
import com.projecthero.mod.miraculous.ladybug.LadybugMoves;
import com.projecthero.mod.miraculous.ladybug.LadybugRestore;
import com.projecthero.mod.miraculous.ladybug.LuckyCharm;
import com.projecthero.mod.miraculous.ladybug.item.LadybugItems;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Ladybug (Tikki's Miraculous): one test per move's server effect -- the numbers, the roots and the yank, the
 * line, the shield stopping an arrow, the marks, which Lucky Charm the moment gets, the countdown, Miraculous Ladybug
 * putting a broken block back and healing. Mock players are not ticked by the server, so tests that run over time
 * drive {@link Miraculous#tick} themselves ({@link #pump}). Hostile mobs need a non-peaceful difficulty (HARD here).
 */
public class LadybugGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** Ladybug, facing +Z, the transformation already finished. */
	private static ServerPlayer ladybug(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.TIKKI), "Tikki is granted");
		Miraculous.transform(p);
		long t = helper.getLevel().getGameTime() - 200L;
		Miraculous.edit(p, n -> {
			n.formChangeAt = t;
			n.animId = MiraculousState.ANIM_NONE;
		});
		helper.assertTrue(Miraculous.canAct(p), "transformed and ready");
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Miraculous.tick(p);
		});
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void wall(GameTestHelper helper, int z) {
		for (int x = 0; x <= 7; x++) {
			for (int y = 1; y <= 6; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static long val(ServerPlayer p, String key) {
		return Miraculous.state(p).val(key);
	}

	// ---------------------------------------------------------------- the kit

	@GameTest(template = EMPTY_STRUCTURE)
	public void kitIsRegisteredWithTwelveMoves(GameTestHelper helper) {
		var kit = MiraculousKits.of(Kwami.TIKKI);
		int n = 0;
		for (var slot : List.of(com.projecthero.mod.hero.AbilitySlot.SLOT_1, com.projecthero.mod.hero.AbilitySlot.SLOT_2,
				com.projecthero.mod.hero.AbilitySlot.SLOT_3, com.projecthero.mod.hero.AbilitySlot.SLOT_4,
				com.projecthero.mod.hero.AbilitySlot.SLOT_5, com.projecthero.mod.hero.AbilitySlot.SLOT_6)) {
			String[] ids = kit.idsOf(slot);
			helper.assertTrue(ids.length == 2, "two moves on " + slot);
			for (String id : ids) {
				helper.assertTrue(id.startsWith("ladybug_"), "ladybug_ id: " + id);
				n++;
			}
		}
		helper.assertTrue(n == 12, "twelve moves");
		helper.assertTrue(kit.maxCooldown(LadybugKeys.STRIKE) == LadybugConfig.STRIKE_COOLDOWN, "the HUD gets the strike's cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void yoyoStrikeHitsForTenAndCoolsDown(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		helper.assertTrue(LadybugMoves.strike(p), "the strike fires");
		helper.assertTrue(Math.abs((100f - z.getHealth()) - LadybugConfig.STRIKE_DAMAGE) < EPS, "10 damage, took " + (100f - z.getHealth()));
		helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.STRIKE) == LadybugConfig.STRIKE_COOLDOWN, "on cooldown");
		helper.assertFalse(LadybugMoves.strike(p), "not again during the cooldown");
		helper.assertTrue(Miraculous.vec(Miraculous.state(p), LadybugKeys.P_TIP) != null, "the tip is synced for the string");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ricochetBouncesBetweenFourForSevenEach(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.0, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 4.5, 100);
		Zombie c = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 5.5, 100);
		Zombie d = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 7.0, 100);
		int hit = LadybugMoves.ricochet(p);
		helper.assertTrue(hit == 4, "four bounces, got " + hit);
		for (Zombie z : List.of(a, b, c, d)) {
			helper.assertTrue(Math.abs((100f - z.getHealth()) - LadybugConfig.RICOCHET_DAMAGE) < EPS, "7 each, took " + (100f - z.getHealth()));
		}
		helper.assertTrue(LadybugKeys.points(Miraculous.state(p), LadybugKeys.P_RICO).size() == 4, "the bounce points are synced");
		helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.RICOCHET) == LadybugConfig.RICOCHET_COOLDOWN, "on cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE)
	public void bindRootsThenYanksOnTheSecondPress(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		helper.assertTrue(LadybugMoves.bindOrYank(p), "the bind lands");
		helper.assertTrue(LadybugKeys.bound(p) == z.getId(), "the zombie is the bound target");
		helper.assertTrue(LadybugMoves.rooted(z), "rooted");
		helper.assertTrue(val(p, LadybugKeys.V_BIND_UNTIL) - helper.getLevel().getGameTime() == LadybugConfig.BIND_TICKS, "for 3 s");
		helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.BIND) == LadybugConfig.BIND_COOLDOWN, "on cooldown");
		helper.assertTrue(LadybugMoves.bindOrYank(p), "G again: the yank");
		helper.assertFalse(LadybugMoves.rooted(z), "no longer rooted");
		helper.assertTrue(z.getDeltaMovement().z < -0.3, "pulled toward her (-Z), v=" + z.getDeltaMovement());
		helper.assertTrue(LadybugKeys.bound(p) < 0, "the bind is spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void bindWearsOffAfterThreeSeconds(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		pump(helper, p);
		LadybugMoves.bindOrYank(p);
		helper.runAfterDelay(LadybugConfig.BIND_TICKS + 4, () -> {
			helper.assertFalse(LadybugMoves.rooted(z), "free again after 3 s");
			helper.assertTrue(LadybugKeys.bound(p) < 0, "the bind ended");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void lassoFlingThrowsAndTheLandingHurts(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 3.0, 5.5, 100);
		z.setOnGround(false);
		helper.assertTrue(LadybugMoves.fling(p), "the fling fires (binds on the spot)");
		Vec3 v = z.getDeltaMovement();
		helper.assertTrue(v.z > 1.5 && v.y > 0.3, "thrown along the look and up, v=" + v);
		helper.assertTrue(LadybugMoves.flying(z), "in the air");
		helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.FLING) == LadybugConfig.FLING_COOLDOWN, "on cooldown");
		helper.runAfterDelay(6, () -> z.setOnGround(true));
		helper.runAfterDelay(10, () -> {
			helper.assertFalse(LadybugMoves.flying(z), "landed");
			helper.assertTrue(Math.abs((100f - z.getHealth()) - LadybugConfig.FLING_IMPACT_DAMAGE) < EPS, "8 on landing, took " + (100f - z.getHealth()));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void swingHooksTheWallAndLetsGo(GameTestHelper helper) {
		wall(helper, 6);
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		helper.assertTrue(LadybugMoves.lineDown(p, false), "hooked");
		helper.assertTrue(LadybugKeys.line(p) == 1, "swinging");
		Vec3 hook = LadybugKeys.hook(p);
		helper.assertTrue(hook != null && Math.abs(hook.z - helper.absoluteVec(new Vec3(0, 0, 6)).z) < 0.05, "the hook is on the wall: " + hook);
		double rope = LadybugKeys.rope(Miraculous.state(p));
		helper.assertTrue(Math.abs(rope - hook.distanceTo(p.getEyePosition())) < 0.1, "rope = the distance, " + rope);
		helper.assertTrue(p.getAbilities().mayfly && !p.getAbilities().flying, "the anti-float tolerance (no flight)");
		LadybugMoves.lineUp(p);
		helper.assertTrue(LadybugKeys.line(p) == 0, "let go");
		helper.assertFalse(p.getAbilities().mayfly, "tolerance handed back");
		helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.SWING) == LadybugConfig.SWING_COOLDOWN, "a short cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void zipLineHooksAndRunsToTheEnd(GameTestHelper helper) {
		wall(helper, 6);
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		helper.assertTrue(LadybugMoves.lineDown(p, true), "zip hooked");
		helper.assertTrue(LadybugKeys.line(p) == 2, "zipping");
		LadybugMoves.lineUp(p);
		helper.assertTrue(LadybugKeys.line(p) == 2, "letting go of X does not stop a zip");
		helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.ZIP) == LadybugConfig.ZIP_COOLDOWN, "2 s cooldown");
		p.teleportTo(p.getX(), p.getY(), helper.absoluteVec(new Vec3(0, 0, 5.2)).z);
		MiraculousKits.of(Kwami.TIKKI).tick(p);
		helper.assertTrue(LadybugKeys.line(p) == 0, "arrived: the zip ends");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void spinningShieldStopsAnArrow(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		pump(helper, p);
		helper.assertTrue(LadybugMoves.shieldUp(p), "the shield spins up");
		helper.assertTrue(LadybugKeys.shielding(p), "synced");
		float hp = p.getHealth();
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		Vec3 at = p.getEyePosition().add(0, -0.4, 2.6);
		arrow.moveTo(at.x, at.y, at.z, 180f, 0f);
		arrow.setDeltaMovement(0, 0, -0.8);
		helper.getLevel().addFreshEntity(arrow);
		MiraculousKits.of(Kwami.TIKKI).tick(p);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(arrow.isRemoved(), "the arrow shattered on the disc");
			helper.assertTrue(p.getHealth() >= hp, "no damage");
			LadybugMoves.shieldDown(p);
			helper.assertFalse(LadybugKeys.shielding(p), "lowered on release");
			helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.SHIELD) > 0, "then a cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spinningShieldBluntsMeleeFromTheFront(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 2.0, 40);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, -1.0 + 0.5, 40);
		LadybugMoves.shieldUp(p);
		var kit = MiraculousKits.of(Kwami.TIKKI);
		float f = kit.modifyIncomingDamage(p, helper.getLevel().damageSources().mobAttack(front), 8f);
		float b = kit.modifyIncomingDamage(p, helper.getLevel().damageSources().mobAttack(behind), 8f);
		helper.assertTrue(Math.abs(f - 8f * LadybugConfig.SHIELD_MELEE_PASS) < 0.01f, "front: a quarter gets through, " + f);
		helper.assertTrue(b == 8f, "behind: the full hit, " + b);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+Z

	@GameTest(template = EMPTY_STRUCTURE)
	public void luckyCharmFitsTheMoment(GameTestHelper helper) {
		helper.assertTrue(LuckyCharm.pick(0.2f, 0, false, -1) == LuckyCharm.Kind.APPLE, "hurt: the apple");
		helper.assertTrue(LuckyCharm.pick(0.2f, 9, true, 2) == LuckyCharm.Kind.APPLE, "hurt beats everything");
		helper.assertTrue(LuckyCharm.pick(1f, 5, false, 2) == LuckyCharm.Kind.TNT, "surrounded by 5: TNT");
		helper.assertTrue(LuckyCharm.pick(1f, 4, true, 2) == LuckyCharm.Kind.CHUTE, "falling: the parachute");
		helper.assertTrue(LuckyCharm.pick(1f, 1, false, 18) == LuckyCharm.Kind.BOW, "a far threat: the bow");
		helper.assertTrue(LuckyCharm.pick(1f, 1, false, 4) == LuckyCharm.Kind.SWORD, "a close one: the sword");
		helper.assertTrue(LuckyCharm.pick(1f, 0, false, -1) == LuckyCharm.Kind.SWORD, "nothing about: the sword");
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 3.5);
		p.setHealth(4f);
		helper.assertTrue(LuckyCharm.choose(p) == LuckyCharm.Kind.APPLE, "a real hurt Ladybug gets the apple");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void luckyCharmDropsStartsTheCountdownAndOnlyOnce(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(Miraculous.countdownLeft(p) == 0, "no countdown yet");
		LuckyCharm.handle(p);
		LuckyCharm.Kind kind = LuckyCharm.Kind.byId(val(p, LadybugKeys.V_CHARM));
		helper.assertTrue(kind != null, "a charm is out");
		helper.assertTrue(Miraculous.countdownLeft(p) > 0, "the 3-minute countdown started");
		helper.assertTrue(LadybugKeys.vision(p), "Ladybug Vision is on");
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(4, 8, 4),
				e -> LadybugItems.isCharm(e.getItem()));
		helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(kind.item()), "the " + kind + " drops from above");
		helper.assertTrue(drops.get(0).getY() > p.getY() + 3, "from the sky");
		long end = Miraculous.state(p).countdownEnd;
		LuckyCharm.handle(p);
		helper.assertTrue(val(p, LadybugKeys.V_CHARM) == kind.id, "not holding it: nothing happens");
		// hold it and cast Miraculous Ladybug
		p.setItemInHand(InteractionHand.MAIN_HAND, LadybugItems.bound(kind.item(), p));
		LuckyCharm.handle(p);
		helper.assertTrue(val(p, LadybugKeys.V_CHARM) == 0L, "Miraculous Ladybug used the charm");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the charm is gone from her hand");
		helper.assertTrue(drops.get(0).isRemoved(), "and off the ground");
		helper.assertTrue(Miraculous.state(p).countdownEnd == end, "the countdown is NOT restarted");
		LuckyCharm.handle(p);
		helper.assertTrue(val(p, LadybugKeys.V_CHARM) == 0L, "one charm per transformation");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void miraculousLadybugRestoresABrokenBlockAndHeals(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 1.5, 2.0, 1.5);
		BlockPos rel = new BlockPos(5, 3, 5);
		BlockPos abs = helper.absolutePos(rel);
		helper.getLevel().setBlock(abs, Blocks.BRICKS.defaultBlockState(), 3);
		MiraculousKits.of(Kwami.TIKKI).tick(p); // she is transformed here: the memory is on
		helper.getLevel().destroyBlock(abs, false);
		helper.assertTrue(helper.getLevel().getBlockState(abs).isAir(), "the block is broken");
		helper.assertTrue(LadybugRestore.tracked(helper.getLevel()) > 0, "remembered");
		p.setHealth(5f);
		LuckyCharm.summon(p);
		int queued = LuckyCharm.miraculousLadybug(p);
		helper.assertTrue(queued >= 1, "the block is queued to come back, " + queued);
		helper.assertTrue(p.getHealth() == p.getMaxHealth(), "healed to full");
		helper.assertTrue(val(p, LadybugKeys.V_ML_AT) != 0L, "the swarm is synced");
		helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.BRICKS, rel));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void miraculousLadybugHealsSquadmates(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 1.5, 2.0, 1.5);
		ServerPlayer mate = mock(helper, 5.5, 2.0, 5.5);
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("lb" + p.getUUID().toString().substring(0, 8), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			mate.setHealth(3f);
			LuckyCharm.summon(p);
			LuckyCharm.miraculousLadybug(p);
			helper.assertTrue(mate.getHealth() == mate.getMaxHealth(), "the squadmate healed to full, " + mate.getHealth());
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void charmsVanishWithTheTransformation(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 3.5);
		LuckyCharm.summon(p);
		LuckyCharm.Kind kind = LuckyCharm.Kind.byId(val(p, LadybugKeys.V_CHARM));
		ItemStack stack = LadybugItems.bound(kind.item(), p);
		helper.assertFalse(LadybugItems.expired(stack, p), "hers, now");
		p.getInventory().add(stack.copy());
		Miraculous.detransform(p, false);
		boolean any = false;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			any |= LadybugItems.isCharm(p.getInventory().getItem(i));
		}
		helper.assertFalse(any, "no charm left after detransforming");
		ServerPlayer other = mock(helper, 5.5, 2.0, 5.5);
		helper.assertTrue(LadybugItems.expired(stack, other), "a charm in anyone else's hands is expired");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE)
	public void poleVaultLaunchesUpAndForward(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		helper.assertTrue(LadybugMoves.vault(p), "vault");
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(Math.abs(v.y - LadybugConfig.VAULT_UP) < 0.01 && Math.abs(v.z - LadybugConfig.VAULT_FORWARD) < 0.01, "up and forward, v=" + v);
		helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.VAULT) == LadybugConfig.VAULT_COOLDOWN, "on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void tripWireRootsWhatCrossesIt(GameTestHelper helper) {
		wall(helper, 7);
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		p.setXRot(10f);
		helper.assertTrue(LadybugMoves.wire(p), "the wire is strung");
		helper.assertTrue(LadybugKeys.wireUp(p), "synced");
		helper.assertTrue(val(p, LadybugKeys.V_WIRE_UNTIL) - helper.getLevel().getGameTime() == LadybugConfig.WIRE_TICKS, "20 s");
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.0, 40);
		Pig pig = mob(helper, EntityType.PIG, 7.5, 2.0, 4.0, 10);
		MiraculousKits.of(Kwami.TIKKI).tick(p);
		helper.assertTrue(LadybugMoves.rooted(z), "the zombie on the wire is rooted");
		helper.assertFalse(LadybugMoves.rooted(pig), "the pig off it is not");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void yoyoScanMarksTheThreats(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 1.5, 2.0, 1.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 5.5, 20);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 2.5, 20);
		Pig pig = mob(helper, EntityType.PIG, 3.5, 2.0, 6.5, 10);
		List<Integer> ids = LadybugMoves.scan(p);
		helper.assertTrue(ids.contains(a.getId()) && ids.contains(b.getId()), "both zombies marked");
		helper.assertFalse(ids.contains(pig.getId()), "not the pig");
		helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.SCAN) == LadybugConfig.SCAN_COOLDOWN, "15 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void yoyoPhoneNeedsASquadAndMarksIt(GameTestHelper helper) {
		ServerPlayer p = ladybug(helper, 1.5, 2.0, 1.5);
		helper.assertTrue(LadybugMoves.phone(p).isEmpty(), "no squad: nobody to call");
		helper.assertTrue(Miraculous.ready(p, LadybugKeys.PHONE), "and no cooldown spent");
		ServerPlayer mate = mock(helper, 5.5, 2.0, 5.5);
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("lp" + p.getUUID().toString().substring(0, 8), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			List<Integer> ids = LadybugMoves.phone(p);
			helper.assertTrue(ids.contains(mate.getId()), "the squadmate glows for her");
			helper.assertTrue(Miraculous.cooldownRemaining(p, LadybugKeys.PHONE) == LadybugConfig.PHONE_COOLDOWN, "on cooldown");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void detransformingDropsTheLineAndTheShield(GameTestHelper helper) {
		wall(helper, 6);
		ServerPlayer p = ladybug(helper, 3.5, 2.0, 0.5);
		LadybugMoves.lineDown(p, false);
		Miraculous.detransform(p, false);
		helper.assertFalse(p.getAbilities().mayfly, "no float tolerance left behind");
		helper.assertTrue(Miraculous.state(p).vals.isEmpty(), "her synced values are cleared");
		helper.assertFalse(Miraculous.canAct(p), "no moves while detransformed");
		helper.succeed();
	}

	/** v0.16.7 (user): the Lucky Charm's numbers -- Sharpness V netherite sword, Power V arrows, 10-TNT blast, the big apple. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void luckyCharmItemsHitLikeTheirVanillaPeers(GameTestHelper helper) {
		var sword = new net.minecraft.world.item.ItemStack(com.projecthero.mod.miraculous.ladybug.item.LadybugItems.LUCKY_SWORD);
		double[] dmg = { 0 };
		sword.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
			if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
				dmg[0] += mod.amount();
			}
		});
		helper.assertTrue(Math.abs(1.0 + dmg[0] - 11.0) < 1e-6, "sword hits 11 like Sharpness V netherite, got " + (1.0 + dmg[0]));
		helper.assertTrue(com.projecthero.mod.miraculous.ladybug.LadybugConfig.CHARM_ARROW_DAMAGE == 5.0, "Power V arrows (base 5)");
		helper.assertTrue(com.projecthero.mod.miraculous.ladybug.LadybugConfig.CHARM_TNT_DAMAGE == 100f
				&& com.projecthero.mod.miraculous.ladybug.LadybugConfig.CHARM_TNT_RADIUS == 12.0, "10-TNT blast");
		var food = new net.minecraft.world.item.ItemStack(com.projecthero.mod.miraculous.ladybug.item.LadybugItems.LUCKY_APPLE)
				.get(net.minecraft.core.component.DataComponents.FOOD);
		helper.assertTrue(food != null && food.nutrition() == 20 && Math.abs(food.saturation() - 20f) < 1e-3, "apple fills hunger and saturation");
		boolean regen2 = false, absorb5 = false;
		for (var e : food.effects()) {
			var fx = e.effect();
			if (fx.getEffect().equals(net.minecraft.world.effect.MobEffects.REGENERATION) && fx.getAmplifier() == 1 && fx.getDuration() == 1200) regen2 = true;
			if (fx.getEffect().equals(net.minecraft.world.effect.MobEffects.ABSORPTION) && fx.getAmplifier() == 4) absorb5 = true;
		}
		helper.assertTrue(regen2 && absorb5, "Regeneration II for a minute and Absorption V");
		helper.succeed();
	}
}

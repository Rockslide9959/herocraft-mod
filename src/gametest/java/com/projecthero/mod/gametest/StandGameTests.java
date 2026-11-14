package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.StandMove;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.StarPlatinum;
import com.projecthero.mod.stand.TimeStop;
import com.projecthero.mod.stand.data.StandState;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Stands (Star Platinum), v0.17.3 the user's rework: the grant (one Primary, never from serums), summon / dismiss and the punch
 * reach, the visibility rule (the state syncs only to Stand users), the left-click punch, the 3-a-second Stamina, the iron-tool mining,
 * one test per move with the user's numbers, and the time stop (queued damage with the 30% bonus, flight, the drain, the slow refill,
 * and a whole-level freeze). Mock players are not ticked by the server, so tests drive {@link StarPlatinum#tick} themselves. The time
 * stops run alone in their own batches; one uses a small radius ({@link TimeStop#testRadius}), the whole-level one has none.
 */
public class StandGameTests implements FabricGameTest {
	// ---------------------------------------------------------------- helpers

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

	/** A Star Platinum user facing +Z with the Stand out and full Stamina. */
	private static ServerPlayer user(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		Stands.grant(p, StandType.STAR_PLATINUM);
		Stands.summon(p);
		return p;
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
		if (m.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
			m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		}
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static boolean onCooldown(ServerPlayer p, String move) {
		return Stands.cooldownRemaining(p, move) > 0;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<StandState> fn) {
		StandState n = Stands.state(p).copy();
		fn.accept(n);
		p.setAttached(ModAttachments.STAND_STATE, n);
	}

	// ---------------------------------------------------------------- grant / revoke / summon

	@GameTest(template = EMPTY_STRUCTURE)
	public void standReplacesThePrimaryAndSummons(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(HeroTiers.HERO_KEYS.contains("stand_star_platinum") && PowerGrants.HERO_TIER_KEYS.contains("stand_star_platinum"), "stand is a Hero-Tier key");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains("stand_star_platinum"), "random serums never give a Stand");
		Kryptonian.grant(p);
		helper.assertTrue(Stands.grant(p, StandType.STAR_PLATINUM), "the Stand awakens");
		double baseReach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		helper.assertTrue(Stands.hasPower(p) && !Kryptonian.hasPower(p), "the Stand replaced the Kryptonian (one Primary)");
		helper.assertTrue(HeroTiers.heroCount(p) == 1 && HeroTiers.holdsHero(p, "stand_star_platinum"), "exactly one hero held");
		helper.assertFalse(Stands.grant(p, StandType.STAR_PLATINUM), "a second grant does nothing");
		helper.assertTrue(Stands.stamina(p) >= StandConfig.STAMINA_MAX - 0.01f, "full Stamina to start");
		helper.assertFalse(Stands.summoned(p), "the Stand starts away");
		Stands.toggleSummon(p);
		helper.assertTrue(Stands.summoned(p), "H summons it");
		helper.assertTrue(Stands.state(p).animId == StandState.ANIM_SUMMON && StandConfig.SUMMON_ANIM_TICKS == 8, "it comes out of you over 0.4 s");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach - StandConfig.PUNCH_REACH_BONUS) < 0.01,
				"+1 punch reach while it is out");
		Stands.dismiss(p);
		helper.assertFalse(Stands.summoned(p), "dismissed");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach) < 0.01, "reach back to normal");
		Stands.summon(p);
		Stands.revoke(p);
		helper.assertTrue(!Stands.hasPower(p) && !Stands.summoned(p), "revoked");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach) < 0.01, "no reach left behind");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyStandUsersReceiveTheStand(GameTestHelper helper) {
		ServerPlayer a = user(helper, 1.5, 2.0, 1.5);
		ServerPlayer b = mock(helper, 3.5, 2.0, 1.5);
		ServerPlayer c = user(helper, 5.5, 2.0, 1.5);
		helper.assertTrue(Stands.SYNC_PREDICATE.test(a, a), "the owner gets their own Stand");
		helper.assertFalse(Stands.SYNC_PREDICATE.test(a, b), "a non-Stand user never receives it");
		helper.assertTrue(Stands.SYNC_PREDICATE.test(a, c), "another Stand user does");
		helper.assertTrue(Stands.SYNC_PREDICATE.test(b, b), "everyone gets their own (empty) state");
		Stands.grant(b, StandType.STAR_PLATINUM);
		helper.assertTrue(Stands.SYNC_PREDICATE.test(a, b), "once awakened, b receives the others' Stands");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void poseToggles(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 2.5);
		Stands.setPoseChoice(p, 4);
		Stands.togglePose(p);
		helper.assertTrue(p.getAttachedOrElse(ModAttachments.STAND_POSE, null).pose() == 1, "N strikes the one pose, whatever an old save chose");
		Stands.togglePose(p);
		helper.assertTrue(p.getAttachedOrElse(ModAttachments.STAND_POSE, null).pose() == 0, "N again drops it");
		helper.succeed();
	}

	// ---------------------------------------------------------------- left click, Stamina, mining

	/** v0.17.3 (user: "left clicking with the stand doesn't make them punch anymore"): the whole attack-callback chain, as a click runs it. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void leftClickIsAStandPunch(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.0, 40);
		InteractionResult r = AttackEntityCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.MAIN_HAND, z, null);
		helper.assertTrue(r == InteractionResult.SUCCESS, "Star Platinum takes the left-click: " + r);
		helper.assertTrue(z.getHealth() < 40f, "and the punch lands, health " + z.getHealth());
		int anim = Stands.state(p).animId;
		helper.assertTrue(anim == StandState.ANIM_PUNCH_R || anim == StandState.ANIM_PUNCH_L || anim == StandState.ANIM_PUNCH_HEAVY, "a punch plays: " + anim);
		helper.succeed();
	}

	/** v0.17.3 (user: "stand stamina regens 3 stamina per second"), and half that for 20 s after a time stop. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void staminaRefillsThreeASecond(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 2.5);
		Stands.setStamina(p, 50f);
		helper.assertTrue(Math.abs(Stands.regenRate(p) - 3f) < 1.0e-4f, "3 a second: " + Stands.regenRate(p));
		helper.onEachTick(() -> Stands.tick(p));
		// (v0.17.4: setting it to 50 counts as a spend, so the refill waits 1 s first: measure the second after that)
		float[] from = { 0f };
		helper.runAfterDelay(25, () -> from[0] = Stands.stamina(p));
		helper.runAfterDelay(45, () -> {
			float st = Stands.stamina(p) - from[0];
			helper.assertTrue(st > 2.2f && st < 3.1f, "about 3 in a second: " + st);
			edit(p, n -> n.regenSlowUntil = helper.getLevel().getGameTime() + 100);
			helper.assertTrue(Math.abs(Stands.regenRate(p) - 1.5f) < 1.0e-4f, "half after a time stop: " + Stands.regenRate(p));
			helper.succeed();
		});
	}

	/** v0.17.3: an empty hand with the Stand out mines like iron tools and keeps the drops; nothing unbreakable gives. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void standMinesLikeIronTools(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 2.0, 1.5);
		Stands.grant(p, StandType.STAR_PLATINUM);
		p.setOnGround(true);
		var stone = Blocks.STONE.defaultBlockState();
		float bare = p.getDestroySpeed(stone);
		helper.assertFalse(p.hasCorrectToolForDrops(stone), "a bare hand gets nothing from stone");
		Stands.summon(p);
		helper.assertTrue(Stands.standMines(p), "the Stand mines for an empty hand");
		float iron = new ItemStack(Items.IRON_PICKAXE).getDestroySpeed(stone);
		// (both go through the same haste / ground / attribute factors: the ratio is the tool's)
		helper.assertTrue(Math.abs(p.getDestroySpeed(stone) / bare - iron) < 0.05f, "iron pickaxe speed on stone: "
				+ p.getDestroySpeed(stone) + " vs bare " + bare + ", iron " + iron);
		helper.assertTrue(p.hasCorrectToolForDrops(stone), "and stone drops");
		helper.assertTrue(p.hasCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()), "iron ore drops (iron tier)");
		helper.assertFalse(p.hasCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "obsidian is past iron");
		float log = new ItemStack(Items.IRON_AXE).getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
		helper.assertTrue(Math.abs(p.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) / bare - log) < 0.05f, "iron axe speed on logs");
		helper.assertTrue(Blocks.BEDROCK.defaultBlockState().getDestroyProgress(p, helper.getLevel(), BlockPos.ZERO) == 0f, "bedrock never breaks");
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 3));
		helper.getLevel().setBlockAndUpdate(pos, stone);
		helper.assertTrue(p.gameMode.destroyBlock(pos), "the block breaks");
		boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(1.5),
				e -> e.getItem().is(Items.COBBLESTONE)).isEmpty();
		helper.assertTrue(dropped, "and drops its cobblestone");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		helper.assertFalse(Stands.standMines(p), "only for an empty hand");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R: Platinum Smash / ORA Barrage, Shift+R: Star Finger

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void platinumSmashIsAFourHitCombo(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 250);
		float[] hp = new float[5];
		hp[0] = z.getHealth();
		StarPlatinum.rPressed(p, false);
		StarPlatinum.rReleased(p); // a tap
		hp[1] = z.getHealth();
		helper.assertTrue(Math.abs(hp[0] - hp[1] - StandConfig.SMASH_DAMAGE) < 0.6f, "hit 1 deals 8: " + (hp[0] - hp[1]));
		helper.assertTrue(PunisherControl.isStunned(z), "and stuns for half a second");
		helper.assertTrue(Math.abs(Stands.stamina(p) - (StandConfig.STAMINA_MAX - StandConfig.SMASH_COST)) < 0.01f, "4 Stamina a hit");
		helper.assertFalse(onCooldown(p, StandMove.PLATINUM_SMASH), "no cooldown mid-combo");
		helper.runAfterDelay(5, () -> {
			StarPlatinum.smash(p);
			hp[2] = z.getHealth();
			helper.assertTrue(Math.abs(hp[1] - hp[2] - StandConfig.SMASH_DAMAGE) < 0.6f, "hit 2 deals 8: " + (hp[1] - hp[2]));
		});
		helper.runAfterDelay(10, () -> {
			StarPlatinum.smash(p);
			hp[3] = z.getHealth();
			helper.assertTrue(Math.abs(hp[2] - hp[3] - StandConfig.SMASH_DAMAGE) < 0.6f, "hit 3 deals 8: " + (hp[2] - hp[3]));
		});
		helper.runAfterDelay(15, () -> {
			z.setDeltaMovement(Vec3.ZERO);
			StarPlatinum.smash(p);
			hp[4] = z.getHealth();
			helper.assertTrue(Math.abs(hp[3] - hp[4] - StandConfig.SMASH_FINAL_DAMAGE) < 0.6f, "the last hit deals 12: " + (hp[3] - hp[4]));
			helper.assertTrue(z.getDeltaMovement().horizontalDistance() > 1.0, "and knocks back, v " + z.getDeltaMovement());
			helper.assertTrue(Stands.cooldownRemaining(p, StandMove.PLATINUM_SMASH) == StandConfig.SMASH_COOLDOWN, "3 s cooldown after the last hit");
			float st = Stands.stamina(p);
			helper.assertTrue(st <= StandConfig.STAMINA_MAX - 4 * StandConfig.SMASH_COST + 2.5f && st >= StandConfig.STAMINA_MAX - 4 * StandConfig.SMASH_COST - 0.01f,
					"16 Stamina for the four (less the 3 a second refill): " + st);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void platinumSmashCoolsDownWhenLeftUnfinished(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 250);
		StarPlatinum.smash(p);
		helper.assertTrue(Stands.state(p).smashStep == 1, "one hit in");
		edit(p, n -> n.smashAt = helper.getLevel().getGameTime() - StandConfig.SMASH_RESET_TICKS - 1);
		StarPlatinum.tick(p);
		helper.assertTrue(Stands.state(p).smashStep == 0 && onCooldown(p, StandMove.PLATINUM_SMASH), "2 s with no R: the combo ends on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void holdingRIsTheOraBarrage(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 250);
		Vec3 start = z.position();
		StarPlatinum.holdR(p);
		StarPlatinum.tick(p);
		helper.assertTrue(Stands.state(p).rushing, "R held past " + StandConfig.HOLD_THRESHOLD + " ticks is the barrage");
		helper.onEachTick(() -> Stands.tick(p)); // (guarded: the server ticks mock players too)
		helper.runAfterDelay(20, () -> {
			float dealt = 250f - z.getHealth();
			helper.assertTrue(dealt >= StandConfig.BARRAGE_HIT_DAMAGE * 4 - 1f, "8 a punch, several punches: " + dealt);
			helper.assertTrue(z.position().distanceTo(start) < 0.3, "held in place: moved " + z.position().distanceTo(start));
			float st = Stands.stamina(p);
			helper.assertTrue(st < StandConfig.STAMINA_MAX - 2f && st > StandConfig.STAMINA_MAX - 5f, "about 3 a second: " + st);
			StarPlatinum.rReleased(p);
			helper.assertFalse(Stands.state(p).rushing, "letting go ends it");
			helper.assertTrue(z.getDeltaMovement().horizontalDistance() > 0.5, "and launches the victim: " + z.getDeltaMovement());
			helper.assertTrue(onCooldown(p, StandMove.ORA_BARRAGE), "on cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void starFingerStretchesThirtyBlocksInASecond(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 0.5);
		Zombie near = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.0, 60);
		Zombie far = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 7.0, 60);
		near.setNoGravity(true);
		far.setNoGravity(true);
		StarPlatinum.starFinger(p);
		helper.assertTrue(Math.abs(Stands.stamina(p) - (StandConfig.STAMINA_MAX - StandConfig.FINGER_COST)) < 0.01f, "15 Stamina");
		helper.assertTrue(onCooldown(p, StandMove.STAR_FINGER), "on cooldown");
		helper.onEachTick(() -> Stands.tick(p)); // (guarded: the server ticks mock players too)
		// (the test structure ends 7.5 blocks out: the fingers are timed over 6.5 blocks, 1.5 a tick)
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(near.getHealth() <= 60f - StandConfig.FINGER_DAMAGE + 0.6f, "the near one is run through: " + near.getHealth());
			helper.assertTrue(far.getHealth() >= 59.9f, "the far one not yet (it takes a second to get there): " + far.getHealth());
		});
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(far.getHealth() <= 60f - StandConfig.FINGER_DAMAGE + 0.6f, "6.5 blocks out, 22 damage: " + far.getHealth() + " end " + Stands.state(p).fingerEnd
					+ " far " + far.position() + " eye " + p.getEyePosition());
			helper.assertTrue(near.getHealth() >= 60f - StandConfig.FINGER_DAMAGE - 0.6f, "each one only once: " + near.getHealth());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G: Ora Chase, Shift+G: Ball Bearing Flick

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void oraChaseGrabsBarragesAndThrows(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 5.0, 100);
		StarPlatinum.oraChase(p);
		helper.assertTrue(Stands.state(p).chasing(), "G sends Star Platinum off");
		helper.assertTrue(Math.abs(Stands.stamina(p) - (StandConfig.STAMINA_MAX - StandConfig.CHASE_COST)) < 0.01f, "15 Stamina");
		helper.onEachTick(() -> Stands.tick(p)); // (guarded: the server ticks mock players too)
		double[] fastest = { 0.0 };
		helper.onEachTick(() -> fastest[0] = Math.max(fastest[0], z.getDeltaMovement().horizontalDistance()));
		helper.runAfterDelay(40, () -> {
			StandState s = Stands.state(p);
			helper.assertTrue(s.chaseGrabAt != 0L && s.chaseTarget == z.getId(), "it flew 3 b/s and grabbed the zombie");
			helper.assertTrue(z.getY() > p.getY() + 0.3, "lifted up by the neck: " + (z.getY() - p.getY()));
		});
		helper.runAfterDelay(110, () -> {
			helper.assertTrue(Stands.state(p).chaseStart == 0L, "the chase is over");
			float dealt = 100f - z.getHealth();
			helper.assertTrue(Math.abs(dealt - (StandConfig.CHASE_BARRAGE_DAMAGE + StandConfig.CHASE_THROW_DAMAGE)) < 1.5f,
					"15 over the barrage and 10 on the throw: " + dealt);
			// (a no-AI test mob never moves by itself: the throw is read off its speed -- 1.1 a tick is about 10 blocks)
			helper.assertTrue(fastest[0] >= StandConfig.CHASE_THROW_SPEED - 0.05, "thrown hard: " + fastest[0]);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void oraChaseComesBackFromNothing(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 0.5);
		p.setXRot(-20f); // up into the empty air
		StarPlatinum.oraChase(p);
		helper.onEachTick(() -> Stands.tick(p)); // (guarded: the server ticks mock players too)
		helper.runAfterDelay(30, () -> helper.assertTrue(Stands.state(p).chasing(), "still out at 30 ticks (4.5 blocks)"));
		helper.runAfterDelay(75, () -> {
			helper.assertFalse(Stands.state(p).chasing(), "10 blocks and nothing: it came back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void bearingFlickShootsABall(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 7.5, 40);
		p.setXRot(2f);
		StarPlatinum.flick(p);
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(com.projecthero.mod.stand.entity.BallBearingEntity.class, p.getBoundingBox().inflate(4)).isEmpty(),
				"a ball bearing is in the air");
		helper.assertTrue(onCooldown(p, StandMove.BEARING_FLICK), "on cooldown");
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(z.getHealth() <= 40f - StandConfig.BEARING_DAMAGE + 0.6f, "20 damage 7 blocks out: " + z.getHealth());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X: Stand Jump, Shift+X: Ora Rush

	@GameTest(template = EMPTY_STRUCTURE)
	public void standJumpThrowsYouAlongTheLook(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 2.5);
		StarPlatinum.standJump(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(Math.abs(v.horizontalDistance() - StandConfig.JUMP_SPEED) < 0.05 && v.y >= StandConfig.JUMP_MIN_UP - 0.01, "a 15-block jump: " + v);
		helper.assertTrue(Stands.cooldownRemaining(p, StandMove.STAND_JUMP) == StandConfig.JUMP_COOLDOWN, "2 s cooldown");
		helper.assertTrue(Stands.state(p).leapRebounds >= 0, "no fall damage until it lands");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void oraRushDashesPunching(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 100);
		StarPlatinum.oraRush(p);
		helper.assertTrue(Stands.state(p).dashUntil > helper.getLevel().getGameTime(), "Shift+X dashes");
		helper.assertTrue(p.getDeltaMovement().horizontalDistance() >= StandConfig.ORA_RUSH_SPEED - 0.01, "fast: " + p.getDeltaMovement());
		helper.assertTrue(Math.abs(Stands.stamina(p) - (StandConfig.STAMINA_MAX - StandConfig.ORA_RUSH_COST)) < 0.01f, "10 Stamina");
		helper.onEachTick(() -> Stands.tick(p)); // (guarded: the server ticks mock players too)
		helper.runAfterDelay(StandConfig.ORA_RUSH_TICKS + 2, () -> {
			helper.assertTrue(100f - z.getHealth() >= StandConfig.ORA_RUSH_HIT_DAMAGE - 0.5f, "ORA hits on the way: " + (100f - z.getHealth()));
			helper.assertTrue(Stands.state(p).dashUntil == 0L && onCooldown(p, StandMove.ORA_RUSH), "over, on cooldown");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z: Star Breaker

	@GameTest(template = EMPTY_STRUCTURE)
	public void starBreakerDealsThirty(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 60);
		StarPlatinum.startBreaker(p);
		helper.assertTrue(Stands.state(p).chargeStart != 0L, "Z starts the charge");
		edit(p, n -> n.chargeStart = helper.getLevel().getGameTime() - StandConfig.BREAKER_FULL_CHARGE);
		StarPlatinum.releaseBreaker(p);
		helper.assertTrue(Math.abs(60f - z.getHealth() - StandConfig.BREAKER_DAMAGE) < 0.6f, "30 damage: " + (60f - z.getHealth()));
		helper.assertTrue(z.getDeltaMovement().horizontalDistance() > 0.8, "and launches it");
		helper.assertTrue(Stands.state(p).chargeStart == 0L && onCooldown(p, StandMove.STAR_BREAKER), "released and on cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V: Stand Guard, Shift+V: Stand Grab

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void guardBlocksSixtyPercentWithNoTimeLimit(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.0, 40);
		StarPlatinum.startGuard(p);
		helper.assertTrue(Stands.state(p).guarding, "V raises the guard");
		helper.onEachTick(() -> Stands.tick(p)); // (guarded: the server ticks mock players too)
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(Stands.state(p).guarding && Stands.stamina(p) >= StandConfig.STAMINA_MAX - 0.01f, "still up, no Stamina spent");
			p.setHealth(20f);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().mobAttack(z), 10f);
			float guarded = 20f - p.getHealth();
			StarPlatinum.stopGuard(p);
			helper.assertTrue(Stands.cooldownRemaining(p, StandMove.GUARD) == StandConfig.GUARD_COOLDOWN, "6 s cooldown on release");
			p.setHealth(20f);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().mobAttack(z), 10f);
			float open = 20f - p.getHealth();
			float ratio = guarded / open;
			helper.assertTrue(open > 0f && ratio > 0.32f && ratio < 0.48f, "60% blocked: " + guarded + " vs " + open);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void standGrabHoldsThrowsSetsDownAndCombos(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 100);
		StarPlatinum.grabKey(p);
		helper.assertTrue(Stands.state(p).grabbedEntity == z.getId(), "Shift+V grabs");
		helper.onEachTick(() -> Stands.tick(p)); // (guarded: the server ticks mock players too)
		for (int i = 0; i < 5; i++) {
			int k = i;
			helper.runAfterDelay(5 + i * 6, () -> {
				StarPlatinum.rPressed(p, false);
				if (k < 4) {
					helper.assertTrue(Stands.state(p).grabbedEntity == z.getId(), "still held after combo hit " + (k + 1));
				}
			});
		}
		helper.runAfterDelay(36, () -> {
			helper.assertTrue(Math.abs(100f - z.getHealth() - StandConfig.GRAB_COMBO_DAMAGE) < 1f, "5 hits, 30 in all: " + (100f - z.getHealth()));
			helper.assertTrue(Stands.state(p).grabbedEntity == -1, "the fifth knocks it out of the hand");
			edit(p, n -> n.abilityReadyAt.clear());
			z.setDeltaMovement(Vec3.ZERO);
			z.moveTo(p.getX(), p.getY(), p.getZ() + 2.0);
			StarPlatinum.grabKey(p);
			helper.assertTrue(Stands.state(p).grabbedEntity == z.getId(), "grabbed again");
		});
		helper.runAfterDelay(42, () -> {
			float hp = z.getHealth();
			StarPlatinum.putDown(p);
			helper.assertTrue(Stands.state(p).grabbedEntity == -1 && z.getY() - p.getY() < 0.5 && z.getY() - p.getY() > -3.01 && z.getHealth() == hp
					&& z.getDeltaMovement().lengthSqr() < 1.0e-4, "Shift+V sets it down unhurt: dy " + (z.getY() - p.getY()) + " hp " + z.getHealth() + " vs " + hp + " held " + Stands.state(p).grabbedEntity);
			edit(p, n -> n.abilityReadyAt.clear());
			z.moveTo(p.getX(), p.getY(), p.getZ() + 2.0); // (the mock player floats above the floor it set the zombie on)
			StarPlatinum.grabKey(p);
		});
		helper.runAfterDelay(48, () -> {
			helper.assertTrue(Stands.state(p).grabbedEntity == z.getId(), "and again");
			StarPlatinum.hurl(p);
			helper.assertTrue(Stands.state(p).grabbedEntity == -1 && z.getDeltaMovement().length() > 1.5, "V throws it: " + z.getDeltaMovement());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C: Super Inhale, Shift+C: Uppercut

	@GameTest(template = EMPTY_STRUCTURE)
	public void inhalePullsMobsInOnAFourSecondCooldown(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 7.5, 40);
		z.setDeltaMovement(Vec3.ZERO);
		StarPlatinum.inhale(p);
		StarPlatinum.tick(p);
		helper.assertTrue(z.getDeltaMovement().z < -0.1, "the zombie is pulled toward the user, vz " + z.getDeltaMovement().z);
		helper.assertTrue(Stands.cooldownRemaining(p, StandMove.INHALE) == 80, "4 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void uppercutDealsTwentyTwo(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 60);
		StarPlatinum.uppercut(p);
		helper.assertTrue(Math.abs(60f - z.getHealth() - 22f) < 0.6f, "22 damage: " + (60f - z.getHealth()));
		helper.assertTrue(z.getDeltaMovement().y > 0.8, "it launches the target, vy " + z.getDeltaMovement().y);
		helper.assertTrue(onCooldown(p, StandMove.UPPERCUT), "on cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the body

	/** v0.17.2 (user: "mirror the users pose but do it behind their back"): idle it copies them from straight behind; a move takes over. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void idleStandFloatsDirectlyBehind(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 2.5);
		Vec3 back = StarPlatinum.restPoint(p).subtract(p.position());
		helper.assertTrue(back.z < -0.5 && Math.abs(back.x) < 0.01 && back.y > 0.2, "straight behind a player facing +Z, off the ground: " + back);
		StandState n = Stands.state(p).copy();
		n.animId = StandState.ANIM_NONE;
		p.setAttached(ModAttachments.STAND_STATE, n);
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(com.projecthero.mod.stand.StandClips.mirrors(n, 0f, now), "nothing running: it copies its user");
		n = Stands.state(p).copy();
		n.animId = StandState.ANIM_FINGER;
		helper.assertFalse(com.projecthero.mod.stand.StandClips.mirrors(n, 3f, now), "a move playing: it does not");
		helper.assertTrue(com.projecthero.mod.stand.StandClips.mirrors(n, 200f, now), "the move over: it copies again");
		n = Stands.state(p).copy();
		n.rushing = true;
		helper.assertFalse(com.projecthero.mod.stand.StandClips.mirrors(n, 200f, now), "a held move: it does not");
		helper.succeed();
	}

	/** v0.17.2 (user: "hands ... in his face or just in his head a lot of the time"): every frame of every move and held pose. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void handsStayClearOfTheHead(GameTestHelper helper) {
		java.util.List<String> bad = com.projecthero.mod.stand.StandClips.clearanceProblems();
		bad.forEach(line -> System.out.println("[STAND-POSE] " + line));
		helper.assertTrue(bad.isEmpty(), bad.size() + " frames with a fist in the head or face, first: " + (bad.isEmpty() ? "" : bad.get(0)));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the time stop

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "stand_time_stop")
	public void timeStopDrainsFliesAndHitsHarder(GameTestHelper helper) {
		TimeStop.clear();
		TimeStop.testRadius = 8.0;
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 60);
		helper.assertTrue(TimeStop.begin(p), "Shift+Z stops time");
		TimeStop.testRadius = 0.0;
		// v0.17.2: the voice line plays first; time stops on its burst
		helper.assertTrue(TimeStop.windingUp() && !TimeStop.active() && !TimeStop.frozen(z), "the voice line first, nothing frozen yet");
		helper.assertFalse(TimeStop.begin(p), "no second stop while the line plays");
		TimeStop.skipWindup(helper.getLevel().getServer());
		helper.assertTrue(TimeStop.active() && TimeStop.isCaster(p), "the stop is running, cast by the user");
		helper.assertTrue(TimeStop.frozen(z) && !TimeStop.frozen(p), "the zombie is frozen, the caster is not");
		helper.assertTrue(onCooldown(p, StandMove.THE_WORLD), "90 s cooldown");
		helper.assertTrue(p.getAbilities().mayfly && Math.abs(p.getAbilities().getFlyingSpeed() - StandConfig.TIME_STOP_FLY_SPEED) < 1.0e-4f,
				"the caster can fly at 10 b/s");
		int ticksBefore = z.tickCount;
		StarPlatinum.uppercut(p);
		helper.assertTrue(Math.abs(z.getHealth() - 60f) < 0.01f, "inside the stop the hit doesn't land yet");
		float queued = TimeStop.queued(z);
		helper.assertTrue(Math.abs(queued - StandConfig.UPPERCUT_DAMAGE * StandConfig.TIME_STOP_DAMAGE_BONUS) < 0.05f, "queued with +30%: " + queued);
		helper.assertTrue(Stands.stamina(p) > StandConfig.STAMINA_MAX - 1f, "moves are free inside your own stop");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(TimeStop.active(), "still stopped");
			helper.assertTrue(z.tickCount == ticksBefore, "the zombie hasn't ticked: " + z.tickCount + " vs " + ticksBefore);
			float st = Stands.stamina(p);
			helper.assertTrue(st < StandConfig.STAMINA_MAX - 3.5f && st > StandConfig.STAMINA_MAX - 6.5f, "10 Stamina a second: " + st);
			StarPlatinum.theWorld(p); // Shift+Z again
			helper.assertFalse(TimeStop.active(), "Shift+Z again: time resumes");
			helper.assertTrue(z.getHealth() <= 60f - queued + 0.6f, "the queued hit landed on resume, health " + z.getHealth());
			// (a mock player reports itself creative, so it keeps mayfly: the flying speed says the stop let go)
			helper.assertTrue(Math.abs(p.getAbilities().getFlyingSpeed() - 0.05f) < 1.0e-4f, "the 10 b/s flight goes with it");
			helper.assertTrue(Math.abs(Stands.regenRate(p) - StandConfig.STAMINA_REGEN * StandConfig.REGEN_SLOW_FACTOR) < 1.0e-4f, "half-speed refill for 20 s");
			helper.assertTrue(Stands.state(p).regenSlowUntil - helper.getLevel().getGameTime() >= StandConfig.REGEN_SLOW_TICKS - 1, "for 20 s");
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.tickCount > ticksBefore, "the zombie ticks again");
			helper.succeed();
		});
	}

	/** v0.17.3 (user: "make sure the entire world gets frozen ... there should be no limit to the range"): a zombie 100 blocks off freezes too. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "stand_time_stop_world")
	public void timeStopFreezesTheWholeLevelUntilTheBarRunsDry(GameTestHelper helper) {
		TimeStop.clear();
		TimeStop.testRadius = 0.0;
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		BlockPos farPos = helper.absolutePos(new BlockPos(2, 2, 102));
		net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(farPos);
		helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
		helper.getLevel().setBlockAndUpdate(farPos.below(), Blocks.STONE.defaultBlockState());
		Zombie far = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 102.5, 40);
		helper.runAfterDelay(2, () -> {
			Stands.setStamina(p, 3f + StandConfig.TIME_STOP_MIN_STAMINA);
			helper.assertTrue(TimeStop.begin(p), "time stops");
			TimeStop.skipWindup(helper.getLevel().getServer());
			helper.assertTrue(TimeStop.frozen(far), "100 blocks away, frozen");
		});
		int[] before = { 0 };
		helper.runAfterDelay(3, () -> before[0] = far.tickCount);
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(TimeStop.active(), "still stopped");
			helper.assertTrue(far.tickCount == before[0], "the far zombie hasn't ticked: " + far.tickCount + " vs " + before[0]);
		});
		helper.runAfterDelay(40, () -> {
			helper.assertFalse(TimeStop.active(), "13 Stamina at 10 a second: time resumed by itself");
			helper.assertTrue(Stands.stamina(p) <= 2.0f, "the bar ran dry (and refills at half speed since): " + Stands.stamina(p));
			helper.assertTrue(far.tickCount > before[0], "the far zombie ticks again");
			helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
			helper.succeed();
		});
	}
}

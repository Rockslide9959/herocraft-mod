package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadAbilities;
import com.projecthero.mod.undead.UndeadAbilityManager;
import com.projecthero.mod.undead.UndeadBloodMoves;
import com.projecthero.mod.undead.UndeadEject;
import com.projecthero.mod.undead.UndeadEjectConfig;
import com.projecthero.mod.undead.UndeadFlight;
import com.projecthero.mod.undead.UndeadLaunch;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Undead's ejection moves ({@link UndeadEject}): what comes off and where it is left, the flight's take-off and landing,
 * Blood Spray / Blood Whirlwind / Deadline, Eject Feet's single impulse and the Combustion Punch, and the X / G / C routing. Mock
 * players are not ticked by the server, so tests over time drive {@link Undead#tick}.
 */
public class UndeadEjectGameTests implements FabricGameTest {
	private static ServerPlayer undead(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f); // facing +z
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		p.invulnerableTime = 0;
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Undead.grant(p);
		return p;
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zo = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zo.setNoAi(true);
		zo.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		zo.setHealth(200);
		// (no burning in the gametest daylight: the health checks are the moves' alone; zombies' 2 armour still trims ~2%)
		zo.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0, false, false));
		return zo;
	}

	private static java.util.List<UndeadPartEntity> pieces(GameTestHelper helper, ServerPlayer p, int style) {
		return helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(24),
				e -> e.ownerIs(p) && e.style() == style);
	}

	private static void clearPieces(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(32), e -> e.ownerIs(p)).forEach(e -> e.discard());
	}

	private static boolean ejected(UndeadState s, int seg) {
		return (s.ex(UndeadState.X_EJECTED) & (1L << seg)) != 0L;
	}

	// ---------------------------------------------------------------- the shared ejection

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ejectingTheHandsTakesHalfTheForearmsAndTheyRegrowAfter(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadEject.hold(p, UndeadEject.Hold.SPRAY);
		helper.assertTrue(UndeadEject.eject(p, UndeadEject.HANDS, true) == 2, "both hands come off");
		UndeadState s = Undead.state(p);
		helper.assertTrue(s.broken(UndeadState.RIGHT_HAND) && s.broken(UndeadState.LEFT_HAND), "the hand segments are gone");
		helper.assertTrue(ejected(s, UndeadState.RIGHT_HAND) && ejected(s, UndeadState.LEFT_HAND), "marked as taking half the forearm with them");
		helper.assertTrue(!s.broken(UndeadState.RIGHT_ARM) && !s.broken(UndeadState.LEFT_ARM), "the arms stay on (cut at the forearm)");
		helper.assertTrue(s.kind(UndeadState.RIGHT_HAND) == UndeadState.KIND_LAUNCHED, "blown off, not burst");
		helper.assertTrue(UndeadEjectConfig.EJECT_ROWS > 6f && UndeadEjectConfig.EJECT_ROWS < 8f, "the cut is half-way down the forearm");
		helper.assertTrue(pieces(helper, p, UndeadPartEntity.STYLE_LEFT_PART).size() == 2, "the two pieces are left where they came off");
		helper.assertTrue(UndeadEject.eject(p, UndeadEject.HANDS, true) == 2 && pieces(helper, p, UndeadPartEntity.STYLE_LEFT_PART).size() == 2,
				"an end already off stays off (no second piece)");
		for (int i = 0; i < 10; i++) {
			Undead.tick(p);
		}
		helper.assertTrue(Undead.state(p).regrowStart(UndeadState.RIGHT_HAND) <= 0L, "nothing regrows while the move holds");
		UndeadEject.release(p, UndeadEject.Hold.SPRAY);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(com.projecthero.mod.undead.UndeadConfig.REGROW_DELAY + com.projecthero.mod.undead.UndeadConfig.REGROW_TICKS[UndeadState.RIGHT_HAND] + 8, () -> { // (after the 1.5 s regrowth delay)
			UndeadState n = Undead.state(p);
			helper.assertTrue(!n.broken(UndeadState.RIGHT_HAND) && !n.broken(UndeadState.LEFT_HAND), "the hands grow back once it lets go");
			helper.assertTrue(!ejected(n, UndeadState.RIGHT_HAND), "and the mark is forgotten");
			clearPieces(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ejectPiecesLiveTheirShortLife(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadEject.eject(p, UndeadEject.FEET, true);
		helper.assertTrue(pieces(helper, p, UndeadPartEntity.STYLE_LEFT_PART).size() == 2, "both feet pieces");
		helper.assertTrue(pieces(helper, p, UndeadPartEntity.STYLE_LEFT_PART).get(0).isLeftBehind(), "on the shared left-behind lifecycle");
		helper.runAfterDelay(UndeadEjectConfig.PIECE_LIFE + 4, () -> {
			helper.assertTrue(pieces(helper, p, UndeadPartEntity.STYLE_LEFT_PART).isEmpty(), "gone after their life");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the flight

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFlightEjectsHandsAndFeetAndGivesVanillaFlight(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadFlight.toggle(p); // the double-tap
		helper.assertTrue(UndeadFlight.isFlying(p), "takes off");
		UndeadState s = Undead.state(p);
		for (int seg = UndeadState.RIGHT_HAND; seg <= UndeadState.LEFT_FOOT; seg++) {
			helper.assertTrue(s.broken(seg) && ejected(s, seg), "end " + seg + " is off with half its limb");
		}
		for (int part = 0; part < UndeadState.PARTS; part++) {
			helper.assertTrue(!s.broken(part), "no whole part goes");
		}
		helper.assertTrue(p.getAbilities().mayfly && p.getAbilities().flying, "vanilla flight is granted (no kick for floating)");
		helper.assertTrue(pieces(helper, p, UndeadPartEntity.STYLE_LEFT_PART).size() == 4, "four pieces left at the take-off");
		Undead.tick(p);
		helper.assertTrue(Undead.state(p).regrowStart(UndeadState.RIGHT_FOOT) <= 0L, "nothing regrows mid-flight");
		UndeadFlight.toggle(p); // (inside the toggle debounce of the start: ignored)
		helper.assertTrue(UndeadFlight.isFlying(p), "one double-tap is one toggle");
		UndeadFlight.stop(p);
		helper.assertTrue(!UndeadFlight.isFlying(p) && !p.getAbilities().mayfly && !p.getAbilities().flying, "landing takes vanilla flight back");
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(80, () -> { // (1.5 s delay, then the feet)
			helper.assertTrue(!Undead.state(p).anyBroken(), "the hands and feet grow back after landing");
			clearPieces(helper, p);
			helper.succeed();
		});
	}

	// (v0.17.3: the flight's speeds and the right-click boost burn are UndeadFlightGameTests)

	// ---------------------------------------------------------------- G: Blood Spray

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bloodSprayHurtsWhatIsInTheStreamThenSlowsAndBlinds(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, 2, 7); // ~4.5 blocks ahead
		Zombie aside = zombie(helper, 6, 7); // well off to the side: outside the 2-block stream
		helper.assertTrue(UndeadBloodMoves.startSpray(p) && UndeadBloodMoves.spraying(p), "G sprays");
		helper.assertTrue(Undead.state(p).broken(UndeadState.RIGHT_HAND) && Undead.state(p).broken(UndeadState.LEFT_HAND), "both hands blown off");
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(UndeadEjectConfig.SPRAY_HIT_EVERY + 2, () -> {
			helper.assertTrue(z.getHealth() <= 200f - UndeadEjectConfig.SPRAY_DAMAGE * 0.95f, "a damage tick lands: " + z.getHealth());
			helper.assertTrue(aside.getHealth() >= 200f, "nothing outside the stream");
			helper.assertTrue(!z.hasEffect(MobEffects.BLINDNESS), "not blinded yet");
		});
		helper.runAfterDelay(UndeadEjectConfig.SPRAY_AFFLICT_AFTER + 4, () -> {
			helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && z.hasEffect(MobEffects.BLINDNESS), "sprayed past 1.5 s: slowed and blinded");
			UndeadAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
			helper.assertTrue(!UndeadBloodMoves.spraying(p), "letting go of G stops it");
			helper.assertTrue(Undead.cooldownRemaining(p, UndeadBloodMoves.SPRAY) == UndeadEjectConfig.SPRAY_COOLDOWN, "then the 6 s cooldown");
			clearPieces(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aWallStopsTheBloodSpray(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		for (int x = 0; x <= 4; x++) {
			for (int y = 2; y <= 5; y++) {
				helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE.defaultBlockState());
			}
		}
		Zombie z = zombie(helper, 2, 7);
		UndeadBloodMoves.startSpray(p);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(UndeadEjectConfig.SPRAY_HIT_EVERY * 2 + 2, () -> {
			helper.assertTrue(z.getHealth() >= 200f, "behind the wall: untouched");
			UndeadBloodMoves.stopSpray(p);
			clearPieces(helper, p);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Shift+G: Blood Whirlwind

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bloodWhirlwindHitsEverythingWithinTenBlocksOnce(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p = undeadAt(helper, p, new Vec3(3.5, 2.0, 3.5));
		final ServerPlayer u = p;
		Zombie front = zombie(helper, 3, 7), back = zombie(helper, 3, 0), left = zombie(helper, 7, 3); // (the test area is 8 x 8)
		helper.assertTrue(UndeadBloodMoves.startWhirl(u), "Shift+G starts the whirlwind");
		UndeadState s = Undead.state(u);
		helper.assertTrue(s.broken(UndeadState.RIGHT_HAND) && s.broken(UndeadState.LEFT_FOOT), "hands and feet blown off");
		helper.assertTrue(s.animId == UndeadState.ANIM_BLOOD_WHIRL, "the whirl pose plays for every viewer");
		helper.assertTrue(UndeadLaunch.fallImmune(u), "no fall damage while it holds him up");
		helper.onEachTick(() -> Undead.tick(u));
		helper.runAfterDelay(2, () -> helper.assertTrue(u.getDeltaMovement().y > 0.0, "the feet's blast lifts him: " + u.getDeltaMovement()));
		helper.runAfterDelay(UndeadEjectConfig.WHIRL_TICKS + 2, () -> {
			for (Zombie z : new Zombie[] { front, back, left }) {
				helper.assertTrue(z.getHealth() < 200f - UndeadEjectConfig.WHIRL_DAMAGE * 0.95f && z.getHealth() > 200f - UndeadEjectConfig.WHIRL_DAMAGE * 1.05f, "hit once for 25: " + z.getHealth());
			}
			helper.assertTrue(Undead.cooldownRemaining(u, UndeadBloodMoves.WHIRL) > 0, "on cooldown");
			helper.assertTrue(!UndeadEject.holding(u), "over: the ends may regrow");
			clearPieces(helper, u);
			helper.succeed();
		});
	}

	private static ServerPlayer undeadAt(GameTestHelper helper, ServerPlayer p, Vec3 rel) {
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		Undead.grant(p);
		return p;
	}

	// ---------------------------------------------------------------- tap X: Eject Feet

	/** A flat launch from the ground lands about 10 blocks away under vanilla drag and gravity (no held push, no hard stop). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ejectFeetIsOneImpulseThatCarriesAboutTenBlocks(GameTestHelper helper) {
		Vec3 v = UndeadLaunch.ejectFeetVelocity(new Vec3(0, 0, 1), true);
		// vanilla air movement: position += v, then gravity 0.08 / drag 0.98 vertically, 0.91 horizontally (ground friction on tick 0)
		double x = 0, y = 0, h = Math.sqrt(v.x * v.x + v.z * v.z), vy = v.y;
		for (int t = 0; t < 100; t++) {
			x += h;
			y += vy;
			if (y <= 0 && vy < 0) {
				break;
			}
			vy = (vy - 0.08) * 0.98;
			h *= t == 0 ? 0.546 : 0.91;
		}
		helper.assertTrue(x > 9.0 && x < 11.0, "lands ~10 blocks out: " + x);
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadLaunch.ejectFeet(p), "X tap launches");
		UndeadState s = Undead.state(p);
		helper.assertTrue(s.broken(UndeadState.RIGHT_FOOT) && s.broken(UndeadState.LEFT_FOOT) && ejected(s, UndeadState.LEFT_FOOT), "the feet and half the calves go");
		helper.assertTrue(!s.broken(UndeadState.RIGHT_LEG), "the legs stay");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadLaunch.EJECT_FEET) == UndeadEjectConfig.EJECT_FEET_COOLDOWN && UndeadEjectConfig.EJECT_FEET_COOLDOWN == 40,
				"a 2 s cooldown");
		helper.assertTrue(UndeadLaunch.fallImmune(p), "no fall damage for the landing");
		helper.assertTrue(p.getDeltaMovement().z > 0.5, "thrown along the look: " + p.getDeltaMovement());
		// the server never pushes again: what happens after the impulse is vanilla's
		p.setDeltaMovement(new Vec3(0, 0.1, 0.3));
		UndeadLaunch.tick(p);
		helper.assertTrue(p.getDeltaMovement().equals(new Vec3(0, 0.1, 0.3)), "no scripted dash, no carry override: " + p.getDeltaMovement());
		clearPieces(helper, p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+X: Combustion Punch

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void combustionPunchNeedsATargetAndCostsNothingWithout(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(!UndeadLaunch.combustionPunch(p), "nothing to lock onto");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadLaunch.COMBUSTION) == 0 && !Undead.state(p).anyBroken(), "no cost");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void combustionPunchFliesAtTheTargetAndPunches(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, 2, 7);
		helper.assertTrue(UndeadLaunch.combustionPunch(p), "locks on");
		UndeadState s = Undead.state(p);
		helper.assertTrue(!s.broken(UndeadState.HEAD) && !s.broken(UndeadState.RIGHT_ARM), "the head and the punching arm stay");
		helper.assertTrue(s.broken(UndeadState.LEFT_ARM) && s.broken(UndeadState.RIGHT_LEG) && s.broken(UndeadState.LEFT_LEG), "the rest goes");
		helper.assertTrue(ejected(s, UndeadState.BODY) && !s.broken(UndeadState.BODY), "the torso is cut to its top third");
		helper.assertTrue(pieces(helper, p, UndeadPartEntity.STYLE_LEFT_PART).stream().filter(e -> e.bodyPart() == UndeadState.BODY).count() == 1, "the lower torso is left standing");
		helper.assertTrue(pieces(helper, p, UndeadPartEntity.STYLE_LEFT_PART).stream().filter(e -> e.bodyPart() != UndeadState.BODY).count() == 3, "with the legs and the other arm");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadLaunch.COMBUSTION) > 0 && UndeadLaunch.launching(p), "flying at it");
		helper.assertTrue(p.getDeltaMovement().z > 1.0, "hurtling at the target: " + p.getDeltaMovement());
		// arrive (the mock player does not move itself)
		p.teleportTo(z.getX(), z.getY(), z.getZ() - 0.7);
		UndeadLaunch.tick(p);
		helper.assertTrue(z.getHealth() < 200f - UndeadEjectConfig.COMBUSTION_DAMAGE * 0.95f && z.getHealth() > 200f - UndeadEjectConfig.COMBUSTION_DAMAGE * 1.05f, "the punch hits for 22: " + z.getHealth());
		helper.assertTrue(z.getDeltaMovement().z > 0.3, "and knocks it back: " + z.getDeltaMovement());
		helper.assertTrue(Undead.state(p).animId == UndeadState.ANIM_COMBUSTION_PUNCH, "the punch pose");
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(UndeadEjectConfig.COMBUSTION_PUNCH_TICKS + com.projecthero.mod.undead.UndeadConfig.REGROW_DELAY + 4, () -> { // (after the 1.5 s delay)
			UndeadState n = Undead.state(p);
			helper.assertTrue(!UndeadLaunch.launching(p) && !ejected(n, UndeadState.BODY), "over");
			helper.assertTrue(n.broken(UndeadState.BODY) && n.regrowStart(UndeadState.BODY) > 0L, "the body regrows from the head");
			clearPieces(helper, p);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C: Deadline

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deadlineHurtsSlowsHimAndStopsAfterEightSeconds(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, 2, 7);
		helper.assertTrue(UndeadBloodMoves.startDeadline(p) && UndeadBloodMoves.deadlining(p), "C fires the Deadline");
		UndeadState s = Undead.state(p);
		helper.assertTrue(s.broken(UndeadState.RIGHT_HAND) && !s.broken(UndeadState.LEFT_HAND), "one hand blown off");
		var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(speed.getModifier(com.projecthero.mod.hero.power.PowerToggles.id("undead_deadline_slow")) != null, "he is slowed 80% while firing");
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(UndeadEjectConfig.DEADLINE_HIT_EVERY + 2, () -> {
			helper.assertTrue(z.getHealth() <= 200f - UndeadEjectConfig.DEADLINE_DAMAGE * 0.95f, "12 a damage tick: " + z.getHealth());
			// run it out to the 8 s
			UndeadState n = Undead.state(p).copy();
			n.holdStart -= UndeadEjectConfig.DEADLINE_MAX;
			p.setAttached(ModAttachments.UNDEAD_STATE, n);
		});
		helper.runAfterDelay(UndeadEjectConfig.DEADLINE_HIT_EVERY + 5, () -> {
			helper.assertTrue(!UndeadBloodMoves.deadlining(p), "stops on its own at 8 s");
			helper.assertTrue(Undead.cooldownRemaining(p, UndeadBloodMoves.DEADLINE) > UndeadEjectConfig.DEADLINE_COOLDOWN - 5, "then the 8 s cooldown");
			helper.assertTrue(speed.getModifier(com.projecthero.mod.hero.power.PowerToggles.id("undead_deadline_slow")) == null, "and his speed comes back");
			clearPieces(helper, p);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the keys

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void xTapEjectsTheFeetAndAHoldIsTheHeadThrow(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(!Undead.state(p).anyBroken(), "nothing on the press");
		UndeadAbilityManager.handle(p, AbilitySlot.SLOT_3, false);
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadLaunch.EJECT_FEET) > 0, "a tap is Eject Feet");
		ServerPlayer q = undeadAt(helper, helper.makeMockServerPlayerInLevel(), new Vec3(6.5, 2.0, 2.5));
		UndeadAbilityManager.handle(q, AbilitySlot.SLOT_3, true);
		UndeadAbilityManager.forTestsHoldX(q, UndeadAbilityManager.HEAD_THROW_HOLD);
		helper.runAfterDelay(30, () -> {
			UndeadAbilityManager.handle(q, AbilitySlot.SLOT_3, false);
			helper.assertTrue(Undead.cooldownRemaining(q, UndeadLaunch.EJECT_FEET) == 0, "a 2 s hold is the Head Throw, not Eject Feet");
			helper.assertTrue(UndeadAbilityManager.HEAD_THROW_HOLD == 40, "the hold is 2 s");
			clearPieces(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theKeysShowTheNewMoves(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(java.util.Arrays.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_2), new String[] { UndeadBloodMoves.SPRAY, UndeadBloodMoves.WHIRL }),
				"G: Blood Spray, Shift: Blood Whirlwind");
		helper.assertTrue(java.util.Arrays.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_3), new String[] { UndeadLaunch.EJECT_FEET, UndeadLaunch.COMBUSTION }),
				"X: Eject Feet, Shift: Combustion Punch");
		helper.assertTrue(UndeadBloodMoves.DEADLINE.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_6)[0]), "C: Deadline");
		helper.assertTrue(UndeadAbilityManager.maxCooldown(UndeadBloodMoves.SPRAY) == 120 && UndeadAbilityManager.maxCooldown(UndeadBloodMoves.DEADLINE) == 160
				&& UndeadAbilityManager.maxCooldown(UndeadBloodMoves.WHIRL) == UndeadEjectConfig.WHIRL_COOLDOWN
				&& UndeadAbilityManager.maxCooldown(UndeadLaunch.COMBUSTION) == UndeadEjectConfig.COMBUSTION_COOLDOWN, "the cooldowns");
		// G pressed and let go goes through the spray; C likewise through the Deadline
		UndeadAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(UndeadBloodMoves.spraying(p), "G held sprays");
		UndeadAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(!UndeadBloodMoves.spraying(p), "G let go stops");
		boolean old = true;
		try {
			UndeadAbilities.class.getMethod("startPropulsion", ServerPlayer.class);
		} catch (NoSuchMethodException e) {
			old = false;
		}
		helper.assertTrue(!old, "the old Shift + hold X flight is gone");
		clearPieces(helper, p);
		helper.succeed();
	}
}

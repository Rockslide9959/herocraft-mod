package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Ghostfreak revamp 2 (user: "make sure ghost freaks possession and his phasing works tho please"): phasing really goes
 * through walls, up through ceilings and down through floors (sneaking) and always ends in open space; possession steers a mob,
 * lets the host strike at +50%, a boss throws him out after 3 s, and a possessed PLAYER is let go cleanly -- after 10 s, on
 * death, on logout / dimension change, on his turning back -- with no burst unless he chose to leave; Fade truly hides him from
 * mobs; True Form's peel, burst, lash and wrap; Lights Out and Creature of the Dark; Tendril Snatch; Phantom Grasp through armour.
 * Mock players are not ticked by the server, so tests over time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixGhostfreakGameTests implements FabricGameTest {
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

	private static ServerPlayer ghost(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, GhostfreakKit.ID), "Ghostfreak is playable");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Ghostfreak");
		return p;
	}

	private static GhostfreakKit kit() {
		return (GhostfreakKit) Aliens.kit(GhostfreakKit.ID);
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	/** A stone floor 12 x 12 at y 1, and a roof at y 6 (no sunlight on him: the tests stay the same at noon and midnight). */
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 11; x++) {
			for (int z = 0; z <= 11; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 6, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, float health, boolean ai) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(!ai);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.05f;
	}

	/** {@code base} from him, or +20% if Creature of the Dark is on (the test world may be at night). */
	private static boolean ghostDmg(float got, float base) {
		return near(got, base) || near(got, base * GhostfreakKit.DARK_DAMAGE_FACTOR);
	}

	// ================================================================ phasing

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void ghostfreakPhasesThroughWallsCeilingsAndFloorsAndAlwaysEndsInOpenSpace(GameTestHelper helper) {
		ServerPlayer p = ghost(helper);
		GhostfreakKit kit = kit();
		// a wall two blocks thick across his way (+X), two high
		for (int x = 5; x <= 6; x++) {
			for (int y = 2; y <= 3; y++) {
				for (int z = 0; z <= 3; z++) {
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 3);
				}
			}
		}
		double x0 = p.getX();
		p.move(MoverType.SELF, new Vec3(4.0, 0, 0));
		helper.assertTrue(p.getX() < x0 + 1.3, "solid: the wall stops him: " + (p.getX() - x0));
		Vec3 start = helper.absoluteVec(new Vec3(3.5, 2, 1.5));
		p.moveTo(start.x, start.y, start.z, 0f, 0f);
		helper.assertTrue(kit.phase(p) && kit.phasing(p), "X: phased");
		p.move(MoverType.SELF, new Vec3(1.8, 0, 0));
		helper.assertFalse(helper.getLevel().noCollision(p, p.getBoundingBox()), "phased: he is INSIDE the wall now");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().inWall(), 1f);
		helper.assertTrue(p.getHealth() == hp, "and does not suffocate in it");
		p.move(MoverType.SELF, new Vec3(2.2, 0, 0));
		helper.assertTrue(Math.abs(p.getX() - (start.x + 4.0)) < 1.0e-3, "and out the other side: " + (p.getX() - start.x));
		// down through the floor only while sneaking
		double y0 = p.getY();
		p.move(MoverType.SELF, new Vec3(0, -1.2, 0));
		helper.assertTrue(Math.abs(p.getY() - y0) < 1.0e-3, "not sneaking: the floor holds him even phased");
		p.setShiftKeyDown(true);
		p.move(MoverType.SELF, new Vec3(0, -1.2, 0));
		p.setShiftKeyDown(false);
		helper.assertTrue(p.getY() < y0 - 1.1, "sneaking: he sinks into the floor: " + (p.getY() - y0));
		// ending the phase there pushes him out into open space (up, not into the void)
		kit.endPhase(p, true);
		helper.assertFalse(kit.phasing(p), "solid again");
		helper.assertTrue(helper.getLevel().noCollision(p, p.getBoundingBox()), "pushed out of the floor into open space: " + p.position());
		helper.assertTrue(p.getY() >= y0 - 0.01, "on top of it, not under it: " + (p.getY() - y0));
		helper.assertTrue(kit.cooldownLeft(p, GhostfreakKit.PHASE) > GhostfreakKit.PHASE_COOLDOWN - 10, "the cooldown starts when it ends");
		// up through the roof (rising while phased)
		kit.cooldown(p, GhostfreakKit.PHASE, 0);
		Vec3 under = helper.absoluteVec(new Vec3(8.5, 2, 1.5));
		p.moveTo(under.x, under.y, under.z, 0f, 0f);
		p.move(MoverType.SELF, new Vec3(0, 4.5, 0));
		helper.assertTrue(p.getY() < under.y + 2.3, "solid: the roof stops him");
		p.moveTo(under.x, under.y, under.z, 0f, 0f);
		helper.assertTrue(kit.phase(p), "phased again");
		p.move(MoverType.SELF, new Vec3(0, 3.4, 0));
		helper.assertTrue(Math.abs(p.getY() - (under.y + 3.4)) < 1.0e-3, "phased: rises INTO the roof");
		// the phase runs out while he is in the roof: settled outside, never stuck
		pump(helper, p);
		helper.runAfterDelay(GhostfreakKit.PHASE_TICKS + 3, () -> {
			helper.assertFalse(kit.phasing(p), "the phase ran out");
			helper.assertTrue(helper.getLevel().noCollision(p, p.getBoundingBox()), "and he was put in open space: " + p.position());
			// the Ecto Rush goes through floors too, and lands in the open
			kit.cooldown(p, GhostfreakKit.RUSH, 0);
			Vec3 at = helper.absoluteVec(new Vec3(8.5, 2, 4.5));
			p.moveTo(at.x, at.y, at.z, 0f, 60f); // looking down
			helper.assertTrue(kit.ectoRush(p), "Shift+X");
			p.move(MoverType.SELF, new Vec3(0, -1.6, 0));
			helper.assertTrue(p.getY() < at.y - 1.5, "the rush dives through the floor: " + (p.getY() - at.y));
			kit.endRush(p);
			helper.assertTrue(helper.getLevel().noCollision(p, p.getBoundingBox()), "and comes out in the open: " + p.position());
			// turning back mid-phase inside a wall: nothing stuck
			kit.cooldown(p, GhostfreakKit.PHASE, 0);
			Vec3 wall = helper.absoluteVec(new Vec3(5.5, 2, 1.5));
			p.moveTo(at.x, at.y, at.z, 0f, 0f);
			helper.assertTrue(kit.phase(p), "phased");
			p.moveTo(wall.x, wall.y, wall.z, 0f, 0f);
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertTrue(AlienKit.activeKit(p) == null, "human again");
			helper.assertTrue(helper.getLevel().noCollision(p, p.getBoundingBox()), "and not left inside the wall: " + p.position());
			helper.succeed();
		});
	}

	// ================================================================ possession: mobs

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void ghostfreakSteersAPossessedMobAndStrikesWithIt(GameTestHelper helper) {
		ServerPlayer p = ghost(helper);
		GhostfreakKit kit = kit();
		Zombie host = zombie(helper, 1.5, 2, 5.5, 60f, true);
		Zombie other = zombie(helper, 3.0, 2, 9.5, 60f, false);
		helper.assertTrue(kit.possess(p, host), "G: inside the zombie");
		helper.assertTrue(p.getVehicle() == host && GhostfreakKit.insideHost(p) && GhostfreakKit.unseen(p), "riding inside, unseen");
		host.setTarget(p);
		helper.assertTrue(host.getTarget() == null, "it can never target him");
		Vec3 from = host.position();
		p.setYRot(-90f); // facing +X
		p.setYHeadRot(-90f);
		helper.onEachTick(() -> {
			p.zza = 1.0f; // W held (the vanilla player-input packet sets this on a passenger)
			p.xxa = 0f;
			p.tickCount++;
			Omnitrix.tick(p);
		});
		helper.runAfterDelay(40, () -> {
			Vec3 moved = host.position().subtract(from);
			helper.assertTrue(moved.x > 1.5, "W walks it the way he faces (+X): " + moved);
			helper.assertTrue(Math.abs(moved.z) < 1.0, "and not off to the side: " + moved);
			helper.assertTrue(host.getTarget() == null, "it chases nothing of its own");
			// left-click: the host's own blow, +50%
			float hp = other.getHealth();
			other.invulnerableTime = 0;
			helper.assertTrue(kit.hostStrike(p, other), "the host strikes");
			float want = (float) host.getAttributeValue(Attributes.ATTACK_DAMAGE) * GhostfreakKit.HOST_DAMAGE_FACTOR;
			helper.assertTrue(near(hp - other.getHealth(), want), "+50% of its own damage (" + want + "), got " + (hp - other.getHealth()));
			helper.assertFalse(kit.hostStrike(p, other), "one blow at a time");
			float hostHp = host.getHealth();
			helper.assertTrue(kit.onAttack(p, host), "a left-click on the body he wears is taken over");
			helper.assertTrue(host.getHealth() == hostHp, "and never hurts the host");
			// out: G again bursts it, the host gets its own legs back
			kit.press(p, AbilitySlot.SLOT_2, false);
			helper.assertTrue(p.getVehicle() == null && GhostfreakKit.host(p) == null, "out");
			helper.assertTrue(host.getHealth() < hostHp, "burst through it");
			helper.assertFalse(GhostfreakKit.unseen(p), "seen again");
			host.discard();
			other.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void ghostfreakIsThrownOutOfABossAfter3s(GameTestHelper helper) {
		ServerPlayer p = ghost(helper);
		GhostfreakKit kit = kit();
		Zombie boss = zombie(helper, 5.5, 2, 3.5, 200f, false); // 150+ health: a boss
		helper.assertTrue(kit.possess(p, boss), "a boss can be possessed -- briefly");
		pump(helper, p);
		helper.runAfterDelay(GhostfreakKit.POSSESS_BOSS_TICKS - 10, () -> {
			helper.assertTrue(p.getVehicle() == boss, "still inside at 2.5 s");
			helper.runAfterDelay(14, () -> {
				helper.assertTrue(p.getVehicle() == null && GhostfreakKit.host(p) == null, "thrown out after 3 s");
				helper.assertTrue(boss.getHealth() == 200f, "quietly: no burst");
				helper.assertTrue(kit.cooldownLeft(p, GhostfreakKit.POSSESS) > GhostfreakKit.POSSESS_COOLDOWN - 20, "and the cooldown runs");
				boss.discard();
				helper.succeed();
			});
		});
	}

	// ================================================================ possession: players

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void ghostfreakPossessesAPlayerFor10sAndAlwaysLetsGo(GameTestHelper helper) {
		ServerPlayer p = ghost(helper);
		GhostfreakKit kit = kit();
		ServerPlayer victim = mock(helper, 5.5, 2, 1.5);
		Zombie z = zombie(helper, 5.5, 2, 4.5, 40f, false);
		helper.assertTrue(kit.possess(p, victim), "G on a player: possessed");
		helper.assertTrue(p.getVehicle() == victim && GhostfreakKit.possessorOf(victim) == p && GhostfreakKit.host(p) == victim, "inside them");
		helper.assertTrue(AttackEntityCallback.EVENT.invoker().interact(victim, victim.level(), InteractionHand.MAIN_HAND, z, null) == InteractionResult.FAIL,
				"the possessed player's own hands do nothing");
		helper.assertFalse(kit.possess(p, z), "one body at a time");
		float vhp = victim.getHealth();
		pump(helper, p);
		helper.runAfterDelay(GhostfreakKit.POSSESS_PLAYER_TICKS - 20, () -> {
			helper.assertTrue(GhostfreakKit.possessorOf(victim) == p, "still held at 9 s");
			helper.runAfterDelay(24, () -> {
				helper.assertTrue(GhostfreakKit.possessorOf(victim) == null && p.getVehicle() == null, "let go after 10 s");
				helper.assertTrue(victim.getHealth() == vhp, "quietly: no burst when the time runs out");
				helper.assertTrue(AttackEntityCallback.EVENT.invoker().interact(victim, victim.level(), InteractionHand.MAIN_HAND, z, null) == InteractionResult.PASS,
						"their hands are theirs again");
				// their death lets him go at once
				kit.cooldown(p, GhostfreakKit.POSSESS, 0);
				helper.assertTrue(kit.possess(p, victim), "again");
				ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(victim, victim.damageSources().generic());
				helper.assertTrue(GhostfreakKit.possessorOf(victim) == null && p.getVehicle() == null, "their death frees him");
				// logging out / changing dimension (the same release)
				kit.cooldown(p, GhostfreakKit.POSSESS, 0);
				helper.assertTrue(kit.possess(p, victim), "again");
				GhostfreakKit.releaseVictim(victim);
				helper.assertTrue(GhostfreakKit.possessorOf(victim) == null && p.getVehicle() == null, "their logout / dimension change frees him");
				// his choosing to leave (G again) bursts out through them
				kit.cooldown(p, GhostfreakKit.POSSESS, 0);
				helper.assertTrue(kit.possess(p, victim), "again");
				float before = victim.getHealth();
				kit.press(p, AbilitySlot.SLOT_2, false);
				helper.assertTrue(GhostfreakKit.possessorOf(victim) == null && p.getVehicle() == null, "G again: he bursts out of them");
				if (helper.getLevel().getServer().isPvpAllowed()) {
					helper.assertTrue(victim.getHealth() < before, "and the burst tears through them");
				}
				// his turning back while inside lets go too
				kit.cooldown(p, GhostfreakKit.POSSESS, 0);
				victim.setHealth(victim.getMaxHealth());
				helper.assertTrue(kit.possess(p, victim), "again");
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.assertTrue(GhostfreakKit.possessorOf(victim) == null && p.getVehicle() == null && victim.getPassengers().isEmpty(), "turning back frees them");
				z.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ghostfreakCannotPossessCreativePlayersOrSomeoneAlreadyHeld(GameTestHelper helper) {
		ServerPlayer p = ghost(helper);
		GhostfreakKit kit = kit();
		ServerPlayer creative = mock(helper, 5.5, 2, 1.5);
		creative.setGameMode(GameType.CREATIVE);
		helper.assertFalse(kit.possess(p, creative), "not a creative player");
		helper.assertTrue(kit.cooldownLeft(p, GhostfreakKit.POSSESS) == 0, "and no cooldown spent on it");
		helper.assertFalse(kit.possess(p, null), "nothing looked at");
		helper.succeed();
	}

	// ================================================================ Fade / Phantom Grasp / Tendril Snatch

	@GameTest(template = EMPTY_STRUCTURE)
	public void ghostfreakFadeIsTrulyUnseenByMobs(GameTestHelper helper) {
		ServerPlayer p = ghost(helper);
		GhostfreakKit kit = kit();
		Zombie z = zombie(helper, 5.5, 2, 3.5, 20f, true);
		Zombie boss = zombie(helper, 1.5, 2, 3.5, 200f, true);
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == p, "seen: it targets him");
		kit.press(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(kit.flag(p, GhostfreakKit.F_INVIS) && p.hasEffect(MobEffects.INVISIBILITY) && GhostfreakKit.unseen(p), "C: Invisibility");
		Omnitrix.tick(p);
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == null, "faded: no mob can target him");
		boss.setTarget(p);
		helper.assertTrue(boss.getTarget() == p, "a boss still senses him");
		kit.press(p, AbilitySlot.SLOT_6, false);
		helper.assertFalse(kit.flag(p, GhostfreakKit.F_INVIS) || p.hasEffect(MobEffects.INVISIBILITY), "C again: seen again");
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == p, "and targetable again");
		z.discard();
		boss.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ghostfreakPhantomGraspIgnoresArmourAndSnatchPullsIn(GameTestHelper helper) {
		ServerPlayer p = ghost(helper);
		GhostfreakKit kit = kit();
		Zombie armoured = zombie(helper, 3.5, 2, 3.5, 60f, false);
		armoured.getAttribute(Attributes.ARMOR).setBaseValue(20);
		float dealt = kit.phantomGrasp(p, armoured);
		helper.assertTrue(ghostDmg(dealt, GhostfreakKit.GRASP_DAMAGE), "Shift+G: 12 straight through 20 armour, got " + dealt);
		helper.assertTrue(armoured.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && armoured.hasEffect(MobEffects.DIG_SLOWDOWN), "slowness and mining fatigue");
		helper.assertTrue(kit.phantomGrasp(p, armoured) == 0f, "then it cools down");
		armoured.discard();
		// Tendril Snatch: 5 blocks out in front (+Z; the test area ends just past 7), yanked in
		Zombie far = zombie(helper, 3.5, 2, 6.5, 60f, false);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, far.position().add(0, 1.0, 0));
		p.setShiftKeyDown(true);
		helper.assertTrue(kit.moves().stream().anyMatch(m -> GhostfreakKit.SNATCH.equals(m.id()) && "Shift+Use (empty hand)".equals(m.keyLabel())),
				"the flight entry's place: Tendril Snatch on Shift+Use");
		helper.assertTrue(AlienKit.lookTarget(p, GhostfreakKit.SNATCH_RANGE) == far, "he looks right at it");
		InteractionResult r = kit.onUse(p, InteractionHand.MAIN_HAND);
		p.setShiftKeyDown(false);
		helper.assertTrue(r == InteractionResult.SUCCESS, "Shift+Use: snatched");
		helper.assertTrue(ghostDmg(60f - far.getHealth(), GhostfreakKit.SNATCH_DAMAGE), "6, got " + (60f - far.getHealth()));
		helper.assertTrue(GhostfreakKit.gripped(p, far) && far.getDeltaMovement().z < -0.3, "held, and yanked toward him: " + far.getDeltaMovement());
		far.discard();
		helper.succeed();
	}

	// ================================================================ True Form / Lights Out / Creature of the Dark

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ghostfreakTrueFormPeelsBurstsLashesAndWrapsBack(GameTestHelper helper) {
		ServerPlayer p = ghost(helper);
		GhostfreakKit kit = kit();
		Zombie z = zombie(helper, 3.5, 2, 4.5, 100f, false);
		kit.press(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(kit.flag(p, GhostfreakKit.F_TRUE) && !kit.trueFormOut(p), "Shift+Z: the skin starts to peel");
		helper.assertTrue(z.getHealth() == 100f, "nothing yet during the peel");
		pump(helper, p);
		helper.runAfterDelay(GhostfreakKit.PEEL_TICKS + 2, () -> {
			helper.assertTrue(kit.trueFormOut(p), "the true form is out");
			float burst = 100f - z.getHealth();
			helper.assertTrue(burst >= GhostfreakKit.TRUE_BURST - 0.05f, "the reveal's terror burst: " + burst);
			helper.assertTrue(GhostfreakKit.fleeing(p, z), "and it flees");
			// no skin: a blow does it all
			float hp = p.getHealth();
			p.invulnerableTime = 0;
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // other tests change it
			p.hurt(p.damageSources().mobAttack(z), 4f);
			float bare = hp - p.getHealth();
			helper.assertTrue(hp - p.getHealth() > 3.5f, "physical blows land in full now (less his 2 armour): " + (hp - p.getHealth()));
			helper.assertFalse(kit.fade(p), "the bare form cannot fade");
			helper.assertTrue(near(kit.clawDamage(p, false), kit.dmg(p, GhostfreakKit.CLAW_DAMAGE) * GhostfreakKit.TRUE_CLAW_FACTOR), "claws +30%");
			kit.cooldown(p, GhostfreakKit.TENTACLES, 0);
			kit.tentacles(p);
			helper.assertTrue(kit.cooldownLeft(p, GhostfreakKit.TENTACLES) <= GhostfreakKit.TENTACLE_COOLDOWN / 2, "Chest Tentacles at half cooldown");
			float before = z.getHealth();
			z.invulnerableTime = 0;
			helper.assertTrue(kit.lash(p, helper.getLevel()) >= 1 && ghostDmg(before - z.getHealth(), GhostfreakKit.LASH_DAMAGE), "the tentacles lash it");
			// the end: the skin wraps back and the 45 s start
			kit.setTimer(p, "true_end", helper.getLevel().getGameTime() + 1);
			helper.runAfterDelay(3, () -> {
				helper.assertFalse(kit.flag(p, GhostfreakKit.F_TRUE), "wrapped back after its time");
				helper.assertTrue(kit.cooldownLeft(p, GhostfreakKit.TRUE_FORM) > GhostfreakKit.TRUE_COOLDOWN - 10, "45 s cooldown from the end");
				float hp2 = p.getHealth();
				p.invulnerableTime = 0;
				helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
				p.hurt(p.damageSources().mobAttack(z), 4f);
				helper.assertTrue(Math.abs((hp2 - p.getHealth()) * 2f - bare) < 0.3f, "the skin halves blows again: " + (hp2 - p.getHealth()) + " vs " + bare);
				z.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void ghostfreakLightsOutDarkensAndCreatureOfTheDarkSpeedsCooldowns(GameTestHelper helper) {
		ServerPlayer p = ghost(helper);
		GhostfreakKit kit = kit();
		Zombie z = zombie(helper, 3.5, 2, 6.5, 20f, false);
		z.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0));
		helper.assertTrue(kit.lightsOut(p), "Shift+V: Lights Out");
		helper.assertTrue(z.hasEffect(MobEffects.DARKNESS) && !z.hasEffect(MobEffects.GLOWING), "darkness on it, its outline gone");
		helper.assertTrue(GhostfreakKit.inLightsOut(z) && !GhostfreakKit.inLightsOut(p), "it is in his dark (he is not hidden from himself)");
		helper.assertTrue(kit.darkHere(p) && kit.flag(p, GhostfreakKit.F_NIGHT), "Creature of the Dark is on");
		helper.assertTrue(near(kit.dmg(p, 10f), 12f), "+20% damage");
		kit.cooldown(p, GhostfreakKit.CLAW, 200);
		long t0 = helper.getLevel().getGameTime();
		pump(helper, p);
		helper.runAfterDelay(41, () -> {
			long elapsed = helper.getLevel().getGameTime() - t0;
			int left = kit.cooldownLeft(p, GhostfreakKit.CLAW);
			helper.assertTrue(left <= 200 - elapsed - 10, "cooldowns run faster in the dark: " + left + " left after " + elapsed);
			kit.setTimer(p, "dark_end", helper.getLevel().getGameTime() + 1);
			helper.runAfterDelay(3, () -> {
				helper.assertFalse(kit.flag(p, GhostfreakKit.F_DARK), "the dark lifts after its time");
				z.discard();
				helper.succeed();
			});
		});
	}
}

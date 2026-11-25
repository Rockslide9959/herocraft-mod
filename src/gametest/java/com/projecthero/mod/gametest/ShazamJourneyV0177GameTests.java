package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.shazam.journey.EternityRealm;
import com.projecthero.mod.shazam.journey.JourneyGeometry;
import com.projecthero.mod.shazam.journey.JourneyScene;
import com.projecthero.mod.shazam.journey.ShazamIllusions;
import com.projecthero.mod.shazam.journey.ShazamJourney;
import com.projecthero.mod.shazam.journey.SubwayTrainEntity;
import com.projecthero.mod.shazam.journey.ThroneWizardEntity;
import com.projecthero.mod.thoracquire.Valor;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition: who is worthy (full Valor and no powers -- a powered player never sees an illusion), an illusion
 * appearing ahead of the player (chance forced), walking up to it -> the descent -> the subway platform with the train arriving,
 * boarding the train -> the ride -> the cave with the Wizard on his throne, meeting him -> "say my name" (the chat path and the
 * button path) -> the power, the first transformation and the return home (turned back) to where the illusion first appeared,
 * and logging out mid-journey (the journey ends, the saved return point brings them home).
 *
 * <p>The GameTest server never loads datapack dimensions, so the realm is built in its overworld fallback ({@link EternityRealm},
 * high in the sky far away); the forced chance is a shared static, so its test runs in a batch of its own.
 */
public class ShazamJourneyV0177GameTests implements FabricGameTest {
	private static final String BATCH = "shazam_journey_v0177";
	private static final String CHANCE = "shazam_journey_chance_v0177";

	private static ServerPlayer player(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static void leave(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			ShazamJourney.onDisconnect(p);
			p.removeAttached(ShazamJourney.RETURN);
			if (!p.isRemoved()) {
				helper.getLevel().getServer().getPlayerList().remove(p);
			}
		}
	}

	/** Runs {@code steps} one after another, each once its condition holds; succeeds after the last. */
	private static final class Script {
		private final GameTestHelper helper;
		private final List<BooleanSupplier> when = new ArrayList<>();
		private final List<Runnable> then = new ArrayList<>();
		private int at;

		Script(GameTestHelper helper) {
			this.helper = helper;
		}

		Script step(BooleanSupplier condition, Runnable action) {
			when.add(condition);
			then.add(action);
			return this;
		}

		void run(Runnable cleanup) {
			helper.onEachTick(() -> {
				if (at >= when.size()) {
					return;
				}
				if (when.get(at).getAsBoolean()) {
					try {
						then.get(at).run();
					} catch (RuntimeException e) {
						cleanup.run();
						throw e;
					}
					at++;
					if (at >= when.size()) {
						cleanup.run();
						helper.succeed();
					}
				}
			});
		}
	}

	private static BlockPos origin(GameTestHelper helper, ServerPlayer p) {
		ServerLevel realm = EternityRealm.level(helper.getLevel().getServer());
		return EternityRealm.origin(realm, ShazamJourney.slot(p));
	}

	// ---------------------------------------------------------------- worthiness

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void onlyTheUnpoweredWithFullValorAreWorthy(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		ServerPlayer hero = player(helper, new Vec3(5.5, 2, 5.5));
		try {
			helper.assertFalse(ShazamIllusions.worthy(p), "no Valor: not worthy");
			Valor.set(p, Valor.MAX - 1);
			helper.assertFalse(ShazamIllusions.worthy(p), "99.9% Valor: not yet");
			Valor.set(p, Valor.MAX);
			helper.assertTrue(ShazamIllusions.worthy(p), "full Valor and no powers: worthy, illusions allowed");
			// a powered player never sees one, however much Valor
			Valor.set(hero, Valor.MAX);
			helper.assertTrue(Shazam.grant(hero), "a power for the hero");
			helper.assertTrue(HeroTiers.hasAnyPower(hero), "he has a power");
			helper.assertFalse(ShazamIllusions.worthy(hero), "powered: never worthy");
			helper.assertFalse(ShazamIllusions.canAppear(hero), "powered: no illusion can appear");
			// being worthy spends nothing
			helper.assertTrue(Valor.get(p) == Valor.MAX, "Valor is not spent by being worthy");
			helper.succeed();
		} finally {
			Shazam.revoke(hero);
			leave(helper, p, hero);
		}
	}

	// ---------------------------------------------------------------- the illusion

	@GameTest(template = EMPTY_STRUCTURE, batch = CHANCE, timeoutTicks = 60)
	public void anIllusionAppearsAheadWhenTheChanceComesUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// open ground 30 blocks over the test (the test box is walled in by barriers)
		BlockPos c = helper.absolutePos(new BlockPos(4, 30, 4));
		for (int x = -8; x <= 8; x++) {
			for (int z = -4; z <= 16; z++) {
				level.setBlock(c.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
			}
		}
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.moveTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 0f, 0f); // looking +z, down the platform
		try {
			Valor.set(p, Valor.MAX);
			long now = level.getGameTime();
			ShazamIllusions.chanceOverride = 0.0;
			helper.assertFalse(ShazamIllusions.roll(p, now), "chance 0: nothing");
			helper.assertTrue(ShazamIllusions.live(p) == null, "no illusion");
			helper.assertTrue(p.getAttachedOrElse(ShazamJourney.ILLUSION_READY, 0L) == now + ShazamIllusions.ROLL_EVERY, "the next roll in a minute");
			ShazamIllusions.chanceOverride = 1.0;
			helper.assertTrue(ShazamIllusions.roll(p, now), "chance forced: it appears");
			ShazamIllusions.Live l = ShazamIllusions.live(p);
			helper.assertTrue(l != null, "a live illusion");
			double d = Math.hypot(l.pos().x - p.getX(), l.pos().z - p.getZ());
			helper.assertTrue(d >= 6.5 && d <= 12.5, "a short way off: " + d);
			helper.assertTrue(l.pos().z > p.getZ() + 5, "ahead, in view");
			helper.assertTrue(Math.abs(l.pos().y - (c.getY() + 1)) < 0.01, "standing on the ground");
			helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(l.yaw())) < 40f, "descending away from the player");
			helper.assertTrue(p.getAttachedOrElse(ShazamJourney.ILLUSION_READY, 0L) == now + ShazamIllusions.COOLDOWN, "then a cooldown");
			helper.assertTrue(l.seenFrom() != null && Math.abs(l.seenFrom().z() - p.getZ()) < 1.0e-6, "it remembers where they stood");
			helper.succeed();
		} finally {
			ShazamIllusions.chanceOverride = null;
			ShazamIllusions.dismiss(p);
			leave(helper, p);
			for (int x = -8; x <= 8; x++) {
				for (int z = -4; z <= 16; z++) {
					level.setBlock(c.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the descent, the subway

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 300)
	public void walkingUpToTheIllusionTakesYouToTheSubway(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 1.5));
		Valor.set(p, Valor.MAX);
		Vec3 seen = p.position();
		Vec3 stairs = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
		ShazamIllusions.showAt(p, stairs, 0f);
		new Script(helper)
				// walk up to it (within 2 blocks of the top step)
				.step(() -> true, () -> p.moveTo(stairs.x, stairs.y, stairs.z - 0.8, 0f, 0f))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.DESCENT, () -> {
					helper.assertTrue(ShazamIllusions.live(p) == null, "the illusion is used up");
					var back = p.getAttachedOrElse(ShazamJourney.RETURN, null);
					helper.assertTrue(back != null && Math.abs(back.x() - seen.x) < 1.0e-6 && Math.abs(back.z() - seen.z) < 1.0e-6,
							"the return point is where they first saw it");
				})
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.TRAIN, () -> {
					BlockPos o = origin(helper, p);
					Vec3 platform = JourneyGeometry.platformArrival(o);
					helper.assertTrue(p.position().distanceTo(platform) < 0.6, "on the station platform: " + p.position() + " vs " + platform);
					helper.assertTrue(p.level().getBlockState(BlockPos.containing(platform).below()).isSolidRender(p.level(), BlockPos.containing(platform).below()),
							"standing on the platform");
				})
				.step(() -> ShazamJourney.train(p) != null, () -> {
					BlockPos o = origin(helper, p);
					SubwayTrainEntity train = ShazamJourney.train(p);
					helper.assertTrue(train.mode() == SubwayTrainEntity.ARRIVING, "the train is coming");
					helper.assertTrue(train.getX() < o.getX() - 60, "still deep in the tunnel");
					helper.assertTrue(ShazamJourney.stage(p) == ShazamJourney.Stage.TRAIN, "watching it come");
				})
				.run(() -> leave(helper, p));
	}

	// ---------------------------------------------------------------- the train, the cave

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 300)
	public void boardingTheTrainTakesYouToTheCave(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		Valor.set(p, Valor.MAX);
		new Script(helper)
				.step(() -> true, () -> helper.assertTrue(ShazamJourney.beginAt(p, helper.absoluteVec(new Vec3(4.5, 2, 6.5)), 0f), "the journey begins"))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.DESCENT, () -> ShazamJourney.fastForward(p, JourneyScene.DESCENT_GO))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.TRAIN && ShazamJourney.train(p) != null, () -> ShazamJourney.fastForward(p, JourneyScene.TRAIN.length))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.WAITING, () -> {
					SubwayTrainEntity train = ShazamJourney.train(p);
					BlockPos o = origin(helper, p);
					helper.assertTrue(train != null && Math.abs(train.getX() - (o.getX() + 0.5)) < 0.01, "stopped at the platform");
					helper.assertTrue(train.doorsOpen(), "doors open");
					// step into the front car's middle doorway
					p.moveTo(train.getX() + JourneyGeometry.CAR_OFFSET, o.getY() + 1, train.getZ() + 1.2, 0f, 0f);
				})
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.RIDE, () -> {
					helper.assertTrue(p.getVehicle() instanceof SubwayTrainEntity, "seated in the train");
					ShazamJourney.fastForward(p, 120);
				})
				.step(() -> true, () -> {
					SubwayTrainEntity train = ShazamJourney.train(p);
					BlockPos o = origin(helper, p);
					helper.assertTrue(train != null && train.getX() > o.getX() + 60, "away down the tunnel");
					helper.assertTrue(p.getVehicle() == train && Math.abs(p.getX() - (train.getX() + JourneyGeometry.SEAT_X)) < 0.01, "riding along");
					ShazamJourney.fastForward(p, JourneyGeometry.RIDE_TICKS);
				})
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.CAVE && ShazamJourney.wizard(p) != null, () -> {
					BlockPos o = origin(helper, p);
					helper.assertFalse(p.isPassenger(), "off the train");
					helper.assertTrue(p.position().distanceTo(JourneyGeometry.caveStepOff(o)) < 0.6, "stepped off in the cave");
					ThroneWizardEntity w = ShazamJourney.wizard(p);
					helper.assertTrue(w != null && w.position().distanceTo(JourneyGeometry.throneSeat(o)) < 0.1, "the Wizard sits on his throne");
					helper.assertTrue(w.sitAmount(0f) == 1f, "seated");
					SubwayTrainEntity train = ShazamJourney.train(p);
					helper.assertTrue(train != null && train.mode() == SubwayTrainEntity.CAVE
							&& Math.abs(train.getX() - (o.getX() + 0.5 + JourneyGeometry.rideEnd())) < 0.01, "the train stands at the end of the line");
				})
				.run(() -> leave(helper, p));
	}

	// ---------------------------------------------------------------- say his name

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 420)
	public void sayingHisNameGrantsThePowerAndTheFirstTransformationThenHome(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(3.5, 2, 3.5));
		Valor.set(p, Valor.MAX);
		Vec3 home = p.position();
		new Script(helper)
				.step(() -> true, () -> ShazamJourney.beginAt(p, helper.absoluteVec(new Vec3(4.5, 2, 6.5)), 0f))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.DESCENT, () -> ShazamJourney.fastForward(p, JourneyScene.DESCENT_GO))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.TRAIN && ShazamJourney.train(p) != null, () -> ShazamJourney.fastForward(p, JourneyScene.TRAIN.length))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.WAITING, () -> {
					SubwayTrainEntity train = ShazamJourney.train(p);
					helper.assertTrue(train != null, "the train");
					p.moveTo(train.getX() - JourneyGeometry.CAR_OFFSET + 5, train.getY() + 1, train.getZ() + 1.0, 0f, 0f);
				})
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.RIDE, () -> ShazamJourney.fastForward(p, JourneyGeometry.RIDE_TICKS))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.CAVE && ShazamJourney.wizard(p) != null, () -> {
					helper.assertFalse(Shazam.hasPower(p), "no power yet");
					// walk up to the throne
					ThroneWizardEntity w = ShazamJourney.wizard(p);
					p.moveTo(w.getX() - 5, w.getY() - 3, w.getZ(), -90f, 0f);
				})
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.MEETING, () -> {
					BlockPos o = origin(helper, p);
					helper.assertTrue(p.position().distanceTo(JourneyGeometry.meetSpot(o)) < 0.6, "standing before the throne");
					helper.assertFalse(ShazamJourney.chatAnswer(p, "Shazam"), "too early: he hasn't asked yet");
					ShazamJourney.fastForward(p, JourneyScene.MEETING.length);
				})
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.PROMPT, () -> {
					ThroneWizardEntity w = ShazamJourney.wizard(p);
					helper.assertTrue(w != null && w.sitAmount(0f) == 0f, "he has risen");
					helper.assertFalse(ShazamJourney.wouldAnswer(p, "shazam please"), "anything more than the word is just chat");
					helper.assertTrue(ShazamJourney.chatAnswer(p, "SHAZAM!"), "typing the word answers him");
					helper.assertTrue(ShazamJourney.stage(p) == ShazamJourney.Stage.TRANSFORM, "the transformation begins");
				})
				.step(() -> Shazam.hasPower(p), () -> {
					helper.assertTrue(Shazam.transformed(p), "transformed for the first time");
					helper.assertTrue(HeroTiers.hasAnyPower(p), "Shazam is his power now");
					helper.assertTrue(ShazamJourney.inJourney(p), "still in the cave");
				})
				.step(() -> !ShazamJourney.inJourney(p), () -> {
					helper.assertTrue(p.level() == helper.getLevel() && p.position().distanceTo(home) < 0.01, "home, where the illusion first appeared");
					helper.assertFalse(Shazam.transformed(p), "turned back on the way home");
					helper.assertTrue(Shazam.hasPower(p), "the power stays");
					helper.assertTrue(p.getAttachedOrElse(ShazamJourney.RETURN, null) == null, "the return point is cleared");
					helper.assertTrue(Valor.get(p) == Valor.MAX, "Valor wasn't spent");
					helper.assertFalse(ShazamIllusions.worthy(p), "and no more illusions");
				})
				.run(() -> {
					Shazam.revoke(p);
					leave(helper, p);
				});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 300)
	public void theButtonAnswersToo(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(5.5, 2, 2.5));
		Valor.set(p, Valor.MAX);
		new Script(helper)
				.step(() -> true, () -> ShazamJourney.beginAt(p, helper.absoluteVec(new Vec3(5.5, 2, 5.5)), 0f))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.DESCENT, () -> ShazamJourney.fastForward(p, JourneyScene.DESCENT_GO))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.TRAIN && ShazamJourney.train(p) != null, () -> ShazamJourney.fastForward(p, JourneyScene.TRAIN.length))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.WAITING, () -> {
					SubwayTrainEntity train = ShazamJourney.train(p);
					ShazamJourney.boardRequestForTest(p, train);
				})
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.RIDE, () -> ShazamJourney.fastForward(p, JourneyGeometry.RIDE_TICKS))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.CAVE && ShazamJourney.wizard(p) != null, () -> ShazamJourney.wizardClickedForTest(p))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.MEETING, () -> ShazamJourney.fastForward(p, JourneyScene.MEETING.length))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.PROMPT, () -> helper.assertTrue(ShazamJourney.answer(p), "the button's answer"))
				.step(() -> Shazam.hasPower(p) && Shazam.transformed(p), () -> helper.assertFalse(ShazamJourney.answer(p), "only once"))
				.run(() -> {
					Shazam.revoke(p);
					leave(helper, p);
				});
	}

	// ---------------------------------------------------------------- safety

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 300)
	public void loggingOutMidJourneySendsYouHomeNextLogin(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(6.5, 2, 6.5));
		Valor.set(p, Valor.MAX);
		Vec3 home = p.position();
		SubwayTrainEntity[] train = new SubwayTrainEntity[1];
		new Script(helper)
				.step(() -> true, () -> ShazamJourney.beginAt(p, helper.absoluteVec(new Vec3(6.5, 2, 3.5)), 180f))
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.DESCENT, () -> {
					helper.assertFalse(p.hurt(p.damageSources().generic(), 5f), "no damage during the journey");
					ShazamJourney.fastForward(p, JourneyScene.DESCENT_GO);
				})
				.step(() -> ShazamJourney.stage(p) == ShazamJourney.Stage.TRAIN, () -> {
					train[0] = ShazamJourney.train(p);
					helper.assertTrue(p.position().distanceTo(home) > 100, "far away in the realm");
					// the player logs out
					ShazamJourney.onDisconnect(p);
					helper.assertFalse(ShazamJourney.inJourney(p), "the journey ends");
					helper.assertTrue(train[0] == null || train[0].isRemoved(), "its train goes");
					helper.assertTrue(p.getAttachedOrElse(ShazamJourney.RETURN, null) != null, "the return point is kept");
					// ...and back in: the next sweep sends them home
					ShazamJourney.check(p);
				})
				.step(() -> true, () -> {
					helper.assertTrue(p.level() == helper.getLevel() && p.position().distanceTo(home) < 0.01, "home where it started");
					helper.assertTrue(p.getAttachedOrElse(ShazamJourney.RETURN, null) == null, "the return point is cleared");
					helper.assertTrue(ShazamIllusions.worthy(p), "still worthy: the next illusion can come");
				})
				.run(() -> leave(helper, p));
	}
}

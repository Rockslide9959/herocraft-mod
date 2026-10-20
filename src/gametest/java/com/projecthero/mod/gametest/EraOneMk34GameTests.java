package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.IronManComicRouter;
import com.projecthero.mod.ironman.ability.IronManComicState;
import com.projecthero.mod.ironman.ability.era1.EraOneMk3Moves;
import com.projecthero.mod.ironman.ability.era1.EraOneMk4Moves;
import com.projecthero.mod.ironman.ability.era1.IronManEraOne;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Era I (user): the Mk III Classic and Mk IV Hydro moves ({@code EraOneMk3Moves} / {@code EraOneMk4Moves}) --
 * one test per move checking what it does on the server and its number from the kit table.
 *
 * <p>Layout: a stone floor at relative y 1, the wearer standing on it at (3, 2, 1) facing +Z, targets in front (NoAI
 * husks with no armour and 200 HP, so every point of damage shows). Hostiles need {@code Difficulty.NORMAL}.
 */
public class EraOneMk34GameTests implements FabricGameTest {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };
	private static final float HP = 200f;
	private static JsonObject lang;

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer tony(GameTestHelper h, String suit) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		BlockPos at = h.absolutePos(new BlockPos(3, 2, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f); // facing +Z
		p.setXRot(0f);
		p.setYHeadRot(0f);
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(suit, TYPES[i])));
		}
		TonyStark.setActiveSuit(p, suit);
		IronManEnergy.setIntegrity(p, suit, IronManEnergy.maxIntegrity(suit));
		return p;
	}

	private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz) {
		T m = type.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180f, 0f);
		m.setNoAi(true);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HP);
		m.setHealth(HP);
		h.getLevel().addFreshEntity(m);
		return m;
	}

	private static LivingEntity husk(GameTestHelper h, ServerPlayer p, double dx, double dz) {
		return mob(h, EntityType.HUSK, p, dx, dz);
	}

	private static float lost(LivingEntity e) {
		return HP - e.getHealth();
	}

	private static float dmg(String moveId) {
		return IronManComicKit.byId(moveId).damage();
	}

	private static void near(GameTestHelper h, float got, float want, String what) {
		h.assertTrue(Math.abs(got - want) < 0.6f, what + ": want " + want + ", got " + got);
	}

	private static void atLeast(GameTestHelper h, float got, float want, String what) {
		h.assertTrue(got >= want - 0.6f, what + ": want at least " + want + ", got " + got);
	}

	private static IronManSuit mk3() {
		return IronManSuits.byId(EraOneMk3Moves.SUIT);
	}

	private static IronManSuit mk4() {
		return IronManSuits.byId(EraOneMk4Moves.SUIT);
	}

	/** Fire a move by id straight through the Era I registry (no cooldown check -- for a second use / a HOLD edge). */
	private static boolean edge(ServerPlayer p, IronManSuit suit, String id, boolean pressed) {
		return IronManEraOne.fire(p, suit, IronManComicKit.byId(id), pressed);
	}

	private static void water(GameTestHelper h, ServerPlayer p) {
		BlockPos feet = p.blockPosition();
		h.getLevel().setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
		h.getLevel().setBlockAndUpdate(feet.above(), Blocks.WATER.defaultBlockState());
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = EraOneMk34GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				throw new IllegalStateException("cannot read en_us.json", e);
			}
		}
		if (!lang.has(key)) {
			throw new IllegalStateException("missing lang key " + key);
		}
		return lang.get(key).getAsString();
	}

	// ------------------------------------------------------------------ the kits

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyMk34MoveHasAHandlerANameAndADescription(GameTestHelper h) {
		int n = 0;
		for (String suit : new String[] { EraOneMk3Moves.SUIT, EraOneMk4Moves.SUIT }) {
			IronManComicKit.Kit kit = IronManComicKit.kit(suit);
			for (IronManComicKit.Key key : kit.keys()) {
				for (IronManComicKit.Move m : new IronManComicKit.Move[] { key.tap(), key.shift() }) {
					if (m == null) {
						continue; // Shift+C = the eject
					}
					h.assertTrue(IronManEraOne.has(m.id()), m.id() + " has a handler");
					h.assertTrue(!t(m.nameKey()).isBlank() && t(m.descKey()).length() <= 140, m.id() + " has a short name + description");
					n++;
				}
			}
		}
		h.assertTrue(n == 22, "11 moves per suit, got " + n);
		h.succeed();
	}

	// ================================================================== MK III CLASSIC

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk3RepulsorHitsFourteen(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		LivingEntity t = husk(h, p, 0, 4);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 1, false), "the repulsor fires");
		near(h, lost(t), dmg("e1mk3_repulsor"), "Repulsor");
		h.assertTrue(dmg("e1mk3_repulsor") == 14f, "14 in the table");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk3TwinVolleyHitsTwiceTwelve(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		LivingEntity t = husk(h, p, 0, 4);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 1, true), "the volley fires");
		near(h, lost(t), 2 * dmg("e1mk3_twin_volley"), "Twin Volley (one bolt from each hand)");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void mk3UnibeamChargesToThirtySix(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		LivingEntity t = husk(h, p, 0, 5);
		float full = dmg("e1mk3_unibeam");
		// a tap (press + release at once) fires uncharged: a third
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 2, false), "the tapped Unibeam fires");
		near(h, lost(t), full / 3f, "uncharged Unibeam");
		h.assertFalse(edge(p, mk3(), "e1mk3_unibeam", true), "the press only starts the charge");
		h.runAfterDelay(EraOneMk3Moves.UNIBEAM_FULL_TICKS + 2, () -> {
			h.assertTrue(edge(p, mk3(), "e1mk3_unibeam", false), "the release fires");
			near(h, lost(t), full / 3f + full, "fully charged Unibeam (36)");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void mk3UnibeamSweepCrossesANinetyDegreeArc(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		LivingEntity left = husk(h, p, -3, 4);
		LivingEntity right = husk(h, p, 3, 4);
		LivingEntity side = husk(h, p, 4, 0); // 90 degrees off the look: outside the arc
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 2, true), "the sweep fires");
		h.runAfterDelay(EraOneMk3Moves.SWEEP_STEPS + 4, () -> {
			near(h, lost(left), dmg("e1mk3_unibeam_sweep"), "left of the arc, hit once");
			near(h, lost(right), dmg("e1mk3_unibeam_sweep"), "right of the arc, hit once");
			near(h, lost(side), 0f, "outside the 90-degree arc");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void mk3FlightBoostIsThreeSecondsAtOnePointEight(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 3, false), "the boost fires");
		h.assertTrue(IronManFlight.isFlying(p), "it takes off");
		long now = p.level().getGameTime();
		h.assertTrue(EraOneMk3Moves.flightBoostMultiplier(TonyStark.state(p), EraOneMk3Moves.SUIT, now) == 1.8, "x1.8 while it runs");
		h.assertTrue(EraOneMk3Moves.flightBoostMultiplier(TonyStark.state(p), "mark_4", now) == 1.0, "only the Mk III");
		h.runAfterDelay(EraOneMk3Moves.FLIGHT_BOOST_TICKS + 2, () -> {
			h.assertTrue(EraOneMk3Moves.flightBoostMultiplier(TonyStark.state(p), EraOneMk3Moves.SUIT, p.level().getGameTime()) == 1.0,
					"over after 3 s");
			IronManFlight.setFlying(p, false);
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void mk3BarrelRollDodgesSidewaysWithIFrames(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 3, true), "the roll fires");
		Vec3 v = p.getDeltaMovement();
		h.assertTrue(Math.abs(v.x) >= 1.3 && Math.abs(v.z) < 0.5, "a sideways launch, got " + v);
		h.assertTrue(EraOneMk3Moves.invulnerable(p), "i-frames on");
		float integ = IronManEnergy.integrity(p, EraOneMk3Moves.SUIT);
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().generic(), 10f), "a hit is dodged");
		h.assertTrue(IronManEnergy.integrity(p, EraOneMk3Moves.SUIT) == integ, "and the suit loses nothing");
		h.runAfterDelay(EraOneMk3Moves.ROLL_IFRAME_TICKS + 2, () -> {
			h.assertFalse(EraOneMk3Moves.invulnerable(p), "0.6 s later the i-frames are over");
			h.assertTrue(EraOneMk3Moves.incomingFactor(p) == 1f, "hits land again");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void mk3MiniMissilesLockThreeTargets(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		List<LivingEntity> targets = List.of(husk(h, p, -2.5, 5), husk(h, p, 0, 6), husk(h, p, 2.5, 5));
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 4, false), "the missiles fire");
		h.runAfterDelay(50, () -> {
			for (LivingEntity t : targets) {
				atLeast(h, lost(t), dmg("e1mk3_mini_missiles"), "each locked target");
			}
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void mk3MissileBarrageRainsOnTheCrosshair(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		LivingEntity t = husk(h, p, 0, 6);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 4, true), "the barrage fires");
		h.runAfterDelay(50, () -> {
			atLeast(h, lost(t), dmg("e1mk3_missile_barrage"), "the aimed target");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void mk3TractorBeamPullsAndLetsGo(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		net.minecraft.world.entity.animal.Cow cow = EntityType.COW.create(h.getLevel());
		cow.moveTo(p.getX(), p.getY(), p.getZ() + 6, 180f, 0f);
		h.getLevel().addFreshEntity(cow);
		h.assertFalse(edge(p, mk3(), "e1mk3_tractor_beam", true), "the press grabs (the cooldown waits for the release)");
		h.assertTrue(EraOneMk3Moves.tractorHeld(p) == cow, "the cow is on the beam");
		h.runAfterDelay(25, () -> {
			h.assertTrue(cow.distanceTo(p) < 5.0, "pulled in from 6 blocks, now " + cow.distanceTo(p));
			h.assertTrue(edge(p, mk3(), "e1mk3_tractor_beam", false), "the release lets go (and starts the cooldown)");
			h.assertTrue(EraOneMk3Moves.tractorHeld(p) == null, "nothing held");
			h.assertTrue(IronManComicKit.energyPerSecond("e1mk3_tractor_beam") == 15f, "15 energy a second in the table");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk3TractorThrowFlingsForSixteen(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		LivingEntity t = husk(h, p, 0, 3);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 5, true), "the throw fires");
		near(h, lost(t), dmg("e1mk3_tractor_throw"), "Tractor Throw");
		h.assertTrue(t.getDeltaMovement().z > 1.5, "flung along the look, got " + t.getDeltaMovement());
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void mk3TargetingHudMarksPrivatelyAndCrits(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk3Moves.SUIT);
		LivingEntity t = husk(h, p, 3, 3);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk3(), 6, false), "the HUD toggles on");
		h.assertTrue(EraOneMk3Moves.hudOn(p), "on");
		h.assertFalse(t.hasGlowingTag() || t.isCurrentlyGlowing(), "never a glow flag -- the marks are private");
		RandomSource r = RandomSource.create(42L);
		int crits = 0;
		for (int i = 0; i < 1000; i++) {
			float m = EraOneMk3Moves.critMultiplier(p, r);
			h.assertTrue(m == 1f || m == EraOneMk3Moves.HUD_CRIT_MULTIPLIER, "a hit is either plain or a crit");
			crits += m > 1f ? 1 : 0;
		}
		h.assertTrue(crits > 140 && crits < 260, "about 20% crits, got " + crits + " / 1000");
		h.runAfterDelay(3, () -> {
			h.assertTrue(EraOneMk3Moves.hudMarked(p) >= 1, "the husk is outlined");
			h.assertTrue(edge(p, mk3(), "e1mk3_targeting_hud", true), "toggles off");
			h.assertFalse(EraOneMk3Moves.hudOn(p), "off");
			h.assertTrue(EraOneMk3Moves.critMultiplier(p, RandomSource.create(1L)) == 1f, "no crits off");
			leave(h, p);
			h.succeed();
		});
	}

	// ================================================================== MK IV HYDRO

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void mk4TorpedoHitsThirty(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		LivingEntity t = husk(h, p, 0, 5);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 1, false), "the torpedo fires");
		h.runAfterDelay(30, () -> {
			atLeast(h, lost(t), dmg("e1mk4_torpedo"), "Torpedo");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void mk4HomingTorpedoFindsTheNearestTarget(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		LivingEntity t = husk(h, p, 3.5, 1.5); // off to the side, not under the crosshair
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 1, true), "the homing torpedo fires");
		h.runAfterDelay(40, () -> {
			atLeast(h, lost(t), dmg("e1mk4_homing_torpedo"), "Homing Torpedo");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk4SonarPingMarksThroughWalls(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		LivingEntity a = husk(h, p, 2, 3);
		BlockPos wall = p.blockPosition().south(2);
		h.getLevel().setBlockAndUpdate(wall.east(2), Blocks.STONE.defaultBlockState());
		h.getLevel().setBlockAndUpdate(wall.east(2).above(), Blocks.STONE.defaultBlockState());
		husk(h, p, -2, 4);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 2, false), "the ping fires");
		h.assertTrue(EraOneMk4Moves.lastPing(p) >= 2, "both husks outlined, got " + EraOneMk4Moves.lastPing(p));
		h.assertFalse(a.hasGlowingTag() || a.isCurrentlyGlowing(), "privately -- no glow flag");
		h.assertTrue(EraOneMk4Moves.PING_RADIUS == 48.0 && EraOneMk4Moves.PING_TICKS == 200, "48 blocks, 10 s");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk4SonicPulseStunsWaterMobsTwiceAsLong(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		LivingEntity land = husk(h, p, 2, 2);
		LivingEntity sea = mob(h, EntityType.GUARDIAN, p, -2, 2);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 2, true), "the pulse fires");
		near(h, lost(land), dmg("e1mk4_sonic_pulse"), "Sonic Pulse on the husk");
		near(h, lost(sea), dmg("e1mk4_sonic_pulse"), "Sonic Pulse on the guardian");
		MobEffectInstance a = land.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		MobEffectInstance b = sea.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		h.assertTrue(a != null && Math.abs(a.getDuration() - EraOneMk4Moves.PULSE_STUN_TICKS) <= 2, "a 2 s stun, got " + a);
		h.assertTrue(b != null && Math.abs(b.getDuration() - EraOneMk4Moves.PULSE_STUN_WATER_TICKS) <= 2, "a 4 s stun on a guardian, got " + b);
		h.assertTrue(EraOneMk4Moves.waterMob(sea) && !EraOneMk4Moves.waterMob(land), "water mobs told apart");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk4HydroSurgeIsThreeTimesStrongerInWater(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 3, false), "the surge fires");
		Vec3 land = p.getDeltaMovement();
		double landFlat = Math.sqrt(land.x * land.x + land.z * land.z);
		h.assertTrue(Math.abs(landFlat - EraOneMk4Moves.SURGE_SPEED) < 0.05 && land.z > 0, "a look-direction dash on land, got " + land);
		water(h, p);
		p.setDeltaMovement(Vec3.ZERO);
		h.assertTrue(EraOneMk4Moves.wet(p), "now in water");
		h.assertTrue(edge(p, mk4(), "e1mk4_hydro_surge", true), "the surge fires in water");
		double wet = p.getDeltaMovement().length();
		h.assertTrue(Math.abs(wet - EraOneMk4Moves.SURGE_SPEED * 3.0) < 0.05, "x3 in water, got " + wet);
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mk4BallastDropStompsOnLandAndSinksInWater(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		LivingEntity t = husk(h, p, 2, 0.5);
		LivingEntity far = husk(h, p, -3.5, 3.5);
		p.fallDistance = 6f;
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 3, true), "the ballast fires");
		near(h, lost(t), dmg("e1mk4_ballast_drop"), "the stomp in 4 blocks");
		near(h, lost(far), 0f, "outside 4 blocks");
		h.assertTrue(p.fallDistance == 0f, "fall-safe");
		water(h, p);
		h.assertTrue(edge(p, mk4(), "e1mk4_ballast_drop", true), "underwater it sinks");
		h.assertTrue(p.getDeltaMovement().y <= -1.4, "driven down fast, got " + p.getDeltaMovement());
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void mk4DepthChargeBlowsTwentyEight(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		LivingEntity t = husk(h, p, 0, 3);
		LivingEntity far = husk(h, p, 0, -3.5);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 4, false), "the charge is thrown");
		h.runAfterDelay(EraOneMk4Moves.CHARGE_FUSE_TICKS + 10, () -> {
			near(h, lost(t), dmg("e1mk4_depth_charge"), "Depth Charge");
			near(h, lost(far), 0f, "out of the 4-block blast");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void mk4MineFieldLaysThreeProximityMines(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 4, true), "the mines are laid");
		h.assertTrue(EraOneMk4Moves.mines(p) == 3, "three mines, got " + EraOneMk4Moves.mines(p));
		long life = EraOneMk4Moves.mineExpiry(p) - p.level().getGameTime();
		h.assertTrue(Math.abs(life - 60 * 20) <= 1, "they last 60 s, got " + life);
		Vec3 at = EraOneMk4Moves.minePositions(p).get(0);
		LivingEntity[] walker = new LivingEntity[1];
		h.runAfterDelay(EraOneMk4Moves.MINE_ARM_TICKS + 5, () -> {
			walker[0] = husk(h, p, 0, -2);
			walker[0].teleportTo(at.x, at.y, at.z);
		});
		h.runAfterDelay(EraOneMk4Moves.MINE_ARM_TICKS + 10, () -> {
			near(h, lost(walker[0]), dmg("e1mk4_mine_field"), "a mine goes off on contact");
			h.assertTrue(EraOneMk4Moves.mines(p) == 2, "and is used up");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void mk4WhirlpoolDealsThreeASecondForFourSeconds(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		LivingEntity t = husk(h, p, 0, 5);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 5, false), "the whirlpool opens");
		h.runAfterDelay(EraOneMk4Moves.WHIRLPOOL_TICKS + 8, () -> {
			near(h, lost(t), dmg("e1mk4_whirlpool") * 4, "3 a second for 4 s");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 240)
	public void mk4BubbleShieldKeepsYouBreathingAndMobsOut(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		water(h, p);
		LivingEntity t = husk(h, p, 2, 0);
		p.setAirSupply(10);
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 5, true), "the bubble goes up");
		h.runAfterDelay(3, () -> {
			h.assertTrue(EraOneMk4Moves.bubbleUp(p), "up");
			h.assertTrue(p.getAirSupply() >= p.getMaxAirSupply() - 5, "full air inside, got " + p.getAirSupply());
			h.assertTrue(t.getDeltaMovement().x > 0.2, "the husk is pushed out, got " + t.getDeltaMovement());
		});
		h.runAfterDelay(EraOneMk4Moves.BUBBLE_TICKS + 3, () -> {
			h.assertFalse(EraOneMk4Moves.bubbleUp(p), "gone after 8 s");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void mk4HeadlampsLightAndAlwaysCleanUp(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		BlockPos feet = p.blockPosition();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				h.getLevel().setBlockAndUpdate(feet.offset(dx, dy, 6), Blocks.STONE.defaultBlockState());
			}
		}
		h.assertTrue(IronManComicRouter.fireForTests(p, mk4(), 6, false), "the lamps switch on");
		List<BlockPos> lit = EraOneMk4Moves.lightPositions(p);
		BlockPos head = BlockPos.containing(p.getEyePosition());
		h.assertTrue(lit.contains(head) && h.getLevel().getBlockState(head).is(Blocks.LIGHT), "a light at the head");
		BlockPos spot = BlockPos.containing(p.getEyePosition()).south(5);
		h.assertTrue(lit.contains(spot) && h.getLevel().getBlockState(spot).is(Blocks.LIGHT), "a light where the beam meets the wall: " + lit);
		h.assertTrue(edge(p, mk4(), "e1mk4_headlamps", true), "the lamps switch off");
		h.assertTrue(EraOneMk4Moves.lightPositions(p).isEmpty() && h.getLevel().getBlockState(head).isAir()
				&& h.getLevel().getBlockState(spot).isAir(), "every light taken down");
		h.assertTrue(edge(p, mk4(), "e1mk4_headlamps", true), "on again");
		h.assertTrue(h.getLevel().getBlockState(head).is(Blocks.LIGHT), "lit again");
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); // the suit comes off
		h.runAfterDelay(3, () -> {
			h.assertFalse(EraOneMk4Moves.headlampsOn(p), "the lamps go out with the suit");
			h.assertTrue(h.getLevel().getBlockState(head).isAir() && h.getLevel().getBlockState(spot).isAir(), "no light left behind");
			leave(h, p);
			h.succeed();
		});
	}

	/** The comic damage hooks stay neutral for everyone else (no stray i-frames or crits). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void mk34HooksAreNeutralByDefault(GameTestHelper h) {
		ServerPlayer p = tony(h, EraOneMk4Moves.SUIT);
		LivingEntity t = husk(h, p, 0, 3);
		h.assertTrue(IronManComicState.outgoingMultiplier(p, t) == 1f, "no crit without the HUD");
		h.assertTrue(IronManComicState.incomingFactor(p, mk4(), h.getLevel().damageSources().generic()) == 1f, "no i-frames");
		leave(h, p);
		h.succeed();
	}
}

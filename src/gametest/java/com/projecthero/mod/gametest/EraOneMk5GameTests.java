package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManComicMk5Access;
import com.projecthero.mod.ironman.ability.IronManComicRouter;
import com.projecthero.mod.ironman.ability.IronManComicState;
import com.projecthero.mod.ironman.ability.era1.EraOneMk5Moves;
import com.projecthero.mod.ironman.ability.era1.EraOneMk5Stealth;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ultron.UltronEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Iron Man Era I, Mk V Stealth: one test per move, each checking its server effect -- the dart's poison +
 * slowness, the sleeper dart's sleep (no target, a hit wakes it, bosses resist), the takedown's x3 from behind / unaware,
 * the shadow strike landing behind, the blink, Active Camo (v0.16.7 (user), replaced the glide), the EMP's 3 s / 6 s shutdown, the decoy pulling a mob off,
 * radar, thermal vision and Silent Running (no target until an attack breaks it).
 */
public class EraOneMk5GameTests implements FabricGameTest {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };
	private static final int R = 1, G = 2, X = 3, Z = 4, V = 5, C = 6;

	private static ServerPlayer tony(GameTestHelper h) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor("mark_v", TYPES[i])));
		}
		TonyStark.setActiveSuit(p, "mark_v");
		IronManEnergy.setIntegrity(p, "mark_v", IronManEnergy.maxIntegrity("mark_v"));
		BlockPos at = h.absolutePos(new BlockPos(1, 2, 4)); // inside the 8x8x8 empty test cage, mid-z
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(-90f); // facing +x
		p.setXRot(0f);
		return p;
	}

	private static boolean fire(ServerPlayer p, int slot, boolean shift) {
		return IronManComicRouter.fireForTests(p, IronManSuits.MARK_V, slot, shift);
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		EraOneMk5Stealth.shutDownForTests(p);
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	/** A mob {@code dx} blocks along +x from the player, at the player's height, with {@code hp} health. */
	private static <T extends Mob> T mob(GameTestHelper h, ServerPlayer p, EntityType<T> type, double dx, float hp, boolean noAi) {
		T m = type.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ(), 0f, 0f);
		m.setNoAi(noAi);
		m.setPersistenceRequired();
		if (hp > 0f) {
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
			m.setHealth(hp);
		}
		h.getLevel().addFreshEntity(m);
		return m;
	}

	private static void face(Mob m, float yaw) {
		m.setYRot(yaw);
		m.setYBodyRot(yaw);
		m.setYHeadRot(yaw);
		m.yBodyRotO = yaw;
	}

	private static void lookAt(ServerPlayer p, Mob m) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, IronManComicMk5Access.centre(m));
	}

	// ------------------------------------------------------------------ R

	@GameTest(template = EMPTY_STRUCTURE)
	public void silencedDartPoisonsAndSlows(GameTestHelper h) {
		ServerPlayer p = tony(h);
		Mob v = mob(h, p, EntityType.VINDICATOR, 4.0, 60f, true);
		lookAt(p, v);
		h.assertTrue(fire(p, R, false), "the dart fires");
		h.assertTrue(v.getHealth() <= 60f - 5f && v.getHealth() >= 60f - 7f, "6 damage, got " + (60f - v.getHealth()));
		MobEffectInstance poison = v.getEffect(MobEffects.POISON);
		MobEffectInstance slow = v.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		h.assertTrue(poison != null && poison.getAmplifier() == 1 && poison.getDuration() > 60, "Poison II for 4 s");
		h.assertTrue(slow != null && slow.getAmplifier() == 1 && slow.getDuration() > 60, "Slowness II for 4 s");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sleeperDartSleepsUntilHitAndBossesResist(GameTestHelper h) {
		ServerPlayer p = tony(h);
		Mob v = mob(h, p, EntityType.VINDICATOR, 4.0, 0f, false);
		lookAt(p, v);
		h.assertTrue(fire(p, R, true), "the sleeper dart fires");
		h.assertTrue(EraOneMk5Stealth.asleep(v), "the mob is asleep");
		v.setTarget(p);
		h.assertTrue(v.getTarget() == null, "a sleeping mob takes no target");
		AbilityHelpers.hurtBurst(p, v, 1f);
		h.assertTrue(!EraOneMk5Stealth.asleep(v), "any hit wakes it");
		v.discard();
		// a boss shrugs it off
		ServerPlayer q = tony(h);
		Mob boss = mob(h, q, EntityType.VINDICATOR, 4.0, 2000f, true);
		lookAt(q, boss);
		fire(q, R, true);
		h.assertTrue(!EraOneMk5Stealth.asleep(boss) && EraOneMk5Moves.boss(boss), "bosses never sleep");
		leave(h, p);
		leave(h, q);
		h.succeed();
	}

	// ------------------------------------------------------------------ G

	@GameTest(template = EMPTY_STRUCTURE)
	public void silentTakedownTriplesFromBehindOrUnaware(GameTestHelper h) {
		ServerPlayer p = tony(h);
		// aware (on the player) and facing him: the plain 15
		Mob aware = mob(h, p, EntityType.VINDICATOR, 2.0, 100f, true);
		face(aware, 90f); // facing -x, at the player
		aware.setTarget(p);
		lookAt(p, aware);
		h.assertTrue(fire(p, G, false), "the takedown lands");
		float plain = 100f - aware.getHealth();
		h.assertTrue(plain >= 13f && plain <= 17f, "15 on an aware mob facing you, got " + plain);
		aware.discard();
		// unaware and facing away: x3
		ServerPlayer q = tony(h);
		Mob prey = mob(h, q, EntityType.VINDICATOR, 2.0, 100f, true);
		face(prey, -90f); // facing +x, away from the player
		lookAt(q, prey);
		h.assertTrue(EraOneMk5Moves.behind(q, prey), "the player is behind it");
		h.assertTrue(fire(q, G, false), "the silent takedown lands");
		float silent = 100f - prey.getHealth();
		h.assertTrue(silent >= 40f && silent <= 50f, "45 from behind, got " + silent);
		leave(h, p);
		leave(h, q);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowStrikeBlinksBehindAndStrikes(GameTestHelper h) {
		ServerPlayer p = tony(h);
		Mob v = mob(h, p, EntityType.VINDICATOR, 3.5, 100f, true);
		face(v, 90f); // facing the player
		lookAt(p, v);
		h.assertTrue(!EraOneMk5Moves.behind(p, v), "starts in front of it");
		h.assertTrue(IronManComicMk5Access.target(p, EraOneMk5Moves.SHADOW_RANGE) == v, "it is the aimed target");
		h.assertTrue(fire(p, G, true), "the shadow strike goes off (room behind it)");
		h.assertTrue(EraOneMk5Moves.behind(p, v), "now behind it: p " + p.position() + " v " + v.position() + " body " + v.yBodyRot);
		h.assertTrue(p.distanceTo(v) < 3.0, "right at its back, " + p.distanceTo(v));
		h.assertTrue(100f - v.getHealth() >= 40f, "the x3 takedown blow, got " + (100f - v.getHealth()));
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ X

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightBendBlinkDashesInvisible(GameTestHelper h) {
		ServerPlayer p = tony(h);
		Vec3 was = p.position();
		h.assertTrue(fire(p, X, false), "the blink goes off");
		double moved = p.position().distanceTo(was);
		h.assertTrue(moved >= 4.0 && moved <= 8.6, "dashed up to 8 blocks, moved " + moved);
		h.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "invisible after the blink");
		leave(h, p);
		h.succeed();
	}

	/** v0.16.7 (user): Shift+X Active Camo -- invisible suit and all, mobs lose you, 3/s, a hit breaks it, never browned out. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void activeCamoHidesTheSuitDrainsAndBreaksOnHits(GameTestHelper h) {
		ServerPlayer p = tony(h);
		com.projecthero.mod.ironman.ability.IronManComicKit.Move camo = com.projecthero.mod.ironman.ability.IronManComicKit.byId("e1mk5_active_camo");
		h.assertTrue(camo != null && com.projecthero.mod.ironman.ability.IronManComicKit.byId("e1mk5_silent_glide") == null,
				"Shift+X is Active Camo now (the glide is gone)");
		h.assertTrue(Math.abs(com.projecthero.mod.ironman.ability.IronManComicKit.energyPerSecond("e1mk5_active_camo") - 3f) < 1e-4f,
				"it costs 3 energy a second");
		Mob zombie = mob(h, p, EntityType.ZOMBIE, 4.0, 60f, true);
		zombie.setTarget(p);
		IronManEnergy.setEnergy(p, "mark_v", 500f);
		h.assertTrue(fire(p, X, true), "Active Camo on");
		h.assertTrue(EraOneMk5Stealth.camo(p) && EraOneMk5Stealth.camoHidden(p), "camo running");
		h.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "the wearer is invisible (the shared flag follows on his next tick)");
		// an invisible Iron Man's armour layers / GeckoLib suit are not drawn for anyone (HumanoidArmorLayerMixin)
		h.assertTrue(com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(p), "the hidden-armour rule applies to this wearer");
		h.assertTrue(zombie.getTarget() == null, "a mob already on you loses you");
		zombie.setTarget(p);
		h.assertTrue(zombie.getTarget() == null, "nothing can pick you while it holds");
		float before = IronManEnergy.energy(p, "mark_v");
		for (int t = 0; t < 20; t++) {
			EraOneMk5Stealth.tickForTests(p);
		}
		float spent = before - IronManEnergy.energy(p, "mark_v");
		h.assertTrue(Math.abs(spent - 3f) < 0.05f, "drains 3 energy a second, spent " + spent);
		h.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "still invisible after a second");
		// a hit given breaks it for 5 s
		AbilityHelpers.hurtBurst(p, zombie, 1f);
		h.assertTrue(EraOneMk5Stealth.camoBroken(p), "attacking breaks it");
		h.assertTrue(!p.hasEffect(MobEffects.INVISIBILITY), "visible while broken");
		zombie.setTarget(p);
		h.assertTrue(zombie.getTarget() == p, "broken: mobs can target you again");
		// a second press switches it off (straight through the handler: the toggle's 1 s cooldown is running)
		h.assertTrue(com.projecthero.mod.ironman.ability.era1.IronManEraOne.fire(p, IronManSuits.MARK_V, camo, true), "switched off");
		h.assertTrue(!EraOneMk5Stealth.camo(p) && !p.hasEffect(MobEffects.INVISIBILITY), "camo off, visible");
		// a hit taken breaks it too
		h.assertTrue(com.projecthero.mod.ironman.ability.era1.IronManEraOne.fire(p, IronManSuits.MARK_V, camo, true), "on again");
		EraOneMk5Stealth.damagedForTests(p, zombie); // the AFTER_DAMAGE hook (mock players never fire it themselves)
		h.assertTrue(EraOneMk5Stealth.camoBroken(p), "taking a hit breaks it");
		// a dry reactor ends it; a browned-out suit cannot switch it on
		IronManEnergy.setEnergy(p, "mark_v", 0.05f);
		EraOneMk5Stealth.tickForTests(p);
		h.assertTrue(!EraOneMk5Stealth.camo(p) && !p.hasEffect(MobEffects.INVISIBILITY), "running dry ends it");
		h.assertTrue(IronManEnergy.brownedOut(p, "mark_v"), "browned out");
		h.assertFalse(fire(p, X, true) && EraOneMk5Stealth.camo(p), "no camo while browned out");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void empShutsMobsThreeSecondsAndMachinesSix(GameTestHelper h) {
		ServerPlayer p = tony(h);
		Mob zombie = mob(h, p, EntityType.ZOMBIE, 3.0, 0f, false);
		Mob drone = mob(h, p, UltronEntityTypes.SENTRY, -3.0, 40f, false);
		h.assertTrue(EraOneMk5Moves.machine(drone) && !EraOneMk5Moves.machine(zombie), "the Ultron sentry is a machine");
		h.assertTrue(fire(p, Z, false), "the EMP goes off");
		h.assertTrue(EraOneMk5Stealth.frozen(zombie) && EraOneMk5Stealth.frozen(drone), "both shut down");
		zombie.setTarget(p);
		h.assertTrue(zombie.getTarget() == null, "a shut-down mob takes no target");
		h.runAfterDelay(80, () -> {
			h.assertTrue(!EraOneMk5Stealth.frozen(zombie), "the zombie is back after 3 s");
			h.assertTrue(EraOneMk5Stealth.frozen(drone), "the machine is still out at 4 s");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void decoyPullsMobsOffTheWearer(GameTestHelper h) {
		ServerPlayer p = tony(h);
		Mob zombie = mob(h, p, EntityType.ZOMBIE, 4.0, 0f, true);
		zombie.setTarget(p);
		h.assertTrue(fire(p, Z, true), "the decoy goes up");
		h.assertTrue(IronManComicMk5Access.decoyAt(p) != null, "a decoy stands");
		IronManComicState.tickServerForTests(h.getLevel().getServer());
		h.assertTrue(zombie.getTarget() != p, "the mob left the wearer for the decoy");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ V

	@GameTest(template = EMPTY_STRUCTURE)
	public void radarAndThermalVisionArePrivateScans(GameTestHelper h) {
		ServerPlayer p = tony(h);
		Mob zombie = mob(h, p, EntityType.ZOMBIE, 6.0, 0f, true);
		h.assertTrue(fire(p, V, false), "radar pings");
		h.assertTrue(!zombie.isCurrentlyGlowing() && !zombie.hasGlowingTag(), "nothing glows for anyone else");
		h.assertTrue(fire(p, V, true), "thermal vision comes on");
		h.assertTrue(EraOneMk5Stealth.thermal(p), "thermal vision running");
		EraOneMk5Stealth.tickForTests(p);
		h.assertTrue(!zombie.hasGlowingTag(), "still no glow flag on the mob");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ C

	@GameTest(template = EMPTY_STRUCTURE)
	public void silentRunningHidesUntilYouAttack(GameTestHelper h) {
		ServerPlayer p = tony(h);
		Mob zombie = mob(h, p, EntityType.ZOMBIE, 4.0, 60f, true);
		zombie.setTarget(p);
		h.assertTrue(fire(p, C, false), "Silent Running on");
		h.assertTrue(zombie.getTarget() == null, "a mob already on you loses you");
		zombie.setTarget(p);
		h.assertTrue(zombie.getTarget() == null, "nothing can pick you while it runs");
		AbilityHelpers.hurtBurst(p, zombie, 1f);
		h.assertTrue(EraOneMk5Stealth.runningBroken(p), "attacking breaks it");
		zombie.setTarget(p);
		h.assertTrue(zombie.getTarget() == p, "broken: mobs can target you again");
		leave(h, p);
		h.succeed();
	}
}

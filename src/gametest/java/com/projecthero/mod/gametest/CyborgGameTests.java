package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.cyborg.Cyborg;
import com.projecthero.mod.cyborg.CyborgAbilities;
import com.projecthero.mod.cyborg.CyborgConfig;
import com.projecthero.mod.cyborg.data.CyborgState;
import com.projecthero.mod.darkseid.item.DarkseidItems;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;

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
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Cyborg: the Mother Box grant (only on 4 hearts or less), the one-Primary rule, the forms and passives, one smoke
 * test per move. Mock players are not ticked by the server, so tests over time drive {@link Cyborg#tick} ({@link #pump}).
 */
public class CyborgGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

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

	/** A Cyborg in combat form, conversion finished, facing +Z. */
	private static ServerPlayer cyborg(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		Cyborg.grant(p);
		CyborgState n = Cyborg.state(p).copy();
		n.formChangeAt = helper.getLevel().getGameTime() - 400L;
		n.animId = CyborgState.ANIM_NONE;
		p.setAttached(ModAttachments.CYBORG_STATE, n);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Cyborg.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
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
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void motherBoxFusesOnlyWhenNearlyDead(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(HeroTiers.HERO_KEYS.contains("cyborg") && PowerGrants.HERO_TIER_KEYS.contains("cyborg"), "cyborg is a Hero-Tier key");
		Kryptonian.grant(p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DarkseidItems.MOTHER_BOX));
		p.setHealth(20f);
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertFalse(Cyborg.hasPower(p), "at full health the Mother Box is only a Boom Tube");
		p.setHealth(CyborgConfig.FUSE_HEALTH);
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(Cyborg.hasPower(p), "on 4 hearts it fuses");
		helper.assertTrue(!Kryptonian.hasPower(p) && HeroTiers.heroCount(p) == 1, "Cyborg replaced the Kryptonian (one Primary)");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the Mother Box is used up");
		helper.assertTrue(Cyborg.combat(p) && Cyborg.changing(p), "the 10 s conversion runs");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hologramIsOnlyADisguise(GameTestHelper helper) {
		ServerPlayer p = cyborg(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - CyborgConfig.ARMOR_BONUS) < 0.01, "diamond-level armour");
		Cyborg.hologramOn(p);
		helper.assertTrue(Cyborg.hologram(p) && Cyborg.combat(p), "H: the hologram is up, he is still Cyborg");
		Cyborg.reconcile(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - CyborgConfig.ARMOR_BONUS) < 0.01, "still diamond-level under the hologram");
		CyborgAbilities.matrix(p);
		helper.assertTrue(Cyborg.matrixUp(p), "a move works under the hologram");
		helper.assertFalse(Cyborg.hologram(p), "... and drops it");
		Cyborg.hologramOn(p);
		Cyborg.toggleForm(p); // inside the toggle cooldown: ignored
		Cyborg.dropHologram(p);
		helper.assertFalse(Cyborg.hologram(p), "dropped");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightningFillsTheCoreEvenUnderTheHologram(GameTestHelper helper) {
		ServerPlayer p = cyborg(helper, 2.5, 2.0, 2.5);
		Cyborg.setCore(p, 10f);
		float hp = p.getHealth();
		p.hurt(p.damageSources().lightningBolt(), 5f);
		helper.assertTrue(p.getHealth() == hp && Cyborg.core(p) == CyborgConfig.CORE_MAX, "absorbed, core full");
		Cyborg.setCore(p, 10f);
		Cyborg.hologramOn(p);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().lightningBolt(), 3f);
		helper.assertTrue(p.getHealth() == hp && Cyborg.core(p) == CyborgConfig.CORE_MAX, "under the hologram too");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noFallDamageAndNoDrowning(GameTestHelper helper) {
		ServerPlayer p = cyborg(helper, 2.5, 2.0, 2.5);
		float hp = p.getHealth();
		p.hurt(p.damageSources().drown(), 4f);
		helper.assertTrue(p.getHealth() == hp, "no drowning");
		p.hurt(p.damageSources().fall(), 8f);
		helper.assertTrue(p.getHealth() == hp, "no fall damage");
		Cyborg.hologramOn(p);
		p.hurt(p.damageSources().fall(), 8f);
		helper.assertTrue(p.getHealth() == hp, "no fall damage under the hologram either");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void sonicCannonChargesAndHits(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		pump(helper, p);
		CyborgAbilities.startCharge(p);
		helper.assertTrue(CyborgAbilities.running(p, CyborgAbilities.CANNON), "charging");
		helper.runAfterDelay(CyborgConfig.CANNON_CHARGE_TICKS + 2, () -> {
			CyborgAbilities.fireCannon(p);
			float taken = 100f - z.getHealth();
			helper.assertTrue(Math.abs(taken - CyborgConfig.CANNON_MAX_DAMAGE) < EPS, "15, took " + taken);
			helper.assertTrue(Cyborg.state(p).fx.beamUntil > helper.getLevel().getGameTime(), "the beam shows");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sonicBurstHitsTheCone(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 3.5, 2.0, 1.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.5, 100);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 0.2, 100);
		CyborgAbilities.burst(p);
		helper.assertTrue(Math.abs((100f - front.getHealth()) - CyborgConfig.BURST_DAMAGE) < EPS, "in front: 24");
		helper.assertTrue(behind.getHealth() == 100f, "behind him: untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hydraulicPunchHitsForTwelve(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		CyborgAbilities.punch(p);
		helper.assertTrue(Math.abs((100f - z.getHealth()) - CyborgConfig.PUNCH_DAMAGE) < EPS, "24, took " + (100f - z.getHealth()));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grappleClawPullsAMob(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 7.0, 100);
		CyborgAbilities.grapple(p);
		helper.assertTrue(z.getDeltaMovement().z < -0.2, "pulled toward him, v=" + z.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hoverLendsFlightAndTakesItBack(GameTestHelper helper) {
		ServerPlayer p = cyborg(helper, 2.5, 2.0, 2.5);
		CyborgAbilities.toggleHover(p);
		helper.assertTrue(Cyborg.state(p).hovering && p.getAbilities().mayfly, "hovering: flight lent");
		CyborgAbilities.toggleHover(p);
		helper.assertTrue(!Cyborg.state(p).hovering && !p.getAbilities().mayfly, "landed: flight taken back");
		helper.succeed();
	}

	/** Points {@code p} at {@code target} (absolute coordinates). */
	private static void aim(ServerPlayer p, Vec3 target) {
		Vec3 to = target.subtract(p.getEyePosition());
		float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.setXRot(pitch);
	}

	private static void ready(ServerPlayer p) {
		CyborgState n = Cyborg.state(p).copy();
		n.abilityReadyAt.clear();
		n.core = CyborgConfig.CORE_MAX;
		p.setAttached(ModAttachments.CYBORG_STATE, n);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void balancePassStats(GameTestHelper helper) {
		ServerPlayer p = cyborg(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - 20.0) < 0.01, "diamond-level armour: 20");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 8.0) < 0.01, "and 8 toughness");
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(Math.abs(base - 10.0) < 0.01, "+9 melee on the bare fist (1): 10, got " + base);
		CyborgAbilities.saw(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 20.0) < 0.01, "the Arm Saw adds 10 more");
		helper.assertTrue(CyborgConfig.CORE_REGEN == 2f, "2 Power Core a second");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void missileBarrageHitsWhatIsInFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 3.5, 2.0, 1.0);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		pump(helper, p);
		CyborgAbilities.missiles(p);
		helper.assertTrue(CyborgAbilities.running(p, CyborgAbilities.MISSILES), "the barrage is running");
		helper.runAfterDelay(60, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= CyborgConfig.MISSILE_DAMAGE * 3 - EPS, "most missiles hit, took " + taken);
			helper.assertTrue(!CyborgAbilities.running(p, CyborgAbilities.MISSILES), "and it is over");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void circuitLinkPowersALampAndFlipsALever(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 3.5, 2.0, 1.0);
		BlockPos lamp = helper.absolutePos(new BlockPos(3, 3, 5));
		helper.getLevel().setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
		pump(helper, p);
		aim(p, Vec3.atCenterOf(lamp));
		com.projecthero.mod.cyborg.CyborgCircuit.press(p);
		com.projecthero.mod.cyborg.CyborgCircuit.release(p);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(BlockStateProperties.LIT), "the lamp is powered");
			helper.assertTrue(helper.getLevel().getBlockState(lamp.north()).is(com.projecthero.mod.cyborg.block.CyborgBlocks.CYBORG_SIGNAL),
					"by a signal on the side he aimed at");
		});
		helper.runAfterDelay(CyborgConfig.LINK_PULSE_TICKS + 12, () -> {
			helper.assertFalse(helper.getLevel().getBlockState(lamp.north()).is(com.projecthero.mod.cyborg.block.CyborgBlocks.CYBORG_SIGNAL),
					"the pulse's signal is gone");
			// a lever on the floor flips
			BlockPos lever = helper.absolutePos(new BlockPos(3, 2, 4));
			helper.getLevel().setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,
					net.minecraft.world.level.block.state.properties.AttachFace.FLOOR), 3);
			ready(p);
			aim(p, Vec3.atCenterOf(lever).add(0, -0.35, 0));
			com.projecthero.mod.cyborg.CyborgCircuit.press(p);
			helper.assertTrue(helper.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED), "the lever flipped");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boomTubeMenuTravelsToAWaypoint(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 1.5, 2.0, 1.5);
		com.projecthero.mod.cyborg.CyborgBoomTube.saveWaypoint(p, 0, "Base");
		Vec3 saved = p.position();
		p.teleportTo(p.getX() + 4, p.getY(), p.getZ() + 4);
		helper.assertTrue(com.projecthero.mod.cyborg.CyborgBoomTube.travelToWaypoint(p, 0) == com.projecthero.mod.cyborg.CyborgBoomTube.Result.OK,
				"travelled");
		helper.assertTrue(p.position().distanceTo(saved) < 1.5, "the Boom Tube took him to the waypoint, at " + p.position());
		helper.assertTrue(Cyborg.cooldownRemaining(p, CyborgAbilities.BOOM) > 0, "60 s cooldown");
		helper.assertTrue(com.projecthero.mod.cyborg.CyborgBoomTube.travelToWaypoint(p, 0) == com.projecthero.mod.cyborg.CyborgBoomTube.Result.COOLDOWN,
				"not again straight away");
		helper.assertTrue(com.projecthero.mod.cyborg.CyborgBoomTube.travelToWaypoint(p, 1) == com.projecthero.mod.cyborg.CyborgBoomTube.Result.EMPTY_WAYPOINT,
				"an empty slot goes nowhere");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void defenseMatrixSoaksTheFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 3.5, 2.0, 1.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		CyborgAbilities.matrix(p);
		float hp = p.getHealth();
		p.hurt(p.damageSources().mobAttack(front), 6f);
		helper.assertTrue(p.getHealth() == hp, "a hit from the front is soaked");
		helper.assertTrue(Cyborg.state(p).shieldHp < CyborgConfig.MATRIX_HP, "the Matrix lost points");
		CyborgAbilities.lowerMatrix(p);
		helper.assertFalse(Cyborg.matrixUp(p), "letting go of C drops it");
		// held again, a big enough beating breaks it
		CyborgState n = Cyborg.state(p).copy();
		n.abilityReadyAt.clear();
		p.setAttached(ModAttachments.CYBORG_STATE, n);
		CyborgAbilities.matrix(p);
		helper.assertTrue(Math.abs(Cyborg.state(p).shieldHp - 100f) < 0.01f, "100 points");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(front), 120f);
		helper.assertFalse(Cyborg.matrixUp(p), "100 points soaked: it broke");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void selfRepairSpendsIronAndHeals(GameTestHelper helper) {
		ServerPlayer p = cyborg(helper, 2.5, 2.0, 2.5);
		p.getInventory().add(new ItemStack(Items.IRON_INGOT, 5));
		p.setHealth(6f);
		pump(helper, p);
		CyborgAbilities.repair(p);
		helper.assertTrue(p.getInventory().countItem(Items.IRON_INGOT) == 2, "3 iron spent");
		helper.runAfterDelay(CyborgConfig.REPAIR_TICKS + 4, () -> {
			helper.assertTrue(Math.abs(p.getHealth() - (6f + CyborgConfig.REPAIR_HEAL)) < 0.6f, "8 healed, at " + p.getHealth());
			helper.succeed();
		});
	}
}

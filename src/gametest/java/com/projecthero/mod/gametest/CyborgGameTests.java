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
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - CyborgConfig.ARMOR_BONUS) < 0.01, "+4 armour");
		Cyborg.hologramOn(p);
		helper.assertTrue(Cyborg.hologram(p) && Cyborg.combat(p), "H: the hologram is up, he is still Cyborg");
		Cyborg.reconcile(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - CyborgConfig.ARMOR_BONUS) < 0.01, "still +4 armour under the hologram");
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
			helper.assertTrue(Math.abs(taken - CyborgConfig.CANNON_MAX_DAMAGE) < EPS, "full charge: 16, took " + taken);
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
		helper.assertTrue(Math.abs((100f - front.getHealth()) - CyborgConfig.BURST_DAMAGE) < EPS, "in front: 8");
		helper.assertTrue(behind.getHealth() == 100f, "behind him: untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hydraulicPunchHitsForTwelve(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		CyborgAbilities.punch(p);
		helper.assertTrue(Math.abs((100f - z.getHealth()) - CyborgConfig.PUNCH_DAMAGE) < EPS, "12, took " + (100f - z.getHealth()));
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void technoOverrideHacksAGolemAndOpensDoors(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 3.5, 2.0, 1.5);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2.0, 5.5, 100);
		CyborgAbilities.override(p);
		helper.assertTrue(CyborgAbilities.hackedBy(g, p), "the golem is his");
		g.discard();
		BlockPos trap = helper.absolutePos(new BlockPos(3, 3, 4));
		helper.getLevel().setBlock(trap, Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF,
				net.minecraft.world.level.block.state.properties.Half.TOP), 3);
		// aim at it
		Vec3 eye = p.getEyePosition();
		Vec3 to = Vec3.atCenterOf(trap).add(0, 0.4, 0).subtract(eye); // a top-half trapdoor is a thin slab at the top of its block
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		p.setXRot(pitch);
		CyborgState n = Cyborg.state(p).copy();
		n.abilityReadyAt.clear();
		n.core = CyborgConfig.CORE_MAX;
		p.setAttached(ModAttachments.CYBORG_STATE, n);
		CyborgAbilities.override(p);
		helper.assertTrue(helper.getLevel().getBlockState(trap).getValue(BlockStateProperties.OPEN), "the trapdoor swung open");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waypointAndBoomTube(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cyborg(helper, 1.5, 2.0, 1.5);
		CyborgAbilities.setWaypoint(p);
		Vec3 saved = p.position();
		p.teleportTo(p.getX() + 4, p.getY(), p.getZ() + 4);
		CyborgAbilities.boomTube(p);
		helper.assertTrue(p.position().distanceTo(saved) < 1.5, "the Boom Tube took him to the waypoint, at " + p.position());
		helper.assertTrue(Cyborg.state(p).waypoint.isEmpty(), "the waypoint is used up");
		helper.assertTrue(Cyborg.cooldownRemaining(p, CyborgAbilities.BOOM) > 0, "90 s cooldown");
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

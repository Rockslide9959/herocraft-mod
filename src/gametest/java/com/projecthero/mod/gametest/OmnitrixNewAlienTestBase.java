package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the shared setup for the Snare-oh, Eye Guy and Frankenstrike gametests (Feedback's helpers): a mock survival player on a stone
 * floor, transformed into a found hidden alien, mobs without AI, aiming, key taps through the real router, and the hidden-alien / dial
 * glyph / key-layout checks every one of them makes.
 */
abstract class OmnitrixNewAlienTestBase implements FabricGameTest {
	static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
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

	static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	/** A mock player turned into hidden alien {@code id} (found first), ticking. */
	static ServerPlayer alien(GameTestHelper helper, String id) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, id, true);
		helper.assertTrue(Omnitrix.select(p, id), id + " is on the prototype's dial once found");
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + id);
		helper.onEachTick(() -> Omnitrix.tick(p));
		return p;
	}

	static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		if (m instanceof Zombie) {
			m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		}
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	static void lookAt(ServerPlayer p, LivingEntity z) {
		lookAt(p, z.position().add(0, z.getBbHeight() * 0.5, 0));
	}

	static void lookAt(ServerPlayer p, Vec3 at) {
		Vec3 d = at.subtract(p.getEyePosition());
		float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
		float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
	}

	static void ready(ServerPlayer p, AlienKit k) {
		for (var m : k.moves()) {
			k.cooldown(p, m.id(), 0);
		}
	}

	/** Hidden on the prototype's dial, found only by a mistransform, glyph {@code glyph}. */
	static void hiddenAlienChecks(GameTestHelper helper, Aliens.Alien a, char glyph) {
		helper.assertTrue(Aliens.byId(a.id()) == a && !a.recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.hidden(a.id()) && !Aliens.itemOnly(a.id()) && Aliens.mistransformFinds().contains(a), "hidden, found by a mistransform (no item)");
		helper.assertTrue(Aliens.dial(false).contains(a) && !Aliens.dial(true).contains(a), "on the prototype's dial only");
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		helper.assertFalse(Aliens.playable(p).contains(a), "not on the dial before he is found");
		helper.assertFalse(Omnitrix.select(p, a.id()), "and cannot be dialled");
		helper.assertTrue(Aliens.unfound(p).contains(a), "a mistransform can find him");
		Aliens.discover(p, a.id(), true);
		helper.assertTrue(Aliens.playable(p).contains(a) && !Aliens.unfound(p).contains(a), "found: on the dial, out of the draw");
		String g = OmnitrixInfo.icon(a).getString();
		helper.assertTrue(g.length() == 1 && g.charAt(0) == glyph,
				"his dial glyph is U+" + Integer.toHexString(glyph).toUpperCase() + ", got " + (g.isEmpty() ? "none" : Integer.toHexString(g.charAt(0))));
		helper.assertTrue(OmnitrixNewAlienTestBase.class.getResource("/assets/projecthero/textures/gui/omnitrix/dial_" + a.id() + ".png") != null
				&& OmnitrixNewAlienTestBase.class.getResource("/assets/projecthero/textures/gui/omnitrix/dial_" + a.id() + "_red.png") != null,
				"his dial art (green and red)");
	}

	/** Every key R/G/X/Z/V/C has a plain move; nothing sits on H or N. */
	static void keyLayoutChecks(GameTestHelper helper, AlienKit k) {
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(k.moveOn(s, false, false) != null, k.id() + ": a move on " + s);
		}
		for (var m : k.moves()) {
			helper.assertTrue(!m.onSlot() || m.slot().ordinal() <= AbilitySlot.SLOT_6.ordinal(), m.id() + " on R/G/X/Z/V/C only");
		}
	}

	/** {@code got} within {@code tol} of {@code want}. */
	static void near(GameTestHelper helper, float got, float want, float tol, String what) {
		helper.assertTrue(Math.abs(got - want) <= tol, what + ": expected about " + want + ", got " + got);
	}
}

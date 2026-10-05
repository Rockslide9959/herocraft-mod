package com.projecthero.mod.gametest;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** v0.14.28: Rescue Tether pacifies what it carries, Mark 1 flamethrower reach, plain-number suit names, lock-on. */
public class V01428GameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void rescueTetherCarriedMobsCannotAttack(GameTestHelper h) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Creeper creeper = EntityType.CREEPER.create(h.getLevel());
		creeper.moveTo(p.getX() + 1.5, p.getY(), p.getZ());
		h.getLevel().addFreshEntity(creeper);
		Skeleton skeleton = EntityType.SKELETON.create(h.getLevel());
		skeleton.moveTo(p.getX() - 2.5, p.getY(), p.getZ());
		h.getLevel().addFreshEntity(skeleton);
		h.onEachTick(() -> {
			if (creeper.isAlive()) {
				creeper.setTarget(p);
				GreenLanternConstructs.pacifyCarried(creeper);
			}
			if (skeleton.isAlive()) {
				skeleton.setTarget(p);
				GreenLanternConstructs.pacifyCarried(skeleton);
			}
		});
		h.runAfterDelay(120, () -> {
			h.assertTrue(creeper.isAlive(), "a carried creeper must never explode");
			h.assertTrue(creeper.getSwelling(1f) <= 0.01f, "a carried creeper's fuse stays wound down");
			h.assertFalse(skeleton.isUsingItem(), "a carried skeleton never draws its bow");
			h.assertTrue(skeleton.getTarget() == null, "a carried skeleton has no target");
			creeper.discard();
			skeleton.discard();
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markOneFlamethrowerReachesTenBlocks(GameTestHelper h) {
		h.assertTrue(IronManAbilities.flamethrowerReach(IronManSuits.MARK_1) == 10.0, "Mark 1 flamethrower reach is 10");
		h.assertTrue(IronManAbilities.flamethrowerReach(IronManSuits.MARK_VII) == 6.0, "other flamethrowers keep 6");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitNamesUsePlainNumbers(GameTestHelper h) {
		JsonObject lang;
		try (var in = V01428GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		for (IronManSuit suit : IronManSuits.all()) {
			String name = lang.get("projecthero.ironman.suit." + suit.id() + ".name").getAsString();
			h.assertTrue(name.equals("Mark " + suit.markNumber()), suit.id() + " should be named Mark " + suit.markNumber() + ", got " + name);
		}
		for (var e : lang.entrySet()) {
			h.assertFalse(e.getValue().getAsString().matches(".*\\bMark (I|II|III|IV|V|VI|VII|VIII)\\b.*"),
					e.getKey() + " still uses a Roman numeral: " + e.getValue().getAsString());
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lockOnFollowsTheCrosshairInAGroup(GameTestHelper h) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		TonyStark.grant(p);
		for (ArmorItem.Type t : ArmorItem.Type.values()) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor("mark_iii", t)));
		}
		IronManEnergy.setEnergy(p, "mark_iii", IronManEnergy.capacity("mark_iii"));
		Zombie left = zombie(h, p.getX() - 0.7, p.getY(), p.getZ() + 6);
		Zombie right = zombie(h, p.getX() + 0.7, p.getY(), p.getZ() + 6);
		IronManSuit suit = IronManSuits.MARK_III;
		aimAt(p, left);
		p.tickCount = 0;
		IronManTargeting.tick(p, suit);
		h.assertTrue(IronManTargeting.locked(p) == left, "aiming at the left zombie locks it");
		aimAt(p, right);
		p.tickCount = 2;
		IronManTargeting.tick(p, suit);
		h.assertTrue(IronManTargeting.locked(p) == right, "aiming at its neighbour switches the lock straight away");
		left.discard();
		right.discard();
		h.succeed();
	}

	private static Zombie zombie(GameTestHelper h, double x, double y, double z) {
		Zombie zb = EntityType.ZOMBIE.create(h.getLevel());
		zb.moveTo(x, y, z);
		zb.setNoAi(true);
		h.getLevel().addFreshEntity(zb);
		return zb;
	}

	private static void aimAt(ServerPlayer p, Zombie z) {
		double dx = z.getX() - p.getX(), dz = z.getZ() - p.getZ();
		double dy = (z.getY() + z.getBbHeight() * 0.5) - p.getEyeY();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.setXRot(pitch);
	}
}

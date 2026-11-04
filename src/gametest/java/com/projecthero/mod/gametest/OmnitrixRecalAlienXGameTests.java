package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixDamage;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXBodyEntity;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXCouncil;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Alien X's Celestial Council (docs/OMNITRIX_REVAMP_ALIENX.md): impervious to every kind of damage (but /kill and the
 * void still get through), 2.5 blocks tall, the Recalibrated Omnitrix battery held where it was, the trip to the council and
 * back (body left standing, inventory intact, back at the body), and a logout mid-council bringing him home on the next login.
 * The debates themselves are OmnitrixAlienXGameTests. Mock players are not ticked by the server, so the tests pump
 * {@link Omnitrix#tick} themselves.
 */
public class OmnitrixRecalAlienXGameTests implements FabricGameTest {
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

	/** A recalibrated watch, Alien X selected and transformed (he is now before the council). */
	static ServerPlayer alienX(GameTestHelper helper, ServerPlayer p) {
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, AlienXKit.ID), "Alien X is on its dial");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Alien X");
		return p;
	}

	static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static AlienXKit kit() {
		return (AlienXKit) Aliens.kit(AlienXKit.ID);
	}

	/**
	 * The game-test server loads no datapack dimensions: without the real council, the trip goes through the Nether as its
	 * stand-in (the same code path -- another level, the floor, the body left behind, the way home).
	 */
	private static void standIn(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		if (server.getLevel(AlienXCouncil.DIMENSION) == null) {
			helper.assertTrue(server.getLevel(net.minecraft.world.level.Level.NETHER) != null, "a level to stand in for the council");
			AlienXCouncil.useTestCouncil(net.minecraft.world.level.Level.NETHER);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void alienXIsImperviousTallAndCannotJustHit(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = alienX(helper, mock(helper, 3.5, 2, 1.5));
		var src = helper.getLevel().damageSources();
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(Math.abs(p.getScale() - 2.5f / 1.8f) < 0.01f, "2.5 blocks tall (scale " + p.getScale() + ")");
		DamageSource[] all = { src.fall(), src.drown(), src.magic(), src.inFire(), src.lava(), src.wither(), src.explosion(null, null), src.mobAttack(z),
				src.playerAttack(p), src.freeze(), src.cactus(), src.starve() };
		for (DamageSource s : all) {
			helper.assertFalse(kit().allowDamage(p, s, 100f), "impervious to " + s.getMsgId());
		}
		float hp = p.getHealth();
		for (DamageSource s : all) {
			p.invulnerableTime = 0;
			p.hurt(s, 50f);
		}
		helper.assertTrue(Math.abs(p.getHealth() - hp) < 0.01f, "no damage got through (" + hp + " -> " + p.getHealth() + ")");
		helper.assertTrue(OmnitrixDamage.allowDamage(p, src.genericKill(), 1f) && OmnitrixDamage.allowDamage(p, src.fellOutOfWorld(), 1f),
				"/kill and the void still work: nobody is stuck as him");
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
		p.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
		kit().tick(p, Omnitrix.state(p), helper.getLevel().getGameTime() + 1);
		helper.assertFalse(p.hasEffect(MobEffects.POISON) || p.hasEffect(MobEffects.WITHER), "harmful effects fall off him");
		helper.assertTrue(kit().onAttack(p, z), "a bare punch does nothing: every strike goes through the council");
		helper.assertTrue(kit().moveOn(AbilitySlot.SLOT_5, false, false) != null, "a V move (Leave the Council)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theBatteryHoldsWhileHeIsAlienX(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = alienX(helper, mock(helper, 3.5, 2, 1.5));
		int b0 = Omnitrix.batteryTicks(p);
		helper.assertTrue(b0 > 0, "a battery to hold");
		pump(helper, p);
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(Omnitrix.batteryTicks(p) == b0, "the battery has not moved after 3 s (" + b0 + " -> " + Omnitrix.batteryTicks(p) + ")");
			// even a time limit already run out does not throw him back
			OmnitrixState n = Omnitrix.state(p).copy();
			n.endsAt = helper.getLevel().getGameTime() + 1;
			Omnitrix.save(p, n);
		});
		helper.runAtTickTime(120, () -> {
			helper.assertTrue(Omnitrix.state(p).transformed() && AlienXKit.ID.equals(Omnitrix.active(p)), "still Alien X: no timeout");
			helper.assertTrue(Omnitrix.batteryTicks(p) == b0, "still the same battery");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
			int after = Omnitrix.batteryTicks(p);
			helper.assertTrue(after >= b0 && after <= b0 + 5, "the battery kept exactly what it had (" + b0 + " -> " + after + ")");
			helper.assertFalse(Omnitrix.state(p).timers.containsKey(Omnitrix.HOLD_BATTERY), "the hold is gone");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void heGoesToTheCouncilAndComesBackToHisBody(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		p.getInventory().add(new ItemStack(Items.DIAMOND, 5));
		p.getInventory().add(new ItemStack(Items.IRON_SWORD));
		Vec3 from = p.position();
		standIn(helper);
		alienX(helper, p);
		helper.assertTrue(AlienXCouncil.isCouncil(p.level()), "Ben is before the council");
		helper.assertTrue(AlienXCouncil.inSession(p), "a council session");
		AlienXBodyEntity body = AlienXCouncil.body(helper.getLevel().getServer(), p);
		helper.assertTrue(body != null && body.level() == helper.getLevel() && body.position().distanceTo(from) < 0.01, "his body stands where he was");
		helper.assertTrue(Math.abs(body.getBbHeight() - 2.5f) < 0.01f, "the body is 2.5 blocks tall");
		helper.assertFalse(body.hurt(helper.getLevel().damageSources().magic(), 1000f), "the body cannot be hurt");
		helper.assertTrue(p.getAttachedOrElse(AlienXCouncil.RETURN, null) != null, "where he came from is saved on him");
		helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 5, "his inventory went with him");
		pump(helper, p);
		helper.runAtTickTime(20, () -> {
			helper.assertTrue(AlienXCouncil.isCouncil(p.level()) && p.getY() > AlienXCouncil.FLOOR_Y, "standing on the council's floor");
			// v0.17.2 (user: "the player has to convince the heads to let them detransform aswell please"): V moves the Let Me Go
			// motion -- nothing happens until both heads agree
			AlienXCouncil.forceConcerns(p, 1, 5); // glory, balance
			kit().press(p, AbilitySlot.SLOT_5, false); // V: ask to leave
			helper.assertTrue(Omnitrix.state(p).transformed() && AlienXCouncil.inSession(p), "V alone does not turn him back");
			helper.assertTrue(AlienXCouncil.phase(p) == AlienXCouncil.PHASE_DEBATE, "the Let Me Go motion is before the council");
			helper.assertFalse(Omnitrix.revertEarly(p), "N does not turn him back either");
			AlienXCouncil.freeTones(p);
			for (int tone : new int[] { 1, 5, 3 }) { // glory (x2), balance (x2), protection: both reach 85
				helper.assertTrue(AlienXCouncil.argue(p, tone), "argued");
			}
			helper.assertTrue(AlienXCouncil.lastCarried(p) && AlienXCouncil.releasing(p), "the council lets him go");
		});
		helper.runAtTickTime(20 + AlienXCouncil.RELEASE_DELAY + 6, () -> {
			helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
			helper.assertTrue(p.level() == helper.getLevel(), "back in the world");
			helper.assertTrue(p.position().distanceTo(from) < 0.6, "at his body (" + p.position() + " vs " + from + ")");
			helper.assertTrue(body.isRemoved(), "the body is gone");
			helper.assertFalse(AlienXCouncil.inSession(p), "the session is over");
			helper.assertTrue(p.getAttachedOrElse(AlienXCouncil.RETURN, null) == null, "the return point is cleared");
			helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 5 && p.getInventory().countItem(Items.IRON_SWORD) == 1, "the inventory is intact");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void aLogoutInTheCouncilBringsHimHomeOnLogin(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		Vec3 from = p.position();
		standIn(helper);
		alienX(helper, p);
		helper.assertTrue(AlienXCouncil.isCouncil(p.level()), "in the council");
		AlienXBodyEntity body = AlienXCouncil.body(helper.getLevel().getServer(), p);
		// the logout: the session ends first (the body goes), then the Omnitrix turns him back -- with no teleport mid-disconnect
		AlienXCouncil.onDisconnect(p);
		Omnitrix.onLogout(p);
		helper.assertFalse(Omnitrix.state(p).transformed(), "turned back on logout");
		helper.assertTrue(AlienXCouncil.isCouncil(p.level()), "saved where he was (in the council)");
		helper.assertTrue(p.getAttachedOrElse(AlienXCouncil.RETURN, null) != null, "with his way home saved");
		helper.runAtTickTime(10, () -> {
			helper.assertTrue(body == null || body.isRemoved(), "no orphaned body");
			// the next login: the sweep finds him in the council with no session
			AlienXCouncil.check(p);
			helper.assertTrue(p.level() == helper.getLevel(), "back in the world");
			helper.assertTrue(p.position().distanceTo(from) < 0.6, "where his body stood");
			helper.assertTrue(p.getAttachedOrElse(AlienXCouncil.RETURN, null) == null, "the return point is cleared");
			helper.succeed();
		});
	}
}

package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.3 Iron Man: immunity while a suit assembles, no worn integrity regen, the faceplate shutting itself on take-off /
 * ability / damage, the 75%-of-damage integrity wear with hits landing in full, flight at 3/s with halved regen, and the
 * Suit Platform's robotic-arm retrieve with the wearer's back held to the platform. Mock players never fire the damage
 * events, so the ALLOW_DAMAGE / AFTER_DAMAGE handlers are called directly; every timing check reads the shared
 * timetables, never wall time.
 */
public class IronManV0153GameTests implements FabricGameTest {
	private static final String[] FLYING_MARKS = { "mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii" };
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final BlockPos PLATFORM = new BlockPos(2, 2, 2);

	private static ServerPlayer player(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		Vec3 v = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 2, 2)));
		p.setPos(v.x, v.y, v.z);
		p.setYRot(0f);
		p.setYHeadRot(0f);
		return p;
	}

	private static ServerPlayer suited(GameTestHelper h, String suitId) {
		ServerPlayer p = player(h);
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(suitId, t)));
		}
		TonyStark.setActiveSuit(p, suitId);
		IronManEnergy.setEnergy(p, suitId, IronManEnergy.capacity(suitId));
		IronManEnergy.setIntegrity(p, suitId, IronManEnergy.maxIntegrity(suitId));
		return p;
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	/** A north-facing platform with a clear, floored spot in front of it (the stance). */
	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h) {
		h.setBlock(PLATFORM, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		h.setBlock(PLATFORM.north().below(), Blocks.STONE);
		h.setBlock(PLATFORM.north(), Blocks.AIR);
		h.setBlock(PLATFORM.north().above(), Blocks.AIR);
		return (IronManSuitPlatformBlockEntity) h.getBlockEntity(PLATFORM);
	}

	private static int wornCount(ServerPlayer p) {
		int worn = 0;
		for (ArmorItem.Type t : TYPES) {
			worn += p.getItemBySlot(IronManSuitUpManager.slotFor(t)).getItem() instanceof IronManArmorItem ? 1 : 0;
		}
		return worn;
	}

	private static boolean yawIs(ServerPlayer p, float yaw) {
		return Math.abs(Mth.wrapDegrees(p.getYRot() - yaw)) < 0.5f && Math.abs(Mth.wrapDegrees(p.getYHeadRot() - yaw)) < 0.5f
				&& Math.abs(Mth.wrapDegrees(p.yBodyRot - yaw)) < 0.5f;
	}

	// ------------------------------------------------------------------ 1. immune while suiting up

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
	public void immuneWhileTheSuitAssemblesAndNotAfter(GameTestHelper h) {
		ServerPlayer p = player(h);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor("mark_iii", t)));
		}
		DamageSource hit = p.damageSources().generic();
		h.assertTrue(IronManDamage.onAllowDamage(p, hit, 5f), "before the suit-up a hit lands");
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii"), "the suit-up starts");
		h.assertTrue(IronManDamage.suitUpImmune(p), "immune the moment it starts");
		h.assertFalse(IronManDamage.onAllowDamage(p, hit, 5f), "a hit is cancelled while the suit assembles");
		h.assertTrue(IronManDamage.onAllowDamage(p, p.damageSources().genericKill(), 5f), "/kill still works");
		h.onEachTick(() -> {
			if (IronManSuitUpManager.assembling(p)) {
				h.assertFalse(IronManDamage.onAllowDamage(p, p.damageSources().generic(), 5f), "still immune while assembling");
			}
		});
		h.succeedWhen(() -> {
			h.assertFalse(IronManSuitUpManager.inTransition(p), "the suit-up finishes");
			h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_iii"), "with the suit on");
			h.assertFalse(IronManDamage.suitUpImmune(p), "the immunity ends with it");
			h.assertTrue(IronManDamage.onAllowDamage(p, p.damageSources().generic(), 5f), "hits land again");
		});
	}

	// ------------------------------------------------------------------ 2. no integrity regen

	@GameTest(template = EMPTY_STRUCTURE)
	public void wornSuitsNeverRepairThemselves(GameTestHelper h) {
		for (String id : FLYING_MARKS) {
			if (com.projecthero.mod.ironman.ability.IronManMark6.isKitSuit(id)) {
				continue; // v0.15.4: the Mark 6 / Mark 7 repair 1/s while worn (IronManMk67V0154GameTests)
			}
			ServerPlayer p = suited(h, id);
			IronManEnergy.setIntegrity(p, id, 100f);
			for (int i = 0; i < 60; i++) {
				IronManSuitTicker.tick(p);
			}
			h.assertTrue(IronManEnergy.integrity(p, id) == 100f, id + " integrity must not regenerate while worn, got "
					+ IronManEnergy.integrity(p, id));
			leave(h, p);
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ 3. the faceplate shuts itself

	@GameTest(template = EMPTY_STRUCTURE)
	public void faceplateClosesOnTakeOffAbilityAndDamage(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		p.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
		p.setOnGround(false);
		IronManFlight.setFlying(p, true);
		h.assertFalse(IronManFaceplate.isOpen(p), "taking off closes the faceplate");
		h.assertTrue(IronManSuitFx.of(p).faceplateAge(p.level().getGameTime(), 0f) >= 0f, "with the normal visor swing");
		IronManFlight.setFlying(p, false);

		p.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		h.assertFalse(IronManFaceplate.isOpen(p), "using an ability closes the faceplate");

		p.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
		IronManDamage.onDamageTaken(p, p.damageSources().generic(), 2f);
		h.assertFalse(IronManFaceplate.isOpen(p), "taking damage closes the faceplate");

		p.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
		IronManDamage.onDamageTaken(p, p.damageSources().generic(), 0f);
		h.assertTrue(IronManFaceplate.isOpen(p), "a hit that did nothing leaves it alone");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ 4. 75% integrity wear, hits land in full

	@GameTest(template = EMPTY_STRUCTURE)
	public void tenDamageCostsSevenPointFiveIntegrityAndLandsInFull(GameTestHelper h) {
		for (String id : FLYING_MARKS) {
			ServerPlayer p = suited(h, id);
			float max = IronManEnergy.maxIntegrity(id);
			float energy = IronManEnergy.energy(p, id);
			DamageSource hit = p.damageSources().generic();
			h.assertTrue(IronManDamage.onAllowDamage(p, hit, 10f),
					id + ": the hit is not cancelled or reduced -- the player takes the full 10");
			h.assertTrue(IronManEnergy.energy(p, id) == energy, id + ": no energy charge for the hit");
			IronManDamage.onDamageTaken(p, hit, 10f);
			h.assertTrue(Math.abs(IronManEnergy.integrity(p, id) - (max - 7.5f)) < 1e-3f,
					id + ": 10 damage taken = 7.5 integrity, got " + (max - IronManEnergy.integrity(p, id)));
			IronManDamage.onDamageTaken(p, hit, 1f);
			IronManDamage.onDamageTaken(p, hit, 1f);
			h.assertTrue(Math.abs(IronManEnergy.integrity(p, id) - (max - 9.0f)) < 1e-3f,
					id + ": fractions accumulate (7.5 + 0.75 + 0.75), got " + (max - IronManEnergy.integrity(p, id)));
			leave(h, p);
		}
		// fire is heat, not impact: it barely wears a suit it can reach (Mark 6 / 7 aren't fire-immune)
		ServerPlayer p6 = suited(h, "mark_6");
		IronManDamage.onDamageTaken(p6, p6.damageSources().inFire(), 10f);
		h.assertTrue(Math.abs(IronManEnergy.integrity(p6, "mark_6") - (IronManEnergy.maxIntegrity("mark_6") - 0.375f)) < 1e-3f,
				"fire wears 5% of the normal rate"); // v0.15.4: the Mark 6 pool is 2500
		// with integrity gone nothing more comes off, and the hit still lands
		IronManEnergy.setIntegrity(p6, "mark_6", 0f);
		IronManDamage.onDamageTaken(p6, p6.damageSources().generic(), 10f);
		h.assertTrue(IronManEnergy.integrity(p6, "mark_6") == 0f, "integrity never goes negative");
		h.assertTrue(IronManDamage.onAllowDamage(p6, p6.damageSources().generic(), 10f), "a failed suit still lets hits land");
		leave(h, p6);
		// special rules kept: arrows / fire do nothing to a powered Mark 1-5, no fall damage
		ServerPlayer p3 = suited(h, "mark_iii");
		h.assertFalse(IronManDamage.onAllowDamage(p3, p3.damageSources().inFire(), 10f), "Mark 3 still ignores fire");
		h.assertFalse(IronManDamage.onAllowDamage(p3, p3.damageSources().fall(), 10f), "and falls");
		leave(h, p3);
		h.succeed();
	}

	// ------------------------------------------------------------------ 5. flight: 3/s drain, regen halved

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightDrainsThreePerSecondAndHalvesRegen(GameTestHelper h) {
		for (String id : FLYING_MARKS) {
			IronManSuit suit = IronManSuits.byId(id);
			h.assertTrue(suit.flatFlightDrainPerSecond() == 3f, id + " flies at 3 energy/s");
			ServerPlayer p = suited(h, id);
			IronManEnergy.setEnergy(p, id, 100f);
			float rest = IronManEnergy.regenPerSecond(p, suit);
			h.assertTrue(rest == suit.energyRegenPerSecond(), id + " full regen on the ground");
			p.setOnGround(false);
			IronManFlight.setFlying(p, true);
			h.assertTrue(IronManEnergy.regenPerSecond(p, suit) == rest * 0.5f, id + " regen halved while flying");
			float e0 = IronManEnergy.energy(p, id);
			IronManFlight.tick(p);
			h.assertTrue(IronManFlight.isFlying(p), id + " still airborne");
			float e1 = IronManEnergy.energy(p, id);
			h.assertTrue(Math.abs((e0 - e1) - 3f / 20f) < 1e-4f, id + " one flight tick drains 3/20, got " + (e0 - e1));
			IronManEnergy.tickRecharge(p, suit);
			float e2 = IronManEnergy.energy(p, id);
			h.assertTrue(Math.abs((e2 - e1) - rest * 0.5f / 20f) < 1e-4f, id + " one regen tick in the air is half, got " + (e2 - e1));
			h.assertTrue(e2 < e0 || rest * 0.5f >= 3f, id + " flying costs energy net");
			IronManFlight.setFlying(p, false);
			h.assertTrue(IronManEnergy.regenPerSecond(p, suit) == rest, id + " regen back to full after landing");
			leave(h, p);
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ rockets explode on creatures

	private static net.minecraft.world.entity.monster.Husk husk(GameTestHelper h, BlockPos rel) {
		h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		net.minecraft.world.entity.monster.Husk z = net.minecraft.world.entity.EntityType.HUSK.create(h.getLevel());
		Vec3 v = Vec3.atBottomCenterOf(h.absolutePos(rel));
		z.moveTo(v.x, v.y, v.z, 0f, 0f);
		z.setNoAi(true);
		z.setPersistenceRequired();
		h.getLevel().addFreshEntity(z);
		return z;
	}

	private static com.projecthero.mod.ironman.entity.IronManMissileEntity rocketAt(ServerPlayer p, Vec3 from, Vec3 dir) {
		com.projecthero.mod.ironman.entity.IronManMissileEntity m = new com.projecthero.mod.ironman.entity.IronManMissileEntity(
				p.level(), p, dir.scale(1.4)).withDamage(20f, 14f).withBlastRadius(2.0f);
		m.setPos(from.x, from.y, from.z);
		p.level().addFreshEntity(m);
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void rocketExplodesOnContactWithACreature(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		Vec3 eye = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))).add(0, 1.0, 0);
		var target = husk(h, new BlockPos(1, 2, 7));
		float max = target.getHealth();
		var rocket = rocketAt(p, eye, new Vec3(0, 0, 1));
		Vec3[] last = { rocket.position() };
		h.onEachTick(() -> {
			if (rocket.isAlive()) {
				last[0] = rocket.position();
			}
		});
		h.succeedWhen(() -> {
			h.assertFalse(rocket.isAlive(), "the rocket went off");
			h.assertTrue(target.getHealth() < max || !target.isAlive(), "and hurt the husk");
			h.assertTrue(last[0].z < target.getZ() + 0.6, "on contact -- it never flew on past the husk (last z "
					+ last[0].z + ", husk z " + target.getZ() + ")");
			leave(h, p);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void pointBlankRocketGoesOffInsideTheTarget(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		var target = husk(h, new BlockPos(1, 2, 3));
		float max = target.getHealth();
		// spawned inside the husk's box: vanilla's swept test never sees it, the contact fuse must
		var rocket = rocketAt(p, target.position().add(0, 1.0, 0), new Vec3(0, 0, 1));
		h.runAfterDelay(3, () -> {
			h.assertFalse(rocket.isAlive(), "a rocket fired into a creature explodes at once");
			h.assertTrue(target.getHealth() < max || !target.isAlive(), "and hurts it");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void rocketNeverGoesOffOnItsShooter(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		// launched from inside the shooter, straight up into open air
		var rocket = rocketAt(p, p.position().add(0, 1.0, 0), new Vec3(0, 1, 0));
		h.runAfterDelay(3, () -> {
			h.assertTrue(rocket.isAlive(), "the rocket must not explode on the player who fired it");
			rocket.discard();
			leave(h, p);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ weapon wheel: release V to equip

	@GameTest(template = EMPTY_STRUCTURE)
	public void weaponWheelEquipsOnReleaseOnlyWhenSomethingNewIsPointedAt(GameTestHelper h) {
		String[] mk3 = com.projecthero.mod.ironman.ability.IronManMark3.WEAPONS;
		String glow = com.projecthero.mod.ironman.ability.IronManAbilities.ENTITY_GLOW_TOGGLE;
		String current = mk3[0];
		h.assertTrue(com.projecthero.mod.ironman.ui.IronManUiLayout.wheelReleaseChoice(mk3, -1, current, glow) == null,
				"a quick tap (cursor still in the centre) keeps the current weapon");
		h.assertTrue(com.projecthero.mod.ironman.ui.IronManUiLayout.wheelReleaseChoice(mk3, 0, current, glow) == null,
				"letting go over the weapon already equipped changes nothing");
		h.assertTrue(mk3[1].equals(com.projecthero.mod.ironman.ui.IronManUiLayout.wheelReleaseChoice(mk3, 1, current, glow)),
				"letting go over another weapon equips it");
		h.assertTrue(com.projecthero.mod.ironman.ui.IronManUiLayout.wheelReleaseChoice(mk3, mk3.length, current, glow) == null,
				"an out-of-range hover is ignored");
		String[] mk7 = com.projecthero.mod.ironman.ability.IronManAbilities.WEAPON_WHEEL_SECTORS;
		int g = java.util.Arrays.asList(mk7).indexOf(glow);
		h.assertTrue(g >= 0 && glow.equals(com.projecthero.mod.ironman.ui.IronManUiLayout.wheelReleaseChoice(mk7, g, mk7[0], glow)),
				"the Mark 7's highlight wedge always toggles on release");
		// the server side of a release: the chosen weapon becomes the Mark III's G weapon
		ServerPlayer p = suited(h, "mark_iii");
		String pick = com.projecthero.mod.ironman.ui.IronManUiLayout.wheelReleaseChoice(mk3, 2,
				com.projecthero.mod.ironman.ability.IronManMark3.selectedWeapon(TonyStark.state(p), "mark_iii"), glow);
		if (pick != null) {
			com.projecthero.mod.network.IronManWeaponWheelPayload.handleServer(p, pick);
		}
		h.assertTrue(mk3[2].equals(com.projecthero.mod.ironman.ability.IronManMark3.selectedWeapon(TonyStark.state(p), "mark_iii")),
				"the released-on weapon is equipped, got "
						+ com.projecthero.mod.ironman.ability.IronManMark3.selectedWeapon(TonyStark.state(p), "mark_iii"));
		leave(h, p);
		h.succeed();
	}
}

package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.ability.IronManHeldBeam;
import com.projecthero.mod.ironman.ability.IronManMark3;
import com.projecthero.mod.ironman.ability.IronManMark6;
import com.projecthero.mod.ironman.ability.IronManMark7;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 (agent C): the Mark 6 kit (Arc Reactor Surge: the damage boost lands, drains, expires and cools down; the
 * shield / barrage / held Unibeam slots), the Mark 7's retuned wheel weapons and held Unibeam, and the orbital drop
 * (the pod starts high above, homes onto an airborne owner and clamps the suit on; a pack call while airborne comes by
 * pod; a pod that can't reach hands the suit over to the ordinary staged suit-up).
 */
public class IronManV01429Mk67GameTests implements FabricGameTest {
	private static final String M6 = IronManMark6.SUIT_ID;
	private static final String M7 = IronManMark7.SUIT_ID;
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = IronManV01429Mk67GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
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

	private static ServerPlayer bare(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f); // facing +Z
		p.setXRot(0f);
		p.setYHeadRot(0f);
		return p;
	}

	private static ServerPlayer suited(GameTestHelper h, String suitId) {
		ServerPlayer p = bare(h);
		for (ArmorItem.Type type : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(type), new ItemStack(IronManItems.armor(suitId, type)));
		}
		IronManEnergy.setEnergy(p, suitId, 5000f);
		IronManEnergy.setIntegrity(p, suitId, 500f);
		return p;
	}

	private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz) {
		T m = type.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0f, 0f);
		m.setNoAi(true);
		h.getLevel().addFreshEntity(m);
		return m;
	}

	private static void press(ServerPlayer p, AbilitySlot slot) {
		IronManAbilityManager.handle(p, slot, true);
		IronManAbilityManager.handle(p, slot, false);
	}

	private static boolean wearing(ServerPlayer p, String suitId) {
		for (ArmorItem.Type t : TYPES) {
			if (!(p.getItemBySlot(IronManSuitUpManager.slotFor(t)).getItem() instanceof IronManArmorItem a) || !a.suitId().equals(suitId)) {
				return false;
			}
		}
		return true;
	}

	private static java.util.function.Predicate<IronManDeliveryPodEntity> podsOf(ServerPlayer p) {
		return e -> p.getUUID().equals(e.ownerId());
	}

	private static AABB sky(GameTestHelper h) {
		return new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40).expandTowards(0, 100, 0);
	}

	// ------------------------------------------------------------------ Mark 6

	@GameTest(template = EMPTY_STRUCTURE)
	public void markSixLayoutAndNumbers(GameTestHelper h) {
		var m6 = IronManSuits.MARK_6;
		h.assertTrue(IronManAbilities.REPULSOR_BLAST.equals(m6.abilityInSlot(1)), "R repulsor");
		h.assertTrue(IronManMark6.SHIELD.equals(m6.abilityInSlot(2)), "G 360 shield / Sneak: Sonic Clap");
		h.assertTrue(IronManMark6.SURGE.equals(m6.abilityInSlot(3)), "X Arc Reactor Surge / Sneak: Flares");
		h.assertTrue(IronManMark6.UNIBEAM.equals(m6.abilityInSlot(4)), "Z held Unibeam");
		h.assertTrue(IronManMark6.BARRAGE.equals(m6.abilityInSlot(5)), "V Shoulder Barrage / Sneak: highlight");
		h.assertTrue(IronManAbilities.SUIT_TOGGLE.equals(m6.abilityInSlot(6)), "C store");
		h.assertTrue(m6.fullBodyShield() && m6.coloredEntityGlow() && m6.targeting() && m6.waterBreathing(), "kept / modern flags");
		// between the Mark III and the Mark 7
		h.assertTrue(m6.repulsorDamage() > IronManSuits.MARK_III.repulsorDamage() && m6.repulsorDamage() < IronManSuits.MARK_VII.repulsorDamage(),
				"Mark 6 tap repulsor sits between the Mark III and Mark 7");
		h.assertTrue(m6.chargedRepulsorDamage() > IronManSuits.MARK_III.chargedRepulsorDamage()
				&& m6.chargedRepulsorDamage() < IronManSuits.MARK_VII.chargedRepulsorDamage(), "charged too");
		h.assertTrue(IronManMark6.UNIBEAM_DAMAGE > IronManMark3.UNIBEAM_DAMAGE && IronManMark6.UNIBEAM_DAMAGE < IronManMark7.UNIBEAM_DAMAGE,
				"and the Unibeam");
		h.assertTrue(IronManMark6.SURGE_TICKS == 200 && IronManMark6.SURGE_DAMAGE_MULTIPLIER == 1.5f && IronManMark6.SURGE_COOLDOWN == 800,
				"surge: 10 s, +50%, 40 s cooldown");
		for (String key : new String[] { "hud.projecthero.ironman.ability.mk6_shield", "hud.projecthero.ironman.ability.mk6_surge",
				"hud.projecthero.ironman.ability.mk6_unibeam", "hud.projecthero.ironman.ability.mk6_barrage",
				"hud.projecthero.ironman.ability.mk7_shield", "hud.projecthero.ironman.ability.mk7_unibeam", "hud.projecthero.ironman.mk6_surge",
				"message.projecthero.ironman.mk6.surge_on", "message.projecthero.ironman.mk6.surge_off",
				"message.projecthero.ironman.mk7.laser_firing", "message.projecthero.ironman.orbital_catch",
				"message.projecthero.ironman.orbital_fallback", "projecthero.guide.iron_man.mark_6.body",
				"projecthero.guide.iron_man.mark_vii.body" }) {
			t(key);
		}
		h.assertTrue(t("hud.projecthero.ironman.mk6_surge").length() <= 6, "the HUD meter label is short");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void surgeBoostsDamageAndSpeed(GameTestHelper h) {
		ServerPlayer p = suited(h, M6);
		Pig before = mob(h, EntityType.PIG, p, 2.0, 2.0);
		Pig during = mob(h, EntityType.PIG, p, -2.0, 2.0);
		Pig after = mob(h, EntityType.PIG, p, 0.0, 3.0);
		// no surge: a 4-damage hit takes 4
		before.hurt(p.damageSources().playerAttack(p), 4f);
		h.assertTrue(Math.abs(before.getMaxHealth() - before.getHealth() - 4f) < 0.01f, "plain hit: 4, got " + (before.getMaxHealth() - before.getHealth()));

		float e0 = IronManEnergy.energy(p, M6);
		press(p, AbilitySlot.SLOT_3);
		h.assertTrue(IronManMark6.surging(p), "X starts the Arc Reactor Surge");
		float startCost = IronManMark6.SURGE_START_ENERGY * IronManSuits.MARK_6.energyCostMultiplier();
		h.assertTrue(Math.abs(e0 - IronManEnergy.energy(p, M6) - startCost) < 0.01f, "it costs " + startCost + " to start");
		var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		h.assertTrue(speed.getModifier(ProjectHeroMod.id("mk6_surge_speed")) != null, "+50% walk speed while surging");
		TonyStarkState synced = p.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		h.assertTrue(IronManMark6.flightSpeedMultiplier(synced, M6, p.level().getGameTime()) == 1.5,
				"the client's flight reads x1.5 off the synced surge timer");
		h.assertTrue(IronManMark6.flightSpeedMultiplier(synced, M7, p.level().getGameTime()) == 1.0, "only for the Mark 6");

		during.hurt(p.damageSources().playerAttack(p), 4f);
		h.assertTrue(Math.abs(during.getMaxHealth() - during.getHealth() - 6f) < 0.01f,
				"surging: the same hit takes 6 (+50%), got " + (during.getMaxHealth() - during.getHealth()));

		press(p, AbilitySlot.SLOT_3); // X again ends it early
		h.assertFalse(IronManMark6.surging(p), "X again ends the surge");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManMark6.SURGE) == IronManMark6.SURGE_COOLDOWN, "40 s cooldown after");
		h.assertTrue(speed.getModifier(ProjectHeroMod.id("mk6_surge_speed")) == null, "speed boost removed");
		after.hurt(p.damageSources().playerAttack(p), 4f);
		h.assertTrue(Math.abs(after.getMaxHealth() - after.getHealth() - 4f) < 0.01f, "back to 4 afterwards");
		press(p, AbilitySlot.SLOT_3);
		h.assertFalse(IronManMark6.surging(p), "can't restart while cooling down");
		before.discard();
		during.discard();
		after.discard();
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void surgeDrainsAndEnds(GameTestHelper h) {
		ServerPlayer p = suited(h, M6);
		press(p, AbilitySlot.SLOT_3);
		float e0 = IronManEnergy.energy(p, M6);
		for (int i = 0; i < 20; i++) {
			IronManMark6.tickForTest(p, IronManSuits.MARK_6);
		}
		float drained = e0 - IronManEnergy.energy(p, M6);
		float expected = 20 * IronManMark6.SURGE_ENERGY_PER_TICK * IronManSuits.MARK_6.energyCostMultiplier();
		h.assertTrue(Math.abs(drained - expected) < 0.05f, "drains " + expected + " a second, drained " + drained);
		h.assertTrue(IronManMark6.surging(p), "still surging after 1 s");

		// out of power -> it ends and cools down
		IronManEnergy.setEnergy(p, M6, 1f);
		IronManMark6.tickForTest(p, IronManSuits.MARK_6);
		h.assertFalse(IronManMark6.surging(p), "an empty suit ends the surge");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManMark6.SURGE) == IronManMark6.SURGE_COOLDOWN, "and starts the cooldown");

		// the timer runs out -> it ends on its own
		TonyStarkState s = TonyStark.state(p).copy();
		s.abilityReadyAt.remove(M6 + "/" + IronManMark6.SURGE);
		s.abilityReadyAt.put(IronManMark6.SURGE_KEY, p.level().getGameTime()); // expires now
		p.setAttached(ModAttachments.TONY_STARK_STATE, s);
		IronManEnergy.setEnergy(p, M6, 5000f);
		IronManMark6.tickForTest(p, IronManSuits.MARK_6);
		h.assertFalse(TonyStark.state(p).abilityReadyAt.containsKey(IronManMark6.SURGE_KEY), "the 10 s timer ends it");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManMark6.SURGE) == IronManMark6.SURGE_COOLDOWN, "cooldown again");

		// taking the chestplate off mid-surge ends it too
		s = TonyStark.state(p).copy();
		s.abilityReadyAt.remove(M6 + "/" + IronManMark6.SURGE);
		p.setAttached(ModAttachments.TONY_STARK_STATE, s);
		press(p, AbilitySlot.SLOT_3);
		h.assertTrue(IronManMark6.surging(p), "surging again");
		IronManMark6.shutDown(p);
		h.assertFalse(IronManMark6.surging(p), "suit shutdown ends it");
		h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ProjectHeroMod.id("mk6_surge_speed")) == null, "no stuck speed");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markSixShieldBeamAndBarrage(GameTestHelper h) {
		ServerPlayer p = suited(h, M6);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		h.assertTrue(IronManAbilities.barrierActive(p), "holding G raises the 360 shield");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		h.assertFalse(IronManAbilities.barrierActive(p), "release drops it");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManAbilities.REPULSOR_BARRIER) > 0, "barrier cooldown");
		h.assertTrue(IronManAbilities.REPULSOR_BARRIER.equals(IronManMark6.hudCooldownId(IronManMark6.SHIELD))
				&& IronManAbilities.REPULSOR_BARRIER.equals(IronManMark6.hudCooldownId(IronManMark7.SHIELD)), "the HUD box reads it");

		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		h.assertTrue(IronManHeldBeam.firing(p), "holding Z fires the Unibeam");
		IronManMark6.tickForTest(p, IronManSuits.MARK_6);
		h.assertTrue(IronManHeldBeam.firing(p), "still firing while held");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
		h.assertFalse(IronManHeldBeam.firing(p), "release stops it");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManMark6.UNIBEAM) == IronManMark6.UNIBEAM_COOLDOWN, "18 s cooldown");

		press(p, AbilitySlot.SLOT_5);
		h.assertTrue(IronManMark6.pendingBarrage(p) == IronManMark6.BARRAGE_COUNT - 1, "V launches the first of 6 barrage missiles");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M6, IronManMark6.BARRAGE) == IronManMark6.BARRAGE_COOLDOWN, "14 s cooldown");
		List<IronManMissileEntity> mine = h.getLevel().getEntitiesOfClass(IronManMissileEntity.class, p.getBoundingBox().inflate(6),
				m -> m.getOwner() == p);
		h.assertTrue(mine.size() == 1 && mine.get(0).directDamage() == IronManMark6.BARRAGE_DAMAGE && mine.get(0).isHoming(),
				"a homing 20-damage missile is away");
		mine.forEach(m -> m.discard());
		IronManMark6.shutDown(p);
		h.assertTrue(IronManMark6.pendingBarrage(p) == 0, "shutdown cancels the rest");
		h.succeed();
	}

	// ------------------------------------------------------------------ Mark 7

	@GameTest(template = EMPTY_STRUCTURE)
	public void markSevenWheelHitsAsHardAsTheMarkThree(GameTestHelper h) {
		var m7 = IronManSuits.MARK_VII;
		h.assertTrue(IronManMark7.SHIELD.equals(m7.abilityInSlot(2)) && IronManAbilities.WEAPON_WHEEL_SLOT.equals(m7.abilityInSlot(3))
				&& IronManMark7.UNIBEAM.equals(m7.abilityInSlot(4)) && IronManAbilities.WEAPON_WHEEL.equals(m7.abilityInSlot(5)),
				"G shield, X wheel weapon, Z held Unibeam, V wheel");
		h.assertTrue(IronManAbilities.WEAPON_WHEEL_SECTORS.length == 7, "the 7-wedge wheel stays");
		h.assertTrue(m7.missileCount() * m7.missileDamage() >= IronManMark3.MICRO_COUNT * IronManMark3.MICRO_DAMAGE, "micro volley");
		h.assertTrue(IronManAbilities.HOMING_MISSILE_COUNT * m7.missileDamage() >= IronManMark3.MICRO_COUNT * IronManMark3.MICRO_DAMAGE,
				"homing volley");
		h.assertTrue(IronManMark7.ROCKET_DAMAGE >= IronManMark3.ROCKET_DAMAGE, "rocket");
		h.assertTrue(IronManMark7.UNIBEAM_DAMAGE >= IronManMark3.UNIBEAM_DAMAGE, "unibeam");
		h.assertTrue(m7.flamethrowerDamagePerSecond() >= IronManSuits.MARK_1.flamethrowerDamagePerSecond(), "flamethrower");
		h.assertTrue(m7.repulsorDamage() >= IronManSuits.MARK_III.repulsorDamage() && m7.hasDash(), "repulsor + dash");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markSevenRocketAndLaser(GameTestHelper h) {
		ServerPlayer p = suited(h, M7);
		TonyStark.setWeaponWheelChoice(p, IronManAbilities.ROCKET);
		press(p, AbilitySlot.SLOT_3);
		List<IronManMissileEntity> rockets = h.getLevel().getEntitiesOfClass(IronManMissileEntity.class, p.getBoundingBox().inflate(6),
				m -> m.getOwner() == p);
		h.assertTrue(rockets.size() == 1 && rockets.get(0).directDamage() == IronManMark7.ROCKET_DAMAGE, "X fires a 40-damage rocket");
		h.assertTrue(TonyStark.abilityCooldownRemaining(p, M7, IronManAbilities.ROCKET) == IronManMark7.ROCKET_COOLDOWN, "10 s cooldown");
		rockets.forEach(m -> m.discard());

		TonyStark.setWeaponWheelChoice(p, IronManAbilities.WRIST_LASER);
		press(p, AbilitySlot.SLOT_3);
		h.assertTrue(IronManMark7.laserFiring(p), "X fires the wrist laser");
		IronManMark7.tickForTest(p, IronManSuits.MARK_VII);
		h.assertTrue(IronManMark7.laserFiring(p), "still firing a tick later");
		// the beam runs on game time: check it once its 3 s are up
		h.runAfterDelay(IronManMark7.LASER_TICKS + 1, () -> {
			IronManMark7.tickForTest(p, IronManSuits.MARK_VII);
			h.assertFalse(IronManMark7.laserFiring(p), "it stops after 3 s");
			int laserCd = TonyStark.abilityCooldownRemaining(p, M7, IronManAbilities.WRIST_LASER);
			h.assertTrue(laserCd > IronManMark7.LASER_COOLDOWN - 5 && laserCd <= IronManMark7.LASER_COOLDOWN, "20 s cooldown, got " + laserCd);
			h.assertFalse(TonyStark.overloaded(p), "no systems overload on the Mark 7");
			h.assertFalse(TonyStark.wristLaserSpent(p, M7), "and no one-shot-until-docked rule");

			IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
			h.assertTrue(IronManHeldBeam.firing(p), "Z holds the Unibeam");
			IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
			h.assertTrue(TonyStark.abilityCooldownRemaining(p, M7, IronManMark7.UNIBEAM) == IronManMark7.UNIBEAM_COOLDOWN, "15 s cooldown");
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ the orbital drop

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void orbitalDropCatchesAnAirbornePlayer(GameTestHelper h) {
		ServerPlayer p = bare(h);
		p.setOnGround(false); // falling
		h.setBlock(new BlockPos(3, 1, 3), IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(new BlockPos(3, 1, 3));
		be.bindTo(p.getUUID());
		for (ArmorItem.Type t : TYPES) {
			be.store(new ItemStack(IronManItems.armor(M7, t)));
		}
		// a loaded platform nearby: the pod streaks straight off it, homes onto the falling player and catches them
		IronManSuitCall.execute(p, M7, IronManSuitListPayload.SOURCE_PLATFORM);
		List<IronManDeliveryPodEntity> pods = h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, sky(h), podsOf(p));
		h.assertTrue(pods.size() == 1 && pods.get(0).cargo().size() == 4, "one pod carrying the whole suit");
		IronManDeliveryPodEntity pod = pods.get(0);
		boolean[] caught = { false };
		h.onEachTick(() -> caught[0] |= pod.catching());
		h.succeedWhen(() -> {
			h.assertTrue(wearing(p, M7), "the pod clamps the full Mark 7 onto the airborne player");
			h.assertTrue(caught[0], "it caught them mid-air (no landing)");
			h.assertTrue(h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, sky(h), podsOf(p)).isEmpty(), "and flies off");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void orbitalDropFromThePackWhileAirborne(GameTestHelper h) {
		ServerPlayer p = bare(h);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor(M7, t)));
		}
		p.setOnGround(true);
		h.assertFalse(IronManSuitCall.orbitalDrop(p, IronManSuits.MARK_VII), "on the ground the pack suit builds normally");
		h.assertFalse(IronManSuitCall.orbitalDrop(p, IronManSuits.MARK_III), "only the pod suit drops from orbit");
		// genuinely airborne: hovering 6 blocks up with no gravity, so a physics tick can't land the player mid-descent
		// (landing would switch the pod to its slower ground delivery and run past the timeout)
		p.setNoGravity(true);
		p.teleportTo(p.getX(), p.getY() + 6.0, p.getZ());
		p.setOnGround(false);
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(p), "C while airborne calls the Mark 7");
		List<IronManDeliveryPodEntity> pods = h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, sky(h), podsOf(p));
		h.assertTrue(pods.size() == 1 && pods.get(0).cargo().size() == 4, "...by pod, with the four pieces out of the pack");
		h.assertTrue(pods.get(0).getY() >= p.getY() + IronManDeliveryPodEntity.COVERED_HEIGHT - 1.0,
				"the pod starts high above the player (y " + pods.get(0).getY() + " vs " + p.getY() + ")");
		h.succeedWhen(() -> h.assertTrue(wearing(p, M7), "and the suit ends up worn"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unreachablePodFallsBackToTheStagedSuitUp(GameTestHelper h) {
		ServerPlayer p = bare(h);
		p.setOnGround(true);
		List<ItemStack> pieces = new ArrayList<>();
		for (ArmorItem.Type t : TYPES) {
			pieces.add(new ItemStack(IronManItems.armor(M7, t)));
		}
		IronManDeliveryPodEntity pod = IronManDeliveryPodEntity.spawn(h.getLevel(), p, pieces, null);
		Vec3 start = pod.position();
		h.assertTrue(start.y > p.getY() + 20.0, "an orbital start high above");
		pod.giveUp(p);
		h.assertTrue(pod.isRemoved() && pod.cargo().isEmpty(), "the pod hands everything over and leaves");
		h.assertTrue(IronManSuitUpManager.inTransition(p), "the ordinary staged suit-up starts instead");
		for (int i = 0; i < 400 && IronManSuitUpManager.inTransition(p); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(wearing(p, M7), "and finishes with the whole suit on -- nothing stranded");
		h.assertTrue(IronManArmor.wearingFullSuit(p, M7), "full suit");
		int strays = 0;
		for (ItemStack s : p.getInventory().items) {
			strays += s.getItem() instanceof IronManArmorItem ? 1 : 0;
		}
		h.assertTrue(strays == 0, "no duplicate pieces left in the pack");
		h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getCount() == 1, "one chestplate");
		h.succeed();
	}
}

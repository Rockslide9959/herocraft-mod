package com.projecthero.mod.gametest;

import com.projecthero.mod.item.ModArmorMaterials;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteCarnageCall;
import com.projecthero.mod.symbiote.SymbioteSuitResistance;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;
import com.projecthero.mod.symbiote.entity.SymbioteEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15 Symbiote: the Normal host's suit is diamond-grade and grants Resistance I while it is on (never stomping a
 * stronger one), and holding right-click on a free Symbiote for 5 s calls a Carnage meteor (cancelled by letting go,
 * 10-minute cooldown, flint and steel unchanged).
 */
public class SymbioteV01515GameTests implements FabricGameTest {
	private static final String CALL_BATCH = "v01515_symbiote_carnage_call";

	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(player);
		return player;
	}

	private static void suitUp(ServerPlayer player) {
		Symbiote.state(player).toggleReadyAt = 0L;
		Symbiote.toggle(player);
		Symbiote.state(player).transformStartTick -= 10_000L;
		Symbiote.tick(player);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void normalHostSuitIsDiamondGrade(GameTestHelper helper) {
		var m = ModArmorMaterials.SYMBIOTE_HOST.value();
		helper.assertTrue(m.defense().get(ArmorItem.Type.HELMET) == 3 && m.defense().get(ArmorItem.Type.CHESTPLATE) == 8
				&& m.defense().get(ArmorItem.Type.LEGGINGS) == 6 && m.defense().get(ArmorItem.Type.BOOTS) == 3,
				"diamond 3/8/6/3");
		helper.assertTrue(m.toughness() == 2.0f, "diamond toughness 2");
		ServerPlayer player = host(helper);
		suitUp(player);
		helper.assertTrue(Symbiote.isActive(player), "precondition: suited");
		int armour = 0;
		float toughness = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (player.getItemBySlot(slot).getItem() instanceof ArmorItem a) {
				armour += a.getDefense();
				toughness += a.getToughness();
			}
		}
		helper.assertTrue(armour == 20, "20 armour worn, got " + armour);
		helper.assertTrue(toughness == 8.0f, "2 toughness per piece, got " + toughness);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitGrantsResistanceIWhileOn(GameTestHelper helper) {
		ServerPlayer player = host(helper);
		Symbiote.tick(player);
		helper.assertTrue(player.getEffect(MobEffects.DAMAGE_RESISTANCE) == null, "no Resistance with the suit off");
		suitUp(player);
		Symbiote.tick(player);
		MobEffectInstance res = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == 0 && res.isAmbient() && !res.isVisible(),
				"ambient, particle-free Resistance I while suited");
		// retract: it goes with the suit
		Symbiote.state(player).toggleReadyAt = 0L;
		Symbiote.toggle(player);
		Symbiote.state(player).transformStartTick -= 10_000L;
		Symbiote.tick(player);
		helper.assertFalse(Symbiote.isActive(player), "precondition: suit off");
		helper.assertTrue(player.getEffect(MobEffects.DAMAGE_RESISTANCE) == null, "Resistance removed with the suit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitResistanceNeverStompsAStrongerOne(GameTestHelper helper) {
		ServerPlayer player = host(helper);
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 2));
		suitUp(player);
		for (int i = 0; i < 5; i++) {
			SymbioteSuitResistance.tick(player);
		}
		MobEffectInstance res = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == 2, "the stronger Resistance III is untouched");
		Symbiote.state(player).toggleReadyAt = 0L;
		Symbiote.toggle(player);
		Symbiote.state(player).transformStartTick -= 10_000L;
		Symbiote.tick(player);
		res = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == 2, "and is not removed with the suit either");
		helper.succeed();
	}

	private static SymbioteEntity blob(GameTestHelper helper) {
		SymbioteEntity blob = SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		blob.moveTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
		blob.setNoGravity(true);
		helper.getLevel().addFreshEntity(blob);
		return blob;
	}

	private static ServerPlayer caller(GameTestHelper helper) {
		ServerPlayer player = host(helper);
		Vec3 at = helper.absoluteVec(new Vec3(1.0, 1.0, 2.5));
		player.moveTo(at.x, at.y, at.z, -90.0f, 30.0f);
		SymbioteCarnageCall.resetCooldown(player);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = CALL_BATCH, timeoutTicks = 200)
	public void holdingRightClickForFiveSecondsCallsCarnage(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		SymbioteEntity blob = blob(helper);
		ServerPlayer player = caller(helper);
		// no real meteor in the shared test world: record the call instead (play uses CarnageSpawner.dropNear)
		int[] drops = { 0 };
		SymbioteCarnageCall.dropper = (level, who, min, max) -> {
			if (who == player && min == SymbioteCarnageCall.DROP_MIN && max == SymbioteCarnageCall.DROP_MAX) {
				drops[0]++;
			}
			return who == player;
		};
		// hold the use key: the client re-sends the interaction every 4 ticks
		for (int t = 1; t <= SymbioteCarnageCall.CHANNEL_TICKS + 8; t += 4) {
			helper.runAtTickTime(t, () -> {
				Vec3 at = helper.absoluteVec(new Vec3(1.0, 1.0, 2.5));
				player.moveTo(at.x, at.y, at.z, -90.0f, 30.0f);
				blob.interact(player, InteractionHand.MAIN_HAND);
			});
		}
		helper.runAtTickTime(40, () -> {
			helper.assertTrue(blob.carnageCaller() != null && blob.carnageCaller().equals(player.getUUID()), "channelling");
			helper.assertTrue(blob.crimson() > 0.1f && blob.crimson() < 1.0f, "reddening, got " + blob.crimson());
			helper.assertFalse(blob.isConsumed(), "not done after 2 s");
		});
		helper.runAtTickTime(SymbioteCarnageCall.CHANNEL_TICKS + 12, () -> {
			helper.assertTrue(blob.isConsumed() || blob.isRemoved(), "the Symbiote is consumed");
			helper.assertTrue(drops[0] == 1, "exactly one Carnage meteor called, 12-20 blocks out, got " + drops[0]);
			helper.assertTrue(SymbioteCarnageCall.readyAt(player) > helper.getLevel().getGameTime() + 11_000L,
					"10-minute cooldown");
			// on cooldown: a second Symbiote will not start a new call
			SymbioteEntity second = blob(helper);
			second.interact(player, InteractionHand.MAIN_HAND);
			helper.assertTrue(second.carnageCaller() == null, "no second call while on cooldown");
			second.discard();
		});
		helper.runAtTickTime(SymbioteCarnageCall.CHANNEL_TICKS + 12 + SymbioteCarnageCall.CONSUME_TICKS + 4, () -> {
			helper.assertTrue(blob.isRemoved(), "it dissolved away");
			SymbioteCarnageCall.dropper = com.projecthero.mod.carnage.CarnageSpawner::dropNear;
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = CALL_BATCH, timeoutTicks = 80)
	public void releasingCancelsTheCall(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		SymbioteEntity blob = blob(helper);
		ServerPlayer player = caller(helper);
		helper.runAtTickTime(1, () -> blob.interact(player, InteractionHand.MAIN_HAND));
		helper.runAtTickTime(5, () -> blob.interact(player, InteractionHand.MAIN_HAND));
		helper.runAtTickTime(8, () -> helper.assertTrue(blob.carnageCaller() != null, "channel started"));
		helper.runAtTickTime(30, () -> {
			helper.assertTrue(blob.carnageCaller() == null, "letting go cancels it");
			helper.assertFalse(blob.isConsumed() || blob.isRemoved(), "and the Symbiote is untouched");
			helper.assertTrue(blob.crimson() == 0.0f, "its colour comes back");
			helper.assertTrue(SymbioteCarnageCall.readyAt(player) == 0L, "no cooldown for a cancelled call");
			blob.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = CALL_BATCH)
	public void flintAndSteelStillBurnsForAHost(GameTestHelper helper) {
		SymbioteEntity blob = blob(helper);
		ServerPlayer player = caller(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
		blob.interact(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(blob.isBurning(), "flint and steel still sets it alight");
		helper.assertTrue(blob.carnageCaller() == null, "and does not start a call");
		blob.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = CALL_BATCH)
	public void anUnbondedPlayerCannotCall(GameTestHelper helper) {
		SymbioteEntity blob = blob(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.0, 1.0, 2.5));
		player.moveTo(at.x, at.y, at.z, -90.0f, 30.0f);
		helper.assertFalse(SymbioteCarnageCall.canChannel(player), "no Symbiote, no call");
		blob.discard();
		helper.succeed();
	}
}

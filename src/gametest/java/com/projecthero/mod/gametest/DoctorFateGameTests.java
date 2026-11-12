package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.DoctorFateAbilityManager;
import com.projecthero.mod.doctorfate.DoctorFateBargains;
import com.projecthero.mod.doctorfate.DoctorFateConfig;
import com.projecthero.mod.doctorfate.DoctorFateDominion;
import com.projecthero.mod.doctorfate.DoctorFateItems;
import com.projecthero.mod.doctorfate.DoctorFateMod;
import com.projecthero.mod.doctorfate.DoctorFatePossession;
import com.projecthero.mod.doctorfate.DoctorFateSpells;
import com.projecthero.mod.doctorfate.data.FateAttachments;
import com.projecthero.mod.doctorfate.data.FateState;
import com.projecthero.mod.doctorfate.network.FateActionPayload;
import com.projecthero.mod.doctorfate.worldgen.HelmetPedestalBlock;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate: the Tomb pedestal's offer and the grant, the helmet's stats, Dominion's gain and drain per tier (step sizes,
 * not totals), Override and Nabu's no-harm rule, Possession (start, struggle, end, lockout), the bargains (death's door, Order's
 * command, the Forbidden spell, meaner after refusals) and every spell's damage. Mock players are not ticked by the server, so tests
 * over time drive {@link DoctorFate#tick} themselves (one onEachTick registered at the start; every check a runAfterDelay registered at
 * the start too). Mob hits are asserted as ratios of the expected damage (zombies wear a little armour; other tests may leave Hard).
 */
public class DoctorFateGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer fate(GameTestHelper helper, boolean helmet) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		helper.assertTrue(DoctorFate.grant(p), "the grant works");
		if (helmet) {
			DoctorFate.setHelmet(p, true);
		}
		DoctorFate.clearCooldowns(p);
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<FateState> change) {
		FateState n = DoctorFate.state(p).copy();
		change.accept(n);
		p.setAttached(FateAttachments.STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2.0, z));
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zb.setHealth((float) maxHealth);
		zb.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zb.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000, 0, false, false));
		return zb;
	}

	private static void look(ServerPlayer p, net.minecraft.world.entity.Entity e) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, e.getBoundingBox().getCenter());
	}

	private static void ratio(GameTestHelper helper, float lost, float expected, String what) {
		float r = lost / expected;
		helper.assertTrue(r >= 0.75f && r <= 1.25f, what + ": expected about " + expected + " damage, dealt " + lost);
	}

	// ---------------------------------------------------------------- getting it

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pedestalOfferGrantsAndRefusalLeavesTheHelmet(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 1.5, 1.5);
		ServerPlayer b = mock(helper, 2.5, 1.5);
		com.projecthero.mod.kryptonian.Kryptonian.grant(a);
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, DoctorFateItems.HELMET_PEDESTAL.defaultBlockState().setValue(HelmetPedestalBlock.HAS_HELMET, true));
		BlockPos pos = helper.absolutePos(rel);
		helper.assertTrue(!DoctorFateMod.serve(b, pos, false), "refusing grants nothing");
		helper.assertTrue(!DoctorFate.hasPower(b), "refused: no power");
		helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(HelmetPedestalBlock.HAS_HELMET), "refusing leaves the helmet on its pedestal");
		helper.assertTrue(DoctorFateMod.serve(a, pos, true), "accepting serves Order");
		helper.assertTrue(DoctorFate.hasPower(a) && HeroTiers.holdsHero(a, DoctorFate.KEY), "Doctor Fate is a Hero-Tier power");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(a), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(!helper.getLevel().getBlockState(pos).getValue(HelmetPedestalBlock.HAS_HELMET), "the pedestal is empty now");
		for (int i = 0; i < a.getInventory().getContainerSize(); i++) {
			ItemStack st = a.getInventory().getItem(i);
			helper.assertTrue(st.isEmpty() || !BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().contains("helmet_of_fate"),
					"no helmet item lands in his inventory (H wears it)");
		}
		helper.assertTrue(!DoctorFate.wearsHelmet(a), "he doesn't wear it until he presses H");
		helper.assertTrue(!DoctorFateMod.serve(b, pos, true) && !DoctorFate.hasPower(b), "an empty pedestal offers nothing");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(DoctorFate.KEY), "random serums never give it");
		DoctorFate.revoke(a);
		helper.assertTrue(!DoctorFate.hasPower(a), "revoked");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHelmetOfFateIsNoItem(GameTestHelper helper) {
		helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(ProjectHeroMod.id("helmet_of_fate")), "no helmet_of_fate item is registered");
		// an old test world's saved stack: an unknown id now, dropped on load instead of crashing
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString("id", "projecthero:helmet_of_fate");
		tag.putInt("count", 1);
		helper.assertTrue(ItemStack.parseOptional(helper.getLevel().registryAccess(), tag).isEmpty(), "an old saved helmet loads as nothing");
		ServerPlayer p = mock(helper, 1.5, 1.5);
		net.minecraft.nbt.ListTag inv = new net.minecraft.nbt.ListTag();
		net.minecraft.nbt.CompoundTag slot = tag.copy();
		slot.putByte("Slot", (byte) 0);
		inv.add(slot);
		p.getInventory().load(inv);
		helper.assertTrue(p.getInventory().getItem(0).isEmpty(), "an inventory holding one loads without it");
		// V is no longer the helmet: Sight of Nabu, and Shift+V the Amulet of Anubis
		helper.assertTrue(DoctorFateAbilityManager.SIGHT.equals(DoctorFateAbilityManager.moveFor(com.projecthero.mod.hero.AbilitySlot.SLOT_5, false))
				&& DoctorFateAbilityManager.AMULET.equals(DoctorFateAbilityManager.moveFor(com.projecthero.mod.hero.AbilitySlot.SLOT_5, true)),
				"V = Sight of Nabu, Shift+V = Amulet of Anubis");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void helmetTakesASecondAndGivesFortyHealthAndResistance(GameTestHelper helper) {
		ServerPlayer p = fate(helper, false);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f, "normal health without the helmet");
		DoctorFateMod.onAction(p, FateActionPayload.of(FateActionPayload.Action.TOGGLE_HELMET));
		helper.assertTrue(DoctorFate.state(p).helmetChangeAt > 0L && DoctorFate.state(p).helmetGoingOn, "H starts putting it on");
		helper.assertTrue(!DoctorFate.wearsHelmet(p), "not on yet (1 s)");
		helper.onEachTick(() -> DoctorFate.tick(p));
		helper.runAfterDelay(DoctorFateConfig.HELMET_ANIM_TICKS + 3, () -> {
			helper.assertTrue(DoctorFate.wearsHelmet(p), "on after the 1 s animation");
			helper.assertTrue(Math.abs(p.getMaxHealth() - 40f) < 0.01f, "40 health, got " + p.getMaxHealth());
			p.setHealth(40f);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().magic(), 10f);
			helper.assertTrue(Math.abs(p.getHealth() - 33f) < 0.1f, "30% off a hit (10 -> 7), health " + p.getHealth());
			p.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
			p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
		});
		helper.runAfterDelay(DoctorFateConfig.HELMET_ANIM_TICKS + 6, () -> {
			helper.assertTrue(!p.hasEffect(MobEffects.WITHER) && !p.hasEffect(MobEffects.POISON), "immune to Wither and Poison with the helmet on");
			p.getInventory().clearContent();
		});
		helper.runAfterDelay(DoctorFateConfig.HELMET_ANIM_TICKS + 10, () -> {
			helper.assertTrue(DoctorFate.wearsHelmet(p), "an empty inventory changes nothing: the helmet is part of the power");
			DoctorFateMod.onAction(p, FateActionPayload.of(FateActionPayload.Action.TOGGLE_HELMET));
			helper.assertTrue(DoctorFate.state(p).helmetChangeAt > 0L && !DoctorFate.state(p).helmetGoingOn, "H again takes it off");
		});
		helper.runAfterDelay(2 * DoctorFateConfig.HELMET_ANIM_TICKS + 14, () -> {
			helper.assertTrue(!DoctorFate.wearsHelmet(p), "off after the 1 s animation");
			helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f, "back to normal health, got " + p.getMaxHealth());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Dominion

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void dominionRisesWornAndDrainsOff(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		DoctorFate.setDominion(p, 10f);
		int[] ticks = { 0 };
		float[] mark = { 0f };
		helper.onEachTick(() -> {
			DoctorFate.tick(p);
			ticks[0]++;
		});
		helper.runAfterDelay(1, () -> {
			ticks[0] = 0;
			mark[0] = DoctorFate.dominion(p);
		});
		helper.runAfterDelay(41, () -> {
			float step = (DoctorFate.dominion(p) - mark[0]) / Math.max(1, ticks[0]);
			helper.assertTrue(Math.abs(step - DoctorFateConfig.WEAR_GAIN_PER_TICK) < 1.0e-4f, "worn: +0.15 a second, step " + step);
			DoctorFate.setHelmet(p, false);
			DoctorFate.setDominion(p, 50f);
			ticks[0] = 0;
			mark[0] = 50f;
		});
		helper.runAfterDelay(81, () -> {
			float step = (mark[0] - DoctorFate.dominion(p)) / Math.max(1, ticks[0]);
			helper.assertTrue(Math.abs(step - DoctorFateConfig.OFF_DRAIN_PER_TICK) < 1.0e-4f, "off: -0.75 a second, step " + step);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tiersAndSpellCosts(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		helper.assertTrue(DoctorFate.tier(10f) == 0 && DoctorFate.tier(25f) == 1 && DoctorFate.tier(50f) == 2 && DoctorFate.tier(75f) == 3
				&& DoctorFate.tier(100f) == 4, "the five tiers at 0 / 25 / 50 / 75 / 100");
		// Whispers: full price
		DoctorFate.setDominion(p, 10f);
		DoctorFateSpells.testOverride = false;
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.ORDER_BLAST), "Order Blast casts");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - 14f) < 0.01f, "+4 at Whispers, got " + DoctorFate.dominion(p));
		// Pull: 10% less
		DoctorFate.clearCooldowns(p);
		DoctorFate.setDominion(p, 60f);
		DoctorFateSpells.testOverride = false;
		DoctorFateSpells.use(p, DoctorFateAbilityManager.ORDER_BLAST);
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - 63.6f) < 0.01f, "+3.6 at Pull, got " + DoctorFate.dominion(p));
		// Taking hold: health too
		DoctorFate.clearCooldowns(p);
		DoctorFate.setDominion(p, 80f);
		p.setHealth(30f);
		DoctorFateSpells.testOverride = false;
		DoctorFateSpells.use(p, DoctorFateAbilityManager.ORDER_BLAST);
		helper.assertTrue(Math.abs(p.getHealth() - 29f) < 0.01f, "Taking hold: a spell costs health, health " + p.getHealth());
		DoctorFate.clearCooldowns(p);
		p.setHealth(30f);
		DoctorFateSpells.use(p, DoctorFateAbilityManager.WARD);
		helper.assertTrue(Math.abs(p.getHealth() - 28f) < 0.01f, "a big spell (8+) costs twice, health " + p.getHealth());
		// the chat tag
		DoctorFate.setDominion(p, 55f);
		helper.assertTrue(DoctorFateDominion.chatTag(p, Component.literal("hi")).getString().contains("hi")
				&& DoctorFateDominion.chatTag(p, Component.literal("hi")) != null, "the chat line survives the tag");
		helper.assertTrue(DoctorFateDominion.chatTag(p, Component.literal("hi")).getString().length() > 2, "Pull tags his chat");
		DoctorFate.setDominion(p, 10f);
		helper.assertTrue(DoctorFateDominion.chatTag(p, Component.literal("hi")).getString().equals("hi"), "no tag below Pull");
		// helmet off: only R, G, X
		DoctorFate.setHelmet(p, false);
		DoctorFate.clearCooldowns(p);
		helper.assertTrue(!DoctorFateSpells.use(p, DoctorFateAbilityManager.ORDER_BLAST), "Order Blast needs the helmet");
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT), "Kent's own Ankh Bolt works bare");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - 10f) < 0.01f, "Kent's own magic adds no Dominion");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void overrideAndNabuProtectsVillagers(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1.5, 2.0, 4.5));
		look(p, v);
		DoctorFate.setDominion(p, 10f);
		float before = v.getHealth();
		DoctorFateSpells.testOverride = false;
		DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT);
		helper.assertTrue(v.getHealth() < before, "below Override he can hurt a villager");
		v.setHealth(v.getMaxHealth());
		DoctorFate.clearCooldowns(p);
		DoctorFate.setDominion(p, 30f);
		DoctorFateSpells.testOverride = false;
		DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT);
		helper.assertTrue(v.getHealth() >= v.getMaxHealth(), "at Override the bolt fizzles on a villager");
		DoctorFate.clearCooldowns(p);
		helper.assertTrue(!DoctorFateSpells.use(p, DoctorFateAbilityManager.BANISHMENT) && v.isAlive(), "and Banishment fizzles on him");
		// Override: Nabu's pick -- Golden Chains on the most chaotic creature near
		Husk h = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(5.5, 2.0, 5.5));
		DoctorFate.clearCooldowns(p);
		DoctorFateSpells.testOverride = true;
		DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT);
		helper.assertTrue(DoctorFateSpells.isBound(h), "Nabu turned the bolt into Golden Chains on the husk (Chaos)");
		helper.assertTrue(DoctorFate.dominion(p) >= 30f + DoctorFateConfig.CHAINS_DOMINION - 0.01f, "it costs his spell's Dominion");
		DoctorFateSpells.testOverride = null;
		helper.succeed();
	}

	// ---------------------------------------------------------------- Possession

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void possessionStartsStrugglesEndsAndLocksTheHelmet(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		DoctorFate.setDominion(p, 100f);
		DoctorFatePossession.tickForTest(p);
		helper.assertTrue(DoctorFate.possessed(p), "100 Dominion with the helmet on: Possession");
		helper.assertTrue(DoctorFate.possessionTicksLeft(p) == DoctorFateConfig.POSSESSION_TICKS, "15 s");
		helper.assertTrue(!DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT), "his own keys do nothing");
		helper.assertTrue(!DoctorFate.pressH(p) && DoctorFate.wearsHelmet(p), "H can't take the helmet off");
		int before = DoctorFate.possessionTicksLeft(p);
		helper.assertTrue(DoctorFatePossession.struggle(p), "Space struggles");
		helper.assertTrue(before - DoctorFate.possessionTicksLeft(p) == DoctorFateConfig.STRUGGLE_TICKS, "each press takes 0.5 s off");
		helper.assertTrue(!DoctorFatePossession.struggle(p), "presses closer than 2 ticks don't stack");
		DoctorFatePossession.end(p);
		helper.assertTrue(!DoctorFate.possessed(p) && !DoctorFate.wearsHelmet(p), "afterwards the helmet falls off");
		helper.assertTrue(DoctorFate.lockoutTicksLeft(p) == DoctorFateConfig.LOCKOUT_TICKS, "60 s lockout");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - DoctorFateConfig.AFTER_POSSESSION_DOMINION) < 0.01f, "Dominion settles at 70");
		helper.assertTrue(!DoctorFate.pressH(p) && DoctorFate.state(p).helmetChangeAt == 0L, "H won't put it back on during the lockout");
		DoctorFate.clearCooldowns(p); // clears the lockout too
		helper.assertTrue(DoctorFate.pressH(p), "after the lockout H puts it on again");
		DoctorFate.setDominion(p, 0f);
		p.discard(); // a possessed mock player left in the world would keep casting at the next test's mobs
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void nabuFightsTheStrongestHostileNeverTheProtected(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5, 2.0, 2.5));
		Zombie weak = zombie(helper, 5.5, 2.5, 20.0);
		Zombie strong = zombie(helper, 2.5, 5.5, 100.0);
		// (in a batch a neighbouring test can hold an even stronger hostile in range, so only check it is never the villager or the weak one)
		var picked = DoctorFatePossession.target(p);
		helper.assertTrue(picked != null && picked != v && picked != weak && picked.getMaxHealth() >= strong.getMaxHealth(), "Nabu picks the strongest hostile");
		DoctorFate.setDominion(p, 100f);
		DoctorFatePossession.tickForTest(p);
		float before = strong.getHealth();
		DoctorFatePossession.castNow(p, strong); // the first in turn: Ankh Bolt
		ratio(helper, before - strong.getHealth(), DoctorFateConfig.BOLT_DAMAGE * DoctorFateConfig.POSSESSION_DAMAGE_MULT, "Nabu's bolt (huge damage)");
		helper.assertTrue(v.getHealth() >= v.getMaxHealth() && weak.isAlive(), "the villager untouched");
		DoctorFate.setDominion(p, 0f);
		p.discard(); // a possessed mock player left in the world would keep casting at the next test's mobs
		helper.succeed();
	}

	// ---------------------------------------------------------------- bargains

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deathsDoorAndMeanerAfterRefusals(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		DoctorFate.setDominion(p, 10f);
		p.setHealth(5f);
		DoctorFateBargains.onHurt(p);
		helper.assertTrue(DoctorFateBargains.pending(p) == FateState.Bargain.DEATHS_DOOR, "3 hearts or fewer: \"Let me save you\"");
		DoctorFateBargains.answer(p, FateState.Bargain.DEATHS_DOOR, false);
		helper.assertTrue(DoctorFateBargains.refusals(p) == 1 && Math.abs(DoctorFate.dominion(p) - 10f) < 0.01f, "refused: nothing, but counted");
		helper.assertTrue(DoctorFateBargains.deathsDoorCost(1) > DoctorFateBargains.deathsDoorCost(0)
				&& DoctorFateBargains.deathsDoorShield(1) > DoctorFateBargains.deathsDoorShield(0), "the next offer is meaner: more cost, more reward");
		edit(p, s -> s.bargain = s.bargain.withLastOffer(0L));
		DoctorFateBargains.onHurt(p);
		helper.assertTrue(DoctorFateBargains.pending(p) == FateState.Bargain.DEATHS_DOOR, "offered again");
		DoctorFateBargains.answer(p, FateState.Bargain.DEATHS_DOOR, true);
		helper.assertTrue(Math.abs(p.getHealth() - p.getMaxHealth()) < 0.01f, "accepted: full heal");
		helper.assertTrue(p.hasEffect(MobEffects.ABSORPTION), "and a golden shield");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - (10f + DoctorFateBargains.deathsDoorCost(1))) < 0.01f,
				"+35 (+10 for the refusal), got " + DoctorFate.dominion(p));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void ignoringAnOfferRefusesIt(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		helper.assertTrue(DoctorFateBargains.offer(p, FateState.Bargain.DEATHS_DOOR), "offered");
		edit(p, s -> s.bargain = s.bargain.withPending(FateState.Bargain.DEATHS_DOOR, p.level().getGameTime() + 2));
		helper.onEachTick(() -> DoctorFate.tick(p));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(DoctorFateBargains.pending(p) == FateState.Bargain.NONE_KIND, "the 10 s ran out");
			helper.assertTrue(DoctorFateBargains.refusals(p) == 1, "ignoring counts as refusing");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ordersCommandAndTheAnkhCharm(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		DoctorFate.setDominion(p, 60f);
		helper.assertTrue(DoctorFateBargains.offer(p, FateState.Bargain.ORDERS_COMMAND), "offered");
		DoctorFateBargains.answer(p, FateState.Bargain.ORDERS_COMMAND, true);
		helper.assertTrue(DoctorFate.state(p).bargain.questActive() && DoctorFate.state(p).bargain.questNeed() == 5, "banish 5 before dawn");
		for (int i = 0; i < 5; i++) {
			Husk h = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, 2.0, 4.5));
			DoctorFateBargains.onKill(p, h);
			h.discard();
		}
		helper.assertTrue(!DoctorFate.state(p).bargain.questActive(), "done");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - 30f) < 0.01f, "Dominion -30, got " + DoctorFate.dominion(p));
		helper.assertTrue(p.getInventory().contains(new ItemStack(DoctorFateItems.ANKH_CHARM)), "an Ankh Charm");
		// failing: dawn comes first
		helper.assertTrue(DoctorFateBargains.offer(p, FateState.Bargain.ORDERS_COMMAND), "offered again");
		DoctorFateBargains.answer(p, FateState.Bargain.ORDERS_COMMAND, true);
		edit(p, s -> s.bargain = s.bargain.withQuest(1, 5, p.level().getGameTime()));
		DoctorFateBargains.tickForTest(p);
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - 70f) < 0.01f, "failed: Dominion +40, got " + DoctorFate.dominion(p));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void forbiddenSpellAsksAndRaisesTheAvatar(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		DoctorFate.setDominion(p, 20f);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.AVATAR), "Shift+Z asks Nabu");
		helper.assertTrue(!DoctorFate.avatarActive(p) && DoctorFateBargains.pending(p) == FateState.Bargain.FORBIDDEN, "nothing until he answers");
		DoctorFateBargains.answer(p, FateState.Bargain.FORBIDDEN, true);
		helper.assertTrue(DoctorFate.avatarActive(p), "the Avatar of Order rises");
		helper.assertTrue(DoctorFate.dominion(p) >= DoctorFateConfig.FORBIDDEN_DOMINION - 0.01f, "Dominion to 90+");
		helper.assertTrue(DoctorFate.cooldownRemaining(p, DoctorFateAbilityManager.AVATAR) > 0, "3 min cooldown");
		// a slam where he looks: 40 damage
		Zombie z = zombie(helper, 1.5, 4.5, 100.0);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.position());
		float before = z.getHealth();
		DoctorFateAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		ratio(helper, before - z.getHealth(), DoctorFateConfig.AVATAR_SLAM_DAMAGE, "the Avatar's slam");
		helper.succeed();
	}

	// ---------------------------------------------------------------- spells

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ankhBoltPiercesOneAndHitsForTen(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		Zombie a = zombie(helper, 1.5, 3.5, 100.0);
		Zombie b = zombie(helper, 1.5, 5.5, 100.0);
		Zombie c = zombie(helper, 1.5, 6.6, 100.0);
		look(p, a);
		DoctorFateSpells.testOverride = false;
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT), "cast");
		ratio(helper, 100f - a.getHealth(), DoctorFateConfig.BOLT_DAMAGE, "the first");
		ratio(helper, 100f - b.getHealth(), DoctorFateConfig.BOLT_DAMAGE, "the one behind it");
		helper.assertTrue(c.getHealth() >= 100f, "only one pierced");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - DoctorFateConfig.BOLT_DOMINION) < 0.01f, "+1 Dominion");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chainsBindAndBlastKnocksBack(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		Zombie a = zombie(helper, 1.5, 4.5, 100.0);
		look(p, a);
		DoctorFateSpells.testOverride = false;
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.GOLDEN_CHAINS), "chains");
		ratio(helper, 100f - a.getHealth(), DoctorFateConfig.CHAINS_DAMAGE, "Golden Chains");
		helper.assertTrue(DoctorFateSpells.isBound(a), "bound");
		a.setHealth(100f);
		DoctorFateSpells.testOverride = false;
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.ORDER_BLAST), "blast");
		ratio(helper, 100f - a.getHealth(), DoctorFateConfig.BLAST_DAMAGE, "Order Blast");
		helper.assertTrue(a.getDeltaMovement().horizontalDistance() > 0.5, "big knockback");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void glyphStormRainsBlades(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		Husk a = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(4.5, 2.0, 4.5));
		a.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		a.setHealth(100f);
		a.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		helper.assertTrue(DoctorFateSpells.castGlyphStormForTest(p, a.position()), "the storm");
		helper.runAfterDelay(DoctorFateConfig.GLYPH_TICKS + 5, () -> {
			float lost = 100f - a.getHealth();
			int hits = DoctorFateConfig.GLYPH_TICKS / DoctorFateConfig.GLYPH_HIT_EVERY;
			helper.assertTrue(lost >= DoctorFateConfig.GLYPH_DAMAGE * 0.75f && lost <= DoctorFateConfig.GLYPH_DAMAGE * (hits + 1) * 1.25f,
					"4 damage per hit, lost " + lost);
			helper.assertTrue(Math.abs(lost / DoctorFateConfig.GLYPH_DAMAGE - Math.round(lost / DoctorFateConfig.GLYPH_DAMAGE)) < 0.3f,
					"in steps of 4, lost " + lost);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void banishmentDeletesMobsThrowsTheStrongNeverBosses(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		Zombie small = zombie(helper, 1.5, 4.5, 20.0);
		look(p, small);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.BANISHMENT), "banish");
		helper.assertTrue(small.isRemoved(), "a normal mob is banished (gone)");
		Zombie big = zombie(helper, 1.5, 4.5, 80.0);
		Vec3 from = big.position();
		look(p, big);
		DoctorFate.clearCooldowns(p);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.BANISHMENT), "banish the strong one");
		helper.assertTrue(big.isAlive() && big.position().distanceTo(from) > 30.0, "a strong mob is thrown 40 blocks, moved "
				+ big.position().distanceTo(from));
		big.discard();
		Zombie boss = zombie(helper, 1.5, 4.5, 400.0);
		look(p, boss);
		DoctorFate.clearCooldowns(p);
		helper.assertTrue(!DoctorFateSpells.use(p, DoctorFateAbilityManager.BANISHMENT) && boss.isAlive(), "bosses are immune");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - 2 * DoctorFateConfig.BANISH_DOMINION) < 0.01f, "+12 each landed");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stepGateDropWardSightAndShield(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		// Ankh Step: blinks along the look (the box is 8 wide: it stops before the wall)
		p.setYRot(0f);
		p.setXRot(0f);
		Vec3 start = p.position();
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_STEP), "step");
		helper.assertTrue(p.position().distanceTo(start) >= 3.0, "blinked forward, moved " + p.position().distanceTo(start));
		// Gate Drop: up it goes
		p.teleportTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)).x, helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)).y, helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)).z);
		Zombie z = zombie(helper, 5.5, 5.5, 60.0);
		double y0 = z.getY();
		look(p, z);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.GATE_DROP), "gate drop");
		helper.assertTrue(z.getY() - y0 >= 3.0, "lifted through the portal, up " + (z.getY() - y0));
		// Ward of Order: 30% less for him inside it
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.WARD) && DoctorFateSpells.inWard(p), "warded");
		DoctorFate.setHelmet(p, false);
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 10f);
		helper.assertTrue(Math.abs(p.getHealth() - 13f) < 0.1f, "the ward takes 30% off, health " + p.getHealth());
		DoctorFate.setHelmet(p, true);
		// Sight of Nabu casts (the glow is client-side)
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.SIGHT), "sight");
		// Ankh Shield: blocks a hit from the front
		helper.assertTrue(DoctorFateSpells.raiseShieldForTest(p) && DoctorFate.shieldUp(p), "shield up");
		Zombie front = zombie(helper, 1.5, 4.5, 20.0);
		look(p, front);
		p.setHealth(30f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(front), 6f);
		helper.assertTrue(Math.abs(p.getHealth() - 30f) < 0.01f, "everything from the front is blocked, health " + p.getHealth());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void amuletOfAnubisQuietsNabuWithOrWithoutTheHelmet(GameTestHelper helper) {
		ServerPlayer p = fate(helper, false);
		DoctorFate.setDominion(p, 30f);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.AMULET), "Shift+V works without the helmet");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - (30f - DoctorFateConfig.AMULET_DOMINION_CUT)) < 0.01f, "-10 Dominion, got " + DoctorFate.dominion(p));
		helper.assertTrue(DoctorFate.cooldownRemaining(p, DoctorFateAbilityManager.AMULET) > 0, "2 min cooldown");
		helper.assertTrue(!DoctorFateSpells.use(p, DoctorFateAbilityManager.AMULET), "not again while it cools down");
		DoctorFate.clearCooldowns(p);
		DoctorFate.setHelmet(p, true);
		DoctorFate.setDominion(p, 5f);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.AMULET) && DoctorFate.dominion(p) <= 0.01f, "with the helmet on too, never below 0");
		DoctorFate.clearCooldowns(p);
		helper.assertTrue(!DoctorFateSpells.use(p, DoctorFateAbilityManager.AMULET), "nothing to quiet at 0 Dominion");
		helper.assertTrue(DoctorFate.cooldownRemaining(p, DoctorFateAbilityManager.AMULET) == 0, "and no cooldown spent");
		helper.succeed();
	}
}

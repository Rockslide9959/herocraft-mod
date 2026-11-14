package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.DoctorFateAbilityManager;
import com.projecthero.mod.doctorfate.DoctorFateBargains;
import com.projecthero.mod.doctorfate.DoctorFateConfig;
import com.projecthero.mod.doctorfate.DoctorFateDominion;
import com.projecthero.mod.doctorfate.DoctorFateItems;
import com.projecthero.mod.doctorfate.DoctorFateMindscape;
import com.projecthero.mod.doctorfate.DoctorFateMod;
import com.projecthero.mod.doctorfate.DoctorFateNabuTrial;
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
		// v0.17.4: the grant starts the put-on sequence; the tests set the helmet outright either way
		DoctorFate.setHelmet(p, helmet);
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
		helper.assertTrue(DoctorFate.state(a).helmetChangeAt > 0L && DoctorFate.state(a).helmetGoingOn && !DoctorFate.wearsHelmet(a),
				"v0.17.4: the helmet's put-on sequence starts at once");
		helper.assertTrue(!DoctorFateMod.serve(b, pos, true) && !DoctorFate.hasPower(b), "an empty pedestal offers nothing");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(DoctorFate.KEY), "random serums never give it");
		DoctorFate.revoke(a);
		helper.assertTrue(!DoctorFate.hasPower(a), "revoked");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHelmetOfFateIsAnItemAgain(GameTestHelper helper) {
		// v0.17.4 (user): the helmet is an item whenever it's off its owner's head
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ProjectHeroMod.id("helmet_of_fate")), "the helmet_of_fate item is registered");
		// an old test world's saved stack (v0.17.2) loads as an unowned helmet, which the first Doctor Fate to wear it claims
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString("id", "projecthero:helmet_of_fate");
		tag.putInt("count", 1);
		ItemStack old = ItemStack.parseOptional(helper.getLevel().registryAccess(), tag);
		helper.assertTrue(com.projecthero.mod.doctorfate.helmet.FateHelmet.is(old) && old.get(com.projecthero.mod.doctorfate.helmet.FateHelmet.TAG) == null,
				"an old saved helmet loads as an unowned Helmet of Fate");
		// V is no longer the helmet: Sight of Nabu, and Shift+V the Amulet of Anubis
		helper.assertTrue(DoctorFateAbilityManager.SIGHT.equals(DoctorFateAbilityManager.moveFor(com.projecthero.mod.hero.AbilitySlot.SLOT_5, false))
				&& DoctorFateAbilityManager.AMULET.equals(DoctorFateAbilityManager.moveFor(com.projecthero.mod.hero.AbilitySlot.SLOT_5, true)),
				"V = Sight of Nabu, Shift+V = Amulet of Anubis");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void helmetTakesASecondAndGivesFortyHealthAndResistance(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		helper.assertTrue(DoctorFate.grant(p), "the grant works");
		DoctorFate.clearCooldowns(p);
		double baseAttack = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f, "normal health until the helmet is on");
		helper.assertTrue(DoctorFate.state(p).helmetChangeAt > 0L && DoctorFate.state(p).helmetGoingOn, "the grant starts putting it on");
		helper.assertTrue(!DoctorFate.wearsHelmet(p), "not on yet (1.5 s)");
		helper.onEachTick(() -> DoctorFate.tick(p));
		helper.runAfterDelay(DoctorFateConfig.HELMET_EQUIP_TICKS + 3, () -> {
			helper.assertTrue(DoctorFate.wearsHelmet(p), "on after the put-on sequence");
			helper.assertTrue(Math.abs(p.getMaxHealth() - 40f) < 0.01f, "40 health, got " + p.getMaxHealth());
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttack - DoctorFateConfig.HELMET_MELEE_BONUS) < 0.01,
					"v0.17.4: +8 melee, attack " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
			helper.assertTrue(p.hasEffect(MobEffects.REGENERATION) && p.getEffect(MobEffects.REGENERATION).getAmplifier() == 0, "v0.17.4: Regeneration I");
			p.setHealth(40f);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().magic(), 10f);
			helper.assertTrue(Math.abs(p.getHealth() - 33f) < 0.1f, "30% off a hit (10 -> 7), health " + p.getHealth());
			p.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
			p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
		});
		helper.runAfterDelay(DoctorFateConfig.HELMET_EQUIP_TICKS + 6, () -> {
			helper.assertTrue(!p.hasEffect(MobEffects.WITHER) && !p.hasEffect(MobEffects.POISON), "immune to Wither and Poison with the helmet on");
			p.getInventory().clearContent();
			// v0.17.4: no armour with the helmet on
			p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		});
		helper.runAfterDelay(DoctorFateConfig.HELMET_EQUIP_TICKS + 10, () -> {
			helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
					&& p.getInventory().contains(new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE)), "the chestplate is moved to the inventory");
			DoctorFateMod.onAction(p, FateActionPayload.of(FateActionPayload.Action.TOGGLE_HELMET));
			helper.assertTrue(DoctorFate.state(p).helmetChangeAt > 0L && !DoctorFate.state(p).helmetGoingOn, "H takes it off");
		});
		helper.runAfterDelay(DoctorFateConfig.HELMET_EQUIP_TICKS + DoctorFateConfig.HELMET_REMOVE_TICKS + 14, () -> {
			helper.assertTrue(!DoctorFate.wearsHelmet(p), "off after the lift-off sequence");
			helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f, "back to normal health, got " + p.getMaxHealth());
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttack) < 0.01, "no melee bonus off");
			helper.assertTrue(!p.hasEffect(MobEffects.REGENERATION), "no Regeneration off");
			boolean item = false;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				item |= com.projecthero.mod.doctorfate.helmet.FateHelmet.belongsTo(p.getInventory().getItem(i), p.getUUID());
			}
			helper.assertTrue(item, "v0.17.4: taking it off gives him the Helmet of Fate item");
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
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - (10f + DoctorFateConfig.BLAST_DOMINION)) < 0.01f, "full price at Whispers, got " + DoctorFate.dominion(p));
		// Pull: 10% less
		DoctorFate.clearCooldowns(p);
		DoctorFate.setDominion(p, 60f);
		DoctorFateSpells.testOverride = false;
		DoctorFateSpells.use(p, DoctorFateAbilityManager.ORDER_BLAST);
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - (60f + DoctorFateConfig.BLAST_DOMINION * DoctorFateConfig.PULL_DISCOUNT)) < 0.01f,
				"10% off at Pull, got " + DoctorFate.dominion(p));
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
		helper.assertTrue(Math.abs(p.getHealth() - 28f) < 0.01f, "a big spell costs twice, health " + p.getHealth());
		// the chat tag
		DoctorFate.setDominion(p, 55f);
		helper.assertTrue(DoctorFateDominion.chatTag(p, Component.literal("hi")).getString().contains("hi")
				&& DoctorFateDominion.chatTag(p, Component.literal("hi")) != null, "the chat line survives the tag");
		helper.assertTrue(DoctorFateDominion.chatTag(p, Component.literal("hi")).getString().length() > 2, "Pull tags his chat");
		DoctorFate.setDominion(p, 10f);
		helper.assertTrue(DoctorFateDominion.chatTag(p, Component.literal("hi")).getString().equals("hi"), "no tag below Pull");
		// v0.17.4 (user: "powers need the helmet"): helmet off, nothing works
		DoctorFate.setHelmet(p, false);
		DoctorFate.clearCooldowns(p);
		for (String id : new String[] { DoctorFateAbilityManager.ORDER_BLAST, DoctorFateAbilityManager.ANKH_BOLT, DoctorFateAbilityManager.ANKH_STEP,
				DoctorFateAbilityManager.AMULET, DoctorFateAbilityManager.SIGHT }) {
			helper.assertTrue(!DoctorFateSpells.use(p, id), id + " needs the helmet");
		}
		DoctorFateAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_2, true);
		helper.assertTrue(!DoctorFate.shieldUp(p), "no Ankh Shield without the helmet");
		DoctorFateAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_2, false);
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - 10f) < 0.01f, "and no Dominion spent");
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
		// v0.17.3: the strongest creature of Chaos within 24 blocks, so a neighbouring test's mob is never Nabu's pick instead
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		h.setHealth(1000f);
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
		helper.assertTrue(DoctorFate.state(p).possessedUntil == DoctorFateConfig.POSSESSED_FOREVER, "v0.17.4 round 2: no time limit");
		helper.assertTrue(DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_PAGES, "the trial in Nabu's library begins");
		helper.assertTrue(!DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT), "his own keys do nothing");
		helper.assertTrue(!DoctorFate.pressH(p) && DoctorFate.wearsHelmet(p), "H can't take the helmet off");
		helper.assertTrue(!DoctorFatePossession.struggle(p) && DoctorFate.possessed(p), "Space no longer shortens it (only the will test uses it)");
		DoctorFatePossession.end(p);
		helper.assertTrue(!p.hasEffect(MobEffects.SLOW_FALLING), "v0.17.4 round 2: no Slow Falling afterwards");
		helper.assertTrue(!DoctorFate.possessed(p) && !DoctorFate.wearsHelmet(p), "afterwards the helmet falls off");
		net.minecraft.world.phys.AABB around = p.getBoundingBox().inflate(6.0);
		helper.assertTrue(com.projecthero.mod.doctorfate.DoctorFateHelmet.liveEntities(helper.getLevel(), p.getUUID(), around) == 1,
				"v0.17.4: it lands as the Helmet of Fate on the ground");
		helper.assertTrue(DoctorFate.lockoutTicksLeft(p) == DoctorFateConfig.LOCKOUT_TICKS, "60 s lockout");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - DoctorFateConfig.AFTER_POSSESSION_DOMINION) < 0.01f, "Dominion settles at 70");
		helper.assertTrue(DoctorFate.pressH(p) && DoctorFate.state(p).helmetChangeAt == 0L,
				"during the lockout H summons it but it won't go on (it lands in the inventory)");
		helper.assertTrue(com.projecthero.mod.doctorfate.DoctorFateHelmet.liveEntities(helper.getLevel(), p.getUUID(), around) == 0,
				"the fallen helmet left the ground");
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

	// ---------------------------------------------------------------- v0.17.4 round 2: the trial in Nabu's library

	/** Possessed by Nabu (the trial begun), without a Possession tick (no casting at the neighbours' mobs). */
	private static ServerPlayer possessed(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		DoctorFatePossession.start(p);
		helper.assertTrue(DoctorFate.possessed(p) && DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_PAGES, "possessed, in the library");
		return p;
	}

	private static void readPages(GameTestHelper helper, ServerPlayer p) {
		for (int i : DoctorFateNabuTrial.lecterns(p, true)) {
			DoctorFateNabuTrial.read(p, i);
		}
		helper.assertTrue(DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_CHALLENGES, "every page found: the challenges");
	}

	private static void winChallenges(GameTestHelper helper, ServerPlayer p) {
		for (int k = 0; k < DoctorFateConfig.TRIAL_PAGES_NEEDED && DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_CHALLENGES; k++) {
			DoctorFateNabuTrial.confront(p);
			DoctorFateNabuTrial.answer(p, DoctorFateNabuTrial.row(p, true));
		}
		helper.assertTrue(DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_WILL, "every challenge won: the will");
	}

	private static void free(ServerPlayer p) {
		if (DoctorFate.possessed(p)) {
			DoctorFatePossession.end(p);
		}
		DoctorFate.setDominion(p, 0f);
		p.discard();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void nabusTrialPagesChallengesAndWill(GameTestHelper helper) {
		ServerPlayer p = possessed(helper);
		java.util.List<Integer> tomes = DoctorFateNabuTrial.lecterns(p, false);
		helper.assertTrue(tomes.size() == DoctorFateConfig.TRIAL_LECTERNS - DoctorFateConfig.TRIAL_PAGES_NEEDED, "three pages, the rest tomes");
		DoctorFateNabuTrial.read(p, tomes.get(0));
		helper.assertTrue(Math.abs(DoctorFateNabuTrial.angerOf(p) - DoctorFateConfig.TRIAL_ANGER_TOME) < 0.01f, "a tome angers him");
		DoctorFateNabuTrial.read(p, tomes.get(0));
		helper.assertTrue(Math.abs(DoctorFateNabuTrial.angerOf(p) - DoctorFateConfig.TRIAL_ANGER_TOME) < 0.01f, "re-reading it costs nothing");
		DoctorFateNabuTrial.confront(p);
		helper.assertTrue(DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_PAGES, "facing him empty-handed does nothing");
		readPages(helper, p);
		// a wrong answer: angrier, that page torn away, back to the pages
		DoctorFateNabuTrial.confront(p);
		DoctorFateNabuTrial.answer(p, DoctorFateNabuTrial.row(p, false));
		helper.assertTrue(DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_PAGES && DoctorFateNabuTrial.pagesFound(p) == 2,
				"wrong: back to the pages with one torn away");
		helper.assertTrue(Math.abs(DoctorFateNabuTrial.angerOf(p) - (DoctorFateConfig.TRIAL_ANGER_TOME + DoctorFateConfig.TRIAL_ANGER_WRONG)) < 0.01f,
				"and angrier");
		readPages(helper, p);
		winChallenges(helper, p);
		helper.assertTrue(DoctorFate.possessed(p), "still his until the will test");
		DoctorFateNabuTrial.confront(p);
		helper.assertTrue(DoctorFateNabuTrial.willOn(p), "the willpower test");
		for (int t = 0; t < DoctorFateConfig.WILL_NEEDED + 5 && DoctorFate.possessed(p); t++) {
			DoctorFateNabuTrial.holdWillForTest(p);
			DoctorFateNabuTrial.tickForTest(p);
		}
		helper.assertTrue(!DoctorFate.possessed(p), "holding the band for 10 s frees him");
		helper.assertTrue(DoctorFate.lockoutTicksLeft(p) == DoctorFateConfig.LOCKOUT_TICKS && !DoctorFate.wearsHelmet(p),
				"released as before: the helmet falls off, 60 s lockout");
		free(p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void failingTheWillReopensAChallengeAndWrathScattersEverything(GameTestHelper helper) {
		ServerPlayer p = possessed(helper);
		readPages(helper, p);
		winChallenges(helper, p);
		DoctorFateNabuTrial.confront(p);
		float anger = DoctorFateNabuTrial.angerOf(p);
		for (int t = 0; t < DoctorFateConfig.WILL_FAIL_OUT + 2 && DoctorFateNabuTrial.willOn(p); t++) {
			DoctorFateNabuTrial.dropWillForTest(p);
			DoctorFateNabuTrial.tickForTest(p);
		}
		helper.assertTrue(DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_CHALLENGES && !DoctorFateNabuTrial.willOn(p),
				"letting go fails the test: one challenge re-opens");
		helper.assertTrue(DoctorFateNabuTrial.angerOf(p) > anger + DoctorFateConfig.TRIAL_ANGER_WILL_FAIL - 0.01f, "and he's angrier");
		helper.assertTrue(DoctorFate.possessed(p), "still possessed");
		// wrong answers until his wrath
		for (int k = 0; k < 8 && DoctorFateNabuTrial.wraths(p) == 0; k++) {
			if (DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_PAGES) {
				readPages(helper, p);
			}
			DoctorFateNabuTrial.confront(p);
			DoctorFateNabuTrial.answer(p, DoctorFateNabuTrial.row(p, false));
		}
		helper.assertTrue(DoctorFateNabuTrial.wraths(p) == 1, "his anger at 100 is his wrath");
		helper.assertTrue(DoctorFateNabuTrial.stage(p) == DoctorFateNabuTrial.STAGE_PAGES && DoctorFateNabuTrial.pagesFound(p) == 0,
				"everything scattered: back to the start");
		helper.assertTrue(Math.abs(DoctorFateNabuTrial.angerOf(p) - DoctorFateConfig.TRIAL_ANGER_AFTER_WRATH) < 0.01f, "his anger settles");
		free(p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void possessedHeCantDieAndHitsTwiceAsHard(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		float worn = (float) p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		DoctorFatePossession.start(p);
		float held = (float) p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		helper.assertTrue(Math.abs(held - 2f * worn) < 0.01f, "possessed, his melee doubles (" + worn + " -> " + held + ")");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 1000f);
		helper.assertTrue(p.isAlive() && p.getHealth() >= 1f, "a killing blow leaves him at 1 health");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fellOutOfWorld(), 1000f);
		helper.assertTrue(p.isAlive(), "even the void can't kill him while Nabu holds him");
		free(p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void nabuAttacksAnyPlayerWhateverTheyveDone(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		ServerPlayer other = mock(helper, 4.5, 4.5);
		helper.assertTrue(!com.projecthero.mod.combat.HeroTargets.isHostile(p, other), "a player who's done nothing to him");
		if (com.projecthero.mod.combat.HeroTargets.canHarm(p, other)) {
			helper.assertTrue(DoctorFateSpells.targetable(p, other, true), "Nabu's own spells can hit him");
			helper.assertTrue(DoctorFatePossession.target(p) instanceof net.minecraft.world.entity.player.Player, "and Nabu picks a player first");
		}
		other.discard();
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deathsDoorHoldsDeathOffUntilHeChooses(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		DoctorFate.setDominion(p, 10f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 1000f);
		helper.assertTrue(p.isAlive() && p.getHealth() >= 1f, "a killing blow opens Death's door instead, at 1 health");
		helper.assertTrue(DoctorFateBargains.pending(p) == FateState.Bargain.DEATHS_DOOR, "\"Let me save you\"");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 1000f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 50f);
		helper.assertTrue(p.isAlive() && Math.abs(p.getHealth() - hp) < 0.01f, "while the offer is open nothing hurts him");
		DoctorFateBargains.answer(p, FateState.Bargain.DEATHS_DOOR, true);
		helper.assertTrue(Math.abs(p.getHealth() - p.getMaxHealth()) < 0.01f, "accepting saves him");
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noFallDamageWithTheHelmet(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 12f);
		helper.assertTrue(Math.abs(p.getHealth() - hp) < 0.01f, "helmet on: no fall damage");
		DoctorFate.setHelmet(p, false);
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 4f);
		helper.assertTrue(p.getHealth() < 20f, "helmet off (no lockout): falls hurt as usual");
		p.discard();
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
		// v0.17.3: the storm's own hits on this husk are counted, so a neighbouring test's area move (Nabu possessing a mock
		// player, Eternity Strike) can't make the damage steps lie; each hit is checked as it lands
		int[] hits = { 0 };
		boolean[] badStep = { false };
		DoctorFateSpells.stormHitListener = (owner, e, dealt) -> {
			if (e == a && owner == p) { // only this test's storm: a neighbouring test's storm can reach this husk too
				hits[0]++;
				if (dealt > 0.01f && Math.abs(dealt - DoctorFateConfig.GLYPH_DAMAGE) > DoctorFateConfig.GLYPH_DAMAGE * 0.25f) { // a hit swallowed by i-frames from a neighbouring test reads 0: not a bad step
					badStep[0] = true;
				}
			}
		};
		helper.runAfterDelay(DoctorFateConfig.GLYPH_TICKS + 5, () -> {
			DoctorFateSpells.stormHitListener = null;
			int expected = DoctorFateConfig.GLYPH_TICKS / DoctorFateConfig.GLYPH_HIT_EVERY;
			helper.assertTrue(!a.isAlive() || (hits[0] >= expected - 2 && hits[0] <= expected + 1), "about " + expected + " hits, got " + hits[0]);
			helper.assertTrue(!badStep[0], "each hit about " + DoctorFateConfig.GLYPH_DAMAGE + " damage");
			helper.assertTrue(a.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || !a.isAlive(), "slowed by the blades");
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
	public void amuletOfAnubisQuietsNabu(GameTestHelper helper) {
		ServerPlayer p = fate(helper, false);
		DoctorFate.setDominion(p, 30f);
		helper.assertTrue(!DoctorFateSpells.use(p, DoctorFateAbilityManager.AMULET), "v0.17.4: Shift+V needs the helmet too");
		DoctorFate.setHelmet(p, true);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.AMULET), "Shift+V with the helmet on");
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

	// ---------------------------------------------------------------- v0.17.3 the Tower of Fate mindscape

	private static void done(ServerPlayer p) {
		DoctorFateMindscape.close(p);
		DoctorFate.setDominion(p, 0f);
		p.discard();
	}

	/** A Shift+V tap is still the Amulet; held for a second it opens the Tower of Fate instead (and spends no Amulet). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void shiftVTapIsTheAmuletHoldOpensTheTower(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		DoctorFate.setDominion(p, 30f);
		p.setShiftKeyDown(true);
		DoctorFateAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
		DoctorFateAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, false);
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - (30f - DoctorFateConfig.AMULET_DOMINION_CUT)) < 0.05f, "a tap is the Amulet, Dominion "
				+ DoctorFate.dominion(p));
		helper.assertTrue(!DoctorFateMindscape.isOpen(p), "and no Tower");
		DoctorFate.clearCooldowns(p);
		DoctorFate.setDominion(p, 30f);
		DoctorFateAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
		helper.assertTrue(DoctorFateAbilityManager.holdingAmulet(p), "holding");
		helper.onEachTick(() -> DoctorFate.tick(p));
		helper.runAfterDelay(DoctorFateConfig.MIND_HOLD_TICKS + 4, () -> {
			helper.assertTrue(DoctorFateMindscape.isOpen(p), "held a second: the Tower of Fate opens");
			DoctorFateAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, false);
			helper.assertTrue(DoctorFate.cooldownRemaining(p, DoctorFateAbilityManager.AMULET) == 0 && DoctorFate.dominion(p) >= 29.9f,
					"letting go afterwards spends no Amulet, Dominion " + DoctorFate.dominion(p));
			done(p);
			helper.succeed();
		});
	}

	/** Blessing: +15 Dominion, spells hit 30% harder; asked again it's cooling. Only from an open Tower; needs the helmet. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mindscapeBlessingCostsDominionAndBoostsSpells(GameTestHelper helper) {
		ServerPlayer p = fate(helper, false);
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.BLESSING) == null, "nothing without an open Tower");
		helper.assertTrue(DoctorFateMindscape.open(p), "the Tower opens");
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.BLESSING) == DoctorFateMindscape.Reply.BLESS_NEEDS_HELMET,
				"the Blessing needs the helmet");
		DoctorFate.setHelmet(p, true);
		DoctorFate.setDominion(p, 10f);
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.BLESSING) == DoctorFateMindscape.Reply.BLESSED, "blessed");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - (10f + DoctorFateConfig.MIND_BLESSING_DOMINION)) < 0.05f, "+15 Dominion, got "
				+ DoctorFate.dominion(p));
		helper.assertTrue(DoctorFateMindscape.blessed(p), "the Blessing runs");
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.BLESSING) == DoctorFateMindscape.Reply.COOLING, "not twice");
		Zombie a = zombie(helper, 1.5, 4.5, 100.0);
		look(p, a);
		DoctorFateSpells.testOverride = false;
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT), "bolt");
		ratio(helper, 100f - a.getHealth(), DoctorFateConfig.BOLT_DAMAGE * DoctorFateConfig.MIND_BLESSING_MULT, "a blessed Ankh Bolt");
		helper.assertTrue(100f - a.getHealth() > DoctorFateConfig.BOLT_DAMAGE * 1.15f, "more than a plain bolt, dealt " + (100f - a.getHealth()));
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.LEAVE) == DoctorFateMindscape.Reply.FAREWELL
				&& !DoctorFateMindscape.isOpen(p), "leaving closes the Tower");
		DoctorFateSpells.testOverride = null;
		done(p);
		helper.succeed();
	}

	/** Silence: refused from Taking hold; granted below, it stops the Override and every spell's Dominion counts x1.5. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mindscapeSilenceStopsTheOverrideButCostsMore(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		helper.assertTrue(DoctorFateMindscape.open(p), "open");
		DoctorFate.setDominion(p, 80f);
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.SILENCE) == DoctorFateMindscape.Reply.SILENCE_REFUSED,
				"refused at Taking hold");
		helper.assertTrue(!DoctorFateMindscape.silenced(p), "still loud");
		DoctorFate.setDominion(p, 30f);
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.SILENCE) == DoctorFateMindscape.Reply.SILENCED, "silenced");
		helper.assertTrue(DoctorFateMindscape.silenced(p), "the Silence runs");
		Zombie a = zombie(helper, 1.5, 4.5, 100.0);
		look(p, a);
		DoctorFateSpells.testOverride = true; // would force Nabu's pick...
		float before = DoctorFate.dominion(p);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT), "bolt");
		helper.assertTrue(DoctorFateSpells.testOverride != null, "...but the Override was never rolled");
		DoctorFateSpells.testOverride = null;
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - before - DoctorFateConfig.BOLT_DOMINION * DoctorFateConfig.MIND_SILENCE_COST_MULT) < 0.05f,
				"the bolt's Dominion counts x1.5, gained " + (DoctorFate.dominion(p) - before));
		done(p);
		helper.succeed();
	}

	/** Debt: -25 now, 35 collected when it comes due; one at a time; settled early it costs 30; nothing to trade below 10. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mindscapeDebtTradesTheFutureForNow(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		helper.assertTrue(DoctorFateMindscape.open(p), "open");
		DoctorFate.setDominion(p, 5f);
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.DEBT) == DoctorFateMindscape.Reply.DEBT_NOTHING,
				"nothing to trade at 5");
		DoctorFate.setDominion(p, 40f);
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.DEBT) == DoctorFateMindscape.Reply.DEBT_MADE, "the debt");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - (40f - DoctorFateConfig.MIND_DEBT_CUT)) < 0.05f, "-25 now, got " + DoctorFate.dominion(p));
		helper.assertTrue(Math.abs(DoctorFateMindscape.debt(p) - DoctorFateConfig.MIND_DEBT_OWED) < 0.05f, "35 owed");
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.DEBT) == DoctorFateMindscape.Reply.DEBT_OWED, "one at a time");
		// it comes due
		edit(p, s -> s.mind = s.mind.withDebt(s.mind.debt(), DoctorFate.now(p)));
		float before = DoctorFate.dominion(p);
		DoctorFate.tick(p);
		helper.assertTrue(DoctorFateMindscape.debt(p) == 0f, "collected");
		helper.assertTrue(DoctorFate.dominion(p) - before >= DoctorFateConfig.MIND_DEBT_OWED - 0.05f, "+35 when due, gained "
				+ (DoctorFate.dominion(p) - before));
		// settled early instead
		DoctorFate.setDominion(p, 40f);
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.DEBT) == DoctorFateMindscape.Reply.DEBT_MADE, "a second debt");
		float mid = DoctorFate.dominion(p);
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.REPAY) == DoctorFateMindscape.Reply.REPAID, "repaid");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - mid - (DoctorFateConfig.MIND_DEBT_OWED - DoctorFateConfig.MIND_REPAY_DISCOUNT)) < 0.05f,
				"settling early costs 30, paid " + (DoctorFate.dominion(p) - mid));
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.REPAY) == DoctorFateMindscape.Reply.NO_DEBT, "no debt left");
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.STATUS) == DoctorFateMindscape.Reply.STATUS, "the status talk is free");
		done(p);
		helper.succeed();
	}

	/** Possessed, the Tower closes and won't open. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noTowerWhilePossessed(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		edit(p, s -> s.possessedUntil = DoctorFate.now(p) + 100);
		helper.assertTrue(!DoctorFateMindscape.open(p), "Nabu has the body: no Tower");
		edit(p, s -> s.possessedUntil = 0L);
		helper.assertTrue(DoctorFateMindscape.open(p), "free again: it opens");
		edit(p, s -> s.possessedUntil = DoctorFate.now(p) + 100);
		helper.assertTrue(DoctorFateMindscape.choose(p, DoctorFateMindscape.Choice.STATUS) == null && !DoctorFateMindscape.isOpen(p),
				"taken mid-talk: the Tower closes");
		edit(p, s -> s.possessedUntil = 0L);
		done(p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.3 the landing

	/** A fast touchdown out of the helmet's flight is a hero landing in the golden-ankh style; a gentle one is not. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void fateFlightLandsInTheGoldenAnkhStyle(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		Vec3 fast = new Vec3(0.0, -0.9, 0.0);
		for (int i = 0; i < 4; i++) {
			helper.runAfterDelay(i, () -> {
				edit(p, s -> s.flying = true);
				p.setOnGround(false);
				p.setKnownMovement(fast);
				com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			});
		}
		helper.runAfterDelay(4, () -> {
			p.setOnGround(true);
			com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			var a = com.projecthero.mod.flight.landing.HeroLanding.active(p);
			helper.assertTrue(a != null && a.style() == com.projecthero.mod.flight.landing.HeroLandingStyle.DOCTOR_FATE,
					"a fast touchdown out of Fate's flight is a Doctor Fate hero landing, got " + (a == null ? "none" : a.style()));
			helper.assertTrue(com.projecthero.mod.flight.landing.HeroLanding.fallSafe(p), "no fall damage");
			com.projecthero.mod.flight.landing.HeroLanding.end(p);
			com.projecthero.mod.flight.landing.HeroLanding.forget(p);
		});
		for (int i = 5; i < 9; i++) {
			helper.runAfterDelay(i, () -> {
				edit(p, s -> s.flying = true);
				p.setOnGround(false);
				p.setKnownMovement(new Vec3(0.05, -0.2, 0.0));
				com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			});
		}
		helper.runAfterDelay(9, () -> {
			p.setOnGround(true);
			com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			helper.assertTrue(!com.projecthero.mod.flight.landing.HeroLanding.landing(p), "a gentle touchdown just lands");
			com.projecthero.mod.flight.landing.HeroLanding.forget(p);
			edit(p, s -> s.flying = false);
			done(p);
			helper.succeed();
		});
	}

	/** The aim assist: a near miss still finds the creature for Golden Chains. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chainsForgiveANearMiss(GameTestHelper helper) {
		ServerPlayer p = fate(helper, true);
		Zombie a = zombie(helper, 1.5, 5.5, 100.0);
		look(p, a);
		p.setYRot(p.getYRot() + 4f); // a few degrees off
		DoctorFateSpells.testOverride = false;
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.GOLDEN_CHAINS), "the chains still find it");
		helper.assertTrue(DoctorFateSpells.isBound(a), "bound");
		DoctorFateSpells.testOverride = null;
		done(p);
		helper.succeed();
	}
}

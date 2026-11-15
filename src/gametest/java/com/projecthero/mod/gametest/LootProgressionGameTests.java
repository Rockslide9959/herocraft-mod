package com.projecthero.mod.gametest;

import java.util.List;
import java.util.Optional;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.loot.HeroRarity;
import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.HeroVaultBlockEntity;
import com.projecthero.mod.loot.LocatorItem;
import com.projecthero.mod.loot.LocatorSearch;
import com.projecthero.mod.loot.LocatorTarget;
import com.projecthero.mod.loot.LootItems;
import com.projecthero.mod.loot.MedKitHealing;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 loot framework: rarity component round-trip, per-player Hero Vault loot, the Locator (binds to a tier-2 tag
 * find, refuses an empty tier), and each hero supply (used up only when it works). Mock players are not ticked by the
 * server, so the Med Kit heal is driven with {@link MedKitHealing#tick}.
 */
public class LootProgressionGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static ItemStack use(ServerPlayer p, ItemStack stack) {
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return stack.finishUsingItem(p.level(), p);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void rarityComponentRoundTrips(GameTestHelper helper) {
		for (HeroRarity r : HeroRarity.values()) {
			Tag nbt = HeroRarity.CODEC.encodeStart(NbtOps.INSTANCE, r).getOrThrow();
			helper.assertTrue(HeroRarity.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow() == r, "codec round-trip " + r);
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			HeroRarity.STREAM_CODEC.encode(buf, r);
			helper.assertTrue(HeroRarity.STREAM_CODEC.decode(buf) == r, "stream codec round-trip " + r);
		}
		var registries = helper.getLevel().registryAccess();
		ItemStack diamond = new ItemStack(Items.DIAMOND);
		diamond.set(HeroRarity.COMPONENT, HeroRarity.LEGENDARY);
		ItemStack back = ItemStack.parse(registries, diamond.save(registries)).orElseThrow();
		helper.assertTrue(HeroRarity.of(back) == HeroRarity.LEGENDARY, "the component survives an item save / load");
		helper.assertTrue(HeroRarity.of(new ItemStack(Items.DIAMOND)) == null, "plain vanilla items have no rarity");
		helper.assertTrue(HeroRarity.of(new ItemStack(LootItems.SALVAGED_TECH)) == HeroRarity.COMMON, "Salvaged Tech is Common");
		helper.assertTrue(HeroRarity.of(new ItemStack(LootItems.HERO_ALLOY_INGOT)) == HeroRarity.UNCOMMON, "Hero Alloy is Uncommon");
		helper.assertTrue(HeroRarity.of(new ItemStack(LootItems.VIBRANIUM_SHARD)) == HeroRarity.RARE, "Vibranium Shard is Rare");
		helper.assertTrue(HeroRarity.of(new ItemStack(LootItems.CELESTIAL_CORE)) == HeroRarity.EPIC, "Celestial Core is Epic");
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE)
				.equals(new ItemStack(LootItems.CELESTIAL_CORE).getHoverName().getStyle().getColor()), "an Epic name is drawn purple");
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(back.getHoverName().getStyle().getColor()),
				"a Legendary vanilla stack is drawn gold");
		// the Locator component too
		LocatorTarget lt = LocatorTarget.unbound(3).bound(GlobalPos.of(helper.getLevel().dimension(), new BlockPos(10, 64, -20)),
				ProjectHeroMod.id("gamma_lab"));
		Tag lnbt = LocatorTarget.CODEC.encodeStart(NbtOps.INSTANCE, lt).getOrThrow();
		helper.assertTrue(LocatorTarget.CODEC.parse(NbtOps.INSTANCE, lnbt).getOrThrow().equals(lt), "locator target codec round-trip");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void vaultGivesEachPlayerTheirOwnLootOnce(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
		helper.assertTrue(HeroVault.place(helper.getLevel(), pos, 2, Direction.WEST), "the vault is placed");
		helper.assertTrue(HeroVault.tierAt(helper.getLevel(), pos) == 2, "tier 2 vault");
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);

		HeroVault.Opened first = HeroVault.open(helper.getLevel(), pos, a);
		helper.assertTrue(first.result() == HeroVault.OpenResult.OPENED, "player A opens it");
		helper.assertTrue(first.loot().stream().anyMatch(s -> s.is(LootItems.SALVAGED_TECH)), "A's roll has the guaranteed Salvaged Tech");
		helper.assertTrue(HeroVault.open(helper.getLevel(), pos, a).result() == HeroVault.OpenResult.ALREADY_LOOTED, "A can't loot it twice");
		HeroVault.Opened second = HeroVault.open(helper.getLevel(), pos, b);
		helper.assertTrue(second.result() == HeroVault.OpenResult.OPENED && !second.loot().isEmpty(), "player B still gets their own loot");
		helper.assertTrue(HeroVault.open(helper.getLevel(), pos, b).result() == HeroVault.OpenResult.ALREADY_LOOTED, "B can't loot it twice");

		HeroVaultBlockEntity be = (HeroVaultBlockEntity) helper.getLevel().getBlockEntity(pos);
		helper.assertTrue(be.hasLooted(a.getUUID()) && be.hasLooted(b.getUUID()), "both players are remembered");
		CompoundTag saved = be.saveWithFullMetadata(helper.getLevel().registryAccess());
		BlockEntity loaded = BlockEntity.loadStatic(pos, helper.getLevel().getBlockState(pos), saved, helper.getLevel().registryAccess());
		helper.assertTrue(loaded instanceof HeroVaultBlockEntity v && v.hasLooted(a.getUUID()) && v.hasLooted(b.getUUID()),
				"the looted list survives a save / load");
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3)).isEmpty(),
				"the loot pops out as items");
		helper.killAllEntitiesOfClass(ItemEntity.class);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void locatorBindsToATier2FindAndRefusesAnEmptyTier(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		List<ResourceLocation> tier2 = LocatorSearch.members(access, 2);
		helper.assertTrue(tier2.size() == 19, "all 19 old sites are tier 2, got " + tier2.size());
		helper.assertTrue(tier2.contains(ProjectHeroMod.id("government_site")) && !tier2.contains(ProjectHeroMod.id("city")),
				"the sites are in, the City is not");
		ServerPlayer p = mock(helper);

		// the real search (the gametest world is flat, so it usually finds nothing): either a tier-2 find or no binding
		ItemStack real = LocatorItem.forTier(2);
		LocatorItem.LocateResult r = LocatorItem.locate(p, real);
		LocatorTarget rt = LocatorItem.target(real);
		helper.assertTrue(r == LocatorItem.LocateResult.BOUND ? rt.isBound() && tier2.contains(rt.structure().orElseThrow()) : !rt.isBound(),
				"a real search binds to a tier-2 structure or stays unbound (" + r + ")");

		// a find from the tag binds like a lodestone compass
		LocatorSearch.finder = (level, origin, tier) -> Optional.of(new LocatorSearch.Found(origin.offset(300, 0, 400), ProjectHeroMod.id("government_site")));
		try {
			ItemStack loc = LocatorItem.forTier(2);
			helper.assertTrue(LocatorItem.locate(p, loc) == LocatorItem.LocateResult.BOUND, "binds to the find");
			LocatorTarget t = LocatorItem.target(loc);
			helper.assertTrue(t.isBound() && t.structure().orElseThrow().equals(ProjectHeroMod.id("government_site")), "remembers the structure");
			helper.assertTrue(t.pos().orElseThrow().dimension().equals(helper.getLevel().dimension()), "and its dimension");
			int d = LocatorItem.distance(loc, helper.getLevel(), p.getX(), p.getZ());
			helper.assertTrue(Math.abs(d - 500) <= 1, "distance 500, got " + d);
			helper.assertTrue(loc.getCount() == 1, "binding doesn't use the Locator up");
		} finally {
			LocatorSearch.finder = LocatorSearch.DEFAULT_FINDER;
		}

		// v0.17.5: tier 3 holds the five villain hideouts, tier 4 The Vault (no tier is empty any more)
		helper.assertTrue(LocatorSearch.members(access, 3).size() >= 5, "tier 3 holds the hideouts");
		helper.assertTrue(LocatorSearch.members(access, 4).contains(ProjectHeroMod.id("the_vault")), "tier 4 holds The Vault");
		// nothing in range: not found, the Locator stays unbound and kept
		LocatorSearch.finder = (level, origin, tier) -> Optional.empty();
		try {
			ItemStack hideout = LocatorItem.forTier(3);
			LocatorItem.LocateResult none = LocatorItem.locate(p, hideout);
			helper.assertTrue(none != LocatorItem.LocateResult.BOUND, "nothing found: not bound (" + none + ")");
			helper.assertTrue(!LocatorItem.target(hideout).isBound() && hideout.getCount() == 1, "and the Locator stays unbound and kept");
		} finally {
			LocatorSearch.finder = LocatorSearch.DEFAULT_FINDER;
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void medKitHealsOverFourSecondsAndCuresPoison(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		ItemStack refused = use(p, new ItemStack(LootItems.MED_KIT, 2));
		helper.assertTrue(refused.getCount() == 2, "a healthy player keeps the kit");

		p.setHealth(4f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
		ItemStack kit = use(p, new ItemStack(LootItems.MED_KIT, 2));
		helper.assertTrue(kit.getCount() == 1, "one kit is used");
		helper.assertTrue(!p.hasEffect(MobEffects.POISON), "Poison is cured");
		helper.assertTrue(MedKitHealing.ticksLeft(p) == MedKitHealing.DURATION_TICKS, "a 4 s heal starts");
		float before = p.getHealth();
		MedKitHealing.tick(p);
		float step = p.getHealth() - before;
		helper.assertTrue(Math.abs(step - MedKitHealing.PER_TICK) < 0.01f, "heals 0.2 health a tick, got " + step);
		for (int i = 1; i < MedKitHealing.DURATION_TICKS; i++) {
			MedKitHealing.tick(p);
		}
		helper.assertTrue(p.getHealth() >= 19.9f, "8 hearts healed, health " + p.getHealth());
		helper.assertTrue(!MedKitHealing.isHealing(p), "the heal ends after 4 s");
		helper.assertTrue(p.getActiveEffects().isEmpty(), "no vanilla effect icon");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void repairKitMendsWornArmourByHalf(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertTrue(use(p, new ItemStack(LootItems.SUIT_REPAIR_KIT)).getCount() == 1, "nothing to repair: the kit is kept");
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		int max = chest.getMaxDamage();
		chest.setDamageValue(max - 10);
		p.setItemSlot(EquipmentSlot.CHEST, chest);
		ItemStack kit = use(p, new ItemStack(LootItems.SUIT_REPAIR_KIT, 3));
		helper.assertTrue(kit.getCount() == 2, "one kit is used");
		int expected = Math.max(0, max - 10 - Math.round(max * 0.5f));
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == expected,
				"repaired by half its durability: damage " + p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() + ", expected " + expected);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void cooldownStimHalvesRemainingCooldowns(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertTrue(use(p, new ItemStack(LootItems.COOLDOWN_STIM)).getCount() == 1, "nothing cooling down: the stim is kept");
		BlackPanther.grant(p);
		long now = p.level().getGameTime();
		var s = BlackPanther.state(p).copy();
		s.abilityReadyAt.put("test_strike", now + 200);
		s.abilityReadyAt.put("test_sentinel", 1L);
		p.setAttached(ModAttachments.BLACK_PANTHER_STATE, s);
		ItemStack stim = use(p, new ItemStack(LootItems.COOLDOWN_STIM, 2));
		helper.assertTrue(stim.getCount() == 1, "one stim is used");
		var after = BlackPanther.state(p).abilityReadyAt;
		helper.assertTrue(after.get("test_strike") == now + 100, "200 ticks left became 100, got " + (after.get("test_strike") - now));
		helper.assertTrue(after.get("test_sentinel") == 1L, "non-cooldown values are left alone");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void energyCellRefillsThePowerMeter(GameTestHelper helper) {
		ServerPlayer plain = mock(helper);
		helper.assertTrue(use(plain, new ItemStack(LootItems.ENERGY_CELL)).getCount() == 1, "no power, no meter: the cell is kept");

		ServerPlayer p = mock(helper);
		BlackPanther.grant(p);
		BlackPanther.setKinetic(p, 0f);
		ItemStack cell = use(p, new ItemStack(LootItems.ENERGY_CELL, 2));
		helper.assertTrue(cell.getCount() == 1, "one cell is used");
		float k = BlackPanther.kinetic(p);
		float want = BlackPantherConfig.KINETIC_MAX * 0.4f;
		helper.assertTrue(Math.abs(k - want) < 0.5f, "Kinetic Energy refilled by 40%: " + k);
		BlackPanther.setKinetic(p, BlackPantherConfig.KINETIC_MAX);
		helper.assertTrue(use(p, new ItemStack(LootItems.ENERGY_CELL)).getCount() == 1, "a full meter refuses the cell");
		helper.succeed();
	}
}

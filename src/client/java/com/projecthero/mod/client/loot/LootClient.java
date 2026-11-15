package com.projecthero.mod.client.loot;

import java.util.List;
import java.util.Optional;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.loot.HeroRarity;
import com.projecthero.mod.loot.HeroVaultBlock;
import com.projecthero.mod.loot.LocatorItem;
import com.projecthero.mod.loot.LocatorTarget;
import com.projecthero.mod.loot.LootItems;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5 loot framework, client side: the rarity line under item names, the Locator's compass needle, tooltip distance
 * and HUD line, the Hero Vault item's per-tier model, and the vault's "you still have loot here" shimmer hook.
 */
public final class LootClient {
	private LootClient() {
	}

	public static void initialize() {
		HeroVaultBlock.localViewer = () -> {
			LocalPlayer p = Minecraft.getInstance().player;
			return p == null ? null : p.getUUID();
		};

		// the Locator's needle: the vanilla compass function aimed at the bound place (spins when unbound / elsewhere)
		FabricModelPredicateProviderRegistry.register(LootItems.LOCATOR, ResourceLocation.withDefaultNamespace("angle"),
				new CompassItemPropertyFunction((level, stack, entity) -> boundPos(stack)));
		// creative Hero Vault stacks show their tier's block model
		FabricModelPredicateProviderRegistry.register(LootItems.HERO_VAULT_ITEM, ProjectHeroMod.id("vault_tier"),
				(stack, level, entity, seed) -> LootItems.vaultTier(stack) / 4f);

		// registered before TooltipWrap (ProjectHeroModClient), so the lines added here are wrapped like any other
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			HeroRarity rarity = HeroRarity.of(stack);
			if (rarity != null && !lines.isEmpty()) {
				lines.add(1, rarity.label());
			}
			if (stack.is(LootItems.LOCATOR)) {
				Component d = distanceLine(stack);
				if (d != null) {
					lines.add(Math.min(lines.size(), rarity != null ? 3 : 2), d);
				}
			}
		});

		HudRenderCallback.EVENT.register(LootClient::hud);
	}

	private static GlobalPos boundPos(ItemStack stack) {
		LocatorTarget t = stack.get(LootItems.LOCATOR_TARGET);
		return t == null ? null : t.pos().orElse(null);
	}

	private static Component distanceLine(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		LocatorTarget t = stack.get(LootItems.LOCATOR_TARGET);
		if (mc.player == null || mc.level == null || t == null || !t.isBound()) {
			return null;
		}
		int d = LocatorItem.distance(stack, mc.level, mc.player.getX(), mc.player.getZ());
		return d >= 0 ? Component.translatable("item.projecthero.locator.distance", d).withStyle(ChatFormatting.AQUA)
				: Component.translatable("item.projecthero.locator.distance_other").withStyle(ChatFormatting.DARK_GRAY);
	}

	/** While a bound Locator is in either hand: "<place>  <distance> m" just above the hotbar. */
	private static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		ItemStack stack = p.getItemInHand(InteractionHand.MAIN_HAND);
		if (!stack.is(LootItems.LOCATOR)) {
			stack = p.getItemInHand(InteractionHand.OFF_HAND);
		}
		if (!stack.is(LootItems.LOCATOR)) {
			return;
		}
		LocatorTarget t = stack.get(LootItems.LOCATOR_TARGET);
		if (t == null || !t.isBound()) {
			return;
		}
		Optional<ResourceLocation> id = t.structure();
		Component name = id.isPresent() ? LocatorItem.structureName(id.get()) : LocatorItem.tierName(t.tier());
		int d = LocatorItem.distance(stack, mc.level, p.getX(), p.getZ());
		Component line = d >= 0
				? Component.translatable("item.projecthero.locator.hud", name, d)
				: Component.translatable("item.projecthero.locator.other_dimension", name);
		int w = mc.font.width(line);
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - 82;
		g.fill(x - 4, y - 3, x + w + 4, y + 11, 0x90000000);
		g.drawString(mc.font, line, x, y, 0x7FF3F0, true);
	}

	/** Test / harness helper: the lines the client adds for a stack (rarity + locator distance). */
	public static List<Component> extraLines(ItemStack stack) {
		HeroRarity r = HeroRarity.of(stack);
		Component d = stack.is(LootItems.LOCATOR) ? distanceLine(stack) : null;
		java.util.ArrayList<Component> out = new java.util.ArrayList<>();
		if (r != null) {
			out.add(r.label());
		}
		if (d != null) {
			out.add(d);
		}
		return out;
	}
}

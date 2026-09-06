package com.herocraft.mod.client.mixin;

import com.herocraft.mod.symbiote.Symbiote;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the Symbiote suit is worn, the armour icons above the health bar render solid black instead
 * of the vanilla grey -- a purely cosmetic signal that the "armour" the player is wearing is the
 * living suit itself. Re-implements vanilla {@code Gui#renderArmor}'s tiny loop with a black colour
 * tint applied to each sprite blit, then cancels the original so the two do not draw on top of each
 * other. Everything else about the armour bar (value, position, half-icons) is identical to vanilla.
 */
@Mixin(Gui.class)
public abstract class GuiArmorBarMixin {
	private static final ResourceLocation HEROCRAFT$ARMOR_FULL = ResourceLocation.withDefaultNamespace("hud/armor_full");
	private static final ResourceLocation HEROCRAFT$ARMOR_HALF = ResourceLocation.withDefaultNamespace("hud/armor_half");
	private static final ResourceLocation HEROCRAFT$ARMOR_EMPTY = ResourceLocation.withDefaultNamespace("hud/armor_empty");

	@Inject(method = "renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V",
			at = @At("HEAD"), cancellable = true)
	private static void herocraft$blackArmorBar(GuiGraphics guiGraphics, Player player, int i, int j, int k, int l,
			CallbackInfo ci) {
		if (!Symbiote.isActive(player)) {
			return;
		}
		int armor = player.getArmorValue();
		if (armor <= 0) {
			ci.cancel();
			return;
		}
		RenderSystem.enableBlend();
		guiGraphics.setColor(0.06f, 0.06f, 0.09f, 1.0f);
		int y = i - (j - 1) * k - 10;
		for (int m = 0; m < 10; m++) {
			int x = l + m * 8;
			if (m * 2 + 1 < armor) {
				guiGraphics.blitSprite(HEROCRAFT$ARMOR_FULL, x, y, 9, 9);
			}
			if (m * 2 + 1 == armor) {
				guiGraphics.blitSprite(HEROCRAFT$ARMOR_HALF, x, y, 9, 9);
			}
			if (m * 2 + 1 > armor) {
				guiGraphics.blitSprite(HEROCRAFT$ARMOR_EMPTY, x, y, 9, 9);
			}
		}
		guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.disableBlend();
		ci.cancel();
	}
}

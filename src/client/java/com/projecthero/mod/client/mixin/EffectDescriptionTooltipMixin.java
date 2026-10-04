package com.projecthero.mod.client.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Ordering;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.gui.TooltipWrap;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.14.21: hovering one of Project Hero's own status effects in the inventory's (wide) effect list shows its name
 * and a short description -- the Supervillain's Mark and its omen first of all, so a player can find out what the
 * Spy did to them. Any {@code projecthero} effect with an {@code effect.projecthero.<id>.desc} lang key gets one.
 * Vanilla already shows a name + time tooltip in the narrow (icon-only) layout, so this only adds one to the wide
 * layout, where vanilla shows none. The layout maths mirror vanilla {@code renderEffects}.
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectDescriptionTooltipMixin<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
	private EffectDescriptionTooltipMixin(T menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Inject(method = "renderEffects", at = @At("TAIL"))
	private void projecthero$effectDescription(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		int x = this.leftPos + this.imageWidth + 2;
		int room = this.width - x;
		Collection<MobEffectInstance> effects = this.minecraft.player.getActiveEffects();
		if (effects.isEmpty() || room < 120 || mouseX < x || mouseX >= x + 120) {
			return;
		}
		int step = effects.size() > 5 ? 132 / (effects.size() - 1) : 33;
		int y = this.topPos;
		for (MobEffectInstance effect : Ordering.natural().sortedCopy(effects)) {
			if (mouseY >= y && mouseY < y + Math.min(step, 32)) {
				projecthero$tooltip(graphics, effect, mouseX, mouseY);
				return;
			}
			y += step;
		}
	}

	private void projecthero$tooltip(GuiGraphics graphics, MobEffectInstance effect, int mouseX, int mouseY) {
		MobEffect type = effect.getEffect().value();
		ResourceLocation id = effect.getEffect().unwrapKey().map(k -> k.location()).orElse(null);
		if (id == null || !ProjectHeroMod.MOD_ID.equals(id.getNamespace())) {
			return;
		}
		String key = type.getDescriptionId() + ".desc";
		if (!I18n.exists(key)) {
			return;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(type.getDisplayName());
		lines.addAll(TooltipWrap.wrap(this.font, Component.translatable(key).withStyle(ChatFormatting.GRAY)));
		graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
	}
}

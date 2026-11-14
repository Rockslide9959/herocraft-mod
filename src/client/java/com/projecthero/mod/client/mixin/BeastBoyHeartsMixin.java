package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.projecthero.mod.client.beastboy.BeastHearts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4 Beast Boy: while he is an animal, vanilla's heart row shows the form's health as green hearts ({@link BeastHearts}). The
 * health and maximum vanilla reads are swapped for the form's (WrapOperation, so other mods' wrappers still chain), and the plain
 * heart sprites are swapped for the green ones.
 */
@Mixin(Gui.class)
public abstract class BeastBoyHeartsMixin {
	@Inject(method = "renderPlayerHealth", at = @At("HEAD"))
	private void projecthero$beastHeartsBegin(GuiGraphics graphics, CallbackInfo ci) {
		BeastHearts.begin(Minecraft.getInstance().getCameraEntity() instanceof Player p ? p : null);
	}

	@Inject(method = "renderPlayerHealth", at = @At("RETURN"))
	private void projecthero$beastHeartsEnd(GuiGraphics graphics, CallbackInfo ci) {
		BeastHearts.end();
	}

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getHealth()F"))
	private float projecthero$beastFormHealth(Player player, Operation<Float> original) {
		float own = original.call(player);
		return BeastHearts.active(player) ? BeastHearts.health(player) : own;
	}

	@WrapOperation(method = "renderPlayerHealth",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double projecthero$beastFormMaxHealth(Player player, Holder<Attribute> attribute, Operation<Double> original) {
		double own = original.call(player, attribute);
		return attribute == Attributes.MAX_HEALTH && BeastHearts.active(player) ? BeastHearts.maxHealth(player) : own;
	}

	@ModifyArg(method = "renderHeart", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), index = 0)
	private ResourceLocation projecthero$beastHeartSprite(ResourceLocation sprite) {
		return BeastHearts.sprite(sprite);
	}
}

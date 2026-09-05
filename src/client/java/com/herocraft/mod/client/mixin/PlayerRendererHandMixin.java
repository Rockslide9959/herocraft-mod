package com.herocraft.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.herocraft.mod.armor.SuperheroArmorItem;
import com.herocraft.mod.client.render.SuperheroFirstPersonArm;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * First-person: when the local player wears any {@link SuperheroArmorItem} chestplate, draw the
 * suit's armoured forearm <b>over</b> the bare player hand so the player actually sees the gauntlet
 * (the sleeve / second skin layer is already hidden by {@link PlayerModelMixin}). Injected at the
 * TAIL of {@code renderHand}, where {@code arm} still holds vanilla's fully-animated pose and the
 * pose stack is exactly where the bare arm was just drawn -- so the armour forearm sheathes the hand
 * and tracks every swing / bob / place animation for free.
 *
 * <p>The geometry and texture come from {@link SuperheroFirstPersonArm}, a rebuild of the shared
 * {@code crimson_vanguard} arm bones, so the first-person view matches the in-world GeckoLib model.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererHandMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void herocraft$armorOverArm(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player) {
			return;
		}
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chest.getItem() instanceof SuperheroArmorItem piece)) {
			return;
		}
		// The Punisher tactical vest is deliberately "clothing only" -- bare forearms, no gauntlet.
		// Its texture has no data at the shared crimson_vanguard arm UVs, so drawing the armoured
		// forearm here was rendering a flat grey sleeve over the hand. Let the vanilla hand show.
		if ("punisher".equals(piece.armorSetId())) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		boolean rightArm = arm == model.rightArm;
		SuperheroFirstPersonArm.render(pose, buffers, light, arm, rightArm, piece.armorSetId());
	}
}

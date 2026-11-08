package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.andy.AndyRender;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Andy's broken parts (and their overlay) are not drawn -- third person and, because {@code renderHand} runs
 * {@code setupAnim} too, the first-person arm. Vanilla's {@code setModelProperties} turns every part back on each frame.
 */
@Mixin(PlayerModel.class)
public abstract class AndyPlayerModelMixin<T extends LivingEntity> {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$andyHideBroken(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
			CallbackInfo ci) {
		if (entity instanceof Player player) {
			AndyRender.hideParts(player, (PlayerModel<?>) (Object) this);
		}
	}
}

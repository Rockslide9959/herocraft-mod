package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.punisher.GunAnim;
import com.projecthero.mod.punisher.ability.PunisherMelee;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;

/**
 * v0.15.18 playtest: the Punisher's Breach Kick in first person. The full body is drawn for the kick
 * ({@code FirstPersonBodySequences}), but a leg can't reach up into a level view, so the view itself dips down for the
 * moment of the kick -- the boot comes up into the bottom of the screen -- and comes straight back. Presentation only:
 * the player's own look (and so their aim) is never touched.
 */
@Mixin(Camera.class)
public abstract class PunisherKickCameraMixin {
	@Shadow
	public abstract float getXRot();

	@Shadow
	public abstract float getYRot();

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("TAIL"))
	private void projecthero$punisherKickDip(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick,
			CallbackInfo ci) {
		if (detached || !(entity instanceof Player player) || entity != Minecraft.getInstance().player
				|| GunAnim.meleeKind(player) != PunisherMelee.ANIM_KICK) {
			return;
		}
		float t = GunAnim.melee(player, partialTick);
		if (t < 0f) {
			return;
		}
		float dip = GunAnim.keys(t, 0f, 0f, 2f, 30f, 3.2f, 55f, 6f, 50f, 10f, 0f);
		setRotation(getYRot(), Math.min(90f, getXRot() + dip));
	}
}

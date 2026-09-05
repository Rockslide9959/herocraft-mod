package com.herocraft.mod.client.mixin;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.hero.data.ExperimentalState;
import com.herocraft.mod.hero.power.p18.DensityManipulationHandlers;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Super Speed movement feel, local player only:
 * <ul>
 *   <li>carry ground momentum through jumps (so a running jump goes much further), and</li>
 *   <li>run across the surface of water while moving fast.</li>
 * </ul>
 *
 * <p>Purely client-side. The player's own client simulates the motion and reports the resulting
 * positions; the server accepts them because Super Speed's large {@code MOVEMENT_SPEED} attribute
 * widens the server's movement tolerance to match. Inert unless Super Speed's {@code speed_mode}
 * toggle or {@code overdrive} is currently active.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Unique
	private double herocraft$groundSpeed;

	@Inject(method = "aiStep", at = @At("HEAD"))
	private void herocraft$phaseNoclip(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (DensityManipulationHandlers.phasing(self)) {
			self.noPhysics = true;
		} else if (!self.isSpectator()) {
			self.noPhysics = false;
		}
	}

	/**
	 * Spider-Man's rope forces, applied before vanilla's own step so gravity and drag land on top of
	 * them and the swing genuinely falls into its arc. See {@code SpiderSwingClient} for why the
	 * physics run on this side.
	 */
	@Inject(method = "aiStep", at = @At("HEAD"))
	private void herocraft$spiderSwing(CallbackInfo ci) {
		com.herocraft.mod.client.spider.SpiderSwingClient.tick((LocalPlayer) (Object) this);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void herocraft$speedMovement(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!herocraft$speedModeActive(self)) {
			herocraft$groundSpeed = 0.0;
			return;
		}

		Vec3 v = self.getDeltaMovement();
		double horiz = Math.sqrt(v.x * v.x + v.z * v.z);

		if (self.onGround()) {
			herocraft$groundSpeed = Math.min(3.0, horiz);
		} else if (herocraft$groundSpeed > 0.05 && horiz > 1.0e-4 && horiz < herocraft$groundSpeed) {
			// ease the horizontal speed back up toward what it was the instant we left the ground
			double target = horiz + (herocraft$groundSpeed - horiz) * 0.5;
			double f = target / horiz;
			self.setDeltaMovement(v.x * f, v.y, v.z * f);
			v = self.getDeltaMovement();
		}

		if (!self.isShiftKeyDown() && !self.getAbilities().flying && horiz > 0.12) {
			BlockPos feet = self.blockPosition();
			FluidState fluid = self.level().getFluidState(feet);
			if (fluid.is(FluidTags.WATER) && self.level().getFluidState(feet.above()).isEmpty()) {
				double surfaceY = feet.getY() + fluid.getHeight(self.level(), feet);
				if (self.getY() >= surfaceY - 1.0 && self.getY() <= surfaceY + 0.5) {
					if (self.getY() < surfaceY) {
						self.setPos(self.getX(), surfaceY, self.getZ());
					}
					self.setDeltaMovement(v.x, Math.max(0.0, v.y), v.z);
					self.setOnGround(true);
					self.resetFallDistance();
				}
			}
		}
	}

	@Unique
	private boolean herocraft$speedModeActive(LocalPlayer self) {
		ExperimentalState st = self.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains("power_04_super_speed")) {
			return false;
		}
		if (st.activeToggles.contains("power_04_super_speed/speed_mode")) {
			return true;
		}
		Float until = st.resources.get("power_04_super_speed/overdrive_until");
		return until != null && until > self.level().getGameTime();
	}
}

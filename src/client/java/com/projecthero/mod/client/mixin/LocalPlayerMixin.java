package com.projecthero.mod.client.mixin;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.p18.DensityManipulationHandlers;

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
	private double projecthero$groundSpeed;
	/** Peak downward speed seen since the elastic hero last left the ground. */
	@Unique
	private double projecthero$elasticFall;
	@Unique
	private boolean projecthero$wasOnGround = true;

	/**
	 * Elasticity bounce, local player only (v0.10.13):
	 * <ul>
	 *   <li>a real fall (~5 blocks or more) always gives a small hop on landing; and</li>
	 *   <li>while Elastic Form is on <em>and</em> the jump key is held, the landing rebounds like a
	 *       slime block, scaled to the fall -- and only then, so it never bounces for ever.</li>
	 * </ul>
	 */
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void projecthero$elasticBounce(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!com.projecthero.mod.hero.power.p17.ElasticityHandlers.owns(self)) {
			projecthero$elasticFall = 0.0;
			projecthero$wasOnGround = self.onGround();
			return;
		}
		Vec3 v = self.getDeltaMovement();
		boolean onGround = self.onGround();
		if (!onGround) {
			if (v.y < -projecthero$elasticFall) {
				projecthero$elasticFall = -v.y;
			}
			projecthero$wasOnGround = false;
			return;
		}
		if (!projecthero$wasOnGround && projecthero$elasticFall > 0.55) {
			boolean elasticForm = com.projecthero.mod.hero.power.p17.ElasticityHandlers.formActiveClient(self);
			boolean holdingJump = net.minecraft.client.Minecraft.getInstance().options.keyJump.isDown();
			double bounce;
			if (elasticForm && holdingJump) {
				bounce = Math.min(1.35, projecthero$elasticFall * 0.55);
			} else if (projecthero$elasticFall > 0.9) {
				bounce = 0.42; // the always-on small hop on a proper fall
			} else {
				bounce = 0.0;
			}
			if (bounce > 0.0) {
				self.setDeltaMovement(v.x * 1.02, bounce, v.z * 1.02);
				self.resetFallDistance();
			}
		}
		projecthero$elasticFall = 0.0;
		projecthero$wasOnGround = true;
	}

	@Inject(method = "aiStep", at = @At("HEAD"))
	private void projecthero$phaseNoclip(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (DensityManipulationHandlers.phasing(self)
				|| com.projecthero.mod.hero.power.p05.GeokinesisHandlers.earthSwimming(self)) {
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
	private void projecthero$spiderSwing(CallbackInfo ci) {
		com.projecthero.mod.client.spider.SpiderSwingClient.tick((LocalPlayer) (Object) this);
	}

	/**
	 * Flight power feel, local player only (v0.10.13):
	 * <ul>
	 *   <li>Toggle Flight cruises at ~11 blocks/s, ~15 while sprinting (a clamp on the horizontal
	 *       delta, so it lands on those numbers regardless of vanilla's fly-speed maths).</li>
	 *   <li>Super Sonic Flight cruises at ~30 blocks/s.</li>
	 *   <li>Falling <em>without</em> flying gives improved aerial control -- a gentle wingsuit-style
	 *       nudge in the direction you are steering, and a softer terminal velocity.</li>
	 * </ul>
	 */
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void projecthero$flightControl(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		ExperimentalState st = self.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains("power_03_flight")) {
			return;
		}
		Vec3 v = self.getDeltaMovement();
		double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
		boolean sonic = st.resources.getOrDefault("power_03_flight/sonic_ticks", 0.0f) > 0.5f;

		if (self.getAbilities().flying && horiz > 1.0e-4) {
			double capPerTick;
			if (sonic) {
				capPerTick = 30.0 / 20.0;
			} else {
				capPerTick = (self.isSprinting() ? 15.0 : 11.0) / 20.0;
			}
			if (horiz > capPerTick) {
				double f = capPerTick / horiz;
				self.setDeltaMovement(v.x * f, v.y, v.z * f);
			}
			return;
		}

		// Not flying: improved aerial control while falling.
		if (!self.onGround() && !self.isInWater() && v.y < 0.0) {
			float fwd = self.zza;
			float str = self.xxa;
			if (fwd != 0.0f || str != 0.0f) {
				float yaw = self.getYRot() * ((float) Math.PI / 180.0f);
				double sin = Math.sin(yaw);
				double cos = Math.cos(yaw);
				Vec3 wish = new Vec3(-sin * fwd + cos * str, 0.0, cos * fwd + sin * str);
				if (wish.lengthSqr() > 1.0e-4) {
					wish = wish.normalize().scale(0.055);
					self.setDeltaMovement(v.x + wish.x, Math.max(v.y, -1.4), v.z + wish.z);
					return;
				}
			}
			if (v.y < -1.4) {
				self.setDeltaMovement(v.x, -1.4, v.z);
			}
		}
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void projecthero$speedMovement(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!projecthero$speedModeActive(self)) {
			projecthero$groundSpeed = 0.0;
			return;
		}

		Vec3 v = self.getDeltaMovement();
		double horiz = Math.sqrt(v.x * v.x + v.z * v.z);

		if (self.onGround()) {
			projecthero$groundSpeed = Math.min(3.0, horiz);
		} else if (projecthero$groundSpeed > 0.05 && horiz > 1.0e-4 && horiz < projecthero$groundSpeed) {
			// ease the horizontal speed back up toward what it was the instant we left the ground
			double target = horiz + (projecthero$groundSpeed - horiz) * 0.5;
			double f = target / horiz;
			self.setDeltaMovement(v.x * f, v.y, v.z * f);
			v = self.getDeltaMovement();
		}

		if (!self.isShiftKeyDown() && !self.getAbilities().flying && horiz > 0.08) {
			BlockPos feet = self.blockPosition();
			FluidState fluid = self.level().getFluidState(feet);
			// If the player has already sunk a touch, the water is one block below the feet position.
			if (!fluid.is(FluidTags.WATER) && self.level().getFluidState(feet.below()).is(FluidTags.WATER)) {
				feet = feet.below();
				fluid = self.level().getFluidState(feet);
			}
			if (fluid.is(FluidTags.WATER) && self.level().getFluidState(feet.above()).isEmpty()) {
				double surfaceY = feet.getY() + fluid.getHeight(self.level(), feet);
				if (self.getY() >= surfaceY - 1.2 && self.getY() <= surfaceY + 0.5) {
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
	private boolean projecthero$speedModeActive(LocalPlayer self) {
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

package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p01.StrengthBareHands;
import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p05.GeoBareHands;
import com.projecthero.mod.hero.power.p17.ElasticityHandlers;
import com.projecthero.mod.symbiote.SymbioteBareHands;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Two per-block hooks:
 * <ul>
 *   <li>Super Speed: you mine as fast as you move. {@code getDestroySpeed} is multiplied by a factor
 *       that tracks the movement state (×2 at base, more in Speed Mode / Overdrive).</li>
 *   <li>Symbiote: a bonded host's empty hand mines and harvests like a wooden pickaxe / axe / shovel
 *       ({@link SymbioteBareHands}).</li>
 * </ul>
 * Inert for every other player/state.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Shadow
	protected abstract boolean canPlayerFitWithinBlocksAndEntitiesWhen(Pose pose);

	/**
	 * Elasticity: squeeze through a one-block gap without needing a trapdoor to trigger the crawl.
	 * While the elastic hero owns the power and neither the standing nor the crouching pose fits where
	 * they are (or a low opening is directly ahead), drop straight to the swimming/crawl pose.
	 */
	@Inject(method = "updatePlayerPose", at = @At("TAIL"))
	private void projecthero$elasticCrawl(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (!ElasticityHandlers.owns(self) || self.getAbilities().flying || self.isPassenger()) {
			return;
		}
		if (self.getPose() == Pose.SWIMMING || self.isVisuallySwimming()) {
			return;
		}
		boolean stuck = !canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING)
				&& !canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING);
		boolean openingAhead = false;
		if (self.isShiftKeyDown() && !stuck) {
			net.minecraft.world.phys.Vec3 look = self.getLookAngle();
			double fx = look.x;
			double fz = look.z;
			double flen = Math.sqrt(fx * fx + fz * fz);
			if (flen > 1.0e-3) {
				fx /= flen;
				fz /= flen;
				net.minecraft.core.BlockPos ahead = net.minecraft.core.BlockPos.containing(
						self.getX() + fx * 0.7, self.getY() + 0.1, self.getZ() + fz * 0.7);
				openingAhead = self.level().getBlockState(ahead).getCollisionShape(self.level(), ahead).isEmpty()
						&& !self.level().getBlockState(ahead.above()).getCollisionShape(self.level(), ahead.above()).isEmpty();
			}
		}
		if ((stuck || openingAhead) && canPlayerFitWithinBlocksAndEntitiesWhen(Pose.SWIMMING)) {
			self.setPose(Pose.SWIMMING);
		}
	}

	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void projecthero$speedMining(BlockState state, CallbackInfoReturnable<Float> cir) {
		float factor = SuperSpeedHandlers.speedFactor((Player) (Object) this);
		if (factor > 1.01f) {
			cir.setReturnValue(cir.getReturnValue() * factor);
		}
	}

	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void projecthero$strengthMining(BlockState state, CallbackInfoReturnable<Float> cir) {
		Player self = (Player) (Object) this;
		if (SuperStrengthHandlers.owns(self)) {
			// A stone-tool floor no matter what's in hand (a better tool still wins), then +25%.
			float floored = Math.max(cir.getReturnValue(), StrengthBareHands.miningSpeed(state, cir.getReturnValue()));
			cir.setReturnValue(floored * 1.25f);
		}
	}

	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void projecthero$geoMining(BlockState state, CallbackInfoReturnable<Float> cir) {
		Player self = (Player) (Object) this;
		if (GeoBareHands.applies(self)) {
			// Iron-tool hands whatever is held (a better tool still wins), and +50% for earth blocks.
			float boosted = GeoBareHands.miningSpeed(state, cir.getReturnValue());
			if (boosted > cir.getReturnValue()) {
				cir.setReturnValue(boosted);
			}
		}
	}

	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void projecthero$symbioteBareHands(BlockState state, CallbackInfoReturnable<Float> cir) {
		Player self = (Player) (Object) this;
		if (SymbioteBareHands.applies(self)) {
			float boosted = SymbioteBareHands.miningSpeed(state, cir.getReturnValue());
			if (boosted > cir.getReturnValue()) {
				cir.setReturnValue(boosted);
			}
		}
	}

	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void projecthero$wolverineClaws(BlockState state, CallbackInfoReturnable<Float> cir) {
		Player self = (Player) (Object) this;
		if (com.projecthero.mod.wolverine.WolverineBareHands.applies(self)) {
			float boosted = com.projecthero.mod.wolverine.WolverineBareHands.miningSpeed(state, cir.getReturnValue());
			if (boosted > cir.getReturnValue()) {
				cir.setReturnValue(boosted);
			}
		}
	}

	@Inject(method = "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$symbioteHarvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		Player self = (Player) (Object) this;
		if (SymbioteBareHands.applies(self) && SymbioteBareHands.correctToolForDrops(state)) {
			cir.setReturnValue(true);
		}
		if (StrengthBareHands.applies(self) && StrengthBareHands.correctToolForDrops(state)) {
			cir.setReturnValue(true);
		}
		if (GeoBareHands.applies(self) && GeoBareHands.correctToolForDrops(state)) {
			cir.setReturnValue(true);
		}
		if (com.projecthero.mod.wolverine.WolverineBareHands.applies(self)
				&& com.projecthero.mod.wolverine.WolverineBareHands.correctToolForDrops(state)) {
			cir.setReturnValue(true);
		}
	}
}

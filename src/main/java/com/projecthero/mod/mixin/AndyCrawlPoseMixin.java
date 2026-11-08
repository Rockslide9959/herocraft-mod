package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyStages;
import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Andy's box and pose follow what his body has left ({@link AndyStages}): a 0.5 rolling head, a 0.6 crawl dragging
 * himself on an arm (vanilla's crawl pose), a 1.3 hand walk, a 1.5 hop on a growing nub, then the normal box. Both sides read
 * the synced part times, so the server's box and every client's agree. The box only grows when the bigger one fits where he
 * is (otherwise he stays small until there is room), so a growing body never wedges him into a ceiling.
 */
@Mixin(Player.class)
public abstract class AndyCrawlPoseMixin {
	@Unique
	private static final EntityDimensions PROJECTHERO$STANDING = EntityDimensions.scalable(0.6f, 1.8f);

	@Unique
	private int projecthero$andyStage = AndyStages.NORMAL;

	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void projecthero$andyStagePose(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		AndyState s = Andy.peek(self);
		int want = AndyStages.NORMAL;
		if (s != null && s.hasPower && !self.isPassenger() && !self.isSpectator() && !self.getAbilities().flying && !self.isFallFlying()) {
			want = AndyStages.stage(s, self.level().getGameTime());
		}
		int cur = this.projecthero$andyStage;
		if (want != cur) {
			EntityDimensions to = projecthero$dims(want);
			EntityDimensions from = projecthero$dims(cur);
			if (to.height() > from.height() + 1.0e-4f
					&& !self.level().noCollision(self, to.makeBoundingBox(self.position()).deflate(1.0E-7))) {
				want = cur; // no room yet: stay small
			}
			if (want != cur) {
				this.projecthero$andyStage = want;
				self.refreshDimensions();
			}
		}
		int st = this.projecthero$andyStage;
		if (AndyStages.dims(st) != null) {
			// flying, the flight lays the body along the look itself (vanilla's crawl tilt on top would turn him over)
			Pose pose = st == AndyStages.DRAG && !com.projecthero.mod.andy.AndyFlight.isFlying(self) ? Pose.SWIMMING : Pose.STANDING;
			if (self.getPose() != pose) {
				self.setPose(pose);
			}
			ci.cancel();
		}
	}

	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void projecthero$andyStageBox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		Player self = (Player) (Object) this;
		int stage = this.projecthero$andyStage;
		if (self.level() != null && self.level().isClientSide && !self.isLocalPlayer()) {
			// another player on this client: vanilla never runs updatePlayerPose for them (their pose comes from the server),
			// so the box follows the synced part times directly (AndyClient refreshes it when the stage changes)
			AndyState s = Andy.peek(self);
			stage = s == null || !s.hasPower || self.isPassenger() ? AndyStages.NORMAL : AndyStages.stage(s, self.level().getGameTime());
		}
		EntityDimensions d = AndyStages.dims(stage);
		if (d != null) {
			cir.setReturnValue(d);
		}
	}

	@Unique
	private static EntityDimensions projecthero$dims(int stage) {
		EntityDimensions d = AndyStages.dims(stage);
		return d != null ? d : PROJECTHERO$STANDING;
	}
}

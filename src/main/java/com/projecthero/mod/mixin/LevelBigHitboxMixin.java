package com.projecthero.mod.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.entity.BigHitboxes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.4: area entity searches also find the {@link BigHitboxes.Big} entities whose position is too far from the search
 * box for the section lookup (Galactus's fist, arms and head). Both sides: the client's crosshair pick runs through here.
 */
@Mixin(Level.class)
public abstract class LevelBigHitboxMixin implements BigHitboxes.Holder {
	@Unique
	private final List<Entity> projecthero$big = new ArrayList<>();

	@Override
	public List<Entity> projecthero$bigHitboxes() {
		return projecthero$big;
	}

	@Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
			at = @At("RETURN"))
	private void projecthero$addBig(Entity except, AABB box, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
		if (!projecthero$big.isEmpty()) {
			BigHitboxes.addMissed((Level) (Object) this, except, box, predicate, cir.getReturnValue());
		}
	}

	@Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
			at = @At("RETURN"))
	private <T extends Entity> void projecthero$addBigTyped(EntityTypeTest<Entity, T> test, AABB box, Predicate<? super T> predicate,
			List<? super T> out, int max, CallbackInfo ci) {
		if (!projecthero$big.isEmpty()) {
			BigHitboxes.addMissed((Level) (Object) this, test, box, predicate, out, max);
		}
	}
}

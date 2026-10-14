package com.projecthero.mod.client.mixin;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.16.2 City part B: villagers have no arm swing (their arms are folded). A swing (a Repair Worker's hammer blow, a
 * City Cop's baton, a firefighter's splash) now bobs the folded arms down and up, so the work reads at a glance.
 */
@Mixin(VillagerModel.class)
public abstract class CityVillagerSwingMixin<T extends Entity> extends HierarchicalModel<T> {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void projecthero$citySwing(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
			CallbackInfo ci) {
		ModelPart arms = getAnyDescendantWithName("arms").orElse(null);
		if (arms == null) {
			return;
		}
		if (!(entity instanceof Villager) || attackTime <= 0f) {
			arms.xRot = -0.75f; // the folded-arms rest pose (the model is shared, so always put it back)
			arms.y = 3.0f;
			return;
		}
		float s = Mth.sin(attackTime * Mth.PI);
		arms.xRot = -0.75f + -s * 1.1f;
		arms.y = 3.0f + s * 1.5f;
	}
}

package com.projecthero.mod.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/**
 * Client-only bridge between a common {@link com.projecthero.mod.armor.SuperheroArmorItem} and its
 * GeckoLib renderer. One of these is created per armour item (lazily, by GeckoLib's animatable
 * cache); it hands GeckoLib a single lazily-built {@link SuperheroArmorRenderer}. Installed onto
 * {@code SuperheroArmorItem.rendererFactory} from {@code ProjectHeroModClient} so {@code src/main}
 * never has to see {@code src/client}.
 */
public class SuperheroArmorRenderProvider implements GeoRenderProvider {
	private SuperheroArmorRenderer renderer;

	@Nullable
	@Override
	public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
			@Nullable EquipmentSlot equipmentSlot, @Nullable HumanoidModel<T> original) {
		if (this.renderer == null) {
			this.renderer = new SuperheroArmorRenderer();
		}
		return this.renderer;
	}
}

package com.projecthero.mod.armor;

import java.util.function.Consumer;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class for every custom superhero armour piece: a vanilla {@link ArmorItem} that also renders
 * through GeckoLib (see docs/ARMOR_MODELS.md). Concrete subclasses are
 * {@link com.projecthero.mod.ironman.item.IronManArmorItem} (the five Iron Man marks) and
 * {@link com.projecthero.mod.item.ThorArmorItem} -- each set stays its own item with its own id,
 * recipe, material, stats, powers and abilities; only the visual model is shared.
 *
 * <p>{@link #armorSetId()} is the key into {@link SuperheroArmorVisuals}, which decides the geometry /
 * texture / animation for this piece. All the GeckoLib plumbing (the animatable cache, the idle
 * controller) lives here once.
 *
 * <h2>Client / server split</h2>
 * This class is common code and must not reference the client-only renderer. The client sets
 * {@link #rendererFactory} during its init; {@link #createGeoRenderer} just forwards to it. On a
 * dedicated server the factory stays null and GeckoLib never asks for a renderer, so nothing
 * client-only is ever touched.
 */
public abstract class SuperheroArmorItem extends ArmorItem implements GeoItem {
	/** The single loop animation played on the armour; also present in every set's animation file. */
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.crimson_vanguard.idle");

	/**
	 * Installed by the client ({@code ProjectHeroModClient}) so common code can hand GeckoLib a
	 * client-only {@code GeoArmorRenderer} without {@code src/main} depending on {@code src/client}.
	 * Null on a dedicated server -- and never consulted there.
	 */
	public static Consumer<Consumer<GeoRenderProvider>> rendererFactory;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	protected SuperheroArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	/** The armour-set id ({@code thor}, {@code mark_iii}, ...) -- the {@link SuperheroArmorVisuals} key. */
	public abstract String armorSetId();

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "base", 5, state -> {
			state.setAndContinue(IDLE);
			return PlayState.CONTINUE;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		if (rendererFactory != null) {
			rendererFactory.accept(consumer);
		}
	}
}

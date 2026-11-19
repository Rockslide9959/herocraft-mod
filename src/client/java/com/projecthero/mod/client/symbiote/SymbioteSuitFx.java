package com.projecthero.mod.client.symbiote;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteSuitStyle;
import com.projecthero.mod.symbiote.item.SymbioteHostArmorItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.17.5: the extra passes drawn over the coloured Symbiote suit ({@link SymbioteSuitTint}):
 * <ul>
 *   <li><b>eyes</b> -- the lenses glow faintly in the eye colour (additive, full bright, head slot only), so they read at night;</li>
 *   <li>while the suit emerges / recedes, the suit-up's raised wet rim riding the front ({@link SymbioteEmerge}).</li>
 * </ul>
 * <p><b>Removed (user: "Remove the enchantment glow or tint from the suit")</b>: the translucent white <i>sheen</i> band that swept
 * up the body every 110 ticks -- exactly what an enchantment glint looks like -- and the pulsing <i>vein</i> pass (a lifted tint of
 * the body colour crawling up the Classic art's veins). The suit is now its painted, coloured texture and nothing else. The old
 * {@code symbiote_host_fx.png} stays in the assets only because {@code make_lethal_protector.js} still reads it; nothing draws it.
 */
public final class SymbioteSuitFx {
	/** v0.17.5 round 2: how far the suit-up's wet rim stands proud of the suit (blocks: 0.16 px). */
	static final float RIM_INFLATE = 0.16f / 16f;

	/** One suit art's eye mask. */
	static final class FxSet {
		final String tag;
		final ResourceLocation maskTexture;
		ResourceLocation eyes;
		boolean failed;

		FxSet(String tag, ResourceLocation maskTexture) {
			this.tag = tag;
			this.maskTexture = maskTexture;
		}
	}

	private static final FxSet CLASSIC_SET = new FxSet("", SymbioteSuitTint.MASK);
	private static final FxSet LETHAL_SET = new FxSet("lethal_", SymbioteSuitTint.LETHAL_MASK);

	/** The eye mask for {@code wearer}'s suit art. */
	static FxSet setFor(Player wearer) {
		return SymbioteSuitTint.styleOf(wearer) == SymbioteSuitStyle.LETHAL_PROTECTOR ? LETHAL_SET : CLASSIC_SET;
	}

	private SymbioteSuitFx() {
	}

	/** Draw the eye glow over a suit model? (built, worn by a visible player whose suit is fully on) */
	static boolean active(@Nullable Entity wearer) {
		if (!(wearer instanceof Player p)) {
			return false;
		}
		FxSet set = setFor(p);
		if (set.failed) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (p.isInvisible() && (mc.player == null || p.isInvisibleTo(mc.player))) {
			return false;
		}
		return !SymbioteReveal.isRevealing(p) && ensure(set);
	}

	/** The GeckoLib layer on {@code SuperheroArmorRenderer}: no-op for anything but the Normal host's suit. */
	public static final class Layer extends GeoRenderLayer<SuperheroArmorItem> {
		public Layer(GeoRenderer<SuperheroArmorItem> renderer) {
			super(renderer);
		}

		@Override
		public void render(PoseStack poseStack, SuperheroArmorItem animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
				MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
			if (!(animatable instanceof SymbioteHostArmorItem) || !(getRenderer() instanceof GeoArmorRenderer<?> armor)) {
				return;
			}
			Entity wearer = armor.getCurrentEntity();
			// v0.17.5 round 2: while the suit emerges / recedes, the raised wet rim riding the front instead of the eye glow
			if (wearer instanceof Player rp && SymbioteEmerge.applies(rp)) {
				Minecraft mc = Minecraft.getInstance();
				if (rp.isInvisible() && (mc.player == null || rp.isInvisibleTo(mc.player))) {
					return;
				}
				ResourceLocation rim = SymbioteEmerge.rimTexture(rp, SymbioteSuitTint.suit(rp), partialTick);
				if (rim != null) {
					RenderType type = RenderType.armorCutoutNoCull(rim);
					SymbioteEmerge.inflate = RIM_INFLATE;
					try {
						getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
								packedLight, packedOverlay, 0xFFFFFFFF);
					} finally {
						SymbioteEmerge.inflate = 0f;
					}
				}
				return;
			}
			if (armor.getCurrentSlot() != net.minecraft.world.entity.EquipmentSlot.HEAD || !active(wearer)) {
				return;
			}
			Player p = (Player) wearer;
			if ((eyeGlow(SymbioteSuitTint.colours(p)) & 0xFFFFFF) == 0) {
				return; // dark eyes: no glow pass at all
			}
			FxSet set = setFor(p);
			RenderType glow = SymbioteFxTypes.glow(set.eyes);
			getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, eyeGlow(SymbioteSuitTint.colours(p)));
		}
	}

	/**
	 * The additive eye glow: the eye colour at up to 40% strength (0xAARRGGBB). v0.17.5: scaled by the colour's brightness
	 * ({@code SymbioteTintMath.eyeGlowStrength}) -- dark eyes get none, so black eyes stay black instead of being lifted by the glow.
	 */
	static int eyeGlow(SymbioteColours c) {
		int e = c.eyes();
		float k = com.projecthero.mod.symbiote.SymbioteTintMath.eyeGlowStrength(e);
		int r = Math.round(((e >> 16) & 0xFF) * k), g = Math.round(((e >> 8) & 0xFF) * k), b = Math.round((e & 0xFF) * k);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------ building the eye mask (once)

	private static boolean ensure(FxSet set) {
		if (set.eyes != null) {
			return true;
		}
		if (set.failed) {
			return false;
		}
		NativeImage mask = SymbioteSuitTint.read(set.maskTexture);
		if (mask == null) {
			set.failed = true;
			return false;
		}
		// white on the lenses (mask channel = eyes), black elsewhere (additive: black adds nothing)
		NativeImage e = new NativeImage(mask.getWidth(), mask.getHeight(), true);
		for (int y = 0; y < mask.getHeight(); y++) {
			for (int x = 0; x < mask.getWidth(); x++) {
				int p = mask.getPixelRGBA(x, y);
				boolean lens = SymbioteSuitTint.alpha(p) > 0 && SymbioteSuitTint.blue(p) >= 192;
				e.setPixelRGBA(x, y, lens ? 0xFFFFFFFF : 0xFF000000);
			}
		}
		set.eyes = ProjectHeroMod.id("dynamic/symbiote_suit_fx/" + set.tag + "eyes");
		Minecraft.getInstance().getTextureManager().register(set.eyes, new DynamicTexture(e));
		mask.close();
		return true;
	}

	/** Releases the masks (resource reload / leaving a world); they are rebuilt on the next draw. */
	static void clear() {
		clear(CLASSIC_SET);
		clear(LETHAL_SET);
	}

	private static void clear(FxSet set) {
		if (set.eyes != null) {
			Minecraft.getInstance().getTextureManager().release(set.eyes);
		}
		set.eyes = null;
		set.failed = false;
	}
}

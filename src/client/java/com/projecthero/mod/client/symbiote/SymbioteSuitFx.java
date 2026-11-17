package com.projecthero.mod.client.symbiote;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteSuitColours;
import com.projecthero.mod.symbiote.item.SymbioteHostArmorItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
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
 * v0.17.5: what makes the new Symbiote suit look alive, drawn over the coloured suit ({@link SymbioteSuitTint}) in three cheap
 * extra passes of the same model:
 * <ul>
 *   <li><b>sheen</b> -- a soft wet highlight band that sweeps diagonally up the body every {@link #SHEEN_PERIOD} ticks, then
 *       rests. Translucent white, lit like the suit;</li>
 *   <li><b>veins</b> -- the suit's vein network pulses, slow waves crawling upward through it, in a lifted tint of the body
 *       colour (a darker one on a pale suit);</li>
 *   <li><b>eyes</b> -- the lenses glow faintly in the eye colour (additive, full bright), so they read at night.</li>
 * </ul>
 * The sheen and veins are pre-built once from {@code textures/armor/symbiote_host_fx.png} (R = where the sweep is, G = how glossy,
 * B = vein strength) into {@link #FRAMES} small dynamic textures each; a frame is picked from the game clock and cross-faded with
 * the next (two passes) so the motion is smooth. Colour-independent, so they are shared by every wearer; the body / eye colour is
 * the vertex colour. Nothing is rebuilt per frame. Skipped while the suit spreads on or off, and for an invisible wearer.
 */
public final class SymbioteSuitFx {
	public static final ResourceLocation FX = ProjectHeroMod.id("textures/armor/symbiote_host_fx.png");
	public static final int FRAMES = 32;
	/** Ticks for one sheen sweep (the band crosses the body in the first ~60% and rests for the rest). */
	public static final int SHEEN_PERIOD = 110;
	/** Ticks for one full vein pulse cycle. */
	public static final int VEIN_PERIOD = 72;
	/** v0.17.5 round 2: how far the suit-up's wet rim stands proud of the suit (blocks: 0.16 px). */
	static final float RIM_INFLATE = 0.16f / 16f;

	/** v0.17.5 Lethal Protector: its own sheen map (the skin's highlights, no veins); its eye mask comes from its tint mask. */
	public static final ResourceLocation LETHAL_FX = ProjectHeroMod.id("textures/armor/symbiote_host_lethal_fx.png");

	/** One suit style's pre-built frames. */
	static final class FxSet {
		final String tag;
		final ResourceLocation fxTexture;
		final ResourceLocation maskTexture;
		final boolean veinsOn;
		ResourceLocation[] sheen;
		ResourceLocation[] veins;
		ResourceLocation eyes;
		boolean failed;

		FxSet(String tag, ResourceLocation fxTexture, ResourceLocation maskTexture, boolean veinsOn) {
			this.tag = tag;
			this.fxTexture = fxTexture;
			this.maskTexture = maskTexture;
			this.veinsOn = veinsOn;
		}
	}

	private static final FxSet CLASSIC_SET = new FxSet("", FX, SymbioteSuitTint.MASK, true);
	private static final FxSet LETHAL_SET = new FxSet("lethal_", LETHAL_FX, SymbioteSuitTint.LETHAL_MASK, false);

	/** The frames for {@code wearer}'s suit style. */
	static FxSet setFor(Player wearer) {
		return SymbioteSuitTint.styleOf(wearer) == com.projecthero.mod.symbiote.SymbioteSuitStyle.LETHAL_PROTECTOR ? LETHAL_SET : CLASSIC_SET;
	}

	private SymbioteSuitFx() {
	}

	/** The vein tint for a colour triple: a lifted body colour, or a darker one on a pale suit. 0xRRGGBB. */
	public static int veinColour(SymbioteColours c) {
		int p = c.primary();
		float r = ((p >> 16) & 0xFF) / 255f, g = ((p >> 8) & 0xFF) / 255f, b = (p & 0xFF) / 255f;
		float lum = 0.299f * r + 0.587f * g + 0.114f * b;
		float sr = ((c.secondary() >> 16) & 0xFF) / 255f, sg = ((c.secondary() >> 8) & 0xFF) / 255f, sb = (c.secondary() & 0xFF) / 255f;
		float or, og, ob;
		if (lum > 0.6f) {
			or = r * 0.55f; og = g * 0.55f; ob = b * 0.6f;
		} else {
			float m = Math.max(r, Math.max(g, b));
			float hr = m < 0.03f ? 0.6f : r / m, hg = m < 0.03f ? 0.66f : g / m, hb = m < 0.03f ? 0.85f : b / m;
			or = r + (hr * 0.55f - r) * 0.7f + sr * 0.08f;
			og = g + (hg * 0.55f - g) * 0.7f + sg * 0.08f;
			ob = b + (hb * 0.55f - b) * 0.7f + sb * 0.08f;
		}
		return Math.round(Math.min(1f, or) * 255) << 16 | Math.round(Math.min(1f, og) * 255) << 8 | Math.round(Math.min(1f, ob) * 255);
	}

	/** Draw the three passes over a suit model? (built, worn by a visible player whose suit is fully on) */
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

	/** Sheen / vein frame index + blend for a game time. */
	private static float phase(long gameTime, float partialTick, int period) {
		double t = ((gameTime % period) + partialTick) / period;
		return (float) (t * FRAMES);
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
			// v0.17.5 round 2: while the suit emerges / recedes, the raised wet rim riding the front instead of the sheen / veins / eyes
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
			if (!active(wearer)) {
				return;
			}
			Player p = (Player) wearer;
			FxSet set = setFor(p);
			SymbioteColours colours = SymbioteSuitTint.colours(p);
			long now = p.level().getGameTime();
			// eyes: only the head pass carries the lenses
			if (armor.getCurrentSlot() == net.minecraft.world.entity.EquipmentSlot.HEAD) {
				RenderType glow = SymbioteFxTypes.glow(set.eyes);
				getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, eyeGlow(colours));
			}
			if (set.veinsOn) {
				float vf = phase(now, partialTick, VEIN_PERIOD);
				pass(bakedModel, poseStack, bufferSource, animatable, set.veins, vf, partialTick, packedLight, veinColour(colours), 0.55f);
			}
			float sf = phase(now, partialTick, SHEEN_PERIOD);
			pass(bakedModel, poseStack, bufferSource, animatable, set.sheen, sf, partialTick, packedLight, 0xFFFFFF, 0.85f);
		}

		private void pass(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers, SuperheroArmorItem item, ResourceLocation[] frames,
				float f, float partialTick, int light, int rgb, float strength) {
			int i0 = (int) Math.floor(f) % FRAMES;
			int i1 = (i0 + 1) % FRAMES;
			float blend = f - (float) Math.floor(f);
			for (int k = 0; k < 2; k++) {
				float a = strength * (k == 0 ? 1f - blend : blend);
				if (a < 0.02f) {
					continue;
				}
				RenderType type = SymbioteFxTypes.sheen(frames[k == 0 ? i0 : i1]);
				getRenderer().reRender(model, pose, buffers, item, type, buffers.getBuffer(type), partialTick, light,
						OverlayTexture.NO_OVERLAY, Math.round(a * 255) << 24 | rgb);
			}
		}
	}

	/** First person: the same sheen + vein passes over the sleeve the player sees ({@code SuperheroFirstPersonArm}). */
	public static void renderSleeve(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, Player player) {
		if (!active(player)) {
			return;
		}
		float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		SymbioteColours colours = SymbioteSuitTint.colours(player);
		FxSet set = setFor(player);
		if (set.veinsOn) {
			sleevePass(pose, buffers, light, arm, set.veins, phase(now, pt, VEIN_PERIOD), veinColour(colours), 0.55f);
		}
		sleevePass(pose, buffers, light, arm, set.sheen, phase(now, pt, SHEEN_PERIOD), 0xFFFFFF, 0.85f);
	}

	private static void sleevePass(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, ResourceLocation[] frames, float f,
			int rgb, float strength) {
		int i0 = (int) Math.floor(f) % FRAMES;
		int i1 = (i0 + 1) % FRAMES;
		float blend = f - (float) Math.floor(f);
		for (int k = 0; k < 2; k++) {
			float a = strength * (k == 0 ? 1f - blend : blend);
			if (a < 0.02f) {
				continue;
			}
			arm.render(pose, buffers.getBuffer(SymbioteFxTypes.sheen(frames[k == 0 ? i0 : i1])), light, OverlayTexture.NO_OVERLAY,
					Math.round(a * 255) << 24 | rgb);
		}
	}

	/** The additive eye glow: the eye colour at a little under half strength (0xAARRGGBB). */
	static int eyeGlow(SymbioteColours c) {
		int e = c.eyes();
		int r = ((e >> 16) & 0xFF) * 40 / 100, g = ((e >> 8) & 0xFF) * 40 / 100, b = (e & 0xFF) * 40 / 100;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------ building the frames (once)

	private static boolean ensure(FxSet set) {
		if (set.sheen != null) {
			return true;
		}
		if (set.failed) {
			return false;
		}
		NativeImage fx = SymbioteSuitTint.read(set.fxTexture);
		NativeImage mask = SymbioteSuitTint.read(set.maskTexture);
		if (fx == null || mask == null) {
			set.failed = true;
			if (fx != null) {
				fx.close();
			}
			if (mask != null) {
				mask.close();
			}
			return false;
		}
		int w = fx.getWidth(), h = fx.getHeight();
		ResourceLocation[] sh = new ResourceLocation[FRAMES];
		ResourceLocation[] vn = new ResourceLocation[FRAMES];
		for (int k = 0; k < FRAMES; k++) {
			float t = k / (float) FRAMES;
			// the band sweeps from below the feet to above the head over the first 60% of the cycle, then rests off-body
			float band = -0.12f + (t / 0.6f) * 1.24f;
			NativeImage s = new NativeImage(w, h, true);
			NativeImage v = new NativeImage(w, h, true);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int p = fx.getPixelRGBA(x, y);
					int cover = SymbioteSuitTint.alpha(p);
					if (cover == 0) {
						continue;
					}
					float sweep = SymbioteSuitTint.red(p) / 255f;
					float gloss = SymbioteSuitTint.green(p) / 255f;
					float vein = SymbioteSuitTint.blue(p) / 255f;
					float d = (sweep - band) / 0.07f;
					float sa = t < 0.6f ? gloss * (float) Math.exp(-d * d) * 0.75f : 0f;
					// a faint second, wider glint trailing the band
					float d2 = (sweep - band + 0.16f) / 0.12f;
					sa += t < 0.62f ? gloss * (float) Math.exp(-d2 * d2) * 0.18f : 0f;
					if (sa > 0.004f) {
						s.setPixelRGBA(x, y, SymbioteSuitTint.abgr(Math.min(255, Math.round(sa * 255)), 255, 255, 255));
					}
					if (vein > 0.01f) {
						// waves crawling upward through the veins
						float wave = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * (sweep * 3.2f - t));
						float va = vein * (0.22f + 0.78f * wave * wave);
						v.setPixelRGBA(x, y, SymbioteSuitTint.abgr(Math.min(255, Math.round(va * 255)), 255, 255, 255));
					}
				}
			}
			sh[k] = ProjectHeroMod.id("dynamic/symbiote_suit_fx/" + set.tag + "sheen_" + k);
			vn[k] = ProjectHeroMod.id("dynamic/symbiote_suit_fx/" + set.tag + "veins_" + k);
			Minecraft.getInstance().getTextureManager().register(sh[k], new DynamicTexture(s));
			Minecraft.getInstance().getTextureManager().register(vn[k], new DynamicTexture(v));
		}
		// the eye glow mask: white on the lenses (mask channel = eyes), black elsewhere (additive: black adds nothing)
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
		fx.close();
		mask.close();
		set.sheen = sh;
		set.veins = vn;
		return true;
	}

	/** Releases the frames (resource reload / leaving a world); they are rebuilt on the next draw. */
	static void clear() {
		clear(CLASSIC_SET);
		clear(LETHAL_SET);
	}

	private static void clear(FxSet set) {
		var tm = Minecraft.getInstance().getTextureManager();
		if (set.sheen != null) {
			for (ResourceLocation id : set.sheen) {
				tm.release(id);
			}
			for (ResourceLocation id : set.veins) {
				tm.release(id);
			}
			tm.release(set.eyes);
		}
		set.sheen = null;
		set.veins = null;
		set.eyes = null;
		set.failed = false;
	}
}

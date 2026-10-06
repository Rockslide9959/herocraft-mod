package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.14.21 round two: the Iron Man suits' emissive details -- eye slits, arc reactor, palm repulsors (Mark 1: its dim
 * reactor dot) -- glow in the dark. Re-renders the piece with {@code textures/armor/<mark>_glowmask.png}
 * (generated from each mark's own cyan texels by {@code scratchpad/gen_v01421_ironman_round2.js}) in the additive,
 * fullbright {@link RenderType#eyes} pass, the way spider / enderman eyes glow.
 *
 * <p>Deliberately <b>not</b> GeckoLib's {@code AutoGlowingGeoLayer}: that one cuts the glow texels out of the base
 * texture in GPU memory, and the same {@code mark_*.png} is also sampled by the first-person arm, the suit-up
 * {@code ArmorSweepReveal} copies and the item previews -- they would all get holes. This layer leaves the base alone.
 *
 * <p>Follows the self-assembly: a bone that has not snapped home yet does not glow -- its lights come on the moment it
 * locks into place ({@link IronManAssemblyClient#glowPass}); the eyes go dark while the faceplate is raised. Hidden bones (open faceplate, slot
 * visibility) stay hidden because the re-render goes through the same bone-visibility pass.
 */
public class IronManSuitGlowLayer extends GeoRenderLayer<SuperheroArmorItem> {
	public static final Set<String> MARKS = Set.of("mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii", "mark_8");
	private static final Map<String, ResourceLocation> GLOWMASKS = new HashMap<>();

	public IronManSuitGlowLayer(GeoRenderer<SuperheroArmorItem> renderer) {
		super(renderer);
	}

	/** {@code textures/armor/<set>_glowmask.png} for an Iron Man mark, else null. */
	public static @Nullable ResourceLocation glowmask(String setId) {
		if (!MARKS.contains(setId)) {
			return null;
		}
		return GLOWMASKS.computeIfAbsent(setId, s -> ProjectHeroMod.id("textures/armor/" + s + "_glowmask.png"));
	}

	@Override
	public void render(PoseStack poseStack, SuperheroArmorItem animatable, BakedGeoModel bakedModel,
			@Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
			float partialTick, int packedLight, int packedOverlay) {
		if (!(animatable instanceof IronManArmorItem item) || !(getRenderer() instanceof GeoArmorRenderer<?> armor)) {
			return;
		}
		ResourceLocation mask = glowmask(item.armorSetId());
		Entity wearer = armor.getCurrentEntity();
		if (mask == null || wearer == null) {
			return;
		}
		if (wearer.isInvisible() && (Minecraft.getInstance().player == null || wearer.isInvisibleTo(Minecraft.getInstance().player))) {
			return;
		}
		// v0.15.5: a part in a Stark Gantry clamp, or a piece the gantry is still building on / taking off, stays dark
		if (IronManGantryBuild.solo >= 0 || !Float.isNaN(IronManGantryBuild.standMk5Frame) || wearer instanceof Player gp && armor.getCurrentSlot() != null
				&& (IronManGantryBuild.incomplete(gp, armor.getCurrentSlot(), item.armorSetId(), partialTick)
						|| IronManGantryBuild.mk5Incomplete(gp, armor.getCurrentSlot(), item.armorSetId(), partialTick))) {
			return;
		}
		// v0.14.29 agent F: a badly damaged suit's lights flicker
		if (wearer instanceof Player fp && IronManBattleDamage.glowFlickerOff(fp)) {
			return;
		}
		// v0.14.21 self-assembly: per bone, not per piece -- IronManAssemblyClient keeps a bone dark (skips it in this
		// pass) until it has snapped home, and the eyes off while the faceplate is raised. The arc reactor (chest done)
		// and the eyes (faceplate snapped / sealed) flash: extra additive passes.
		RenderType glow = RenderType.eyes(mask);
		int passes = 1;
		if (wearer instanceof Player p && armor.getCurrentSlot() != null) {
			float flash = IronManAssemblyClient.flash(p, armor.getCurrentSlot(), partialTick);
			passes += flash > 0.66f ? 3 : flash > 0.33f ? 2 : flash > 0f ? 1 : 0;
		}
		IronManAssemblyClient.glowPass = true;
		try {
			for (int i = 0; i < passes; i++) {
				getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
						partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			}
		} finally {
			IronManAssemblyClient.glowPass = false;
		}
	}
}

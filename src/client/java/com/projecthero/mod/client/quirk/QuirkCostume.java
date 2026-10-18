package com.projecthero.mod.client.quirk;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * v0.16.7: the Quirk hero costumes -- a second 64x64 player skin ({@code textures/entity/quirk/<id>_wide.png} or
 * {@code _slim.png}, picked by the wearer's skin model; costume only, transparent where the wearer's own skin shows)
 * drawn OVER the wearer, the way the Miraculous suits are: the base layer a hair over the body, the outer layer at
 * vanilla's own overlay offsets. While it is drawn the wearer's own outer layer is hidden ({@link QuirkClient#hidesSkinOverlay})
 * so it never pokes through; if the costume leaves its whole hat layer empty (no mask / hood), the wearer's own hat
 * layer (hair, glasses) is drawn again so they keep their hair.
 *
 * <p>A missing texture draws nothing (and hides nothing). Which textures exist is cached and re-read on every resource
 * reload.
 */
public final class QuirkCostume {
	/** The base layer's inflation (model pixels): a hair over the body, never bulky. */
	public static final float BASE_INFLATE = 0.02f;

	/** One costume texture: the id, and whether its hat-layer region (u 32-63, v 0-15) has any pixel at all. */
	private record Tex(ResourceLocation id, boolean hasHat) {
	}

	private static final Map<Quirk, Optional<Tex>> WIDE = new EnumMap<>(Quirk.class);
	private static final Map<Quirk, Optional<Tex>> SLIM = new EnumMap<>(Quirk.class);

	private static PlayerModel<LivingEntity> slimBase;
	private static PlayerModel<LivingEntity> wideBase;
	private static PlayerModel<LivingEntity> slimOver;
	private static PlayerModel<LivingEntity> wideOver;

	private QuirkCostume() {
	}

	public static void initialize() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ProjectHeroMod.id("quirk_costumes");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				WIDE.clear();
				SLIM.clear();
			}
		});
	}

	public static ResourceLocation textureId(Quirk q, boolean slim) {
		return ProjectHeroMod.id("textures/entity/quirk/" + q.id + (slim ? "_slim.png" : "_wide.png"));
	}

	private static @Nullable Tex tex(Quirk q, boolean slim) {
		Map<Quirk, Optional<Tex>> cache = slim ? SLIM : WIDE;
		Optional<Tex> t = cache.get(q);
		if (t == null) {
			t = load(textureId(q, slim));
			cache.put(q, t);
		}
		return t.orElse(null);
	}

	private static Optional<Tex> load(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return Optional.empty();
		}
		boolean hat = true;
		try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
			hat = false;
			int s = img.getWidth() / 64;
			for (int y = 0; y < 16 * s && !hat; y++) {
				for (int x = 32 * s; x < 64 * s; x++) {
					if (((img.getPixelRGBA(x, y) >>> 24) & 0xFF) != 0) {
						hat = true;
						break;
					}
				}
			}
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("Quirk costume {} unreadable: {}", id, e.toString());
		}
		return Optional.of(new Tex(id, hat));
	}

	private static boolean slim(Player player) {
		return player instanceof AbstractClientPlayer acp && acp.getSkin().model() == PlayerSkin.Model.SLIM;
	}

	/** The costume texture drawn on {@code player} right now, or null (no Quirk, costume off, no texture). */
	public static @Nullable ResourceLocation drawn(Player player) {
		Quirk q = Quirks.quirk(player);
		if (q == null || !Quirks.costumeOn(player)) {
			return null;
		}
		Tex t = tex(q, slim(player));
		return t == null ? null : t.id();
	}

	/** True while a costume is drawn (the wearer's own outer layer is hidden then). */
	public static boolean shows(Player player) {
		return drawn(player) != null;
	}

	// ---------------------------------------------------------------- the models

	private static PlayerModel<LivingEntity> base(boolean slim) {
		if (slim) {
			if (slimBase == null) {
				slimBase = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(BASE_INFLATE), true), 64, 64).bakeRoot(), true);
			}
			return slimBase;
		}
		if (wideBase == null) {
			wideBase = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(BASE_INFLATE), false), 64, 64).bakeRoot(), false);
		}
		return wideBase;
	}

	private static PlayerModel<LivingEntity> over(boolean slim) {
		if (slim) {
			if (slimOver == null) {
				slimOver = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64).bakeRoot(), true);
			}
			return slimOver;
		}
		if (wideOver == null) {
			wideOver = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
		}
		return wideOver;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static PlayerModel<LivingEntity> posed(PlayerModel<LivingEntity> m, HumanoidModel<?> parent, boolean baseParts) {
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		m.head.visible = baseParts && parent.head.visible;
		m.body.visible = baseParts && parent.body.visible;
		m.rightArm.visible = baseParts && parent.rightArm.visible;
		m.leftArm.visible = baseParts && parent.leftArm.visible;
		m.rightLeg.visible = baseParts && parent.rightLeg.visible;
		m.leftLeg.visible = baseParts && parent.leftLeg.visible;
		m.hat.visible = !baseParts && parent.head.visible;
		m.jacket.visible = !baseParts && parent.body.visible;
		m.rightSleeve.visible = !baseParts && parent.rightArm.visible;
		m.leftSleeve.visible = !baseParts && parent.leftArm.visible;
		m.rightPants.visible = !baseParts && parent.rightLeg.visible;
		m.leftPants.visible = !baseParts && parent.leftLeg.visible;
		return m;
	}

	// ---------------------------------------------------------------- drawing

	/** Third person (every viewer): the costume over {@code parent}, then the wearer's own hair if the costume has no hat layer. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> parent) {
		Quirk q = Quirks.quirk(player);
		if (q == null || !Quirks.costumeOn(player)) {
			return;
		}
		boolean slim = slim(player);
		Tex t = tex(q, slim);
		if (t == null) {
			return;
		}
		int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
		RenderType type = RenderType.entityTranslucent(t.id());
		posed(base(slim), parent, true).renderToBuffer(pose, buffers.getBuffer(type), light, overlay, 0xFFFFFFFF);
		posed(over(slim), parent, false).renderToBuffer(pose, buffers.getBuffer(type), light, overlay, 0xFFFFFFFF);
		if (!t.hasHat() && player.isModelPartShown(PlayerModelPart.HAT)) {
			boolean was = parent.hat.visible;
			parent.hat.visible = true;
			parent.hat.copyFrom(parent.head);
			parent.hat.render(pose, buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture())), light, overlay);
			parent.hat.visible = was;
		}
	}

	/** First person: the costume sleeve over the bare arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, boolean right) {
		ResourceLocation id = drawn(player);
		if (id == null) {
			return;
		}
		boolean slim = slim(player);
		ModelPart a = right ? base(slim).rightArm : base(slim).leftArm;
		ModelPart sleeve = right ? over(slim).rightSleeve : over(slim).leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(id)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(id)), light, OverlayTexture.NO_OVERLAY);
	}
}

package com.projecthero.mod.client.quirk;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.item.QuirkSuits;

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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user: "getting the quirks shouldn't change your character model"): the Quirk hero suits drawn from WORN
 * suit pieces ({@link QuirkSuits}) -- holding a Quirk changes nothing. Each suit's look is its 64x64 costume skin
 * ({@code textures/entity/quirk/<quirk>_wide.png} or {@code _slim.png}, picked by the wearer's skin model; costume only,
 * transparent where the wearer's own skin shows), drawn OVER the wearer: the base layer a hair over the body
 * ({@link #BASE_INFLATE}), the outer layer at vanilla's overlay offsets. Each piece draws only its own parts:
 * <ul>
 *   <li>helmet: head + hat layer;</li>
 *   <li>chestplate: body + jacket, both arms + sleeves (and the first-person sleeve, {@link #renderArm});</li>
 *   <li>leggings: the legs + trousers above {@link #BOOT_ROW} (thighs, knees);</li>
 *   <li>boots: the legs + trousers from {@link #BOOT_ROW} down, and the soles.</li>
 * </ul>
 * The leg split is two masked copies of the skin built once per texture ({@link DynamicTexture}s). The wearer's own
 * outer layer is hidden only where a piece covers it ({@link #hideCoveredOverlay}); a helmet whose costume has an empty
 * hat region (u 32-63, v 0-15) keeps the wearer's own hat layer (hair). The vanilla armour model is never drawn for
 * these pieces ({@code QuirkSuitArmourMixin}).
 *
 * <p>The one exception to "a Quirk never changes your look" (user): holding <b>Dark Shadow</b> turns the head into a
 * bird's ({@link #DARK_SHADOW_HEAD}, head + hat only) for every viewer -- a mutation, so it stays while Erased -- and
 * it wins over any Quirk suit helmet.
 *
 * <p>A missing texture draws nothing (and hides nothing). Textures are cached and re-read on every resource reload.
 */
public final class QuirkCostume {
	/** The base layer's inflation (model pixels): a hair over the body, never bulky. */
	public static final float BASE_INFLATE = 0.02f;
	/** The first leg-side texel row (0 = top of the thigh, 11 = the ankle) drawn by the boots; above it, the leggings. */
	public static final int BOOT_ROW = 7;
	public static final ResourceLocation DARK_SHADOW_HEAD = ProjectHeroMod.id("textures/entity/quirk/dark_shadow_head.png");

	/** One costume texture: the id, its hat region has any pixel, and the leggings / boots masked copies. */
	private record Tex(ResourceLocation id, boolean hasHat, ResourceLocation legs, ResourceLocation boots) {
	}

	private static final Map<ResourceLocation, Optional<Tex>> CACHE = new HashMap<>();
	private static final List<ResourceLocation> DYNAMIC = new ArrayList<>();

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
				CACHE.clear();
				for (ResourceLocation id : DYNAMIC) {
					Minecraft.getInstance().getTextureManager().release(id);
				}
				DYNAMIC.clear();
			}
		});
	}

	public static ResourceLocation textureId(Quirk q, boolean slim) {
		return ProjectHeroMod.id("textures/entity/quirk/" + q.id + (slim ? "_slim.png" : "_wide.png"));
	}

	private static @Nullable Tex tex(Quirk q, boolean slim) {
		return tex(textureId(q, slim));
	}

	private static @Nullable Tex tex(ResourceLocation id) {
		Optional<Tex> t = CACHE.get(id);
		if (t == null) {
			t = load(id);
			CACHE.put(id, t);
		}
		return t.orElse(null);
	}

	private static Optional<Tex> load(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return Optional.empty();
		}
		try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
			int s = img.getWidth() / 64;
			boolean hat = false;
			for (int y = 0; y < 16 * s && !hat; y++) {
				for (int x = 32 * s; x < 64 * s; x++) {
					if (((img.getPixelRGBA(x, y) >>> 24) & 0xFF) != 0) {
						hat = true;
						break;
					}
				}
			}
			ResourceLocation legs = masked(img, s, false);
			ResourceLocation boots = masked(img, s, true);
			return Optional.of(new Tex(id, hat, legs, boots));
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("Quirk costume {} unreadable: {}", id, e.toString());
			return Optional.empty();
		}
	}

	/** The four leg boxes of a skin (right leg, left leg, right trousers, left trousers): their unwrap origins. */
	private static final int[][] LEG_BOXES = { { 0, 16 }, { 16, 48 }, { 0, 32 }, { 0, 48 } };

	/**
	 * A copy of the skin with only the leggings' (upper legs + leg tops) or the boots' (lower legs + soles) texels of the
	 * four leg boxes; everything else transparent. A 4x12x4 box unwraps at (ox, oy): top face (ox+4, oy), bottom face
	 * (ox+8, oy), the four sides in the 16x12 strip at (ox, oy+4).
	 */
	private static ResourceLocation masked(NativeImage src, int s, boolean boots) {
		NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), true);
		for (int[] b : LEG_BOXES) {
			int ox = b[0] * s, oy = b[1] * s;
			// top / bottom faces
			int fx = (boots ? b[0] + 8 : b[0] + 4) * s;
			copy(src, out, fx, oy, 4 * s, 4 * s);
			int from = boots ? BOOT_ROW : 0, to = boots ? 12 : BOOT_ROW;
			copy(src, out, ox, oy + (4 + from) * s, 16 * s, (to - from) * s);
		}
		ResourceLocation id = Minecraft.getInstance().getTextureManager().register("projecthero_quirk_suit_" + (boots ? "boots" : "legs"),
				new DynamicTexture(out));
		DYNAMIC.add(id);
		return id;
	}

	private static void copy(NativeImage src, NativeImage dst, int x0, int y0, int w, int h) {
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				dst.setPixelRGBA(x, y, src.getPixelRGBA(x, y));
			}
		}
	}

	private static boolean slim(LivingEntity entity) {
		return entity instanceof AbstractClientPlayer acp && acp.getSkin().model() == PlayerSkin.Model.SLIM;
	}

	/** The bird head is drawn on this player (holds Dark Shadow: a mutation, so Erased or not). */
	public static boolean birdHead(LivingEntity entity) {
		return entity instanceof Player p && Quirks.holds(p, Quirk.DARK_SHADOW);
	}

	/** The worn suit piece's texture in this slot (null: no Quirk suit there, or no texture). */
	private static @Nullable Tex worn(LivingEntity entity, EquipmentSlot slot) {
		// v0.17.3: Andy's Suit is drawn the same way, from its own skin
		if (com.projecthero.mod.andy.item.AndySuitItem.wornIn(entity, slot)) {
			return tex(com.projecthero.mod.andy.item.AndySuitItem.texture(slim(entity)));
		}
		Quirk q = QuirkSuits.wornIn(entity, slot);
		return q == null ? null : tex(q, slim(entity));
	}

	/**
	 * From {@code PlayerModelMixin} (setupAnim TAIL): hide the wearer's own outer layer only on the parts a worn Quirk
	 * suit piece covers (and the hat under the Dark Shadow bird head).
	 */
	public static void hideCoveredOverlay(Player player, PlayerModel<?> model) {
		Tex head = worn(player, EquipmentSlot.HEAD);
		if (birdHead(player) || (head != null && head.hasHat())) {
			model.hat.visible = false;
		}
		if (worn(player, EquipmentSlot.CHEST) != null) {
			model.jacket.visible = false;
			model.leftSleeve.visible = false;
			model.rightSleeve.visible = false;
		}
		if (worn(player, EquipmentSlot.LEGS) != null || worn(player, EquipmentSlot.FEET) != null) {
			model.leftPants.visible = false;
			model.rightPants.visible = false;
		}
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

	/** {@code m} posed like {@code parent}, with only the chosen parts (base or outer layer) visible. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static PlayerModel<LivingEntity> posed(PlayerModel<LivingEntity> m, HumanoidModel<?> parent, boolean baseParts,
			boolean head, boolean body, boolean legs) {
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		boolean vHead = vis(parent.head, com.projecthero.mod.andy.data.AndyState.HEAD);
		boolean vBody = vis(parent.body, com.projecthero.mod.andy.data.AndyState.BODY);
		boolean vRA = vis(parent.rightArm, com.projecthero.mod.andy.data.AndyState.RIGHT_ARM);
		boolean vLA = vis(parent.leftArm, com.projecthero.mod.andy.data.AndyState.LEFT_ARM);
		boolean vRL = vis(parent.rightLeg, com.projecthero.mod.andy.data.AndyState.RIGHT_LEG);
		boolean vLL = vis(parent.leftLeg, com.projecthero.mod.andy.data.AndyState.LEFT_LEG);
		// v0.17.3: a chestplate sleeve torn off with its shoulder pad (AndyArmour) is gone until the piece is repaired
		if (body) {
			vRA &= (andyLostPads & com.projecthero.mod.andy.AndyArmour.PAD_RIGHT) == 0;
			vLA &= (andyLostPads & com.projecthero.mod.andy.AndyArmour.PAD_LEFT) == 0;
		}
		head &= vHead;
		body &= vBody;
		legs &= vRL || vLL;
		m.head.visible = baseParts && head;
		m.body.visible = baseParts && body;
		m.rightArm.visible = baseParts && body && vRA;
		m.leftArm.visible = baseParts && body && vLA;
		m.rightLeg.visible = baseParts && legs && vRL;
		m.leftLeg.visible = baseParts && legs && vLL;
		m.hat.visible = !baseParts && head;
		m.jacket.visible = !baseParts && body;
		m.rightSleeve.visible = !baseParts && body && vRA;
		m.leftSleeve.visible = !baseParts && body && vLA;
		m.rightPants.visible = !baseParts && legs && vRL;
		m.leftPants.visible = !baseParts && legs && vLL;
		return m;
	}

	/**
	 * v0.17.3: for an Andy, which of his six parts may wear a suit piece (bit per AndyState part; -1 = not an Andy, follow the
	 * parent model) and which shoulder pads his chestplate has lost. The same rule as worn armour (AndyArmorPartsMixin): nothing on a
	 * part that is gone or regrowing, while a part with only its hand or foot cut keeps its piece (the parent model hides those for
	 * Andy's own cut drawing, so his own state is read instead).
	 */
	private static int andyParts = -1, andyLostPads = 0;

	private static boolean vis(ModelPart parentPart, int andyPart) {
		return andyParts < 0 ? parentPart.visible : (andyParts & (1 << andyPart)) != 0;
	}

	/** Sets {@link #andyParts} / {@link #andyLostPads} for this wearer (cleared with {@link #endAndy}). */
	private static void beginAndy(LivingEntity entity) {
		andyParts = -1;
		andyLostPads = 0;
		if (entity instanceof Player p) {
			com.projecthero.mod.andy.data.AndyState s = com.projecthero.mod.andy.Andy.peek(p);
			if (s != null && s.hasPower) {
				int mask = 0;
				for (int i = 0; i < com.projecthero.mod.andy.data.AndyState.PARTS; i++) {
					if (!s.broken(i)) {
						mask |= 1 << i;
					}
				}
				andyParts = mask;
			}
		}
		andyLostPads = com.projecthero.mod.andy.AndyArmour.wornLostPads(entity);
	}

	private static void endAndy() {
		andyParts = -1;
		andyLostPads = 0;
	}

	private static void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, HumanoidModel<?> parent, boolean slim,
			ResourceLocation texture, boolean head, boolean body, boolean legs) {
		RenderType type = RenderType.entityTranslucent(texture);
		posed(base(slim), parent, true, head, body, legs).renderToBuffer(pose, buffers.getBuffer(type), light, overlay, 0xFFFFFFFF);
		posed(over(slim), parent, false, head, body, legs).renderToBuffer(pose, buffers.getBuffer(type), light, overlay, 0xFFFFFFFF);
	}

	// ---------------------------------------------------------------- drawing

	/** Third person (every viewer; players, armour stands, mannequins, any humanoid): the worn suit pieces + the bird head. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidModel<?> parent) {
		if (entity instanceof Player && entity.isInvisible()) {
			return;
		}
		boolean bird = birdHead(entity);
		if (!bird && !QuirkSuits.anyWorn(entity) && !com.projecthero.mod.andy.item.AndySuitItem.anyWorn(entity)) {
			return;
		}
		beginAndy(entity);
		try {
			renderPieces(pose, buffers, light, entity, parent, bird);
		} finally {
			endAndy();
		}
	}

	private static void renderPieces(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidModel<?> parent, boolean bird) {
		boolean slim = slim(entity);
		int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
		Tex head = worn(entity, EquipmentSlot.HEAD);
		Tex chest = worn(entity, EquipmentSlot.CHEST);
		Tex legs = worn(entity, EquipmentSlot.LEGS);
		Tex feet = worn(entity, EquipmentSlot.FEET);
		if (bird) {
			draw(pose, buffers, light, overlay, parent, slim, DARK_SHADOW_HEAD, true, false, false);
		} else if (head != null) {
			draw(pose, buffers, light, overlay, parent, slim, head.id(), true, false, false);
		}
		if (chest != null) {
			draw(pose, buffers, light, overlay, parent, slim, chest.id(), false, true, false);
		}
		if (legs != null) {
			draw(pose, buffers, light, overlay, parent, slim, legs.legs(), false, false, true);
		}
		if (feet != null) {
			draw(pose, buffers, light, overlay, parent, slim, feet.boots(), false, false, true);
		}
	}

	/**
	 * v0.16.7: only the legs (worn leggings / boots) on {@code parent}'s posed legs -- the Engine Quirk's first-person legs
	 * ({@code EngineFirstPersonLegs}).
	 */
	public static void renderLegs(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidModel<?> parent) {
		beginAndy(entity);
		try {
			renderLegPieces(pose, buffers, light, entity, parent);
		} finally {
			endAndy();
		}
	}

	private static void renderLegPieces(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidModel<?> parent) {
		boolean slim = slim(entity);
		Tex legs = worn(entity, EquipmentSlot.LEGS);
		Tex feet = worn(entity, EquipmentSlot.FEET);
		if (legs != null) {
			draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, parent, slim, legs.legs(), false, false, true);
		}
		if (feet != null) {
			draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, parent, slim, feet.boots(), false, false, true);
		}
	}

	/** First person: the worn chestplate's sleeve over the bare arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, boolean right) {
		Tex chest = worn(player, EquipmentSlot.CHEST);
		if (chest == null) {
			return;
		}
		// v0.17.3: not on a torn-off shoulder pad's arm, nor on an arm Andy hasn't got
		int lost = com.projecthero.mod.andy.AndyArmour.wornLostPads(player);
		if ((lost & (right ? com.projecthero.mod.andy.AndyArmour.PAD_RIGHT : com.projecthero.mod.andy.AndyArmour.PAD_LEFT)) != 0) {
			return;
		}
		com.projecthero.mod.andy.data.AndyState s = com.projecthero.mod.andy.Andy.peek(player);
		if (s != null && s.hasPower && s.broken(right ? com.projecthero.mod.andy.data.AndyState.RIGHT_ARM : com.projecthero.mod.andy.data.AndyState.LEFT_ARM)) {
			return;
		}
		boolean slim = slim(player);
		ModelPart a = right ? base(slim).rightArm : base(slim).leftArm;
		ModelPart sleeve = right ? over(slim).rightSleeve : over(slim).leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(chest.id())), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(chest.id())), light, OverlayTexture.NO_OVERLAY);
	}
}

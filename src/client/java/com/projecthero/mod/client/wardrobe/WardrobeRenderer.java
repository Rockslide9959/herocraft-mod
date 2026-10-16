package com.projecthero.mod.client.wardrobe;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.wardrobe.MannequinEntity;
import com.projecthero.mod.wardrobe.WardrobeBlock;
import com.projecthero.mod.wardrobe.WardrobeBlockEntity;
import com.projecthero.mod.wardrobe.WardrobeMod;
import com.projecthero.mod.wardrobe.WardrobePart;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5: what the Wardrobe shows. The block model is the wooden case (drawers, crown, the brass rail brackets); this
 * draws the suits:
 * <ul>
 * <li>the selected suit on a full mannequin figure on the middle display plinth;</li>
 * <li>the next {@link #HUNG} suits each way on a brass "waterfall" arm in the left / right column -- each hung on a
 * wooden hanger, flattened, the nearest one in front and the farther ones stepping back, up and outward so every
 * helmet peeks over the one in front. An empty slot shows its empty hanger. The suits wrap round, a carousel.</li>
 * <li>a small brass "n / 10" plate on the middle crown.</li>
 * </ul>
 * The figures are reusable client-only {@link MannequinEntity}s (the same trick as the Suit Platform), so every
 * armour -- vanilla, GeckoLib or the skin-tight Spider-Man suits -- looks exactly as it does when worn.
 */
public class WardrobeRenderer implements BlockEntityRenderer<WardrobeBlockEntity> {
	/** Suits shown each side of the selected one. */
	public static final int HUNG = 4;
	private static final float HUNG_SCALE = 0.42f;
	/** Hung suits turn this far toward the middle. */
	private static final float HUNG_TURN = 12.0f;
	/** How flat a hung suit is (front to back). */
	private static final float FLATTEN = 0.42f;
	private static final ResourceLocation BRASS = ResourceLocation.withDefaultNamespace("textures/block/gold_block.png");
	private static final ResourceLocation HANGER = ResourceLocation.withDefaultNamespace("textures/block/stripped_birch_log.png");
	private static final Map<WardrobeBlockEntity, MannequinEntity[]> FIGURES = new WeakHashMap<>();

	private final Font font;

	public WardrobeRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	@Override
	public boolean shouldRenderOffScreen(WardrobeBlockEntity wardrobe) {
		return true; // it spans six blocks, the block entity sits in only one
	}

	@Override
	public void render(WardrobeBlockEntity wardrobe, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		Level level = wardrobe.getLevel();
		BlockState state = wardrobe.getBlockState();
		if (level == null || !state.is(WardrobeMod.WARDROBE)) {
			return;
		}
		Direction facing = state.getValue(WardrobeBlock.FACING);
		MannequinEntity[] figures = FIGURES.computeIfAbsent(wardrobe, k -> makeFigures(level));
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		BlockPos origin = wardrobe.getBlockPos();
		int sel = wardrobe.selected();

		pose.pushPose();
		// local frame: origin at the middle's bottom centre, +Z out of the front, +X the viewer's right
		pose.translate(0.5, 0.0, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
		dispatcher.setRenderShadow(false);
		try {
			// the selected suit on the middle figure
			MannequinEntity middle = figures[0];
			dress(middle, wardrobe, sel);
			pose.pushPose();
			pose.translate(0.0, WardrobeBlockEntity.FIGURE_Y, 0.0);
			pose.scale(WardrobeBlockEntity.FIGURE_SCALE, WardrobeBlockEntity.FIGURE_SCALE, WardrobeBlockEntity.FIGURE_SCALE);
			dispatcher.render(middle, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers,
					LevelRenderer.getLightColor(level, origin.above()));
			pose.popPose();

			// the rest on the side rails: left = the suits before the selected one, right = the ones after
			for (int side = -1; side <= 1; side += 2) {
				WardrobePart part = side < 0 ? WardrobePart.LEFT : WardrobePart.RIGHT;
				int columnLight = LevelRenderer.getLightColor(level, WardrobeBlock.partPos(origin, facing, part, DoubleBlockHalf.UPPER));
				renderColumn(wardrobe, figures, side, sel, pose, buffers, columnLight, dispatcher, partialTick);
			}
		} finally {
			dispatcher.setRenderShadow(true);
		}
		renderPlate(sel, pose, buffers, LevelRenderer.getLightColor(level, origin.above().relative(facing)));
		pose.popPose();
	}

	/** One side column, back to front so the nearer suits draw over the farther ones' edges. */
	private void renderColumn(WardrobeBlockEntity wardrobe, MannequinEntity[] figures, int side, int sel, PoseStack pose,
			MultiBufferSource buffers, int light, EntityRenderDispatcher dispatcher, float partialTick) {
		VertexConsumer brass = buffers.getBuffer(RenderType.entityCutoutNoCull(BRASS));
		Vec3 front = hook(side, 0);
		Vec3 back = hook(side, HUNG - 1);
		Vec3 step = front.subtract(back).scale(1.0 / (HUNG - 1));
		// the waterfall arm: out of the back panel, through every hook, a little past the front one
		double toPanel = (back.z - (-0.375)) / -step.z;
		rod(pose, brass, back.subtract(step.scale(toPanel)), front.add(step.scale(0.35)), 0.035f, light);
		for (int k = HUNG - 1; k >= 0; k--) {
			int suit = Math.floorMod(sel + side * (k + 1), WardrobeBlockEntity.SUITS);
			Vec3 h = hook(side, k);
			pose.pushPose();
			// from here on the hook is the origin, the hanger and its suit turned a little toward the middle
			pose.translate(h.x, h.y, h.z);
			pose.mulPose(Axis.YP.rotationDegrees(-side * HUNG_TURN));
			hanger(pose, buffers, light);
			if (!wardrobe.suitEmpty(suit)) {
				MannequinEntity figure = figures[1 + (side < 0 ? 0 : HUNG) + k];
				dress(figure, wardrobe, suit);
				// shoulders (24 px below the top of a 32 px figure) on the hanger bar
				pose.translate(0.0, -HOOK - 1.5 * HUNG_SCALE + 0.02, 0.0);
				pose.scale(HUNG_SCALE, HUNG_SCALE, HUNG_SCALE * FLATTEN);
				dispatcher.render(figure, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, light);
			}
			pose.popPose();
		}
	}

	/** Height of a hanger, from its shoulder bar up to the arm. */
	private static final double HOOK = 0.075;

	/**
	 * Where hanger {@code k} (0 = the front one, next to the middle) hooks onto its arm, in the local frame. Each one
	 * farther back steps 0.16 back, 0.085 up and 0.128 outward -- across the whole column -- so every suit behind shows
	 * beside and above the one in front. The front suit's feet clear the drawer shelf, the back one's helmet the header.
	 */
	static Vec3 hook(int side, int k) {
		return new Vec3(side * (0.78 + 0.128 * k), 1.20 + HOOK + 0.085 * k, 0.24 - 0.16 * k);
	}

	/** A wooden coat hanger hooked on at the origin: a shoulder bar, two slopes up to the neck and a brass hook. */
	private static void hanger(PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer wood = buffers.getBuffer(RenderType.entityCutoutNoCull(HANGER));
		double half = 0.17;
		Vec3 l = new Vec3(-half, -HOOK, 0.0);
		Vec3 r = new Vec3(half, -HOOK, 0.0);
		Vec3 neck = new Vec3(0.0, -HOOK + 0.045, 0.0);
		rod(pose, wood, l, r, 0.03f, light);
		rod(pose, wood, l, neck, 0.024f, light);
		rod(pose, wood, r, neck, 0.024f, light);
		VertexConsumer brass = buffers.getBuffer(RenderType.entityCutoutNoCull(BRASS));
		rod(pose, brass, neck, new Vec3(0.0, 0.02, 0.0), 0.014f, light);
	}

	/** The "3 / 10" brass plate on the middle crown's pediment. */
	private void renderPlate(int sel, PoseStack pose, MultiBufferSource buffers, int light) {
		String text = (sel + 1) + " / " + WardrobeBlockEntity.SUITS;
		pose.pushPose();
		pose.translate(0.0, 1.0 + 19.5 / 16.0, 0.5 + 0.5 / 16.0 + 0.004); // the plate: 18.5-20.5 px up the top block, 0.5 px proud
		pose.scale(1.0f / 110.0f, -1.0f / 110.0f, 1.0f / 110.0f);
		font.drawInBatch(text, -font.width(text) / 2.0f, -font.lineHeight / 2.0f, 0xFF3A2410, false, pose.last().pose(), buffers,
				Font.DisplayMode.POLYGON_OFFSET, 0, light);
		pose.popPose();
	}

	private static void dress(MannequinEntity figure, WardrobeBlockEntity wardrobe, int suit) {
		for (EquipmentSlot slot : WardrobeBlockEntity.SLOTS) {
			ItemStack want = wardrobe.piece(suit, slot);
			if (!ItemStack.matches(figure.getItemBySlot(slot), want)) {
				figure.setItemSlot(slot, want.copy());
			}
		}
	}

	private static MannequinEntity[] makeFigures(Level level) {
		MannequinEntity[] figures = new MannequinEntity[1 + 2 * HUNG];
		for (int i = 0; i < figures.length; i++) {
			MannequinEntity m = new MannequinEntity(WardrobeMod.MANNEQUIN, level);
			m.setNoBasePlate(true);
			m.setInvisible(i > 0); // hung suits: only the armour shows
			m.setNoGravity(true);
			m.setYRot(0.0f);
			m.yBodyRot = 0.0f;
			m.yBodyRotO = 0.0f;
			m.yHeadRot = 0.0f;
			m.yHeadRotO = 0.0f;
			if (i > 0) {
				// arms hanging straight down, as on a hanger
				m.setLeftArmPose(new net.minecraft.core.Rotations(0.0f, 0.0f, -4.0f));
				m.setRightArmPose(new net.minecraft.core.Rotations(0.0f, 0.0f, 4.0f));
			}
			figures[i] = m;
		}
		return figures;
	}

	/** A square rod from {@code a} to {@code b} (local frame), {@code thick} wide, textured with the bound texture. */
	static void rod(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float thick, int light) {
		Vector3f dir = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z));
		float len = dir.length();
		if (len < 1.0e-4f) {
			return;
		}
		dir.div(len);
		Vector3f up = Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f u = new Vector3f(dir).cross(up).normalize().mul(thick / 2.0f);
		Vector3f v = new Vector3f(dir).cross(u).normalize().mul(thick / 2.0f);
		Vector3f[] corners = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			Vector3f base = new Vector3f((float) ((i & 4) == 0 ? a.x : b.x), (float) ((i & 4) == 0 ? a.y : b.y),
					(float) ((i & 4) == 0 ? a.z : b.z));
			base.add(new Vector3f(u).mul((i & 1) == 0 ? -1 : 1)).add(new Vector3f(v).mul((i & 2) == 0 ? -1 : 1));
			corners[i] = base;
		}
		PoseStack.Pose last = pose.last();
		float uLen = Math.min(1.0f, len);
		// four long sides and two caps: corner bits are (u sign, v sign, end)
		quad(vc, last, corners[0], corners[4], corners[5], corners[1], uLen, light); // -v
		quad(vc, last, corners[3], corners[7], corners[6], corners[2], uLen, light); // +v
		quad(vc, last, corners[1], corners[5], corners[7], corners[3], uLen, light); // +u
		quad(vc, last, corners[2], corners[6], corners[4], corners[0], uLen, light); // -u
		quad(vc, last, corners[0], corners[1], corners[3], corners[2], 0.1f, light); // a cap
		quad(vc, last, corners[6], corners[7], corners[5], corners[4], 0.1f, light); // b cap
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float uLen, int light) {
		Vector3f n = new Vector3f(p1).sub(p0).cross(new Vector3f(p3).sub(p0)).normalize();
		vertex(vc, pose, p0, 0.0f, 0.0f, n, light);
		vertex(vc, pose, p1, uLen, 0.0f, n, light);
		vertex(vc, pose, p2, uLen, 0.1f, n, light);
		vertex(vc, pose, p3, 0.0f, 0.1f, n, light);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vector3f p, float u, float v, Vector3f n, int light) {
		vc.addVertex(pose, p.x, p.y, p.z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, n.x, n.y, n.z);
	}
}

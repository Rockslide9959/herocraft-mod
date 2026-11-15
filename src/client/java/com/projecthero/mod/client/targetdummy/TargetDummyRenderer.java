package com.projecthero.mod.client.targetdummy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.targetdummy.DummyDamageKind;
import com.projecthero.mod.targetdummy.TargetDummyEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: draws the {@link TargetDummyEntity} -- the straw dummy (wobbling on its planted base plate after each hit,
 * harder for bigger hits), its armour, the floating damage numbers and the stats readout above its head.
 *
 * <ul>
 * <li><b>Numbers:</b> one per hit, outlined, coloured by {@link DummyDamageKind}; crits gold, bold and bigger with a
 * "!"; damage-over-time ticks smaller. They rise and fade over {@link DummyClientStats#POPUP_LIFE} ticks, nudged toward
 * the camera so the dummy's arms never hide them.</li>
 * <li><b>Readout:</b> while in combat, for {@link DummyClientStats#SUMMARY_TICKS} after (the summary), or while looked
 * at: combat time, DPS, total, hits, biggest hit, the last hit and where it came from, raw vs after-armour when armour
 * or protection made a difference, and the hit-cooldown mode when it is on. A compact panel at nametag scale, every line
 * kept short (labels are clipped), so it never sprawls.</li>
 * </ul>
 */
public class TargetDummyRenderer extends LivingEntityRenderer<TargetDummyEntity, TargetDummyModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectHeroMod.id("target_dummy"), "main");
	public static final ModelLayerLocation BASE_LAYER = new ModelLayerLocation(ProjectHeroMod.id("target_dummy"), "base");
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/target_dummy.png");
	/** The dummy pivots at the top of its base plate. */
	private static final float PIVOT = 2.0f / 16.0f;
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Widest the hit label may be (font pixels) before it is clipped. */
	private static final int LABEL_MAX = 96;
	private static final float TEXT_SCALE = 0.016f;

	private final ModelPart base;

	public TargetDummyRenderer(EntityRendererProvider.Context context) {
		super(context, new TargetDummyModel(context.bakeLayer(LAYER)), 0.45f);
		this.base = context.bakeLayer(BASE_LAYER).getChild("base");
		addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
		addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
	}

	@Override
	public ResourceLocation getTextureLocation(TargetDummyEntity dummy) {
		return TEXTURE;
	}

	@Override
	public void render(TargetDummyEntity dummy, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		// the planted base plate (no wobble)
		if (!dummy.isInvisible()) {
			pose.pushPose();
			float bodyYaw = Mth.rotLerp(partialTick, dummy.yBodyRotO, dummy.yBodyRot);
			pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
			pose.scale(-1.0f, -1.0f, 1.0f);
			pose.translate(0.0f, -1.501f, 0.0f);
			base.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		// no red hurt flash (the wobble is the feedback, and a fast power would keep it red the whole time)
		int hurtTime = dummy.hurtTime;
		dummy.hurtTime = 0;
		super.render(dummy, yaw, partialTick, pose, buffers, light);
		dummy.hurtTime = hurtTime;
		if (dummy.clientData instanceof DummyClientStats stats) {
			Vec3 cam = entityRenderDispatcher.camera.getPosition();
			if (dummy.position().distanceToSqr(cam) < 40.0 * 40.0) {
				renderNumbers(dummy, stats, partialTick, pose, buffers, cam);
				renderReadout(dummy, stats, partialTick, pose, buffers);
			}
		} else if (lookedAt(dummy)) {
			renderReadout(dummy, DummyClientStats.of(dummy), partialTick, pose, buffers);
		}
	}

	@Override
	protected void setupRotations(TargetDummyEntity dummy, PoseStack pose, float bob, float bodyYaw, float partialTick, float scale) {
		if (dummy.clientData instanceof DummyClientStats stats) {
			float lean = stats.wobble(dummy, partialTick);
			if (lean != 0.0f) {
				double dir = Math.toRadians(stats.wobbleYaw);
				float dx = (float) Math.cos(dir);
				float dz = (float) Math.sin(dir);
				// axis (dz, 0, -dx) tips the top toward the hit's direction of travel, then back (damped sine)
				pose.translate(0.0f, PIVOT, 0.0f);
				pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(lean), dz, 0.0f, -dx));
				pose.translate(0.0f, -PIVOT, 0.0f);
			}
		}
		pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
	}

	/** Only a custom name, and only when it is set visible or looked at (like a named mob); never "Target Dummy". */
	@Override
	protected boolean shouldShowName(TargetDummyEntity dummy) {
		return dummy.hasCustomName() && (dummy.isCustomNameVisible() || lookedAt(dummy)) && super.shouldShowName(dummy);
	}

	private static boolean lookedAt(TargetDummyEntity dummy) {
		return Minecraft.getInstance().crosshairPickEntity == dummy;
	}

	// ---------------------------------------------------------------- floating numbers

	private void renderNumbers(TargetDummyEntity dummy, DummyClientStats stats, float partialTick, PoseStack pose, MultiBufferSource buffers,
			Vec3 cam) {
		if (stats.popups.isEmpty()) {
			return;
		}
		Font font = getFont();
		Vec3 toCam = cam.subtract(dummy.position());
		Vec3 flat = new Vec3(toCam.x, 0.0, toCam.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 right = new Vec3(flat.z, 0.0, -flat.x); // sideways as the camera sees it
		flat = flat.scale(0.6);
		for (Iterator<DummyClientStats.Popup> it = stats.popups.iterator(); it.hasNext();) {
			DummyClientStats.Popup p = it.next();
			float age = dummy.tickCount - p.born + partialTick;
			if (age > DummyClientStats.POPUP_LIFE || age < -1.0f) {
				it.remove();
				continue;
			}
			float t = Math.max(0.0f, age) / DummyClientStats.POPUP_LIFE;
			float rise = (1.0f - (1.0f - t) * (1.0f - t)) * 0.55f; // ease-out
			float alpha = t < 0.55f ? 1.0f : 1.0f - (t - 0.55f) / 0.45f;
			// crits pop in big and settle
			float size = p.crit ? 1.1f + 0.45f * Math.max(0.0f, 1.0f - age / 4.0f) : p.dot ? 0.6f : 0.8f;
			int rgb = p.crit ? DummyDamageKind.CRIT_RGB : p.kind.rgb();
			String text = fmt(p.value) + (p.crit ? "!" : "");
			MutableComponent comp = Component.literal(text);
			if (p.crit) {
				comp.withStyle(ChatFormatting.BOLD);
			}
			FormattedCharSequence seq = comp.getVisualOrderText();
			int a = Mth.clamp((int) (alpha * 255.0f), 8, 255);
			pose.pushPose();
			pose.translate(right.x * p.dx + flat.x, p.dy + rise, right.z * p.dx + flat.z);
			pose.mulPose(entityRenderDispatcher.cameraOrientation());
			float s = 0.025f * size;
			pose.scale(s, -s, s);
			float x = -font.width(seq) / 2.0f;
			int outline = (a << 24) | darken(rgb);
			font.drawInBatch8xOutline(seq, x, 0.0f, (a << 24) | rgb, outline, pose.last().pose(), buffers, FULL_BRIGHT);
			pose.popPose();
		}
	}

	private static int darken(int rgb) {
		int r = (rgb >> 16 & 0xFF) / 5;
		int g = (rgb >> 8 & 0xFF) / 5;
		int b = (rgb & 0xFF) / 5;
		return r << 16 | g << 8 | b;
	}

	// ---------------------------------------------------------------- readout

	private void renderReadout(TargetDummyEntity dummy, DummyClientStats stats, float partialTick, PoseStack pose, MultiBufferSource buffers) {
		boolean looked = lookedAt(dummy);
		boolean combat = stats.inCombat(dummy);
		boolean summary = stats.inSummary(dummy);
		if (!combat && !summary && !looked) {
			return;
		}
		Font font = getFont();
		List<Component> lines = new ArrayList<>();
		float alpha = 1.0f;
		if (stats.hits == 0) {
			lines.add(Component.translatable("projecthero.target_dummy.hud.idle").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("projecthero.target_dummy.hud.idle_hint").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			String secs = fmt1(stats.combatSeconds(dummy, partialTick));
			if (combat) {
				lines.add(Component.translatable("projecthero.target_dummy.hud.combat", secs).withStyle(ChatFormatting.YELLOW));
			} else {
				lines.add(Component.translatable("projecthero.target_dummy.hud.summary", secs).withStyle(ChatFormatting.AQUA));
				if (summary && !looked) {
					int since = dummy.tickCount - stats.lastHit - TargetDummyEntity.COMBAT_TIMEOUT;
					int left = DummyClientStats.SUMMARY_TICKS - since;
					alpha = Mth.clamp((left - partialTick) / 20.0f, 0.1f, 1.0f); // fades over its last second
				}
			}
			lines.add(Component.translatable("projecthero.target_dummy.hud.dps_total", value(fmt(stats.dps(dummy, partialTick)), 0xFFE070),
					value(fmt(stats.total), 0xFFFFFF)).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("projecthero.target_dummy.hud.hits_biggest", value(Integer.toString(stats.hits), 0xFFFFFF),
					value(fmt(stats.biggest), 0xFFFFFF)).withStyle(ChatFormatting.GRAY));
			MutableComponent last = value(fmt(stats.lastDealt) + (stats.lastCrit ? "!" : ""), stats.lastCrit ? DummyDamageKind.CRIT_RGB : stats.lastKind.rgb());
			String label = font.plainSubstrByWidth(stats.lastLabel.getString(), LABEL_MAX);
			if (label.length() < stats.lastLabel.getString().length()) {
				label = font.plainSubstrByWidth(label, LABEL_MAX - font.width("...")) + "...";
			}
			lines.add(Component.translatable("projecthero.target_dummy.hud.last", last, Component.literal(label).withStyle(ChatFormatting.WHITE))
					.withStyle(ChatFormatting.GRAY));
			if (stats.totalRaw - stats.total > 0.05f) {
				lines.add(Component.translatable("projecthero.target_dummy.hud.last_raw", value(fmt(stats.lastRaw), 0xFFFFFF), value(fmt(stats.lastDealt), 0x9FE0FF))
						.withStyle(ChatFormatting.GRAY));
				int pct = Math.round(100.0f * (stats.totalRaw - stats.total) / Math.max(0.001f, stats.totalRaw));
				lines.add(Component.translatable("projecthero.target_dummy.hud.total_raw", value(fmt(stats.totalRaw), 0xFFFFFF), value(pct + "%", 0x9FE0FF))
						.withStyle(ChatFormatting.GRAY));
			}
		}
		if (dummy.hitCooldown()) {
			lines.add(Component.translatable("projecthero.target_dummy.hud.cooldown").withStyle(ChatFormatting.DARK_GRAY));
		}

		float lift = dummy.getBbHeight() + 0.2f + (shouldShowName(dummy) ? 0.3f : 0.0f);
		pose.pushPose();
		pose.translate(0.0f, lift, 0.0f);
		pose.mulPose(entityRenderDispatcher.cameraOrientation());
		pose.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
		Matrix4f m = pose.last().pose();
		int width = 0;
		for (Component c : lines) {
			width = Math.max(width, font.width(c));
		}
		int n = lines.size();
		float top = -n * 10.0f;
		// one even panel behind every line
		int bgA = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.4f) * 255.0f * alpha);
		VertexConsumer bg = buffers.getBuffer(RenderType.textBackgroundSeeThrough()); // like a nametag: no depth write, the text sits on it
		float x0 = -width / 2.0f - 3.0f;
		float x1 = width / 2.0f + 3.0f;
		float y0 = top - 2.0f;
		float y1 = 1.0f;
		int col = bgA << 24;
		bg.addVertex(m, x0, y0, 0.01f).setColor(col).setLight(FULL_BRIGHT);
		bg.addVertex(m, x0, y1, 0.01f).setColor(col).setLight(FULL_BRIGHT);
		bg.addVertex(m, x1, y1, 0.01f).setColor(col).setLight(FULL_BRIGHT);
		bg.addVertex(m, x1, y0, 0.01f).setColor(col).setLight(FULL_BRIGHT);
		int textA = Mth.clamp((int) (alpha * 255.0f), 8, 255);
		for (int i = 0; i < n; i++) {
			Component c = lines.get(i);
			float x = -font.width(c) / 2.0f;
			float y = top + i * 10.0f;
			// a faint pass that shows through walls (like a nametag), then the real one
			font.drawInBatch(c, x, y, (Math.max(8, textA / 5) << 24) | 0xFFFFFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
			font.drawInBatch(c, x, y, (textA << 24) | 0xFFFFFF, false, m, buffers, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
		}
		pose.popPose();
	}

	private static MutableComponent value(String text, int rgb) {
		return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
	}

	/** 7.5 / 12.5 / 140 -- one decimal under 100. */
	static String fmt(float v) {
		if (v >= 100.0f) {
			return String.format(Locale.ROOT, "%.0f", v);
		}
		return String.format(Locale.ROOT, "%.1f", v);
	}

	private static String fmt1(float v) {
		return String.format(Locale.ROOT, "%.1f", v);
	}
}

package com.projecthero.mod.client.ultron.contagion;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.client.supersoldier.AdamantiumShieldRenderer;
import com.projecthero.mod.ultron.contagion.Assimilation;
import com.projecthero.mod.ultron.contagion.ContagionNet;
import com.projecthero.mod.ultron.contagion.block.ContagionBlocks;
import com.projecthero.mod.ultron.contagion.entity.ContagionEntityTypes;
import com.projecthero.mod.ultron.contagion.entity.UltronOmegaEntity;
import com.projecthero.mod.ultron.contagion.item.ContagionItems;
import com.projecthero.mod.ultron.contagion.item.FirewallChip;
import com.projecthero.mod.ultron.contagion.item.VibraniumPlatingII;
import com.projecthero.mod.ultron.item.UltronItems;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Ultron Contagion's client side -- every renderer (Nexus, Lift Engine, Uplink Antenna, Vibranium Core, Ultron
 * Omega, the Iron Legion, the marker beam, the Purge Charge), the Firewall Beacon dome and Omega Core eyes, the Assimilated
 * / Ultron Golem layers on vanilla mobs, the HUD, coloured beams, the Mind Stone hold report, the Omega gold outline hook,
 * the Vibranium Shield and the tooltips for components the items themselves don't own.
 */
public final class ContagionClient {
	private record Beam(Vec3 a, Vec3 b, int color, float width, int life, long born) {
	}

	private static final List<Beam> BEAMS = new ArrayList<>();
	private static boolean sentHolding;

	private ContagionClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(ContagionEntityTypes.ULTRON_NEXUS, ctx -> new ContagionGeoRenderer<>(ctx, "ultron_nexus", 1.6f));
		EntityRendererRegistry.register(ContagionEntityTypes.LIFT_ENGINE, ctx -> new ContagionGeoRenderer<>(ctx, "lift_engine", 1.2f));
		EntityRendererRegistry.register(ContagionEntityTypes.UPLINK_ANTENNA, ctx -> new ContagionGeoRenderer<>(ctx, "uplink_antenna", 0.8f));
		EntityRendererRegistry.register(ContagionEntityTypes.VIBRANIUM_CORE, VibraniumCoreRenderer::new);
		EntityRendererRegistry.register(ContagionEntityTypes.ULTRON_OMEGA, OmegaRenderer::new);
		EntityRendererRegistry.register(ContagionEntityTypes.HIJACKED_MARK, HijackedMarkRenderer::new);
		EntityRendererRegistry.register(ContagionEntityTypes.CITADEL_MARKER, CitadelMarkerRenderer::new);
		EntityRendererRegistry.register(ContagionEntityTypes.PURGE_CHARGE, ThrownItemRenderer::new);
		BlockEntityRendererRegistry.register(ContagionBlocks.FIREWALL_BEACON_BE, FirewallBeaconRenderer::new);
		BlockEntityRendererRegistry.register(ContagionBlocks.OMEGA_CORE_BE, OmegaCoreRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(ContagionBlocks.CORRUPTED_LEAVES, RenderType.cutoutMipped());
		BlockRenderLayerMap.INSTANCE.putBlock(ContagionBlocks.FIREWALL_BEACON, RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ContagionBlocks.OMEGA_CORE, RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ContagionBlocks.CITADEL_CABLE, RenderType.cutout());

		// the Vibranium Shield: the Adamantium disc with its own art, and the vanilla "blocking" predicate
		BuiltinItemRendererRegistry.INSTANCE.register(ContagionItems.VIBRANIUM_SHIELD, new AdamantiumShieldRenderer(AdamantiumShieldRenderer.VIBRANIUM_TEXTURE));
		FabricModelPredicateProviderRegistry.register(ContagionItems.VIBRANIUM_SHIELD, ResourceLocation.withDefaultNamespace("blocking"),
				(stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0f : 0.0f);

		// Assimilated mobs and Ultron Golems: an overlay of plating and red eyes on their own model
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			String tex = overlayFor(type);
			if (tex != null) {
				helper.register(new AssimilatedLayer<>(renderer, tex, Assimilation::isAssimilated));
			} else if (type == EntityType.IRON_GOLEM) {
				helper.register(new AssimilatedLayer<>(renderer, "ultron_golem", Assimilation::isUltronGolem));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ContagionNet.Hud.TYPE, (payload, ctx) -> ctx.client().execute(() -> ContagionHud.accept(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ContagionNet.Beam.TYPE, (payload, ctx) -> ctx.client().execute(() -> addBeam(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			ContagionHud.clear();
			BEAMS.clear();
			sentHolding = false;
		});
		HudRenderCallback.EVENT.register(ContagionHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(ContagionClient::renderBeams);
		WorldRenderEvents.AFTER_ENTITIES.register(ContagionClient::renderMarker);
		ClientTickEvents.END_CLIENT_TICK.register(ContagionClient::tickHold);

		// the Legion tell: a viewer holding the Mind Stone sees the real Omega outlined gold (their own view only)
		UltronOmegaEntity.clientGoldOutline = omega -> {
			LocalPlayer viewer = Minecraft.getInstance().player;
			return viewer != null && UltronItems.MIND_STONE != null && omega.distanceToSqr(viewer) < 64 * 64
					&& (viewer.getOffhandItem().is(UltronItems.MIND_STONE) || viewer.getMainHandItem().is(UltronItems.MIND_STONE));
		};

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (UltronItems.MIND_STONE != null && stack.is(UltronItems.MIND_STONE) && ContagionItems.empowered(stack)) {
				lines.add(Component.translatable("item.projecthero.mind_stone.empowered").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				lines.add(Component.translatable("item.projecthero.mind_stone.empowered_beam").withStyle(ChatFormatting.YELLOW));
			}
			if (FirewallChip.hasChip(stack)) {
				lines.add(Component.translatable("item.projecthero.firewall_chip.installed").withStyle(ChatFormatting.AQUA));
			}
			if (VibraniumPlatingII.isPlated(stack)) {
				lines.add(Component.translatable("item.projecthero.vibranium_plated_2").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		});
	}

	/** The overlay texture under {@code textures/entity/ultron/} for a vanilla mob type that can be Assimilated, or null. */
	static String overlayFor(EntityType<? extends LivingEntity> type) {
		if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
			return "assimilated/zombie";
		}
		if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.WITHER_SKELETON || type == EntityType.BOGGED) {
			return "assimilated/skeleton";
		}
		if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) {
			return "assimilated/spider";
		}
		if (type == EntityType.CREEPER) {
			return "assimilated/creeper";
		}
		if (type == EntityType.PILLAGER) {
			return "assimilated/pillager";
		}
		return null;
	}

	/** Reports "holding right-click with a Mind Stone" when it could fire a beam (the Vision moment, or Sneak + an Empowered stone). */
	private static void tickHold(Minecraft mc) {
		LocalPlayer p = mc.player;
		boolean holding = false;
		if (p != null && mc.screen == null && UltronItems.MIND_STONE != null && mc.options.keyUse.isDown()) {
			ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
			boolean stone = main.is(UltronItems.MIND_STONE) || off.is(UltronItems.MIND_STONE);
			boolean empowered = main.is(UltronItems.MIND_STONE) && ContagionItems.empowered(main) || off.is(UltronItems.MIND_STONE) && ContagionItems.empowered(off);
			ContagionNet.Hud s = ContagionHud.state();
			holding = stone && (s != null && s.has(ContagionNet.F_VISION_PROMPT) || empowered && p.isShiftKeyDown());
		}
		if (holding != sentHolding && ClientPlayNetworking.canSend(ContagionNet.MindHold.TYPE)) {
			sentHolding = holding;
			ClientPlayNetworking.send(new ContagionNet.MindHold(holding));
		}
	}

	private static void addBeam(ContagionNet.Beam p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (BEAMS.size() > 96) {
			BEAMS.remove(0);
		}
		BEAMS.add(new Beam(p.from(), p.to(), p.color(), p.width(), Math.max(1, p.life()), mc.level.getGameTime()));
	}

	private static void renderBeams(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null || BEAMS.isEmpty()) {
			return;
		}
		float now = mc.level.getGameTime() + ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Iterator<Beam> it = BEAMS.iterator();
		while (it.hasNext()) {
			Beam b = it.next();
			if (now - b.born() > b.life() + 1) {
				it.remove();
			}
		}
		if (BEAMS.isEmpty()) {
			return;
		}
		MultiBufferSource buffers = ctx.consumers();
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		for (int pass = 0; pass < 2; pass++) {
			boolean glow = pass == 0;
			VertexConsumer vc = buffers.getBuffer(glow ? RenderType.debugQuads() : RenderType.lightning());
			for (Beam b : BEAMS) {
				float fade = Mth.clamp(1f - (now - b.born()) / (b.life() + 1f), 0f, 1f);
				float flicker = 0.85f + 0.15f * Mth.sin(now * 2.1f);
				BeamDraw.beam(vc, glow, last, b.a(), b.b(), cam, b.width(), glow ? b.color() : mixWhite(b.color()), fade * flicker);
			}
		}
		pose.popPose();
	}

	/**
	 * The Sokovia Protocol's red marker beam, from the HUD state: from below the ground up to the sky while the site is only
	 * marked (seen from far off), then only up to the underside of the sky-citadel once it is built -- never through it.
	 */
	private static void renderMarker(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		ContagionNet.Hud s = ContagionHud.state();
		if (mc.level == null || s == null || !s.has(ContagionNet.F_MARKER) || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		Vec3 cam = ctx.camera().getPosition();
		double x = s.markerX() + 0.5, z = s.markerZ() + 0.5;
		if ((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z) > 1024 * 1024) {
			return;
		}
		float now = mc.level.getGameTime() + ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float flick = 0.8f + 0.2f * Mth.sin(now * 0.7f);
		Vec3 a = new Vec3(x, mc.level.getMinBuildHeight(), z), b = new Vec3(x, s.markerTop(), z);
		PoseStack pose = ctx.matrixStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		BeamDraw.beam(ctx.consumers().getBuffer(RenderType.debugQuads()), true, pose.last(), a, b, cam, 0.45f, 0xFF1A10, flick * 0.8f);
		BeamDraw.beam(ctx.consumers().getBuffer(RenderType.lightning()), false, pose.last(), a, b, cam, 0.45f, 0xFFA090, flick);
		pose.popPose();
	}

	private static int mixWhite(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return ((r + 255) / 2 << 16) | ((g + 255) / 2 << 8) | (b + 255) / 2;
	}
}

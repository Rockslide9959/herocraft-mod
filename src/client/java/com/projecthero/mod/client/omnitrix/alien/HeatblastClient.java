package com.projecthero.mod.client.omnitrix.alien;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.ghostrider.GhostRiderRender;
import com.projecthero.mod.client.mutation.PyrokinesisClient;
import com.projecthero.mod.client.mutation.geo.GeoModels;
import com.projecthero.mod.client.mutation.pyro.PyroBoltRenderer;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixHud;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Heatblast's client kit -- the user's magma-rock skin with an animated burning overlay, Ghost Rider's fire-sprite
 * blaze for a head, embers, and a pose (third and first person) for every move. v0.17.2: every move draws with Pyrokinesis'
 * own code (its fireballs, Flamethrower cone, flame bodies and white-hot hands).
 *
 * <p>v0.17.2 revamp (user-approved kit): the Rock Ride -- the chunk of ground he surfs, drawn under his feet from the real
 * block (Geokinesis' block models), banking into turns and pitching with the climb, fire roaring out under it, flown by his
 * own client ({@link #ride}); the Fire Lasso's whip, the Fire Tornado's column, the Heat Shield's dome, the Meteor Crash's
 * cloak of fire and a pose for every new move.
 *
 * <p>v0.17.2 round 3: the gather in both hands before the volley, the Fire Breath from his mouth ({@link #breath}), the big ball over
 * his head, the Magma Slam, and the Supernova's 4 s charge -- flames that grow and turn jagged on his body ({@link #novaFlames},
 * {@link #jagged}), the fire drawn in from all round him ({@link #novaWorld}) and the blast ({@link #novaBurst}).
 *
 * <p>Doused (user: "change heatblasts skin when hes flame it put out, his fire parts should be a dark red and the flames on his
 * head should be out aswell"): the charred skin ({@code heatblast_doused.png}: the flame-yellow parts dark ember red, the rock
 * charcoal), no burning overlay and no head fire, grey smoke curling off his head; out of the water embers flicker over him
 * (and the overlay glows back faintly) as the countdown runs, then the flames burst back. The HUD shows "Reigniting Ns".
 */
public final class HeatblastClient implements AlienClientKit {
	private static final float A = QuirkKeyframes.AIM;

	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/heatblast.png");
	private static final ResourceLocation DOUSED_SKIN = ProjectHeroMod.id("textures/entity/omnitrix/heatblast_doused.png");
	private static final ResourceLocation[] GLOW = new ResourceLocation[32];

	static {
		for (int i = 0; i < GLOW.length; i++) {
			GLOW[i] = ProjectHeroMod.id(String.format("textures/entity/omnitrix/heatblast_glow_%02d.png", i));
		}
	}

	private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0f, 0.42f, 0.06f), 0.7f);
	private static final DustParticleOptions DIM_EMBER = new DustParticleOptions(new Vector3f(0.55f, 0.12f, 0.06f), 0.6f);

	// {tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX} (radians); A = along the look
	// R Fireball Volley -- v0.17.2 (user: "player gathers fire in their hands and throws 3 fireballs"): both hands in front of his belly while
	// the fire gathers, then each hand in turn thrown out down the aim, the other drawn back to the belly and the torso turning into it
	private static final float[][] VOLLEY_GATHER = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(4, -1.0f, -0.5f, 0.12f, -1.0f, 0.5f, -0.12f, 0.1f, 0f, 0.14f, 0f, 0.06f, -0.14f, 0f, -0.06f, 0.12f),
			QuirkKeyframes.f(9, -1.05f, -0.55f, 0.1f, -1.05f, 0.55f, -0.1f, 0.13f, 0f, 0.16f, 0f, 0.06f, -0.16f, 0f, -0.06f, 0.15f), QuirkKeyframes.rest(12) };
	private static final float[][] VOLLEY_R = {
			QuirkKeyframes.f(0, -1.05f, -0.55f, 0.1f, -1.05f, 0.55f, -0.1f, 0.13f, 0f, 0.16f, 0f, 0.06f, -0.16f, 0f, -0.06f, 0.15f),
			QuirkKeyframes.f(2.5f, A, A, 0f, -1.05f, 0.55f, -0.1f, 0.06f, -0.42f, 0.26f, 0f, 0f, -0.26f, 0f, 0f, 0f),
			QuirkKeyframes.f(5, A, A, 0f, -1.05f, 0.55f, -0.1f, 0.05f, -0.36f, 0.22f, 0f, 0f, -0.22f, 0f, 0f, 0f),
			QuirkKeyframes.f(9, -0.7f, 0f, 0.1f, -0.7f, 0.3f, -0.1f, 0f, -0.12f, 0.1f, 0f, 0f, -0.1f, 0f, 0f, 0f), QuirkKeyframes.rest(13) };
	private static final float[][] VOLLEY_L = {
			QuirkKeyframes.f(0, A, A, 0f, -1.05f, 0.55f, -0.1f, 0.05f, -0.36f, 0.22f, 0f, 0f, -0.22f, 0f, 0f, 0f),
			QuirkKeyframes.f(2.5f, -1.05f, -0.55f, 0.1f, A, A, 0f, 0.06f, 0.42f, -0.26f, 0f, 0f, 0.26f, 0f, 0f, 0f),
			QuirkKeyframes.f(5, -1.05f, -0.55f, 0.1f, A, A, 0f, 0.05f, 0.36f, -0.22f, 0f, 0f, 0.22f, 0f, 0f, 0f),
			QuirkKeyframes.f(9, -0.7f, -0.3f, 0.1f, -0.7f, 0f, -0.1f, 0f, 0.12f, -0.1f, 0f, 0f, 0.1f, 0f, 0f, 0f), QuirkKeyframes.rest(13) };
	private static final float[][] VOLLEY_3 = {
			QuirkKeyframes.f(0, -1.05f, -0.55f, 0.1f, A, A, 0f, 0.05f, 0.36f, -0.22f, 0f, 0f, 0.22f, 0f, 0f, 0f),
			QuirkKeyframes.f(2.5f, A, A, 0f, 0.3f, 0f, -0.2f, 0.12f, -0.5f, 0.3f, 0f, 0f, -0.3f, 0f, 0f, 0f),
			QuirkKeyframes.f(7, A, A, 0f, 0.25f, 0f, -0.18f, 0.1f, -0.42f, 0.26f, 0f, 0f, -0.26f, 0f, 0f, 0f),
			QuirkKeyframes.f(11, -0.7f, 0f, 0.1f, 0.1f, 0f, -0.1f, 0.03f, -0.12f, 0.1f, 0f, 0f, -0.1f, 0f, 0f, 0f), QuirkKeyframes.rest(15) };
	// Use's small bolt -- a quick flick from the hip
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1, -0.6f, 0.2f, 0.2f, 0f, 0f, 0f, 0f, 0.15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
			QuirkKeyframes.f(3, A, A, 0f, 0.1f, 0f, -0.1f, 0.05f, -0.15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
			QuirkKeyframes.f(5, A, A, 0f, 0.1f, 0f, -0.1f, 0.05f, -0.12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), QuirkKeyframes.rest(9) };
	// Shift+R Fire Breath -- v0.17.2 (user: "player breaths fire from their mouths"): a breath in, chest high and head back, then the chest
	// driven forward, fists clenched and drawn back past his hips, feet braced, the head on the aim; held for as long as the key is
	private static final float[][] BREATH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(HeatblastKit.BREATH_INHALE - 0.5f, 0.2f, 0f, 0.45f, 0.2f, 0f, -0.45f, -0.2f, 0f, 0.14f, 0f, 0.05f, -0.14f, 0f, -0.05f, -0.24f),
			QuirkKeyframes.f(HeatblastKit.BREATH_INHALE + 3, 0.62f, 0f, 0.38f, 0.62f, 0f, -0.38f, 0.22f, 0f, 0.36f, 0f, 0.08f, -0.4f, 0f, -0.08f, 0f),
			QuirkKeyframes.f(1000000f, 0.62f, 0f, 0.38f, 0.62f, 0f, -0.38f, 0.22f, 0f, 0.36f, 0f, 0.08f, -0.4f, 0f, -0.08f, 0f) };
	private static final float[][] BREATH_END = {
			QuirkKeyframes.f(0, 0.62f, 0f, 0.38f, 0.62f, 0f, -0.38f, 0.22f, 0f, 0.36f, 0f, 0.08f, -0.4f, 0f, -0.08f, 0f),
			QuirkKeyframes.f(4, 0.3f, 0f, 0.3f, 0.3f, 0f, -0.3f, 0.06f, 0f, 0.18f, 0f, 0.04f, -0.2f, 0f, -0.04f, 0.1f), QuirkKeyframes.rest(9) };
	// Shift+G Magma Slam -- v0.17.2 (user: "gathers fire in their hands and slams the ground in front of them"): fists brought together at
	// the chest, raised high over his head on his toes, then both driven down into the ground in front of him, deep in a crouch
	private static final float[][] SLAM_GATHER = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(4, -1.2f, -0.7f, 0.1f, -1.2f, 0.7f, -0.1f, 0.14f, 0f, 0.1f, 0f, 0.08f, -0.1f, 0f, -0.08f, 0.15f, 0.35f),
			QuirkKeyframes.fc(HeatblastKit.SLAM_WINDUP - 1, -2.95f, -0.25f, 0.05f, -2.95f, 0.25f, -0.05f, -0.3f, 0f, 0.12f, 0f, 0.12f, -0.12f, 0f, -0.12f, -0.35f, 0f),
			QuirkKeyframes.fc(HeatblastKit.SLAM_WINDUP + 3, -2.95f, -0.25f, 0.05f, -2.95f, 0.25f, -0.05f, -0.3f, 0f, 0.12f, 0f, 0.12f, -0.12f, 0f, -0.12f, -0.35f, 0f) };
	private static final float[][] SLAM = {
			QuirkKeyframes.fc(0, -2.95f, -0.25f, 0.05f, -2.95f, 0.25f, -0.05f, -0.3f, 0f, 0.12f, 0f, 0.12f, -0.12f, 0f, -0.12f, -0.35f, 0f),
			QuirkKeyframes.fc(2.5f, -0.45f, -0.2f, 0f, -0.45f, 0.2f, 0f, 0.85f, 0f, -0.9f, 0f, 0.14f, 0.5f, 0f, -0.14f, -0.5f, 1f),
			QuirkKeyframes.fc(10, -0.4f, -0.2f, 0f, -0.4f, 0.2f, 0f, 0.78f, 0f, -0.85f, 0f, 0.14f, 0.46f, 0f, -0.14f, -0.45f, 0.95f), QuirkKeyframes.restC(19) };
	// G Fire Lasso -- the whip wound up overhead, cracked out down the aim...
	private static final float[][] LASSO = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -2.9f, 0.2f, 0.45f, -0.3f, 0f, -0.2f, -0.12f, 0.35f, 0.15f, 0f, 0f, -0.15f, 0f, 0f, -0.1f),
			QuirkKeyframes.f(3.5f, A, A, 0f, 0.3f, 0f, -0.15f, 0.2f, -0.25f, 0.3f, 0f, 0f, -0.3f, 0f, 0f, 0.05f), QuirkKeyframes.rest(8) };
	// ...and the haul: leaning back, both hands dragging the rope in
	private static final float[][] YANK = { QuirkKeyframes.f(0, A, A, 0f, 0.3f, 0f, -0.15f, 0.2f, -0.25f, 0.3f, 0f, 0f, -0.3f, 0f, 0f, 0.05f),
			QuirkKeyframes.f(2, -1.3f, 0.2f, 0.15f, -1.3f, -0.2f, -0.15f, -0.25f, 0.1f, -0.35f, 0f, 0.05f, 0.35f, 0f, -0.05f, -0.1f),
			QuirkKeyframes.f(6, -0.5f, 0.1f, 0.3f, -0.5f, -0.1f, -0.3f, -0.3f, 0.15f, -0.45f, 0f, 0.05f, 0.4f, 0f, -0.05f, -0.15f), QuirkKeyframes.rest(12) };
	// G Magma Bomb -- v0.17.2 (user: "charges a big ball of fire above their head for 1 second and throws it"): both arms up under the ball
	// on braced legs, then the whole body whipped over and down, stepping into the throw
	private static final float[][] MAGMA_CHARGE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(4, -2.7f, -0.2f, 0.3f, -2.7f, 0.2f, -0.3f, -0.12f, 0f, 0.12f, 0f, 0.12f, -0.12f, 0f, -0.12f, -0.3f),
			QuirkKeyframes.f(HeatblastKit.MAGMA_CHARGE, -3.0f, -0.2f, 0.12f, -3.0f, 0.2f, -0.12f, -0.2f, 0f, 0.16f, 0f, 0.14f, -0.16f, 0f, -0.14f, -0.35f),
			QuirkKeyframes.f(HeatblastKit.MAGMA_CHARGE + 4, -3.0f, -0.2f, 0.12f, -3.0f, 0.2f, -0.12f, -0.2f, 0f, 0.16f, 0f, 0.14f, -0.16f, 0f, -0.14f, -0.35f) };
	private static final float[][] MAGMA = {
			QuirkKeyframes.f(0, -3.0f, -0.2f, 0.12f, -3.0f, 0.2f, -0.12f, -0.2f, 0f, 0.16f, 0f, 0.14f, -0.16f, 0f, -0.14f, -0.35f),
			QuirkKeyframes.f(3, -1.35f, -0.1f, 0.05f, -1.35f, 0.1f, -0.05f, 0.42f, 0f, 0.42f, 0f, 0.1f, -0.42f, 0f, -0.1f, 0.05f),
			QuirkKeyframes.f(7, -0.85f, -0.1f, 0f, -0.85f, 0.1f, 0f, 0.3f, 0f, 0.36f, 0f, 0.05f, -0.36f, 0f, -0.05f, 0f), QuirkKeyframes.rest(14) };
	// X Flame Jump -- knees bent and palms down, then sprung up off the jets, palms pushed down and back
	private static final float[][] JUMP = { QuirkKeyframes.rest(0),
			QuirkKeyframes.fc(2, 0.35f, 0f, 0.5f, 0.35f, 0f, -0.5f, 0.2f, 0f, -0.5f, 0f, 0.05f, -0.5f, 0f, -0.05f, 0f, 0.6f),
			QuirkKeyframes.fc(5, 0.6f, 0f, 0.35f, 0.6f, 0f, -0.35f, -0.1f, 0f, 0.3f, 0f, 0f, -0.25f, 0f, 0f, -0.2f, 0f),
			QuirkKeyframes.fc(12, 0.45f, 0f, 0.3f, 0.45f, 0f, -0.3f, -0.05f, 0f, 0.2f, 0f, 0f, -0.15f, 0f, 0f, -0.1f, 0f), QuirkKeyframes.restC(18) };
	// Hold X: the Rock Ride's charge -- crouched low, right fist raised high and burning, the left braced on the knee
	private static final float[][] RIDE_CHARGE = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(6, -2.8f, 0f, 0.25f, -0.6f, 0f, -0.4f, 0.35f, 0.2f, -0.6f, 0f, 0.15f, 0.3f, 0f, -0.15f, 0.3f, 0.7f),
			QuirkKeyframes.fc(HeatblastKit.RIDE_CHARGE_TICKS, -3.05f, 0f, 0.15f, -0.7f, 0f, -0.45f, 0.45f, 0.25f, -0.7f, 0f, 0.18f, 0.35f, 0f, -0.18f, 0.35f,
					0.85f),
			QuirkKeyframes.fc(HeatblastKit.RIDE_CHARGE_TICKS + 8, -3.05f, 0f, 0.15f, -0.7f, 0f, -0.45f, 0.45f, 0.25f, -0.7f, 0f, 0.18f, 0.35f, 0f, -0.18f, 0.35f,
					0.85f) };
	// ...the fist driven into the ground as the rock rips out
	private static final float[][] RIDE_PUNCH = {
			QuirkKeyframes.fc(0, -3.05f, 0f, 0.15f, -0.7f, 0f, -0.45f, 0.45f, 0.25f, -0.7f, 0f, 0.18f, 0.35f, 0f, -0.18f, 0.35f, 0.85f),
			QuirkKeyframes.fc(2, 0.25f, 0f, 0.1f, -0.5f, 0f, -0.5f, 0.75f, 0.1f, -0.8f, 0f, 0.1f, 0.45f, 0f, -0.1f, 0.5f, 1f),
			QuirkKeyframes.fc(6, 0.2f, 0f, 0.1f, -0.5f, 0f, -0.5f, 0.7f, 0.1f, -0.75f, 0f, 0.1f, 0.4f, 0f, -0.1f, 0.45f, 0.9f), QuirkKeyframes.restC(12) };
	// on the rock: the surfer's stance -- side-on, knees loose, arms out for balance (banking is added in pose())
	private static final float[][] SURF = { QuirkKeyframes.fc(0, -0.25f, 0f, 1.15f, -0.35f, 0f, -0.95f, 0.15f, 0.55f, -0.45f, 0.1f, 0.2f, 0.4f, -0.1f,
			-0.2f, 0.05f, 0.45f), QuirkKeyframes.fc(20, -0.25f, 0f, 1.15f, -0.35f, 0f, -0.95f, 0.15f, 0.55f, -0.45f, 0.1f, 0.2f, 0.4f, -0.1f, -0.2f, 0.05f, 0.45f) };
	// Shift+X Blaze Dash -- a coil, then low and fast, arms swept back (jets in the palms), legs driving
	private static final float[][] DASH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1, -0.4f, 0f, 0.2f, -0.4f, 0f, -0.2f, 0.35f, 0f, -0.4f, 0f, 0f, 0.3f, 0f, 0f, -0.2f),
			QuirkKeyframes.f(3, 1.1f, 0f, 0.25f, 1.1f, 0f, -0.25f, 0.55f, 0f, -0.6f, 0f, 0.05f, 0.6f, 0f, -0.05f, -0.45f),
			QuirkKeyframes.f(HeatblastKit.DASH_TICKS, 1.0f, 0f, 0.3f, 1.0f, 0f, -0.3f, 0.5f, 0f, 0.5f, 0f, 0.05f, -0.5f, 0f, -0.05f, -0.4f),
			QuirkKeyframes.rest(HeatblastKit.DASH_TICKS + 5) };
	// Z Fire Tornado -- both arms flung up, then both pointing at the spot, held while it spins up
	private static final float[][] TORNADO = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -2.7f, 0f, 0.6f, -2.7f, 0f, -0.6f, -0.2f, 0f, 0f, 0f, 0.12f, 0f, 0f, -0.12f, -0.35f),
			QuirkKeyframes.f(6, A, A, 0f, A, A, 0f, 0.12f, 0f, 0.3f, 0f, 0.1f, -0.3f, 0f, -0.1f, 0f),
			QuirkKeyframes.f(16, A, A, 0f, A, A, 0f, 0.1f, 0f, 0.3f, 0f, 0.1f, -0.3f, 0f, -0.1f, 0f), QuirkKeyframes.rest(22) };
	// Shift+Z Supernova's 4 s charge -- v0.17.2 (user: "flames grow around the players body ... players gathers flames ... make the animations
	// better please"): palms pressed down at his sides as the fire rises, arms swept wide to pull it in from all round him, sweeping in to
	// the chest as he sinks, then crushed down over the core (and trembling, in pose())
	private static final float[][] CHARGE = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(10, -0.2f, 0f, 0.85f, -0.2f, 0f, -0.85f, 0.05f, 0f, 0f, 0f, 0.16f, 0f, 0f, -0.16f, 0.12f, 0.15f),
			QuirkKeyframes.fc(32, -1.0f, 0.75f, 0.35f, -1.0f, -0.75f, -0.35f, -0.12f, 0f, 0f, 0f, 0.2f, 0f, 0f, -0.2f, -0.22f, 0.2f),
			QuirkKeyframes.fc(56, -1.2f, -0.75f, 0.1f, -1.2f, 0.75f, -0.1f, 0.25f, 0f, -0.1f, 0f, 0.2f, -0.1f, 0f, -0.2f, 0.2f, 0.45f),
			QuirkKeyframes.fc(HeatblastKit.SUPERNOVA_CHARGE - 8, -1.05f, -1.0f, 0f, -1.05f, 1.0f, 0f, 0.5f, 0f, -0.2f, 0f, 0.22f, -0.2f, 0f, -0.22f, 0.45f, 0.85f),
			QuirkKeyframes.fc(HeatblastKit.SUPERNOVA_CHARGE + 4, -1.05f, -1.0f, 0f, -1.05f, 1.0f, 0f, 0.5f, 0f, -0.2f, 0f, 0.22f, -0.2f, 0f, -0.22f, 0.45f, 0.85f) };
	// ...and the blast (and the Meteor Crash's impact): thrown open into a huge star
	private static final float[][] SUPERNOVA = {
			QuirkKeyframes.fc(0, -1.05f, -1.0f, 0f, -1.05f, 1.0f, 0f, 0.5f, 0f, -0.2f, 0f, 0.22f, -0.2f, 0f, -0.22f, 0.45f, 0.85f),
			QuirkKeyframes.fc(2, -0.6f, 0f, 2.3f, -0.6f, 0f, -2.3f, -0.45f, 0f, 0.15f, 0f, 0.5f, 0.15f, 0f, -0.5f, -0.7f, 0f),
			QuirkKeyframes.fc(14, -0.5f, 0f, 2.1f, -0.5f, 0f, -2.1f, -0.35f, 0f, 0.1f, 0f, 0.45f, 0.1f, 0f, -0.45f, -0.6f, 0f), QuirkKeyframes.restC(24) };
	// the flaming punch -- cocked back, driven out along the look with the hips
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1, 0.4f, 0.3f, 0.2f, -0.5f, 0f, -0.1f, 0f, 0.35f, 0.2f, 0f, 0f, -0.2f, 0f, 0f, 0f),
			QuirkKeyframes.f(3, A, A, 0f, 0.45f, 0f, -0.1f, 0.15f, -0.45f, 0.3f, 0f, 0f, -0.3f, 0f, 0f, 0f),
			QuirkKeyframes.f(5, A, A, 0f, 0.4f, 0f, -0.1f, 0.12f, -0.4f, 0.28f, 0f, 0f, -0.28f, 0f, 0f, 0f), QuirkKeyframes.rest(10) };
	private static final float[][] PUNCH_L = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1, -0.5f, 0f, 0.1f, 0.4f, -0.3f, -0.2f, 0f, -0.35f, -0.2f, 0f, 0f, 0.2f, 0f, 0f, 0f),
			QuirkKeyframes.f(3, 0.45f, 0f, 0.1f, A, A, 0f, 0.15f, 0.45f, -0.3f, 0f, 0f, 0.3f, 0f, 0f, 0f),
			QuirkKeyframes.f(5, 0.4f, 0f, 0.1f, A, A, 0f, 0.12f, 0.4f, -0.28f, 0f, 0f, 0.28f, 0f, 0f, 0f), QuirkKeyframes.rest(10) };
	// smelting -- hands cupped round the heat, kneading it
	private static final float[][] SMELT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.2f, -0.45f, 0f, -1.2f, 0.45f, 0f, 0.15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.35f),
			QuirkKeyframes.f(7, -1.05f, -0.52f, 0f, -1.05f, 0.52f, 0f, 0.18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f),
			QuirkKeyframes.f(12, -1.2f, -0.45f, 0f, -1.2f, 0.45f, 0f, 0.15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.35f), QuirkKeyframes.rest(16) };
	// V Heat Aura -- a short curl in, then the palms pushed out and down as the heat rolls off him
	private static final float[][] AURA = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.7f, 0.4f, 0f, -0.7f, -0.4f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.3f),
			QuirkKeyframes.f(5, -0.3f, 0f, 1.0f, -0.3f, 0f, -1.0f, -0.2f, 0f, 0f, 0f, 0.18f, 0f, 0f, -0.18f, -0.35f),
			QuirkKeyframes.f(14, -0.3f, 0f, 0.9f, -0.3f, 0f, -0.9f, -0.12f, 0f, 0f, 0f, 0.15f, 0f, 0f, -0.15f, -0.25f), QuirkKeyframes.rest(20) };
	// Shift+V Absorb Flames (v0.17.2, was the Heat Shield) -- arms crossed, hunched over, then flung wide with the head thrown back as the flames pour in
	private static final float[][] SHIELD = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -1.3f, -0.9f, 0.1f, -1.3f, 0.9f, -0.1f, 0.3f, 0f, 0.3f, 0f, 0.1f, -0.3f, 0f, -0.1f, 0.25f),
			QuirkKeyframes.f(6, -0.4f, 0f, 1.4f, -0.4f, 0f, -1.4f, -0.15f, 0f, 0f, 0f, 0.25f, 0f, 0f, -0.25f, -0.3f),
			QuirkKeyframes.f(10, -0.3f, 0f, 1.2f, -0.3f, 0f, -1.2f, -0.05f, 0f, 0f, 0f, 0.2f, 0f, 0f, -0.2f, -0.15f), QuirkKeyframes.rest(16) };
	// C Volcanic Eruption -- the right fist raised, then down into the ground on one knee
	private static final float[][] ERUPT = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(3, -2.8f, 0f, 0.3f, -0.5f, 0f, -0.3f, -0.2f, 0f, 0.1f, 0f, 0.1f, -0.1f, 0f, -0.1f, -0.3f, 0.2f),
			QuirkKeyframes.fc(5, 0.25f, 0f, 0.1f, -0.4f, 0f, -0.45f, 0.75f, 0f, -0.9f, 0f, 0.1f, 0.5f, 0f, -0.1f, -0.45f, 1f),
			QuirkKeyframes.fc(14, 0.2f, 0f, 0.1f, -0.4f, 0f, -0.45f, 0.7f, 0f, -0.85f, 0f, 0.1f, 0.45f, 0f, -0.1f, -0.4f, 0.9f), QuirkKeyframes.restC(20) };
	// Shift+C Meteor Crash -- the coil and spring, arms streamlined overhead on the way up...
	private static final float[][] METEOR_RISE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, 0.35f, 0f, 0.5f, 0.35f, 0f, -0.5f, 0.2f, 0f, -0.5f, 0f, 0.05f, -0.5f, 0f, -0.05f, 0f),
			QuirkKeyframes.f(5, -3.0f, 0f, 0.12f, -3.0f, 0f, -0.12f, -0.1f, 0f, 0.15f, 0f, 0.03f, 0.1f, 0f, -0.03f, -0.3f),
			QuirkKeyframes.f(40, -3.0f, 0f, 0.12f, -3.0f, 0f, -0.12f, -0.1f, 0f, 0.15f, 0f, 0.03f, 0.1f, 0f, -0.03f, -0.3f) };
	// ...and the dive: fists down in front, bent over them, legs trailing
	private static final float[][] METEOR_DIVE = { QuirkKeyframes.f(0, -3.0f, 0f, 0.12f, -3.0f, 0f, -0.12f, -0.1f, 0f, 0.15f, 0f, 0.03f, 0.1f, 0f, -0.03f, -0.3f),
			QuirkKeyframes.f(3, -0.6f, 0f, 0.2f, -0.6f, 0f, -0.2f, 0.65f, 0f, 0.35f, 0f, 0.05f, 0.25f, 0f, -0.05f, -0.6f),
			QuirkKeyframes.f(100, -0.6f, 0f, 0.2f, -0.6f, 0f, -0.2f, 0.65f, 0f, 0.35f, 0f, 0.05f, 0.25f, 0f, -0.05f, -0.6f) };
	// put out: a flinch, arms up shaking the water off, then a slump (head down, arms hanging)
	private static final float[][] DOUSED = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -1.4f, -0.6f, 0.1f, -1.4f, 0.6f, -0.1f, 0.35f, 0f, 0.2f, 0f, 0f, -0.1f, 0f, 0f, 0.4f),
			QuirkKeyframes.f(8, -1.0f, -0.5f, 0.1f, -1.0f, 0.5f, -0.1f, 0.3f, 0f, 0.15f, 0f, 0f, -0.05f, 0f, 0f, 0.3f),
			QuirkKeyframes.f(16, 0.1f, 0f, 0.15f, 0.1f, 0f, -0.15f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.45f), QuirkKeyframes.rest(26) };
	// relit: a gasp, then arms flung wide and the head thrown back as the flames burst back
	private static final float[][] REIGNITE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -1.3f, -0.9f, 0.1f, -1.3f, 0.9f, -0.1f, 0.3f, 0f, 0.3f, 0f, 0.1f, -0.3f, 0f, -0.1f, 0.25f),
			QuirkKeyframes.f(5, -0.5f, 0f, 1.6f, -0.5f, 0f, -1.6f, -0.2f, 0f, 0f, 0f, 0.25f, 0f, 0f, -0.25f, -0.45f),
			QuirkKeyframes.f(12, -0.4f, 0f, 1.4f, -0.4f, 0f, -1.4f, -0.12f, 0f, 0f, 0f, 0.2f, 0f, 0f, -0.2f, -0.3f), QuirkKeyframes.rest(20) };

	// first person {tick, pitch, yaw, roll, x, y, z}: each pulled back a touch first, then thrust, then eased home
	private static final FirstPersonClip FP_BLAST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 15, -10, 0, -0.05f, -0.05f, 0.12f),
			FirstPersonClip.f(2.5f, -28, 18, 0, 0.14f, 0.12f, -0.16f), FirstPersonClip.f(5, -24, 16, 0, 0.12f, 0.1f, -0.12f), FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0)).both();
	/** The hands drawn in to the middle of the view as the fire gathers in them. */
	private static final FirstPersonClip FP_GATHER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -18, 30, 0, 0.14f, 0.06f, 0.02f),
			FirstPersonClip.f(9, -20, 34, 0, 0.16f, 0.08f, 0.02f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BOLT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 8, -5, 0, 0f, -0.03f, 0.06f),
			FirstPersonClip.f(3, -22, 14, 0, 0.1f, 0.08f, -0.12f), FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0));
	/** The arms drawn down out of the way: the fire comes from his mouth, not his hands. */
	private static final FirstPersonClip FP_BREATH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, 25, 0, 0, 0, -0.42f, 0.1f),
			FirstPersonClip.f(1000000f, 25, 0, 0, 0, -0.42f, 0.1f)).both();
	private static final FirstPersonClip FP_BREATH_END = FirstPersonClip.of(FirstPersonClip.f(0, 25, 0, 0, 0, -0.42f, 0.1f), FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLAM_GATHER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, 30, 0, 0.14f, 0.08f, 0f),
			FirstPersonClip.f(HeatblastKit.SLAM_WINDUP - 1, -75, 10, 10, 0, 0.42f, 0.15f), FirstPersonClip.f(HeatblastKit.SLAM_WINDUP + 3, -75, 10, 10, 0, 0.42f, 0.15f)).both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, -75, 10, 10, 0, 0.42f, 0.15f), FirstPersonClip.f(2.5f, 45, 8, 0, 0.03f, -0.38f, -0.1f),
			FirstPersonClip.f(10, 40, 8, 0, 0.03f, -0.36f, -0.1f), FirstPersonClip.f(19, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LASSO = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -70, 5, 12, 0.02f, 0.38f, 0.12f),
			FirstPersonClip.f(3.5f, -18, 22, 0, 0.12f, 0.06f, -0.22f), FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_YANK = FirstPersonClip.of(FirstPersonClip.f(0, -18, 22, 0, 0.12f, 0.06f, -0.22f),
			FirstPersonClip.f(2, 18, 15, 0, 0.08f, -0.08f, 0.22f), FirstPersonClip.f(6, 25, 12, 0, 0.06f, -0.12f, 0.28f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MAGMA_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -60, 6, 8, 0.02f, 0.34f, 0.12f),
			FirstPersonClip.f(HeatblastKit.MAGMA_CHARGE + 4, -78, 8, 10, 0.02f, 0.42f, 0.16f)).both();
	private static final FirstPersonClip FP_MAGMA = FirstPersonClip.of(FirstPersonClip.f(0, -78, 8, 10, 0.02f, 0.42f, 0.16f), FirstPersonClip.f(3, -10, 22, 0, 0.12f, 0.05f, -0.22f),
			FirstPersonClip.f(7, 5, 15, 0, 0.08f, -0.05f, -0.12f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_JUMP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 35, 0, 0, 0, -0.3f, 0.1f),
			FirstPersonClip.f(5, 25, 0, 0, 0, -0.25f, 0.08f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RIDE_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(6, -70, 0, 10, 0.02f, 0.35f, 0.12f), FirstPersonClip.f(HeatblastKit.RIDE_CHARGE_TICKS, -78, 0, 12, 0.02f, 0.4f, 0.14f),
			FirstPersonClip.f(HeatblastKit.RIDE_CHARGE_TICKS + 8, -78, 0, 12, 0.02f, 0.4f, 0.14f));
	private static final FirstPersonClip FP_RIDE_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, -78, 0, 12, 0.02f, 0.4f, 0.14f),
			FirstPersonClip.f(2, 45, -10, 0, -0.05f, -0.4f, 0.05f), FirstPersonClip.f(6, 40, -10, 0, -0.05f, -0.38f, 0.05f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_DASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 10, 0, 0, 0, -0.05f, 0.1f),
			FirstPersonClip.f(3, 40, -10, 0, -0.05f, -0.3f, 0.15f), FirstPersonClip.f(HeatblastKit.DASH_TICKS, 38, -10, 0, -0.05f, -0.28f, 0.14f),
			FirstPersonClip.f(HeatblastKit.DASH_TICKS + 5, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TORNADO = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 0, 15, -0.05f, 0.45f, 0.05f),
			FirstPersonClip.f(6, -26, 18, 0, 0.14f, 0.12f, -0.16f), FirstPersonClip.f(16, -24, 16, 0, 0.12f, 0.1f, -0.12f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0))
			.both();
	// the Supernova: the hands at the bottom corners, swept out to the edges, then in to the core in the middle of the view
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(10, 25, -6, 0, -0.1f, -0.25f, 0f),
			FirstPersonClip.f(32, -26, -32, 18, -0.24f, 0.1f, 0f), FirstPersonClip.f(56, -28, 38, 0, 0.22f, 0.02f, 0.05f),
			FirstPersonClip.f(HeatblastKit.SUPERNOVA_CHARGE - 8, -18, 48, 0, 0.3f, -0.06f, 0.14f), FirstPersonClip.f(HeatblastKit.SUPERNOVA_CHARGE + 4, -18, 48, 0, 0.3f, -0.06f, 0.14f)).both();
	private static final FirstPersonClip FP_SUPERNOVA = FirstPersonClip.of(FirstPersonClip.f(0, -18, 48, 0, 0.3f, -0.06f, 0.14f),
			FirstPersonClip.f(2, -45, -50, 35, -0.3f, 0.3f, 0), FirstPersonClip.f(14, -40, -45, 30, -0.27f, 0.27f, 0), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 10, -8, 0, -0.03f, -0.05f, 0.1f),
			FirstPersonClip.f(3, -22, 26, 0, 0.16f, 0.08f, -0.22f), FirstPersonClip.f(5, -20, 24, 0, 0.15f, 0.08f, -0.18f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CUP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 30, 0, 0.18f, 0.12f, -0.05f),
			FirstPersonClip.f(7, -16, 33, 0, 0.2f, 0.1f, -0.03f), FirstPersonClip.f(12, -20, 30, 0, 0.18f, 0.12f, -0.05f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_AURA = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(2, -15f, 30f, 0f, 0.18f, 0f, 0.05f),
			FirstPersonClip.f(5, -20f, -30f, 25f, -0.2f, 0.1f, 0f), FirstPersonClip.f(14, -18f, -28f, 22f, -0.18f, 0.08f, 0f), FirstPersonClip.f(20, 0f, 0f, 0f, 0f, 0f, 0f))
			.both();
	private static final FirstPersonClip FP_SHIELD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -35, 45, 0, 0.25f, 0.1f, 0.05f),
			FirstPersonClip.f(6, -25, -35, 25, -0.2f, 0.2f, 0), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ERUPT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 0, 10, 0, 0.4f, 0.1f),
			FirstPersonClip.f(5, 40, 10, 0, 0.05f, -0.4f, -0.05f), FirstPersonClip.f(14, 36, 10, 0, 0.05f, -0.38f, -0.05f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_RISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 35, 0, 0, 0, -0.3f, 0.1f),
			FirstPersonClip.f(5, -60, 0, 8, 0, 0.35f, 0.1f), FirstPersonClip.f(40, -60, 0, 8, 0, 0.35f, 0.1f)).both();
	private static final FirstPersonClip FP_DIVE = FirstPersonClip.of(FirstPersonClip.f(0, -60, 0, 8, 0, 0.35f, 0.1f), FirstPersonClip.f(3, 30, 0, 0, 0, -0.2f, -0.15f),
			FirstPersonClip.f(100, 30, 0, 0, 0, -0.2f, -0.15f)).both();
	private static final FirstPersonClip FP_DOUSED = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -40, 30, 10, 0.15f, 0.2f, 0.1f),
			FirstPersonClip.f(5, -32, 26, -8, 0.13f, 0.17f, 0.1f), FirstPersonClip.f(8, -38, 30, 8, 0.15f, 0.19f, 0.1f), FirstPersonClip.f(16, 20, 0, 0, 0, -0.25f, 0f),
			FirstPersonClip.f(26, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_REIGNITE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 35, 0, 0.2f, 0, 0.05f),
			FirstPersonClip.f(5, -35, -40, 30, -0.25f, 0.2f, 0), FirstPersonClip.f(12, -32, -36, 26, -0.22f, 0.18f, 0), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();

	private static HeatblastKit kit() {
		return (HeatblastKit) Aliens.kit(HeatblastKit.ID);
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public ResourceLocation skin(AbstractClientPlayer p) {
		return HeatblastKit.doused(p) ? DOUSED_SKIN : SKIN;
	}

	/** The burning overlay frame for {@code age} (32 frames, 2 ticks each: lava pulsing through the cracks, flames up the rock). */
	private static ResourceLocation glow(float age) {
		return GLOW[((int) (age / 2f) % GLOW.length + GLOW.length) % GLOW.length];
	}

	/** Ticks since the synced move animation started. */
	private static float animTicks(AbstractClientPlayer p, float partial) {
		return (p.level().getGameTime() - Omnitrix.state(p).animStart) + partial;
	}

	/**
	 * Doused: how far the reignite countdown has run (0 = just out of the water / still wet .. 1 = about to relight), with a
	 * flicker -- what the embers and the faint returning glow follow.
	 */
	private static float rekindle(AbstractClientPlayer p, float age) {
		int left = kit().reigniteLeft(p);
		if (left <= 0) {
			return 0f;
		}
		float k = 1f - left / (float) HeatblastKit.REIGNITE_TICKS;
		float flicker = 0.55f + 0.45f * Mth.sin(age * 0.9f) * Mth.sin(age * 0.37f + 1.3f);
		return k * k * flicker;
	}

	private static int grey(float k) {
		int c = Mth.clamp((int) (255 * k), 0, 255);
		return 0xFF000000 | c << 16 | c << 8 | c;
	}

	/**
	 * His head fire -- v0.17.2 (user: "make the flames on heatblats head look slightly better please, they dont properly match up on
	 * his head 100%"): his own blaze instead of Ghost Rider's skull fire (whose tongues hung down the sides of the face and stood out
	 * past it). Four sheets of flame rise flush off the four top edges of his head (the hat layer's), leaning in as they climb, and
	 * a taller crossed pair blazes up out of the middle; each flickers on its own beat. Head space: the cube spans x / z -0.25..0.25,
	 * y -0.5 (top) .. 0; -y is up.
	 */
	private static void headFire(PoseStack pose, MultiBufferSource buffers, ModelPart head, float strength, float age) {
		net.minecraft.client.renderer.texture.TextureAtlasSprite fire0 = net.minecraft.client.resources.model.ModelBakery.FIRE_0.sprite();
		net.minecraft.client.renderer.texture.TextureAtlasSprite fire1 = net.minecraft.client.resources.model.ModelBakery.FIRE_1.sprite();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
		pose.pushPose();
		head.translateAndRotate(pose);
		PoseStack.Pose last = pose.last();
		float s = Mth.clamp(strength, 0f, 1.8f);
		float e = 0.27f; // the hat layer's edge
		float base = -0.5f - 0.02f; // just above the top of the head
		// the four edge sheets: flush along an edge at the base, drawn in and up toward the middle at the top
		for (int side = 0; side < 4; side++) {
			float flick = 0.85f + 0.15f * Mth.sin(age * 0.6f + side * 1.9f);
			float h = (0.62f + 0.1f * (side % 2)) * flick * s;
			float in = 0.05f + 0.02f * Mth.sin(age * 0.4f + side);
			float top = base - h;
			float tw = e * 0.85f;
			// corners in head space, rotated per side: the edge at z = -e (front), its top drawn in toward the middle
			float[][] q = { { -e, base, -e }, { e, base, -e }, { tw, top, -e + in }, { -tw, top, -e + in } };
			float[][] r = new float[4][3];
			for (int k = 0; k < 4; k++) {
				float x = q[k][0], z = q[k][2];
				switch (side) {
					case 0 -> { r[k][0] = x; r[k][2] = z; }
					case 1 -> { r[k][0] = -z; r[k][2] = x; }
					case 2 -> { r[k][0] = -x; r[k][2] = -z; }
					default -> { r[k][0] = z; r[k][2] = -x; }
				}
				r[k][1] = q[k][1];
			}
			fireQuad(vc, last, (side & 1) == 0 ? fire0 : fire1, r);
		}
		// the core: a taller crossed pair straight up out of the middle, swaying
		float sway = 0.04f * Mth.sin(age * 0.25f);
		float core = (0.95f + 0.12f * Mth.sin(age * 0.7f)) * s;
		float w = e * 0.85f;
		float top = base - core;
		fireQuad(vc, last, fire0, new float[][] { { -w, base, 0 }, { w, base, 0 }, { w * 0.6f + sway, top, sway }, { -w * 0.6f + sway, top, sway } });
		fireQuad(vc, last, fire1, new float[][] { { 0, base, -w }, { 0, base, w }, { sway, top, w * 0.6f + sway }, { sway, top, -w * 0.6f + sway } });
		pose.popPose();
	}

	/** One fire quad: corners 0-1 the base (the sprite's bottom edge), 2-3 the top. Full bright, double-sided. */
	private static void fireQuad(VertexConsumer vc, PoseStack.Pose last, net.minecraft.client.renderer.texture.TextureAtlasSprite sp, float[][] c) {
		float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
		float[][] uv = { { u0, v1 }, { u1, v1 }, { u1, v0 }, { u0, v0 } };
		for (int k = 0; k < 4; k++) {
			vc.addVertex(last.pose(), c[k][0], c[k][1], c[k][2]).setColor(255, 255, 255, 255).setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, -1f, 0f);
		}
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		var st = Omnitrix.state(p);
		int anim = st.animId;
		float ticks = animTicks(p, partial);
		if (HeatblastKit.doused(p)) {
			// put out: the charred skin (skin(p)) alone -- no burning overlay, no head fire; as the countdown runs, the cracks
			// glow back faintly, flickering
			float k = rekindle(p, age);
			if (k > 0.02f) {
				model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(glow(age))), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, grey(0.45f * k));
			}
			return;
		}
		// the burning overlay -- the skin's pale cracks run with lava, flames lick up the rock, the head glows from inside
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(glow(age))), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		// the burning-skull fire on his head -- blazing up taller in the big moves (and the moment he relights)
		boolean big = anim == HeatblastKit.ANIM_SUPERNOVA || anim == HeatblastKit.ANIM_TORNADO || anim == HeatblastKit.ANIM_REIGNITE && ticks < 14f
				|| (st.kitFlags & (HeatblastKit.F_RIDE_CHARGE | HeatblastKit.F_STREAM | HeatblastKit.F_MAGMA | HeatblastKit.F_SLAM)) != 0;
		float relit = anim == HeatblastKit.ANIM_REIGNITE && ticks < 14f ? 1f + 0.8f * (1f - ticks / 14f) : 1f;
		float nova = kit().novaCharge(p, partial);
		headFire(pose, buffers, model.head, nova > 0f ? 1f + 0.6f * nova : big ? 1.4f * relit : 1f, age);
		if (anim == HeatblastKit.ANIM_REIGNITE && ticks < 10f) {
			// the flames burst back over his whole body
			PyrokinesisClient.bodyFire(pose, buffers, age, 1.2f * (1f - ticks / 10f) + 0.2f);
		}

		boolean dashing = (st.kitFlags & HeatblastKit.F_DASH) != 0 || anim == HeatblastKit.ANIM_DASH && ticks < HeatblastKit.DASH_TICKS + 1;
		boolean meteor = (st.kitFlags & HeatblastKit.F_METEOR) != 0;
		if (dashing || meteor) {
			// the Blaze Dash and the Meteor Crash: wrapped in fire, flames streaming back off every limb
			PyrokinesisClient.bodyFire(pose, buffers, age, meteor ? 1.35f : 1.1f);
			PyrokinesisClient.limbFlames(pose, buffers, model, false, age, meteor ? 1.8f : 1.6f, 0.9f);
		} else if (anim == HeatblastKit.ANIM_JUMP && ticks < 8f) {
			PyrokinesisClient.jetFlames(pose, buffers, model, false, age, 1.2f * (1f - ticks / 8f) + 0.2f);
		}
		// v0.17.2 (user: "player gathers fire in their hands"): the fire gathering in both palms; each hand keeps its fire until it has thrown it
		if (anim == HeatblastKit.ANIM_VOLLEY_GATHER) {
			float k = Mth.clamp(ticks / HeatblastKit.VOLLEY_GATHER, 0f, 1f);
			handBall(pose, buffers, model.rightArm, -1f, 0.06f + 0.16f * k, age);
			handBall(pose, buffers, model.leftArm, 1f, 0.06f + 0.16f * k, age);
		}
		if (anim == HeatblastKit.ANIM_VOLLEY) {
			handBall(pose, buffers, model.leftArm, 1f, 0.2f, age);
		} else if (anim == HeatblastKit.ANIM_VOLLEY_L) {
			handBall(pose, buffers, model.rightArm, -1f, 0.2f, age);
		}
		// the Magma Slam: the fire swelling in both raised fists, then bursting out where they hit the ground
		if ((st.kitFlags & HeatblastKit.F_SLAM) != 0) {
			float k = Mth.clamp(ticks / HeatblastKit.SLAM_WINDUP, 0f, 1f);
			handBall(pose, buffers, model.rightArm, -1f, 0.1f + 0.24f * k, age);
			handBall(pose, buffers, model.leftArm, 1f, 0.1f + 0.24f * k, age);
			PyrokinesisClient.limbFlames(pose, buffers, model, false, age, 0.4f + 0.5f * k, 0f);
		} else if (anim == HeatblastKit.ANIM_SLAM && ticks < 9f) {
			float k = 1.1f * (1f - ticks / 9f);
			palmFire(pose, buffers, model.rightArm, -1f, k, age);
			palmFire(pose, buffers, model.leftArm, 1f, k, age);
		}
		// the Fire Breath: the glow of it in his mouth and flames licking off his head and shoulders
		if ((st.kitFlags & HeatblastKit.F_STREAM) != 0) {
			pose.pushPose();
			model.head.translateAndRotate(pose);
			float g = Mth.clamp(ticks / HeatblastKit.BREATH_INHALE, 0f, 1f);
			PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, -2.0 / 16.0, -4.6 / 16.0), 0.34f * g, PyroDraw.dim(0xFFFFB050, 0.9f));
			pose.popPose();
		}
		// the shots' palm flares, the punch's burning fist
		if ((anim == HeatblastKit.ANIM_VOLLEY || anim == HeatblastKit.ANIM_VOLLEY_3) && ticks < 5f) {
			palmFire(pose, buffers, model.rightArm, -1f, 0.85f * (1f - ticks / 5f) + 0.2f, age);
		} else if (anim == HeatblastKit.ANIM_VOLLEY_L && ticks < 5f) {
			palmFire(pose, buffers, model.leftArm, 1f, 0.85f * (1f - ticks / 5f) + 0.2f, age);
		} else if (anim == HeatblastKit.ANIM_BOLT && ticks < 4f) {
			palmFire(pose, buffers, model.rightArm, -1f, 0.6f * (1f - ticks / 4f) + 0.15f, age);
		} else if (anim == HeatblastKit.ANIM_LASSO && ticks < 5f) {
			palmFire(pose, buffers, model.rightArm, -1f, 0.5f, age);
		} else if (anim == HeatblastKit.ANIM_PUNCH && ticks < 5f) {
			handGlow(pose, buffers, model.rightArm, -1f, 0.32f * (1f - ticks / 5f) + 0.1f);
		} else if (anim == HeatblastKit.ANIM_PUNCH_L && ticks < 5f) {
			handGlow(pose, buffers, model.leftArm, 1f, 0.32f * (1f - ticks / 5f) + 0.1f);
		} else if (anim == HeatblastKit.ANIM_ERUPT && ticks < 12f) {
			palmFire(pose, buffers, model.rightArm, -1f, ticks < 5f ? 0.4f + ticks * 0.1f : 0.9f * (1f - (ticks - 5f) / 7f), age);
		}
		// the Rock Ride's charge: fire gathering round the raised fist, growing until it hits the ground
		if ((st.kitFlags & HeatblastKit.F_RIDE_CHARGE) != 0) {
			float k = kit().rideCharge(p);
			palmFire(pose, buffers, model.rightArm, -1f, 0.35f + 0.9f * k, age);
			handGlow(pose, buffers, model.rightArm, -1f, 0.25f + 0.45f * k * (0.85f + 0.15f * Mth.sin(age * 1.3f)));
		} else if (anim == HeatblastKit.ANIM_RIDE_PUNCH && ticks < 6f) {
			handGlow(pose, buffers, model.rightArm, -1f, 0.7f * (1f - ticks / 6f));
		}
		// the Magma Bomb swelling over his head, between his raised hands, until it is thrown
		float mag = kit().magmaCharge(p, partial);
		if (mag > 0f) {
			pose.pushPose();
			float r = magmaRadius(mag);
			pose.translate(0f, -0.62f - r * 0.85f, 0f);
			PyroDraw.fireball(pose, buffers, r, age, new Vec3(0, 1, 0), true);
			pose.popPose();
			handGlow(pose, buffers, model.rightArm, -1f, 0.3f + 0.2f * mag);
			handGlow(pose, buffers, model.leftArm, 1f, 0.3f + 0.2f * mag);
		} else if (anim == HeatblastKit.ANIM_MAGMA && ticks < 3f) {
			handGlow(pose, buffers, model.rightArm, -1f, 0.5f * (1f - ticks / 3f));
			handGlow(pose, buffers, model.leftArm, 1f, 0.5f * (1f - ticks / 3f));
		}
		// the Supernova: the core growing between his hands, the flames growing and turning jagged round his body; right at the end
		// it all crushes down into the core, white-hot, before the blast
		if (nova > 0f) {
			float crush = Mth.clamp((nova - 0.86f) / 0.14f, 0f, 1f);
			novaFlames(pose, buffers, model, nova, crush, age);
			float r = (nova < 0.86f ? 0.1f + 0.42f * nova / 0.86f : Mth.lerp(crush, 0.52f, 0.24f)) * (0.95f + 0.05f * Mth.sin(age * 1.7f));
			chestBall(pose, buffers, model, r, age, 0.42f);
			if (crush > 0f) {
				pose.pushPose();
				model.body.translateAndRotate(pose);
				PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, 0.42, -0.3f - r), 0.5f + 1.2f * crush, PyroDraw.dim(0xFFFFF0D0, crush));
				pose.popPose();
			}
		} else if (anim == HeatblastKit.ANIM_SMELT && ticks < 13f) {
			chestBall(pose, buffers, model, 0.1f + 0.02f * Mth.sin(age * 0.9f), age, 0.5f);
		}
		if (anim == HeatblastKit.ANIM_SUPERNOVA && ticks < 12f) {
			// the blast: fire pouring off him
			PyrokinesisClient.bodyFire(pose, buffers, age, 1.9f * (1f - ticks / 12f) + 0.2f);
			PyrokinesisClient.limbFlames(pose, buffers, model, false, age, 2.2f * (1f - ticks / 12f) + 0.3f, 0f);
		}
		// v0.17.2 (user: "Make shift+V absorb flames instead of heat shield"): Absorb Flames -- a shell of fire collapsing in on him
		// as the streams arrive (the streams themselves are rendered ribbons from the server), fire licking up off every limb
		if ((st.kitFlags & HeatblastKit.F_ABSORB) != 0) {
			float k = Mth.clamp(ticks / HeatblastKit.ABSORB_TICKS, 0f, 1f);
			PyrokinesisClient.limbFlames(pose, buffers, model, false, age, 0.6f + 0.8f * k, 0f);
			pose.pushPose();
			pose.translate(0f, 0.75f, 0f);
			float r = Mth.lerp(k, 3.0f, 0.55f) + 0.04f * Mth.sin(age * 0.6f);
			PyroDraw.sphere(pose, PyroDraw.additive(buffers), r, PyroDraw.dim(0xFFFF8A30, 0.18f + 0.25f * k), PyroDraw.dim(0xFF801800, 0.1f), 0.06f, -age,
					PyroDraw.FULL);
			PyroDraw.sphere(pose, PyroDraw.additive(buffers), r * 0.9f, PyroDraw.dim(0xFFFFD080, 0.1f + 0.15f * k), 0xFF000000, 0.1f, -age * 1.3f, PyroDraw.FULL);
			pose.popPose();
		}
		// the Heat Aura: the ground glowing like a hotplate round him, his hands white-hot
		if ((st.kitFlags & HeatblastKit.F_AURA) != 0) {
			float g = 0.3f + 0.08f * Mth.sin(age * 0.2f);
			PyroDraw.annulus(pose, PyroDraw.additive(buffers), 0f, (float) HeatblastKit.AURA_RADIUS, PyroDraw.dim(0xFFFF5A10, g), 0xFF000000, 1.48f);
			PyrokinesisClient.whiteHotHands(pose, buffers, model, 0.85f + 0.15f * (0.5f + 0.5f * Mth.sin(age * Mth.TWO_PI / 18f)));
		}
	}

	// ---------------------------------------------------------------- the world pass: shots, Flamethrower, lasso, tornado, rock

	/** From {@code OmnitrixClient}: the shots drawn as Pyrokinesis' fireballs; the world-pass effects. */
	public static void init() {
		OmnitrixRenderers.projectileLook("heatblast_blast", (e, partial, pose, buffers) -> shot(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("heatblast_bolt", (e, partial, pose, buffers) -> shot(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("heatblast_bomb", (e, partial, pose, buffers) -> shot(e, partial, pose, buffers, true));
		WorldRenderEvents.AFTER_ENTITIES.register(HeatblastClient::renderWorld);
	}

	/** A shot in flight: Pyrokinesis' Fireball (the volley, the Use bolt) or Meteor (the Magma Bomb), at the shot's size. */
	private static void shot(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean meteor) {
		pose.pushPose();
		pose.translate(0.0, e.getBbHeight() * 0.5, 0.0);
		PyroBoltRenderer.draw(pose, buffers, e.size(), e.tickCount + partial, e.getDeltaMovement(), meteor, false);
		pose.popPose();
	}

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			var st = Omnitrix.state(pl);
			if (!HeatblastKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			if ((st.kitFlags & HeatblastKit.F_STREAM) != 0) {
				breath(pl, partial, cam, pose, buffers);
			}
			float nova = kit().novaCharge(pl, partial);
			if (nova > 0f) {
				novaWorld(pl, nova, partial, cam, pose, buffers);
			}
			float mag = kit().magmaCharge(pl, partial);
			if (mag > 0f && pl == mc.player && mc.options.getCameraType().isFirstPerson()) {
				magmaFirstPerson(pl, mag, partial, cam, pose, buffers);
			}
			if (st.animId == HeatblastKit.ANIM_SUPERNOVA) {
				novaBurst(pl, animTicks((AbstractClientPlayer) pl, partial), partial, cam, pose, buffers);
			}
			if ((st.kitFlags & HeatblastKit.F_LASSO) != 0) {
				Entity t = mc.level.getEntity(kit().lassoTarget(pl));
				if (t != null) {
					lasso(pl, t, partial, cam, pose, buffers);
				}
			}
			if ((st.kitFlags & HeatblastKit.F_TORNADO) != 0) {
				Vec3 c = kit().tornadoAt(pl);
				if (c != null) {
					tornado(pl, c, partial, cam, pose, buffers);
				}
			}
			if ((st.kitFlags & HeatblastKit.F_RIDING) != 0) {
				rock(pl, partial, cam, pose, buffers);
			}
		}
	}

	/** The big ball's radius at charge {@code k} (0..1): it swells fast, then steadies at the size that is thrown. */
	private static float magmaRadius(float k) {
		return 0.2f + (HeatblastKit.MAGMA_BALL - 0.2f) * (1f - (1f - k) * (1f - k));
	}

	/** First person the ball is out of the model's view: it hangs at the top of his sight instead. */
	private static void magmaFirstPerson(Player pl, float k, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 look = pl.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		float r = magmaRadius(k);
		Vec3 at = pl.getEyePosition(partial).add(look.scale(1.5)).add(up.scale(0.72 + r * 0.6)).subtract(cam);
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		PyroDraw.fireball(pose, buffers, r, pl.tickCount + partial, new Vec3(0, -1, 0), true);
		pose.popPose();
	}

	/** Where the Fire Breath leaves him: his mouth, or first person just under the view. */
	private static Vec3 mouth(Player pl, float partial, Vec3 look) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = pl.getEyePosition(partial);
		float s = pl.getScale();
		if (pl == mc.player && mc.options.getCameraType().isFirstPerson()) {
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			return eye.add(look.scale(0.75)).subtract(right.cross(look).normalize().scale(0.5));
		}
		return eye.add(look.scale(0.33 * s)).add(0, -0.08 * s, 0);
	}

	/**
	 * The Fire Breath -- v0.17.2 (user: "player breaths fire from their mouths"): a narrow white-hot jet from his mouth widening into a
	 * rolling stream of fire down his aim, stopping at the first block, jagged tongues of flame lashing off it near the mouth.
	 */
	private static void breath(Player pl, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float age = kit().breathAge(pl, partial) - HeatblastKit.BREATH_INHALE;
		float grow = Mth.clamp(age / 5f, 0f, 1f);
		if (grow <= 0f) {
			return;
		}
		float time = pl.tickCount + partial;
		Vec3 look = pl.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		double range = HeatblastKit.BREATH_RANGE * grow;
		var hit = pl.pick(range, partial, false);
		if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			range = Math.max(0.8, hit.getLocation().distanceTo(pl.getEyePosition(partial)));
		}
		Minecraft mc = Minecraft.getInstance();
		boolean own = pl == mc.player && mc.options.getCameraType().isFirstPerson();
		float view = own ? 0.5f : 1f;
		Vec3 h = mouth(pl, partial, look).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		final double reach = range;
		List<Vec3> core = new ArrayList<>();
		for (int i = 0; i <= 10; i++) {
			double u = i / 10.0;
			double wob = Math.sin(u * 7.0 - time * 1.3) * 0.05 * u;
			core.add(h.add(look.scale(Math.min(reach, 3.6) * u)).add(up.scale(wob)));
		}
		PyroDraw.ribbon(pose, add, core, u -> 0.08 + 0.75 * u, u -> PyroDraw.dim(0xFFFFE8B0, (float) (1.0 - u * 0.9)));
		PyroDraw.glow(pose, glow, h, 0.42f, PyroDraw.dim(0xFFFFC070, 0.8f * grow));
		// the jagged tongues lashing off the jet (a few steps from the mouth)
		for (int i = 0; i < 7; i++) {
			double a = i * 2.4 + time * 0.31;
			double d = 0.3 + (i * 0.37 + time * 0.09) % 1.0 * Math.min(reach, 2.6);
			Vec3 at = h.add(look.scale(d));
			Vec3 out = right.scale(Math.cos(a)).add(up.scale(Math.sin(a))).add(look.scale(1.3)).normalize();
			jagged(pose, add, at, out, 0.5f + 0.5f * (float) (d / 2.6), 0.13f, 0.35f, i, time, 0xFFFF7A20, 0xFF301000);
		}
		int n = 40;
		for (int i = 0; i < n; i++) {
			// each puff of fire scrolls out along the stream and grows as it goes (bunched toward the mouth)
			double u0 = (i * 0.6180339 + time * 0.13) % 1.0;
			double u = u0 * u0 * 0.6 + u0 * 0.4;
			double d = 0.25 + u * range;
			double jx = Math.sin(i * 12.9898) * 0.5;
			double jy = Math.sin(i * 78.233) * 0.5;
			if (own && d < 1.6) {
				continue;
			}
			double spread = 0.02 + d * 0.085;
			Vec3 q = h.add(look.scale(d)).add(right.scale(jx * spread * 2)).add(up.scale(jy * spread * 2));
			float size = (float) (0.16 + u * 1.3) * view;
			float k = (float) (u > 0.82 ? (1.0 - u) / 0.18 : 1.0);
			if (i % 2 == 0) {
				PyroDraw.glow(pose, glow, q, size * 1.05f, PyroDraw.dim(PyroDraw.lerpRgb(0xFFFFB050, 0xFFB02800, (float) u), 0.55f * k));
			}
			if (k > 0.3f) {
				PyroDraw.fireBillboard(pose, fire, q.subtract(0, size * 0.4, 0), Vec3.ZERO, size * 0.85f * k, size * k, (int) (time / 2) + i);
			}
		}
	}

	/**
	 * One jagged tongue of fire from {@code root} along {@code dir} (unit) in the pose's space: a zig-zag spine, its width pinched and
	 * flared at every kink so the edge is all barbs, in two crossed strips that read from any side. {@code rough} 0 is a smooth
	 * spike, 0.5 savagely jagged. Additive: {@code hot} at the root, {@code tip} (near black) at the point.
	 */
	private static void jagged(PoseStack pose, VertexConsumer vc, Vec3 root, Vec3 dir, float len, float width, float rough, int seed, float time, int hot, int tip) {
		Vec3 a = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 s1 = dir.cross(a).normalize();
		Vec3 s2 = dir.cross(s1).normalize();
		PoseStack.Pose last = pose.last();
		int segs = 6;
		for (int strip = 0; strip < 2; strip++) {
			Vec3 side = strip == 0 ? s1 : s2;
			Vec3 prevL = null, prevR = null;
			int prevC = hot;
			for (int j = 0; j <= segs; j++) {
				float u = j / (float) segs;
				float flick = 0.75f + 0.25f * Mth.sin(time * 0.9f + j * 1.7f + seed * 2.3f);
				// the spine kicks left and right, harder toward the tip
				float kick = (j == 0 ? 0f : (j % 2 == 0 ? 1f : -1f)) * rough * len * 0.2f * flick * (0.4f + u);
				float w = width * (1f - u * 0.92f) * (j % 2 == 0 ? 1.45f : 0.6f) * (j == segs ? 0f : 1f);
				Vec3 p = root.add(dir.scale(len * u)).add(side.scale(kick));
				Vec3 l = p.add(side.scale(w * 0.5f));
				Vec3 r = p.subtract(side.scale(w * 0.5f));
				int c = PyroDraw.lerpRgb(hot, tip, u * u);
				if (prevL != null) {
					PyroDraw.quad(vc, last, prevL, prevR, r, l, prevC, prevC, c, c);
					PyroDraw.quad(vc, last, l, r, prevR, prevL, c, c, prevC, prevC);
				}
				prevL = l;
				prevR = r;
				prevC = c;
			}
		}
	}

	/** The points on his body the Supernova's flames rise from: {bone 0 body / 1 right arm / 2 left arm / 3 right leg / 4 left leg / 5 head, x, y, z px}. */
	private static final float[][] NOVA_ROOTS = { { 0, -3, 2, -2 }, { 0, 3, 2, -2 }, { 0, -3, 2, 2 }, { 0, 3, 2, 2 }, { 0, 0, 7, -2 }, { 0, 0, 7, 2 }, { 0, -4, 8, 0 },
			{ 0, 4, 8, 0 }, { 0, -2, 11, -2 }, { 0, 2, 11, -2 }, { 0, -2, 11, 2 }, { 0, 2, 11, 2 }, { 1, -3, 1, 0 }, { 1, -3, 5, 0 }, { 1, -3, 9, 0 }, { 1, -1, 3, -2 },
			{ 1, -1, 7, 2 }, { 2, 3, 1, 0 }, { 2, 3, 5, 0 }, { 2, 3, 9, 0 }, { 2, 1, 3, -2 }, { 2, 1, 7, 2 }, { 3, -2, 3, 0 }, { 3, -2, 8, 0 }, { 3, 0, 6, -2 }, { 4, 2, 3, 0 },
			{ 4, 2, 8, 0 }, { 4, 0, 6, -2 }, { 5, -4, -3, 0 }, { 5, 4, -3, 0 } };

	/**
	 * The Supernova's flames on his body -- v0.17.2 (user: "flames grow around the players body, they become bigger and more jagged").
	 * Early on soft little flames lick up the limbs; as {@code k} climbs there are more of them, longer and jagged (the kinks of
	 * {@link #jagged} kicking harder), real fire rising off him higher and higher. In the last moments ({@code crush}) they are
	 * dragged back down into him, white-hot. Model space: y down, the feet at +1.5.
	 */
	private static void novaFlames(PoseStack pose, MultiBufferSource buffers, PlayerModel<AbstractClientPlayer> m, float k, float crush, float age) {
		PyrokinesisClient.bodyFire(pose, buffers, age, (0.7f + 0.95f * k) * (1f - 0.5f * crush));
		PyrokinesisClient.limbFlames(pose, buffers, m, false, age, (0.55f + 1.5f * k) * (1f - 0.45f * crush), 0f);
		VertexConsumer add = PyroDraw.additive(buffers);
		ModelPart[] bones = { m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg, m.head };
		int n = Math.round(Mth.lerp(k, 10, NOVA_ROOTS.length));
		float len = (0.22f + 1.5f * k * k) * (1f - 0.65f * crush);
		float rough = 0.03f + 0.62f * k;
		int hot = PyroDraw.lerpRgb(0xFFFF7A20, 0xFFFFF0C0, crush);
		for (int i = 0; i < n; i++) {
			float[] r = NOVA_ROOTS[i];
			ModelPart b = bones[(int) r[0]];
			org.joml.Vector3f c = new org.joml.Quaternionf().rotationZYX(b.zRot, b.yRot, b.xRot).transform(new org.joml.Vector3f(r[1], r[2], r[3]));
			Vec3 at = new Vec3((b.x + c.x) / 16f, (b.y + c.y) / 16f, (b.z + c.z) / 16f);
			Vec3 outward = at.subtract(0, 0.7, 0);
			outward = outward.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, -1) : outward.normalize();
			Vec3 dir = outward.scale(0.75).add(0, -1.0, 0).normalize();
			float size = len * (0.65f + 0.55f * Mth.sin(i * 12.9898f) * Mth.sin(i * 12.9898f)) * (0.8f + 0.2f * Mth.sin(age * 0.8f + i * 1.9f));
			jagged(pose, add, at, dir, size, 0.14f + 0.14f * k, rough, i, age, hot, 0xFF401000);
			jagged(pose, add, at, dir, size * 0.6f, 0.07f + 0.07f * k, rough, i + 40, age, 0xFFFFE8B0, 0xFF803000);
		}
	}

	/**
	 * The Supernova's charge in the world (every viewer, first person too): the ground glowing wider and hotter, a crown of jagged flames
	 * rising round his feet, and ribbons of fire spiralling in from all round him to his chest -- "players gathers flames".
	 */
	private static void novaWorld(Player pl, float k, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float time = pl.tickCount + partial;
		float crush = Mth.clamp((k - 0.86f) / 0.14f, 0f, 1f);
		Vec3 b = pl.getPosition(partial).subtract(cam);
		Vec3 chest = b.add(0, 1.05, 0);
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		pose.pushPose();
		pose.translate(b.x, b.y + 0.06, b.z);
		PyroDraw.annulus(pose, add, 0f, (1.4f + 4.2f * k) * (1f - 0.7f * crush), PyroDraw.dim(0xFFFF5A10, 0.25f + 0.45f * k), 0xFF000000, 0f);
		pose.popPose();
		// the crown of jagged flames round his feet, pulled in at the end
		int crown = 12 + (int) (14 * k);
		float ringR = Mth.lerp(k, 0.8f, 1.35f) * (1f - 0.75f * crush);
		for (int i = 0; i < crown; i++) {
			float a = i * Mth.TWO_PI / crown + time * 0.04f;
			Vec3 at = b.add(Mth.cos(a) * ringR, 0.05, Mth.sin(a) * ringR);
			Vec3 dir = new Vec3(Mth.cos(a) * (0.35 - 0.8 * crush), 1, Mth.sin(a) * (0.35 - 0.8 * crush)).normalize();
			float size = (0.3f + 2.3f * k * k) * (0.6f + 0.4f * Mth.sin(i * 2.7f + time * 0.33f) * Mth.sin(i * 2.7f + time * 0.33f)) * (1f - 0.5f * crush);
			jagged(pose, add, at, dir, size, 0.22f + 0.2f * k, 0.05f + 0.6f * k, i, time, 0xFFFF7A20, 0xFF401000);
		}
		// the flames drawn in: comets of fire winding down a spiral to his chest, more and faster as it builds
		int arms = 5 + (int) (6 * k);
		for (int i = 0; i < arms; i++) {
			double head = ((time * (0.018 + 0.03 * k) + i * 0.6180339) % 1.0);
			double tail = Math.max(0.0, head - 0.34);
			List<Vec3> pts = new ArrayList<>();
			Vec3 last = chest;
			for (int j = 0; j <= 14; j++) {
				double s = Mth.lerp(j / 14.0, tail, head);
				double r = (1.0 - s) * (1.0 - s * 0.3) * 7.0;
				double ang = i * 2.399963 + s * 4.6;
				last = chest.add(Math.cos(ang) * r, (1.0 - s) * (-0.9 + 0.5 * Math.sin(i * 3.1)), Math.sin(ang) * r);
				pts.add(last);
			}
			float fade = (float) Math.min(1.0, Math.min(head / 0.15, (1.0 - head) / 0.08)) * (0.45f + 0.55f * k);
			if (fade <= 0.02f) {
				continue;
			}
			PyroDraw.ribbon(pose, add, pts, u -> 0.03 + 0.17 * u, u -> PyroDraw.dim(PyroDraw.lerpRgb(0xFF601000, 0xFFFF9030, (float) u), (float) u * 0.7f * fade));
			// real fire along it, thickest at the head of the comet
			for (int j = 6; j <= 14; j += 4) {
				float sz = (0.25f + 0.4f * j / 14f) * fade;
				PyroDraw.fireBillboard(pose, fire, pts.get(j).subtract(0, sz * 0.4f, 0), Vec3.ZERO, sz * 0.85f, sz, (int) (time / 2) + i + j);
			}
		}
	}

	/** The Supernova's blast: a hot shell of fire racing out to the blast's edge, jagged spears of flame bursting through it. */
	private static void novaBurst(Player pl, float a, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float u = a / 16f;
		if (u >= 1f) {
			return;
		}
		float out = 1f - (1f - u) * (1f - u) * (1f - u);
		float fade = (1f - u) * (1f - u);
		float r = 1f + (HeatblastKit.SUPERNOVA_RADIUS - 1f) * out;
		float time = pl.tickCount + partial;
		Vec3 b = pl.getPosition(partial).add(0, 1.0, 0).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		pose.pushPose();
		pose.translate(b.x, b.y, b.z);
		PyroDraw.sphere(pose, add, r, PyroDraw.dim(0xFFFFC070, 0.55f * fade), PyroDraw.dim(0xFF902000, 0.3f * fade), 0.07f, time, PyroDraw.FULL);
		PyroDraw.sphere(pose, add, r * 0.72f, PyroDraw.dim(0xFFFFF0D0, 0.5f * fade), PyroDraw.dim(0xFFFF8020, 0.2f * fade), 0.1f, -time * 1.3f, PyroDraw.FULL);
		pose.popPose();
		int n = 34;
		for (int i = 0; i < n; i++) {
			// a golden-spiral spread of directions
			double y = 1.0 - (i + 0.5) * 2.0 / n;
			double rad = Math.sqrt(Math.max(0.0, 1.0 - y * y));
			double ang = i * 2.399963;
			Vec3 dir = new Vec3(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
			jagged(pose, add, b.add(dir.scale(r * 0.55f)), dir, (2.0f + 3.0f * out) * (0.7f + 0.3f * Mth.sin(i * 1.7f)), 0.5f * (1f - u * 0.5f), 0.6f, i, time,
					PyroDraw.dim(0xFFFFB050, fade), 0xFF000000);
		}
	}

	/** The right hand (or both together) in world space, an arm aimed along the look (Pyrokinesis' hand point). */
	private static Vec3 hand(Player p, float partial, boolean both) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 look = p.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return p.getEyePosition(partial).add(look.scale(0.6)).add(right.scale(both ? 0.05 : 0.3)).subtract(up.scale(0.3));
		}
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 bodyRight = new Vec3(-Math.cos(bodyYaw), 0, -Math.sin(bodyYaw));
		Vec3 shoulder = p.getPosition(partial).add(0, 1.38 * p.getScale(), 0).add(bodyRight.scale(both ? 0.0 : 0.36 * p.getScale()));
		return shoulder.add(look.scale(0.7 * p.getScale()));
	}

	/** The Fire Lasso: a burning whip lashing out to the target, a ring of fire cinched round it, then hauled in. */
	private static void lasso(Player pl, Entity t, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float a = animTicks((AbstractClientPlayer) pl, partial);
		float out = Mth.clamp(a / 2.5f, 0f, 1f);
		float fade = a < HeatblastKit.LASSO_TICKS ? 1f : Math.max(0f, 1f - (a - HeatblastKit.LASSO_TICKS) / 4f);
		if (Omnitrix.state(pl).animId == HeatblastKit.ANIM_YANK) {
			out = 1f;
			fade = Math.max(0f, 1f - Math.max(0f, a - 8f) / 4f);
		}
		Vec3 h = hand(pl, partial, false).subtract(cam);
		Vec3 base = t.getPosition(partial).subtract(cam);
		Vec3 tgt = base.add(0, t.getBbHeight() * 0.55, 0);
		Vec3 end = h.lerp(tgt, out);
		double dist = h.distanceTo(end);
		float time = pl.tickCount + partial;
		List<Vec3> pts = new ArrayList<>();
		int n = 28;
		for (int i = 0; i <= n; i++) {
			double u = i / (double) n;
			double sag = Math.sin(u * Math.PI) * (0.08 * dist + 0.1) * (1.0 - 0.5 * out);
			double ripple = Math.sin(u * 14.0 - time * 1.1) * 0.07 * Math.sin(u * Math.PI);
			pts.add(h.lerp(end, u).add(0, -sag + ripple, 0));
		}
		final float f = fade;
		VertexConsumer add = PyroDraw.additive(buffers);
		PyroDraw.ribbon(pose, add, pts, u -> 0.34 * (1.0 - 0.3 * u), u -> PyroDraw.dim(0xFF7A2000, 0.8f * f));
		PyroDraw.ribbon(pose, add, pts, u -> 0.14 * (1.0 - 0.3 * u), u -> PyroDraw.dim(PyroDraw.lerpRgb(0xFFFFB050, 0xFFE05010, (float) u), f));
		PyroDraw.ribbon(pose, add, pts, u -> 0.05, u -> PyroDraw.dim(0xFFFFF0C0, f));
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		for (int i = 3; i <= n; i += 5) {
			float fh = 0.35f * fade;
			if (fh > 0.05f) {
				PyroDraw.fireBillboard(pose, fire, pts.get(i).subtract(0, fh * 0.35, 0), Vec3.ZERO, fh * 0.8f, fh, (int) (time / 2) + i);
			}
		}
		if (out >= 1f && fade > 0.05f) {
			// the noose of fire round the target
			float rr = t.getBbWidth() * 0.7f + 0.25f;
			pose.pushPose();
			pose.translate(tgt.x, tgt.y, tgt.z);
			pose.mulPose(Axis.YP.rotation(time * 0.3f));
			PyroDraw.band(pose, add, rr * 1.05f, 0.18f, PyroDraw.dim(0xFFB03000, 0.8f * fade), 0xFF000000);
			PyroDraw.band(pose, add, rr, 0.07f, PyroDraw.dim(0xFFFFC060, fade), 0xFF401000);
			for (int i = 0; i < 6; i++) {
				float ang = Mth.TWO_PI * i / 6;
				pose.pushPose();
				pose.translate(Mth.cos(ang) * rr, -0.05f, Mth.sin(ang) * rr);
				PyroDraw.fireCross(pose, fire, 0.3f * fade, 0.4f * fade, (int) (time / 3) + i, ang);
				pose.popPose();
			}
			pose.popPose();
		}
		if (a > 1.5f && a < 4.5f) {
			float k = 1f - Math.abs(a - 3f) / 1.5f;
			PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), end, 0.5f + 0.5f * k, PyroDraw.dim(0xFFFFE0A0, k));
		}
	}

	/** The Fire Tornado: a twisting column of flame, wider at the top, with a glowing ring of fire at its foot. */
	private static void tornado(Player pl, Vec3 c, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		long end = kit().timer(pl, "tor_end");
		float left = end <= 0 ? 0f : end - pl.level().getGameTime() - partial;
		float age = HeatblastKit.TORNADO_TICKS - left;
		float grow = Mth.clamp(age / 8f, 0f, 1f) * Mth.clamp(left / 10f, 0f, 1f);
		if (grow <= 0.01f) {
			return;
		}
		float time = pl.tickCount + partial;
		Vec3 b = c.subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		double height = 7.0 * grow;
		pose.pushPose();
		pose.translate(b.x, b.y + 0.05, b.z);
		PyroDraw.annulus(pose, add, 0f, 2.6f * grow, PyroDraw.dim(0xFFFF7020, 0.55f), 0xFF000000, 0f);
		pose.popPose();
		// the core: a ribbon spiralling up the axis
		List<Vec3> core = new ArrayList<>();
		for (int i = 0; i <= 24; i++) {
			double u = i / 24.0;
			double h = u * height;
			double r = 0.15 + u * 0.5;
			double ang = time * 0.55 + u * 9.0;
			core.add(b.add(Math.cos(ang) * r, h, Math.sin(ang) * r));
		}
		PyroDraw.ribbon(pose, add, core, u -> 0.25 + u * 0.5, u -> PyroDraw.dim(PyroDraw.lerpRgb(0xFFFFB050, 0xFFB02800, (float) u), 0.45f * grow));
		// the walls: flames whirling round it, the funnel flaring out toward the top
		int n = 46;
		for (int i = 0; i < n; i++) {
			double u0 = (i * 0.6180339 + time * 0.035) % 1.0;
			double h = u0 * height;
			double r = (0.45 + u0 * 1.9) * grow;
			double h0 = Math.sin(i * 12.9898) * 43758.5453;
			double ang = time * 0.45 + (h0 - Math.floor(h0)) * Mth.TWO_PI + u0 * 3.0;
			Vec3 at = b.add(Math.cos(ang) * r, h, Math.sin(ang) * r);
			float size = (float) (0.7 + u0 * 1.1) * grow;
			float k = (float) (u0 > 0.85 ? (1.0 - u0) / 0.15 : 1.0);
			PyroDraw.fireBillboard(pose, fire, at.subtract(0, size * 0.4, 0), Vec3.ZERO, size * 0.9f * k, size * k, (int) (time / 2) + i);
			if (i % 3 == 0) {
				PyroDraw.glow(pose, glow, at, size * 1.1f, PyroDraw.dim(PyroDraw.lerpRgb(0xFFFFB050, 0xFFB02800, (float) u0), 0.5f * k));
			}
		}
	}

	// ---------------------------------------------------------------- Rock Ride: the rock, its banking, the flight

	/** Per rider: the rock's eased heading, bank and pitch (degrees), now and last tick. */
	private static final Map<Integer, float[]> ROCK = new HashMap<>();

	/** Eases the rock's heading after the way he is moving, banks it into turns and pitches it with the climb / dive. */
	private static void steerRock(AbstractClientPlayer p) {
		float[] s = ROCK.get(p.getId());
		double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float want = horiz > 0.05 ? (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) : p.getYRot();
		if (s == null) {
			s = new float[] { want, 0f, 0f, want, 0f, 0f };
			ROCK.put(p.getId(), s);
		}
		s[3] = s[0];
		s[4] = s[1];
		s[5] = s[2];
		float turn = Mth.wrapDegrees(want - s[0]);
		s[0] = s[0] + turn * 0.35f;
		s[1] = Mth.lerp(0.2f, s[1], Mth.clamp(-turn * 1.6f, -16f, 16f));
		float pitch = horiz > 0.05 || Math.abs(dy) > 0.05 ? (float) (-Mth.atan2(dy, Math.max(horiz, 0.2)) * Mth.RAD_TO_DEG) : 0f;
		s[2] = Mth.lerp(0.2f, s[2], Mth.clamp(pitch * 0.6f, -22f, 22f));
	}

	/** The rock under a rider's feet: a slab of the real block ripped out of the ground, broken off underneath, fire roaring out of it. */
	private static void rock(Player pl, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float[] s = ROCK.get(pl.getId());
		float yaw = s == null ? pl.getYRot() : s[3] + Mth.wrapDegrees(s[0] - s[3]) * partial;
		float bank = s == null ? 0f : Mth.lerp(partial, s[4], s[1]);
		float pitch = s == null ? 0f : Mth.lerp(partial, s[5], s[2]);
		BlockState block = kit().rideBlock(pl);
		Vec3 at = pl.getPosition(partial).subtract(cam);
		float time = pl.tickCount + partial;
		int light = LevelRenderer.getLightColor(pl.level(), BlockPos.containing(pl.position()));
		light = LightTexture.pack(Math.max(LightTexture.block(light), 11), LightTexture.sky(light));
		float sc = pl.getScale();
		pose.pushPose();
		pose.translate(at.x, at.y + Mth.sin(time * 0.25f) * 0.03f, at.z);
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(bank));
		pose.scale(sc, sc, sc);
		int seed = pl.getId();
		// the slab he stands on, its nose a little raised
		pose.pushPose();
		pose.translate(0, -0.23, 0);
		GeoModels.centred(pose, buffers, light, block, 1.45f, 0.42f, 1.95f);
		pose.popPose();
		pose.pushPose();
		pose.translate(0, -0.16, 0.92);
		pose.mulPose(Axis.XP.rotationDegrees(-16));
		GeoModels.centred(pose, buffers, light, block, 1.1f, 0.3f, 0.5f);
		pose.popPose();
		// the broken underside: a jagged root of earth tapering down where it tore out of the ground
		pose.pushPose();
		pose.translate(0.04, -0.58, -0.05);
		pose.mulPose(Axis.YP.rotationDegrees(7));
		GeoModels.centred(pose, buffers, light, block, 1.15f, 0.36f, 1.5f);
		pose.popPose();
		pose.pushPose();
		pose.translate(-0.06, -0.88, -0.12);
		pose.mulPose(Axis.YP.rotationDegrees(-14));
		GeoModels.centred(pose, buffers, light, block, 0.72f, 0.32f, 0.95f);
		pose.popPose();
		pose.pushPose();
		pose.translate(0.05, -1.12, -0.18);
		pose.mulPose(Axis.YP.rotationDegrees(25));
		GeoModels.centred(pose, buffers, light, block, 0.36f, 0.26f, 0.46f);
		pose.popPose();
		// knobbly edges
		for (int i = 0; i < 4; i++) {
			pose.pushPose();
			float side = i % 2 == 0 ? -1f : 1f;
			pose.translate(side * (0.62 + GeoModels.rnd(seed, i) * 0.08), -0.3 - GeoModels.rnd(seed, 4 + i) * 0.1, (i < 2 ? -0.55 : 0.35) + GeoModels.rnd(seed, 8 + i) * 0.2);
			pose.mulPose(Axis.YP.rotationDegrees(GeoModels.rnd(seed, 12 + i) * 60));
			pose.mulPose(Axis.ZP.rotationDegrees((GeoModels.rnd(seed, 16 + i) - 0.5f) * 40));
			GeoModels.centred(pose, buffers, light, block, 0.34f, 0.3f, 0.38f);
			pose.popPose();
		}
		// the fire: a hot glow under the rock and two roaring jets out of its broken root, streaming back
		VertexConsumer add = PyroDraw.additive(buffers);
		float flick = 0.85f + 0.15f * Mth.sin(time * 2.3f) + 0.08f * Mth.sin(time * 5.7f);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, -0.95, -0.15), 1.05f, PyroDraw.dim(0xFFFF7A20, 0.75f));
		for (int k = -1; k <= 1; k += 2) {
			pose.pushPose();
			pose.translate(k * 0.2, -1.05, -0.2);
			PyroDraw.alignY(pose, new Vec3(k * 0.1, -1.0, -0.75));
			PyroDraw.cone(pose, add, 0.13f, 0.75f * flick, 0xFFFFF4D0, 0xFF000000);
			PyroDraw.cone(pose, add, 0.24f, 1.35f * flick, 0xFFFF7A20, 0xFF000000);
			pose.popPose();
		}
		pose.pushPose();
		pose.translate(0, -1.25, -0.35);
		pose.mulPose(Axis.XP.rotationDegrees(180));
		PyroDraw.fireCross(pose, PyroDraw.fireBuffer(buffers), 0.7f, 0.9f * flick, (int) (time / 2), time * 0.2f);
		pose.popPose();
		pose.popPose();
	}

	/** The Rock Ride's eased velocity on his own client (NaN: not riding). */
	private static final double[] RIDE = { Double.NaN, 0, 0 };

	/**
	 * Flies the local player's rock: along the full look (look up to climb, down to dive), W faster, S slower, Space a little
	 * lift; it eases into the speed and the turns from its own record (not the player's velocity, which gravity and drag
	 * touch after every move), hops walls, and never takes fall damage. The server times the ride and ends it.
	 */
	private static void ride(LocalPlayer me, boolean on) {
		if (!on || me.isPassenger()) {
			RIDE[0] = Double.NaN;
			return;
		}
		if (me.getAbilities().flying) {
			me.getAbilities().flying = false; // (no creative flight on the rock)
		}
		float fwd = me.input.forwardImpulse;
		double speed = fwd > 0.1f ? HeatblastKit.RIDE_BOOST : fwd < -0.1f ? HeatblastKit.RIDE_BRAKE : HeatblastKit.RIDE_SPEED;
		Vec3 want = me.getLookAngle().scale(speed);
		if (me.input.jumping) {
			want = want.add(0, 0.3, 0);
		}
		Vec3 v = me.getDeltaMovement();
		if (Double.isNaN(RIDE[0])) {
			RIDE[0] = v.x;
			RIDE[1] = v.y;
			RIDE[2] = v.z;
		}
		RIDE[0] = Mth.lerp(0.14, RIDE[0], want.x);
		RIDE[1] = Mth.lerp(0.12, RIDE[1], want.y);
		RIDE[2] = Mth.lerp(0.14, RIDE[2], want.z);
		if (me.horizontalCollision) {
			RIDE[1] = Math.max(RIDE[1], 0.42); // a wall: ride up over it instead of grinding to a stop
		}
		if (me.onGround() && RIDE[1] < 0) {
			RIDE[1] = 0; // skimming the ground
		}
		me.setDeltaMovement(RIDE[0], RIDE[1], RIDE[2]);
		me.fallDistance = 0f;
	}

	// ---------------------------------------------------------------- body helpers

	/**
	 * Fire pouring out of a palm along the arm -- the jets' white-hot core and orange cone, a fire sprite and a glow.
	 * {@code side} -1 the right arm, +1 the left (the wide arm's centre line); {@code k} sizes it.
	 */
	private static void palmFire(PoseStack pose, MultiBufferSource buffers, ModelPart arm, float side, float k, float age) {
		float flick = 0.8f + 0.25f * Mth.sin(age * 2.4f + side) + 0.1f * Mth.sin(age * 5.1f);
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.translate(side / 16f, 10f / 16f + 0.01f, 0f);
		VertexConsumer add = PyroDraw.additive(buffers);
		PyroDraw.cone(pose, add, 0.11f * k, 0.6f * k * flick, 0xFFFFF4D0, 0xFF000000);
		PyroDraw.cone(pose, add, 0.2f * k, 1.0f * k * flick, 0xFFFF7A20, 0xFF000000);
		PyroDraw.fireCross(pose, PyroDraw.fireBuffer(buffers), 0.4f * k, 0.7f * k * flick, (int) (age / 2), age * 0.3f);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, 0.12, 0), 0.38f * k, PyroDraw.dim(0xFFFF9040, 0.8f));
		pose.popPose();
	}

	/** A ball of fire held in a palm. */
	private static void handBall(PoseStack pose, MultiBufferSource buffers, ModelPart arm, float side, float r, float age) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.translate(side / 16f, 11f / 16f + r * 0.5f, 0f);
		PyroDraw.fireball(pose, buffers, r, age, new Vec3(0, 1, 0), false);
		pose.popPose();
	}

	/** A soft glow round a fist. */
	private static void handGlow(PoseStack pose, MultiBufferSource buffers, ModelPart arm, float side, float r) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(side / 16f, 10.5 / 16f, 0), r, 0xFFFFA040);
		pose.popPose();
	}

	/** A fireball held in front of the chest ({@code y} down the body, blocks), its tongues licking up. */
	private static void chestBall(PoseStack pose, MultiBufferSource buffers, PlayerModel<AbstractClientPlayer> model, float r, float age, float y) {
		pose.pushPose();
		model.body.translateAndRotate(pose);
		pose.translate(0f, y, -0.3f - r);
		PyroDraw.fireball(pose, buffers, r, age, new Vec3(0, 1, 0), false);
		pose.popPose();
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float age = p.tickCount + partial;
		PartPose was = arm.storePose();
		arm.setPos(0, 0, 0);
		arm.setRotation(0, 0, 0);
		boolean doused = HeatblastKit.doused(p);
		float k = doused ? rekindle(p, age) : 1f;
		if (k > 0.02f) {
			// the burning overlay on the first-person arm too (faint and flickering while he rekindles)
			arm.render(pose, buffers.getBuffer(RenderType.eyes(glow(age))), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, doused ? grey(0.45f * k) : 0xFFFFFFFF);
		}
		if (!doused) {
			var st = Omnitrix.state(p);
			float ticks = animTicks(p, partial);
			float side = right ? -1f : 1f;
			float nova = kit().novaCharge(p, partial);
			if (right && (st.animId == HeatblastKit.ANIM_VOLLEY || st.animId == HeatblastKit.ANIM_VOLLEY_3) && ticks < 5f
					|| !right && st.animId == HeatblastKit.ANIM_VOLLEY_L && ticks < 5f) {
				palmFire(pose, buffers, arm, side, 0.65f * (1f - ticks / 5f) + 0.15f, age);
			} else if (st.animId == HeatblastKit.ANIM_VOLLEY_GATHER) {
				handBall(pose, buffers, arm, side, 0.05f + 0.12f * Mth.clamp(ticks / HeatblastKit.VOLLEY_GATHER, 0f, 1f), age);
			} else if ((st.kitFlags & HeatblastKit.F_SLAM) != 0) {
				handBall(pose, buffers, arm, side, 0.08f + 0.16f * Mth.clamp(ticks / HeatblastKit.SLAM_WINDUP, 0f, 1f), age);
			} else if (st.animId == HeatblastKit.ANIM_SLAM && ticks < 9f) {
				palmFire(pose, buffers, arm, side, 0.9f * (1f - ticks / 9f), age);
			} else if (nova > 0f) {
				palmFire(pose, buffers, arm, side, 0.25f + 0.85f * nova, age);
			} else if (right && st.animId == HeatblastKit.ANIM_BOLT && ticks < 4f) {
				palmFire(pose, buffers, arm, side, 0.45f * (1f - ticks / 4f) + 0.1f, age);
			} else if (right && (st.kitFlags & HeatblastKit.F_RIDE_CHARGE) != 0) {
				palmFire(pose, buffers, arm, side, 0.3f + 0.7f * kit().rideCharge(p), age);
			}
		}
		arm.loadPose(was);
	}

	// ---------------------------------------------------------------- poses

	/** Every column but the legs': a held move leaves the walk cycle's legs alone while he walks. */
	private static final int NO_LEGS;

	static {
		int mask = QuirkKeyframes.bit(QuirkKeyframes.HEAD_X);
		for (int c = QuirkKeyframes.R_ARM_X; c <= QuirkKeyframes.BODY_Y; c++) {
			mask |= QuirkKeyframes.bit(c);
		}
		NO_LEGS = mask;
	}

	/**
	 * A clip that carries on from the one before it: its first frame IS that pose, so there is no fade in from the walk cycle to snap
	 * through ({@code QuirkKeyframes.weight}'s), only the fade out.
	 */
	private static boolean chained(int animId) {
		return switch (animId) {
			case HeatblastKit.ANIM_VOLLEY, HeatblastKit.ANIM_VOLLEY_L, HeatblastKit.ANIM_VOLLEY_3, HeatblastKit.ANIM_BREATH_END, HeatblastKit.ANIM_SLAM, HeatblastKit.ANIM_MAGMA,
					HeatblastKit.ANIM_SUPERNOVA, HeatblastKit.ANIM_METEOR_DIVE, HeatblastKit.ANIM_RIDE_PUNCH, HeatblastKit.ANIM_YANK -> true;
			default -> false;
		};
	}

	/** A held move: the legs stay on the walk cycle while he is moving. */
	private static boolean legsFree(AbstractClientPlayer p, int animId, float ticks) {
		boolean held = animId == HeatblastKit.ANIM_BREATH || animId == HeatblastKit.ANIM_MAGMA_CHARGE || animId == HeatblastKit.ANIM_CHARGE
				|| animId == HeatblastKit.ANIM_VOLLEY_GATHER || animId == HeatblastKit.ANIM_VOLLEY || animId == HeatblastKit.ANIM_VOLLEY_L || animId == HeatblastKit.ANIM_VOLLEY_3;
		return held && p.walkAnimation.speed() > 0.08f;
	}

	/** Plays a keyframe table, its {@link QuirkKeyframes#AIM} arm columns resolved to point down the look. */
	private static boolean playAimed(HumanoidModel<?> m, float[][] clip, float ticks, boolean chained, boolean noLegs) {
		// (the crouching tables -- 17 columns -- have no aimed arms: sampled whole so the crouch column is kept)
		float[] f = clip[0].length > QuirkKeyframes.COLS ? QuirkKeyframes.sample(clip, ticks)
				: QuirkKeyframes.sample(clip, ticks, -Mth.HALF_PI + m.head.xRot, m.head.yRot);
		if (f == null) {
			return false;
		}
		float len = QuirkKeyframes.length(clip);
		float w = chained ? Mth.clamp((len - ticks) / QuirkKeyframes.FADE_OUT, 0f, 1f) : QuirkKeyframes.weight(ticks, len);
		if (noLegs) {
			QuirkKeyframes.applyMasked(m, f, w, NO_LEGS);
			if (f.length > QuirkKeyframes.CROUCH) {
				QuirkKeyframes.crouch(m, w * f[QuirkKeyframes.CROUCH]);
			}
		} else {
			QuirkKeyframes.apply(m, f, w);
		}
		return true;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		float[][] clip = switch (animId) {
			case HeatblastKit.ANIM_VOLLEY_GATHER -> VOLLEY_GATHER;
			case HeatblastKit.ANIM_VOLLEY -> VOLLEY_R;
			case HeatblastKit.ANIM_VOLLEY_L -> VOLLEY_L;
			case HeatblastKit.ANIM_VOLLEY_3 -> VOLLEY_3;
			case HeatblastKit.ANIM_BOLT -> BOLT;
			case HeatblastKit.ANIM_BREATH -> (flags & HeatblastKit.F_STREAM) != 0 ? BREATH : null;
			case HeatblastKit.ANIM_BREATH_END -> BREATH_END;
			case HeatblastKit.ANIM_SLAM_GATHER -> SLAM_GATHER;
			case HeatblastKit.ANIM_SLAM -> SLAM;
			case HeatblastKit.ANIM_MAGMA_CHARGE -> MAGMA_CHARGE;
			case HeatblastKit.ANIM_LASSO -> LASSO;
			case HeatblastKit.ANIM_YANK -> YANK;
			case HeatblastKit.ANIM_MAGMA -> MAGMA;
			case HeatblastKit.ANIM_JUMP -> JUMP;
			case HeatblastKit.ANIM_RIDE_CHARGE -> (flags & HeatblastKit.F_RIDE_CHARGE) != 0 ? RIDE_CHARGE : null;
			case HeatblastKit.ANIM_RIDE_PUNCH -> RIDE_PUNCH;
			case HeatblastKit.ANIM_DASH -> DASH;
			case HeatblastKit.ANIM_TORNADO -> TORNADO;
			case HeatblastKit.ANIM_CHARGE -> CHARGE;
			case HeatblastKit.ANIM_SUPERNOVA, HeatblastKit.ANIM_CRASH -> SUPERNOVA;
			case HeatblastKit.ANIM_PUNCH -> PUNCH;
			case HeatblastKit.ANIM_PUNCH_L -> PUNCH_L;
			case HeatblastKit.ANIM_SMELT -> SMELT;
			case HeatblastKit.ANIM_AURA -> AURA;
			case HeatblastKit.ANIM_ABSORB -> SHIELD;
			case HeatblastKit.ANIM_ERUPT -> ERUPT;
			case HeatblastKit.ANIM_METEOR_RISE -> (flags & HeatblastKit.F_METEOR) != 0 ? METEOR_RISE : null;
			case HeatblastKit.ANIM_METEOR_DIVE -> (flags & HeatblastKit.F_METEOR) != 0 ? METEOR_DIVE : null;
			case HeatblastKit.ANIM_DOUSED -> DOUSED;
			case HeatblastKit.ANIM_REIGNITE -> REIGNITE;
			default -> null;
		};
		// on the rock: the surfer's stance, laid down FIRST so a move used on it blends in and out; the arms counter the bank
		boolean riding = (flags & HeatblastKit.F_RIDING) != 0;
		if (riding) {
			float[] s = ROCK.get(p.getId());
			float bank = s == null ? 0f : s[1] * Mth.DEG_TO_RAD;
			QuirkKeyframes.play(m, SURF, 10f, 1f);
			float sway = Mth.sin(age * 0.21f) * 0.05f;
			m.rightArm.zRot += bank * 0.9f + sway;
			m.leftArm.zRot += bank * 0.9f - sway;
		}
		boolean played = clip != null && playAimed(m, clip, ticks, chained(animId), legsFree(p, animId, ticks));
		if (played) {
			float w = QuirkKeyframes.weight(ticks, QuirkKeyframes.length(clip));
			if (animId == HeatblastKit.ANIM_BREATH && ticks > HeatblastKit.BREATH_INHALE) {
				// the whole chest heaves with the stream of fire
				float heave = Mth.sin(age * 1.9f) * 0.02f + Mth.sin(age * 3.3f) * 0.012f;
				m.head.xRot += heave * w;
				m.rightArm.xRot += heave * 1.5f * w;
				m.leftArm.xRot -= heave * 1.5f * w;
				m.hat.copyFrom(m.head);
			} else if (animId == HeatblastKit.ANIM_MAGMA_CHARGE) {
				// the arms straining under the weight of it
				float k = Mth.clamp(ticks / HeatblastKit.MAGMA_CHARGE, 0f, 1f) * w;
				m.rightArm.zRot += Mth.sin(age * 2.6f) * 0.03f * k;
				m.leftArm.zRot -= Mth.sin(age * 2.6f) * 0.03f * k;
			} else if (animId == HeatblastKit.ANIM_CHARGE || animId == HeatblastKit.ANIM_RIDE_CHARGE) {
				// a tremble that builds with the charge
				float k = (animId == HeatblastKit.ANIM_CHARGE ? Mth.clamp(ticks / HeatblastKit.SUPERNOVA_CHARGE, 0f, 1f) : kit().rideCharge(p)) * w;
				float shake = Mth.sin(age * 3.3f) * 0.06f * k;
				m.rightArm.zRot += shake;
				m.leftArm.zRot -= shake;
				m.rightArm.xRot += Mth.sin(age * 4.1f) * 0.03f * k;
				m.head.xRot += Mth.sin(age * 2.9f) * 0.025f * k;
				m.hat.copyFrom(m.head);
			} else if (animId == HeatblastKit.ANIM_SUPERNOVA && ticks < 14f) {
				float shake = Mth.sin(age * 3.0f) * 0.05f * (1f - ticks / 14f) * w;
				m.rightArm.zRot += shake;
				m.leftArm.zRot -= shake;
			} else if (animId == HeatblastKit.ANIM_DOUSED && ticks > 2f && ticks < 10f) {
				// shaking the water off
				float shake = Mth.sin(age * 4.5f) * 0.12f * w;
				m.rightArm.yRot += shake;
				m.leftArm.yRot -= shake;
				m.head.yRot += shake * 0.6f;
				m.hat.copyFrom(m.head);
			}
		}
		if (!played && HeatblastKit.doused(p)) {
			// put out: a slight slump while he waits
			m.head.xRot += 0.18f;
			m.hat.copyFrom(m.head);
		}
		return played || riding;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case HeatblastKit.ANIM_VOLLEY_GATHER -> FP_GATHER;
			case HeatblastKit.ANIM_VOLLEY, HeatblastKit.ANIM_VOLLEY_L, HeatblastKit.ANIM_VOLLEY_3 -> FP_BLAST;
			case HeatblastKit.ANIM_BOLT -> FP_BOLT;
			case HeatblastKit.ANIM_BREATH -> FP_BREATH;
			case HeatblastKit.ANIM_BREATH_END -> FP_BREATH_END;
			case HeatblastKit.ANIM_SLAM_GATHER -> FP_SLAM_GATHER;
			case HeatblastKit.ANIM_SLAM -> FP_SLAM;
			case HeatblastKit.ANIM_MAGMA_CHARGE -> FP_MAGMA_CHARGE;
			case HeatblastKit.ANIM_LASSO -> FP_LASSO;
			case HeatblastKit.ANIM_YANK -> FP_YANK;
			case HeatblastKit.ANIM_MAGMA -> FP_MAGMA;
			case HeatblastKit.ANIM_JUMP -> FP_JUMP;
			case HeatblastKit.ANIM_RIDE_CHARGE -> FP_RIDE_CHARGE;
			case HeatblastKit.ANIM_RIDE_PUNCH -> FP_RIDE_PUNCH;
			case HeatblastKit.ANIM_DASH -> FP_DASH;
			case HeatblastKit.ANIM_TORNADO -> FP_TORNADO;
			case HeatblastKit.ANIM_CHARGE -> FP_CHARGE;
			case HeatblastKit.ANIM_SUPERNOVA, HeatblastKit.ANIM_CRASH -> FP_SUPERNOVA;
			case HeatblastKit.ANIM_PUNCH, HeatblastKit.ANIM_PUNCH_L -> FP_PUNCH;
			case HeatblastKit.ANIM_SMELT -> FP_CUP;
			case HeatblastKit.ANIM_AURA -> FP_AURA;
			case HeatblastKit.ANIM_ABSORB -> FP_SHIELD;
			case HeatblastKit.ANIM_ERUPT -> FP_ERUPT;
			case HeatblastKit.ANIM_METEOR_RISE -> FP_RISE;
			case HeatblastKit.ANIM_METEOR_DIVE -> FP_DIVE;
			case HeatblastKit.ANIM_DOUSED -> FP_DOUSED;
			case HeatblastKit.ANIM_REIGNITE -> FP_REIGNITE;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- ticks: the ride, the flames, the smoke

	@Override
	public void tick(AbstractClientPlayer p) {
		var level = p.level();
		var r = p.getRandom();
		int flags = Omnitrix.state(p).kitFlags;
		boolean riding = (flags & HeatblastKit.F_RIDING) != 0;
		if (p instanceof LocalPlayer me) {
			ride(me, riding);
		}
		if (riding) {
			steerRock(p);
			// the fire trail streaming off the bottom of the rock
			Vec3 v = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
			Vec3 under = p.position().add(0, -1.15 * p.getScale(), 0);
			for (int i = 0; i < 2; i++) {
				level.addParticle(ParticleTypes.FLAME, under.x + (r.nextDouble() - 0.5) * 0.4, under.y, under.z + (r.nextDouble() - 0.5) * 0.4, -v.x * 0.3,
						-0.08 - r.nextDouble() * 0.05, -v.z * 0.3);
			}
			if (r.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, under.x - v.x, under.y - 0.2, under.z - v.z, 0, 0.01, 0);
			}
		} else {
			ROCK.remove(p.getId());
		}
		double top = p.getY() + p.getBbHeight();
		if (HeatblastKit.doused(p)) {
			// put out: grey smoke curling off his head (steam while still in the water), embers flickering as he rekindles
			if (HeatblastKit.touchingWater(p)) {
				if (r.nextInt(3) == 0) {
					level.addParticle(p.isUnderWater() ? ParticleTypes.BUBBLE : ParticleTypes.CLOUD, p.getX() + (r.nextDouble() - 0.5) * 0.3, top,
							p.getZ() + (r.nextDouble() - 0.5) * 0.3, 0, 0.06, 0);
				}
				return;
			}
			if (r.nextInt(2) == 0) {
				level.addParticle(ParticleTypes.SMOKE, p.getX() + (r.nextDouble() - 0.5) * 0.3, top - 0.1, p.getZ() + (r.nextDouble() - 0.5) * 0.3, 0, 0.035, 0);
			}
			if (r.nextInt(9) == 0) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, p.getX(), top, p.getZ(), 0, 0.02, 0);
			}
			int left = kit().reigniteLeft(p);
			if (left > 0) {
				float k = 1f - left / (float) HeatblastKit.REIGNITE_TICKS;
				if (r.nextFloat() < 0.15f + 0.85f * k) {
					level.addParticle(k > 0.5f ? EMBER : DIM_EMBER, p.getX() + (r.nextDouble() - 0.5) * 0.7, p.getY() + r.nextDouble() * p.getBbHeight(),
							p.getZ() + (r.nextDouble() - 0.5) * 0.7, 0, 0.02, 0);
				}
				if (k > 0.75f && r.nextInt(3) == 0) {
					level.addParticle(ParticleTypes.SMALL_FLAME, p.getX() + (r.nextDouble() - 0.5) * 0.3, top - 0.05, p.getZ() + (r.nextDouble() - 0.5) * 0.3, 0, 0.03,
							0);
				}
			}
			return;
		}
		// flames off the head, embers drifting from the body
		if (r.nextInt(5) == 0) {
			// (the odd spark above the head fire; the blaze itself is the model's)
			level.addParticle(ParticleTypes.FLAME, p.getX() + (r.nextDouble() - 0.5) * 0.15, top + 0.4, p.getZ() + (r.nextDouble() - 0.5) * 0.15, 0, 0.04, 0);
		}
		if (r.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
		}
	}

	// ---------------------------------------------------------------- HUD

	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		HeatblastKit k = kit();
		float nova = k.novaCharge(p, partial);
		if (nova > 0f) {
			OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.heatblast.supernova"), nova, nova >= 0.86f ? 0xFFFFE060 : 0xFFFF7A1A,
					Math.round(nova * 100) + "%", 0xFFFFFFFF);
		} else if (k.flag(p, HeatblastKit.F_RIDE_CHARGE)) {
			float c = k.rideCharge(p);
			OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.heatblast.ride"), c, c >= 1f ? 0xFFFFE060 : 0xFFFF7A1A,
					Math.round(c * 100) + "%", 0xFFFFFFFF);
		} else if (k.flag(p, HeatblastKit.F_RIDING)) {
			int left = k.rideLeft(p);
			OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.heatblast.ride"), left / (float) HeatblastKit.RIDE_TICKS,
					left < 200 ? 0xFFE04030 : 0xFFFF7A1A, (left + 19) / 20 + "s", 0xFFFFFFFF);
		} else if (k.flag(p, HeatblastKit.F_DOUSED)) {
			int left = k.reigniteLeft(p);
			if (k.flag(p, HeatblastKit.F_WET) || left <= 0) {
				OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.heatblast.doused"), 0f, 0xFF5080C0,
						Component.translatable("hud.projecthero.omnitrix.heatblast.wet").getString(), 0xFF80B8FF);
			} else {
				OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.heatblast.reigniting"),
						1f - left / (float) HeatblastKit.REIGNITE_TICKS, 0xFFFF7A1A, (left + 19) / 20 + "s", 0xFFFFFFFF);
			}
		}
	}

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public float[] badge() {
		// over the skin's own symbol (painted out) -- v0.17.2: the second skin paints it higher, at the top of his chest
		// (jacket 23..24 x 37..38, gen_heatblast_v2.js)
		return new float[] { 0f, 2.0f, -2.06f, 3.0f };
	}
}

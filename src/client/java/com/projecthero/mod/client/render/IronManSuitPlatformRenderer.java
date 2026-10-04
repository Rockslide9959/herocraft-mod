package com.projecthero.mod.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * "Hall of Armor" display ("changes 16"): renders the racked suit as the actual worn armour <em>model</em> on an
 * invisible, slowly-rotating armour stand above the platform (vanilla {@code HumanoidArmorLayer}, so the GeckoLib suit
 * models show exactly as on a player). The block entity syncs its contents on every change, so this always reflects
 * what is really stored.
 *
 * <p>v0.14.21: also draws the animated deploy / retrieve. While a sequence runs the rack stops spinning and turns to
 * face the player, and each piece in flight gets its own stand that travels on a little arc between the rack (62%
 * size, rack yaw) and the player's body (full size, their body yaw) -- so on deploy it lands exactly where the player
 * then locks it on, and on retrieve it leaves exactly where it broke away. Timing comes from the synced sequence and
 * {@link IronManSuitPlatformBlockEntity}'s shared timing functions, so every viewer sees the same thing.
 */
public class IronManSuitPlatformRenderer implements BlockEntityRenderer<IronManSuitPlatformBlockEntity> {
	/** One reusable client-only armour stand per platform BE (the rack), plus one per piece in flight. */
	private static final Map<IronManSuitPlatformBlockEntity, ArmorStand[]> STANDS = new WeakHashMap<>();

	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final float RACK_SCALE = 0.62f;
	/** Height the racked suit stands at above the block (master: the raised floor plate of the new platform model). */
	public static final double DISPLAY_Y_OFFSET = 0.2;

	public IronManSuitPlatformRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public boolean shouldRenderOffScreen(IronManSuitPlatformBlockEntity be) {
		return be.sequenceRunning(); // a piece in flight can be well outside the block's own box
	}

	@Override
	public void render(IronManSuitPlatformBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight, int packedOverlay) {
		if (be.getLevel() == null) {
			return;
		}
		ArmorStand[] stands = STANDS.computeIfAbsent(be, k -> makeStands(be));
		if (stands == null) {
			return;
		}
		long gameTime = be.getLevel().getGameTime();
		float time = gameTime + partialTick;
		float spin = time * 1.4f;
		float bob = (float) Math.sin(time * 0.06f) * 0.03f;

		// ---- the running sequence (if any) ----
		int mode = be.seqMode();
		int[] seq = be.seqSlots();
		LivingEntity player = mode != IronManSuitPlatformBlockEntity.SEQ_NONE
				&& be.getLevel().getEntity(be.seqPlayerEntity()) instanceof LivingEntity le ? le : null;
		float t = time - be.seqStart();
		boolean[] inFlight = new boolean[4];
		float[] flight = new float[4];
		boolean[] gone = new boolean[4];
		if (player != null) {
			// the rack turns to face the player while it works
			Vec3 d = player.position().subtract(Vec3.atBottomCenterOf(be.getBlockPos()));
			// pose yaw -Y draws the stand as an entity with yaw Y; the yaw looking along d is atan2(-dx, dz)
			spin = (float) (Mth.atan2(d.x, d.z) * (180.0 / Math.PI));
			for (int i = 0; i < seq.length; i++) {
				int idx = seq[i];
				if (idx < 0 || idx > 3) {
					continue;
				}
				if (mode == IronManSuitPlatformBlockEntity.SEQ_DEPLOY) {
					float lift = IronManSuitPlatformBlockEntity.deployLiftTick(i);
					float equip = IronManSuitPlatformBlockEntity.deployEquipTick(i);
					if (t >= equip) {
						gone[idx] = true; // on the player now (the block update may lag a tick behind)
					} else if (t >= lift) {
						inFlight[idx] = true;
						flight[idx] = (t - lift) / (equip - lift);
					}
				} else {
					float move = IronManSuitPlatformBlockEntity.retrieveMoveTick(i);
					float land = move + IronManSuitPlatformBlockEntity.SEQ_FLIGHT;
					if (t >= move && t < land) {
						inFlight[idx] = true;
						flight[idx] = 1f - (t - move) / (land - move); // 1 = at the player, 0 = on the rack
					}
				}
			}
		}

		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);

		// ---- the rack ----
		ArmorStand rack = stands[4];
		boolean any = false;
		for (int i = 0; i < 4; i++) {
			ItemStack piece = inFlight[i] || gone[i] ? ItemStack.EMPTY : be.getItem(i);
			setIfChanged(rack, SLOTS[i], piece);
			any |= !piece.isEmpty();
		}
		if (any) {
			pose.pushPose();
			pose.translate(0.5, DISPLAY_Y_OFFSET + bob, 0.5);
			pose.mulPose(Axis.YP.rotationDegrees(spin));
			pose.scale(RACK_SCALE, RACK_SCALE, RACK_SCALE);
			dispatcher.render(rack, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
			pose.popPose();
		}

		// ---- pieces in flight ----
		if (player != null) {
			Vec3 toPlayer = player.getPosition(partialTick).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
			float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
			for (int i = 0; i < 4; i++) {
				if (!inFlight[i] || be.getItem(i).isEmpty()) {
					continue;
				}
				ArmorStand s = stands[i];
				for (int j = 0; j < 4; j++) {
					setIfChanged(s, SLOTS[j], j == i ? be.getItem(i) : ItemStack.EMPTY);
				}
				float f = smooth(flight[i]);
				double x = Mth.lerp(f, 0.5, toPlayer.x);
				double y = Mth.lerp(f, DISPLAY_Y_OFFSET + bob, toPlayer.y) + Math.sin(Math.PI * f) * 0.6;
				double z = Mth.lerp(f, 0.5, toPlayer.z);
				float yaw = Mth.rotLerp(f, spin, -bodyYaw);
				float scale = Mth.lerp(f, RACK_SCALE, 1.0f);
				pose.pushPose();
				pose.translate(x, y, z);
				pose.mulPose(Axis.YP.rotationDegrees(yaw));
				pose.scale(scale, scale, scale);
				dispatcher.render(s, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
				pose.popPose();
				if (be.getLevel().random.nextFloat() < 0.35f) {
					Vec3 w = Vec3.atLowerCornerOf(be.getBlockPos()).add(x, y + 0.9 * scale, z);
					be.getLevel().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, 0, 0, 0);
				}
			}
		}
		dispatcher.setRenderShadow(true);
	}

	private static void setIfChanged(ArmorStand stand, EquipmentSlot slot, ItemStack want) {
		if (!ItemStack.matches(stand.getItemBySlot(slot), want)) {
			stand.setItemSlot(slot, want);
		}
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	private static ArmorStand[] makeStands(IronManSuitPlatformBlockEntity be) {
		if (be.getLevel() == null) {
			return null;
		}
		ArmorStand[] out = new ArmorStand[5];
		for (int i = 0; i < out.length; i++) {
			ArmorStand stand = new ArmorStand(be.getLevel(), 0, 0, 0);
			stand.setInvisible(true);      // only the armour shows
			stand.setNoBasePlate(true);
			stand.setShowArms(true);
			stand.setNoGravity(true);
			stand.setYRot(0f);
			stand.yBodyRot = 0f;
			stand.yHeadRot = 0f;
			out[i] = stand;
		}
		return out;
	}
}

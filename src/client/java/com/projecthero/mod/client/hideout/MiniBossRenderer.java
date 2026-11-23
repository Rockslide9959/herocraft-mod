package com.projecthero.mod.client.hideout;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hideout.boss.BroodSpawnBoss;
import com.projecthero.mod.hideout.boss.CryptLordBoss;
import com.projecthero.mod.hideout.boss.MiniBossBase;
import com.projecthero.mod.hideout.boss.OathboundChampionBoss;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.7: the three new hideout mini-bosses on the player model (CLAUDE.md: no new GeckoLib body for a mini-boss) --
 * the Crypt Lord ({@code hideout/crypt_lord}), the Brood Spawn of Carnage ({@code hideout/brood_spawn}, Carnage's wide
 * rig in a darker recolour of his skin) and the Oathbound Champion ({@code hideout/oathbound_champion}). Posed from the
 * synced move ({@link MiniBossBase#action()}); their size comes from the SCALE attribute.
 */
public class MiniBossRenderer<T extends MiniBossBase> extends HumanoidMobRenderer<T, MiniBossRenderer.Model<T>> {
	/** Arm / head pose for a move, {@code t} 0..1 into the wind-up, {@code age} in ticks. */
	@FunctionalInterface
	public interface Pose<T extends MiniBossBase> {
		void apply(Model<T> m, T e, byte action, float t, float age);
	}

	private final ResourceLocation skin;

	public MiniBossRenderer(EntityRendererProvider.Context ctx, String skin, Pose<T> pose) {
		super(ctx, new Model<>(ctx.bakeLayer(ModelLayers.PLAYER), pose), 0.9f);
		this.skin = ProjectHeroMod.id("textures/entity/hideout/" + skin + ".png");
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return skin;
	}

	public static final class Model<T extends MiniBossBase> extends PlayerModel<T> {
		private final Pose<T> pose;

		Model(ModelPart root, Pose<T> pose) {
			super(root, false);
			this.pose = pose;
		}

		@Override
		public void prepareMobModel(T e, float limbSwing, float limbSwingAmount, float partialTick) {
			rightArmPose = e.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
			leftArmPose = HumanoidModel.ArmPose.EMPTY;
			super.prepareMobModel(e, limbSwing, limbSwingAmount, partialTick);
		}

		@Override
		public void setupAnim(T e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
			super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
			float t = Mth.clamp((age - e.actionStartTick) / 8f, 0f, 1f);
			pose.apply(this, e, e.action(), t, age);
			rightSleeve.copyFrom(rightArm);
			leftSleeve.copyFrom(leftArm);
			hat.copyFrom(head);
			jacket.copyFrom(body);
			rightPants.copyFrom(rightLeg);
			leftPants.copyFrom(leftLeg);
		}
	}

	// ---------------------------------------------------------------- the three poses

	public static void cryptLord(Model<CryptLordBoss> m, CryptLordBoss e, byte a, float t, float age) {
		switch (a) {
			case CryptLordBoss.ACTION_REAP -> { // arms drawn back to one side, then swept across
				float sweep = Mth.clamp((age - e.actionStartTick - 10) / 4f, 0f, 1f);
				m.rightArm.xRot = -1.4f;
				m.leftArm.xRot = -1.4f;
				m.rightArm.yRot = Mth.lerp(sweep, -1.0f, 1.0f);
				m.leftArm.yRot = Mth.lerp(sweep, -1.0f, 1.0f);
				m.body.yRot = Mth.lerp(sweep, -0.4f, 0.4f);
			}
			case CryptLordBoss.ACTION_GRASP -> { // hands clawing at the floor
				m.rightArm.xRot = Mth.lerp(t, m.rightArm.xRot, -0.6f);
				m.leftArm.xRot = Mth.lerp(t, m.leftArm.xRot, -0.6f);
				m.rightArm.zRot = 0.5f;
				m.leftArm.zRot = -0.5f;
				m.head.xRot = 0.5f;
				m.crouching = true;
			}
			case CryptLordBoss.ACTION_VOLLEY -> { // right hand thrust out
				m.rightArm.xRot = Mth.lerp(t, m.rightArm.xRot, -1.7f);
				m.leftArm.xRot = -0.3f;
			}
			case CryptLordBoss.ACTION_RAISE -> { // both arms up, head back
				m.rightArm.xRot = Mth.lerp(t, m.rightArm.xRot, -2.8f);
				m.leftArm.xRot = Mth.lerp(t, m.leftArm.xRot, -2.8f);
				m.rightArm.zRot = 0.3f;
				m.leftArm.zRot = -0.3f;
				m.head.xRot = -0.6f;
			}
			default -> { // the dead king's reach
				if (e.isAggressive()) {
					net.minecraft.client.model.AnimationUtils.animateZombieArms(m.leftArm, m.rightArm, true, m.attackTime, age);
				}
			}
		}
	}

	public static void broodSpawn(Model<BroodSpawnBoss> m, BroodSpawnBoss e, byte a, float t, float age) {
		switch (a) {
			case BroodSpawnBoss.ACTION_SPIKES -> { // both fists slammed down
				m.rightArm.xRot = Mth.lerp(t, -2.8f, -0.2f);
				m.leftArm.xRot = Mth.lerp(t, -2.8f, -0.2f);
				m.crouching = t > 0.6f;
			}
			case BroodSpawnBoss.ACTION_VOLLEY -> { // arms flung wide
				m.rightArm.zRot = Mth.lerp(t, 0f, 1.3f);
				m.leftArm.zRot = Mth.lerp(t, 0f, -1.3f);
				m.rightArm.xRot = -0.4f;
				m.leftArm.xRot = -0.4f;
			}
			case BroodSpawnBoss.ACTION_SNARE -> {
				m.rightArm.xRot = Mth.lerp(t, m.rightArm.xRot, -1.6f);
				m.leftArm.xRot = Mth.lerp(t, m.leftArm.xRot, -1.6f);
			}
			case BroodSpawnBoss.ACTION_POUNCE -> {
				m.rightArm.xRot = -2.2f;
				m.leftArm.xRot = -2.2f;
				m.crouching = true;
			}
			case BroodSpawnBoss.ACTION_BROOD -> {
				m.rightArm.xRot = Mth.lerp(t, m.rightArm.xRot, -2.6f);
				m.leftArm.xRot = Mth.lerp(t, m.leftArm.xRot, -2.6f);
				m.rightArm.zRot = 0.4f;
				m.leftArm.zRot = -0.4f;
				m.head.xRot = -0.5f;
				m.crouching = true;
			}
			default -> { // claws out
				m.rightArm.zRot += 0.15f;
				m.leftArm.zRot -= 0.15f;
			}
		}
	}

	public static void champion(Model<OathboundChampionBoss> m, OathboundChampionBoss e, byte a, float t, float age) {
		float since = age - e.actionStartTick;
		switch (a) {
			case OathboundChampionBoss.ACTION_COMBO -> { // three cuts: wind back, slash across
				float cut = (since % 9f) / 9f;
				m.rightArm.xRot = Mth.lerp(cut, -2.4f, -0.4f);
				m.rightArm.yRot = Mth.lerp(cut, 0.6f, -0.6f);
				m.body.yRot = Mth.lerp(cut, 0.3f, -0.3f);
			}
			case OathboundChampionBoss.ACTION_CHARGE -> { // shoulder down, blade forward
				m.rightArm.xRot = -1.4f;
				m.leftArm.xRot = -1.1f;
				m.leftArm.yRot = 0.6f;
				m.body.xRot = 0.35f;
				m.head.xRot = -0.2f;
			}
			case OathboundChampionBoss.ACTION_SLAM -> { // blade raised two-handed, then down
				float down = Mth.clamp((since - 18) / 3f, 0f, 1f);
				m.rightArm.xRot = Mth.lerp(down, Mth.lerp(t, m.rightArm.xRot, -3.0f), -0.5f);
				m.leftArm.xRot = Mth.lerp(down, Mth.lerp(t, m.leftArm.xRot, -3.0f), -0.5f);
				m.rightArm.yRot = -0.3f;
				m.leftArm.yRot = 0.3f;
				m.crouching = down > 0.5f;
			}
			case OathboundChampionBoss.ACTION_RALLY -> { // blade held high
				m.rightArm.xRot = Mth.lerp(t, m.rightArm.xRot, -3.0f);
				m.head.xRot = -0.4f;
			}
			default -> {
			}
		}
	}
}

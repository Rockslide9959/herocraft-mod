# Super Saiyan hair (stashed v0.17.3)

User, 2026-11-13: "Remove the Super Saiyan hair from the mod for now, keep it stashed somewhere."
Taken out of the build on branch `worktree-agent-a22106df4dd557573`; nothing in `src/` references it any more.

| Stashed file | Where it lived |
|---|---|
| `SsjHairModel.java.txt` | `src/client/java/com/projecthero/mod/client/saiyan/hair/SsjHairModel.java` (the mesh: Goku's SSJ locks + toon outline, baked cel colours) |
| `SsjHairRenderer.java.txt` | `src/client/java/com/projecthero/mod/client/saiyan/hair/SsjHairRenderer.java` (draws it on the head, grow-in, sway, `hideHat`) |
| `white.png` | `src/main/resources/assets/projecthero/textures/entity/saiyan/white.png` -- a copy only: the file is still in the build because the monkey tail (`SaiyanTailRenderer`) uses it too |

## Restoring it

1. Copy the two `.java.txt` files back to `src/client/java/com/projecthero/mod/client/saiyan/hair/` and drop the `.txt`.
2. `client/saiyan/SaiyanLayer.java` `render(...)`: add after the aura line
   `com.projecthero.mod.client.saiyan.hair.SsjHairRenderer.render(pose, buffers, light, player, getParentModel(), partialTick, ageInTicks);`
   (the tail call can stay: they don't overlap).
3. `client/mixin/PlayerModelMixin.java`: add back the hat hider (it hid the skin's hat layer under the hair):
   ```java
   /** v0.17.3: a Super Saiyan's hair hides the wearer's own hat layer. */
   @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
   private void projecthero$ssjHairHidesHat(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
           float headPitch, CallbackInfo ci) {
       if (entity instanceof Player player) {
           com.projecthero.mod.client.saiyan.hair.SsjHairRenderer.hideHat(player, (PlayerModel<?>) (Object) this);
       }
   }
   ```
4. `SsjHairRenderer.shows` only draws for `SaiyanForm.SSJ`; since v0.17.3 there is also `SaiyanForm.ULTRA` (Ultra Instinct). If
   the hair comes back for it, use `form != SaiyanForm.BASE` and tint with `form.hairColor` (ULTRA's is silver-black).
5. Compile (`./gradlew compileJava compileClientJava compileGametestJava`) and put the hair line back in the Guidebook
   (`projecthero.guide.saiyan.rage.body`: "...with golden hair and aura").

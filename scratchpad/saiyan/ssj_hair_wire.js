// v0.17.3: wires the SSJ hair PREVIEW renderer into SaiyanLayer and the hat-hiding into PlayerModelMixin (CRLF files).
// Both calls are no-ops unless SsjHairRenderer.PREVIEW is set. Run from the worktree root.
const fs = require('fs');
let f = 'src/client/java/com/projecthero/mod/client/saiyan/SaiyanLayer.java';
let t = fs.readFileSync(f, 'utf8');
const call = 'SaiyanAuraRenderer.render(pose, buffers, player, partialTick, ageInTicks);';
if (!t.includes('SsjHairRenderer')) {
	t = t.replace(call, call + '\r\n\t\t// v0.17.3 PREVIEW: the SSJ hair model (off unless SsjHairRenderer.PREVIEW is set)\r\n'
		+ '\t\tcom.projecthero.mod.client.saiyan.hair.SsjHairRenderer.render(pose, buffers, light, player, getParentModel());');
	fs.writeFileSync(f, t);
}
f = 'src/client/java/com/projecthero/mod/client/mixin/PlayerModelMixin.java';
t = fs.readFileSync(f, 'utf8');
const a = '\t/**\r\n\t * True while an Iron Man helmet';
if (!t.includes(a)) throw new Error('no anchor');
if (!t.includes('ssjHairHidesHat')) {
	t = t.replace(a, '\t/** v0.17.3 PREVIEW: the SSJ hair model hides the wearer\'s own hat layer (off unless SsjHairRenderer.PREVIEW is set). */\r\n'
		+ '\t@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))\r\n'
		+ '\tprivate void projecthero$ssjHairHidesHat(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,\r\n'
		+ '\t\t\tfloat headPitch, CallbackInfo ci) {\r\n'
		+ '\t\tif (entity instanceof Player player) {\r\n'
		+ '\t\t\tcom.projecthero.mod.client.saiyan.hair.SsjHairRenderer.hideHat(player, (PlayerModel<?>) (Object) this);\r\n'
		+ '\t\t}\r\n\t}\r\n\r\n' + a);
	fs.writeFileSync(f, t);
}
console.log('wired');

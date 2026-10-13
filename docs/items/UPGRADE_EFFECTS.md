# Upgrade visual effects

Implemented client-side: fullbright translucent spark/rune particles, seven level palette bands, and tinting of Epic Fight's existing animation trails. No bloom, dynamic light, weapon edge mask, armor aura, or hit-confirm effect is implemented in this phase.

`UpgradeEffectStyle` chooses white (+1..3), cyan (+4..6), violet (+7..9), gold (+10..12), red-gold (+13..14), and pale gold (+15). +0 and invalid levels disable extra particles/tint. Runes start at +7, trail tint at +10. Rates are bounded to one ambient spark per player per 3..12 ticks, with occasional runes. Native particle settings and a 24-block distance limit apply.

Ambient effects approximate the main-hand position in third person; they do not follow the precise weapon tip at idle. Local first-person ambient emission is omitted to avoid obscuring the screen. During Epic Fight attacks, extra particles use the trail's computed endpoint, following its animation. This phase affects main-hand player weapons only. Invisibility, spectator status, and dead players suppress upgrade effects.

`client.UpgradeTrailMixin` targets the installed Epic Fight `AbstractTrailParticle.render` and `tick` methods. It limits itself to `AnimationTrailParticle`, reads the current main-hand stack's upgrade level, and changes only per-particle RGB. Shared TrailInfo, weapon capability, geometry, timing, texture, and gameplay are untouched. Existing trails revert to their original color if the level returns to zero; already-spawned particles finish their short lifetime (12 or 24 ticks). Animations without an existing Epic Fight trail receive ambient particles but no new slash geometry. Other mods' custom trail implementations need in-game compatibility checks.

Particle registration: `registry/ModParticles.java`; provider: `client/particle/UpgradeParticle.java`; ambient emitter: `client/particle/UpgradeEffects.java`. PNG assets are 16x16 RGBA and drawn as simple geometric sprites by `tools/create_upgrade_particle_textures.py`, independent of the concept illustration.

Verify `compileJava`, `processResources`, sprite JSON/PNG references and the pure tier regression check. Compilation cannot confirm mixin application or animation alignment. In-game checks still required: +0/+3/+6/+9/+12/+14/+15, idle/attack, downgrade/reset while a trail exists, invisible players, first/third-person camera, left-handed players, multiplayer distance, particle settings, and Epic Fight/WOM weapon-specific animations. Fullbright particles do not light nearby blocks.

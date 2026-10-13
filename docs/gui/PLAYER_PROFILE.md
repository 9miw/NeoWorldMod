# Player profile

- Use `/profile` to open your own profile. It is a player command and requires no operator permission.
- Press the existing player-actions key (G by default) while targeting another nearby player, then choose ดูโปรไฟล์. G does nothing when no eligible other player is targeted.
- The server validates alive/spectator state, dimension, distance (8 blocks), and the existing request rate limit. Self-targeting is permitted only for PROFILE.
- The profile is a read-only snapshot. อัปเดต requests a fresh server snapshot; it does not subscribe to live updates. Effect times are explicitly shown as of the last update.
- Overview: NeoWorld level/EXP, HP, Epic Fight stamina, main-hand weapon name, attack damage/speed, armor/toughness, movement speed, knockback resistance, armor negation, impact, weight, stun armor, maximum strikes, and stamina regeneration when the player has those attributes.
- Combat details and effects support mouse-wheel scrolling. Stat tooltips show base and total values; they do not claim to separate equipment from buffs.
- Weapon attributes and attack speed use the installed Epic Fight API. These are not final damage for every animation, target, skill, or critical hit. Movement speed is displayed as a raw attribute rather than a percentage.
- No mana, critical chance, guild, title, or skill values are invented. Unsupported attributes are omitted; unavailable stamina is labeled.
- Character preview uses the currently tracked player entity and can be unavailable outside tracking range; it is separate from the server stat snapshot.
- Layout follows `player-profile-mockup-v1.png`: character portrait at left; player identity and horizontal EXP/HP/Stamina bars at right; three tabs; a two-column six-row stat table; teal update button in the footer.
- Runtime frame: `src/main/resources/assets/neoworld/textures/gui/player/profile_frame.png` (1462 x 1076, transparent exterior, opaque charcoal interior, blank title plaque). Built-in ImageGen extracted decorative frame art from the approved mockup; the frame contains no text, stats, player preview or widget states. Texture metadata disables blur and enables clamp.
- Uses NeoGuiStyle colors and shared NeoButton states, including a teal selected tab with gold accents and the shared refresh icon. Dynamic content stays out of textures. Content and hit regions use a 440 x 324 logical layout scaled to fit the screen. Long values fit their allocated width. Detail/effect lists support wheel scrolling, track clicks and thumb dragging; refresh clamps the scroll position when rows disappear.

Validation: `gradlew.bat compileJava` and `gradlew.bat playerProfileRegressionTest` passed for the profile feature. The mockup layout revision passes `compileJava`. Frame dimensions and transparent corner pixels were checked. Codec tests cover identity, fractional values, max-level EXP, absent Epic Fight data, immutable lists and negative/oversized counts. Computer Use did not expose the running Minecraft window, so no in-game screenshot was obtained. In-game rendering, GUI scales, multiplayer rejection paths and dedicated-server startup still require runtime verification.

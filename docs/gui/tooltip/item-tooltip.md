# Shared NeoWorld Item Tooltip

Approved concept: `item-tooltip-approved-mockup.png`. The enlarged concept uses illustrative item art and sample coin/EXP data; it is not a game screenshot or runtime texture.

`client/gui/common/NeoWorldItemTooltip.java` scopes the frame and presentation to the `neoworld` item namespace. It adds an item type, applicable durability/slot information, and real usage instructions. Vanilla attributes, lore, advanced information, existing required-level data and upgrade data remain intact. Unknown items receive a neutral item type without invented usage.

`NeoItemTooltipHeader` renders the current ItemStack at its normal 16-pixel size beside wrapped, styled item-name and type components. Its 12-pixel text spacing allows headroom for Thai marks. Ordinary tooltip lines use Minecraft's existing wrapping and positioning. The Thai font and glyph spacing implementation are unchanged.

The shared frame is `textures/gui/common/item_tooltip_frame.png`, reusing the existing blank fashion shell's silver beveled edges via nine-slice rendering. The center is filled dynamically. Names, icons, values, instructions and state are never baked into the frame. Metadata disables blur and enables clamp.

FashionTooltip now supplies content only, keeping its slot and usage bitmap glyphs and synchronized owned-state feedback. ItemLevelEventHandler and UpgradeTooltip continue supplying their existing rows. CoinItem supplies actual per-item value, count and checked total without duplicate descriptions. ExperienceItem retains its actual EXP range.

Coin and EXP instructions describe automatic collection in Survival mode. Upgrade stones show target ranges +1..+6, +7..+12 and +13..+15; required amounts depend on configured upgrade costs. Protection scroll descriptions follow UpgradeRules. The upgrade table currently receives ordinary block placement instructions; its registration alone does not establish a right-click menu.

Verification: compileJava and processResources passed. Resource references and merged Thai/English labels were checked. In-game rendering has not been verified. Check long Thai names, GUI scales, screen edges, enchanted/upgraded equipment, advanced tooltips, owned fashion, tooltips without a player and non-NeoWorld control items in the game before considering the visual layout verified.

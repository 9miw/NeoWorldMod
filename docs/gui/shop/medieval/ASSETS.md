# Medieval Shop separated assets

Approved art reference: `approved-mockup.png`, copied from `../shop-medieval-adapted-v1.png`. The user approved separate artwork and the new button families. This is an asset delivery, not renderer integration or final layout approval. The reference inventory has compressed rectangular slots; do not copy those proportions into native slot layout.

## Installed pack

Runtime root: `src/main/resources/assets/neoworld/textures/gui/shop/medieval/`.

- `frame.png`: transparent-center wooden outer frame, no internal panels or title.
- `panels/{title,products,details,inventory,section}.png`: separate blank wooden title/section plaques and parchment panels. Section plaque is reused for product/inventory headings.
- `tabs/{normal,hover,pressed,selected,disabled}.png`: 3 tab labels are rendered dynamically using one shared family.
- `buttons/{buy,sell,utility,close}/{normal,hover,pressed,selected,disabled}.png`: green buy, burgundy sell, wooden utility and close backgrounds. Close normal/disabled reuse wood utility art; hover/pressed use red.
- `slots/{normal,hover,pressed,selected,disabled}.png`: one square socket family reused by products, inventory, hotbar and appropriate preview framing.
- `icons/{left,right,close}.png`: existing Obsidian foreground symbols reused, no new glyph registrations. Plus/minus remain dynamic text. Currency glyphs remain the existing catalogued currency resources.

Total: 39 PNGs with sampling metadata and a measured `manifest.json`. Text, items, prices, counts, tab labels and player data are absent from backgrounds.

## Export and verification

Run `python tools/export_shop_medieval_assets.py`. Source art is preserved in `sources/panels.png` and `sources/widgets.png`. Hard alpha threshold is 224. State families are registered to the normal state's size by one nearest-neighbor normalization; square families have square canvases and identical visible bounds. All exports have transparent padding and blur=false/clamp=true metadata. The manifest records crop regions, native widget dimensions, final bounds and hashes.

`export-preview.png` displays the actual exported files on a checkerboard. Visually inspected all components: transparent frame center, blank panels, full title/section cutouts, complete state borders and separate symbols. Export checks passed for 39 PNGs; `gradlew.bat compileJava` passed. No font sources or icon catalog registrations changed.

## Runtime layout requirements

Use 16x16 native item interiors and 18x18 logical socket bounds for all 63 native slots (27 product, 27 inventory, 9 hotbar). Never shrink inventory rows to the rectangular proportions in the reference. Keep native slot coordinates, rendered socket bounds and hitboxes aligned. Use fixed-corner slicing for frames/panels/buttons; preserve title chains, cloth and section silhouettes with aspect-fit or measured segmentation. Measure slice borders and verify readability at actual GUI scale during integration. The generated outer frame has a wide native aspect ratio and must not be stretched as one image to fit the window.

The pack is now referenced by `ShopTextures`/`ShopScreen`. The synchronized `ShopView` state filters offers server-side before pagination: ALL, STORE_SELLS (buy_price>0), STORE_BUYS (sell_price>0). Switching tabs resets page, selection, armed action and operation UUID. The server rejects trading in a direction hidden by the active view. Item data and currency policy remain unchanged.

Runtime logical canvas remains 300x225: tabs `(18+58*i,29,56,15)`, products panel `(18,45,173,86)`, inventory panel `(18,133,173,88)`, details `(199,45,88,176)`. Product native interiors start `(23,60)`; inventory `(23,145)`; hotbar `(23,201)`, all pitch 18. Socket art uses a one-pixel rim around a 16x16 native item. Title chains and board are segmented inside y=0..27; no negative coordinates. Buy/sell are `(205,180,77,18)` and `(205,201,77,18)`; totals remain dynamic. Shop view IDs use menu data slot 15, requiring matching client/server versions.

Verification: new filter tests first failed before implementation; after implementation 126 shop regression checks and 3 shared wallet checkpoint checks passed. Independent resource checks matched all 39 declared sprite dimensions and opaque bounds; all 63 socket rectangles are square, disjoint and clear of section labels. Compilation passed. The currently running client is connected to an external server and has not loaded this compiled revision. No new in-game screenshot or GUI-scale comparison has been performed; runtime visual matching remains unverified. No server deployment or restart was performed.

## Generation provenance

Built-in ImageGen edit mode with the approved local mockup as reference. Panels prompt: separate blank wooden transparent-center frame, hanging title with burgundy cloth, parchment products/details/inventory panels and blank wooden section plaque; no text, items, sockets or controls. Widget prompt: five columns normal/hover/pressed/selected/disabled and six rows tabs, green buy, burgundy sell, square utility, square close and square item sockets; no text or symbols, matching Minecraft pixel wood style. Extraction uses measured rectangles rather than assumed equal generated cells.

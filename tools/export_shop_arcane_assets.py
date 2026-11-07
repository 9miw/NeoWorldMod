"""Export the approved AI-created Arcane source artwork into separate crisp PNG assets.

No drawing/redesign of the source art: crop components, quantize hard-cutout alpha,
pad square sockets/icons without resizing, and record measured source rectangles.
"""
from pathlib import Path
import argparse
import hashlib
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/gui/shop/arcane"
SOURCE = DOCS / "sources"
OUTPUT = ROOT / "src/main/resources/assets/neoworld/textures/gui/shop/arcane"
ALPHA_THRESHOLD = 224
STATES = ("normal", "hover", "selected", "disabled")


def main():
    global DOCS, SOURCE, OUTPUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--theme", choices=("arcane", "obsidian"), default="arcane")
    theme = parser.parse_args().theme
    DOCS = ROOT / "docs/gui/shop" / theme
    SOURCE = DOCS / "sources"
    OUTPUT = ROOT / "src/main/resources/assets/neoworld/textures/gui/shop" / theme
    OUTPUT.mkdir(parents=True, exist_ok=True)
    images = {name: Image.open(SOURCE / f"{name}.png").convert("RGBA")
              for name in ("frame", "panels", "widgets", "icons")}
    manifest = {
        "reference": f"docs/gui/shop/{theme}/approved-mockup.png",
        "stage": "separated assets; not yet integrated into the Shop renderer",
        "alpha_threshold": ALPHA_THRESHOLD,
        "sampling": "nearest",
        "source_dimensions": {name: list(im.size) for name, im in images.items()},
        "assets": {},
        "shared_currency_icons": {
            name: f"neoworld:textures/font/icons/{name}_coin.png"
            for name in ("silver", "diamond")
        },
    }
    previews = []

    def export(name, sheet, roi=None, square=False, sliced=False, white_icon=False):
        original = images[sheet]
        roi = roi or (0, 0, original.width, original.height)
        piece = original.crop(roi)
        if white_icon:
            # Exact bright foreground strokes inside tight reference ROIs, excluding button art.
            rgb = piece.convert("RGB")
            alpha = Image.new("L", piece.size)
            alpha.putdata([255 if min(p)>=185 and max(p)-min(p)<=35 else 0
                           for p in rgb.get_flattened_data()])
            piece = Image.new("RGBA",piece.size,(255,255,255,255))
        else:
            alpha = piece.getchannel("A").point(lambda a: 255 if a >= ALPHA_THRESHOLD else 0)
        bounds = alpha.getbbox()
        if bounds is None:
            raise ValueError(f"Empty component: {name}")
        piece.putalpha(alpha)
        piece = piece.crop(bounds)
        piece.paste((0, 0, 0, 0), mask=piece.getchannel("A").point(lambda a: 255-a))
        source_rect = [roi[0]+bounds[0], roi[1]+bounds[1], piece.width, piece.height]
        padding_x = padding_y = 2
        if square:
            side = max(piece.size)+4
            padding_x, padding_y = (side-piece.width)//2, (side-piece.height)//2
            size = (side, side)
        else:
            size = (piece.width+4, piece.height+4)
        result = Image.new("RGBA", size)
        result.paste(piece, (padding_x, padding_y))
        path = OUTPUT / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        result.save(path)
        path.with_suffix(".png.mcmeta").write_text(
            json.dumps({"texture": {"blur": False, "clamp": True}})+"\n", encoding="utf-8")
        measured = result.getchannel("A").getbbox()
        alpha_values = {value for value, count in enumerate(result.getchannel("A").histogram()) if count}
        assert alpha_values == {0, 255}, (name, alpha_values)
        assert result.getpixel((0, 0))[3] == 0
        if square:
            assert result.width == result.height
        entry = {
            "file": path.relative_to(ROOT).as_posix(),
            "size": list(result.size),
            "source_sheet": f"docs/gui/shop/{theme}/sources/{sheet}.png",
            "source_rect_xywh": source_rect,
            "opaque_bounds_xyxy": list(measured),
            "padding_xy": [padding_x, padding_y],
            "alpha_levels": sorted(alpha_values),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "render": "nine_slice" if sliced else "preserve_aspect",
        }
        if sliced:
            # Borders apply to the opaque source region, excluding transparent padding.
            entry["suggested_slice_xy"] = [min(24, piece.width//3), min(24, piece.height//3)]
        manifest["assets"][name] = entry
        previews.append((name, result))

    export("frame", "frame")
    panel_top = 270 if theme == "obsidian" else 220
    export("panels/title", "panels", (240, 0, 1300, panel_top))
    export("panels/products", "panels", (0, panel_top, 995 if theme == "obsidian" else 1000, 715 if theme == "obsidian" else 710))
    export("panels/details", "panels", (995 if theme == "obsidian" else 1005, panel_top, 1536, 715 if theme == "obsidian" else 710))
    export("panels/inventory", "panels", (0, 715, 1536, 1024))

    rows = ((35, 330), (350, 515), (530, 715), (730, 990))
    columns = ((0, 405), (405, 775), (775, 1140), (1140, 1536))
    if theme == "obsidian":
        columns = ((0, 405), (405, 775), (775, 1125), (1125, 1536))
    groups = ("slots", "buttons/standard", "buttons/primary", "buttons/close")
    for row, (group, (y1, y2)) in enumerate(zip(groups, rows)):
        state_hashes = set()
        for state, (x1, x2) in zip(STATES, columns):
            name = f"{group}/{state}"
            export(name, "widgets", (x1, y1, x2, y2), square=row in (0, 3), sliced=True)
            state_hashes.add(manifest["assets"][name]["sha256"])
        assert len(state_hashes) == 4, f"Duplicate states in {group}"

    icon_sheet = images["icons"]
    reference_icons = ((393,536,419,570),(645,536,674,570),(1307,125,1340,159))
    for i, name in enumerate(("left", "right", "close")):
        roi = reference_icons[i] if theme == "obsidian" else (i*icon_sheet.width//3,0,(i+1)*icon_sheet.width//3,icon_sheet.height)
        export(f"icons/{name}", "icons", roi, square=True, white_icon=theme == "obsidian")

    # Normalize registration and visible control geometry instead of stretching each state differently at runtime.
    def normalize(names, resize, square=False):
        pieces = []
        for name in names:
            image = Image.open(OUTPUT / f"{name}.png").convert("RGBA")
            pieces.append(image.crop(image.getchannel("A").getbbox()))
        if resize:
            target = pieces[0].size
        else:
            target = (max(p.width for p in pieces),max(p.height for p in pieces))
        if square:
            target = (max(target),max(target))
        for name,piece in zip(names,pieces):
            original_size = piece.size
            if resize and piece.size != target:
                piece = piece.resize(target,Image.Resampling.NEAREST)
            offset = (2+(target[0]-piece.width)//2,2+(target[1]-piece.height)//2)
            image = Image.new("RGBA",(target[0]+4,target[1]+4))
            image.paste(piece,offset)
            path = OUTPUT / f"{name}.png"
            image.save(path)
            entry = manifest["assets"][name]
            entry.update(size=list(image.size),opaque_bounds_xyxy=list(image.getchannel("A").getbbox()),
                         padding_xy=list(offset),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                         normalized_from=list(original_size),normalization="nearest_once" if resize else "padding_only")
            if resize:
                assert image.getchannel("A").getbbox() == (2,2,target[0]+2,target[1]+2)
            for index,(preview_name,_) in enumerate(previews):
                if preview_name == name:
                    previews[index]=(name,image)
                    break
        assert len({tuple(manifest["assets"][name]["size"]) for name in names}) == 1

    for group in groups:
        normalize([f"{group}/{state}" for state in STATES],resize=True,square=group in ("slots","buttons/close"))
    normalize([f"icons/{name}" for name in ("left","right","close")],resize=False,square=True)

    assert len(manifest["assets"]) == 24
    manifest_path = OUTPUT / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")

    # QA contact sheet: actual exported assets on a checkerboard, not a concept mockup.
    cell_w, cell_h, cols = 320, 240, 4
    sheet = Image.new("RGB", (cols*cell_w, ((len(previews)+cols-1)//cols)*cell_h), (32, 36, 44))
    draw = ImageDraw.Draw(sheet)
    for i, (name, artwork) in enumerate(previews):
        ox, oy = i % cols * cell_w, i // cols * cell_h
        for y in range(24, cell_h, 12):
            for x in range(0, cell_w, 12):
                color = (56, 61, 71) if ((x//12)+(y//12))%2 else (78, 84, 96)
                draw.rectangle((ox+x, oy+y, ox+x+11, oy+y+11), fill=color)
        draw.text((ox+8, oy+6), f"{name}  {artwork.width}x{artwork.height}", fill="white")
        ratio = min((cell_w-20)/artwork.width, (cell_h-42)/artwork.height)
        thumb = artwork.resize((max(1, round(artwork.width*ratio)),
                                max(1, round(artwork.height*ratio))), Image.Resampling.NEAREST)
        x, y = ox+(cell_w-thumb.width)//2, oy+30+(cell_h-36-thumb.height)//2
        sheet.paste(thumb, (x, y), thumb)
    sheet.save(DOCS / "export-preview.png")
    print(f"Exported and validated {len(manifest['assets'])} separate {theme} PNGs: binary alpha, aligned state geometry, square sockets/icons, metadata and measured bounds.")


if __name__ == "__main__":
    main()

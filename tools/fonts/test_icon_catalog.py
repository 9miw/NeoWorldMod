import json
import tempfile
import unittest
from pathlib import Path
from PIL import Image
import icon_catalog


class IconCatalogTest(unittest.TestCase):
    def fixture(self, root, providers):
        base = root / 'src/main/resources/assets/demo'
        (base / 'font').mkdir(parents=True)
        (base / 'textures').mkdir()
        Image.new('RGBA', (8, 4), 'white').save(base / 'textures/icons.png')
        (base / 'font/icons.json').write_text(json.dumps({'providers': providers}))

    def test_grid_cells_and_zero_padding(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, [dict(type='bitmap', file='demo:icons.png', height=4, ascent=3,
                                     chars=['\ue001\u0000'])])
            entries = icon_catalog.read_font(root, 'demo:icons')
            self.assertEqual(len(entries), 1)
            self.assertEqual(entries[0]['source_rect'], [0, 0, 4, 4])

    def test_missing_image_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, [dict(type='bitmap', file='demo:missing.png', height=4, ascent=3,
                                     chars=['\ue001'])])
            with self.assertRaisesRegex(ValueError, 'Missing image'):
                icon_catalog.read_font(root, 'demo:icons')

    def test_duplicate_codepoint_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            provider = dict(type='bitmap', file='demo:icons.png', height=4, ascent=3, chars=['\ue001'])
            self.fixture(root, [provider, provider])
            with self.assertRaisesRegex(ValueError, 'Duplicate glyph'):
                icon_catalog.read_font(root, 'demo:icons')

    def test_reference_cycle_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, [dict(type='reference', id='demo:icons')])
            with self.assertRaisesRegex(ValueError, 'cycle'):
                icon_catalog.read_font(root, 'demo:icons')

    def test_generated_names_keep_font_variants_distinct(self):
        base = dict(name='member', codepoint='U+E101', height=16, ascent=13)
        data = {'entries': [dict(base, category='gui_icons', font_id='neoworld:gui_icons'),
                            dict(base, category='dungeon_hud', font_id='neoworld:dungeon_hud')]}
        generated = icon_catalog.java_registry(data).decode('utf-8')
        self.assertIn('GUI_ICONS_MEMBER("neoworld:gui_icons", 0xE101', generated)
        self.assertIn('DUNGEON_HUD_MEMBER("neoworld:dungeon_hud", 0xE101', generated)
        data['entries'].append(data['entries'][0])
        with self.assertRaisesRegex(ValueError, 'Duplicate Java icon name'):
            icon_catalog.java_registry(data)


if __name__ == '__main__':
    unittest.main()

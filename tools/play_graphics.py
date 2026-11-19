"""Draws the Google Play graphics into fastlane/metadata/android/<locale>/images.

    python tools/play_graphics.py

- icon.png: 512 x 512, 32-bit PNG. The adaptive icon's visible 72 dp area (18..90 of the 108 dp canvas) fills the
  square, the same symbol and color as the launcher icon. Google Play adds the rounded corners and the shadow itself.
- featureGraphic.png: 1024 x 500, 24-bit PNG (no alpha), in English and Turkish.
- phoneScreenshots/1.png, 2.png: 1080 x 1920, 24-bit PNG, a caption above a screenshot from docs/. Play accepts a long
  side of at most twice the short side, so the phone's own screenshots (about 1:2.1) are cut at the bottom.

Each page is drawn by headless Chrome (no window on the screen, its own temporary profile) and saved with Pillow in the
format Play expects. The texts use Roboto from Google Fonts, so the script needs the internet.
"""

import html
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "fastlane" / "metadata" / "android"
CHROME_CANDIDATES = [
    Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
    Path(r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"),
    Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
]

# The same paths as res/drawable/ic_launcher_foreground.xml (108 unit canvas).
SYMBOL = (
    '<path fill="#fff" fill-rule="evenodd" d="M44,28H64A3,3 0 0,1 67,31V44H41V31A3,3 0 0,1 44,28Z '
    'M45,33H51V37H45Z M57,33H63V37H57Z"/>'
    '<path fill="#fff" d="M38,48H70A6,6 0 0,1 76,54V60C76,71 67,78 54,82C41,78 32,71 32,60V54A6,6 0 0,1 38,48Z"/>'
)
BRAND = "#E0502A"
# Samsung's launcher mask (One UI), in a 100 unit square. Used only where the icon is shown inside a picture.
SAMSUNG_MASK = "M99,50C99,6.935,77.063,1,50,1C22.935,1,1,6.935,1,50c0,43.063,21.935,49,49,49C77.063,99,99,93.063,99,50z"

TEXTS = {
    "en-US": {
        "tagline": "Reminds you of debugging settings left on",
        "rows": [("USB debugging", "Developer options", "On", "risk"),
                 ("Wireless debugging", "Developer options", "Off", "safe")],
        "shots": [("docs/screen-main.png", "Debugging left on?<br>Watchdog reminds you."),
                  ("docs/screen-language.png", "English or Turkish,<br>chosen in the app.")],
    },
    "tr-TR": {
        "tagline": "Açık unutulan hata ayıklama ayarlarını hatırlatır",
        "rows": [("USB hata ayıklama", "Geliştirici seçenekleri", "Açık", "risk"),
                 ("Kablosuz hata ayıklama", "Geliştirici seçenekleri", "Kapalı", "safe")],
        "shots": [("docs/screen-main-tr.png", "Hata ayıklama açık mı kaldı?<br>Watchdog hatırlatır."),
                  ("docs/screen-language-tr.png", "Türkçe ya da İngilizce,<br>uygulamanın içinden seçilir.")],
    },
}

HEAD = """<!doctype html><meta charset="utf-8">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500&display=block" rel="stylesheet">
<style>
  html, body { margin: 0; padding: 0; overflow: hidden; }
  body { width: %(w)dpx; height: %(h)dpx; background: #121314; color: #ECEDEE;
         font-family: Roboto, sans-serif; -webkit-font-smoothing: antialiased; }
</style>"""


def chrome() -> Path:
    for candidate in CHROME_CANDIDATES:
        if candidate.is_file():
            return candidate
    sys.exit("Chrome or Edge was not found.")


def render(page: str, width: int, height: int, target: Path, mode: str) -> None:
    """Draws the page at exactly width x height and saves it as RGB or RGBA PNG."""
    with tempfile.TemporaryDirectory() as temp:
        temp_path = Path(temp)
        source = temp_path / "page.html"
        source.write_text(page, encoding="utf-8")
        shot = temp_path / "shot.png"
        subprocess.run([
            str(chrome()), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
            f"--user-data-dir={temp_path / 'profile'}", "--force-device-scale-factor=1",
            "--allow-file-access-from-files", "--virtual-time-budget=10000",
            f"--window-size={width},{height}", f"--screenshot={shot}", source.as_uri(),
        ], check=True, capture_output=True, timeout=120)
        image = Image.open(shot)
        if image.size != (width, height):
            sys.exit(f"{target.name}: Chrome drew {image.size}, expected {(width, height)}.")
        target.parent.mkdir(parents=True, exist_ok=True)
        image.convert(mode).save(target, optimize=True)
        print(f"{target.relative_to(ROOT)}  {width}x{height} {mode}")


def icon_page() -> str:
    return (HEAD % {"w": 512, "h": 512}) + (
        f'<svg width="512" height="512" viewBox="18 18 72 72" style="display:block">'
        f'<rect x="0" y="0" width="108" height="108" fill="{BRAND}"/>{SYMBOL}</svg>')


def masked_icon(size: int) -> str:
    """The launcher icon as it looks on a Samsung phone, for use inside a picture."""
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 100 100" style="display:block">'
            f'<defs><clipPath id="m"><path d="{SAMSUNG_MASK}"/></clipPath></defs>'
            f'<g clip-path="url(#m)"><svg x="0" y="0" width="100" height="100" viewBox="18 18 72 72">'
            f'<rect x="0" y="0" width="108" height="108" fill="{BRAND}"/>{SYMBOL}</svg></g></svg>')


def feature_page(texts: dict) -> str:
    rows = "".join(
        f'<div class="row"><div class="names"><div class="name">{html.escape(name)}</div>'
        f'<div class="where">{html.escape(where)}</div></div>'
        f'<div class="value {kind}"><span class="lamp"></span>{html.escape(value)}</div></div>'
        for name, where, value, kind in texts["rows"])
    style = """<style>
      .wrap { position: absolute; inset: 0; display: flex; align-items: center; gap: 48px; padding: 0 64px; }
      .brand { flex: 1 1 0; }
      .title { font-size: 76px; font-weight: 500; letter-spacing: -0.5px; margin: 28px 0 12px; }
      .tagline { font-size: 30px; line-height: 40px; color: #A9ABAF; max-width: 400px; }
      .card { width: 468px; background: #1B1C1E; border-radius: 28px; padding: 4px 0; }
      .row { display: flex; align-items: center; justify-content: space-between; padding: 22px 28px; }
      .row + .row { border-top: 1px solid #2A2C2F; margin: 0 0 0 28px; padding-left: 0; }
      .name { font-size: 25px; white-space: nowrap; }
      .where { font-size: 19px; color: #7D8085; margin-top: 6px; }
      .value { font-size: 25px; display: flex; align-items: center; gap: 12px; white-space: nowrap; }
      .lamp { width: 12px; height: 12px; border-radius: 50%; }
      .risk { color: #EDB45E; } .risk .lamp { background: #E9A94B; }
      .safe { color: #A9ABAF; } .safe .lamp { background: #5FBF8C; }
    </style>"""
    return (HEAD % {"w": 1024, "h": 500}) + style + (
        f'<div class="wrap"><div class="brand">{masked_icon(112)}<div class="title">Watchdog</div>'
        f'<div class="tagline">{html.escape(texts["tagline"])}</div></div>'
        f'<div class="card">{rows}</div></div>')


def screenshot_page(image: Path, caption: str) -> str:
    style = """<style>
      .caption { position: absolute; top: 0; left: 0; right: 0; height: 360px; display: flex; align-items: center;
                 justify-content: center; text-align: center; font-size: 60px; line-height: 76px; font-weight: 500;
                 letter-spacing: -0.3px; }
      .screen { position: absolute; top: 360px; left: 96px; width: 888px; height: 1600px; overflow: hidden;
                border-radius: 44px 44px 0 0; border: 2px solid #3B3D41; border-bottom: none; box-sizing: border-box; }
      .screen img { width: 100%; display: block; }
    </style>"""
    return (HEAD % {"w": 1080, "h": 1920}) + style + (
        f'<div class="caption">{caption}</div><div class="screen"><img src="{image.as_uri()}"></div>')


def main() -> None:
    render(icon_page(), 512, 512, OUT / "en-US" / "images" / "icon.png", "RGBA")
    for locale, texts in TEXTS.items():
        images = OUT / locale / "images"
        render(feature_page(texts), 1024, 500, images / "featureGraphic.png", "RGB")
        shots = images / "phoneScreenshots"
        if shots.exists():
            shutil.rmtree(shots)
        for number, (source, caption) in enumerate(texts["shots"], start=1):
            render(screenshot_page(ROOT / source, caption), 1080, 1920, shots / f"{number}.png", "RGB")


if __name__ == "__main__":
    main()

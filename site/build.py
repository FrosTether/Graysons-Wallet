#!/usr/bin/env python3
"""Builds the Get Qoin page.

    python3 site/build.py [out-dir] [page-url]

Writes two versions into out-dir (default site/out):
  pages/     the hosted page: index.html with the fonts and og.png beside it
  preview/   getqoin.html, the same page as a body fragment that loads its fonts from Google Fonts

page-url is the address the page is served at, for link previews
(default https://get.finux.tech/, which also writes the CNAME file GitHub Pages needs). og.png comes from tools/og.js, which needs Playwright.
"""
import html
import pathlib
import shutil
import subprocess
import sys

SITE = pathlib.Path(__file__).resolve().parent
REPO = SITE.parent
APK_URL = "https://github.com/FrosTether/Graysons-Wallet/releases/latest/download/GraysonsWallet.apk"
RELEASE_URL = "https://github.com/FrosTether/Graysons-Wallet/releases/latest"
TITLE = "Get Qoin | finux"
DESCRIPTION = ("Install Graysons Wallet and mine Qoin on your Android phone, only while it hears a tone. "
               "Mining is open.")
GOOGLE_FONTS = ("https://fonts.googleapis.com/css2?family=Archivo:wdth,wght@62..125,100..900"
                "&family=Martian+Mono:wdth,wght@75..112.5,100..800&display=swap")
ICON = ("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E"
        "%3Ccircle cx='16' cy='16' r='15' fill='%23150e1a'/%3E%3Ccircle cx='16' cy='16' r='8' fill='%23ffb547'/%3E%3C/svg%3E")

FONT_FACES = """@font-face { font-family: "Archivo"; src: url("fonts/archivo.woff") format("woff");
  font-weight: 100 900; font-stretch: 62% 125%; font-display: swap; }
@font-face { font-family: "Martian Mono"; src: url("fonts/martian-mono.woff") format("woff");
  font-weight: 100 800; font-stretch: 75% 112.5%; font-display: swap; }
"""


def qr_svg(text):
    qr_js = REPO / "app/src/main/assets/ui/qr.js"
    out = subprocess.run(["node", str(SITE / "tools/qrsvg.js"), str(qr_js), text],
                         check=True, capture_output=True, text=True)
    return out.stdout


def body():
    b = (SITE / "getqoin/body.html").read_text()
    b = b.replace("{{APK_URL}}", APK_URL).replace("{{RELEASE_URL}}", RELEASE_URL)
    b = b.replace("{{QR_SVG}}", qr_svg(APK_URL))
    assert "{{" not in b, "unfilled placeholder"
    return b


def main():
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else SITE / "out"
    page_url = sys.argv[2] if len(sys.argv) > 2 else "https://get.finux.tech/"
    css = (SITE / "getqoin/style.css").read_text()
    js = (SITE / "getqoin/script.js").read_text()
    content = body()

    pages = out / "pages"
    if pages.exists():
        shutil.rmtree(pages)
    (pages / "fonts").mkdir(parents=True)
    for f in (SITE / "fonts").iterdir():
        shutil.copy(f, pages / "fonts" / f.name)
    meta = f"""<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{html.escape(TITLE)}</title>
<meta name="description" content="{html.escape(DESCRIPTION)}">
<meta name="theme-color" content="#f5f0fa" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#150e1a" media="(prefers-color-scheme: dark)">
<meta property="og:type" content="website">
<meta property="og:title" content="Get Qoin">
<meta property="og:description" content="{html.escape(DESCRIPTION)}">
<meta property="og:url" content="{page_url}">
<meta property="og:image" content="{page_url}og.png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="Get Qoin. Three mining tones: 4.0, 7.83 and 11.11 Hz.">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="{ICON}">
<link rel="preload" href="fonts/archivo.woff" as="font" type="font/woff" crossorigin>"""
    (pages / "index.html").write_text(
        f"<!doctype html>\n<html lang=\"en\">\n<head>\n{meta}\n<style>\n{FONT_FACES}{css}</style>\n</head>\n"
        f"<body>\n{content}<script>\n{js}</script>\n</body>\n</html>\n")
    (pages / ".nojekyll").write_text("")
    host = page_url.split("/")[2]
    if not host.endswith("github.io"):
        (pages / "CNAME").write_text(host + "\n")
    try:
        subprocess.run(["node", str(SITE / "tools/og.js"), str(pages / "og.png")], check=True, timeout=120)
    except (OSError, subprocess.SubprocessError) as e:
        print("og.png not made (needs Playwright):", e)

    preview = out / "preview"
    preview.mkdir(parents=True, exist_ok=True)
    (preview / "getqoin.html").write_text(
        f"<title>Get Qoin</title>\n<link rel=\"stylesheet\" href=\"{GOOGLE_FONTS}\">\n"
        f"<style>\n{css}</style>\n{content}<script>\n{js}</script>\n")
    print("built", pages / "index.html", "and", preview / "getqoin.html")


if __name__ == "__main__":
    main()

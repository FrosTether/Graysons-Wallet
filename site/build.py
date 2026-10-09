#!/usr/bin/env python3
"""Builds the Qoin site: Get Qoin, Run a node, QNR vs XMR and For developers.

    python3 site/build.py [out-dir] [site-url]

Writes out-dir/pages (default site/out/pages): one folder per page, the fonts, the link-preview images, the
node installer and, for a custom domain, the CNAME file GitHub Pages needs. site-url is where it's served
(default https://get.finux.tech/). The preview images come from tools/og.js, which needs Playwright.
"""
import hashlib
import html
import pathlib
import shutil
import subprocess
import sys

SITE = pathlib.Path(__file__).resolve().parent
REPO = SITE.parent
RELEASE = "https://github.com/FrosTether/Graysons-Wallet/releases/latest"
APK_URL = RELEASE + "/download/GraysonsVault.apk"
NODE_URL = RELEASE + "/download/frostnode.zip"
ICON = ("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E"
        "%3Ccircle cx='16' cy='16' r='15' fill='%23150e1a'/%3E%3Ccircle cx='16' cy='16' r='8' fill='%23ffb547'/%3E%3C/svg%3E")
FONT_FACES = """@font-face { font-family: "Archivo"; src: url("/fonts/archivo.woff") format("woff");
  font-weight: 100 900; font-stretch: 62% 125%; font-display: swap; }
@font-face { font-family: "Martian Mono"; src: url("/fonts/martian-mono.woff") format("woff");
  font-weight: 100 800; font-stretch: 75% 112.5%; font-display: swap; }
"""

PAGES = [
    dict(slug="", folder="getqoin", nav="Get Qoin", title="Get Qoin | finux",
         description="Install Graysons Vault and mine Qoin (QNR) on your Android phone, only while it hears a tone. Mining is open.",
         og="home", og_query="t=Get Qoin&l=Mined on phones, only while they hear a tone.",
         og_alt="Get Qoin. Three mining tones: 4.0, 7.83 and 11.11 Hz."),
    dict(slug="node", folder="node", nav="Nodes", title="Run a node | Qoin",
         description="Keep Frostchain online while phones sleep: run frostnode on a server, a laptop or a spare phone, "
                     "and show the chain live in FrostExplorer.",
         og="node", og_query="t=Run a node&l=Keep the chain online while phones sleep.&lamps=0"
                            "&f=One command on a server. FrostExplorer built in.",
         og_alt="Run a node: keep the chain online while phones sleep."),
    dict(slug="qnr", folder="qnr", nav="QNR vs XMR", title="QNR vs XMR | Qoin",
         description="How Qoin (QNR) compares with Monero (XMR), and what it could take next from Monero, Zcash and Dash.",
         og="qnr", og_query="t=QNR vs XMR&l=A Monero-sized supply, quantum-safe keys and tone mining.&lamps=0"
                           "&f=Side by side, as of 8 October 2026.",
         og_alt="QNR vs XMR, side by side."),
    dict(slug="dev", folder="dev", nav="Developers", title="For developers | Qoin",
         description="Build frostnode from source, run a Frostchain test network on one machine, use the node's HTTP API "
                     "and keep a public node healthy.",
         og="dev", og_query="t=For developers&l=Build, run and script Frostchain nodes.&lamps=0"
                           "&f=Open source, GPL-3.0. Plain HTTP and JSON.",
         og_alt="For developers: build, run and script Frostchain nodes."),
]


def qr_svg(text):
    qr_js = REPO / "app/src/main/assets/ui/qr.js"
    out = subprocess.run(["node", str(SITE / "tools/qrsvg.js"), str(qr_js), text],
                         check=True, capture_output=True, text=True)
    return out.stdout


def header(active):
    links = "\n".join(
        f'      <a href="/{p["slug"] + "/" if p["slug"] else ""}"'
        + (' aria-current="page"' if p["slug"] == active else "") + f'>{p["nav"]}</a>'
        for p in PAGES)
    return f"""  <header class="top">
    <a class="brand" href="/">Qoin <span class="tick mono">QNR</span></a>
    <nav aria-label="Qoin">
{links}
    </nav>
  </header>"""


FOOTER = """  <footer>
    <a href="https://finux.tech/">finux.tech</a>
    <a href="https://finux.tech/projects">Projects</a>
    <a href="https://finux.tech/contact">Contact</a>
    <a href="https://github.com/FrosTether/Graysons-Wallet">Source and issues</a>
    <a href="https://github.com/FrosTether">GitHub @FrosTether</a>
  </footer>"""


def fill(text, values):
    for key, value in values.items():
        text = text.replace("{{" + key + "}}", value)
    assert "{{" not in text, "unfilled placeholder"
    return text


def page_html(p, site_url, values, base_css, site_js):
    folder = SITE / p["folder"]
    body = fill((folder / "body.html").read_text(), values)
    css = (folder / "style.css").read_text() if (folder / "style.css").exists() else ""
    js = fill((folder / "script.js").read_text(), values) if (folder / "script.js").exists() else ""
    url = site_url + (p["slug"] + "/" if p["slug"] else "")
    image = site_url + "og/" + p["og"] + ".png"
    meta = f"""<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{html.escape(p["title"])}</title>
<meta name="description" content="{html.escape(p["description"])}">
<link rel="canonical" href="{url}">
<meta name="theme-color" content="#f5f0fa" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#150e1a" media="(prefers-color-scheme: dark)">
<meta property="og:type" content="website">
<meta property="og:title" content="{html.escape(p["title"].split(" | ")[0])}">
<meta property="og:description" content="{html.escape(p["description"])}">
<meta property="og:url" content="{url}">
<meta property="og:image" content="{image}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="{html.escape(p["og_alt"])}">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="{ICON}">
<link rel="preload" href="/fonts/archivo.woff" as="font" type="font/woff" crossorigin>"""
    extra = "".join(f"<script>\n{s.read_text()}</script>\n" for s in p.get("scripts", []))
    page_js = f"<script>\n{js}</script>\n" if js else ""
    return (f"<!doctype html>\n<html lang=\"en\">\n<head>\n{meta}\n<style>\n{FONT_FACES}{base_css}{css}</style>\n</head>\n"
            f"<body>\n<div class=\"wrap\">\n{header(p['slug'])}\n  <main>\n{body}  </main>\n{FOOTER}\n</div>\n"
            f"<script>\n{site_js}</script>\n{extra}{page_js}</body>\n</html>\n")


def main():
    out = (pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else SITE / "out") / "pages"
    site_url = sys.argv[2] if len(sys.argv) > 2 else "https://get.finux.tech/"
    if not site_url.endswith("/"):
        site_url += "/"
    values = {"APK_URL": APK_URL, "RELEASE_URL": RELEASE, "NODE_URL": NODE_URL,
              "QR_SVG": qr_svg(APK_URL)}
    base_css = (SITE / "common/base.css").read_text()
    site_js = (SITE / "common/site.js").read_text()

    if out.exists():
        shutil.rmtree(out)
    (out / "fonts").mkdir(parents=True)
    for f in (SITE / "fonts").iterdir():
        shutil.copy(f, out / "fonts" / f.name)
    for p in PAGES:
        target = out / p["slug"] / "index.html" if p["slug"] else out / "index.html"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(page_html(p, site_url, values, base_css, site_js))
    shutil.copy(REPO / "node/install.sh", out / "node/install.sh")

    missing = dict(PAGES[0], slug="404", title="Not here | Qoin")
    (out / "404.html").write_text(
        f"<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
        f"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
        f"<title>{missing['title']}</title>\n<link rel=\"icon\" href=\"{ICON}\">\n<style>\n{FONT_FACES}{base_css}</style>\n</head>\n"
        f"<body>\n<div class=\"wrap\">\n{header('404')}\n  <main>\n    <div class=\"opener\">\n      <h1>Not here</h1>\n"
        f"      <p class=\"lede\">That page doesn't exist, or it moved. <a href=\"/\">Get Qoin</a> starts from the top.</p>\n"
        f"    </div>\n  </main>\n{FOOTER}\n</div>\n</body>\n</html>\n")
    (out / ".nojekyll").write_text("")
    host = site_url.split("/")[2]
    if not host.endswith("github.io"):
        (out / "CNAME").write_text(host + "\n")

    (out / "og").mkdir()
    for p in PAGES:
        try:
            subprocess.run(["node", str(SITE / "tools/og.js"), str(out / "og" / (p["og"] + ".png")), p["og_query"]],
                           check=True, timeout=120)
        except (OSError, subprocess.SubprocessError) as e:
            print("og image not made (needs Playwright):", e)
            break
    if (out / "og/home.png").exists():
        shutil.copy(out / "og/home.png", out / "og.png")  # links shared before the site had more pages
    print("built", ", ".join("/" + p["slug"] for p in PAGES), "into", out)


if __name__ == "__main__":
    main()

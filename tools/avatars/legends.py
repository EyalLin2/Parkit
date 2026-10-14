"""Draws the legend avatars (famous Jewish & Israeli figures) as SVG.

Same flat look as the free DiceBear "Personas" avatars — round-cornered
head, dot eyes, a little scene behind — but each one is drawn by hand with
the features people recognise (Herzl's beard, Dayan's eye patch, Ben-Gurion's
white tufts...).

    python3 tools/avatars/legends.py          # writes tools/avatars/out/*.svg
    tools/avatars/render.sh                   # SVG -> 256px PNGs in the app
"""
from pathlib import Path

OUT = Path(__file__).parent / "out"

EYE = "#1d1a3a"
SKIN = {"light": "#f2c9a8", "mid": "#e6b08f", "tan": "#d99c78", "old": "#ecbfa0"}
SHADE = {"light": "#e3b391", "mid": "#d49a78", "tan": "#c48863", "old": "#dcab8b"}


# ── scenes ─────────────────────────────────────────────────────────────────
def scene(kind: str) -> str:
    sky = {
        "day": ("#9fd3f5", "#d8eefb"), "sunset": ("#f6a95b", "#fcd9a1"),
        "dusk": ("#7b8fd1", "#c6cff0"), "night": ("#2c2f6b", "#5560a8"),
        "sand": ("#f3b562", "#fbe2b0"),
    }
    if kind == "jerusalem":
        top, bot = sky["sunset"]
        ground = ('<path d="M0 200 L0 150 L30 150 L30 132 L52 132 L52 150 L70 150 L70 120 '
                  'Q88 96 106 120 L106 150 L150 150 L150 128 L172 128 L172 150 L196 150 L196 112 '
                  'L214 112 L214 150 L256 150 L256 200 Z" fill="#e7c48f"/>'
                  '<circle cx="88" cy="104" r="2" fill="#d6a85a"/>'
                  '<rect x="0" y="196" width="256" height="60" fill="#d9b27a"/>')
        sun = '<circle cx="206" cy="58" r="22" fill="#ffe27a"/>'
    elif kind == "sea":
        top, bot = sky["day"]
        ground = ('<rect x="0" y="160" width="256" height="96" fill="#4fa3d8"/>'
                  '<path d="M0 172 Q16 166 32 172 T64 172 T96 172 T128 172 T160 172 T192 172 T224 172 T256 172" '
                  'stroke="#bfe3f7" stroke-width="3" fill="none"/>')
        sun = '<circle cx="204" cy="54" r="20" fill="#ffe27a"/>'
    elif kind == "desert":
        top, bot = sky["sand"]
        ground = ('<path d="M0 170 L90 120 L170 165 L256 130 L256 256 L0 256 Z" fill="#e39c4f"/>'
                  '<rect x="0" y="180" width="256" height="76" fill="#efbf78"/>')
        sun = '<circle cx="196" cy="70" r="28" fill="#ffe27a"/>'
    elif kind == "night":
        top, bot = sky["night"]
        ground = ('<circle cx="40" cy="40" r="2" fill="#fff"/><circle cx="210" cy="30" r="2" fill="#fff"/>'
                  '<circle cx="180" cy="80" r="1.6" fill="#fff"/><circle cx="70" cy="90" r="1.6" fill="#fff"/>'
                  '<rect x="0" y="190" width="256" height="66" fill="#3b3f7a"/>')
        sun = '<circle cx="200" cy="56" r="18" fill="#f5f0c8"/><circle cx="208" cy="50" r="16" fill="#2f337a"/>'
    elif kind == "fields":
        top, bot = sky["day"]
        ground = ('<path d="M0 160 Q64 140 128 158 T256 150 L256 256 L0 256 Z" fill="#8cc96b"/>'
                  '<path d="M0 190 Q80 172 160 190 T256 184 L256 256 L0 256 Z" fill="#6fb257"/>')
        sun = '<circle cx="54" cy="52" r="20" fill="#ffe27a"/>'
    elif kind == "books":
        top, bot = ("#c9a27a", "#e6cba8")
        shelf = ""
        x = 0
        cols = ["#8b3a3a", "#2f5d8a", "#3f7a4a", "#b5853b", "#5b3f7a", "#8a5a2f"]
        i = 0
        for y in (40, 112, 184):
            x = 0
            shelf += f'<rect x="0" y="{y + 60}" width="256" height="8" fill="#8a6440"/>'
            while x < 256:
                w = 12 + (i * 7) % 9
                h = 44 + (i * 5) % 14
                shelf += f'<rect x="{x}" y="{y + 60 - h}" width="{w - 2}" height="{h}" fill="{cols[i % len(cols)]}"/>'
                x += w
                i += 1
        ground = shelf
        sun = ""
    elif kind == "knesset":
        top, bot = sky["dusk"]
        ground = ('<rect x="40" y="120" width="176" height="80" fill="#e9e2d0"/>'
                  + "".join(f'<rect x="{50 + i * 16}" y="128" width="8" height="72" fill="#d3c9b0"/>' for i in range(11))
                  + '<rect x="0" y="196" width="256" height="60" fill="#b8b0a0"/>')
        sun = ""
    else:  # fence, like the free avatars
        top, bot = sky["day"]
        ground = ('<rect x="0" y="176" width="256" height="80" fill="#cdbfb6"/>'
                  + "".join(f'<rect x="{i * 16 + 4}" y="150" width="9" height="106" fill="#b59f93"/>' for i in range(16))
                  + '<rect x="0" y="160" width="256" height="6" fill="#a68d80"/>')
        sun = '<circle cx="206" cy="46" r="18" fill="#ffe27a"/>'
    return (f'<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0" stop-color="{top}"/><stop offset="1" stop-color="{bot}"/></linearGradient></defs>'
            f'<rect width="256" height="256" fill="url(#sky)"/>{sun}{ground}')


# ── body ───────────────────────────────────────────────────────────────────
def body(outfit: str, skin: str) -> str:
    s = SKIN[skin]
    neck = f'<rect x="108" y="168" width="40" height="44" rx="10" fill="{s}"/>' \
           f'<rect x="108" y="168" width="40" height="18" fill="{SHADE[skin]}"/>'
    torso = "M48 256 Q50 206 96 198 L160 198 Q206 206 208 256 Z"
    if outfit == "suit":  # dark jacket, white shirt, tie
        return (neck + f'<path d="{torso}" fill="#2b2f45"/>'
                '<path d="M106 198 L128 246 L150 198 Z" fill="#fff"/>'
                '<path d="M122 204 L134 204 L131 214 L136 246 L128 254 L120 246 L125 214 Z" fill="#7a2230"/>'
                '<path d="M96 198 L128 256 L106 256 L88 206 Z" fill="#3a3f5c"/>'
                '<path d="M160 198 L128 256 L150 256 L168 206 Z" fill="#3a3f5c"/>')
    if outfit == "suit_blue":
        return (neck + f'<path d="{torso}" fill="#2c3e66"/>'
                '<path d="M106 198 L128 246 L150 198 Z" fill="#e9eef7"/>'
                '<path d="M122 204 L134 204 L131 214 L136 246 L128 254 L120 246 L125 214 Z" fill="#3b6fb6"/>'
                '<path d="M96 198 L128 256 L106 256 L88 206 Z" fill="#344a78"/>'
                '<path d="M160 198 L128 256 L150 256 L168 206 Z" fill="#344a78"/>')
    if outfit == "bowtie":  # 19th-century frock coat
        return (neck + f'<path d="{torso}" fill="#1f2230"/>'
                '<path d="M104 198 L128 236 L152 198 Z" fill="#fff"/>'
                '<path d="M112 204 L128 212 L144 204 L144 220 L128 212 L112 220 Z" fill="#14161f"/>'
                '<path d="M96 198 L124 256 L104 256 L86 206 Z" fill="#2c3042"/>'
                '<path d="M160 198 L132 256 L152 256 L170 206 Z" fill="#2c3042"/>')
    if outfit == "open_white":  # Ben-Gurion's open collar
        return (neck + f'<path d="{torso}" fill="#f4f4f2"/>'
                f'<path d="M110 198 L128 226 L146 198 Z" fill="{s}"/>'
                '<path d="M104 198 L124 232 L112 238 L96 202 Z" fill="#e2e2df"/>'
                '<path d="M152 198 L132 232 L144 238 L160 202 Z" fill="#e2e2df"/>')
    if outfit == "khaki":  # Palmach / pioneer shirt
        return (neck + f'<path d="{torso}" fill="#b9a372"/>'
                f'<path d="M112 198 L128 222 L144 198 Z" fill="{s}"/>'
                '<path d="M106 198 L124 228 L112 236 L96 204 Z" fill="#a38e5e"/>'
                '<path d="M150 198 L132 228 L144 236 L160 204 Z" fill="#a38e5e"/>'
                '<rect x="70" y="222" width="26" height="18" rx="3" fill="#a38e5e"/>'
                '<rect x="160" y="222" width="26" height="18" rx="3" fill="#a38e5e"/>')
    if outfit == "uniform":  # IDF olive with general's insignia
        return (neck + f'<path d="{torso}" fill="#5b6b3c"/>'
                f'<path d="M112 198 L128 220 L144 198 Z" fill="{s}"/>'
                '<path d="M106 198 L124 226 L112 234 L96 204 Z" fill="#4d5c31"/>'
                '<path d="M150 198 L132 226 L144 234 L160 204 Z" fill="#4d5c31"/>'
                '<rect x="60" y="204" width="34" height="10" rx="4" fill="#4d5c31"/>'
                '<rect x="162" y="204" width="34" height="10" rx="4" fill="#4d5c31"/>'
                '<circle cx="70" cy="209" r="3" fill="#e9d38a"/><circle cx="82" cy="209" r="3" fill="#e9d38a"/>'
                '<circle cx="174" cy="209" r="3" fill="#e9d38a"/><circle cx="186" cy="209" r="3" fill="#e9d38a"/>')
    if outfit == "tsarist":  # Trumpeldor's Russian army tunic
        return (neck + f'<path d="{torso}" fill="#6d6b4a"/>'
                '<rect x="106" y="194" width="44" height="14" rx="3" fill="#5c5a3c"/>'
                '<circle cx="128" cy="220" r="3" fill="#e0c46a"/><circle cx="128" cy="236" r="3" fill="#e0c46a"/>'
                '<circle cx="128" cy="252" r="3" fill="#e0c46a"/>'
                '<rect x="62" y="204" width="32" height="9" rx="3" fill="#a33b2f"/>'
                '<rect x="162" y="204" width="32" height="9" rx="3" fill="#a33b2f"/>'
                '<path d="M92 214 L104 214 L98 230 Z" fill="#d6b04c"/>')
    if outfit == "dress_dark":  # Golda's plain dark dress + pearls
        return (neck + f'<path d="{torso}" fill="#2d3555"/>'
                f'<path d="M106 196 Q128 222 150 196 Z" fill="{s}"/>'
                + "".join(f'<circle cx="{110 + i * 4.5:.1f}" cy="{200 + 12 * (1 - ((i - 4) / 4) ** 2):.1f}" r="2.6" fill="#f3efe4"/>'
                          for i in range(9)))
    if outfit == "blouse":  # white collared blouse under a jacket
        return (neck + f'<path d="{torso}" fill="#6a5a48"/>'
                '<path d="M100 198 L128 232 L156 198 Z" fill="#fbfbf7"/>'
                '<path d="M100 198 L122 230 L110 236 L92 204 Z" fill="#ffffff"/>'
                '<path d="M156 198 L134 230 L146 236 L164 204 Z" fill="#ffffff"/>')
    if outfit == "red_jacket":  # Aloni's bright blazer
        return (neck + f'<path d="{torso}" fill="#b23a48"/>'
                '<path d="M108 198 L128 236 L148 198 Z" fill="#1f2230"/>'
                '<path d="M96 198 L128 256 L108 256 L88 206 Z" fill="#c4505d"/>'
                '<path d="M160 198 L128 256 L148 256 L168 206 Z" fill="#c4505d"/>')
    if outfit == "robe":  # medieval robe
        return (neck + f'<path d="{torso}" fill="#6b3f6e"/>'
                '<path d="M110 198 L128 256 L146 198 Z" fill="#e9dcc0"/>'
                '<path d="M48 256 Q50 206 96 198 L104 198 L92 256 Z" fill="#7d4c80"/>'
                '<path d="M208 256 Q206 206 160 198 L152 198 L164 256 Z" fill="#7d4c80"/>')
    raise ValueError(outfit)


# ── head ───────────────────────────────────────────────────────────────────
def head(skin: str, wide: int = 0, tall: int = 0, jowl: bool = False) -> str:
    s, d = SKIN[skin], SHADE[skin]
    x, w = 80 - wide, 96 + 2 * wide
    y, h = 64 - tall, 120 + tall
    ears = (f'<ellipse cx="{x + 2}" cy="132" rx="12" ry="15" fill="{s}"/>'
            f'<ellipse cx="{x + w - 2}" cy="132" rx="12" ry="15" fill="{s}"/>'
            f'<ellipse cx="{x + 2}" cy="132" rx="6" ry="8" fill="{d}"/>'
            f'<ellipse cx="{x + w - 2}" cy="132" rx="6" ry="8" fill="{d}"/>')
    face = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{44 + wide // 2}" fill="{s}"/>'
    if jowl:
        face += f'<path d="M{x + 6} 150 Q{x + 10} 186 128 186 Q{x + w - 10} 186 {x + w - 6} 150 Z" fill="{s}"/>'
    return ears + face


def eyes(y: int = 128, gap: int = 23, brows: str | None = None, brow_tilt: int = 0, r: float = 5.5,
         lines: bool = False) -> str:
    out = (f'<circle cx="{128 - gap}" cy="{y}" r="{r}" fill="{EYE}"/>'
           f'<circle cx="{128 + gap}" cy="{y}" r="{r}" fill="{EYE}"/>')
    if brows:
        out += (f'<path d="M{128 - gap - 12} {y - 14 + brow_tilt} Q{128 - gap} {y - 20} {128 - gap + 11} {y - 15}" '
                f'stroke="{brows}" stroke-width="5" fill="none" stroke-linecap="round"/>'
                f'<path d="M{128 + gap + 12} {y - 14 + brow_tilt} Q{128 + gap} {y - 20} {128 + gap - 11} {y - 15}" '
                f'stroke="{brows}" stroke-width="5" fill="none" stroke-linecap="round"/>')
    if lines:  # crow's feet / under-eye age lines
        out += (f'<path d="M{128 - gap - 8} {y + 10} Q{128 - gap} {y + 14} {128 - gap + 8} {y + 10}" stroke="#c9906f" stroke-width="2" fill="none"/>'
                f'<path d="M{128 + gap - 8} {y + 10} Q{128 + gap} {y + 14} {128 + gap + 8} {y + 10}" stroke="#c9906f" stroke-width="2" fill="none"/>')
    return out


def nose(skin: str, big: bool = False) -> str:
    d = SHADE[skin]
    if big:
        return f'<path d="M124 132 Q118 158 114 160 Q128 170 142 160 Q138 158 132 132 Z" fill="{d}"/>'
    return f'<path d="M123 138 Q120 152 118 154 Q128 160 138 154 Q136 152 133 138 Z" fill="{d}"/>'


def mouth(kind: str = "smile", y: int = 166) -> str:
    if kind == "smile":
        return f'<path d="M114 {y} Q128 {y + 12} 142 {y}" stroke="{EYE}" stroke-width="4" fill="none" stroke-linecap="round"/>'
    if kind == "grin":
        return f'<path d="M112 {y - 2} Q128 {y + 18} 144 {y - 2} Z" fill="#fff" stroke="{EYE}" stroke-width="3.5" stroke-linejoin="round"/>'
    if kind == "flat":
        return f'<path d="M116 {y + 2} Q128 {y + 6} 140 {y + 2}" stroke="{EYE}" stroke-width="4" fill="none" stroke-linecap="round"/>'
    if kind == "stern":
        return f'<path d="M116 {y + 4} Q128 {y} 140 {y + 4}" stroke="{EYE}" stroke-width="4" fill="none" stroke-linecap="round"/>'
    raise ValueError(kind)


def glasses(kind: str = "round", y: int = 128, gap: int = 23, color: str = "#2a2a2a") -> str:
    if kind == "round":
        r = 14
        return (f'<circle cx="{128 - gap}" cy="{y}" r="{r}" fill="#ffffff" fill-opacity=".18" stroke="{color}" stroke-width="3.5"/>'
                f'<circle cx="{128 + gap}" cy="{y}" r="{r}" fill="#ffffff" fill-opacity=".18" stroke="{color}" stroke-width="3.5"/>'
                f'<path d="M{128 - gap + r} {y} Q128 {y - 6} {128 + gap - r} {y}" stroke="{color}" stroke-width="3" fill="none"/>'
                f'<path d="M{128 - gap - r} {y - 2} L82 {y - 6}" stroke="{color}" stroke-width="3"/>'
                f'<path d="M{128 + gap + r} {y - 2} L174 {y - 6}" stroke="{color}" stroke-width="3"/>')
    if kind == "big":  # 70s-80s large frames
        return (f'<rect x="{128 - gap - 18}" y="{y - 14}" width="34" height="28" rx="10" fill="#ffffff" fill-opacity=".2" stroke="{color}" stroke-width="4"/>'
                f'<rect x="{128 + gap - 16}" y="{y - 14}" width="34" height="28" rx="10" fill="#ffffff" fill-opacity=".2" stroke="{color}" stroke-width="4"/>'
                f'<path d="M{128 - gap + 16} {y - 4} Q128 {y - 10} {128 + gap - 16} {y - 4}" stroke="{color}" stroke-width="3.5" fill="none"/>')
    raise ValueError(kind)


def svg(*parts: str) -> str:
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">'
            + "".join(parts) + "</svg>")


# ── the people ─────────────────────────────────────────────────────────────
def herzl() -> str:
    hair, beard = "#1c1714", "#231b16"
    return svg(
        scene("fence"), body("bowtie", "light"), head("light"),
        # thick hair swept up and back
        f'<path d="M76 118 Q66 40 132 34 Q192 42 180 118 Q176 86 158 78 Q128 70 98 80 Q80 90 76 118 Z" fill="{hair}"/>',
        eyes(124, brows=hair, r=6),
        nose("light", big=True),
        # the famous long, wide, dark beard
        f'<path d="M80 128 Q78 168 96 204 Q112 236 128 240 Q144 236 160 204 Q178 168 176 128 '
        f'Q172 156 150 160 Q128 150 106 160 Q84 156 80 128 Z" fill="{beard}"/>',
        f'<path d="M104 160 Q116 150 128 156 Q140 150 152 160 Q140 168 128 164 Q116 168 104 160 Z" fill="{beard}"/>',
        f'<path d="M118 172 Q128 176 138 172" stroke="#b9786a" stroke-width="3" fill="none" stroke-linecap="round"/>',
    )


def bengurion() -> str:
    white = "#f7f7f5"
    return svg(
        scene("desert"), body("open_white", "old"), head("old", wide=8, jowl=True),
        # bald dome + shine
        '<ellipse cx="114" cy="80" rx="16" ry="7" fill="#fff" fill-opacity=".45"/>',
        # wild white tufts at both sides
        f'<path d="M80 136 Q46 136 44 112 Q30 98 44 84 Q38 62 60 60 Q66 42 86 52 Q100 46 102 64 Q90 84 90 106 Q88 126 80 136 Z" fill="{white}"/>',
        f'<path d="M176 136 Q210 136 212 112 Q226 98 212 84 Q218 62 196 60 Q190 42 170 52 Q156 46 154 64 Q166 84 166 106 Q168 126 176 136 Z" fill="{white}"/>',
        f'<path d="M64 92 Q72 90 76 98 M188 92 Q182 90 178 98" stroke="#d9d9d4" stroke-width="3" fill="none"/>',
        eyes(126, gap=24, brows="#e2e0da", r=5.5, lines=True),
        nose("old", big=True),
        mouth("stern", 166),
        '<path d="M98 150 Q102 168 112 174 M158 150 Q154 168 144 174" stroke="#cf9a7b" stroke-width="3" fill="none"/>',
    )


def golda() -> str:
    hair = "#8f8a86"
    return svg(
        scene("knesset"), body("dress_dark", "old"),
        f'<ellipse cx="128" cy="150" rx="62" ry="22" fill="#7f7a76"/>',
        head("old", wide=4, jowl=True),
        # grey hair pulled straight back, centre part, low bun
        f'<path d="M78 120 Q72 58 128 52 Q184 58 178 120 Q172 92 150 80 Q138 76 128 80 Q118 76 106 80 Q84 92 78 120 Z" fill="{hair}"/>',
        '<path d="M128 54 L128 80" stroke="#7a7570" stroke-width="2.5"/>',
        eyes(128, gap=22, brows="#6d6864", brow_tilt=4, r=5, lines=True),
        nose("old", big=True),
        mouth("flat", 168),
        '<path d="M100 156 Q104 172 114 178 M156 156 Q152 172 142 178" stroke="#cf9a7b" stroke-width="3" fill="none"/>',
    )


def dayan() -> str:
    hair = "#4a3a2e"
    return svg(
        scene("desert"), body("uniform", "tan"), head("tan"),
        # receding, very short hair
        f'<path d="M80 108 Q76 70 98 62 Q90 80 88 112 Z" fill="{hair}"/>',
        f'<path d="M176 108 Q180 70 158 62 Q166 80 168 112 Z" fill="{hair}"/>',
        eyes(126, gap=23, brows="#3d2f25", r=5.5),
        # black eye patch over the LEFT eye (viewer's right) + strap
        '<path d="M74 98 L178 132" stroke="#111" stroke-width="4"/>',
        '<path d="M140 118 Q151 112 164 118 Q168 132 152 140 Q140 136 140 118 Z" fill="#111"/>',
        nose("tan", big=True),
        mouth("smile", 166),
    )


def rabin() -> str:
    hair = "#9c8f86"
    return svg(
        scene("sea"), body("suit_blue", "light"), head("light"),
        # side-parted hair, sweep from left to right
        f'<path d="M78 118 Q72 62 122 54 Q170 50 180 92 Q182 106 178 116 Q172 84 140 76 Q112 74 94 72 Q84 86 78 118 Z" fill="{hair}"/>',
        '<path d="M94 72 Q110 60 132 58" stroke="#857870" stroke-width="3" fill="none"/>',
        eyes(128, gap=22, brows="#857870", r=5),
        nose("light"),
        # his shy half smile
        f'<path d="M114 164 Q128 172 142 162" stroke="{EYE}" stroke-width="4" fill="none" stroke-linecap="round"/>',
        '<ellipse cx="96" cy="152" rx="9" ry="5" fill="#f2a08c" fill-opacity=".5"/>'
        '<ellipse cx="160" cy="152" rx="9" ry="5" fill="#f2a08c" fill-opacity=".5"/>',
    )


def begin() -> str:
    hair = "#2b2420"
    return svg(
        scene("jerusalem"), body("suit", "light"), head("light", tall=6),
        '<ellipse cx="116" cy="70" rx="14" ry="6" fill="#fff" fill-opacity=".45"/>',
        # bald crown, dark hair only at the sides
        f'<path d="M80 128 Q74 98 90 84 Q88 108 90 128 Z" fill="{hair}"/>',
        f'<path d="M176 128 Q182 98 166 84 Q168 108 166 128 Z" fill="{hair}"/>',
        eyes(128, gap=23, brows=hair, r=5),
        glasses("round", 128, 23),
        nose("light", big=True),
        mouth("smile", 168),
    )


def bialik() -> str:
    return svg(
        scene("books"), body("bowtie", "mid"), head("mid", wide=10),
        # completely bald, round head
        '<ellipse cx="112" cy="76" rx="16" ry="7" fill="#fff" fill-opacity=".4"/>',
        '<path d="M72 120 Q70 104 78 98 Q80 112 80 124 Z M184 120 Q186 104 178 98 Q176 112 176 124 Z" fill="#5a4a3e"/>',
        eyes(128, gap=24, brows="#5a4a3e", r=5.5),
        nose("mid"),
        # small moustache
        '<path d="M110 156 Q120 150 128 154 Q136 150 146 156 Q136 160 128 158 Q120 160 110 156 Z" fill="#4a3a30"/>',
        mouth("flat", 162),
    )


def benyehuda() -> str:
    hair, beard = "#2e2722", "#3a312b"
    return svg(
        scene("books"), body("bowtie", "light"), head("light"),
        # thick wavy dark hair
        f'<path d="M74 122 Q64 60 108 48 Q118 40 132 46 Q150 38 164 52 Q190 62 182 122 Q176 90 160 82 Q130 74 98 82 Q80 92 74 122 Z" fill="{hair}"/>',
        eyes(126, gap=23, brows=hair, r=4.5),
        glasses("round", 126, 23, "#3b3b3b"),
        nose("light"),
        # pointed beard + moustache
        f'<path d="M82 136 Q84 176 104 198 Q118 216 128 222 Q138 216 152 198 Q172 176 174 136 '
        f'Q168 160 150 162 Q128 154 106 162 Q88 160 82 136 Z" fill="{beard}"/>',
        f'<path d="M106 158 Q118 150 128 156 Q138 150 150 158 Q138 166 128 162 Q118 166 106 158 Z" fill="{beard}"/>',
        '<path d="M120 172 Q128 175 136 172" stroke="#b9786a" stroke-width="3" fill="none" stroke-linecap="round"/>',
    )


def szenes() -> str:
    hair = "#3a2a22"
    return svg(
        scene("sea"), body("blouse", "light"),
        # shoulder-length dark wavy hair behind the face
        f'<path d="M70 120 Q62 54 128 46 Q194 54 186 120 Q194 170 180 198 Q168 206 160 196 L96 196 Q88 206 76 198 Q62 170 70 120 Z" fill="{hair}"/>',
        head("light"),
        f'<path d="M78 120 Q74 66 128 58 Q176 62 180 116 Q166 86 140 82 Q130 96 108 98 Q88 100 78 120 Z" fill="{hair}"/>',
        eyes(130, gap=22, brows=hair, r=5.5),
        nose("light"),
        mouth("smile", 166),
        '<ellipse cx="98" cy="154" rx="9" ry="5" fill="#f2a08c" fill-opacity=".55"/>'
        '<ellipse cx="158" cy="154" rx="9" ry="5" fill="#f2a08c" fill-opacity=".55"/>',
    )


def allon() -> str:
    hair = "#2f251f"
    return svg(
        scene("fields"), body("khaki", "tan"), head("tan"),
        # full, wavy dark hair combed back
        f'<path d="M76 118 Q66 58 124 48 Q172 46 182 96 Q184 110 180 118 Q174 88 154 80 Q140 70 124 78 Q106 70 92 84 Q80 96 76 118 Z" fill="{hair}"/>',
        f'<path d="M96 66 Q112 56 128 64 Q144 54 160 64" stroke="#463830" stroke-width="3" fill="none"/>',
        eyes(128, gap=23, brows=hair, r=5.5),
        nose("tan"),
        mouth("grin", 164),
    )


def aloni() -> str:
    hair = "#b8b2ab"
    return svg(
        scene("knesset"), body("red_jacket", "light"), head("light"),
        # short, curly grey hair
        "".join(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{hair}"/>' for cx, cy, r in [
            (84, 98, 14), (92, 76, 16), (110, 62, 16), (130, 58, 16), (150, 62, 16), (166, 76, 16), (174, 98, 14),
            (80, 116, 10), (178, 116, 10)]),
        eyes(130, gap=22, brows="#9a938b", r=4.5),
        glasses("big", 130, 22, "#5a2a2a"),
        nose("light"),
        mouth("grin", 166),
        '<circle cx="78" cy="150" r="4" fill="#e3c35a"/><circle cx="178" cy="150" r="4" fill="#e3c35a"/>',
    )


def trumpeldor() -> str:
    hair = "#3a2b22"
    return svg(
        scene("fields"), body("tsarist", "mid"), head("mid"),
        f'<path d="M78 116 Q74 62 128 56 Q182 62 178 116 Q172 88 150 80 Q128 74 106 80 Q84 88 78 116 Z" fill="{hair}"/>',
        eyes(128, gap=23, brows=hair, r=5.5),
        nose("mid"),
        # big walrus moustache
        f'<path d="M100 160 Q112 146 128 154 Q144 146 156 160 Q160 170 150 168 Q138 160 128 162 Q118 160 106 168 Q96 170 100 160 Z" fill="{hair}"/>',
    )


def peres() -> str:
    hair = "#d9d6d0"
    return svg(
        scene("sea"), body("suit", "old"), head("old", tall=4),
        # silver hair combed straight back
        f'<path d="M78 118 Q72 56 128 50 Q184 56 178 118 Q174 86 156 76 Q128 68 100 76 Q82 86 78 118 Z" fill="{hair}"/>',
        '<path d="M100 66 Q128 56 156 66 M96 78 Q128 66 160 78" stroke="#c4c0b9" stroke-width="2.5" fill="none"/>',
        eyes(128, gap=22, brows="#bdb8b0", r=5, lines=True),
        nose("old", big=True),
        mouth("smile", 168),
    )


def rambam() -> str:
    beard = "#2a2420"
    return svg(
        scene("desert"), body("robe", "tan"), head("tan"),
        # turban
        '<path d="M70 110 Q64 44 128 38 Q192 44 186 110 Q180 94 128 90 Q76 94 70 110 Z" fill="#f2ead8"/>',
        '<path d="M76 90 Q128 66 180 90 M74 74 Q128 52 182 74" stroke="#d9cfb6" stroke-width="5" fill="none"/>',
        '<circle cx="128" cy="62" r="7" fill="#3d6fb0"/>',
        eyes(128, gap=23, brows=beard, r=5.5),
        nose("tan", big=True),
        f'<path d="M80 134 Q80 180 104 206 Q118 222 128 226 Q138 222 152 206 Q176 180 176 134 '
        f'Q170 160 150 162 Q128 154 106 162 Q86 160 80 134 Z" fill="{beard}"/>',
        f'<path d="M106 158 Q118 150 128 156 Q138 150 150 158 Q138 166 128 162 Q118 166 106 158 Z" fill="{beard}"/>',
    )


PEOPLE = {
    "herzl": herzl, "bengurion": bengurion, "golda": golda, "dayan": dayan, "rabin": rabin,
    "begin": begin, "bialik": bialik, "benyehuda": benyehuda, "szenes": szenes, "allon": allon,
    "aloni": aloni, "trumpeldor": trumpeldor, "peres": peres, "rambam": rambam,
}

if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    for name, draw in PEOPLE.items():
        (OUT / f"avatar_legend_{name}.svg").write_text(draw())
    print(f"{len(PEOPLE)} SVGs in {OUT}")

"""
The launcher icon for Balance Calories: a balance beam with a slice of pie on one side and a dumbbell on the
other — the two halves of the product, weighed against each other.

Owner's decisions (2026-11-08): structure **B** from the sketch sheet (beam and objects, no pans), the slice
**rotated with some depth** so it reads as a wedge of cake rather than a flat triangle, the dumbbell in **steel
blue #4682B4**, and the pie in **two colours** — golden crust over a berry filling.

This file is the source of the geometry. It renders previews at 108/48/24 dp for review and prints the
VectorDrawable path data that res/drawable/ic_launcher_foreground.xml carries, so the shipped vector and the
approved preview cannot drift apart.

Geometry is in icon units on a 108 x 108 canvas; the adaptive-icon safe zone is the centre 72 x 72, so every
shape stays inside roughly x, y in [22, 86].
"""

from PIL import Image, ImageDraw
import math
import os

SS = 8

INK = (34, 38, 46, 255)          # beam, stem, base, pivot
STEEL = (70, 130, 180, 255)      # #4682B4 — the dumbbell
CRUST = (214, 158, 74, 255)      # the top of the crust
CRUST_SIDE = (166, 112, 46, 255)  # the crust's front face — the visible depth of the slice
FILLING = (176, 66, 62, 255)       # berry filling, the pie's second colour
FILLING_DARK = (138, 48, 46, 255)  # the filling's cut faces, in shadow
CREAM = (243, 237, 225, 255)     # adaptive background
GUIDE = (233, 30, 99, 90)

BEAM_Y = 47.0
BEAM_HALF = 29.0
STEM_W = 4.8
BASE_Y = 84.0
BASE_HALF = 12.0

# The slice is placed point by point rather than by squash-and-rotate: a wedge that has to read at 24 dp cannot
# be left to a parametric guess. A is the tip, P1 and P2 are where the crust begins and ends, Q bulges the crust
# outward. The depth is the extrusion below the top face, which is what says "a slice of something" rather than
# "a triangle". A tip 20 units from a 60-degree crust is a slice; a long thin one is a pennant.
PIE_A = (22.0, 59.5)
PIE_P1 = (38.5, 52.5)
PIE_P2 = (38.5, 65.5)
PIE_Q = (43.5, 59.0)
PIE_DEPTH = 4.4
PIE_TILT = -9.0
DUMBBELL_C = (77.0, 61.5)
DUMBBELL_LENGTH = 18.0
DUMBBELL_BAR = 3.4
PLATE_W = 5.4
PLATE_H = 13.0


# --- geometry ------------------------------------------------------------------------------------------------

def rotate(points, degrees, around):
    a = math.radians(degrees)
    ca, sa = math.cos(a), math.sin(a)
    ox, oy = around
    out = []
    for x, y in points:
        dx, dy = x - ox, y - oy
        out.append((ox + dx * ca - dy * sa, oy + dx * sa + dy * ca))
    return out


def _lerp(p, q, t):
    return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)


def _quad(p, q, r, segments):
    return [_lerp(_lerp(p, q, i / segments), _lerp(q, r, i / segments), i / segments)
            for i in range(segments + 1)]


def pie_top(segments=18):
    """The slice's top face: tip, the two cut edges, and the curved crust between them."""
    outer = _quad(PIE_P1, PIE_Q, PIE_P2, segments)
    return [PIE_A] + outer


def pie_crust_band(segments=18, width=4.6):
    """The golden crust along the arc: the pie's second colour, on the top face."""
    outer = _quad(PIE_P1, PIE_Q, PIE_P2, segments)
    inner_p1 = _lerp(PIE_P1, PIE_A, width / math.dist(PIE_P1, PIE_A))
    inner_p2 = _lerp(PIE_P2, PIE_A, width / math.dist(PIE_P2, PIE_A))
    inner_q = _lerp(PIE_Q, PIE_A, width / math.dist(PIE_Q, PIE_A) * 0.9)
    inner = _quad(inner_p1, inner_q, inner_p2, segments)
    return outer + inner[::-1]


def extrude(shape, depth=PIE_DEPTH):
    return [(x, y + depth) for x, y in shape]

def dumbbell_shapes(c=DUMBBELL_C, length=DUMBBELL_LENGTH, bar=DUMBBELL_BAR,
                    plate_w=PLATE_W, plate_h=PLATE_H):
    cx, cy = c
    half_bar = length / 2.0 - plate_w
    bar_rect = [(cx - half_bar, cy - bar / 2), (cx + half_bar, cy - bar / 2),
                (cx + half_bar, cy + bar / 2), (cx - half_bar, cy + bar / 2)]
    plates = []
    for sign in (-1, 1):
        x = cx + sign * (length / 2.0 - plate_w / 2.0)
        plates.append([(x - plate_w / 2, cy - plate_h / 2), (x + plate_w / 2, cy - plate_h / 2),
                       (x + plate_w / 2, cy + plate_h / 2), (x - plate_w / 2, cy + plate_h / 2)])
    return bar_rect, plates


def rounded_rect(x0, y0, x1, y1, r, segments=6):
    pts = []
    corners = [((x1 - r, y0 + r), -90), ((x1 - r, y1 - r), 0), ((x0 + r, y1 - r), 90), ((x0 + r, y0 + r), 180)]
    for (cx, cy), start in corners:
        for i in range(segments + 1):
            a = math.radians(start + 90.0 * i / segments)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def structure_shapes():
    """Beam, pivot, stem and base — all ink."""
    beam = rounded_rect(54 - BEAM_HALF, BEAM_Y - 2.8, 54 + BEAM_HALF, BEAM_Y + 2.8, 2.6)
    pivot = [(54.0, BEAM_Y - 9.0), (59.6, BEAM_Y + 1.0), (48.4, BEAM_Y + 1.0)]
    stem = rounded_rect(54 - STEM_W / 2, BEAM_Y, 54 + STEM_W / 2, BASE_Y, 2.0)
    base = rounded_rect(54 - BASE_HALF, BASE_Y - 2.8, 54 + BASE_HALF, BASE_Y + 2.8, 2.4)
    return [beam, pivot, stem, base]


def strings():
    return [rounded_rect(28.6, BEAM_Y + 2.0, 31.4, 54.0, 1.2),
            rounded_rect(75.6, BEAM_Y + 2.0, 78.4, 53.2, 1.2)]


def icon_layers():
    """Fill and polygon, back to front. The single source of truth for both the preview and the vector."""
    top = pie_top()
    crust = pie_crust_band()
    bar_rect, plates = dumbbell_shapes()
    layers = []
    for shape in strings():
        layers.append((INK, shape))
    for shape in structure_shapes():
        layers.append((INK, shape))
    # the slice, back to front: the filling's depth, the crust's depth, then the two top faces. Extruding the
    # crust separately is what keeps the cut faces berry-coloured while the crust face stays golden.
    layers.append((FILLING_DARK, extrude(top)))
    layers.append((CRUST_SIDE, extrude(crust)))
    layers.append((FILLING, top))
    layers.append((CRUST, crust))
    layers.append((STEEL, bar_rect))
    for plate in plates:
        layers.append((STEEL, plate))
    return layers


# --- rendering -----------------------------------------------------------------------------------------------

def render_preview(size_px):
    k = size_px * SS / 108.0
    img = Image.new("RGBA", (size_px * SS, size_px * SS), CREAM)
    d = ImageDraw.Draw(img)
    for colour, shape in icon_layers():
        d.polygon([(x * k, y * k) for x, y in shape], fill=colour)
    return img.resize((size_px, size_px), Image.LANCZOS)


def path_data(points):
    """Android VectorDrawable path data: M, L…, Z. Points are already in icon units."""
    body = "M{:.2f},{:.2f}".format(*points[0])
    for x, y in points[1:]:
        body += " L{:.2f},{:.2f}".format(x, y)
    return body + " Z"


def emit_vector():
    """Print the <path> elements for res/drawable/ic_launcher_foreground.xml."""
    hex_colour = lambda c: "#{:02X}{:02X}{:02X}".format(c[0], c[1], c[2])
    for colour, shape in icon_layers():
        print(f'    <path android:fillColor="{hex_colour(colour)}" android:pathData="{path_data(shape)}" />')


def main():
    out_dir = os.path.join("tmp", "icon")
    os.makedirs(out_dir, exist_ok=True)
    for size_px in (108, 48, 24):
        render_preview(size_px).save(os.path.join(out_dir, f"final_{size_px}.png"))
    # one review sheet: the real sizes, shown at 2x
    sheet = Image.new("RGBA", (3 * 240 + 80, 240 + 80), (252, 252, 254, 255))
    d = ImageDraw.Draw(sheet)
    x = 30
    for size_px in (108, 48, 24):
        shown = render_preview(size_px).resize((size_px * 2, size_px * 2), Image.LANCZOS)
        sheet.paste(shown, (x, 30))
        d.text((x, 30 + size_px * 2 + 8), f"{size_px} dp", fill=(140, 140, 150, 255))
        x += size_px * 2 + 60
    sheet.save(os.path.join(out_dir, "final_sheet.png"))
    print("wrote", os.path.join(out_dir, "final_sheet.png"))
    emit_vector()


if __name__ == "__main__":
    main()
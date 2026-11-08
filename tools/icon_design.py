"""
The launcher icon for Balance Calories: a balance beam with a slice of pie on one side and a dumbbell on the
other — the two halves of the product, weighed against each other.

Owner's decisions: the reduced structure from the sketch sheet (beam and objects, no pans), both objects
**seen at an angle** rather than flat, the dumbbell in **steel blue #4682B4**, and the pie in **two tones** —
golden crust over berry filling. The proportions of the slice come from the owner's own correction (2026-11-08):
a circular sector of radius ~19 units, a crust band about a third of it, and a depth face below.

This file is the source of the geometry. It renders previews at 108/48/24 dp for review and prints the
VectorDrawable path data that res/drawable/ic_launcher_foreground.xml carries, so the shipped vector and the
approved preview cannot drift apart.

Geometry is in icon units on a 108 x 108 canvas; the adaptive-icon safe zone is the centre 72 x 72, so every
shape stays inside roughly x, y in [21, 87].
"""

from PIL import Image, ImageDraw
import math
import os

SS = 8

INK = (34, 38, 46, 255)            # beam, stem, base, pivot
STEEL = (70, 130, 180, 255)        # #4682B4 — the dumbbell
STEEL_DARK = (44, 92, 132, 255)    # the bar, in the shadow of the weights
STEEL_LIGHT = (104, 154, 198, 255)   # the lit disc face on the outer end of each weight
CRUST = (214, 158, 74, 255)        # the top of the crust
CRUST_SIDE = (166, 112, 46, 255)   # the crust's front face
FILLING = (176, 66, 62, 255)       # berry filling, the pie's second colour
FILLING_DARK = (138, 48, 46, 255)  # its cut faces, in shadow
CREAM = (243, 237, 225, 255)       # adaptive background
GUIDE = (233, 30, 99, 90)

BEAM_Y = 47.0
BEAM_HALF = 29.0
STEM_W = 4.8
BASE_Y = 84.0
BASE_HALF = 12.0

# The slice: a circular sector, apex at the left, seen almost from above. Angles in degrees, screen convention.
PIE_APEX = (21.5, 62.0)
PIE_RADIUS = 19.0
PIE_START = -30.0      # the upper radius
PIE_END = 28.0         # the lower radius
PIE_SQUASH = 0.94      # just enough foreshortening to stop it looking like a flat wedge
PIE_CRUST = 6.0
PIE_DEPTH = 5.0
PIE_SEGMENTS = 20

DUMBBELL_C = (76.0, 61.5)
DUMBBELL_TILT = -18.0      # degrees, clockwise: the bar runs up to the right
DUMBBELL_BAR = 16.0
DUMBBELL_BAR_TH = 3.0
WEIGHT_HALF = 5.2          # half the height of one end weight, across the bar
WEIGHT_T0 = 3.2            # where the weight starts, along the bar
WEIGHT_T1 = 7.7            # and where it ends
# --- small geometry helpers ----------------------------------------------------------------------------------

def unit(size_px):
    return size_px * SS / 108.0


def poly(d, points, fill, k):
    d.polygon([(x * k, y * k) for x, y in points], fill=fill)


def rotate(points, degrees, around):
    a = math.radians(degrees)
    ca, sa = math.cos(a), math.sin(a)
    ox, oy = around
    out = []
    for x, y in points:
        dx, dy = x - ox, y - oy
        out.append((ox + dx * ca - dy * sa, oy + dx * sa + dy * ca))
    return out


def ellipse(centre, u, v, ru, rv, segments=28):
    """A closed ellipse around `centre`, `ru` along u and `rv` along v (both unit vectors)."""
    cx, cy = centre
    pts = []
    for i in range(segments):
        a = 2.0 * math.pi * i / segments
        ca, sa = math.cos(a), math.sin(a)
        pts.append((cx + ca * ru * u[0] + sa * rv * v[0], cy + ca * ru * u[1] + sa * rv * v[1]))
    return pts


def rounded_rect(x0, y0, x1, y1, r, segments=5):
    pts = []
    corners = [((x1 - r, y0 + r), -90), ((x1 - r, y1 - r), 0), ((x0 + r, y1 - r), 90), ((x0 + r, y0 + r), 180)]
    for (cx, cy), start in corners:
        for i in range(segments + 1):
            a = math.radians(start + 90.0 * i / segments)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


# --- the slice ------------------------------------------------------------------------------------------------

def _sector_point(radius, degrees):
    ax, ay = PIE_APEX
    a = math.radians(degrees)
    return (ax + radius * math.cos(a), ay + radius * math.sin(a) * PIE_SQUASH)


def pie_top(segments=PIE_SEGMENTS):
    """The top face: the apex, then the arc of the outer edge."""
    pts = [PIE_APEX]
    for i in range(segments + 1):
        pts.append(_sector_point(PIE_RADIUS, PIE_START + (PIE_END - PIE_START) * i / segments))
    return pts


def pie_crust_band(segments=PIE_SEGMENTS):
    """The crust: a band along the outer edge, the pie's second colour."""
    outer = [_sector_point(PIE_RADIUS, PIE_START + (PIE_END - PIE_START) * i / segments)
             for i in range(segments + 1)]
    inner = [_sector_point(PIE_RADIUS - PIE_CRUST, PIE_START + (PIE_END - PIE_START) * i / segments)
             for i in range(segments + 1)]
    return outer + inner[::-1]


def extrude(shape, depth=PIE_DEPTH):
    """
    The visible side of a slice: the shape swept downwards.

    Translating the whole outline by `depth` and filling it leaves a gap wherever the slice is thinner than the
    depth — at the tip it showed as two prongs with the background between them, which read as a pen nib rather
    than as a solid slice. A swept solid is the shape plus its copy plus a quad on every edge, and the seam
    disappears because all of it is one colour.
    """
    shifted = [(x, y + depth) for x, y in shape]
    pieces = [shape, shifted]
    n = len(shape)
    for i in range(n):
        a, b = shape[i], shape[(i + 1) % n]
        pieces.append([a, b, (b[0], b[1] + depth), (a[0], a[1] + depth)])
    return pieces


# --- the dumbbell ---------------------------------------------------------------------------------------------

def _dumbbell_axes():
    a = math.radians(DUMBBELL_TILT)
    return (math.cos(a), math.sin(a)), (-math.sin(a), math.cos(a))


def _at(t, across_offset=0.0):
    """A point t units from the dumbbell's centre along the bar, offset across it."""
    along, across = _dumbbell_axes()
    cx, cy = DUMBBELL_C
    return (cx + along[0] * t + across[0] * across_offset,
            cy + along[1] * t + across[1] * across_offset)


def dumbbell_shapes():
    """
    A dumbbell seen at an angle, as two weights on a bar.

    The first attempt drew the plates as thin ellipses — which is what a plate looks like edge-on, and which is
    also exactly the vertical stroke of a capital H. These are **rounded blocks with a lit disc face on the outer
    end**: the face is the cue that says "this is a weight seen from the side", and the round corners are what
    stop the two ends reading as letters.
    """
    along, across = _dumbbell_axes()
    half = DUMBBELL_BAR / 2.0
    bar = rounded_rect(-half, -DUMBBELL_BAR_TH / 2.0, half, DUMBBELL_BAR_TH / 2.0, DUMBBELL_BAR_TH / 2.0)
    bar = [_at(x, y) for x, y in bar]

    weights, caps = [], []
    for sign in (-1, 1):
        t0, t1 = sorted((sign * WEIGHT_T0, sign * WEIGHT_T1))
        block = rounded_rect(t0, -WEIGHT_HALF, t1, WEIGHT_HALF, 1.7)
        weights.append([_at(x, y) for x, y in block])
        caps.append(ellipse(_at(sign * (WEIGHT_T1 - 0.85), 0.0), across, along, WEIGHT_HALF - 0.35, 1.4))
    return bar, weights, caps

# --- the frame ------------------------------------------------------------------------------------------------

def structure_shapes():
    """Beam, pivot, stem and base — all ink."""
    beam = rounded_rect(54 - BEAM_HALF, BEAM_Y - 2.8, 54 + BEAM_HALF, BEAM_Y + 2.8, 2.6)
    pivot = [(54.0, BEAM_Y - 9.0), (59.6, BEAM_Y + 1.0), (48.4, BEAM_Y + 1.0)]
    stem = rounded_rect(54 - STEM_W / 2, BEAM_Y, 54 + STEM_W / 2, BASE_Y, 2.0)
    base = rounded_rect(54 - BASE_HALF, BASE_Y - 2.8, 54 + BASE_HALF, BASE_Y + 2.8, 2.4)
    return [beam, pivot, stem, base]


def strings():
    return [rounded_rect(28.6, BEAM_Y + 2.0, 31.4, 54.0, 1.2),
            rounded_rect(74.6, BEAM_Y + 2.0, 77.4, 53.2, 1.2)]


def icon_layers():
    """
    (colour, [polygons]) back to front — the single source of truth for both the preview and the vector.

    The side of the slice is a list of quads rather than one outline because a swept solid is easier to build
    that way; each group still becomes ONE `<path>` with several subpaths, so the pieces are rasterised together
    and no antialiasing seam appears between them.
    """
    top = pie_top()
    crust = pie_crust_band()
    bar, weights, caps = dumbbell_shapes()
    return [
        (INK, strings() + structure_shapes()),
        # the slice, back to front: the filling's depth, the crust's depth, then the two top faces. Extruding
        # the crust separately keeps the cut faces berry-coloured while the crust's own face stays golden.
        (FILLING_DARK, extrude(top)),
        (CRUST_SIDE, extrude(crust)),
        (FILLING, [top]),
        (CRUST, [crust]),
        # the dumbbell: the bar, then each weight, then the lit disc on its outer end
        (STEEL_DARK, [bar]),
        (STEEL, weights),
        (STEEL_LIGHT, caps),
    ]

# --- rendering -------------------------------------------------------------------------------------------------

def render_preview(size_px):
    k = size_px * SS / 108.0
    img = Image.new("RGBA", (size_px * SS, size_px * SS), CREAM)
    d = ImageDraw.Draw(img)
    for colour, shapes in icon_layers():
        for shape in shapes:
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
    for colour, shapes in icon_layers():
        data = " ".join(path_data(shape) for shape in shapes)
        print(f'    <path android:fillColor="{hex_colour(colour)}" android:pathData="{data}" />')


def main():
    out_dir = os.path.join("tmp", "icon")
    os.makedirs(out_dir, exist_ok=True)
    for size_px in (108, 48, 24):
        render_preview(size_px).save(os.path.join(out_dir, f"final_{size_px}.png"))
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

"""Build the two transparent Android working-status GIFs (requires Pillow)."""

from math import cos, pi, sin
from pathlib import Path

from PIL import Image, ImageDraw


SIZE = 48
SCALE = 4
FRAME_COUNT = 16
FRAME_MS = 80
OUTPUT = Path(__file__).resolve().parents[1] / "android/app/src/main/res/drawable-nodpi"


def bot_frame(index, foreground, surface):
    phase = 2 * pi * index / FRAME_COUNT
    rise = round(1.5 * sin(phase), 2)
    frame = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE))
    pen = ImageDraw.Draw(frame)

    def box(coords):
        return tuple(round(point * SCALE) for point in coords)

    def dot(x, y, radius, opacity=255):
        pen.ellipse(box((x - radius, y - radius, x + radius, y + radius)), fill=(foreground,) * 3 + (opacity,))

    ink = (foreground,) * 3 + (255,)
    # Lift the bot as its three-dot progress trail sweeps the outer orbit.
    for step, radius, opacity in [(2, 1.05, 104), (1, 1.4, 173), (0, 2.05, 255)]:
        theta = phase - pi / 2 - step * 0.31
        dot(24 + 20 * cos(theta), 24 + 20 * sin(theta), radius, opacity)

    pen.line(box((24, 13 + rise, 24, 16 + rise)), fill=ink, width=2 * SCALE)
    dot(24, 11.5 + rise, 2)
    pen.rounded_rectangle(box((12.5, 16 + rise, 35.5, 35 + rise)), radius=7 * SCALE, outline=ink, width=2 * SCALE)
    for eye in (19.5, 28.5):
        if index in (9, 10):
            pen.line(box((eye - 1.7, 23 + rise, eye + 1.7, 23 + rise)), fill=ink, width=2 * SCALE)
        else:
            dot(eye, 23 + rise, 1.65)
    pen.arc(box((20.5, 25 + rise, 27.5, 30.5 + rise)), 12, 168, fill=ink, width=round(1.6 * SCALE))

    # GIF has one transparent color. Blend edge pixels toward the matching theme.
    frame = frame.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    gif = Image.new("P", frame.size)
    gif.putpalette([0, 0, 0] + [channel for gray in range(255) for channel in (gray, gray, gray)])
    gif.putdata([
        min(255, 1 + round((foreground * alpha + surface * (255 - alpha)) / 255))
        if alpha >= 56 else 0
        for alpha in frame.getchannel("A").tobytes()
    ])
    return gif


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for theme, foreground, surface in (("light", 17, 250), ("dark", 245, 25)):
        frames = [bot_frame(index, foreground, surface) for index in range(FRAME_COUNT)]
        frames[0].save(
            OUTPUT / f"working_bot_{theme}.gif",
            save_all=True,
            append_images=frames[1:],
            loop=0,
            duration=FRAME_MS,
            transparency=0,
            disposal=2,
            optimize=False,
        )


if __name__ == "__main__":
    main()

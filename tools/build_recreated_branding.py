from pathlib import Path

from PIL import Image, ImageChops, ImageOps


ROOT = Path(__file__).resolve().parents[1]
MASTER_DIR = ROOT / "assets" / "branding" / "masters"
OUTPUT = ROOT / "assets" / "branding"


def content_crop(image: Image.Image, background: tuple[int, int, int], threshold: int = 8, padding: int = 24) -> Image.Image:
    rgb = image.convert("RGB")
    diff = ImageChops.difference(rgb, Image.new("RGB", rgb.size, background)).convert("L")
    mask = diff.point(lambda value: 255 if value > threshold else 0)
    bbox = mask.getbbox()
    if not bbox:
        return rgb
    left, top, right, bottom = bbox
    return rgb.crop((
        max(0, left - padding),
        max(0, top - padding),
        min(rgb.width, right + padding),
        min(rgb.height, bottom + padding),
    ))


def save(name: str, image: Image.Image, ico: bool = False) -> None:
    rgba = image.convert("RGBA")
    rgba.save(OUTPUT / f"{name}.png", format="PNG", optimize=True)
    rgba.save(OUTPUT / f"{name}.webp", format="WEBP", lossless=True, method=6)
    if ico:
        rgba.save(
            OUTPUT / f"{name}.ico",
            format="ICO",
            sizes=[(16, 16), (32, 32), (48, 48), (96, 96), (192, 192)],
        )
        rgba.save(OUTPUT / f"{name}.icns", format="ICNS")


def recolor(image: Image.Image, background: tuple[int, int, int], foreground: tuple[int, int, int]) -> Image.Image:
    rgb = image.convert("RGB")
    bg = Image.new("RGB", rgb.size, background)
    mask = ImageChops.difference(rgb, bg).convert("L").point(lambda value: 255 if value > 8 else 0)
    return Image.composite(Image.new("RGB", rgb.size, foreground), bg, mask)


def foreground_alpha(image: Image.Image, background: tuple[int, int, int], threshold: int = 8) -> Image.Image:
    rgb = image.convert("RGB")
    return ImageChops.difference(rgb, Image.new("RGB", rgb.size, background)).convert("L").point(
        lambda value: 255 if value > threshold else 0
    )


def white_mark_on_dark(mark: Image.Image, background: tuple[int, int, int]) -> Image.Image:
    canvas = Image.new("RGBA", mark.size, (*background, 255))
    white = Image.new("RGBA", mark.size, (255, 255, 255, 255))
    white.putalpha(foreground_alpha(mark, (255, 255, 255)))
    canvas.alpha_composite(white)
    return canvas


def centered_mark(mark: Image.Image, size: int, background: tuple[int, int, int], max_side: int = 360) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (*background, 255))
    fitted = ImageOps.contain(mark.convert("RGBA"), (max_side, max_side), method=Image.Resampling.LANCZOS)
    canvas.alpha_composite(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2))
    return canvas


def splash(
    logo: Image.Image,
    size: tuple[int, int],
    background: tuple[int, int, int],
    width_ratio: float = 0.78,
    height_ratio: float = 0.52,
) -> Image.Image:
    canvas = Image.new("RGBA", size, (*background, 255))
    fitted = ImageOps.contain(logo.convert("RGBA"), (int(size[0] * width_ratio), int(size[1] * height_ratio)), method=Image.Resampling.LANCZOS)
    x = (size[0] - fitted.width) // 2
    y = int(size[1] * 0.22)
    canvas.alpha_composite(fitted, (x, y))
    return canvas


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)

    # Every output below is generated from these masters only. The board and
    # its rough crops are intentionally not read by this build step.
    light_master = Image.open(MASTER_DIR / "tino_logo_horizontal_light.png")
    dark_master = Image.open(MASTER_DIR / "tino_logo_horizontal_dark.png")
    vertical_master = Image.open(MASTER_DIR / "tino_logo_vertical.png")

    light_logo = content_crop(light_master, (255, 255, 255))
    dark_logo = content_crop(dark_master, tuple(dark_master.convert("RGB").getpixel((0, 0))))
    vertical_logo = content_crop(vertical_master, (255, 255, 255))

    # The vertical master has the symbol isolated above the wordmark.
    mark = content_crop(vertical_master.crop((200, 300, 850, 850)), (255, 255, 255), padding=12)
    mark_black = recolor(mark, (255, 255, 255), (0, 0, 0))
    mark_white = white_mark_on_dark(mark, (0, 76, 48))

    save("tino_logo_horizontal", light_logo)
    save("tino_logo_vertical", vertical_logo)
    save("tino_logo_dark", dark_logo)
    save("tino_logo_black", recolor(light_logo, (255, 255, 255), (0, 0, 0)))
    save("tino_mark", mark)
    save("tino_mark_black", mark_black)
    save("tino_mark_white", mark_white)

    save("tino_app_icon", centered_mark(mark, 512, (255, 255, 255)), ico=True)
    save("tino_app_icon_dark", centered_mark(mark_white, 512, (0, 76, 48)), ico=True)
    save("tino_favicon", ImageOps.contain(mark, (192, 192), method=Image.Resampling.LANCZOS), ico=True)

    slogan = content_crop(light_logo.crop((260, 340, light_logo.width, light_logo.height)), (255, 255, 255), padding=12)
    save("tino_slogan", slogan)

    save("tino_splash_light", splash(vertical_logo, (1080, 1920), (255, 255, 255), width_ratio=0.66, height_ratio=0.45))
    dark_background = tuple(dark_logo.convert("RGB").getpixel((0, 0)))
    dark_foreground = dark_logo.convert("RGBA")
    dark_foreground.putalpha(foreground_alpha(dark_logo, dark_background, threshold=24))
    save("tino_splash_dark", splash(dark_foreground, (1080, 1920), dark_background))


if __name__ == "__main__":
    main()

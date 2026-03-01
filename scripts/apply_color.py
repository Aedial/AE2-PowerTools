#!/usr/bin/env python3
"""
Apply a chosen color to a grayscale image using HSV color space.
The grayscale values become the Value (V) channel, the chosen color provides H and S.
Preserves the original alpha channel.
"""

import argparse
import sys
from pathlib import Path

import cv2
import numpy as np


def parse_color(color_str: str) -> tuple[int, int, int]:
    """
    Parse a color string in hex format (#RRGGBB or RRGGBB) or RGB format (R,G,B).

    Returns:
        Tuple of (R, G, B) values in range 0-255
    """

    # Handle hex format
    color_str = color_str.strip()
    if color_str.startswith("#"):
        color_str = color_str[1:]

    if len(color_str) == 6 and all(c in "0123456789abcdefABCDEF" for c in color_str):
        r = int(color_str[0:2], 16)
        g = int(color_str[2:4], 16)
        b = int(color_str[4:6], 16)
        return (r, g, b)

    # Handle RGB format (comma-separated)
    if "," in color_str:
        parts = [p.strip() for p in color_str.split(",")]
        if len(parts) == 3:
            r, g, b = int(parts[0]), int(parts[1]), int(parts[2])
            if all(0 <= v <= 255 for v in (r, g, b)):
                return (r, g, b)

    raise ValueError(
        f"Invalid color format: {color_str}. Use hex (#RRGGBB or RRGGBB) or RGB (R,G,B) format."
    )


def apply_color_to_grayscale(
    input_path: str,
    output_path: str,
    color: tuple[int, int, int]
) -> None:
    """
    Apply a color to a grayscale image using HSV color space.

    The grayscale values are used as the Value (V) channel,
    while the Hue (H) and Saturation (S) come from the chosen color.

    Args:
        input_path: Path to the input grayscale image
        output_path: Path to save the colorized output image
        color: RGB tuple (R, G, B) of the color to apply
    """

    # Read image with alpha channel
    image = cv2.imread(input_path, cv2.IMREAD_UNCHANGED)
    if image is None:
        raise FileNotFoundError(f"Could not read image: {input_path}")

    # Determine if image has alpha channel
    has_alpha = image.shape[2] == 4 if len(image.shape) == 3 else False
    if has_alpha:
        bgr = image[:, :, :3]
        alpha = image[:, :, 3]
    elif len(image.shape) == 2:
        # Pure grayscale image (no color channels)
        bgr = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
        alpha = None
    else:
        bgr = image
        alpha = None

    # Get the value channel from the grayscale input
    # (if it's already grayscale, all BGR channels should be equal)
    hsv_input = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    value_channel = hsv_input[:, :, 2]

    # Convert the target color to HSV to get H and S
    # OpenCV uses BGR, so reverse the RGB tuple
    color_bgr = np.array([[[color[2], color[1], color[0]]]], dtype=np.uint8)
    color_hsv = cv2.cvtColor(color_bgr, cv2.COLOR_BGR2HSV)[0, 0]

    target_h = color_hsv[0]  # Hue
    target_s = color_hsv[1]  # Saturation

    # Create the output HSV image
    height, width = value_channel.shape
    output_hsv = np.zeros((height, width, 3), dtype=np.uint8)
    output_hsv[:, :, 0] = target_h       # Apply target Hue
    output_hsv[:, :, 1] = target_s       # Apply target Saturation
    output_hsv[:, :, 2] = value_channel  # Keep original Value from grayscale

    # Convert back to BGR
    output_bgr = cv2.cvtColor(output_hsv, cv2.COLOR_HSV2BGR)
    if alpha is not None:
        # Combine with alpha channel
        output = np.zeros((height, width, 4), dtype=np.uint8)
        output[:, :, :3] = output_bgr
        output[:, :, 3] = alpha
    else:
        output = output_bgr

    # Save the output image
    cv2.imwrite(output_path, output)
    print(f"Saved colorized image to: {output_path}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Apply a color to a grayscale image using HSV, preserving alpha."
    )

    parser.add_argument("input", type=str, help="Path to the input grayscale image")

    parser.add_argument(
        "-c", "--color",
        type=str,
        required=True,
        help="Color to apply in hex (#RRGGBB or RRGGBB) or RGB (R,G,B) format"
    )

    parser.add_argument(
        "-o", "--output",
        type=str,
        default=None,
        help="Path for the output image (default: input_colorized.png)"
    )

    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        print(f"Error: Input file does not exist: {input_path}", file=sys.stderr)
        sys.exit(1)

    try:
        color = parse_color(args.color)
    except ValueError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    if args.output:
        output_path = args.output
    else:
        output_path = str(input_path.parent / f"{input_path.stem}_colorized.png")

    apply_color_to_grayscale(str(input_path), output_path, color)


if __name__ == "__main__":
    main()

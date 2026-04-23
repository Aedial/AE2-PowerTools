#!/usr/bin/env python3
"""
Extract the Value (V) channel from HSV color space and output a grayscale image.
Preserves the original alpha channel.
"""

import argparse
import sys
from pathlib import Path

import cv2
import numpy as np


def extract_value_to_grayscale(input_path: str, output_path: str) -> None:
    """
    Extract the V channel from an image and save as grayscale with alpha.

    Args:
        input_path: Path to the input image (supports PNG with transparency)
        output_path: Path to save the grayscale output image
    """

    # Read image with alpha channel (IMREAD_UNCHANGED preserves alpha)
    image = cv2.imread(input_path, cv2.IMREAD_UNCHANGED)
    if image is None:
        raise FileNotFoundError(f"Could not read image: {input_path}")

    # Determine if image has alpha channel
    has_alpha = image.shape[2] == 4 if len(image.shape) == 3 else False
    if has_alpha:
        # Split into BGR and Alpha
        bgr = image[:, :, :3]
        alpha = image[:, :, 3]
    else:
        bgr = image
        alpha = None

    # Convert BGR to HSV
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)

    # Extract the Value channel (index 2)
    value_channel = hsv[:, :, 2]
    if alpha is not None:
        # Create grayscale image with alpha (RGBA where R=G=B=V)
        output = np.zeros((image.shape[0], image.shape[1], 4), dtype=np.uint8)
        output[:, :, 0] = value_channel  # B
        output[:, :, 1] = value_channel  # G
        output[:, :, 2] = value_channel  # R
        output[:, :, 3] = alpha          # A
    else:
        # Just output the grayscale value channel
        output = value_channel

    # Save the output image
    cv2.imwrite(output_path, output)
    print(f"Saved grayscale image to: {output_path}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Extract HSV Value channel to grayscale, preserving alpha."
    )

    parser.add_argument("input", type=str, help="Path to the input image")

    parser.add_argument(
        "-o", "--output",
        type=str,
        default=None,
        help="Path for the output image (default: input_grayscale.png)"
    )

    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        print(f"Error: Input file does not exist: {input_path}", file=sys.stderr)
        sys.exit(1)

    if args.output:
        output_path = args.output
    else:
        output_path = str(input_path.parent / f"{input_path.stem}_grayscale.png")

    extract_value_to_grayscale(str(input_path), output_path)


if __name__ == "__main__":
    main()

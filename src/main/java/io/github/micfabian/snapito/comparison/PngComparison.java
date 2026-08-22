package io.github.micfabian.snapito.comparison;

import io.github.micfabian.snapito.SnapshotDiff;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

public class PngComparison extends BinaryComparison {
  public enum Mode {
    PIXEL, SIZE
  }

  private Mode comparisonMode = Mode.SIZE;
  private int channelTolerance = 0;
  private double maxDifferentPixelRatio = 0.0d;

  public PngComparison() {
    super("png");
  }

  public PngComparison(Mode mode) {
    super("png");
    this.comparisonMode = mode;
  }

  public static PngComparison withMode(Mode mode) {
    return new PngComparison(mode);
  }

  public Mode getComparisonMode() {
    return comparisonMode;
  }

  public PngComparison tolerating(int channelDelta) {
    return tolerating(channelDelta, 0.0d);
  }

  public PngComparison tolerating(int channelDelta, double differentPixelRatio) {
    channelTolerance = Math.max(0, channelDelta);
    maxDifferentPixelRatio = Math.max(0.0d, Math.min(1.0d, differentPixelRatio));
    return this;
  }

  @Override
  public Object beforeComparison(Object input) {
    BufferedImage image = readImage(toBytes(input));
    if (comparisonMode == Mode.PIXEL) {
      return pixelData(image);
    }
    return new Dimensions(image.getWidth(), image.getHeight());
  }

  @Override
  public Object afterRestore(byte[] bytes) {
    return bytes;
  }

  @Override
  public boolean matches(Object expected, Object actual) {
    if (comparisonMode == Mode.SIZE) {
      return Objects.equals(expected, actual);
    }
    PixelData left = asPixelData(expected);
    PixelData right = asPixelData(actual);
    if (left.width() != right.width() || left.height() != right.height()) {
      return false;
    }
    int different = differentPixels(left.pixels(), right.pixels());
    return different <= Math.floor(left.pixels().length * maxDifferentPixelRatio);
  }

  @Override
  public String describeDifference(Object expected, Object actual) {
    if (comparisonMode == Mode.SIZE) {
      return SnapshotDiff.describe(expected, actual);
    }
    PixelData left = asPixelData(expected);
    PixelData right = asPixelData(actual);
    if (left.width() != right.width() || left.height() != right.height()) {
      return "PNG dimensions differ: expected " + left.width() + "x" + left.height()
        + ", but was " + right.width() + "x" + right.height();
    }
    int count = differentPixels(left.pixels(), right.pixels());
    double ratio = left.pixels().length == 0 ? 0.0d : count / (double) left.pixels().length;
    return "PNG pixels differ: " + count + "/" + left.pixels().length
      + " (" + String.format(Locale.ROOT, "%.4f%%", ratio * 100.0d) + "), allowed "
      + String.format(Locale.ROOT, "%.4f%%", maxDifferentPixelRatio * 100.0d)
      + " with channel tolerance " + channelTolerance;
  }

  @Override
  public Map<String, byte[]> differenceArtifacts(byte[] expectedBytes, byte[] actualBytes) {
    if (comparisonMode != Mode.PIXEL) {
      return Map.of();
    }
    BufferedImage expected = readImage(expectedBytes);
    BufferedImage actual = readImage(actualBytes);
    int width = Math.max(expected.getWidth(), actual.getWidth());
    int height = Math.max(expected.getHeight(), actual.getHeight());
    BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (x >= expected.getWidth() || y >= expected.getHeight()
          || x >= actual.getWidth() || y >= actual.getHeight()) {
          diff.setRGB(x, y, Color.MAGENTA.getRGB());
          continue;
        }
        int left = expected.getRGB(x, y);
        int right = actual.getRGB(x, y);
        diff.setRGB(x, y, pixelDifferent(left, right) ? Color.RED.getRGB() : (right & 0x33FFFFFF));
      }
    }

    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ImageIO.write(diff, "png", output);
      return Map.of(".diff.png", output.toByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private int differentPixels(int[] expected, int[] actual) {
    int count = Math.abs(expected.length - actual.length);
    int shared = Math.min(expected.length, actual.length);
    for (int index = 0; index < shared; index++) {
      if (pixelDifferent(expected[index], actual[index])) {
        count++;
      }
    }
    return count;
  }

  private boolean pixelDifferent(int left, int right) {
    return channelDifference(left >>> 24, right >>> 24) > channelTolerance
      || channelDifference(left >>> 16, right >>> 16) > channelTolerance
      || channelDifference(left >>> 8, right >>> 8) > channelTolerance
      || channelDifference(left, right) > channelTolerance;
  }

  private static int channelDifference(int left, int right) {
    return Math.abs((left & 0xFF) - (right & 0xFF));
  }

  static BufferedImage readImage(byte[] bytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new IllegalArgumentException("PngComparison input must be a valid PNG image");
      }
      return image;
    } catch (IOException e) {
      throw new IllegalArgumentException("PngComparison input must be a valid PNG image", e);
    }
  }

  private static PixelData pixelData(BufferedImage image) {
    return new PixelData(
      image.getWidth(),
      image.getHeight(),
      image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
  }

  private static PixelData asPixelData(Object value) {
    if (value instanceof PixelData pixels) {
      return pixels;
    }
    if (value instanceof byte[] bytes) {
      return pixelData(readImage(bytes));
    }
    throw new IllegalArgumentException(
      "PNG comparison expected decoded pixels but got " + (value == null ? "null" : value.getClass()));
  }

  private static byte[] toBytes(Object input) {
    if (input instanceof byte[] bytes) {
      return bytes;
    }
    throw new IllegalArgumentException(
      "PngComparison input must provide PNG bytes, input was " + (input == null ? "null" : input.getClass()));
  }

  private record PixelData(int width, int height, int[] pixels) {
    @Override
    public boolean equals(Object other) {
      return other instanceof PixelData that
        && width == that.width
        && height == that.height
        && java.util.Arrays.equals(pixels, that.pixels);
    }

    @Override
    public int hashCode() {
      return 31 * (31 * width + height) + java.util.Arrays.hashCode(pixels);
    }

    @Override
    public String toString() {
      return "PixelData(" + width + "x" + height + ", " + pixels.length + " pixels)";
    }
  }

  public record Dimensions(int width, int height) {
    @Override
    public String toString() {
      return "Dimensions(width=" + width + ", height=" + height + ")";
    }
  }
}

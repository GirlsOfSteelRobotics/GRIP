package edu.wpi.grip.core.operations.composite;

import com.google.common.base.MoreObjects;
import edu.wpi.grip.core.operations.network.PublishValue;
import edu.wpi.grip.core.operations.network.Publishable;
import edu.wpi.grip.core.sockets.NoSocketTypeLabel;
import edu.wpi.grip.core.sockets.Socket;
import org.bytedeco.javacpp.opencv_core.Point;
import org.bytedeco.javacpp.opencv_core.Size;

import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_imgproc.clipLine;

/**
 * This class contains the results of the RANSAC line detection algorithm. It has an input matrix (the image
 * supplied to the algorithm) and the threshold used for RANSAC. The primary output is the line representing
 * the best fit found by RANSAC, but the count of inliers and outliers is also available.
 * This is used by FindLineInBlobs as the type of its output socket, allowing other classes (like
 * GUI previews and line filtering operations) to have a type-safe way of operating on line
 * detection results and not just any random matrix.
 */
@NoSocketTypeLabel
public class RansacLineReport implements Publishable {
  private final Mat input;
  private final int threshold;
  private final int inliers;
  private final int outliers;
  private final Line line;

  /**
   * Construct an empty report.  This is used as a default value for {@link Socket}s containing
   * these reports.
   */
  public RansacLineReport() {
    this(new Mat(), 0, 0, 0, new Line(0,0,0,0));
  }

  /**
   * @param input The input matrix.
   * @param threshold The threshold used during the line detection operation.
   * @param inliers The count of blobs whose distance from line is less than or equal to threshold
   * @param outliers The count of blobs whose distance from line is greater than threshold
   * @param line The best line that has been found.
   */
  public RansacLineReport(Mat input, int threshold, int inliers, int outliers, Line line) {
    this.input = input;
    this.threshold = threshold;
    this.inliers = inliers;
    this.outliers = outliers;
    this.line = line;
  }

  /**
   * @return The original image that the line detection was performed on.
   */
  public Mat getInput() {
    return this.input;
  }

  /**
   * @return The original threshold used during the line detection operation.
   */
  public int getThreshold() { return this.threshold; }

  /**
   * @return The number of inliers, the blobs whose distance from line is <= threshold.
   */
  public int getInliers() { return this.inliers; }

  /**
   * @return The number of outliers, the blobs whose distance from line is > threshold.
   */
  public int getOutliers() { return this.outliers; }

  /**
   * @return The best fit line that was found for the input blobs.
   */
  public Line getLine() { return this.line; }

  @PublishValue(key = "x1", weight = 0)
  public double getX1() {
    return line.x1;
  }

  @PublishValue(key = "y1", weight = 1)
  public double getY1() {
    return line.y1;
  }

  @PublishValue(key = "x2", weight = 2)
  public double getX2() {
    return line.x2;
  }

  @PublishValue(key = "y2", weight = 3)
  public double getY2() {
    return line.y2;
  }

  @PublishValue(key = "angle", weight = 4)
  public double getAngle() {
    return line.angle();
  }

  @PublishValue(key = "height", weight = 5)
  public double getHeight() {
    return line.height();
  }

  @Override
  public String toString() {
    return line.toString();
  }

  public static class Line {
    public final double x1;
    public final double y1;
    public final double x2;
    public final double y2;

    Line(double x1, double y1, double x2, double y2) {
      this.x1 = x1;
      this.y1 = y1;
      this.x2 = x2;
      this.y2 = y2;
    }

    public double angle() {
      return Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
    }

    public double height() {
      return (y2 + y1) / 2.0;
    }

    /**
     * Construct a new line that's co-linear with this one but extends
     * to the edges of an image that is maxX pixels high by maxY pixels wide.
     *
     * @return Line that represents the extension of this line or
     * null if the line is completely outside (0,0) to (maxX, maxY) rectangle.
     */
    public Line extendedLine(double maxX, double maxY) {
      Line extended;
      if (x1 == x2) {
        // Special case if line is vertical (infinite slope)
        // x values are the same as the (matching) x values in the original line
        // y values are 0 and maxY, top and bottom of the image
        extended = new Line(x1, 0, x1, maxY);
      } else {
        // General case where line can be written as y = mx + b
        double m = (y1 - y2) / (x1 - x2);
        double b = y1 - (m * x1);
        // x values are 0 and maxX, the left and right of the image
        // y values are calculated as the y-intercepts at x=0 and x=maxX
        Point pt1 = new Point(0, (int) (m * 0 + b));
        Point pt2 = new Point((int) maxX, (int) (m * maxX + b));
        // Either of the calculated points may be outside the image rectangle (< 0 or > max)
        // clipLine will change pt1 and/or pt2 to bring them back into the image, if necessary
        if (clipLine(new Size((int) maxX, (int) maxY), pt1, pt2)) {
          extended = new Line(pt1.x(), pt1.y(), pt2.x(), pt2.y());
        } else {
          // clipLine reports that the resulting line is completely outside the image rectangle
          extended = null;
        }
      }
      return extended;
    }

    /**
     * Return a line that is parallel to the original line but offset by the given
     * perpendicular distance. Positive or negative offsets are allowed. Before drawing
     * the resulting line, call extendedLine() to extend and clip the line to the image rectangle.
     *
     * @param offset Perpendicular distance from the original line to the returned line
     *
     * @return Line The parallel line
     */
    public Line offsetLine(double offset) {
      Line offsetLine;
      if (x1 == x2) {
        // Special case if line is vertical (infinite slope)
        // x values are offset from the (matching) x values in the original line
        // y values are unchanged
        offsetLine = new Line(x1 + offset, y1, x2 + offset, y2);
      } else {
        /* General case where line can be written as y = mx + b
         * Create the offset line by shifting the y values up or down by the appropriate amount.
         * The given offset is the perpendicular distance, so do some geometry to calculate the
         * change in y direction.
         */
        double yOffset = offset / Math.cos(Math.toRadians(angle()));
        // x values are unchanged
        // y values are shifted from the original values by yOffset
        offsetLine = new Line(x1, y1 + yOffset, x2, y2 + yOffset);
      }
      return offsetLine;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
              .add("x1", x1)
              .add("y1", y1)
              .add("x2", x2)
              .add("y2", y2)
              .toString();
    }

  }
}

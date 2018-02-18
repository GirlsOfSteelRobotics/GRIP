package edu.wpi.grip.ui.preview;

import edu.wpi.grip.core.operations.composite.BlobsReport;
import edu.wpi.grip.core.operations.composite.RansacLineReport;
import edu.wpi.grip.core.sockets.OutputSocket;
import edu.wpi.grip.ui.util.GripPlatform;
import edu.wpi.grip.ui.util.ImageConverter;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.util.List;

import static org.bytedeco.javacpp.opencv_core.bitwise_xor;
import static org.bytedeco.javacpp.opencv_core.LINE_8;
import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.Point;
import static org.bytedeco.javacpp.opencv_core.Scalar;
import static org.bytedeco.javacpp.opencv_imgproc.CV_GRAY2BGR;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.circle;
import static org.bytedeco.javacpp.opencv_imgproc.line;

/**
 * A <code>SocketPreviewView</code> that previews sockets containing containing the
 * result of the RANSAC line detection algorithm.
 */
public final class RansacLineSocketPreviewView extends ImageBasedPreviewView<RansacLineReport> {

  private final ImageConverter imageConverter = new ImageConverter();
  private final ImageView imageView = new ImageView();
  private final Label infoLabel = new Label();
  private final Mat tmp = new Mat();
  private final GripPlatform platform;
  @SuppressWarnings("PMD.ImmutableField")
  private boolean showInputImage = false;

  /**
   * @param socket An output socket to preview.
   */
  public RansacLineSocketPreviewView(GripPlatform platform, OutputSocket<RansacLineReport> socket) {
    super(socket);
    this.platform = platform;

    // Add a checkbox to set if the preview should just show the lines, or also the input image
    final CheckBox show = new CheckBox("Show Input Image");
    show.setSelected(this.showInputImage);
    show.selectedProperty().addListener(observable -> {
      synchronized (this) {
        this.showInputImage = show.isSelected();
        this.convertImage();
      }
    });

    final VBox content = new VBox(this.imageView, new Separator(Orientation.HORIZONTAL), this
        .infoLabel, show);
    content.getStyleClass().add("preview-box");
    this.setContent(content);
  }

  private void drawLine(Mat image, RansacLineReport.Line line, Scalar color, int thickness) {
    final Point startPoint = new Point();
    final Point endPoint = new Point();

    // Extended lines are set to null if the entire line is outside the bounds of the image
    if (line != null) {
      startPoint.x((int) line.x1);
      startPoint.y((int) line.y1);
      endPoint.x((int) line.x2);
      endPoint.y((int) line.y2);
      line(image, startPoint, endPoint, color, thickness, LINE_8, 0);
    }
  }

  private void drawBlob(Mat image, BlobsReport.Blob blob, Scalar color) {
    final Point point = new Point();

    point.x((int) blob.x);
    point.y((int) blob.y);
    circle(image, point, 2, color, 2, LINE_8, 0);
  }

  @Override
  protected void convertImage() {
    synchronized (this) {
      final RansacLineReport lineReport = this.getSocket().getValue().get();
      Mat input = lineReport.getInput();
      final int threshold = lineReport.getThreshold();
      final List<BlobsReport.Blob> inliers = lineReport.getInliers();
      final List<BlobsReport.Blob> outliers = lineReport.getOutliers();
      final RansacLineReport.Line line = lineReport.getLine();
      final RansacLineReport.Line extended = line.extendedLine(input.cols(), input.rows());
      final RansacLineReport.Line aboveThresh = line.offsetLine(+threshold).extendedLine(input.cols(), input.rows());
      final RansacLineReport.Line belowThresh = line.offsetLine(-threshold).extendedLine(input.cols(), input.rows());

      if (input.channels() == 3) {
        input.copyTo(tmp);
      } else {
        cvtColor(input, tmp, CV_GRAY2BGR);
      }

      input = tmp;

      // If we don't want to see the background image, set it to black
      if (!this.showInputImage) {
        bitwise_xor(tmp, tmp, tmp);
      }

      // Draw all the inlier blobs in green and outliers in red
      for (BlobsReport.Blob blob : inliers) {
        drawBlob(input, blob, Scalar.GREEN);
      }
      for (BlobsReport.Blob blob : outliers) {
        drawBlob(input, blob, Scalar.RED);
      }

      // If a line was found, draw it on the image before displaying it
      if (inliers.size() > 1) {
        // Draw the starting and ending points for the line
        drawLine(input, extended, Scalar.BLUE, 2);
        // Draw parallel lines to visually indicate the threshold zone
        drawLine(input, aboveThresh, Scalar.GREEN, 1);
        drawLine(input, belowThresh, Scalar.GREEN, 1);
      }
      final Mat convertInput = input;
      platform.runAsSoonAsPossible(() -> {
        final Image image = this.imageConverter.convert(convertInput, getImageHeight());
        this.imageView.setImage(image);
        this.infoLabel.setText("Found " + inliers.size() + " inliers and " + outliers.size() + " outliers");
      });
    }
  }
}

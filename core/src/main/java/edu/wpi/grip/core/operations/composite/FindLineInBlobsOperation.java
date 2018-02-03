package edu.wpi.grip.core.operations.composite;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import edu.wpi.grip.core.Description;
import edu.wpi.grip.core.Operation;
import edu.wpi.grip.core.OperationDescription;
import edu.wpi.grip.core.sockets.InputSocket;
import edu.wpi.grip.core.sockets.OutputSocket;
import edu.wpi.grip.core.sockets.SocketHint;
import edu.wpi.grip.core.sockets.SocketHints;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.javacpp.opencv_imgproc.*;

/**
 * Find the best line in a set of blobs using the RANSAC algorithm.
 */
@Description(name = "Find Line In Blobs",
             summary = "Find a best fit line in a set of blobs, ignoring outliers, using the RANSAC algorithm",
             category = OperationDescription.Category.FEATURE_DETECTION,
             iconName = "find-line-in-blobs")
public class FindLineInBlobsOperation implements Operation {

  private final SocketHint<BlobsReport> inputHint =
          new SocketHint.Builder<>(BlobsReport.class).identifier("Blobs").build();

  private final SocketHint<Number> thresholdHint = SocketHints.Inputs
          .createNumberSliderSocketHint("Threshold", 1, 1, 150);

  private final SocketHint<Number> iterationsHint = SocketHints.Inputs
          .createNumberSliderSocketHint("Max Iterations", 0, 0, 200);

  private final SocketHint<Number> pctInliersHint = SocketHints.Inputs
          .createNumberSliderSocketHint("Stop at % Inliers", 100, 0, 100);

  private final SocketHint<LinesReport> linesHint = new SocketHint.Builder<>(LinesReport.class)
          .identifier("Lines").initialValueSupplier(LinesReport::new).build();


  private final InputSocket<BlobsReport> inputSocket;
  private final InputSocket<Number> thresholdSocket;
  private final InputSocket<Number> iterationsSocket;
  private final InputSocket<Number> pctInliersSocket;

  private final OutputSocket<LinesReport> linesReportSocket;

  @Inject
  public FindLineInBlobsOperation(InputSocket.Factory inputSocketFactory, OutputSocket.Factory
      outputSocketFactory) {
    this.inputSocket = inputSocketFactory.create(inputHint);
    this.thresholdSocket = inputSocketFactory.create(thresholdHint);
    this.iterationsSocket = inputSocketFactory.create(iterationsHint);
    this.pctInliersSocket = inputSocketFactory.create(pctInliersHint);

    this.linesReportSocket = outputSocketFactory.create(linesHint);
  }

  @Override
  public List<InputSocket> getInputSockets() {
    return ImmutableList.of(
        inputSocket,
        thresholdSocket,
        iterationsSocket,
        pctInliersSocket
    );
  }

  @Override
  public List<OutputSocket> getOutputSockets() {
    return ImmutableList.of(
        linesReportSocket
    );
  }

  @Override
  @SuppressWarnings("unchecked")
  public void perform() {
    final BlobsReport input = inputSocket.getValue().get();
    final int threshold = thresholdSocket.getValue().get().intValue();
    final double iterations = iterationsSocket.getValue().get().doubleValue();
    final double pctInliers = pctInliersSocket.getValue().get().doubleValue();

    final LineSegmentDetector lsd = linesReportSocket.getValue().get().getLineSegmentDetector();

    // Store the lines in the LinesReport object
    List<LinesReport.Line> lineList = new ArrayList<>();
    if (input.getBlobs().size() > threshold) {
      BlobsReport.Blob first = input.getBlobs().get(0);
      BlobsReport.Blob middle = input.getBlobs().get(threshold);
      BlobsReport.Blob last =  input.getBlobs().get(input.getBlobs().size()-1);
      lineList.add(new LinesReport.Line(first.x, first.y, middle.x, middle.y));
      lineList.add(new LinesReport.Line(middle.x, middle.y, last.x, last.y));
    }

    linesReportSocket.setValue(new LinesReport(lsd, input.getInput(), lineList));
  }
}

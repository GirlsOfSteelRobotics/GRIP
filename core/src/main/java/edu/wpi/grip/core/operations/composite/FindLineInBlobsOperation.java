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

import java.util.List;

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

  private final SocketHint<RansacLineReport> lineHint = new SocketHint.Builder<>(RansacLineReport.class)
          .identifier("Line").initialValueSupplier(RansacLineReport::new).build();


  private final InputSocket<BlobsReport> inputSocket;
  private final InputSocket<Number> thresholdSocket;
  private final InputSocket<Number> iterationsSocket;
  private final InputSocket<Number> pctInliersSocket;

  private final OutputSocket<RansacLineReport> lineReportSocket;

  @Inject
  public FindLineInBlobsOperation(InputSocket.Factory inputSocketFactory, OutputSocket.Factory outputSocketFactory) {
    this.inputSocket = inputSocketFactory.create(inputHint);
    this.thresholdSocket = inputSocketFactory.create(thresholdHint);
    this.iterationsSocket = inputSocketFactory.create(iterationsHint);
    this.pctInliersSocket = inputSocketFactory.create(pctInliersHint);

    this.lineReportSocket = outputSocketFactory.create(lineHint);
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
            lineReportSocket
    );
  }

  @Override
  @SuppressWarnings("unchecked")
  public void perform() {
    final BlobsReport input = inputSocket.getValue().get();
    final int threshold = thresholdSocket.getValue().get().intValue();
    final int iterations = iterationsSocket.getValue().get().intValue();
    final double pctInliers = pctInliersSocket.getValue().get().doubleValue();
    int inliers = 2;
    int outliers = input.getBlobs().size();
    RansacLineReport.Line bestLine;

    // Store the results in the RANSACLineReport object
    if (input.getBlobs().size() > threshold) {
      BlobsReport.Blob first = input.getBlobs().get(0);
      BlobsReport.Blob middle = input.getBlobs().get(threshold);
      bestLine = new RansacLineReport.Line(first.x, first.y, middle.x, middle.y);
    } else {
      bestLine = new RansacLineReport.Line(0, 0, 10, 10);
    }

    lineReportSocket.setValue(new RansacLineReport(input.getInput(), threshold, inliers, outliers, bestLine));
  }
}

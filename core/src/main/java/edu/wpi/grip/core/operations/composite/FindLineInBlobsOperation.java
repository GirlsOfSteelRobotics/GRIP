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
import java.util.Random;

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
          .createNumberSliderSocketHint("Threshold", 10, 1, 150);

  private final SocketHint<Number> iterationsHint = SocketHints.Inputs
          .createNumberSliderSocketHint("Max Iterations", 40, 0, 75);

  private final SocketHint<Number> pctInliersHint = SocketHints.Inputs
          .createNumberSliderSocketHint("Min % Inliers", 0, 0, 100);

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

  //Finds distance between a line (defined by blobs line1 and line2) and a point (blob)
  private double findDistance(BlobsReport.Blob line1, BlobsReport.Blob line2, BlobsReport.Blob blob) {
    return ((Math.abs(((line2.y - line1.y)*blob.x) - ((line2.x - line1.x)*blob.y) + ((line2.x*line1.y)-(line2.y*line1.x))))
            /(Math.sqrt(((line2.y - line1.y)*(line2.y - line1.y)) + ((line2.x - line1.x)*(line2.x - line1.x)))));
  }

  @Override
  @SuppressWarnings("unchecked")
  public void perform() {
    final BlobsReport input = inputSocket.getValue().get();
    final int threshold = thresholdSocket.getValue().get().intValue();
    final int iterations = iterationsSocket.getValue().get().intValue();
    final double pctInliers = pctInliersSocket.getValue().get().doubleValue();
    final List<BlobsReport.Blob> blobs = input.getBlobs();
    final int blobCount = blobs.size();
    // Variables for storing details on the best fitting line we've seen yet
    double bestScore = Double.MAX_VALUE;
    int bestInliers = 0;
    RansacLineReport.Line bestLine = new RansacLineReport.Line(0, 0, 0, 0);

    // We need at least two blobs to form a line, so return a zeroed out report if we don't have enough
    if (blobCount >= 2) {
      // Repeat the process at most "iterations" times
      for (int i = 0; i < iterations; i++) {
        // Pick two random blobs, making sure we don't pick the same one twice
        Random rand = new Random();
        int blobIdx1 = rand.nextInt(blobCount);
        int blobIdx2 = rand.nextInt(blobCount);
        if (blobIdx1 == blobIdx2) {
          blobIdx2 = (blobIdx2 + 1) % blobCount;
        }
        // For all blobs, calculate the distance from this blob to a line defined by the two blobs selected above
        // The sum of those distances becomes the score for this pair of randomly selected blobs
        double score = 0.0;
        int inliers = 0;
        for (int blobIndex = 0; blobIndex < blobCount; blobIndex++) {
          // Small optimization: we know the distance to either of the selected blobs is zero
          if (blobIndex == blobIdx1 || blobIndex == blobIdx2) {
            continue;
          }
          double distance = findDistance(blobs.get(blobIdx1), blobs.get(blobIdx2), blobs.get(blobIndex));
          if (distance < threshold) {
            score += distance;
            inliers++;
          } else {
            score += threshold;
          }
        }
        // Does this line have a better score than any we've seen yet?
        // Does it also meet our requirements for the minimum percentage of inlier blobs?
        // If so, save it as the best we've seen.
        if (score < bestScore && ((double) inliers / (double) blobCount) >= (pctInliers/100.0)) {
          bestScore = score;
          bestInliers = inliers;
          bestLine = new RansacLineReport.Line(blobs.get(blobIdx1).x, blobs.get(blobIdx1).y,
                  blobs.get(blobIdx2).x, blobs.get(blobIdx2).y);
        }
      }
    }
    // Store the results in the RANSACLineReport object
    lineReportSocket.setValue(new RansacLineReport(input.getInput(), threshold,
            bestInliers, blobCount - bestInliers, bestLine));
  }
}

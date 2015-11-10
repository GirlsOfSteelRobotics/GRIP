package edu.wpi.grip.ui;

import com.google.common.eventbus.EventBus;
import edu.wpi.grip.core.Operation;
import edu.wpi.grip.core.Step;
import edu.wpi.grip.core.events.StepAddedEvent;
import edu.wpi.grip.ui.util.DPIUtility;
import edu.wpi.grip.ui.util.StyleClassNameUtility;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A JavaFX control that renders information about an {@link Operation}.  This is used in the palette view to present
 * the user with information on the various operations to choose from.
 */
public class OperationView extends GridPane implements Initializable {
    private final EventBus eventBus;
    private final Operation operation;

    @FXML
    private Label name;

    @FXML
    private Label description;

    @FXML
    private ImageView icon;

    public OperationView(EventBus eventBus, Operation operation) {
        checkNotNull(eventBus);
        checkNotNull(operation);

        this.eventBus = eventBus;
        this.operation = operation;

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Operation.fxml"));
            fxmlLoader.setRoot(this);
            fxmlLoader.setController(this);
            fxmlLoader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.eventBus.register(this);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.setId(StyleClassNameUtility.idNameFor(this.operation));
        this.name.setText(this.operation.getName());
        this.description.setText(this.operation.getDescription());
        this.description.setMaxHeight(DPIUtility.LARGE_ICON_SIZE);

        final Tooltip tooltip = new Tooltip(this.operation.getDescription());
        tooltip.setPrefWidth(400.0);
        tooltip.setWrapText(true);
        Tooltip.install(this, tooltip);

        this.description.setAccessibleHelp(this.operation.getDescription());

        this.operation.getIcon().ifPresent(icon -> this.icon.setImage(new Image(icon)));

        // Make the icon a fixed width and height in inches.  It would be cleaner to define this in CSS, but JavaFX
        // doesn't allow fitWidth or fitHeight to be used from CSS, and defining it in FXML would not allow it to be
        // set dynamically based on DPI.
        this.icon.setFitWidth(DPIUtility.LARGE_ICON_SIZE);
        this.icon.setFitHeight(DPIUtility.LARGE_ICON_SIZE);


        // When the user clicks the operation, add a new step.
        this.setOnMouseClicked(mouseEvent -> {
            Step step = new Step(this.eventBus, this.operation);
            this.eventBus.post(new StepAddedEvent(step));
        });


        // Ensures that when this element is hidden that it also removes its size calculations
        this.managedProperty().bind(this.visibleProperty());
    }


    public Operation getOperation() {
        return operation;
    }
}

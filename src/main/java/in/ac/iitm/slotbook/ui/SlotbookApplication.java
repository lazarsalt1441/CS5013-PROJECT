package in.ac.iitm.slotbook.ui;

import in.ac.iitm.slotbook.io.SlotbookExporter;
import in.ac.iitm.slotbook.model.AssignmentExplanation;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.service.SlotbookRun;
import in.ac.iitm.slotbook.service.SlotbookService;
import in.ac.iitm.slotbook.solver.SolverChoice;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** Small JavaFX front end for import, solve, inspect, and export. */
public final class SlotbookApplication extends Application {
    private final ComboBox<SolverChoice> solverChoice =
            new ComboBox<>(FXCollections.observableArrayList(SolverChoice.values()));
    private final SlotbookExporter exporter = new SlotbookExporter();
    private final TableView<CourseRow> table = new TableView<>();
    private final TextArea explanation = new TextArea();
    private final Label status = new Label("Load a dataset to begin.");
    private final TextField datasetPath = new TextField("data/feasible");
    private SlotbookRun currentRun;

    @Override
    public void start(Stage stage) {
        stage.setTitle("IITM Explainable Slotbook");
        configureTable();
        explanation.setEditable(false);
        explanation.setWrapText(true);
        explanation.setPromptText("Select a solved course to inspect its evidence-backed explanation.");

        Button browse = new Button("Browse");
        browse.setOnAction(event -> browse(stage));
        Button solve = new Button("Generate slotbook");
        solve.setDefaultButton(true);
        solve.setOnAction(event -> solve());
        Button export = new Button("Export CSV");
        export.setOnAction(event -> export(stage));

        solverChoice.getSelectionModel().select(SolverChoice.UNITIME);
        HBox inputBar = new HBox(8, new Label("Dataset:"), datasetPath, browse,
                new Label("Solver:"), solverChoice, solve, export);
        HBox.setHgrow(datasetPath, Priority.ALWAYS);
        inputBar.setPadding(new Insets(12));

        SplitPane split = new SplitPane(table, explanation);
        split.setDividerPositions(0.62);
        VBox center = new VBox(8, split, status);
        VBox.setVgrow(split, Priority.ALWAYS);
        center.setPadding(new Insets(0, 12, 12, 12));

        BorderPane root = new BorderPane(center);
        root.setTop(inputBar);
        stage.setScene(new Scene(root, 1180, 680));
        stage.show();
    }

    private void configureTable() {
        TableColumn<CourseRow, String> id = column("Course", CourseRow::courseId);
        TableColumn<CourseRow, String> title = column("Title", CourseRow::title);
        title.setPrefWidth(270);
        TableColumn<CourseRow, String> group = column("Group", CourseRow::semesterGroup);
        group.setPrefWidth(120);
        TableColumn<CourseRow, String> instructor = column("Instructor", CourseRow::instructor);
        instructor.setPrefWidth(140);
        TableColumn<CourseRow, String> slot = column("Assigned slot", CourseRow::assignedSlot);
        TableColumn<CourseRow, String> historical = column("Historical", CourseRow::historicalSlot);
        table.getColumns().setAll(List.of(id, title, group, instructor, slot, historical));
        table.getSelectionModel().selectedItemProperty().addListener((ignored, oldValue, selected) -> {
            if (selected == null || currentRun == null) return;
            AssignmentExplanation details = currentRun.solution().explanations().get(selected.courseId());
            explanation.setText(details == null ? "No explanation available." : details.asText());
        });
    }

    private TableColumn<CourseRow, String> column(
            String title, java.util.function.Function<CourseRow, String> getter) {
        TableColumn<CourseRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
        return column;
    }

    private void browse(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose slotbook dataset directory");
        File selected = chooser.showDialog(stage);
        if (selected != null) datasetPath.setText(selected.getAbsolutePath());
    }

    private void solve() {
        try {
            SlotbookService service = new SlotbookService(
                    solverChoice.getValue(), SlotbookService.DEFAULT_TIME_LIMIT_SECONDS);
            currentRun = service.run(Path.of(datasetPath.getText()));
            if (!currentRun.solution().hasSolution()) {
                table.setItems(FXCollections.observableArrayList());
                explanation.setText(String.join("\n", currentRun.solution().diagnostics()));
                status.setText("Status: " + currentRun.solution().status());
                return;
            }
            table.setItems(FXCollections.observableArrayList(currentRun.problem().courses().stream()
                    .map(course -> new CourseRow(
                            course.id(), course.title(), course.semesterGroup(), course.instructor(),
                            currentRun.solution().assignments().get(course.id()), course.historicalSlot()))
                    .toList()));
            status.setText(solverChoice.getValue() + " | status: " + currentRun.solution().status()
                    + " | objective: " + currentRun.solution().objectiveValue()
                    + " | runtime: " + String.format("%.3f s", currentRun.solution().runtimeSeconds()));
            if (!table.getItems().isEmpty()) table.getSelectionModel().selectFirst();
        } catch (Exception error) {
            showError("Unable to generate slotbook", error);
        }
    }

    private void export(Stage stage) {
        if (currentRun == null || !currentRun.solution().hasSolution()) {
            showError("Nothing to export", new IllegalStateException("Generate a feasible slotbook first."));
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose export directory");
        File selected = chooser.showDialog(stage);
        if (selected == null) return;
        try {
            exporter.export(currentRun, selected.toPath());
            status.setText("Exported slotbook and explanations to " + selected);
        } catch (Exception error) {
            showError("Export failed", error);
        }
    }

    private void showError(String header, Exception error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(error.getMessage());
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private record CourseRow(
            String courseId,
            String title,
            String semesterGroup,
            String instructor,
            String assignedSlot,
            String historicalSlot) {}
}


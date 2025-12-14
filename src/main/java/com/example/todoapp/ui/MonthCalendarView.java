package com.example.todoapp.ui;

import com.example.todoapp.todo.DayStatus;
import com.example.todoapp.todo.TodoService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

public class MonthCalendarView {

    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TodoService todoService;

    private final VBox root;
    private final Label monthLabel;
    private final GridPane grid;

    private final ObjectProperty<YearMonth> displayedMonth = new SimpleObjectProperty<>(YearMonth.now());
    private final ObjectProperty<LocalDate> selectedDate = new SimpleObjectProperty<>(LocalDate.now());

    private Map<LocalDate, DayStatus> cachedStatuses = Map.of();

    public MonthCalendarView(TodoService todoService) {
        this.todoService = todoService;

        Button prevButton = new Button("<");
        Button nextButton = new Button(">");
        monthLabel = new Label();
        monthLabel.setMaxWidth(Double.MAX_VALUE);
        monthLabel.setAlignment(Pos.CENTER);

        Region spacerLeft = new Region();
        Region spacerRight = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        HBox header = new HBox(8, prevButton, spacerLeft, monthLabel, spacerRight, nextButton);
        header.setAlignment(Pos.CENTER);

        grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        root = new VBox(10, header, grid);
        root.setPadding(new Insets(10));

        prevButton.setOnAction(e -> displayedMonth.set(displayedMonth.get().minusMonths(1)));
        nextButton.setOnAction(e -> displayedMonth.set(displayedMonth.get().plusMonths(1)));

        displayedMonth.addListener((obs, oldVal, newVal) -> {
            refresh();
        });

        selectedDate.addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                YearMonth ym = YearMonth.from(newVal);
                if (!ym.equals(displayedMonth.get())) {
                    displayedMonth.set(ym);
                } else {
                    render();
                }
            }
        });

        refresh();
    }

    public Node getRoot() {
        return root;
    }

    public ObjectProperty<LocalDate> selectedDateProperty() {
        return selectedDate;
    }

    public LocalDate getSelectedDate() {
        return selectedDate.get();
    }

    public void setSelectedDate(LocalDate date) {
        selectedDate.set(date);
    }

    public void refresh() {
        cachedStatuses = todoService.getMonthDayStatuses(displayedMonth.get());
        render();
    }

    private void render() {
        grid.getChildren().clear();

        YearMonth month = displayedMonth.get();
        monthLabel.setText(month.format(MONTH_LABEL_FORMAT));

        addDayOfWeekHeader();
        addDays(month);
    }

    private void addDayOfWeekHeader() {
        Map<DayOfWeek, String> labels = new EnumMap<>(DayOfWeek.class);
        labels.put(DayOfWeek.MONDAY, "월");
        labels.put(DayOfWeek.TUESDAY, "화");
        labels.put(DayOfWeek.WEDNESDAY, "수");
        labels.put(DayOfWeek.THURSDAY, "목");
        labels.put(DayOfWeek.FRIDAY, "금");
        labels.put(DayOfWeek.SATURDAY, "토");
        labels.put(DayOfWeek.SUNDAY, "일");

        int col = 0;
        for (DayOfWeek dow : DayOfWeek.values()) {
            // DayOfWeek.values() starts with MONDAY in java.time
            Label label = new Label(labels.get(dow));
            label.setMinWidth(40);
            label.setAlignment(Pos.CENTER);
            grid.add(label, col, 0);
            col++;
        }
    }

    private void addDays(YearMonth month) {
        LocalDate first = month.atDay(1);
        int offset = first.getDayOfWeek().getValue() - 1; // Monday=0

        int daysInMonth = month.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            int index = offset + (day - 1);
            int col = index % 7;
            int row = (index / 7) + 1;

            grid.add(createDayCell(date), col, row);
        }
    }

    private Node createDayCell(LocalDate date) {
        Button button = new Button(String.valueOf(date.getDayOfMonth()));
        button.setMinSize(40, 36);
        button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        if (date.equals(selectedDate.get())) {
            button.setStyle("-fx-border-color: -fx-focus-color; -fx-border-width: 2;");
        } else {
            button.setStyle("-fx-border-color: transparent;");
        }

        button.setOnAction(e -> selectedDate.set(date));

        Circle marker = new Circle(4);
        marker.setVisible(false);

        DayStatus status = cachedStatuses.getOrDefault(date, DayStatus.NONE);
        if (status == DayStatus.INCOMPLETE) {
            marker.setFill(Color.ORANGE);
            marker.setVisible(true);
        } else if (status == DayStatus.ALL_DONE) {
            marker.setFill(Color.GREEN);
            marker.setVisible(true);
        }

        javafx.scene.layout.StackPane stack = new javafx.scene.layout.StackPane(button);
        stack.setMinSize(40, 36);
        stack.setAlignment(Pos.CENTER);

        javafx.scene.layout.StackPane.setAlignment(marker, Pos.BOTTOM_RIGHT);
        javafx.scene.layout.StackPane.setMargin(marker, new Insets(0, 4, 4, 0));
        stack.getChildren().add(marker);

        return stack;
    }
}

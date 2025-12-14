package com.example.todoapp.ui;

import com.example.todoapp.todo.Todo;
import com.example.todoapp.todo.TodoService;
import jakarta.validation.ConstraintViolationException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.List;

public class MainView {

    private final TodoService todoService;

    private final BorderPane root;

    private final MonthCalendarView calendarView;

    private final ListView<Todo> todoListView;

    private final TextField titleField;
    private final TextArea contentArea;
    private final CheckBox doneCheck;
    private final Button saveButton;
    private final Button newButton;
    private final Button deleteButton;
    private final Label messageLabel;

    private Long editingTodoId;

    public MainView(TodoService todoService) {
        this.todoService = todoService;

        calendarView = new MonthCalendarView(todoService);

        todoListView = new ListView<>();
        todoListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Todo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTextFill(Color.BLACK);
                    return;
                }

                setText(item.getTitle());
                if (item.isDone()) {
                    setTextFill(Color.LIGHTGRAY);
                } else {
                    setTextFill(Color.BLACK);
                }
            }
        });

        titleField = new TextField();
        contentArea = new TextArea();
        contentArea.setWrapText(true);

        doneCheck = new CheckBox("완료");

        saveButton = new Button("Save");
        newButton = new Button("새 ToDo");
        deleteButton = new Button("삭제");
        messageLabel = new Label();
        messageLabel.setTextFill(Color.FIREBRICK);

        Parent rightPane = buildRightPane();

        root = new BorderPane();
        root.setLeft(calendarView.getRoot());
        root.setCenter(new Separator(Orientation.VERTICAL));
        root.setRight(rightPane);

        BorderPane.setMargin(calendarView.getRoot(), new Insets(0));
        BorderPane.setMargin(rightPane, new Insets(0));

        wireEvents();

        // Initial load
        refreshForDate(calendarView.getSelectedDate());
    }

    public Parent getRoot() {
        return root;
    }

    private Parent buildRightPane() {
        Label listHeader = new Label("ToDo 목록");

        HBox listHeaderRow = new HBox(10, listHeader, newButton, deleteButton);
        listHeaderRow.setAlignment(Pos.CENTER_LEFT);

        VBox listBox = new VBox(8, listHeaderRow, todoListView);
        VBox.setVgrow(todoListView, Priority.ALWAYS);

        Label detailHeader = new Label("상세");

        Label titleLabel = new Label("제목 (최대 100자)");
        Label contentLabel = new Label("내용 (최대 200자)");

        VBox detailBox = new VBox(8,
                detailHeader,
                titleLabel,
                titleField,
                contentLabel,
                contentArea,
                doneCheck,
                saveButton,
                messageLabel
        );

        VBox.setVgrow(contentArea, Priority.ALWAYS);

        VBox right = new VBox(12, listBox, new Separator(), detailBox);
        right.setPadding(new Insets(10));
        right.setPrefWidth(520);

        VBox.setVgrow(listBox, Priority.ALWAYS);
        VBox.setVgrow(detailBox, Priority.ALWAYS);

        return right;
    }

    private void wireEvents() {
        calendarView.selectedDateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                refreshForDate(newVal);
            }
        });

        todoListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadForEdit(newVal);
            }
        });

        newButton.setOnAction(e -> {
            clearEditor();
            todoListView.getSelectionModel().clearSelection();
        });

        deleteButton.setOnAction(e -> {
            if (editingTodoId == null) {
                return;
            }
            todoService.delete(editingTodoId);
            LocalDate selected = calendarView.getSelectedDate();
            refreshForDate(selected);
            calendarView.refresh();
            clearEditor();
        });

        saveButton.setOnAction(e -> {
            messageLabel.setText("");

            LocalDate date = calendarView.getSelectedDate();
            String title = titleField.getText();
            String content = contentArea.getText();
            boolean done = doneCheck.isSelected();

            String validationError = validateInputs(title, content);
            if (validationError != null) {
                messageLabel.setText(validationError);
                return;
            }

            try {
                if (editingTodoId == null) {
                    Todo created = todoService.create(date, title.trim(), content.trim(), done);
                    loadForEdit(created);
                } else {
                    Todo updated = todoService.update(editingTodoId, date, title.trim(), content.trim(), done);
                    loadForEdit(updated);
                }

                refreshForDate(date);
                calendarView.refresh();
            } catch (ConstraintViolationException ex) {
                messageLabel.setText("입력값을 확인해주세요 (제목 100자, 내용 200자 이내, 공백 불가)");
            } catch (RuntimeException ex) {
                messageLabel.setText(ex.getMessage() == null ? "저장 중 오류" : ex.getMessage());
            }
        });
    }

    private void refreshForDate(LocalDate date) {
        List<Todo> todos = todoService.getTodosForDate(date);
        todoListView.setItems(FXCollections.observableArrayList(todos));

        // Keep selection if possible
        if (editingTodoId != null) {
            for (Todo todo : todos) {
                if (editingTodoId.equals(todo.getId())) {
                    todoListView.getSelectionModel().select(todo);
                    break;
                }
            }
        }

        if (todos.isEmpty()) {
            todoListView.getSelectionModel().clearSelection();
            clearEditor();
        }
    }

    private void loadForEdit(Todo todo) {
        editingTodoId = todo.getId();
        titleField.setText(todo.getTitle());
        contentArea.setText(todo.getContent());
        doneCheck.setSelected(todo.isDone());
    }

    private void clearEditor() {
        editingTodoId = null;
        titleField.clear();
        contentArea.clear();
        doneCheck.setSelected(false);
        messageLabel.setText("");
    }

    private String validateInputs(String title, String content) {
        String t = title == null ? "" : title.trim();
        String c = content == null ? "" : content.trim();

        if (t.isBlank()) {
            return "제목을 입력해주세요.";
        }
        if (t.length() > 100) {
            return "제목은 100자 이하여야 합니다.";
        }
        if (c.isBlank()) {
            return "내용을 입력해주세요.";
        }
        if (c.length() > 200) {
            return "내용은 200자 이하여야 합니다.";
        }

        return null;
    }
}

package com.example.todoapp.todo;

import com.example.todoapp.TodoAppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ConstraintViolationException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TodoAppApplication.class)
@ActiveProfiles("test")
@Transactional
class TodoServiceTest {

    @Autowired
    private TodoService todoService;

    @Test
    void create_persistsAndDefaultsAreApplied() {
        LocalDate date = LocalDate.of(2025, 12, 15);

        Todo todo = todoService.create(date, "제목", "내용", false);

        assertNotNull(todo.getId());
        assertEquals(date, todo.getDate());
        assertEquals("제목", todo.getTitle());
        assertEquals("내용", todo.getContent());
        assertFalse(todo.isDone());
        assertNotNull(todo.getCreatedAt());
        assertNotNull(todo.getUpdatedAt());
    }

    @Test
    void create_validatesTitleAndContent() {
        LocalDate date = LocalDate.of(2025, 12, 15);

        assertThrows(ConstraintViolationException.class,
                () -> todoService.create(date, "", "내용", false));

        assertThrows(ConstraintViolationException.class,
                () -> todoService.create(date, "제목", "", false));

        String longTitle = "x".repeat(101);
        assertThrows(ConstraintViolationException.class,
                () -> todoService.create(date, longTitle, "내용", false));

        String longContent = "y".repeat(201);
        assertThrows(ConstraintViolationException.class,
                () -> todoService.create(date, "제목", longContent, false));
    }

    @Test
    void getTodosForDate_ordersIncompleteFirst_thenCreatedAt() {
        LocalDate date = LocalDate.of(2025, 12, 15);

        Todo first = todoService.create(date, "A", "A", false);
        Todo second = todoService.create(date, "B", "B", false);

        // mark first as done; should move to bottom
        todoService.update(first.getId(), date, "A", "A", true);

        List<Todo> todos = todoService.getTodosForDate(date);
        assertEquals(2, todos.size());
        assertEquals(second.getId(), todos.get(0).getId());
        assertFalse(todos.get(0).isDone());
        assertEquals(first.getId(), todos.get(1).getId());
        assertTrue(todos.get(1).isDone());
    }

    @Test
    void getMonthDayStatuses_incompleteVsAllDone() {
        LocalDate d1 = LocalDate.of(2025, 12, 15);
        LocalDate d2 = LocalDate.of(2025, 12, 16);

        Todo a = todoService.create(d1, "A", "A", true);
        Todo b = todoService.create(d1, "B", "B", true);

        todoService.create(d2, "C", "C", true);
        todoService.create(d2, "D", "D", false);

        // ensure persisted + consistent
        todoService.update(a.getId(), d1, "A", "A", true);
        todoService.update(b.getId(), d1, "B", "B", true);

        Map<LocalDate, DayStatus> statuses = todoService.getMonthDayStatuses(YearMonth.of(2025, 12));

        assertEquals(DayStatus.ALL_DONE, statuses.get(d1));
        assertEquals(DayStatus.INCOMPLETE, statuses.get(d2));
    }
}

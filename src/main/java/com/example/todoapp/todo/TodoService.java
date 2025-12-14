package com.example.todoapp.todo;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class TodoService {

    private final TodoRepository todoRepository;
    private final Validator validator;

    public TodoService(TodoRepository todoRepository, Validator validator) {
        this.todoRepository = todoRepository;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<Todo> getTodosForDate(LocalDate date) {
        return todoRepository.findByDateOrderByDoneAscCreatedAtAsc(date);
    }

    @Transactional(readOnly = true)
    public Optional<Todo> getById(Long id) {
        return todoRepository.findById(id);
    }

    public Todo create(LocalDate date, String title, String content, boolean done) {
        Todo todo = new Todo(date, title, content, done);
        validate(todo);
        return todoRepository.save(todo);
    }

    public Todo update(Long id, LocalDate date, String title, String content, boolean done) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Todo not found: " + id));

        todo.setDate(date);
        todo.setTitle(title);
        todo.setContent(content);
        todo.setDone(done);

        validate(todo);
        return todoRepository.save(todo);
    }

    public void delete(Long id) {
        todoRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, DayStatus> getMonthDayStatuses(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<TodoDaySummary> summaries = todoRepository.summarizeByDate(start, end);
        Map<LocalDate, DayStatus> result = new HashMap<>();

        for (TodoDaySummary summary : summaries) {
            LocalDate date = summary.getDate();
            long total = summary.getTotalCount() == null ? 0 : summary.getTotalCount();
            long doneCount = summary.getDoneCount() == null ? 0 : summary.getDoneCount();

            if (total <= 0) {
                continue;
            }

            if (doneCount >= total) {
                result.put(date, DayStatus.ALL_DONE);
            } else {
                result.put(date, DayStatus.INCOMPLETE);
            }
        }

        return result;
    }

    private void validate(Todo todo) {
        Set<ConstraintViolation<Todo>> violations = validator.validate(todo);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}

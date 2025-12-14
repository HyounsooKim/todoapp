package com.example.todoapp.todo;

import java.time.LocalDate;

public interface TodoDaySummary {
    LocalDate getDate();

    Long getTotalCount();

    Long getDoneCount();
}

package com.example.todoapp.todo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findByDateOrderByDoneAscCreatedAtAsc(LocalDate date);

    @Query("""
            select t.date as date,
                   count(t) as totalCount,
                   sum(case when t.done = true then 1 else 0 end) as doneCount
            from Todo t
            where t.date between :start and :end
            group by t.date
            """)
    List<TodoDaySummary> summarizeByDate(@Param("start") LocalDate start, @Param("end") LocalDate end);
}

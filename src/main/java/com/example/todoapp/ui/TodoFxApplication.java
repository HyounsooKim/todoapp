package com.example.todoapp.ui;

import com.example.todoapp.TodoAppApplication;
import com.example.todoapp.todo.TodoService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TodoFxApplication extends Application {

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void init() {
        String dataDir = resolveDataDir();
        ensureDirectoryExists(dataDir);
        applicationContext = new SpringApplicationBuilder(TodoAppApplication.class)
                .headless(false)
                .properties("todoapp.data-dir=" + dataDir)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    private static String resolveDataDir() {
        boolean installed = Boolean.getBoolean("todoapp.installed");
        if (!installed) {
            return "./data";
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            return "./data";
        }

        // Use forward slashes; H2 JDBC URL accepts them on Windows.
        return Path.of(localAppData, "TodoApp", "data").toString().replace('\\', '/');
    }

    private static void ensureDirectoryExists(String directory) {
        Path dir = Path.of(directory);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create data directory: " + dir.toAbsolutePath(), e);
        }
    }

    @Override
    public void start(Stage stage) {
        TodoService todoService = applicationContext.getBean(TodoService.class);

        MainView mainView = new MainView(todoService);

        Scene scene = new Scene(mainView.getRoot(), 1100, 700);

        stage.setTitle("ToDo앱");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }
}

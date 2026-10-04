package com.nzsk.videodownloader.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Builds the whole view graph on a real JavaFX toolkit so that startup regressions (missing runtime
 * modules, scene-less dialog owners, broken bindings) fail the build instead of the packaged application.
 */
class MainViewSmokeTest {
    @Test
    void buildsMainViewOnJavaFxToolkit() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "需要图形环境才能运行 JavaFX 冒烟测试");
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.startup(() -> {
            try (AppContext context = new AppContext()) {
                Stage stage = new Stage();
                MainView view = new MainView(stage, context);
                assertNotNull(view.node());
                view.stop();
                stage.close();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                finished.countDown();
            }
        });

        assertTrue(finished.await(30, TimeUnit.SECONDS), "JavaFX 视图构建超时");
        if (failure.get() != null) {
            throw new AssertionError("JavaFX 视图构建失败", failure.get());
        }
        Platform.exit();
    }
}

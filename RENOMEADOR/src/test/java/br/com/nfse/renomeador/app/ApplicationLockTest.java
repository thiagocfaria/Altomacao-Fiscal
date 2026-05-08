package br.com.nfse.renomeador.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationLockTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsSecondInstanceUsingSameConfigFile() throws Exception {
        Path config = config("empresas.yaml");

        try (ApplicationLock ignored = ApplicationLock.acquire(config)) {
            assertThatThrownBy(() -> ApplicationLock.acquire(config))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Outra instancia");
        }
    }

    @Test
    void releasesLockWhenClosed() throws Exception {
        Path config = config("empresas.yaml");

        try (ApplicationLock ignored = ApplicationLock.acquire(config)) {
            assertThatThrownBy(() -> ApplicationLock.acquire(config))
                    .isInstanceOf(IllegalStateException.class);
        }

        assertThatCode(() -> {
            try (ApplicationLock ignored = ApplicationLock.acquire(config)) {
                // lock acquired and released by try-with-resources
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void allowsDifferentConfigFilesToRunIndependently() throws Exception {
        Path first = config("empresas-a.yaml");
        Path second = config("empresas-b.yaml");

        try (ApplicationLock ignoredFirst = ApplicationLock.acquire(first);
             ApplicationLock ignoredSecond = ApplicationLock.acquire(second)) {
            assertThatCode(() -> {
            }).doesNotThrowAnyException();
        }
    }

    @Test
    void storesLockUnderConfiguredBackendRoot() throws Exception {
        Path backend = tempDir.resolve("backend-oficial");
        Path config = tempDir.resolve("empresas.yaml");
        Files.writeString(config, """
                backendRoot: "%s"
                empresas: []
                """.formatted(backend.toString().replace("\\", "/")));

        try (ApplicationLock ignored = ApplicationLock.acquire(config)) {
            assertThat(backend.resolve("locks")).isDirectoryContaining(path ->
                    path.getFileName().toString().endsWith(".lock"));
        }
    }

    private Path config(String fileName) throws Exception {
        Path config = tempDir.resolve(fileName);
        Files.writeString(config, "empresas: []");
        return config;
    }
}

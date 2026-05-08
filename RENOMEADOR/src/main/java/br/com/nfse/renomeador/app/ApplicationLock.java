package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyRegistry;
import br.com.nfse.renomeador.config.CompanyRegistryLoader;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ApplicationLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private ApplicationLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static ApplicationLock acquire(Path config) throws IOException {
        Path lockFile = lockFileFor(config);
        Files.createDirectories(lockFile.getParent());
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquiredLock = null;
        try {
            acquiredLock = channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            closeQuietly(channel);
            throw alreadyRunning(config, exception);
        }
        if (acquiredLock == null) {
            closeQuietly(channel);
            throw alreadyRunning(config, null);
        }
        return new ApplicationLock(channel, acquiredLock);
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }

    private static Path lockFileFor(Path config) {
        String normalizedConfig = config.toAbsolutePath().normalize().toString();
        return backendRootFor(config).resolve("locks")
                .resolve("config-" + sha256Prefix(normalizedConfig) + ".lock");
    }

    private static Path backendRootFor(Path config) {
        try {
            CompanyRegistry registry = new CompanyRegistryLoader().load(config);
            return registry.backendRoot()
                    .map(path -> resolveBackendRoot(config, path))
                    .orElseGet(() -> defaultBackendRoot(config));
        } catch (IOException exception) {
            return defaultBackendRoot(config);
        }
    }

    private static Path defaultBackendRoot(Path config) {
        Path normalized = config.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        return (parent == null ? Path.of(".").toAbsolutePath().normalize() : parent).resolve("backend");
    }

    private static Path resolveBackendRoot(Path config, Path configured) {
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path parent = config.toAbsolutePath().normalize().getParent();
        Path base = parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
        return base.resolve(configured).normalize();
    }

    private static IllegalStateException alreadyRunning(Path config, Exception cause) {
        return new IllegalStateException("Outra instancia do Renomeador NFS-e ja esta usando este config: "
                + config.toAbsolutePath().normalize(), cause);
    }

    private static String sha256Prefix(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                builder.append("%02x".formatted(digest[index] & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 nao disponivel", exception);
        }
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // A falha relevante e a impossibilidade de obter o lock.
        }
    }
}

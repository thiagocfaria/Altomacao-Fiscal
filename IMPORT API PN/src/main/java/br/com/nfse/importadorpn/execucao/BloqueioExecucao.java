package br.com.nfse.importadorpn.execucao;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class BloqueioExecucao {
    private final Path lockFile;

    public BloqueioExecucao(Path lockFile) {
        this.lockFile = lockFile;
    }

    public Optional<FileLock> tentarAdquirir() throws IOException {
        if (lockFile.getParent() != null) {
            Files.createDirectories(lockFile.getParent());
        }
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            channel.close();
            return Optional.empty();
        }
        if (lock == null) {
            channel.close();
            return Optional.empty();
        }
        return Optional.of(new ClosingFileLock(lock, channel));
    }

    private static final class ClosingFileLock extends FileLock {
        private final FileLock delegate;
        private final FileChannel channel;

        private ClosingFileLock(FileLock delegate, FileChannel channel) {
            super(delegate.channel(), delegate.position(), delegate.size(), delegate.isShared());
            this.delegate = delegate;
            this.channel = channel;
        }

        @Override
        public boolean isValid() {
            return delegate.isValid();
        }

        @Override
        public void release() throws IOException {
            try {
                delegate.release();
            } finally {
                channel.close();
            }
        }
    }
}

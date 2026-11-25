package com.bricktools.csv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registry of one reentrant lock per CSV file.
 *
 * <p>Services each have their own instance lock, but several services write the same file (for example
 * {@code bw/setprices.csv}). Coordinating on the file path instead prevents concurrent writers from dropping each
 * other's rows. Locks are reentrant, so a service can hold one around a read-modify-write sequence whose individual
 * {@link Csv#write} calls also take the same lock.
 */
public final class FileLocks {

  private static final ConcurrentMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

  private FileLocks() {}

  public static ReentrantLock forFile(Path file) {
    return LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), path -> new ReentrantLock());
  }

  /** Runs {@code task} while holding the lock for {@code file}. */
  public static <T> T call(Path file, CheckedSupplier<T> task) throws IOException, InterruptedException {
    ReentrantLock lock = forFile(file);
    lock.lock();
    try {
      return task.get();
    } finally {
      lock.unlock();
    }
  }

  @FunctionalInterface
  public interface CheckedSupplier<T> {
    T get() throws IOException, InterruptedException;
  }
}

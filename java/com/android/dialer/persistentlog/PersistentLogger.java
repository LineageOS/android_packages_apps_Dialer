/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.dialer.persistentlog;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.support.v4.os.UserManagerCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Logs data that is persisted across app termination and device reboot. The logs are stored as
 * rolling files in cache with a limit of {@link #LOG_FILE_SIZE_LIMIT} * {@link
 * #LOG_FILE_COUNT_LIMIT}. The log writing is batched and there is a {@link #FLUSH_DELAY_MILLIS}
 * delay before the logs are committed to disk to avoid excessive IO. If the app is terminated
 * before the logs are committed it will be lost. {@link
 * com.google.android.apps.dialer.crashreporter.SilentCrashReporter} is expected to handle such
 * cases.
 *
 * <p>{@link #logText(String, String)} should be used to log ad-hoc text logs. TODO: switch
 * to structured logging
 */
public final class PersistentLogger {

  private static final int FLUSH_DELAY_MILLIS = 200;
  private static final String LOG_FOLDER = "plain_text";
  private static final int MESSAGE_FLUSH = 1;

  @VisibleForTesting static final int LOG_FILE_SIZE_LIMIT = 64 * 1024;
  @VisibleForTesting static final int LOG_FILE_COUNT_LIMIT = 8;

  private static PersistentLogFileHandler fileHandler;

  private static HandlerThread loggerThread;
  private static Handler loggerThreadHandler;

  private static final LinkedBlockingQueue<byte[]> messageQueue = new LinkedBlockingQueue<>();

  private PersistentLogger() {}

  public static void initialize(Context context) {
    fileHandler =
        new PersistentLogFileHandler(LOG_FOLDER, LOG_FILE_SIZE_LIMIT, LOG_FILE_COUNT_LIMIT);
    loggerThread = new HandlerThread("PersistentLogger");
    loggerThread.start();
    loggerThreadHandler =
        new Handler(
            loggerThread.getLooper(),
            (message) -> {
              if (message.what == MESSAGE_FLUSH) {
                if (messageQueue.isEmpty()) {
                  return true;
                }
                loggerThreadHandler.removeMessages(MESSAGE_FLUSH);
                List<byte[]> messages = new ArrayList<>();
                messageQueue.drainTo(messages);
                if (!UserManagerCompat.isUserUnlocked(context)) {
                  return true;
                }
                try {
                  fileHandler.writeLogs(messages);
                } catch (IOException e) {
                  LogUtil.e("PersistentLogger.MESSAGE_FLUSH", "error writing message", e);
                }
              }
              return true;
            });
    loggerThreadHandler.post(() -> fileHandler.initialize(context));
  }

  static HandlerThread getLoggerThread() {
    return loggerThread;
  }

  @AnyThread
  public static void logText(String tag, String string) {
    log(buildTextLog(tag, string));
  }

  @VisibleForTesting
  @AnyThread
  static void log(byte[] data) {
    messageQueue.add(data);
    loggerThreadHandler.sendEmptyMessageDelayed(MESSAGE_FLUSH, FLUSH_DELAY_MILLIS);
  }

  /** Dump the log as human readable string. Blocks until the dump is finished. */
  @NonNull
  @WorkerThread
  public static String dumpLogToString() {
    Assert.isWorkerThread();
    DumpStringRunnable dumpStringRunnable = new DumpStringRunnable();
    loggerThreadHandler.post(dumpStringRunnable);
    try {
      return dumpStringRunnable.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "Cannot dump logText: " + e;
    }
  }

  private static class DumpStringRunnable implements Runnable {
    private String result;
    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void run() {
      result = dumpLogToStringInternal();
      latch.countDown();
    }

    public String get() throws InterruptedException {
      latch.await();
      return result;
    }
  }

  @NonNull
  @WorkerThread
  private static String dumpLogToStringInternal() {
    StringBuilder result = new StringBuilder();
    List<byte[]> logs;
    try {
      logs = readLogs();
    } catch (IOException e) {
      return "Cannot dump logText: " + e;
    }

    for (byte[] log : logs) {
      result.append(new String(log, StandardCharsets.UTF_8)).append("\n");
    }
    return result.toString();
  }

  @NonNull
  @WorkerThread
  @VisibleForTesting
  static List<byte[]> readLogs() throws IOException {
    Assert.isWorkerThread();
    return fileHandler.getLogs();
  }

  private static byte[] buildTextLog(String tag, String string) {
    Calendar c = Calendar.getInstance();
    return String.format("%tm-%td %tH:%tM:%tS.%tL - %s - %s", c, c, c, c, c, c, tag, string)
        .getBytes(StandardCharsets.UTF_8);
  }
}

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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.os.UserManagerCompat;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles serialization of byte arrays and read/write them to multiple rotating files. If a logText
 * file exceeds {@code fileSizeLimit} after a write, a new file will be used. if the total number of
 * files exceeds {@code fileCountLimit} the oldest ones will be deleted. The logs are stored in the
 * cache but the file index is stored in the data (clearing data will also clear the cache). The
 * logs will be stored under /cache_dir/persistent_log/{@code subfolder}, so multiple independent
 * logs can be created.
 *
 * <p>This class is NOT thread safe. All methods expect the constructor must be called on the same
 * worker thread.
 */
@SuppressWarnings("AndroidApiChecker") // lambdas
@TargetApi(VERSION_CODES.M)
final class PersistentLogFileHandler {

  private static final String LOG_DIRECTORY = "persistent_log";
  private static final String NEXT_FILE_INDEX_PREFIX = "persistent_long_next_file_index_";

  private File logDirectory;
  private final String subfolder;
  private final int fileSizeLimit;
  private final int fileCountLimit;

  private SharedPreferences sharedPreferences;

  private File outputFile;
  private Context context;

  @MainThread
  PersistentLogFileHandler(String subfolder, int fileSizeLimit, int fileCountLimit) {
    this.subfolder = subfolder;
    this.fileSizeLimit = fileSizeLimit;
    this.fileCountLimit = fileCountLimit;
  }

  /** Must be called right after the logger thread is created. */
  @WorkerThread
  void initialize(Context context) {
    this.context = context;
    logDirectory = new File(new File(context.getCacheDir(), LOG_DIRECTORY), subfolder);
    initializeSharedPreference(context);
  }

  @WorkerThread
  private boolean initializeSharedPreference(Context context) {
    if (sharedPreferences == null && UserManagerCompat.isUserUnlocked(context)) {
      sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
      return true;
    }
    return sharedPreferences != null;
  }

  /**
   * Write the list of byte arrays to the current log file, prefixing each entry with its' length. A
   * new file will only be selected when the batch is completed, so the resulting file might be
   * larger then {@code fileSizeLimit}
   */
  @WorkerThread
  void writeLogs(List<byte[]> logs) throws IOException {
    if (outputFile == null) {
      selectNextFileToWrite();
    }
    outputFile.createNewFile();
    try (DataOutputStream outputStream =
        new DataOutputStream(new FileOutputStream(outputFile, true))) {
      for (byte[] log : logs) {
        outputStream.writeInt(log.length);
        outputStream.write(log);
      }
      outputStream.close();
      if (outputFile.length() > fileSizeLimit) {
        selectNextFileToWrite();
      }
    }
  }

  /** Concatenate all log files in chronicle order and return a byte array. */
  @WorkerThread
  @NonNull
  private byte[] readBlob() throws IOException {
    File[] files = getLogFiles();

    ByteBuffer byteBuffer = ByteBuffer.allocate(getTotalSize(files));
    for (File file : files) {
      byteBuffer.put(readAllBytes(file));
    }
    return byteBuffer.array();
  }

  private static int getTotalSize(File[] files) {
    int sum = 0;
    for (File file : files) {
      sum += (int) file.length();
    }
    return sum;
  }

  /** Parses the content of all files back to individual byte arrays. */
  @WorkerThread
  @NonNull
  List<byte[]> getLogs() throws IOException {
    byte[] blob = readBlob();
    List<byte[]> logs = new ArrayList<>();
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(blob))) {
      byte[] log = readLog(input);
      while (log != null) {
        logs.add(log);
        log = readLog(input);
      }
    }
    return logs;
  }

  @WorkerThread
  private void selectNextFileToWrite() throws IOException {
    File[] files = getLogFiles();

    if (files.length == 0 || files[files.length - 1].length() > fileSizeLimit) {
      if (files.length >= fileCountLimit) {
        for (int i = 0; i <= files.length - fileCountLimit; i++) {
          files[i].delete();
        }
      }
      outputFile = new File(logDirectory, String.valueOf(getAndIncrementNextFileIndex()));
    } else {
      outputFile = files[files.length - 1];
    }
  }

  @NonNull
  @WorkerThread
  private File[] getLogFiles() {
    logDirectory.mkdirs();
    File[] files = logDirectory.listFiles();
    if (files == null) {
      files = new File[0];
    }
    Arrays.sort(
        files,
        (File lhs, File rhs) ->
            Long.compare(Long.valueOf(lhs.getName()), Long.valueOf(rhs.getName())));
    return files;
  }

  @Nullable
  @WorkerThread
  private static byte[] readLog(DataInputStream inputStream) throws IOException {
    try {
      byte[] data = new byte[inputStream.readInt()];
      inputStream.read(data);
      return data;
    } catch (EOFException e) {
      return null;
    }
  }

  @NonNull
  @WorkerThread
  private static byte[] readAllBytes(File file) throws IOException {
    byte[] result = new byte[(int) file.length()];
    try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
      randomAccessFile.readFully(result);
    }
    return result;
  }

  @WorkerThread
  private int getAndIncrementNextFileIndex() throws IOException {
    if (!initializeSharedPreference(context)) {
      throw new IOException("Shared preference is not available");
    }

    int index = sharedPreferences.getInt(getNextFileKey(), 0);
    sharedPreferences.edit().putInt(getNextFileKey(), index + 1).commit();
    return index;
  }

  @AnyThread
  private String getNextFileKey() {
    return NEXT_FILE_INDEX_PREFIX + subfolder;
  }
}

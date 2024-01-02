/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
package com.android.voicemail.impl;

import com.android.dialer.common.LogUtil;
import com.android.voicemail.impl.utils.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;
import java.util.Iterator;

/** Helper methods for adding to OMTP visual voicemail local logs. */
public class VvmLog {

  private static final int MAX_OMTP_VVM_LOGS = 100;

  private static final LocalLog localLog = new LocalLog(MAX_OMTP_VVM_LOGS);

  public static void dump(FileDescriptor fd, PrintWriter printwriter, String[] args) {
    IndentingPrintWriter indentingPrintWriter = new IndentingPrintWriter(printwriter, "  ");
    indentingPrintWriter.increaseIndent();
    localLog.dump(fd, indentingPrintWriter, args);
    indentingPrintWriter.decreaseIndent();
  }

  public static void e(String tag, String log) {
    LogUtil.e(tag, log);
  }

  public static void e(String tag, String log, Throwable e) {
    LogUtil.e(tag, log, e);
  }

  public static void w(String tag, String log) {
    LogUtil.w(tag, log);
  }

  public static void w(String tag, String log, Throwable e) {
    LogUtil.w(tag, log, e);
  }

  public static void i(String tag, String log) {
    LogUtil.i(tag, log);
  }

  public static void i(String tag, String log, Throwable e) {
    LogUtil.i(tag, log, e);
  }

  public static void d(String tag, String log) {
    LogUtil.d(tag, log);
  }

  public static void d(String tag, String log, Throwable e) {
    LogUtil.d(tag, log, e);
  }

  public static void v(String tag, String log) {
    LogUtil.v(tag, log);
  }

  public static void v(String tag, String log, Throwable e) {
    LogUtil.v(tag, log, e);
  }

  public static void wtf(String tag, String log) {
    LogUtil.e(tag, log);
  }

  public static void wtf(String tag, String log, Throwable e) {
    LogUtil.e(tag, log, e);
  }

  /**
   * Redact personally identifiable information for production users. If we are running in verbose
   * mode, return the original string, otherwise return a SHA-1 hash of the input string.
   */
  public static String pii(Object pii) {
    if (pii == null) {
      return String.valueOf(pii);
    }
    return "[PII]";
  }

  public static class LocalLog {

    private final Deque<String> log;
    private final int maxLines;

    public LocalLog(int maxLines) {
      this.maxLines = Math.max(0, maxLines);
      log = new ArrayDeque<>(this.maxLines);
    }

    public void log(String msg) {
      if (maxLines <= 0) {
        return;
      }
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(System.currentTimeMillis());
      append(String.format("%tm-%td %tH:%tM:%tS.%tL - %s", c, c, c, c, c, c, msg));
    }

    private synchronized void append(String logLine) {
      while (log.size() >= maxLines) {
        log.remove();
      }
      log.add(logLine);
    }

    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
      Iterator<String> itr = log.iterator();
      while (itr.hasNext()) {
        pw.println(itr.next());
      }
    }

    public synchronized void reverseDump(FileDescriptor fd, PrintWriter pw, String[] args) {
      Iterator<String> itr = log.descendingIterator();
      while (itr.hasNext()) {
        pw.println(itr.next());
      }
    }

    public static class ReadOnlyLocalLog {

      private final LocalLog log;

      ReadOnlyLocalLog(LocalLog log) {
        this.log = log;
      }

      public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        log.dump(fd, pw, args);
      }

      public void reverseDump(FileDescriptor fd, PrintWriter pw, String[] args) {
        log.reverseDump(fd, pw, args);
      }
    }

    public ReadOnlyLocalLog readOnlyLocalLog() {
      return new ReadOnlyLocalLog(this);
    }
  }
}

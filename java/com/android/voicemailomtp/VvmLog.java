/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.voicemailomtp;

import android.util.Log;
import com.android.voicemailomtp.utils.IndentingPrintWriter;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;
import java.util.Iterator;

/**
 * Helper methods for adding to OMTP visual voicemail local logs.
 */
public class VvmLog {

    private static final int MAX_OMTP_VVM_LOGS = 100;

    private static final LocalLog sLocalLog = new LocalLog(MAX_OMTP_VVM_LOGS);

    public static void log(String tag, String log) {
        sLocalLog.log(tag + ": " + log);
    }

    public static void dump(FileDescriptor fd, PrintWriter printwriter, String[] args) {
        IndentingPrintWriter indentingPrintWriter = new IndentingPrintWriter(printwriter, "  ");
        indentingPrintWriter.increaseIndent();
        sLocalLog.dump(fd, indentingPrintWriter, args);
        indentingPrintWriter.decreaseIndent();
    }

    public static int e(String tag, String log) {
        log(tag, log);
        return Log.e(tag, log);
    }

    public static int e(String tag, String log, Throwable e) {
        log(tag, log + " " + e);
        return Log.e(tag, log, e);
    }

    public static int w(String tag, String log) {
        log(tag, log);
        return Log.w(tag, log);
    }

    public static int w(String tag, String log, Throwable e) {
        log(tag, log + " " + e);
        return Log.w(tag, log, e);
    }

    public static int i(String tag, String log) {
        log(tag, log);
        return Log.i(tag, log);
    }

    public static int i(String tag, String log, Throwable e) {
        log(tag, log + " " + e);
        return Log.i(tag, log, e);
    }

    public static int d(String tag, String log) {
        log(tag, log);
        return Log.d(tag, log);
    }

    public static int d(String tag, String log, Throwable e) {
        log(tag, log + " " + e);
        return Log.d(tag, log, e);
    }

    public static int v(String tag, String log) {
        log(tag, log);
        return Log.v(tag, log);
    }

    public static int v(String tag, String log, Throwable e) {
        log(tag, log + " " + e);
        return Log.v(tag, log, e);
    }

    public static int wtf(String tag, String log) {
        log(tag, log);
        return Log.wtf(tag, log);
    }

    public static int wtf(String tag, String log, Throwable e) {
        log(tag, log + " " + e);
        return Log.wtf(tag, log, e);
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

        private final Deque<String> mLog;
        private final int mMaxLines;

        public LocalLog(int maxLines) {
            mMaxLines = Math.max(0, maxLines);
            mLog = new ArrayDeque<>(mMaxLines);
        }

        public void log(String msg) {
            if (mMaxLines <= 0) {
                return;
            }
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(System.currentTimeMillis());
            append(String.format("%tm-%td %tH:%tM:%tS.%tL - %s", c, c, c, c, c, c, msg));
        }

        private synchronized void append(String logLine) {
            while (mLog.size() >= mMaxLines) {
                mLog.remove();
            }
            mLog.add(logLine);
        }

        public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            Iterator<String> itr = mLog.iterator();
            while (itr.hasNext()) {
                pw.println(itr.next());
            }
        }

        public synchronized void reverseDump(FileDescriptor fd, PrintWriter pw, String[] args) {
            Iterator<String> itr = mLog.descendingIterator();
            while (itr.hasNext()) {
                pw.println(itr.next());
            }
        }

        public static class ReadOnlyLocalLog {

            private final LocalLog mLog;

            ReadOnlyLocalLog(LocalLog log) {
                mLog = log;
            }

            public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
                mLog.dump(fd, pw, args);
            }

            public void reverseDump(FileDescriptor fd, PrintWriter pw, String[] args) {
                mLog.reverseDump(fd, pw, args);
            }
        }

        public ReadOnlyLocalLog readOnlyLocalLog() {
            return new ReadOnlyLocalLog(this);
        }
    }
}

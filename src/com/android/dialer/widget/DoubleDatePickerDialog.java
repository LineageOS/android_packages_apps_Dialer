/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2013 Android Open Kang Project
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
 * limitations under the License.
 */

package com.android.dialer.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.DatePicker.OnDateChangedListener;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;

import com.android.dialer.R;

/**
 * Alertdialog with two date pickers - one for a start and one for an end date.
 * Used to filter the callstats query.
 */
public class DoubleDatePickerDialog extends AlertDialog
        implements OnClickListener, OnDateChangedListener, OnItemSelectedListener {

    private static final String TAG = "DoubleDatePickerDialog";

    public interface OnDateSetListener {
        void onDateSet(long from, long to);
    }

    public static class Fragment extends DialogFragment implements OnDateSetListener {
        private DoubleDatePickerDialog mDialog;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            mDialog = new DoubleDatePickerDialog(getActivity(), this);
            return mDialog;
        }

        @Override
        public void onStart() {
            final Bundle args = getArguments();
            final long from = args.getLong("from", -1);
            final long to = args.getLong("to", -1);

            if (from != -1) {
                mDialog.setValues(from, to);
            } else {
                mDialog.resetPickers();
            }
            super.onStart();
        }

        @Override
        public void onDateSet(long from, long to) {
            ((DoubleDatePickerDialog.OnDateSetListener) getActivity()).onDateSet(from, to);
        }

        public static Bundle createArguments(long from, long to) {
            final Bundle args = new Bundle();
            args.putLong("from", from);
            args.putLong("to", to);
            return args;
        }
    }

    private interface QuickSelection {
        void adjustStartDate(Calendar date);
    }

    private static final int[] QUICKSELECTION_ENTRIES = new int[] {
        R.string.date_qs_currentmonth,
        R.string.date_qs_currentquarter,
        R.string.date_qs_currentyear,
        R.string.date_qs_lastweek,
        R.string.date_qs_lastmonth,
        R.string.date_qs_lastquarter,
        R.string.date_qs_lastyear
    };

    private static final QuickSelection[] QUICKSELECTIONS = new QuickSelection[] {
        new QuickSelection() {
            @Override
            public void adjustStartDate(Calendar date) {
                date.set(Calendar.DAY_OF_MONTH, 1);
            }
        },
        new QuickSelection() {
            @Override
            public void adjustStartDate(Calendar date) {
                final int currentMonth = date.get(Calendar.MONTH);
                date.set(Calendar.MONTH, currentMonth - (currentMonth % 3));
                date.set(Calendar.DAY_OF_MONTH, 1);
            }
        },
        new QuickSelection() {
            @Override
            public void adjustStartDate(Calendar date) {
                date.set(Calendar.MONTH, 0);
                date.set(Calendar.DAY_OF_MONTH, 1);
            }
        },
        new QuickSelection() {
            @Override
            public void adjustStartDate(Calendar date) {
                date.add(Calendar.WEEK_OF_YEAR, -1);
            }
        },
        new QuickSelection() {
            @Override
            public void adjustStartDate(Calendar date) {
                date.add(Calendar.MONTH, -1);
            }
        },
        new QuickSelection() {
            @Override
            public void adjustStartDate(Calendar date) {
                date.add(Calendar.MONTH, -3);
            }
        },
        new QuickSelection() {
            @Override
            public void adjustStartDate(Calendar date) {
                date.add(Calendar.YEAR, -1);
            }
        },
    };

    private static final String YEAR = "year";
    private static final String MONTH = "month";
    private static final String DAY = "day";

    private final Spinner mQuickSelSpinner;
    private final DatePicker mDatePickerFrom;
    private final DatePicker mDatePickerTo;
    private final OnDateSetListener mCallBack;
    private Button mOkButton;
    private int mQuickSelSelection = -1;

    public DoubleDatePickerDialog(final Context context, OnDateSetListener callBack) {
        super(context);

        mCallBack = callBack;

        setTitle(R.string.call_stats_filter_picker_title);
        setButton(BUTTON_NEGATIVE, context.getString(android.R.string.cancel), this);
        setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
        setIcon(0);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.double_date_picker_dialog, null);
        setView(view);

        mDatePickerFrom = (DatePicker) view.findViewById(R.id.date_picker_from);
        mDatePickerTo = (DatePicker) view.findViewById(R.id.date_picker_to);

        ArrayList<CharSequence> quickSelEntries = new ArrayList<CharSequence>();
        for (int entryId : QUICKSELECTION_ENTRIES) {
            quickSelEntries.add(context.getString(entryId));
        }
        ArrayAdapter<CharSequence> quickSelAdapter = new ArrayAdapter<CharSequence>(
                context, android.R.layout.simple_spinner_item,
                android.R.id.text1, quickSelEntries) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                final TextView v = (TextView) super.getView(position, convertView, parent);
                v.setText(context.getString(R.string.date_quick_selection));
                return v;
            }
        };
        quickSelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mQuickSelSpinner = (Spinner) view.findViewById(R.id.date_quick_selection);
        mQuickSelSpinner.setOnItemSelectedListener(this);
        mQuickSelSpinner.setAdapter(quickSelAdapter);

        resetPickers();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mOkButton = getButton(DialogInterface.BUTTON_POSITIVE);
        updateOkButtonState();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                tryNotifyDateSet();
                break;
            case BUTTON_NEGATIVE:
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        if (mQuickSelSelection >= 0) {
            QuickSelection sel = QUICKSELECTIONS[pos];
            Calendar from = Calendar.getInstance();
            long millisTo = from.getTimeInMillis();
            sel.adjustStartDate(from);
            long millisFrom = from.getTimeInMillis();

            setValues(millisFrom, millisTo);
        }
        mQuickSelSelection = pos;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public void onDateChanged(DatePicker view, int year,
            int month, int day) {
        view.init(year, month, day, this);
        updateOkButtonState();
    }

    public void setValues(long millisFrom, long millisTo) {
        setPicker(mDatePickerFrom, millisFrom);
        setPicker(mDatePickerTo, millisTo);
        updateOkButtonState();
    }

    public void resetPickers() {
        long millis = System.currentTimeMillis();
        setPicker(mDatePickerFrom, millis);
        setPicker(mDatePickerTo, millis);
        updateOkButtonState();
    }

    private void setPicker(DatePicker picker, long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);

        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        picker.init(year, month, day, this);
    }

    private long getMillisForPicker(DatePicker picker, boolean endOfDay) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, picker.getYear());
        c.set(Calendar.MONTH, picker.getMonth());
        c.set(Calendar.DAY_OF_MONTH, picker.getDayOfMonth());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);

        long millis = c.getTimeInMillis();
        if (endOfDay) {
            millis += 24L * 60L * 60L * 1000L - 1L;
        }

        return millis;
    }

    private void updateOkButtonState() {
        if (mOkButton != null) {
            long millisFrom = getMillisForPicker(mDatePickerFrom, false);
            long millisTo = getMillisForPicker(mDatePickerTo, true);
            mOkButton.setEnabled(millisFrom < millisTo);
        }
    }

    private void tryNotifyDateSet() {
        if (mCallBack != null) {
            mDatePickerFrom.clearFocus();
            mDatePickerTo.clearFocus();

            long millisFrom = getMillisForPicker(mDatePickerFrom, false);
            long millisTo = getMillisForPicker(mDatePickerTo, true);

            mCallBack.onDateSet(millisFrom, millisTo);
        }
    }

    // users like to play with it, so save the state and don't reset each time
    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt("F_" + YEAR, mDatePickerFrom.getYear());
        state.putInt("F_" + MONTH, mDatePickerFrom.getMonth());
        state.putInt("F_" + DAY, mDatePickerFrom.getDayOfMonth());
        state.putInt("T_" + YEAR, mDatePickerTo.getYear());
        state.putInt("T_" + MONTH, mDatePickerTo.getMonth());
        state.putInt("T_" + DAY, mDatePickerTo.getDayOfMonth());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int fyear = savedInstanceState.getInt("F_" + YEAR);
        int fmonth = savedInstanceState.getInt("F_" + MONTH);
        int fday = savedInstanceState.getInt("F_" + DAY);
        int tyear = savedInstanceState.getInt("T_" + YEAR);
        int tmonth = savedInstanceState.getInt("T_" + MONTH);
        int tday = savedInstanceState.getInt("T_" + DAY);
        mDatePickerFrom.init(fyear, fmonth, fday, this);
        mDatePickerTo.init(tyear, tmonth, tday, this);
    }
}

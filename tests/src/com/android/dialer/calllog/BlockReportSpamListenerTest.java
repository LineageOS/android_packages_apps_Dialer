package com.android.dialer.calllog;

import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.test.ActivityInstrumentationTestCase2;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.service.ExtendedCallInfoService;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link BlockReportSpamListener}.
 */
public class BlockReportSpamListenerTest extends ActivityInstrumentationTestCase2<DialtactsActivity> {

    private static final String TEST_DISPLAY_NUMBER = "(123)456-7890";
    private static final String TEST_NUMBER = "1234567890";
    private static final String TEST_COUNTRY_ISO = "us";
    private static final int TEST_CALL_TYPE = 0;
    private static final int TEST_CALL_BLOCK_ID = 1;

    private BlockReportSpamListener blockReportSpamListener;

    @Mock private RecyclerView.Adapter adapter;
    @Mock private ExtendedCallInfoService extendedCallInfoService;
    @Mock private FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler;

    public BlockReportSpamListenerTest() {
        super(DialtactsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        MockitoAnnotations.initMocks(this);

        blockReportSpamListener = new BlockReportSpamListener(
                ((Activity) getActivity()).getFragmentManager(), adapter,
                extendedCallInfoService, filteredNumberAsyncQueryHandler);
    }

    public void testOnBlockReportSpam() {
        blockReportSpamListener.onBlockReportSpam(
                TEST_DISPLAY_NUMBER, TEST_NUMBER, TEST_COUNTRY_ISO, TEST_CALL_TYPE);
    }

    public void testOnBlock() {
        blockReportSpamListener.onBlock(
                TEST_DISPLAY_NUMBER, TEST_NUMBER, TEST_COUNTRY_ISO, TEST_CALL_TYPE);
    }

    public void testOnUnlock_isSpam() {
        blockReportSpamListener.onUnblock(
                TEST_DISPLAY_NUMBER, TEST_NUMBER, TEST_COUNTRY_ISO, TEST_CALL_BLOCK_ID,
                true, TEST_CALL_TYPE);
    }

    public void testOnUnlock_isNotSpam() {
        blockReportSpamListener.onUnblock(
                TEST_DISPLAY_NUMBER, TEST_NUMBER, TEST_COUNTRY_ISO, TEST_CALL_BLOCK_ID,
                false, TEST_CALL_TYPE);
    }

    public void testOnReportNotSpam() {
        blockReportSpamListener.onReportNotSpam(
                TEST_DISPLAY_NUMBER, TEST_NUMBER, TEST_COUNTRY_ISO, TEST_CALL_TYPE);
    }
}

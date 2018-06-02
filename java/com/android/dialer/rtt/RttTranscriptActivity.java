/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.rtt;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.UiListener;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.protos.ProtoParsers;
import com.android.dialer.widget.DialerToolbar;

/** Activity holds RTT transcript. */
public class RttTranscriptActivity extends AppCompatActivity {

  public static final String EXTRA_TRANSCRIPT_ID = "extra_transcript_id";
  public static final String EXTRA_PRIMARY_TEXT = "extra_primary_text";
  public static final String EXTRA_PHOTO_INFO = "extra_photo_info";

  private RttTranscriptAdapter adapter;
  private UiListener<RttTranscript> rttTranscriptUiListener;
  private DialerToolbar toolbar;

  public static Intent getIntent(
      Context context, String transcriptId, String primaryText, PhotoInfo photoInfo) {
    Intent intent = new Intent(context, RttTranscriptActivity.class);
    intent.putExtra(RttTranscriptActivity.EXTRA_TRANSCRIPT_ID, transcriptId);
    intent.putExtra(RttTranscriptActivity.EXTRA_PRIMARY_TEXT, primaryText);
    ProtoParsers.put(intent, RttTranscriptActivity.EXTRA_PHOTO_INFO, Assert.isNotNull(photoInfo));
    return intent;
  }

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.activity_rtt_transcript);
    toolbar = findViewById(R.id.toolbar);
    toolbar.setBackgroundColor(getColor(R.color.rtt_transcript_primary_color));
    getWindow().setStatusBarColor(getColor(R.color.rtt_transcript_primary_color_dark));

    RecyclerView recyclerView = findViewById(R.id.rtt_recycler_view);
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setHasFixedSize(true);
    adapter = new RttTranscriptAdapter(this);
    recyclerView.setAdapter(adapter);

    rttTranscriptUiListener =
        DialerExecutorComponent.get(this)
            .createUiListener(getFragmentManager(), "Load RTT transcript");
    handleIntent(getIntent());
  }

  private void handleIntent(Intent intent) {
    Assert.checkArgument(intent.hasExtra(EXTRA_TRANSCRIPT_ID));
    Assert.checkArgument(intent.hasExtra(EXTRA_PRIMARY_TEXT));
    Assert.checkArgument(intent.hasExtra(EXTRA_PHOTO_INFO));

    String id = intent.getStringExtra(EXTRA_TRANSCRIPT_ID);
    rttTranscriptUiListener.listen(
        this,
        RttTranscriptUtil.loadRttTranscript(this, id),
        adapter::setRttTranscript,
        throwable -> {
          throw new RuntimeException(throwable);
        });

    String primaryText = intent.getStringExtra(EXTRA_PRIMARY_TEXT);
    toolbar.setTitle(primaryText);

    PhotoInfo photoInfo =
        ProtoParsers.getTrusted(intent, EXTRA_PHOTO_INFO, PhotoInfo.getDefaultInstance());
    // Photo shown here shouldn't have video or RTT badge.
    PhotoInfo sanitizedPhotoInfo =
        PhotoInfo.newBuilder().mergeFrom(photoInfo).setIsRtt(false).setIsVideo(false).build();
    adapter.setPhotoInfo(sanitizedPhotoInfo);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleIntent(intent);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}

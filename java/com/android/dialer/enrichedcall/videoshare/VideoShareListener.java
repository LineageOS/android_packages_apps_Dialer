package com.android.dialer.enrichedcall.videoshare;

import android.support.annotation.MainThread;

/** Receives updates when video share status has changed. */
public interface VideoShareListener {

  /**
   * Callback fired when video share has changed (service connected / disconnected, video share
   * invite received or canceled, or when a session changes).
   */
  @MainThread
  void onVideoShareChanged();
}

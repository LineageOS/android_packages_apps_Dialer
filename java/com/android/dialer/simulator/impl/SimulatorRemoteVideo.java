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

package com.android.dialer.simulator.impl;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.view.Surface;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;

/**
 * Used by the video provider to draw the remote party's video. The in-call UI is responsible for
 * setting the view to draw to. Since the simulator doesn't have a remote party we simply draw a
 * green screen with a ball bouncing around.
 */
final class SimulatorRemoteVideo {
  @NonNull private final RenderThread thread;
  private boolean isStopped;

  SimulatorRemoteVideo(@NonNull Surface surface) {
    thread = new RenderThread(new Renderer(surface));
  }

  void startVideo() {
    LogUtil.enterBlock("SimulatorRemoteVideo.startVideo");
    Assert.checkState(!isStopped);
    thread.start();
  }

  void stopVideo() {
    LogUtil.enterBlock("SimulatorRemoteVideo.stopVideo");
    isStopped = true;
    thread.quitSafely();
  }

  @VisibleForTesting
  Runnable getRenderer() {
    return thread.getRenderer();
  }

  private static class Renderer implements Runnable {
    private static final int FRAME_DELAY_MILLIS = 33;
    private static final float CIRCLE_STEP = 16.0f;

    @NonNull private final Surface surface;
    private float circleX;
    private float circleY;
    private float radius;
    private double angle;

    Renderer(@NonNull Surface surface) {
      this.surface = Assert.isNotNull(surface);
    }

    @Override
    public void run() {
      drawFrame();
      schedule();
    }

    @WorkerThread
    void schedule() {
      Assert.isWorkerThread();
      new Handler().postDelayed(this, FRAME_DELAY_MILLIS);
    }

    @WorkerThread
    private void drawFrame() {
      Assert.isWorkerThread();
      Canvas canvas;
      try {
        canvas = surface.lockCanvas(null /* dirtyRect */);
      } catch (IllegalArgumentException e) {
        // This can happen when the video fragment tears down.
        LogUtil.e("SimulatorRemoteVideo.RenderThread.drawFrame", "unable to lock canvas", e);
        return;
      }

      LogUtil.i(
          "SimulatorRemoteVideo.RenderThread.drawFrame",
          "size; %d x %d",
          canvas.getWidth(),
          canvas.getHeight());
      canvas.drawColor(Color.GREEN);
      moveCircle(canvas);
      drawCircle(canvas);
      surface.unlockCanvasAndPost(canvas);
    }

    @WorkerThread
    private void moveCircle(Canvas canvas) {
      Assert.isWorkerThread();
      int width = canvas.getWidth();
      int height = canvas.getHeight();
      if (circleX == 0 && circleY == 0) {
        circleX = width / 2.0f;
        circleY = height / 2.0f;
        angle = Math.PI / 4.0;
        radius = Math.min(canvas.getWidth(), canvas.getHeight()) * 0.15f;
      } else {
        circleX += (float) Math.cos(angle) * CIRCLE_STEP;
        circleY += (float) Math.sin(angle) * CIRCLE_STEP;
        // Bounce the circle off the edge.
        if (circleX + radius >= width
            || circleX - radius <= 0
            || circleY + radius >= height
            || circleY - radius <= 0) {
          angle += Math.PI / 2.0;
        }
      }
    }

    @WorkerThread
    private void drawCircle(Canvas canvas) {
      Assert.isWorkerThread();
      Paint paint = new Paint();
      paint.setColor(Color.MAGENTA);
      paint.setStyle(Paint.Style.FILL);
      canvas.drawCircle(circleX, circleY, radius, paint);
    }
  }

  private static class RenderThread extends HandlerThread {
    @NonNull private final Renderer renderer;

    RenderThread(@NonNull Renderer renderer) {
      super("SimulatorRemoteVideo");
      this.renderer = Assert.isNotNull(renderer);
    }

    @Override
    @WorkerThread
    protected void onLooperPrepared() {
      Assert.isWorkerThread();
      renderer.schedule();
    }

    @VisibleForTesting
    Runnable getRenderer() {
      return renderer;
    }
  }
}

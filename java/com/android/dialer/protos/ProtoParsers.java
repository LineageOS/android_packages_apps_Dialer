/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.protos;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.dialer.common.Assert;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.io.IOException;

/** Useful methods for using Protocol Buffers with Android. */
public final class ProtoParsers {

  private ProtoParsers() {}

  /** Retrieve a proto from a Bundle which was not created within the current executable/version. */
  @SuppressWarnings("unchecked") // We want to eventually optimize away parser classes, so cast
  public static <T extends MessageLite> T get(Bundle bundle, String key, T defaultInstance)
      throws InvalidProtocolBufferException {
    // Class loaders are unique to each Class instance, so we need to specify how to decode
    // the information again, even though we set the class loaders when serializing the data.
    bundle.setClassLoader(ProtoParsers.class.getClassLoader());
    InternalDontUse parcelable = bundle.getParcelable(key);
    return (T) parcelable.getMessageUnsafe(defaultInstance.getDefaultInstanceForType());
  }

  /**
   * Retrieve a proto from a trusted bundle which was created within the current executable/version.
   *
   * @throws RuntimeException if the proto cannot be parsed
   */
  public static <T extends MessageLite> T getTrusted(Bundle bundle, String key, T defaultInstance) {
    try {
      return get(bundle, key, defaultInstance);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Retrieve a proto from a trusted bundle which was created within the current executable/version.
   *
   * @throws RuntimeException if the proto cannot be parsed
   */
  public static <T extends MessageLite> T getTrusted(Intent intent, String key, T defaultInstance) {
    return getTrusted(intent.getExtras(), key, defaultInstance);
  }

  /**
   * Stores a proto in a Bundle, for later retrieval by {@link #get(Bundle, String, MessageLite)} or
   * {@link #getFromInstanceState(Bundle, String, MessageLite)}.
   */
  public static void put(Bundle bundle, String key, MessageLite message) {
    bundle.putParcelable(key, new InternalDontUse<>(null, message));
  }

  /**
   * Stores a proto in an Intent, for later retrieval by {@link #get(Bundle, String, MessageLite)}.
   * Needs separate method because Intent has similar to but different API than Bundle.
   */
  public static void put(Intent intent, String key, MessageLite message) {
    intent.putExtra(key, new InternalDontUse<>(null, message));
  }

  /** Returns a {@linkplain Parcelable} representation of this protobuf message. */
  public static <T extends MessageLite> ParcelableProto<T> asParcelable(T message) {
    return new InternalDontUse<>(null, message);
  }

  /**
   * A protobuf message that can be stored in a {@link Parcel}.
   *
   * <p><b>Note:</b> This <code>Parcelable</code> can only be used in single app. Attempting to send
   * it to another app through an <code>Intent</code> will result in an exception due to Proguard
   * obfusation when the target application attempts to load the <code>ParcelableProto</code> class.
   */
  public interface ParcelableProto<T extends MessageLite> extends Parcelable {
    /**
     * @throws IllegalStateException if the parceled data does not correspond to the defaultInstance
     *     type.
     */
    T getMessage(T defaultInstance);
  }

  /** Public because of Parcelable requirements. Do not use. */
  public static final class InternalDontUse<T extends MessageLite> implements ParcelableProto<T> {
    /* One of these two fields is always populated - since the bytes field never escapes this
     * object, there is no risk of concurrent modification by multiple threads, and volatile
     * is sufficient to be thread-safe. */
    private volatile byte[] bytes;
    private volatile T message;

    /**
     * Ideally, we would have type safety here. However, a static field {@link Creator} is required
     * by {@link Parcelable}. Static fields are inherently not type safe, since only 1 exists per
     * class (rather than 1 per type).
     */
    public static final Parcelable.Creator<InternalDontUse<?>> CREATOR =
        new Creator<InternalDontUse<?>>() {
          @Override
          public InternalDontUse<?> createFromParcel(Parcel parcel) {
            int serializedSize = parcel.readInt();
            byte[] array = new byte[serializedSize];
            parcel.readByteArray(array);
            return new InternalDontUse<>(array, null);
          }

          @Override
          public InternalDontUse<?>[] newArray(int i) {
            return new InternalDontUse[i];
          }
        };

    private InternalDontUse(byte[] bytes, T message) {
      Assert.checkArgument(bytes != null || message != null, "Must have a message or bytes");
      this.bytes = bytes;
      this.message = message;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
      if (bytes == null) {
        final byte[] flatArray = new byte[message.getSerializedSize()];
        try {
          message.writeTo(CodedOutputStream.newInstance(flatArray));
          bytes = flatArray;
        } catch (IOException impossible) {
          throw new AssertionError(impossible);
        }
      }
      parcel.writeInt(bytes.length);
      parcel.writeByteArray(bytes);
    }

    @Override
    public T getMessage(T defaultInstance) {
      try {
        // The proto should never be invalid if it came from our application, so if it is, throw.
        return getMessageUnsafe(defaultInstance);
      } catch (InvalidProtocolBufferException e) {
        throw new IllegalStateException(e);
      }
    }

    @SuppressWarnings("unchecked") // We're being deserialized, so there's no real type safety
    T getMessageUnsafe(T defaultInstance) throws InvalidProtocolBufferException {
      // There's a risk that we'll double-parse the bytes, but that's OK, because it'll end up
      // as the same immutable object anyway.
      if (message == null) {
        message = (T) defaultInstance.toBuilder().mergeFrom(bytes).build();
      }
      return message;
    }
  }

  /** Parses a proto, throwing parser errors as runtime exceptions. */
  @SuppressWarnings("unchecked") // We want to eventually optimize away parser classes
  public static <T extends MessageLite> T mergeFrom(byte[] bytes, T defaultInstance) {
    try {
      return (T) defaultInstance.toBuilder().mergeFrom(bytes).build();
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }
}

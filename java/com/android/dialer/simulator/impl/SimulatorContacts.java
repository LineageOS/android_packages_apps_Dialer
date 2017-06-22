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

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.google.auto.value.AutoValue;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Populates the device database with contacts. */
public final class SimulatorContacts {
  // Phone numbers from https://www.google.com/about/company/facts/locations/
  private static final Contact[] SIMPLE_CONTACTS = {
    // US, contact with e164 number.
    Contact.builder()
        .setName("Michelangelo")
        .addPhoneNumber(new PhoneNumber("+1-302-6365454", Phone.TYPE_MOBILE))
        .addEmail(new Email("m@example.com"))
        .setIsStarred(true)
        .setOrangePhoto()
        .build(),
    // US, contact with a non-e164 number.
    Contact.builder()
        .setName("Leonardo da Vinci")
        .addPhoneNumber(new PhoneNumber("(425) 739-5600", Phone.TYPE_MOBILE))
        .addEmail(new Email("l@example.com"))
        .setIsStarred(true)
        .setBluePhoto()
        .build(),
    // UK, number where the (0) should be dropped.
    Contact.builder()
        .setName("Raphael")
        .addPhoneNumber(new PhoneNumber("+44 (0) 20 7031 3000", Phone.TYPE_MOBILE))
        .addEmail(new Email("r@example.com"))
        .setIsStarred(true)
        .setRedPhoto()
        .build(),
    // US and Australia, contact with a long name and multiple phone numbers.
    Contact.builder()
        .setName("Donatello di Niccolò di Betto Bardi")
        .addPhoneNumber(new PhoneNumber("+1-650-2530000", Phone.TYPE_HOME))
        .addPhoneNumber(new PhoneNumber("+1 404-487-9000", Phone.TYPE_WORK))
        .addPhoneNumber(new PhoneNumber("+61 2 9374 4001", Phone.TYPE_FAX_HOME))
        .setIsStarred(true)
        .setPurplePhoto()
        .build(),
    // US, phone number shared with another contact and 2nd phone number with wait and pause.
    Contact.builder()
        .setName("Splinter")
        .addPhoneNumber(new PhoneNumber("+1-650-2530000", Phone.TYPE_HOME))
        .addPhoneNumber(new PhoneNumber("+1 303-245-0086;123,456", Phone.TYPE_WORK))
        .build(),
    // France, number with Japanese name.
    Contact.builder()
        .setName("スパイク・スピーゲル")
        .addPhoneNumber(new PhoneNumber("+33 (0)1 42 68 53 00", Phone.TYPE_MOBILE))
        .build(),
    // Israel, RTL name and non-e164 number.
    Contact.builder()
        .setName("עקב אריה טברסק")
        .addPhoneNumber(new PhoneNumber("+33 (0)1 42 68 53 00", Phone.TYPE_MOBILE))
        .build(),
    // UAE, RTL name.
    Contact.builder()
        .setName("سلام دنیا")
        .addPhoneNumber(new PhoneNumber("+971 4 4509500", Phone.TYPE_MOBILE))
        .build(),
    // Brazil, contact with no name.
    Contact.builder()
        .addPhoneNumber(new PhoneNumber("+55-31-2128-6800", Phone.TYPE_MOBILE))
        .build(),
    // Short number, contact with no name.
    Contact.builder().addPhoneNumber(new PhoneNumber("611", Phone.TYPE_MOBILE)).build(),
    // US, number with an anonymous prefix.
    Contact.builder()
        .setName("Anonymous")
        .addPhoneNumber(new PhoneNumber("*86 512-343-5283", Phone.TYPE_MOBILE))
        .build(),
    // None, contact with no phone number.
    Contact.builder()
        .setName("No Phone Number")
        .addEmail(new Email("no@example.com"))
        .setIsStarred(true)
        .build(),
  };

  @WorkerThread
  public static void populateContacts(@NonNull Context context) {
    Assert.isWorkerThread();
    ArrayList<ContentProviderOperation> operations = new ArrayList<>();
    for (Contact contact : SIMPLE_CONTACTS) {
      addContact(contact, operations);
    }
    try {
      context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);
    } catch (RemoteException | OperationApplicationException e) {
      Assert.fail("error adding contacts: " + e);
    }
  }

  private static void addContact(Contact contact, List<ContentProviderOperation> operations) {
    int index = operations.size();

    operations.add(
        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, contact.getAccountType())
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, contact.getAccountName())
            .withValue(ContactsContract.RawContacts.STARRED, contact.getIsStarred() ? 1 : 0)
            .withYieldAllowed(true)
            .build());

    if (!TextUtils.isEmpty(contact.getName())) {
      operations.add(
          ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
              .withValue(
                  ContactsContract.Data.MIMETYPE,
                  ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
              .withValue(
                  ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.getName())
              .build());
    }

    if (contact.getPhotoStream() != null) {
      operations.add(
          ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
              .withValue(
                  ContactsContract.Data.MIMETYPE,
                  ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
              .withValue(
                  ContactsContract.CommonDataKinds.Photo.PHOTO,
                  contact.getPhotoStream().toByteArray())
              .build());
    }

    for (PhoneNumber phoneNumber : contact.getPhoneNumbers()) {
      operations.add(
          ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
              .withValue(
                  ContactsContract.Data.MIMETYPE,
                  ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
              .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber.value)
              .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneNumber.type)
              .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, phoneNumber.label)
              .build());
    }

    for (Email email : contact.getEmails()) {
      operations.add(
          ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
              .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
              .withValue(
                  ContactsContract.Data.MIMETYPE,
                  ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
              .withValue(ContactsContract.CommonDataKinds.Email.DATA, email.value)
              .withValue(ContactsContract.CommonDataKinds.Email.TYPE, email.type)
              .withValue(ContactsContract.CommonDataKinds.Email.LABEL, email.label)
              .build());
    }
  }

  @AutoValue
  abstract static class Contact {
    @NonNull
    abstract String getAccountType();

    @NonNull
    abstract String getAccountName();

    @Nullable
    abstract String getName();

    abstract boolean getIsStarred();

    @Nullable
    abstract ByteArrayOutputStream getPhotoStream();

    @NonNull
    abstract List<PhoneNumber> getPhoneNumbers();

    @NonNull
    abstract List<Email> getEmails();

    static Builder builder() {
      return new AutoValue_SimulatorContacts_Contact.Builder()
          .setAccountType("com.google")
          .setAccountName("foo@example")
          .setIsStarred(false)
          .setPhoneNumbers(new ArrayList<>())
          .setEmails(new ArrayList<>());
    }

    @AutoValue.Builder
    abstract static class Builder {
      @NonNull private final List<PhoneNumber> phoneNumbers = new ArrayList<>();
      @NonNull private final List<Email> emails = new ArrayList<>();

      abstract Builder setAccountType(@NonNull String accountType);

      abstract Builder setAccountName(@NonNull String accountName);

      abstract Builder setName(@NonNull String name);

      abstract Builder setIsStarred(boolean isStarred);

      abstract Builder setPhotoStream(ByteArrayOutputStream photoStream);

      abstract Builder setPhoneNumbers(@NonNull List<PhoneNumber> phoneNumbers);

      abstract Builder setEmails(@NonNull List<Email> emails);

      abstract Contact build();

      Builder addPhoneNumber(PhoneNumber phoneNumber) {
        phoneNumbers.add(phoneNumber);
        return setPhoneNumbers(phoneNumbers);
      }

      Builder addEmail(Email email) {
        emails.add(email);
        return setEmails(emails);
      }

      Builder setRedPhoto() {
        setPhotoStream(getPhotoStreamWithColor(Color.rgb(0xe3, 0x33, 0x1c)));
        return this;
      }

      Builder setBluePhoto() {
        setPhotoStream(getPhotoStreamWithColor(Color.rgb(0x00, 0xaa, 0xe6)));
        return this;
      }

      Builder setOrangePhoto() {
        setPhotoStream(getPhotoStreamWithColor(Color.rgb(0xea, 0x95, 0x00)));
        return this;
      }

      Builder setPurplePhoto() {
        setPhotoStream(getPhotoStreamWithColor(Color.rgb(0x99, 0x5a, 0xa0)));
        return this;
      }

      /** Creates a contact photo with a green background and a circle of the given color. */
      private static ByteArrayOutputStream getPhotoStreamWithColor(int color) {
        int width = 300;
        int height = 300;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.argb(0xff, 0x4c, 0x9c, 0x23));
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(width / 2, height / 2, width / 3, paint);

        ByteArrayOutputStream photoStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 75, photoStream);
        return photoStream;
      }
    }
  }

  static class PhoneNumber {
    public final String value;
    public final int type;
    public final String label;

    PhoneNumber(String value, int type) {
      this.value = value;
      this.type = type;
      label = "simulator phone number";
    }
  }

  static class Email {
    public final String value;
    public final int type;
    public final String label;

    Email(String simpleEmail) {
      value = simpleEmail;
      type = ContactsContract.CommonDataKinds.Email.TYPE_WORK;
      label = "simulator email";
    }
  }

  private SimulatorContacts() {}
}

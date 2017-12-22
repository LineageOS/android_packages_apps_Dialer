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
 * limitations under the License.
 */

package com.android.dialer.speeddial;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.contactsfragment.ContactsFragment;
import com.android.dialer.contactsfragment.ContactsFragment.OnContactSelectedListener;

/**
 * Activity for selecting a single contact and adding it to favorites.
 *
 * <p>Contacts are displayed using {@link ContactsFragment}. Contacts are searchable via search bar
 * in the toolbar. When a contact is selected, it's uri is passed back in the result data.
 */
public class AddFavoriteActivity extends AppCompatActivity implements OnContactSelectedListener {

  private ContactsFragment contactsFragment;

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.add_favorite_activity);
    contactsFragment = ContactsFragment.newAddFavoritesInstance();
    getFragmentManager()
        .beginTransaction()
        .add(R.id.add_favorite_container, contactsFragment, null)
        .commit();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.add_favorite_menu, menu);

    MenuItem searchItem = menu.findItem(R.id.action_search);
    SearchView searchView = (SearchView) searchItem.getActionView();
    searchView.setOnQueryTextListener(
        new SearchView.OnQueryTextListener() {
          @Override
          public boolean onQueryTextSubmit(String query) {
            if (!searchView.isIconified()) {
              searchView.setIconified(true);
            }
            searchItem.collapseActionView();
            return false;
          }

          @Override
          public boolean onQueryTextChange(String s) {
            contactsFragment.updateQuery(s);
            return false;
          }
        });
    return true;
  }

  @Override
  public void onContactSelected(ImageView photo, Uri contactUri, long contactId) {
    DialerExecutorComponent.get(this)
        .dialerExecutorFactory()
        .createUiTaskBuilder(
            getFragmentManager(), "mark_contact_favorite", this::markContactStarred)
        .onSuccess(output -> finish())
        .onFailure(this::onContactStarredFailed)
        .build()
        .executeParallel(contactId);
  }

  @WorkerThread
  private int markContactStarred(long contactId) {
    // TODO(calderwoodra): For now, we will just mark contacts as starred. This means that contacts
    // will only be able to exist once in favorites until we implement multiple contact avenues.
    ContentValues contentValues = new ContentValues();
    contentValues.put(Contacts.STARRED, 1);

    String where = Contacts._ID + " = ?";
    String[] selectionArgs = new String[] {Long.toString(contactId)};
    return getContentResolver().update(Contacts.CONTENT_URI, contentValues, where, selectionArgs);
  }

  private void onContactStarredFailed(Throwable throwable) {
    throw Assert.createAssertionFailException(throwable.getMessage());
  }
}

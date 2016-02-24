/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.RecipientsPreferences;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.documents.UnregisteredUser;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.whispersystems.libaxolotl.util.guava.Optional;

/**
 * A simple view to show the recipients of a message
 *
 * @author Jake McGinty
 */
public class MessageRecipientListItem extends RelativeLayout
    implements Recipient.RecipientModifiedListener
{
  private final static String TAG = MessageRecipientListItem.class.getSimpleName();

  private Recipient       recipient;
  private FromTextView    fromView;
  private TextView        errorDescription;
  private Button          unregisteredButton;
  private Button          conflictButton;
  private Button          resendButton;
  private AvatarImageView contactPhotoImage;

  private final Handler handler = new Handler();

  public MessageRecipientListItem(Context context) {
    super(context);
  }

  public MessageRecipientListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    this.fromView           = (FromTextView)    findViewById(R.id.from);
    this.errorDescription   = (TextView)        findViewById(R.id.error_description);
    this.contactPhotoImage  = (AvatarImageView) findViewById(R.id.contact_photo_image);
    this.unregisteredButton = (Button)          findViewById(R.id.unregistered_button);
    this.conflictButton     = (Button)          findViewById(R.id.conflict_button);
    this.resendButton       = (Button)          findViewById(R.id.resend_button);
  }

  public void set(final MasterSecret masterSecret,
                  final MessageRecord record,
                  final Recipient recipient,
                  final boolean isPushGroup)
  {
    this.recipient = recipient;

    recipient.addListener(this);
    fromView.setText(recipient);
    contactPhotoImage.setAvatar(recipient, false);
    setIssueIndicators(masterSecret, record, isPushGroup);
  }

  private void setIssueIndicators(final MasterSecret masterSecret,
                                  final MessageRecord record,
                                  final boolean isPushGroup)
  {
    final NetworkFailure      networkFailure   = getNetworkFailure(record);
    final UnregisteredUser    unregisteredUser = getUnregisteredUser(record);
    final IdentityKeyMismatch keyMismatch      = networkFailure == null ? getKeyMismatch(record) : null;

    String errorText = "";

    if (keyMismatch != null) {
      unregisteredButton.setVisibility(View.GONE);
      resendButton.setVisibility(View.GONE);
      conflictButton.setVisibility(View.VISIBLE);

      errorText = getContext().getString(R.string.MessageDetailsRecipient_new_identity);
      conflictButton.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          new ConfirmIdentityDialog(getContext(), masterSecret, record, keyMismatch).show();
        }
      });
    } else if (networkFailure != null || (!isPushGroup && record.isFailed())) {
      unregisteredButton.setVisibility(View.GONE);
      resendButton.setVisibility(View.VISIBLE);
      resendButton.setEnabled(true);
      conflictButton.setVisibility(View.GONE);

      errorText = getContext().getString(R.string.MessageDetailsRecipient_failed_to_send);
      resendButton.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          resendButton.setEnabled(false);
          new ResendAsyncTask(masterSecret, record, networkFailure).execute();
        }
      });
    } else if (unregisteredUser != null) {
      resendButton.setVisibility(View.GONE);
      conflictButton.setVisibility(View.GONE);

      errorText = "No longer registered";

      Optional<RecipientsPreferences> prefs = DatabaseFactory.getRecipientPreferenceDatabase(getContext())
                                                             .getRecipientsPreferences(new long[] {recipient.getRecipientId()});
      // TODO: Should we always display the "More Info" button instead of hiding it after the user has seen the UserUnregisteredDialog for this recipient?
      if (!prefs.isPresent() || !prefs.get().hasSeenUserUnregistered()) {
        unregisteredButton.setVisibility(View.VISIBLE);
        unregisteredButton.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
            new UserUnregisteredDialog(getContext(), masterSecret, record, unregisteredUser);
          }
        });
      } else {
        unregisteredButton.setVisibility(View.GONE);
      }
    } else {
      unregisteredButton.setVisibility(View.GONE);
      resendButton.setVisibility(View.GONE);
      conflictButton.setVisibility(View.GONE);
    }

    errorDescription.setText(errorText);
    errorDescription.setVisibility(TextUtils.isEmpty(errorText) ? View.GONE : View.VISIBLE);
  }

  private NetworkFailure getNetworkFailure(final MessageRecord record) {
    if (record.hasNetworkFailures()) {
      for (final NetworkFailure failure : record.getNetworkFailures()) {
        if (failure.getRecipientId() == recipient.getRecipientId()) {
          return failure;
        }
      }
    }
    return null;
  }

  private UnregisteredUser getUnregisteredUser(final MessageRecord record) {
    if (record.hasUnregisteredUsers()) {
      for (final UnregisteredUser user : record.getUnregisteredUsers()) {
        if (user.getRecipientId() == recipient.getRecipientId()) {
          return user;
        }
      }
    }
    return null;
  }

  private IdentityKeyMismatch getKeyMismatch(final MessageRecord record) {
    if (record.isIdentityMismatchFailure()) {
      for (final IdentityKeyMismatch mismatch : record.getIdentityKeyMismatches()) {
        if (mismatch.getRecipientId() == recipient.getRecipientId()) {
          return mismatch;
        }
      }
    }
    return null;
  }

  public void unbind() {
    if (this.recipient != null) this.recipient.removeListener(this);
  }

  @Override
  public void onModified(final Recipient recipient) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        fromView.setText(recipient);
        contactPhotoImage.setAvatar(recipient, false);
      }
    });
  }

  private class ResendAsyncTask extends AsyncTask<Void,Void,Void> {
    private final MasterSecret   masterSecret;
    private final MessageRecord  record;
    private final NetworkFailure failure;

    public ResendAsyncTask(MasterSecret masterSecret, MessageRecord record, NetworkFailure failure) {
      this.masterSecret = masterSecret;
      this.record       = record;
      this.failure      = failure;
    }

    @Override
    protected Void doInBackground(Void... params) {
      MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(getContext());
      mmsDatabase.removeFailure(record.getId(), failure);

      if (record.getRecipients().isGroupRecipient()) {
        MessageSender.resendGroupMessage(getContext(), masterSecret, record, failure.getRecipientId());
      } else {
        MessageSender.resend(getContext(), masterSecret, record);
      }
      return null;
    }
  }

}

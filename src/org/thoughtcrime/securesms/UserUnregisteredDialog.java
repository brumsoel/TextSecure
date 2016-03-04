package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.documents.UnregisteredUser;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;

public class UserUnregisteredDialog extends AlertDialog {

  private static final String TAG = UserUnregisteredDialog.class.getSimpleName();

  private OnClickListener callback;

  public UserUnregisteredDialog(Context context,
                                MasterSecret masterSecret,
                                MessageRecord messageRecord,
                                UnregisteredUser user)
  {
    super(context);
    Recipient recipient = RecipientFactory.getRecipientForId(context, user.getRecipientId(), false);
    String    name      = recipient.toShortString();
    String    message   = String.format("%1$s has unregistered and is no longer a Signal user. %1$s won't receive your group messages until they reregister.", name);

    setTitle(name);
    setMessage(message);

    setButton(AlertDialog.BUTTON_POSITIVE, "Got it", new AcceptListener(masterSecret, messageRecord, user));
  }

  @Override
  public void show() {
    super.show();
    ((TextView)this.findViewById(android.R.id.message))
                   .setMovementMethod(LinkMovementMethod.getInstance());
  }

  public void setCallback(OnClickListener callback) {
    this.callback = callback;
  }

  private class AcceptListener implements OnClickListener {

    private final MasterSecret     masterSecret;
    private final MessageRecord    messageRecord;
    private final UnregisteredUser user;

    private AcceptListener(MasterSecret masterSecret, MessageRecord messageRecord, UnregisteredUser user) {
      this.masterSecret  = masterSecret;
      this.messageRecord = messageRecord;
      this.user          = user;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      new AsyncTask<Void, Void, Void>()
      {
        @Override
        protected Void doInBackground(Void... params) {
          Recipients recipients = RecipientFactory.getRecipientsForIds(getContext(), new long[]{user.getRecipientId()}, false);
          DatabaseFactory.getRecipientPreferenceDatabase(getContext()).setSeenUserUnregistered(recipients, true);

          processPendingMessageRecords(messageRecord.getThreadId());

          return null;
        }

        private void processMessageRecord(MessageRecord record) {
          if (!record.isIdentityMismatchFailure() &&
              !record.hasNetworkFailures()        &&
              !record.hasUnseenUnregisteredUsers())
          {
            MmsDatabase database = DatabaseFactory.getMmsDatabase(getContext());

            database.markAsSecure(record.getId());
            database.markAsSent(record.getId());
          }
        }

        private void processPendingMessageRecords(long threadId) {
          MmsDatabase        database = DatabaseFactory.getMmsDatabase(getContext());
          MmsDatabase.Reader reader   = database.getUnregisteredUserMessagesForThread(masterSecret, threadId);
          MessageRecord      record;

          try {
            while ((record = reader.getNext()) != null) {
              for (UnregisteredUser recordUser : record.getUnregisteredUsers()) {
                if (user.equals(recordUser)) {
                  processMessageRecord(record);
                }
              }
            }
          } finally {
            if (reader != null) reader.close();
          }
        }
      }.execute();

      if (callback != null) callback.onClick(null, 0);
    }
  }
}

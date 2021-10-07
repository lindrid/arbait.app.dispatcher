package `in`.arbait.http.poll_service

import `in`.arbait.APP_ID_ARG
import `in`.arbait.MainActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val APP_NO_ID = -1
private const val TAG = "NotificationTapReceiver"

class NotificationTapReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    Log.i(TAG,"NotificationTapReceiver.onReceive()")

    val appId = intent.getIntExtra(APP_ID_ARG, APP_NO_ID)
    val intentMain = Intent(context, MainActivity::class.java).apply {
      putExtra(APP_ID_ARG, appId)
      action = Intent.ACTION_MAIN
    }
    context.startActivity(intentMain)
  }
}
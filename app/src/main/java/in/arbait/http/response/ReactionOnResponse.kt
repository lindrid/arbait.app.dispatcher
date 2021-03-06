package `in`.arbait.http

import `in`.arbait.*
import `in`.arbait.http.response.Response
import `in`.arbait.http.response.SERVER_ERROR
import `in`.arbait.http.response.SYSTEM_ERROR
import android.content.Context
import android.util.Log
import android.view.View

const val OLD_VERSION_ERROR_CODE = 99
const val END_SESSION_ERROR_CODE = 100

abstract class ReactionOnResponse (
  private val TAG: String,
  private val context: Context,
  private val view: View,
  private val response: Response
) {

  abstract fun doOnServerOkResult()
  abstract fun doOnServerFieldValidationError(response: Response)
  abstract fun doOnEndSessionError()

  fun doOnServerError() {
    App.noInternetErrorCouldShown = true

    if (response.isItValidationError) {
      Log.i(TAG, "Поле: ${response.errorValidationField}")
      doOnServerFieldValidationError(response)
      return
    }

    if (response.isItErrorWithCode) {
      Log.i (TAG, "Response type ${response.type}, message: ${response.message}")

      when(response.code) {
        END_SESSION_ERROR_CODE -> {
          doOnEndSessionError()
          return
        }
        else -> {
          response.message?.let {
            showErrorBalloon(context, view, it)
            return
          }
        }
      }
    }

    App.res?.let {
      val unknownServerError = it.getString(R.string.unknown_server_error, response.message)
      showErrorBalloon(context, view, unknownServerError)
    }
  }

  fun doOnSystemError() {
    if (!internetIsAvailable()) {
      if (App.noInternetErrorCouldShown) {
        showErrorBalloon(context, view, R.string.internet_is_not_available)
        App.noInternetErrorCouldShown = false
      }
      return
    }

    App.res?.let {
      val systemError = it.getString(R.string.system_error, response.message)
      showErrorBalloon(context, view, systemError)
      App.noInternetErrorCouldShown = true
    }
  }


  fun serverValidationError(): String {
    App.res?.let {
      return it.getString(
        R.string.server_validation_error,
        response.errorValidationField,
        response.message
      )
    }
    return "ServerValidationError"
  }

  companion object {
    fun doOnFailure (response: Response, context: Context, view: View) {
      when (response.type) {
        SYSTEM_ERROR -> {
          if (!internetIsAvailable()) {
            showErrorBalloon(context, view, R.string.internet_is_not_available)
            return
          }

          App.res?.let {
            val systemError = it.getString(R.string.system_error, response.message)
            showErrorBalloon(context, view, systemError)
          }
        }
        SERVER_ERROR -> {
          App.res?.let {
            response.message?.let { errorMsg ->
              showErrorBalloon(context, view, errorMsg)
            }
          }
        }
      }
    }
  }
}
package `in`.arbait

import `in`.arbait.database.User
import `in`.arbait.models.ApplicationItem
import `in`.arbait.http.sessionIsAlive
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


private const val TAG = "MainActivity"


class MainActivity : AppCompatActivity() {

  private lateinit var repository: UserRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    repository = UserRepository.get()

    GlobalScope.launch {
      val lastUser: User? = repository.getUserLastByDate()
      Log.i (TAG, "Repository.getUserLastByDate(): $lastUser")

      val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

      if (currentFragment == null) {
        var fragment: Fragment = RegistrationFragment()

        if (lastUser != null) {
          if (lastUser.isConfirmed) {
            fragment = ApplicationsFragment()
          }
          else if (sessionIsAlive(lastUser.createdAt)) {
            fragment = PhoneConfirmationFragment(lastUser)
          }
        }

        supportFragmentManager
          .beginTransaction()
          .add(R.id.fragment_container, fragment)
          .commit()
      }
    }
  }

  fun replaceOnFragment (fragmentName: String, args: Bundle = Bundle()) {
    val fragment: Fragment = when (fragmentName) {
      "PhoneConfirmation" -> {
        val user = args.getSerializable(USER_ARG) as User
        PhoneConfirmationFragment(user)
      }
      "Application" -> {
        val appItem = args.getSerializable(APP_ARG) as ApplicationItem
        ApplicationFragment(appItem)
      }
      "Applications" -> ApplicationsFragment()
      else -> RegistrationFragment()
    }

    supportFragmentManager
      .beginTransaction()
      .replace(R.id.fragment_container, fragment)
      .addToBackStack(null)
      .commit()
  }
}
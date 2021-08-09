package `in`.arbait

import `in`.arbait.http.*
import android.graphics.Color
import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import java.util.*

const val BIRTH_DATE_KEY = "birthDate"

private const val TAG = "RegistrationFragment"
private const val BIRTH_DATE_DIALOG_TAG = "BirthDateFragmentDialog"

private const val DEFAULT_BIRTH_DATE = "01.02.1995"
private const val DATE_FORMAT1 = "dd.MM.yyyy"
private const val DATE_FORMAT2 = "dd-MM-yyyy"
private const val DATE_FORMAT3 = "dd/MM/yyyy"
private const val WORKER_AGE_FROM = 18
private const val WORKER_AGE_UP_TO = 65

class RegistrationFragment : Fragment() {

  private val server = Server()

  private lateinit var tvRegistration: TextView
  private lateinit var etFirstName: EditText
  private lateinit var etLastName: EditText
  private lateinit var etBirthDate: EditText
  private lateinit var etPhone: EditText
  private lateinit var etPhoneWhatsapp: EditText
  private lateinit var btSamePhone: Button
  private lateinit var etPassword: EditText
  private lateinit var btDone: Button

  private lateinit var balloon: Balloon
  private lateinit var userBirthDate: Date
  private lateinit var birthDateFragmentDialog: BirthDateFragmentDialog

  private lateinit var supportFragmentManager: FragmentManager

  private val setPhoneWaEqualsToPhone = { _: View ->
    etPhoneWhatsapp.text = etPhone.text
  }


  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.fragment_registration, container, false)
    supportFragmentManager = requireActivity().supportFragmentManager

    tvRegistration = view.findViewById(R.id.tv_reg_registration)

    etFirstName = view.findViewById(R.id.et_reg_first_name)
    etLastName = view.findViewById(R.id.et_reg_last_name)
    etBirthDate = view.findViewById(R.id.et_reg_birth_date)
    etPhone = view.findViewById(R.id.et_reg_phone)
    etPhoneWhatsapp = view.findViewById(R.id.et_reg_phone_whatsapp)
    btSamePhone = view.findViewById(R.id.bt_reg_same_phone)
    etPassword = view.findViewById(R.id.et_reg_password)
    btDone = view.findViewById(R.id.bt_reg_done)

    etPhone.addTextChangedListener(PhoneNumberFormattingTextWatcher())
    etPhoneWhatsapp.addTextChangedListener(PhoneNumberFormattingTextWatcher())

    btSamePhone.setOnClickListener(setPhoneWaEqualsToPhone)

    btDone.setOnClickListener {
      val user = User (
        id = null,
        firstName = etFirstName.text.toString(),
        lastName = etLastName.text.toString(),
        birthDate = etBirthDate.text.toString(),
        phone = etPhone.text.toString(),
        phoneWa = etPhoneWhatsapp.text.toString(),
        password = etPassword.text.toString()
      )
      Log.i (TAG, "User = ${user.toString()}")

      if (isInputValid(user)) {
        server.registerUser(user) { response: Response ->
          onResult(response)
        }
      }
    }

    etFirstName.setText("Дмитрий")
    etLastName.setText("Федоров")
    etBirthDate.setText("")
    etPhone.setText("89240078897")
    etPhoneWhatsapp.setText("89240078897")
    etPassword.setText("12345")

    Log.i (TAG, "manufacturer is $MANUFACTURER")
    Log.i (TAG, "Android v ersion is $VERSION")

    if (isSamsung() && versionIsNineOrGreater()) {
      Log.i(TAG, "Manufacturer is samsung and version >= 9")

      setBirthDateEditTextWhenDialogResult()

      etBirthDate.setOnClickListener {
        createBirthDateDialog()
      }

      etBirthDate.setOnFocusChangeListener { view, hasFocus ->
        if (hasFocus) {
          createBirthDateDialog()
        }
      }
    }

    return view
  }


  private fun setBirthDateEditTextWhenDialogResult() {
    supportFragmentManager.setFragmentResultListener(BIRTH_DATE_KEY, viewLifecycleOwner)
    { _, bundle ->
      etBirthDate.setText(bundle.getString(BIRTH_DATE_KEY))
    }
  }

  private fun createBirthDateDialog() {
    val date = getValidBirthDateForSamsung9()
    birthDateFragmentDialog = BirthDateFragmentDialog.newInstance(date)
    birthDateFragmentDialog.show(supportFragmentManager, BIRTH_DATE_DIALOG_TAG)
  }

  private fun getValidBirthDateForSamsung9(): Date {
    val dateStr = etBirthDate.text.toString()
    val date = strToDate(dateStr, DATE_FORMAT1)
    if (date != null) {
      return date
    }

    return strToDate(DEFAULT_BIRTH_DATE, DATE_FORMAT1)!!
  }

  private fun onResult (response: Response) {
    when (response.code) {
      SERVER_OK -> {
        Log.i (TAG,"Все ок, сервер вернул: ${response.message}")
      }
      else -> {
        Log.i (TAG,"Регистрация не прошла, error is ${response.message}")
        if (response.isItValidationError) {
          Log.i (TAG, "Поле: ${response.errorValidationField}")
        }
      }
    }
  }

  private fun isInputValid(user: User): Boolean {
    if (user.birthDate.isNullOrEmpty()) {
      Log.i (TAG, "Укажите дату рождения!")

      balloon = createBalloon(requireContext()) {
        setArrowSize(10)
        setWidth(BalloonSizeSpec.WRAP)
        setHeight(65)
        setArrowPosition(0.7f)
        setCornerRadius(4f)
        setAlpha(0.9f)
        setText("Укажите дату рождения! Пример правильной даты: 01.02.1995")
        setTextColorResource(R.color.white)
        setTextIsHtml(true)
        setBackgroundColor(Color.RED)
        setBalloonAnimation(BalloonAnimation.FADE)
        setLifecycleOwner(lifecycleOwner)
      }

      balloon.showAlignBottom(etBirthDate)
      return false
    }

    return if (isValidDate(user.birthDate)) {
      val currentTime = Calendar.getInstance().time
      val age = getDiffYears(userBirthDate, currentTime)

      Log.i (TAG, "Age is $age")

      if (age in WORKER_AGE_FROM..WORKER_AGE_UP_TO) {
        Log.i (TAG, "${user.birthDate} is valid date, $userBirthDate")
        true
      }
      else {
        Log.i (TAG, "У нас принимаются работники от $WORKER_AGE_FROM до" +
            "$WORKER_AGE_UP_TO лет, $userBirthDate")
        false
      }
    }
    else {
      Log.i (TAG, "${user.birthDate} is invalid date, $userBirthDate")
      false
    }

    return false
  }

  private fun isValidDate (dateStr: String): Boolean {
    return  isValidFormatDate(dateStr, DATE_FORMAT1) ||
            isValidFormatDate(dateStr, DATE_FORMAT2) ||
            isValidFormatDate(dateStr, DATE_FORMAT3)
  }

  private fun isValidFormatDate (dateStr: String, format: String): Boolean {
    val date = strToDate(dateStr, format)
    if (date != null) {
      userBirthDate = date
      return true
    }
    return false
  }
}
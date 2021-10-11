package `in`.arbait

import `in`.arbait.database.EnrollingPermission
import `in`.arbait.http.Server
import `in`.arbait.http.items.ApplicationItem
import `in`.arbait.http.items.DebitCardItem
import `in`.arbait.http.items.PhoneItem
import `in`.arbait.http.items.PorterItem
import `in`.arbait.http.response.SERVER_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.pow


private const val TAG = "ApplicationFragment"

private const val DISABLE_BTN_MILLISECONDS = 1 * 10 * 1000  // 10 sec
private const val ONE_MINUTE = 60 * 1000

const val APP_REFUSE_DIALOG_TAG = "ApplicationRefuseDialog"
const val DEBIT_CARD_DIALOG_TAG = "DebitCardDialog"
const val APPLICATION_KEY = "application"

const val PHONE_CALL = 1
const val PHONE_WHATSAPP = 2
const val PHONE_CALL_AND_WHATSAPP = 3

const val PM_CARD = 1
const val PM_CASH = 2

class ApplicationFragment (private val appId: Int): Fragment()
{
  private var btnClickCount = 0
  private var porter: PorterItem? = null
  private var porterIsEnrolled = false
  private lateinit var lvdAppItem: MutableLiveData<ApplicationItem>

  private lateinit var server: Server
  private lateinit var supportFragmentManager: FragmentManager
  private lateinit var tvEnrolled: AppCompatTextView
  private lateinit var tvAddress: AppCompatTextView
  private lateinit var tvTime: AppCompatTextView
  private lateinit var tvIncome: AppCompatTextView
  private lateinit var tvDescription: AppCompatTextView
  private lateinit var tvPayMethod: AppCompatTextView
  private lateinit var tvPortersCount: AppCompatTextView
  private lateinit var rvPorters: RecyclerView
  private lateinit var btCallClient: AppCompatButton
  private lateinit var btEnrollRefuse: AppCompatButton
  private lateinit var btBack: AppCompatButton
  private lateinit var btChangeDebitCard: AppCompatButton
  private lateinit var nsvApp: NestedScrollView

  private val vm: PollServerViewModel by lazy {
    val mainActivity = requireActivity() as MainActivity
    mainActivity.pollServerViewModel
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                            savedInstanceState: Bundle?): View?
  {
    val view = inflater.inflate(R.layout.fragment_application, container, false)
    vm.rootView = view

    supportFragmentManager = requireActivity().supportFragmentManager
    server = Server(requireContext())

    Log.i (TAG, "OnCreate()")

    setViews(view)
    setAppItem()
    setPorter()
    updateUI()

    rvPorters.layoutManager = LinearLayoutManager(context)
    setAppObserver()
    setVisibilityToViews(porterIsEnrolled, view)

    btEnrollRefuse.setOnClickListener {
      onEnrollRefuseBtnClick(view)
    }

    btChangeDebitCard.setOnClickListener {
      onChangeDebitCardClick()
    }

    btBack.setOnClickListener {
      vm.mainActivity.replaceOnFragment("Applications")
    }

    btCallClient.setOnClickListener {
      onCallClientBtnClick()
    }

    return view
  }

  private fun onCallClientBtnClick() {
    lvdAppItem.value?.let { appItem ->
      val intent = Intent(Intent.ACTION_DIAL)
      val uriStr = "tel:${appItem.clientPhoneNumber}"
      intent.data = Uri.parse(uriStr)
      context?.startActivity(intent)
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
    val appName = getString(R.string.app_name)
    val app = getString(R.string.app_action_bar_title)
    actionBar?.title = "$appName - $app"
  }


  private fun setAppItem() {
    vm.lvdOpenApps[appId]?.let {
      lvdAppItem = it
      return
    }
    vm.lvdTakenApps[appId]?.let {
      lvdAppItem = it
    }
  }

  private fun setPorter() {
    lvdAppItem.value?.let { appItem ->
      Log.i (TAG, "set:lvdAppItem")
      porter = getThisUserPorter(appItem)
      Log.i (TAG, "Porter= $porter")
      porter?.let {
        porterIsEnrolled = true
      }
    }
  }

  private fun onChangeDebitCardClick() {
    App.userItem?.let { user ->
      val dcd = DebitCardDialog.newInstance(appId, user, itIsChangingCard = true)
      dcd.show(supportFragmentManager, DEBIT_CARD_DIALOG_TAG)

      supportFragmentManager.setFragmentResultListener(APPLICATION_KEY, viewLifecycleOwner)
      { _, bundle ->
        val app = bundle.getSerializable(APPLICATION_KEY) as ApplicationItem
        Log.i (TAG, "when change card, app from dialog = $app")
        lvdAppItem.value = app
      }
    }
  }

  private fun onEnrollRefuseBtnClick(view: View) {
    val porterWantToEnroll = !porterIsEnrolled
    val porterWantToRefuse = porterIsEnrolled

    GlobalScope.launch {
      App.userItem?.let { user ->
        val enrollingPermission = App.repository.getEnrollingPermission(user.id)
        val now = Date().time

        val passMoreThenOneMinuteAfterLastClick = (enrollingPermission != null &&
          now > enrollingPermission.lastClickTime + ONE_MINUTE
        )

        val clickCount =
          if (enrollingPermission == null || passMoreThenOneMinuteAfterLastClick) {
            if (porterWantToEnroll) 1 else 0
          }
          else {
            enrollingPermission.clickCountWithinOneMin + 1
          }

        val newEnrollingPermission = EnrollingPermission(
          userId = user.id,
          clickCountWithinOneMin = clickCount,
          enableClickTime = now,
          lastClickTime = now
        )

        if (porterWantToEnroll) {
          val debitCardDialog = DebitCardDialog.newInstance(appId, user)
          debitCardDialog.show(supportFragmentManager, DEBIT_CARD_DIALOG_TAG)
          porterIsEnrolled = false
          reactOnDebitCardDialog(view, enrollingPermission, newEnrollingPermission)
        }

        if (porterWantToRefuse) {
          ApplicationRefuseDialog().show(supportFragmentManager, APP_REFUSE_DIALOG_TAG)
          reactOnRefuseDialog(view, enrollingPermission, newEnrollingPermission, clickCount)
        }
      }
    }
  }

  private fun reactOnDebitCardDialog(view: View, ep: EnrollingPermission?, newEp: EnrollingPermission)
  {
    supportFragmentManager.setFragmentResultListener(APPLICATION_KEY, viewLifecycleOwner)
    { _, bundle ->
      porterIsEnrolled = true
      val app = bundle.getSerializable(APPLICATION_KEY) as ApplicationItem
      Log.i (TAG, "app from dialog = $app")

      vm.lvdTakenApps[appId] = MutableLiveData(app)
      vm.lvdTakenApps[appId]?.let {
        lvdAppItem = it
      }

      setAppObserver()
      setVisibilityToViews(true, view)

      Log.i (TAG, "newEnrollingPermission = $newEp")
      addOrUpdateEnrollPermission(ep, newEp)

      porterIsEnrolled = !porterIsEnrolled
    }
  }

  private fun reactOnRefuseDialog(view: View,
                                  ep: EnrollingPermission?, newEp: EnrollingPermission,
                                  clickCount: Int)
  {
    supportFragmentManager.setFragmentResultListener(OK_KEY, viewLifecycleOwner)
    { _, bundle ->
      Log.i (TAG, "setFragmentResultListener")
      server.refuseApp(appId) { appUserResponse ->
        Log.i (TAG, "setFragmentResultListener. response.type=${appUserResponse.response.type}")
        if (appUserResponse.response.type == SERVER_OK) {
          porterIsEnrolled = false
          setVisibilityToViews(false, view)
          btEnrollRefuse.text = getString(R.string.app_enroll)
          lvdAppItem.value = appUserResponse.app

          vm.lvdOpenApps[appId] = MutableLiveData(lvdAppItem.value)
          vm.lvdOpenApps[appId]?.let {
            lvdAppItem = it
          }
          setAppObserver()

          if (clickCount > 1 && ep != null) {
            val now = Date().time
            val p = ((clickCount / 2) - 1).toDouble()
            val coefficient = 3.0.pow(p).toLong()
            Log.i(TAG, "coefficient=$coefficient")

            newEp.enableClickTime = now + coefficient * DISABLE_BTN_MILLISECONDS
            btEnrollRefuse.isEnabled = false
          }

          Log.i (TAG, "newEnrollingPermission = $newEp")
          addOrUpdateEnrollPermission(ep, newEp)
          porterIsEnrolled = !porterIsEnrolled
        }
      }
    }
  }

  private fun addOrUpdateEnrollPermission(ep: EnrollingPermission?, newEp: EnrollingPermission) {
    if (ep == null)
      App.repository.addEnrollingPermission(newEp)
    else
      App.repository.updateEnrollingPermission(newEp)
  }

  private fun setAppObserver() {
    lvdAppItem.observe(viewLifecycleOwner,
      Observer { appItem ->
        Log.i (TAG, "lvdAppItem=$lvdAppItem, observer appItem is $appItem")
        appItem?.let {
          if (appItem.porters != null)
            porter = getThisUserPorter(it)
          Log.i (TAG, "porter is $porter")
          updateUI()
        }
      }
    )
  }

  private fun updateUI() {
    setViewsTexts()
    updatePorters()
    updateBtnEnabling()
  }

  private fun updatePorters() {
    lvdAppItem.value?.let {
      it.porters?.let { porters ->
        rvPorters.adapter = PortersAdapter(porters)
      }
    }
  }

  private fun updateBtnEnabling() {
    GlobalScope.launch {
      porter?.let { porter ->
        val userId = porter.user.id
        val ep = App.repository.getEnrollingPermission(userId)
        val now = Date().time

        if (ep == null)
          btEnrollRefuse.isEnabled = true
        else
          btEnrollRefuse.isEnabled = (now >= ep.enableClickTime)
      }
    }
  }

  private fun setVisibilityToViews(porterIsEnrolled: Boolean, view: View) {
    if (porterIsEnrolled) {
      tvEnrolled.visibility = View.VISIBLE
      rvPorters.visibility = View.VISIBLE
      btCallClient.visibility = View.VISIBLE

      val ap = tvAddress.layoutParams as ConstraintLayout.LayoutParams
      ap.topToTop = ConstraintLayout.LayoutParams.UNSET
      ap.topToBottom = tvEnrolled.id
      tvAddress.layoutParams = ap

      val dp = tvDescription.layoutParams as ConstraintLayout.LayoutParams
      dp.topToBottom = rvPorters.id
      tvDescription.layoutParams = dp

      val np = nsvApp.layoutParams as ConstraintLayout.LayoutParams
      np.bottomToTop = btCallClient.id
      nsvApp.layoutParams = np

    }
    else {
      tvEnrolled.visibility = View.INVISIBLE
      rvPorters.visibility = View.INVISIBLE
      btCallClient.visibility = View.INVISIBLE

      val ap = tvAddress.layoutParams as ConstraintLayout.LayoutParams
      ap.topToTop = view.id
      ap.topToBottom = ConstraintLayout.LayoutParams.UNSET
      tvAddress.layoutParams = ap

      val dp = tvDescription.layoutParams as ConstraintLayout.LayoutParams
      dp.topToBottom = tvPortersCount.id
      tvDescription.layoutParams = dp

      val np = nsvApp.layoutParams as ConstraintLayout.LayoutParams
      np.bottomToTop = btBack.id
      nsvApp.layoutParams = np
    }
    //dp.endToEnd = ConstraintLayout.LayoutParams.UNSET
    //dp.marginStart = HEADER_MARGIN_START
  }



  private fun getThisUserPorter(app: ApplicationItem): PorterItem? {
    var porter: PorterItem? = null
    App.dbUser?.let { user ->
      Log.i (TAG, "getThisUserPorter: app = $app, porters = ${app.porters}")
      app.porters?.let {
        for (i in app.porters.indices) {
          if (user.id == app.porters[i].user.id) {
            Log.i (TAG, "user.id=${user.id}")
            porter = app.porters[i]
            break
          }
        }
      }
    }
    return porter
  }


  private fun setViews(view: View) {
    tvEnrolled = view.findViewById(R.id.tv_app_enrolled)
    tvAddress = view.findViewById(R.id.tv_app_address)
    tvTime = view.findViewById(R.id.tv_app_time)
    tvIncome = view.findViewById(R.id.tv_app_worker_income)
    tvDescription = view.findViewById(R.id.tv_app_description)
    tvPayMethod = view.findViewById(R.id.tv_app_pay_method)
    tvPortersCount = view.findViewById(R.id.tv_app_porters)
    rvPorters = view.findViewById(R.id.rv_app_porters)
    btCallClient = view.findViewById(R.id.bt_app_call_client)
    btEnrollRefuse = view.findViewById(R.id.bt_app_enroll_refuse)
    btBack = view.findViewById(R.id.bt_app_back)
    btChangeDebitCard = view.findViewById(R.id.bt_app_change_debit_card)
    nsvApp = view.findViewById(R.id.nsv_app)
  }

  private fun setViewsTexts() {
    lvdAppItem.value?.let { appItem ->
      tvAddress.text = Html.fromHtml(getString(R.string.app_address, appItem.address))

      val date = strToDate(appItem.date, DATE_FORMAT)
      date?.let {
        val time = if (isItToday(it))
          "${appItem.time}"
        else
          "${getDateStr(it)} ${appItem.time}"
        tvTime.text = Html.fromHtml(getString(R.string.app_time, time))
      }

      val suffix = if (appItem.hourlyJob)
        " " + getString(R.string.hourly_suffix)
      else
        getString(R.string.daily_suffix)
      val income = "${appItem.priceForWorker}$suffix"
      tvIncome.text = Html.fromHtml(getString(R.string.app_worker_income, income))

      tvDescription.text = Html.fromHtml(getString(R.string.app_description, appItem.whatToDo))

      val payMethod = when (appItem.payMethod) {
        PM_CARD -> getString(R.string.app_on_card)
        PM_CASH -> getString(R.string.app_cash)
        else -> ""
      }

      if (porterIsEnrolled) {
        val debitCardNumber = getDebitCardNumber()
        btChangeDebitCard.text = debitCardNumber
        btChangeDebitCard.visibility = View.VISIBLE
      }
      else {
        btChangeDebitCard.visibility = View.INVISIBLE
      }

      tvPayMethod.text = Html.fromHtml(getString(R.string.app_pay_method, payMethod))

      val workers = "${appItem.workerCount} / ${appItem.workerTotal}"
      tvPortersCount.text = Html.fromHtml(getString(R.string.app_worker_count, workers))

      btEnrollRefuse.text = if (porterIsEnrolled)
        getString(R.string.app_refuse)
      else
        getString(R.string.app_enroll)
      //btCallClient.visibility = View.INVISIBLE
    }
  }

  private fun getDebitCardNumber(): String {
    porter?.let { porter ->
      val dcId = porter.pivot.appDebitCardId
      Log.i (TAG, "debitCardId = $dcId")
      var dcStr = getString(R.string.app_no_card)

      if (dcId != 0) {
        val debitCards = porter.user.debitCards
        for (i in debitCards.indices)
          if (debitCards[i].id == dcId) {
            dcStr = debitCards[i].number
            break
          }
      }

      return dcStr
    }

    return ""
  }

  private fun getMainDebitCard(): DebitCardItem? {
    var dc: DebitCardItem? = null
    porter?.let {
      val cards = it.user.debitCards
      for (i in cards.indices) {
        if (cards[i].main) {
          dc = cards[i]
          break
        }
      }
    }
    return dc
  }

  private inner class PorterHolder (view: View) : RecyclerView.ViewHolder(view) {
    private val tvName: TextView = view.findViewById(R.id.tv_porter_name)
    private val ivCall: ImageView = view.findViewById(R.id.iv_porter_call)
    private val ivWhatsapp: ImageView = view.findViewById(R.id.iv_porter_whatsapp)

    fun bind(porterName: String, phones: List<PhoneItem>) {
      tvName.text = porterName

      var phoneWhatsapp = ""
      var phoneCall = ""
      for (i in phones.indices) {
        if (phones[i].type == PHONE_CALL || phones[i].type == PHONE_CALL_AND_WHATSAPP)
          phoneCall = phones[i].number
        if (phones[i].type == PHONE_WHATSAPP || phones[i].type == PHONE_CALL_AND_WHATSAPP)
          phoneWhatsapp = phones[i].number
      }

      ivCall.setOnClickListener {
        val intent = Intent(Intent.ACTION_DIAL)
        val uriStr = "tel:$phoneCall"
        intent.data = Uri.parse(uriStr)
        context?.startActivity(intent)
      }

      ivWhatsapp.setOnClickListener {
        openWhatsappContact(phoneWhatsapp)
      }
    }

    private fun openWhatsappContact(number: String) {
      Log.i ("openWhatsappContact", "https://wa.me/$number")
      val uri = Uri.parse("https://wa.me/$number")
      val intent = Intent(Intent.ACTION_VIEW, uri)
      startActivity(intent)
    }
  }

  private inner class PortersAdapter (porters: List<PorterItem>):
    RecyclerView.Adapter<PorterHolder>()
  {
    private val porters = mutableListOf<PorterItem>()

    init {
      App.userItem?.let { user ->
        for (i in porters.indices) {
          if (porters[i].user.id != user.id)
            this.porters.add(porters[i])
        }
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PorterHolder {
      val view = LayoutInflater.from(parent.context).inflate(
        R.layout.list_item_porter, parent, false
      )
      return PorterHolder(view)
    }

    override fun getItemCount() = porters.size

    override fun onBindViewHolder(holder: PorterHolder, position: Int) {
      holder.bind(porters[position].user.name, porters[position].user.phones)
    }
  }
}
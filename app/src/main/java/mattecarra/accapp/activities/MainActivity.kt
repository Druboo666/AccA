package mattecarra.accapp.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import com.github.javiersantos.appupdater.AppUpdater
import com.github.javiersantos.appupdater.enums.Display
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.google.gson.Gson
import com.topjohnwu.superuser.Shell
import kotlinx.android.synthetic.main.content_main.*
import mattecarra.accapp.utils.AccUtils
import mattecarra.accapp.R
import mattecarra.accapp.adapters.Profile
import mattecarra.accapp.adapters.ProfilesViewAdapter
import mattecarra.accapp.data.AccConfig
import mattecarra.accapp.utils.ProfileUtils
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File

class MainActivity : AppCompatActivity() {
    private val LOG_TAG = "MainActivity"
    private val PERMISSION_REQUEST: Int = 0
    private val ACC_CONFIG_EDITOR_REQUEST: Int = 1
    private val ACC_PROFILE_CREATOR_REQUEST: Int = 2
    private val ACC_PROFILE_EDITOR_REQUEST: Int = 3

    private lateinit var config: AccConfig
    private lateinit var sharedPrefs: SharedPreferences
    private val gson: Gson = Gson()

    private var profilesAdapter: ProfilesViewAdapter? = null

    //Used to update battery info every second
    private val handler = Handler()
    private val updateUIRunnable = object : Runnable {
        override fun run() {
            val r = this //need this to make it recursive
            doAsync {
                val batteryInfo = AccUtils.getBatteryInfo()
                val isDeamonRunning = AccUtils.isAccdRunning()
                uiThread {
                    deamon_start_stop_label.text = getString(if(isDeamonRunning) R.string.acc_deamon_status_running else R.string.acc_deamon_status_not_running)
                    deamon_start_stop.text = getString(if(isDeamonRunning) R.string.stop else R.string.start)

                    status.text = batteryInfo.status
                    battery_info.text = getString(R.string.battery_info, batteryInfo.health, batteryInfo.temp, batteryInfo.current / 1000, batteryInfo.voltage / 1000000f)

                    handler.postDelayed(r, 1000)// Repeat the same runnable code block again after 1 seconds
                }
            }
        }
    }

    private fun showConfigReadError() {
        MaterialDialog(this).show {
            title(R.string.config_error_title)
            message(R.string.config_error_dialog)
            positiveButton(android.R.string.ok)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    initUi()
                } else {
                    finish()
                }
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun initProfiles(savedInstanceState: Bundle? = null) {
        val profiles = File(filesDir, "profiles")

        val profileList =
            if(!profiles.exists())
                emptyList()
            else
                gson.fromJson(profiles.readText(), Array<String>::class.java).toList()

        if(profileList.isNotEmpty()) {
            val currentProfile = sharedPrefs.getString("PROFILE", null)

            val layoutManager = GridLayoutManager(this, 3)
            profilesAdapter = ProfilesViewAdapter(ArrayList(profileList.map { Profile(it) }), currentProfile) { profile, longPress ->
                //TODO handle profile select
                if(longPress) {
                    MaterialDialog(this@MainActivity).show {
                        listItems(R.array.profile_long_press_options) { _, index, _ ->
                            when(index) {
                                0 -> {
                                    Intent(this@MainActivity, AccConfigEditorActivity::class.java).also { intent ->
                                        intent.putExtra("profileName", profile.profileName)
                                        intent.putExtra("config", ProfileUtils.readProfile(profile.profileName, this@MainActivity, gson))
                                        startActivityForResult(intent, ACC_PROFILE_EDITOR_REQUEST)
                                    }
                                }
                                1 -> {
                                    val f = File(context.filesDir, "$profile.profile")
                                    f.delete()

                                    ProfileUtils.writeProfiles(
                                        this@MainActivity,
                                        ProfileUtils
                                            .listProfiles(this@MainActivity, gson).filter { it != profile.profileName },
                                        gson
                                    ) //update profile list without this element

                                    profilesAdapter?.remove(profile)
                                }
                            }
                        }
                    }
                } else {
                    //apply profile
                    val profileConfig = ProfileUtils.readProfile(profile.profileName, this@MainActivity, gson)

                    doAsync {
                        profileConfig.updateAcc()

                        ProfileUtils.saveCurrentProfile(profile.profileName, sharedPrefs)
                    }

                    profilesAdapter?.selectedProfile = profile.profileName
                    profilesAdapter?.notifyDataSetChanged()
                }
            }
            profiles_recyclerview.layoutManager = layoutManager
            profiles_recyclerview.adapter = profilesAdapter

            if(savedInstanceState != null) profilesAdapter!!.restoreState(savedInstanceState)

            profiles_cardview.visibility = android.view.View.VISIBLE
        } else {
            profiles_cardview.visibility = android.view.View.GONE
        }
    }

    private fun initUi(savedInstanceState: Bundle? = null) {
        try {
            this.config = AccUtils.readConfig()
        } catch (ex: Exception) {
            ex.printStackTrace()
            showConfigReadError()
            this.config = AccUtils.defaultConfig //if config is null I use default config values.
        }

        //profiles
        initProfiles(savedInstanceState)

        //Rest of the UI
        edit_config.setOnClickListener {
            Intent(this@MainActivity, AccConfigEditorActivity::class.java).also { intent ->
                startActivityForResult(intent, ACC_CONFIG_EDITOR_REQUEST)
            }
        }

        create_acc_profile.setOnClickListener {
            Intent(this@MainActivity, AccConfigEditorActivity::class.java).also { intent ->
                startActivityForResult(intent, ACC_PROFILE_CREATOR_REQUEST)
            }
        }

        deamon_start_stop.setOnClickListener {
            Toast.makeText(this, R.string.wait, Toast.LENGTH_LONG).show()

            if(AccUtils.isAccdRunning())
                AccUtils.accStopDeamon()
            else
                AccUtils.accStartDeamon()
        }

        reset_stats_on_unplugged_switch.setOnCheckedChangeListener { _, isChecked ->
            config.resetUnplugged = isChecked
            AccUtils.updateResetUnplugged(isChecked)

            //If I manually modify the config I have to set current profile to null (custom profile)
            ProfileUtils.saveCurrentProfile(null, sharedPrefs)
        }

        reset_stats_on_unplugged_switch.isChecked = config.resetUnplugged
        reset_battery_stats.setOnClickListener {
            AccUtils.resetBatteryStats()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val appUpdater = AppUpdater(this)
            .setDisplay(Display.NOTIFICATION)
            .setUpdateFrom(UpdateFrom.GITHUB)
            .setGitHubUserAndRepo("MatteCarra", "AccA")
            //.setIcon(R.drawable.app_icon)
        appUpdater.start()

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST)
            return
        }

        if(!Shell.rootAccess()) {
            val dialog = MaterialDialog(this).show {
                title(R.string.tile_acc_no_root)
                message(R.string.no_root_message)
                positiveButton(android.R.string.ok) {
                    finish()
                }
                cancelOnTouchOutside(false)
            }

            dialog.setOnKeyListener { _, keyCode, _ ->
                if(keyCode == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss()
                    finish()
                    false
                } else true
            }
            return
        }

        initUi(savedInstanceState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACC_CONFIG_EDITOR_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                if(data?.getBooleanExtra("hasChanges", false) == true) {
                    config = data.getParcelableExtra("config")
                    doAsync {
                        config.updateAcc()

                        //If I manually modify the config I have to set current profile to null (custom profile)
                        ProfileUtils.saveCurrentProfile(null, sharedPrefs)
                    }
                }
            }
        } else if(requestCode == ACC_PROFILE_CREATOR_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                if(data?.getBooleanExtra("hasChanges", false) == true) {
                    MaterialDialog(this)
                        .show {
                            title(R.string.profile_name)
                            message(R.string.dialog_profile_name_message)
                            input { _, charSequence ->
                                val config: AccConfig = data.getParcelableExtra("config")

                                //profiles index
                                val profileList = ProfileUtils.listProfiles(this@MainActivity, gson).toMutableList()

                                if(!profileList.contains(charSequence.toString())) {
                                    profileList.add(charSequence.toString())
                                    ProfileUtils.writeProfiles(this@MainActivity, profileList, gson) //Update profiles file with new profile
                                }

                                //Saving profile
                                val f = File(context.filesDir, "$charSequence.profile")
                                val json = gson.toJson(config)
                                f.writeText(json)

                                if(profileList.size == 1) {
                                    initProfiles()
                                } else {
                                    profilesAdapter?.add(Profile(charSequence.toString()))
                                }
                            }
                            positiveButton(R.string.save)
                            negativeButton(android.R.string.cancel)
                        }
                }
            }
        } else if(requestCode == ACC_PROFILE_EDITOR_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                if(data?.getBooleanExtra("hasChanges", false) == true) {
                    val config: AccConfig = data.getParcelableExtra("config")
                    val profileName = data.getStringExtra("profileName")

                    //Saving profile
                    val f = File(this@MainActivity.filesDir, "$profileName.profile")
                    val json = gson.toJson(config)
                    f.writeText(json)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the main_activity_menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when(item.itemId) {
            R.id.actions_logs -> {
                startActivity(Intent(this, LogViewerActivity::class.java))
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        handler.post(updateUIRunnable) // Start the initial runnable task by posting through the handler

        super.onResume()
    }

    override fun onPause() {
        handler.removeCallbacks(updateUIRunnable)

        super.onPause()
    }
}
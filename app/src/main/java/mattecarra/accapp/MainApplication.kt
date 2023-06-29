package mattecarra.accapp

import android.annotation.SuppressLint
import android.app.Application
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.topjohnwu.superuser.Shell
import mattecarra.accapp.utils.LogExt

class MainApplication: MultiDexApplication()
{
    companion object
    {
        var mDEBUG: Int = 0

        init
        {
         val customBuilder = Shell.Builder.create()
            .setFlags(Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(10)

         if (BuildConfig.DEBUG) {
            Shell.enableVerboseLogging
         }

         Shell.setDefaultBuilder(customBuilder)
        }
    }

    @SuppressLint("LogNotTimber")
    override fun onCreate()
    {
        super.onCreate()
        mDEBUG = (getDefaultSharedPreferences(applicationContext).getString("appdebug", "0") ?: "0").toInt()
        LogExt().s(javaClass.simpleName, "DEBUG=$mDEBUG " +when(mDEBUG) {0->"[NONE]" 1->"[CONSOLE]" 2->"[FILE]" else->"[UNKNOWN]"})
    }
}

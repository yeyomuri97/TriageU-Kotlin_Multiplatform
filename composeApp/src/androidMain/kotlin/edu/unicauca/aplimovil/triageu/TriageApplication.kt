package edu.unicauca.aplimovil.triageu

import android.app.Application
import edu.unicauca.aplimovil.triageu.platform.AndroidContext
import edu.unicauca.aplimovil.triageu.platform.ReminderNotifications
import edu.unicauca.aplimovil.triageu.platform.SyncScheduler
import edu.unicauca.aplimovil.triageu.platform.createPlatformServices

class TriageApplication : Application() {

    /** Un único store por proceso (equivale a los singletons `.shared` de Swift). */
    val store: TriageStore by lazy { TriageStore(createPlatformServices()) }

    override fun onCreate() {
        super.onCreate()
        AndroidContext.init(this)
        ReminderNotifications.ensureChannel(this)
        SyncScheduler.schedulePeriodic(this)
    }
}

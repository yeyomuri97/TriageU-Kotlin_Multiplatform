package edu.unicauca.aplimovil.triageu

import androidx.compose.ui.window.ComposeUIViewController
import edu.unicauca.aplimovil.triageu.platform.createPlatformServices

// Un único store por proceso.
private val store: TriageStore by lazy { TriageStore(createPlatformServices()) }

@Suppress("FunctionName")
fun MainViewController() = ComposeUIViewController(
    configure = { enforceStrictPlistSanityCheck = false }
) {
    App(store)
}

/** Lo llama el host Swift cuando la escena pasa a `.active`. */
fun onAppForeground() {
    store.onForeground()
}

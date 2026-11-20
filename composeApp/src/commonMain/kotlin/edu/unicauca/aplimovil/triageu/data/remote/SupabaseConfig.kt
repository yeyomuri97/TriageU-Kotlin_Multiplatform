package edu.unicauca.aplimovil.triageu.data.remote

object SupabaseConfig {
    // Mismos valores del proyecto iOS original (Project Settings > API).
    val url: String = "https://ynetbxihgoziqgspxazx.supabase.co"
    val anonKey: String = "sb_publishable_PEmuiomgdIzxW00QgU87eA_Nky1b4ZG"

    /** Sin credenciales de Supabase la app funciona solo en local, sin login. */
    val isConfigured: Boolean
        get() = !url.contains("TU-PROYECTO") && anonKey != "TU_ANON_KEY"
}

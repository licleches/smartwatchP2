package com.example.medtime.presentation.data.remote

import com.example.medtime.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.SupabaseClient

object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "Supabase URL no configurada en local.properties"
        }
        require(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Supabase publishable key no configurada en local.properties"
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
        }
    }
}

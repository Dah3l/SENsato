package com.apagones.habana.parser

/**
 * Configuración del proyecto Supabase (URL base y clave anónima del backend).
 */
object SupabaseConfig {
    /** URL base del proyecto Supabase. */
    const val BASE_URL = "https://vhxosjmdafgxcyslrpqz.supabase.co"

    /** Clave anónima (anon key) con permiso de lectura pública en la tabla 'posts'. */
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZoeG9zam1kYWZneGN5c2xycHF6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE1MzU1NDMsImV4cCI6MjEwNzExMTU0M30.UkaJaqWDngbWEczwxpAHQgE52yoputYZxpSdE4TWCyY"
}

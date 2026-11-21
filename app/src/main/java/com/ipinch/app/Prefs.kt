package com.ipinch.app

import android.content.Context

/** User settings. The token lives only on the phone (private app storage, backups disabled). */
// Nothing is baked in: URL and token are entered on first launch.
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("ipinch", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = p.getString("url", DEFAULT_URL)!!
        set(v) = p.edit().putString("url", normaliseUrl(v)).apply()

    var token: String
        get() = p.getString("token", "")!!
        set(v) = p.edit().putString("token", v.trim()).apply()

    var device: String
        get() = p.getString("device", DEFAULT_DEVICE)!!
        set(v) = p.edit().putString("device", v.trim().ifEmpty { DEFAULT_DEVICE }).apply()

    var speak: Boolean
        get() = p.getBoolean("speak", true)
        set(v) = p.edit().putBoolean("speak", v).apply()

    var autoListen: Boolean
        get() = p.getBoolean("autoListen", true)
        set(v) = p.edit().putBoolean("autoListen", v).apply()

    val isConfigured get() = baseUrl.isNotEmpty() && token.isNotEmpty()

    companion object {
        const val DEFAULT_URL = ""
        const val DEFAULT_DEVICE = "phone"

        /** Accept "host:port", a trailing slash, or the full /v1/turn path. */
        fun normaliseUrl(raw: String): String {
            var u = raw.trim().trimEnd('/')
            if (u.endsWith("/v1/turn")) u = u.removeSuffix("/v1/turn")
            if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            return u.ifEmpty { DEFAULT_URL }
        }
    }
}

/*
 * SPDX-FileCopyrightText: 2026 Aurora Wear contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.providers

import android.content.Context
import android.util.Log
import com.aurora.extensions.TAG
import com.aurora.gplayapi.Constants.Restriction
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.network.IHttpClient
import com.aurora.store.AuroraApp
import com.aurora.store.util.WearUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps a second Play session that presents itself as a Wear OS watch.
 *
 * On a watch the main session uses a phone profile so that every app shows up. Play only offers
 * an app to a watch when the app ships a Wear OS build, so asking this watch session about a
 * package tells whether the app was made for Wear OS. The same session is used to download and
 * update those apps, so the watch gets the Wear OS build instead of the phone one.
 */
@Singleton
class WearSessionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authProvider: AuthProvider,
    private val spoofProvider: SpoofProvider,
    private val httpClient: IHttpClient
) {
    private companion object {
        const val BATCH_WINDOW_MS = 400L
        const val BATCH_SIZE = 50
        const val FEATURE_WATCH = "android.hardware.type.watch"
    }

    private val sessionLock = Mutex()

    @Volatile
    private var wearAuthData: AuthData? = null

    @Volatile
    private var sourceToken: String? = null

    private val _wearCompatibility = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Package name to whether Play offers the app to a watch. Missing means not checked yet. */
    val wearCompatibility: StateFlow<Map<String, Boolean>> = _wearCompatibility.asStateFlow()

    private val pending = Channel<String>(Channel.UNLIMITED)
    private val requested: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        AuroraApp.scope.launch(Dispatchers.IO) {
            while (true) {
                val batch = mutableSetOf(pending.receive())
                delay(BATCH_WINDOW_MS)
                while (true) {
                    batch += pending.tryReceive().getOrNull() ?: break
                }

                batch.chunked(BATCH_SIZE).forEach { packages ->
                    try {
                        fetchWearApps(packages)
                    } catch (exception: Exception) {
                        Log.w(TAG, "Wear OS check failed for ${packages.size} apps", exception)
                        requested.removeAll(packages.toSet())
                    }
                }
            }
        }
    }

    /**
     * Device properties of a watch: the real ones on a watch, otherwise this device's own
     * properties marked as a watch so the check still works when testing on a phone.
     */
    val watchProperties: Properties
        get() {
            val properties = NativeDeviceInfoProvider.getNativeDeviceProperties(context)
            if (WearUtil.isWatch(context)) return properties

            val features = properties.getProperty("Features").orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .toMutableSet()
                .apply { add(FEATURE_WATCH) }
            return properties.apply {
                setProperty("Features", features.joinToString(separator = ","))
                setProperty("ScreenLayout", "1")
                setProperty("Screen.Width", "450")
                setProperty("Screen.Height", "450")
                setProperty("Screen.Density", "320")
            }
        }

    /**
     * Queues [packageName] for a Wear OS check. Requests are grouped into bulk lookups.
     */
    fun request(packageName: String) {
        if (packageName.isBlank() || _wearCompatibility.value.containsKey(packageName)) return
        if (requested.add(packageName)) pending.trySend(packageName)
    }

    /**
     * Whether [packageName] has a Wear OS build, checking with Play if it isn't known yet.
     * Returns null when the check fails.
     */
    suspend fun isWearApp(packageName: String): Boolean? {
        _wearCompatibility.value[packageName]?.let { return it }
        return try {
            fetchWearApps(listOf(packageName))
            _wearCompatibility.value[packageName]
        } catch (exception: Exception) {
            Log.w(TAG, "Wear OS check failed for $packageName", exception)
            null
        }
    }

    /**
     * Looks [packageNames] up as a watch, records which ones are offered to watches and returns
     * their watch details (version code, files, offer type).
     */
    suspend fun fetchWearApps(packageNames: List<String>): List<App> = withContext(Dispatchers.IO) {
        if (packageNames.isEmpty()) return@withContext emptyList()

        val apps = try {
            lookup(getAuthData(), packageNames)
        } catch (exception: Exception) {
            // The watch session may have expired; rebuild it once before giving up.
            Log.i(TAG, "Rebuilding Wear OS session", exception)
            invalidate()
            lookup(getAuthData(), packageNames)
        }

        val wearApps = apps.filter { it.isOfferedToWatch() }
        val wearPackages = wearApps.map { it.packageName }.toSet()
        _wearCompatibility.update { known ->
            known + packageNames.associateWith { it in wearPackages }
        }
        wearApps
    }

    /**
     * Session that Play sees as a watch, built from the default account's token.
     */
    suspend fun getAuthData(): AuthData = sessionLock.withLock {
        val source = authProvider.authData
        if (source == null || source.authToken.isBlank()) {
            throw IllegalStateException("No signed-in session to build the Wear OS session from")
        }

        wearAuthData?.takeIf { sourceToken == source.authToken }?.let { return@withLock it }

        withContext(Dispatchers.IO) {
            AuthHelper.build(
                email = source.email,
                token = source.authToken,
                tokenType = AuthHelper.Token.AUTH,
                isAnonymous = source.isAnonymous,
                properties = watchProperties,
                locale = spoofProvider.locale
            )
        }.also {
            wearAuthData = it
            sourceToken = source.authToken
        }
    }

    private fun invalidate() {
        wearAuthData = null
        sourceToken = null
    }

    private fun lookup(authData: AuthData, packageNames: List<String>): List<App> =
        AppDetailsHelper(authData).using(httpClient).getAppByPackageName(packageNames)

    private fun App.isOfferedToWatch(): Boolean = displayName.isNotBlank() &&
        versionCode > 0 &&
        restriction != Restriction.DEVICE_RESTRICTED
}

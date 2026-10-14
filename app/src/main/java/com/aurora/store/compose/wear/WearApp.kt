/*
 * SPDX-FileCopyrightText: 2026 Aurora Wear contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.store.viewmodel.downloads.DownloadsViewModel
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.aurora.gplayapi.helpers.contracts.TopChartsContract

object WearRoute {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val HOME = "home"
    const val UPDATES = "updates"
    const val DOWNLOADS = "downloads"

    const val ARG_QUERY = "query"
    const val ARG_TYPE = "type"
    const val ARG_PACKAGE = "package"

    const val SEARCH = "search/{$ARG_QUERY}"
    const val TOP_CHART = "top/{$ARG_TYPE}"
    const val DETAILS = "details/{$ARG_PACKAGE}"

    fun search(query: String) = "search/${Uri.encode(query)}"
    fun topChart(type: TopChartsContract.Type) = "top/${type.name}"
    fun details(packageName: String) = "details/${Uri.encode(packageName)}"
}

/**
 * The whole Aurora Wear interface on a watch: Wear OS Material 3, swipe right to go back.
 * @param isIntroDone Whether first-run setup has finished
 * @param startPackage App to open once signed in, e.g. from a market:// link
 */
@Composable
fun WearApp(isIntroDone: Boolean, startPackage: String?) {
    MaterialTheme {
        AppScaffold {
            NotificationPermissionRequest()
            PendingInstallPrompter()

            val navController = rememberSwipeDismissableNavController()
            val openApp: (String) -> Unit = { navController.navigate(WearRoute.details(it)) }

            SwipeDismissableNavHost(
                navController = navController,
                startDestination = if (isIntroDone) WearRoute.LOGIN else WearRoute.WELCOME
            ) {
                composable(WearRoute.WELCOME) { WelcomeScreen() }

                composable(WearRoute.LOGIN) {
                    LoginScreen(
                        onSignedIn = {
                            navController.navigate(WearRoute.HOME) {
                                popUpTo(WearRoute.LOGIN) { inclusive = true }
                            }
                            startPackage?.let(openApp)
                        }
                    )
                }

                composable(WearRoute.HOME) {
                    HomeScreen(onNavigate = { navController.navigate(it) })
                }

                composable(
                    route = WearRoute.SEARCH,
                    arguments = listOf(navArgument(WearRoute.ARG_QUERY) { type = NavType.StringType })
                ) { entry ->
                    SearchScreen(
                        query = entry.arguments?.getString(WearRoute.ARG_QUERY).orEmpty(),
                        onOpenApp = openApp,
                        onSearch = { query ->
                            navController.navigate(WearRoute.search(query)) {
                                popUpTo(WearRoute.HOME)
                            }
                        }
                    )
                }

                composable(
                    route = WearRoute.TOP_CHART,
                    arguments = listOf(navArgument(WearRoute.ARG_TYPE) { type = NavType.StringType })
                ) { entry ->
                    val type = entry.arguments?.getString(WearRoute.ARG_TYPE)
                        ?.let { name -> TopChartsContract.Type.entries.find { it.name == name } }
                        ?: TopChartsContract.Type.APPLICATION
                    TopChartScreen(type = type, onOpenApp = openApp)
                }

                composable(
                    route = WearRoute.DETAILS,
                    arguments = listOf(navArgument(WearRoute.ARG_PACKAGE) { type = NavType.StringType })
                ) { entry ->
                    DetailsScreen(packageName = entry.arguments?.getString(WearRoute.ARG_PACKAGE).orEmpty())
                }

                composable(WearRoute.UPDATES) { UpdatesScreen(onOpenApp = openApp) }

                composable(WearRoute.DOWNLOADS) { DownloadsScreen(onOpenApp = openApp) }
            }
        }
    }
}

/**
 * Asks for the notification permission. Aurora falls back to a notification for the install
 * confirmation when it is in the background, which is often the case on a watch whose screen
 * has turned off; without the permission that notification never shows.
 */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Starts the install confirmation for downloads that are waiting for it whenever Aurora comes
 * to the foreground, e.g. after the screen turned off while the download was running.
 * Each download version is prompted once per app start, so dismissing the prompt sticks.
 */
@Composable
private fun PendingInstallPrompter(viewModel: DownloadsViewModel = hiltViewModel()) {
    val downloads by viewModel.downloadsList.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    val prompted = remember { mutableSetOf<String>() }

    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val pending = downloads.filter { it.isAwaitingInstall }
        .map { "${it.packageName}:${it.versionCode}" to it }

    LaunchedEffect(isResumed, pending.map { it.first }) {
        if (!isResumed) return@LaunchedEffect
        pending.firstOrNull { it.first !in prompted }?.let { (key, download) ->
            prompted += key
            viewModel.install(download)
        }
    }
}

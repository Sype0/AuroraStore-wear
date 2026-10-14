/*
 * SPDX-FileCopyrightText: 2026 Aurora Wear contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.contracts.TopChartsContract
import com.aurora.store.R
import com.aurora.store.data.installer.AppInstaller
import com.aurora.store.data.model.AppState
import com.aurora.store.data.model.AuthState
import com.aurora.store.data.model.ViewState
import com.aurora.store.util.CommonUtil
import com.aurora.store.util.PackageUtil
import com.aurora.store.viewmodel.all.UpdatesViewModel
import com.aurora.store.viewmodel.auth.AuthViewModel
import com.aurora.store.viewmodel.details.AppDetailsViewModel
import com.aurora.store.viewmodel.onboarding.OnboardingViewModel
import com.aurora.store.viewmodel.search.SearchViewModel
import com.aurora.store.viewmodel.topchart.TopChartViewModel

@Composable
fun WelcomeScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    WearList {
        item { WearHeader(stringResource(R.string.app_name)) }
        item { WearMessage(stringResource(R.string.wear_welcome_text)) }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.finishOnboarding() },
                label = { Text(stringResource(R.string.wear_start)) }
            )
        }
    }
}

@Composable
fun LoginScreen(onSignedIn: () -> Unit, viewModel: AuthViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        if (authState == AuthState.Valid || authState == AuthState.SignedIn) onSignedIn()
    }

    val canSignIn = authState is AuthState.Unavailable ||
        authState is AuthState.Failed ||
        authState is AuthState.SignedOut

    WearList {
        item { WearHeader(stringResource(R.string.app_name)) }
        if (canSignIn) {
            (authState as? AuthState.Failed)?.let { failed ->
                item { WearMessage(failed.status, isError = true) }
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.buildAnonymousAuthData() },
                    label = { Text(stringResource(R.string.wear_login_anonymous)) }
                )
            }
        } else {
            item { WearLoading() }
            item { WearMessage(stringResource(R.string.wear_signing_in)) }
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, viewModel: UpdatesViewModel = hiltViewModel()) {
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val searchLabel = stringResource(R.string.wear_search)
    val openSearch = rememberWearTextInput(searchLabel) { onNavigate(WearRoute.search(it)) }

    WearList {
        item { WearHeader(stringResource(R.string.app_name)) }
        item { WearMenuButton(R.drawable.ic_round_search, searchLabel, onClick = openSearch) }
        item {
            WearMenuButton(R.drawable.ic_apps, stringResource(R.string.wear_top_apps)) {
                onNavigate(WearRoute.topChart(TopChartsContract.Type.APPLICATION))
            }
        }
        item {
            WearMenuButton(R.drawable.ic_games, stringResource(R.string.wear_top_games)) {
                onNavigate(WearRoute.topChart(TopChartsContract.Type.GAME))
            }
        }
        item {
            WearMenuButton(
                icon = R.drawable.ic_updates,
                label = stringResource(R.string.wear_updates),
                secondary = updates?.size?.takeIf { it > 0 }?.toString()
            ) { onNavigate(WearRoute.UPDATES) }
        }
        item {
            WearMenuButton(R.drawable.ic_download_manager, stringResource(R.string.wear_downloads)) {
                onNavigate(WearRoute.DOWNLOADS)
            }
        }
    }
}

@Composable
fun SearchScreen(
    query: String,
    onOpenApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    LaunchedEffect(query) { viewModel.search(query) }
    val apps = viewModel.apps.collectAsLazyPagingItems()
    val searchLabel = stringResource(R.string.wear_search)
    val openSearch = rememberWearTextInput(searchLabel, onSearch)

    WearList {
        item { WearHeader(query) }
        item { WearMenuButton(R.drawable.ic_round_search, searchLabel, onClick = openSearch) }
        when (apps.loadState.refresh) {
            is LoadState.Loading -> item { WearLoading() }
            is LoadState.Error -> item {
                WearMessage(stringResource(R.string.wear_failed_to_load), isError = true)
            }
            else -> if (apps.itemCount == 0) {
                item { WearMessage(stringResource(R.string.wear_no_results)) }
            }
        }
        items(count = apps.itemCount) { index ->
            apps[index]?.let { app ->
                WearAppCard(
                    packageName = app.packageName,
                    name = app.displayName,
                    subtitle = app.developerName,
                    iconUrl = app.iconArtwork.url
                ) { onOpenApp(app.packageName) }
            }
        }
    }
}

@Composable
fun TopChartScreen(
    type: TopChartsContract.Type,
    onOpenApp: (String) -> Unit,
    viewModel: TopChartViewModel = hiltViewModel()
) {
    val chart = TopChartsContract.Chart.TOP_SELLING_FREE
    LaunchedEffect(type) { viewModel.getStreamCluster(type, chart) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    WearList {
        item {
            WearHeader(
                stringResource(
                    if (type == TopChartsContract.Type.GAME) {
                        R.string.wear_top_games
                    } else {
                        R.string.wear_top_apps
                    }
                )
            )
        }
        when (val current = state) {
            is ViewState.Success<*> -> {
                val cluster = current.data as? StreamCluster
                items(cluster?.clusterAppList.orEmpty()) { app ->
                    WearAppCard(
                        packageName = app.packageName,
                        name = app.displayName,
                        subtitle = app.developerName,
                        iconUrl = app.iconArtwork.url
                    ) { onOpenApp(app.packageName) }
                }
                if (cluster?.hasNext() == true) {
                    item {
                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.nextCluster(type, chart) },
                            label = { Text(stringResource(R.string.wear_load_more)) }
                        )
                    }
                }
            }
            is ViewState.Error -> {
                item { WearMessage(stringResource(R.string.wear_failed_to_load), isError = true) }
                item {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.getStreamCluster(type, chart) },
                        label = { Text(stringResource(R.string.wear_retry)) }
                    )
                }
            }
            else -> item { WearLoading() }
        }
    }
}

@Composable
fun DetailsScreen(packageName: String, viewModel: AppDetailsViewModel = hiltViewModel()) {
    LaunchedEffect(packageName) { viewModel.fetchAppDetails(packageName) }
    val app by viewModel.app.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    WearList {
        val current = app
        if (current == null || current.packageName != packageName) {
            item { WearLoading() }
            return@WearList
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WearAppIcon(url = current.iconArtwork.url, size = 52.dp)
            }
        }
        item {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = current.displayName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        item { WearCompatibilityBadge(packageName = packageName) }
        item {
            val extras = buildList {
                add(current.developerName)
                if (current.labeledRating.isNotBlank()) add("${current.labeledRating}★")
                if (current.size > 0) add(CommonUtil.addSiPrefix(current.size))
            }
            WearMessage(extras.filter { it.isNotBlank() }.joinToString("  •  "))
        }

        val install = { viewModel.enqueueDownload(current) }
        val cancel = { viewModel.cancelDownload(current) }
        val launchIntent = PackageUtil.getLaunchIntent(context, packageName)
        val open = { launchIntent?.let { context.startActivity(it) } ?: Unit }
        val uninstall = { AppInstaller.uninstall(context, packageName) }

        when (val appState = state) {
            is AppState.Downloading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            progress = { appState.progress / 100F }
                        )
                        Text(
                            text = "${appState.progress.toInt()}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                item { WearActionButton(R.string.wear_cancel, primary = false, onClick = cancel) }
            }

            AppState.Queued, AppState.Purchasing, AppState.Verifying, is AppState.Installing -> {
                item { WearLoading() }
                item { WearMessage(stringResource(statusText(appState))) }
                if (appState !is AppState.Installing) {
                    item {
                        WearActionButton(R.string.wear_cancel, primary = false, onClick = cancel)
                    }
                }
            }

            is AppState.Installed -> {
                if (launchIntent != null) {
                    item { WearActionButton(R.string.wear_open, onClick = open) }
                }
                item { WearActionButton(R.string.wear_uninstall, primary = false, onClick = uninstall) }
            }

            AppState.Updatable -> {
                item { WearActionButton(R.string.wear_update, onClick = install) }
                if (launchIntent != null) {
                    item { WearActionButton(R.string.wear_open, primary = false, onClick = open) }
                }
            }

            AppState.Unavailable -> item {
                WearMessage(stringResource(R.string.wear_unavailable), isError = true)
            }

            is AppState.Error -> {
                appState.message?.let { item { WearMessage(it, isError = true) } }
                item { WearActionButton(R.string.wear_retry, onClick = install) }
            }

            AppState.Loading -> item { WearLoading() }

            AppState.Archived -> item { WearActionButton(R.string.wear_install, onClick = install) }
        }

        if (current.shortDescription.isNotBlank()) {
            item { WearMessage(current.shortDescription) }
        }
    }
}

@Composable
fun UpdatesScreen(onOpenApp: (String) -> Unit, viewModel: UpdatesViewModel = hiltViewModel()) {
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val isChecking by viewModel.fetchingUpdates.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.fetchUpdates() }

    WearList {
        item { WearHeader(stringResource(R.string.wear_updates)) }
        val list = updates.orEmpty()
        when {
            isChecking || updates == null -> item { WearLoading() }

            list.isEmpty() -> {
                item { WearMessage(stringResource(R.string.wear_no_updates)) }
                item {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.fetchUpdates() },
                        label = { Text(stringResource(R.string.wear_check_updates)) }
                    )
                }
            }

            else -> {
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.downloadAll(list) },
                        label = { Text(stringResource(R.string.wear_update_all)) }
                    )
                }
                items(list) { update ->
                    WearAppCard(
                        packageName = update.packageName,
                        name = update.displayName,
                        subtitle = update.versionName,
                        iconUrl = update.iconURL
                    ) { onOpenApp(update.packageName) }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(onOpenApp: (String) -> Unit, viewModel: UpdatesViewModel = hiltViewModel()) {
    val downloads by viewModel.downloadsList.collectAsStateWithLifecycle()

    WearList {
        item { WearHeader(stringResource(R.string.wear_downloads)) }
        if (downloads.isEmpty()) {
            item { WearMessage(stringResource(R.string.wear_no_downloads)) }
        }
        items(downloads.sortedByDescending { it.downloadedAt }) { download ->
            val status = stringResource(download.status.localized)
            WearAppCard(
                packageName = download.packageName,
                name = download.displayName,
                subtitle = if (download.isRunning) "$status • ${download.progress}%" else status,
                iconUrl = download.iconURL
            ) { onOpenApp(download.packageName) }
        }
    }
}

@Composable
private fun WearActionButton(label: Int, primary: Boolean = true, onClick: () -> Unit) {
    if (primary) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            label = { Text(stringResource(label)) }
        )
    } else {
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            label = { Text(stringResource(label)) }
        )
    }
}

private fun statusText(state: AppState): Int = when (state) {
    AppState.Queued -> R.string.wear_status_queued
    AppState.Purchasing -> R.string.wear_status_preparing
    AppState.Verifying -> R.string.wear_status_verifying
    else -> R.string.wear_status_installing
}

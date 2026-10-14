/*
 * SPDX-FileCopyrightText: 2026 Aurora Wear contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.aurora.store.R
import com.aurora.store.compose.preview.ThemePreviewProvider
import com.aurora.store.data.providers.WearSessionProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearSessionEntryPoint {
    fun wearSessionProvider(): WearSessionProvider
}

/**
 * Whether the app is made for Wear OS: true or false once checked, null while unknown.
 * Asks [WearSessionProvider] to check the app the first time it is shown.
 */
@Composable
fun rememberIsWearApp(packageName: String): Boolean? {
    if (LocalInspectionMode.current) return null

    val context = LocalContext.current
    val provider = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WearSessionEntryPoint::class.java
        ).wearSessionProvider()
    }

    LaunchedEffect(packageName) { provider.request(packageName) }

    val flow = remember(packageName) {
        provider.wearCompatibility.map { it[packageName] }.distinctUntilChanged()
    }
    val isWearApp by flow.collectAsState(initial = provider.wearCompatibility.value[packageName])
    return isWearApp
}

/**
 * Label placed at the top of an app entry when the app has no Wear OS build.
 * Shows nothing for Wear OS apps and while the check is still running.
 */
@Composable
fun WearCompatibilityLabel(modifier: Modifier = Modifier, packageName: String) {
    if (rememberIsWearApp(packageName) != false) return
    NotMadeForWearBadge(modifier = modifier)
}

@Composable
private fun NotMadeForWearBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            text = stringResource(R.string.not_made_for_wear_os),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun NotMadeForWearBadgePreview() {
    NotMadeForWearBadge()
}

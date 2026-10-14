/*
 * SPDX-FileCopyrightText: 2026 Aurora Wear contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.wear

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.aurora.store.R
import com.aurora.store.compose.composable.app.rememberIsWearApp

private const val KEY_TEXT_INPUT = "text_input"

/**
 * A screen with a single scrolling list that curves along the round display. The time is shown
 * at the top by the app scaffold; there are no top or bottom bars.
 */
@Composable
fun WearList(content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { contentPadding ->
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = contentPadding,
            content = content
        )
    }
}

@Composable
fun WearHeader(text: String) {
    ListHeader {
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WearMessage(text: String, isError: Boolean = false) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center
    )
}

@Composable
fun WearLoading() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp))
    }
}

/**
 * Full-width button with an icon and an optional second line.
 */
@Composable
fun WearMenuButton(
    @DrawableRes icon: Int,
    label: String,
    secondary: String? = null,
    onClick: () -> Unit
) {
    FilledTonalButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        icon = {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(icon),
                contentDescription = null
            )
        },
        secondaryLabel = if (secondary != null) {
            { Text(text = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        label = { Text(text = label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    )
}

@Composable
fun WearAppIcon(url: String, size: Dp) {
    AsyncImage(
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop
    )
}

/**
 * "Not made for Wear OS" badge, shown once the check says the app has no Wear OS build.
 */
@Composable
fun WearCompatibilityBadge(packageName: String, modifier: Modifier = Modifier) {
    if (rememberIsWearApp(packageName) != false) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.not_made_for_wear_os),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * One app in a list: the badge on top, then icon, name and a second line.
 */
@Composable
fun WearAppCard(
    packageName: String,
    name: String,
    subtitle: String?,
    iconUrl: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        WearCompatibilityBadge(
            packageName = packageName,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WearAppIcon(url = iconUrl, size = 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Opens the watch's text input (keyboard, voice or handwriting) and returns what was entered.
 */
@Composable
fun rememberWearTextInput(label: String, onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)
            ?.getCharSequence(KEY_TEXT_INPUT)
            ?.toString()
            ?.trim()
        if (!text.isNullOrEmpty()) onResult(text)
    }

    return remember(launcher, label) {
        {
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putRemoteInputsExtra(
                intent,
                listOf(RemoteInput.Builder(KEY_TEXT_INPUT).setLabel(label).build())
            )
            launcher.launch(intent)
        }
    }
}

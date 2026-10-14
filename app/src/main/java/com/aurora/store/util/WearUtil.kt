/*
 * SPDX-FileCopyrightText: 2026 Aurora Wear contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.util

import android.content.Context
import android.content.pm.PackageManager

object WearUtil {

    /**
     * Whether Aurora Store is running on a Wear OS watch.
     */
    fun isWatch(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
}

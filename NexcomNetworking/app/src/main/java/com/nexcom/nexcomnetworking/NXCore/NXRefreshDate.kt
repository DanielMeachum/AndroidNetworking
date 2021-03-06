package com.nexcom.nexcomnetworking.NXCore

import com.beust.klaxon.Json
import com.nexcom.NXCore.NXDate
import java.util.*
import org.threeten.bp.ZonedDateTime

/**
 * Data class representing the return of a refresh procedure.
 * Looks for "refresh_date" for comparing on-disk caches.
 * Created by danielmeachum on 1/29/18.
 */
data class NXRefreshDate(
        @NXDate
        @Json(name = "refresh_date") val refreshDate: ZonedDateTime
)
package com.pushpushgo.sdk.push.utils

import com.pushpushgo.sdk.core.api.Config

/**
 * Whether both configurations point at the same project of the same API. Subscriber state saved
 * for one of them is valid for the other even if the API key differs (e.g. after key rotation).
 */
internal fun Config.isSameProjectAs(other: Config): Boolean = projectId == other.projectId && apiUrl == other.apiUrl

package com.pushpushgo.sdk.push

import com.pushpushgo.sdk.core.api.Config

internal fun testConfig(): Config =
  Config.create(
    projectId = "8kp60aqdi49eioqzp0ihiytn",
    apiKey = "00000000-0000-0000-0000-000000000001",
  )

internal fun otherProjectTestConfig(): Config =
  Config.create(
    projectId = "j15m43rl9l3owwuonfnvxm84",
    apiKey = "00000000-0000-0000-0000-000000000002",
  )

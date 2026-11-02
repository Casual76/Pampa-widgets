package com.pampa.widgets.core.media

import com.pampa.widgets.core.settings.MediaSourceMode

/** Session tokens, rather than app priority, identify the destination of an existing control. */
internal fun <T> resolveMediaCommandTarget(
  mode: MediaSourceMode,
  shownPackage: String,
  shownToken: T?,
  sessions: List<Pair<String, T>>,
): T? {
  if (shownToken == null || !mode.accepts(shownPackage)) return null
  return sessions.firstOrNull { (pkg, token) -> pkg == shownPackage && token == shownToken }?.second
}

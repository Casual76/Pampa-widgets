package com.pampa.widgets.widget.media

import android.content.Context
import android.os.SystemClock
import com.pampa.widgets.core.media.MediaControlAction
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.media.MediaSessionReader
import com.pampa.widgets.core.settings.AppSettingsSnapshotReader
import com.pampa.widgets.core.settings.MediaWidgetSettings
import com.pampa.widgets.core.settings.MediaWidgetSettingsRepository
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MediaSignalDebounceMs = 90L
private const val CommandTimeoutMs = 1_600L
private val CommandResyncDelaysMs = longArrayOf(220L, 600L, 1_100L)
private val ArtworkRetryDelaysMs = longArrayOf(200L, 750L, 2_000L)

internal enum class MediaWidgetUpdateReason(val immediate: Boolean) {
  MediaSignal(false),
  ArtworkRetry(true),
  WidgetLifecycle(true),
  Resize(true),
  Settings(true),
  ManualRefresh(true),
  ControlFeedback(true),
  ControlResync(true),
}

internal data class MediaWidgetInteraction(
  val action: MediaControlAction,
  val showFeedback: Boolean,
  val expectedPlaying: Boolean?,
)

private data class UpdateRequest(
  val generation: Long,
  val context: Context,
  val reason: MediaWidgetUpdateReason,
)

private data class PendingMediaCommand(
  val id: Long,
  val action: MediaControlAction,
  val baselineTrackKey: String,
  val expectedPlaying: Boolean?,
  val startedAtElapsedMs: Long,
)

internal class LatestUpdateGeneration {
  private val value = AtomicLong(0L)

  fun issue(): Long = value.incrementAndGet()

  fun isLatest(generation: Long): Boolean = generation == value.get()
}

/** Serial work queue: commands and settings writes cannot race a rendered source. */
internal object MediaWidgetUpdateCoordinator {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val requests = Channel<UpdateRequest>(Channel.CONFLATED)
  private val requestedGeneration = LatestUpdateGeneration()
  private val mutex = kotlinx.coroutines.sync.Mutex()
  private val latest = mutableMapOf<Int, Pair<MediaWidgetSettings, MediaPlaybackSnapshot>>()
  private val pending = mutableMapOf<Int, PendingMediaCommand>()
  private val commandJobs = mutableMapOf<Int, Job>()
  private val artworkJobs = mutableMapOf<Int, Pair<String, Job>>()
  private var commandId = 0L

  init {
    scope.launch {
      for (request in requests) {
        try { process(request) } catch (error: Exception) {
          if (error is kotlinx.coroutines.CancellationException) throw error
          android.util.Log.e("MediaWidget", "Widget update failed", error)
        }
      }
    }
  }

  fun requestUpdate(context: Context, reason: MediaWidgetUpdateReason = MediaWidgetUpdateReason.MediaSignal) {
    requests.trySend(UpdateRequest(requestedGeneration.issue(), context.applicationContext, reason))
  }

  private fun ids(context: Context) = android.appwidget.AppWidgetManager.getInstance(context)
    .getAppWidgetIds(android.content.ComponentName(context, MediaWidgetProvider::class.java))

  suspend fun saveSettings(context: Context, id: Int, settings: MediaWidgetSettings) {
    requestedGeneration.issue()
    mutex.lock()
    try {
      MediaWidgetSettingsRepository(context).save(id, settings)
      clearState(id)
    } finally { mutex.unlock() }
    requestUpdate(context, MediaWidgetUpdateReason.Settings)
  }

  fun dispatchControl(context: Context, id: Int, action: MediaControlAction, onComplete: () -> Unit = {}) {
    scope.launch {
      try {
        mutex.lock()
        try {
          if (id !in ids(context)) return@launch
          val settings = MediaWidgetSettingsRepository(context).read(id)
          val displayed = latest[id] ?: return@launch // No guessing after process death.
          if (displayed.first != settings) return@launch
          val baseline = displayed.second
          if (MediaSessionReader.dispatch(context, action, settings.source, baseline)) {
            val previous = pending[id]
            val expected = if (action == MediaControlAction.TogglePlayPause) {
              !(previous?.expectedPlaying ?: baseline.isPlaying)
            } else null
            pending[id] = PendingMediaCommand(++commandId, action, baseline.trackKey, expected, SystemClock.elapsedRealtime())
            commandJobs.remove(id)?.cancel()
            commandJobs[id] = scope.launch {
              var last = 0L
              (CommandResyncDelaysMs + CommandTimeoutMs).forEach { next ->
                delay(next - last); last = next
                requestUpdate(context, MediaWidgetUpdateReason.ControlResync)
              }
            }
          }
        } finally { mutex.unlock() }
      } finally {
        requestUpdate(context, MediaWidgetUpdateReason.ControlFeedback)
        onComplete()
      }
    }
  }

  fun onWidgetsDeleted(context: Context, widgetIds: IntArray, onComplete: () -> Unit = {}) {
    scope.launch {
      try {
        requestedGeneration.issue()
        mutex.lock()
        try {
          MediaWidgetSettingsRepository(context).delete(widgetIds)
          widgetIds.forEach(::clearState)
        } finally { mutex.unlock() }
      } finally { onComplete() }
    }
  }

  fun restore(context: Context, oldIds: IntArray, newIds: IntArray, onComplete: () -> Unit) {
    scope.launch {
      try {
        requestedGeneration.issue()
        mutex.lock()
        try {
          val defaults = MediaWidgetSettings.fromLegacy(AppSettingsSnapshotReader.readBlocking(context))
          MediaWidgetSettingsRepository(context).restore(oldIds, newIds, defaults)
          (oldIds + newIds).forEach(::clearState)
          newIds.forEach { id -> android.appwidget.AppWidgetManager.getInstance(context).updateAppWidgetOptions(id,
            android.os.Bundle().apply { putBoolean(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true) }) }
        } finally { mutex.unlock() }
        requestUpdate(context, MediaWidgetUpdateReason.WidgetLifecycle)
      } finally { onComplete() }
    }
  }

  private fun clearState(id: Int) {
    latest.remove(id); pending.remove(id)
    commandJobs.remove(id)?.cancel(); artworkJobs.remove(id)?.second?.cancel()
    MediaWidgetUpdater.onDeleted(intArrayOf(id))
  }

  private suspend fun process(request: UpdateRequest) {
    if (!request.reason.immediate) delay(MediaSignalDebounceMs)
    if (!requestedGeneration.isLatest(request.generation)) return
    val repository = MediaWidgetSettingsRepository(request.context)
    val app = AppSettingsSnapshotReader.readBlocking(request.context)
    mutex.lock()
    val settings = try {
      if (!requestedGeneration.isLatest(request.generation)) return
      repository.ensure(ids(request.context), MediaWidgetSettings.fromLegacy(app))
    } finally { mutex.unlock() }
    val snapshots = MediaSessionReader.readSnapshots(request.context, settings.values)
    mutex.lock()
    try {
      if (!requestedGeneration.isLatest(request.generation)) return
      val activeIds = ids(request.context).toSet()
      for ((id, config) in settings) {
        if (id !in activeIds || repository.read(id) != config) continue
        val snapshot = snapshots.getValue(config.source to config.keepLastSong)
        val old = latest[id]
        if (old != null && (old.first.source != config.source || old.second.sessionToken != snapshot.sessionToken)) {
          clearState(id)
        }
        val command = pending[id]
        val confirmed = command != null && when (command.action) {
          MediaControlAction.TogglePlayPause -> snapshot.isPlaying == command.expectedPlaying
          else -> snapshot.trackKey != command.baselineTrackKey
        }
        if (command != null && (confirmed || SystemClock.elapsedRealtime() - command.startedAtElapsedMs >= CommandTimeoutMs ||
            snapshot.availability != MediaPlaybackAvailability.Active)) {
          pending.remove(id); commandJobs.remove(id)?.cancel()
        }
        val feedback = pending[id]?.let { MediaWidgetInteraction(it.action,
          config.animatedFeedback || config.instantControls, it.expectedPlaying) }
        MediaWidgetUpdater.renderInstance(request.context, id, snapshot, config.applyTo(app), feedback)
        latest[id] = config to snapshot
        if (snapshot.availability == MediaPlaybackAvailability.Active && snapshot.artwork == null) {
          if (artworkJobs[id]?.first != snapshot.trackKey) {
            artworkJobs.remove(id)?.second?.cancel()
            artworkJobs[id] = snapshot.trackKey to scope.launch {
              var last = 0L
              ArtworkRetryDelaysMs.forEach { next ->
                delay(next - last); last = next
                requestUpdate(request.context, MediaWidgetUpdateReason.ArtworkRetry)
              }
            }
          }
        } else { artworkJobs.remove(id)?.second?.cancel() }
      }
    } finally { mutex.unlock() }
  }
}

package com.pampa.widgets.widget.media

import android.content.Context
import android.os.SystemClock
import com.pampa.widgets.core.media.MediaControlAction
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.media.MediaSessionReader
import com.pampa.widgets.core.settings.AppSettingsSnapshotReader
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

internal fun interface MediaPlaybackSnapshotReader {
  suspend fun read(context: Context, keepLastSong: Boolean): MediaPlaybackSnapshot
}

internal class LatestUpdateGeneration {
  private val value = AtomicLong(0L)

  fun issue(): Long = value.incrementAndGet()

  fun isLatest(generation: Long): Boolean = generation == value.get()
}

private object PlatformMediaPlaybackSnapshotReader : MediaPlaybackSnapshotReader {
  override suspend fun read(context: Context, keepLastSong: Boolean): MediaPlaybackSnapshot =
    MediaSessionReader.readSnapshot(context, keepLastSong)
}

/**
 * Serializes every media/widget update and rejects work from an older generation before render.
 * A conflated queue collapses callback bursts without losing the newest state.
 */
internal object MediaWidgetUpdateCoordinator {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val requests = Channel<UpdateRequest>(Channel.CONFLATED)
  private val requestedGeneration = LatestUpdateGeneration()
  private val stateLock = Any()
  private var snapshotReader: MediaPlaybackSnapshotReader = PlatformMediaPlaybackSnapshotReader
  private var latestSnapshot: MediaPlaybackSnapshot? = null
  private var nextCommandId = 0L
  private var pendingCommand: PendingMediaCommand? = null
  private var commandResolutionJob: Job? = null
  private var artworkRetryTrackKey = ""
  private var artworkRetryJob: Job? = null

  init {
    scope.launch {
      for (request in requests) process(request)
    }
  }

  fun requestUpdate(
    context: Context,
    reason: MediaWidgetUpdateReason = MediaWidgetUpdateReason.MediaSignal,
  ) {
    val appContext = context.applicationContext
    val generation = requestedGeneration.issue()
    requests.trySend(UpdateRequest(generation, appContext, reason))
  }

  fun dispatchControl(
    context: Context,
    action: MediaControlAction,
    onComplete: () -> Unit = {},
  ) {
    val appContext = context.applicationContext
    scope.launch {
      try {
        val settings = withContext(Dispatchers.IO) {
          AppSettingsSnapshotReader.readBlocking(appContext)
        }
        val baseline = synchronized(stateLock) { latestSnapshot }
          ?: snapshotReader.read(appContext, settings.mediaWidgetKeepLastSong)
        val dispatched = MediaSessionReader.dispatch(appContext, action)
        if (dispatched && baseline.canHandle(action)) {
          val command = synchronized(stateLock) {
            PendingMediaCommand(
              id = ++nextCommandId,
              action = action,
              baselineTrackKey = baseline.trackKey,
              expectedPlaying = if (action == MediaControlAction.TogglePlayPause) !baseline.isPlaying else null,
              startedAtElapsedMs = SystemClock.elapsedRealtime(),
            ).also { pendingCommand = it }
          }
          scheduleCommandResolution(appContext, command)
        }
        requestUpdate(appContext, MediaWidgetUpdateReason.ControlFeedback)
      } finally {
        onComplete()
      }
    }
  }

  fun onWidgetsDeleted(appWidgetIds: IntArray) {
    MediaWidgetUpdater.onDeleted(appWidgetIds)
  }

  private suspend fun process(request: UpdateRequest) {
    if (!request.reason.immediate) delay(MediaSignalDebounceMs)
    if (!requestedGeneration.isLatest(request.generation)) return

    val settings = withContext(Dispatchers.IO) {
      AppSettingsSnapshotReader.readBlocking(request.context)
    }
    val snapshot = snapshotReader.read(request.context, settings.mediaWidgetKeepLastSong)
    if (!requestedGeneration.isLatest(request.generation)) return

    val interaction = interactionFor(snapshot, settings.mediaWidgetAnimatedFeedback || settings.mediaWidgetInstantControls)
    MediaWidgetUpdater.renderAll(
      context = request.context,
      snapshot = snapshot,
      settings = settings,
      interaction = interaction,
    )
    synchronized(stateLock) { latestSnapshot = snapshot }
    updateArtworkRetry(request.context, snapshot)
  }

  private fun interactionFor(
    snapshot: MediaPlaybackSnapshot,
    showFeedback: Boolean,
  ): MediaWidgetInteraction? = synchronized(stateLock) {
    val command = pendingCommand ?: return@synchronized null
    val expired = SystemClock.elapsedRealtime() - command.startedAtElapsedMs >= CommandTimeoutMs
    if (expired || snapshot.confirms(command) || snapshot.availability == MediaPlaybackAvailability.PermissionRequired) {
      pendingCommand = null
      commandResolutionJob?.cancel()
      commandResolutionJob = null
      return@synchronized null
    }
    MediaWidgetInteraction(command.action, showFeedback, command.expectedPlaying)
  }

  private fun scheduleCommandResolution(context: Context, command: PendingMediaCommand) {
    synchronized(stateLock) {
      commandResolutionJob?.cancel()
      commandResolutionJob = scope.launch {
        var previousDelay = 0L
        CommandResyncDelaysMs.forEach { absoluteDelay ->
          delay(absoluteDelay - previousDelay)
          previousDelay = absoluteDelay
          if (!isCommandPending(command.id)) return@launch
          requestUpdate(context, MediaWidgetUpdateReason.ControlResync)
        }
        delay((CommandTimeoutMs - previousDelay).coerceAtLeast(0L))
        val cleared = synchronized(stateLock) {
          if (pendingCommand?.id != command.id) false else {
            pendingCommand = null
            commandResolutionJob = null
            true
          }
        }
        if (cleared) requestUpdate(context, MediaWidgetUpdateReason.ControlResync)
      }
    }
  }

  private fun isCommandPending(commandId: Long): Boolean =
    synchronized(stateLock) { pendingCommand?.id == commandId }

  private fun updateArtworkRetry(context: Context, snapshot: MediaPlaybackSnapshot) {
    val shouldRetry = snapshot.availability == MediaPlaybackAvailability.Active &&
      snapshot.trackKey.isNotBlank() && snapshot.artwork == null
    if (!shouldRetry) {
      cancelArtworkRetry()
      return
    }
    synchronized(stateLock) {
      if (artworkRetryTrackKey == snapshot.trackKey) return
      artworkRetryJob?.cancel()
      artworkRetryTrackKey = snapshot.trackKey
      artworkRetryJob = scope.launch {
        var previousDelay = 0L
        ArtworkRetryDelaysMs.forEach { absoluteDelay ->
          delay(absoluteDelay - previousDelay)
          previousDelay = absoluteDelay
          val stillMissing = synchronized(stateLock) {
            latestSnapshot?.trackKey == snapshot.trackKey && latestSnapshot?.artwork == null
          }
          if (!stillMissing) return@launch
          requestUpdate(context, MediaWidgetUpdateReason.ArtworkRetry)
        }
      }
    }
  }

  private fun cancelArtworkRetry() = synchronized(stateLock) {
    artworkRetryJob?.cancel()
    artworkRetryJob = null
    artworkRetryTrackKey = ""
  }

  private fun MediaPlaybackSnapshot.canHandle(action: MediaControlAction): Boolean =
    when (action) {
      MediaControlAction.TogglePlayPause -> canPlayPause
      MediaControlAction.Next -> canSkipNext
      MediaControlAction.Previous -> canSkipPrevious
    }

  private fun MediaPlaybackSnapshot.confirms(command: PendingMediaCommand): Boolean {
    if (availability != MediaPlaybackAvailability.Active) return false
    return when (command.action) {
      MediaControlAction.TogglePlayPause -> isPlaying == command.expectedPlaying
      MediaControlAction.Next,
      MediaControlAction.Previous -> trackKey.isNotBlank() && trackKey != command.baselineTrackKey
    }
  }

  internal fun setSnapshotReaderForTest(reader: MediaPlaybackSnapshotReader) {
    synchronized(stateLock) { snapshotReader = reader }
  }

  internal fun resetSnapshotReaderForTest() {
    synchronized(stateLock) { snapshotReader = PlatformMediaPlaybackSnapshotReader }
  }
}

package com.example.scrollfit.platform.pose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.View
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.example.scrollfit.core.data.CameraChoice
import com.example.scrollfit.core.pose.CountingProgress
import com.example.scrollfit.core.pose.ExerciseCounter
import com.example.scrollfit.core.pose.PoseFilter
import com.example.scrollfit.core.pose.PoseSample
import com.example.scrollfit.core.pose.PoseSessionGate
import com.example.scrollfit.core.pose.TrackingState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class CameraPoseStatus {
    INITIALIZING, SEARCHING, TRACKING, TRACKING_LOST, PAUSED,
    PERMISSION_REQUIRED, CAMERA_UNAVAILABLE, CAMERA_ERROR, MODEL_ERROR, INFERENCE_ERROR,
}

data class CameraPoseState(
    val status: CameraPoseStatus,
    val sample: PoseSample? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val mirrored: Boolean = false,
    val progress: CountingProgress? = null,
    val timestampMs: Long? = null,
)

/** Main-thread owner. Each instance is confined to one visible screen/lens choice. */
class CameraPoseSession(
    context: Context,
    private val owner: LifecycleOwner,
    private val previewView: PreviewView,
    private val choice: CameraChoice,
    requiredLandmarks: Set<Int>,
    alternativeRequiredLandmarks: Set<Int> = emptySet(),
    /** Each owned camera run has a fresh counter; the challenge owner invalidates on restart. */
    private val counter: ExerciseCounter? = null,
    private val onState: (CameraPoseState) -> Unit,
) : LifecycleEventObserver, AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val gate = PoseSessionGate()
    private val filter = PoseFilter(requiredLandmarks, alternativeRequiredLandmarks = alternativeRequiredLandmarks)
    private data class Delivery(val token: Long, val timestampMs: Long, val state: CameraPoseState)
    private val pending = AtomicReference<Delivery?>(null)
    private val deliveryPosted = AtomicBoolean(false)
    private var model: MediaPipePoseEstimator? = null // worker only
    private var provider: ProcessCameraProvider? = null // main only below
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var cameraInfo: CameraInfo? = null
    private var cameraObserver: Observer<CameraState>? = null
    private var layoutListener: View.OnLayoutChangeListener? = null
    private var token = 0L
    private var running = false
    private var closed = false
    private var lastDeliveryMs = 0L
    private var lastStatus = CameraPoseStatus.INITIALIZING
    private val displays = this.context.getSystemService(DisplayManager::class.java)
    private var boundRotation: Int? = null
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            val display = previewView.display ?: return
            if (running && display.displayId == displayId && boundRotation != null &&
                display.rotation != boundRotation) {
                // Refresh both use cases and the shared viewport, including 180-degree
                // changes that do not recreate the Activity or resize the view.
                stop()
                start()
            }
        }
    }

    private val deliver = object : Runnable {
        override fun run() {
            val update = pending.getAndSet(null)
            deliveryPosted.set(false)
            if (update != null && gate.isCurrent(update.token)) {
                lastDeliveryMs = SystemClock.elapsedRealtime()
                val state = if (gate.isFresh(update.token, update.timestampMs, lastDeliveryMs)) update.state
                    else update.state.copy(status = CameraPoseStatus.TRACKING_LOST, sample = null,
                        progress = update.state.progress?.copy(trackingAvailable = false))
                emit(state.copy(timestampMs = update.timestampMs))
            }
            if (pending.get() != null && deliveryPosted.compareAndSet(false, true)) main.post(this)
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!running || closed) return
            if (!hasPermission()) {
                fail(token, CameraPoseStatus.PERMISSION_REQUIRED)
                return
            }
            val gap = SystemClock.elapsedRealtime() - lastDeliveryMs
            if (gap > 750 && lastStatus == CameraPoseStatus.TRACKING) {
                emit(CameraPoseState(CameraPoseStatus.TRACKING_LOST))
            }
            if (gap > 5_000 && lastStatus == CameraPoseStatus.INITIALIZING) {
                fail(token, CameraPoseStatus.CAMERA_ERROR)
                return
            }
            main.postDelayed(this, 750)
        }
    }

    init {
        check(Looper.myLooper() == Looper.getMainLooper())
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        owner.lifecycle.addObserver(this)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
    }

    override fun onStateChanged(owner: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> start()
            Lifecycle.Event.ON_PAUSE -> stop()
            Lifecycle.Event.ON_DESTROY -> close()
            else -> Unit
        }
    }

    private fun start() {
        if (closed || running) return
        if (!hasPermission()) {
            emit(CameraPoseState(CameraPoseStatus.PERMISSION_REQUIRED))
            return
        }
        running = true
        token = gate.start()
        val current = token
        lastDeliveryMs = SystemClock.elapsedRealtime()
        emit(CameraPoseState(CameraPoseStatus.INITIALIZING))
        main.postDelayed(watchdog, 750)
        displays.registerDisplayListener(displayListener, main)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!gate.isCurrent(current)) return@addListener
            try {
                val available = future.get()
                provider = available
                val selector = selector()
                if (!available.hasCamera(selector)) {
                    fail(current, CameraPoseStatus.CAMERA_UNAVAILABLE)
                    return@addListener
                }
                worker.execute {
                    if (!gate.isCurrent(current)) return@execute
                    try {
                        model = MediaPipePoseEstimator(context)
                        filter.reset()
                        main.post { if (gate.isCurrent(current)) bindWhenLaidOut(current, available) }
                    } catch (_: Exception) {
                        main.post { fail(current, CameraPoseStatus.MODEL_ERROR) }
                    }
                }
            } catch (_: Exception) {
                fail(current, CameraPoseStatus.CAMERA_ERROR)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindWhenLaidOut(current: Long, available: ProcessCameraProvider) {
        if (previewView.width > 0 && previewView.height > 0) bind(current, available)
        else {
            val listener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                                            oldL: Int, oldT: Int, oldR: Int, oldB: Int) {
                    previewView.removeOnLayoutChangeListener(this)
                    layoutListener = null
                    if (gate.isCurrent(current)) bind(current, available)
                }
            }
            layoutListener = listener
            previewView.addOnLayoutChangeListener(listener)
        }
    }

    private fun bind(current: Long, available: ProcessCameraProvider) {
        if (!hasPermission()) {
            fail(current, CameraPoseStatus.PERMISSION_REQUIRED)
            return
        }
        try {
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            boundRotation = rotation
            val p = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation).build()
            val a = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            preview = p
            analysis = a
            p.setSurfaceProvider(previewView.surfaceProvider)
            a.setAnalyzer(worker) { image -> analyze(current, image) }
            val group = UseCaseGroup.Builder().addUseCase(p).addUseCase(a)
            previewView.viewPort?.let { group.setViewPort(it) }
            val camera = available.bindToLifecycle(owner, selector(), group.build())
            cameraInfo = camera.cameraInfo
            val observer = Observer<CameraState> { state ->
                if (state.error != null) fail(current, CameraPoseStatus.CAMERA_ERROR)
            }
            cameraObserver = observer
            camera.cameraInfo.cameraState.observe(owner, observer)
        } catch (_: SecurityException) {
            fail(current, CameraPoseStatus.PERMISSION_REQUIRED)
        } catch (_: Exception) {
            fail(current, CameraPoseStatus.CAMERA_ERROR)
        }
    }

    private fun analyze(current: Long, image: ImageProxy) {
        // Camera sensor timestamps may use an unknown time base. Use arrival monotonic
        // time, with CameraX retaining only its latest frame, then reject delayed results.
        val timestamp = SystemClock.elapsedRealtime()
        var bitmap: Bitmap? = null
        var cropped: Bitmap? = null
        var upright: Bitmap? = null
        var admitted = false
        try {
            admitted = gate.admit(current, timestamp, SystemClock.elapsedRealtime())
            if (!admitted) return
            bitmap = image.toBitmap() // CameraX handles row/pixel strides and channel order.
            val rect = image.cropRect
            cropped = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
            upright = uprightBitmap(cropped, image.imageInfo.rotationDegrees)
            val sample = model?.estimate(upright, timestamp)
            val filtered = filter.accept(sample, timestamp)
            if (SystemClock.elapsedRealtime() - timestamp > 250) {
                filter.reset()
                val progress = counter?.accept(null, timestamp)
                publish(current, timestamp, CameraPoseState(CameraPoseStatus.TRACKING_LOST, progress = progress))
                return
            }
            if (!gate.isCurrent(current)) return
            // Each fresh raw inference reaches the detector on this serial worker.
            // UI delivery below may coalesce; do not run counting from that stream.
            val progress = counter?.accept(sample, timestamp)
            val status = if (progress != null && !progress.trackingAvailable) {
                if (filtered.tracking == TrackingState.SEARCHING) CameraPoseStatus.SEARCHING
                else CameraPoseStatus.TRACKING_LOST
            } else when (filtered.tracking) {
                TrackingState.TRACKING -> CameraPoseStatus.TRACKING
                TrackingState.LOST -> CameraPoseStatus.TRACKING_LOST
                TrackingState.SEARCHING -> CameraPoseStatus.SEARCHING
            }
            publish(current, timestamp, CameraPoseState(status, filtered.sample, upright.width, upright.height,
                choice == CameraChoice.FRONT, progress))
        } catch (_: Exception) {
            main.post { fail(current, CameraPoseStatus.INFERENCE_ERROR) }
        } finally {
            if (upright != null && upright !== cropped && upright !== bitmap) upright.recycle()
            if (cropped != null && cropped !== bitmap) cropped.recycle()
            bitmap?.recycle()
            image.close()
            if (admitted) gate.finish(current)
        }
    }

    private fun publish(current: Long, timestampMs: Long, state: CameraPoseState) {
        if (!gate.isCurrent(current)) return
        pending.set(Delivery(current, timestampMs, state))
        if (deliveryPosted.compareAndSet(false, true)) main.post(deliver)
    }

    private fun emit(state: CameraPoseState) {
        lastStatus = state.status
        onState(state)
    }

    private fun fail(current: Long, status: CameraPoseStatus) {
        if (!gate.isCurrent(current)) return
        stop()
        emit(CameraPoseState(status))
    }

    private fun stop() {
        if (!running) return
        running = false
        gate.stop() // Invalidate before camera/model cleanup or any queued result delivery.
        main.removeCallbacks(watchdog)
        displays.unregisterDisplayListener(displayListener)
        boundRotation = null
        pending.set(null)
        layoutListener?.let { previewView.removeOnLayoutChangeListener(it) }
        layoutListener = null
        cameraObserver?.let { cameraInfo?.cameraState?.removeObserver(it) }
        cameraObserver = null
        cameraInfo = null
        analysis?.clearAnalyzer()
        val owned = listOfNotNull(preview, analysis).toTypedArray()
        if (owned.isNotEmpty()) provider?.unbind(*owned)
        preview = null
        analysis = null
        worker.execute {
            try { model?.close() } finally {
                model = null
                filter.reset()
                counter?.reset()
            }
        }
        emit(CameraPoseState(CameraPoseStatus.PAUSED))
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
        owner.lifecycle.removeObserver(this)
        main.removeCallbacks(deliver)
        worker.shutdown() // Already queued close finishes behind any in-flight inference.
    }

    private fun selector() = if (choice == CameraChoice.FRONT)
        CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    private fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
}

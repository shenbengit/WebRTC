package com.shencoder.webrtcdemo

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.WatermarkVideoProcessor
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class MainActivity : AppCompatActivity() {
    private lateinit var previewButton: Button
    private lateinit var surfaceViewRenderer: SurfaceViewRenderer

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var watermarkVideoProcessor: WatermarkVideoProcessor? = null
    private var videoTrack: VideoTrack? = null
    private var isPreviewing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceViewRenderer = findViewById(R.id.surfaceViewRenderer)
        previewButton = findViewById(R.id.previewButton)

        previewButton.setOnClickListener {
            if (isPreviewing) return@setOnClickListener

            if (hasCameraPermission()) {
                startCameraPreview()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMERA_PERMISSION) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        } else {
            Toast.makeText(this, "需要相机权限才能预览", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        releaseCameraPreview()
        super.onDestroy()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCameraPreview() {
        try {
            ensureWebRtcInitialized()

            eglBase = EglBase.create()
            surfaceViewRenderer.init(eglBase!!.eglBaseContext, null)
            surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            surfaceViewRenderer.setMirror(true)
            surfaceViewRenderer.setEnableHardwareScaler(true)

            peerConnectionFactory = createPeerConnectionFactory(eglBase!!.eglBaseContext)
            videoCapturer = createCameraCapturer()

            if (videoCapturer == null) {
                Toast.makeText(this, "未找到可用摄像头", Toast.LENGTH_SHORT).show()
                releaseCameraPreview()
                return
            }

            surfaceTextureHelper = SurfaceTextureHelper.create(
                "CameraCaptureThread",
                eglBase!!.eglBaseContext
            )

            videoSource = peerConnectionFactory!!.createVideoSource(false)
            watermarkVideoProcessor = WatermarkVideoProcessor(eglBase!!.eglBaseContext).apply {
                setWatermarks(
                    WatermarkVideoProcessor.Watermark.withWidthFraction(
                        createAppIconWatermarkBitmap(WATERMARK_SOURCE_SIZE_PX),
                        WATERMARK_WIDTH_FRACTION,
                        WatermarkVideoProcessor.Anchor.CENTER,
                        0,
                        WATERMARK_MARGIN_PX
                    )
                )
            }
            videoSource!!.setVideoProcessor(watermarkVideoProcessor)

            videoCapturer!!.initialize(
                surfaceTextureHelper,
                applicationContext,
                videoSource!!.capturerObserver
            )
            videoCapturer!!.startCapture(PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_FPS)

            videoTrack = peerConnectionFactory!!.createVideoTrack("camera_video_track", videoSource)
            videoTrack!!.addSink(surfaceViewRenderer)

            isPreviewing = true
            previewButton.isEnabled = false
        } catch (e: Exception) {
            Log.e(TAG, "Start camera preview failed", e)
            Toast.makeText(this, "启动摄像头预览失败", Toast.LENGTH_SHORT).show()
            releaseCameraPreview()
        }
    }

    private fun ensureWebRtcInitialized() {
        if (isWebRtcInitialized) return

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .createInitializationOptions()
        )
        isWebRtcInitialized = true
    }

    private fun createPeerConnectionFactory(
        eglBaseContext: EglBase.Context
    ): PeerConnectionFactory {
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun createAppIconWatermarkBitmap(sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val background = ContextCompat.getDrawable(this, R.drawable.ic_launcher_background)
            ?: throw IllegalStateException("App icon background not found")
        background.setBounds(0, 0, canvas.width, canvas.height)
        background.draw(canvas)

        val foreground = ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)
            ?: throw IllegalStateException("App icon foreground not found")
        foreground.setBounds(0, 0, canvas.width, canvas.height)
        foreground.draw(canvas)

        return bitmap
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(this)) {
            Camera2Enumerator(this)
        } else {
            Camera1Enumerator(false)
        }

        return createCapturer(enumerator, frontFacing = true)
            ?: createCapturer(enumerator, frontFacing = false)
    }

    private fun createCapturer(
        enumerator: CameraEnumerator,
        frontFacing: Boolean
    ): CameraVideoCapturer? {
        for (deviceName in enumerator.deviceNames) {
            val isTargetCamera = if (frontFacing) {
                enumerator.isFrontFacing(deviceName)
            } else {
                enumerator.isBackFacing(deviceName)
            }

            if (isTargetCamera) {
                return enumerator.createCapturer(deviceName, cameraEventsHandler)
            }
        }
        return null
    }

    private fun releaseCameraPreview() {
        isPreviewing = false
        if (::previewButton.isInitialized) {
            previewButton.isEnabled = true
        }

        if (::surfaceViewRenderer.isInitialized) {
            videoTrack?.removeSink(surfaceViewRenderer)
        }
        videoTrack?.dispose()
        videoTrack = null

        try {
            videoCapturer?.stopCapture()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Stop camera capture interrupted", e)
        } catch (e: Exception) {
            Log.w(TAG, "Stop camera capture failed", e)
        }
        videoCapturer?.dispose()
        videoCapturer = null

        videoSource?.setVideoProcessor(null)
        watermarkVideoProcessor?.dispose()
        watermarkVideoProcessor = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        if (::surfaceViewRenderer.isInitialized) {
            surfaceViewRenderer.release()
        }
        eglBase?.release()
        eglBase = null
    }

    private val cameraEventsHandler = object : CameraVideoCapturer.CameraEventsHandler {
        override fun onCameraError(errorDescription: String) {
            Log.e(TAG, "Camera error: $errorDescription")
            runOnUiThread {
                Toast.makeText(this@MainActivity, errorDescription, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCameraDisconnected() {
            Log.w(TAG, "Camera disconnected")
        }

        override fun onCameraFreezed(errorDescription: String) {
            Log.e(TAG, "Camera freezed: $errorDescription")
        }

        override fun onCameraOpening(cameraName: String) {
            Log.d(TAG, "Camera opening: $cameraName")
        }

        override fun onFirstFrameAvailable() {
            Log.d(TAG, "First camera frame available")
        }

        override fun onCameraClosed() {
            Log.d(TAG, "Camera closed")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CAMERA_PERMISSION = 1001
        private const val PREVIEW_WIDTH = 1280
        private const val PREVIEW_HEIGHT = 720
        private const val PREVIEW_FPS = 30
        private const val WATERMARK_SOURCE_SIZE_PX = 256
        private const val WATERMARK_MARGIN_PX = 24
        private const val WATERMARK_WIDTH_FRACTION = 0.14f
        private var isWebRtcInitialized = false
    }
}

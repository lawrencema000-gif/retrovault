package com.retrovault.app

import android.app.Activity
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.retrovault.emulator.LibretroBridge
import java.util.concurrent.CountDownLatch

/** Bare SurfaceView host for render tests; feeds its Surface to the native host. */
class RenderTestActivity : Activity() {

    val surfaceReady = CountDownLatch(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = SurfaceView(this)
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                LibretroBridge.nativeSetVideoSurface(holder.surface)
                surfaceReady.countDown()
            }

            override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
                LibretroBridge.nativeSetVideoSurface(holder.surface)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                LibretroBridge.nativeSetVideoSurface(null)
            }
        })
        setContentView(view)
    }
}

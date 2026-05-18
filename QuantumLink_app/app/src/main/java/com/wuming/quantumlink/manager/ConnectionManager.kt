package com.wuming.quantumlink.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.wuming.quantumlink.core.Constants
import com.wuming.quantumlink.core.IMManager

/**
 * 连接管理器 — 监听应用前后台切换，自动重连 WebSocket
 */
object ConnectionManager {

    private const val TAG = "ConnectionManager"
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // 应用回到前台，检查并重连
                    val im = IMManager.getInstance()
                    if (!im.isConnected && LoginManager.hasSavedLogin()) {
                        val info = LoginManager.getSavedLogin()
                        if (info != null) {
                            Log.d(TAG, "应用回到前台，重连 WebSocket")
                            im.connect(
                                Constants.Server.host,
                                Constants.Server.port,
                                info.userId
                            )
                        }
                    } else if (im.isConnected) {
                        Log.d(TAG, "应用回到前台，WS 已连接跳过重连")
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    Log.d(TAG, "应用进入后台")
                }
                else -> {}
            }
        })
    }
}

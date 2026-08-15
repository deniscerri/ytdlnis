package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.terminal.SessionService
import com.deniscerri.ytdl.ui.more.terminal.TerminalActivity
import com.deniscerri.ytdl.ui.more.terminal.TerminalBackEnd
import com.google.android.material.R
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysListener
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysView
import com.deniscerri.ytdl.ui.more.terminal.TerminalUtils
import com.termux.view.TerminalView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.lang.ref.WeakReference


class TerminalViewModel(private val application: Application) : AndroidViewModel(application) {
    private var terminalViewRef = WeakReference<TerminalView>(null)
    private var virtualKeysViewRef = WeakReference<VirtualKeysView>(null)

    val terminalView: TerminalView? get() = terminalViewRef.get()
    val virtualKeysView: VirtualKeysView? get() = virtualKeysViewRef.get()

    fun setTerminalView(view: TerminalView?) { terminalViewRef = WeakReference(view) }
    fun setVirtualKeysView(view: VirtualKeysView?) { virtualKeysViewRef = WeakReference(view) }

    fun setFont(typeface: Typeface) {
        TerminalUtils.typeface = typeface
        terminalView?.apply {
            setTypeface(typeface)
            onScreenUpdated()
        }
    }

    fun changeSession(context: Context, sessionBinder: SessionService.SessionBinder, sessionId: String) {
        val terminal = terminalView ?: return
        val activity = context as? TerminalActivity ?: return
        val client = TerminalBackEnd(terminal, activity)

        val session = sessionBinder.getSession(sessionId)
            ?: sessionBinder.createSession(sessionId, client)

        session.updateTerminalSessionClient(client)
        terminal.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val zoom = PreferenceManager.getDefaultSharedPreferences(context)
            .getFloat("terminal_zoom", 14f).coerceIn(10f, 30f)
        terminal.setTextSize(zoom.toInt())
        terminal.setTypeface(TerminalUtils.typeface)

        terminal.attachSession(session)
        terminal.setTerminalViewClient(client)

        terminal.post {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)
            terminal.keepScreenOn = true
            terminal.requestFocus()
            terminal.isFocusableInTouchMode = true

            terminal.mEmulator?.mColors?.mCurrentColors?.apply {
                set(256, typedValue.data)
                set(257, TerminalUtils.getBackgroundColor(context))
                set(258, typedValue.data)
            }
        }

        virtualKeysView?.apply {
            virtualKeysViewClient = terminal.mTermSession?.let { VirtualKeysListener(it) }
        }

        sessionBinder.getService().currentSession.value = sessionId
    }

    var sessionBinder by mutableStateOf<SessionService.SessionBinder?>(null)
        private set

    var isBound by mutableStateOf(false)
        private set

    private val _isBoundState = MutableStateFlow(false)
    val isBoundState: StateFlow<Boolean> = _isBoundState

    private val _serviceConnectedEvent = Channel<Unit>(Channel.BUFFERED)
    val serviceConnectedEvent = _serviceConnectedEvent.receiveAsFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sessionBinder = service as SessionService.SessionBinder
            isBound = true
            _isBoundState.value = true
            _serviceConnectedEvent.trySend(Unit)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            sessionBinder = null
            _isBoundState.value = false
        }
    }

    fun startAndBindService(context: Context) {
        val intent = Intent(context, SessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            sessionBinder = null
        }
    }
}
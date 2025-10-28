package com.deniscerri.ytdl.util.extractors.ytdlp.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SuppressLint("SetJavaScriptEnabled")
class YTDLPWebview(private val context: Context, private val port: Int, private val evalTimeoutMs: Long = 10_000L) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()
    private lateinit var webView: WebView

    // map callId -> callback
    private val callbacks = ConcurrentHashMap<String, (JSONObject) -> Unit>()

    init {
        initWebView()
        // start server off-main thread
        executor.submit { startServer() }
    }

    private fun initWebView() {
        mainHandler.post {
            webView = WebView(context)
            val s = webView.settings
            s.javaScriptEnabled = true
            s.allowFileAccess = false
            s.allowContentAccess = false
            // disable access to file:// and other risky things
            webView.settings.domStorageEnabled = false
            webView.webViewClient = WebViewClient()
            webView.addJavascriptInterface(JsBridge(), "AndroidBridge")
            // Load blank page (we will eval into this context)
            webView.loadData("<html><body></body></html>", "text/html", "utf-8")
        }
    }

    inner class JsBridge {
        @JavascriptInterface
        fun postResponse(id: String, json: String) {
            val obj = try { JSONObject(json) } catch (_: Exception) {
                JSONObject().put("result", JSONObject.NULL).put("error", "invalid_json_from_js")
            }
            callbacks.remove(id)?.invoke(obj)
        }
    }

    private fun startServer() {
        // Bind only to localhost
        val server = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        while (!server.isClosed) {
            val client = server.accept()
            executor.submit { handleClient(client) }
        }
    }

    private fun handleClient(client: Socket) {
        client.soTimeout = (evalTimeoutMs + 2000).toInt()
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        // read until NUL terminator (wrapper should send code+'\0')
        val sb = StringBuilder()
        var ch = reader.read()
        while (ch >= 0 && ch != 0) {
            sb.append(ch.toChar())
            ch = reader.read()
        }
        val code = sb.toString()

        val callId = System.nanoTime().toString()
        // register callback with timeout
        val done = Object()
        var responded = false

        callbacks[callId] = { jsonObj ->
            try {
                val bytes = (jsonObj.toString() + "\u0000").toByteArray()
                client.getOutputStream().write(bytes)
                client.getOutputStream().flush()
            } catch (_: Exception) { }
            finally {
                try { client.close() } catch (_: Exception) {}
                synchronized(done) { responded = true; done.notifyAll() }
            }
        }

        // schedule timeout
        executor.submit {
            try {
                Thread.sleep(evalTimeoutMs)
            } catch (_: InterruptedException) {}
            if (callbacks.remove(callId) != null) {
                val timeoutJson = JSONObject().put("result", JSONObject.NULL).put("logs", arrayOf<String>()).put("error", "timeout")
                try {
                    client.getOutputStream().write((timeoutJson.toString() + "\u0000").toByteArray())
                    client.close()
                } catch (_: Exception) {}
            }
        }

        // eval
        evalJsWithConsoleCapture(code, callId)
        // wait briefly for response (not strictly necessary)
        synchronized(done) {
            if (!responded) {
                try { done.wait(evalTimeoutMs + 1000) } catch (_: InterruptedException) {}
            }
        }
    }

    private fun evalJsWithConsoleCapture(code: String, id: String) {
        // wrap code so we capture console logs and async results
        val escaped = code.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"")
        val wrapped = """
            (function(){
              (function(){
                var logs = [];
                var origLog = console.log;
                console.log = function(){
                  try {
                    var a = Array.prototype.slice.call(arguments).map(function(x){ 
                      try { return typeof x === 'object' ? JSON.stringify(x) : String(x); } catch (e) { return String(x); }
                    });
                    logs.push(a.join(' '));
                  } catch(e){}
                  try{ origLog.apply(console, arguments); }catch(e){}
                };
                function send(obj){
                  try{
                    AndroidBridge.postResponse("$id", JSON.stringify(obj));
                  }catch(e){}
                }
                try {
                  var res = (function(){ return eval("$escaped"); })();
                  // if Promise-like, wait for it
                  if (res && typeof res.then === 'function') {
                    res.then(function(v){ send({result: v==undefined? null: v, logs: logs, error: null}); })
                       .catch(function(err){ send({result: null, logs: logs, error: String(err)}); });
                  } else {
                    send({result: res==undefined? null: res, logs: logs, error: null});
                  }
                } catch(e) {
                  send({result: null, logs: logs, error: String(e)});
                }
              })();
            })();
        """.trimIndent()

        mainHandler.post {
            try {
                webView.evaluateJavascript(wrapped, null)
            } catch (e: Exception) {
                // immediate failure: return error
                callbacks.remove(id)?.invoke(JSONObject().put("result", JSONObject.NULL).put("logs", arrayOf<String>()).put("error", e.message))
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
        try { executor.awaitTermination(1, TimeUnit.SECONDS) } catch (_: Exception) {}
        mainHandler.post { webView.destroy() }
    }
}
package com.example.richtapvideoplayer

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.apprichtap.haptic.RichTapPlayer
import com.apprichtap.haptic.RichTapUtils
import com.apprichtap.haptic.sync.SyncCallback
import com.example.richtapvideoplayer.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var hapticPlayer: RichTapPlayer
    // IMPORTANT: this callback is used for sync between media player and haptic player
    // 重要：下面这个回调用于媒体播放器与触感播放器之间的播放进度同步
    private val syncCallback = object : SyncCallback {
        override fun getCurrentPosition() = binding.videoView.currentPosition

        override fun getDuration(): Int {
            //return binding.videoView.duration // may be -1 if media player isn't prepared
            return Utils.getMediaDuration(srcMediaFile)
        }
    }

    private var playbackSpeed = 1.0F
    private lateinit var srcMediaFile: Uri
    private lateinit var srcHeFile: File

    private val seekBarUpdateTimer = Timer(true)
    private var seekBarUpdateTask: TimerTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Prepare the video file and RichTap haptic file for playback
        // 准备素材：视频文件 + RichTap触感文件
        srcMediaFile = Uri.parse("android.resource://$packageName/${R.raw.demo}")
        srcHeFile = dumpAssetToDataStorage("demo.he")

        binding.videoView.apply {
            setVideoURI(srcMediaFile)
            setOnPreparedListener {
                mediaPlayer = it // For later use, e.g. adjust playback speed
                binding.tvDuration.text = Utils.stringForTime(duration)
            }
        }
        hapticPlayer = RichTapPlayer.create(this)
        prepareForPlayback()

        // This button is used to switch player status between play and pause
        // 开始播放/暂停 之间的切换
        binding.btnStart.setOnClickListener {
            if (binding.videoView.isPlaying) {
                binding.videoView.pause()
                hapticPlayer.pause()
                binding.btnStart.text = "Play"
                seekBarUpdateTask?.cancel()  // Stop updating the progress bar
            } else {
                binding.videoView.start()
                hapticPlayer.start()
                binding.btnStart.text = "Pause"
                // Start a timer to update the playback progress-bar
                seekBarUpdateTask = PlayerTimerTask()
                seekBarUpdateTimer.schedule(seekBarUpdateTask, 0, 100)
            }
        }

        // This button is used to stop the current playback
        // 停止播放当前的视频
        binding.btnStop.setOnClickListener {
            seekBarUpdateTask?.cancel()
            seekBarUpdateTimer.purge() // removed all cancelled tasks
            // stop haptic player prior to media player to avoid accident callback
            // 注意顺序：先停止触感播放器，再停止媒体播放器
            hapticPlayer.stop()
            binding.videoView.stopPlayback()

            prepareForPlayback()
        }

        // When the playback finishes, reset all UI control's status
        // 当前播放的视频自然结束时，重置相关的UI状态
        binding.videoView.setOnCompletionListener {
            binding.btnStop.performClick()
        }

        // Seeking support during the playback
        // 支持在播放进度条上的拖动
        binding.seekbarPlayer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.videoView.run {
                        val newPos = duration * progress / 100
                        seekTo(newPos)
                        hapticPlayer.seekTo(newPos)
                        binding.tvCurrent.text = Utils.stringForTime(newPos)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        // Speed can be be changed at any time during the playback
        // 支持倍速播放
        binding.seekbarPlayRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = (0.5 + 2.5 * progress / 100).toFloat()
                    binding.tvPlayRate.text = "${speed}X"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6.0
                    binding.videoView.run {
                        val previousPlaying = isPlaying
                        // NOTE: changing the playback speed will automatically start the playback.
                        // So we call pause() here when necessary after setting the new speed.
                        // 注意: 在prepared状态下调整播放速度后会自动开播！
                        // 因此，调整速度之后，如果之前不是播放状态，则需调用一次pause()
                        playbackSpeed = (0.5 + 2.5 * seekBar.progress / 100).toFloat()
                        mediaPlayer?.let {
                            it.playbackParams = it.playbackParams.setSpeed(playbackSpeed)
                        }
                        hapticPlayer.speed = playbackSpeed

                        if (!previousPlaying) {
                            pause()
                        }
                    }
                }
            }
        })
    }

    private fun prepareForPlayback() {
        binding.sourceFile.text = "Now Playing: $srcMediaFile"
        binding.btnStart.text = "Play"
        binding.seekbarPlayer.progress = 0
        binding.seekbarPlayRate.progress = 20 // speed = 1.0 by default
        binding.tvCurrent.text = Utils.stringForTime(0)
        binding.tvPlayRate.text = "1.0X"

        try {
            binding.videoView.run {
                resume()
            }

            hapticPlayer.run {
                reset()
                setDataSource(srcHeFile, 255, 0, syncCallback)
                prepare()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        // NOTE: VideoView doesn't save the playback progress when switching to background.
        // 注意: VideoView 退到后台时会自动停止播放（没有记忆上次播放进度）
        binding.btnStop.performClick()
        super.onStop()
    }

    override fun onDestroy() {
        seekBarUpdateTimer.cancel()
        // Remember to stop and release haptic player
        // 记得停止触感播放器，并释放其资源
        hapticPlayer.stop()
        hapticPlayer.release()

        mediaPlayer = null
        binding.videoView.suspend()

        super.onDestroy()
    }

    private fun dumpAssetToDataStorage(filename: String) : File {
        try {
            assets.open(filename, Context.MODE_PRIVATE).use {
                val output = openFileOutput(filename, Context.MODE_PRIVATE)
                it.copyTo(output)
                output.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return getFileStreamPath(filename)
    }

    private fun updatePlaybackProgress() {
        runOnUiThread {
            binding.videoView.run {
                if (isPlaying) {
                    val curPos = 100 * currentPosition / duration
                    binding.seekbarPlayer.progress = curPos
                    binding.tvCurrent.text = Utils.stringForTime(currentPosition)
                }
            }
        }
    }

    inner class PlayerTimerTask : TimerTask() {
        override fun run() {
            updatePlaybackProgress()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    // Make sure we're using the correct version of RichTap SDK
    // 当遇到问题时，首先需要确认我们是否使用了正确版本的 RichTap SDK
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.about -> {
                AlertDialog.Builder(this).apply {
                    setTitle("About...")
                    setMessage("App Version: ${BuildConfig.VERSION_NAME}\n" +
                            "RichTap SDK: ${RichTapUtils.VERSION_NAME}")
                    setCancelable(true)
                    setPositiveButton("OK") { _, _ ->}
                    show()
                }
            }

            R.id.close -> finish()
        }
        return true
    }
}
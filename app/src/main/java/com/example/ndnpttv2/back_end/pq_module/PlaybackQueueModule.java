package com.example.ndnpttv2.back_end.pq_module;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.AppState;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.StreamPlayer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.ProgressEventInfo;

import net.named_data.jndn.Name;

import java.util.HashMap;
import java.util.concurrent.LinkedTransferQueue;

public class PlaybackQueueModule {

    private static final String TAG = "PlaybackQueueModule";

    // Private constants
    private static final int DEFAULT_JITTER_BUFFER_SIZE = 5;
    private static final int PROCESSING_INTERVAL_MS = 50;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;
    private static final int MSG_STREAM_CONSUMER_FETCHING_COMPLETE = 1;
    private static final int MSG_STREAM_PLAYER_PLAYING_COMPLETE = 2;
    private static final int MSG_NEW_STREAM_AVAILABLE = 3;

    private Context ctx_;
    private Handler progressEventHandler_;
    private Handler moduleMessageHandler_;
    private Handler workHandler_;
    private LinkedTransferQueue<StreamInfo> fetchingQueue_;
    private LinkedTransferQueue<StreamInfo> playbackQueue_;
    private HashMap<Name, InternalStreamConsumptionState> streamStates_;
    private NetworkThread.Info networkThreadInfo_;

    AppState appState_;

    public PlaybackQueueModule(Context ctx, Looper mainThreadLooper, NetworkThread.Info networkThreadInfo,
                               AppState appState) {

        appState_ = appState;

        ctx_ = ctx;
        fetchingQueue_ = new LinkedTransferQueue<>();
        playbackQueue_ = new LinkedTransferQueue<>();
        streamStates_ = new HashMap<>();
        networkThreadInfo_ = networkThreadInfo;

        progressEventHandler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;
                Name streamName = progressEventInfo.streamName;
                InternalStreamConsumptionState streamState = streamStates_.get(streamName);

                if (streamState == null) {
                    Log.w(TAG, "streamState was null for msg (" +
                            "msg.what " + msg.what + ", " +
                            "stream name " + streamName.toString() +
                            ")");
                    return;
                }

                switch (msg.what) {
                    case MSG_STREAM_CONSUMER_FETCHING_COMPLETE: {
                        Log.d(TAG, "fetching of stream " + streamName.toString() + " finished");
                        break;
                    }
                    case MSG_STREAM_PLAYER_PLAYING_COMPLETE: {
                        Log.d(TAG, "playing of stream " + streamName.toString() + " finished");
                        streamState.streamConsumer.close();
                        streamState.streamPlayer.close();
                        streamStates_.remove(streamName);
                        appState_.stopPlaying();
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        moduleMessageHandler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {

                Log.d(TAG, "got message " + msg.what);

                StreamInfo streamInfo = (StreamInfo) msg.obj;

                switch (msg.what) {
                    case MSG_NEW_STREAM_AVAILABLE: {
                        Log.d(TAG, "Notified of new stream " + "(" +
                                streamInfo.toString() +
                                ")");
                        fetchingQueue_.add(streamInfo);
                        playbackQueue_.add(streamInfo);
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        workHandler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_DO_SOME_WORK:
                    {
                        doSomeWork();
                        break;
                    }
                }
            }
        };

        workHandler_.obtainMessage(MSG_DO_SOME_WORK).sendToTarget(); // start the work handler's work cycle

        Log.d(TAG, "PlaybackQueueModule constructed.");

    }

    public void notifyNewStreamAvailable(StreamInfo streamInfo) {
        Log.d(TAG, "notifyNewStreamAvailable called for stream " + streamInfo.streamName.toString());
        moduleMessageHandler_.obtainMessage(MSG_NEW_STREAM_AVAILABLE, streamInfo).sendToTarget();
    }

    private void doSomeWork() {

        // initiate the fetching of streams ready for fetching
        if (fetchingQueue_.size() != 0) {

            StreamInfo streamInfo = fetchingQueue_.poll();
            Log.d(TAG, "fetching queue was non empty, fetching stream " + streamInfo.streamName.toString());

            InputStreamDataSource transferSource = new InputStreamDataSource();

            StreamPlayer streamPlayer = new StreamPlayer(ctx_, transferSource,
                    streamInfo.streamName, progressEventHandler_);
            streamPlayer.eventPlayingCompleted.addListener(progressEventInfo -> {
                progressEventHandler_
                        .obtainMessage(MSG_STREAM_PLAYER_PLAYING_COMPLETE, progressEventInfo)
                        .sendToTarget();
            });

            StreamConsumer streamConsumer = new StreamConsumer(
                    streamInfo,
                    transferSource,
                    networkThreadInfo_,
                    new StreamConsumer.Options(DEFAULT_JITTER_BUFFER_SIZE)
            );
            InternalStreamConsumptionState internalStreamConsumptionState = new InternalStreamConsumptionState(streamConsumer, streamPlayer);
            streamStates_.put(streamInfo.streamName, internalStreamConsumptionState);
            streamConsumer.eventFetchingCompleted.addListener(progressEventInfo -> {
                progressEventHandler_
                        .obtainMessage(MSG_STREAM_CONSUMER_FETCHING_COMPLETE, progressEventInfo)
                        .sendToTarget();
            });

            streamConsumer.streamFetchStart();

        }

        // initiate the playback of streams ready for playback
        if (playbackQueue_.size() != 0 && !appState_.isRecording() && !appState_.isPlaying()) {

            StreamInfo streamInfo = playbackQueue_.poll();
            Log.d(TAG, "playback queue was non empty, playing stream " + streamInfo.streamName.toString());

            InternalStreamConsumptionState streamState = streamStates_.get(streamInfo.streamName);
            streamState.streamConsumer.streamBufferStart();

            appState_.startPlaying();

        }

        scheduleNextWork(SystemClock.uptimeMillis());
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        workHandler_.removeMessages(MSG_DO_SOME_WORK);
        workHandler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
    }
}

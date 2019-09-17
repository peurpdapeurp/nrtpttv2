package com.example.ndnpttv2.back_end.rec_module;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.AppState;
import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.back_end.rec_module.stream_producer.StreamProducer;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.Name;

import java.util.HashMap;

public class RecorderModule {

    private static final String TAG = "RecorderModule";

    // Private constants
    private static final int SAMPLING_RATE = 8000;
    private static final int FRAMES_PER_SEGMENT = 1;

    // Messages
    private static final int MSG_RECORDING_COMPLETE = 0;
    private static final int MSG_RECORD_REQUEST_START = 1;
    private static final int MSG_RECORD_REQUEST_STOP = 2;

    // Events
    public Event<StreamInfo> eventRecordingStarted;
    public Event<Name> eventRecordingFinished;
    public Event<StreamInfoAndStreamState> eventStreamStateCreated;

    private Handler progressEventHandler_;
    private Handler moduleMessageHandler_;
    private HashMap<Name, InternalStreamProductionState> pastStreamProducers_;
    private StreamProducer currentStreamProducer_;
    private Name applicationDataPrefix_;
    private NetworkThread.Info networkThreadInfo_;
    private long lastStreamId_ = 0;

    private AppState appState_;

    public static class StreamInfoAndStreamState {
        StreamInfoAndStreamState(StreamInfo streamInfo, InternalStreamProductionState streamState) {
            this.streamInfo = streamInfo;
            this.streamState = streamState;
        }
        public StreamInfo streamInfo;
        public InternalStreamProductionState streamState;
    }

    public RecorderModule(Name applicationDataPrefix, NetworkThread.Info networkThreadInfo,
                          AppState appState) {

        appState_ = appState;

        applicationDataPrefix_ = applicationDataPrefix;
        networkThreadInfo_ = networkThreadInfo;
        pastStreamProducers_ = new HashMap<>();

        eventRecordingStarted = new SimpleEvent<>();
        eventRecordingFinished = new SimpleEvent<>();
        eventStreamStateCreated = new SimpleEvent<>();

        progressEventHandler_ = new Handler(networkThreadInfo_.looper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;
                Name streamName = progressEventInfo.streamName;
                InternalStreamProductionState streamState = pastStreamProducers_.get(streamName);

                if (streamState == null) {
                    Log.w(TAG, "streamState was null for msg (" +
                            "msg.what " + msg.what + ", " +
                            "stream name " + streamName.toString() +
                            ")");
                    return;
                }

                switch (msg.what) {
                    case MSG_RECORDING_COMPLETE: {
                        Log.d(TAG, "recording of stream " + streamName.toString() + " finished");
                        appState_.stopRecording();
                        eventRecordingFinished.trigger(streamName);
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        moduleMessageHandler_ = new Handler(networkThreadInfo_.looper) {
            @Override
            public void handleMessage(@NonNull Message msg) {

                Log.d(TAG, "got message " + msg.what);

                switch (msg.what) {
                    case MSG_RECORD_REQUEST_START: {
                        if (appState_.isRecording()) {
                            Log.e(TAG, "Got request to record while already recording, ignoring request.");
                            return;
                        }
                        if (appState_.isPlaying()) {
                            Log.e(TAG, "Got request to record while PlaybackQueueModule was playing, ignoring request.");
                            return;
                        }

                        Log.d(TAG, "Got valid request to start recording, last stream id " + lastStreamId_);

                        lastStreamId_++;

                        long recordingStartTime = System.currentTimeMillis();
                        Name streamName = new Name(applicationDataPrefix_).appendSequenceNumber(lastStreamId_);
                        StreamInfo streamInfo = new StreamInfo(
                                streamName,
                                FRAMES_PER_SEGMENT,
                                SAMPLING_RATE,
                                recordingStartTime
                        );

                        currentStreamProducer_ = new StreamProducer(applicationDataPrefix_, lastStreamId_,
                                networkThreadInfo_,
                                new StreamProducer.Options(FRAMES_PER_SEGMENT, SAMPLING_RATE));
                        currentStreamProducer_.eventFinalSegmentPublished.addListener(progressEventInfo -> {
                            progressEventHandler_
                                    .obtainMessage(MSG_RECORDING_COMPLETE, progressEventInfo)
                                    .sendToTarget();
                        });

                        InternalStreamProductionState state = new InternalStreamProductionState(currentStreamProducer_);

                        pastStreamProducers_.put(streamName, state);

                        eventStreamStateCreated.trigger(new StreamInfoAndStreamState(streamInfo, state));

                        currentStreamProducer_.recordStart();
                        eventRecordingStarted.trigger(streamInfo);

                        appState_.startRecording();
                        break;
                    }
                    case MSG_RECORD_REQUEST_STOP: {
                        if (currentStreamProducer_ != null)
                            currentStreamProducer_.recordStop();
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        Log.d(TAG, "RecorderModule constructed.");

    }

    public void recordRequestStart() {
        moduleMessageHandler_
                .obtainMessage(MSG_RECORD_REQUEST_START)
                .sendToTarget();
    }

    public void recordRequestStop() {
        moduleMessageHandler_
                .obtainMessage(MSG_RECORD_REQUEST_STOP)
                .sendToTarget();
    }


}

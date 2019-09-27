package com.example.ndnpttv2.front_end;

public class IntentInfo {

    // LoginActivity broadcast intent info
    public static int
        CHANNEL_NAME = 0,
        USER_NAME = 1,
        PRODUCER_SAMPLING_RATE = 2,
        PRODUCER_FRAMES_PER_SEGMENT = 3,
        CONSUMER_JITTER_BUFFER_SIZE = 4,
        CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS = 5,
        CONSUMER_MEDIA_DATA_TIMEOUT_MS = 6,
        CONSUMER_META_DATA_TIMEOUT_MS = 7,
        ACCESS_POINT_IP_ADDRESS = 8,
        DEBUG_LOGGING_ENABLED_SETTING = 9,
        ERROR_LOGGING_ENABLED_SETTING = 10;
    public static String
            LOGIN_CONFIG = "LOGIN_CONFIG";

    // PTTButtonPressReceiver broadcast intent info
    public static String PTTButtonPressReceiver_PTT_BUTTON_DOWN =
            "PTTButtonPressReceiver_PTT_BUTTON_DOWN";
    public static String PTTButtonPressReceiver_PTT_BUTTON_UP =
            "PTTButtonPressReceiver_PTT_BUTTON_UP";

}

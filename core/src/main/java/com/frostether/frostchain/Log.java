package com.frostether.frostchain;

public final class Log {
    private static volatile Sink sink = new Sink() {
        @Override // com.frostether.frostchain.Log.Sink
        public void log(char c, String str, String str2) {
            System.err.println(c + " " + str + ": " + str2);
        }
    };
    public static volatile boolean quiet = false;

    public interface Sink {
        void log(char c, String str, String str2);
    }

    private Log() {
    }

    public static void setSink(Sink sink2) {
        sink = sink2;
    }

    public static void i(String str, String str2) {
        if (quiet) {
            return;
        }
        sink.log('I', str, str2);
    }

    public static void w(String str, String str2) {
        sink.log('W', str, str2);
    }
}

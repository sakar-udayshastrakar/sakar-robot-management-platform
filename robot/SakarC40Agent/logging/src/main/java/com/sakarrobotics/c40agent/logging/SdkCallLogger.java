package com.sakarrobotics.c40agent.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory ring buffer of every Peanut SDK call made by the app, feeding
 * the on-screen scrolling raw SDK log. This is the only place log entries
 * are created; every module that talks to the SDK should route its call
 * bookkeeping through here rather than logging ad-hoc.
 */
public final class SdkCallLogger {

    /** Notified on the calling thread whenever a new entry is appended. */
    public interface Listener {
        void onLogEntryAdded(LogEntry entry);
    }

    private static final int MAX_ENTRIES = 500;
    private static final SdkCallLogger INSTANCE = new SdkCallLogger();

    private final List<LogEntry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private SdkCallLogger() {
    }

    public static SdkCallLogger getInstance() {
        return INSTANCE;
    }

    public synchronized void logSuccess(String api, String request, String response) {
        append(new LogEntry(System.currentTimeMillis(), api, request, response, true, 0, null));
    }

    public synchronized void logError(String api, String request, int errorCode, String errorMessage) {
        append(new LogEntry(System.currentTimeMillis(), api, request, null, false, errorCode, errorMessage));
    }

    public synchronized void log(LogEntry entry) {
        append(entry);
    }

    private void append(LogEntry entry) {
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        for (Listener listener : listeners) {
            listener.onLogEntryAdded(entry);
        }
    }

    public synchronized List<LogEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized void clear() {
        entries.clear();
    }
}

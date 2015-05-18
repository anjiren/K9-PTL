package com.handmark.pulltorefresh.library;

import android.util.Log;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by rena on 2/22/15.
 */
public class PTLLogger {
    private static String firebaseURL;
    private static Firebase rootRef;
    private static Firebase eventsRef;
    private static List<Event> pendingEvents = new LinkedList<>();
    private static String userId;
    private static String promptType = "noneyet";
    final private static String waitType = "placeholder";
    private static String exposure = "init";
    private static String itemId = "init";
    private static int numExercises;
    private static Long emailSession;
    private static Long lastPull;
    private static boolean isConnected;
    private static long emailLoadStart;
    private static long emailLoadEnd;
    private static boolean emailsLoaded = true;
    final private static String system = "pulltolearn";
    private static int currentBucket;

    private LeitnerUser user;

    static private class Event {
        private String userid;
        private String firstexposure;
        private String interactiontype;
        private String itemid;
        private int numexercises;
        private String prompttype;
        private String system;
        private String date;
        private String time;
        private Long timestamp;
        private String waittype;
        private Map details;

        public Event() {}
        public Event(String _userId, String _firstExposure, String _interactionType,
                     String _itemId, int _numExercises, String _promptType, String _system,
                     String _date, String _time, Long _timestamp,
                     String _waitType, Map _details) {
            this.userid = userId;
            this.firstexposure = _firstExposure;
            this.interactiontype = _interactionType;
            this.itemid = _itemId;
            this.numexercises = _numExercises;
            this.prompttype = _promptType;
            this.system = _system;
            this.date = _date;
            this.time = _time;
            this.timestamp = _timestamp;
            this.waittype = _waitType;
            this.details = _details;
        }
        public String getUserid() {
            return userid;
        }

        public void setUserid(String _userId) {
            userId = _userId;
        }
        public String getFirstexposure() {
            return firstexposure;
        }

        public String getInteractiontype() {
            return interactiontype;
        }

        public String getItemid() {
            return itemid;
        }

        public int getNumexercises() { return numexercises; }

        public String getPrompttype() {
            return prompttype;
        }

        public String getSystem() {
            return system;
        }

        public String getDate() {
            return date;
        }

        public String getTime() {
            return time;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public String getWaittype() {
            return waittype;
        }

        public Object getDetails() {
            return details;
        }
    }

    public PTLLogger(String _userId) {
        firebaseURL = "https://burning-fire-9367.firebaseio.com/";
        rootRef = new Firebase(firebaseURL);
        eventsRef = rootRef.child("events");
        userId = _userId;
        Firebase connectedRef = rootRef.child(".info/connected");
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                isConnected = snapshot.getValue(Boolean.class);
                Log.i("ISCONNECTED", "Firebase is connected: " + isConnected);
                if (isConnected) {
                    writePendingEvents();
                }
            }
            @Override
            public void onCancelled(FirebaseError error) {
                System.err.println("Firebase isConnected listener was cancelled.");
            }
        });
    }

    /*
    Use once Firebase is connected.
     */

    static public void updateNumExercises(int number) {
        numExercises = number;
    }

    static public void updateEmailSession() {
        Log.i("LOGGER", "updateEmailSession: from " + emailSession);
        if (emailSession == null) {
            emailSession = getCurrentTimestamp();
            updatePromptType("noneyet");
        } else {
            if (getCurrentTimestamp() - lastPull < 60000) { // If last pull was less than 30s ago...
                if (promptType == "initial" || promptType == "followup") {
                    updatePromptType("followup");
                } else {
                    updatePromptType("noneyet");
                }
            } else {
                emailSession = getCurrentTimestamp();
                updatePromptType("noneyet");
            }
        }
        Log.i("LOGGER", "updateEmailSession: " + promptType);
        Log.i("LOGGER", "updateEmailSession: to " + emailSession + "(last pull: " + lastPull + ")");
    }

    static public void updatePromptType(String prompt) {
        promptType = prompt;
    }

    public void updateState(String _exposure, String _itemId, int _bucketNum) {
        exposure = _exposure;
        itemId = _itemId;
        currentBucket = _bucketNum;
    }

    static public void updateLastPull() {
        updateEmailSession();
        lastPull = getCurrentTimestamp();
    }

    static public void setEmailLoadStart() {
        emailLoadStart = getCurrentTimestamp();
    }

    static public void setEmailLoadEnd() {
        emailLoadEnd = getCurrentTimestamp();
    }

    static public void setEmailsLoaded(boolean _emailsLoaded) {
        emailsLoaded = _emailsLoaded;
    }

    public boolean isFirstExposure() {
        return exposure.equals("first");
    }

    static public void writePendingEvents() {
        if (pendingEvents.size() > 0) {
            for (Event event : pendingEvents) {
                if (event.getUserid() == null) {
                    event.setUserid(userId);
                }
                log(event);
                Log.i("WRITING PENDING EVENT", event.toString());
            }
            pendingEvents = new LinkedList<>();
        }
    }


    static public void logPull() {
        Log.i("LOGGER", "logPull");
        Map details = new HashMap();
        details.put("emailsession", emailSession);
        details.put("emailsloaded", emailsLoaded);
        details.put("isconnected", isConnected);
        Event event = new Event(userId, exposure, "pull", itemId, numExercises, promptType,
                system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                waitType, details);
        log(event);
    }

    static public void logEmailsLoaded(int numEmails) {
        if (userId != null) {
            Log.i("LOGGER", "logEmailsLoaded");
            Map details = new HashMap();
            details.put("numEmails", numEmails);
            details.put("emailsession", emailSession);
            details.put("emailsloaded", emailsLoaded);
            details.put("isconnected", isConnected);
            Log.i("LOGGER", "email load time:" + (emailLoadEnd - emailLoadStart));
            if (emailLoadStart > 0) {
                details.put("emailloadtime", emailLoadEnd - emailLoadStart);
            }
            Event event = new Event(userId, exposure, "emailsloaded", itemId, numExercises, promptType,
                    system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                    waitType, details);
            log(event);
        }
    }

    static public void logEmailsReceived(int numEmails) {
        if (userId != null) {
            Log.i("LOGGER", "logEmailsReceived");
            Map details = new HashMap();
            details.put("numEmails", numEmails);
            details.put("isconnected", isConnected);
            Event event = new Event(userId, exposure, "emailsreceived", itemId, numExercises, promptType,
                    system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                    waitType, details);
            log(event);
        }
    }

    static public void logDebug(String debugInfo) {
        Log.i("LOGGER", "logDebug");
        Map details = new HashMap();
        details.put("emailsession", emailSession);
        details.put("emailsloaded", emailsLoaded);
        details.put("isconnected", isConnected);
        details.put("debug", debugInfo);
        Event event = new Event(userId, exposure, "emailsloaded", itemId, numExercises, promptType,
                system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                waitType, details);
        log(event);
    }

    public void logEngage() {
        Log.i("LOGGER", "logEngage");
        Map details = new HashMap();
        details.put("emailSession", emailSession);
        details.put("emailsloaded", emailsLoaded);
        details.put("isconnected", isConnected);
        Event event = new Event(userId, exposure, "engage", itemId, numExercises, promptType,
                system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                waitType, details);
        log(event);
    }

    public void logSubmit(String result) {
        if (promptType != "followup") {
            updatePromptType("initial");
        }
        Log.i("LOGGER", "logSubmit");
        Map details = new HashMap();
        details.put("emailsession", emailSession);
        details.put("emailsloaded", emailsLoaded);
        details.put("isconnected", isConnected);
        details.put("iscorrect", result);
        Event event = new Event(userId, exposure, "submit", itemId, numExercises, promptType,
                system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                waitType, details);
        log(event);
    }

    public static void logInterruptScroll() {
        Log.i("LOGGER", "logInterruptScroll");
        Map details = new HashMap();
        details.put("emailsession", emailSession);
        details.put("emailsloaded", emailsLoaded);
        details.put("isconnected", isConnected);
        Event event = new Event(userId, exposure, "interruptscroll", itemId, numExercises, promptType,
                system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                waitType, details);
        log(event);
    }

    public static void logInterruptEmailClick() {
        Log.i("LOGGER", "logInterruptScroll");
        Map details = new HashMap();
        details.put("emailsession", emailSession);
        details.put("emailsloaded", emailsLoaded);
        details.put("isconnected", isConnected);
        Event event = new Event(userId, exposure, "interruptemailclick", itemId, numExercises, promptType,
                system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                waitType, details);
        log(event);
    }

    public static void logOnForeground() {
        if (userId != null) {
            Log.i("LOGGER", "logInterruptScroll");
            Map details = new HashMap();
            details.put("emailsession", emailSession);
            details.put("emailsloaded", emailsLoaded);
            details.put("isconnected", isConnected);
            Event event = new Event(userId, exposure, "onforeground", itemId, numExercises, promptType,
                    system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                    waitType, details);
            log(event);
        }
    }

    public static void logOnBackground() {
        Log.i("LOGGER", "logInterruptScroll");
        Map details = new HashMap();
        details.put("emailsession", emailSession);
        details.put("emailsloaded", emailsLoaded);
        details.put("isconnected", isConnected);
        Event event = new Event(userId, exposure, "onbackground", itemId, numExercises, promptType,
                system, getCurrentDate(), getCurrentTime(), getCurrentTimestamp(),
                waitType, details);
        log(event);
    }

    static public void log(Event event) {
        if (event.getPrompttype() != null) {
            Log.i("LOGGER", "log: prompttype: "+ event.getPrompttype());
        }
        if (isConnected) {
            if (event.getUserid() == null) {
                pendingEvents.add(event);
            } else {
                Firebase eventRef = eventsRef.push();
                eventRef.setValue(event);
                Log.i("LOGGER", "log: "+ event.getUserid());
                Log.i("LOGGER", "log: "+ eventRef.getKey());
            }
        } else {
            pendingEvents.add(event);
        }
    }

    /*
    Returns current date in MM/DD/YYYY format.
     */
    static public String getCurrentDate() {
        Calendar current = Calendar.getInstance();
        String MM;
        String DD;
        String YYYY = "" + current.get(Calendar.YEAR);
        int month = current.get(Calendar.MONTH) + 1;
        int day = current.get(Calendar.DAY_OF_MONTH);
        if (month < 10) {
            MM = "0" + month;
        } else {
            MM =  "" + month;
        }

        if (day < 10) {
            DD = "0" + day;
        } else {
            DD =  "" + day;
        }
        Log.i("LOGGER", "current date: " + MM + "/" + DD + "/" + YYYY);
        return MM + "/" + DD + "/" + YYYY;
    }

    /*
    Returns current time in HH:MM:SS format.
     */
    static public String getCurrentTime() {
        Calendar current = Calendar.getInstance();
        String HH;
        String MM;
        String SS;
        int hour = current.get(Calendar.HOUR_OF_DAY);
        int minute = current.get(Calendar.MINUTE);
        int second = current.get(Calendar.SECOND);
        if (hour < 10) {
            HH = "0" + hour;
        } else {
            HH =  "" + hour;
        }

        if (minute < 10) {
            MM = "0" + minute;
        } else {
            MM =  "" + minute;
        }

        if (second < 10) {
            SS = "0" + second;
        } else {
            SS =  "" + second;
        }
        Log.i("LOGGER", "current time:" + HH + ":" + MM + ":" + SS);
        return HH + ":" + MM + ":" + SS;
    }

    static public Long getCurrentTimestamp() {
        Calendar current = Calendar.getInstance();
        return current.getTimeInMillis();
    }
}

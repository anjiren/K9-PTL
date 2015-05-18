package com.handmark.pulltorefresh.library; /**
 * Created by Anji Ren on 2/5/15.
 */

import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.MutableData;
import com.firebase.client.Query;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Transaction;
import com.firebase.client.ValueEventListener;

public class Leitner {

    static int maxBinNo;
    static int numAliveLItems;
    static int maxCards;
    static int numExercises;

    // Firebase references.
    static Firebase rootRef;
    static Firebase userRef;
    static Firebase itemsRef;

    static Map<String, Item> items;
    static ArrayList<Item> itemsArray = new ArrayList<>();
    static Map<String, Map<String, LeitnerItem>> lItems = new HashMap<>();
    static Map<String, Boolean> lSessionItems = new HashMap<>();
    static int lSessionNum;

    static Map<String, LeitnerItem> aliveLItems = new HashMap<>();
    static Map<String, LeitnerItem> retiredLItems = new HashMap<>();

    // Current Leitner item information.
    static int currBucket;
    static String currLItemId;

    // Asynchronous management.
    static String userId;

    static LeitnerUser user;

    static boolean initializing;
    static boolean isConnected;

    // CHECK CAPS!

    static class LeitnerItem {
        private int bucket;
        private int scheduledsession;
        public LeitnerItem() {}

        public LeitnerItem(int _bucket, int _scheduledsession) {
            this.bucket = _bucket;
            this.scheduledsession = _scheduledsession;
        }

        public int getBucket() { return bucket; }

        public void setBucket(int newBucket) { bucket = newBucket; }

        public int getScheduledsession() { return scheduledsession; }

        public void setScheduledsession(int newSessionNum) {
            scheduledsession = newSessionNum;
        }
    }

    static class Item {
        private String l1;
        private String l2;
        private int order;
        private String id;
        public Item() {}

        public Item(String _l1, String _l2, int _order) {
            this.l1 = _l1;
            this.l2 = _l2;
            this.order = _order;
        }

        public String getL1() {
            return l1;
        }

        public String getL2() { return l2; }

        public void setId(String _id) { id = _id; }

        public String getId() { return id; }

        public int getOrder() {
            return order;
        }
    }

    static ICallback onDoneInitializing;
    static ICallback onGotNextItem;
    final static ICallback onFetchFirebaseUserDataCallback = new FetchFirebaseUserDataCallback();

    private static class FetchFirebaseUserDataCallback implements ICallback {
        @Override
        public void callback(String itemId) {
            Log.e("FLASHCARD FINISHED", itemId);
            if (newSlotsAvailable()) {
                Log.i("GET NEXT ITEM", "Get new item!");
                getNewItem();
            } else {
                Log.i("GET NEXT ITEM", "Get old item!!");
                getOldItem();
            }
            onGotNextItem.callback("DONE");
        }
    }

    /*
     */
    public Leitner(String _userId, LeitnerUser _user, final ICallback _ic) {
        initializing = true;
        Log.i("LEITNER INIT", "Initializing...");
        userId = _userId;
        user = _user;
        maxBinNo = 5;
        maxCards = 7;
        onDoneInitializing = _ic;

        // Set Firebase references.
        rootRef = new Firebase("https://burning-fire-9367.firebaseio.com/");
        userRef = rootRef.child("users/" + _userId);
        itemsRef = rootRef.child("items/" + user.getLanguage());

        // Load in items.
        Query itemsQuery = itemsRef.orderByKey();
        itemsQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (items == null) {
                    items = new HashMap<>();
                }
                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    Item item = itemSnapshot.getValue(Item.class);
                    item.setId(itemSnapshot.getKey());
                    items.put(item.id, item);
                    itemsArray.add(item);
                }
                onDoneInitializing.callback(userId);
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
            }
        });

        Firebase connectedRef = rootRef.child(".info/connected");
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                isConnected = snapshot.getValue(Boolean.class);
                Log.i("ISCONNECTED", "Firebase is connected: " + isConnected);
            }
            @Override
            public void onCancelled(FirebaseError error) {
                System.err.println("Firebase isConnected listener was cancelled.");
            }
        });
    }

    /*
    Returns true if item has never been completed.
     */
    public boolean isFirstExposure() {
        if (aliveLItems.containsKey(currLItemId)) {
            int currBucket = aliveLItems.get(currLItemId).getBucket();
            Log.i("IS_FIRST_EXPOSURE", "Bucket #" + currBucket);
            return currBucket == 0;
        }
        return false;
    }

    public void onAlreadyKnew(ICallback postTransactionCallback) {
        // Change bucket to alreadyKnew and move from alive to retired.
        LeitnerItem lItem = aliveLItems.get(currLItemId);
        lItem.setBucket(-1);
        retiredLItems.put(currLItemId, lItem);
        aliveLItems.remove(currLItemId);
        removeFromSession();
        onFlashcardFinished(postTransactionCallback);
    }

    public static boolean newSlotsAvailable() {
        return aliveLItems.size() < maxCards;
    }

    public boolean hasItem(String itemId) {
        return (aliveLItems != null && aliveLItems.containsKey(itemId) ||
                retiredLItems != null && retiredLItems.containsKey(itemId));
    }

    public boolean isCurrentlyLearning(String itemId) {
        return (aliveLItems != null && (aliveLItems.containsKey(itemId)));
    }

    /*
    Core method called by the view.
    Fetches and returns the next item according to the Leitner algorithm.
     */
    public static void getNextItem(ICallback callback) {
        onGotNextItem = callback;
        Log.e("HUH", "WeT");
        fetchFirebaseUserData();
    }

    /*
    Fetches a new item that has never before been entered into the Leitner algorithm.
     */
    public static void getNewItem() {
        String newItemId = extractItem();
        if (newItemId != null) {
            currLItemId = makeLeitnerItem(newItemId);
        }
        Log.i("GET NEW ITEM", "New current LItemID: " + currLItemId);
    }

    public static  void getOldItem() {
        for (int i = 0; i < maxBinNo; i++) {
            if (isEmptySession()) {
                newSession();
            } else {
                currLItemId = getNextSessionItem();
                return;
            }
        }
        Log.e("GET OLD ITEM", "NO FLASHCARDS AFTER TRYING MANY TIMES!");

    }

    /* Returns the first item in current session without removing it.
     */
    public static String getNextSessionItem() {
        ArrayList<String> sessionItemIds = new ArrayList<String>();
        for (String lSessionItemId : lSessionItems.keySet()) {
            sessionItemIds.add(lSessionItemId);
        }
        Log.i("GET NEXT SESSION", sessionItemIds.toString());
        if (sessionItemIds.size() == 0) {
            Log.e("GET NEXT SESSION", "Should get next flashcard but session is empty!");
        }
        String lSessionItemFound = sessionItemIds.get(0);
        Log.i("GET NEXT SESSION", "lSessionItemFound: " + lSessionItemFound);
        return lSessionItemFound;
    }

    public static void fetchFirebaseUserData() {
        Log.e("HUH", "wat");
        if (isConnected) {
            Log.e("HUH", "HERE???");
            userRef.addListenerForSingleValueEvent(new ValueEventListener(){
                @Override
                public void onDataChange(DataSnapshot userSnapshot) {
                    aliveLItems = new HashMap<>();
                    retiredLItems = new HashMap<>();
                    if (userSnapshot.hasChild("litems")) {
                        if (userSnapshot.child("litems").hasChild("alive")) {
                            for (DataSnapshot lItemSnapshot :
                                    userSnapshot.child("litems/alive").getChildren()) {
                                Log.i("FLASHCARD FINISHED", ""+lItemSnapshot.getKey());
                                aliveLItems.put(lItemSnapshot.getKey(),
                                        lItemSnapshot.getValue(LeitnerItem.class));
                            };
                        }

                        if (userSnapshot.child("litems").hasChild("retired")) {
                            for (DataSnapshot lItemSnapshot :
                                    userSnapshot.child("litems/retired").getChildren()) {
                                retiredLItems.put(lItemSnapshot.getKey(),
                                        lItemSnapshot.getValue(LeitnerItem.class));
                            };
                        }
                    }

                    lSessionItems = new HashMap<>();
                    if (userSnapshot.hasChild("lsessitems")) {
                        for (DataSnapshot lItemSnapshot :
                                userSnapshot.child("lsessitems").getChildren()) {
                            lSessionItems.put(lItemSnapshot.getKey(),
                                    lItemSnapshot.getValue(Boolean.class));
                        }
                    }
                    Log.e("DEBUG", userSnapshot.child("numexercises").getValue(Integer.class).toString());
                    if (userSnapshot.hasChild("numexercises")) {
                        numExercises = userSnapshot.child("numexercises").getValue(Integer.class);
                        Log.i("FLASHCARD FINISHED", "sever numex: "
                                + userSnapshot.child("numexercises").getValue(Integer.class));
                        Log.i("FLASHCARD FINISHED", "numexercises: " + numExercises);
                    } else {
                        numExercises = 0;
                    }
                    PTLLogger.updateNumExercises(numExercises);

                    if (userSnapshot.hasChild("lsessnum")) {
                        lSessionNum = userSnapshot.child("lsessnum").getValue(Integer.class);
                        Log.i("FLASHCARD FINISHED", "lsessnum: " + lSessionNum);
                    } else {
                        lSessionNum = 0;
                    }
                    numAliveLItems = aliveLItems.size();
                    onFetchFirebaseUserDataCallback.callback("DONE");
                }

                @Override
                public void onCancelled(FirebaseError firebaseError) {
                }
            });
        } else {
            Log.e("HUH", "ORRR HERE???");
            onFetchFirebaseUserDataCallback.callback("FAILED!");
        }
    }

    public void onFlashcardFinished(final ICallback postTransactionCallback) {
        Log.i("FLASHCARD FINISHED", "Flashcard finished.");
        numExercises = numExercises + 1;

        // Write back to Firebase as a transaction.
        userRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                Log.e("THIS TRANSACTION", "TRANSACTION");
                if (currentData.getValue() == null) {
                    Log.i("FLASHCARD FINISHED", "Server is null, so client overrides server.");
                    // Client overrides server.
                    lItems.put("alive", aliveLItems);
                    lItems.put("retired", retiredLItems);
                    Log.e("DATA", "LITEMS: "+ lItems.toString());
                    Log.e("DATA", "ALIVELITEMS: "+ aliveLItems.toString());
                    Log.e("DATA", "RETIREDLITEMS: "+ retiredLItems.toString());
                    Log.e("DATA", "LSESSITEMS: "+ lSessionItems.toString());
                    currentData.child("litems").setValue(lItems);
                    currentData.child("lsessitems").setValue(lSessionItems);
                    currentData.child("lsessnum").setValue(lSessionNum);
                    Log.e("DATA", "NUMEXERCISES: " + numExercises);
                    currentData.child("numexercises").setValue(numExercises);
                } else {
                    // Check numExercises.
                    if (numExercises >=
                            currentData.child("numexercises").getValue(Integer.class)) {
                        // Client overrides server.
                        Log.i("FLASHCARD FINISHED",
                                "Client ahead of server, so client should override server.");
                        lItems.put("alive", aliveLItems);
                        lItems.put("retired", retiredLItems);
                        Log.e("DATA", "LITEMS: "+ lItems.toString());
                        Log.e("DATA", "ALIVELITEMS: "+ aliveLItems.toString());
                        Log.e("DATA", "RETIREDLITEMS: "+ retiredLItems.toString());
                        Log.e("DATA", "LSESSITEMS: "+ lSessionItems.toString());
                        currentData.child("litems").setValue(lItems);
                        currentData.child("lsessitems").setValue(lSessionItems);
                        currentData.child("lsessnum").setValue(lSessionNum);
                        Log.e("DATA", "NUMEXERCISES: " + numExercises);
                        currentData.child("numexercises").setValue(numExercises);
                    } else {
                        // Server overrides client.
                        Log.i("FLASHCARD FINISHED",
                                "Client behind server, so server overrides client.");
                        Log.e("FLASHCARD FINISHED", "!!NUMEXERCISES: " + numExercises);
                        Log.e("FLASHCARD FINISHED", "!!NUMEXERCISES at serv: "
                                + currentData.child("numexercises").getValue());
                        // OVERRIDE LOCAL!!! TODO!!!
                    }

                }
                if (!isConnected) {
                    postTransactionCallback.callback("rawr");
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(FirebaseError firebaseError, boolean committed, DataSnapshot currentData) {
                Log.i("FLASHCARD FINISHED", "transaction oncomplete");
                postTransactionCallback.callback("???");
            }
        });
    }
    /*
    Removes the current Leitner item from the current session.
    This should only be called once the user has responded to the exercise (ie. pressed "got it
    right"/"got it wrong"/"already knew").
     */
    public void removeFromSession() {
        Log.i("REMOVE FROM SESSION", "Removing current LItem:"+ currLItemId +" from session.");
        lSessionItems.remove(currLItemId);
    }

    public static Boolean isEmptySession() {
        return (lSessionItems.size() == 0);
    }

    /*
    Creates a new session, populating it with the corresponding Leitner items that were
    scheduled for this session number.
     */
    static void newSession() {
        lSessionNum = lSessionNum + 1;
        for (String lItemId : aliveLItems.keySet()) {
            if (aliveLItems.get(lItemId).getScheduledsession() == lSessionNum) {
                // Add lItem to current session.
                lSessionItems.put(lItemId, true);
            }
        }
    }

    public static String makeLeitnerItem(String itemId) {
        LeitnerItem lItem = new LeitnerItem(0, lSessionNum + 1);
        aliveLItems.put(itemId, lItem);
        return itemId;
    }

    /* Walks through the list of stock items until we find one the user doesn't already have.
     */
    public static String extractItem() {
        String itemIdFound = null;

        for (Item item : itemsArray) {
            if (!hasAliveLItem(item) && !hasRetiredLItem(item)) {
                itemIdFound = item.getId();
                break;
            }
        }

        if (itemIdFound == null) {
            Log.e("EXTRACT ITEM", "NO UNSEEN ITEM FOUND?");
        }
        return itemIdFound;
    }

    /*
    Checks if considered item is alive.
     */
    public static boolean hasAliveLItem(Item item) {
        return aliveLItems.containsKey(item.getId());
    };

    /*
    Checks if considered item is alive.
     */
    public static boolean hasRetiredLItem(Item item) {
        return retiredLItems.containsKey(item.getId());
    };

    public void promote(ICallback postTransactionCallback) {
        // Update bucket.
        int oldBucket = aliveLItems.get(currLItemId).getBucket();
        int newBucket = oldBucket + 1;
        aliveLItems.get(currLItemId).setBucket(newBucket);

        // Update session.
        int newSessionNum = lSessionNum + newBucket;
        aliveLItems.get(currLItemId).setScheduledsession(newSessionNum);
        Log.i("PROMOTE", "Promote from current bucket #" + oldBucket + " to session: "
                + newSessionNum);

        // Flashcard learned!
        if (newBucket == maxBinNo) {
            Log.i("PROMOTE", "Flashcard learned!");
            LeitnerItem lItem = aliveLItems.get(currLItemId);
            retiredLItems.put(currLItemId, lItem);
            aliveLItems.remove(currLItemId);
        }

        removeFromSession();
        onFlashcardFinished(postTransactionCallback);
    }

    public void demote(ICallback postTransactionCallback) {
        // Update bucket.
        int oldBucket = aliveLItems.get(currLItemId).getBucket();
        int newBucket = Math.max(oldBucket - 1, 1);
        aliveLItems.get(currLItemId).setBucket(newBucket);

        // Update session.
        int newSessionNum = lSessionNum + newBucket;
        aliveLItems.get(currLItemId).setScheduledsession(newSessionNum);
        Log.i("DEMOTE", "Demote from current bucket #" + currBucket + " to session: "
                + newSessionNum);

        removeFromSession();
        onFlashcardFinished(postTransactionCallback);
    }

    public String getItemId() {
        return currLItemId;
    }

    public String getL1() {
        if (items.containsKey(currLItemId)) {
            return items.get(currLItemId).getL1();
        } else {
            PTLLogger.logDebug("getL1failed");
            return null;
        }
    }

    public String getL2() {
        if (items.containsKey(currLItemId)) {
            return items.get(currLItemId).getL2();
        } else {
            PTLLogger.logDebug("getL2failed");
            return null;
        }
    }

    public String getPrompt() {
        if (atEasyLevel() && !isFirstExposure()) {
            return getL2();
        } else {
            return getL1();
        }
    }

    public String getTarget() {
        if (atEasyLevel() && !isFirstExposure()) {
            return getL1();
        } else {
            return getL2();
        }
    }

    public int getCurrentBucket() {
        return aliveLItems.get(currLItemId).getBucket();
    }

    public boolean atEasyLevel() {
        if (aliveLItems.containsKey(currLItemId)) {
            return aliveLItems.get(currLItemId).getBucket() < 3;
        }
        return true;
    }

}
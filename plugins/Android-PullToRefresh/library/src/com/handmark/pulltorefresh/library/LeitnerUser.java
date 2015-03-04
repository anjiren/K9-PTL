package com.handmark.pulltorefresh.library; /**
 * Created by Anji Ren on 2/6/15.
 */

import android.content.Context;
import com.firebase.client.Firebase;
import com.firebase.client.DataSnapshot;

public class LeitnerUser {

    String userId;
    String floor;
    String email;
    String language;

    public LeitnerUser(DataSnapshot snapshot) {
        this.userId = snapshot.getKey();
        this.floor = snapshot.child("floor").getValue(String.class);
        this.email = snapshot.child("email").getValue(String.class);
        this.language = snapshot.child("language").getValue(String.class);
    }

    public String getFloor() {
        return floor;
    }

    public String getLanguage() {
        return language;
    }
}

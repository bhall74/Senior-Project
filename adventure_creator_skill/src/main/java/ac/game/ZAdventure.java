package ac.game;

import com.google.firebase.database.Exclude;

import java.util.ArrayList;

/**
 * Created by siatk on 3/3/2018.
 */

//storage class for an Adventure in Firebase
public class ZAdventure {
    public String userid;
    public String adventureKey; //for convenience - the String under which it is stored in Firebase
    public String adventureName;
    public String adventureDescription;
    public long dateModified;    //long currDate = System.currentTimeMillis();     //use mDate = Date(currDate) to get it back.
    public int adventureType;

    public int eventCounter = 0;


    public ArrayList<ZEvent> events;


    public ArrayList<ZEvent> getEvents() {
        return events;
    }

    public void setEvents(ArrayList<ZEvent> events) {
        this.events = events;
    }


    public ZAdventure() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    public ZAdventure(String userid, String adventureName, String adventureDescription, String adventureKey, int adventureType) {
        this.userid = userid;
        this.adventureKey = adventureKey;
        this.adventureName = adventureName;
        this.adventureDescription = adventureDescription;
        this.dateModified = System.currentTimeMillis();
        this.adventureType = adventureType;

        events = new ArrayList<>();
    }

    @Exclude
    public ZEvent AddNewEvent(String title, String description) {
        ++eventCounter;  //start at one
        ZEvent newEvent = new ZEvent(title, description, eventCounter);
        events.add(newEvent);
        return newEvent;
    }

    @Exclude
    public ZEvent getEventFromEventListUsingEventId(int id) {
        for (ZEvent zEvent : events) {
            if (zEvent.eventId == id) {
                return zEvent;
            }
        }
        return null;
    }

}

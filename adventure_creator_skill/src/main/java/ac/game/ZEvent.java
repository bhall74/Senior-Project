package ac.game;

import com.google.firebase.database.Exclude;
import java.util.ArrayList;

/**
 * Created by siatk on 3/6/2018.
 */

public class ZEvent {

    int eventId;                //1, 2, 3, 4.     0 = starting node.
    //String ZEventKey;           //for convenience - the entry Key in Firebase

    String title;           //optional event title

    String description;
    int eventType = 0;          //default, basic ZEvent

    String monsterName;
    String weaponName;
    String monsterPronoun; //its, his, her, their
    int monsterHealth;
    int minDamage;
    int maxDamage;

    //String prevEventKey;          //to go back in the game
    ArrayList<Integer> prevEventIds;     //which Nodes call this node?  when a node gets deleted, we also need to remove references to it from calling nodes.
    ArrayList<String> nextActions;       //e.g. "go left", "go right", "go center"
    ArrayList<Integer> nextEventIds;     //e.g. leftNode, rightNode, centerNode


    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getEventType() {
        return eventType;
    }

    public void setEventType(int eventType) {
        this.eventType = eventType;
    }

    public String getMonsterName() {
        return monsterName;
    }

    public void setMonsterName(String monsterName) {
        this.monsterName = monsterName;
    }

    public String getWeaponName() {
        return weaponName;
    }

    public void setWeaponName(String weaponName) {
        this.weaponName = weaponName;
    }

    public String getMonsterPronoun() {
        return monsterPronoun;
    }

    public void setMonsterPronoun(String monsterPronoun) {
        this.monsterPronoun = monsterPronoun;
    }

    public int getMonsterHealth() {
        return monsterHealth;
    }

    public void setMonsterHealth(int monsterHealth) {
        this.monsterHealth = monsterHealth;
    }

    public int getMinDamage() {
        return minDamage;
    }

    public void setMinDamage(int minDamage) {
        this.minDamage = minDamage;
    }

    public int getMaxDamage() {
        return maxDamage;
    }

    public void setMaxDamage(int maxDamage) {
        this.maxDamage = maxDamage;
    }

    public ArrayList<Integer> getPrevEventIds() {
        return prevEventIds;
    }
    public void setPrevEventIds(ArrayList<Integer> prevEventIds) {
        this.prevEventIds = prevEventIds;
    }
    public ArrayList<String> getNextActions() {
        return nextActions;
    }
    public void setNextActions(ArrayList<String> nextActions) {
        this.nextActions = nextActions;
    }

    public ArrayList<Integer> getNextEventIds() {
        return nextEventIds;
    }
    public void setNextEventIds(ArrayList<Integer> nextEventIds) {
        this.nextEventIds = nextEventIds;
    }


    //int level = 0;    // level only makes sense in the context of TreeAdventure -
                        // where you can only add to layer immediately below current level

    public ZEvent () {
        nextActions = new ArrayList<>();
        nextEventIds = new ArrayList<>();
        prevEventIds = new ArrayList<>();
    }

/*    public ZEvent(String zEventKey, String title, String description) {
        ++eventIdCounter;
        eventId = eventIdCounter;           //start at 1.
        this.ZEventKey = zEventKey;
        this.description = description;
    }*/

    public ZEvent(String title, String description, int eventId) {
        this.eventId = eventId;           //start at 1.
        this.title = title;
        this.description = description;

        nextActions = new ArrayList<>();
        nextEventIds = new ArrayList<>();
        prevEventIds = new ArrayList<>();
    }



    @Exclude
    public int getListIndexFromChildEventId(int childEventId) {
        for (int i = 0; i < nextEventIds.size(); i++) {
            if (nextEventIds.get(i) == childEventId) {
                return i;
            }
        }
        return -1;
    }

    @Exclude
    public int getListIndexFromPreviousEventId(int previousEventId) {
        for (int i = 0; i < prevEventIds.size(); i++) {
            if (prevEventIds.get(i) == previousEventId) {
                return i;
            }
        }
        return -1;
    }

    @Exclude
    public String getTriggerWordsFromChildEventId(int childEventId) {
        for (int i = 0; i < nextEventIds.size(); i++) {
            if (nextEventIds.get(i) == childEventId) {
                return nextActions.get(i);
            }
        }
        return null;
    }

}

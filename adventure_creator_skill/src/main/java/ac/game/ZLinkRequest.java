package ac.game;

/**
 * Created by siatk on 4/17/2018.
 */

public class ZLinkRequest {

	// https://stackoverflow.com/questions/36658833/firebase-servervalue-timestamp-in-java-data-models-objects
	// but just going to grab server timestamp from amazon instead. easier.

	public String amazonUserID; // session.getUser().getUserID()
	public String pin; // "0001"-"9999"
	public long timeStamp;

	public ZLinkRequest() {
	}

	public ZLinkRequest(String amazonUserID, String pin, long timeStamp) {
		this.amazonUserID = amazonUserID;
		this.pin = pin;
		this.timeStamp = timeStamp;
	}
}

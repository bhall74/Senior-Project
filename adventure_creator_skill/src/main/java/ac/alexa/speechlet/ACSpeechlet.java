package ac.alexa.speechlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
/**
Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.Context;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.SpeechletV2;
import com.amazon.speech.speechlet.interfaces.system.SystemInterface;
import com.amazon.speech.speechlet.interfaces.system.SystemState;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazon.speech.ui.SsmlOutputSpeech;
import com.amazon.speech.ui.LinkAccountCard;
import com.amazon.speech.ui.OutputSpeech;
import com.amazon.speech.speechlet.services.DirectiveEnvelope;
import com.amazon.speech.speechlet.services.DirectiveEnvelopeHeader;
import com.amazon.speech.speechlet.services.DirectiveService;
import com.amazon.speech.speechlet.services.SpeakDirective;

//Firebase
//import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseApp;
import com.google.auth.oauth2.GoogleCredentials;

//other imports
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.HttpsURLConnection;
import ac.game.*;

/**
 * Adventure Creator Skill Queries game data from Firebase and builds Intents
 */

public class ACSpeechlet implements SpeechletV2 {

	private static final Logger log = LoggerFactory.getLogger(ACSpeechlet.class);

	/**
	 * Service to send progressive response directives.
	 */
	private DirectiveService directiveService;

	private boolean loginSuccess = false;

	private static final String GAMENUMBER_SLOT = "gameNumber";
	private static final String USERACTION_SLOT = "userAction";

	// session attributes
	private static final String FIREBASE_LINK_INFO_RETRIEVED = "accountLinkRetrieved";
	private static final String FIREBASE_USERID = "firebaseUserId";
	private static final String LINK_PIN = "linkPin";
	private static final String WAITING_FOR_LINK_ACCOUNT = "waitingForLinkAccount";

	private static final String GAMES_RETRIEVED = "gamesListed"; // true if games retrieved
	private static final String CURRENT_GAME = "currentGame"; // -1 if no current game.
	private static final String CURRENT_NODE = "currentNode"; // 0 is the starting node
	private static final String IN_COMBAT = "combatStarted";
	private static final String PLAYER_HEALTH = "playerHealth";
	private static final String MONSTER_HEALTH = "monsterHealth";

	// store last speech information, so we can repeat it
	private static final String LAST_SPEECH = "lastSpeech";
	private static final String LAST_REPROMPT = "lastReprompt";
	private static final String LAST_SPEECH_IS_SSML = "lastSpeechSSML";
	private static final String LAST_REPROMPT_IS_SSML = "lastRepromptSSML";

	private static final int BASIC_EVENT = 0;
	private static final int MONSTER_EVENT = 1;

	private static final int SIMPLE_ADVENTURE = 0;
	private static final int FIGHTY_ADVENTURE = 1;

	private static HashMap<String, ArrayList<ZAdventure>> gameData = new HashMap<String, ArrayList<ZAdventure>>();

	private static final String WELCOMESTRING = "<speak>Welcome to Adventure Creator, the app that let's you create and play your own choose your own adventure stories. <audio src='https://s3.amazonaws.com/ask-soundlibrary/magic/amzn_sfx_fairy_melodic_chimes_01.mp3'/></speak>";

	private static final String THANKYOUFORPLAYING_STRING = "Thank you for playing!  You can say list to list your games, or say play game number, to play another game, or say bye, to leave Adventure Creator.";

	/**
	 * Constructs an instance of {@link ACSpeechlet}.
	 *
	 * @param directiveService
	 *            implementation of directive service
	 */
	public ACSpeechlet(DirectiveService directiveService) {
		this.directiveService = directiveService;

		// login to firebase
		try {
			// Fetch the service account key JSON file contents
			FileInputStream serviceAccount = new FileInputStream("ACServiceAccount.json");

			// Initialize the app with a service account, granting admin privileges
			FirebaseOptions options = new FirebaseOptions.Builder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.setDatabaseUrl("https://alexa-adventure-creator-ca7c9.firebaseio.com/").build();

			FirebaseApp.initializeApp(options);
			loginSuccess = true;
		} catch (IOException ioe) {
			System.out.println("Error connecting to firebase with credentials");
		}
	}

	@Override
	public void onSessionStarted(SpeechletRequestEnvelope<SessionStartedRequest> requestEnvelope) {
		log.info("onSessionStarted requestId={}, sessionId={}", requestEnvelope.getRequest().getRequestId(),
				requestEnvelope.getSession().getSessionId());

		// any initialization logic goes here
		Session session = requestEnvelope.getSession();
		session.setAttribute(FIREBASE_LINK_INFO_RETRIEVED, false);
		session.setAttribute(FIREBASE_USERID, "");
		session.setAttribute(LINK_PIN, 0);
		session.setAttribute(WAITING_FOR_LINK_ACCOUNT, false);
		session.setAttribute(GAMES_RETRIEVED, false);
		session.setAttribute(CURRENT_GAME, -1);
		session.setAttribute(CURRENT_NODE, -1);
		session.setAttribute(IN_COMBAT, false);

		session.setAttribute(LAST_SPEECH, "Nothing to repeat.");
		session.setAttribute(LAST_REPROMPT, "Nothing to repeat.");
		session.setAttribute(LAST_SPEECH_IS_SSML, false);
		session.setAttribute(LAST_REPROMPT_IS_SSML, false);
	}

	@Override
	public SpeechletResponse onLaunch(SpeechletRequestEnvelope<LaunchRequest> requestEnvelope) {
		log.info("onLaunch requestId={}, sessionId={}", requestEnvelope.getRequest().getRequestId(),
				requestEnvelope.getSession().getSessionId());
		Session session = requestEnvelope.getSession();

		if (!loginSuccess) {
			PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
			outputSpeech.setText("Login unsuccessful.  Please try again later, goodbye!");
			return SpeechletResponse.newTellResponse(outputSpeech);
		}

		// It is an issue with the simulator that the progressive message comes in about
		// the same time as the real response (just a few mill seconds apart)
		SystemState systemState = getSystemState(requestEnvelope.getContext());
		String apiEndpoint = systemState.getApiEndpoint();
		// Dispatch a progressive response to engage the user while reading from
		// Firebase
		dispatchProgressiveResponse(requestEnvelope.getRequest().getRequestId(), WELCOMESTRING, systemState,
				apiEndpoint);

		// check: is the account linked?
		boolean isLinked = checkIsAccountLinkedWithFirebase(session);

		if (!isLinked) {
			// generate random PIN
			Random rand = new Random();
			int linkPIN = rand.nextInt(9999) + 1; // 1-9999
			String linkPINasString = String.format("%04d", linkPIN);
			session.setAttribute(LINK_PIN, linkPIN);
			String amazonUserID = session.getUser().getUserId();
			String amazonUserIDKey = amazonUserID.replaceAll("[^a-zA-Z0-9 ]", ""); // remove punctuation - not allowed
																					// in a firebase key
			Date serverDate = new Date();
			ZLinkRequest newLinkRequest = new ZLinkRequest(amazonUserID, linkPINasString, serverDate.getTime());

			CountDownLatch countDownLatch = new CountDownLatch(1);
			FirebaseDatabase.getInstance().getReference().child("linkRequest").child(amazonUserIDKey)
					.setValue(newLinkRequest, new DatabaseReference.CompletionListener() {
						@Override
						public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
							if (databaseError != null) {
								System.out.println("Data could not be saved " + databaseError.getMessage());
								countDownLatch.countDown();
							} else {
								System.out.println("Data saved successfully.");
								countDownLatch.countDown();
							}
						}
					});
			waitForCountdownLatch(countDownLatch);

			return getPleaseLinkAccountResponse(session, false);
		} else {
			return getAskResponse(session, "You can say list to list my games.");
		}
	}

	private SpeechletResponse getPleaseLinkAccountResponse(Session session, boolean thereWasAProblem) {
		session.setAttribute(WAITING_FOR_LINK_ACCOUNT, true);

		int linkPIN = (int) session.getAttribute(LINK_PIN);

		int digit1 = linkPIN / 1000;
		int remainder = linkPIN - digit1 * 1000;
		int digit2 = remainder / 100;
		remainder -= digit2 * 100;
		int digit3 = remainder / 10;
		remainder -= digit3 * 10;
		int digit4 = remainder;

		StringBuilder requestLinkStr = new StringBuilder().append("<speak>");

		if (thereWasAProblem) {
			requestLinkStr
					.append("Uh oh, that didn't work.  Are you sure you entered the right code? <break time=\".5s\"/>");
		} else {
			requestLinkStr.append("First, we need to link your account.  ");
		}

		requestLinkStr.append(
				"Open the Android app, and go to the Link Amazon Account screen. Enter the following four digits: ")
				.append("<break time=\".5s\"/>").append("<say-as interpret-as=\"digits\">" + linkPIN + "</say-as>.")
				.append("<break time=\".5s\"/>").append("The number again, is, ").append(Integer.toString(digit1))
				.append("<break time=\".7s\"/>").append(Integer.toString(digit2)).append("<break time=\".7s\"/>")
				.append(Integer.toString(digit3)).append("<break time=\".7s\"/>").append(Integer.toString(digit4))
				.append("<break time=\".7s\"/>").append("When you are done, say, Link Account.").append("</speak>");
		String repromptString = "<speak>Open the Android app, and go to the Link Amazon Account screen. Enter the following four digits:"
				+ "<say-as interpret-as=\"digits\">" + linkPIN + "</say-as>" + "and then say, Link Account."
				+ "</speak>";
		return newAskResponse(session, requestLinkStr.toString(), true, repromptString, true);
	}

	@Override
	public SpeechletResponse onIntent(SpeechletRequestEnvelope<IntentRequest> requestEnvelope) {
		IntentRequest request = requestEnvelope.getRequest();
		log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
				requestEnvelope.getSession().getSessionId());

		Intent intent = request.getIntent();
		String intentName = (intent != null) ? intent.getName() : null;
		if (intentName != null) {
			System.out.println("current intent: " + intentName);
		}
		Session session = requestEnvelope.getSession();

		// we must check the firebase link info, before doing anything else.
		if (!(boolean) session.getAttribute(FIREBASE_LINK_INFO_RETRIEVED)) {
			SystemState systemState = getSystemState(requestEnvelope.getContext());
			String apiEndpoint = systemState.getApiEndpoint();
			// Dispatch a progressive response to engage the user while reading from
			// Firebase
			dispatchProgressiveResponse(requestEnvelope.getRequest().getRequestId(), WELCOMESTRING, systemState,
					apiEndpoint);
			checkIsAccountLinkedWithFirebase(session);
		}

		if ((boolean) session.getAttribute(WAITING_FOR_LINK_ACCOUNT)) {
			// user was asked to enter a linked account. lets check it
			checkIsAccountLinkedWithFirebase(session);
			if (((String) session.getAttribute(FIREBASE_USERID)).equals("")) {
				// the user was asked to enter a pin code, but it didn't work, so ask them to
				// try linking again.
				return getPleaseLinkAccountResponse(session, true);
			}
		}

		// do NOT proceed until we have a linked account.
		if (((String) session.getAttribute(FIREBASE_USERID)).equals("")) {
			return getPleaseLinkAccountResponse(session, false);
		}

		session.setAttribute(WAITING_FOR_LINK_ACCOUNT, false);

		if ("AMAZON.HelpIntent".equals(intentName)) {
			return getHelpResponse(session);
		}

		else if ("AMAZON.StopIntent".equals(intentName)) {
			PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
			outputSpeech.setText("Goodbye.  Thanks for playing!");
			return SpeechletResponse.newTellResponse(outputSpeech);
		}

		else if ("AMAZON.CancelIntent".equals(intentName)) {
			PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
			outputSpeech.setText("Goodbye");
			return SpeechletResponse.newTellResponse(outputSpeech);
		}

		else if ("RepeatIntent".equals(intentName)) {
			String last_speech = (String) session.getAttribute(LAST_SPEECH);
			String last_reprompt = (String) session.getAttribute(LAST_REPROMPT);
			boolean lastSpeechIsSSML = (boolean) session.getAttribute(LAST_SPEECH_IS_SSML);
			boolean lastRepromptIsSSML = (boolean) session.getAttribute(LAST_REPROMPT_IS_SSML);
			return newAskResponse(session, last_speech, lastSpeechIsSSML, last_reprompt, lastRepromptIsSSML);
		}

		else if ("LinkAccountIntent".equals(intentName)) {
			return getAskResponse(session,
					"OK, your account is linked, and you are ready to play.   You can say list to list my games.");
		}

		else if ("ListIntent".equals(intentName)) {
			boolean gamesRetrieved = (Boolean) session.getAttribute(GAMES_RETRIEVED);
			return getGameList(requestEnvelope, !gamesRetrieved);

		}

		else if ("RefreshIntent".equals(intentName)) {
			return getGameList(requestEnvelope, true); // refresh data from Firebase

		}

		else if ("PlayGame".equals(intentName)) {
			return getPlayGameResponse(session, requestEnvelope);
		}

		else if ("ActionIntent".equals(intentName)) {
			return getActionResponse(session, requestEnvelope);
		}

		/*
		 * else if ("NonsenseIntent".equals(intentName)) {
		 *
		 * String speechText = "I'm sorry I didn't catch that.  What did you say?";
		 *
		 * return
		 *
		 * getAskResponse("Adventure Creator", speechText); }
		 */

		return getAskResponse(session, "This is unsupported.  Please try something else.");

	}

	private SpeechletResponse getPlayGameResponse(Session session,
			SpeechletRequestEnvelope<IntentRequest> requestEnvelope) {
		if (!(Boolean) session.getAttribute(GAMES_RETRIEVED)) {
			return getGameList(requestEnvelope, true);
		}

		IntentRequest request = requestEnvelope.getRequest();
		Intent intent = request.getIntent();
		Slot gameNumberSlot = intent.getSlot(GAMENUMBER_SLOT);
		if (gameNumberSlot != null && gameNumberSlot.getValue() != null) {
			String gameNumberString = gameNumberSlot.getValue();
			int gameNum = Integer.parseInt(gameNumberString) - 1;

			ArrayList<ZAdventure> adventureList = gameData.get(session.getUser().getUserId());

			String speechOutput;

			if (adventureList == null) {
				speechOutput = "There was a problem loading the adventure.  Try refreshing the game list.";
				return getAskResponse(session, speechOutput);
			}

			if ((gameNum > adventureList.size() - 1) || gameNum < 0) {
				speechOutput = "Game number " + gameNumberString + " is not a valid choice.  Please choose again.  ";
				return getAskResponse(session, speechOutput);
			}

			ZAdventure currAdventure = adventureList.get(gameNum);
			ArrayList<ZEvent> eventList = currAdventure.getEvents();

			if (eventList == null || eventList.size() == 0) {
				speechOutput = "Game number " + gameNumberString
						+ " does not have any events yet, and is not playable.  Please choose again.";
				return getAskResponse(session, speechOutput);
			}

			ZEvent startNode = eventList.get(0);

			session.setAttribute(CURRENT_GAME, gameNum);
			session.setAttribute(CURRENT_NODE, startNode.getEventId());

			StringBuilder gameStartSpeech = new StringBuilder();
			gameStartSpeech.append("<speak>");
			gameStartSpeech.append("Ok, let's play game ").append(gameNumberString).append(", ")
					.append(currAdventure.adventureName);
			gameStartSpeech.append("<break time=\"0.5s\"/>");

			if (currAdventure.adventureType == FIGHTY_ADVENTURE) {
				gameStartSpeech.append("You start the game with ").append(Integer.toString(currAdventure.playerHealth))
						.append(" health, and armed with your ").append(currAdventure.weaponName).append(".  ")
						.append("<break time=\"0.5s\"/>");
				session.setAttribute(PLAYER_HEALTH, currAdventure.playerHealth);
				session.setAttribute(IN_COMBAT, false);
			}

			gameStartSpeech.append(startNode.getDescription());
			gameStartSpeech.append("<break time=\"0.5s\"/>");
			gameStartSpeech.append("What do you do?  ");
			gameStartSpeech.append("<break time=\"0.5s\"/>");

			gameStartSpeech.append("You can: ");

			StringBuilder reprompt = new StringBuilder("You can say I ");

			StringBuilder actionListText = buildActionList(startNode);

			gameStartSpeech.append(actionListText);
			reprompt.append(actionListText);

			gameStartSpeech.append("</speak>");

			speechOutput = gameStartSpeech.toString();
			String repromptText = reprompt.toString();

			return newAskResponse(session, speechOutput, true, repromptText, false);

		} else {
			// There was no item in the intent so return the help prompt.
			String speechOutput = "Pick the game you'd like to play.  For example, you can say game one.";
			return getAskResponse(session, speechOutput);
		}

	}

	private SpeechletResponse getActionResponse(Session session,
			SpeechletRequestEnvelope<IntentRequest> requestEnvelope) {

		IntentRequest request = requestEnvelope.getRequest();
		Intent intent = request.getIntent();

		int gameNum = (Integer) session.getAttribute(CURRENT_GAME);
		if (gameNum < 0) {
			return getAskResponse(session, "Please select a game first.");
		}

		Slot userActionSlot = intent.getSlot(USERACTION_SLOT);

		if (userActionSlot == null || userActionSlot.getValue() == null) {
			// There was no item in the intent so return the help prompt.
			// String speechOutput = "Please say an action. For example, you can say I
			// walk.";
			// return getAskResponse(session, speechOutput);
			return getHelpResponse(session);
		}

		// convert to lower-case and remove punctuation, for easier matching.
		String userActionString = userActionSlot.getValue().toLowerCase().replaceAll("\\p{P}", "");
		int nodeId = (Integer) session.getAttribute(CURRENT_NODE);

		ArrayList<ZAdventure> adventureList = gameData.get(session.getUser().getUserId());
		ZAdventure currAdventure = adventureList.get(gameNum);
		ZEvent currNode = currAdventure.getEventFromEventListUsingEventId(nodeId);

		StringBuilder gameSpeech = new StringBuilder();

		if (currNode.getEventType() == MONSTER_EVENT) {

			boolean inCombat = (boolean) session.getAttribute(IN_COMBAT);

			if (userActionString.equals("attack")) {
				// ********BEGIN COMBAT
				// LOGIC***************************************************//
				// attacking.

				if (!inCombat) {
					session.setAttribute(MONSTER_HEALTH, currNode.getMonsterHealth());
					session.setAttribute(IN_COMBAT, true);
				}

				Random rand = new Random();
				// random.nextInt(max - min + 1) + min
				int playerAttackDmg = rand.nextInt(currAdventure.maxDamage - currAdventure.minDamage+ 1)
						+ currAdventure.minDamage;
				int monsterHealth = (int) session.getAttribute(MONSTER_HEALTH) - playerAttackDmg;

				gameSpeech.append("<speak>");

				if (playerAttackDmg == 0) {
					gameSpeech.append("You missed!  ");
				} else {
					gameSpeech.append("You hit the ").append(currNode.getMonsterName()).append(" with your ")
							.append(currAdventure.weaponName).append(", for ").append(Integer.toString(playerAttackDmg))
							.append(" damage.  ");
				}
				if (monsterHealth <= 0) {
					// monster is dead!
					gameSpeech.append("The ").append(currNode.getMonsterName()).append(" is dead!");
					gameSpeech.append("<break time=\"0.3s\"/>");
					gameSpeech.append("<say-as interpret-as=\"interjection\">hurray</say-as>");
					gameSpeech.append("<break time=\"0.3s\"/>");
					session.setAttribute(IN_COMBAT, false);

					if ((currNode.getNextActions() == null) || (currNode.getNextActions().size() == 0)) { // last node;
																											// we're
																											// done.
						session.setAttribute(CURRENT_GAME, -1);
						gameSpeech.append(THANKYOUFORPLAYING_STRING);
						gameSpeech.append("</speak>");
						String gameSpeechStr = gameSpeech.toString();
						return newAskResponse(session, gameSpeechStr, true, gameSpeechStr, true);
					}

					else {
						// monster is dead, go to next node.
						int nextEventId = currNode.getNextEventIds().get(0); // attack always corresponds to node(0)
						ZEvent nextEvent = currAdventure.getEventFromEventListUsingEventId(nextEventId);
						session.setAttribute(CURRENT_NODE, nextEventId);

						gameSpeech.append("<break time=\"0.5s\"/>");
						gameSpeech.append(nextEvent.getDescription());
						gameSpeech.append("<break time=\"0.5s\"/>");

						if ((nextEvent.getEventType() != MONSTER_EVENT)
								&& ((nextEvent.getNextActions() == null) || nextEvent.getNextActions().isEmpty())) {
							session.setAttribute(CURRENT_GAME, -1);
							gameSpeech.append(THANKYOUFORPLAYING_STRING);
							gameSpeech.append("</speak>");
							String gameSpeechStr = gameSpeech.toString();
							return newAskResponse(session, gameSpeechStr, true, gameSpeechStr, true);
						} else {
							gameSpeech.append("What do you do?  ");
							gameSpeech.append("<break time=\"0.5s\"/>");
							gameSpeech.append("You can: ");

							StringBuilder reprompt = new StringBuilder("You can say I ");
							StringBuilder actionListText = buildActionList(nextEvent);

							gameSpeech.append(actionListText);
							reprompt.append(actionListText);

							gameSpeech.append("</speak>");

							String gameSpeechStr = gameSpeech.toString();
							String repromptText = reprompt.toString();

							return newAskResponse(session, gameSpeechStr, true, repromptText, false);
						}
					}
				} else {
					// monster is not dead

					session.setAttribute(MONSTER_HEALTH, monsterHealth);
					int monsterAttackDmg = rand.nextInt(currNode.getMaxDamage() - currNode.getMinDamage() + 1)
							+ currNode.getMinDamage();
					gameSpeech.append("<break time=\"0.5s\"/>");

					if (monsterAttackDmg == 0) {
						gameSpeech.append("The ").append(currNode.getMonsterName()).append(" misses!  ");
					} else {
						gameSpeech.append("The ").append(currNode.getMonsterName()).append(" hits you with its ")
								.append(currNode.getWeaponName()).append(", for ").append(Integer.toString(monsterAttackDmg))
								.append(" damage.  ");
					}
					int playerHealth = (int) session.getAttribute(PLAYER_HEALTH) - monsterAttackDmg;
					if (playerHealth <= 0) {
						// you is dead!
						gameSpeech.append("Sorry, you have died.  ");
						gameSpeech.append("<audio src='https://s3.amazonaws.com/ask-soundlibrary/human/amzn_sfx_crowd_boo_01.mp3'/>");
						session.setAttribute(IN_COMBAT, false);
						session.setAttribute(CURRENT_GAME, -1);
						gameSpeech.append(THANKYOUFORPLAYING_STRING);
						gameSpeech.append("</speak>");
						String gameSpeechStr = gameSpeech.toString();
						return newAskResponse(session, gameSpeechStr, true, gameSpeechStr, true);
					} else {
						session.setAttribute(PLAYER_HEALTH, playerHealth);
						gameSpeech.append("<break time=\"0.5s\"/>");
						gameSpeech.append("You can say, I attack.  ");
						gameSpeech.append("</speak>");
						String gameSpeechStr = gameSpeech.toString();
						return newAskResponse(session, gameSpeechStr, true, gameSpeechStr, true);
					}
				}

				// ********END COMBAT LOGIC***************************************************//

			} else {

				// at a monster node, but action string is NOT attack.

				// if we're in combat, only valid action is attack.
				// if there are no next actions, or only 1 next action - the only valid action
				// is attack.
				if (inCombat || currNode.getNextActions().size() <= 1) {
					StringBuilder notAttackString = new StringBuilder().append(userActionSlot.getValue())
							.append(" is not a valid action.  You can say I attack.");

					return getAskResponse(session, notAttackString.toString());
				}

			}
		}

		// handle non-combat actions

		boolean actionMatched = false;
		int i = 0;
		for (i = 0; i < currNode.getNextActions().size(); i++) {
			String nextAction = currNode.getNextActions().get(i);
			String nextActionToMatch = nextAction.toLowerCase().replaceAll("\\p{P}", "");
			if (nextActionToMatch.equals(userActionString)) {
				// found a match! go to next node.
				actionMatched = true;
				break;
			}
		}

		if (!actionMatched) {
			StringBuilder noMatchString = new StringBuilder().append(userActionSlot.getValue())
					.append(" is not a valid action.  ").append("You can say I ").append(buildActionList(currNode));

			return getAskResponse(session, noMatchString.toString());
		}

		int nextEventId = currNode.getNextEventIds().get(i);
		ZEvent nextEvent = currAdventure.getEventFromEventListUsingEventId(nextEventId);
		session.setAttribute(CURRENT_NODE, nextEventId);

		gameSpeech = new StringBuilder();

		gameSpeech.append("<speak>");
		gameSpeech.append(nextEvent.getDescription());
		gameSpeech.append("<break time=\"0.5s\"/>");

		if ((nextEvent.getEventType() != MONSTER_EVENT)
				&& ((nextEvent.getNextActions() == null) || nextEvent.getNextActions().isEmpty())) {
			session.setAttribute(CURRENT_GAME, -1);
			gameSpeech.append(THANKYOUFORPLAYING_STRING);
			gameSpeech.append("</speak>");
			String gameSpeechStr = gameSpeech.toString();
			return newAskResponse(session, gameSpeechStr, true, gameSpeechStr, true);
		} else {
			gameSpeech.append("What do you do?  ");
			gameSpeech.append("<break time=\"0.5s\"/>");
			gameSpeech.append("You can: ");

			StringBuilder reprompt = new StringBuilder("You can say I ");
			StringBuilder actionListText = buildActionList(nextEvent);

			gameSpeech.append(actionListText);
			reprompt.append(actionListText);

			gameSpeech.append("</speak>");

			String gameSpeechStr = gameSpeech.toString();
			String repromptText = reprompt.toString();

			return newAskResponse(session, gameSpeechStr, true, repromptText, false);
		}

	}

	// return the node's action list as a String.
	// For example: "You can go left, go right, or go center."
	private StringBuilder buildActionList(ZEvent node) {
		StringBuilder actionListText = new StringBuilder();

		if (node.getEventType() == MONSTER_EVENT) {
			// even if there are no next actions - for monster events, the user can always
			// attack.
			// attacking is ALWAYS the first action.
			if ((node.getNextActions() == null) || (node.getNextActions().size() == 0)) {
				actionListText.append("attack.  ");
				return actionListText;
			}
		}

		for (int i = 0; i < node.getNextActions().size(); i++) {
			if ((i == node.getNextActions().size() - 1) && (node.getNextActions().size() > 1)) {
				// for last element, add an "or"
				actionListText.append("or ");
			}

			actionListText.append(node.getNextActions().get(i));

			if (i == node.getNextActions().size() - 1) {
				actionListText.append(".  ");
			} else {
				actionListText.append(", ");
			}
		}
		return actionListText;
	}

	// return the node's action list as a help String.
	// For example: "You can say I go left, I go right, or I go center."
	private StringBuilder buildHelpActionList(ZEvent node) {
		StringBuilder actionHelpListText = new StringBuilder("You can say ");

		if (node.getEventType() == MONSTER_EVENT) {
			// even if there are no next actions - for monster events, the user can always
			// attack.
			// attacking is ALWAYS the first action.
			if ((node.getNextActions() == null) || (node.getNextActions().size() == 0)) {
				actionHelpListText.append("I attack.  ");
				return actionHelpListText;
			}
		}

		for (int i = 0; i < node.getNextActions().size(); i++) {
			if ((i == node.getNextActions().size() - 1) && (node.getNextActions().size() > 1)) {
				// for last element, add an "or"
				actionHelpListText.append("or ");
			}
			actionHelpListText.append("I ");
			actionHelpListText.append(node.getNextActions().get(i));

			if (i == node.getNextActions().size() - 1) {
				actionHelpListText.append(".  ");
			} else {
				actionHelpListText.append(", ");
			}
		}
		return actionHelpListText;
	}

	// if timeToRefresh is true, read the AdventureList from Firebase (takes a long
	// time)
	// if timeToRefresh is false, just use the existing AdventureList that was
	// previously loaded.
	public SpeechletResponse getGameList(SpeechletRequestEnvelope<IntentRequest> requestEnvelope,
			boolean timeToRefresh) {
		IntentRequest request = requestEnvelope.getRequest();
		Session session = requestEnvelope.getSession();
		ArrayList<ZAdventure> adventureList;

		if (timeToRefresh) {
			// It is an issue with the simulator that the progressive message comes in about
			// the same time as the real response (just a few mill seconds apart)
			SystemState systemState = getSystemState(requestEnvelope.getContext());
			String apiEndpoint = systemState.getApiEndpoint();
			// Dispatch a progressive response to engage the user while fetching events
			dispatchProgressiveResponse(request.getRequestId(), "One moment, retrieving your games", systemState,
					apiEndpoint);

			adventureList = retrieveAdventureList((String) session.getAttribute(FIREBASE_USERID));
			session.setAttribute(GAMES_RETRIEVED, true);
			gameData.put(session.getUser().getUserId(), adventureList);
		} else {
			adventureList = gameData.get(session.getUser().getUserId());
		}

		StringBuilder speechText = new StringBuilder();

		if (adventureList == null) {
			speechText.append("The adventure list is null.  Please contact support!");
			String speechStr = speechText.toString();
			String repromptStr = "Unexpected Error.";
			return newAskResponse(session, speechStr, false, repromptStr, false);
		}

		String gamesAvailableString = (adventureList.size() == 1) ? " game available.  " : " games available.  ";

		speechText.append("You have ").append(Integer.toString(adventureList.size())).append(gamesAvailableString);

		if (adventureList.size() == 0) {
			speechText.append("Please create a game first using the Android app.  Goodbye!");
			String speechStr = speechText.toString();
			// SimpleCard card = getSimpleCard("No games available", speechStr);
			PlainTextOutputSpeech speech = getPlainTextOutputSpeech(speechStr);
			return SpeechletResponse.newTellResponse(speech);
		}

		for (int i = 0; i < adventureList.size(); i++) {
			if ((i == adventureList.size() - 1) && (adventureList.size() > 1)) {
				// for last element, add an "and"
				speechText.append("and ");
			}
			speechText.append("Game ").append(Integer.toString(i + 1)).append(": ") // 1, game name, 2, game
																					// name, ...
					.append(adventureList.get(i).adventureName);
			if (i == adventureList.size() - 1) {
				speechText.append(".  ");
			} else {
				speechText.append(", ");
			}
		}
		speechText.append("Which game would you like to play?");
		String speechStr = speechText.toString();
		String repromptStr = "Pick the game you'd like to play.  For example, you can say game 1.";
		return newAskResponse(session, speechStr, false, repromptStr, false);

	}

	private ArrayList<ZAdventure> retrieveAdventureList(String firebaseUserID) {
		ArrayList<ZAdventure> adventureList = new ArrayList<>();
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		try {
			Query query = FirebaseDatabase.getInstance().getReference().child("adventures").orderByChild("userid")
					.equalTo(firebaseUserID);
			query.addListenerForSingleValueEvent(new ValueEventListener() {
				@Override
				public void onDataChange(DataSnapshot dataSnapshot) {
					for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
						ZAdventure zAdventure = postSnapshot.getValue(ZAdventure.class);
						adventureList.add(zAdventure);
					}
					countDownLatch.countDown();
				}

				@Override
				public void onCancelled(DatabaseError databaseError) {
					System.out.println("something went wrong...database error");
					log.error("something went wrong...database error");
					countDownLatch.countDown();
				}
			});

		} catch (Exception ioe) {
			System.out.println(ioe.getMessage());
			countDownLatch.countDown();
		}

		waitForCountdownLatch(countDownLatch);
		return adventureList;
	}

	@Override
	public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope) {
		log.info("onSessionEnded requestId={}, sessionId={}", requestEnvelope.getRequest().getRequestId(),
				requestEnvelope.getSession().getSessionId());

		// any cleanup logic goes here
		gameData.remove(requestEnvelope.getSession().getUser().getUserId());
	}

	/**
	 * Creates a {@code SpeechletResponse} for the help intent.
	 *
	 * @return SpeechletResponse spoken and visual response for the given intent
	 */
	private SpeechletResponse getHelpResponse(Session session) {
		String helpStr = "";
		int gameNum = (Integer) session.getAttribute(CURRENT_GAME);
		if (gameNum < 0) {
			// Create the plain text output.
			helpStr = "Adventure Creator lets you design and play your own choose your own adventure stories.  "
					+ "You can say list to list your games, or say play game, followed by the game number, to play a game.";
		} else {
			boolean inCombat = (boolean) session.getAttribute(IN_COMBAT);
			if (inCombat) {
				helpStr += "You can say, I attack.  ";
			} else {
				int nodeId = (Integer) session.getAttribute(CURRENT_NODE);
				ArrayList<ZAdventure> adventureList = gameData.get(session.getUser().getUserId());
				ZAdventure currAdventure = adventureList.get(gameNum);
				ZEvent currNode = currAdventure.getEventFromEventListUsingEventId(nodeId);
				helpStr += buildHelpActionList(currNode);
			}
			helpStr += "Or you can say, repeat, to hear it again.  Or you can say list to list your games, or say bye, to leave adventure creator.";
		}

		return getAskResponse(session, helpStr);
	}

	/**
	 * Helper method that creates a card object.
	 *
	 * @param title
	 *            title of the card
	 * @param content
	 *            body of the card
	 * @return SimpleCard the display card to be sent along with the voice response.
	 */
	/*
	 * private SimpleCard getSimpleCard(String title, String content) { SimpleCard
	 * card = new SimpleCard(); card.setTitle(title); card.setContent(content);
	 * return card; }
	 */

	/**
	 * Helper method for retrieving an OutputSpeech object when given a string of
	 * TTS.
	 *
	 * @param speechText
	 *            the text that should be spoken out to the user.
	 * @return an instance of SpeechOutput.
	 */
	private PlainTextOutputSpeech getPlainTextOutputSpeech(String speechText) {
		PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
		speech.setText(speechText);
		return speech;
	}

	/**
	 * Helper method that returns a reprompt object. This is used in Ask responses
	 * where you want the user to be able to respond to your speech.
	 *
	 * @param outputSpeech
	 *            The OutputSpeech object that will be said once and repeated if
	 *            necessary.
	 * @return Reprompt instance.
	 */
	private Reprompt getReprompt(OutputSpeech outputSpeech) {
		Reprompt reprompt = new Reprompt();
		reprompt.setOutputSpeech(outputSpeech);
		return reprompt;
	}

	/**
	 * Helper method for retrieving an Ask response with a reprompt included.
	 *
	 * @param speechText
	 *            speech text that will be spoken to the user.
	 * @return the speech text.
	 */
	private SpeechletResponse getAskResponse(Session session, String speechText) {
		session.setAttribute(LAST_SPEECH, speechText);
		session.setAttribute(LAST_REPROMPT, speechText);
		session.setAttribute(LAST_SPEECH_IS_SSML, false);
		session.setAttribute(LAST_REPROMPT_IS_SSML, false);

		PlainTextOutputSpeech speech = getPlainTextOutputSpeech(speechText);
		Reprompt reprompt = getReprompt(speech);
		return SpeechletResponse.newAskResponse(speech, reprompt);
	}
	/*
	 * private SpeechletResponse getAskResponse(String cardTitle, String speechText)
	 * { SimpleCard card = getSimpleCard(cardTitle, speechText);
	 * PlainTextOutputSpeech speech = getPlainTextOutputSpeech(speechText); Reprompt
	 * reprompt = getReprompt(speech); return
	 * SpeechletResponse.newAskResponse(speech, reprompt, card); }
	 */

	/**
	 * Wrapper for creating the Ask response from the input strings.
	 *
	 * @param stringOutput
	 *            the output to be spoken
	 * @param isOutputSsml
	 *            whether the output text is of type SSML
	 * @param repromptText
	 *            the reprompt for if the user doesn't reply or is misunderstood.
	 * @param isRepromptSsml
	 *            whether the reprompt text is of type SSML
	 * @return SpeechletResponse the speechlet response
	 */
	private SpeechletResponse newAskResponse(Session session, String stringOutput, boolean isOutputSsml,
			String repromptText, boolean isRepromptSsml) {
		session.setAttribute(LAST_SPEECH, stringOutput);
		session.setAttribute(LAST_REPROMPT, repromptText);
		session.setAttribute(LAST_SPEECH_IS_SSML, isOutputSsml);
		session.setAttribute(LAST_REPROMPT_IS_SSML, isRepromptSsml);

		OutputSpeech outputSpeech, repromptOutputSpeech;
		if (isOutputSsml) {
			outputSpeech = new SsmlOutputSpeech();
			((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
		} else {
			outputSpeech = new PlainTextOutputSpeech();
			((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
		}

		if (isRepromptSsml) {
			repromptOutputSpeech = new SsmlOutputSpeech();
			((SsmlOutputSpeech) repromptOutputSpeech).setSsml(repromptText);
		} else {
			repromptOutputSpeech = new PlainTextOutputSpeech();
			((PlainTextOutputSpeech) repromptOutputSpeech).setText(repromptText);
		}
		Reprompt reprompt = new Reprompt();
		reprompt.setOutputSpeech(repromptOutputSpeech);
		return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
	}

	/**
	 * Creates and returns a {@code SpeechletResponse} with a welcome message.
	 *
	 * @return SpeechletResponse spoken and visual response for the given intent
	 */
	private SpeechletResponse getWelcomeResponse(Session session) {
		String repromptText = "You can say list to list my games.";
		return newAskResponse(session, WELCOMESTRING, true, repromptText, false);
	}

	/**
	 * Dispatches a progressive response.
	 *
	 * @param requestId
	 *            the unique request identifier
	 * @param text
	 *            the text of the progressive response to send
	 * @param systemState
	 *            the SystemState object
	 * @param apiEndpoint
	 *            the Alexa API endpoint
	 */
	private void dispatchProgressiveResponse(String requestId, String text, SystemState systemState,
			String apiEndpoint) {
		DirectiveEnvelopeHeader header = DirectiveEnvelopeHeader.builder().withRequestId(requestId).build();
		SpeakDirective directive = SpeakDirective.builder().withSpeech(text).build();
		DirectiveEnvelope directiveEnvelope = DirectiveEnvelope.builder().withHeader(header).withDirective(directive)
				.build();

		if (systemState.getApiAccessToken() != null && !systemState.getApiAccessToken().isEmpty()) {
			String token = systemState.getApiAccessToken();
			try {
				directiveService.enqueue(directiveEnvelope, apiEndpoint, token);
			} catch (Exception e) {
				log.error("Failed to dispatch a progressive response", e);
				System.err.println("Failed to dispatch a progressive response" + e.getMessage());
			}
		}
	}

	/**
	 * Helper method that retrieves the system state from the request context.
	 *
	 * @param context
	 *            request context.
	 * @return SystemState the systemState
	 */
	private SystemState getSystemState(Context context) {
		return context.getState(SystemInterface.class, SystemState.class);
	}

	// sets FIREBASE_LINK_INFO_RETRIEVED and FIREBASE_USERID
	private boolean checkIsAccountLinkedWithFirebase(Session session) {

		String amazonUserID = session.getUser().getUserId();
		String amazonUserIDKey = amazonUserID.replaceAll("[^a-zA-Z0-9 ]", ""); // remove punctuation - not allowed in a
																				// firebase key
		final StringBuilder firebaseUserID = new StringBuilder("");
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		try {
			Query query = FirebaseDatabase.getInstance().getReference().child("linkedUserAccounts")
					.child(amazonUserIDKey);
			query.addListenerForSingleValueEvent(new ValueEventListener() {
				@Override
				public void onDataChange(DataSnapshot dataSnapshot) {
					String linkString = (String) dataSnapshot.getValue();
					if (linkString != null) {
						firebaseUserID.append(linkString);
					}
					countDownLatch.countDown();
				}

				@Override
				public void onCancelled(DatabaseError databaseError) {
					// Getting Post failed, log a message
					System.out.println("something went wrong...database error");
					log.error("something went wrong...database error");
					countDownLatch.countDown();
				}
			});

		} catch (Exception ioe) {
			System.out.println(ioe.getMessage());
			countDownLatch.countDown();
		}

		waitForCountdownLatch(countDownLatch);

		String firebaseUserIDString = firebaseUserID.toString();
		session.setAttribute(FIREBASE_LINK_INFO_RETRIEVED, true);

		if (!firebaseUserIDString.equals("")) {
			session.setAttribute(FIREBASE_USERID, firebaseUserIDString);
			return true;
		} else {
			return false;
		}
	}

	private void waitForCountdownLatch(CountDownLatch countDownLatch) {
		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			log.error(e.getMessage());
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

}

/*
 * we tried using Amazon Account Linking, using LinkAccount card below. Problem:
 * Not supported by Alexa test environment!!! Also, requires a visual web
 * browser to complete successfully. So instead, we used a different Account
 * Linking method
 */
/*
 * if (requestEnvelope.getSession().getUser().getAccessToken() == null) { return
 * requestLogin(); //if user doesn't have a token, return a LinkAccount card }
 * else {
 *
 * // https://www.mkyong.com/java/java-https-client-httpsurlconnection-example/
 * try { BufferedReader bufferedReader = null; String text = ""; String
 * amazonProfileURL = "https://api.amazon.com/user/profile?access_token=";
 * amazonProfileURL += requestEnvelope.getSession().getUser().getAccessToken();
 * URL url = new URL(amazonProfileURL);
 *
 * HttpsURLConnection con = (HttpsURLConnection) url.openConnection(); //
 * con.setRequestMethod("GET"); // con.setConnectTimeout(5000); //
 * con.setReadTimeout(5000); // int status = con.getResponseCode();
 * bufferedReader = new BufferedReader(new
 * InputStreamReader(con.getInputStream())); String inputLine; StringBuffer
 * response = new StringBuffer(); while ((inputLine = bufferedReader.readLine())
 * != null) { response.append(inputLine); } bufferedReader.close();
 * con.disconnect();
 *
 * // print result System.out.println(response.toString()); // String
 * profileString = response.toString();
 *
 * JSONParser parser = new JSONParser(); JSONObject jsonProfile = (JSONObject)
 * parser.parse(text); String emailAddress = (String) jsonProfile.get("email");
 * // store it in session information. session.setAttribute(USER_EMAIL,
 * emailAddress);
 *
 * } catch (Exception ex) { ex.printStackTrace(); }
 *
 * return getWelcomeResponse(); }
 */

/*
 * private SpeechletResponse requestLogin() { LinkAccountCard card = new
 * LinkAccountCard();
 *
 * String ssml = WELCOMESTRING +
 * "<speak>To start using this skill, please use the companion app to authenticate on Amazon</speak>"
 * ; SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
 * outputSpeech.setSsml(ssml);
 *
 * String requestLinkText =
 * "Welcome to the Alexa Adventure Creator.  To start using this skill, please use the companion app to authenticate on Amazon"
 * ; PlainTextOutputSpeech speech = getPlainTextOutputSpeech(requestLinkText);
 * Reprompt reprompt = getReprompt(speech); return
 * SpeechletResponse.newAskResponse(outputSpeech, reprompt, card); }
 */

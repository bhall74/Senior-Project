package ac.alexa.speechlet;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
/**
Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
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
import org.apache.commons.io.IOUtils;

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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;

//other imports
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

//get ZAdventure and ZEvent
import ac.game.*;

import javax.net.ssl.HttpsURLConnection;

/**
 * Adventure Creator Skill Queries game data from Firebase and builds Intents
 */

public class ACSpeechlet implements SpeechletV2 {

	// private static final Logger log = LoggerFactory.getLogger(ACSpeechlet.class);

	/**
	 * Service to send progressive response directives.
	 */
	private DirectiveService directiveService;

	private boolean loginSuccess = false;

	private static final String GAMENUMBER_SLOT = "gameNumber";
	private static final String USERACTION_SLOT = "userAction";

	// session attributes
	private static final String GAMES_RETRIEVED = "gamesListed"; // true if games retrieved
	// private static final String ADVENTURE_LIST = "adventureList"; // the
	// adventure list
	private static final String USER_EMAIL = "userEmail";

	private static final String CURRENT_GAME = "currentGame"; // -1 if no current game.
	private static final String CURRENT_NODE = "currentNode"; // 0 is the starting node

	private static HashMap<String, ArrayList<ZAdventure>> gameData = new HashMap<String, ArrayList<ZAdventure>>();

	/**
	 * Constructs an instance of {@link ACSpeechlet}.
	 *
	 * @param directiveService
	 *            implementation of directive service
	 */
	public ACSpeechlet(DirectiveService directiveService) {
		this.directiveService = directiveService;

		int successPoint = 0;

		// login to firebase
		try {
			// Fetch the service account key JSON file contents
			FileInputStream serviceAccount = new FileInputStream("ACServiceAccount.json");
			successPoint = 1;

			// Initialize the app with a service account, granting admin privileges
			FirebaseOptions options = new FirebaseOptions.Builder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.setDatabaseUrl("https://alexa-adventure-creator-ca7c9.firebaseio.com/").build();

			successPoint = 2;
			FirebaseApp.initializeApp(options);
			successPoint = 3;
			loginSuccess = true;
		} catch (IOException ioe) {
			System.out.println("Error connecting to firebase with credentials");
			System.out.println("Success point reached: " + Integer.toString(successPoint));
		}
	}

	@Override
	public void onSessionStarted(SpeechletRequestEnvelope<SessionStartedRequest> requestEnvelope) {
		// log.info("onSessionStarted requestId={}, sessionId={}",
		// requestEnvelope.getRequest().getRequestId(),
		// requestEnvelope.getSession().getSessionId());

		// any initialization logic goes here
		Session session = requestEnvelope.getSession();
		session.setAttribute(GAMES_RETRIEVED, false);
		session.setAttribute(CURRENT_GAME, -1);
		session.setAttribute(CURRENT_NODE, -1);
	}

	@Override
	public SpeechletResponse onLaunch(SpeechletRequestEnvelope<LaunchRequest> requestEnvelope) {
		Session session = requestEnvelope.getSession();

		if (!loginSuccess) {
			PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
			outputSpeech.setText("Login unsuccessful.  Please try again later, goodbye!");
			return SpeechletResponse.newTellResponse(outputSpeech);
		}

		// return getWelcomeResponse();

		// https://www.mkyong.com/java/java-https-client-httpsurlconnection-example/

		// if no amazon token, return a LinkAccount card if
		if (requestEnvelope.getSession().getUser().getAccessToken() == null) {
			return requestLogin();
		} else {

			try {
				BufferedReader bufferedReader = null;
				String text = "";
				String amazonProfileURL = "https://api.amazon.com/user/profile?access_token=";
				amazonProfileURL += requestEnvelope.getSession().getUser().getAccessToken();
				URL url = new URL(amazonProfileURL);

				HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
				// con.setRequestMethod("GET");
				// con.setConnectTimeout(5000);
				// con.setReadTimeout(5000);
				// int status = con.getResponseCode();
				bufferedReader = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();
				while ((inputLine = bufferedReader.readLine()) != null) {
					response.append(inputLine);
				}
				bufferedReader.close();
				con.disconnect();

				// print result System.out.println(response.toString());
				// String profileString = response.toString();

				JSONParser parser = new JSONParser();
				JSONObject jsonProfile = (JSONObject) parser.parse(text);
				String emailAddress = (String) jsonProfile.get("email");
				// store it in session information.
				session.setAttribute(USER_EMAIL, emailAddress);

			} catch (Exception ex) {
				ex.printStackTrace();
			}

			return getWelcomeResponse();
		}
	}

	@Override
	public SpeechletResponse onIntent(SpeechletRequestEnvelope<IntentRequest> requestEnvelope) {
		IntentRequest request = requestEnvelope.getRequest();
		// log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
		// requestEnvelope.getSession().getSessionId());
		Intent intent = request.getIntent();
		String intentName = (intent != null) ? intent.getName() : null;

		if (intentName != null) {
			System.out.println("current intent: " + intentName);
		}

		Session session = requestEnvelope.getSession();

		if ("WelcomeIntent".equals(intentName)) {
			return getWelcomeResponse();
		}

		else if ("AMAZON.HelpIntent".equals(intentName)) {
			return getHelpResponse();
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

		else if ("ListIntent".equals(intentName)) {
			boolean gamesRetrieved = (Boolean) session.getAttribute(GAMES_RETRIEVED);
			return getGameList(requestEnvelope, !gamesRetrieved);
		} else if ("RefreshIntent".equals(intentName)) {
			return getGameList(requestEnvelope, true); // refresh data from Firebase
		} else if ("ChooseGame".equals(intentName)) {

			if (!(Boolean) session.getAttribute(GAMES_RETRIEVED)) {
				String speechStr = "Please list your games first.";
				String repromptStr = "You can say list to list your games.";
				return newAskResponse(speechStr, false, repromptStr, false);
			} else {
				Slot gameNumberSlot = intent.getSlot(GAMENUMBER_SLOT);
				if (gameNumberSlot != null && gameNumberSlot.getValue() != null) {
					String gameNumberString = gameNumberSlot.getValue();
					int gameNum = Integer.parseInt(gameNumberString) - 1;
					ArrayList<ZAdventure> adventureList = gameData.get(session.getUser().getUserId());

					String speechOutput;

					if (adventureList == null) {
						speechOutput = "There was a problem loading the adventure.  Try refreshing the game list.";
						return getAskResponse(speechOutput, speechOutput);
					}

					if ((gameNum > adventureList.size() - 1) || gameNum < 0) {
						speechOutput = "Game number " + gameNumberString
								+ " is not a valid choice.  Please choose again.  ";
						return getAskResponse(speechOutput, speechOutput);
					}

					ZAdventure currAdventure = adventureList.get(gameNum);
					ArrayList<ZEvent> eventList = currAdventure.getEvents();

					if (eventList == null || eventList.size() == 0) {
						speechOutput = "Game number " + gameNumberString
								+ " does not have any events yet, and is not playable.  Please choose again.";
						return getAskResponse(speechOutput, speechOutput);
					}

					ZEvent startNode = eventList.get(0);

					session.setAttribute(CURRENT_GAME, gameNum);
					session.setAttribute(CURRENT_NODE, startNode.eventId);

					StringBuilder gameStartSpeech = new StringBuilder();
					gameStartSpeech.append("<speak>");
					gameStartSpeech.append("Ok, let's play game ").append(gameNumberString).append(", ")
							.append(currAdventure.adventureName);
					gameStartSpeech.append("<break time=\"0.5s\"/>");
					gameStartSpeech.append(startNode.description);
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

					return newAskResponse(speechOutput, true, repromptText, false);

				} else {
					// There was no item in the intent so return the help prompt.
					String speechOutput = "Pick the game you'd like to play.  For example, you can say game one.";
					return getAskResponse(speechOutput, speechOutput);
				}
			}
		}

		else if ("ActionIntent".equals(intentName))

		{
			int gameNum = (Integer) session.getAttribute(CURRENT_GAME);
			if (gameNum < 0) {
				return getAskResponse("Please select a game first.", "Please select a game first.");
			}

			Slot userActionSlot = intent.getSlot(USERACTION_SLOT);
			if (userActionSlot != null && userActionSlot.getValue() != null) {
				//convert to lower-case and remove punctuation, for easier matching.
				String userActionString = userActionSlot.getValue().toLowerCase().replaceAll("\\p{P}", "");
				//String speechOutput = "Here is your action: " + userActionString;

				int nodeId = (Integer) session.getAttribute(CURRENT_NODE);

				ArrayList<ZAdventure> adventureList = gameData.get(session.getUser().getUserId());
				ZAdventure currAdventure = adventureList.get(gameNum);
				ZEvent currNode = currAdventure.getEventFromEventListUsingEventId(nodeId);

				for (int i = 0; i < currNode.getNextActions().size(); i++) {
					String nextAction = currNode.getNextActions().get(i);
					String nextActionToMatch = nextAction.toLowerCase().replaceAll("\\p{P}", "");
					if (nextActionToMatch.equals(userActionString)) {
						//found a match!  go to next node.

						int nextEventId = currNode.getNextEventIds().get(i);
						ZEvent nextEvent = currAdventure.getEventFromEventListUsingEventId(nextEventId);
						session.setAttribute(CURRENT_NODE, nextEventId);

						StringBuilder gameSpeech = new StringBuilder();

						gameSpeech.append("<speak>");
						gameSpeech.append(nextEvent.description);
						gameSpeech.append("<break time=\"0.5s\"/>");

						if ((nextEvent.getNextActions() == null) || nextEvent.getNextActions().isEmpty()) {
							session.setAttribute(CURRENT_GAME, -1);
							gameSpeech.append("Thank you for playing!  You can play another game, or say bye to leave.");
							gameSpeech.append("</speak>");
							String gameSpeechStr = gameSpeech.toString();
							return newAskResponse(gameSpeechStr, true, gameSpeechStr, true);
						}
						else {
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

							return newAskResponse(gameSpeechStr, true, repromptText, false);
						}
					}
				}

				//if no match...
				StringBuilder noMatchReprompt = new StringBuilder().append(userActionSlot.getValue())
						.append(" is not a valid action.  ")
						.append("You can say I ").append(buildActionList(currNode));

				return getAskResponse("Invalid action", noMatchReprompt.toString());

			} else {
				// There was no item in the intent so return the help prompt.
				String speechOutput = "Please say an action.  For example, you can say I walk.";
				return getAskResponse("Action Required", speechOutput);
			}
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

		else

		{
			return getAskResponse("Adventure Creator", "This is unsupported.  Please try something else.");
		}
	}

	private StringBuilder buildActionList(ZEvent node) {
		StringBuilder actionListText = new StringBuilder();
		for (int i = 0; i < node.getNextActions().size(); i ++) {
			if ((i == node.getNextActions().size() - 1) && (node.getNextActions().size() > 1)) {
				// for last element, add an "and"
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


	// if timeToRefresh is true, read from Firebase (takes a long time)
	// if timeToRefresh is false, use the existing list.
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

			adventureList = retrieveAdventureList();
			session.setAttribute(GAMES_RETRIEVED, true);
			// session.setAttribute(ADVENTURE_LIST, adventureList);
			gameData.put(session.getUser().getUserId(), adventureList);
		} else {
			// adventureList = (ArrayList<ZAdventure>) session.getAttribute(ADVENTURE_LIST);
			adventureList = gameData.get(session.getUser().getUserId());
		}

		StringBuilder speechText = new StringBuilder();
		speechText.append("You have ").append(Integer.toString(adventureList.size())).append(" games available.  ");

		if (adventureList.size() == 0) {
			speechText.append("Please create a game first using the Android app.  Goodbye!");
			String speechStr = speechText.toString();
			SimpleCard card = getSimpleCard("No games available", speechStr);
			PlainTextOutputSpeech speech = getPlainTextOutputSpeech(speechStr);
			return SpeechletResponse.newTellResponse(speech, card);
		} else {
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
			return newAskResponse(speechStr, false, repromptStr, false);
		}
	}

	@Override
	public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope) {
		// log.info("onSessionEnded requestId={}, sessionId={}",
		// requestEnvelope.getRequest().getRequestId(),
		// requestEnvelope.getSession().getSessionId());

		// any cleanup logic goes here
		gameData.remove(requestEnvelope.getSession().getUser().getUserId());
	}

	/**
	 * Creates a {@code SpeechletResponse} for the help intent.
	 *
	 * @return SpeechletResponse spoken and visual response for the given intent
	 */
	private SpeechletResponse getHelpResponse() {
		// Create the plain text output.
		String helpStr = "Adventure Creator lets you design and play " + "your own choose your own adventure stories.  "
				+ "You can say list to list your games or refresh to reload your game list.";

		return getAskResponse("Help", helpStr);
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
	private SimpleCard getSimpleCard(String title, String content) {
		SimpleCard card = new SimpleCard();
		card.setTitle(title);
		card.setContent(content);

		return card;
	}

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
	 * Helper method for retrieving an Ask response with a simple card and reprompt
	 * included.
	 *
	 * @param cardTitle
	 *            Title of the card that you want displayed.
	 * @param speechText
	 *            speech text that will be spoken to the user.
	 * @return the resulting card and speech text.
	 */
	private SpeechletResponse getAskResponse(String cardTitle, String speechText) {
		SimpleCard card = getSimpleCard(cardTitle, speechText);
		PlainTextOutputSpeech speech = getPlainTextOutputSpeech(speechText);
		Reprompt reprompt = getReprompt(speech);

		return SpeechletResponse.newAskResponse(speech, reprompt, card);
	}

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
	private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml, String repromptText,
			boolean isRepromptSsml) {
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

	private void waitForCountdownLatch(CountDownLatch countDownLatch) {
		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			// log.error(e.getMessage());
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Creates and returns a {@code SpeechletResponse} with a welcome message.
	 *
	 * @return SpeechletResponse spoken and visual response for the given intent
	 */
	private SpeechletResponse getWelcomeResponse() {
		String descriptionText = " the app that let's you create and play your own choose your own adventure stories.  ";
		String speechText = "<speak>Welcome to Adventure Creator. "
				+ "<audio src='https://s3.amazonaws.com/ask-soundlibrary/magic/amzn_sfx_fairy_melodic_chimes_01.mp3'/>"
				// + "<audio src='https://s3.amazonaws.com/siatksubucket/triumph.mp3'/>"
				// //incompatible file.
				+ descriptionText + "</speak>";
		String repromptText = "You can say list to list my games.";
		return newAskResponse(speechText, true, repromptText, false);
	}

	private SpeechletResponse requestLogin() {
		LinkAccountCard card = new LinkAccountCard();

		String ssml = "<speak>Welcome to the Alexa Adventure Creator.  "
				+ "<audio src='https://s3.amazonaws.com/ask-soundlibrary/magic/amzn_sfx_fairy_melodic_chimes_01.mp3'/>"
				+ "To start using this skill, please use the companion app to authenticate on Amazon" + "</speak>";
		SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
		outputSpeech.setSsml(ssml);

		String requestLinkText = "Welcome to the Alexa Adventure Creator.  To start using this skill, please use the companion app to authenticate on Amazon";
		PlainTextOutputSpeech speech = getPlainTextOutputSpeech(requestLinkText);
		Reprompt reprompt = getReprompt(speech);
		return SpeechletResponse.newAskResponse(outputSpeech, reprompt, card);
	}

	private ArrayList<ZAdventure> retrieveAdventureList() {
		ArrayList<ZAdventure> adventureList = new ArrayList<>();
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		try {
			Query query = FirebaseDatabase.getInstance().getReference().child("adventures");
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
					// Getting Post failed, log a message
					System.out.println("something went wrong...database error");
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
				// log.error("Failed to dispatch a progressive response", e);
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

}

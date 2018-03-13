package ac.alexa.speechlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Amazon Alexa imports
import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.SpeechletV2;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazon.speech.ui.OutputSpeech;

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

//game object references
import ac.game.ZAdventure;
import ac.game.ZEvent;

//other imports
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;


/**
 * Adventure Creator Skill
 *  Queries game data from Firebase and builds Intents
 */
public class ACSpeechlet implements SpeechletV2
{
  private static final String[] events = { "Event one", "Event two", "Event three"};
  private static final String[] descriptions = { "Description one", "Description two", "Description three" };
  private int counter = 0;

  //object references
  private static final Logger log = LoggerFactory.getLogger(ACSpeechlet.class);
  //firebase references
  private Query query;
  private ValueEventListener acQueryListener;
  private FirebaseOptions options;
  private DatabaseReference acDatabase;
  //other object references
  private FileInputStream serviceAccount;
  private ArrayList<ZAdventure> adventureList = new ArrayList<>();
  private String testStr;

  public ACSpeechlet() {
    counter = 0;
    adventureList = new ArrayList<>();
    try {
      // Fetch the service account key JSON file contents
      serviceAccount = new FileInputStream("ACServiceAccount.json");

      // Initialize the app with a service account, granting admin privileges
      options = new FirebaseOptions.Builder()
          .setCredentials(GoogleCredentials.fromStream(serviceAccount))
          .setDatabaseUrl("https://alexa-adventure-creator-ca7c9.firebaseio.com/")
          .build();
      FirebaseApp.initializeApp(options);
      // Initialize the firebase reference
      acDatabase = FirebaseDatabase.getInstance().getReference("testForBryan");


    } catch (IOException ioe) {
      System.out.println("Cannot find file");
    }
  }


  @Override
  public void onSessionStarted(SpeechletRequestEnvelope<SessionStartedRequest> requestEnvelope) {
      log.info("onSessionStarted requestId={}, sessionId={}", requestEnvelope.getRequest().getRequestId(),
              requestEnvelope.getSession().getSessionId());

      // any initialization logic goes here
      // Test query
      System.out.println("in onSessionStarted...");



  }

  @Override
  public SpeechletResponse onLaunch(SpeechletRequestEnvelope<LaunchRequest> requestEnvelope) {
      log.info("onLaunch requestId={}, sessionId={}", requestEnvelope.getRequest().getRequestId(),
              requestEnvelope.getSession().getSessionId());
      System.out.println("Starting adventure creator...");

      //wait for firebase to return
      try {
        testStr = "test failed";
        CountDownLatch latch = new CountDownLatch(1);
        //query = acDatabase.child("adventures").orderByChild("userid");
        System.out.println("attempting asycn query call in thread...");
        acDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot dataSnapshot) {
            System.out.println("async call");
            testStr = dataSnapshot.getValue(String.class);
            // for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
            //   ZAdventure zAdventure = postSnapshot.getValue(ZAdventure.class);
            //   adventureList.add(zAdventure);
            //   System.out.println("added adventure");
            // }

            latch.countDown();
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {
              // Getting Post failed, log a message
              System.out.println("something went wrong...database error");
              // ...
              latch.countDown();
          }//end onCancelled
        });//end value listener
        latch.await();
      } catch (InterruptedException ie) {
        System.out.println("Interruption");
      }

      return getWelcomeResponse();
  }

  @Override
  public SpeechletResponse onIntent(SpeechletRequestEnvelope<IntentRequest> requestEnvelope) {
      IntentRequest request = requestEnvelope.getRequest();
      log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
              requestEnvelope.getSession().getSessionId());

      Intent intent = request.getIntent();
      String intentName = (intent != null) ? intent.getName() : null;

    if ("GameChoiceIntent".equals(intentName)) {
      // String outputStr;
      // if (adventureList.size() > 0) {
      //   ZAdventure curAdventure = adventureList.get(counter);
      //   outputStr = curAdventure.getName() + " " + curAdventure.getDescription();
      //   counter =  (counter % adventureList.size()) + 1;
      // } else {
      //   outputStr = "Waiting for data";
      // }

      return getAskResponse("GameChoice", testStr);
    } else if ("AMAZON.HelpIntent".equals(intentName)) {
          // Create the plain text output.
          String helpStr = "Adventure Creator lets you choose you " +
            "experience a choose your own adventure story, or " +
            "design your own stories using the mobile app available for Android.";
          return getAskResponse("AMAZON.HelpIntent", helpStr);
      } else if ("AMAZON.StopIntent".equals(intentName)) {
          return getAskResponse("AMAZON.StopIntent", "Goodbye");
      } else if ("AMAZON.CancelIntent".equals(intentName)) {
          return getAskResponse("AMAZON.CancelIntent", "Goodbye");
      } else {
          String outputStr = "Sorry, you mumble too much.";
          return getAskResponse("Error", outputStr);
      }

  }

  @Override
  public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope) {
      log.info("onSessionEnded requestId={}, sessionId={}", requestEnvelope.getRequest().getRequestId(),
              requestEnvelope.getSession().getSessionId());
      // any cleanup logic goes here
  }

  /**
   * Creates and returns a {@code SpeechletResponse} with a welcome message.
   *
   * @return SpeechletResponse spoken and visual response for the given intent
   */
  private SpeechletResponse getWelcomeResponse() {
      String speechText = "Welcome to Adventure Creator. Do you want to play a game?";
      return getAskResponse("Welcome", speechText);
  }

  /**
   * Helper method that creates a card object.
   * @param title title of the card
   * @param content body of the card
   * @return SimpleCard the display card to be sent along with the voice response.
   */
  private SimpleCard getSimpleCard(String title, String content) {
      SimpleCard card = new SimpleCard();
      card.setTitle(title);
      card.setContent(content);

      return card;
  }

  /**
   * Helper method for retrieving an OutputSpeech object when given a string of TTS.
   * @param speechText the text that should be spoken out to the user.
   * @return an instance of SpeechOutput.
   */
  private PlainTextOutputSpeech getPlainTextOutputSpeech(String speechText) {
      PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
      speech.setText(speechText);

      return speech;
  }

  /**
   * Helper method that returns a reprompt object. This is used in Ask responses where you want
   * the user to be able to respond to your speech.
   * @param outputSpeech The OutputSpeech object that will be said once and repeated if necessary.
   * @return Reprompt instance.
   */
  private Reprompt getReprompt(OutputSpeech outputSpeech) {
      Reprompt reprompt = new Reprompt();
      reprompt.setOutputSpeech(outputSpeech);

      return reprompt;
  }

  /**
   * Helper method for retrieving an Ask response with a simple card and reprompt included.
   * @param cardTitle Title of the card that you want displayed.
   * @param speechText speech text that will be spoken to the user.
   * @return the resulting card and speech text.
   */
  private SpeechletResponse getAskResponse(String cardTitle, String speechText) {
      SimpleCard card = getSimpleCard(cardTitle, speechText);
      PlainTextOutputSpeech speech = getPlainTextOutputSpeech(speechText);
      Reprompt reprompt = getReprompt(speech);

      return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }
}

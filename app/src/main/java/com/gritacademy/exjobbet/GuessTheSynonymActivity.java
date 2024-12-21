package com.gritacademy.exjobbet;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GuessTheSynonymActivity extends AppCompatActivity {

    private TextView wordTextView;
    private Button option1Button, option2Button, option3Button, option4Button, nextQuestionButton, giveUpButton;
    private TextView scoreTextView;

    private int score = 0;
    private String correctAnswer;
    private FirebaseFirestore firestore;
    private List<DocumentSnapshot> wordDocuments;
    private int currentQuestionIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guess_the_synonym);

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance();
        wordDocuments = new ArrayList<>();

        // Initialize Views
        wordTextView = findViewById(R.id.wordTextView);
        option1Button = findViewById(R.id.option1Button);
        option2Button = findViewById(R.id.option2Button);
        option3Button = findViewById(R.id.option3Button);
        option4Button = findViewById(R.id.option4Button);
        nextQuestionButton = findViewById(R.id.nextQuestionButton);
        giveUpButton = findViewById(R.id.giveUpButton); // Initialize Give Up button
        scoreTextView = findViewById(R.id.scoreTextView);

        // Fetch words from Firestore
        fetchFlashcards();

        // Set click listeners for answer buttons
        option1Button.setOnClickListener(v -> checkAnswer(option1Button.getText().toString()));
        option2Button.setOnClickListener(v -> checkAnswer(option2Button.getText().toString()));
        option3Button.setOnClickListener(v -> checkAnswer(option3Button.getText().toString()));
        option4Button.setOnClickListener(v -> checkAnswer(option4Button.getText().toString()));

        // Set listener for Next Question button
        nextQuestionButton.setOnClickListener(v -> loadNextQuestion());

        // Set listener for Give Up button
        // Set listener for Give Up button
        giveUpButton.setOnClickListener(v -> {
            // Show the "Returning to Game Modes..." message
            Toast.makeText(this, "Returning to Game Modes...", Toast.LENGTH_SHORT).show();

            // Get the current user's UID from FirebaseAuth
            FirebaseAuth auth = FirebaseAuth.getInstance();
            String uid = auth.getCurrentUser().getUid();

            // Fetch the username from Firestore
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(uid)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot userDocument = task.getResult();
                            if (userDocument.exists()) {
                                String username = userDocument.getString("username");

                                // Create a new leaderboard entry with timestamp, score, and username
                                long timestamp = System.currentTimeMillis();  // Get the current timestamp
                                saveLeaderboardEntry(uid, username, timestamp, score);

                            } else {
                                Log.e("Firestore", "No user found with UID: " + uid);
                            }
                        } else {
                            Log.e("Firestore", "Error getting username: ", task.getException());
                        }
                    });

            finish();
        });

    }
    private void saveLeaderboardEntry(String uid, String username, long timestamp, int score) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Prepare the leaderboard entry
        Map<String, Object> leaderboardEntry = new HashMap<>();
        leaderboardEntry.put("username", username);
        leaderboardEntry.put("score", score);

        // First, try to set the document if it's new, without the timestamp
        db.collection("leaderboard")
                .document(uid)
                .get() // Check if the document already exists
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot documentSnapshot = task.getResult();
                        if (documentSnapshot.exists()) {
                            // Document already exists, so we update the timestamp
                            leaderboardEntry.put("timestamp", FieldValue.serverTimestamp());
                            db.collection("leaderboard")
                                    .document(uid)
                                    .update(leaderboardEntry)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("Firestore", "Leaderboard entry updated successfully for UID: " + uid);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("Firestore", "Error updating leaderboard entry: ", e);
                                    });
                        } else {
                            // Document doesn't exist, so create a new document without timestamp
                            leaderboardEntry.put("timestamp", FieldValue.serverTimestamp()); // Add timestamp only when creating
                            db.collection("leaderboard")
                                    .document(uid)
                                    .set(leaderboardEntry)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("Firestore", "Leaderboard entry saved successfully for UID: " + uid);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("Firestore", "Error saving leaderboard entry: ", e);
                                    });
                        }
                    } else {
                        Log.e("Firestore", "Error checking for document existence: ", task.getException());
                    }
                });
    }

    private void fetchFlashcards() {
        Log.d("Firestore", "Fetching flashcards...");

        firestore.collection("flashcards").get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                Log.d("Firestore", "Fetched document: " + doc.getId());
                                Log.d("Firestore", "Word: " + doc.getString("word"));

                                Object synonymsObj = doc.get("synonyms");

                                if (synonymsObj instanceof List) {
                                    List<String> synonyms = (List<String>) synonymsObj;
                                    Log.d("Firestore", "Synonyms: " + synonyms);
                                } else {
                                    Log.e("Firestore", "Synonyms field is not a List for document: " + doc.getId());
                                }
                            }

                            wordDocuments.addAll(querySnapshot.getDocuments());
                            Log.d("Firestore", "Documents fetched successfully. Total docs: " + querySnapshot.size());

                            runOnUiThread(() -> loadNextQuestion());
                        } else {
                            Log.e("Firestore", "No flashcards found!");
                            Toast.makeText(this, "No flashcards found!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e("Firestore", "Failed to fetch data: " + task.getException().getMessage());
                        Toast.makeText(this, "Failed to fetch data: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean answeredCorrectly = false;

    private void loadNextQuestion() {
        if (wordDocuments.isEmpty()) {
            Log.d("Game", "No more questions available!");
            Toast.makeText(this, "No more questions available!", Toast.LENGTH_SHORT).show();
            return;
        }

        Random random = new Random();
        DocumentSnapshot document = wordDocuments.get(random.nextInt(wordDocuments.size()));

        String word = document.getString("word");

        Object synonymsObj = document.get("synonyms");
        List<String> synonyms = null;

        if (synonymsObj instanceof List) {
            synonyms = (List<String>) synonymsObj;
            Log.d("Game", "Synonyms: " + synonyms);
        } else {
            Log.e("DataError", "'synonyms' field is not a List. Found: " + synonymsObj);
        }

        if (word == null || synonyms == null || synonyms.isEmpty()) {
            Log.e("DataError", "Missing or invalid 'synonyms' for word: " + word);
            return;
        }

        correctAnswer = synonyms.get(0);

        Log.d("Game", "Word: " + word + ", Correct Answer: " + correctAnswer);

        List<String> incorrectOptions = new ArrayList<>();

        // Collect incorrect options from other words (not the current word)
        while (incorrectOptions.size() < 3) {
            DocumentSnapshot randomDoc = wordDocuments.get(random.nextInt(wordDocuments.size()));
            String randomWord = randomDoc.getString("word");

            if (!randomWord.equals(word)) {  // Ensure we're not picking synonyms of the same word
                List<String> randomSynonyms = (List<String>) randomDoc.get("synonyms");

                if (randomSynonyms != null && !randomSynonyms.isEmpty()) {
                    String randomSynonym = randomSynonyms.get(0);
                    if (!randomSynonym.equals(correctAnswer) && !incorrectOptions.contains(randomSynonym)) {
                        incorrectOptions.add(randomSynonym);
                    }
                }
            }
        }

        // Add the correct answer to the options
        List<String> options = new ArrayList<>(incorrectOptions);
        options.add(correctAnswer);
        Collections.shuffle(options);

        wordTextView.setText(word);
        option1Button.setText(options.get(0));
        option2Button.setText(options.get(1));
        option3Button.setText(options.get(2));
        option4Button.setText(options.get(3));

        Log.d("Game", "Options: " + options);

        answeredCorrectly = false;
    }


    private void checkAnswer(String selectedAnswer) {
        Log.d("Game", "Selected answer: " + selectedAnswer);
        if (selectedAnswer.equals(correctAnswer)) {
            score++;
            answeredCorrectly = true;
            Toast.makeText(this, "Correct!", Toast.LENGTH_SHORT).show();
            loadNextQuestion();
        } else {
            Toast.makeText(this, "Wrong! Point is lost", Toast.LENGTH_SHORT).show();
            score--;
        }
        scoreTextView.setText("Score: " + score);

        nextQuestionButton.setOnClickListener(v -> {
            if (!answeredCorrectly) {
                Toast.makeText(this, "You lost a point! Moving to next question.", Toast.LENGTH_SHORT).show();
                score--;
                scoreTextView.setText("Score: " + score);
            }
            loadNextQuestion();
        });
    }
}




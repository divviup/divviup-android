package org.divviup.sampleapp;

import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.util.Log;
import android.view.View;

import org.divviup.android.Client;
import org.divviup.android.TaskId;
import org.divviup.sampleapp.databinding.ActivityMainBinding;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        this.executorService = Executors.newSingleThreadExecutor();
        ExecutorService executorService = this.executorService;

        binding.button.setOnClickListener(view -> {
            Editable leaderEditable = binding.leaderEndpoint.getText();
            if (leaderEditable == null) {
                Snackbar.make(view, "Leader endpoint URL is required", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.leaderEndpoint)
                        .show();
                return;
            }
            String leaderString = leaderEditable.toString();
            URI leaderUri;
            try {
                leaderUri = new URI(leaderString);
            } catch (URISyntaxException e) {
                Snackbar.make(view, "Invalid leader endpoint URL", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.leaderEndpoint)
                        .show();
                return;
            }

            Editable helperEditable = binding.helperEndpoint.getText();
            if (helperEditable == null) {
                Snackbar.make(view, "Helper endpoint URL is required", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.helperEndpoint)
                        .show();
                return;
            }
            String helperString = helperEditable.toString();
            URI helperUri;
            try {
                helperUri = new URI(helperString);
            } catch (URISyntaxException e) {
                Snackbar.make(view, "Invalid helper endpoint URL", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.helperEndpoint)
                        .show();
                return;
            }

            Editable taskIdEditable = binding.taskId.getText();
            if (taskIdEditable == null) {
                Snackbar.make(view, "Task ID is required", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.helperEndpoint)
                        .show();
                return;
            }
            String taskIdString = taskIdEditable.toString();
            TaskId taskId;
            try {
                taskId = TaskId.parse(taskIdString);
            } catch (IllegalArgumentException e) {
                Snackbar.make(view, "Invalid task ID", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.taskId)
                        .show();
                return;
            }

            String timePrecisionString = binding.timePrecision.getText().toString();
            long timePrecision;
            try {
                timePrecision = Long.parseLong(timePrecisionString);
            } catch (NumberFormatException e) {
                Snackbar.make(view, "Invalid time precision", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.taskId)
                        .show();
                return;
            }

            int index = binding.radioGroup.getCheckedRadioButtonId();
            if (index == -1) {
                Snackbar.make(view, "Select true or false", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.radioGroup)
                        .show();
                return;
            }
            boolean measurement = index == R.id.trueRadioButton;

            executorService.submit(
                    new ReportSubmissionJob(view, leaderUri, helperUri, taskId, timePrecision, measurement)
            );
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.executorService.shutdown();
    }

    private static class ReportSubmissionJob implements Runnable {
        private static final String TAG = "ReportSubmissionJob";

        private final View view;
        private final URI leaderEndpoint, helperEndpoint;
        private final TaskId taskId;
        private final long timePrecisionSeconds;
        private final boolean measurement;

        public ReportSubmissionJob(
                View view,
                URI leaderEndpoint,
                URI helperEndpoint,
                TaskId taskId,
                long timePrecisionSeconds,
                boolean measurement
        ) {
            this.view = view;
            this.leaderEndpoint = leaderEndpoint;
            this.helperEndpoint = helperEndpoint;
            this.taskId = taskId;
            this.timePrecisionSeconds = timePrecisionSeconds;
            this.measurement = measurement;
        }

        public void run() {
            Handler handler = new Handler(Looper.getMainLooper());

            Client client = Client.createPrio3Count(leaderEndpoint, helperEndpoint, taskId, timePrecisionSeconds);
            try {
                client.sendMeasurement(measurement);
                handler.post(
                        () -> Snackbar.make(view, "Success!", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.button)
                        .show()
                );
            } catch (IOException e) {
                Log.e(TAG, "upload failed", e);
                handler.post(
                        () -> Snackbar.make(view, "Error uploading report", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.button)
                        .show()
                );
            }
        }
    }
}

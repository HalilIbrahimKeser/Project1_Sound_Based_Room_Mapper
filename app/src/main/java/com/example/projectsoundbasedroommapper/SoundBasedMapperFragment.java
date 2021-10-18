package com.example.projectsoundbasedroommapper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.projectsoundbasedroommapper.classes.RoomView;
import com.example.projectsoundbasedroommapper.classes.SoundGraphView;
import com.example.projectsoundbasedroommapper.classes.XYCoordinates;
import com.example.projectsoundbasedroommapper.fft.RealDoubleFFT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class SoundBasedMapperFragment extends Fragment implements SensorEventListener {

    private SensorManager mSensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private Sensor accelerometer;
    private Sensor magneticField;
    private TextView tvRotationMatrix;
    private TextView tvOrientationAngles;
    private ImageView ivRoom;
    private ImageView ivGraph;
    private Canvas roomCanvas;
    private Bitmap bmRoom;
    private Bitmap bmGraph;
    private Canvas graphCanvas;
    private Timer timer;
    private boolean isRunning = false;
    private Button btSoundPlayer;
    private RoomView roomView;
    private SoundGraphView soundGraphView;
    private MediaRecorder recorder;
    private static String fileName = null;
    private MediaPlayer recordedMediaPlayer;
    private ToneGenerator tone;

    private int blockSize = 256; //for fft
    private RealDoubleFFT fft;
    private AudioRecord audioRecord;
    int frequency = 8000;
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private RecordAudio recordTask;

    //NYTT
    int timeStep;
    ArrayList<XYCoordinates> soundGraphCoordinates;
    double[] afterFFT;
    private double beforeFFT;



    public SoundBasedMapperFragment() {
    }

    public static SoundBasedMapperFragment newInstance() {
        SoundBasedMapperFragment fragment = new SoundBasedMapperFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sound_based_mapper, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            mSensorManager.registerListener(this, accelerometer, mSensorManager.SENSOR_DELAY_NORMAL);
        }
        magneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            mSensorManager.registerListener(this, magneticField, mSensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btSoundPlayer = view.findViewById(R.id.playSound);

        fft = new RealDoubleFFT(blockSize);

        fileName = requireContext().getFilesDir().getAbsolutePath();
        fileName += "/audiorecordtest.3gp";

        boolean hasMic = checkMicAvailability();
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        tvOrientationAngles = view.findViewById(R.id.tvOrientationAngles);
        tvRotationMatrix = view.findViewById(R.id.tvRotationMatrix);

        ivRoom = view.findViewById(R.id.ivRoom);
        bmRoom = Bitmap.createBitmap(1500, 1500, Bitmap.Config.ARGB_8888);
        roomCanvas = new Canvas(bmRoom);

        ivGraph = view.findViewById(R.id.ivGraph);
        ivGraph.setScaleY(-1);
        bmGraph = Bitmap.createBitmap(410, 150, Bitmap.Config.ARGB_8888);
        graphCanvas = new Canvas(bmGraph);

        ivRoom.setImageBitmap(bmRoom);
        ivGraph.setImageBitmap(bmGraph); //nytt halil

        roomView = new RoomView(requireActivity());
        roomView.draw(roomCanvas);

        soundGraphView = new SoundGraphView(requireActivity());
        soundGraphView.draw(graphCanvas);

        btSoundPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRunning) {
                    if (hasMic) {
                        //ask for recording permissions
                        askForRecordingPermission();
                    } else {
                        Toast.makeText(requireActivity(),
                                "This feature will only play a sound without a microphone. Please enjoy this tone.",
                                Toast.LENGTH_SHORT).show();
                        playSound();
                    }
                } else {
                    stopProgram();
                }
            }
        });
    }

    public boolean checkMicAvailability() {
        if (requireActivity().getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE)) {
            return true;
        } else {
            return false;
        }
    }

    public void askForRecordingPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
            //performAction(...);
            startProgram();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // In an educational UI, explain to the user why your app requires this
            // permission for a specific feature to behave as expected. In this UI,
            // include a "cancel" or "no thanks" button that allows the user to
            // continue using your app without granting the permission.
            //showInContextUI(...);
            Toast.makeText(requireActivity(), "Needs dialog.", Toast.LENGTH_SHORT).show();
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                    startProgram();
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                    Toast.makeText(requireActivity(), "This button will only play a sound.", Toast.LENGTH_SHORT).show();
                }
            });

    private void startProgram() {
        isRunning = true;
        btSoundPlayer.setText("Stop");
        recordSound();
        playSound();
    }

    private void stopProgram() {
        isRunning = false;
        btSoundPlayer.setText("Play");
        stopRecording();
        stopSound();
    }

    private void playSound() {
        if (recordedMediaPlayer != null) {
            recordedMediaPlayer.release();
            recordedMediaPlayer = null;
        }
        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        tone.startTone(ToneGenerator.TONE_SUP_PIP);
    }

    private void stopSound() {
        tone.stopTone();
        tone.release();
        tone = null;
    }

    private void recordSound() {
        recordTask = new RecordAudio();
        recordTask.execute();
        startDrawing();
    }

    private void playRecorded() {
        recordedMediaPlayer = new MediaPlayer();
        try {
            recordedMediaPlayer.setDataSource(fileName);
            recordedMediaPlayer.prepare();
            recordedMediaPlayer.start();
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Mediaplayer has failed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        recordTask.cancel(true);
    }

    private void stopPlayingRecorded() {
        recordedMediaPlayer.stop();
        recordedMediaPlayer.release();
        recordedMediaPlayer = null;

    }

    @Override
    public void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        tvOrientationAngles.setText("");
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(sensorEvent.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(sensorEvent.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
        }
        updateOrientationAngles();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void updateOrientationAngles() {
        SensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading);

        SensorManager.getOrientation(rotationMatrix, orientationAngles);

        for (int i = 0; i < orientationAngles.length; i++) {
            tvOrientationAngles.append(i + ": " + orientationAngles[i] + "\n");
        }
    }

    public void startDrawing() {
        if (timer == null) {
            timer = new Timer();
            try {
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        drawGraph();
                        //Log.d("bufferReadResult, Before toTransform:", String.valueOf(beforeFFT));
                        //Log.d("bufferReadResult, After toTransform:", String.valueOf(afterFFT));
                    }
                }, 0, 1000);
            } catch (IllegalArgumentException | IllegalStateException iae) {
                iae.printStackTrace();
            }
        } else {
            timer = null;
        }
    }

    public void drawGraph(){
        graphCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
        soundGraphView.draw(graphCanvas);
        ivGraph.setImageBitmap(bmGraph);
    }

    @SuppressLint("StaticFieldLeak")
    private class RecordAudio extends AsyncTask<Void, double[], Void> {
        @Override
        protected Void doInBackground(Void... arg0) {

            try {
                int bufferSize = AudioRecord.getMinBufferSize(frequency,
                        channelConfiguration, audioEncoding);
                //Har checkpermission ved trykk av knappen før denne
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    Toast.makeText(requireContext(), "Permission not given.", Toast.LENGTH_SHORT).show();
                }
                AudioRecord audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, frequency,
                        channelConfiguration, audioEncoding, bufferSize);

                short[] buffer = new short[blockSize];
                double[] toTransform = new double[blockSize];
                //double[] transformed = new double[blockSize];


                audioRecord.startRecording();

                // started = true; hopes this should true before calling
                // following while loop

                while (isRunning) {
                    int bufferReadResult = audioRecord.read(buffer, 0,
                            blockSize);

                    for (int i = 0; i < blockSize && i < bufferReadResult; i++) {
                        toTransform[i] = (double) buffer[i] / 32768.0; // signed
                    }
                    beforeFFT = Arrays.stream(toTransform).min().orElse(Double.NaN);

                    fft.ft(toTransform);
                    publishProgress(toTransform);

                    //afterFFT = Arrays.stream(toTransform).min().orElse(Double.NaN);
                    afterFFT = toTransform;
                    ArrayList<XYCoordinates> graphCoordinates = new ArrayList<XYCoordinates>();
                    for (int i = 0; i < afterFFT.length; i++ ) {

                        XYCoordinates coordinate = new XYCoordinates((float) ((i+20)*1.4), (float) afterFFT[i]*80);
                        graphCoordinates.add(coordinate);

                    }
                    soundGraphView.setGraphCoordinates(graphCoordinates);
                }

                audioRecord.stop();

            } catch (Throwable t) {
                t.printStackTrace();
                Log.e("AudioRecord", "Recording Failed");
            }
            return null;
        }
    }
}
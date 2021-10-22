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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.projectsoundbasedroommapper.classes.RoomView;
import com.example.projectsoundbasedroommapper.classes.SoundGraphView;
import com.example.projectsoundbasedroommapper.classes.XYCoordinates;
import com.example.projectsoundbasedroommapper.fft.RealDoubleFFT;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class SoundBasedMapperFragment extends Fragment implements SensorEventListener {

    private SensorManager mSensorManager;

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] proximityReading = new float[1];

    private Canvas roomCanvas;
    private TextView tvDistance;
    private TextView tvMagneticFieldAngles;
    private TextView tvDistanceLightProximity;
    private TextView tvDirection;
    private ImageView ivRoom;
    private ImageView ivGraph;

    private Bitmap bmRoom;
    private Bitmap bmGraph;
    private Canvas graphCanvas;

    private Timer timer;
    private boolean isRunning = false;
    private int timeStep = 0;
    private Handler handler;

    private Button btSoundPlayer;
    private RoomView roomView;
    private SoundGraphView soundGraphView;
    private MediaPlayer recordedMediaPlayer;
    private ToneGenerator tone;
    private final int blockSize = 256; //for fft

    File fileSpike;
    FileWriter streamSpike;
    private static final String FILE_NAME_SPIKE = "fileNameSpikeMaxValue.txt";

    //Source: https://stackoverflow.com/questions/5511250/capturing-sound-for-analysis-and-visualizing-frequencies-in-android
    private RealDoubleFFT fft;
    int frequency = 8000;
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private RecordAudio recordTask;
    private double currentFFTSpike = 0.0;
    private double currentDistance;
    private ArrayList<Double> fftAverageHolder;

    public SoundBasedMapperFragment() {
    }

    public static SoundBasedMapperFragment newInstance() {
        return new SoundBasedMapperFragment();
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
        Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        Sensor magneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            mSensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_NORMAL);
        }
        Sensor proximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximity != null) {
            mSensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        btSoundPlayer = view.findViewById(R.id.playSound);

        fft = new RealDoubleFFT(blockSize);

        String pathSpikeMaxValue = requireContext().getFilesDir().getAbsolutePath();
        fileSpike = new File(pathSpikeMaxValue, FILE_NAME_SPIKE);

        boolean hasMic = checkMicAvailability();
        mSensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        tvMagneticFieldAngles = view.findViewById(R.id.tvMagneticFieldAngles);
        tvDistanceLightProximity = view.findViewById(R.id.tvDistanceLightProximity);
        tvDistance = view.findViewById(R.id.tvDistance);
        tvDirection = view.findViewById(R.id.tvDirection);

        ivRoom = view.findViewById(R.id.ivRoom);
        bmRoom = Bitmap.createBitmap(1500, 1500, Bitmap.Config.ARGB_8888);
        roomCanvas = new Canvas(bmRoom);

        ivGraph = view.findViewById(R.id.ivGraph);
        ivGraph.setScaleY(-1);
        bmGraph = Bitmap.createBitmap(420, 250, Bitmap.Config.ARGB_8888);
        graphCanvas = new Canvas(bmGraph);

        ivRoom.setImageBitmap(bmRoom);
        ivGraph.setImageBitmap(bmGraph); //nytt halil

        roomView = new RoomView(requireActivity());
        roomView.draw(roomCanvas);

        soundGraphView = new SoundGraphView(requireActivity());
        soundGraphView.draw(graphCanvas);

        btSoundPlayer.setOnClickListener(view1 -> {
            if (!isRunning) {
                if (hasMic) {
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
        });
    }

    @SuppressLint("StaticFieldLeak")
    private class RecordAudio extends AsyncTask<Void, double[], Void> {
        //Source: https://stackoverflow.com/questions/5511250/capturing-sound-for-analysis-and-visualizing-frequencies-in-android
        @Override
        protected Void doInBackground(Void... arg0) {

            try {
                if (fftAverageHolder == null){
                    fftAverageHolder = new ArrayList<>();
                }
                int bufferSize = AudioRecord.getMinBufferSize(frequency,
                        channelConfiguration, audioEncoding);
                //Placed here because it is persisting. By this part, permission is already checked with the method askForRecordingPermission.
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(requireContext(), "Permission not given.", Toast.LENGTH_SHORT).show();
                }
                AudioRecord audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, frequency,
                        channelConfiguration, audioEncoding, bufferSize);

                short[] buffer = new short[blockSize];
                double[] toTransform = new double[blockSize];

                audioRecord.startRecording();

                while (isRunning) {
                    if(fftAverageHolder.size() == 30){
                        fftAverageHolder.clear();
                    }
                    int bufferReadResult = audioRecord.read(buffer, 0,
                            blockSize);

                    for (int i = 0; i < blockSize && i < bufferReadResult; i++) {
                        toTransform[i] = (double) buffer[i] / 32768.0; // signed
                    }
                    fft.ft(toTransform);
                    publishProgress(toTransform);
                    ArrayList<XYCoordinates> graphCoordinates = new ArrayList<XYCoordinates>();
                    ArrayList<Double> frequency40 = new ArrayList<Double>();
                    for (int i = 0; i < toTransform.length; i++) {
                        XYCoordinates coordinate = new XYCoordinates((float) ((i + 8) * 1.55), (float) (toTransform[i] * 10) + 125);
                        if (i > 39 && i < 51) {
                            frequency40.add(toTransform[i]*100);
                        }
                        graphCoordinates.add(coordinate);
                    }
                    double max40 = Collections.max(frequency40); //spike i 40 området, brukes til kalibrering, lagres med distanseverdi
                    fftAverageHolder.add(max40);
                    if (fftAverageHolder.size() == 30){
                        double total = 0.0;
                        for(int i = 0; i < 30; i++){
                            total += fftAverageHolder.get(i);
                        }
                        currentFFTSpike = total/30;
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

    private void saveDistance(double distance) {
        try {
            streamSpike = new FileWriter(fileSpike, true); //false for å slette gamle verdier i filen
            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date(System.currentTimeMillis());
            String now = dateFormat.format(date);
            streamSpike.write(now + "- distance: " + distance + "-type: fft");
            streamSpike.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                streamSpike.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean checkMicAvailability() {
        return requireActivity().getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }

    //Source: https://developer.android.com/training/permissions/requesting#java
    public void askForRecordingPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) {
            startProgram();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(requireActivity(), "Needs dialog.", Toast.LENGTH_SHORT).show();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    //Source: https://developer.android.com/training/permissions/requesting#java
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startProgram();
                } else {
                    Toast.makeText(requireActivity(), "This button will only play a sound.", Toast.LENGTH_SHORT).show();
                }
            });

    private void startProgram() {
        isRunning = true;
        btSoundPlayer.setText("Stop");
        try {
            PrintWriter pw = new PrintWriter(fileSpike);
            pw.close();
        } catch (FileNotFoundException e) {
            Log.d("File empty", "error");
        }
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
        tone.startTone(ToneGenerator.TONE_DTMF_1);
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

    private void stopRecording() {
        recordTask.cancel(true);
    }

    @Override
    public void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        int sensorType = sensorEvent.sensor.getType();
        switch (sensorType) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(sensorEvent.values, 0, magnetometerReading,
                        0, magnetometerReading.length);
                @SuppressLint("DefaultLocale")
                String sensorResultMagneticFields = String.format("Magnetic Field: North: %.2f , East: %.2f , Up: %.2f ", magnetometerReading[0], magnetometerReading[1], magnetometerReading[2]);
                tvMagneticFieldAngles.setText(sensorResultMagneticFields);
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(sensorEvent.values, 0, accelerometerReading,
                        0, accelerometerReading.length);
            case Sensor.TYPE_PROXIMITY:
                if (sensorEvent.values[0] != 0.0f) {
                    proximityReading[0] = sensorEvent.values[0];
                }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    public void startDrawing() {
        if (timer == null) {
            timer = new Timer();
            try {
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        drawGraph();
                        timeStep+=1;
                        if(timeStep%20 == 0){
                            RoomView.updateCircles(magnetometerReading, accelerometerReading, proximityReading);
                            currentDistance = calculateDistance(currentFFTSpike);
                            saveDistance(currentDistance);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                   updateDistanceText(currentDistance);
                                   tvDirection.setText(roomView.getDirection());
                                }
                            });
                            Log.d("Current pos", currentDistance + "");
                            drawObject(currentDistance);
                        }
                    }
                }, 0, 50);
            } catch (IllegalArgumentException | IllegalStateException iae) {
                iae.printStackTrace();
            }
        } else {
            timer = null;
        }
    }

    private void drawObject(double currentDistance){
        if (!roomView.isStarted()){
            roomView.setStarted(true);
        }
        roomView.setZ_value((float) currentDistance);
        roomCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
        roomView.draw(roomCanvas);
        ivRoom.setImageBitmap(bmRoom);
    }

    @SuppressLint("SetTextI18n")
    private void updateDistanceText(double distance) {
        String stringDistanceSound = String.format("Sound distance: %.3f cm", distance);
        String stringDistanceLight = String.format("Light distance: %.3f cm", proximityReading[0]);
        tvDistanceLightProximity.setText(stringDistanceSound);
        tvDistance.setText(stringDistanceLight);
    }

    public void drawGraph() {
        soundGraphView.setClose(currentDistance < 0.5);
        graphCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
        soundGraphView.draw(graphCanvas);
        ivGraph.setImageBitmap(bmGraph);
    }

    private double calculateDistance(double fft ){
        //hardcoded based on calibration curve that is made with a trendline in Excel

        //based on frequency in area 40, power equation based on calibration values
        //x = 0.0007y^2 - 0.0601y + 12.151
        //get x = distance
        //fft = y
        //double x = (0.00014 * Math.pow(fft, 2)) - (0.0601 * fft) + 12.151;
        double x = (0.00007 * Math.pow(fft, 2)) - (0.0601*fft) + 12.151;
        if (x < 0.0){
            x = 0.0;
        }
        return x;
    }
}
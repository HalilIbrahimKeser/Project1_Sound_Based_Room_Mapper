package com.example.projectsoundbasedroommapper.classes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class RoomView extends View {


    private Paint paintCircle, paintBox, paintPerson;

    private static float[] magnetometerReadingUpdated;
    private static float[] accelerometerReadingUpdated;
    private static float[] proximityReadingUpdated;

    private ArrayList<XYCoordinates> objectCoordinates;

    private boolean started = false;
    private float x_value;
    private float z_value;

    public RoomView(Context context) {
        super(context);
        init();
    }

    public RoomView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public RoomView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public static void updateCircles(float[] magnetometerReading, float[] accelerometerReading, float[] proximityReading) {
        magnetometerReadingUpdated = magnetometerReading;
        accelerometerReadingUpdated = accelerometerReading;
        proximityReadingUpdated = proximityReading;
    }

    public void init(){
        paintCircle = new Paint();
        paintPerson = new Paint();
        paintBox = new Paint();
        paintCircle.setColor(Color.BLUE);
        paintCircle.setStyle(Paint.Style.FILL);
        paintCircle.setStrokeWidth(5);
        paintPerson.setColor(Color.RED);
        paintPerson.setStyle(Paint.Style.FILL);
        paintPerson.setStrokeWidth(5);
        paintBox.setColor(Color.GRAY);
        paintBox.setStyle(Paint.Style.STROKE);
        paintBox.setStrokeWidth(5);
        z_value = 100;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        float my_x_pos = canvas.getWidth()/2;
        float my_z_pos = canvas.getHeight()-300;
        x_value = my_x_pos;

        if (started){

            canvas.drawCircle(x_value, my_z_pos - (z_value * 50), 100, paintCircle);
        }
        canvas.drawCircle(my_x_pos, my_z_pos, 70, paintPerson);
        canvas.drawRect(20, 100, canvas.getWidth()-30, canvas.getHeight()-100, paintBox);

    }

    public String getDirection(){
        if(magnetometerReadingUpdated != null && accelerometerReadingUpdated != null && proximityReadingUpdated != null) {
            //Avstand fra proximity
            if (proximityReadingUpdated[0] != 0.0f){
                Log.d("proximityReadingUpdated", proximityReadingUpdated[0] + " cm");
            }

            // Peker nord,
            // Magnet 0:Nord, Magnet 1:East, Magnet 2:Up
            if (magnetometerReadingUpdated[0] > (-20) && magnetometerReadingUpdated[0] < 20 && magnetometerReadingUpdated[1] >= 15) {
                Log.d("PEKER", "Nord");
                return "North";
            } // Peker sør
            else if (magnetometerReadingUpdated[0] > (-20) && magnetometerReadingUpdated[0] < 20 && magnetometerReadingUpdated[1] <= (-15)) {
                Log.d("PEKER", "Sør");
                return "South";
            } // Peker øst
            else if (magnetometerReadingUpdated[0] < (-17) && magnetometerReadingUpdated[1] > (-15) && magnetometerReadingUpdated[1] < 15) {
                Log.d("PEKER", "Øst");
                return "East";
            }// Peker vest
            else if (magnetometerReadingUpdated[0] > 17 && magnetometerReadingUpdated[1] > (-15) && magnetometerReadingUpdated[1] < 15) {
                Log.d("PEKER", "Vest");
                return "West";
            } else{
                return "Direction";
            }
        } else{
            return "Direction is not yet ready";
        }
    }

    public float getX_value() {
        return x_value;
    }

    public void setX_value(float x_value) {
        this.x_value = x_value;
    }

    public float getZ_value() {
        return z_value;
    }

    public void setZ_value(float z_value) {
        this.z_value = z_value;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }
}

package com.example.projectsoundbasedroommapper.classes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;


public class SoundGraphView extends View {

    private Paint borderPaint, pathPaint, circlePaint;

    private Path pathBorder, pathGraph;

    private float circle_x;
    private float circle_y;
    private ArrayList<XZCoordinates> graphCoordinates;


    public SoundGraphView(Context context) {
        super(context);
        init();
    }

    public SoundGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public SoundGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }


    public void init(){
        pathBorder = new Path();
        pathGraph = new Path();

        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.GRAY);

        pathPaint = new Paint();
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setColor(Color.BLACK);

        circlePaint = new Paint();
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setColor(Color.BLUE);

        pathBorder = new Path();
        pathGraph = new Path();

    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        pathBorder.moveTo(30, canvas.getHeight()-200);
        pathBorder.lineTo(canvas.getWidth()-30, canvas.getHeight()-200);
        pathBorder.moveTo(30, 200);
        pathBorder.lineTo(canvas.getWidth()-30, 200);

        canvas.drawPath(pathBorder, borderPaint);
    }

    public void addPoint(XZCoordinates point){
        //krasjer her
        graphCoordinates.add(point);
    }


    public float getCircle_x() {
        return circle_x;
    }

    public void setCircle_x(float circle_x) {
        this.circle_x = circle_x;
    }

    public float getCircle_y() {
        return circle_y;
    }

    public void setCircle_y(float circle_y) {
        this.circle_y = circle_y;
    }

}

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
    private ArrayList<XYCoordinates> graphCoordinates;


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
        graphCoordinates = new ArrayList<XYCoordinates>();
        pathBorder = new Path();
        pathGraph = new Path();

        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.GRAY);

        pathPaint = new Paint();
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setColor(Color.MAGENTA);

        circlePaint = new Paint();
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setColor(Color.BLUE);

        pathBorder = new Path();
        pathGraph = new Path();

    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        pathGraph.reset();
        pathBorder.moveTo(30, canvas.getHeight()-200);
        pathBorder.lineTo(canvas.getWidth()-30, canvas.getHeight()-200);
        pathBorder.moveTo(30, 200);
        pathBorder.lineTo(canvas.getWidth()-30, 200);

        for (int i = 1; i < graphCoordinates.size(); i++){
            pathGraph.moveTo(graphCoordinates.get(i-1).getX(), graphCoordinates.get(i-1).getY());
            pathGraph.lineTo(graphCoordinates.get(i).getX(), graphCoordinates.get(i).getY());
            canvas.drawPath(pathGraph, pathPaint);
        }

        canvas.drawPath(pathBorder, borderPaint);

        invalidate();
    }

    public ArrayList<XYCoordinates> getGraphCoordinates() {
        return graphCoordinates;
    }

    public void setGraphCoordinates(ArrayList<XYCoordinates> graphCoordinates) {
        this.graphCoordinates = graphCoordinates;
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

package com.example.projectsoundbasedroommapper.classes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;


public class SoundGraphView extends View {

    private Paint borderPaint, pathPaint, circlePaint;

    private Path pathBorder, pathGraph;

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
}

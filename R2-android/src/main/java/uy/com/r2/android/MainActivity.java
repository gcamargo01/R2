package uy.com.r2.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import uy.com.r2.core.Boot;
import uy.com.r2.core.SvcCatalog;

public class MainActivity extends Activity {
    
    private TextView textView;
    private Button startButton;
    private Button stopButton;
    
    OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick( View v) {
                if( startButton == v) {
                    Log.v( "MainActivity", "start button");
                    try {
                        Boot.start( 8015, "http://localhost:8016");
                    } catch( Exception x) { 
                        Log.e( "MainActivity", "" + x, x);
                    }
                } else if( stopButton == v) {
                    Log.v( "MainActivity", "stop Button");
                    SvcCatalog.getCatalog().shutdown();
                }
            }
        };
    
    @Override
    public void onCreate( Bundle savedInstanceState) {
        Log.d( "MainActivity", "onCreate");
        super.onCreate( savedInstanceState);
        setContentView( R.layout.main);
        startButton = (Button)findViewById( R.id.start_button);
        stopButton = (Button)findViewById( R.id.stop_button);
        textView = (TextView)findViewById( R.id.text_view);
        startButton.setOnClickListener( clickListener);
        stopButton.setOnClickListener( clickListener);
        // configure Log4j appender
        TextViewAppender ap = new TextViewAppender( textView);
        ap.setLayout( new PatternLayout( "%d{HH:mm:ss} [%t] %-5p %c - %m%n"));
        ap.setThreshold( Level.TRACE);
        Logger root = Logger.getRootLogger();
        root.setLevel( Level.TRACE);
        root.addAppender( ap);
    }
   

}

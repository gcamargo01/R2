/* StringBuilderAppender.java */
package uy.com.r2.android;

import android.app.Activity;
import android.util.Log;
import android.widget.TextView;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

/** Log4Java Text View Appender.
 */
public class TextViewAppender extends AppenderSkeleton {
    private static final int SIZE = 20480;
    private final Activity act;
    private final TextView tv;
    private StringBuilder sb = new StringBuilder();
    
    public TextViewAppender( Activity act, TextView tv) {
        this.act = act;
        this.tv = tv;
    }

    @Override
    protected void append( LoggingEvent event) {
        String m = layout.format( event);
        Log.d( event.getThreadName(), m);
        sb.append( m);
        if( sb.length() > SIZE) {
            sb = sb.replace( 0, sb.length() - SIZE, "...\n");
        }
        act.runOnUiThread( new Runnable() {
            @Override
            public void run() {
                tv.setText( sb);
            }
        });
    }

    @Override
    public boolean requiresLayout() {
        return true;
    }

    @Override
    public void close() {
    }

}

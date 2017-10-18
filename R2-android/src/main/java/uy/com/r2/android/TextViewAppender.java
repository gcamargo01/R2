/* StringBuilderAppender.java */
package uy.com.r2.android;

import android.util.Log;
import android.widget.TextView;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

/** 
 */
public class TextViewAppender extends AppenderSkeleton {
    StringBuilder sb = new StringBuilder();
    TextView tv;
    
    public TextViewAppender( TextView tv) {
        this.tv = tv;
    }

    @Override
    protected void append( LoggingEvent event) {
        String m = layout.format( event);
        Log.d( event.getThreadName(), m);
        sb.append( m);
        if( sb.length() > 5000) {
            sb = sb.replace( 0, sb.length() - 5000, "");
        }
        tv.setText( sb.toString());
    }

    @Override
    public boolean requiresLayout() {
        return true;
    }

    @Override
    public void close() {
    }

}

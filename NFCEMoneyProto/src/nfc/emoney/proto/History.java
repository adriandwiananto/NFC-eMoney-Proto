package nfc.emoney.proto;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class History extends Activity{
	// Initialize the array
    String[] logArray = { "1st Log Amount", "2nd Log Amount", "3rd Log Amount", "4th Log Amount" };
 
    // Declare the UI components
    private ListView LogLV;
 
    private ArrayAdapter arrayAdapter;
 
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history);
 
        // Initialize the UI components
        LogLV = (ListView) findViewById(R.id.LVH);
        // For this moment, you have ListView where you can display a list.
        // But how can we put this data set to the list?
        // This is where you need an Adapter
 
        // context - The current context.
        // resource - The resource ID for a layout file containing a layout 
                // to use when instantiating views.
        // From the third parameter, you plugged the data set to adapter
        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, logArray);
 
        // By using setAdapter method, you plugged the ListView with adapter
        LogLV.setAdapter(arrayAdapter);
    }
}

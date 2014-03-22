package nfc.emoney.proto;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

import nfc.emoney.proto.userdata.AppData;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class Login extends Activity implements OnClickListener {
	TextView loginDebug;
	
	private final static String TAG = "{class} Login";
	private AppData appdata;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);
		
		appdata = new AppData(this);
		
		Date d = new Date(appdata.getLATS()*1000);
		SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US);
		
		Log.d(TAG,"Write debug textview!");
		loginDebug = (TextView)findViewById(R.id.tLoginDebug);
		loginDebug.setText("ACCN : "+String.valueOf(appdata.getACCN()));
		loginDebug.append("\nHWID : "+String.valueOf(appdata.getIMEI()));
		loginDebug.append("\nPassword : "+appdata.getPass());
		loginDebug.append("\nBalance : "+appdata.getBalance());
		loginDebug.append("\nLast Sync : "+String.valueOf(appdata.getLATS()));
		loginDebug.append("\nLast Sync(Readable) : "+ df.format(d));
		
		((Button)findViewById(R.id.bLoginProceed)).setOnClickListener(this);
		((Button)findViewById(R.id.bLoginCancel)).setOnClickListener(this);
	}

	@Override
    protected void onResume() {
        super.onResume();
    }
	
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
			case R.id.bLoginProceed:
				break;
			case R.id.bLoginCancel:
				finish();
				break;
		}
	}
}

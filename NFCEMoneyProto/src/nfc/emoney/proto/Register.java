package nfc.emoney.proto;

import org.json.JSONException;
import org.json.JSONObject;

import nfc.emoney.proto.misc.Network;
import nfc.emoney.proto.userdata.AppData;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class Register extends Activity implements OnClickListener {
	private final static String TAG = "{class} Register";
	private AppData appdata;
	private String ACCN, NewPass, ConfPass;
	private static final String HOSTNAME = "http://emoney-server.herokuapp.com/register.json";
	private ProgressBar spinner;
			
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.register);
		
//		appdata = new AppData(getApplicationContext());
		appdata = new AppData(this);
		appdata.setIMEI();
		
		spinner = (ProgressBar)findViewById(R.id.pReg);
		spinner.setVisibility(View.GONE);
		
		((Button)findViewById(R.id.bRegConfirm)).setOnClickListener(this);
		((Button)findViewById(R.id.bRegCancel)).setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
			case R.id.bRegConfirm:
				spinner.setVisibility(View.VISIBLE);
				Log.d(TAG,"Starts register");
				
				((Button)findViewById(R.id.bRegConfirm)).setEnabled(false);
				((Button)findViewById(R.id.bRegCancel)).setEnabled(false);
				ACCN = ((EditText)findViewById(R.id.eRegACCN)).getText().toString();
				NewPass = ((EditText)findViewById(R.id.eRegNewPass)).getText().toString();
				ConfPass = ((EditText)findViewById(R.id.eRegConfPass)).getText().toString();
				
//				if(ACCN.length() != 15){
				if(ACCN.length() < 12){
					Toast.makeText(getApplicationContext(), "Incorrect Account ID length" , Toast.LENGTH_SHORT).show();
					return;
				}
				
				if(NewPass.length() < 1){
					Toast.makeText(getApplicationContext(), "Please input password", Toast.LENGTH_SHORT).show();
					return;
				}
				
				if(NewPass.compareTo(ConfPass) != 0){
					Toast.makeText(getApplicationContext(), "Incorrect password confirmation", Toast.LENGTH_SHORT).show();
					return;
				}
				
				JSONObject json = new JSONObject();
				try {
					json.put("HWID", appdata.getIMEI());
					json.put("ACCN", ACCN);
					((TextView)findViewById(R.id.tRegDebug)).setText("JSON send:"+json.toString());
				} catch (NumberFormatException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				Log.d(TAG,"Create asynctask");
				Network net = new Network(Register.this ,getApplicationContext(), HOSTNAME, json, NewPass, ACCN);
				net.execute();
				Log.d(TAG,"Finish main thread");
				break;
			case R.id.bRegCancel:
				finish();
				break;
		}
	}
}

package nfc.emoney.proto;

import nfc.emoney.proto.userdata.AppData;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class Login extends Activity implements OnClickListener {
	TextView loginDebug;
	private AppData appdata;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);
		
		appdata = new AppData(this);
		
		loginDebug = (TextView)findViewById(R.id.tLoginDebug);
		loginDebug.setText("ACCN:"+String.valueOf(appdata.getACCN()));
//		loginDebug.setText("\nHWID:"+String.valueOf(appdata.getIMEI()));
//		loginDebug.setText("\nPassword:"+appdata.getPass());
//		loginDebug.setText("\nBalance:"+String.valueOf(appdata.getBalance()));
//		loginDebug.setText("\nLast Sync:"+String.valueOf(appdata.getIMEI()));
		
		((Button)findViewById(R.id.bLoginProceed)).setOnClickListener(this);
		((Button)findViewById(R.id.bLoginCancel)).setOnClickListener(this);
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

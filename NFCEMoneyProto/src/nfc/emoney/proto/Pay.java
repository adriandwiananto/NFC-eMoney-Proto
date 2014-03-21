package nfc.emoney.proto;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Pay extends Activity implements OnClickListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pay);
		
		((Button) findViewById(R.id.bPaySend)).setOnClickListener(this);
		((Button) findViewById(R.id.bPayCancel)).setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
			case R.id.bPaySend:
				break;
			case R.id.bPayCancel:
				finish();
				break;
		}
	}
}

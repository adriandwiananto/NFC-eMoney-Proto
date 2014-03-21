package nfc.emoney.proto;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Option extends Activity implements OnClickListener{

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.option);
		
		((Button)findViewById(R.id.bOptionProceed)).setOnClickListener(this);
		((Button)findViewById(R.id.bOptionCancel)).setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
			case R.id.bOptionProceed:
				break;
			case R.id.bOptionCancel:
				finish();
				break;
		}
	}
}

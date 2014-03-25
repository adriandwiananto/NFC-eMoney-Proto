package nfc.emoney.proto;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import nfc.emoney.proto.crypto.AES256cipher;
import nfc.emoney.proto.misc.Converter;
import nfc.emoney.proto.userdata.AppData;
import nfc.emoney.proto.userdata.LogDB;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class History extends Activity{
	private final static String TAG = "{class} History";
	ListView lv;
	ProgressBar pHistory;
	TextView tMsg;
	private Cursor cur;
	private LogDB db;
	private byte[] aes_key, log_key, balance_key;
	private List<String[]> colorList;
	private boolean error;
	private AppData appdata;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history);

        error = false;
        
        Intent myIntent = getIntent();
		aes_key = myIntent.getByteArrayExtra("aesKey");
		log_key = myIntent.getByteArrayExtra("logKey");
		balance_key = myIntent.getByteArrayExtra("balanceKey");
	
		appdata = new AppData(this);
		colorList = new LinkedList<String[]>();
		
        lv = (ListView)findViewById(R.id.LVH);
        pHistory = (ProgressBar)findViewById(R.id.pHistory);
        tMsg = (TextView)findViewById(R.id.tHistoryMsg);
        
        pHistory.setVisibility(View.VISIBLE);
		lv.setVisibility(View.GONE);
		tMsg.setVisibility(View.GONE);
        refreshListView();
    }

	private void refreshListView() {
		// TODO Auto-generated method stub

        Runnable runnable = new Runnable(){
    		public void run(){
    			Message msg = handler.obtainMessage();
    			db = new LogDB(getApplicationContext(), log_key);
    	        cur = db.getLogBlob();
    			while((cur.isAfterLast() == false) && (error == false)){
    				byte[] encryptedLog = cur.getBlob(cur.getColumnIndex(db.getLOGColumnName()));
    				Log.d(TAG,"encryptedLog: "+Converter.byteArrayToHexString(encryptedLog));
    				int rowNum = cur.getInt(cur.getColumnIndex(db.getIDColumnName()));
    				byte[] iv = Arrays.copyOfRange(encryptedLog, 32, 48);
    				byte[] logOnly = Arrays.copyOfRange(encryptedLog, 0, 32);
    				
    				try{
    					byte[] decryptedLog = AES256cipher.decrypt(iv, log_key, logOnly);
    					Log.d(TAG,"decryptedLog: "+Converter.byteArrayToHexString(decryptedLog));
    					byte[] NUM = Arrays.copyOfRange(decryptedLog, 0, 3);
    					byte PT = decryptedLog[3];
    					byte[] binaryID = Arrays.copyOfRange(decryptedLog, 4, 8);
    					byte[] accnM = Arrays.copyOfRange(decryptedLog, 8, 14);
    					byte[] accnP = Arrays.copyOfRange(decryptedLog, 14, 20);
    					byte[] amnt = Arrays.copyOfRange(decryptedLog, 20, 24);
    					byte[] TS = Arrays.copyOfRange(decryptedLog, 24, 28);
    					byte STAT = decryptedLog[28];
    					byte CNL = decryptedLog[29];
    					
    					if(rowNum != Converter.byteArrayToInteger(NUM)){
    						error = true;
    					}
    					
    					if(appdata.getACCN() != Converter.byteArrayToLong(accnP)){
    						error = true;
    					}
    					
    					if(PT != 1){
    						error = true;
    					}
    					
    					Date d = new Date(Converter.byteArrayToLong(TS)*1000);
    					SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US);
    					Log.d(TAG,"timestamp: "+df.format(d));

    					if(Converter.byteArrayToLong(accnM) == 0){
    						colorList.add(new String[]{String.valueOf(Converter.byteArrayToInteger(amnt)), df.format(d)});
    					}
    					
    					cur.moveToNext();
    					
    				}catch(Exception e){
    					e.printStackTrace();
    				}
    			}
    			
//    	        colorList.add(new String[] { "Red", "the color red" });
//    	        colorList.add(new String[] { "Green", "the color green" });
//    	        colorList.add(new String[] { "Blue", "the color blue" });
    			
    			Bundle bundle = new Bundle();
    			bundle.putBoolean("error", error);
    			bundle.putInt("logRow", cur.getCount());
    			Log.d(TAG,"logRow:"+String.valueOf(cur.getCount()));
    			msg.setData(bundle);
    			handler.sendMessage(msg);
    		}
    	};
    	Thread historyListView = new Thread(runnable);
    	historyListView.start();
	}
	
	@SuppressLint("HandlerLeak")
	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle bundle = msg.getData();
			boolean error = bundle.getBoolean("error");
			int logRow = bundle.getInt("logRow");
			
//	        colorList.add(new String[] { "Red", "the color red" });
//	        colorList.add(new String[] { "Green", "the color green" });
//	        colorList.add(new String[] { "Blue", "the color blue" });
			
			if(error == false){
				if(logRow > 0){
					pHistory.setVisibility(View.GONE);
					lv.setVisibility(View.VISIBLE);
					tMsg.setVisibility(View.GONE);
			        ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(getApplicationContext(), android.R.layout.simple_list_item_2, android.R.id.text1, colorList) {
			 
			            @Override
			            public View getView(int position, View convertView, ViewGroup parent) {
			 
			                // Must always return just a View.
			                View view = super.getView(position, convertView, parent);
			 
			                // If you look at the android.R.layout.simple_list_item_2 source, you'll see
			                // it's a TwoLineListItem with 2 TextViews - text1 and text2.
			                //TwoLineListItem listItem = (TwoLineListItem) view;
			                String[] entry = colorList.get(position);
			                TextView text1 = (TextView) view.findViewById(android.R.id.text1);
			                TextView text2 = (TextView) view.findViewById(android.R.id.text2);
			                text1.setText(entry[0]);
			                text2.setText(entry[1]);
			                text1.setTextColor(Color.BLACK);
			                text2.setTextColor(Color.DKGRAY);
			                return view;
			            }
			        };
			        
			        lv.setAdapter(adapter);
				} else {
					tMsg.setText("Log Empty");
					pHistory.setVisibility(View.GONE);
					lv.setVisibility(View.GONE);
					tMsg.setVisibility(View.VISIBLE);
				}
			} else {
				tMsg.setText(getApplicationContext().getString(R.string.tHistoryError));
				pHistory.setVisibility(View.GONE);
				lv.setVisibility(View.GONE);
				tMsg.setVisibility(View.VISIBLE);
			}
		}
	};
}

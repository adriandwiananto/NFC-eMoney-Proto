package nfc.emoney.proto.misc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nfc.emoney.proto.Login;
import nfc.emoney.proto.R;
import nfc.emoney.proto.userdata.AppData;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

public class Network extends AsyncTask<Void, Void, JSONObject> {

	private final static String TAG = "{class} Network";
	private final static int REGISTRATION_MODE = 99;
	private final static int LOG_SYNC_MODE = 49;
	
	private String hostname;
	private String data,header,logs;
	private String newPassword, newACCN, newIMEI;
	
	private JSONObject jobj_response;
	
	private int param_mode;
	private int error;
	
	private Context ctx;
	private AppData appdata;
	private Activity parentActivity;
	
	public Network(Activity parent, Context context, String host, JSONObject jobj, String NewPass, String ACCNtoSend, String HWID){
		ctx = context;
		parentActivity = parent;
		hostname = host;
		newPassword = NewPass;
		newACCN = ACCNtoSend;
		newIMEI = HWID;
		error = 0;
		
		param_mode = REGISTRATION_MODE;
		appdata = new AppData(ctx);
		
		if(param_mode == REGISTRATION_MODE){
			data = jobj.toString();
		}
		
		else if(param_mode == LOG_SYNC_MODE){
			JSONObject jheader;
			JSONObject jlogs;
			
			try {
				jheader = jobj.getJSONObject("header");
				header = jheader.toString();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			try {
				jlogs = jobj.getJSONObject("logs");
				logs = jlogs.toString();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Override
	protected JSONObject doInBackground(Void... params) {
		try {
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(hostname);

			// Add your data
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
			if(param_mode == LOG_SYNC_MODE){
				nameValuePairs.add(new BasicNameValuePair("header", header));
				nameValuePairs.add(new BasicNameValuePair("logs", logs));
			}
			else if(param_mode == REGISTRATION_MODE){
				nameValuePairs.add(new BasicNameValuePair("data", data));
			}
			else{
				return null;
			}
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

			// Execute HTTP Post Request
			HttpResponse response = httpclient.execute(httppost);

			BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
			String json = reader.readLine();
			JSONTokener tokener = new JSONTokener(json);
			JSONObject finalResult = new JSONObject(tokener);
			Log.d(TAG, "return:"+finalResult.toString());
			
			return finalResult;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected void onPreExecute() {
		Toast.makeText(ctx, "Registration Starts", Toast.LENGTH_LONG).show();
	}
	 
	@Override
	protected void onPostExecute(JSONObject result) {
		if (result.length() != 0) {
			// do something
			jobj_response = result;
//			Toast.makeText(ctx, "Response:"+jobj_response.toString(), Toast.LENGTH_LONG).show();
			if(param_mode == REGISTRATION_MODE){
				String responseStatus;
				try {
					responseStatus = jobj_response.getString("result");
					
					if(responseStatus.compareTo("Error") == 0){
						Toast.makeText(ctx, "Registration failed!!", Toast.LENGTH_LONG).show();
						appdata.deleteAppData();
						error = 3;
					}
					else{
						String responseKey = jobj_response.getString("key");
						byte[] aesKey = new byte[responseKey.length()/2];
						aesKey = Converter.hexStringToByteArray(responseKey.toString());
						Log.d(TAG,"aesKey byte array:"+Arrays.toString(aesKey));
						
						Log.d(TAG,"Start writing shared pref");
						appdata.setACCN(Long.parseLong(newACCN));
						appdata.setPass(newPassword);
						appdata.deriveKey(newPassword, newIMEI);
						appdata.setKey(aesKey);						
						appdata.setLATS(System.currentTimeMillis() / 1000);
						appdata.setBalance(100000);
						Log.d(TAG,"Finish writing shared pref");
						
						Toast.makeText(ctx, "Registration Success", Toast.LENGTH_LONG).show();
						
						((ProgressBar)parentActivity.findViewById(R.id.pReg)).setVisibility(View.INVISIBLE);
						ctx.startActivity((new Intent(ctx, Login.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
						parentActivity.finish();
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					appdata.deleteAppData();
				}
			}
			else if(param_mode == LOG_SYNC_MODE){
				
			}
			else{
				error = 4;
				Log.d(TAG,"WTF -- What a Terible Failure. Param_mode not LOG or REG!");
			}
			
		} else {
			// error occured
			error = 1;
			Log.d(TAG,"Response is empty JSON Object");
			appdata.deleteAppData();
		}
	}
    
	public String getData(){
		return data;
	}
	
	public String getHeader(){
		return header;
	}
	
	public String getLogs(){
		return logs;
	}
	
	public int getError(){
		return error;
	}
	
	public JSONObject getResponse(){
		return jobj_response;
	}
}

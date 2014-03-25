package nfc.emoney.proto.userdata;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

import nfc.emoney.proto.crypto.AES256cipher;
import nfc.emoney.proto.misc.Converter;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class LogDB extends SQLiteOpenHelper{
	protected static final String TAG = "{class} LogDB";
	private static String DBLNAME = "log";	
	private static String DBLCREATE = "CREATE TABLE TransLog (_id INTEGER PRIMARY KEY AUTOINCREMENT, _log BLOB NOT NULL)";
	private static String TABLE = "TransLog";
	private static String CL_ID = "_id";
	private static String CL_LOG = "_log";
	
	private byte[]logKey;
	
	public LogDB(Context c, byte[] log_key){
		super(c, DBLNAME, null, 1);
		logKey = log_key;
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(DBLCREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub
		
	}
	
	public long insertLastTransToLog(byte[] transPacket){
		Log.d(TAG,"transaction packet: "+Converter.byteArrayToHexString(transPacket));
		SQLiteDatabase db = getWritableDatabase();
		ContentValues CV =  new ContentValues();
		
		long logNum = this.rowCountLog() + 1;
		byte[] num = new byte[3];
		System.arraycopy(ByteBuffer.allocate(8).putLong(logNum).array(), 5, num, 0, 3);
		byte payloadType = transPacket[1];
		byte[] binaryID = new byte[4];
		Arrays.fill(binaryID, (byte) 0);
		byte[] accnM = new byte[6];
		Arrays.fill(accnM, (byte) 0);
		byte[] accnP = new byte[6];
		System.arraycopy(transPacket, 7, accnP, 0, 6);
		byte[] amount = new byte[4];
		System.arraycopy(transPacket, 17, amount, 0, 4);
		byte[] timeStamp = new byte[4];
		System.arraycopy(transPacket, 13, timeStamp, 0, 4);
		byte status = 0;
		byte cancel = 0;
		
		byte[] plainLog = new byte[30];
		ByteBuffer target = ByteBuffer.wrap(plainLog);
		target.put(num);
		target.put(payloadType);
		target.put(binaryID);
		target.put(accnM);
		target.put(accnP);
		target.put(amount);
		target.put(timeStamp);
		target.put(status);
		target.put(cancel);
		Log.d(TAG,"plain log:"+Converter.byteArrayToHexString(plainLog));

		String ciphertext;
		byte[] encryptedLogArray = new byte[32];
		byte[] iv = new byte[16];
		byte[] logIv= new byte[48];

		SecureRandom random = new SecureRandom();		
		random.nextBytes(iv);
		
		System.arraycopy(iv, 0, logIv, 32, 16);
		
		try {
			encryptedLogArray = AES256cipher.encrypt(iv, logKey, plainLog);
			System.arraycopy(encryptedLogArray, 0, logIv, 0, 32);
			ciphertext = Converter.byteArrayToHexString(logIv);
			Log.d(TAG,"Log to write to database:"+ciphertext);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		CV.put(CL_LOG, logIv);
		long rowid = db.insert(TABLE, null, CV);
		//db.close();
		return rowid;
	}
	
	public void changeLogKey(byte[] newLogKey){
		SQLiteDatabase db = getWritableDatabase();
		ContentValues CV =  new ContentValues();

		Cursor c = db.query(TABLE, new String[]{CL_ID, CL_LOG}, null, null, null, null, CL_ID);
		if(c != null){
			c.moveToFirst();
			while(c.isAfterLast() == false){
				byte[] encryptedLog = c.getBlob(c.getColumnIndex(this.getLOGColumnName()));
				Log.d(TAG,"encryptedLog: "+Converter.byteArrayToHexString(encryptedLog));
				int rowNum = c.getInt(c.getColumnIndex(this.getIDColumnName()));
				byte[] iv = Arrays.copyOfRange(encryptedLog, 32, 48);
				byte[] logOnly = Arrays.copyOfRange(encryptedLog, 0, 32);
				
				try{
					byte[] decryptedLog = AES256cipher.decrypt(iv, logKey, logOnly);
					Log.d(TAG,"decryptedLog: "+Converter.byteArrayToHexString(decryptedLog));
					
					byte[] newEncryptedLog = new byte[32];
					byte[] newIv = new byte[16];
					byte[] newLogIv = new byte[48];

					SecureRandom random = new SecureRandom();		
					random.nextBytes(newIv);
					
					System.arraycopy(newIv, 0, newLogIv, 32, 16);
					
					newEncryptedLog = AES256cipher.encrypt(newIv, newLogKey, decryptedLog);
					System.arraycopy(newEncryptedLog, 0, newLogIv, 0, 32);
					Log.d(TAG,"new log key:"+Converter.byteArrayToHexString(newLogKey));
					Log.d(TAG,"newly encrypted log:"+Converter.byteArrayToHexString(newLogIv));
					CV.put(CL_LOG, newLogIv);
					int updatedRow = db.update(TABLE, CV, CL_ID + "=" + rowNum, null);
					
					if(updatedRow != rowNum){
						return;
					}
					
					c.moveToNext();
					
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
			}
		}
	}
	
	public long rowCountLog(){
		SQLiteDatabase db = getReadableDatabase();
		return DatabaseUtils.queryNumEntries(db, TABLE);
	}
	
	public Cursor getLogBlob(){
		SQLiteDatabase db = getReadableDatabase();
		Cursor c = db.query(TABLE, new String[]{CL_ID, CL_LOG}, null, null, null, null, CL_ID);
		if (c != null) c.moveToFirst();
		return c;
	}
	
	public static void deleteDB(Context c){
		c.deleteDatabase(DBLNAME);
	}
	
	public String getLOGColumnName(){
		return CL_LOG;
	}

	public String getIDColumnName(){
		return CL_ID;
	}
}

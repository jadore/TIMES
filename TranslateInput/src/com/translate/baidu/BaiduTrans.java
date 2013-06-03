package com.translate.baidu;


import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

public class BaiduTrans extends Activity {

    private TextView FromText;
    private TextView ToText ;
    private Button Cancel;
    private Button Translate;

    public String text="";
    private CancelOnClickListener cListener = new CancelOnClickListener();
    private TranslateOnClickListener tListener = new TranslateOnClickListener();
    class CancelOnClickListener implements OnClickListener{

		public void onClick(View arg0) {
			FromText.setText("");
		}
	   
	}
    
    class TranslateOnClickListener implements OnClickListener{
		
		public void onClick(View v) {
		
			CharSequence fText = FromText.getText();
			//trans.translate(fText.toString());
			text = translate(fText.toString());     
		    ToText.setText(text);
		}
    }                              

    private void init(){
        FromText = (TextView) findViewById(R.id.FromText);
        ToText = (TextView) findViewById(R.id.ToText);
        Cancel = (Button) findViewById(R.id.Cancel);
        Translate = (Button) findViewById(R.id.Translate);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_baidu_trans); 
        this.init();    
        
        Cancel.setOnClickListener(cListener);
        Translate.setOnClickListener(tListener);
        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_baidu_trans, menu);
        return true;
    }
    static String TAG = "BaiduTrans";
    public String translate(String text){
		String urlApi = "http://openapi.baidu.com/public/2.0/bmt/translate";
		   
		NameValuePair clientId =new BasicNameValuePair("client_id","gd0nlRMUvn7HKgjBENxGNKqI");
		NameValuePair q =new BasicNameValuePair("q",text);
		List<NameValuePair> postParams = new ArrayList<NameValuePair>();
		postParams.add(clientId);
		postParams.add(q);	
		NameValuePair from =new BasicNameValuePair("from","auto");
		NameValuePair to =new BasicNameValuePair("to","auto");
		postParams.add(from);
		postParams.add(to);
		
		JSONObject jsonObject;
		Log.d(TAG,"HAPPY!!");
		try{
			Log.d(TAG,"WYW");
			HttpEntity httpEntity = new UrlEncodedFormEntity(postParams,HTTP.UTF_8);
			HttpPost httpPost = new HttpPost(urlApi);
			HttpClient httpClient = new DefaultHttpClient();
			httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
			httpPost.setEntity(httpEntity);
			HttpResponse response = httpClient.execute(httpPost);
			if(response.getStatusLine().getStatusCode()==200){ 
				Log.d(TAG,"WYW1");
				String strResult=EntityUtils.toString(response.getEntity());
				jsonObject =new JSONObject(strResult);
				//ToText.setText(jsonObject.toString());
				JSONArray json = jsonObject.getJSONArray("trans_result");
				String showMessage="";
				for(int i =0;i<json.length();i++){
					 JSONObject data =(JSONObject)json.get(i);
					 showMessage +=data.getString("dst");                
				}  
				return showMessage;
		    }else{ 
		    	return "unable to connect the server!";
		    } 
			}catch(Exception e){
				return e.getMessage().toString();
				
		}
    }
}
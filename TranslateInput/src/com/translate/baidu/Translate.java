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

import android.widget.Toast;

public class Translate {
	private BaiduTrans baidu;
	public void translate(String text){
		String urlApi = "http://openapi.baidu.com/public/2.0/bmt/translate";
		   
		NameValuePair clientId =new BasicNameValuePair("client_id","gd0nlRMUvn7HKgjBENxGNKqI");
		NameValuePair q =new BasicNameValuePair("q","love");
		List<NameValuePair> postParams = new ArrayList<NameValuePair>();
		postParams.add(clientId);
		postParams.add(q);	
		NameValuePair from =new BasicNameValuePair("from","auto");
		NameValuePair to =new BasicNameValuePair("to","auto");
		postParams.add(from);
		postParams.add(to);
		
		JSONObject jsonObject;
		try{
			HttpEntity httpEntity = new UrlEncodedFormEntity(postParams,HTTP.UTF_8);
			HttpPost httpPost = new HttpPost(urlApi);
			HttpClient httpClient = new DefaultHttpClient();
			httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
			httpPost.setEntity(httpEntity);
			HttpResponse response = httpClient.execute(httpPost);
			if(response.getStatusLine().getStatusCode()==200){ 
				String strResult=EntityUtils.toString(response.getEntity());
				jsonObject =new JSONObject(strResult);
				//ToText.setText(jsonObject.toString());
				JSONArray json = jsonObject.getJSONArray("trans_result");
				String showMessage="";
				for(int i =0;i<json.length();i++){
					 JSONObject data =(JSONObject)json.get(i);
					 showMessage +=data.getString("dst");                
				}  
				System.out.print(showMessage);
				Toast.makeText(baidu, showMessage, 0).show();
		    }else{ 
		    	
		    } 
			}catch(Exception e){
				
		}
    }
}

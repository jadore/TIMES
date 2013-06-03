package com.android.baidutranslateinput;


import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

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

public class CandidateView extends View {

    private static final int OUT_OF_BOUNDS = -1;
    
    
    private BaiduTranslateInput mService;
    private List<String> mSuggestions;
    private int mSelectedIndex;
    private int mTouchX = OUT_OF_BOUNDS;
    private Drawable mSelectionHighlight;
    private boolean mTypedWordValid;
    
    private Rect mBgPadding;

    private static final int MAX_SUGGESTIONS = 32;
    private static final int SCROLL_PIXELS = 20;
    
    private int[] mWordWidth = new int[MAX_SUGGESTIONS];
    private int[] mWordX = new int[MAX_SUGGESTIONS];

    private static final int X_GAP = 10;
    
    private static final List<String> EMPTY_LIST = new ArrayList<String>();

    private int mColorNormal;
    private int mColorRecommended;
    private int mColorOther;
    private int mVerticalPadding;
    private int tVerticalPadding;
    
    //Paint即画笔，在绘图过程中七大保存颜色样式等绘制信息，制定了如何绘制文本和图像，
    //Paint对象有很多设置方法，大体分为两类，一类与图像绘制有关，一类与文本绘制相关
    //两个画笔mPaint用以绘制原本候选词，TPaint用以绘画翻译后的文本
    private Paint mPaint;
    private Paint TPaint;
    
    private boolean mScrolled;
    private int mTargetScrollX;
    
    private int mTotalWidth;
    
    private GestureDetector mGestureDetector;

    /**
     * Construct a CandidateView for showing suggested words for completion.
     * @param context
     * @param attrs
     * 创建一个候选词视图，用以显示候选词
     */
    public CandidateView(Context context) {
        super(context);
        mSelectionHighlight = context.getResources().getDrawable(android.R.drawable.list_selector_background);
        mSelectionHighlight.setState(new int[] {
                android.R.attr.state_enabled,
                android.R.attr.state_focused,
                android.R.attr.state_window_focused,
                android.R.attr.state_pressed
        });
        

        Resources r = context.getResources();
        
        setBackgroundColor(r.getColor(R.color.candidate_background));
        
        //黑色
        mColorNormal = r.getColor(R.color.candidate_normal);
        //橙红色
        mColorRecommended = r.getColor(R.color.candidate_recommended);
        //灰色
        mColorOther = r.getColor(R.color.candidate_other);
        //用以控制候选词栏矩形的大小
        mVerticalPadding =r.getDimensionPixelSize(R.dimen.candidate_vertical_padding);
        tVerticalPadding =r.getDimensionPixelSize(R.dimen.candidate_vertical_padding);
        
        //得到原始输入的候选词栏
        mPaint = new Paint();
        //Paint的图形绘制相关函数
        //设置绘制颜色，RGB
        mPaint.setColor(mColorNormal);
        //设置是否使用抗锯齿功能，他会好肥较大资源，导致绘画速度变慢
        mPaint.setAntiAlias(true);
        //当画笔样式为STROKE或者FILL_OR_STROKE是，用以设置笔刷粗细度
        mPaint.setStrokeWidth(0);
        
        //设置文本相关
        //绘制文本中的文字字号大小
        mPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_font_height));
        
        
        //得到翻译后的候选词栏
        //雷同上着
        TPaint = new Paint();
        TPaint.setColor(mColorNormal);
        TPaint.setAntiAlias(true);
        TPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_font_height));
        TPaint.setStrokeWidth(0);
        //用以得到用户的触摸结果
        mGestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
            @Override
            //distancex: The distance along the X axis that has been scrolled since the last call to onScroll.
            //distancey: The distance along the Y axis that has been scrolled since the last call to onScroll.
            public boolean onScroll(MotionEvent e1, MotionEvent e2,
                    float distanceX, float distanceY) {
                mScrolled = true;
                //getScrollX():The left edge of the displayed part of your view, in pixels.
                int sx = getScrollX();
                sx += distanceX;
                if (sx < 0) {
                    sx = 0;
                }
                //getWidth():The width of your view, in pixels
                if (sx + getWidth() > mTotalWidth) {                    
                    sx -= distanceX;
                }
                mTargetScrollX = sx;
                scrollTo(sx, getScrollY());
                invalidate();
                return true;
            }
        });
        //Define whether the horizontal edges should be faded when this view is scrolled horizontally.
        setHorizontalFadingEdgeEnabled(true);
        //whether or not this View draw on its own
        setWillNotDraw(false);
        //用以设置是否使用水平与竖直放上的平移，下上学bar
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);
    }
    
    /**
     * A connection back to the service to communicate with the text field
     * @param listener
     */
    public void setService(BaiduTranslateInput listener) {
        mService = listener;
    }
    
    @Override
    public int computeHorizontalScrollRange() {
        return mTotalWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = resolveSize(50, widthMeasureSpec);
        
        // Get the desired height of the icon menu view (last row of items does
        // not have a divider below)
        Rect padding = new Rect();
        mSelectionHighlight.getPadding(padding);
        //候选词栏高度的决定语句
        final int desiredHeight = ((int)TPaint.getTextSize())+((int)mPaint.getTextSize()) + mVerticalPadding + tVerticalPadding ;
        
        // Maximum possible width and desired height
        setMeasuredDimension(measuredWidth,
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    /**
     * If the canvas is null, then only touch calculations are performed to pick the target
     * candidate.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        if (canvas != null) {
            super.onDraw(canvas);
        }
        mTotalWidth = 0;
        if (mSuggestions == null) return;
        
        if (mBgPadding == null) {
            mBgPadding = new Rect(0, 0, 0, 0);
            if (getBackground() != null) {
                getBackground().getPadding(mBgPadding);
            }
        }
        int x = 0;
        final int count = mSuggestions.size();
        //The height of your view, in pixels.
        final int height = getHeight();
        final int width = getWidth();
        final Rect bgPadding = mBgPadding;
        final Paint paint = mPaint;
        final Paint tpaint = TPaint;
        final int touchX = mTouchX;
        //The left edge of the displayed part of your view, in pixels. 
        final int scrollX = getScrollX();
        final boolean scrolled = mScrolled;
        final boolean typedWordValid = mTypedWordValid;
        final int y = (int) (((height - mPaint.getTextSize() - TPaint.getTextSize()) / 2) - mPaint.ascent() - TPaint.ascent());
        

        
        for (int i = 0; i < count; i++) {
            String suggestion = mSuggestions.get(i);
            final int wordWidth;
            float textWidth = paint.measureText(suggestion);
            final int wordWidth0 = (int) textWidth + X_GAP * 2;
            String tsuggestion;
            tsuggestion =translate(suggestion);
            float textWidth1 = paint.measureText(tsuggestion);
 
            final int wordWidth1 = (int) textWidth1 + X_GAP * 2;
            if (wordWidth0 >= wordWidth1){
            	wordWidth = wordWidth0;
            }else{
            	
            	wordWidth = wordWidth1;
            }
          

            mWordX[i] = x;
            mWordWidth[i] = wordWidth;
            paint.setColor(mColorNormal);
            tpaint.setColor(mColorNormal);
            if (touchX + scrollX >= x && touchX + scrollX < x + wordWidth && !scrolled) {
                if (canvas != null) {
                    canvas.translate(x, 0);
                    mSelectionHighlight.setBounds(0, bgPadding.top, wordWidth, height);
                    mSelectionHighlight.draw(canvas);
                    canvas.translate(-x, 0);
                }
                mSelectedIndex = i;
            }

            if (canvas != null) {
                if ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid)) {
                    paint.setFakeBoldText(true);
                    paint.setColor(mColorRecommended);
                    tpaint.setFakeBoldText(true);
                    tpaint.setColor(mColorRecommended);
                } else if (i != 0) {
                    paint.setColor(mColorOther);
                    tpaint.setColor(mColorOther);
                }
                canvas.drawLine(x , bgPadding.top, width+1, bgPadding.top, paint);
                canvas.drawText(suggestion, x + X_GAP, y+5, paint);
                paint.setColor(mColorOther); 
                canvas.drawLine(x + wordWidth + 0.5f, bgPadding.top, x + wordWidth + 0.5f, height + 1, paint);
                paint.setFakeBoldText(false);
                
                canvas.drawLine(x , y-18, width+1, y-18, paint);
                canvas.drawText(tsuggestion, x + X_GAP, y-25, tpaint);
                tpaint.setColor(mColorOther); 
                canvas.drawLine(x + wordWidth + 0.5f, bgPadding.top, x + wordWidth + 0.5f, height + 1, tpaint);
                tpaint.setFakeBoldText(false);
            }
            x += wordWidth;
        }
        mTotalWidth = x;
        if (mTargetScrollX != getScrollX()) {
            scrollToTarget();
        }
    }
    
    private void scrollToTarget() {
        int sx = getScrollX();
        if (mTargetScrollX > sx) {
            sx += SCROLL_PIXELS;
            if (sx >= mTargetScrollX) {
                sx = mTargetScrollX;
                requestLayout();
            }
        } else {
            sx -= SCROLL_PIXELS;
            if (sx <= mTargetScrollX) {
                sx = mTargetScrollX;
                requestLayout();
            }
        }
        scrollTo(sx, getScrollY());
        invalidate();
    }
    
    public void setSuggestions(List<String> suggestions, boolean completions,
            boolean typedWordValid) {
        clear();
        if (suggestions != null) {
            mSuggestions = new ArrayList<String>(suggestions);
        }
        mTypedWordValid = typedWordValid;
        scrollTo(0, 0);
        mTargetScrollX = 0;
        // Compute the total width
        onDraw(null);
        invalidate();
        requestLayout();
    }

    public void clear() {
        mSuggestions = EMPTY_LIST;
        mTouchX = OUT_OF_BOUNDS;
        mSelectedIndex = -1;
        invalidate();
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent me) {

        if (mGestureDetector.onTouchEvent(me)) {
            return true;
        }

        int action = me.getAction();
        int x = (int) me.getX();
        int y = (int) me.getY();
        mTouchX = x;

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mScrolled = false;
            invalidate();
            break;
        case MotionEvent.ACTION_MOVE:
            if (y <= 0) {
                // Fling up!?
                if (mSelectedIndex >= 0) {
                    mService.pickSuggestionManually(mSelectedIndex);
                    mSelectedIndex = -1;
                }
            }
            invalidate();
            break;
        case MotionEvent.ACTION_UP:
            if (!mScrolled) {
                if (mSelectedIndex >= 0) {
                    mService.pickSuggestionManually(mSelectedIndex);
                }
            }
            mSelectedIndex = -1;
            removeHighlight();
            requestLayout();
            break;
        }
        return true;
    }
    
    /**
     * For flick through from keyboard, call this method with the x coordinate of the flick 
     * gesture.
     * @param x
     */
    public void takeSuggestionAt(float x) {
        mTouchX = (int) x;
        // To detect candidate
        onDraw(null);
        if (mSelectedIndex >= 0) {
            mService.pickSuggestionManually(mSelectedIndex);
        }
        invalidate();
    }

    private void removeHighlight() {
        mTouchX = OUT_OF_BOUNDS;
        invalidate();
    }
    
	 public String translate(String text){
			String urlApi = "http://openapi.baidu.com/public/2.0/bmt/translate";			   
			NameValuePair clientId =new BasicNameValuePair("client_id","gd0nlRMUvn7HKgjBENxGNKqI");
			NameValuePair q =new BasicNameValuePair("q",text);
			List<NameValuePair> postParams = new ArrayList<NameValuePair>();
			postParams.add(clientId);
			postParams.add(q);	
			NameValuePair from =new BasicNameValuePair("from","en");
			NameValuePair to =new BasicNameValuePair("to","zh");
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
					return showMessage;
			    }else{ 
			    	return "unable to connect the server!";
			    } 
				}catch(Exception e){
					return e.getMessage().toString();
					
			}
	    }
	 
}

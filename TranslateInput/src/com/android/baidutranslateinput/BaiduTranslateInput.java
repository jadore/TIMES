package com.android.baidutranslateinput;

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

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.text.method.MetaKeyKeyListener;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class BaiduTranslateInput extends InputMethodService 
implements KeyboardView.OnKeyboardActionListener {
	static final boolean DEBUG = false;
	
	/**
	 * PROCESS_HARD_KEYS:
	* This boolean indicates the optional example code for performing
	* processing of hard keys in addition to regular text generation
	* from on-screen interaction.
	* 这个bool值，表示，该示例程序，不仅可以处理常见的屏幕操作，同事适用于按键操作  
	* It would be used for input methods that
	* perform language translations (such as converting text entered on 
	* a QWERTY keyboard to Chinese), 
	* 它将被用作语言翻译，比如将在Qwerty键盘输入的文字转化为中文
	* but may not be used for input methods
	* that are primarily intended to be used for on-screen text entry.
	* 但是可能不适用于那些最初就是为了屏幕文本输入的输入法
	* 
	*/
	static final boolean PROCESS_HARD_KEYS = true;
	
	/*A view that renders a virtual Keyboard. 
	 * It handles rendering of keys and detecting key presses and touch movements.
	 * 呈现一个虚拟键盘。它处理渲染键和按键和触摸运动检测。
	 */
	private KeyboardView mInputView;
	/*
	 * 候选词视图
	 */
	private CandidateView mCandidateView;
	/*
	 * Information about a single text completion that an editor has reported to an input method. 
	 * 编辑器提交给输入法的文本完成信息
	 */
	private CompletionInfo[] mCompletions;
	/*
	 * 字符串构造
	 */
	private StringBuilder mComposing = new StringBuilder();
	private boolean mPredictionOn;
	private boolean mCompletionOn;
	private int mLastDisplayWidth;
	private boolean mCapsLock;
	private long mLastShiftTime;
	private long mMetaState;
	
	private LatinKeyboard mSymbolsKeyboard;
	private LatinKeyboard mSymbolsShiftedKeyboard;
	private LatinKeyboard mQwertyKeyboard;
	
	private LatinKeyboard mCurKeyboard;
	
	private String mWordSeparators;
	
	/**
	* Main initialization of the input method component.  Be sure to call
	* to super class.
	* 输入法组件的主要初始化程序，确保他电泳super类
	*/
	@Override 
	public void onCreate() {
		super.onCreate();
		mWordSeparators = getResources().getString(R.string.word_separators);
	}
	
	/**
	* This is the point where you can do all of your UI initialization.  It
	* is called after creation and any configuration change.
	* 这是你所有的完成UI初始化的位置，它将在创建及配置改变后被调用
	*/
	@Override
	public void onInitializeInterface() {
		if (mQwertyKeyboard != null) {
		    // Configuration changes can happen after the keyboard gets recreated,
			//在键盘被重新创建后，配置可以改变
		    // so we need to be able to re-build the keyboards if the available
		    // space has changed.
			//所以，当一点的空间改变后，我们需要重新的创建键盘
		    int displayWidth = getMaxWidth();
		    if (displayWidth == mLastDisplayWidth) return;
		    mLastDisplayWidth = displayWidth;
		}
		mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
		mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
		mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);
	}
	
	/**
	* Called by the framework when your view for creating input needs to
	* be generated.  
	* 当你的需要输入的视图时，被框架调用
	* This will be called the first time your input method
	* is displayed, and every time it needs to be re-created such as due to
	* a configuration change.
	* 当你的输入法显示的第一时间就会被调用。并且每当配置发生变化时都将会被调用
	*/
	@Override 
	public View onCreateInputView() {
		mInputView = (KeyboardView) getLayoutInflater().inflate(
		        R.layout.baidu_translate_input, null);
		mInputView.setOnKeyboardActionListener(this);
		mInputView.setKeyboard(mQwertyKeyboard);
		return mInputView;
	}
	
	/**
	* Called by the framework when your view for showing candidates needs to
	* be generated, like {@link #onCreateInputView}.
	* 当你的视图需要显示候选词的时候被框架调用，如OnCreateInputView方法
	*/
	@Override public View onCreateCandidatesView() {
		mCandidateView = new CandidateView(this);
		mCandidateView.setService(this);
		return mCandidateView;
	}

	/**
	* This is the main point where we do our initialization of the input method
	* to begin operating on an application.  
	* 这是我们初始化输入法后，开始在应用程序上操作的关键点
	* At this point we have been
	* bound to the client, and are now receiving all of the detailed information
	* about the target of our edits.
	* 这是个好，我们已经将控制权交给了client，并且在接受所有我们编辑器的具体信息
	*/
	@Override 
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting);
		
		// Reset our state.  We want to do this even if restarting, because
		// the underlying state of the text editor could have changed in any way.
		//重置状态，即使重启，也要这么做，应为文件编辑器可是实时变化
		mComposing.setLength(0);
		updateCandidates();
		
		if (!restarting) {
		    // Clear shift states.
			//清楚shift状态
		    mMetaState = 0;
		}
		
		mPredictionOn = true;
		mCompletionOn = false;
		mCompletions = null;
		
		// We are now going to initialize our state based on the type of
		// text being edited.
		//我们将初始化我们的状态，基于正在被编辑的文字
		switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
		    case EditorInfo.TYPE_CLASS_NUMBER:
		    case EditorInfo.TYPE_CLASS_DATETIME:
		        // Numbers and dates default to the symbols keyboard, with
		        // no extra features.
		    	//日期跟数字默认为symbol键盘兵器没有其他特性
		        mCurKeyboard = mSymbolsKeyboard;
		        break;
		        
		    case EditorInfo.TYPE_CLASS_PHONE:
		        // Phones will also default to the symbols keyboard, though
		        // often you will want to have a dedicated phone keyboard.
		    	//电话也被默认为symbol键盘，尽管经常你想要要一个phone键盘级九宫数字键盘
		        mCurKeyboard = mSymbolsKeyboard;
		        break;
		        
		    case EditorInfo.TYPE_CLASS_TEXT:
		        // This is general text editing.  We will default to the
		        // normal alphabetic keyboard, and assume that we should
		        // be doing predictive text (showing candidates as the
		        // user types).
		    	//这是通常的文字便捷，我们默认为这是通常的alphabetic键盘，并且我们见做文字预测
		        mCurKeyboard = mQwertyKeyboard;
		        mPredictionOn = true;
		        
		        // We now look for a few special variations of text that will
		        // modify our behavior.
		        //我们正在寻找一些特别的文字的Variation，聪哥修改我们的操作
		        int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
		        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
		                variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
		            // Do not display predictions / what the user is typing
		            // when they are entering a password.
		        	//不显示预测，并且当输入密码是不显示
		            mPredictionOn = false;
		        }
		        
		        if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS 
		                || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
		                || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
		            // Our predictions are not useful for e-mail addresses
		            // or URIs.
		        	//我们的预测不适用于email地址跟url
		            mPredictionOn = false;
		        }
		        
		        if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
		            // If this is an auto-complete text view, then our predictions
		            // will not be shown and instead we will allow the editor
		            // to supply their own.  We only show the editor's
		            // candidates when in fullscreen mode, otherwise relying
		            // own it displaying its own UI.
		        	//如果是紫铜补全文本视图，我们的预测见不被显示，并且我们允许编辑器提供自己的预测功能
		        	//我们仅仅显示编辑器的候选器，当时全屏模式的时候，否则的话让编辑器在自己的ui'中显示
		            mPredictionOn = false;
		            mCompletionOn = isFullscreenMode();
		        }
		        
		        // We also want to look at the current state of the editor
		        // to decide whether our alphabetic keyboard should start out
		        // shifted.
		        //我们也想差选编辑器状态从而决定我们的alp键盘是否应该启动
		        updateShiftKeyState(attribute);
		        break;
		        
		    default:
		        // For all unknown input types, default to the alphabetic
		        // keyboard with no special features.
		    	//对于其他所有位置的输入类型，默认alph键盘，不附带其他任何特征
		        mCurKeyboard = mQwertyKeyboard;
		        updateShiftKeyState(attribute);
		}
		
		// Update the label on the enter key, depending on what the application
		// says it will do.
		//根据应用程序的行为，更新label在enter键上
		mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
	}

	/**
	* This is called when the user is done editing a field.  We can use
	* this to reset our state.
	* 这个健在用户完成编辑室被调用，从而我们可以使用这个方法来重置我们的状态
	*/
	@Override 
	public void onFinishInput() {
		super.onFinishInput();
		
		// Clear current composing text and candidates
		//清除但却的文本跟候选词.
		mComposing.setLength(0);
		updateCandidates();
		
		// We only hide the candidates window when finishing input on
		// a particular editor, to avoid popping the underlying application
		// up and down if the user is entering text into the bottom of
		// its window.
		//我们在完成编辑器上完成输入后隐藏候选词视窗，从而避免因为用户要在他的窗口底部输入文字时，是单签应用程序呢上下跳动
		setCandidatesViewShown(false);
		
		mCurKeyboard = mQwertyKeyboard;
		if (mInputView != null) {
		    mInputView.closing();
		}
	}
	
	@Override 
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		super.onStartInputView(attribute, restarting);
		// Apply the selected keyboard to the input view.
		//应用选定的键盘到输入视图中
		mInputView.setKeyboard(mCurKeyboard);
		mInputView.closing();
	}
	
	/**
	* Deal with the editor reporting movement of its cursor.
	* 处理编辑器报告他的焦点移动问题
	*/
	@Override 
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
	    int newSelStart, int newSelEnd,
	    int candidatesStart, int candidatesEnd) {
		super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
		        candidatesStart, candidatesEnd);
		
		// If the current selection in the text view changes, we should
		// clear whatever candidate text we have.
		//如果当前在文本视图中的选择变化时，我们应当清除所有的候选词
		if (mComposing.length() > 0 && (newSelStart != candidatesEnd
		        || newSelEnd != candidatesEnd)) {
		    mComposing.setLength(0);
		    updateCandidates();
		    InputConnection ic = getCurrentInputConnection();
		    if (ic != null) {
		        ic.finishComposingText();
		    }
		}
	}
	
	/**
	* This tells us about completions that the editor has determined based
	* on the current text in it.  We want to use this in fullscreen mode
	* to show the completions ourself, since the editor can not be seen
	* in that situation.
	*/
	@Override 
	public void onDisplayCompletions(CompletionInfo[] completions) {
		if (mCompletionOn) {
		    mCompletions = completions;
		    if (completions == null) {
		        setSuggestions(null, false, false);
		        return;
		    }
		    
		    List<String> stringList = new ArrayList<String>();
		    for (int i=0; i<(completions != null ? completions.length : 0); i++) {
		        CompletionInfo ci = completions[i];
		        if (ci != null) stringList.add(ci.getText().toString());
		    }
		    setSuggestions(stringList, true, true);
		}
	}
	
	/**
	* This translates incoming hard key events in to edit operations on an
	* InputConnection.  It is only needed when using the
	* PROCESS_HARD_KEYS option.
	* 将按键输入转化为编辑操作，仅仅在有硬件键盘是需要被调咏
	*/
	private boolean translateKeyDown(int keyCode, KeyEvent event) {
		mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
		        keyCode, event);
		int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
		mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
		InputConnection ic = getCurrentInputConnection();
		if (c == 0 || ic == null) {
		    return false;
		}
		
		boolean dead = false;
		
		if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
		    dead = true;
		    c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
		}
		
		if (mComposing.length() > 0) {
		    char accent = mComposing.charAt(mComposing.length() -1 );
		    int composed = KeyEvent.getDeadChar(accent, c);
		
		    if (composed != 0) {
		        c = composed;
		        mComposing.setLength(mComposing.length()-1);
		    }
		}
		
		onKey(c, null);
		
		return true;
	}
	
	/**
	* Use this to monitor key events being delivered to the application.
	* We get first crack at them, and can either resume them or let them
	* continue to the app.
	* 监控key时间，并决定是否将它传递给app
	*/
	@Override 
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		    case KeyEvent.KEYCODE_BACK:
		        // The InputMethodService already takes care of the back
		        // key for us, to dismiss the input method if it is shown.
		        // However, our keyboard could be showing a pop-up window
		        // that back should dismiss, so we first allow it to do that.
		    	//这个InputMethodService已经为我们处理了back键，用以关闭输入法，但是我们的键盘可以显示一个pop-up视窗，所以我们需要让back将它关闭
		        if (event.getRepeatCount() == 0 && mInputView != null) {
		            if (mInputView.handleBack()) {
		                return true;
		            }
		        }
		        break;
		        
		    case KeyEvent.KEYCODE_DEL:
		        // Special handling of the delete key: if we currently are
		        // composing text for the user, we want to modify that instead
		        // of let the application to the delete itself.
		    	//
		        if (mComposing.length() > 0) {
		            onKey(Keyboard.KEYCODE_DELETE, null);
		            return true;
		        }
		        break;
		        
		    case KeyEvent.KEYCODE_ENTER:
		        // Let the underlying text editor always handle these.
		        return false;
		        
		    default:
		        // For all other keys, if we want to do transformations on
		        // text being entered with a hard keyboard, we need to process
		        // it and do the appropriate action.
		        if (PROCESS_HARD_KEYS) {
		            if (keyCode == KeyEvent.KEYCODE_SPACE
		                    && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
		                // A silly example: in our input method, Alt+Space
		                // is a shortcut for 'android' in lower case.
		                InputConnection ic = getCurrentInputConnection();
		                if (ic != null) {
		                    // First, tell the editor that it is no longer in the
		                    // shift state, since we are consuming this.
		                    ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
		                    keyDownUp(KeyEvent.KEYCODE_A);
		                    keyDownUp(KeyEvent.KEYCODE_N);
		                    keyDownUp(KeyEvent.KEYCODE_D);
		                    keyDownUp(KeyEvent.KEYCODE_R);
		                    keyDownUp(KeyEvent.KEYCODE_O);
		                    keyDownUp(KeyEvent.KEYCODE_I);
		                    keyDownUp(KeyEvent.KEYCODE_D);
		                    // And we consume this event.
		                    return true;
		                }
		            }
		            if (mPredictionOn && translateKeyDown(keyCode, event)) {
		                return true;
		            }
		        }
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	/**
	* Use this to monitor key events being delivered to the application.
	* We get first crack at them, and can either resume them or let them
	* continue to the app.
	* 重写方法，为实体键盘
	*/
	@Override 
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// If we want to do transformations on text being entered with a hard
		// keyboard, we need to process the up events to update the meta key
		// state we are tracking.
		if (PROCESS_HARD_KEYS) {
		    if (mPredictionOn) {
		        mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
		                keyCode, event);
		    }
		}
		
		return super.onKeyUp(keyCode, event);
	}
	
	/**
	* Helper function to commit any text being composed in to the editor.
	* 辅助函数，用以体检编辑器中的文字
	*/
	private void commitTyped(InputConnection inputConnection) {
		if (mComposing.length() > 0) {
			String tComposing ;
			tComposing = translate(mComposing.toString());
		    inputConnection.commitText(tComposing, tComposing.length());
		    mComposing.setLength(0);
		    updateCandidates();
		}
	}
	
	/**
	* Helper to update the shift state of our keyboard based on the initial
	* editor state.
	* 更新shift状态
	*/
	private void updateShiftKeyState(EditorInfo attr) {
		if (attr != null 
		        && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
		    int caps = 0;
		    EditorInfo ei = getCurrentInputEditorInfo();
		    if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
		        caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
		    }
		    mInputView.setShifted(mCapsLock || caps != 0);
		}
	}
	
	/**
	* Helper to determine if a given character code is alphabetic.
	* 用以确定所低昂的字符是否为alph
	*/
	private boolean isAlphabet(int code) {
		if (Character.isLetter(code)) {
		    return true;
		} else {
		    return false;
		}
	}
	
	/**
	* Helper to send a key down / key up pair to the current editor.
	* 为当前编辑器发送一个key up ordown时间
	*/
	private void keyDownUp(int keyEventCode) {
		getCurrentInputConnection().sendKeyEvent(
		        new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
		getCurrentInputConnection().sendKeyEvent(
		        new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
	}
	
	/**
	* Helper to send a character to the editor as raw key events.
	* 发送一个字符给编辑器作为行key时间
	*/
	private void sendKey(int keyCode) {
		switch (keyCode) {
		    case '\n':
		        keyDownUp(KeyEvent.KEYCODE_ENTER);
		        break;
		    default:
		        if (keyCode >= '0' && keyCode <= '9') {
		            keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
		        } else {
		            getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
		        }
		        break;
		}
	}
	
	// Implementation of KeyboardViewListener
	
	public void onKey(int primaryCode, int[] keyCodes) {
		if (isWordSeparator(primaryCode)) {
		    // Handle separator
		    if (mComposing.length() > 0) {
		        commitTyped(getCurrentInputConnection());
		    }
		    sendKey(primaryCode);
		    updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (primaryCode == Keyboard.KEYCODE_DELETE) {
		    handleBackspace();
		} else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
		    handleShift();
		} else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
		    handleClose();
		    return;
		} else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
		    // Show a menu or somethin'
		} else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
		        && mInputView != null) {
		    Keyboard current = mInputView.getKeyboard();
		    if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
		        current = mQwertyKeyboard;
		    } else {
		        current = mSymbolsKeyboard;
		    }
		    mInputView.setKeyboard(current);
		    if (current == mSymbolsKeyboard) {
		        current.setShifted(false);
		    }
		} else {
		    handleCharacter(primaryCode, keyCodes);
		}
		}
		
		public void onText(CharSequence text) {
		InputConnection ic = getCurrentInputConnection();
		if (ic == null) return;
		ic.beginBatchEdit();
		if (mComposing.length() > 0) {
		    commitTyped(ic);
		}
		ic.commitText(text, 0);
		ic.endBatchEdit();
		updateShiftKeyState(getCurrentInputEditorInfo());
	}
	
	/**
	* Update the list of available candidates from the current composing
	* text.  This will need to be filled in by however you are determining
	* candidates.
	* 更新候选测
	*/
	private void updateCandidates() {
		if (!mCompletionOn) {
		    if (mComposing.length() > 0) {
		        ArrayList<String> list = new ArrayList<String>();
		        list.add(mComposing.toString());
		        setSuggestions(list, true, true);
		    } else {
		        setSuggestions(null, false, false);
		    }
		}
	}
	
	public void setSuggestions(List<String> suggestions, boolean completions,
	    boolean typedWordValid) {
		if (suggestions != null && suggestions.size() > 0) {
		    setCandidatesViewShown(true);
		} else if (isExtractViewShown()) {
		    setCandidatesViewShown(true);
		}
		if (mCandidateView != null) {
		    mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
		}
	}
	
	private void handleBackspace() {
		final int length = mComposing.length();
		if (length > 1) {
		    mComposing.delete(length - 1, length);
		    getCurrentInputConnection().setComposingText(mComposing, 1);
		    updateCandidates();
		} else if (length > 0) {
		    mComposing.setLength(0);
		    getCurrentInputConnection().commitText("", 0);
		    updateCandidates();
		} else {
		    keyDownUp(KeyEvent.KEYCODE_DEL);
		}
		updateShiftKeyState(getCurrentInputEditorInfo());
		}
		
		private void handleShift() {
		if (mInputView == null) {
		    return;
		}
		
		Keyboard currentKeyboard = mInputView.getKeyboard();
		if (mQwertyKeyboard == currentKeyboard) {
		    // Alphabet keyboard
		    checkToggleCapsLock();
		    mInputView.setShifted(mCapsLock || !mInputView.isShifted());
		} else if (currentKeyboard == mSymbolsKeyboard) {
		    mSymbolsKeyboard.setShifted(true);
		    mInputView.setKeyboard(mSymbolsShiftedKeyboard);
		    mSymbolsShiftedKeyboard.setShifted(true);
		} else if (currentKeyboard == mSymbolsShiftedKeyboard) {
		    mSymbolsShiftedKeyboard.setShifted(false);
		    mInputView.setKeyboard(mSymbolsKeyboard);
		    mSymbolsKeyboard.setShifted(false);
		}
	}
	
	private void handleCharacter(int primaryCode, int[] keyCodes) {
		if (isInputViewShown()) {
		    if (mInputView.isShifted()) {
		        primaryCode = Character.toUpperCase(primaryCode);
		    }
		}
		if ((isAlphabet(primaryCode) && mPredictionOn)||(mPredictionOn&&(primaryCode == 32 ))) {
		    mComposing.append((char) primaryCode);
		    getCurrentInputConnection().setComposingText(mComposing, 1);
		    updateShiftKeyState(getCurrentInputEditorInfo());
		    updateCandidates();
		} else {
		    getCurrentInputConnection().commitText(
		            String.valueOf((char) primaryCode), 1);
		}
	}
	
	private void handleClose() {
		commitTyped(getCurrentInputConnection());
		requestHideSelf(0);
		mInputView.closing();
	}
	
	private void checkToggleCapsLock() {
		long now = System.currentTimeMillis();
		if (mLastShiftTime + 800 > now) {
		    mCapsLock = !mCapsLock;
		    mLastShiftTime = 0;
		} else {
		    mLastShiftTime = now;
		}
	}
	
	private String getWordSeparators() {
		return mWordSeparators;
	}
	
	public boolean isWordSeparator(int code) {
		String separators = getWordSeparators();
		return separators.contains(String.valueOf((char)code));
	}
	
	public void pickDefaultCandidate() {
		pickSuggestionManually(0);
	}
	
	public void pickSuggestionManually(int index) {
		if (mCompletionOn && mCompletions != null && index >= 0
		        && index < mCompletions.length) {
		    CompletionInfo ci = mCompletions[index];
		    getCurrentInputConnection().commitCompletion(ci);
		    if (mCandidateView != null) {
		        mCandidateView.clear();
		    }
		    updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (mComposing.length() > 0) {
		    // If we were generating candidate suggestions for the current
		    // text, we would commit one of them here.  But for this sample,
		    // we will just commit the current text.
		    commitTyped(getCurrentInputConnection());
		}
	}
	
	public void swipeRight() {
		if (mCompletionOn) {
		    pickDefaultCandidate();
		}
	}
	
	public void swipeLeft() {
		handleBackspace();
	}
	
	public void swipeDown() {
		handleClose();
	}
	
	public void swipeUp() {
	}
	
	public void onPress(int primaryCode) {
	}
	
	public void onRelease(int primaryCode) {
	}
	
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
		try{
			HttpEntity httpEntity = new UrlEncodedFormEntity(postParams,HTTP.UTF_8);
			HttpPost httpPost = new HttpPost(urlApi);
			HttpClient httpClient = new DefaultHttpClient();
			System.out.println("121212121212");
			httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
			httpPost.setEntity(httpEntity);
			HttpResponse response = httpClient.execute(httpPost);
			if(response.getStatusLine().getStatusCode()==200){
				System.out.println("adjfdkfkdjf");
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

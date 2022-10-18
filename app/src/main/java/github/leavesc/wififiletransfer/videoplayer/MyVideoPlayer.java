package github.leavesc.wififiletransfer.videoplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

public class MyVideoPlayer extends StandardGSYVideoPlayer {

    public MyVideoPlayer(Context context, Boolean fullFlag) {
        super(context, fullFlag);
    }

    public MyVideoPlayer(Context context) {
        super(context);
    }

    public MyVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void init(Context context) {
        super.init(context);
        //设置透明度，隐藏界面UI。
//        setAlphaTo0f(mLockScreen, mBottomContainer, mBottomProgressBar, mStartButton, mTopContainer);
    }

    protected void  setAlphaTo0f(View... vs){
        for (View view : vs) {
            view.setAlpha(0.0f);
        }
    }
}

package com.ptfast.scanqr;

import android.app.Application;

/**
 * 进程入口。Material You 的动态取色是在每个 Activity 的 onCreate 里套用的,
 * 这里只把 Application 挂上,方便以后放全局初始化。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
    }
}

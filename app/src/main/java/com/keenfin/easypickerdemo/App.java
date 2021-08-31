/*
 *           Copyright © 2021 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.easypickerdemo;

import android.app.Application;
import android.util.Log;

import com.hypertrack.hyperlog.HyperLog;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        HyperLog.initialize(this);
        HyperLog.setLogLevel(Log.VERBOSE);
    }
}

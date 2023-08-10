package com.example.dragfloatactionlayout;

import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class RxUtils {
    public static Flowable<Long> timer(int count) {
        return Flowable.
                intervalRange(0, count+1, 0, 1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread());

    }
}

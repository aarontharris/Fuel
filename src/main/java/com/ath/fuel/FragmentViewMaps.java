package com.ath.fuel;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public final class FragmentViewMaps { // TODO: move to DI ?

    private static WeakHashMap<Context, FragmentViewMaps> CONTEXT_SINGLETONS = new WeakHashMap<>();
    private static @NonNull final Object lock = new Object();

    public static synchronized @NonNull FragmentViewMaps get(@NonNull final Context context) {
        FragmentViewMaps maps = null;
        if ((maps = CONTEXT_SINGLETONS.get(context)) == null) {
            synchronized (lock) {
                if ((maps = CONTEXT_SINGLETONS.get(context)) == null) {
                    maps = new FragmentViewMaps();
                    CONTEXT_SINGLETONS.put(context, maps);
                }
            }
        }
        return maps;
    }

    private WeakHashMap<View, WeakReference<Fragment>> map = new WeakHashMap<>();
    private WeakHashMap<Fragment, WeakReference<View>> rmap = new WeakHashMap<>();

    private FragmentViewMaps() { }

    public void associate(View view, Fragment fragment) {
        map.put(view, new WeakReference<>(fragment));
        rmap.put(fragment, new WeakReference<>(view));
    }

    public @Nullable Fragment lookup(View view) {
        WeakReference<Fragment> ref = map.get(view);
        return ref == null ? null : ref.get();
    }

    public @Nullable View lookup(Fragment fragment) {
        WeakReference<View> ref = rmap.get(fragment);
        return ref == null ? null : ref.get();
    }

}

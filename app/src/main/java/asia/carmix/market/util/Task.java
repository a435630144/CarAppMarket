package asia.carmix.market.util;

import android.os.AsyncTask;

/**
 * 简易后台任务包装：后台线程执行 callable，主线程回调结果或异常。
 */
public abstract class Task {

    public interface Background<T> {
        T run() throws Exception;
    }

    public interface Ui<T> {
        void onDone(T result, Exception error);
    }

    public static <T> void run(final Background<T> bg, final Ui<T> ui) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final Object[] outcome = new Object[2];
                try {
                    outcome[0] = bg.run();
                } catch (Exception e) {
                    outcome[1] = e;
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public void run() {
                        if (ui != null) ui.onDone((T) outcome[0], (Exception) outcome[1]);
                    }
                });
            }
        });
    }
}

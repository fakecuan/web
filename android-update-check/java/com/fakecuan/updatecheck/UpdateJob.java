package com.fakecuan.updatecheck;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Cek update berkala (sekitar tiap 6 jam) walau aplikasi tidak dibuka. */
public class UpdateJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            UpdateCheck.check(getApplicationContext(), false);
            jobFinished(params, false);
        }, "update-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}

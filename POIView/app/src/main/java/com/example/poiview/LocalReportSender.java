package com.example.poiview;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.auto.service.AutoService;
import java.io.File;
import java.io.FileWriter;
import org.acra.config.CoreConfiguration;
import org.acra.data.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;
import org.acra.sender.ReportSenderFactory;
import org.jetbrains.annotations.NotNull;

public class LocalReportSender implements ReportSender {
    CoreConfiguration config;

    public LocalReportSender(CoreConfiguration coreConfiguration) {
        config = coreConfiguration;
    }

    @Override
    public void send(@NotNull Context context, @NotNull CrashReportData errorContent)
            throws ReportSenderException {
        // the destination
        // This usually appear as:
        // Internal shared /storage/self/primary/Android/data/com.example.poiview/files/Documents/
        // on USB connection or Google files App
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);

        String state = Environment.getExternalStorageState();

        String logName = "crash_report_" + System.currentTimeMillis() + ".txt";

        File logFile;
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            logFile = new File(dir, logName);
        } else {
            // backup if external storage is not available
            logFile = new File(context.getCacheDir(), logName);
        }

        try {
            // Use the core ReportFormat configuration
            String reportText = config.getReportFormat()
                    .toFormattedString(errorContent,
                            config.getReportContent(), "\n", "\n\t", false);

            // Overwrite last report
            FileWriter writer = new FileWriter(logFile, false);
            writer.append(reportText);
            writer.flush();
            writer.close();
            Log.d("[LocalReportSender]", "Report Saved (" + logFile.getAbsolutePath() + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AutoService(ReportSenderFactory.class)
    public static class LocalReportFactory implements ReportSenderFactory {
        @NotNull
        @Override
        public ReportSender create(@NotNull Context context,
                                   @NotNull CoreConfiguration coreConfiguration) {
            Log.d("[LocalReportSender]", "LocalReportSender created!");
            return new LocalReportSender(coreConfiguration);
        }

        @Override
        public boolean enabled(@NonNull CoreConfiguration coreConfig) {
            Log.d("[LocalReportSender]", "LocalReportSender enabled!");
            return true;
        }
    }
}

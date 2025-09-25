package org.client.scrcpy.utils;

import android.os.Build;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Execute a command that keeps running in the background until the callback closes it.
 */
public class ProcessHelper {
    protected volatile boolean isClose;
    // server
    private Process process = null;
    private OutputStream os = null;
    private BufferedReader successResult = null;
    private BufferedReader errorResult = null;

    public static ProcessHelper runComand(String command,
                                          StatuCallback statuCallback) {
        return new ProcessHelper(command, statuCallback);
    }

    public void writeCommand(String newCommand) {
        writeCommand(newCommand, false);
    }

    public void writeCommand(String newCommand, boolean needEnter) {
        write(newCommand.getBytes(StandardCharsets.UTF_8));
        if (needEnter) {
            write("\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    public void write(byte[] bytes) {
        if (!isClose && os != null) {
            try {
                os.write(bytes);
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // The process has been interrupted, refuse further writes
            throw new RuntimeException("process is close");
        }
    }

    public void stopCommand() {
        isClose = true;
        streamClose();
    }

    public boolean isRunning() {
        return !isClose;
    }

    public ProcessHelper() {
        this.isClose = false;
    }

    private ProcessHelper(String command, StatuCallback statuCallback) {
        this.isClose = false;
        try {
            process = Runtime.getRuntime().exec(command);
            os = process.getOutputStream();
            // Read standard output
            successResult = new BufferedReader(new InputStreamReader(
                    process.getInputStream()));
            errorResult = new BufferedReader(new InputStreamReader(
                    process.getErrorStream()));
            if (statuCallback != null) {
                statuCallback.processOpen(this, process, os);
            }
            // After closing the streams, destroy the process
            Thread outputThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    String s;
                    while (!isClose) {
                        try {
                            if ((s = successResult.readLine()) != null) {
                                if (statuCallback != null) {
                                    statuCallback.onOutput(ProcessHelper.this, s);
                                }
                            } else {
                                isClose = true;
                            }
                        } catch (IOException ignored) {
                            isClose = true;
                        } catch (Exception ignored) {
                        }
                    }
                    streamClose();
                    // After closing the streams, destroy the process
                    if (process != null) {
                        process.destroy();
                    }
                    int exitCode = -1;
                    if (process != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            if (process.isAlive()) {
                                try {
                                    // Wait 1 second to see if the process exits
                                    if (process.waitFor(1, TimeUnit.SECONDS)) {
                                        exitCode = process.exitValue();
                                    }
                                } catch (InterruptedException ignore) {
                                }
                            } else {
                                exitCode = process.exitValue();
                            }
                        } else {
                            try {
                                exitCode = process.exitValue();
                            } catch (Exception ignore) {
                            }
                        }
                    }
                    if (statuCallback != null) {
                        // statuCallback.processClose(ProcessHelper.this, process, process == null ? -1 : process.exitValue());
                        statuCallback.processClose(ProcessHelper.this, process, exitCode);
                    }
                }
            });
            Thread errorThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    String s;
                    while (!isClose) {
                        try {
                            if ((s = errorResult.readLine()) != null) {
                                if (statuCallback != null) {
                                    statuCallback.onError(ProcessHelper.this, s);
                                }
                            } else {
                                isClose = true;
                            }
                        } catch (IOException ignored) {
                            isClose = true;
                        } catch (Exception ignored) {
                        }
                    }
                    streamClose();
                }
            });
            outputThread.start();
            errorThread.start();
        } catch (Exception e) {
            // Mark as closed if startup throws an exception
            this.isClose = true;
            if (statuCallback != null) {
                statuCallback.processError(this, process, e);
            }
        }
    }

    private synchronized void streamClose() {
        if (os != null) {
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                os = null;
            }
        }
        if (successResult != null) {
            try {
                successResult.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                successResult = null;
            }
        }
        if (errorResult != null) {
            try {
                errorResult.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                errorResult = null;
            }
        }
    }

    public static class StatuCallback {
        public void processOpen(ProcessHelper processHelper, Process process, OutputStream outputStream) {

        }

        public void processError(ProcessHelper processHelper, Process process, Exception e) {

        }

        public void processClose(ProcessHelper processHelper, Process process, int exitCode) {

        }

        public void onOutput(ProcessHelper processHelper, String output) {
        }

        public void onError(ProcessHelper processHelper, String error) {
        }
    }

}

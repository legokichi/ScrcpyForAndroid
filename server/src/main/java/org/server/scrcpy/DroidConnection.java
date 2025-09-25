package org.server.scrcpy;


import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public final class DroidConnection implements Closeable {


    private static Socket socket = null;
    private OutputStream outputStream;
    private InputStream inputStream;

    private DroidConnection(Socket socket) throws IOException {
        this.socket = socket;

        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }


    private static Socket listenAndAccept() throws IOException {
        ServerSocket serverSocket = new ServerSocket(7007);
        Socket sock = null;
        try {
            sock = serverSocket.accept();
        } finally {
            serverSocket.close();
        }
        return sock;
    }

    public static DroidConnection open(String ip) throws IOException {

        socket = listenAndAccept();
        DroidConnection connection = null;
//        if (socket.getInetAddress().toString().equals(ip)) {
//            connection = new DroidConnection(socket);
//        }
        if (!socket.getInetAddress().toString().equals(ip)) {
            Ln.w("socket connect address != " + ip);
        }
        // Ensure the socket reports a valid address
        if (!socket.getInetAddress().toString().isEmpty()) {
            connection = new DroidConnection(socket);
        }
        return connection;
    }

    public void close() throws IOException {
        socket.shutdownInput();
        socket.shutdownOutput();
        socket.close();
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }


    /**
     * TODO Align message transport with upstream scrcpy; currently only touch events are supported.
     *
     * @return decoded event payload
     * @throws IOException if reading from the socket fails
     */
    public int[] NewreceiveControlEvent() throws IOException {

        byte[] buf = new byte[20];
        int n = inputStream.read(buf, 0, 20);
        if (n == -1) {
            throw new EOFException("Event controller socket closed");
        }

        final int[] array = new int[buf.length / 4];
        for (int i = 0; i < array.length; i++)
            array[i] = (((int) (buf[i * 4]) << 24) & 0xFF000000) |
                    (((int) (buf[i * 4 + 1]) << 16) & 0xFF0000) |
                    (((int) (buf[i * 4 + 2]) << 8) & 0xFF00) |
                    ((int) (buf[i * 4 + 3]) & 0xFF);
        return array;


    }

}

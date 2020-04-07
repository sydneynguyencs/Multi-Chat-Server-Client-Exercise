package ch.zhaw.pm2.multichat.protocol;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

/**
 * Helper class to support simple network communication.
 * It provides access to the two subclasses:
 * <ul>
 *     <li>{@link NetworkServer} is used on the server side to open a port and wait for connection request from clients</li>
 *     <li>{@link NetworkConnection} represents a bidirectional connection between client and server, to send and
 *     receive Objects</li>
 * </ul>
 * <p>The typical process works as follows</p>
 * <ul>
 *     <li>The server creates a {@link NetworkServer} instance using the factory method
 *     {@link NetworkHandler#createServer(int port)}. This creates and opens a port (range: 0 - 65535)
 *     on all available interfaces (IP-networks, incl. localhost loopback) of the current host.</li>
 *     <li>As soon the server is ready to receive requests it calls the method {@link NetworkServer#waitForConnection()}
 *     which is blocking and waiting for client to open a connection.</li>
 *     <li>On the client side, a new {@link NetworkConnection} instance is created to connect to the server using the
 *     factory method {@link NetworkHandler#openConnection(String host, int port)}, which opens a connection to the
 *     given host (domainname or ip-address) on the specified server port</li>
 *     <li>On the server side, the waiting method {@link NetworkServer#waitForConnection()} returns an instance of
 *     {@link NetworkConnection} which represents the specific connection to the calling client.</li>
 *     <li>This connection can be used to send and receive data between server and client.</li>
 *     <li>On the server side, the handling of each interaction ('session') with a specific client should be handled in
 *     a separate {@link Thread}, after starting the thread, the server can go back and wait for the next connection
 *     request.</li>
 *     <li>Both sides (server & client) need to handle sending and receiving of data separately
 *         <ul>
 *             <li>reading data: call {@link NetworkConnection#receive()}, which is blocking until a data object is
 *             received. As soon the object has been received, the method returns an instance of the object.
 *             This object (request) can be processed (on the server side, usually a response is sent back;
 *             on the client side, usually the result is displayed to the user). After processing is finished the
 *             process calls {@link NetworkConnection#receive()} again to wait for the next request.
 *             </li>
 *             <li>sending data: call {@link NetworkConnection#send(Serializable data)}, which sends the given data
 *             object to the remote side. The method returns as soon the object has been transmitted.
 *             <b>Important: {@link NetworkConnection} is not thread safe</b>, therefore make sure that only one thread
 *             at a time is sending data.</li>
 *         </ul>
 *         <b>Important:Sending and receiving of data is completely asynchronous and can happen in parallel.</b>
 *     </li>
 *     <li>The connection stays open until one of the peers decides to close it using {@link NetworkConnection#close()}.
 *     In this case, all waiting method calls (e.g. {@link NetworkConnection#receive()} on the opposite side are
 *     interrupted and a {@link EOFException} is thrown.<br>
 *     On the local side, waiting method calls (threads) are also interrupted and a {@link java.net.SocketException}
 *     is thrown.</li>
 *     <li>To stop receiving new connection requests on the server side, the server may call
 *     {@link NetworkServer#close()} which will close all currently open {@link NetworkConnection} objects.</li>
 * </ul>
 * <p>{@link NetworkServer} and {@link NetworkConnection} are typed using generics. This means, when creating an
 * instance it has to be specified, what types of objects can be sent between server and client. The type has to be
 * identical on both sides of the connection. These Objects have to be of type {@link Serializable}, which is a
 * marker interface specifying that an object can be serialized/deserialized. As long all properties within a
 * class are also Serializable, your class simply can be marked using it. All standard Java data-types are by default
 * Serializable.</p>
 */
public class NetworkHandler {
    /**
     * Default network address used to open a connection to:  localhost (domainname), 127.0.0.1 (IPv4), ::1 (IPv6)
     */
    public static final InetAddress DEFAULT_ADDRESS = InetAddress.getLoopbackAddress();
    /**
     * Default port on the server side to listen for requests
     */
    public static final int DEFAULT_PORT = 22243;

    /**
     * private Constructor to avoid initialization.
     * Use the static factory methods to create {@link NetworkServer} or {@link NetworkConnection} instances.
     */
    private NetworkHandler() {}

    /**
     * Creates an instance of a {@link NetworkServer} listening on the specified port for connection request for
     * Objects of type T.
     * @param port  port to open on the server host (range: 1 - 65535)
     * @param <T>   type of the Objects to be transmitted in the created {@link NetworkConnection}
     * @return  {@link NetworkServer} object to be used to wait for connections.
     * @throws IOException  if an error occured opening the port, e.g. the port number is already used.
     */
    public static <T extends Serializable> NetworkServer<T> createServer(int port) throws IOException {
        return new NetworkServer<>(port);
    }

    /**
     * Creates an instance of a {@link NetworkServer} listening on the default port (22243) for connection request for
     * Objects of type T.
     * @param <T>   type of the Objects to be transmitted in the created {@link NetworkConnection}
     * @return  {@link NetworkServer} object to be used to wait for connections.
     * @throws IOException  if an error occured opening the port, e.g. the port number is already used.
     */
    public static <T extends Serializable> NetworkServer<T> createServer() throws IOException {
        return new NetworkServer<>();
    }

    /**
     * Creates an instance of a {@link NetworkConnection} connecting to the specified host/port to send and receive
     * objects of type T.
     * @param address   {@link InetAddress} object for the host
     * @param port      port number the server is waiting for connection requests
     * @param <T>       type of Objects to be transmitted trough this connection
     * @return  {@link NetworkConnection} object representing the bidirectional channel between client and server.
     * @throws IOException  if an error occurred opening the connection, e.g. server is not responding.
     */
    public static <T extends Serializable> NetworkConnection<T> openConnection(InetAddress address, int port)
    throws IOException
    {
        Socket socket = new Socket(address, port);
        socket.setKeepAlive(true);
        return new NetworkConnection<>(socket);
    }

    /**
     * Creates an instance of a {@link NetworkConnection} connecting to the specified host/port to send and receive
     * objects of type T.
     * @param hostname  server host name or address in String representation (e.g. "www.zhaw.ch", "160.85.104.112")
     * @param port      port number the server is waiting for connection requests
     * @param <T>       type of Objects to be transmitted trough this connection
     * @return  {@link NetworkConnection} object representing the bidirectional channel between client and server.
     * @throws IOException  if an error occurred opening the connection, e.g. server is not responding.
     */
    public static <T extends Serializable> NetworkConnection<T> openConnection(String hostname, int port)
    throws IOException
    {
        return openConnection(InetAddress.getByName(hostname), port);
    }

    /**
     * Creates an instance of a {@link NetworkConnection} connecting to the default host ("localhost",127.0.0.1,::1)
     * and port (22243) to send and receive objects of type T.
     * @param <T>   type of Objects to be transmitted trough this connection
     * @return      {@link NetworkConnection} object representing the bidirectional channel between client and server.
     * @throws IOException  if an error occurred opening the connection, e.g. server is not responding.
     */
    public static <T extends Serializable> NetworkConnection<T> openConnection()
    throws IOException
    {
        return openConnection(DEFAULT_ADDRESS, DEFAULT_PORT);
    }





    /**
     * Network communication class used on the server side to handle connection request from clients.
     * The class opens a port on the server host and allows the server process to wait for connection requests.
     * As soon a request comes in a {@link NetworkConnection} object is created, which is used to handle all the
     * communication between the two peers.
     * @param <T> type of the Objects to be transmitted in the created {@link NetworkConnection}
     */
    public static class NetworkServer<T extends Serializable> implements Closeable {
        private ServerSocket serverSocket;

        /**
         * <b>Private constructor: use {@link NetworkHandler#createServer(int port)} factory method to create an instance</b>
         * Open a server port an the given port number. The port number must be unique (i.e. not used by another process)
         * @param port  port number (range: 1 - 65535) to open to wait for requests.
         * @throws IOException if an error occurred opening the port, e.g. the port number is already used.
         */
        private NetworkServer(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
        }

        /**
         * <b>Private constructor: use {@link NetworkHandler#createServer(int port)} factory method to create an instance</b>
         * Open a server port an the default port (22243).
         * @throws IOException if an error occurred opening the port, e.g. the port number is already used.
         */
        private NetworkServer() throws IOException {
            this(DEFAULT_PORT);
        }

        /**
         * Blocks the current thread and waits for connection requests on the declared port of the
         * {@link NetworkServer} object. Returns a {@link NetworkConnection} object representing the connection to a
         * client if a successfull connection has been established.
         * @return  {@link NetworkConnection} object representing the connection to the connecting client.
         * @throws IOException if an error occurred while waiting (e.g. throws a {@link java.net.SocketException} if
         * the port has been closed using the {@link NetworkServer#close()} method.
         */
        public NetworkConnection<T> waitForConnection() throws IOException {
            Socket socket = serverSocket.accept();
            socket.setKeepAlive(true);
            return new NetworkConnection<>(socket);
        }

        /**
         * Does indicate if the server is ready and bound to the declared port.
         * @return true if the server is ready and bound to the declared port, false otherwise
         */
        public boolean isAvailable() {
            return serverSocket != null && serverSocket.isBound();
        }

        /**
         * Does indicate if the server has been closed. A closed server can not be reopened. To reopen a port, a
         * new Instance must be created.
         * @return true if the server is closed, false otherwise.
         */
        public boolean isClosed() {
            return serverSocket == null || serverSocket.isClosed();
        }

        /**
         * Returns the port number on which the server is listening for requests.
         * @return returns the port number (range: 1 - 65535) if the server is available, 0 otherwise.
         */
        public int getHostPort() {
            return isAvailable()? serverSocket.getLocalPort() : 0;
        }

        /**
         * Returns the host address in String format on which the server is listening for requests.
         * @return host address in String format or "unbound" if not available.
         */
        public String getHostAddress() {
            return isAvailable()? serverSocket.getInetAddress().getHostAddress() : "unbound";
        }

        /**
         * Closes this Server and releases any system resources associated with it.
         * Closing the Server, closes also all {@link NetworkConnection} objects created by the server and throws a
         * {@link java.net.SocketException} on all blocking calls (e.g. {@link NetworkServer#waitForConnection()})
         * on the server.
         * If the Server is already closed then invoking this method has no effect.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NetworkServer<?> that = (NetworkServer<?>) o;
            return serverSocket.equals(that.serverSocket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serverSocket);
        }
    }

    /**
     * Network communication class representing a bidirectional connection between two peers (client and server),
     * to send and receive Objects of type T.
     * The client can open a new connection using the factory method
     * {@link NetworkHandler#openConnection(String hostname, int port)} to connect to the specified server.
     * On the server side, the {@link NetworkServer#waitForConnection()} method is creating a matching instance for the
     * connecting client.
     *
     * <li>On an open connection, both sides (server & client) need to handle sending and receiving of data separately
     * <ul>
     *     <li>reading data: call {@link NetworkConnection#receive()}, which is blocking until a data object is
     *         received. As soon the object has been received, the method returns an instance of the object.
     *         This object (request) can be processed (on the server side, usually a response is sent back;
     *         on the client side, usually the result is displayed to the user). After processing is finished the
     *         process calls {@link NetworkConnection#receive()} again to wait for the next request.
     *     </li>
     *     <li>sending data: call {@link NetworkConnection#send(Serializable data)}, which sends the given data
     *         object to the remote side. The method returns as soon the object has been transmitted.
     *         <b>Important: {@link NetworkConnection} is not thread safe</b>, therefore make sure that only one thread
     *         at a time is sending data.
     *     </li>
     * </ul>
     * <p><b>Important: Sending and receiving of data is completely asynchronous and can happen in parallel.</b>
     * The connection stays open until one of the peers decides to close it using {@link NetworkConnection#close()}.<br>
     * In this case, all waiting method calls (e.g. {@link NetworkConnection#receive()} on the opposite side are
     * interrupted and a {@link EOFException} is thrown.<br>
     * On the local side, waiting method calls (threads) are also interrupted and a {@link java.net.SocketException}
     * is thrown.</p>
     *
     * @param <T> type of Objects to be transmitted trough this connection
     */
    public static class NetworkConnection<T extends Serializable> implements Closeable {
        private Socket socket;

        /**
         * <b>Privat constructor: Use {@link NetworkHandler#openConnection(String hostname, int port)} and similar
         * factory methods to create instances of {@link NetworkConnection}</b>
         * @param socket   operating system socket to use for the communication.
         */
        private NetworkConnection(Socket socket) {
            this.socket = socket;
        }

        /**
         * Method to send data to the opposite side. The call is sending out the requests immediately and returns if
         * submitted successfully. Data can also be sent, while another thread is waiting for requests, but it has
         * to be made sure that only one thread is sending data at a time (not thread-safe).
         * If an error occurs a {@link IOException} is thrown.
         * @param data  data object of type T to be submitted through the connection.
         * @throws IOException if an error occurs (e.g. connection interrupted while sending, ...)
         */
        public void send(T data) throws IOException {
            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.writeObject(data);
        }

        /**
         * Method to receive data from the opposite side. The call is blocking until a requests comes in, and the
         * transferred object is returned.
         * If the connection is closed during waiting, a {@link java.net.SocketException} is thrown, if the close
         * was initiated locally or {@link EOFException} is thrown if the connection is closed from the remote side.
         * Other {@link IOException} may be thrown on any another communication error.
         * @return data object of type T received through the connection.
         * @throws IOException if an error occours. (e.g. terminated locally/remotely) see above.
         * @throws ClassNotFoundException if the data object received does not match any class in the local classpath
         */
        public T receive() throws IOException, ClassNotFoundException {
            ObjectInputStream inputStream = new ObjectInputStream(this.socket.getInputStream());
            return (T) inputStream.readObject();
        }

        /**
         * Indicates if the connection is open and connected to the peer.
         * @return true if the connection is open and connected, false otherwise
         */
        public boolean isAvailable() {
            return !isClosed() && socket.isConnected();
        }

        /**
         * Indicate if the connection has been closed. A closed connection can not be reopened.
         * To re-open, a new Instance must be created.
         * @return true if the connection is closed, false otherwise.
         */
        public boolean isClosed() {
            return socket == null || socket.isClosed();
        }

        /**
         * Returns the port number of the remote host, if the connection is available.
         * @return port number (range: 1 - 65535) of the port on the remote host, 0 if not connected.
         */
        public int getRemotePort() {
            return isAvailable()? socket.getPort() : 0;
        }

        /**
         * Returns the host name of the remote peer. If available looks up the hostname (e.g. "www.zhaw.ch"),
         * otherwise returns a string representation of the IP address (e.g. "160.85.104.112").
         * @return host name of the remote peer, "not connected" if connection is not available.
         */
        public String getRemoteHost() {
            return isAvailable()? socket.getInetAddress().getHostName() : "not connected";
        }

        /**
         * Closes this NetworkConnection and releases any system resources associated with it.
         * If the connection is closed a {@link java.net.SocketException} is thrown on all local waiting threads
         * (e.g. in {@link NetworkConnection#receive()}), and on the remote side an {@link EOFException} is thrown
         * on all waiting threads.
         * If the connection is already closed then invoking this method has no effect.
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            if (!isClosed()) {
                socket.close();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NetworkConnection<?> that = (NetworkConnection<?>) o;
            return socket.equals(that.socket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(socket);
        }
    }
}

package com.giliannereyes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * HTTPS web server implemented with TCP sockets over TLS.
 *
 * <p>The server accepts incoming connections, parses a single request line, and returns simple
 * HTML responses for a few routes.
 */
public class WebServer {
  private static final int DEFAULT_PORT = 8443;
  private static final String DEFAULT_KEYSTORE_PATH = "keystore-new.p12";
  private static final String DEFAULT_KEYSTORE_PASSWORD = "changeit";
  private static final String DEFAULT_KEYSTORE_TYPE = "PKCS12";

  /**
   * Starts the web server and handles clients sequentially.
   *
   * <p>Optional JVM system properties:
   *
   * <ul>
   *   <li>{@code https.keystore.path} (defaults to keystore-new.p12)
   *   <li>{@code https.keystore.password} (defaults to changeit)
   *   <li>{@code https.key.password} (defaults to keystore password)
   *   <li>{@code https.keystore.type} (defaults to PKCS12)
   *   <li>{@code https.port} (defaults to 8443)
   * </ul>
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    try {
      TlsServerConfig config = TlsServerConfig.fromSystemProperties();
      try (SSLServerSocket serverSocket = createTlsServerSocket(config)) {
        System.out.println("Server running at https://localhost:" + config.port());

        while (true) {
          Socket client = serverSocket.accept();
          handleClient(client);
        }
      }
    } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
      e.printStackTrace();
    }
  }

  /**
   *
   * Creates and configures an SSL server socket from keystore settings.
   *
   * @param config TLS server configuration
   * @return ready-to-use SSL server socket
   * @throws IOException if keystore file cannot be read
   * @throws GeneralSecurityException if TLS initialization fails
   */
  private static SSLServerSocket createTlsServerSocket(TlsServerConfig config)
      throws IOException, GeneralSecurityException {
    SSLContext sslContext = buildSslContext(config);
    SSLServerSocketFactory socketFactory = sslContext.getServerSocketFactory();
    SSLServerSocket serverSocket = (SSLServerSocket) socketFactory.createServerSocket(config.port());
    serverSocket.setNeedClientAuth(false);
    return serverSocket;
  }

  /**
   * Builds an SSL context using the configured keystore.
   *
   * @param config TLS server configuration
   * @return initialized SSL context
   * @throws IOException if keystore cannot be read
   * @throws GeneralSecurityException if cryptographic initialization fails
   */
  private static SSLContext buildSslContext(TlsServerConfig config)
      throws IOException, GeneralSecurityException {
    KeyStore keyStore = KeyStore.getInstance(config.keystoreType());
    try (InputStream in = new FileInputStream(config.keystorePath())) {
      keyStore.load(in, config.keystorePassword().toCharArray());
    }

    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, config.keyPassword().toCharArray());

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
    return sslContext;
  }

  /**
   * Handles one client connection from request read to response write.
   *
   * @param client accepted client socket
   */
  private static void handleClient(Socket client) {
    try (
        Socket socket = client;
        BufferedReader in =
            new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out =
            new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)),
                true)) {

      String requestLine = in.readLine();
      System.out.println("Received: " + requestLine);

      if (requestLine == null || requestLine.isBlank()) {
        return;
      }

      discardHeaders(in);

      HttpRequest request = parseRequestLine(requestLine);
      HttpResponse response = (request == null) ? badRequestResponse() : routeRequest(request);

      sendResponse(out, response);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Reads and discards all request headers until the blank separator line.
   *
   * @param in request input reader
   * @throws IOException if reading fails
   */
  private static void discardHeaders(BufferedReader in) throws IOException {
    String headerLine;
    while ((headerLine = in.readLine()) != null) {
      if (headerLine.isEmpty()) {
        break;
      }
    }
  }

  /**
   * Parses an HTTP request line into method, path, and version.
   *
   * @param requestLine first line of the HTTP request
   * @return parsed request, or {@code null} if the line is malformed
   */
  private static HttpRequest parseRequestLine(String requestLine) {
    String[] parts = requestLine.split(" ");
    if (parts.length != 3) {
      return null;
    }
    return new HttpRequest(parts[0], parts[1], parts[2]);
  }

  /**
   * Routes a validated request and returns the corresponding HTTP response.
   *
   * @param request parsed HTTP request
   * @return response with status and body
   */
  private static HttpResponse routeRequest(HttpRequest request) {
    if (!"GET".equals(request.method())) {
      return new HttpResponse(
          "405 Method Not Allowed",
          "<html><body><h1>405 Method Not Allowed</h1><p>Only GET is supported.</p></body></html>");
    }

    if (!"HTTP/1.1".equals(request.version()) && !"HTTP/1.0".equals(request.version())) {
      return new HttpResponse(
          "505 HTTP Version Not Supported",
          "<html><body><h1>505 HTTP Version Not Supported</h1></body></html>");
    }

    return switch (request.path()) {
      case "/" -> new HttpResponse(
          "200 OK",
          "<html><body>"
              + "<h1>Home Page</h1>"
              + "<p>Simple web server.</p>"
              + "<a href=\"/page1\">Go to page 1</a><br>"
              + "<a href=\"/page2\">Go to page 2</a>"
              + "</body></html>");
      case "/page1" -> new HttpResponse(
          "200 OK",
          "<html><body>"
              + "<h1>Page 1</h1>"
              + "<p>This is page1.</p>"
              + "<a href=\"/\">Back to the home page</a>"
              + "</body></html>");
      case "/page2" -> new HttpResponse(
          "200 OK",
          "<html><body>"
              + "<h1>Page 2</h1>"
              + "<p>This is page2.</p>"
              + "<a href=\"/\">Back to the home page</a>"
              + "</body></html>");
      default -> new HttpResponse(
          "404 Not Found",
          "<html><body><h1>404 Not Found</h1><p>The page does not exist.</p></body></html>");
    };
  }

  /**
   * Creates a generic bad-request response for malformed request lines.
   *
   * @return 400 response
   */
  private static HttpResponse badRequestResponse() {
    return new HttpResponse(
        "400 Bad Request",
        "<html><body><h1>400 Bad Request</h1><p>Invalid request line.</p></body></html>");
  }

  /**
   * Writes a full HTTP response to the client.
   *
   * @param out response writer
   * @param response response status and body
   */
  private static void sendResponse(PrintWriter out, HttpResponse response) {
    byte[] bodyBytes = response.body().getBytes(StandardCharsets.UTF_8);

    out.println("HTTP/1.1 " + response.status());
    out.println("Content-Type: text/html; charset=UTF-8");
    out.println("Content-Length: " + bodyBytes.length);
    out.println("Connection: close");
    out.println();
    out.print(response.body());
    out.flush();
  }

  /** Parsed values from the HTTP request line. */
  private record HttpRequest(String method, String path, String version) {}

  /** HTTP status line value and response body content. */
  private record HttpResponse(String status, String body) {}

  /**
   * Immutable TLS server configuration read from JVM system properties.
   *
   * @param port HTTPS listen port
   * @param keystorePath path to server keystore
   * @param keystorePassword keystore password
   * @param keyPassword key password
   * @param keystoreType keystore type (for example PKCS12 or JKS)
   */
  private record TlsServerConfig(
      int port, String keystorePath, String keystorePassword, String keyPassword, String keystoreType) {
    /**
     * Loads TLS configuration from JVM system properties.
     *
     * @return TLS configuration instance
     */
    private static TlsServerConfig fromSystemProperties() {
      int port = Integer.parseInt(System.getProperty("https.port", String.valueOf(DEFAULT_PORT)));
      String keystorePath = System.getProperty("https.keystore.path", DEFAULT_KEYSTORE_PATH);
      String keystorePassword =
          System.getProperty("https.keystore.password", DEFAULT_KEYSTORE_PASSWORD);
      String keyPassword = System.getProperty("https.key.password", keystorePassword);
      String keystoreType = System.getProperty("https.keystore.type", DEFAULT_KEYSTORE_TYPE);
      return new TlsServerConfig(port, keystorePath, keystorePassword, keyPassword, keystoreType);
    }
  }
}

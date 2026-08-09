package com.giliannereyes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/**
 * UDP client for the dot-product service.
 *
 * <p>This client sends both vectors in a single datagram and waits for one server response.
 */
public class UdpDotProductClient {
  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 9_876;
  private static final int DEFAULT_TIMEOUT_MILLIS = 3_000;

  private final String host;
  private final int port;
  private final int timeoutMillis;

  /**
   * Creates a UDP dot-product client.
   *
   * @param host server host name or IP
   * @param port server UDP port
   * @param timeoutMillis receive timeout in milliseconds
   */
  public UdpDotProductClient(String host, int port, int timeoutMillis) {
    this.host = host;
    this.port = port;
    this.timeoutMillis = timeoutMillis;
  }

  /**
   * Sends one request and returns the parsed server response.
   *
   * @param first first vector
   * @param second second vector
   * @return parsed response
   * @throws IOException if sending or receiving fails
   */
  public UdpDotProductProtocol.ParsedResponse send(double[] first, double[] second) throws IOException {
    byte[] requestPayload = UdpDotProductProtocol.encodeRequest(first, second);

    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(timeoutMillis);
      InetAddress serverAddress = InetAddress.getByName(host);

      DatagramPacket requestPacket =
          new DatagramPacket(requestPayload, requestPayload.length, serverAddress, port);
      socket.send(requestPacket);

      byte[] responseBuffer = new byte[UdpDotProductProtocol.MAX_UDP_PAYLOAD_BYTES];
      DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
      socket.receive(responsePacket);

      return UdpDotProductProtocol.decodeResponse(responsePacket.getData(), responsePacket.getLength());
    }
  }

  /**
   * Demo entry point for manually testing the UDP service.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    double[] vectorA = {1.0, 2.0, 3.0};
    double[] vectorB = {4.0, 5.0, 6.0};

    UdpDotProductClient client = new UdpDotProductClient(DEFAULT_HOST, DEFAULT_PORT, DEFAULT_TIMEOUT_MILLIS);

    try {
      UdpDotProductProtocol.ParsedResponse response = client.send(vectorA, vectorB);
      if (response.success()) {
        System.out.println("Dot product: " + response.dotProduct());
      } else {
        System.out.println("Server error: " + response.errorMessage());
      }
    } catch (SocketTimeoutException e) {
      System.err.println("Timed out while waiting for server response.");
    } catch (IOException | IllegalArgumentException e) {
      System.err.println("Client error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}

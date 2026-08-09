package com.giliannereyes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * UDP server that computes dot products for vector pairs received in one datagram.
 *
 * <p>The server runs in a simple event loop: receive request, compute response, send response.
 */
public class UdpDotProductServer {
  private static final int DEFAULT_PORT = 9_876;

  private final int port;

  /**
   * Creates a UDP dot-product server.
   *
   * @param port UDP port to listen on
   */
  public UdpDotProductServer(int port) {
    this.port = port;
  }

  /**
   * Starts the server loop and listens forever.
   *
   * @throws IOException if socket setup or I/O fails
   */
  public void start() throws IOException {
    try (DatagramSocket socket = new DatagramSocket(port)) {
      System.out.println("UDP dot-product server listening on port " + port);
      byte[] receiveBuffer = new byte[UdpDotProductProtocol.MAX_UDP_PAYLOAD_BYTES];

      while (true) {
        DatagramPacket requestPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(requestPacket);

        byte[] responsePayload = buildResponse(requestPacket.getData(), requestPacket.getLength());
        DatagramPacket responsePacket =
            new DatagramPacket(
                responsePayload,
                responsePayload.length,
                requestPacket.getAddress(),
                requestPacket.getPort());
        socket.send(responsePacket);
      }
    }
  }

  /**
   * Builds a protocol response for a received request payload.
   *
   * @param payload request bytes
   * @param payloadLength number of valid bytes in payload
   * @return encoded response bytes
   */
  private byte[] buildResponse(byte[] payload, int payloadLength) {
    try {
      UdpDotProductProtocol.ParsedRequest request =
          UdpDotProductProtocol.decodeRequest(payload, payloadLength);
      double dotProduct = computeDotProduct(request.first(), request.second());
      return UdpDotProductProtocol.encodeSuccessResponse(dotProduct);
    } catch (IllegalArgumentException e) {
      return UdpDotProductProtocol.encodeErrorResponse(e.getMessage());
    }
  }

  /**
   * Computes dot product for two equally-sized vectors.
   *
   * @param first first vector
   * @param second second vector
   * @return dot product value
   */
  private double computeDotProduct(double[] first, double[] second) {
    double sum = 0.0;
    for (int i = 0; i < first.length; i++) {
      sum += first[i] * second[i];
    }
    return sum;
  }

  /**
   * Starts the server on the default port.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    UdpDotProductServer server = new UdpDotProductServer(DEFAULT_PORT);
    try {
      server.start();
    } catch (IOException e) {
      System.err.println("Failed to run UDP server: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
